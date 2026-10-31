#!/usr/bin/env python3
"""코드 지도 다시 만들기. 표준 라이브러리만 쓴다.

실행: python3 docs/codemap/gen.py
결과: docs/codemap/ios.md, docs/codemap/android.md (손으로 고치지 않는다)
정규식으로 가볍게 훑으므로 들여쓴 선언(중첩 타입·멤버)은 보지 않는다.
"""
import os
import re

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.dirname(os.path.abspath(__file__))
SKIP_DIRS = {"build", ".gradle", "DerivedData", "Pods", ".build", "node_modules", "test", "androidTest"}

PLATFORMS = {
    "ios": {
        "title": "iOS 코드 지도",
        "ext": ".swift",
        "roots": ["ios-app/Naeilo", "ios-app/WidgetShared", "ios-app/Widgets", "ios-app/Watch"],
        # 줄 맨 앞(들여쓰기 없음)의 선언만
        "decl": re.compile(
            r"^(?:@\w+(?:\([^)]*\))?\s+)*"
            r"((?:public|private|fileprivate|internal|open|final|indirect|nonisolated)\s+)*"
            r"(class|struct|enum|protocol|extension|func|actor|typealias)\s+"
            r"([A-Za-z_][\w.]*)"
        ),
        "types": {"class", "struct", "enum", "protocol", "actor", "typealias"},
        "private": {"private", "fileprivate"},
        "alias": [("ios-app/Naeilo/", ""), ("ios-app/", "")],
    },
    "android": {
        "title": "안드로이드 코드 지도",
        "ext": ".kt",
        "roots": ["android-app/app/src/main", "android-app/widget/src/main"],
        "decl": re.compile(
            r"^(?:@\w+(?:\([^)]*\))?\s+)*"
            r"((?:private|internal|public|protected|data|sealed|enum|abstract|open|inline|value|"
            r"annotation|suspend|operator|infix|tailrec|inner|override|const)\s+)*"
            r"(class|object|interface|fun|typealias)\s+"
            r"(?:<[^>]*>\s*)?"
            r"([A-Za-z_][\w.]*)"
        ),
        "types": {"class", "object", "interface", "typealias"},
        "private": {"private"},
        # 모듈 경로를 줄여 쓴다. 위젯 모듈은 w/ 로 시작
        "alias": [("android-app/app/src/main/java/com/naeilo/app/", ""),
                  ("android-app/app/src/main/", "res:"),
                  ("android-app/widget/src/main/java/com/naeilo/", "w/"),
                  ("android-app/widget/src/main/", "w/res:")],
    },
}

SHORT = {"extension": "ext", "protocol": "proto", "interface": "iface", "typealias": "alias"}
STRIP = re.compile(r'//[^\n]*|"(?:\\.|[^"\\\n])*"')
WORD = re.compile(r"\b[A-Z]\w*\b")


def source_files(roots, ext):
    files = []
    for r in roots:
        base = os.path.join(ROOT, r)
        for dp, dns, fns in os.walk(base):
            dns[:] = sorted(d for d in dns if d not in SKIP_DIRS and not d.startswith("."))
            files += [os.path.join(dp, f) for f in sorted(fns) if f.endswith(ext)]
    return files


def scan(path, cfg):
    with open(path, encoding="utf-8", errors="replace") as fh:
        text = fh.read()
    decls, types = [], set()
    for line in text.splitlines():
        m = cfg["decl"].match(line)
        if not m:
            continue
        mods = set((m.group(1) or "").split())
        kind, name = m.group(2), m.group(3)
        if kind in cfg["types"]:
            types.add(name)
        if mods & cfg["private"]:
            continue
        decls.append(f"{SHORT.get(kind, kind)} {name}")
    return text.count("\n") + 1, decls, types, set(WORD.findall(STRIP.sub(" ", text)))


def build(key, cfg):
    files = source_files(cfg["roots"], cfg["ext"])
    info = {os.path.relpath(f, ROOT): scan(f, cfg) for f in files}
    owner = {}  # 타입 이름 -> 선언한 파일 (두 곳 이상이면 관계에서 뺌)
    for rel, (_, _, types, _) in info.items():
        for t in types:
            owner[t] = None if t in owner and owner[t] != rel else rel

    def short(rel):
        for a, b in cfg["alias"]:
            if rel.startswith(a):
                return b + rel[len(a):]
        return rel

    stems = {}
    for rel in info:
        st = os.path.splitext(os.path.basename(rel))[0]
        stems[st] = stems.get(st, 0) + 1

    def stem(rel):  # 관계 줄은 파일 이름만, 겹치면 줄인 경로
        st = os.path.splitext(os.path.basename(rel))[0]
        return st if stems[st] == 1 else os.path.splitext(short(rel))[0]

    out = [f"# {cfg['title']}", "", "`python3 docs/codemap/gen.py`로 다시 만든다. 손으로 고치지 않는다.", ""]
    total = sum(v[0] for v in info.values())
    out += [f"소스 {len(info)}개 · {total}줄 · 뿌리: " + ", ".join(f"`{r}`" for r in cfg["roots"]), ""]

    out += ["## 폴더 (파일 줄수)", ""]
    folders = {}
    for rel in info:
        d, f = os.path.split(short(rel))
        folders.setdefault(d or ".", []).append(f"{os.path.splitext(f)[0]} {info[rel][0]}")
    for d, fs in folders.items():
        out.append(f"- `{d}/` ({len(fs)}): " + ", ".join(fs))

    out += ["", "## 최상위 선언 (private 뺌)", ""]
    for rel, (_, decls, _, _) in info.items():
        if decls:
            by = {}
            for d in decls:
                k, n = d.split(" ", 1)
                if n not in by.setdefault(k, []):
                    by[k].append(n)
            out.append(f"- {short(rel)}: " + "; ".join(f"{k} " + ", ".join(ns) for k, ns in by.items()))

    out += ["", "## 파일 관계 (A -> A가 쓰는 타입을 선언한 파일, 확장자 뺌)", ""]
    for rel, (_, _, _, words) in info.items():
        deps = sorted({owner[w] for w in words if owner.get(w) and owner[w] != rel})
        if deps:
            out.append(f"- {stem(rel)} -> " + ", ".join(stem(d) for d in deps))

    dest = os.path.join(OUT, f"{key}.md")
    with open(dest, "w", encoding="utf-8") as fh:
        fh.write("\n".join(out) + "\n")
    return dest, len(out)


if __name__ == "__main__":
    for key, cfg in PLATFORMS.items():
        dest, n = build(key, cfg)
        size = os.path.getsize(dest)
        print(f"{os.path.relpath(dest, ROOT)}: {n}줄, {size}바이트")
