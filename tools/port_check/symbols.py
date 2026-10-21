#!/usr/bin/env python3
"""SF Symbols → Material Symbols(Rounded, 굵기 400) 벡터 만들기.

symbols.md 표의 줄(`| sf | material | fill |`)을 읽어, 쓰는 기호만 res/drawable/ms_<이름>[_fill].xml 로 만든다.
SVG 는 google/material-design-icons 저장소에서 받는다 (viewBox 0 -960 960 960 → 그룹을 960 내려 그린다).
쓰는 법: python3 tools/port_check/symbols.py   (이미 있는 파일은 건너뛴다)
"""
import os, re, sys, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MD = os.path.join(ROOT, 'tools', 'port_check', 'symbols.md')
OUT = os.path.join(ROOT, 'android-app', 'app', 'src', 'main', 'res', 'drawable')
URL = 'https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/{n}/materialsymbolsrounded/{n}{f}_24px.svg'

rows = []
for line in open(MD, encoding='utf-8'):
    m = re.match(r'\|\s*`([^`]+)`\s*\|\s*`([a-z0-9_]+)`\s*\|\s*(채움|-)\s*\|', line)
    if m:
        rows.append((m.group(1), m.group(2), m.group(3) == '채움'))
done = set()
for sf, name, fill in rows:
    res = 'ms_' + name + ('_fill' if fill else '')
    if res in done:
        continue
    done.add(res)
    path = os.path.join(OUT, res + '.xml')
    if os.path.exists(path):
        continue
    svg = urllib.request.urlopen(URL.format(n=name, f='_fill1' if fill else ''), timeout=20).read().decode()
    d = re.findall(r'<path[^>]* d="([^"]+)"', svg)
    if not d:
        sys.exit(f'{name}: path 없음')
    paths = '\n'.join(f'        <path android:fillColor="#FF000000" android:pathData="{x}"/>' for x in d)
    open(path, 'w', encoding='utf-8').write(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols Rounded {name}{' (채움)' if fill else ''} · SF Symbol {sf} 자리 (tools/port_check/symbols.md) -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="960" android:viewportHeight="960">
    <group android:translateY="960">
{paths}
    </group>
</vector>
''')
    print('만듦', res)
