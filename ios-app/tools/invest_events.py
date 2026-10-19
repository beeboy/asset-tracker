"""인물 자세히 보기 · 투자하는 갈래의 사건 카드 (강했던 때 / 약했던 때) 미니 그래프를 미리 계산한다.

  python3 ios-app/tools/invest_events.py          # data/macro/sp_monthly.json → ios-app/Naeilo/Data/InvestEvents.swift

월간 S&P500(실러, 그 달 일별 종가 평균) 하나로 인물 규칙을 월 단위로 단순화해 굴린다. 현금은 연 2%로 셈한다.
- 세리: 주식 60 · 현금 40, 출발한 달마다 1년에 한 번 ±5%p 넘게 벗어났으면 60으로 되돌림.   그냥 둠 = 60·40 그대로
- 시오: 주식 80 · 현금 20, 주식 고점 대비 −20%에 현금 절반, −30%에 나머지. 넣은 뒤 고점을 다시 넘으면 현금 20%로 다시 채움.   그냥 둠 = 80·20 그대로
- 선배: 주식 60 · 현금 40, 분기마다 처음보다 10%p 넘게 커지거나 작아지면 처음 비율로.   그냥 둠 = 60·40 그대로
- 이르: 주식 100, 10개월 평균선보다 3% 넘게 내려가면 현금, 3% 넘게 올라오면 다시 주식 (200일선의 월간판).   그냥 둠 = 주식 100
- 수아: 매달 같은 금액, 지난달 −5% 넘게 빠졌으면 1.5배, −15% 넘게 2배, +10% 넘게 오르면 0.7배.   그냥 둠 = 매달 같은 금액
  수아 선은 넣은 돈 대비 평가액 (100 = 본전)
결과는 수익률·최대 낙폭 같은 비율로만 쓴다. 매매 내역은 만들지 않는다.
"""
from __future__ import annotations

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "data" / "macro" / "sp_monthly.json"
OUT = ROOT / "ios-app" / "Naeilo" / "Data" / "InvestEvents.swift"
CASH = 1.02 ** (1 / 12)

# (인물, 강함/약함, 제목, 시기 글, 시작 달, 끝 달, 설명)  설명은 계산 결과와 맞는지 아래 check 로 확인한다
EVENTS = [
    ("seri", "strong", "닷컴 붕괴", "2000~2002", "1996-01", "2003-03",
     "해마다 60%로 되돌려 붕괴 때 덜 빠짐"),
    ("seri", "strong", "금융위기", "2008~2009", "2007-10", "2013-10",
     "빠진 해 기념일에 주식 채움 → 회복 온전히"),
    ("seri", "weak", "긴 상승장", "2013~2019", "2013-01", "2019-12",
     "오래 오르는 장에서 해마다 덜어 덜 벎"),
    ("seri", "weak", "코로나 뒤 상승과 AI 랠리", "2019~2025", "2019-01", "2025-06",
     "계속 \"덜 갖기\" · 낙폭 얕고 수익 작음"),
    ("sio", "strong", "금리 인상기", "2022", "2021-06", "2024-06",
     "−20%에 현금 절반 → 2023 회복 때 효과"),
    ("sio", "strong", "금융위기", "2008~2009", "2007-06", "2010-12",
     "−20%·−30%에 나눠 넣어 덜 잃음"),
    ("sio", "weak", "닷컴 붕괴", "2000~2002", "1999-06", "2003-12",
     "다 넣은 뒤 더 떨어짐 · 총알 일찍 소진"),
    ("seonbae", "strong", "닷컴 고점", "2000", "1995-01", "2003-03",
     "부푼 몫을 분기마다 덜어 붕괴를 덜 맞음"),
    ("seonbae", "strong", "금융위기", "2008~2009", "2007-06", "2013-06",
     "줄어든 쪽도 채워 회복을 받음"),
    ("seonbae", "weak", "닷컴 전 수직 상승", "1995~1999", "1995-01", "1999-12",
     "분기마다 덜어 큰 상승의 일부만"),
    ("seonbae", "weak", "코로나 뒤 상승과 AI 랠리", "2020~2024", "2020-04", "2024-12",
     "계속 덜어 오르는 몫을 덜 가짐"),
    ("ir", "strong", "금융위기", "2008", "2007-01", "2010-06",
     "평균선 아래서 나와 큰 하락 대부분 피함"),
    ("ir", "strong", "금리 인상기", "2022", "2021-06", "2023-12",
     "평균선 깨고 나와 깊은 하락 피함"),
    ("ir", "weak", "코로나 V자", "2020", "2019-06", "2021-06",
     "판 뒤 급반등 · 더 비싸게 다시 삼"),
    ("ir", "weak", "횡보장", "2015~2016", "2015-01", "2016-12",
     "들락날락하며 조금씩 잃음"),
    ("sua", "strong", "금리 인상기", "2022", "2022-01", "2024-12",
     "떨어진 달 더 넣어 싸게 더 담음"),
    ("sua", "strong", "금융위기", "2008~2009", "2008-01", "2011-12",
     "바닥 근처 1.5~2배 적립 → 회복의 씨앗"),
]
# 그래프 없이 숫자로만 보여 주는 카드 (나스닥·닛케이 장기 자료 없음)
FACTS = [
    ("sio", "weak", "나스닥", "2000~2002", "−78%",
     "다 넣어도 바닥은 훨씬 아래"),
    ("sua", "weak", "닛케이", "1989~2024", "34년",
     "고점 회복에 34년 · 한 시장 적립은 한 세대 걸릴 수도"),
    ("sua", "weak", "적립이 끊길 때", "소득이 흔들릴 때", "멈춤",
     "넣을 돈이 끊기면 규칙도 멈춤"),
]


def months(a: str, b: str, frm: str) -> tuple[int, int]:
    def idx(d): return (int(d[:4]) - int(frm[:4])) * 12 + int(d[5:7]) - int(frm[5:7])
    return idx(a), idx(b)


def run(who: str, P: list[float], i0: int, i1: int) -> tuple[list[float], list[float]]:
    """인물 규칙 선과 그냥 둠 선 (출발 = 100)"""
    S = P[i0:i1 + 1]
    n = len(S)
    rule, hold = [100.0], [100.0]
    if who in ("seri", "seonbae"):
        w0, band, every = 0.6, (0.05 if who == "seri" else 0.10), (12 if who == "seri" else 3)
        s, c = 60.0, 40.0
        hs, hc = 60.0, 40.0
        for t in range(1, n):
            g = S[t] / S[t - 1]
            s *= g; c *= CASH; hs *= g; hc *= CASH
            if t % every == 0:
                V = s + c
                if abs(s / V - w0) > band:
                    s, c = V * w0, V * (1 - w0)
            rule.append(s + c); hold.append(hs + hc)
    elif who == "sio":
        s, c = 80.0, 20.0
        hs, hc = 80.0, 20.0
        peak, stock_idx, stage = S[0], S[0], 0
        for t in range(1, n):
            g = S[t] / S[t - 1]
            s *= g; c *= CASH; hs *= g; hc *= CASH
            if S[t] > peak:
                peak = S[t]
                if stage > 0:          # 넣었던 하락을 지나 고점을 다시 넘으면 현금을 20%까지 다시 채운다
                    V = s + c
                    s, c = V * 0.8, V * 0.2
                stage = 0
            dd = S[t] / peak - 1
            if stage == 0 and dd <= -0.20 and c > 0:
                put = c / 2; s += put; c -= put; stage = 1
            if stage == 1 and dd <= -0.30 and c > 0:
                s += c; c = 0.0; stage = 2
            rule.append(s + c); hold.append(hs + hc)
    elif who == "ir":
        inn = True
        v, h = 100.0, 100.0
        for t in range(1, n):
            g = S[t] / S[t - 1]
            v *= g if inn else CASH
            h *= g
            j = i0 + t
            sma = sum(P[j - 9:j + 1]) / 10
            if inn and P[j] < sma * 0.97: inn = False
            elif not inn and P[j] > sma * 1.03: inn = True
            rule.append(v); hold.append(h)
    elif who == "sua":
        ur, uh, invr, invh = 1 / S[0], 1 / S[0], 1.0, 1.0
        for t in range(1, n):
            m = S[t - 1] / S[t - 2] - 1 if t >= 2 else 0.0
            k = 2.0 if m <= -0.15 else 1.5 if m <= -0.05 else 0.7 if m >= 0.10 else 1.0
            ur += k / S[t]; invr += k
            uh += 1 / S[t]; invh += 1
            rule.append(100 * ur * S[t] / invr); hold.append(100 * uh * S[t] / invh)
    return rule, hold


def mdd(x: list[float]) -> float:
    pk, m = x[0], 0.0
    for v in x:
        pk = max(pk, v); m = min(m, v / pk - 1)
    return m


def main() -> None:
    sp = json.loads(SRC.read_text(encoding="utf-8"))
    P, frm = sp["p"], sp["from"]
    out = []
    for who, kind, title, when, a, b, desc in EVENTS:
        i0, i1 = months(a, b, frm)
        r, h = run(who, P, i0, i1)
        rr, hr = r[-1] / 100 - 1, h[-1] / 100 - 1
        ok = (rr > hr or mdd(r) > mdd(h) + 0.03) if kind == "strong" else (rr < hr)
        print(f"{who:8} {kind:6} {title:14} 규칙 {rr:+.1%} (낙폭 {mdd(r):.0%})  그냥 {hr:+.1%} (낙폭 {mdd(h):.0%})  {'OK' if ok else '!! 설명과 다름'}")
        out.append((who, kind, title, when, a, b, desc, r, h))
    lines = [
        "import Foundation",
        "",
        "// 생성 파일: ios-app/tools/invest_events.py 가 data/macro/sp_monthly.json (실러 월간 S&P500)으로 만든다. 손으로 고치지 않는다.",
        "// 인물 규칙을 월 단위로 단순화해 굴린 선(rule)과 그냥 둔 선(hold), 출발 = 100. 규칙은 그 파일 머리말 참고.",
        "// 카드마다 따로 둔다 (긴 배열 식 하나는 컴파일러 타입 추론이 느리다)",
        "",
        "extension Invest {",
    ]
    f = lambda xs: "[" + ", ".join(f"{v:.1f}" for v in xs) + "]"
    q = lambda x: x.replace("\\", "\\\\").replace('"', '\\"')   # Swift 문자열 안 따옴표
    names = []
    for i, (who, kind, title, when, a, b, desc, r, h) in enumerate(out):
        names.append(f"e{i}")
        lines.append(f'    private static let e{i} = Event(who: "{who}", strong: {str(kind == "strong").lower()}, title: "{title}", when: "{when}", from: "{a}", to: "{b}",')
        lines.append(f'        text: "{q(desc)}",')
        lines.append(f"        rule: {f(r)} as [Double],")
        lines.append(f"        hold: {f(h)} as [Double])")
    for j, (who, kind, title, when, big, desc) in enumerate(FACTS):
        names.append(f"f{j}")
        lines.append(f'    private static let f{j} = Event(who: "{who}", strong: {str(kind == "strong").lower()}, title: "{title}", when: "{when}", from: "", to: "", text: "{q(desc)}", rule: [], hold: [], fact: "{big}")')
    lines += [f"    static let events: [Event] = [{', '.join(names)}]",
              f'    static let eventsSource = "S&P500 월간 {frm[:4]}~{sp["to"][:4]} (Robert J. Shiller)"', "}", ""]
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print("저장:", OUT)


if __name__ == "__main__":
    main()
