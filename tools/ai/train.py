#!/usr/bin/env python3
"""길동무 AI 학습: 마음결 분류 모델(7갈래)과 '남을 해치는 말' 판별 모델을 학습해 앱의 assets/ai/ 에 넣는다.

  python3 tools/ai/train.py            (gildongmu-app 폴더에서)

모델: 글자 1~3개 묶음(n-gram)을 특징으로 하는 로지스틱 회귀(소프트맥스). 기기 안에서 인터넷 없이 돈다.
앱(Kotlin)의 Ai.kt 가 똑같은 방식으로 글을 다듬고 특징을 뽑는다 — 둘 중 하나를 바꾸면 다른 쪽도 바꿀 것.
정확도는 학습에 쓰지 않은 문장(corpus.txt 의 TEST: 갈래)으로 잰다. 결과는 report.txt 에 남긴다.
"""
import os, random, sys
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(APP, "app/src/main/assets/ai")
MOODS = ["JOY", "FLUTTER", "CALM", "WORRY", "TIRED", "SAD", "ANGRY"]
LABEL = {"JOY": "기쁨", "FLUTTER": "설렘", "CALM": "잔잔함", "WORRY": "걱정", "TIRED": "지침", "SAD": "슬픔", "ANGRY": "속상함"}


def norm(s: str) -> str:
    out = []
    for ch in s.lower():
        o = ord(ch)
        ok = 0xAC00 <= o <= 0xD7A3 or 0x3131 <= o <= 0x318E or "a" <= ch <= "z" or "0" <= ch <= "9"
        out.append(ch if ok else " ")
    return " ".join("".join(out).split())


def feats(s: str):
    t = " " + norm(s) + " "
    fs = set()
    for n in (1, 2, 3):
        for i in range(len(t) - n + 1):
            g = t[i:i + n]
            if g.strip():
                fs.add(g)
    return fs


def read_corpus(path):
    data = {}
    cur = None
    for line in open(path, encoding="utf-8"):
        line = line.rstrip("\n")
        if not line.strip():
            continue
        if line.startswith("# "):
            continue
        if line.startswith("#"):
            cur = line[1:].strip(); data.setdefault(cur, []); continue
        data[cur].append(line.strip())
    return data


OPENERS = ["아 ", "하 ", "음 ", "진짜 ", "오늘 ", "와 ", "근데 ", "아니 "]


def augment(sents, rng, k=2):
    out = []
    for s in sents:
        out.append(s)
        for _ in range(k):
            w = s.split()
            if len(w) > 4 and rng.random() < 0.6:
                del w[rng.randrange(1, len(w))]
            out.append(rng.choice(OPENERS) + " ".join(w))
    return out


class Vocab:
    def __init__(self, sents):
        idx = {}
        for s in sents:
            for f in feats(s):
                if f not in idx:
                    idx[f] = len(idx)
        self.idx = idx

    def matrix(self, sents):
        X = np.zeros((len(sents), len(self.idx)), dtype=np.float32)
        for r, s in enumerate(sents):
            fs = feats(s)
            v = 1.0 / np.sqrt(max(1, len(fs)))
            for f in fs:
                j = self.idx.get(f)
                if j is not None:
                    X[r, j] = v
        return X


def train_softmax(X, y, k, lam, steps=700, lr=0.08, weights=None, seed=0):
    rng = np.random.default_rng(seed)
    n, d = X.shape
    W = (rng.standard_normal((d, k)) * 0.001).astype(np.float32)
    b = np.zeros(k, dtype=np.float32)
    Y = np.eye(k, dtype=np.float32)[y]
    sw = np.ones(n, dtype=np.float32) if weights is None else weights.astype(np.float32)
    sw = sw / sw.sum()
    mW = np.zeros_like(W); vW = np.zeros_like(W); mb = np.zeros_like(b); vb = np.zeros_like(b)
    b1, b2, eps = 0.9, 0.999, 1e-8
    for t in range(1, steps + 1):
        Z = X @ W + b
        Z -= Z.max(axis=1, keepdims=True)
        P = np.exp(Z); P /= P.sum(axis=1, keepdims=True)
        G = (P - Y) * sw[:, None]
        gW = X.T @ G + lam * W
        gb = G.sum(axis=0)
        mW = b1 * mW + (1 - b1) * gW; vW = b2 * vW + (1 - b2) * gW * gW
        mb = b1 * mb + (1 - b1) * gb; vb = b2 * vb + (1 - b2) * gb * gb
        W -= lr * (mW / (1 - b1 ** t)) / (np.sqrt(vW / (1 - b2 ** t)) + eps)
        b -= lr * (mb / (1 - b1 ** t)) / (np.sqrt(vb / (1 - b2 ** t)) + eps)
    return W, b


def predict(W, b, X):
    Z = X @ W + b
    Z -= Z.max(axis=1, keepdims=True)
    P = np.exp(Z); P /= P.sum(axis=1, keepdims=True)
    return P


def mood_fit(sents, labels, lam, seed=0):
    rng = random.Random(seed)
    aug_s, aug_y = [], []
    for s, y in zip(sents, labels):
        for a in augment([s], rng):
            aug_s.append(a); aug_y.append(y)
    voc = Vocab(aug_s)
    X = voc.matrix(aug_s)
    W, b = train_softmax(X, np.array(aug_y), len(MOODS), lam, seed=seed)
    return voc, W, b


def cv_mood(sents, labels, lam, folds=5, seed=1):
    rng = random.Random(seed)
    order = list(range(len(sents))); rng.shuffle(order)
    right = 0
    for f in range(folds):
        test = sorted(order[f::folds])
        held = set(test)
        tr = [i for i in range(len(sents)) if i not in held]
        voc, W, b = mood_fit([sents[i] for i in tr], [labels[i] for i in tr], lam, seed=f)
        P = predict(W, b, voc.matrix([sents[i] for i in test]))
        right += sum(int(P[r].argmax() == labels[i]) for r, i in enumerate(test))
    return right / len(sents)


def export(path, voc, W, b, classes, prune=0.004):
    inv = {j: f for f, j in voc.idx.items()}
    kept = 0
    with open(path, "w", encoding="utf-8") as fp:
        fp.write("#classes\t" + " ".join(classes) + "\n")
        fp.write("#bias\t" + " ".join(f"{x:.4f}" for x in b) + "\n")
        for j in range(W.shape[0]):
            row = W[j]
            if np.abs(row).max() < prune:
                continue
            g = inv[j].replace(" ", "_")          # 칸은 _ 로 적는다 (앱에서 되돌린다)
            fp.write(g + "\t" + " ".join(f"{x:.4f}" for x in row) + "\n")
            kept += 1
    return kept


def main():
    data = read_corpus(os.path.join(HERE, "corpus.txt"))
    sents, labels = [], []
    for k, name in enumerate(MOODS):
        for s in data[name]:
            sents.append(s); labels.append(k)
    rep = []
    say = lambda s="": (print(s), rep.append(s))

    # 1) 마음결: 규제 세기를 교차 검증으로 고른다
    best = None
    for lam in (3e-5, 1e-4, 3e-4, 1e-3):
        acc = cv_mood(sents, labels, lam)
        say(f"마음결 5겹 교차 검증  lam={lam:g}  정확도 {acc:.3f}")
        if best is None or acc > best[1]:
            best = (lam, acc)
    lam = best[0]
    voc, W, b = mood_fit(sents, labels, lam, seed=7)
    say(f"→ lam={lam:g}, 학습 문장 {len(sents)}개 (갈래마다 {', '.join(f'{LABEL[m]} {len(data[m])}' for m in MOODS)})")

    # 학습에 쓰지 않은 문장으로 잰다
    tests = [(s, k) for k, m in enumerate(MOODS) for s in data.get("TEST:" + m, [])]
    P = predict(W, b, voc.matrix([s for s, _ in tests]))
    right = 0
    say("\n[따로 둔 문장으로 잰 마음결]")
    for (s, k), p in zip(tests, P):
        g = int(p.argmax()); ok = g == k; right += ok
        say(f"{'O' if ok else 'X'}  {LABEL[MOODS[k]]:>3} → {LABEL[MOODS[g]]:<3} {p[g]:.2f}  {s}")
    test_acc = right / len(tests)
    say(f"→ 따로 둔 {len(tests)}문장 가운데 {right}문장 맞힘 ({test_acc * 100:.0f}%)")

    # 시연에 쓰는 문장들
    say("\n[시연 문장]")
    demo = [
        "오늘 정말 하루가 힘들었어 팀장한테 깨지고 야근도 하고 힘들다",
        "아 피곤해 오늘 회의만 내보냈네 점심도 김밥 1줄로 때우고 집 가서 라면이나 끓여 먹어야지",
        "아 그리고네 오늘 하루 종일 매일만 했네 점심도 비빔밥 한식으로 때우고 집가서 라면이나 음료 먹어야지",
        "오늘 모두 야근 고생하셨고 집 가서 행복하게 사랑하는 사람과 마무리하세요",
        "그 사람 진짜 쓰레기 같아",
        "아 진짜 씨1발 오늘 왜 이래",
        "노을 진짜 죽여준다",
    ]
    Pd = predict(W, b, voc.matrix(demo))
    for s, p in zip(demo, Pd):
        g = int(p.argmax())
        say(f"   {LABEL[MOODS[g]]} {p[g]:.2f}  {s}")

    # 2) 남을 해치는 말: 해치는 말(HARM) 대 나머지 모든 문장
    pos = data["HARM"]; neg = sents
    hs = pos + neg; hy = [1] * len(pos) + [0] * len(neg)
    rng = random.Random(3)
    aug_s, aug_y = [], []
    for s, y in zip(hs, hy):
        for a in augment([s], rng):
            aug_s.append(a); aug_y.append(y)
    hvoc = Vocab(aug_s)
    hX = hvoc.matrix(aug_s)
    wts = np.array([len(neg) / len(pos) if y else 1.0 for y in aug_y])
    hW, hb = train_softmax(hX, np.array(aug_y), 2, 3e-4, weights=wts, seed=5)
    th = 0.80
    say(f"\n[남을 해치는 말 판별]  막는 기준: 해칠 확률 {th:.2f} 이상")
    ht = [(s, 1) for s in data["TEST:HARM"]] + [(s, 0) for s in data["TEST:SAFE"]] + [(s, 0) for s, _ in tests]
    Ph = predict(hW, hb, hvoc.matrix([s for s, _ in ht]))
    tp = fp = fn = 0
    for (s, y), p in zip(ht, Ph):
        hit = p[1] >= th
        if y and hit: tp += 1
        if y and not hit: fn += 1
        if not y and hit: fp += 1
        if y or hit or "죽" in s:
            say(f"{'O' if hit == bool(y) else 'X'}  {'해침' if y else '괜찮음'} → {'막음' if hit else '통과'} {p[1]:.2f}  {s}")
    say(f"→ 해치는 말 {tp + fn}문장 가운데 {tp}문장 막음, 괜찮은 말 {len(ht) - tp - fn}문장 가운데 {fp}문장 잘못 막음")

    os.makedirs(OUT, exist_ok=True)
    k1 = export(os.path.join(OUT, "mood.tsv"), voc, W, b, MOODS)
    k2 = export(os.path.join(OUT, "harm.tsv"), hvoc, hW, hb, ["OK", "HARM"])
    with open(os.path.join(OUT, "meta.txt"), "w", encoding="utf-8") as mf:
        mf.write(f"mood_train={len(sents)}\nmood_test={len(tests)}\nmood_test_acc={test_acc:.3f}\nmood_cv_acc={best[1]:.3f}\n")
        mf.write(f"harm_train_pos={len(pos)}\nharm_test_pos={tp + fn}\nharm_test_hit={tp}\nharm_test_neg={len(ht) - tp - fn}\nharm_test_fp={fp}\nharm_threshold={th}\n")
    say(f"\n내보냄: mood.tsv 특징 {k1}개, harm.tsv 특징 {k2}개 → {os.path.relpath(OUT, APP)}")
    open(os.path.join(HERE, "report.txt"), "w", encoding="utf-8").write("\n".join(rep) + "\n")


if __name__ == "__main__":
    main()
