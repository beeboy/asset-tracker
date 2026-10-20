#!/usr/bin/env python3
"""아이폰 Codable 형과 안드로이드(kotlinx.serialization) 형이 같은 JSON 을 서로 읽는지 확인한다.

  python3 tools/json_parity/parity.py gen     # Swift 형 선언을 뽑아 예시 값 → Swift 가 인코딩 → fixtures/*.json
  (cd android-app && ./gradlew :app:testReleaseUnitTest)   # Kotlin 이 fixtures 를 읽고 다시 써서 같은지 본다
  python3 tools/json_parity/parity.py check   # Kotlin 이 쓴 JSON 을 Swift 가 다시 읽어 fixtures 와 같은지 본다

Swift 쪽은 앱 파일을 통째로 컴파일하지 않고, 형 선언에서 저장 속성·경우(case)만 뽑아 작은 프로그램을 만든다
(합성된 Codable 은 저장 속성만 보므로 JSON 모양은 같다). 맥의 swiftc 가 있어야 한다.
"""
import json, os, re, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
IOS = ROOT / "ios-app"
FIX = Path(__file__).resolve().parent / "fixtures"
WORK = Path.home() / "Library/Caches/naeilo-android/json_parity"   # 빌드 결과는 저장소 밖 (android-app 과 같은 곳)

# 시험할 형 (Swift 이름 → 선언이 있는 파일). 속성 형으로만 쓰이는 형(Route 등)은 DEPS
TYPES = {
    "WSummary": "WidgetShared/Models.swift", "WFeed": "WidgetShared/Models.swift", "WLive": "WidgetShared/Models.swift",
    "TossKeys.Keys": "WidgetShared/Models.swift", "TossKeys.Token": "WidgetShared/Models.swift",
    "AlpacaKeys.Keys": "WidgetShared/Models.swift", "LongbridgeKeys.Keys": "WidgetShared/Models.swift",
    "TossDaily.File": "WidgetShared/Models.swift", "QuoteFeed.File": "WidgetShared/Models.swift",
    "WReward": "WidgetShared/Reward.swift", "WMe": "WidgetShared/Reward.swift", "WSmall": "WidgetShared/Reward.swift",
    "WStory": "WidgetShared/StoryWidgetData.swift",
    "Progress": "Naeilo/Data/Progress.swift", "DayLog": "Naeilo/Data/AppModel.swift", "Holding": "Naeilo/Data/SampleData.swift",
    "Lot": "Naeilo/Data/Lots.swift", "DayRec": "Naeilo/Data/MyRecord.swift", "MeChar": "Naeilo/Data/Me.swift",
}
DEPS = {"WeekPick": "Naeilo/Data/AppModel.swift", "Lens": "Naeilo/Data/AppModel.swift",
        "Route": "Naeilo/Data/GoalRoute.swift", "GoalWeek": "Naeilo/Data/GoalRoute.swift"}
PRIM = {"String", "Int", "Double", "Bool", "UInt32", "Int64", "Float"}


# ---- Swift 선언 읽기 ----
def scan(text, i):
    """text[i] == '{' 일 때 짝 닫는 } 위치. 문자열·주석은 건너뛴다"""
    d = 0; n = len(text)
    while i < n:
        c = text[i]
        if text.startswith("//", i): i = text.find("\n", i); i = n if i < 0 else i; continue
        if text.startswith("/*", i): i = text.find("*/", i) + 2; continue
        if c == '"':
            i += 1
            while i < n and text[i] != '"': i += 2 if text[i] == "\\" else 1
        elif c == "{": d += 1
        elif c == "}":
            d -= 1
            if d == 0: return i
        i += 1
    raise ValueError("닫는 } 없음")


def statements(body):
    """한 단계 몸통을 (머리, 블록 몸통 또는 None) 문장으로 나눈다"""
    out, buf, i, n, par = [], "", 0, len(body), 0
    while i < n:
        c = body[i]
        if body.startswith("//", i): j = body.find("\n", i); i = n if j < 0 else j; continue
        if body.startswith("/*", i): i = body.find("*/", i) + 2; continue
        if c == '"':
            j = i + 1
            while j < n and body[j] != '"': j += 2 if body[j] == "\\" else 1
            buf += body[i:j + 1]; i = j + 1; continue
        if c in "([": par += 1
        if c in ")]": par -= 1
        if c == "{" and par == 0:
            j = scan(body, i); out.append((buf.strip(), body[i + 1:j])); buf = ""; i = j + 1; continue
        if c in "\n;" and par == 0:
            if buf.strip(): out.append((buf.strip(), None))
            buf = ""
        else:
            buf += c
        i += 1
    if buf.strip(): out.append((buf.strip(), None))
    return out


def split_top(s, sep=","):
    out, d, cur = [], 0, ""
    for c in s:
        if c in "([<": d += 1
        if c in ")]>": d -= 1
        if c == sep and d == 0: out.append(cur); cur = ""
        else: cur += c
    out.append(cur)
    return [x.strip() for x in out if x.strip()]


DECL = re.compile(r"^(?:(?:public|private|fileprivate|internal|final|indirect)\s+)*(struct|enum|class)\s+(\w+)\s*(?::\s*(.*))?$")


def parse_type(head, body):
    m = DECL.match(head)
    kind, name, conf = m.group(1), m.group(2), [x.strip() for x in split_top(m.group(3) or "")]
    t = {"kind": kind, "name": name, "conf": conf, "fields": [], "cases": [], "nested": {}}
    for h, b in statements(body):
        if DECL.match(h):
            if b is not None: t["nested"][DECL.match(h).group(2)] = parse_type(h, b)
            continue
        if b is not None: continue                                    # 계산 속성·함수·init
        if re.match(r"^(@\w+\s+)*(static|class|func|init|lazy|typealias|private static|fileprivate static)\b", h): continue
        if h.startswith("case ") and kind == "enum":
            t["cases"] += [c.split("=")[0].strip() for c in split_top(h[5:])]
            continue
        m2 = re.match(r"^(?:(?:private\(set\)|private|fileprivate|public|internal)\s+)*(var|let)\s+(.*)$", h)
        if not m2: continue
        for part in split_top(m2.group(2)):
            nm, _, rest = part.partition(":")
            nm = nm.strip()
            if "=" in nm and not rest:                                # var v = 1 (형 추론)
                nm, dv = [x.strip() for x in nm.split("=", 1)]
                ty = "Bool" if dv in ("true", "false") else "String" if dv.startswith('"') else "Double" if re.match(r"^-?\d+\.\d", dv) else "Int" if re.match(r"^-?\d+$", dv) else None
                if ty is None: raise ValueError(f"{name}.{nm}: 형을 알 수 없음 ({dv})")
                t["fields"].append({"name": nm, "type": ty, "decl": m2.group(1), "default": dv}); continue
            ty, _, dv = rest.partition("=")
            t["fields"].append({"name": nm, "type": ty.strip(), "decl": m2.group(1), "default": dv.strip() or None})
    # 여러 줄 속성 형 뒤에 붙은 typealias 같은 건 위에서 걸렀다
    return t


def find(path, qual):
    text = (IOS / path).read_text()
    parts = qual.split(".")
    for m in re.finditer(r"^[ \t]*(?:(?:public|private|fileprivate|internal|final|indirect)\s+)*(struct|enum|class)\s+" + parts[0] + r"\b[^{\n]*\{", text, re.M):
        i = text.index("{", m.start())
        j = scan(text, i)
        t = parse_type(text[m.start():i].strip(), text[i + 1:j])
        for p in parts[1:]: t = t["nested"][p]
        return t
    raise KeyError(f"{qual} 없음 ({path})")


def codable(t): return any(c in ("Codable", "Decodable", "Encodable") for c in t["conf"])


def emit(t, ind=""):
    """저장 속성·경우만 남긴 선언"""
    if t["kind"] == "enum" and t["cases"]:
        raw = [c for c in t["conf"] if c in PRIM]
        return f"{ind}enum {t['name']}: {', '.join(raw + ['Codable'])} {{ case {', '.join(t['cases'])} }}\n"
    kind = "struct" if codable(t) else "enum"
    s = f"{ind}{kind} {t['name']}{': Codable' if codable(t) else ''} {{\n"
    for k, n in t["nested"].items():
        if codable(n) or n["nested"]: s += emit(n, ind + "    ")
    if codable(t):
        for f in t["fields"]:
            s += f"{ind}    {f['decl']} {f['name']}: {f['type']}" + (f" = {f['default']}" if f["default"] else "") + "\n"
    return s + ind + "}\n"


# ---- 형 문법 ----
def ptype(s):
    s = s.strip()
    if s.endswith("?"): return ("opt", ptype(s[:-1]))
    if s.startswith("Optional<"): return ("opt", ptype(s[9:-1]))
    if s.startswith("Set<"): return ("set", ptype(s[4:-1]))
    if s.startswith("[") and s.endswith("]"):
        inner = s[1:-1]; kv = split_top(inner, ":")
        return ("dict", ptype(kv[0]), ptype(kv[1])) if len(kv) == 2 else ("arr", ptype(inner))
    return ("name", s)


class Schema:
    def __init__(self):
        self.top = {}
        for q, p in list(TYPES.items()) + list(DEPS.items()):
            top = q.split(".")[0]                                     # TossKeys.Keys 는 바깥 TossKeys 를 통째로 (옆 형 Q 등도 쓰므로)
            if top not in self.top: self.top[top] = find(p, top)

    def resolve(self, name, scope):
        for i in range(len(scope), -1, -1):
            path = scope[:i]
            cur = self.top.get(path[0]) if path else None
            for p in path[1:]: cur = cur["nested"].get(p) if cur else None
            if cur and name in cur["nested"]: return path + [name], cur["nested"][name]
        if name in self.top: return [name], self.top[name]
        raise KeyError(f"형 {name} 을 못 찾음 (scope {'.'.join(scope)})")

    # 예시 값: full = 선택 값도 모두 채움, min = 선택 값은 뺌
    def sample(self, ty, scope, full, ctr):
        k = ty[0]
        if k == "opt": return self.sample(ty[1], scope, full, ctr)
        if k in ("arr", "set"):
            n = 2 if full else 1
            el = ty[1]
            out = []
            for j in range(n):
                if el[0] == "opt" and full and j == 1: out.append(None)
                else: out.append(self.sample(el, scope, full, ctr))
            return out
        if k == "dict":
            n = 2 if full else 1
            out = {}
            for j in range(n):
                key = self.sample(ty[1], scope, full, ctr)
                out[str(key)] = None if (ty[2][0] == "opt" and full and j == 1) else self.sample(ty[2], scope, full, ctr)
            return out
        name = ty[1]
        ctr[0] += 1; c = ctr[0]
        if name == "String": return f"s{c}"
        if name in ("Int", "Int64"): return c
        if name == "UInt32": return 0xC8352E + c
        if name in ("Double", "Float"): return c + 0.25
        if name == "Bool": return c % 2 == 0
        path, t = self.resolve(name, scope)
        if t["kind"] == "enum" and t["cases"]: return t["cases"][c % len(t["cases"])]
        return self.obj(t, path, full, ctr)

    def obj(self, t, path, full, ctr):
        o = {}
        for f in t["fields"]:
            ty = ptype(f["type"])
            if ty[0] == "opt" and not full: continue
            o[f["name"]] = self.sample(ty, path, full, ctr)
        return o

    def canon(self, v, ty, scope):
        """비교용: Set 은 정렬하고, 숫자는 실수로"""
        k = ty[0]
        if v is None: return None
        if k == "opt": return self.canon(v, ty[1], scope)
        if k == "arr": return [self.canon(x, ty[1], scope) for x in v]
        if k == "set": return sorted((self.canon(x, ty[1], scope) for x in v), key=json.dumps)
        if k == "dict": return {kk: self.canon(x, ty[2], scope) for kk, x in v.items()}
        name = ty[1]
        if name in ("Int", "Int64", "UInt32", "Double", "Float"): return float(v)
        if name in PRIM: return v
        path, t = self.resolve(name, scope)
        if t["kind"] == "enum" and t["cases"]: return v
        if not isinstance(v, dict): return v
        out = {}
        for kk, x in v.items():
            f = next((f for f in t["fields"] if f["name"] == kk), None)
            out[kk] = self.canon(x, ptype(f["type"]), path) if f else ("?extra", x)
        return out


SWIFT_MAIN = """
import Foundation
func rt<T: Codable>(_ t: T.Type, _ inp: URL, _ out: URL) throws {
    let v = try JSONDecoder().decode(T.self, from: Data(contentsOf: inp))
    let e = JSONEncoder(); e.outputFormatting = [.sortedKeys, .prettyPrinted]
    try e.encode(v).write(to: out)
}
let a = CommandLine.arguments
// 사용: rt <형 이름> <읽을 파일> <쓸 파일>
do {
    switch a[1] {
%CASES%
    default: fatalError("모르는 형 " + a[1])
    }
} catch { FileHandle.standardError.write(("디코드 실패 \\(a[1]) \\(a[2]): \\(error)\\n").data(using: .utf8)!); exit(1) }
"""


def build(sc):
    WORK.mkdir(parents=True, exist_ok=True)
    src = "import Foundation\n"
    done = set()
    for q in list(TYPES) + list(DEPS):
        top = q.split(".")[0]
        if top in done: continue
        done.add(top); src += emit(sc.top[top])
    (WORK / "Types.swift").write_text(src)
    cases = "\n".join(f'    case "{q}": try rt({q}.self, URL(fileURLWithPath: a[2]), URL(fileURLWithPath: a[3]))' for q in TYPES)
    (WORK / "main.swift").write_text(SWIFT_MAIN.replace("%CASES%", cases))
    exe = WORK / "rt"
    r = subprocess.run(["swiftc", "-O", "-o", str(exe), str(WORK / "Types.swift"), str(WORK / "main.swift")], capture_output=True, text=True)
    if r.returncode: sys.exit("swiftc 실패:\n" + r.stderr[-3000:])
    return exe


def type_of(q): return ("name", q)


def scope_of(q): return q.split(".")[:-1]


def gen():
    sc = Schema(); exe = build(sc)
    FIX.mkdir(exist_ok=True)
    for old in FIX.glob("*.json"): old.unlink()
    for q in TYPES:
        path, t = sc.resolve(q.split(".")[-1], scope_of(q))
        for var in ("full", "min"):
            inp = WORK / f"in_{q}.{var}.json"
            inp.write_text(json.dumps(sc.obj(t, path, var == "full", [0]), ensure_ascii=False, indent=1))
            out = FIX / f"{q}.{var}.json"
            r = subprocess.run([str(exe), q, str(inp), str(out)], capture_output=True, text=True)
            if r.returncode: sys.exit(r.stderr)
            a = sc.canon(json.loads(inp.read_text()), ("name", t["name"]), path[:-1])
            b = sc.canon(json.loads(out.read_text()), ("name", t["name"]), path[:-1])
            if a != b: sys.exit(f"Swift 자체 왕복이 다름: {q}.{var}")
    print(f"fixtures {len(TYPES) * 2}개 ({len(TYPES)}형 × full·min) → {FIX.relative_to(ROOT)}")


def check():
    sc = Schema(); exe = build(sc)
    kdir = WORK / "kotlin"
    bad, n = [], 0
    for f in sorted(FIX.glob("*.json")):
        q = f.name.rsplit(".", 2)[0]
        k = kdir / f.name
        if not k.exists(): bad.append(f"{f.name}: Kotlin 결과 없음 (gradle 시험을 먼저)"); continue
        out = WORK / f"swift_{f.name}"
        r = subprocess.run([str(exe), q, str(k), str(out)], capture_output=True, text=True)
        n += 1
        if r.returncode: bad.append(f"{f.name}: Swift 가 Kotlin JSON 을 못 읽음 {r.stderr.strip()}"); continue
        path, t = sc.resolve(q.split(".")[-1], scope_of(q))
        a = sc.canon(json.loads(f.read_text()), ("name", t["name"]), path[:-1])
        b = sc.canon(json.loads(out.read_text()), ("name", t["name"]), path[:-1])
        if a != b: bad.append(f"{f.name}: Swift 가 다시 쓴 값이 다름")
    print(f"Swift 가 Kotlin JSON 읽기: {n - len(bad)}/{len(list(FIX.glob('*.json')))} 통과")
    for b in bad: print("  ✗", b)
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    {"gen": gen, "check": check}[sys.argv[1] if len(sys.argv) > 1 else "gen"]()
