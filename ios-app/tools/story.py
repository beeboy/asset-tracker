#!/usr/bin/env python3
"""외전 원고(MyVault 원고 폴더의 .md)를 앱 서재용 JSON 으로 바꾼다. 프롤로그~5장만 넣는다 (사이트 공개 범위와 같음).

  python3 tools/story.py <side_story_v2.1.md> <"Side Story - Resonance Across Kinds.md">
  → Naeilo/Resources/story_ko.json, story_en.json

블록: h = 절 제목(## 01 …), q = 인용(> 줄: 화면·메시지), p = 문단, s = 장면 나눔, t = 표(rows).
문단 안의 줄 끝 공백 두 칸은 줄바꿈으로, **굵게**·_기울임_ 은 그대로 둔다 (앱이 마크다운으로 그린다).
"""
import json, re, sys
from pathlib import Path

CHAPTERS = 6   # 프롤로그, 1~5장

def parse(md: str):
    chapters, cur, buf, kind = [], None, [], "p"

    def flush():
        nonlocal buf, kind
        if cur is not None and buf and kind == "t":
            rows = [[c.strip() for c in l.strip().strip("|").split("|")] for l in buf]
            rows = [r for r in rows if not all(re.fullmatch(r":?-+:?", c) for c in r)]
            cur["blocks"].append({"k": "t", "t": "", "rows": rows})
        elif cur is not None and buf:
            text = "".join(l.rstrip() + ("\n" if l.endswith("  ") else " ") for l in buf).strip()
            text = re.sub(r"\s*\n\s*", "\n", text)
            if text:
                cur["blocks"].append({"k": kind, "t": text})
        buf, kind = [], "p"

    for raw in md.splitlines():
        line = raw.rstrip("\n")
        if line.startswith("# "):
            flush()
            head = line[2:].strip()
            parts = re.split(r"\s+[―—]\s+", head, maxsplit=1)
            if len(parts) == 2 and not head.startswith(("중첩된 현실", "Nested Reality")):
                if len(chapters) == CHAPTERS:
                    break
                cur = {"title": parts[0], "name": parts[1], "blocks": []}
                chapters.append(cur)
            continue
        if cur is None:
            continue
        if line.startswith("## "):
            flush()
            cur["blocks"].append({"k": "h", "t": line[3:].strip()})
        elif line.strip() in ("---", "***", "⸻", "* * *"):
            flush()
            cur["blocks"].append({"k": "s", "t": ""})
        elif not line.strip():
            flush()
        elif line.startswith("|"):
            if buf and kind != "t":
                flush()
            kind = "t"
            buf.append(line)
        elif line.startswith(">"):
            if buf and kind != "q":
                flush()
            kind = "q"
            buf.append(line.lstrip(">").lstrip(" ") if line.strip() != ">" else "")
        else:
            if buf and kind in ("q", "t"):
                flush()
            buf.append(line)
    flush()
    if len(chapters) != CHAPTERS:
        sys.exit(f"장 {len(chapters)}개만 찾음 (프롤로그~5장 {CHAPTERS}개 필요)")
    return chapters

def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    out = Path(__file__).resolve().parent.parent / "Naeilo" / "Resources"
    books = {lang: parse(Path(src).read_text(encoding="utf-8")) for lang, src in (("ko", sys.argv[1]), ("en", sys.argv[2]))}
    # 번역본은 한글 원고와 블록이 하나씩 맞는다 (언어를 바꿔도 읽던 자리가 같다). 영문은 인용(>) 표시가 없어서 한글 쪽 표시를 따른다
    for ci, (k, e) in enumerate(zip(books["ko"], books["en"])):
        if len(k["blocks"]) != len(e["blocks"]):
            sys.exit(f"{k['title']}: 블록 수가 다름 (한 {len(k['blocks'])} / 영 {len(e['blocks'])})")
        for a, b in zip(k["blocks"], e["blocks"]):
            if (a["k"] in "ht" or b["k"] in "ht") and a["k"] != b["k"]:
                sys.exit(f"{k['title']}: 블록 종류가 다름 {a} / {b}")
            if a["k"] == "q":
                b["k"] = "q"
    for lang, src in (("ko", sys.argv[1]), ("en", sys.argv[2])):
        chs = books[lang]
        data = {"lang": lang, "source": Path(src).name, "chapters": chs}
        (out / f"story_{lang}.json").write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
        print(lang, [(c["title"], c["name"], len(c["blocks"])) for c in chs])

main()
