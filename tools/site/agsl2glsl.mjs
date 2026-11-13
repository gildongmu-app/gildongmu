#!/usr/bin/env node
// 길동무 — 앱의 살아 있는 바탕(AGSL)을 웹의 셰이더(GLSL ES 1.00 · WebGL 1)로 옮긴다.
//
// 앱은 assets/scenes/_lib.agsl 과 <장면>.agsl 을 이어 붙여 셰이더 한 편을 만든다 (Scene.kt 의 SceneSrc).
// 여기서도 똑같이 이어 붙인 뒤, AGSL 에만 있는 것을 GLSL 로 바꿔 docs/assets/scenes/<장면>.frag 로 쓴다.
// 장면의 식은 한 글자도 바꾸지 않는다 (같은 그림이 나와야 하므로). 바뀌는 것은 자료형의 이름과 들머리뿐이다.
// 만들어진 .frag 는 손으로 고치지 않는다. 고칠 것이 생기면 아래 FIXES 에 까닭과 함께 적는다.
//
// 쓰는 법 (어느 폴더에서 불러도 된다):
//   node tools/site/agsl2glsl.mjs              열 장면을 모두 다시 만든다
//   node tools/site/agsl2glsl.mjs city rain    고른 장면만
//   node tools/site/agsl2glsl.mjs --check      만들어 둔 파일이 지금의 원본과 맞는지만 본다 (다르면 1 로 끝난다)
//
// 실제로 브라우저에서 만들어지는지는 docs/dev/scene.html?check=all 로 본다.

import { readFileSync, writeFileSync, mkdirSync, readdirSync, existsSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', '..');
const SRC = join(ROOT, 'app', 'src', 'main', 'assets', 'scenes');
const OUT = join(ROOT, 'docs', 'assets', 'scenes');

// 손으로 고치는 곳. { scene: '장면 이름' 또는 '*', why: '까닭', from: 정규식, to: '바꿀 글' }
// 지금의 열 장면은 고칠 것이 없다 (AGSL 의 약속대로 쓰여 있어서 GLSL ES 1.00 의 약속도 그대로 지킨다).
const FIXES = [];

// AGSL 의 자료형 이름 → GLSL
function typeName(word, base, n, m) {
    if (word === 'half') return 'float';
    if (m) {
        if (m !== n) throw new Error(`GLSL ES 1.00 에는 정사각이 아닌 행렬이 없어요: ${word}`);
        if (base === 'int' || base === 'bool') throw new Error(`알 수 없는 자료형: ${word}`);
        return 'mat' + n;
    }
    return { half: 'vec', float: 'vec', int: 'ivec', bool: 'bvec' }[base] + n;
}

// 주석을 걷어 낸다 (줄 수는 그대로 둔다: 오류가 나면 원본의 줄을 찾기 쉽게)
function stripComments(s) {
    return s
        .replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ''))
        .replace(/\/\/[^\n]*/g, '')
        .replace(/[ \t]+$/gm, '');
}

// WebGL 1 이 받아 주지 않는 것이 남아 있으면 여기서 멈춘다 (브라우저까지 가서야 알게 되지 않도록)
const FORBID = [
    [/\b(while|do|switch|struct|inout|out|discard)\b/, 'GLSL ES 1.00 에서 쓸 수 없거나 장면에서 쓰지 않기로 한 낱말'],
    [/#/, '전처리문'],
    [/\[/, '배열'],
    [/%|<<|>>|\^|~|(?<![&])&(?![&])|(?<![|])\|(?![|])/, '정수 나머지·비트 연산'],
    [/\b(round|trunc|roundEven|tanh|sinh|cosh|asinh|acosh|atanh|isnan|isinf|modf|inverse|transpose|determinant|outerProduct)\s*\(/, 'GLSL ES 1.00 에 없는 함수'],
    [/\b(saturate|unpremul|toLinearSrgb|fromLinearSrgb)\s*\(|\.eval\s*\(/, 'AGSL 에만 있는 함수'],
    [/\b(short|ushort|uint)[234]?\b/, 'GLSL ES 1.00 에 없는 자료형'],
    [/\b(gl_\w*|webgl_\w*|_webgl_\w*|\w*__\w*)\b/, 'GLSL·WebGL 이 맡아 둔 이름'],
    [/\b(sceneMain|uDim|uVeil|uFade)\b/, '웹의 들머리가 쓰는 이름'],
    [/\b(attribute|varying|sampler2D|samplerCube|lowp|mediump|highp|precision|invariant|asm|class|union|enum|typedef|template|this|packed|goto|inline|noinline|volatile|public|static|extern|external|interface|flat|long|double|fixed|unsigned|superp|input|output|hvec[234]|dvec[234]|fvec[234]|sampler\w*|sizeof|cast|namespace|using|default)\b/, 'GLSL ES 1.00 이 맡아 둔 낱말'],
];

function lint(id, body) {
    const lines = body.split('\n');
    for (const [re, what] of FORBID) {
        for (let n = 0; n < lines.length; n++) {
            const m = re.exec(lines[n]);
            if (m) throw new Error(`${id}: ${what} (${m[0]}) — 이어 붙인 원본의 ${n + 1}번째 줄: ${lines[n].trim().slice(0, 120)}`);
        }
    }
    // 반복문은 횟수가 상수인 꼴만 (WebGL 1 은 이것만 받는다)
    const loops = body.match(/\bfor\s*\([^)]*\)/g) || [];
    for (const l of loops) {
        if (!/^for\s*\(\s*int\s+(\w+)\s*=\s*\d+\s*;\s*\1\s*<=?\s*\d+\s*;\s*\1\s*(\+\+|\+=\s*\d+)\s*\)$/.test(l)) throw new Error(`${id}: 횟수가 상수가 아닌 반복문: ${l}`);
    }
}

// 내려받는 크기를 줄인다: 빈 줄과 들여쓰기, 없어도 되는 빈칸을 걷어 낸다 (낱말의 차례는 그대로다).
// 빈칸을 남기는 곳은 둘뿐이다: 낱말과 낱말 사이, 그리고 붙이면 다른 뜻이 되는 부호 사이 (a - -b 를 a--b 로 만들면 안 된다).
function squeeze(body) {
    return body.split('\n').map((line) => {
        const l = line.trim().replace(/\s+/g, ' ');
        return l.replace(/ /g, (sp, i) => {
            const a = l[i - 1], b = l[i + 1];
            if (/\w/.test(a) && /\w/.test(b)) return ' ';
            if ((a === '+' || a === '-') && (b === '+' || b === '-')) return ' ';
            return '';
        });
    }).filter((l) => l !== '').join('\n');
}

const PRELUDE = [
    '#ifdef GL_FRAGMENT_PRECISION_HIGH',
    'precision highp float;',
    '#else',
    'precision mediump float;',
    '#endif',
].join('\n');

// 들머리. 앱에서는 Scene.kt 가 장면 위에 덧그리던 것(비 오는 날의 푸른 막, 어둡게 가라앉히는 검은 막)을 여기서 함께 셈한다.
// AGSL 의 좌표는 왼쪽 위가 (0,0) 이고 GL 은 왼쪽 아래가 (0,0) 이라, 세로를 뒤집어 넘긴다.
const ENTRY = [
    'uniform float uDim;',
    'uniform vec4 uVeil;',
    'uniform float uFade;',
    'void main() {',
    '    vec4 c = sceneMain(vec2(gl_FragCoord.x, uSize.y - gl_FragCoord.y));',
    '    gl_FragColor = vec4(mix(c.rgb, uVeil.rgb, uVeil.a) * (1.0 - uDim), uFade);',
    '}',
].join('\n');

function convert(id, lib, scene) {
    // 앱과 같은 차례로 잇는다
    let body = stripComments(lib + '\n' + scene);
    for (const f of FIXES) if (f.scene === '*' || f.scene === id) body = body.replace(f.from, f.to);
    lint(id, body);
    body = body.replace(/\blayout\s*\(\s*color\s*\)\s*/g, '');
    body = body.replace(/\b(half|float|int|bool)([234])(?:x([234]))?\b|\bhalf\b/g, typeName);
    // 장면의 들머리: half4 main(float2 xy) → vec4 sceneMain(vec2 xy)
    const entry = /\bvec4\s+main\s*\(\s*vec2\s+\w+\s*\)/;
    if (!entry.test(body)) throw new Error(`${id}: 'half4 main(float2 xy)' 를 찾지 못했어요`);
    body = body.replace(/\bvec4\s+main\s*\(/, 'vec4 sceneMain(');
    if (/\bmain\s*\(/.test(body)) throw new Error(`${id}: main 이 둘 이상이에요`);
    body = squeeze(body);
    const head = `// ${id} — tools/site/agsl2glsl.mjs 가 app/src/main/assets/scenes/_lib.agsl + ${id}.agsl 에서 만든 파일. 손으로 고치지 말 것.`;
    return [head, PRELUDE, body, ENTRY, ''].join('\n');
}

function main() {
    const args = process.argv.slice(2);
    const check = args.includes('--check');
    const picked = args.filter((a) => !a.startsWith('--'));
    const all = readdirSync(SRC).filter((f) => f.endsWith('.agsl') && !f.startsWith('_')).map((f) => f.slice(0, -5)).sort();
    const ids = picked.length ? picked : all;
    const lib = readFileSync(join(SRC, '_lib.agsl'), 'utf8');
    if (!check) mkdirSync(OUT, { recursive: true });
    let stale = 0;
    for (const id of ids) {
        if (!all.includes(id)) { console.error(`없는 장면: ${id}`); process.exitCode = 1; continue; }
        const text = convert(id, lib, readFileSync(join(SRC, id + '.agsl'), 'utf8'));
        const file = join(OUT, id + '.frag');
        const rel = relative(ROOT, file);
        if (check) {
            const same = existsSync(file) && readFileSync(file, 'utf8') === text;
            if (!same) stale++;
            console.log(`${same ? '같음' : '다름'}  ${rel}`);
        } else {
            writeFileSync(file, text);
            console.log(`${rel}  ${(text.length / 1024).toFixed(1)} KB`);
        }
    }
    if (check && stale) { console.error(`${stale}개가 원본과 달라요. 'node tools/site/agsl2glsl.mjs' 로 다시 만드세요.`); process.exitCode = 1; }
}

main();
