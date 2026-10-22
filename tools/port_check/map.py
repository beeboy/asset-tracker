#!/usr/bin/env python3
"""짝 지도 만들기: 안드로이드 Kotlin 의 `// ios: 파일.swift:줄` 표시를 모아 Swift 파일 ↔ Kotlin 파일 표를 docs/android-map.md 위쪽에 쓴다.
부품 대응표(아래쪽, 손으로 쓰는 칸)는 그대로 둔다. 다시 돌리면 위쪽 표만 새로 쓴다.
실행: python3 tools/port_check/map.py
"""
import os, re, collections
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
KT = [os.path.join(ROOT, "android-app", d) for d in ("app/src/main/java", "widget/src/main/java")]
OUT = os.path.join(ROOT, "docs", "android-map.md")
MARK = "<!-- 아래는 손으로 쓰는 칸 -->"
pat = re.compile(r"(?:ios: )?((?:Views|Data|Core|Widgets|WidgetShared|App)[A-Za-z/]*\.swift|[A-Z][A-Za-z]+\.swift):\d")
pairs = collections.defaultdict(collections.Counter)
for base in KT:
    for d, _, fs in os.walk(base):
        for f in fs:
            if not f.endswith(".kt"): continue
            p = os.path.join(d, f); rel = os.path.relpath(p, base)
            for line in open(p, encoding="utf-8"):
                if "ios:" not in line: continue
                for s in pat.findall(line): pairs[s][rel] += 1
rows = ["| Swift | Kotlin (표시 수) |", "|---|---|"]
for s in sorted(pairs):
    rows.append(f"| `{s}` | " + ", ".join(f"`{k}` ({n})" for k, n in pairs[s].most_common()) + " |")
head = ("# 아이폰 ↔ 안드로이드 짝 지도\n\n"
        "Swift 파일을 옮긴 Kotlin 파일. `python3 tools/port_check/map.py` 로 다시 만든다 (Kotlin 줄 끝 `// ios:` 표시에서 뽑음).\n"
        "Kotlin 경로는 `android-app/app/src/main/java/` 또는 `widget/src/main/java/` 아래.\n\n" + "\n".join(rows) + "\n\n")
tail = MARK + "\n"
if os.path.exists(OUT):
    t = open(OUT, encoding="utf-8").read()
    if MARK in t: tail = t[t.index(MARK):]
open(OUT, "w", encoding="utf-8").write(head + tail)
print(f"Swift {len(pairs)}개 → {OUT}")
