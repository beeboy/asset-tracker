#!/usr/bin/env python3
"""iOS(Swift) → Android(Kotlin) 옮김 검사.

Swift 파일에서 항목(코드 줄·View/struct/enum 이름·enum case·@State·static let·버튼·문구·숫자·애니메이션)을 뽑고,
Kotlin 의 `// ios: 파일:줄` 주석과 짝을 맞춰 짝 없는 항목을 출력한다.

짝 규칙
- Swift 코드 줄마다 그 줄을 가리키는 `// ios: App.swift:31` (여러 줄은 `App.swift:31,32`) 이 있어야 한다.
- 주석이 붙은 Kotlin 줄부터 다음 `// ios:` 줄 또는 빈 줄 앞까지가 그 짝의 '구역'이다. 구역의 코드(주석 뺀 것)에
  · 이름(View·struct·enum·case·@State·static let)이 있어야 하고 (대소문자 무시)
  · 문구는 같은 글자로, 숫자는 같은 값(초→밀리초 ×1000 허용)으로 있어야 하고
  · Button 은 clickable/onClick/Button, 애니메이션은 animate/spring/tween/Animatable/fade 가 있어야 한다.
- 예외 목록 파일: `App.swift:8 이유` 또는 `App.swift:47-53 이유` (# 로 시작하면 주석).

쓰는 법: python3 tools/port_check/port_check.py [--kotlin android-app] [--exceptions tools/port_check/exceptions.txt] App.swift Theme.swift
"""
import argparse
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SWIFT_BASE = os.path.join(ROOT, "ios-app", "Naeilo")

STR_RE = re.compile(r'"((?:[^"\\]|\\.)*)"')
NUM_RE = re.compile(r'(?<![\w.$])(0x[0-9A-Fa-f]+|\d+\.\d+|\d+)(?![\w])')
KNUM_RE = re.compile(r'(?<![\w.$])(0x[0-9A-Fa-f]+|\d+\.\d+|\d+)(?:[fFL]|\.dp|\.sp)?(?![\w])')
WORD_RE = re.compile(r'[A-Za-z_]\w*')
REF_RE = re.compile(r'//\s*ios:\s*(.*)$')
ONE_REF_RE = re.compile(r'([\w/]+\.swift):(\d+(?:\s*,\s*\d+)*)')
ANIM_RE = re.compile(r'\b(withAnimation|easeOut|easeIn|easeInOut|spring|linear|repeatForever|transition)\b|\.animation\(|\.default\b(?=.*animation)')
ANIM_OK = re.compile(r'animate|spring|tween|Animatable|fade|AnimatedVisibility|snapTo', re.I)
BUTTON_OK = re.compile(r'clickable|onClick|Button|toggleable|selectable', re.I)


def strip_comment(line):
    """문자열 밖의 // 뒤를 지운다."""
    out, i, q = [], 0, False
    while i < len(line):
        c = line[i]
        if c == '\\' and q:
            out.append(line[i:i + 2]); i += 2; continue
        if c == '"':
            q = not q
        if not q and line.startswith('//', i):
            break
        out.append(c); i += 1
    return ''.join(out)


def num_val(tok):
    return float(int(tok, 16)) if tok.lower().startswith('0x') else float(tok)


def swift_items(rel):
    path = os.path.join(SWIFT_BASE, rel)
    items = []   # (줄, 종류, 값, 원문)
    with open(path, encoding='utf-8') as f:
        lines = f.read().split('\n')
    for n, raw in enumerate(lines, 1):
        code = strip_comment(raw).strip()
        if not code or code.startswith('import ') or re.fullmatch(r'[\}\)\]\s,]+', code):
            continue
        items.append((n, 'line', None, raw.strip()))
        bare = STR_RE.sub('""', code)
        for m in re.finditer(r'\b(?:struct|enum|class)\s+(\w+)', bare):
            items.append((n, 'name', m.group(1), raw.strip()))
        for m in re.finditer(r'\bcase\s+([A-Za-z_]\w*(?:\s*,\s*[A-Za-z_]\w*)*)\s*(?:\}|$)', bare):
            for c in m.group(1).split(','):
                items.append((n, 'name', c.strip(), raw.strip()))
        for m in re.finditer(r'@State\s+(?:private\s+)?var\s+(\w+)', bare):
            items.append((n, 'name', m.group(1), raw.strip()))
        for m in re.finditer(r'\bstatic\s+(?:let|var)\s+(\w+)', bare):
            items.append((n, 'name', m.group(1), raw.strip()))
        if re.search(r'\bButton\b', bare):
            items.append((n, 'button', None, raw.strip()))
        if ANIM_RE.search(bare):
            items.append((n, 'anim', None, raw.strip()))
        # 같은 값이 한 줄에 여러 번 있으면 Kotlin 구역에도 그만큼 있어야 한다 (값, 개수)
        strs = {}
        for m in STR_RE.finditer(code):
            v = m.group(1)
            if '\\(' in v or v == '':
                continue
            strs[v] = strs.get(v, 0) + 1
        for v, c in strs.items():
            items.append((n, 'string', (v, c), raw.strip()))
        nums = {}
        for m in NUM_RE.finditer(bare):
            v = num_val(m.group(1))
            nums[v] = nums.get(v, 0) + 1
        for v, c in nums.items():
            items.append((n, 'number', (v, c), raw.strip()))
    return items, lines


def kotlin_blocks(kdir):
    """{(파일, 줄): [구역 코드, ...]} 와 참조 목록."""
    blocks, refs = {}, []
    for dp, dn, fn in os.walk(kdir):
        dn[:] = [d for d in dn if d not in ('build', '.gradle', '.kotlin')]
        for f in sorted(fn):
            if not f.endswith('.kt'):
                continue
            p = os.path.join(dp, f)
            with open(p, encoding='utf-8') as fh:
                kl = fh.read().split('\n')
            marks = [i for i, l in enumerate(kl) if REF_RE.search(l)]
            for j, i in enumerate(marks):
                end = marks[j + 1] if j + 1 < len(marks) else len(kl)
                # 빈 줄에서도 끊는다 (다음 함수·선언의 값이 섞이지 않게)
                blank = next((x for x in range(i + 1, end) if not kl[x].strip()), end)
                end = min(end, blank)
                body = '\n'.join(strip_comment(re.sub(r'/\*.*?\*/', '', x)) for x in kl[i:end])
                for m in ONE_REF_RE.finditer(REF_RE.search(kl[i]).group(1)):
                    for ln in m.group(2).split(','):
                        key = (m.group(1), int(ln))
                        blocks.setdefault(key, []).append(body)
                        refs.append((key, os.path.relpath(p, ROOT), i + 1))
    return blocks, refs


def load_exceptions(path):
    ex = {}
    if not path or not os.path.exists(path):
        return ex
    with open(path, encoding='utf-8') as f:
        for raw in f:
            s = raw.strip()
            if not s or s.startswith('#'):
                continue
            m = re.match(r'([\w/]+\.swift):(\d+)(?:-(\d+))?\s*(.*)', s)
            if not m:
                print(f'예외 목록 줄을 못 읽음: {s}', file=sys.stderr); continue
            a = int(m.group(2)); b = int(m.group(3) or a)
            for n in range(a, b + 1):
                ex[(m.group(1), n)] = m.group(4)
    return ex


def satisfied(kind, val, bodies):
    for b in bodies:
        if kind == 'line':
            return True
        if kind == 'name':
            if val.lower() in {w.lower() for w in WORD_RE.findall(STR_RE.sub('""', b))}:
                return True
        elif kind == 'string':
            v, c = val
            if sum(m.group(1) == v for m in STR_RE.finditer(b)) >= c:
                return True
        elif kind == 'number':
            v, c = val
            ks = [num_val(m.group(1)) for m in KNUM_RE.finditer(STR_RE.sub('""', b))]
            if sum(abs(k - v) < 1e-9 or abs(k - v * 1000) < 1e-6 for k in ks) >= c:
                return True
        elif kind == 'button':
            if BUTTON_OK.search(b):
                return True
        elif kind == 'anim':
            if ANIM_OK.search(b):
                return True
    return False


def check(files, kdir, expath, quiet=False):
    blocks, refs = kotlin_blocks(kdir)
    ex = load_exceptions(expath)
    missing, total, excepted = [], 0, 0
    for rel in files:
        items, lines = swift_items(rel)
        for n, kind, val, src in items:
            total += 1
            key = (rel, n)
            if key in ex:
                excepted += 1; continue
            if not satisfied(kind, val, blocks.get(key, [])):
                missing.append((rel, n, kind, val, src))
        # 잘못된 참조: 코드 아닌 줄·예외 줄을 가리키면 알린다
    code_lines = {}
    for rel in files:
        items, _ = swift_items(rel)
        code_lines[rel] = {n for n, k, *_ in items if k == 'line'}
    bad = [(k, f, l) for k, f, l in refs if k[0] in code_lines and (k[1] not in code_lines[k[0]] or k in ex)]
    if not quiet:
        for rel, n, kind, val, src in missing:
            v = '' if val is None else f' {val[0]!r}×{val[1]}' if isinstance(val, tuple) else f' {val!r}'
            print(f'짝 없음  {rel}:{n}  [{kind}{v}]  {src[:80]}')
        for k, f, l in bad:
            print(f'참조 이상  {f}:{l} → {k[0]}:{k[1]} (코드 줄이 아니거나 예외 목록에 있음)')
        print(f'항목 {total} · 예외 {excepted} · 짝 없음 {len(missing)} · 참조 이상 {len(bad)}')
    return missing, bad


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('files', nargs='+', help='ios-app/Naeilo 기준 Swift 경로')
    ap.add_argument('--kotlin', default=os.path.join(ROOT, 'android-app'))
    ap.add_argument('--exceptions', default=os.path.join(ROOT, 'tools', 'port_check', 'exceptions.txt'))
    a = ap.parse_args()
    missing, bad = check(a.files, a.kotlin, a.exceptions)
    sys.exit(1 if missing or bad else 0)


if __name__ == '__main__':
    main()
