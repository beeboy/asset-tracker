#!/usr/bin/env python3
"""숫자 맞추기: 아이폰(Swift) 계산 엔진과 안드로이드(Kotlin) 계산 엔진이 같은 입력에 같은 숫자를 내는지 본다 (차이 0).

  python3 tools/num_parity/parity.py gen     # mc_parity·calc_parity 와 같은 입력을 만들고 Swift 로 돌려 app.json 들을 쓴다
  (cd android-app && ./gradlew :app:testReleaseUnitTest)   # Kotlin 이 같은 입력으로 kotlin.json 들을 쓴다 (NumParityTest)
  python3 tools/num_parity/parity.py check   # Swift 와 Kotlin 결과를 비교한다 (차이 0, exp·log 를 거친 칸만 상대 1e-12)

입력은 tools/mc_parity.mjs gen (전망 몬테카를로 MonteCarlo.swift) 과 tools/calc_parity.mjs gen (SiteCalc 배당·세금) 이 만든다.
난수는 같은 시드(stats.json model.seed). 시간(ms)만 비교에서 뺀다. 결과는 저장소 밖에 둔다 (json_parity 와 같은 곳).
맥의 node·swiftc 가 있어야 한다.
"""
import json, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORK = Path.home() / "Library/Caches/naeilo-android/num_parity"
SKIP = {"ms"}   # 시간은 비교하지 않는다
# 오차 허용 (2026-10-21 사용자 결정): exp·log 를 거친 금액·비율은 맥 libm 과 JVM 이 끝자리에서 달라 비트 동일이 불가능하다.
# 아래 칸만 상대 1e-12 이내면 같다고 본다. 확률·날짜·개수·사건 수·배당·세금은 차이 0 그대로
TOL = 1e-12
TOL_KEYS = {"bands", "terminal", "mdd_median", "blend", "fxAt", "factorMove", "replay"}
# D 단계 augment: 아이폰은 날짜→수익률을 사전(순서 없음)에 담아 더하는 순서가 실행마다 달라 끝자리가 흔들린다. 같은 상대 1e-12
TOL_KEYS |= {"augment"}
# 전망 요인 smooth: exp(g + ½·Δσ²) − 1 을 거친다 (Darwin libm 과 JVM exp 끝자리). 같은 상대 1e-12
TOL_KEYS |= {"smooth"}
# E 단계 첫 질문 예보 카드: value = exp(g·t + z·σ·√t) 금액, fanStart = 그 금액으로 정한 화면 자리. 같은 상대 1e-12
TOL_KEYS |= {"value", "fanStart"}
# G2 내 길: 목표 길(need)은 pow 를 거친다 (Darwin libm 과 Java pow 끝자리)
TOL_KEYS |= {"need"}
# G2 외부 요인·금리: 지난 종가 회귀(log)·요인 변화 exp(β·log(1+x))−1·한 달 변화 exp(sd)−1, 닫힌 식 log·exp·normCDF, 식단 시장 수익 exp. 같은 상대 1e-12
TOL_KEYS |= {"extBeta", "extMove", "extShock", "extRanks", "extRows", "rateClosed", "rateProb", "rateMkt"}
# J 판 깨기: 공·판·물건 위치는 sin·cos 를 거친다 (Darwin libm 과 JVM 끝자리, 최대 상대 3.5e-16). 칸·점수·소리·물건은 차이 0 그대로
TOL_KEYS |= {"ballXY", "padX", "dropXY"}
# 흐름 탭 DRNK 샘플 캔들: 시드 난수를 log·sqrt·cos·exp 로 정규분포로 바꾼다 (Darwin libm 과 JVM 끝자리). 추세 점·지지저항 가격은 그 캔들 값. 같은 상대 1e-12
# (패턴 종류·구간·진하게·추세 띠는 차이 0 그대로)
TOL_KEYS |= {"candles", "pivots", "levels", "price"}


def run(*cmd, cwd=ROOT):
    r = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
    if r.returncode:
        sys.exit(f"실패: {' '.join(map(str, cmd))}\n{r.stdout[-2000:]}\n{r.stderr[-2000:]}")
    return r.stdout


def gen():
    mc, calc, bins = WORK / "mc", WORK / "calc", WORK / "bin"
    for d in (mc, calc, bins): d.mkdir(parents=True, exist_ok=True)
    for f in WORK.glob("*/kotlin*.json"): f.unlink()
    # 전망 몬테카를로 (mc_parity 와 같은 입력)
    run("node", "tools/mc_parity.mjs", "gen", str(mc))
    run("swiftc", "-O", "ios-app/Naeilo/Data/MonteCarlo.swift", "ios-app/tools/mc_parity/main.swift", "-o", str(bins / "mc"))
    print(run(str(bins / "mc"), str(mc)).strip().splitlines()[0])
    # 화면 계산 (calc_parity 와 같은 입력): 배당·세금은 calc_parity 앱 쪽, 그 밖의 SiteCalc 식은 sc_main.swift
    run("node", "tools/calc_parity.mjs", "gen", str(calc))
    run("swiftc", "-O", str(calc / "CalcSrc.swift"), "ios-app/tools/calc_parity/main.swift", "-o", str(bins / "calc"))
    run(str(bins / "calc"), str(calc))
    run("swiftc", "-O", str(calc / "CalcSrc.swift"), "tools/num_parity/sc/main.swift", "-o", str(bins / "sc"))
    print(run(str(bins / "sc"), str(calc)).strip())
    # D 단계 (시세·동기화): 앱 원본에서 꺼낸 DSrc.swift + 고정 입력, 웹 esDerive·AES-GCM 도 같은 입력으로
    d = WORK / "d"; d.mkdir(parents=True, exist_ok=True)
    run("python3", "tools/num_parity/d/dsrc.py", str(d))
    run("swiftc", "-O", "ios-app/Naeilo/Data/MonteCarlo.swift", str(d / "DSrc.swift"), "tools/num_parity/d/main.swift", "-o", str(bins / "d"))
    print(run(str(bins / "d"), str(d)).strip())
    print(run("node", "tools/num_parity/d/web.mjs", str(d)).strip())
    # E 단계 (홈·설정): 첫 질문 예보 카드·인물 말투·세금 규칙·홈 글 (e/esrc.py 가 원본 줄을 꺼낸 main.swift)
    e = WORK / "e"; e.mkdir(parents=True, exist_ok=True)
    run("python3", "tools/num_parity/e/esrc.py", str(e))
    run("swiftc", "-O", str(e / "main.swift"), "-o", str(bins / "e"))
    print(run(str(bins / "e"), str(e)).strip())
    # F 단계 (종목): 매매·고치기 셈과 글, 만원·가격 글, 숫자 글 (f/fsrc.py)
    f = WORK / "f"; f.mkdir(parents=True, exist_ok=True)
    run("python3", "tools/num_parity/f/fsrc.py", str(f))
    run("swiftc", "-O", str(f / "main.swift"), "-o", str(bins / "f"))
    print(run(str(bins / "f"), str(f)).strip())
    # G 단계 (분석): 전망 금액 글·햇달·빈도·눈금, 읽기 쉬운 금액 (g/gsrc.py)
    g = WORK / "g"; g.mkdir(parents=True, exist_ok=True)
    run("python3", "tools/num_parity/g/gsrc.py", str(g))
    run("swiftc", "-O", str(g / "main.swift"), "-o", str(bins / "g"))
    print(run(str(bins / "g"), str(g)).strip())
    # H 단계 (미션·리마인드): 주 이름·끝난 주·예보 판정·일정 날짜 반복·글 첫 문장 (h/hsrc.py)
    h = WORK / "h"; h.mkdir(parents=True, exist_ok=True)
    run("python3", "tools/num_parity/h/hsrc.py", str(h))
    run("swiftc", "-O", str(h / "main.swift"), "-o", str(bins / "h"))
    print(run(str(bins / "h"), str(h)).strip())
    # I 단계 (인물 말투): 대사 첫 줄 번호·차례 (i/isrc.py)
    i = WORK / "i"; i.mkdir(parents=True, exist_ok=True)
    run("python3", "tools/num_parity/i/isrc.py", str(i))
    run("swiftc", "-O", str(i / "main.swift"), "-o", str(bins / "i"))
    print(run(str(bins / "i"), str(i)).strip())
    # J 단계 (판 깨기 게임 엔진): 같은 시드·대본으로 한 판씩 (j/jsrc.py)
    j = WORK / "j"; j.mkdir(parents=True, exist_ok=True)
    run("python3", "tools/num_parity/j/jsrc.py", str(j))
    run("swiftc", "-O", str(j / "main.swift"), "-o", str(bins / "j"))
    print(run(str(bins / "j"), str(j)).strip())
    # J 단계 (파도 타기 한 판 다시 돌리기): w/wsrc.py
    w = WORK / "w"; w.mkdir(parents=True, exist_ok=True)
    run("python3", "tools/num_parity/w/wsrc.py", str(w))
    run("swiftc", "-O", str(w / "main.swift"), "-o", str(bins / "w"))
    print(run(str(bins / "w"), str(w)).strip())
    # 흐름 탭: DRNK 샘플 캔들·추세·지지저항·패턴 (flow/flowsrc.py)
    fl = WORK / "flow"; fl.mkdir(parents=True, exist_ok=True)
    run("python3", "tools/num_parity/flow/flowsrc.py", str(fl))
    run("swiftc", "-O", str(fl / "main.swift"), "-o", str(bins / "flow"))
    print(run(str(bins / "flow"), str(fl)).strip())
    print(f"입력·Swift 결과: {WORK}")


def walk(a, b, path, out, tol=False):
    """a(Swift)·b(Kotlin) 을 끝까지 내려가며 같은지 센다. 숫자는 float 로 정확히 같아야 한다"""
    if isinstance(a, dict):
        if not isinstance(b, dict): out["bad"].append(f"{path}: 모양 다름"); return
        for k in sorted(set(a) | set(b)):
            if k in SKIP: continue
            if k not in a or k not in b: out["bad"].append(f"{path}.{k}: {'Swift' if k in a else 'Kotlin'} 에만 있음"); continue
            walk(a[k], b[k], f"{path}.{k}", out, tol or k in TOL_KEYS)
    elif isinstance(a, list):
        if not isinstance(b, list) or len(a) != len(b): out["bad"].append(f"{path}: 길이 다름 {len(a)} · {len(b) if isinstance(b, list) else b}"); return
        for i, (x, y) in enumerate(zip(a, b)): walk(x, y, f"{path}[{i}]", out, tol)
    else:
        out["n"] += 1
        if isinstance(a, (int, float)) and not isinstance(a, bool) and isinstance(b, (int, float)) and not isinstance(b, bool):
            same = float(a) == float(b)
            if not same and tol and abs(a - b) <= TOL * max(abs(a), abs(b)): same = True; out["tol"] += 1
        else:
            same = a == b
        if not same: out["bad"].append(f"{path}: Swift {a!r} · Kotlin {b!r}")


def check():
    pairs = [("mc", "app.json", "kotlin.json", None),
             ("calc", "app.json", "kotlin.json", ("consts", "divs", "tax")),
             ("calc", "sc_app.json", "kotlin_sc.json", None),
             ("d", "app.json", "kotlin.json", None),
             ("d", "web.json", "kotlin.json", ("derive", "aes")),
             ("e", "app.json", "kotlin.json", None),
             ("f", "app.json", "kotlin.json", None),
             ("g", "app.json", "kotlin.json", None),
             ("h", "app.json", "kotlin.json", None),
             ("i", "app.json", "kotlin.json", None),
             ("j", "app.json", "kotlin.json", None),
             ("w", "app.json", "kotlin.json", None),
             ("flow", "app.json", "kotlin.json", None)]
    total, bad = 0, []
    for d, sw, kt, keys in pairs:
        a_p, b_p = WORK / d / sw, WORK / d / kt
        if not a_p.exists(): sys.exit(f"{a_p} 없음: gen 을 먼저")
        if not b_p.exists(): sys.exit(f"{b_p} 없음: ./gradlew :app:testReleaseUnitTest 를 먼저")
        a, b = json.loads(a_p.read_text()), json.loads(b_p.read_text())
        if d == "d" and keys:   # 웹은 비밀번호·암호만 (Kotlin 의 back 칸은 아이폰 암호문을 푼 글자라 웹에 없다)
            a = {k: a[k] for k in keys}; b = {k: b[k] for k in keys}
            b["aes"] = [{"c": x["c"]} for x in b["aes"]]
        elif keys:   # calc 의 app.json 은 C 밖(환율 고르기·금리·위젯)도 담는다. SiteCalc 몫만 본다
            a = {k: a[k] for k in keys}
            # 배당의 fx·sumFx, 세금의 gain 은 Market·화면 쪽 값이라 Kotlin 이 Swift 값을 받아 쓴다. 비교는 SiteCalc 결과만
            a["divs"] = [{"name": x["name"], "out": x.get("out")} for x in a["divs"]]
            a["tax"] = [{"name": x["name"], "tax": x["tax"]} for x in a["tax"]]
        out = {"n": 0, "bad": [], "tol": 0}
        walk(a, b, f"{d}/{sw}", out)
        print(f"{d}/{sw}: 비교 {out['n']}개, 다름 {len(out['bad'])}개 (끝자리 허용 {out['tol']}개)")
        total += out["n"]; bad += out["bad"]
    for x in bad[:40]: print("다름", x)
    print(f"숫자 맞추기: 비교 {total}개, 통과 {total - len(bad)}개, 다름 {len(bad)}개")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    {"gen": gen, "check": check}.get(sys.argv[1] if len(sys.argv) > 1 else "", lambda: sys.exit(__doc__))()
