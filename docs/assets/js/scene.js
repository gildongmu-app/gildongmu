// 살아 있는 바탕 — 앱이 화면 뒤에 그리는 장면(AGSL 셰이더)을 WebGL 로 그대로 그린다.
// 셰이더는 tools/site/agsl2glsl.mjs 가 앱의 assets/scenes/*.agsl 에서 만든 assets/scenes/<id>.frag.
// 쓰는 법: const s = await createScene(canvas, { scene: 'rain', day: 3 }); s.set({ day: 2, soft: .4 }, 1600)
//
// 느린 컴퓨터에서도 페이지가 멈추지 않게:
//  - 셰이더를 준비하는 동안(Windows 에서는 몇 초~몇십 초 걸릴 수 있다) 화면을 붙잡지 않는다. 그동안에는 뒤에 깔린 정지 그림이 보인다.
//  - 그래픽 가속이 없거나(소프트웨어 렌더링) 준비가 너무 오래 걸리면 정지 그림으로 둔다.
//  - 그리는 것이 버거우면 스스로 크기를 줄이고, 그래도 버거우면 한 장만 그려 두고 멈춘다 ('lite': 유리 효과도 끈다).

export const SCENES = ['city', 'ridges', 'coast', 'green', 'reeds', 'blossom', 'ginkgo', 'snow', 'rain', 'aurora'];
export const DAY = { dawn: 0, day: 1, dusk: 2, night: 3 };          // 앱의 uDay 와 같다 (사이 값은 섞인다)

const VERT = 'attribute vec2 p; void main(){ gl_Position = vec4(p, 0.0, 1.0); }';
const NUM = ['day', 'soft', 'dim', 'rain', 'wind', 'level', 'flow', 'veil'];
const ease = x => x * x * (3 - 2 * x);
const lite = () => document.documentElement.classList.add('lite');

export async function createScene(canvas, opts = {}) {
  const base = new URL('../scenes/', import.meta.url);
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const st = { scene: 'rain', day: 3, soft: 0.2, dim: 0, rain: 0, wind: 0.4, level: 0, flow: 0, veil: 0, seed: 7, grade: 0, speed: 1, ...opts };
  const from = { ...st }, to = { ...st };
  let tw0 = 0, twDur = 0;
  const api = { fallback: false, canvas, state: st, set: async () => {}, start() {}, stop() {}, destroy() {}, renderOnce() {} };
  const giveUp = () => { api.fallback = true; canvas.classList.remove('on'); lite(); return api; };
  let gl = null;
  try { gl = canvas.getContext('webgl', { antialias: false, alpha: false, powerPreference: 'default', failIfMajorPerformanceCaveat: true }); } catch { /* 아래에서 정지 그림으로 */ }
  if (!gl) return giveUp();

  const par = gl.getExtension('KHR_parallel_shader_compile');      // 있으면 셰이더 준비를 기다리는 동안 화면이 멈추지 않는다
  const buf = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, buf);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
  const progs = new Map();
  let cur = null, raf = 0, running = false, visible = true, t = 20 + st.seed * 3, last = 0, shown = 0;
  let quality = 1, slow = 0, counted = 0, frozen = false;

  async function program(id) {
    if (progs.has(id)) return progs.get(id);
    const src = await (await fetch(new URL(id + '.frag', base))).text();
    const mk = (type, s) => { const sh = gl.createShader(type); gl.shaderSource(sh, s); gl.compileShader(sh); return sh; };
    const pr = gl.createProgram();
    gl.attachShader(pr, mk(gl.VERTEX_SHADER, VERT)); gl.attachShader(pr, mk(gl.FRAGMENT_SHADER, src));
    gl.bindAttribLocation(pr, 0, 'p'); gl.linkProgram(pr);
    if (par) {
      const t0 = performance.now();
      while (!gl.getProgramParameter(pr, par.COMPLETION_STATUS_KHR)) {
        if (performance.now() - t0 > 20000) throw new Error('장면 준비가 너무 오래 걸림: ' + id);
        await new Promise(r => setTimeout(r, 80));
      }
    }
    if (!gl.getProgramParameter(pr, gl.LINK_STATUS)) throw new Error('장면 셰이더: ' + id);
    const u = {};
    for (const n of ['uSize', 'uTime', 'uFlow', 'uDay', 'uWind', 'uRain', 'uLevel', 'uSoft', 'uSeed', 'uGrade', 'uDim', 'uVeil', 'uFade']) u[n] = gl.getUniformLocation(pr, n);
    const p = { pr, u }; progs.set(id, p); return p;
  }

  function resize() {
    // 장면은 부드러운 그림이라 작게 그려 늘려도 티가 나지 않는다: 작은 화면 약 0.45, 큰 화면 약 0.9 메가픽셀 (버거우면 더 줄인다)
    const w = canvas.clientWidth || innerWidth, h = canvas.clientHeight || innerHeight;
    const budget = (w < 760 ? 0.45 : 0.9) * 1e6 * quality;
    const k = Math.min(Math.min(devicePixelRatio || 1, 2), Math.sqrt(budget / Math.max(1, w * h)));
    const W = Math.max(2, Math.round(w * k)), H = Math.max(2, Math.round(h * k));
    if (canvas.width !== W || canvas.height !== H) { canvas.width = W; canvas.height = H; gl.viewport(0, 0, W, H); }
  }

  function draw(now) {
    if (!cur) return;
    if (twDur > 0) {
      const k = Math.min(1, (now - tw0) / twDur), e = ease(k);
      for (const n of NUM) st[n] = from[n] + (to[n] - from[n]) * e;
      if (k >= 1) twDur = 0;
    }
    const { u } = cur;
    gl.useProgram(cur.pr);
    gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0);
    gl.uniform2f(u.uSize, canvas.width, canvas.height);
    gl.uniform1f(u.uTime, t); gl.uniform1f(u.uFlow, st.flow);
    gl.uniform1f(u.uDay, ((st.day % 4) + 4) % 4); gl.uniform1f(u.uWind, st.wind);
    if (u.uRain) gl.uniform1f(u.uRain, st.rain);
    gl.uniform1f(u.uLevel, st.level); gl.uniform1f(u.uSoft, st.soft);
    gl.uniform1f(u.uSeed, st.seed * 1.37); gl.uniform1f(u.uGrade, st.grade);
    gl.uniform1f(u.uDim, st.dim); gl.uniform4f(u.uVeil, 0.043, 0.051, 0.102, st.veil); gl.uniform1f(u.uFade, 1);
    gl.drawArrays(gl.TRIANGLES, 0, 3);
  }

  function frame(now) {
    raf = requestAnimationFrame(frame);
    if (now - shown < 30) return;                      // 초당 30장이면 넉넉하다 (앱도 흐려서 깔 때는 그렇게 그린다)
    const gap = now - shown;
    const dt = Math.min(0.1, (now - (last || now)) / 1000); last = now; shown = now;
    t += dt * st.speed * (reduce ? 0.15 : 1);
    draw(now);
    // 버거운지 본다: 서른 장을 그리는 동안 절반 넘게 늦었으면 크기를 줄이고, 두 번 줄여도 늦으면 한 장만 그려 두고 멈춘다
    if (gap < 400) { counted++; if (gap > 58) slow++; }
    if (counted >= 30) {
      if (slow > 15) {
        if (quality > 0.3) { quality *= 0.55; resize(); }
        else { frozen = true; stop(); lite(); draw(now); }
      }
      counted = 0; slow = 0;
    }
  }
  function start() { if (running || frozen || !visible || document.hidden || !cur) return; running = true; last = 0; shown = 0; counted = 0; slow = 0; raf = requestAnimationFrame(frame); }
  function stop() { running = false; cancelAnimationFrame(raf); }

  /** 값을 바꾼다. 숫자는 ms 동안 부드럽게 옮겨 가고, 장면이 바뀌면 그 장면으로 넘어간다. */
  async function set(next, ms = 1200) {
    if (api.fallback) return;
    if (next.scene && next.scene !== st.scene) {
      const p = await program(next.scene).catch(() => null);
      if (p) { st.scene = next.scene; cur = p; }
    }
    for (const n of ['seed', 'grade', 'speed']) if (n in next) st[n] = next[n];
    let any = false;
    for (const n of NUM) if (n in next) {
      from[n] = st[n]; to[n] = next[n]; any = true;
      if (n === 'day') { const d = (((next.day - st.day) % 4) + 6) % 4 - 2; to.day = st.day + d; }
    } else { from[n] = st[n]; if (twDur <= 0) to[n] = st[n]; }
    if (any) { tw0 = performance.now(); twDur = Math.max(1, ms); }
    if (!running) { if (frozen) { for (const n of NUM) st[n] = to[n]; twDur = 0; } draw(performance.now() + ms); }
  }
  function destroy() { stop(); io.disconnect(); ro.disconnect(); }

  const io = new IntersectionObserver(es => { visible = es[0].isIntersecting; visible ? start() : stop(); });
  const ro = new ResizeObserver(() => { resize(); if (!running) draw(performance.now()); });
  io.observe(canvas); ro.observe(canvas);
  document.addEventListener('visibilitychange', () => document.hidden ? stop() : start());
  canvas.addEventListener('webglcontextlost', e => { e.preventDefault(); stop(); canvas.classList.remove('on'); });
  canvas.addEventListener('webglcontextrestored', () => location.reload());

  Object.assign(api, { set, start, stop, destroy, renderOnce: () => draw(performance.now()) });
  resize();
  try { cur = await program(st.scene); } catch { return giveUp(); }
  draw(performance.now());
  requestAnimationFrame(() => canvas.classList.add('on'));          // 첫 그림이 그려진 뒤에, 정지 그림 위로 부드럽게 올라온다
  start();
  return api;
}
