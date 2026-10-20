"""내 캐릭터 부품(24×34)의 옆모습(오른쪽 보기) 그림을 만든다: me_<부품>_side.
왼쪽은 앱이 뒤집어 쓴다. 다시 만들 때: python3 tools/gen_me_side.py"""
import glob, json, os
from PIL import Image

A = os.path.join(os.path.dirname(__file__), '..', 'ios-app', 'Naeilo', 'Assets.xcassets')
W, H = 24, 34
T = (0, 0, 0, 0)


def blank():
    return Image.new('RGBA', (W, H), T)


def pixels(im):
    p = im.load()
    return {(x, y): p[x, y] for y in range(H) for x in range(W) if p[x, y][3]}


def put(d):
    im = blank()
    for (x, y), c in d.items():
        if 0 <= x < W and 0 <= y < H:
            im.putpixel((x, y), c)
    return im


def dark(c, k=0.82):
    return (int(c[0] * k), int(c[1] * k), int(c[2] * k), c[3])


def main_color(d):
    cs = {}
    for c in d.values():
        cs[c] = cs.get(c, 0) + 1
    return max(cs, key=cs.get)


def face(d):
    # 얼굴은 한 칸 앞으로, 앞쪽 그늘은 밝게, 코 1px
    base = main_color(d)
    o = {(x + 1, y): (base if x >= 14 else c) for (x, y), c in d.items()}
    row = [x for (x, y) in o if y == 10]
    if row:
        o[(max(row) + 1, 10)] = o[(max(row), 10)]
    return o


def hair(d):
    o = {(x + 1, y): c for (x, y), c in d.items()}
    base = main_color(d)
    # 앞머리 끝: 가운데(10~15)가 비는 첫 줄
    rows = sorted({y for (_, y) in o})
    fringe = next((y for y in rows if not any((x, y) in o for x in range(10, 16))), rows[-1] + 1)
    o = {(x, y): c for (x, y), c in o.items() if y < fringe or x < 14}
    # 뒤통수를 덮는다 (귀 위까지)
    for y in range(fringe, min(12, rows[-1] + 1)):
        xs = [x for (x, yy) in o if yy == y]
        if not xs:
            continue
        for x in range(min(xs), 11):
            o.setdefault((x, y), base)
    # 귀 앞 구레나룻 1px
    if fringe <= 9:
        o[(11, fringe)] = dark(base)
    return o


def eyes(d):
    r = {k: c for k, c in d.items() if k[0] >= 12}
    return {(x + 2, y): c for (x, y), c in r.items()}


def mouth(d):
    if not d:
        return d
    right = max(x for x, _ in d)
    r = {k: c for k, c in d.items() if k[0] >= right - 1}
    return {(x + 17 - right, y): c for (x, y), c in r.items()}


def glasses(d):
    r = {(x + 2, y): c for (x, y), c in d.items() if x >= 12}
    if r:
        left = min(x for x, _ in r)
        top = min(y for _, y in r)
        c = main_color(r)
        for x in range(10, left):
            r[(x, top + 1)] = c          # 다리
    return r


def mask(d):
    return {(x + 1, y): c for (x, y), c in d.items() if x >= 10}


def torso(d, sleeve):
    # 몸통은 8칸(8~15), 팔 하나는 가운데 앞(11~13)
    o = {(x, y): c for (x, y), c in d.items() if not (15 <= y <= 24) or 8 <= x <= 15}
    arm = [y for (x, y) in d if x == 5 and 15 <= y <= 24]
    if not arm:
        return o
    bot = max(arm)
    for y in range(16, 25):
        for x in (11, 12, 13):
            if y <= bot:
                src = d.get((x - 6 if x < 12 else 5, y)) or d.get((5, y))
                if src:
                    o[(x, y)] = dark(src, 0.86) if sleeve else src
            elif sleeve:
                o.pop((x, y), None)      # 소매 아래는 팔(피부)이 보이게
    return o


def prop(d):
    if not d:
        return d
    xs = [x for x, _ in d]
    dx = 14 - (min(xs) + max(xs)) // 2
    return {(x + dx, y): c for (x, y), c in d.items()}


RULES = {
    'face': face, 'hair': hair, 'eyes': eyes, 'mouth': mouth, 'glasses': glasses, 'mask': mask,
    'body': lambda d: torso(d, False), 'top': lambda d: torso(d, True), 'bottom': lambda d: d, 'prop': prop,
}


def main():
    n = 0
    for path in sorted(glob.glob(os.path.join(A, 'me_*.imageset'))):
        name = os.path.basename(path)[:-9]
        if name.endswith('_side'):
            continue
        layer = name.split('_')[1]
        im = Image.open(os.path.join(path, name + '.png')).convert('RGBA')
        out = put(RULES[layer](pixels(im)))
        sname = name + '_side'
        os.makedirs(os.path.join(A, sname + '.imageset'), exist_ok=True)
        out.save(os.path.join(A, sname + '.imageset', sname + '.png'))
        with open(os.path.join(A, sname + '.imageset', 'Contents.json'), 'w') as f:
            json.dump({"images": [{"idiom": "universal", "filename": sname + '.png'}], "info": {"author": "xcode", "version": 1}}, f)
        n += 1
    print(n, 'side parts')


if __name__ == '__main__':
    main()
