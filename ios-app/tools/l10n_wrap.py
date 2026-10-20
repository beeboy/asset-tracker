# Swift 소스의 한글 문자열을 String(localized:)로 감싼다. SwiftUI 자동 번역 자리(Text("…") 등)는 그대로 둔다.
import re, sys
HAN = re.compile(r'[가-힣ㄱ-ㅎㅏ-ㅣ]')
LSK = re.compile(r'(?:^|[^\w.])(?:Text|Button|Label|Toggle|Section|Picker|TextField|SecureField|NavigationLink|Menu|Link|Stepper|DisclosureGroup|LabeledContent|ContentUnavailableView)\s*\(\s*$'
                 r'|\.(?:navigationTitle|alert|confirmationDialog|help|accessibilityLabel|accessibilityHint|accessibilityValue)\s*\(\s*$')
SKIP = re.compile(r'(?:localized:|verbatim:|forKey:|named:|forResource:|of:|with:|separator:|separatedBy:|prefix:|suffix:)\s*$'
                  r'|(?:WL\.t|print|debugPrint|fatalError|assert|assertionFailure|precondition|preconditionFailure|NSLog|Image|hasPrefix|hasSuffix|contains|custom|#Preview|\.info|\.debug|\.error|\.notice|\.warning|\.fault|\.log)\s*\(\s*$')

def parse_string(s, i):
    """s[i]=='"'. 끝 위치와 조각 목록을 돌려준다."""
    multi = s.startswith('"""', i)
    j = i + (3 if multi else 1)
    segs, buf = [], ''
    while True:
        if multi and s.startswith('"""', j):
            segs.append(('lit', buf)); return j + 3, segs, multi
        c = s[j]
        if not multi and c == '"':
            segs.append(('lit', buf)); return j + 1, segs, multi
        if c == '\\':
            if s[j+1] == '(':
                segs.append(('lit', buf)); buf = ''
                k = j + 2; depth = 1; start = k
                while depth:
                    ch = s[k]
                    if ch == '"':
                        k, _, _ = parse_string(s, k); continue
                    if ch == '(': depth += 1
                    elif ch == ')': depth -= 1
                    k += 1
                segs.append(('interp', s[start:k-1])); j = k; continue
            buf += s[j:j+2]; j += 2; continue
        buf += c; j += 1

def has_han(segs):
    return any(t == 'lit' and HAN.search(v) for t, v in segs)

def decide(code, i, end):
    pre = code[max(0, i-200):i]
    line = code[code.rfind('\n', 0, i)+1:i]
    st = line.strip()
    if st.startswith('@') or st.startswith('#'): return False
    if re.match(r'case\b', st) and ':' not in st[4:]: return False
    if SKIP.search(pre): return False
    if code[end:end+1] == ':': return False   # 사전 열쇠("키": 값)는 찾기용이라 그대로
    if re.search(r'[=!]=\s*$', pre) or re.match(r'\s*[=!]=', code[end:end+5]): return False
    if LSK.search(pre):
        rest = code[end:end+40].lstrip()
        if rest[:1] in (')', ','): return False
    return True

def transform(code):
    out = []; i = 0; n = len(code)
    while i < n:
        if code.startswith('//', i):
            k = code.find('\n', i); k = n if k < 0 else k
            out.append(code[i:k]); i = k; continue
        if code.startswith('/*', i):
            k = code.find('*/', i) + 2; out.append(code[i:k]); i = k; continue
        c = code[i]
        if c == '#' and code.startswith('#"', i):
            k = code.find('"#', i+2) + 2; out.append(code[i:k]); i = k; continue
        if c == '"':
            end, segs, multi = parse_string(code, i)
            wrap = has_han(segs) and decide(code, i, end)
            q = '"""' if multi else '"'
            body = ''.join(v if t == 'lit' else '\\(' + (('String(describing: ' + transform(v) + ')') if wrap else transform(v)) + ')' for t, v in segs)
            lit = q + body + q
            out.append('String(localized: ' + lit + ')' if wrap else lit)
            i = end; continue
        out.append(c); i += 1
    return ''.join(out)

if __name__ == '__main__':
    src = sys.stdin.read() if sys.argv[1] == '-' else open(sys.argv[1], encoding='utf-8').read()
    res = transform(src)
    if sys.argv[1] == '-': sys.stdout.write(res)
    else: open(sys.argv[1], 'w', encoding='utf-8').write(res)
