// 길동무 소개 페이지의 움직임 — 장면(바탕), 처음의 빛 글씨, 스크롤에 따라 나타나는 글, 해 보기, 시연 영상
import { createScene } from './scene.js';
import { LOGO } from './logo.js';
import { heroIntro } from './intro.js';

/** 저장소 주소 (올린 뒤 여기 한 곳만 채운다). 비어 있으면 단추가 '곧 공개'로 보인다. */
const REPO = 'https://github.com/gildongmu-app/gildongmu';
const $ = (q, el = document) => el.querySelector(q), $$ = (q, el = document) => [...el.querySelectorAll(q)];

// 새로고침으로 들어왔으면(index.html 머리의 짧은 스크립트가 표시해 둔다) 처음 들어올 때처럼 맨 위, 첫 화면부터 시작한다
const fresh = !!window.gdFresh;
const toTop = () => { const h = document.documentElement, b = h.style.scrollBehavior; h.style.scrollBehavior = 'auto'; scrollTo(0, 0); h.style.scrollBehavior = b; };
if (fresh) toTop();
const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
const wait = ms => new Promise(r => setTimeout(r, ms));

// ── 로고 「따옴길」: 앱 아이콘과 같은 도형을 svg 로
let uid = 0;
function markSVG() {
  const id = 'm' + uid++, L = LOGO;
  const dash = (arr, cls) => arr.map((d, i) => `<path class="dash d${i + 1}" d="${d}" fill="url(#${id}i)"/><path class="flash d${i + 1} ${cls}" d="${d}" fill="#fff"/>`).join('');
  return `<svg viewBox="18 18 72 72" aria-hidden="true"><defs>
    <linearGradient id="${id}i" gradientUnits="userSpaceOnUse" x1="0" y1="${L.inkY0}" x2="0" y2="${L.inkY1}"><stop offset="0" stop-color="#FFF4E6"/><stop offset=".35" stop-color="#FFDDB4"/><stop offset=".7" stop-color="#FFC98A"/><stop offset="1" stop-color="#FFB25C"/></linearGradient>
    <radialGradient id="${id}s"><stop offset="0" stop-color="#8C7BFF" stop-opacity=".25"/><stop offset="1" stop-color="#8C7BFF" stop-opacity="0"/></radialGradient>
    <radialGradient id="${id}g"><stop offset="0" stop-color="#FFC98A" stop-opacity=".66"/><stop offset=".35" stop-color="#FFC98A" stop-opacity=".33"/><stop offset="1" stop-color="#FFC98A" stop-opacity="0"/></radialGradient>
    <radialGradient id="${id}c"><stop offset="0" stop-color="#fff"/><stop offset=".6" stop-color="#FFF4E6"/><stop offset="1" stop-color="#FFD9A8"/></radialGradient>
    <linearGradient id="${id}h" gradientUnits="userSpaceOnUse" x1="${L.hx0}" x2="${L.hx1}"><stop offset="0" stop-color="#FFC98A" stop-opacity="0"/><stop offset=".59" stop-color="#FFD9A8" stop-opacity=".9"/><stop offset="1" stop-color="#FFC98A" stop-opacity="0"/></linearGradient></defs>
    <circle class="sky" cx="${L.lightX}" cy="${L.lightY}" r="${L.sky}" fill="url(#${id}s)"/>
    <rect class="hz" x="${L.hx0}" y="${L.lightY - 0.3}" width="${L.hx1 - L.hx0}" height=".6" fill="url(#${id}h)"/>
    <path class="q" d="${L.quoteL}" fill="url(#${id}i)"/><path class="q" d="${L.quoteR}" fill="url(#${id}i)"/>
    ${dash(L.dashL, 'l')}${dash(L.dashR, 'r')}
    <circle class="lg" cx="${L.lightX}" cy="${L.lightY}" r="${L.glow}" fill="url(#${id}g)"/><circle class="lc" cx="${L.lightX}" cy="${L.lightY}" r="${L.core}" fill="url(#${id}c)"/></svg>`;
}
for (const m of $$('[data-mark]')) { m.innerHTML = markSVG(); if (m.dataset.mark) m.classList.add('wait'); }

// ── 글귀를 줄마다 나눠, 줄이 아래에서 올라오게
for (const el of $$('[data-lines]')) el.innerHTML = el.innerHTML.split(/<br\s*\/?>/).map((l, i) => `<span class="ln" style="--i:${i}"><span>${l.trim()}</span></span>`).join('');
// 제목은 낱말마다 (빛이 닿는 차례로 또렷해진다)
const title = $('#heroTitle');
title.innerHTML = title.innerHTML.split(/<br\s*\/?>/).map(l => l.trim().split(' ').map(w => `<span class="w">${w}</span>`).join(' ')).join('<br>');

// ── 바탕 장면: 지금 읽는 대목에 맞춰 풍경과 때가 바뀐다
// (장면이 준비되기를 기다리지 않고 처음 화면을 시작한다)
const sceneP = createScene($('#scene'), { scene: 'rain', day: 3, rain: 1, soft: 0.85, dim: 0.6 }).catch(() => null);      // 길동무의 얼굴: 빗빛 · 밤
const now = { scene: 'rain', day: 3 };
const scene = { set: (o, ms) => { Object.assign(now, o); return sceneP.then(s => s && s.set(o, ms)); } };
let manual = false;
const sceneOf = el => ({ scene: el.dataset.scene, day: +el.dataset.day, soft: +(el.dataset.soft ?? 0.6), dim: +(el.dataset.dim ?? 0.4), rain: +(el.dataset.rain ?? 0) });
const sceneIO = new IntersectionObserver(es => { for (const e of es) if (e.isIntersecting && !(manual && e.target.id === 'features')) { manual = false; scene.set(sceneOf(e.target), 1800); } }, { rootMargin: '-45% 0px -45% 0px' });
$$('[data-scene]').forEach(el => sceneIO.observe(el));

// ── 처음: 밤길을 달리는 차 위로 한마디 네 줄이 적히고, 길의 두 선이 따옴표가 되어 로고와 제목으로 모인다
const hero = $('.hero'), nav = $('#nav');
nav.classList.add('intro');
await Promise.race([Promise.all([document.fonts.load('400 20px "GD Serif"', '가'), document.fonts.load('700 20px "GD Serif"', '가')]), wait(1200)]);
const intro = heroIntro({ hero, canvas: $('.light', hero), title, mark: $('#heroMark'), onLit: () => { nav.classList.remove('intro'); scene.set({ dim: 0.3, soft: 0.45 }, 2200); } });
const skip = () => intro.finish();
$('#skipIntro').addEventListener('click', skip);
// 내려가면 첫 장면을 넘긴다. 다만 새로고침 직후에 브라우저가 보던 자리로 옮겨 놓는 것은 '사용자가 내려갔다'로 보지 않고 맨 위로 되돌린다
let armed = !fresh;
addEventListener('scroll', () => { if (armed && scrollY > 40) skip(); }, { passive: true });
if (fresh) {
  const arm = () => { armed = true; };
  for (const ev of ['wheel', 'touchstart', 'pointerdown', 'keydown']) addEventListener(ev, arm, { once: true, passive: true });
  const settle = () => setTimeout(() => { if (!armed) { toTop(); armed = true; } }, 120);
  document.readyState === 'complete' ? settle() : addEventListener('load', settle, { once: true });
}
const w0 = innerWidth; addEventListener('resize', () => { if (Math.abs(innerWidth - w0) > 80) skip(); });      // (휴대폰의 주소창이 접히는 것은 넘어간다)
addEventListener('keydown', e => { if (['Tab', 'Enter', ' ', 'ArrowDown', 'PageDown', 'End'].includes(e.key)) skip(); });
if (location.hash && location.hash !== '#top') skip();
else if (!fresh && scrollY > 40) skip();      // 뒤로 가기로 돌아와 이미 아래에 있을 때

// ── 스크롤에 따라 나타나기
const seen = new IntersectionObserver(es => { for (const e of es) if (e.isIntersecting) { e.target.classList.add('in'); seen.unobserve(e.target); e.target.dispatchEvent(new Event('seen')); } }, { rootMargin: '0px 0px -12% 0px', threshold: 0.1 });
for (const el of $$('[data-r], [data-lines]')) {
  const sib = [...el.parentElement.children].filter(c => c.hasAttribute('data-r'));
  if (el.hasAttribute('data-r')) el.style.setProperty('--i', sib.indexOf(el) % 6);
  seen.observe(el);
}

// ── 위쪽 줄: 스크롤하면 유리가 되고, 읽은 만큼 가는 줄이 찬다
const links = $$('.nav nav a'), secs = links.map(a => $(a.getAttribute('href')));
function onScroll() {
  nav.classList.toggle('solid', scrollY > 30);
  nav.style.setProperty('--p', (scrollY / Math.max(1, document.documentElement.scrollHeight - innerHeight)).toFixed(4));
  const mid = scrollY + innerHeight * 0.4; let on = -1;
  secs.forEach((s, i) => { if (s && s.offsetTop <= mid) on = i; });
  links.forEach((a, i) => a.classList.toggle('on', i === on));
}
addEventListener('scroll', onScroll, { passive: true }); onScroll();

// ── 사라지는 말: 한 글자씩 적혔다가, 아무도 듣지 못한 채 흩어진다
const vanish = $('.vanish');
if (vanish) {
  const items = $$('li', vanish);
  for (const li of items) { li.dataset.text = li.textContent; li.innerHTML = [...li.textContent].map(c => `<span class="c">${c}</span>`).join(''); }
  let running = false;
  async function play() {
    if (running) return; running = true;
    for (const li of items) { li.classList.remove('gone'); $$('.c', li).forEach(c => c.classList.remove('on')); }
    for (const li of items) { for (const c of $$('.c', li)) { c.classList.add('on'); await wait(reduce ? 0 : /[.,…]/.test(c.textContent) ? 190 : 36); } await wait(260); }
    await wait(1500);
    for (const li of items) { li.classList.add('gone'); await wait(420); }
    await wait(3200); running = false;
    if (inView) play();
  }
  let inView = false;
  new IntersectionObserver(es => { inView = es[0].isIntersecting; if (inView) play(); }, { threshold: 0.35 }).observe(vanish);
}

// ── 한 번의 퇴근길: 글을 따라 화면이 바뀐다
const steps = $$('.steps li'), vids = $$('.stage video'), dots = $$('.dots i');
function step(n) {
  steps.forEach((s, i) => s.classList.toggle('on', i === n)); dots.forEach((d, i) => d.classList.toggle('on', i === n));
  vids.forEach((v, i) => { v.classList.toggle('on', i === n); if (i === n) { v.currentTime = 0; v.play().catch(() => {}); } else v.pause(); });
}
const stepIO = new IntersectionObserver(es => { for (const e of es) if (e.isIntersecting) step(+e.target.dataset.step); }, { rootMargin: '-48% 0px -48% 0px' });
steps.forEach(s => stepIO.observe(s)); vids[0]?.classList.add('on');

// ── 기능 카드: 보일 때만 영상이 돌고, 마우스를 따라 빛이 번진다
const playIO = new IntersectionObserver(es => { for (const e of es) e.isIntersecting ? e.target.play().catch(() => {}) : e.target.pause(); }, { threshold: 0.45 });
$$('.shot video').forEach(v => playIO.observe(v));
for (const c of $$('.card')) c.addEventListener('pointermove', e => { const r = c.getBoundingClientRect(); c.style.setProperty('--mx', e.clientX - r.left + 'px'); c.style.setProperty('--my', e.clientY - r.top + 'px'); });

// ── 살아 있는 바탕 고르기 (이 페이지의 배경이 바로 그 장면)
const NAMES = { city: '불빛', ridges: '안개빛', coast: '바다빛', green: '풀빛', reeds: '억새빛', blossom: '꽃빛', ginkgo: '은행빛', snow: '눈빛', rain: '빗빛', aurora: '빛결' };
const TIMES = ['새벽', '낮', '노을', '밤'];
const picker = $('#scenePicker'), days = $('#dayPicker');
picker.innerHTML = Object.entries(NAMES).map(([id, n]) => `<button type="button" data-id="${id}" aria-pressed="false">${n}</button>`).join('');
days.innerHTML = TIMES.map((n, i) => `<button type="button" data-day="${i}" aria-pressed="false">${n}</button>`).join('');
function choose(next) {
  manual = true;
  const sc = next.scene ?? now.scene, d = next.day ?? Math.round(((now.day % 4) + 4) % 4) % 4;
  scene.set({ soft: 0.12, dim: 0.16, rain: sc === 'rain' ? 1 : 0, scene: sc, day: d }, 1400);
  $$('button', picker).forEach(b => b.setAttribute('aria-pressed', b.dataset.id === sc)); $$('button', days).forEach(b => b.setAttribute('aria-pressed', +b.dataset.day === d));
}
picker.addEventListener('click', e => { const b = e.target.closest('button'); if (b) choose({ scene: b.dataset.id }); });
days.addEventListener('click', e => { const b = e.target.closest('button'); if (b) choose({ day: +b.dataset.day }); });

// ── 로고 이야기: 보이면 따옴표 → 차선 → 불빛 차례로 켜진다
for (const m of $$('[data-mark=scroll], .closing [data-mark]')) new IntersectionObserver((es, io) => { if (es[0].isIntersecting) { m.classList.remove('wait'); io.disconnect(); } }, { threshold: 0.5 }).observe(m);

// ── 해 보기: 앱과 같은 AI 모델을 여기서 돌린다
const SAMPLES = [
  ['오늘 정말 하루가 힘들었어. 팀장한테 깨지고, 야근도 하고… 힘들다.', 'diary'],
  ['엄마랑 통화하고 나서 괜히 눈물이 났어.', 'diary'],
  ['내일 발표인데 너무 떨린다. 어떡하지 진짜.', 'diary'],
  ['오늘은 일이 빨리 끝났다. 진짜 오랜만이네, 기분 좋네.', 'road'],
  ['제 번호는 공일공 이사팔일 삼오칠구예요. 연락 주세요.', 'road'],
  ['아 진짜 개빡친다. 왜 저러는 거야.', 'diary'],      // 거친 말: 담지 않고 다시 적어 달라고 청한다
];
const form = $('#tryForm'), input = $('#tryText'), result = $('#tryResult'), orb = $('.orb');
let ai = null, share = 'diary';
const getAi = () => ai ??= import('./ai.js').then(m => m.loadAi());
new IntersectionObserver((es, io) => { if (es[0].isIntersecting) { getAi(); io.disconnect(); } }, { rootMargin: '600px' }).observe(form);
function setShare(v) {
  share = v; $$('.seg button').forEach(b => b.setAttribute('aria-checked', b.dataset.share === v));
  $('#tryHint').textContent = v === 'road' ? '길 위에는 이름 없이 남고, 올라가기 전에 AI 필터를 거칩니다.' : '말은 내 일기에만 담깁니다.';
}
$('.seg').addEventListener('click', e => { const b = e.target.closest('button'); if (b) setShare(b.dataset.share); });
$('#tryChips').innerHTML = SAMPLES.map(([t], i) => `<button type="button" data-i="${i}">${t.length > 24 ? t.slice(0, 23) + '…' : t}</button>`).join('');
$('#tryChips').addEventListener('click', e => { const b = e.target.closest('button'); if (!b) return; const [t, s] = SAMPLES[b.dataset.i]; input.value = t; setShare(s); form.requestSubmit(); });
let typing = 0;
async function type(el, text) { const me = ++typing; el.textContent = ''; for (const c of text) { if (me !== typing) return; el.textContent += c; await wait(reduce ? 0 : /[.,]/.test(c) ? 150 : 30); } }
form.addEventListener('submit', async e => {
  e.preventDefault();
  const text = input.value.trim(); if (!text) { input.focus(); return; }
  orb.classList.add('think');
  const m = await getAi(), r = m.save(text, { share, seed: text + Date.now() });
  await wait(reduce ? 0 : 520); orb.classList.remove('think');
  result.hidden = false; result.style.animation = 'none'; void result.offsetWidth; result.style.animation = '';
  result.classList.toggle('refused', !!r.refused);
  $('#trySaved').textContent = r.refused ? '담지 않았어요' : '오늘의 기록에 담았어요';
  if (r.refused) {
    // 거친 말은 담지 않는다: 안 된다고 알리고, 다듬어서 다시 적어 달라고 청한다 (앱에서는 목소리로 알린 뒤 곧바로 한 번 더 듣는다)
    const chip = $('#tryMood'); chip.textContent = '거친 말'; chip.style.setProperty('--c', '#FFB25C');
    const where = $('#tryWhere'); where.className = 'where blocked'; where.textContent = '일기에도, 길 위에도 담지 않았어요';
    $('#tryBars').innerHTML = `<p class="note"><b>${r.refused.label}</b> 담지 않았어요.<br>욕설이나 누군가를 깎아내리고 위협하는 말은 어디에도 담지 않습니다. 전화번호 같은 개인 정보는 길 위에 올릴 때 알아서 걸러요.</p>`;
    type($('#tryReply'), `${r.refused.notice} 조금만 다듬어서 다시 적어 주세요.`);
    input.focus(); input.select();
    return;
  }
  const chip = $('#tryMood'); chip.textContent = r.mood.label; chip.style.setProperty('--c', r.mood.color);
  const where = $('#tryWhere');
  where.className = 'where' + (r.shared ? ' shared' : r.blocked ? ' blocked' : '');
  where.textContent = r.shared ? '길 위에도 남겼어요 · 이름 없이' : r.blocked ? `길 위에는 올리지 않았어요 · ${r.blocked.label}` : '내 일기에만 담았어요';
  const order = ['JOY', 'FLUTTER', 'CALM', 'WORRY', 'TIRED', 'SAD', 'ANGRY'], sc = r.scores ?? [];
  $('#tryBars').innerHTML = order.map(id => { const s = sc.find(x => x.id === id); return s ? `<div class="${s.id === r.mood.id ? 'top' : ''}"><span>${s.label}</span><i style="--p:0;--c:${s.color}" data-p="${s.p}"></i><span>${Math.round(s.p * 100)}%</span></div>` : ''; }).join('');
  requestAnimationFrame(() => requestAnimationFrame(() => $$('#tryBars i').forEach(i => i.style.setProperty('--p', i.dataset.p))));
  type($('#tryReply'), r.reply);
  const li = document.createElement('li'), now = new Date();
  li.innerHTML = `<time>${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}</time><span></span><em class="chip" style="--c:${r.mood.color}">${r.mood.label}</em>`;
  li.children[1].textContent = text; $('#tryDiary').prepend(li);
  while ($('#tryDiary').children.length > 4) $('#tryDiary').lastChild.remove();
  scene.set({ level: 1 }, 300).then(() => setTimeout(() => scene.set({ level: 0 }, 1600), 400));
  input.value = ''; if (matchMedia('(pointer: fine)').matches) input.focus();
});
getAi().then(m => $$('[data-meta]').forEach(el => el.textContent = m.meta[el.dataset.meta] ?? el.textContent)).catch(() => {});

// ── 시연 영상: 두 편 가운데 고르고, 장면 목록에서 고르면 그 자리로
const video = $('#demoVideo'), chapters = $('#chapters');
const mmss = s => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`;
const REELS = { demo: '1114b', emul: '1114c' };          // 영상 이름 → 파일 판 (바뀌면 올려 주어 예전 것이 남아 보이지 않게)
const media = (name, ext) => new URL(`../media/${name}.${ext}?v=${REELS[name]}`, import.meta.url);
let marks = [];
chapters.addEventListener('click', e => { const b = e.target.closest('button'); if (b) { video.currentTime = +b.dataset.t + 0.05; video.play(); } });
video.addEventListener('timeupdate', () => { let on = 0; marks.forEach((b, i) => { if (+b.dataset.t <= video.currentTime + 0.2) on = i; }); marks.forEach((b, i) => b.classList.toggle('on', i === on)); });
function loadChapters(name) {
  fetch(media(name, 'json')).then(r => r.json()).then(list => {
    chapters.innerHTML = list.map(c => `<li><button type="button" data-t="${c.t}"><span>${c.title}</span><time>${mmss(c.t)}</time></button></li>`).join('');
    marks = $$('button', chapters);
  }).catch(() => { chapters.innerHTML = ''; marks = []; });
}
loadChapters('demo');
$('.reels').addEventListener('click', e => {
  const b = e.target.closest('button[data-reel]'); if (!b || b.getAttribute('aria-selected') === 'true') return;
  $$('.reels button').forEach(x => x.setAttribute('aria-selected', x === b));
  const name = b.dataset.reel;
  video.pause(); video.poster = media(name, 'jpg').href; video.src = media(name, 'mp4').href; video.load();
  loadChapters(name);
});

// ── 코드: 탭과 가벼운 색
for (const pre of $$('pre[data-code] code')) pre.innerHTML = pre.innerHTML
  .replace(/(\/\*\*[\s\S]*?\*\/|\/\/[^\n]*)/g, '<span class="cm">$1</span>')
  .replace(/\b(fun|val|var|for|while|if|else|return|in|private|until)\b(?![^<]*<\/span>)/g, '<span class="kw">$1</span>');
$('.tabs').addEventListener('click', e => { const b = e.target.closest('button'); if (!b) return; $$('.tabs button').forEach(x => x.setAttribute('aria-selected', x === b)); $$('pre[data-code]').forEach(p => p.hidden = p.dataset.code !== b.dataset.code); });

// ── 숫자가 차오르기
for (const el of $$('[data-count]')) el.closest('[data-r]').addEventListener('seen', () => {
  const to = +el.dataset.count, t0 = performance.now(); if (reduce || !to) return;
  (function tick(now) { const k = Math.min(1, (now - t0) / 1400), e = 1 - (1 - k) ** 3; el.textContent = Math.round(to * e); if (k < 1) requestAnimationFrame(tick); })(t0);
});

// ── 봉인된 제작자 서명: 만든 사람을 밝힌 글을 비밀번호로 암호화해 넣어 둔 것 (앱의 Maker.kt, tools/maker/seal.py 와 같은 방식).
// 비밀번호 없이는 읽을 수도, 다른 글로 바꿔 넣을 수도 없다. 비밀번호는 어디에도 보내지 않고 이 페이지 안에서만 쓴다.
const SEAL = 'gd1:Mp64g0JQKPoEal+IjOaYugqHLPpgUKwU8uSqpR3n7RgYNP1mj0yoAqDN5rXASiMOn9PTK7s3zpWD+MXDwi2goLUoKZKGNpukFC+3S7RtjYVAWwGc2DPkDzPE0dwxAyvKPMX7cRzR8H5GpB5n0WzRg1nXDJrpUJz+Ow==';
async function openSeal(pw) {
  const raw = Uint8Array.from(atob(SEAL.slice(4)), c => c.charCodeAt(0)), salt = raw.slice(0, 16), ct = raw.slice(16, -32), tag = raw.slice(-32);
  const cat = (...a) => { const o = new Uint8Array(a.reduce((n, x) => n + x.length, 0)); let i = 0; for (const x of a) { o.set(x, i); i += x.length; } return o; };
  const base = await crypto.subtle.importKey('raw', new TextEncoder().encode(pw.trim()), 'PBKDF2', false, ['deriveBits']);
  const bits = new Uint8Array(await crypto.subtle.deriveBits({ name: 'PBKDF2', salt, iterations: 120000, hash: 'SHA-256' }, base, 512));
  const key = k => crypto.subtle.importKey('raw', k, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const mac = new Uint8Array(await crypto.subtle.sign('HMAC', await key(bits.slice(32)), cat(salt, ct)));
  if (!mac.every((b, i) => b === tag[i])) return null;
  const ek = await key(bits.slice(0, 32)), out = new Uint8Array(ct.length);
  for (let j = 0; j * 32 < ct.length; j++) {
    const ks = new Uint8Array(await crypto.subtle.sign('HMAC', ek, cat(salt, new Uint8Array([j >>> 24, j >>> 16, j >>> 8, j]))));
    for (let i = 0; i < 32 && j * 32 + i < ct.length; i++) out[j * 32 + i] = ct[j * 32 + i] ^ ks[i];
  }
  return new TextDecoder().decode(out);
}
const makerForm = $('#makerForm'), makerBtn = $('#makerBtn');
makerBtn?.addEventListener('click', () => { makerForm.hidden = !makerForm.hidden; makerBtn.setAttribute('aria-expanded', !makerForm.hidden); if (!makerForm.hidden) $('#makerPw').focus(); });
makerForm?.addEventListener('submit', async e => {
  e.preventDefault();
  const out = $('#makerOut'); out.textContent = '여는 중…';
  const r = await openSeal($('#makerPw').value).catch(() => null);
  out.textContent = r ?? '비밀번호가 맞지 않아요';
});

// ── 저장소 링크
for (const a of $$('[data-gh]')) {
  if (!REPO) { if (a.closest('.repo')) { a.classList.add('off'); a.title = '저장소 주소는 곧 올라갑니다'; } continue; }
  const p = a.dataset.gh; a.href = !p ? REPO : p === 'apk' ? `${REPO}/tree/main/apk` : `${REPO}/blob/main/${p}`; a.target = '_blank'; a.rel = 'noopener';
}
