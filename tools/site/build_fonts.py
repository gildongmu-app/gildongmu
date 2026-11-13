#!/usr/bin/env python3
"""웹사이트용 글꼴 만들기: 앱의 글꼴(마루 부리, Pretendard)에서 사이트에 쓰인 글자만 뽑아 woff2 로.

  python3 -m venv .venv && .venv/bin/pip install fonttools brotli
  .venv/bin/python tools/site/build_fonts.py        (gildongmu-app 폴더에서)

- docs 의 html·js 에 나오는 글자만 담는다 (페이지의 글을 고치면 다시 돌린다).
- 방문자가 직접 적는 글(해 보기)에 쓰는 명조 400 은, 자주 쓰는 한글 2,350자를 따로 한 파일(…x.woff2)에 담아 필요할 때만 받게 한다.
- 두 글꼴 모두 SIL OFL 1.1. 일부 글자만 담은 수정본이라, 원래 이름 대신 'GD Serif', 'GD Sans' 라는 이름으로 싣는다.
"""
import glob, os, re
from fontTools import subset
from fontTools.ttLib import TTFont

APP = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC, OUT = APP + "/app/src/main/res/font/", APP + "/docs/assets/fonts/"
FACES = [("maruburi_regular.ttf", "gd-serif-400", "GD Serif"), ("maruburi_semibold.ttf", "gd-serif-600", "GD Serif"), ("maruburi_bold.ttf", "gd-serif-700", "GD Serif"),
         ("pretendard_light.otf", "gd-sans-300", "GD Sans"), ("pretendard_regular.otf", "gd-sans-400", "GD Sans"),
         ("pretendard_medium.otf", "gd-sans-500", "GD Sans"), ("pretendard_semibold.otf", "gd-sans-600", "GD Sans")]

text = "".join(open(f, encoding="utf-8").read() for f in glob.glob(APP + "/docs/*.html") + glob.glob(APP + "/docs/assets/js/*.js") + glob.glob(APP + "/docs/assets/media/*.json"))
core = {c for c in text if ord(c) > 0x20} | set(map(chr, range(0x20, 0x7F))) | set("“”‘’·…—–※→←↑↓×°「」〔〕")
ksx = {bytes([a, b]).decode("euc-kr") for a in range(0xB0, 0xC9) for b in range(0xA1, 0xFF)} | set(map(chr, range(0x3131, 0x3164)))

def ranges(chars):
    cps, out, i = sorted(map(ord, chars)), [], 0
    while i < len(cps):
        j = i
        while j + 1 < len(cps) and cps[j + 1] == cps[j] + 1: j += 1
        out.append(f"U+{cps[i]:X}" if i == j else f"U+{cps[i]:X}-{cps[j]:X}"); i = j + 1
    return ",".join(out)

def make(src, name, family, chars):
    opt = subset.Options(); opt.flavor = "woff2"; opt.layout_features = ["*"]; opt.hinting = False; opt.desubroutinize = True; opt.name_IDs = [1, 2, 4, 6]; opt.notdef_outline = True
    f = TTFont(SRC + src); s = subset.Subsetter(opt); s.populate(unicodes=[ord(c) for c in chars]); s.subset(f)
    for rec in f["name"].names:      # 수정본이므로 원래 이름을 쓰지 않는다
        if rec.nameID in (1, 4): rec.string = family
        elif rec.nameID == 6: rec.string = family.replace(" ", "")
    f.flavor = "woff2"; f.save(OUT + name + ".woff2"); return os.path.getsize(OUT + name + ".woff2")

os.makedirs(OUT, exist_ok=True); total = 0
for src, name, family in FACES:
    n = make(src, name, family, core); total += n; print(f"{name}.woff2  {n / 1024:.0f} KB  ({len(core)}자)")
n = make("maruburi_regular.ttf", "gd-serif-400x", "GD Serif", ksx - core); total += n; print(f"gd-serif-400x.woff2  {n / 1024:.0f} KB  ({len(ksx - core)}자, 필요할 때만)")
open(OUT + "ranges.css", "w").write("/* build_fonts.py 가 만든다: 직접 적는 글에 쓰는 한글 (명조 400) */\n"
    f'@font-face {{ font-family: "GD Serif"; src: url(gd-serif-400x.woff2) format("woff2"); font-weight: 400; font-display: swap; unicode-range: {ranges(ksx - core)}; }}\n')
for lic in ("OFL-MaruBuri.txt", "OFL-Pretendard.txt"): open(OUT + lic, "wb").write(open(APP + "/app/src/main/assets/" + lic, "rb").read())
print(f"모두 {total / 1024:.0f} KB")
