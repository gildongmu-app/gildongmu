// 처음 화면 — 앱의 첫 화면(Launch.kt)과 같은 장면이다.
// 밤길을 달리는 차 → 하늘에 빠르게 적히는 한마디들 → 길의 두 선이 휘어 올라 따옴표가 되어 건네는 물음
// → 두 따옴표가 하나로 모인 로고 (길 끝으로 멀어진 차의 불빛이 날아와 로고의 불빛이 된다).
import { LOGO } from './logo.js';

const SAID = [
  '“아 오늘 하루도 고생했다. 어서 집에 가서 쉬자.”',
  '“오늘 정말 재미있었다. 인생에 꼽히는 날이야.”',
  '“엄마한테 전화나 해 볼까. 괜히 보고 싶네.”',
  '“드디어 금요일이다. 주말엔 애들이랑 바다 보러 가야지.”',
];
const ASK = '오늘 하루는 어떠셨나요?';
const C0 = [38.5, 61], FOOT = [38.6, 69.5], TIP = [42.994, 46.104];      // 따옴표 도형(왼쪽)의 가운데, 발, 꼬리 끝 (108칸 좌표)
const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const ease = x => { x = clamp(x); return x * x * (3 - 2 * x); };
const easeOut = x => 1 - (1 - clamp(x)) ** 3;
const lerp = (a, b, k) => a + (b - a) * k;

export function heroIntro({ hero, canvas, title, mark, onLit }) {
  const ctx = canvas.getContext('2d');
  let raf = 0, done = false, t0 = 0, stage = 0;
  const words = [...title.querySelectorAll('.w')];
  words.forEach((w, i) => w.style.setProperty('--d', (i * 0.07).toFixed(2) + 's'));
  const finish = () => {
    if (done) return; done = true;
    cancelAnimationFrame(raf); ctx.clearRect(0, 0, canvas.width, canvas.height); canvas.style.display = 'none';
    mark.classList.remove('wait'); hero.classList.add('title', 'lit', 'now'); onLit?.();
  };
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) { finish(); return { finish }; }

  const W = hero.clientWidth, H = hero.clientHeight, dpr = Math.min(devicePixelRatio || 1, 1.5);
  canvas.width = W * dpr; canvas.height = H * dpr; ctx.scale(dpr, dpr);
  const box = hero.getBoundingClientRect(), serif = getComputedStyle(title).fontFamily, px = Math.max(1, W / 1500);
  // ---- 길: 지평선의 한 점으로 모이는 두 선과 가운데 선
  const hor = H * 0.62, vx = W * 0.5, half = Math.max(W * 0.47, H * 0.42);
  const ry = u => hor + (H - hor) * u, rx = (side, u) => vx + side * half * u;
  // ---- 한마디 네 줄 (좁은 화면에서는 문장에서 줄을 나눈다)
  const fs = clamp(W * 0.028, 14, 40), lineFont = `400 ${fs}px ${serif}`;
  const type = (text, x, y, start, per) => {
    const at = [], xs = []; let tt = start;
    for (let i = 0; i < text.length; i++) { at.push(tt); xs.push(ctx.measureText(text.slice(0, i)).width); const c = text[i]; tt += '.…?'.includes(c) ? per + 0.12 : c === ',' ? per + 0.06 : c === ' ' ? per * 0.6 : per; }
    return { text, x, y, w: ctx.measureText(text).width, at, xs, start, end: tt };
  };
  ctx.font = lineFont;
  const lines = []; let start = 0.7, row = H * 0.15;
  SAID.forEach((text, k) => {
    let parts = [text];
    if (ctx.measureText(text).width > W * 0.88) { const cut = text.indexOf('. ') + 2; parts = [text.slice(0, cut), text.slice(cut)]; }
    const wide = Math.max(...parts.map(p => ctx.measureText(p).width));
    const x = clamp(k % 2 === 0 ? W * (0.08 + 0.03 * k) : W - wide - W * (0.08 + 0.02 * k), W * 0.05, Math.max(W * 0.05, W * 0.95 - wide));
    const segs = parts.map((p, r) => { const g = type(p, x, row + r * fs * 1.45, start, 0.022); start = g.end; return g; });
    lines.push({ segs, start: segs[0].start, end: start }); start += 0.12; row += Math.max(H * 0.098, parts.length * fs * 1.45 + fs * 0.5);
  });
  const tOut = lines.at(-1).end + 0.45, tQ0 = tOut + 0.4;
  // ---- 물음과, 그 곁의 따옴표 자리
  const qf = clamp(W * 0.05, 22, 76), askFont = `700 ${qf}px ${serif}`;
  ctx.font = askFont;
  const ask = type(ASK, (W - ctx.measureText(ASK).width) / 2, H * 0.40, tQ0 + 1.05, 0.05);
  const tMerge = ask.end + 1.2, tLock = tMerge + 1.05, tLit = tLock + 0.62;
  const qs = qf * 1.12 / 31.8, qy = ask.y - qf * 0.5, qxL = ask.x - 15 * qs, qxR = ask.x + ask.w + 15 * qs;
  // ---- 로고의 자리 (진짜 로고가 놓인 곳)
  const mr = mark.getBoundingClientRect(), unit = mr.width / 72, ox = mr.left - box.left - 18 * unit, oy = mr.top - box.top - 18 * unit;
  const lcx0 = ox + C0[0] * unit, lcx1 = ox + (C0[0] + 19.162) * unit, lcy = oy + C0[1] * unit;
  const lightX = ox + LOGO.lightX * unit, lightY = oy + LOGO.lightY * unit;
  hero.style.setProperty('--lx', lightX + 'px'); hero.style.setProperty('--ly', lightY + 'px');
  const quotePath = new Path2D(LOGO.quoteL);
  // 선이 따옴표로 바뀌는 모양 변형: 따옴표의 윤곽을 점으로 뽑아 '발 → 꼬리 끝' 등뼈를 기준으로 한 자리로 적어 둔다.
  // 길의 선도 같은 차례의 점들로 보면, 두 모양 사이를 점마다 이어 줄 수 있다 (겹쳐 바꾸는 것이 아니라 같은 도형이 자란다).
  const MN = 96, sp = document.createElementNS('http://www.w3.org/2000/svg', 'path'); sp.setAttribute('d', LOGO.quoteL);
  const plen = sp.getTotalLength(), sl = Math.hypot(TIP[0] - FOOT[0], TIP[1] - FOOT[1]), ux = (TIP[0] - FOOT[0]) / sl, uy = (TIP[1] - FOOT[1]) / sl;
  const qsx = [], qsy = []; let k0 = 1, minS = 1e9;
  for (let i = 0; i < MN; i++) { const p = sp.getPointAtLength(plen * i / MN), dx = p.x - FOOT[0], dy = p.y - FOOT[1]; qsx.push((dx * ux + dy * uy) / sl); qsy.push((-dx * uy + dy * ux) / sl); if (qsx[i] < minS) { minS = qsx[i]; k0 = i; } }
  k0 = clamp(k0, 1, MN - 2);
  const sgn = qsy.slice(0, k0).reduce((a, b) => a + b, 0) >= 0 ? 1 : -1;
  const lu = qsx.map((_, i) => i <= k0 ? 1 - i / k0 : (i - k0) / (MN - k0)), lv = qsx.map((_, i) => i <= k0 ? sgn : -sgn);
  function morph(side, q) {
    const a0x = rx(side, 1.05), a0y = ry(1.05), qx = side < 0 ? qxL : qxR, sg = side < 0 ? 1 : -1;
    const fx = qx + sg * (FOOT[0] - C0[0]) * qs, fy = qy + sg * (FOOT[1] - C0[1]) * qs, tx = qx + sg * (TIP[0] - C0[0]) * qs, ty = qy + sg * (TIP[1] - C0[1]) * qs;
    const l0 = Math.hypot(vx - a0x, hor - a0y), l1 = Math.hypot(tx - fx, ty - fy), th0 = Math.atan2(hor - a0y, vx - a0x);
    let d = Math.atan2(ty - fy, tx - fx) - th0; while (d > Math.PI) d -= 2 * Math.PI; while (d < -Math.PI) d += 2 * Math.PI;
    const e = ease(q), th = th0 + d * e, len = Math.exp(lerp(Math.log(l0), Math.log(l1), easeOut(q * 1.15)));
    const mx = lerp((a0x + vx) / 2, (fx + tx) / 2, e), my = lerp((a0y + hor) / 2, (fy + ty) / 2, e) - Math.sin(q * Math.PI) * H * 0.045;
    const ca = Math.cos(th), sa = Math.sin(th), f0x = mx - ca * len / 2, f0y = my - sa * len / 2, k = ease((q - 0.22) / 0.78);
    ctx.beginPath();
    for (let i = 0; i < MN; i++) {
      const u = lu[i], hw = lerp(3.4, 0.6, u) * px, along = lerp(u, qsx[i], k) * len, perp = lerp(lv[i] * hw, qsy[i] * l1, k);
      const x = f0x + ca * along - sa * perp, y = f0y + sa * along + ca * perp; i ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
    }
    ctx.closePath();
    const g = ctx.createLinearGradient(f0x, f0y, f0x + ca * len, f0y + sa * len); g.addColorStop(0, '#FFF4E6'); g.addColorStop(0.35, '#FFDDB4'); g.addColorStop(0.7, '#FFC98A'); g.addColorStop(1, '#FFB25C');
    ctx.fillStyle = g; ctx.fill();
  }

  // 차 (뒤에서 본 모습, 너비 1 · 바닥 가운데가 원점)
  const body = new Path2D('M-.5 0V-.3C-.5-.44-.44-.5-.4-.54C-.36-.7-.32-.82-.22-.84H.22C.32-.82.36-.7.4-.54C.44-.5.5-.44.5-.3V0C.5.04.46.06.42.06H-.42C-.46.06-.5.04-.5 0Z');
  const glass = new Path2D('M-.21-.78H.21C.27-.77.3-.68.33-.56H-.33C-.3-.68-.27-.77-.21-.78Z');
  const rr = (x, y, w, h, r) => { const p = new Path2D(); p.roundRect(x, y, w, h, r); return p; };
  function car(cx, base, cw, alpha, t) {
    if (alpha <= 0.01) return;
    if (cw < 30 * px) {                       // 멀어지면 불빛 한 점
      const k = clamp(cw / (30 * px));
      ctx.globalCompositeOperation = 'lighter';
      ctx.fillStyle = `rgba(255,138,96,${0.6 * alpha})`; ctx.beginPath(); ctx.arc(cx, base - cw * 0.37, (4 + 8 * k) * px, 0, 7); ctx.fill();
      ctx.fillStyle = `rgba(255,217,168,${0.9 * alpha})`; ctx.beginPath(); ctx.arc(cx, base - cw * 0.37, (1.6 + 1.6 * k) * px, 0, 7); ctx.fill();
      ctx.globalCompositeOperation = 'source-over'; return;
    }
    ctx.save(); ctx.translate(cx, base + Math.sin(t * 5.3) * 0.9 * px); ctx.scale(cw, cw); ctx.globalAlpha = alpha;
    ctx.globalCompositeOperation = 'lighter';          // 젖은 길에 비친 뒷불
    for (const sx of [-0.31, 0.31]) {
      ctx.save(); ctx.translate(sx, 0.08); ctx.scale(0.17, 0.78);
      const g = ctx.createRadialGradient(0, 0, 0, 0, 0, 1); g.addColorStop(0, 'rgba(255,106,79,.44)'); g.addColorStop(0.45, 'rgba(255,106,79,.18)'); g.addColorStop(1, 'rgba(255,106,79,0)');
      ctx.fillStyle = g; ctx.beginPath(); ctx.arc(0, 0, 1, 0, 7); ctx.fill(); ctx.restore();
    }
    ctx.globalCompositeOperation = 'source-over';
    ctx.fillStyle = 'rgba(4,5,10,.55)'; ctx.beginPath(); ctx.ellipse(0, 0.055, 0.58, 0.075, 0, 0, 7); ctx.fill();
    ctx.fillStyle = '#04050a'; ctx.fill(rr(-0.475, -0.04, 0.175, 0.15, 0.035)); ctx.fill(rr(0.30, -0.04, 0.175, 0.15, 0.035));
    let g = ctx.createLinearGradient(0, -0.84, 0, 0.06); g.addColorStop(0, '#262b4a'); g.addColorStop(0.55, '#14172c'); g.addColorStop(1, '#0a0c18'); ctx.fillStyle = g; ctx.fill(body);
    g = ctx.createLinearGradient(-0.3, -0.8, 0.3, -0.54); g.addColorStop(0, 'rgba(85,96,143,.8)'); g.addColorStop(0.6, 'rgba(34,40,72,.8)'); g.addColorStop(1, 'rgba(58,68,112,.8)'); ctx.fillStyle = g; ctx.fill(glass);
    g = ctx.createLinearGradient(0, -0.86, 0, -0.3); g.addColorStop(0, 'rgba(255,241,220,.6)'); g.addColorStop(1, 'rgba(255,241,220,0)'); ctx.strokeStyle = g; ctx.lineWidth = 0.012; ctx.stroke(body);
    ctx.fillStyle = '#080a14'; ctx.fill(rr(-0.105, -0.265, 0.21, 0.08, 0.012));
    g = ctx.createLinearGradient(-0.46, 0, 0.46, 0); g.addColorStop(0, '#ff6a4f'); g.addColorStop(0.25, '#ffa070'); g.addColorStop(0.5, '#ff7a5a'); g.addColorStop(0.75, '#ffa070'); g.addColorStop(1, '#ff6a4f');
    ctx.fillStyle = g; ctx.fill(rr(-0.455, -0.405, 0.285, 0.07, 0.03)); ctx.fill(rr(0.17, -0.405, 0.285, 0.07, 0.03));
    ctx.globalAlpha = alpha * 0.55; ctx.fillRect(-0.17, -0.378, 0.34, 0.016); ctx.globalAlpha = alpha * (0.85 + 0.15 * Math.sin(t * 2.3));
    ctx.globalCompositeOperation = 'lighter';
    for (const sx of [-0.31, 0.31]) { const q = ctx.createRadialGradient(sx, -0.37, 0, sx, -0.37, 0.3); q.addColorStop(0, 'rgba(255,106,79,.6)'); q.addColorStop(0.5, 'rgba(255,106,79,.2)'); q.addColorStop(1, 'rgba(255,106,79,0)'); ctx.fillStyle = q; ctx.beginPath(); ctx.arc(sx, -0.37, 0.3, 0, 7); ctx.fill(); }
    ctx.restore(); ctx.globalCompositeOperation = 'source-over'; ctx.globalAlpha = 1;
  }
  /** 가는 사다리꼴 선 */
  function taper(ax, ay, bx, by, ha, hb) {
    const dx = bx - ax, dy = by - ay, d = Math.max(0.001, Math.hypot(dx, dy)), nx = -dy / d, ny = dx / d;
    ctx.beginPath(); ctx.moveTo(ax + nx * ha, ay + ny * ha); ctx.lineTo(bx + nx * hb, by + ny * hb); ctx.lineTo(bx - nx * hb, by - ny * hb); ctx.lineTo(ax - nx * ha, ay - ny * ha); ctx.closePath(); ctx.fill();
  }
  const edgeFill = () => { const g = ctx.createLinearGradient(0, hor, 0, H); g.addColorStop(0, '#ffb25c'); g.addColorStop(0.5, '#ffddb4'); g.addColorStop(1, '#fff4e6'); return g; };
  function typed(g, t, alpha, dy, font, caret) {
    if (t < g.start || alpha <= 0.01) return;
    ctx.font = font;
    let shown = 0; while (shown < g.at.length && g.at[shown] <= t) shown++;
    let full = shown; while (full > 0 && t - g.at[full - 1] < 0.14) full--;
    ctx.fillStyle = `rgba(255,246,234,${alpha})`; if (full) ctx.fillText(g.text.slice(0, full), g.x, g.y + dy);
    for (let j = full; j < shown; j++) { const a = clamp((t - g.at[j]) / 0.14); ctx.fillStyle = `rgba(255,246,234,${alpha * a})`; ctx.fillText(g.text[j], g.x + g.xs[j], g.y + dy + (1 - a) * 6); }
    if (caret && shown > 0 && shown < g.at.length) {
      ctx.globalCompositeOperation = 'lighter'; ctx.fillStyle = `rgba(255,241,220,${0.8 * alpha})`;
      ctx.beginPath(); ctx.arc(g.x + g.xs[shown] + 7, g.y - fs * 0.3, fs * 0.09, 0, 7); ctx.fill(); ctx.globalCompositeOperation = 'source-over';
    }
  }
  function quote(cx, cy, s, rot, alpha) {
    if (alpha <= 0.01) return;
    ctx.save(); ctx.translate(cx, cy); ctx.rotate(rot); ctx.scale(s, s); ctx.translate(-C0[0], -C0[1]); ctx.globalAlpha = alpha;
    const g = ctx.createLinearGradient(0, LOGO.inkY0, 0, LOGO.inkY1); g.addColorStop(0, '#FFF4E6'); g.addColorStop(0.35, '#FFDDB4'); g.addColorStop(0.7, '#FFC98A'); g.addColorStop(1, '#FFB25C');
    ctx.fillStyle = g; ctx.fill(quotePath); ctx.restore();
  }

  function frame(now) {
    if (done) return;
    raf = requestAnimationFrame(frame);
    const t = (now - t0) / 1000;
    ctx.clearRect(0, 0, W, H);
    const roadIn = easeOut(t / 0.9), roadA = roadIn * (1 - ease((t - tQ0) / 0.7)), q1 = clamp((t - tQ0) / 1.45), q2 = ease((t - tMerge - 0.1) / (tLock - tMerge - 0.1));
    // 1) 길바닥, 지평선의 빛, 다가오는 가운데 선
    if (roadA > 0.01) {
      let g = ctx.createLinearGradient(0, hor, 0, H); g.addColorStop(0, 'rgba(11,13,26,0)'); g.addColorStop(0.4, `rgba(11,13,26,${0.4 * roadA})`); g.addColorStop(1, `rgba(10,11,22,${0.56 * roadA})`);
      ctx.fillStyle = g; ctx.beginPath(); ctx.moveTo(vx, hor); ctx.lineTo(rx(1, 1.05), ry(1.05)); ctx.lineTo(rx(-1, 1.05), ry(1.05)); ctx.closePath(); ctx.fill();
      g = ctx.createLinearGradient(vx - W * 0.36, 0, vx + W * 0.36, 0); g.addColorStop(0, 'rgba(255,201,138,0)'); g.addColorStop(0.5, `rgba(255,217,168,${0.6 * roadA})`); g.addColorStop(1, 'rgba(255,201,138,0)');
      ctx.fillStyle = g; ctx.fillRect(vx - W * 0.36, hor - 0.6, W * 0.72, 1.2);
      for (let k = 0; k < 8; k++) {
        const u0 = ((k + t * 0.62) / 8) % 1, ua = u0 ** 2.1, ub = Math.min(1.02, u0 + 0.05) ** 2.1, a = roadA * ease(u0 / 0.14) * (1 - ease((u0 - 0.9) / 0.1));
        if (a <= 0.01 || ua > roadIn) continue;
        ctx.globalCompositeOperation = 'lighter'; ctx.fillStyle = `rgba(255,201,138,${0.16 * a})`; taper(vx, ry(ua), vx, ry(ub), (3 + 12 * ua) * px, (3 + 12 * ub) * px);
        ctx.globalCompositeOperation = 'source-over'; ctx.fillStyle = `rgba(255,201,138,${0.92 * a})`; taper(vx, ry(ua), vx, ry(ub), (0.5 + 4.2 * ua) * px, (0.5 + 4.2 * ub) * px);
      }
    }
    // 2) 길의 두 선: 그어지고 → 휘어 올라 따옴표의 꼬리가 된다
    for (const side of [-1, 1]) {
      if (q1 <= 0) {
        const top = 1.05 - 1.03 * roadIn, a = ease(t / 0.4);
        if (roadIn > 0.01) {
          ctx.globalCompositeOperation = 'lighter'; ctx.fillStyle = `rgba(255,201,138,${0.13 * a})`; taper(rx(side, 1.05), ry(1.05), rx(side, top), ry(top), 13 * px, (2 + 9 * top) * px);
          ctx.globalCompositeOperation = 'source-over'; ctx.globalAlpha = 0.94 * a; ctx.fillStyle = edgeFill(); taper(rx(side, 1.05), ry(1.05), rx(side, top), ry(top), 3.4 * px, (0.5 + 2.9 * top) * px); ctx.globalAlpha = 1;
        }
      } else if (q1 < 1) morph(side, q1);
    }
    // 3) 차: 달려 들어와 길 위를 달리다가, 한마디가 끝나면 길 끝으로 멀어져 불빛 한 점이 된다
    const away = clamp((t - (tOut - 0.15)) / 1.3), uCar = away <= 0 ? 0.6 + 0.7 * (1 - easeOut(t / 1.2)) : 0.015 + 0.585 * (1 - away * away) ** 2.2;
    const farX = vx + 0.5 * half * 0.015, farY = ry(0.015), carGone = t > tOut - 0.15 + 1.3;
    if (!carGone) car(vx + 0.5 * half * uCar, ry(uCar), 0.68 * half * uCar, ease(t / 0.5), t);
    // 4) 누군가의 한마디: 하늘에 한 줄씩, 빠르게
    if (t < tOut + 0.6) {
      const out = ease((t - tOut) / 0.55);
      lines.forEach((l, k) => { const next = lines[k + 1]?.start ?? 1e9; for (const g of l.segs) typed(g, t, (0.97 - 0.3 * ease((t - next) / 0.5)) * (1 - out), -24 * out, lineFont, true); });
    }
    // 5) 따옴표 둘과 그 사이의 물음 → 로고의 자리로 모인다 (오른쪽 따옴표는 돌아서 같은 쪽을 본다)
    const qa = (q1 >= 1 ? 1 : 0) * (1 - ease((t - tLock) / 0.5));
    if (qa > 0.01) {
      const arc = Math.sin(q2 * Math.PI) * H * 0.035, s = lerp(qs, unit, q2);
      quote(lerp(qxL, lcx0, q2), lerp(qy, lcy, q2) - arc, s, 0, qa);
      quote(lerp(qxR, lcx1, q2), lerp(qy, lcy, q2) - arc, s, Math.PI * (1 + q2), qa);
    }
    if (t >= ask.start && t < tMerge + 0.5) { const out = ease((t - tMerge) / 0.45); typed(ask, t, 1 - out, -14 * out, askFont, false); }
    // 6) 길 끝에 남아 있던 불빛이 날아와 로고의 불빛이 된다
    if (carGone && t < tLit) {
      const e = ease((t - (tLock - 0.45)) / (tLit - tLock + 0.45)), x = lerp(farX, lightX, e), y = lerp(farY, lightY, e) - Math.sin(e * Math.PI) * H * 0.04, tw = 0.75 + 0.25 * Math.sin(t * 6);
      ctx.globalCompositeOperation = 'lighter';
      ctx.fillStyle = `rgba(255,201,138,${0.26 * tw})`; ctx.beginPath(); ctx.arc(x, y, 22 * px, 0, 7); ctx.fill();
      ctx.fillStyle = `rgba(255,201,138,${0.55 * tw})`; ctx.beginPath(); ctx.arc(x, y, 9 * px, 0, 7); ctx.fill();
      ctx.fillStyle = `rgba(255,241,220,${0.98 * tw})`; ctx.beginPath(); ctx.arc(x, y, 3.2 * px, 0, 7); ctx.fill();
      ctx.globalCompositeOperation = 'source-over';
    }
    // 7) 제자리에 서면 진짜 로고와 제목이 이어받는다 (CSS)
    if (stage < 1 && t >= tLock) { stage = 1; mark.classList.remove('wait'); hero.classList.add('title'); }
    if (stage < 2 && t >= tLit) { stage = 2; hero.classList.add('lit'); onLit?.(); }
    if (t > tLit + 0.6) { cancelAnimationFrame(raf); ctx.clearRect(0, 0, W, H); canvas.style.display = 'none'; done = true; }
  }
  raf = requestAnimationFrame(now => { t0 = now; frame(now); });
  return { finish };
}
