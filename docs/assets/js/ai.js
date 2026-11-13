// 길동무의 AI — 앱에 들어 있는 것과 같은 모델 파일(assets/ai/*.tsv)을 브라우저에서 그대로 돌린다.
// 앱의 Ai.kt(마음결·해치는 말 모델, AI 필터)와 Diary.kt(마음결 판별, 길동무의 답)를 한 줄씩 옮긴 것이다.
// 입력한 말은 이 페이지 밖으로 나가지 않는다 (모델 파일 세 개를 받아 오는 것 말고는 통신이 없다).

export const MOODS = [
  { id: 'JOY', label: '기쁨', color: '#FFC53D' }, { id: 'FLUTTER', label: '설렘', color: '#FF7FA8' },
  { id: 'CALM', label: '잔잔함', color: '#5FE0C0' }, { id: 'WORRY', label: '걱정', color: '#FF9A4D' },
  { id: 'TIRED', label: '지침', color: '#B4B8C5' }, { id: 'SAD', label: '슬픔', color: '#6FA8FF' },
  { id: 'ANGRY', label: '속상함', color: '#FF5A4F' },
];
const moodOf = id => MOODS.find(m => m.id === id);
const f32 = Math.fround;                       // 앱은 Float 로 셈한다

const isHangul = o => (o >= 0xAC00 && o <= 0xD7A3) || (o >= 0x3131 && o <= 0x318E);
/** 한글·영문 소문자·숫자만 남기고, 나머지는 칸 하나로 */
export function norm(s) {
  let out = '';
  for (const ch of s.toLowerCase()) {
    const o = ch.charCodeAt(0);
    out += (ch.length === 1 && (isHangul(o) || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9'))) ? ch : ' ';
  }
  return out.split(' ').filter(Boolean).join(' ');
}
/** 글자 1~3개 묶음 (낱말의 처음과 끝을 알 수 있게 앞뒤에 칸을 붙인다) */
export function feats(s) {
  const t = ' ' + norm(s) + ' ', fs = new Set();
  for (let n = 1; n <= 3; n++) for (let i = 0; i + n <= t.length; i++) { const g = t.substring(i, i + n); if (g.trim()) fs.add(g); }
  return fs;
}
// 낱말들 (낱말 안에 섞어 넣은 숫자는 걷어 낸다), 그리고 한 문장을 두 가지로 풀어 둔 것
const words = s => norm(s).split(' ').map(w => w.replace(/[0-9]/g, '')).filter(Boolean);
const seen = text => { const ws = words(text); return { t: tight(text), ws, sp: ws.join(' ') }; };
const has = (s, inTight, inWord) => inTight.test(s.t) || s.ws.some(w => inWord.test(w));
const hurting = s => has(s, selfHarm, selfHarmWord) || (noLive.test(s.sp) && !noLiveAlone.test(s.sp));
const swearing = s => has(s, profanity, profanityWord) || profanitySpaced.test(s.sp);
const roughKind = (s, text) => swearing(s) ? 'PROFANITY' : has(s, hate, hateWord) ? 'HATE' : (has(s, sexual, sexualStart) || sexualWord.test(norm(text))) ? 'SEXUAL' : has(s, violence, violenceWord) ? 'HARM' : null;
const tight = s => [...s.toLowerCase()].filter(c => { const o = c.charCodeAt(0); return c.length === 1 && (isHangul(o) || (c >= 'a' && c <= 'z')); }).join('');

function parse(text) {
  let classes = [], bias = [];
  const w = new Map();
  for (const line of text.split('\n')) {
    const tab = line.indexOf('\t');
    if (tab <= 0) continue;
    const key = line.substring(0, tab), nums = line.substring(tab + 1).trim().split(' ');
    if (key === '#classes') classes = nums;
    else if (key === '#bias') bias = Float32Array.from(nums, Number);
    else w.set(key.replaceAll('_', ' '), Float32Array.from(nums, Number));
  }
  return {
    classes,
    predict(s) {
      const fs = feats(s), v = f32(1 / f32(Math.sqrt(Math.max(1, fs.size))));
      const z = Float32Array.from(bias);
      for (const f of fs) { const row = w.get(f); if (row) for (let k = 0; k < z.length; k++) z[k] = f32(z[k] + f32(row[k] * v)); }
      const mx = Math.max(...z); let sum = 0;
      for (let k = 0; k < z.length; k++) { z[k] = f32(Math.exp(z[k] - mx)); sum = f32(sum + z[k]); }
      for (let k = 0; k < z.length; k++) z[k] = f32(z[k] / sum);
      return z;
    },
  };
}

// ---- AI 필터 (Guard): 남에게 보이는 자리에 올라가기 전에 거치는 곳
const KIND = {
  SELF_HARM: '마음이 많이 힘든 말이라서', PRIVACY: '전화번호 같은 개인 정보가 들어 있어서', SPAM: '광고 같은 말이 들어 있어서',
  PROFANITY: '욕설이 들어 있어서', HATE: '누군가를 깎아내리는 말이 들어 있어서', SEXUAL: '성적인 말이 들어 있어서', HARM: '남을 해치거나 공격하는 말이라서',
};
const HARM_THRESHOLD = 0.80;
// 사전: 붙여 본 글(tight)에서 찾는 것과, 낱말 하나하나의 앞머리에서 찾는 것 (앱의 Ai.kt 와 글자 하나까지 같다. 까닭은 거기에 적었다)
const selfHarm = new RegExp('죽고싶|사라지고싶|목숨을끊|뛰어내리고싶|뛰어내릴까|죽어버리고싶|그만살고싶');
const selfHarmWord = new RegExp('^자살');
const noLive = new RegExp('살기 ?싫');
const noLiveAlone = new RegExp('(혼자|같이|둘이|여기서|거기서|집에서) 살기 ?싫');
const profanity = new RegExp('씨발|씨빨|씨팔|쓰발|씌발|싀발|씹새|씹년|씹놈|씹창|씹할|씹탱|ㅅㅂ|ㅆㅂ|ㅅㅃ|ㅆㅃ|ㅈㄴ|ㅈㄹ|ㅂㅅ|ㅁㅊ|ㄱㅅㄲ|tlqkf|qudtls|ssibal|좆|ㅈ같|병신|븅신|지랄|육시럴|씨부랄|씨부럴|니미럴|니애미|느금마|엠창|썅|쌍놈|쌍년|개새끼|개색끼|개색기|개색히|개세끼|개쉐끼|개자식|미친새끼|미친놈|미친년|씨새끼|병새끼|망할새끼|새끼야|새꺄|존나|존내|엿같|엿먹|호로새끼|호로자식|후레자식|후레새끼');
const profanityWord = new RegExp('^(아|야|아오|에이|이런|하)?(시발|시빨|시팔|씨바|시바)(?!점|역|견|스)|^(이|저|그|야|야이|이런|저런|그런)?개(새$|새끼|새키|색기|색끼|색히|쉐이|쉐리|쉑|세끼|놈)|^(이|저|그|요|야이)(새끼|새키|색기|쌔끼|색히)|^개같|^개소리|^개빡|^개지랄|^(왕|핵|존)?빡(치|친|쳐|쳤|침|돌)|^닥쳐(라|요|봐)?$|^아닥|^입닥|^염병|^옘병|^엠병|^등신(아|이야|이네|들|새끼)?$|^등신같|^졸라(게)?$|^(처|쳐)먹|^아가리|^또라이|^돌아이|^싸가지(야|네)?$|^싸가지(없|가없|도없)|^조까|^좃|^fuck|^shit$|^bitch|^asshole');
const profanitySpaced = new RegExp('(^| )(이|저|그|요|이런|저런|그런|야이|미친) (새끼|새키|색기|쌔끼|색히)');
const hate = new RegExp('한남충|한남새끼|김치녀|된장녀|맘충|급식충|틀딱|짱깨|쪽바리|깜둥|똥꼬충|홍어새끼|전라디언|애자새끼');
const hateWord = new RegExp('^한남들|^개독');
const sexual = new RegExp('섹스|성폭행|조건만남|원나잇|몸캠');
const sexualStart = new RegExp('^야동|^자위(?!대|권)|^섹파|^따먹|^강간|^딸딸이');
const sexualWord = new RegExp('(^| )(보지|자지)( |$)');
const violence = new RegExp('죽여버|죽일거|죽일놈|죽일년|죽일테|찔러버|불질러|때려죽|칼들고|칼로찌|칼로쑤|목을졸|목을비틀|들이받아버|폭탄테러|폭탄을터|폭탄터뜨|폭탄터트|폭탄던|테러를하|테러할|테러한다|테러해버');
const violenceWord = new RegExp('^패(버리|버려|버릴|줄|주고|죽)|^뒤져라|^뒤질래|^뒈져|^뒈질|^디져라|^디질래');
const spam = new RegExp('http|www|오픈채팅|오픈톡|카톡아이디|카카오톡아이디|텔레그램|바카라|카지노|토토사이트|리딩방|코인리딩|수익보장|대출상담|무료상담');
const spamWord = new RegExp('^급전');
// 거친 말을 받았을 때의 알림 (Guard.notice)
const NOTICE = { PROFANITY: '거친 말은 담을 수 없어요.', HATE: '누군가를 깎아내리는 말은 담을 수 없어요.', SEXUAL: '이런 말은 여기에 담을 수 없어요.', HARM: '누군가를 다치게 할 수 있는 말은 담을 수 없어요.' };
const privacy = [
  /(01[016789]|02|0[3-6][1-5]|070)[\s.-]*\d{3,4}[\s.-]*\d{4}/, /\d{6}\s*-?\s*[1-4]\d{6}/,
  /\d{2,3}\s?[가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주아바사자배하허호]\s?\d{4}/,
  /\d{2,6}-\d{2,6}-\d{2,8}/, /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/, /\d{1,4}\s?동\s?\d{1,4}\s?호/, /\d{9,}/,
];
const digitWord = { 공: '0', 영: '0', 일: '1', 이: '2', 삼: '3', 사: '4', 오: '5', 육: '6', 륙: '6', 칠: '7', 팔: '8', 구: '9' };
const phoneDigits = /(01[016789]|02|0[3-6][1-5]|070)\d{7,8}|\d{9,}/;
/** 숫자를 읽은 말(세 글자 이상 이어질 때만)을 숫자로 바꾸고, 숫자 사이의 칸·점·줄표를 없앤다 */
function digits(text) {
  let sb = '', i = 0;
  while (i < text.length) {
    let j = i;
    while (j < text.length && text[j] in digitWord) j++;
    if (j - i >= 3) { for (let k = i; k < j; k++) sb += digitWord[text[k]]; i = j; } else { sb += text[i]; i++; }
  }
  return sb.replace(/(?<=\d)[\s.-]+(?=\d)/g, '');
}

// ---- 길동무의 답 (Diary.kt 의 Words)
const heard = [
  [/죽고 ?싶|자살|살기 ?싫|사라지고 ?싶|목숨을 ?끊|그만 ?살고 ?싶/, ['많이 힘들었군요. 혼자 견디지 않아도 돼요. 이야기할 곳이 필요하면 109에 전화해 보세요. 언제든 받아요.']],
  [/야근.*(깨지|깨졌|혼나|혼났|욕먹|욕 먹|한 ?소리|털렸)|(깨지|깨졌|혼나|혼났|욕먹|욕 먹|한 ?소리|털렸).*야근/, ['혼나고, 야근까지. 오늘 정말 애쓰셨어요.']],
  [/깨지|깨졌|혼나|혼났|욕먹|욕 먹|한 ?소리 (듣|들)|털렸/, ['그 말은 여기 내려놓고 가요. 오늘 애쓰셨어요.']],
  [/야근/, ['야근까지 하고 가는 길이네요. 오늘 정말 애쓰셨어요.']],
  [/칼퇴|정시 ?퇴근|일찍 퇴근|(일찍|빨리) ?끝났/, ['일찍 가는 날이네요. 이런 날은 천천히 누려요.']],
  [/(엄마|아빠|어머니|아버지|부모님|할머니|할아버지).*(전화|통화|보고 싶)/, ['전화 한 통에 마음이 움직였네요. 잘 적어 둘게요.']],
  [/라면|치킨|맥주|야식|떡볶이/, ['맛있게 먹어요. 오늘 하루 버틴 상이에요.']],
  [/막히|막혀|막힌|막힌다/, ['길이 막히면 마음도 급해지죠. 천천히 가도 돼요.']],
];
const reflect = {
  JOY: ['좋은 일이 있었네요. 듣는 저도 좋아요.', '오늘 같은 날은 오래 기억해 둘게요.', '목소리가 밝아서 좋네요.'],
  FLUTTER: ['설레는 일이 있다는 건 좋은 거죠.', '기다려지는 일이 생겼네요. 잘 적어 둘게요.', '두근거리는 게 여기까지 들려요.'],
  CALM: ['별일 없는 하루도 잘 적어 둘게요.', '이런 날이 사실 제일 좋은 날이죠.', '편안하게 들었어요.'],
  WORRY: ['걱정되는 게 당연해요. 잘하고 싶은 거니까요.', '일단 출발했잖아요. 절반은 한 거예요.', '막상 닥치면 생각보다 괜찮을 거예요.'],
  TIRED: ['오늘도 끝까지 왔어요. 그걸로 충분해요.', '애쓴 하루였어요. 정말로.', '오늘은 일찍 쉬어요. 그래도 되는 날이에요.'],
  SAD: ['말해 줘서 고마워요. 잘 적어 둘게요.', '울고 싶으면 울어도 돼요. 차 안이니까요.', '오늘은 여기 내려놓고 가요.'],
  ANGRY: ['그럴 만했어요. 화날 일이네요.', '속상한 건 속상한 거죠. 잘 적어 둘게요.', '숨 한 번 크게 쉬고, 창문 조금 열어 봐요.'],
};
const plain = ['들려줘서 고마워요. 잘 적어 둘게요.', '마음, 잘 담아 둘게요.', '오늘의 한마디, 잘 받았어요.'];
function hash(s) { let h = 0; for (let i = 0; i < s.length; i++) h = (Math.imul(h, 31) + s.charCodeAt(i)) | 0; return h; }
const pick = (list, seed) => list[Math.abs(hash(seed)) % list.length];

export async function loadAi(base = new URL('../ai/', import.meta.url)) {
  const [mt, ht, meta] = await Promise.all(['mood.tsv', 'harm.tsv', 'meta.txt'].map(async f => (await fetch(new URL(f, base))).text()));
  const moodModel = parse(mt), harmModel = parse(ht);
  const harm = text => harmModel.predict(text)[1];
  /** 일곱 마음결의 확률 (모델이 낸 그대로) */
  function scores(text) {
    if (!norm(text)) return null;
    const p = moodModel.predict(text);
    return moodModel.classes.map((id, k) => ({ ...moodOf(id), p: p[k] })).sort((a, b) => b.p - a.p);
  }
  /** 마음결 판별 (MoodReader.score): 해치는 말로 본 문장은 즐거운 마음결로 읽지 않고, 확신이 낮으면 잔잔함으로 둔다 */
  function read(text) {
    const sc = scores(text);
    if (!sc) return { mood: moodOf('CALM'), p: 0, sure: false, scores: null };
    const top = sc[0];
    const hostile = harm(text) >= f32(0.6) && ['JOY', 'FLUTTER', 'CALM'].includes(top.id);
    const mood = hostile ? moodOf('ANGRY') : top.p < f32(0.40) ? moodOf('CALM') : moodOf(top.id);
    return { mood, p: top.p, sure: hostile || top.p >= f32(0.40), hostile, scores: sc };
  }
  const swears = text => swearing(seen(text));
  /** 거친 말인지 (Guard.rough): 욕설·깎아내리는 말·성적인 말·남을 해치는 말. 이런 말은 담지 않고, 다듬어서 다시 적어 달라고 청한다 */
  function rough(text) {
    const s = seen(text);
    if (hurting(s)) return null;
    const k = roughKind(s, text) ?? (harm(text) >= f32(HARM_THRESHOLD) ? 'HARM' : null);
    return k ? { kind: k, label: KIND[k], notice: NOTICE[k] } : null;
  }
  function guard(text) {
    const s = seen(text);
    const k = hurting(s) ? 'SELF_HARM'
      : (privacy.some(r => r.test(text)) || phoneDigits.test(digits(text))) ? 'PRIVACY'
      : has(s, spam, spamWord) ? 'SPAM' : roughKind(s, text);
    if (k) return { ok: false, kind: k, label: KIND[k], byModel: false, p: 0 };
    const p = harm(text);
    return p >= f32(HARM_THRESHOLD) ? { ok: false, kind: 'HARM', label: KIND.HARM, byModel: true, p } : { ok: true, kind: null, label: null, byModel: false, p };
  }
  function reply(text, seed = text) {
    for (const [re, lines] of heard) if (re.test(text)) return pick(lines, seed);
    if (swears(text)) return pick(['속상한 날이었네요. 여기서는 마음껏 털어놔도 돼요.', '그럴 만한 날이었네요. 일기에는 그대로 담아 둘게요.'], seed);
    const r = read(text);
    return r.sure ? pick(reflect[r.mood.id], seed) : pick(plain, seed);
  }
  /** 앱에서 한마디를 담을 때 일어나는 일 그대로: 마음결, 길동무의 답, 길 위에 올라갔는지 */
  function save(text, { share = 'diary', seed = text } = {}) {
    const refused = rough(text);
    if (refused) return { text, refused, mood: null, p: 0, scores: null, reply: null, shared: false, blocked: null };      // 거친 말은 담지 않는다
    const r = read(text), v = share === 'road' ? guard(text) : null;
    // (길 위에 올리지 않은 말에는 마음결을 넘겨짚는 답을 하지 않는다: 담담하게 받았다고만 한다)
    const held = !!v && !v.ok && v.kind !== 'SELF_HARM';
    return { text, refused: null, mood: r.mood, p: r.p, scores: r.scores, reply: held ? '오늘의 한마디, 잘 받았어요.' : reply(text, seed), shared: !!v && v.ok, blocked: v && !v.ok ? v : null };
  }
  const m = Object.fromEntries(meta.trim().split('\n').map(l => l.split('=')));
  return { scores, read, guard, rough, reply, save, harm, meta: m };
}
