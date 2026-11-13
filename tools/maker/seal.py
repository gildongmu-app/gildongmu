#!/usr/bin/env python3
"""봉인된 제작자 서명 만들기·열기 (앱의 Maker.kt, 웹사이트의 site.js 와 같은 방식).

  python3 tools/maker/seal.py make "<서명 글>" "<비밀번호>"     → gd1:… (이 글자열을 Maker.kt 와 site.js 에 넣는다)
  python3 tools/maker/seal.py open "<gd1:…>" "<비밀번호>"       → 서명 글

방식: 비밀번호에서 PBKDF2-HMAC-SHA256(12만 번)으로 열쇠 둘을 뽑아, 하나로는 글을 가리고(HMAC 으로 만든 흐름과 XOR),
하나로는 꼬리표(HMAC)를 붙인다. 비밀번호 없이는 읽을 수도, 다른 글로 바꿔 넣을 수도 없다. 비밀번호는 저장소에 두지 않는다.
"""
import base64, hashlib, hmac, os, sys
ROUNDS = 120_000
def keys(pw, salt):
    k = hashlib.pbkdf2_hmac("sha256", pw.strip().encode("utf-8"), salt, ROUNDS, 64); return k[:32], k[32:]
def stream(enc, salt, n):
    out = b""; j = 0
    while len(out) < n: out += hmac.new(enc, salt + j.to_bytes(4, "big"), hashlib.sha256).digest(); j += 1
    return out[:n]
def make(text, pw):
    salt = os.urandom(16); enc, mac = keys(pw, salt); pt = text.encode("utf-8")
    ct = bytes(a ^ b for a, b in zip(pt, stream(enc, salt, len(pt))))
    return "gd1:" + base64.b64encode(salt + ct + hmac.new(mac, salt + ct, hashlib.sha256).digest()).decode()
def open_(seal, pw):
    raw = base64.b64decode(seal[4:]); salt, ct, tag = raw[:16], raw[16:-32], raw[-32:]
    enc, mac = keys(pw, salt)
    if not hmac.compare_digest(hmac.new(mac, salt + ct, hashlib.sha256).digest(), tag): return None
    return bytes(a ^ b for a, b in zip(ct, stream(enc, salt, len(ct)))).decode("utf-8")
if __name__ == "__main__":
    cmd, a, b = sys.argv[1:4]
    print(make(a, b) if cmd == "make" else (open_(a, b) or "(비밀번호가 맞지 않습니다)"))
