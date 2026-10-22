// 내 몫 실적 계산 기준 (웹 입구 해 보기·아이폰 Earnings.swift·안드로이드 Earnings.kt 가 같은 답을 내야 한다).
// 입력: data/earnings/{종목}.json (tools/earnings.py), 지금 수량, 오늘 환율(원/달러). 자료가 없으면(ETF) null → 화면은 줄표.
// 규칙 (docs/decisions.md 2026-10-22):
// - 주당 값 × 지금 수량. 모든 분기 같은 수량. 주당 = 그 분기 값 ÷ 그 분기 신고서 첫 장 주식 수
// - 통화는 회사 신고 통화. 원화는 내 몫 순이익 옆 한 곳만
// - 갈래: 원가·연구비·판관비가 다 있으면 3갈래, 남는 돈(이자·세금·기타) = 매출 − 셋 − 순이익 은 물안개. 하나라도 없으면 '쓴 돈' 1갈래(물안개 없음)
// - 적자: 순이익 < 0. 모자란 만큼 = −순이익
// - 작년 같은 분기 = 끝 날짜가 365±25일 앞인 분기

const DAY = 86400000;

export function mine(q, qty) {
  const k = qty / q.shares, m = (v) => (v == null ? null : v * k);
  return { revenue: m(q.revenue), cogs: m(q.cogs), rnd: m(q.rnd), sga: m(q.sga), net: m(q.net) };
}

export function sameLastYear(qs, q) {
  const e = Date.parse(q.end);
  return qs.find((x) => Math.abs(e - Date.parse(x.end) - 365 * DAY) <= 25 * DAY) || null;
}

/** 작년 대비 비율 글: "+3%" / "−43%" / "작년과 같음". 작년이나 올해가 손실이면 null (비율이 뜻이 없다) */
export function yoyText(now, before) {
  if (now == null || before == null || before <= 0 || now < 0) return null;
  const r = now / before - 1;
  if (Math.abs(r) < 0.005) return "작년과 같음";
  return (r > 0 ? "+" : "−") + Math.round(Math.abs(r) * 100) + "%";
}

/** 돈 글: 10 이상 정수, 1 이상 소수 한 자리, 그 아래 두 자리. 음수는 "−" */
export function money(v, currency) {
  const a = Math.abs(v), n = a >= 10 ? Math.round(a).toString() : a >= 1 ? a.toFixed(1) : a.toFixed(2);
  return (v < 0 ? "−" : "") + n + (currency === "USD" ? "달러" : " " + currency);
}

/** 원화 어림: "약 21만" / "약 8천" / "약 300원" */
export function krw(v) {
  const a = Math.abs(v), s = v < 0 ? "−" : "";
  if (a >= 10000) return "약 " + s + Math.round(a / 10000).toLocaleString("en-US") + "만";
  if (a >= 1000) return "약 " + s + Math.round(a / 1000) + "천";
  return "약 " + s + Math.round(a / 10) * 10 + "원";
}

/** 강 팝업·목록 막대·홈 카드가 쓰는 값 한 벌. at = 몇 번째 분기(0 = 가장 옛 분기). at 이 없으면 마지막 분기 + 기다리는 중, 있으면 그 분기를 다 찬 강으로(지난 분기 넘기기) */
export function view(j, qty, fx, at) {
  if (!j || !j.quarters || !j.quarters.length) return null;
  const qs = j.quarters, last = qs.length - 1;
  const i = at == null ? last : Math.max(0, Math.min(last, at));
  const q = qs[i], ly = sameLastYear(qs, q);
  const me = mine(q, qty), lm = ly ? mine(ly, qty) : null;
  const three = q.cogs != null && q.rnd != null && q.sga != null;
  const branches = three
    ? [{ k: "cogs", v: me.cogs }, { k: "sga", v: me.sga }, { k: "rnd", v: me.rnd }]
    : [{ k: "spent", v: me.revenue - me.net }];
  const mist = three ? me.revenue - me.cogs - me.rnd - me.sga - me.net : 0;
  const loss = me.net < 0;
  return {
    symbol: j.symbol, currency: j.currency, months: q.months, label: q.label,
    lastYearMonths: ly ? "작년 " + ly.months : null,
    pending: j.pending && at == null ? j.pending.announced : null,
    revenue: me.revenue, net: me.net, branches, mist, loss, short: loss ? -me.net : 0,
    // 수위표: 그 분기 앞 4분기 내 몫 매출 (작년 같은 분기는 진하게). 옛 분기는 앞에 있는 만큼만
    gauge: qs.slice(Math.max(0, i - 4), i).map((x) => ({ label: x.label, value: mine(x, qty).revenue, lastYear: x === ly })),
    revYoY: yoyText(me.revenue, lm && lm.revenue),
    // 홈 카드·작은 막대: 내 몫 순이익 작년 대비
    netYoY: yoyText(me.net, lm && lm.net),
    lastYearNet: lm ? lm.net : null,
    netText: money(me.net, j.currency),
    netKrw: !loss && j.currency === "USD" && fx ? krw(me.net * fx) : null,
  };
}
