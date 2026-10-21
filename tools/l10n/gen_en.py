#!/usr/bin/env python3
"""안드로이드 영어 표 만들기.

아이폰 Localizable.xcstrings(한국어 원문 = 키, en 번역)에서 안드로이드 코틀린 글자가 실제로 쓰는 것만 골라
android-app/app/src/main/assets/l10n_en.json 으로 쓴다. 런타임(core/L10n.kt)이 한 번 읽어 한국어 → 영어로 바꾼다.

출력 json:
  {"x": {한국어: 영어}            자리(%@ 등) 없는 글 — 그대로 찾기
   "p": [[조각들, 영어틀], ...]    자리 있는 글 — 조각 사이 값을 잡아 영어틀 {0} {1} 에 넣는다
   "f": [한국어 조각, ...]         "a" + x + "b" 처럼 이어 붙인 조각 — 통째로 못 찾으면 조각을 바꾼다 (x 표 안의 키)}

알림:
  빠진 키   — 화면에 보일 한국어 글자인데 xcstrings 에 en 이 없는 것 (A~H 단계 파일)
  진행 중   — I/J/K/L 단계 파일의 같은 글자 수 (나중에 다시)
  제외      — 로그·저장 키·정규식·개발자용 등 (ignore.txt 와 규칙)

쓰기: python3 tools/l10n/gen_en.py [-v]   (-v: 빠진 키·진행 중 목록을 모두 찍는다)
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
XC = os.path.join(ROOT, "ios-app/Naeilo/Localizable.xcstrings")
SRC = os.path.join(ROOT, "android-app/app/src/main/java/com/naeilo/app")
OUT = os.path.join(ROOT, "android-app/app/src/main/assets/l10n_en.json")
IGNORE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "ignore.txt")
# 아이폰에 같은 키가 없는 안드로이드 전용 문구(아이폰 → 폰 등)의 영어. xcstrings 보다 뒤 순위
EXTRA = os.path.join(os.path.dirname(os.path.abspath(__file__)), "android_en.json")

# 아직 진행 중인 단계(I/J/K/L) 파일 — 빠진 키로 세지 않고 따로 센다
IN_PROGRESS = ("ui/shelter/", "ui/town/", "ui/calm/", "ui/CharMotion.kt", "data/Story.kt", "data/Lines.kt",
               "ui/game/", "core/game/", "ui/wave/", "data/Notifier.kt", "data/Support.kt", "ui/support/", "pending/")

HANGUL = re.compile(r"[가-힣ㄱ-ㅎㅏ-ㅣ]")
SLOT = "\x00"
# printf 자리: %@ %lld %d %.1f %1$@ ... (%% 는 따로)
SPEC = re.compile(r"%(?:(\d+)\$)?[-+ 0#']*\d*(?:\.\d+)?(?:ll|l|h|hh|q|z|t|j|L)?([@dDiuUxXoOfFeEgGcCsSaA])")


# ---------- 코틀린 글자 뽑기 ----------
class Lit:
    __slots__ = ("file", "line", "parts", "before", "after", "nested")

    def __init__(self, file, line):
        self.file, self.line, self.parts, self.before, self.after, self.nested = file, line, [], "", "", False

    def template(self):
        """조각과 자리를 하나로: 자리는 SLOT"""
        return "".join(p if isinstance(p, str) else SLOT for p in self.parts)


def scan(path, rel):
    s = open(path, encoding="utf-8").read()
    out = []
    n = len(s)

    def line_of(i):
        return s.count("\n", 0, i) + 1

    def skip_ws_back(i):
        while i >= 0 and s[i] in " \t\r\n":
            i -= 1
        return i

    def read_string(i, depth):
        """s[i] 가 따옴표. 끝 다음 위치를 돌려준다"""
        raw = s.startswith('"""', i)
        q = '"""' if raw else '"'
        lit = Lit(rel, line_of(i))
        lit.nested = depth > 0
        j = i + len(q)
        buf = []
        while j < n:
            if s.startswith(q, j):
                if raw:  # """ 뒤 따옴표가 더 있으면 글자
                    while s.startswith('""""', j):
                        buf.append('"'); j += 1
                j += len(q)
                break
            c = s[j]
            if not raw and c == "\\":
                e = s[j + 1]
                buf.append({"n": "\n", "t": "\t", "r": "\r", "\"": "\"", "'": "'", "\\": "\\", "$": "$", "b": "\b"}.get(e, e))
                if e == "u":
                    buf[-1] = chr(int(s[j + 2:j + 6], 16)); j += 6
                else:
                    j += 2
                continue
            if c == "$" and j + 1 < n and s[j + 1] == "{":
                if buf: lit.parts.append("".join(buf)); buf = []
                j = read_expr(j + 2, depth + 1)
                lit.parts.append(None)
                continue
            if c == "$" and j + 1 < n and (s[j + 1].isalpha() or s[j + 1] == "_"):
                if buf: lit.parts.append("".join(buf)); buf = []
                j += 1
                while j < n and (s[j].isalnum() or s[j] == "_"):
                    j += 1
                lit.parts.append(None)
                continue
            buf.append(c); j += 1
        if buf: lit.parts.append("".join(buf))
        b = skip_ws_back(i - 1)
        lit.before = s[max(0, b - 119):b + 1]
        a = j
        while a < n and s[a] in " \t\r\n": a += 1
        lit.after = s[a:a + 60]
        out.append(lit)
        return j

    def read_expr(j, depth):
        """${ 다음부터 짝 맞는 } 다음까지"""
        lv = 1
        while j < n:
            c = s[j]
            if c == '"':
                j = read_string(j, depth); continue
            if c == "'":
                j = skip_char(j); continue
            if c == "{": lv += 1
            elif c == "}":
                lv -= 1
                if lv == 0: return j + 1
            j += 1
        return j

    def skip_char(j):
        k = j + 1
        if k < n and s[k] == "\\": k += 2
        else: k += 1
        while k < n and s[k] != "'" and k - j < 10: k += 1
        return k + 1

    i = 0
    while i < n:
        c = s[i]
        if s.startswith("//", i):
            e = s.find("\n", i); i = n if e < 0 else e; continue
        if s.startswith("/*", i):
            lv, i = 1, i + 2
            while i < n and lv:
                if s.startswith("/*", i): lv += 1; i += 2
                elif s.startswith("*/", i): lv -= 1; i += 2
                else: i += 1
            continue
        if c == '"':
            i = read_string(i, 0); continue
        if c == "'":
            i = skip_char(i); continue
        i += 1
    return out


# ---------- xcstrings ----------
def key_template(k):
    """키를 (틀, 자리별 인자 번호) 로. 자리는 SLOT"""
    idx, seq, out, pos = [], 0, [], 0
    for m in re.finditer(r"%%|" + SPEC.pattern, k):
        out.append(k[pos:m.start()]); pos = m.end()
        if m.group(0) == "%%":
            out.append("%"); continue
        if m.group(1): idx.append(int(m.group(1)) - 1)
        else: idx.append(seq); seq += 1
        out.append(SLOT)
    out.append(k[pos:])
    return "".join(out), idx


def en_template(v, nargs):
    """영어 값을 {인자번호} 틀로"""
    seq, out, pos = 0, [], 0
    for m in re.finditer(r"%%|" + SPEC.pattern, v):
        out.append(v[pos:m.start()].replace("{", "{{").replace("}", "}}")); pos = m.end()
        if m.group(0) == "%%":
            out.append("%"); continue
        if m.group(1): a = int(m.group(1)) - 1
        else: a = seq; seq += 1
        out.append("{%d}" % a)
    out.append(v[pos:].replace("{", "{{").replace("}", "}}"))
    return "".join(out)


def lit_template(t):
    """코틀린 글자의 printf 자리(cformat 등)도 SLOT 으로, %% 는 %"""
    t = re.sub(r"%%|" + SPEC.pattern, lambda m: "%" if m.group(0) == "%%" else SLOT, t)
    return t


# ---------- 화면 글자가 아닌 것 ----------
NON_UI_BEFORE = re.compile(
    r"(Log\.[dwiev]|println|getString|putString|getBoolean|putBoolean|getInt|putInt|getLong|putLong|getFloat|putFloat|"
    r"getStringSet|putStringSet|remove|contains|Regex|toRegex|split|startsWith|endsWith|indexOf|replace|matches|"
    r"SerialName|JsonNames|optString|getJSONObject|getJSONArray|has|jsonObject|Pref\w*|rememberPref\w*|"
    r"assets\.open|openFileInput|openFileOutput|File|Uri\.parse|Intent|putExtra|getStringExtra|key\s*=)\s*\(?\s*$")


def load_ignore():
    """ignore.txt: '## 분류' 머리 아래 줄마다 하나. file:경로(/ 로 끝나면 폴더, 경로:줄), re:정규식, 아니면 글자 그대로(%@ = 자리)"""
    pats, cat = [], "기타"
    if os.path.exists(IGNORE):
        for ln in open(IGNORE, encoding="utf-8"):
            ln = ln.rstrip("\n")
            if ln.startswith("## "):
                cat = ln[3:].strip(); continue
            if not ln.strip() or ln.lstrip().startswith("#"):
                continue
            pats.append((cat, ln))
    return pats


def ignored(lit, tmpl, pats):
    for cat, p in pats:
        if p.startswith("file:"):
            f = p[5:].strip()
            if lit.file == f or (f.endswith("/") and lit.file.startswith(f)) or lit.file == f.split(":")[0] and ":" in f and str(lit.line) == f.split(":")[1]:
                return cat
        elif p.startswith("re:"):
            if re.search(p[3:].strip(), tmpl.replace(SLOT, "%@")):
                return cat
        elif tmpl.replace(SLOT, "%@") == p:
            return cat
    return None


def main():
    verbose = "-v" in sys.argv
    xc = json.load(open(XC, encoding="utf-8"))["strings"]
    keys = {}   # 틀 → (키, 인자번호, 영어틀)
    for k, v in xc.items():
        en = v.get("localizations", {}).get("en", {}).get("stringUnit", {}).get("value")
        if en is None:
            continue
        t, idx = key_template(k)
        if t in keys and SLOT not in t:
            continue
        keys[t] = (k, idx, en_template(en, len(idx)) if idx else en.replace("%%", "%") if "%%" in en and "%" in k else en)

    if os.path.exists(EXTRA):
        for k, en in json.load(open(EXTRA, encoding="utf-8")).items():
            t, idx = key_template(k)
            if t not in keys:
                keys[t] = (k, idx, en_template(en, len(idx)) if idx else en)
    pats = load_ignore()
    lits = []
    for dp, _, fs in os.walk(SRC):
        for f in sorted(fs):
            if f.endswith(".kt"):
                p = os.path.join(dp, f)
                lits += scan(p, os.path.relpath(p, SRC))

    used = {}          # 키 틀 → 들어 있는 항목
    frags = set()      # 이어 붙인 조각
    missing, progress, excluded = [], [], {}
    for L in lits:
        raw = L.template()
        if not HANGUL.search(raw):
            continue
        t = lit_template(raw)
        inprog = L.file.startswith(IN_PROGRESS)
        cat = ("위젯 문구(WL.t, lines_en.json)" if re.search(r"WL\.t\(\s*$", L.before) else
               "코드용(저장 키·정규식 등)" if NON_UI_BEFORE.search(L.before) else None)
        if cat:
            excluded.setdefault(cat, []).append(L)
            continue
        hit = keys.get(t) or keys.get(raw)
        if hit is None:
            # 앞뒤 공백만 다르면 공백을 살린 채 쓴다 (조각 바꾸기가 받는다)
            hit = keys.get(t.strip())
        if hit is not None:
            used[t if t in keys else (raw if raw in keys else t.strip())] = hit
            # 이어 붙이는 자리: + 옆, ${} 안, if/else·when 갈래, 앞뒤 빈칸·문장부호로 시작
            concat = (L.before.endswith("+") or L.after.startswith("+")
                      or re.search(r"(\belse|\bif\s*\(.*\)|->|\?:)\s*$", L.before) is not None)
            if concat or L.nested or (SLOT not in t and (t != t.strip() or t[:1] in ",.·:)(")):
                if SLOT not in t and len(t.strip()) >= 2:
                    frags.add(t if t in keys else t.strip())
            continue
        # 자리 하나가 통째로 빠진 경우("a ${x}" 가 iOS 에선 "a " + x) — 조각들이 다 키면 됨
        pieces = [p for p in L.parts if isinstance(p, str) and HANGUL.search(p)]
        if pieces and SLOT in t and all(keys.get(lit_template(p)) or keys.get(lit_template(p).strip()) for p in pieces):
            for p in pieces:
                q = lit_template(p); q = q if q in keys else q.strip()
                used[q] = keys[q]; frags.add(q)
            continue
        # 키가 없을 때만 분류표(ignore.txt)를 본다 — 키가 있으면 위에서 이미 씀
        cat = ignored(L, t, pats)
        if cat:
            excluded.setdefault(cat, []).append(L)
            continue
        (progress if inprog else missing).append(L)

    # 안드로이드 전용 표는 다 쓴다 (코드가 글자를 잘라 만드는 경우: "한은 금리 결정" → "한은 금리")
    if os.path.exists(EXTRA):
        for k in json.load(open(EXTRA, encoding="utf-8")):
            t = key_template(k)[0]
            used.setdefault(t, keys[t])
    x, pl = {}, []
    for t, (k, idx, en) in sorted(used.items()):
        if SLOT in t and not HANGUL.search(t):
            continue   # 한국어 없는 틀(%@ 만)은 바꿀 것이 없다
        if SLOT not in t:
            x[t] = en
        else:
            # 자리 순서대로 잡힌 값 → 인자 번호로 바꿔 둔다
            def remap(m):
                if m.group(0) in ("{{", "}}"):
                    return m.group(0)
                a = int(m.group(1))
                return "{%d}" % idx.index(a) if a in idx else m.group(0)
            pl.append([t.split(SLOT), re.sub(r"\{\{|\}\}|\{(\d+)\}", remap, en)])
    # 조각은 긴 것부터 (짧은 조각이 긴 조각 속을 먼저 바꾸지 않게)
    # 한글 두 자 미만 조각은 다른 낱말 속을 잘못 바꿀 수 있어 뺀다 ('일' → '3일' 등)
    fr = sorted((z for z in frags if len(HANGUL.findall(z)) >= 2 and z in x), key=lambda z: (-len(z), z))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"x": x, "p": pl, "f": fr}, f, ensure_ascii=False, separators=(",", ":"), sort_keys=False)
        f.write("\n")

    def show(lst, title):
        print(f"{title}: {len(lst)}")
        if verbose or title.startswith("빠진"):
            for L in lst[: (100000 if verbose else 400)]:
                print(f"  {L.file}:{L.line}  {L.template().replace(SLOT, '%@')!r}")

    print(f"쓴 항목: {len(x)} 그대로 + {len(pl)} 자리 + 조각 {len(fr)}  → {os.path.relpath(OUT, ROOT)}")
    print(f"쓰지 않는 키: 0 (쓰는 것만 씀)")
    print(f"제외(빠진 키로 안 셈): {sum(len(v) for v in excluded.values())}")
    for c, v in excluded.items():
        print(f"  - {c}: {len(v)}")
        if verbose:
            for L in v:
                print(f"      {L.file}:{L.line}  {L.template().replace(SLOT, '%@')!r}")
    show(missing, "빠진 키 (A~H)")
    show(progress, "진행 중 단계(I/J/K/L) 글자")


if __name__ == "__main__":
    main()
