// 웹사이트의 AI 이식본(docs/assets/js/ai.js)이 앱과 같은 답을 내는지 본다: node tools/site/test_ai.mjs
import { readFile } from 'node:fs/promises';
globalThis.fetch = async u => ({ text: async () => readFile(new URL(u), 'utf8') });
const { loadAi } = await import('../../docs/assets/js/ai.js');
const ai = await loadAi();
const cases = [
  ['오늘 정말 하루가 힘들었어 팀장한테 깨지고 야근도 하고 힘들다', 'TIRED', '혼나고, 야근까지. 오늘 정말 애쓰셨어요.', true],
  ['제 번호는 010 1481 삼오칠오입니다 연락 주세요', null, null, 'PRIVACY'],
  ['혹시 제 번호 0102481 삼오칠구로 연락주세요', null, null, 'PRIVACY'],
  ['배고파 죽겠다', null, null, true], ['노을 죽여준다', null, null, true],
  ['너 같은 건 죽여버릴 거야', null, null, 'HARM'], ['죽고 싶다', null, '많이 힘들었군요. 혼자 견디지 않아도 돼요. 이야기할 곳이 필요하면 109에 전화해 보세요. 언제든 받아요.', 'SELF_HARM'],
  ['아 오늘 하루도 고생했다. 어서 집에 가서 쉬자.', null, null, true], ['오늘 정말 재미있었다. 인생에 꼽히는 날이야.', null, null, true],
  ['엄마랑 통화하고 나서 괜히 눈물이 났어 엄마가 너무 보고 싶다', 'SAD', '전화 한 통에 마음이 움직였네요. 잘 적어 둘게요.', true],
  ['그 사람 진짜 쓰레기 같아', 'ANGRY', null, null], ['시1발 짜증나', null, null, 'PROFANITY'],
];
let bad = 0;
for (const [t, mood, reply, guard] of cases) {
  const r = ai.save(t, { share: 'road' });
  const g = r.refused ? r.refused.kind : r.blocked ? r.blocked.kind : true;
  const m = r.mood ?? ai.read(t).mood;
  const ok = (!mood || m.id === mood) && (!reply || r.reply === reply) && (guard === null || g === guard);
  if (!ok) bad++;
  console.log(ok ? 'ok ' : 'XX ', m.label, '|', g === true ? '통과' : g + (r.refused ? ' (담지 않음)' : r.blocked.byModel ? '(모델 ' + r.blocked.p.toFixed(2) + ')' : ''), '|', r.reply ?? '-', '|', t.slice(0, 22));
}

// 거친 말은 담지 않는다 (refused) · 멀쩡한 말은 잘못 막지 않는다
const refuse = {
  PROFANITY: ['씨발 진짜', '아 시발', '시1발 짜증나', 'ㅅㅂ 뭐야', '하 진짜 개빡친다', '빡쳐 죽겠네', '존나 피곤해', '졸라 힘들다', '저 새끼 뭐야', '그새끼 진짜 왜 저래', '개새끼',
    '병신 같은 게', '지랄하네', '개같네 진짜', '개소리하지 마', '닥쳐', '싸가지 없는 놈', '미친놈 아니야', '염병하네', '엿 같은 하루'],
  HATE: ['김치녀들은 운전하지 마'], SEXUAL: ['야동 보다가 늦게 잤다'],
  HARM: ['죽여버릴 거야', '저 인간 패버리고 싶다', '칼로 찔러 버릴까'],
};
const fine = ['다시 바꿔야겠다', '다시 발표해야 돼', '택시 아저씨 바로 앞에서 내렸어', '두 개 새로 샀어', '무지개색 우산 예쁘다', '두 개 같이 주문했어', '전염병 때문에 걱정이다',
  '마감이 닥쳐서 정신없다', '도시락 싸 가지고 왔어', '이 근처 먹을 데 없나', '깜빡 치과 예약을 잊었네', '주말에 한강 간다', '의자 위에 가방 올려놨어', '혼자 위로하는 중',
  '얘기야 동생 생겼대', '딸기 따 먹으러 가자', '과자 살까', '혼자 살기 싫어서 강아지 데려왔어', '월급 전이라 돈이 없다', '실패 줄이려고 노력 중', '세금 폭탄 맞았다',
  '오늘 비가 물폭탄이네', '별점 테러 당했다', '고양이 새끼 낳았어', '옆집 개 소리 때문에 잠을 못 잤다', '오늘 일정 빡세다', '애가 졸라서 사 줬다', '노을 죽여준다',
  '배고파 죽겠다', '팔등신 모델 같다', '시바견 너무 귀엽다', '또 라이브 방송 하네', '가방을 뒤져 봤는데 없어', '불이 꺼져 있었다', '짜증나', '열받아 죽겠네', '진짜 킹받네',
  '오늘 정말 하루가 힘들었어 팀장한테 깨지고 야근도 하고 힘들다', '5개년 계획 발표했다', '시발점이 어디였더라'];
for (const [kind, list] of Object.entries(refuse)) for (const t of list) {
  const r = ai.save(t, { share: 'diary' }); const ok = r.refused?.kind === kind;
  if (!ok) { bad++; console.log('XX  담지 않아야 하는데:', t, '→', r.refused?.kind ?? '담음'); }
}
for (const t of fine) {
  const r = ai.save(t, { share: 'road' });
  if (r.refused || r.blocked) { bad++; console.log('XX  멀쩡한 말을 막음:', t, '→', (r.refused ?? r.blocked).kind, (r.blocked?.byModel ? '(모델 ' + r.blocked.p.toFixed(2) + ')' : '')); }
}
for (const [t, want] of [['죽고 싶다', 'SELF_HARM'], ['자살하고 싶어', 'SELF_HARM'], ['살기 싫다 진짜', 'SELF_HARM'], ['급전 필요하신 분 연락 주세요', 'SPAM']]) {
  const r = ai.save(t, { share: 'road' });
  if (r.refused || r.blocked?.kind !== want) { bad++; console.log('XX ', t, '→', r.refused?.kind ?? r.blocked?.kind ?? '통과'); }
}
console.log(`거친 말 ${Object.values(refuse).flat().length}문장, 멀쩡한 말 ${fine.length}문장을 봄`);

// 앱(Ai.kt)과 웹(ai.js)의 사전이 글자 하나까지 같은지
const kt = await readFile(new URL('../../app/src/main/java/com/gildongmu/app/Ai.kt', import.meta.url), 'utf8');
const js = await readFile(new URL('../../docs/assets/js/ai.js', import.meta.url), 'utf8');
const inKt = Object.fromEntries([...kt.matchAll(/private val (\w+) = Regex\("(.*)"\)$/gm)].map(m => [m[1], m[2].replaceAll('\\$', '$')]));
const inJs = Object.fromEntries([...js.matchAll(/^const (\w+) = new RegExp\('(.*)'\);$/gm)].map(m => [m[1], m[2]]));
let same = 0;
for (const [name, pat] of Object.entries(inJs)) { if (inKt[name] === pat) same++; else { bad++; console.log('XX  사전이 다름:', name); } }
console.log(`사전 ${same}개가 앱과 같음`);

console.log(bad ? `${bad}개 다름` : '모두 맞음', ai.meta);
process.exit(bad ? 1 : 0);
