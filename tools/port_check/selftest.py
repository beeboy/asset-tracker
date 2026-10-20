#!/usr/bin/env python3
"""port_check 시험: 옮긴 Kotlin 에서 항목 20개를 일부러 지운 시험판을 만들어 다 잡는지, 원래 상태에서 헛경보가 0인지 본다.

고르는 법(고정 씨앗, 골라 맞추지 않음): `// ios:` 참조가 붙은 Kotlin 줄 중 무작위 20개.
  짝수 번째 → 그 줄을 통째로 지운다
  홀수 번째 → 그 줄 코드의 첫 숫자·문구를 다른 값으로 바꾼다 (없으면 줄을 지운다)
쓰는 법: python3 tools/port_check/selftest.py [App.swift Theme.swift] [--seed N] [--n 20]
"""
import argparse
import os
import random
import re
import shutil
import sys
import tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import port_check as pc  # noqa: E402

ap = argparse.ArgumentParser()
ap.add_argument('files', nargs='*', default=['App.swift', 'Theme.swift'])
ap.add_argument('--seed', type=int, default=20261020)
ap.add_argument('--n', type=int, default=20)
a = ap.parse_args()

kdir = os.path.join(pc.ROOT, 'android-app')
ex = os.path.join(pc.ROOT, 'tools', 'port_check', 'exceptions.txt')

clean_missing, clean_bad = pc.check(a.files, kdir, ex, quiet=True)
print(f'원래 상태: 짝 없음 {len(clean_missing)} · 참조 이상 {len(clean_bad)} (헛경보 {len(clean_missing) + len(clean_bad)})')

tmp = tempfile.mkdtemp(prefix='port_check_')
cands = []
for dp, dn, fn in os.walk(kdir):
    dn[:] = [d for d in dn if d not in ('build', '.gradle', '.kotlin')]
    for f in fn:
        if not f.endswith('.kt'):
            continue
        src = os.path.join(dp, f)
        dst = os.path.join(tmp, os.path.relpath(src, kdir))
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy(src, dst)
        for i, l in enumerate(open(src, encoding='utf-8').read().split('\n')):
            m = pc.REF_RE.search(l)
            if not m:
                continue
            refs = {(r.group(1), int(n)) for r in pc.ONE_REF_RE.finditer(m.group(1)) for n in r.group(2).split(',')}
            files = {pc.split_range(f)[0]: pc.split_range(f)[1] for f in a.files}
            refs = {r for r in refs if r[0] in files and (files[r[0]] is None or r[1] in files[r[0]])}
            if refs:
                cands.append((dst, i, refs))

_sw = {}
def swift_of(rel):
    """그 파일의 Swift 항목 (줄 범위 없이, 한 번만 읽는다)"""
    if rel not in _sw:
        _sw[rel] = pc.swift_items(rel)[0]
    return _sw[rel]


random.seed(a.seed)
picked = random.sample(cands, a.n)
by_file = {}
for k, (dst, i, refs) in enumerate(picked):
    by_file.setdefault(dst, []).append((i, k, refs))

plan = []
for dst, items in by_file.items():
    lines = open(dst, encoding='utf-8').read().split('\n')
    for i, k, refs in sorted(items, reverse=True):
        code = pc.strip_comment(lines[i])
        how = '줄 지움'
        if k % 2 == 1:
            # 바꿀 것은 원본 Swift 줄에 있는 문구·숫자 중 하나 (Swift 에 없는 Kotlin 값, 예: weight(1f), 을 바꾸면 검사 대상이 아니라 시험이 아니다)
            sw = [it for r in refs for it in swift_of(r[0]) if it[0] == r[1]]
            sw_str = {it[2][0] for it in sw if it[1] == 'string'}
            sw_num = {}
            for it in sw:
                if it[1] == 'number': sw_num[it[2][0]] = max(sw_num.get(it[2][0], 0), it[2][1])
            nums = list(pc.KNUM_RE.finditer(pc.STR_RE.sub(lambda q: '"' + ' ' * (len(q.group(0)) - 2) + '"', code)))
            def same(x, v): return abs(pc.num_val(x.group(1)) - v) < 1e-9 or abs(pc.num_val(x.group(1)) - v * 1000) < 1e-6
            # 그 값이 이 줄에 Swift 가 요구하는 개수만큼만 있을 때 고른다 (더 있으면 하나 바꿔도 남아 있어 못 잡는 게 맞다)
            m = next((x for x in pc.STR_RE.finditer(code) if x.group(1) in sw_str), None) or \
                next((x for x in nums for v, c in sw_num.items() if same(x, v) and sum(same(y, v) for y in nums) <= c), None)
            if m and m.re is pc.STR_RE:
                # 맨 앞에 넣는다 (끝에 붙이면 "$http" 가 "$httpX" 처럼 변수 이름이 바뀌어 글자 변화가 아니게 된다)
                lines[i] = lines[i][:m.start()] + '"X' + m.group(1) + '"' + lines[i][m.end():]
                how = f'문구 바꿈 {m.group(0)}'
            elif m:
                tok = m.group(1)
                new = str(pc.num_val(tok) + 7)
                lines[i] = lines[i][:m.start(1)] + new + lines[i][m.end(1):]
                how = f'숫자 바꿈 {tok}→{new}'
            else:
                del lines[i]
        else:
            del lines[i]
        plan.append((k, os.path.relpath(dst, tmp), i + 1, sorted(refs), how))
    open(dst, 'w', encoding='utf-8').write('\n'.join(lines))

missing, bad = pc.check(a.files, tmp, ex, quiet=True)
hit = {(m[0], m[1]) for m in missing}
caught = 0
all_refs = set()
for k, f, ln, refs, how in sorted(plan):
    ok = any(r in hit for r in refs)
    caught += ok
    all_refs |= set(refs)
    print(f'{"잡음" if ok else "놓침"}  #{k:02d} {f}:{ln} {how}  → {", ".join(f"{r[0]}:{r[1]}" for r in refs)}')
extra = sorted(h for h in hit if h not in all_refs)
print(f'시험판: 일부러 지운 {a.n}개 중 {caught}개 잡음 · 지운 것과 무관한 경보 {len(extra)}')
for h in extra:
    print(f'  무관 경보 {h[0]}:{h[1]}')
shutil.rmtree(tmp)
sys.exit(0 if caught == a.n and not clean_missing and not clean_bad else 1)
