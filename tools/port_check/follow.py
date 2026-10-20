#!/usr/bin/env python3
"""아이폰 코드가 바뀌었을 때 안드로이드 이름표를 따라 옮긴다.

쓰는 법: python3 tools/port_check/follow.py <옛 커밋> [--apply]
옛 커밋과 지금(작업 트리)의 ios-app Swift 파일을 줄 단위로 비교해,
- 그대로 남은 줄을 가리키는 `// ios: 파일:줄` 이름표는 새 줄 번호로 바꾸고 (--apply 일 때만 씀)
- 바뀌거나 지워진 줄을 가리키는 이름표, 새로 생긴 Swift 줄 범위를 목록으로 보여 준다 (사람이 옮길 몫).
"""
import difflib, os, re, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
KT = os.path.join(ROOT, "android-app")
REF = re.compile(r'(//\s*ios:\s*)([\w/]+\.swift):(\d+(?:\s*,\s*\d+)*)')


def swift_path(rel):
    for base in ("ios-app/Naeilo/", "ios-app/"):
        if os.path.exists(os.path.join(ROOT, base + rel)):
            return base + rel
    return None


def main():
    old, apply = sys.argv[1], "--apply" in sys.argv
    changed = subprocess.run(["git", "diff", "--name-only", old, "--", "ios-app"], cwd=ROOT, capture_output=True, text=True).stdout.split()
    changed = [c for c in changed if c.endswith(".swift")]
    maps, gone, added = {}, {}, {}
    for c in changed:
        rel = c.split("ios-app/Naeilo/", 1)[-1] if "ios-app/Naeilo/" in c else c.split("ios-app/", 1)[-1]
        a = subprocess.run(["git", "show", f"{old}:{c}"], cwd=ROOT, capture_output=True, text=True).stdout.split("\n")
        b = open(os.path.join(ROOT, c)).read().split("\n") if os.path.exists(os.path.join(ROOT, c)) else []
        m, g, ad = {}, set(), []
        for op, i1, i2, j1, j2 in difflib.SequenceMatcher(None, a, b, autojunk=False).get_opcodes():
            if op == "equal":
                for k in range(i2 - i1): m[i1 + k + 1] = j1 + k + 1
            else:
                g.update(range(i1 + 1, i2 + 1))
                if j2 > j1: ad.append((j1 + 1, j2))
        maps[rel], gone[rel], added[rel] = m, g, ad
    todo = []
    for root, _, fs in os.walk(os.path.join(KT, "app", "src")):
        for f in fs:
            if not f.endswith(".kt"): continue
            p = os.path.join(root, f); s = open(p).read(); out = []; ch = False
            for ln, line in enumerate(s.split("\n"), 1):
                def rep(mm):
                    nonlocal ch
                    rel = mm.group(2)
                    if rel not in maps: return mm.group(0)
                    nums = [int(x) for x in re.split(r"\s*,\s*", mm.group(3))]
                    new = []
                    for n in nums:
                        if n in maps[rel]: new.append(maps[rel][n])
                        else: todo.append(f"{os.path.relpath(p, ROOT)}:{ln} → {rel}:{n} (바뀐 줄)")
                    if new != nums: ch = True
                    return mm.group(1) + rel + ":" + ",".join(map(str, new)) if new else mm.group(0)
                out.append(REF.sub(rep, line))
            if ch and apply: open(p, "w").write("\n".join(out))
    # 예외 목록의 줄 번호도 같이 옮긴다 (그대로 남은 줄만. 바뀐 줄의 예외는 목록에 남겨 사람이 본다)
    ex = os.path.join(ROOT, "tools", "port_check", "exceptions.txt"); lines = open(ex).read().split("\n"); ech = False
    for k, line in enumerate(lines):
        mm = re.match(r"^([\w/]+\.swift):(\d+)(?:-(\d+))?(\s.*)$", line)
        if not mm or mm.group(1) not in maps: continue
        rel, a1 = mm.group(1), int(mm.group(2)); a2 = int(mm.group(3) or a1)
        if all(n in maps[rel] for n in range(a1, a2 + 1)):
            b1, b2 = maps[rel][a1], maps[rel][a2]
            new = f"{rel}:{b1}" + (f"-{b2}" if b2 != b1 else "") + mm.group(4)
            if new != line: lines[k] = new; ech = True
        else:
            todo.append(f"exceptions.txt:{k + 1} → {rel}:{a1}-{a2} (바뀐 줄의 예외)")
    if ech and apply: open(ex, "w").write("\n".join(lines))
    print("바뀐 Swift 파일:", ", ".join(maps))
    for rel, ad in added.items():
        if ad: print(f"새로 옮길 줄 {rel}: " + ", ".join(f"{a}-{b}" if a != b else str(a) for a, b in ad))
    print(f"바뀐 줄을 가리키는 이름표 {len(todo)}개")
    for t in todo: print("  ", t)


if __name__ == "__main__":
    main()
