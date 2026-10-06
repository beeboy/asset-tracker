// 화면 로직: 입력 편집, 저장, 시세 수집 요청, 화면 그리기.
(function () {
  "use strict";
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => [...document.querySelectorAll(s)];
  const C = ["var(--c1)", "var(--c2)", "var(--c3)", "var(--c4)", "var(--c5)", "var(--c6)", "var(--c7)"];
  const DEFAULT_MODEL = {
    scenario: "blend", trust: 50, n_paths: 3000, seed: 20261004, history_years: 3, prior_mu: 10, prior_tau: 15, conservative_mu: 4,
    new_listing_vol: 60, default_vol: 40, default_corr: 0.3, t_dof: 5, fx_drift: 0, fx_vol_mult: 1, rebalance_yearly: false, earnings_adjust: true,
  };
  const MODEL_FIELDS = [
    ["history_years", "통계 기간 (년)", "변동성·상관·과거 평균을 이 기간 일봉으로 계산"],
    ["prior_mu", "사전 기대수익률 (연 %)", "개별 주식 장기 기대수익의 기준값. 과거 평균을 이 값 쪽으로 당김"],
    ["prior_tau", "사전값 불확실성 (연 %)", "작을수록 사전값을 더 믿음 (과거 평균 영향 감소)"],
    ["conservative_mu", "보수 시나리오 수익률 (연 %)", "보수 시나리오에서 모든 위험자산에 적용"],
    ["new_listing_vol", "신규 상장 기준 변동성 (연 %)", "이력 1년 미만 종목의 변동성을 이 값과 섞음"],
    ["default_vol", "이력 없는 종목 변동성 (연 %)", "시세 이력이 없을 때 사용"],
    ["default_corr", "기본 상관계수", "이력이 짧을 때 종목 간 상관을 이 값 쪽으로 당김"],
    ["t_dof", "충격 분포 자유도", "작을수록 꼬리가 두꺼움 (급등락 빈도 증가). 3 이상"],
    ["fx_drift", "환율 연 기대 변화 (%)", "원/달러 환율 연간 기대 변화. +면 원화 약세"],
    ["fx_vol_mult", "환율 변동성 배수", "과거 환율 변동성에 곱함"],
    ["seed", "난수 시드", "같은 시드면 같은 결과 (비교용)"],
    ["earnings_adjust", "반복 사건만큼 평소 변동성 줄이기", "분기 반복 사건(실적)의 분산을 평소 변동성에서 빼 이중 계산 방지", "bool"],
  ];
  const KINDS = ["실적", "규제", "보호예수 해제", "소송", "신제품·행사", "리콜", "지수 편입·제외", "차량 인도량", "FOMC 금리 결정", "물가(CPI) 발표", "고용 지표", "금리 급등",
    "OPEC+ 회의", "유가 급등", "금값 급등", "원자재 급등", "선거", "관세·정책", "전쟁·지정학", "환율 급변", "한국은행 금리 결정", "물가 급등", "공급망 차질", "수출 규제·제재",
    "옵션 만기·리밸런싱", "외국인·기관 매매", "기타"];
  // 외부 요인 묶음. 요인 사건(factor)은 요인 충격 × 종목별 민감도만큼 움직인다
  const CATS = [["rate", "금리·통화정책"], ["fx", "환율"], ["infl", "인플레이션"], ["oil", "유가"], ["gold", "금값"], ["cmdty", "기타 금속·곡물"],
    ["politics", "선거·정책"], ["war", "전쟁·분쟁"], ["trade", "무역·제재"], ["corp", "실적·공시"], ["product", "신제품·리콜"], ["tsla", "주요 KPI (인도량 등)"], ["flow", "기관·외국인 매매 동향"]];
  // 3개 박스(거시·기업·수급) > 버튼(소분류) > 묶음(cat)
  const XGROUPS = [
    { k: "macro", n: "거시", icon: "📊", en: "Macro", subs: [["fin", "금융·통화", ["rate", "fx", "infl"]], ["comm", "원자재", ["oil", "gold", "cmdty"]], ["geo", "정치·지정학", ["politics", "war", "trade"]]] },
    { k: "corp", n: "기업", icon: "🏢", en: "Company", subs: [["earn", "실적·공시", ["corp"]], ["product", "신제품·리콜", ["product"]], ["kpi", "주요 KPI", ["tsla"]]] },
    { k: "flow", n: "수급", icon: "💰", en: "Flow", subs: [["inst", "기관·외국인 매매 동향", ["flow"]]] },
  ];
  const XSUBS = XGROUPS.flatMap((g) => g.subs.map(([k, n, cats]) => ({ k, n, cats, g })));
  const grpCats = (g) => XGROUPS.find((x) => x.k === g)?.subs.flatMap((s) => s[2]) || [];
  const grpOf = (c) => XGROUPS.find((g) => g.subs.some((s) => s[2].includes(c)))?.k || "corp";
  const xfName = (k) => XGROUPS.find((g) => g.k === k)?.n || XSUBS.find((s) => s.k === k)?.n || k;
  const catName = (c) => (CATS.find(([k]) => k === c) || [, c])[1];
  const FACTORS = {
    mkt: { sym: "SPY", name: "미국 시장 (S&P500, SPY)", unit: "%", shock: -5, shockTxt: "S&P500 -5%" },
    rate: { sym: "^TNX", name: "미국 10년 국채 금리", unit: "bp", shock: 25, shockTxt: "10년 금리 +0.25%p" },
    oil: { sym: "CL=F", name: "WTI 유가", unit: "%", shock: 10, shockTxt: "유가 +10%" },
    gold: { sym: "GC=F", name: "금 선물", unit: "%", shock: 10, shockTxt: "금값 +10%" },
    cmdty: { sym: "DBC", name: "원자재 지수 (DBC)", unit: "%", shock: 10, shockTxt: "원자재 +10%" },
  };
  const FACTOR_SYMS = Object.values(FACTORS).map((f) => f.sym);
  const CAT_FACTOR = { rate: "rate", infl: "rate", oil: "oil", gold: "gold", cmdty: "cmdty", politics: "mkt", war: "mkt", trade: "mkt", flow: "mkt" };
  // 환율 묶음은 요인 민감도 대신 원/달러(KRW=X)에 직접 반영한다 (사건 대상 FX)
  const FX_F = { sym: "KRW=X", name: "원/달러 환율", unit: "%", shock: 5, shockTxt: "원/달러 +5%" };
  const KIND_CAT = [[/신제품|리콜/, "product"], [/인도량|KPI/, "tsla"], [/환율|한국은행/, "fx"], [/CPI|물가|인플레/, "infl"], [/FOMC|고용|금리/, "rate"], [/OPEC|유가/, "oil"], [/금값/, "gold"], [/원자재/, "cmdty"],
    [/공급망/, "corp"], [/관세|수출|제재/, "trade"], [/선거/, "politics"], [/전쟁/, "war"], [/만기|리밸런싱|매매|수급|지수 편입/, "flow"]];
  const catOfKind = (k) => (KIND_CAT.find(([re]) => re.test(k)) || [, "corp"])[1];
  // 기본 외부 요인 사건 (날짜·크기는 추정. 시장 요인은 S&P500 기준 %, 금리는 bp)
  const EXT_EVENTS = [
    { id: "x_fomc", cat: "rate", factor: "mkt", target: "ALL", kind: "FOMC 금리 결정", date: "2026-10-28", repeat: "6w", prob: 100, mean: 0, sd: 1.0, vol_mult: 1, vol_days: 0, note: "6주마다 (날짜 근사). 발표일 S&P500 ±1.0% 가정" },
    { id: "x_cpi", cat: "infl", factor: "mkt", target: "ALL", kind: "물가(CPI) 발표", date: "2026-10-14", repeat: "monthly", prob: 100, mean: 0, sd: 0.8, vol_mult: 1, vol_days: 0, note: "매달 중순 (날짜 근사). S&P500 ±0.8%" },
    { id: "x_jobs", cat: "rate", factor: "mkt", target: "ALL", kind: "고용 지표", date: "2026-11-06", repeat: "monthly", prob: 100, mean: 0, sd: 0.6, vol_mult: 1, vol_days: 0, note: "매달 첫 금요일 무렵. S&P500 ±0.6%" },
    { id: "x_rate", cat: "rate", factor: "rate", target: "ALL", kind: "금리 급등", date: "2027-03-15", repeat: "yearly", prob: 20, mean: 40, sd: 20, vol_mult: 1.2, vol_days: 20, note: "해마다 20% 확률로 10년 금리 +0.4%p (bp 단위)" },
    { id: "x_bok", cat: "fx", target: "FX", kind: "한국은행 금리 결정", date: "2026-10-22", repeat: "6w", prob: 100, mean: 0, sd: 0.4, vol_mult: 1, vol_days: 0, note: "해마다 8번 (날짜 근사). 원/달러 ±0.4%" },
    { id: "x_fx", cat: "fx", target: "FX", kind: "환율 급변", date: "2027-01-15", repeat: "yearly", prob: 25, mean: 0, sd: 5, vol_mult: 1.3, vol_days: 20, note: "해마다 25% 확률로 원/달러 ±5% (원화 약세면 해외 주식 평가액 증가)" },
    { id: "x_infl", cat: "infl", factor: "rate", target: "ALL", kind: "물가 급등", date: "2027-06-10", repeat: "yearly", prob: 15, mean: 30, sd: 15, vol_mult: 1.2, vol_days: 20, note: "해마다 15% 확률로 물가 재상승 → 10년 금리 +0.3%p (bp 단위)" },
    { id: "x_dlv", cat: "tsla", target: "TSLA", kind: "차량 인도량", date: "2027-01-04", repeat: "quarterly", prob: 100, mean: 0, sd: 4, vol_mult: 1, vol_days: 0, note: "분기 첫 달 2일 무렵 발표 (날짜 추정)" },
    { id: "x_gtc", cat: "product", target: "NVDA", kind: "신제품·행사", date: "2027-03-16", repeat: "yearly", prob: 100, mean: 0, sd: 4, vol_mult: 1, vol_days: 0, note: "GTC 신제품 발표 (날짜 추정)" },
    { id: "x_tprod", cat: "product", target: "TSLA", kind: "신제품·행사", date: "2027-06-15", repeat: "yearly", prob: 60, mean: 0, sd: 6, vol_mult: 1, vol_days: 0, note: "로보택시·옵티머스 등 공개 행사 (가정)" },
    { id: "x_recall", cat: "product", target: "TSLA", kind: "리콜", date: "2027-02-15", repeat: "yearly", prob: 40, mean: -2, sd: 3, vol_mult: 1, vol_days: 0, note: "대규모 리콜·조사 (가정)" },
    { id: "x_ship", cat: "product", target: "SPCX", kind: "신제품·행사", date: "2026-12-15", repeat: "quarterly", prob: 70, mean: 0, sd: 5, vol_mult: 1, vol_days: 0, note: "스타십 발사·신규 서비스 (가정)" },
    { id: "x_opec", cat: "oil", factor: "oil", target: "ALL", kind: "OPEC+ 회의", date: "2026-11-30", repeat: "semi", prob: 100, mean: 0, sd: 4, vol_mult: 1, vol_days: 0, note: "반년마다. 유가 ±4% (날짜 추정)" },
    { id: "x_oil", cat: "oil", factor: "oil", target: "ALL", kind: "유가 급등", date: "2027-05-17", repeat: "yearly", prob: 15, mean: 25, sd: 12, vol_mult: 1.2, vol_days: 20, note: "해마다 15% 확률로 유가 +25% (중동·감산)" },
    { id: "x_gold", cat: "gold", factor: "gold", target: "ALL", kind: "금값 급등", date: "2027-08-16", repeat: "yearly", prob: 20, mean: 10, sd: 6, vol_mult: 1, vol_days: 0, note: "해마다 20% 확률로 금값 +10% (안전자산 쏠림)" },
    { id: "x_cmdty", cat: "cmdty", factor: "cmdty", target: "ALL", kind: "원자재 급등", date: "2027-07-15", repeat: "yearly", prob: 15, mean: 12, sd: 8, vol_mult: 1, vol_days: 0, note: "해마다 15% 확률로 원자재 +12% (공급망)" },
    { id: "x_chip", cat: "corp", target: "NVDA", kind: "공급망 차질", date: "2027-05-10", repeat: "yearly", prob: 15, mean: -4, sd: 4, vol_mult: 1.2, vol_days: 15, note: "해마다 15% 확률로 TSMC·HBM 공급 차질 (가정)" },
    { id: "x_parts", cat: "corp", target: "TSLA", kind: "공급망 차질", date: "2027-08-10", repeat: "yearly", prob: 15, mean: -3, sd: 4, vol_mult: 1, vol_days: 0, note: "해마다 15% 확률로 배터리·부품 공급 차질 (가정)" },
    { id: "x_mid", cat: "politics", factor: "mkt", target: "ALL", kind: "선거", date: "2026-11-04", repeat: "none", prob: 100, mean: 0, sd: 1.5, vol_mult: 1.2, vol_days: 15, note: "미국 중간선거 결과 (11/3 투표)" },
    { id: "x_pres", cat: "politics", factor: "mkt", target: "ALL", kind: "선거", date: "2028-11-08", repeat: "none", prob: 100, mean: 0, sd: 2, vol_mult: 1.3, vol_days: 20, note: "미국 대통령 선거 결과 (11/7 투표)" },
    { id: "x_tariff", cat: "trade", factor: "mkt", target: "ALL", kind: "관세·정책", date: "2027-04-05", repeat: "yearly", prob: 20, mean: -3, sd: 3, vol_mult: 1.4, vol_days: 20, note: "해마다 20% 확률로 관세·규제 충격 S&P500 -3%" },
    { id: "x_war", cat: "war", factor: "mkt", target: "ALL", kind: "전쟁·지정학", date: "2027-09-15", repeat: "yearly", prob: 10, mean: -5, sd: 4, vol_mult: 1.6, vol_days: 30, note: "해마다 10% 확률로 전쟁·분쟁 충격 S&P500 -5% (날짜는 임의)" },
    { id: "x_export", cat: "trade", target: "NVDA", kind: "수출 규제·제재", date: "2027-04-20", repeat: "yearly", prob: 25, mean: -5, sd: 4, vol_mult: 1.3, vol_days: 20, note: "해마다 25% 확률로 대중국 반도체 수출 규제 (H20 사례)" },
    { id: "x_witch", cat: "flow", factor: "mkt", target: "ALL", kind: "옵션 만기·리밸런싱", date: "2026-12-18", repeat: "quarterly", prob: 100, mean: 0, sd: 0.5, vol_mult: 1, vol_days: 0, note: "분기 셋째 금요일 (쿼드러플 위칭·지수 리밸런싱). S&P500 ±0.5%" },
    { id: "x_spidx", cat: "flow", target: "SPCX", kind: "지수 편입·제외", date: "2026-12-21", repeat: "none", prob: 30, mean: 4, sd: 4, vol_mult: 1, vol_days: 0, note: "S&P500·나스닥100 편입 기대 매수 (가정)" },
  ];
  const EXT_V2 = new Set(["x_bok", "x_fx", "x_infl", "x_chip", "x_parts", "x_export", "x_witch", "x_spidx"]); // ext_ver 2에서 추가된 기본 사건
  const REPEATS = [["none", "한 번"], ["monthly", "매달"], ["6w", "6주"], ["quarterly", "분기"], ["semi", "반년"], ["yearly", "매년"]];
  const repName = (r) => (REPEATS.find(([k]) => k === r) || [, "한 번"])[1];
  // 과거 반응을 볼 날짜 (공개 기록 기준, 미국 장 마감 기준으로 반영된 날)
  const REF_DAYS = {
    rate: ["2023-11-01", "2023-12-13", "2024-01-31", "2024-03-20", "2024-05-01", "2024-06-12", "2024-07-31", "2024-09-18", "2024-11-07", "2024-12-18", "2025-01-29", "2025-03-19", "2025-05-07", "2025-06-18", "2025-07-30", "2025-09-17", "2025-10-29", "2025-12-10", "2026-01-28", "2026-03-18", "2026-04-29", "2026-06-17", "2026-07-29", "2026-09-16"].map((d) => [d, "FOMC 결정"]),
    product: [["2023-11-30", "테슬라 사이버트럭 첫 인도 행사"], ["2023-12-13", "테슬라 오토파일럿 200만 대 리콜"], ["2024-03-19", "엔비디아 GTC 2024 (블랙웰) 다음 날"], ["2024-10-11", "테슬라 로보택시 공개 다음 날"], ["2025-03-18", "엔비디아 GTC 2025 기조연설"]],
    infl: [["2023-11-14", "CPI 둔화"], ["2024-02-13", "CPI 예상 상회"], ["2024-04-10", "CPI 예상 상회"], ["2024-05-15", "CPI 둔화"], ["2024-07-11", "CPI 둔화"], ["2025-02-12", "CPI 예상 상회"]],
    politics: [["2024-11-06", "미국 대선 결과"]],
    trade: [["2025-04-03", "상호관세 발표 다음 날"], ["2025-04-04", "관세 충격 이틀째"], ["2025-04-09", "관세 90일 유예"], ["2025-04-16", "엔비디아 H20 수출 규제 공시"], ["2025-05-12", "미·중 관세 인하 합의"]],
    war: [["2023-10-09", "하마스 이스라엘 공격 뒤 첫 거래일"], ["2024-04-15", "이란의 이스라엘 공격 뒤 첫 거래일"], ["2024-10-01", "이란 미사일 공격"], ["2025-06-13", "이스라엘의 이란 공습"], ["2025-06-23", "미국의 이란 핵시설 공습 뒤 첫 거래일"]],
  };
  const SESS = { pre: "프리", regular: "정규", post: "애프터", close: "종가", manual: "수동" };

  let S = { state: null, prices: {}, quotes: {} };
  // 실행 방식: "local" = 내 PC 의 server.py, "static" = GitHub Pages 같은 정적 사이트 (입력은 이 브라우저에 저장)
  let MODE = "local";
  const LS_KEY = "asset-tracker-state";
  const GH = (() => {
    const m = location.hostname.match(/^([^.]+)\.github\.io$/), repo = location.pathname.split("/").filter(Boolean)[0];
    // 내 도메인(naeilo.com 등)으로 열면 주소에 저장소 이름이 없으므로 이 앱의 저장소를 쓴다
    const [owner, name] = m && repo ? [m[1], repo] : /^(localhost|127\.|\[::1\]|$)/.test(location.hostname) ? [] : ["beeboy", "asset-tracker"];
    return owner ? { owner, repo: name, actions: `https://github.com/${owner}/${name}/actions/workflows/collect.yml` } : null;
  })();
  let saveTimer = null, autoTimer = null, lastForecast = null, fcDirty = true, lastAlloc = null, allocDirty = true;
  let fcCache = {}; // 시나리오별 전망 (대시보드 미래 표시·AI 용). 입력이나 시세가 바뀌면 비운다
  const markDirty = () => { fcDirty = allocDirty = true; fcCache = {}; };
  const SCEN = { blend: "내 관점", base: "기준", conservative: "보수", history: "과거 반복", smooth: "스무딩 추종", trend: "추세 추종" };
  const scenName = (k) => SCEN[k] || k;

  // ------------------------------------------------------------ 형식
  const nf = (v, d = 0) => (v == null || !isFinite(v) ? "-" : Number(v).toLocaleString("ko-KR", { minimumFractionDigits: d, maximumFractionDigits: d }));
  const pct = (v, d = 1) => (v == null || !isFinite(v) ? "-" : (v * 100).toFixed(d) + "%");
  const spct = (v, d = 1) => (v == null || !isFinite(v) ? "-" : (v >= 0 ? "+" : "") + (v * 100).toFixed(d) + "%");
  const cls = (v) => (v == null ? "" : v > 0 ? "up" : v < 0 ? "dn" : "");
  function krw(v) {
    if (v == null || !isFinite(v)) return "-";
    const a = Math.abs(v), s = v < 0 ? "-" : "";
    if (a >= 1e8) return s + (a / 1e8).toFixed(a >= 1e10 ? 0 : 2) + "억";
    if (a >= 1e4) return s + nf(a / 1e4) + "만";
    return s + nf(a);
  }
  const krwAxis = (v) => krw(v);
  function usd(v) {
    if (v == null || !isFinite(v)) return "-";
    const a = Math.abs(v), s = v < 0 ? "-$" : "$";
    if (a >= 1e6) return s + (a / 1e6).toFixed(a >= 1e8 ? 0 : 2) + "M";
    if (a >= 1e3) return s + nf(a / 1e3) + "K";
    return s + nf(a);
  }
  // 가격 축: 값의 폭이 좁으면 소수 자리를 늘려 같은 눈금 글자가 겹치지 않게
  const priceAxis = (arrs) => { const v = arrs.flat().filter((x) => x != null && isFinite(x)); const sp = Math.max(...v) - Math.min(...v); const d = sp < 2 ? 2 : sp < 20 ? 1 : 0; return (x) => nf(x, Math.max(d, x < 10 ? 2 : 0)); };
  // 소수 입력: "12.5", "12,5"(쉼표 소수점), "1,234.5" 모두 받는다. 빈칸은 null, 숫자가 아니면 undefined
  const parseDec = (s) => {
    let t = String(s ?? "").replace(/\s/g, ""); if (t === "") return null;
    t = /^\d+,\d{1,4}$/.test(t) ? t.replace(",", ".") : t.replace(/,/g, "");
    const v = Number(t); return isFinite(v) ? v : undefined;
  };
  const parseNum = (s) => { const v = Number(String(s).replace(/[,\s원]/g, "")); return isFinite(v) ? v : null; };
  const today = () => { const d = new Date(); return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10); };
  const esc = (s) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));
  function toast(msg) { let t = $(".toast"); if (!t) { t = document.createElement("div"); t.className = "toast"; document.body.appendChild(t); } t.textContent = msg; t.classList.add("on"); clearTimeout(t._h); t._h = setTimeout(() => t.classList.remove("on"), 1800); }

  // ------------------------------------------------------------ 서버
  async function api(path, body) {
    const r = await fetch(path, body ? { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) } : {});
    const j = await r.json();
    if (!r.ok) throw new Error(j.error || r.statusText);
    return j;
  }
  async function reload() {
    if (MODE === "local") {
      try {
        const d = await api("/api/data");
        S.prices = d.prices || {}; S.quotes = d.quotes || {};
        if (!S.state) S.state = normalize(d.state || {});
        S.config = d.config || {};
        return;
      } catch (e) { if (S.state) throw e; MODE = "static"; }
    }
    // 정적 사이트: ../data/ 의 파일을 읽는다 (GitHub Actions 가 주기적으로 갱신)
    const base = "../data/", bust = "?t=" + Date.now();
    const get = async (f) => { const r = await fetch(base + f + bust, { cache: "no-store" }); if (!r.ok) throw new Error(f + " " + r.status); return r.json(); };
    const idx = await get("index.json"), prices = {};
    await Promise.all(Object.entries(idx.prices || {}).map(async ([sym, f]) => { try { prices[sym] = await get("prices/" + encodeURIComponent(f)); } catch (e) { /* 없는 파일 무시 */ } }));
    S.prices = prices; S.quotes = await get("quotes.json").catch(() => ({})); S.dataUpdated = idx.updated;
    S.tickerCfg = await get("tickers.json").catch(() => ({ tickers: [] }));
    S.config = await get("config.json").catch(() => ({}));
    mergeExtra();
    if (!S.state) {
      let st = null;
      try { st = JSON.parse(localStorage.getItem(LS_KEY) || "null"); } catch (e) { /* 무시 */ }
      S.firstVisit = !st;
      S.state = normalize(st || (await get("state.default.json").catch(() => ({}))));
      // 첫 방문: 빈 화면 대신 TSLA 1,000주 샘플. 내 종목을 처음 넣거나 고치면 샘플은 사라진다
      if (S.firstVisit && !S.state.holdings.length) { S.state.holdings = [{ ticker: "TSLA", shares: 1000, price: null, avg_cost: null, note: "", sample: true }]; S.state.sample = true; }
    }
  }
  function normalize(st) {
    st.holdings = st.holdings || [];
    st.holdings.forEach((h) => { if (h.note === "수량을 입력하세요") h.note = ""; }); // 예전 기본 문구 정리
    st.goal = Object.assign({ amount: 1e9, date: Model.addMonths(today(), 36), start_date: today(), monthly_contribution: 0 }, st.goal || {});
    st.events = st.events || [];
    // 외부 요인 기본 사건을 한 번 넣는다 (이미 지운 사건은 다시 넣지 않도록 버전으로 표시)
    const xv = st.ext_ver || 0;
    if (xv < 2) {
      const have = new Set(st.events.map((e) => e.id));
      EXT_EVENTS.forEach((e) => { if (!have.has(e.id) && (xv < 1 || EXT_V2.has(e.id))) st.events.push({ on: true, ...e }); });
      st.events.forEach((e) => { if (e.id === "x_cpi" && e.cat === "rate") e.cat = "infl"; if (e.cat === "politics" && /관세/.test(e.kind || "")) e.cat = "trade"; }); // 14개 묶음으로 옮김
      st.ext_ver = 2;
    }
    if (st.ext_ver < 3) { st.events.forEach((e) => { if (e.cat === "supply") e.cat = "corp"; }); st.ext_ver = 3; } // 공급망 묶음은 기업 실적·공시로 합침
    st.events.forEach((e) => { if (!e.cat) e.cat = catOfKind(e.kind || ""); });
    if ((st.purge_ver || 0) < 1) {
      st.holdings = st.holdings.filter((h) => !PURGED.has(String(h.ticker || "").toUpperCase()));
      st.events = st.events.filter((e) => !PURGED.has(String(e.target || "").toUpperCase()));
      st.purge_ver = 1; S.purged = true;
    }
    if ((st.purge_ver || 0) < 2) { // 처음 받은 샘플 종목(수량 0)은 지운다
      const SAMPLE = new Set(["TSLA", "NVDA", "SPCX", "SGOV"]);
      st.holdings = st.holdings.filter((h) => !(SAMPLE.has(String(h.ticker || "").toUpperCase()) && !(Number(h.shares) > 0)));
      st.purge_ver = 2;
    }
    // 렌즈 도입 전 저장값: 기본 시나리오(기준)였다면 내 관점(추세 신뢰 50%)으로 옮긴다
    if (st.model && st.model.trust == null) { st.model.trust = 50; if (!st.model.scenario || st.model.scenario === "base") st.model.scenario = "blend"; }
    st.model = Object.assign({}, DEFAULT_MODEL, st.model || {});
    st.ui = Object.assign({ auto_refresh_min: 0, manual_price: false }, st.ui || {});
    return st;
  }
  // 수량이 바뀐 날만 기록해 둔다 (목표 진행을 그때 수량으로 계산하려고). 같은 날 여러 번 바꾸면 마지막 것만
  function recordLot() {
    const st = S.state; if (st.sample) return;
    const h = {}; st.holdings.forEach((x) => { if (Number(x.shares) > 0) h[x.ticker] = Number(x.shares); });
    st.lots = st.lots || [];
    const last = st.lots[st.lots.length - 1];
    if (last && JSON.stringify(last.h) === JSON.stringify(h)) return;
    const d = today();
    if (!last) { st.lots.push({ d: st.goal.start_date && st.goal.start_date < d ? st.goal.start_date : d, h }); return; }
    if (last.d === d) last.h = h; else st.lots.push({ d, h });
    if (st.lots.length > 400) st.lots.splice(0, st.lots.length - 400);
  }
  function save(dirtyForecast = true) {
    recordLot();
    if (dirtyForecast) markDirty();
    clearTimeout(saveTimer);
    saveTimer = setTimeout(async () => {
      if (MODE === "static") {
        try { localStorage.setItem(LS_KEY, JSON.stringify(S.state)); $("#footer").textContent = "이 브라우저에 저장됨 " + new Date().toLocaleTimeString() + " · 다른 기기에서 쓰려면 아래 내보내기/불러오기"; }
        catch (e) { $("#footer").textContent = "브라우저 저장 실패: " + e.message; }
        return;
      }
      try { await api("/api/state", S.state); $("#footer").textContent = "저장됨 " + new Date().toLocaleTimeString(); }
      catch (e) { $("#footer").textContent = "저장 실패: " + e.message + " (프로그램 창이 켜져 있는지 확인)"; }
    }, 400);
  }

  // ------------------------------------------------------------ 평가
  const fxOf = (ccy) => (!ccy || ccy === "KRW" ? null : ccy === "USD" ? "KRW=X" : ccy + "KRW=X");
  const ccyOf = (t) => S.prices[t]?.currency || S.quotes[t]?.currency || "USD";
  const lastOf = (sym) => { const p = S.prices[sym]; return p && p.close.length ? { v: p.close[p.close.length - 1], d: p.dates[p.dates.length - 1] } : null; };
  function fxNow(ccy) {
    const s = fxOf(ccy); if (!s) return 1;
    const q = S.quotes[s]; if (q && q.last) return q.last;
    const l = lastOf(s); return l ? l.v : null;
  }
  function curPrice(h) {
    if (S.state?.ui?.manual_price && h.price != null && h.price !== "" && Number(h.price) > 0) return { v: Number(h.price), src: "manual" };
    const q = S.quotes[h.ticker], l = lastOf(h.ticker);
    if (q && q.last && (!l || (q.last_time || 0) * 1000 >= Date.parse(l.d))) return { v: q.last, src: q.last_session || "regular", t: q.last_time };
    if (l) return { v: l.v, src: "close", d: l.d };
    if (q && q.last) return { v: q.last, src: q.last_session || "regular", t: q.last_time };
    return { v: null, src: null };
  }
  function valuation() {
    const rows = S.state.holdings.map((h) => {
      const ccy = ccyOf(h.ticker), p = curPrice(h), fx = fxNow(ccy), sh = Number(h.shares) || 0;
      const valueLocal = p.v != null ? sh * p.v : null, valueKrw = valueLocal != null && fx ? valueLocal * fx : null;
      const avg = Number(h.avg_cost) > 0 ? Number(h.avg_cost) : null;
      const q = S.quotes[h.ticker], prev = q?.prev_close || null;
      return { h, ccy, p, fx, sh, valueLocal, valueKrw, avg, pl: avg && p.v ? (p.v - avg) * sh : null, plPct: avg && p.v ? p.v / avg - 1 : null,
        dayChg: prev && p.v ? p.v / prev - 1 : null, name: S.prices[h.ticker]?.name || q?.name || "" };
    });
    const total = rows.reduce((s, r) => s + (r.valueKrw || 0), 0);
    rows.forEach((r) => (r.w = total ? (r.valueKrw || 0) / total : 0));
    return { rows, total };
  }
  // 현재 수량을 과거에 적용한 원화 평가액 + 편입 효과를 뺀 연결 지수
  function history() {
    const hs = S.state.holdings.filter((h) => S.prices[h.ticker] && Number(h.shares) > 0);
    const dset = new Set(); hs.forEach((h) => S.prices[h.ticker].dates.forEach((d) => dset.add(d)));
    const dates = [...dset].sort();
    const fxMaps = {};
    const getFx = (ccy) => {
      const s = fxOf(ccy); if (!s) return () => 1;
      if (!fxMaps[s]) { const p = S.prices[s]; const m = new Map(); if (p) p.dates.forEach((d, i) => m.set(d, p.close[i])); fxMaps[s] = { m, last: null }; }
      return fxMaps[s];
    };
    const each = {}, cur = {}, curFx = {};
    hs.forEach((h) => { each[h.ticker] = []; const m = new Map(); S.prices[h.ticker].dates.forEach((d, i) => m.set(d, S.prices[h.ticker].close[i])); cur[h.ticker] = { m, v: null }; });
    const total = [], index = [], usdK = []; let idx = 1, prevTotal = null, prevSet = null;
    for (const d of dates) {
      for (const ccy of new Set(hs.map((h) => ccyOf(h.ticker)))) {
        const f = getFx(ccy); if (typeof f === "function") { curFx[ccy] = 1; continue; }
        if (f.m.has(d)) f.last = f.m.get(d); curFx[ccy] = f.last ?? (S.prices[fxOf(ccy)]?.close[0] || fxNow(ccy));
      }
      const fu = getFx("USD"); if (fu.m.has(d)) fu.last = fu.m.get(d); usdK.push(fu.last ?? (S.prices["KRW=X"]?.close[0] || fxNow("USD")));
      let t = 0, tPrev = 0; const set = new Set();
      for (const h of hs) {
        const c = cur[h.ticker]; if (c.m.has(d)) c.v = c.m.get(d);
        const v = c.v != null ? c.v * Number(h.shares) * curFx[ccyOf(h.ticker)] : null;
        each[h.ticker].push(v);
        if (v != null) { t += v; set.add(h.ticker); if (prevSet && prevSet.has(h.ticker)) tPrev += v; }
      }
      if (prevTotal != null && prevSet) { const base = [...prevSet].reduce((s, k) => s + (each[k][each[k].length - 2] || 0), 0); if (base > 0) idx *= tPrev / base; }
      total.push(t); index.push(idx); prevTotal = t; prevSet = set;
    }
    return { dates, total, each, index, usdK };
  }

  // 매수 단가를 넣은 종목만 모아 계산한 수익률 (원화 환산은 현재 환율, 매수 시점 환율은 모름)
  function myReturn() {
    const rs = valuation().rows.filter((r) => r.avg && r.p.v && r.sh > 0 && r.fx);
    if (!rs.length) return null;
    const cost = rs.reduce((a, r) => a + r.avg * r.sh * r.fx, 0), val = rs.reduce((a, r) => a + r.p.v * r.sh * r.fx, 0);
    return cost > 0 ? { r: val / cost - 1, pl: val - cost, n: rs.length } : null;
  }
  // ------------------------------------------------------------ 머리글
  function renderHeader() {
    const { total } = valuation(), g = S.state.goal, fx = fxNow("USD");
    const prog = g.amount ? total / g.amount : 0;
    const my = myReturn();
    $("#headKpi").innerHTML = `<span>자산 <b>${krw(total)}</b>${my ? ` <b class="${cls(my.r)}">${spct(my.r)}</b>` : ""}</span><span class="hpill" title="목표 ${krw(g.amount)} 대비"><i style="width:${Math.max(0, Math.min(100, prog * 100)).toFixed(1)}%"></i><b>${pct(prog)}</b></span>`;
  }

  // ------------------------------------------------------------ 시세·종목
  function logLine(msg, ok = true, html = false) {
    const box = $("#collectLog"), div = document.createElement("div");
    div.innerHTML = `<span class="t">${new Date().toLocaleTimeString()}</span> ${html ? msg : esc(msg)}`;
    if (!ok) div.className = "err";
    box.appendChild(div); box.scrollTop = box.scrollHeight;
  }
  function symbolsToCollect(list) {
    const ts = list || S.state.holdings.map((h) => h.ticker);
    const fx = new Set(["KRW=X"]); ts.forEach((t) => { const f = fxOf(ccyOf(t)); if (f) fx.add(f); });
    return [...new Set([...ts, ...fx, ...(list ? [] : FACTOR_SYMS)])];
  }
  // GitHub 토큰(이 브라우저에만 저장)이 있으면 화면에서 바로 수집 작업을 실행하고, 끝나면 새 데이터를 불러온다
  const TOKEN_KEY = "asset-tracker-gh-token";
  const ghToken = () => { try { return localStorage.getItem(TOKEN_KEY) || ""; } catch (e) { return ""; } };
  async function ghApi(path, opt = {}) {
    if (!GH) throw new Error("저장소 주소를 알 수 없습니다. 페이지를 새로 고침해 주세요");
    // 브라우저는 머리글에 한글·줄바꿈 같은 글자가 섞이면 요청을 보내지 않고 TypeError 를 낸다
    if (/[^\x21-\x7e]/.test(ghToken())) throw new Error("저장된 토큰에 다른 글자가 섞여 있습니다. 설정 > 개발자용에서 토큰을 다시 붙여넣어 주세요");
    const r = await fetch(`https://api.github.com/repos/${GH.owner}/${GH.repo}/${path}`, {
      ...opt, cache: "no-store",
      headers: { Accept: opt.raw ? "application/vnd.github.raw+json" : "application/vnd.github+json", Authorization: "Bearer " + ghToken(), "X-GitHub-Api-Version": "2022-11-28", ...(opt.body ? { "Content-Type": "application/json" } : {}) },
    }).catch((e) => { throw new Error("GitHub에 연결하지 못했습니다 (" + (e.message || e) + "). 인터넷 연결을 확인하고 다시 시도해 주세요"); });
    if (!r.ok) {
      const msg = r.status === 401 ? "토큰이 맞지 않습니다" : r.status === 403 || r.status === 404 ? `토큰 권한 부족 (${opt.need || "Actions"} 읽기·쓰기 필요)` : "GitHub 오류 " + r.status;
      throw Object.assign(new Error(msg), { status: r.status });
    }
    return r.status === 204 ? null : opt.raw ? r.json() : r.json();
  }
  let ghBusy = false;
  async function ghCollect(add) {
    if (!GH || !ghToken() || ghBusy) return false;
    ghBusy = true;
    const btns = [$("#btnCollect")]; btns.forEach((b) => b && (b.disabled = true));
    try {
      const before = (await ghApi("contents/data/index.json?ref=main", { raw: true })).updated;
      await ghApi("actions/workflows/collect.yml/dispatches", { method: "POST", body: JSON.stringify({ ref: "main", inputs: { add_tickers: add.join(",") } }) });
      logLine(`GitHub에서 시세 수집을 시작했습니다${add.length ? " (새 종목 " + esc(add.join(", ")) + ")" : ""}. 보통 1분 안팎 걸리며 끝나면 자동으로 반영합니다.`, true, true);
      const t0 = Date.now();
      while (Date.now() - t0 < 6 * 60000) {
        await new Promise((r) => setTimeout(r, 12000));
        const idx = await ghApi("contents/data/index.json?ref=main", { raw: true });
        if (idx.updated === before) continue;
        // Pages 반영을 기다리지 않고 저장소에서 바로 읽는다
        const prices = {};
        await Promise.all(Object.entries(idx.prices || {}).map(async ([sym, f]) => { try { prices[sym] = await ghApi("contents/data/prices/" + encodeURIComponent(f) + "?ref=main", { raw: true }); } catch (e) { /* 무시 */ } }));
        S.prices = prices; S.quotes = await ghApi("contents/data/quotes.json?ref=main", { raw: true }); S.dataUpdated = idx.updated;
        S.tickerCfg = await ghApi("contents/data/tickers.json?ref=main", { raw: true });
        markDirty(); renderAll();
        const still = add.filter((t) => !S.prices[t]);
        logLine(`수집 완료 (${new Date(idx.updated).toLocaleString()}).` + (still.length ? ` 시세를 찾지 못한 종목: ${still.join(", ")} (티커 확인)` : ""), !still.length);
        return true;
      }
      logLine("수집이 오래 걸립니다. 잠시 뒤 '최신 데이터 불러오기'를 눌러 주세요." + ghLink("진행 상황 보기"), false, true);
    } catch (e) { logLine("GitHub 수집 실행 실패: " + esc(e.message) + ". 설정의 GitHub 연결을 확인하세요.", false, true); }
    finally { ghBusy = false; btns.forEach((b) => b && (b.disabled = false)); }
    return false;
  }
  const ghLink = (txt) => (GH ? ` <a href="${GH.actions}" target="_blank" rel="noopener">${txt}</a>` : "");
  // 토큰 없이 브라우저에서 Yahoo 시세를 받는다: 개발자가 정한 중계 주소(data/config.json) → 공개 CORS 중계 순서로 시도
  const EXTRA_KEY = "asset-tracker-extra";
  const PURGED = new Set(["004540.KS"]); // 앱에서 뺀 종목: 예전에 저장된 입력값·시세에서도 지운다
  const proxies = () => [...(S.config?.proxy ? [S.config.proxy] : []), "https://corsproxy.io/?url=", "https://api.allorigins.win/raw?url=", "https://api.codetabs.com/v1/proxy?quest="];
  async function yahoo(sym, params) {
    const url = "https://query1.finance.yahoo.com/v8/finance/chart/" + encodeURIComponent(sym) + "?" + new URLSearchParams(params);
    let last = null;
    for (const p of proxies()) {
      try {
        const ctl = new AbortController(), t = setTimeout(() => ctl.abort(), 15000);
        const r = await fetch(p + encodeURIComponent(url), { signal: ctl.signal, cache: "no-store" }); clearTimeout(t);
        const j = await r.json().catch(() => null);
        if (j && j.chart) { if (!j.chart.result) throw Object.assign(new Error("티커를 찾지 못함"), { notFound: true }); return j.chart.result[0]; }
        last = new Error("중계 응답 " + r.status);
      } catch (e) { if (e.notFound) throw e; last = e; }
    }
    throw new Error("시세 서버에 연결하지 못함 (" + (last?.message || "") + ")");
  }
  const dayOf = (ts, off) => new Date((ts + off) * 1000).toISOString().slice(0, 10);
  async function browserHistory(sym) {
    const now = Math.floor(Date.now() / 1000), r = await yahoo(sym, { period1: now - Math.round(3.05 * 365.25 * 86400), period2: now + 86400, interval: "1d", includeAdjustedClose: "true", events: "div,split" });
    const m = r.meta || {}, off = m.gmtoffset || 0, ts = r.timestamp || [], q = (r.indicators?.quote || [{}])[0], adj = (r.indicators?.adjclose || [{}])[0]?.adjclose || q.close || [];
    const rows = new Map(); ts.forEach((t, i) => { if (q.close?.[i] != null) rows.set(dayOf(t, off), [q.close[i], adj[i] ?? q.close[i]]); });
    const dates = [...rows.keys()].sort();
    return { symbol: sym, currency: m.currency || "USD", name: m.longName || m.shortName || sym, updated: new Date().toISOString(), dates, close: dates.map((d) => rows.get(d)[0]), adj: dates.map((d) => rows.get(d)[1]), browser: true };
  }
  async function browserQuote(sym) {
    const r = await yahoo(sym, { range: "1d", interval: "1m", includePrePost: "true" }), m = r.meta || {}, per = m.currentTradingPeriod || {};
    const out = { symbol: sym, currency: m.currency, name: m.longName || m.shortName, regular: m.regularMarketPrice, prev_close: m.chartPreviousClose || m.previousClose, pre: null, post: null,
      last: m.regularMarketPrice, last_time: m.regularMarketTime, last_session: "regular", fetched: Math.floor(Date.now() / 1000) };
    const cl = (r.indicators?.quote || [{}])[0].close || [];
    (r.timestamp || []).forEach((t, i) => {
      const c = cl[i]; if (c == null) return;
      for (const sname of ["pre", "regular", "post"]) { const pp = per[sname] || {}; if (pp.start <= t && t < pp.end) { if (sname !== "regular") out[sname] = c; out.last = c; out.last_time = t; out.last_session = sname; } }
    });
    return out;
  }
  function loadExtra() { try { return JSON.parse(localStorage.getItem(EXTRA_KEY) || "null") || { prices: {}, quotes: {} }; } catch (e) { return { prices: {}, quotes: {} }; } }
  function saveExtra(x) { try { localStorage.setItem(EXTRA_KEY, JSON.stringify(x)); } catch (e) { /* 용량 초과 등은 무시 */ } }
  function mergeExtra() {
    const x = loadExtra();
    if ([...PURGED].some((k) => x.prices[k] || x.quotes[k])) { PURGED.forEach((k) => { delete x.prices[k]; delete x.quotes[k]; }); saveExtra(x); }
    for (const [k, p] of Object.entries(x.prices)) { const cur = S.prices[k]; if (!cur || (p.dates.at(-1) || "") > (cur.dates.at(-1) || "")) S.prices[k] = p; }
    for (const [k, q] of Object.entries(x.quotes)) { if (!S.quotes[k] || (q.last_time || 0) > (S.quotes[k].last_time || 0)) S.quotes[k] = q; }
  }
  let bBusy = false;
  async function browserCollect(newSyms) {
    if (bBusy) return; bBusy = true;
    const btns = [$("#btnCollect")]; btns.forEach((b) => b && (b.disabled = true));
    const x = loadExtra(), held = S.state.holdings.map((h) => h.ticker);
    const need = [...new Set([...newSyms, ...held.filter((t) => !S.prices[t])])];
    let ok = 0, bad = [];
    logLine(`시세 받는 중: ${esc([...new Set([...need, ...held])].join(", "))}`, true, true);
    for (const t of need) {
      try { const h = await browserHistory(t); if (!h.dates.length) throw new Error("일봉 없음"); x.prices[t] = h; S.prices[t] = h; ok++; }
      catch (e) { bad.push(`${t} (${e.message})`); }
    }
    // 새 통화의 환율
    for (const t of held) { const f = fxOf(ccyOf(t)); if (f && !S.prices[f]) { try { const h = await browserHistory(f); x.prices[f] = h; S.prices[f] = h; } catch (e) { bad.push(`${f} (${e.message})`); } } }
    // 현재가 (보유 종목 + 환율)
    for (const t of symbolsToCollect(held)) { try { const q = await browserQuote(t); x.quotes[t] = q; S.quotes[t] = q; ok++; } catch (e) { if (need.includes(t)) continue; } }
    saveExtra(x);
    markDirty(); renderAll();
    logLine(bad.length ? `받지 못한 항목: ${esc(bad.join(", "))}. 티커를 확인하거나 잠시 뒤 다시 '시세 수집'을 눌러 주세요.` : "시세를 받았습니다.", !bad.length, true);
    bBusy = false; btns.forEach((b) => b && (b.disabled = false));
  }
  function missingTickers() {
    if (MODE !== "static") return [];
    const have = new Set((S.tickerCfg?.tickers || []).map((t) => t.toUpperCase()));
    return S.state.holdings.map((h) => h.ticker).filter((t) => !have.has(t) && !S.prices[t]);
  }
  async function collectStatic(manual) {
    if (GH && ghToken()) { if (manual) await ghCollect(missingTickers()); return; } // 개발자용
    if (manual) { try { await reload(); } catch (e) { /* 무시 */ } return browserCollect([]); }
    const btns = [$("#btnCollect")]; btns.forEach((b) => b && (b.disabled = true));
    try {
      await reload(); markDirty(); renderAll();
      logLine(`최신 데이터를 불러왔습니다 (서버 수집 ${S.dataUpdated ? new Date(S.dataUpdated).toLocaleString() : "-"}).`);
    } catch (e) { logLine("불러오기 실패: " + e.message, false); }
    const miss = missingTickers();
    if (miss.length) browserCollect(miss);
    btns.forEach((b) => b && (b.disabled = false));
  }
  async function collect(quotesOnly, list) {
    if (MODE === "static") return collectStatic(!quotesOnly);
    const btns = [$("#btnCollect"), $("#btnQuotes")]; btns.forEach((b) => b && (b.disabled = true));
    const syms = symbolsToCollect(list);
    logLine(`${quotesOnly ? "현재가" : "일봉+현재가"} 수집 시작: ${syms.join(", ")}`);
    try {
      const r = await api("/api/collect", { symbols: syms, years: Math.max(3, Number(S.state.model.history_years) || 3), quotes_only: quotesOnly });
      r.log.forEach((l) => logLine(l.msg, l.ok));
      await reload();
      // 처음 추가한 종목의 통화가 원화가 아니면 그 환율도 받는다
      const missingFx = symbolsToCollect(list).filter((s) => !syms.includes(s));
      if (missingFx.length) { const r2 = await api("/api/collect", { symbols: missingFx, years: 3, quotes_only: false }); r2.log.forEach((l) => logLine(l.msg, l.ok)); await reload(); }
      markDirty(); renderAll();
    } catch (e) { logLine("수집 실패: " + e.message + " (인터넷 연결 또는 프로그램 창 확인)", false); }
    btns.forEach((b) => b && (b.disabled = false));
  }
  function setAuto(min) {
    clearInterval(autoTimer); autoTimer = null;
    if (min > 0) autoTimer = setInterval(() => collect(true), Math.max(min, MODE === "static" ? 5 : 1) * 60000);
  }
  // 시세 수집이 끝나면 보유 종목 표를 접는다 (종목 추가 줄은 그대로 보임). 수량이 하나도 없으면 펼쳐 둔다
  function foldHold() { const d = $("#holdDet"); if (d) d.open = !!S.state.sample || !S.state.holdings.some((h) => Number(h.shares) > 0); }
  // 샘플 끝내기: keep=true 면 샘플 종목을 남기고(사용자가 그 수량을 고친 경우) 표시만 지운다
  function endSample(keep) {
    if (!S.state.sample) return;
    if (!keep) S.state.holdings = S.state.holdings.filter((h) => !h.sample);
    S.state.holdings.forEach((h) => delete h.sample); delete S.state.sample;
  }
  // 종목 로고: 티커로 받는 무료 로고 이미지 → 다른 곳 → 실패하면 첫 글자
  const LOGO_DOMAIN = { SPCX: "spacex.com" };
  const LOGO_SRC = (t) => [...(LOGO_DOMAIN[t] ? [`https://www.google.com/s2/favicons?domain=${LOGO_DOMAIN[t]}&sz=64`] : []), `https://financialmodelingprep.com/image-stock/${encodeURIComponent(t)}.png`, `https://assets.parqet.com/logos/symbol/${encodeURIComponent(t)}?format=png`];
  window.logoErr = (img) => { const n = LOGO_SRC(img.dataset.t)[+img.dataset.k + 1]; if (n) { img.dataset.k = +img.dataset.k + 1; img.src = n; } else img.remove(); };
  const logo = (t, name) => `<span class="tlogo" data-c="${esc(t.replace(/[^A-Z0-9]/gi, "").slice(0, 1))}" title="${esc(name || t)}"><img src="${LOGO_SRC(t)[0]}" data-t="${esc(t)}" data-k="0" alt="" loading="lazy" onerror="logoErr(this)"></span>`;
  function renderQuotes() {
    const { rows, total } = valuation();
    const fx = fxNow("USD"), fq = S.quotes["KRW=X"];
    $("#fxLine").textContent = `원/달러 ${nf(fx, 2)}${fq?.last_time ? " (" + dtStr(fq.last_time * 1000) + ")" : ""}`;
    const adv = !!S.state.ui.manual_price, hasPl = rows.some((r) => r.pl != null); // 매수 단가가 하나도 없으면 손익 칸은 숨김
    const head = `<tr><th class="l">종목</th><th>수량</th><th>매수 단가</th><th>현재가<br><span class="muted">평가액</span></th>${adv ? "<th>현재가 직접 입력</th>" : ""}<th>비중<br><span class="muted">전일</span></th>${hasPl ? "<th>손익</th>" : ""}<th></th></tr>`;
    const body = rows.map((r, i) => {
      const mk = r.p.src === "manual" ? curPrice({ ...r.h, price: null }).v : null;
      const gap = mk ? r.p.v / mk - 1 : 0;
      const warn = Math.abs(gap) > 0.05 ? `<br><span class="bad small">시세 ${nf(mk, 2)}와 ${spct(gap, 0)} 차이</span>` : "";
      const p = r.p, tag = p.src && p.src !== "regular" ? `<span class="tag ${p.src === "manual" ? "manual" : ""}">${SESS[p.src] || p.src}</span>` : "";
      const pl = r.pl != null ? `<span class="${cls(r.pl)}" title="${nf(r.pl, 0)} ${r.ccy}">${spct(r.plPct, 0)}</span><br><span class="muted small">${krw(r.pl * (r.fx || 1))}</span>` : `<span class="muted">-</span>`;
      return `<tr data-i="${i}">
        <td class="l tkc">${logo(r.h.ticker, r.name)}<b>${esc(r.h.ticker)}</b></td>
        <td><input data-f="shares" type="text" inputmode="decimal" placeholder="수량" value="${r.h.shares ? r.h.shares : ""}"></td>
        <td><input data-f="avg_cost" type="text" inputmode="decimal" placeholder="선택" value="${r.h.avg_cost ?? ""}"></td>
        <td>${p.v != null ? nf(p.v, 2) + " <span class='muted small'>" + r.ccy + "</span>" : "-"}${tag}<br><b>${krw(r.valueKrw)}</b></td>
        ${adv ? `<td><input data-f="price" type="text" inputmode="decimal" placeholder="자동" value="${r.h.price ?? ""}">${warn}</td>` : ""}
        <td>${pct(r.w, 0)}<br><span class="${cls(r.dayChg)}">${spct(r.dayChg, 1)}</span></td>${hasPl ? `<td>${pl}</td>` : ""}<td><button class="danger x" data-del="${i}" title="이 종목 삭제">✕</button></td></tr>`;
    }).join("");
    const plTot = rows.filter((r) => r.pl != null).reduce((s, r) => s + r.pl * (r.fx || 1), 0);
    const foot = `<tr><td class="l"><b>합계</b></td><td></td><td></td><td><b>${krw(total)}</b></td>${adv ? "<td></td>" : ""}<td>100%</td>${hasPl ? `<td><span class="${cls(plTot)}">${krw(plTot)}</span></td>` : ""}<td></td></tr>`;
    $("#holdTable").innerHTML = head + body + foot;
    renderTrades();
  }
  function onHoldEdit(e) {
    const tr = e.target.closest("tr[data-i]"); if (!tr) return;
    const h = S.state.holdings[+tr.dataset.i], f = e.target.dataset.f; if (!f) return;
    if (S.state.sample && f !== "note") endSample(true);
    if (f === "note") h.note = e.target.value;
    else { const v = parseDec(e.target.value); if (v === undefined) return; h[f] = v; }
    save(f !== "note"); renderHeader();
    if (e.type === "change") renderQuotes();
  }
  // 새 종목: 그 종목 분기 실적 사건을 함께 넣는다 (날짜는 1·4·7·10월 하순으로 추정). 지수·현금성 ETF 는 제외
  function addEarnEvent(t) {
    if (INS_SKIP.has(t) || t.includes("=") || S.state.events.some((e) => e.target === t && /실적/.test(e.kind))) return;
    const d = new Date(today()); let y = d.getFullYear(), m = [0, 3, 6, 9].find((k) => k > d.getMonth() || (k === d.getMonth() && d.getDate() < 25));
    if (m == null) { m = 0; y++; }
    S.state.events.push({ id: "auto_" + t, on: true, date: `${y}-${String(m + 1).padStart(2, "0")}-25`, target: t, kind: "실적", cat: "corp", repeat: "quarterly", prob: 100, mean: 0, sd: 8, vol_mult: 1, vol_days: 0, note: "분기 실적 (날짜 추정)" });
  }
  async function addHolding() {
    const t = $("#addTicker").value.trim().toUpperCase(), sh = parseDec($("#addShares").value), avg = parseDec($("#addAvg").value);
    if (!t) return toast("티커를 넣어 주세요");
    if (!(sh > 0)) return toast("수량을 넣어 주세요");
    endSample(false);
    const ex = S.state.holdings.find((h) => h.ticker === t);
    if (ex) {
      const old = Number(ex.shares) || 0;
      // 매수 단가를 같이 넣으면 기존 단가와 수량 가중 평균 (기존 단가가 없으면 새 단가)
      if (avg > 0) ex.avg_cost = Number(ex.avg_cost) > 0 && old > 0 ? (Number(ex.avg_cost) * old + avg * sh) / (old + sh) : avg;
      ex.shares = old + sh; toast(`${t} 수량을 더했습니다`);
    }
    else { S.state.holdings.push({ ticker: t, shares: sh, price: null, avg_cost: avg || null, note: "" }); addEarnEvent(t); }
    $("#addTicker").value = $("#addShares").value = $("#addAvg").value = "";
    save(); renderAll();
    if (!S.prices[t]) {
      if (MODE === "static") {
        showTab("quotes");
        if (GH && ghToken()) await ghCollect([t]);
        else await browserCollect([t]);
      }
      else await collect(false, [t]);
    }
  }
  // 확인 창(confirm) 대신 두 번 누르기: 앱 안 브라우저는 확인 창을 막는 경우가 있다
  function armed(btn) {
    if (btn.dataset.armed) return true;
    btn.dataset.armed = "1"; const old = btn.textContent; btn.textContent = "한 번 더";
    setTimeout(() => { if (btn.isConnected) { delete btn.dataset.armed; btn.textContent = old; } }, 3000);
    return false;
  }
  async function delHolding(i, btn) {
    if (!armed(btn)) return;
    const h = S.state.holdings[i];
    S.state.holdings.splice(i, 1); endSample(true);
    toast(`${h.ticker} 삭제함`);
    save(); renderAll();
  }

  // ------------------------------------------------------------ 목표·현황
  function yearsBetween(a, b) { return (Date.parse(b) - Date.parse(a)) / (365.25 * 86400e3); }
  function renderGoalInputs() {
    const g = S.state.goal;
    $("#goalAmount").value = nf(g.amount); $("#goalAmountKr").textContent = `= ${krw(g.amount)}원`;
    $("#startDate").value = g.start_date || today(); $("#goalDate").value = g.date;
    $("#goalYears").value = Math.round(yearsBetween(today(), g.date) * 10) / 10;
    $("#monthly").value = nf(g.monthly_contribution || 0);
  }
  // 과거 구간의 추세값 (합계): 종목마다 칼만 수준 또는 3년 스무딩 직선을 종가 대신 넣어 더한 값
  function pastFit(H, kind) {
    const out = H.dates.map(() => 0);
    for (const t of Object.keys(H.each)) {
      const p = S.prices[t]; if (!p) continue;
      const close = new Map(p.dates.map((d, i) => [d, p.close[i]])), fit = new Map();
      if (kind === "smooth") { const sf = Model.smoothFit(p.dates, p.close, 3); if (sf) p.dates.forEach((d) => fit.set(d, sf.fitAt(d))); }
      else { const ind = Model.indicators(p.dates, p.close); if (ind) p.dates.forEach((d, i) => fit.set(d, ind.level[i])); }
      let c = null, f = null;
      H.dates.forEach((d, i) => {
        if (close.has(d)) { c = close.get(d); f = fit.get(d); }
        const v = H.each[t][i];
        if (out[i] == null || v == null) return;
        out[i] = f != null && c ? out[i] + (v * f) / c : null;
      });
    }
    return out;
  }
  // 대시보드 미래 기준 → 시나리오
  const basisScen = (b) => (b === "base" ? "base" : b === "smooth" ? "smooth" : S.state.model.scenario);
  const BASIS = { model: "내 관점", base: "현재 정세", smooth: "과거 추세" };
  function simCommon(b, scen) {
    const m = S.state.model, g = S.state.goal;
    return { holdings: b.holdings, scenario: scen, nPaths: Number(m.n_paths), seed: Number(m.seed) || 1, goal: g.amount,
      monthly: Number(g.monthly_contribution) || 0, rebalance: !!m.rebalance_yearly, dof: m.t_dof, fxOf, usdKrw0: fxNow("USD") };
  }
  // 계산해 둔 전망만 돌려준다 (없으면 null)
  function fcReady(scen) {
    if (lastForecast && !fcDirty) {
      if (lastForecast.scen === scen) return { R: lastForecast.withEv, model: lastForecast.b.model };
      const lr = lastForecast.lens && lastForecast.lens[scen]; if (lr) return { R: lr, model: lastForecast.b.model };
    }
    return fcCache[scen] || null;
  }
  function forecastFor(scen) {
    const r = fcReady(scen); if (r) return r;
    const b = buildModelNow(); if (!b) return null;
    // 분석에서 고른 시나리오가 아니면 (대시보드 추세·스무딩, AI 비교용) 경로 수를 줄여 화면이 덜 멈추게
    const c = simCommon(b, scen); if (scen !== S.state.model.scenario) c.nPaths = Math.min(c.nPaths, 1500);
    const R = Model.simulate(b.model, { ...c, withEvents: true });
    return (fcCache[scen] = { R, model: b.model });
  }
  // 화면을 막지 않게 한 박자 쉬고 계산한 뒤 다시 그린다
  const fcPending = {};
  function forecastLater(scen, then) {
    if (fcPending[scen]) { if (then) fcPending[scen].push(then); return; }
    fcPending[scen] = then ? [then] : [];
    setTimeout(() => { try { if (!forecastFor(scen)) fcCache[scen] = { err: "평가액이 있는 종목이 없습니다." }; } catch (e) { fcCache[scen] = { err: e.message }; } const cbs = fcPending[scen]; delete fcPending[scen]; cbs.forEach((f) => f()); }, 30);
  }
  // 목표 진행: 목표 시작일부터 실제(그때 수량) 평가액과 필요 경로 비교
  function actualSeries(H) {
    const lots = S.state.lots || []; if (!lots.length) return null;
    const curSh = {}; S.state.holdings.forEach((x) => (curSh[x.ticker] = Number(x.shares) || 0));
    let li = -1;
    return H.dates.map((d, i) => {
      while (li + 1 < lots.length && lots[li + 1].d <= d) li++;
      if (li < 0) return null;
      const h = lots[li].h; let t = 0;
      for (const k of Object.keys(H.each)) { const v = H.each[k][i]; if (v == null || !curSh[k]) continue; t += (v * (h[k] || 0)) / curSh[k]; }
      return t;
    });
  }
  function renderProgress(H, total) {
    const card = $("#progCard"), g = S.state.goal;
    if (!(total > 0) || S.state.sample || !H.dates.length) { card.style.display = "none"; return; }
    const A = actualSeries(H) || H.total, start = g.start_date || today();
    let i0 = H.dates.findIndex((d) => d >= start); if (i0 < 0) i0 = H.dates.length - 1;
    const k = H.dates.length - 1, V0 = A[i0] || H.total[i0], now = total;
    const span = yearsBetween(start, g.date), el = Math.max(0, yearsBetween(start, today()));
    const need = V0 > 0 && span > 0 ? V0 * (g.amount / V0) ** (el / span) : null, gap = need ? now / need - 1 : null;
    const back = (n) => { const j = k - n; if (j < 0) return null; const a = A[j] ?? H.total[j]; return a ? A[k] / a - 1 : null; };
    card.style.display = "block";
    $("#progSub").textContent = `${start} 시작 · 수량 바뀐 날은 그때 수량으로`;
    $("#prog").innerHTML = [
      ["시작 대비", spct(now / V0 - 1), `${krw(V0)}원 → ${krw(now)}원`],
      ["필요 경로 대비", gap == null ? "-" : `${gap >= 0 ? "앞섬" : "뒤처짐"} ${spct(gap)}`, need ? `오늘 필요 ${krw(need)}원` : ""],
      ["지난주", spct(back(5)), "실제 수량 기준"],
      ["지난달", spct(back(21)), "실제 수량 기준"],
    ].map(([a, v, s2]) => `<div class="kpi"><div class="k">${a}</div><div class="v ${a === "필요 경로 대비" ? cls(gap) : a === "시작 대비" ? cls(now / V0 - 1) : ""}">${v}</div><div class="s">${s2}</div></div>`).join("");
    const xs = H.dates.slice(i0), ys = A.slice(i0);
    if (xs.length >= 3) {
      $("#progChart").style.display = "block";
      Charts.lineChart($("#progChart"), { x: xs, height: 150, yfmt: krwAxis, series: [{ name: "실제", y: ys, color: "var(--c1)", width: 2 }, { name: "필요 경로", y: xs.map((d) => V0 * (g.amount / V0) ** (Math.max(0, yearsBetween(start, d)) / span)), color: "var(--accent2)", dash: "5 4", width: 1.3 }], markers: [...new Set(trades().map((t) => t.d))].filter((d) => d >= start).map((d) => ({ x: d, label: `${d} ${trades().filter((t) => t.d === d).map((t) => `${t.t} ${t.q > 0 ? "+" : ""}${t.q}`).join(", ")}` })) });
    } else $("#progChart").style.display = "none";
  }
  function renderDash() {
    const g = S.state.goal, { total } = valuation(), yrs = yearsBetween(today(), g.date);
    $("#dashEmpty").style.display = total > 0 && !S.state.sample ? "none" : "block";
    $("#dashEmpty").innerHTML = S.state.sample ? `<b>샘플 화면입니다 (TSLA 1,000주).</b> <span class="small">내 종목과 수량을 넣으면 샘플은 사라집니다.</span> <button class="primary" data-go="quotes">내 수량 넣기</button>`
      : `<b>보유 수량을 넣어 주세요.</b> <span class="small">설정에서 종목별 수량만 넣으면 나머지는 자동.</span> <button class="primary" data-go="quotes">수량 입력하러 가기</button>`;
    const need = g.amount - total, req = yrs > 0 && total > 0 ? (g.amount / total) ** (1 / yrs) - 1 : null;
    const H = history();
    const ret = (n) => { const k = H.index.length - 1; if (k < n && k >= n * 0.97) n = k; return k - n >= 0 ? H.index[k] / H.index[k - n] - 1 : null; };
    const kc = H.index.length - 1, jc = Math.max(0, kc - 756); // 과거 연평균은 최근 3년 (이력이 더 길어도)
    const pastCagr = kc - jc > 30 ? (H.index[kc] / H.index[jc]) ** (252 / (kc - jc)) - 1 : null;
    renderProgress(H, total); renderBackupNag();
    $("#goalKpis").innerHTML = [
      ["현재 평가액", krw(total) + "원", `${nf(total)}원 · <a href="#" data-go="quotes">수량 수정</a>`],
      ["목표 대비", pct(total / g.amount), `<div class="bar"><i style="width:${Math.min(100, (total / g.amount) * 100)}%"></i></div>`],
      ["남은 금액", krw(Math.max(0, need)) + "원", `목표 ${krw(g.amount)}원`],
      ["남은 기간", yrs > 0 ? yrs.toFixed(1) + "년" : "지남", g.date],
      ["필요 연평균 수익률", req != null ? pct(req) : "-", "지금 자산만으로"],
      ["과거 연평균 (원화)", pct(pastCagr), `${H.dates[jc] || "-"} 이후`],
    ].map(([k, v, s]) => `<div class="kpi"><div class="k">${k}</div><div class="v">${v}</div><div class="s">${s}</div></div>`).join("");

    const rsel = $("#histRange .on")?.dataset.r || "252", step = $("#histStep .on")?.dataset.s || "d", mode = $("#histMode .on")?.dataset.m || "total";
    const inUsd = $("#histCcy .on")?.dataset.c === "usd", basis = $("#histBasis .on")?.dataset.b || "model";
    const fxNowUsd = fxNow("USD") || 1, conv = (v, i) => (v == null ? null : inUsd ? v / H.usdK[i] : v), money = inUsd ? usd : krwAxis;
    const future = rsel === "future", n = future ? 780 : +rsel; // 미래: 과거 3년 + 목표일까지
    const k0 = Math.max(0, H.dates.length - 1 - n);
    // 간격: 주·월은 그 기간의 마지막 거래일 값
    let ix = []; for (let i = k0; i < H.dates.length; i++) ix.push(i);
    if (step !== "d") {
      const key = (d) => { if (step === "m") return d.slice(0, 7); const t = new Date(d + "T00:00:00Z"); t.setUTCDate(t.getUTCDate() - ((t.getUTCDay() + 6) % 7)); return t.toISOString().slice(0, 10); };
      ix = ix.filter((i, j) => j === ix.length - 1 || key(H.dates[i]) !== key(H.dates[ix[j + 1]]));
    }
    const x = ix.map((i) => H.dates[i]), tick = Object.keys(H.each), col = (t) => C[tick.indexOf(t) % C.length];
    let series = [], bands = [];
    if (mode === "total") series = [{ name: "합계", y: ix.map((i) => conv(H.total[i], i)), color: "var(--c1)", width: 2 }];
    else if (mode === "each") series = tick.map((t) => ({ name: t, y: ix.map((i) => conv(H.each[t][i], i)), color: col(t) }));
    else { // 누적: 종목 값을 쌓아 올린 띠
      let lo = ix.map(() => 0);
      tick.forEach((t) => { const hi = ix.map((i, k) => lo[k] + (conv(H.each[t][i], i) || 0)); bands.push({ lo, hi, color: col(t), opacity: 0.55, name: t }); lo = hi; });
      series = [{ y: lo, color: "var(--fg)", width: 1 }];
    }
    const opt = { x, series, bands, yfmt: money, height: 320, hlines: [], vlines: [], ymin: mode === "total" ? undefined : 0 };
    const goalV = inUsd ? g.amount / fxNowUsd : g.amount, goalLab = inUsd ? `목표 ${usd(goalV)} (지금 환율)` : "목표 " + krw(g.amount);
    const maxV = Math.max(...ix.map((i) => conv(H.total[i], i)));
    const notes = [];
    if (future) {
      const V0 = total, last = x[x.length - 1] || today();
      opt.vlines.push({ x: today(), label: "오늘" });
      if (mode !== "each") opt.hlines.push({ y: goalV, label: goalLab });
      const scen = basisScen(basis), F = total > 0 ? fcReady(scen) : null;
      if (!F && total > 0) forecastLater(scen, () => { if ($("#tabs .on")?.dataset.tab === "dash") renderDash(); });
      if (F && F.err) notes.push("전망 계산 실패: " + esc(F.err));
      else if (F) {
        const R = F.R, fd = F.model.monthDates, B = inUsd ? R.bandsUsd : R.bands;
        const byT = new Map(R.stocks.map((s2) => [s2.ticker, s2]));
        const vb = (t) => { const s2 = byT.get(t); return s2 && (inUsd ? s2.valBandsUsd : s2.valBands); };
        if (mode === "total") {
          opt.bands.push({ x: fd, lo: B.p5, hi: B.p95, color: "var(--band)", opacity: 0.13, name: "전망 5~95%" }, { x: fd, lo: B.p25, hi: B.p75, color: "var(--band)", opacity: 0.25, name: "전망 25~75%" });
          opt.series.push({ name: "전망 중앙값", x: fd, y: B.p50, color: "var(--c1)", width: 2, dash: "2 2" });
        } else if (mode === "each") {
          tick.forEach((t) => { const b2 = vb(t); if (!b2) return;
            opt.bands.push({ x: fd, lo: b2.p25, hi: b2.p75, color: col(t), opacity: 0.12 });
            opt.series.push({ x: fd, y: b2.p50, color: col(t), width: 1.8, dash: "3 3" }); });
        } else {
          let lo = fd.map(() => 0);
          tick.forEach((t) => { const b2 = vb(t); if (!b2) return; const hi = lo.map((v, k) => v + b2.p50[k]); opt.bands.push({ x: fd, lo, hi, color: col(t), opacity: 0.28 }); lo = hi; });
          opt.series.push({ name: "종목 중앙값 합", x: fd, y: lo, color: "var(--fg)", width: 1, dash: "3 3" });
        }
        notes.push(basis === "base" ? "<b>현재 정세</b>: 과거 수익률을 장기 평균 쪽으로 당긴 값"
          : basis === "smooth" ? "<b>과거 추세</b>: 지난 3년 성장 속도가 이어지면"
          : `<b>내 관점</b> (추세 신뢰 ${S.state.model.scenario === "blend" ? S.state.model.trust + "%" : scenName(S.state.model.scenario)}): ` + (mode === "total" ? "진한 띠 25~75%, 옅은 띠 5~95%" : mode === "each" ? "점선은 종목별 중앙값, 띠는 25~75%" : "쌓은 띠 = 종목별 중앙값"));
        if (Number(g.monthly_contribution) > 0) notes.push(`· 월 적립 ${krw(Number(g.monthly_contribution))}원 포함`);
      } else if (total > 0) notes.push("전망을 계산하는 중입니다…");
      if (mode === "total" && basis === "smooth") {
        const pf = pastFit(H, basis);
        opt.series.push({ name: "3년 추세선 (과거)", y: ix.map((i) => conv(pf[i], i)), color: "var(--c7)", width: 1.4, dash: "4 3" });
      }
      const md = []; for (let k = 0; k <= 1200 && Model.addMonths(today(), k) <= g.date; k++) md.push(Model.addMonths(today(), k));
      if (md[md.length - 1] !== g.date) md.push(g.date);
      if (V0 > 0 && mode !== "each") opt.series.push({ name: "필요 경로", x: [last, ...md], y: [conv(H.total[H.total.length - 1], H.total.length - 1), ...md.map((d) => (V0 * (g.amount / V0) ** (yearsBetween(today(), d) / Math.max(0.01, yearsBetween(today(), g.date)))) / (inUsd ? fxNowUsd : 1))], color: "var(--accent2)", dash: "5 4", width: 1.3 });
    } else {
      if (mode !== "each" && goalV <= maxV * 1.05) opt.hlines.push({ y: goalV, label: "목표" });
      notes.push("현재 수량을 과거에 적용" + (inUsd ? ", 달러 환산." : "."));
    }
    $("#histNote").innerHTML = notes.join(" ");
    Charts.lineChart($("#histChart"), opt);

    const periods = [["1일", 1], ["1주", 5], ["1개월", 21], ["3개월", 63], ["6개월", 126], ["1년", 252], ["3년", 756]];
    $("#periodTable").innerHTML = `<tr>${periods.map((p) => `<th>${p[0]}</th>`).join("")}</tr><tr>${periods.map((p) => { const v = ret(p[1]); return `<td class="${cls(v)}">${spct(v)}</td>`; }).join("")}</tr>`;
    aiDash();
  }
  // 대시보드 AI: 세 가지 미래 기준을 모두 계산해 둔 뒤 묻는다
  let aiDashBusy = false;
  function aiDash() {
    if (aiDashBusy) return;
    if (S.state.ui.ai_auto === false || valuation().total <= 0) { aiAuto("dash", false); return; }
    const need = [...new Set([S.state.model.scenario, "base", "smooth"])].filter((k) => !fcReady(k));
    if (!need.length) { aiAuto("dash", false); return; }
    aiDashBusy = true;
    $("#aiOut-dash").innerHTML = "<p class='muted'>전망을 계산하는 중입니다…</p>";
    const next = () => { const k = need.shift(); if (!k) { aiDashBusy = false; aiAuto("dash", false); return; } forecastLater(k, next); };
    next();
  }
  // 분석·전략 맨 위: 종목별 가격 (현지 통화)
  function renderStockPrices() {
    const H = history(), n = +($("#stockRange .on")?.dataset.r || 252), from = H.dates[Math.max(0, H.dates.length - 1 - n)];
    const host = $("#dashStocks"); host.innerHTML = "";
    if (!from) { host.innerHTML = "<p class='muted small'>보유 수량이 있는 종목의 시세가 필요합니다.</p>"; return; }
    S.state.holdings.filter((h) => S.prices[h.ticker]).forEach((h, j) => {
      const p = S.prices[h.ticker], i0 = Math.max(0, p.dates.findIndex((d) => d >= from)), xs = p.dates.slice(i0), ys = p.close.slice(i0);
      const ch = ys.length > 1 ? ys[ys.length - 1] / ys[0] - 1 : null, box = document.createElement("div");
      box.innerHTML = `<h3>${esc(h.ticker)} <span class="muted small">${nf(ys[ys.length - 1], 2)} ${ccyOf(h.ticker)} · 기간 <span class="${cls(ch)}">${spct(ch)}</span></span></h3><div class="chartbox"></div>`;
      host.appendChild(box);
      Charts.lineChart(box.querySelector(".chartbox"), { x: xs, height: 170, legend: false, axisOut: true, yfmt: priceAxis([ys]), series: [{ name: h.ticker, y: ys, color: C[j % C.length], width: 1.6 }] });
    });
  }
  function onGoalEdit(e) {
    const g = S.state.goal, id = e.target.id;
    if (id === "goalAmount") { const v = parseNum(e.target.value); if (v > 0) g.amount = v; }
    if (id === "monthly") { const v = parseNum(e.target.value); g.monthly_contribution = v > 0 ? v : 0; }
    if (id === "goalDate" && e.target.value) g.date = e.target.value;
    if (id === "startDate" && e.target.value) g.start_date = e.target.value;
    if (id === "goalYears") { const y = Number(e.target.value); if (y > 0) g.date = Model.addMonths(today(), Math.round(y * 12)); }
    save(); renderHeader(); renderDash();
    if (e.type === "change") renderGoalInputs();
  }

  // ------------------------------------------------------------ 외부 요인 (사건)
  const tgtLab = (e) => (e.factor && e.factor !== "none" ? (e.factor === "mkt" ? "시장 전체" : FACTORS[e.factor]?.name || e.factor) : { ALL: "전체 종목", FX: "원/달러 환율" }[e.target] || e.target);
  const effTxt = (e) => {
    const u = e.factor === "rate" ? "bp" : "%", base = e.factor && e.factor !== "none" ? (e.factor === "mkt" ? "S&P500 " : (FACTORS[e.factor]?.name.split(" (")[0] || "") + " ") : "";
    return base + (e.mean ? `평균 <b class="${(e.factor === "rate" || e.factor === "oil" ? -e.mean : e.mean) > 0 ? "up" : "dn"}">${e.mean > 0 ? "+" : ""}${e.mean}${u}</b> ` : "방향 중립 ") + `±${e.sd}${u}`;
  };
  function renderEvents() {
    const opts = [...S.state.holdings.map((h) => h.ticker), "ALL", "FX"];
    const lab = { ALL: "전체", FX: "환율" };
    const fopts = [["none", "직접 (종목)"], ...Object.entries(FACTORS).map(([k, f]) => [k, f.name.split(" (")[0]])];
    const head = `<tr><th>사용</th><th class="l">묶음</th><th>날짜</th><th class="l">대상</th><th class="l">요인</th><th class="l">종류</th><th class="l">반복</th><th>발생 확률 %</th><th>평균 영향</th><th>불확실성 ±</th><th>변동성 배수</th><th>지속 (거래일)</th><th class="l">메모</th><th></th></tr>`;
    const body = S.state.events.map((e, i) => `<tr data-i="${i}">
      <td><input type="checkbox" data-f="on" ${e.on ? "checked" : ""}></td>
      <td class="l"><select data-f="cat">${XGROUPS.map((g) => `<optgroup label="${g.n}">${grpCats(g.k).map((k) => `<option value="${k}" ${k === e.cat ? "selected" : ""}>${catName(k)}</option>`).join("")}</optgroup>`).join("")}</select></td>
      <td><input type="date" class="date" data-f="date" value="${e.date || ""}"></td>
      <td class="l"><select data-f="target">${[...new Set([...opts, e.target])].map((o) => `<option value="${esc(o)}" ${o === e.target ? "selected" : ""}>${lab[o] || esc(o)}</option>`).join("")}</select></td>
      <td class="l"><select data-f="factor">${fopts.map(([k, n]) => `<option value="${k}" ${k === (e.factor || "none") ? "selected" : ""}>${n}</option>`).join("")}</select></td>
      <td class="l"><select data-f="kind">${[...new Set([...KINDS, e.kind])].map((o) => `<option ${o === e.kind ? "selected" : ""}>${esc(o)}</option>`).join("")}</select></td>
      <td class="l"><select data-f="repeat">${REPEATS.map(([k, n]) => `<option value="${k}" ${k === (e.repeat || "none") ? "selected" : ""}>${n}</option>`).join("")}</select></td>
      <td><input type="number" data-f="prob" min="0" max="100" value="${e.prob ?? 100}" style="width:5em"></td>
      <td><input type="number" data-f="mean" step="any" value="${e.mean ?? 0}" style="width:5em"></td>
      <td><input type="number" data-f="sd" step="any" min="0" value="${e.sd ?? 0}" style="width:5em"></td>
      <td><input type="number" data-f="vol_mult" step="0.1" min="0.1" value="${e.vol_mult ?? 1}" style="width:5em"></td>
      <td><input type="number" data-f="vol_days" step="1" min="0" value="${e.vol_days ?? 0}" style="width:5em"></td>
      <td class="l"><input class="wide" data-f="note" value="${esc(e.note)}"></td>
      <td><button class="danger" data-del="${i}">삭제</button></td></tr>`).join("");
    $("#eventTable").innerHTML = head + body;
    const on = S.state.events.filter((e) => e.on).length;
    $("#evSum").textContent = `${S.state.events.length}건${on < S.state.events.length ? ` · 켜짐 ${on}건` : ""}`;
    renderEvTiles();
    renderSchedule();
    renderXf();
  }
  // 사건 타일: 다음 날짜, 반복 횟수, 영향을 한눈에. 누르면 켜고 끈다
  function nextOcc(e) {
    const y = new Date(Date.parse(today()) - 864e5).toISOString().slice(0, 10); // 오늘 당일도 다음 사건으로 본다
    const list = Model.occurrences(e, y, S.state.goal.date);
    return { next: list[0] || null, n: list.length };
  }
  const curSub = () => XSUBS.find((x) => x.k === $("#xfNav .on")?.dataset.c) || XSUBS.find((x) => x.k === "earn");
  // 보유 종목이 없으면 외부 요인 화면은 비운다. 종목 대상 사건은 지금 보유한 종목 것만 보여 준다
  const heldTks = () => S.state.holdings.filter((h) => Number(h.shares) > 0).map((h) => h.ticker);
  const evShown = (e) => !e.target || e.target === "ALL" || e.target === "FX" || (e.factor && e.factor !== "none") || heldTks().includes(e.target);
  function evEmpty() {
    const none = !heldTks().length, host = $("#ana-events");
    [...host.children].forEach((c) => (c.style.display = c.id === "evNone" ? (none ? "" : "none") : none ? "none" : c.id === "xfAnalysis" ? c.style.display : ""));
    return none;
  }
  function renderEvTiles() {
    if (evEmpty()) return;
    const sub = curSub(), multi = sub.cats.length > 1;
    const all = S.state.events.map((e, i) => ({ e, i, ...nextOcc(e) })).filter(({ e }) => sub.cats.includes(e.cat) && evShown(e)).sort((a, b) => ((a.next || "9") < (b.next || "9") ? -1 : 1));
    const first = all.find((x) => x.e.on && x.next);
    const tile = ({ e, i, next, n }) => {
      const earn = /실적/.test(e.kind), q = earn && next ? quarterOf(next) : null;
      const eff = effTxt(e) + (Number(e.prob) < 100 ? ` · 확률 ${e.prob}%` : "") + (e.vol_mult && e.vol_mult !== 1 && e.vol_days ? ` · 변동성 ${e.vol_mult}배 ${e.vol_days}일` : "");
      const when = next ? `${next}${q ? ` (${q.label})` : ""}${e.repeat && e.repeat !== "none" ? ` · ${repName(e.repeat)} · 남은 ${n}회` : ""}` : "지난 사건";
      return `<button type="button" class="evtile ${e.repeat && e.repeat !== "none" ? "earn" : "once"} ${e.on ? "" : "off"} ${next ? "" : "past"} ${first && first.i === i ? "next" : ""}" data-evt="${i}" title="누르면 ${e.on ? "끄기" : "켜기"}">
        <div class="top"><span class="tk">${esc(tgtLab(e))}</span><span class="kd">${esc(e.kind)}</span></div>
        <div class="dt">${when}</div><div class="ef">${eff}</div>${e.note ? `<div class="nt">${esc(e.note)}</div>` : ""}</button>`;
    };
    // 소분류에 묶음이 여럿이면 (예: 원자재 = 유가·금값·기타 금속·곡물) 묶음별 제목을 달아 같이 보여준다
    $("#evTiles").innerHTML = multi
      ? sub.cats.map((c) => { const xs = all.filter((x) => x.e.cat === c); return `<div class="evcat">${esc(catName(c))} <span class="muted">${xs.length}건</span></div>` + (xs.map(tile).join("") || `<p class="muted small">사건 없음</p>`); }).join("")
      : all.map(tile).join("") || `<p class="muted">이 묶음에 사건이 없습니다. 아래 '사건 직접 편집'에서 추가할 수 있습니다.</p>`;
  }

  // ---- 요인별 분석: 대리 지표(금리·유가·금·원자재·시장)에 대한 종목별 민감도, 과거 반응일
  let betaCache = null;
  // 날짜가 같은 날끼리 맞춘 일별 변화 (가격은 로그수익률, 금리는 bp 변화)
  function alignedChanges(symA, symB, rateB, years = 3) {
    const A = S.prices[symA], B = S.prices[symB]; if (!A || !B) return null;
    const cut = Model.addMonths(today(), -12 * years), mb = new Map(B.dates.map((d, i) => [d, B.adj[i]]));
    const xs = [], ys = [], ds = []; let pa = null, pb = null;
    for (let i = 0; i < A.dates.length; i++) {
      const d = A.dates[i]; if (d < cut || !mb.has(d)) continue;
      const a = A.adj[i], b = mb.get(d);
      if (pa != null && a > 0 && pa > 0 && b != null && pb != null && (rateB || (b > 0 && pb > 0))) { ys.push(Math.log(a / pa)); xs.push(rateB ? (b - pb) * 100 : Math.log(b / pb)); ds.push(d); }
      pa = a; pb = b;
    }
    return { x: xs, y: ys, d: ds };
  }
  function factorBetas() {
    const stamp = Object.values(S.prices).map((p) => p.dates.at(-1)).join() + S.state.holdings.map((h) => h.ticker).join();
    if (betaCache && betaCache.stamp === stamp) return betaCache;
    const out = { stamp, beta: {}, stat: {} }, tks = S.state.holdings.map((h) => h.ticker).filter((t) => S.prices[t]);
    for (const [fk, F] of Object.entries(FACTORS)) {
      out.beta[fk] = {}; out.stat[fk] = {};
      if (!S.prices[F.sym]) continue;
      for (const t of tks) {
        if (t === F.sym) { out.beta[fk][t] = 1; out.stat[fk][t] = { beta: 1, corr: 1, n: 0 }; continue; }
        const r = alignedChanges(t, F.sym, fk === "rate"); if (!r || r.y.length < 30) continue;
        const n = r.y.length, mx = Model.mean(r.x), my = Model.mean(r.y);
        let sxy = 0, sxx = 0, syy = 0; for (let i = 0; i < n; i++) { sxy += (r.x[i] - mx) * (r.y[i] - my); sxx += (r.x[i] - mx) ** 2; syy += (r.y[i] - my) ** 2; }
        const corr = sxx && syy ? sxy / Math.sqrt(sxx * syy) : 0;
        let beta = sxx ? sxy / sxx : 0;
        // 시장 외 요인은 시장(SPY) 움직임을 뺀 순수 민감도 (두 변수 회귀)
        if (fk !== "mkt" && S.prices.SPY && t !== "SPY") {
          const m2 = alignedChanges(t, "SPY", false), mm = new Map(m2 ? m2.d.map((d, i) => [d, m2.x[i]]) : []);
          const X1 = [], X2 = [], Y = []; r.d.forEach((d, i) => { if (mm.has(d)) { X1.push(r.x[i]); X2.push(mm.get(d)); Y.push(r.y[i]); } });
          if (Y.length > 30) {
            const a1 = Model.mean(X1), a2 = Model.mean(X2), ay = Model.mean(Y); let s11 = 0, s22 = 0, s12 = 0, s1y = 0, s2y = 0;
            for (let i = 0; i < Y.length; i++) { const u = X1[i] - a1, v = X2[i] - a2, w = Y[i] - ay; s11 += u * u; s22 += v * v; s12 += u * v; s1y += u * w; s2y += v * w; }
            const det = s11 * s22 - s12 * s12; if (det > 0) beta = (s1y * s22 - s2y * s12) / det;
          }
        }
        const w = n / (n + 60), prior = fk === "mkt" ? 1 : 0; // 이력이 짧으면 기본값 쪽으로 당긴다
        out.beta[fk][t] = w * beta + (1 - w) * prior; out.stat[fk][t] = { beta: out.beta[fk][t], raw: beta, corr, n };
      }
    }
    return (betaCache = out);
  }
  // 하루 수익률 (그날 종가 / 전 거래일 종가). 그날 거래가 없으면 다음 거래일
  function dayRet(sym, d) {
    const p = S.prices[sym]; if (!p) return null;
    const i = p.dates.findIndex((x) => x >= d); if (i <= 0) return null;
    if ((Date.parse(p.dates[i]) - Date.parse(d)) / 864e5 > 4) return null;
    return p.adj[i] / p.adj[i - 1] - 1;
  }
  function absAvg(sym) { const p = S.prices[sym]; if (!p || p.adj.length < 30) return null; const k = Math.max(1, p.adj.length - 756); let s2 = 0, n = 0; for (let i = k; i < p.adj.length; i++) { s2 += Math.abs(p.adj[i] / p.adj[i - 1] - 1); n++; } return s2 / n; }
  function refDays(cat) {
    if (cat === "tsla") { // 분기 첫 달 2일 무렵 인도량 발표 → 그날(주말이면 다음 거래일) 반응
      const out = [], p = S.prices.TSLA; if (!p) return out;
      for (let y = +p.dates[0].slice(0, 4); y <= +today().slice(0, 4); y++) for (const mo of ["01", "04", "07", "10"]) { const d = `${y}-${mo}-02`; if (d > p.dates[0] && d <= today()) out.push([d, `${String(mo === "01" ? y - 1 : y).slice(2)}년 ${mo === "01" ? 4 : +mo / 3 | 0}Q 인도량`]); }
      return out;
    }
    if (cat === "flow") { // 분기 셋째 금요일 (3·6·9·12월 옵션 만기, 지수 리밸런싱)
      const out = [], p = S.prices.SPY; if (!p) return out;
      for (let y = +p.dates[0].slice(0, 4); y <= +today().slice(0, 4); y++) for (const mo of [3, 6, 9, 12]) {
        const d1 = new Date(Date.UTC(y, mo - 1, 1)), d = new Date(Date.UTC(y, mo - 1, 1 + ((5 - d1.getUTCDay() + 7) % 7) + 14)).toISOString().slice(0, 10);
        if (d > p.dates[0] && d <= today()) out.push([d, "쿼드러플 위칭"]);
      }
      return out;
    }
    return REF_DAYS[cat] || [];
  }
  // 외부 요인이 이만큼 움직이면 내 평가액은 (종목 베타 × 비중, 달러 비중은 환율)
  function renderSens() {
    const card = $("#sensCard"); if (!card) return;
    const { rows, total } = valuation(), hs = rows.filter((r) => r.valueKrw > 0);
    if (!(total > 0)) { card.style.display = "none"; return; }
    const B = factorBetas().beta, out = [];
    for (const [fk, F] of Object.entries(FACTORS)) {
      if (fk === "cmdty") continue;
      const sh = fk === "mkt" ? -10 : fk === "rate" ? 50 : 10, txt = fk === "mkt" ? "미국 증시 -10%" : fk === "rate" ? "10년 금리 +0.5%p" : fk === "oil" ? "유가 +10%" : "금값 +10%";
      let r = 0, ok = false;
      hs.forEach((x) => { const b = B[fk] && B[fk][x.h.ticker]; if (b == null) return; ok = true; r += x.w * (Math.exp(b * (fk === "rate" ? sh : Math.log(1 + sh / 100))) - 1); });
      if (ok) out.push([txt, Math.abs(r) < 0.0005 ? 0 : r]);
    }
    const usdW = hs.filter((x) => x.ccy === "USD").reduce((a, x) => a + x.w, 0);
    if (usdW > 0) out.push(["원화 10% 강세", -0.1 * usdW]);
    card.style.display = out.length ? "block" : "none";
    $("#sens").innerHTML = out.map(([t, r]) => `<div class="kpi"><div class="k">${t}</div><div class="v ${cls(r)}">${spct(r, 1)}</div><div class="s">${krw(r * total)}원</div></div>`).join("");
  }
  function renderXf() {
    const sub = curSub(), host = $("#xfAnalysis"); if (!host || !heldTks().length) return;
    host.style.display = "block";
    const seen = new Set(), charts = [];
    host.innerHTML = `<h2>${esc(sub.g.n)} · ${esc(sub.n)} 분석</h2>` + sub.cats.map((c, j) => xfSection(c, seen, j, charts, sub.cats.length > 1)).join("");
    charts.forEach((f) => f());
  }
  // 묶음 하나의 분석: 대리 지표 시세·민감도(같은 지표는 소분류 안에서 한 번만), 과거 반응일
  function xfSection(cat, seen, j, charts, multi) {
    const { rows, total } = valuation(), held = rows.filter((r) => r.valueKrw > 0), H = [];
    H.push(multi ? `<h3 style="margin-top:${j ? 16 : 4}px">${esc(catName(cat))}</h3>` : "");
    let fk = cat === "fx" ? "fx" : CAT_FACTOR[cat], F = fk === "fx" ? FX_F : FACTORS[fk];
    if (fk && seen.has(fk)) { H.push(`<p class="muted small">${esc(F.name)} 민감도는 위와 같습니다.</p>`); fk = null; F = null; }
    if (fk) seen.add(fk);
    const cid = "xfChart" + j;
    if (fk === "fx") {
      const p = S.prices[F.sym];
      if (!p) H.push(`<p class="muted small">원/달러 환율 시세가 아직 없습니다. 다음 자동 수집 뒤에 나옵니다.</p>`);
      else {
        const last = p.close.at(-1), at = (k) => p.close[Math.max(0, p.close.length - 1 - k)], ch = (k) => spct(last / at(k) - 1);
        H.push(`<p class="small"><b>${esc(F.name)}</b> ${nf(last, 1)}원 · 1개월 ${ch(21)} · 3개월 ${ch(63)} · 1년 ${ch(252)} <span class="muted">(${p.dates.at(-1)})</span></p><div id="${cid}" class="chartbox"></div>`);
        let port = 0;
        const tr = held.map((r) => { const e2 = r.ccy === "KRW" ? 0 : F.shock / 100; port += r.w * e2;
          return `<tr><td class="l">${esc(r.h.ticker)}</td><td>${esc(r.ccy)}</td><td>${pct(r.w, 0)}</td><td class="${cls(e2)}">${spct(e2)}</td></tr>`; }).join("");
        H.push(`<div class="tablewrap"><table class="grid"><tr><th class="l">종목</th><th>통화</th><th>비중</th><th>${esc(F.shockTxt)}일 때 (원화 평가액)</th></tr>${tr}
          <tr><td class="l"><b>내 포트폴리오</b></td><td></td><td></td><td class="${cls(port)}"><b>${spct(port)}</b> (${krw(total * port)}원)</td></tr></table></div>
          <p class="muted small">원/달러가 오르면 해외 주식의 원화 평가액도 같은 비율로 오름.</p>`);
      }
    } else if (F) {
      const p = S.prices[F.sym];
      if (!p) H.push(`<p class="muted small">${esc(F.name)} 시세가 아직 없습니다. 다음 자동 수집 뒤에 나옵니다.</p>`);
      else {
        const last = p.close.at(-1), at = (k) => p.close[Math.max(0, p.close.length - 1 - k)], ch = (k) => (fk === "rate" ? `${((last - at(k)) * 100) >= 0 ? "+" : ""}${nf((last - at(k)) * 100)}bp` : spct(last / at(k) - 1));
        H.push(`<p class="small"><b>${esc(F.name)}</b> ${fk === "rate" ? nf(last, 2) + "%" : nf(last, 2)} · 1개월 ${ch(21)} · 3개월 ${ch(63)} · 1년 ${ch(252)} <span class="muted">(${p.dates.at(-1)})</span></p><div id="${cid}" class="chartbox"></div>`);
        const B = factorBetas(), st = B.stat[fk] || {};
        let port = 0;
        const tr = held.map((r) => { const t = r.h.ticker, s2 = st[t]; if (!s2) return `<tr><td class="l">${esc(t)}</td><td colspan="3" class="muted">이력 부족</td></tr>`;
          const effPct = fk === "rate" ? s2.beta * F.shock : s2.beta * Math.log(1 + F.shock / 100);
          port += r.w * effPct;
          return `<tr><td class="l">${esc(t)}</td><td>${s2.corr.toFixed(2)}</td><td>${fk === "rate" ? spct(s2.beta * 10, 2) : s2.beta.toFixed(2)}</td><td class="${cls(effPct)}">${spct(Math.exp(effPct) - 1)}</td></tr>`; }).join("");
        H.push(`<div class="tablewrap"><table class="grid"><tr><th class="l">종목</th><th>상관</th><th>민감도${fk === "mkt" ? " (베타)" : fk === "rate" ? " (+10bp당, 시장 제외)" : " (시장 제외)"}</th><th>${esc(F.shockTxt)}일 때</th></tr>${tr}
          <tr><td class="l"><b>내 포트폴리오</b></td><td></td><td></td><td class="${cls(port)}"><b>${spct(Math.exp(port) - 1)}</b> (${krw(total * (Math.exp(port) - 1))}원)</td></tr></table></div>
          <p class="muted small">최근 3년 일별 변화 기준. ${fk === "mkt" ? "베타 1.5 = 시장 1%에 1.5%." : "시장 움직임을 뺀 이 요인만의 영향."}</p>`);
      }
    }
    const refs = refDays(cat), tks = ["SPY", ...held.map((r) => r.h.ticker).filter((t) => t !== "SPY")].filter((t) => S.prices[t]);
    if (refs.length && tks.length) {
      const rr = refs.map(([d, n]) => ({ d, n, r: tks.map((t) => dayRet(t, d)) })).filter((x) => x.r.some((v) => v != null));
      if (rr.length) {
        const avg = tks.map((t, j) => { const v = rr.map((x) => x.r[j]).filter((x) => x != null); return v.length ? v.reduce((s2, x) => s2 + Math.abs(x), 0) / v.length : null; });
        const norm = tks.map(absAvg);
        H.push(`<h3 style="margin-top:12px">과거 같은 일이 있던 날의 하루 변동</h3><div class="tablewrap"><table class="grid"><tr><th class="l">날짜</th><th class="l">일</th>${tks.map((t) => `<th>${esc(t)}</th>`).join("")}</tr>
          ${rr.slice(-12).reverse().map((x) => `<tr><td class="l">${x.d}</td><td class="l">${esc(x.n)}</td>${x.r.map((v) => `<td class="${cls(v)}">${spct(v)}</td>`).join("")}</tr>`).join("")}
          <tr><td class="l" colspan="2"><b>평균 크기 (부호 무시)</b></td>${avg.map((v) => `<td><b>${pct(v)}</b></td>`).join("")}</tr>
          <tr><td class="l muted" colspan="2">평소 하루 평균 크기</td>${norm.map((v) => `<td class="muted">${pct(v)}</td>`).join("")}</tr></table></div>
          <p class="muted small">${rr.length}번 중 최근 ${Math.min(12, rr.length)}번. 평소보다 크면 사건으로 넣을 이유가 됨.</p>`);
      }
    }
    if (H.length === 1) H.push(cat === "corp" ? `<p class="muted small">종목별 사건(실적·보호예수·규제 등)으로 반영.</p>`
      : `<p class="muted small">대표 지표가 없어 종목별 사건으로 반영.</p>`);
    if (F && S.prices[F.sym]) charts.push(() => {
      const p = S.prices[F.sym], k = Math.max(0, p.dates.length - 756);
      Charts.lineChart($("#" + cid), { x: p.dates.slice(k), height: 170, legend: false, yfmt: (v) => (fk === "rate" ? nf(v, 2) + "%" : nf(v, v < 100 ? 1 : 0)), series: [{ name: F.name, y: p.close.slice(k), color: "var(--c2)", width: 1.5 }] });
    });
    return H.join("");
  }
  // 요인별 영향: 같은 난수로 '사건 없음'과 '그 박스·버튼만 켬'을 비교
  let xfEff = null, xfBusy = false;
  async function runXfEffect() {
    const host = $("#xfEffect"); if (!host || !lastForecast) return;
    if (xfEff && xfEff.fc === lastForecast) return drawXfEffect();
    if (xfBusy) return; xfBusy = true;
    const { b } = lastForecast, common = { ...simCommon(b, S.state.model.scenario) }; common.nPaths = Math.min(common.nPaths, 1500);
    const has = (cats) => b.model.eventList.some((x) => cats.includes(x.event.cat || "corp"));
    host.innerHTML = "<p class='muted small'>요인별 영향을 계산하는 중입니다…</p>";
    const res = { fc: lastForecast, none: null, all: null, grp: {}, by: {} };
    const run = (cats) => Model.simulate(b.model, { ...common, withEvents: true, cats: cats && new Set(cats) });
    await new Promise((r) => setTimeout(r, 20));
    res.none = run([]); res.all = run(null);
    for (const g of XGROUPS) {
      const gc = grpCats(g.k); if (!has(gc)) continue;
      await new Promise((r) => setTimeout(r, 0)); if (lastForecast !== res.fc) { xfBusy = false; return; }
      res.grp[g.k] = run(gc);
      for (const [k, , cats] of g.subs) {
        if (!has(cats)) continue;
        if (g.subs.length === 1) { res.by[k] = res.grp[g.k]; continue; }
        await new Promise((r) => setTimeout(r, 0)); if (lastForecast !== res.fc) { xfBusy = false; return; }
        res.by[k] = run(cats);
      }
    }
    xfEff = res; xfBusy = false; drawXfEffect();
  }
  function drawXfEffect() {
    const host = $("#xfEffect"); if (!host || !xfEff) return;
    const { none, all, grp, by } = xfEff, md = lastForecast.b.model;
    const cnt = (cats) => md.eventList.filter((x) => cats.includes(x.event.cat || "corp")).length;
    const row = (n, R, k, sub) => `<tr class="${sub ? "subrow" : ""}"><td class="l">${n}</td><td>${k ?? ""}</td><td class="${cls(R.p_goal - none.p_goal)}">${spct(R.p_goal - none.p_goal, 0).replace("%", "%p")}</td><td class="${cls(R.terminal.p50 - none.terminal.p50)}">${R.terminal.p50 >= none.terminal.p50 ? "+" : ""}${krw(R.terminal.p50 - none.terminal.p50)}</td><td class="${cls(R.terminal.p5 - none.terminal.p5)}">${R.terminal.p5 >= none.terminal.p5 ? "+" : ""}${krw(R.terminal.p5 - none.terminal.p5)}</td></tr>`;
    const body = XGROUPS.filter((g) => grp[g.k]).map((g) => row(`<b>${g.icon} ${esc(g.n)}</b>`, grp[g.k], cnt(grpCats(g.k))) +
      (g.subs.length > 1 ? g.subs.filter(([k]) => by[k]).map(([k, n, cats]) => row("└ " + esc(n), by[k], cnt(cats), true)).join("") : "")).join("");
    host.innerHTML = `<div class="tablewrap"><table class="grid"><tr><th class="l">요인</th><th>횟수</th><th>목표 확률</th><th>중앙값</th><th>하위 5%</th></tr>
      ${body}${row("<b>모두 반영</b>", all, md.eventList.length)}</table></div>
      <p class="muted small">외부 요인 없는 전망(목표 확률 ${pct(none.p_goal, 0)}, 중앙값 ${krw(none.terminal.p50)}원) 대비 변화. 같은 난수, 경로 ${nf(Math.min(S.state.model.n_paths, 1500))}개.</p>`;
  }
  // 실적 발표일 → 그 직전 분기 (10월 발표 = 3분기 실적). 회계연도가 다른 회사(NVDA 등)는 회사 기준 분기와 다를 수 있음
  function quarterOf(d) {
    let y = +d.slice(0, 4), q = Math.ceil(+d.slice(5, 7) / 3) - 1;
    if (q === 0) { q = 4; y--; }
    return { y, q, label: `${String(y).slice(2)}년 ${q}Q` };
  }
  function renderSchedule() {
    try {
      const m = buildModelNow();
      if (!m) { $("#eventSchedule").textContent = "종목 시세가 있어야 일정을 펼칠 수 있습니다."; $("#schedSum").textContent = ""; return; }
      const list = m.model.eventList.sort((a, b) => (a.date < b.date ? -1 : 1));
      const lab = { ALL: "전체", FX: "환율" };
      if (!list.length) { $("#eventSchedule").textContent = "켜진 사건이 없거나 모두 지난 날짜입니다."; $("#schedSum").textContent = "· 없음"; return; }
      // 요약: 종류별 건수
      $("#schedSum").textContent = `· 전체 ${list.length}건`;
      // 대상별 한 줄: 날짜 순서로 칩을 늘어놓는다 (가장 가까운 사건은 강조)
      const groups = new Map(); list.forEach((x) => { const t = tgtLab(x.event); if (!groups.has(t)) groups.set(t, []); groups.get(t).push(x); });
      const next = list[0];
      const chip = (x) => {
        const e = x.event, earn = /실적/.test(e.kind), q = earn ? quarterOf(x.date) : null;
        const tip = `${x.date} ${e.kind}${q ? " (" + q.label + ")" : ""} · 확률 ${e.prob}% · 평균 ${e.mean}% · ±${e.sd}%`;
        const u = e.factor === "rate" ? "bp" : "%";
        const txt = earn ? `<b>${q.label}</b><span>${x.date.slice(2, 7).replace("-", ".")}</span>` : `<b>${esc(e.kind)}</b><span>${x.date.slice(2).replace(/-/g, ".")} · ${e.mean > 0 ? "+" : ""}${e.mean}${u}±${e.sd}${Number(e.prob) < 100 ? ` (${e.prob}%)` : ""}</span>`;
        return `<span class="chip ${e.repeat && e.repeat !== "none" ? "earn" : "once"} ${x === next ? "next" : ""}" title="${esc(tip)}">${txt}</span>`;
      };
      $("#eventSchedule").innerHTML = [...groups].map(([t, xs]) => {
        const earn = xs.filter((x) => /실적/.test(x.event.kind)), e0 = earn[0]?.event;
        const sub = earn.length && earn.length === xs.length ? `분기 실적 ${earn.length}회${e0 ? ` · 회당 ±${e0.sd}%` : ""}` : `${xs.length}건`;
        return `<div class="schedrow"><div class="schedhead"><b>${esc(t)}</b> <span class="muted">${sub}</span></div><div class="chips">${xs.map(chip).join("")}</div></div>`;
      }).join("") + `<p class="muted small">회색 = 분기 실적, 주황 = 한 번 있는 사건, 진한 테두리 = 가장 가까운 사건.</p>`;
    } catch (e) { $("#eventSchedule").textContent = e.message; }
  }
  function onEventEdit(e) {
    const tr = e.target.closest("tr[data-i]"); if (!tr) return;
    const ev = S.state.events[+tr.dataset.i], f = e.target.dataset.f; if (!f) return;
    if (f === "on") ev.on = e.target.checked;
    else if (["prob", "mean", "sd", "vol_mult", "vol_days"].includes(f)) ev[f] = e.target.value === "" ? 0 : Number(e.target.value);
    else ev[f] = e.target.value;
    save(f !== "note");
    if (e.type === "change") { renderEvTiles(); renderSchedule(); if (f === "cat") renderXf(); }
  }

  // ------------------------------------------------------------ 전망
  function buildModelNow() {
    const g = S.state.goal, start = today();
    if (g.date <= start) throw new Error("목표일이 오늘 이후여야 합니다.");
    const { rows } = valuation();
    const holdings = rows.filter((r) => r.valueKrw > 0).map((r) => ({ ticker: r.h.ticker, shares: r.sh, price0: r.p.v, ccy: r.ccy, valueKrw: r.valueKrw }));
    if (!holdings.length) return null;
    const series = {};
    for (const k in S.prices) series[k] = { dates: S.prices[k].dates, adj: S.prices[k].adj };
    const model = Model.buildModel({ holdings, series, fxOf, settings: S.state.model, events: S.state.events, betas: factorBetas().beta, startDate: start, goalDate: g.date });
    return { holdings, model };
  }
  const SCEN_LAB = { blend: "내 관점 (현재 정세와 과거 추세 사이)", base: "기준 (과거+사전값 절충)", conservative: "보수 (위험 프리미엄 없음)", history: "과거 반복 (지난 수익률 그대로)", smooth: "스무딩 추종 (3년 추세선)", trend: "추세 추종 (칼만·EMA)" };
  const SCEN_SHORT = { blend: "내 관점", base: "기준", conservative: "보수", history: "과거 반복", smooth: "스무딩 추종", trend: "추세 추종" };
  function dtStr(t) { const d = new Date(t), z = (n) => String(n).padStart(2, "0"); return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()} ${z(d.getHours())}:${z(d.getMinutes())}`; }
  // 전망 결과를 이 브라우저에 저장해 두고 리로드 때 다시 쓴다. 종목 구성이 바뀌었을 때만 새로 계산
  const FC_KEY = "naeilo-forecast";
  const fcSig = () => hashStr(JSON.stringify([S.state.holdings.map((h) => [h.ticker, h.shares]), S.state.goal, S.state.events.map((e) => [e.id, e.on, e.date, e.prob, e.mean, e.sd, e.repeat]), S.state.model]));
  function fcStore() {
    const L = lastForecast; if (!L) return;
    const strip = (k, v) => (k === "raw" || k === "term" ? undefined : v);
    try { localStorage.setItem(FC_KEY, JSON.stringify({ sig: fcSig(), tks: L.b.holdings.map((h) => h.ticker).join(), scen: L.scen, at: L.at.getTime(), ms: L.ms, hasEv: L.hasEv, withEv: L.withEv, noEv: L.hasEv ? L.noEv : null, lens: L.lens }, strip)); }
    catch (e) { try { localStorage.removeItem(FC_KEY); } catch (e2) { /* 무시 */ } }
  }
  function fcRestore() {
    try {
      const c = JSON.parse(localStorage.getItem(FC_KEY) || "null"); if (!c || !c.withEv) return false;
      const b = buildModelNow(); if (!b || b.holdings.map((h) => h.ticker).join() !== c.tks) return false;
      const common = simCommon(b, c.scen);
      lastForecast = { b, sim: null, simNo: null, withEv: c.withEv, noEv: c.noEv || c.withEv, lens: c.lens || {}, hasEv: c.hasEv, common, grp: {}, scen: c.scen, at: new Date(c.at), ms: c.ms };
      fcDirty = c.sig !== fcSig() || c.scen !== S.state.model.scenario; fcCache[c.scen] = { R: c.withEv, model: b.model };
      return true;
    } catch (e) { return false; }
  }
  // ------------------------------------------------------------ 렌즈·내 관점·만약에
  // 한 번 시뮬레이션한 경로(같은 충격)에 렌즈별 기대수익만 바꿔 다시 계산한다 (Model.reweight). 연 1회 재조정을 켜면 경로가 없어 따로 계산
  const viewKey = () => (S.state.model.scenario === "blend" ? Number(S.state.model.trust) : S.state.model.scenario);
  function viewMu(md, key) {
    const A = lastForecast.b.holdings.length;
    return md.factors.slice(0, A).map((f) => (typeof key === "number" ? Model.blendMu(f.mu.base, f.mu.smooth ?? f.mu.base, key / 100) : f.mu[key] ?? f.mu.base));
  }
  function portMu(key) {
    const { b } = lastForecast, V = b.holdings.reduce((s2, h) => s2 + h.valueKrw, 0), mu = viewMu(b.model, key);
    return b.holdings.reduce((s2, h, i) => s2 + (h.valueKrw / V) * mu[i], 0);
  }
  // 모든 보유 종목에 매수 단가가 있으면 원금(매수가 합) 기준으로 손실을 본다
  function costBasis() {
    const rs = valuation().rows.filter((r) => r.valueKrw > 0);
    if (!rs.length || rs.some((r) => !(Number(r.avg) > 0) || !r.fx)) return null;
    return rs.reduce((a, r) => a + r.avg * r.sh * r.fx, 0);
  }
  const WI = { monthly: null, sell: 0 }; // 만약에: 저장하지 않는 임시 가정
  function wiTop() {
    const { b } = lastForecast, md = b.model;
    const risky = b.holdings.map((h, i) => (md.factors[i].cash ? 0 : h.valueKrw)), top = risky.indexOf(Math.max(...risky));
    const cash = b.holdings.findIndex((h, i) => md.factors[i].cash && i !== top);
    return { top, cash };
  }
  function wiOpt() {
    const plain = Number(S.state.goal.monthly_contribution) || 0;
    const monthly = WI.monthly == null ? plain : WI.monthly;
    if (monthly === plain && !(WI.sell > 0)) return null;
    const { top, cash } = wiTop();
    return { monthly, move: WI.sell > 0 ? { from: top, to: cash, frac: WI.sell / 100 } : null };
  }
  function applyView(full) {
    const L = lastForecast; if (!L || !L.sim) return;
    const md = L.b.model, g = S.state.goal, lossRef = costBasis() ?? undefined, plain = Number(g.monthly_contribution) || 0;
    if (L.sim.raw) {
      const rw = (sim, key, wi) => Model.reweight(sim, { mu: viewMu(md, key), goal: g.amount, monthly: wi ? wi.monthly : plain, move: wi ? wi.move : null, lossRef });
      const k = viewKey(), wi = wiOpt();
      L.withEv = rw(L.sim, k); L.noEv = L.hasEv ? rw(L.simNo, k) : L.withEv;
      if (full || !L.lens || !L.lens.base) L.lens = { base: rw(L.sim, "base"), smooth: rw(L.sim, "smooth") };
      L.disp = wi ? { R: rw(L.sim, k, wi), No: L.hasEv ? rw(L.simNo, k, wi) : null, base: rw(L.sim, "base", wi), smooth: rw(L.sim, "smooth", wi) } : null;
    } else {
      L.withEv = L.sim; L.noEv = L.simNo; L.disp = null;
      if (full) { const one = (scen) => (L.scen === scen ? L.sim : Model.simulate(md, { ...L.common, scenario: scen, nPaths: Math.min(L.common.nPaths, 1500), withEvents: true })); L.lens = { base: one("base"), smooth: one("smooth") }; }
    }
    L.scen = S.state.model.scenario;
  }
  const fcDisp = () => { const L = lastForecast; return L.disp || { R: L.withEv, No: L.hasEv ? L.noEv : null, base: L.lens && L.lens.base, smooth: L.lens && L.lens.smooth }; };
  // 슬라이더·시나리오·만약에: 경로가 있으면 바로, 없으면 다시 계산
  let viewRaf = 0;
  function viewChanged(final) {
    const L = lastForecast;
    if (!L || fcDirty || !L.sim || !L.sim.raw) { if (final) { fcDirty = true; runForecast(); } return; }
    cancelAnimationFrame(viewRaf);
    viewRaf = requestAnimationFrame(() => {
      applyView(false); fcCache = {}; fcCache[L.scen] = { R: L.withEv, model: L.b.model };
      if (final) { L.b.model.factors.forEach((f) => (f.mu.blend = Model.blendMu(f.mu.base, f.mu.smooth ?? f.mu.base, Number(S.state.model.trust) / 100))); fcStore(); renderForecast(); }
      else renderForecastView();
    });
  }
  function renderLenses() {
    const L = lastForecast, D = fcDisp(), m = S.state.model, n = L.b.model.eventList.length;
    const lens = (k, name, sub, mu, R) => `<div class="lens l-${k}"><div class="lh"><b>${name}</b><span class="small muted">연 기대 <b class="${cls(mu)}">${spct(mu, 0)}</b></span></div>
      <div class="lv">${R ? pct(R.p_goal, 0) : "-"} <small>목표 확률</small></div><p>목표일 중앙값 ${R ? krw(R.terminal.p50) + "원" : "-"}</p><p>${sub}</p></div>`;
    const No = D.No, R = D.R;
    const shock = `<div class="lens l-shock"><div class="lh"><b>충격 반영</b><span class="small muted">외부 요인 ${nf(n)}건</span></div>` + (L.hasEv && No
      ? `<div class="lv">${krw(No.terminal.p5)} → ${krw(R.terminal.p5)} <small>나쁜 경우 5%</small></div><p>좋은 경우 5% ${krw(No.terminal.p95)} → ${krw(R.terminal.p95)} · 목표 확률 ${pct(No.p_goal, 0)} → ${pct(R.p_goal, 0)}</p><p>중앙값 ${krw(No.terminal.p50)} → ${krw(R.terminal.p50)}. 가운데보다 나쁜 쪽 끝을 더 끌어내립니다 (내 관점 기준).</p>`
      : `<p>켜진 외부 요인이 없습니다. '외부 요인'에서 켜면 결과의 폭이 넓어집니다.</p>`) + "</div>";
    $("#lenses").innerHTML = lens("base", "현재 정세", `과거 수익률을 장기 평균(연 ${m.prior_mu}%) 쪽으로 당긴 값`, portMu("base"), D.base) + lens("smooth", "과거 추세", "지난 3년 성장 속도가 이어지면", portMu("smooth"), D.smooth) + shock;
    const on = m.scenario === "blend";
    $("#trust").value = String(m.trust); $("#trustVal").textContent = on ? m.trust + "%" : "-";
    $(".trust").classList.toggle("off", !on);
    $("#trustNote").textContent = on ? `내 관점 연 기대 ${spct(portMu(viewKey()), 0)}: 아래 숫자와 그래프가 이 관점입니다.` : `계산 옵션에서 '${SCEN_SHORT[m.scenario] || m.scenario}' 시나리오를 골랐습니다. 슬라이더를 움직이면 내 관점으로 돌아갑니다.`;
    // 만약에
    const ok = !!(L.sim && L.sim.raw) || !L.sim;
    const { top, cash } = wiTop(), th = L.b.holdings[top], plain = Number(S.state.goal.monthly_contribution) || 0;
    const mv = WI.monthly == null ? plain : WI.monthly;
    $("#wiMonthly").value = String(Math.round(mv / 1e4)); $("#wiMonthlyV").textContent = mv ? krw(mv) + "원" : "0원";
    $("#wiSellLab").textContent = th ? `${th.ticker} 일부를 ${cash >= 0 ? L.b.holdings[cash].ticker : "현금(연 3.5%)"}로` : "가장 큰 종목 일부 매도";
    $("#wiSell").value = String(WI.sell); $("#wiSellV").textContent = WI.sell ? `${WI.sell}% (${nf(Math.round((th.shares * WI.sell) / 100))}주)` : "0%";
    $$("#wiDet input").forEach((x) => (x.disabled = !ok || m.rebalance_yearly));
    $("#wiNote").textContent = m.rebalance_yearly ? "연 1회 재조정을 켜면 만약에 계산은 꺼집니다." : "저장되지 않는 가정입니다. 세금·수수료는 빼고 계산.";
    $("#wiSum").textContent = L.disp ? `· 적용 중 (월 ${krw(mv)}원${WI.sell ? `, ${th.ticker} ${WI.sell}% 매도` : ""})` : "";
  }
  async function runForecast() {
    const st = $("#fcStatus"), btns = $$("#scenBox button");
    btns.forEach((x) => (x.disabled = true)); st.textContent = `${SCEN_SHORT[S.state.model.scenario] || ""} 시나리오, 계산 중...`;
    await new Promise((r) => setTimeout(r, 30));
    try {
      const t0 = performance.now(), b = buildModelNow();
      if (!b) { st.textContent = "평가액이 있는 종목이 없습니다."; btns.forEach((x) => (x.disabled = false)); return; }
      const m = S.state.model, common = simCommon(b, m.scenario);
      const sim = Model.simulate(b.model, { ...common, withEvents: true });
      const hasEv = b.model.eventList.length > 0;
      const simNo = hasEv ? Model.simulate(b.model, { ...common, withEvents: false }) : sim;
      lastForecast = { b, sim, simNo, hasEv, common, grp: {}, scen: m.scenario, at: new Date(), ms: 0 };
      applyView(true);
      lastForecast.ms = performance.now() - t0;
      fcDirty = false; fcCache = {}; fcCache[m.scenario] = { R: lastForecast.withEv, model: b.model };
      // 목표 확률이 지난번보다 5%p 넘게 바뀌면 알려 준다 (같은 관점일 때)
      try {
        const prev = JSON.parse(localStorage.getItem("naeilo-pgoal") || "null"), now = { p: lastForecast.withEv.p_goal, k: String(viewKey()), d: today() };
        if (prev && prev.k === now.k && prev.d !== now.d && Math.abs(now.p - prev.p) >= 0.05) toast(`목표 확률이 ${prev.d} ${pct(prev.p, 0)}에서 ${pct(now.p, 0)}로 바뀌었습니다`);
        if (!prev || prev.d !== now.d || prev.k !== now.k) localStorage.setItem("naeilo-pgoal", JSON.stringify(now));
      } catch (e) { /* 무시 */ }
      fcStore();
      renderForecast();
      if ($("#tabs .on").dataset.tab === "dash") renderDash();
    } catch (e) { st.textContent = "오류: " + e.message; console.error(e); }
    btns.forEach((x) => (x.disabled = false));
  }
  // 원화 평가액 전망 그래프. 우상단 버튼: 미반영 / 전체 요인 / 거시·기업·수급만 반영 (같은 난수, 같은 경로 수)
  const FCV = { none: "외부 요인 미반영", all: "전체 요인 반영", macro: "거시 요인만 반영", corp: "기업 요인만 반영", flow: "수급 요인만 반영" };
  function fcViewRes(v) {
    const L = lastForecast; if (v === "none") return L.noEv; if (v === "all" || !L.hasEv) return L.withEv;
    if (!L.grp[v]) L.grp[v] = Model.simulate(L.b.model, { ...L.common, withEvents: true, cats: new Set(grpCats(v)) });
    return L.grp[v];
  }
  // 그래프 + 아래 두 상자(연도별 확률, 외부 요인 반영 효과)를 고른 대상(전체·종목)과 요인 버튼에 맞춰 그린다
  function drawFcChart() {
    if (!lastForecast) return;
    const { b, hasEv } = lastForecast, g = S.state.goal, md = b.model, fx = md.monthDates, D = fcDisp();
    const v = hasEv ? $("#fcView .on")?.dataset.v || "all" : "all", R = v === "all" ? D.R : fcViewRes(v), noEv = v === "all" && D.No ? D.No : lastForecast.noEv;
    const marks = $("#fcMarks")?.checked;
    $$("#fcView button").forEach((x) => (x.disabled = !hasEv && x.dataset.v !== "all"));
    let tk = $("#fcTk .on")?.dataset.t || "port"; if (tk !== "port" && !b.holdings.some((h) => h.ticker === tk)) tk = "port";
    $("#fcTk").innerHTML = [["port", "전체"], ...b.holdings.map((h) => [h.ticker, h.ticker])].map(([k, n]) => `<button data-t="${esc(k)}" class="${k === tk ? "on" : ""}">${esc(n)}</button>`).join("");
    const inV = (e) => v === "all" || (v !== "none" && grpOf(e.event.cat || "corp") === v);
    const lab = v === "all" ? "내 관점 중앙값" : v === "none" ? "중앙값 (미반영)" : `중앙값 (${xfName(v)})`, cmp = v === "none" ? "all" : v, Rc = v === "none" ? lastForecast.withEv : R;
    const inC = (e) => cmp === "all" || grpOf(e.event.cat || "corp") === cmp;
    const cmpName = cmp === "all" ? "전체 요인" : `${xfName(cmp)}만`;
    const evTable = (rows, note) => `<table class="grid"><tr><th class="l"></th><th>미반영</th><th>${esc(cmpName)} 반영</th></tr>${rows.map((r) => `<tr><td class="l">${r[0]}</td><td>${v === "none" ? `<b>${r[1]}</b>` : r[1]}</td><td>${v === "none" ? r[2] : `<b>${r[2]}</b>`}</td></tr>`).join("")}</table><p class="muted small">${note}</p>`;
    const vName = FCV[v].replace(" 반영", "");
    if (tk === "port") {
      $("#fcTitle").textContent = "원화 평가액 전망 (환율·외부 요인 포함)";
      const H = history(), k0 = Math.max(0, H.dates.length - 253), V0 = R.V0, yrs = yearsBetween(md.startDate, g.date);
      const reqPath = fx.map((d) => V0 * (g.amount / V0) ** (yearsBetween(md.startDate, d) / yrs));
      const evs = v === "none" ? [] : md.eventList.filter(inV);
      // 전체 보기: 내 관점 띠 + 두 렌즈의 중앙값 + 외부 요인 없을 때의 5~95% 선(충격이 넓힌 폭)
      const lensLines = v === "all" ? [...(D.base ? [{ name: "현재 정세", y: D.base.bands.p50, color: "var(--c3)", width: 1.3 }] : []), ...(D.smooth ? [{ name: "과거 추세", y: D.smooth.bands.p50, color: "var(--c4)", width: 1.3 }] : []),
        ...(hasEv && noEv ? [{ name: "충격 없을 때 5~95%", y: noEv.bands.p5, color: "var(--warn)", width: 1, dash: "2 3" }, { y: noEv.bands.p95, color: "var(--warn)", width: 1, dash: "2 3" }] : [])] : [];
      Charts.lineChart($("#fcChart"), {
        x: fx, height: 340, yfmt: krwAxis,
        bands: [{ lo: R.bands.p5, hi: R.bands.p95, color: "var(--band)", opacity: 0.13, name: "5~95%" }, { lo: R.bands.p25, hi: R.bands.p75, color: "var(--band)", opacity: 0.25, name: "25~75%" }],
        series: [{ name: "과거", x: [...H.dates.slice(k0), md.startDate], y: [...H.total.slice(k0), V0], color: "var(--fg)", width: 1.4 },
          ...lensLines,
          { name: lab, y: R.bands.p50, color: "var(--c1)", width: 2.4 },
          ...(hasEv && v !== "none" && v !== "all" ? [{ name: "미반영 중앙값", y: noEv.bands.p50, color: "var(--muted)", width: 1.2, dash: "2 3" }] : []),
          { name: "필요 경로", y: reqPath, color: "var(--accent2)", dash: "5 4", width: 1.3 }],
        hlines: [{ y: g.amount, label: "목표 " + krw(g.amount) }],
        vlines: [{ x: md.startDate, label: "오늘" }],
        markers: marks ? evs.map((e) => ({ x: e.date, label: `${e.date} ${e.event.target} ${e.event.kind}` })) : [],
      });
      $("#fcViewNote").innerHTML = !hasEv ? `<span class="muted">켜진 외부 요인이 없어 미반영 전망과 같습니다. '외부 요인'에서 켜 주세요.</span>`
        : `<b>${FCV[v]}</b>: 목표 달성 확률 <b>${pct(R.p_goal, 0)}</b>, 목표일 중앙값 <b>${krw(R.terminal.p50)}원</b>, 나쁜 경우 5% ${krw(R.terminal.p5)}원` +
          (v === "none" ? "" : ` <span class="muted">(미반영 ${pct(noEv.p_goal, 0)} · ${krw(noEv.terminal.p50)}원${v === "all" ? "" : `, 반영 사건 ${evs.length}건`})</span>`);
      $("#fcYearsTitle").textContent = `목표 도달 확률 (연도별 누적 · ${vName})`;
      $("#fcYears").innerHTML = R.byYear.map((y) => `<div class="probbar"><span>${y.year}년 내 (${y.date})</span><div class="b"><i style="width:${y.p * 100}%"></i></div><span>${pct(y.p, 0)}</span></div>`).join("") || "기간이 1년 미만입니다.";
      $("#fcEvTitle").textContent = "외부 요인 반영 효과 (전체)";
      $("#fcEvents").innerHTML = !hasEv ? `<p class="muted">켜진 사건이 없습니다. '외부 요인'에서 켜 주세요.</p>`
        : evTable([["목표 달성 확률", pct(noEv.p_goal, 0), pct(Rc.p_goal, 0)], ["목표일 중앙값", krw(noEv.terminal.p50), krw(Rc.terminal.p50)], ["하위 5%", krw(noEv.terminal.p5), krw(Rc.terminal.p5)],
          ["상위 5%", krw(noEv.terminal.p95), krw(Rc.terminal.p95)], ["목표일에 더 낮을 확률", pct(noEv.p_loss, 0), pct(Rc.p_loss, 0)]],
          `사건만 빼고 다시 계산한 비교(미반영 쪽은 실적 변동=평소 변동성). 반영 사건 ${md.eventList.filter(inC).length}건 (반복 포함).`);
    } else {
      const i = b.holdings.findIndex((h) => h.ticker === tk), h = b.holdings[i], s1 = R.stocks[i], s0 = noEv.stocks[i], sc = Rc.stocks[i], c = C[i % C.length];
      $("#fcTitle").textContent = `${tk} 가격 전망 (${h.ccy}, 외부 요인 포함)`;
      const p = S.prices[tk], kk = p ? Math.max(0, p.dates.length - 253) : 0;
      const onTk = (e) => e.event.target === tk || e.event.target === "ALL";
      const evs = v === "none" ? [] : md.eventList.filter((e) => inV(e) && onTk(e));
      Charts.lineChart($("#fcChart"), {
        x: fx, height: 340, log: true, yfmt: priceAxis([s1.bands.p5, s1.bands.p95]),
        bands: [{ lo: s1.bands.p5, hi: s1.bands.p95, color: c, opacity: 0.13, name: "5~95%" }, { lo: s1.bands.p25, hi: s1.bands.p75, color: c, opacity: 0.25, name: "25~75%" }],
        series: [...(p ? [{ name: "과거", x: [...p.dates.slice(kk), md.startDate], y: [...p.close.slice(kk), h.price0], color: "var(--fg)", width: 1.4 }] : []),
          { name: lab, y: s1.bands.p50, color: c, width: 2.2 },
          ...(hasEv && v !== "none" ? [{ name: "미반영 중앙값", y: s0.bands.p50, color: "var(--muted)", width: 1.2, dash: "2 3" }] : [])],
        hlines: [{ y: h.price0, label: "현재 " + nf(h.price0, 2) }],
        vlines: [{ x: md.startDate, label: "오늘" }],
        markers: marks ? evs.map((e) => ({ x: e.date, label: `${e.date} ${e.event.kind}` })) : [],
      });
      const T = s1.bands.p50.length - 1, f2 = (x) => nf(x, 2), T6 = Math.min(T, 6);
      $("#fcViewNote").innerHTML = `<b>${esc(tk)} · ${FCV[v]}</b>: 현재 ${f2(h.price0)} ${h.ccy}, 목표일 중앙값 <b>${f2(s1.bands.p50[T])}</b>, 나쁜 경우 5% ${f2(s1.bands.p5[T])}, 좋은 경우 95% ${f2(s1.bands.p95[T])}, 오를 확률 <b>${pct(s1.p_up, 0)}</b>` +
        (hasEv && v !== "none" ? ` <span class="muted">(미반영 중앙값 ${f2(s0.bands.p50[T])} · 오를 확률 ${pct(s0.p_up, 0)}${v === "all" ? "" : `, 반영 사건 ${evs.length}건`})</span>` : "");
      $("#fcYearsTitle").textContent = `${tk} 연도별 확률 (${vName})`;
      $("#fcYears").innerHTML = (s1.byYear || []).map((y) => `<div class="probbar"><span>${y.year}년 뒤 (${fx[y.k]}) 현재가 이상</span><div class="b"><i style="width:${y.p_up * 100}%"></i></div><span>${pct(y.p_up, 0)}</span></div>`).join("") +
        `<p class="muted small">그 시점 가격이 오늘(${f2(h.price0)} ${h.ccy})보다 높을 확률. 2배 이상: ${(s1.byYear || []).map((y) => `${y.year}년 ${pct(y.p_x2, 0)}`).join(" · ")}. 포트폴리오 목표 확률은 '전체'에서 봄.</p>`;
      $("#fcEvTitle").textContent = `외부 요인 반영 효과 (${tk})`;
      $("#fcEvents").innerHTML = !hasEv ? `<p class="muted">켜진 사건이 없습니다. '외부 요인'에서 켜 주세요.</p>`
        : evTable([["목표일 중앙값", f2(s0.bands.p50[T]), f2(sc.bands.p50[T])], ["6개월 뒤 중앙값", f2(s0.bands.p50[T6]), f2(sc.bands.p50[T6])],
          ["하위 5%", f2(s0.bands.p5[T]), f2(sc.bands.p5[T])], ["상위 5%", f2(s0.bands.p95[T]), f2(sc.bands.p95[T])], ["오를 확률", pct(s0.p_up, 0), pct(sc.p_up, 0)]],
          `종목 가격(${h.ccy}) 기준, 사건만 빼고 다시 계산한 비교. 이 종목에 걸린 사건(시장·요인 사건 포함) ${md.eventList.filter((e) => inC(e) && onTk(e)).length}건.`);
    }
  }
  function renderForecast() {
    const m = S.state.model;
    $$("#scenBox button").forEach((x) => x.classList.toggle("on", x.dataset.s === m.scenario)); $("#nPaths").value = String(m.n_paths); $("#rebalance").checked = !!m.rebalance_yearly;
    if (!lastForecast) { $("#fcStatus").textContent = `${SCEN_SHORT[m.scenario] || ""} 시나리오`; return; }
    const { b, withEv: R, noEv, hasEv } = lastForecast, g = S.state.goal, md = b.model;
    // 직접 입력한 현재가 때문에 오늘 평가액이 시세 기준과 크게 다르면 알린다 (차트가 오늘에서 꺾이는 원인)
    const manual = b.holdings.filter((h) => { const src = S.state.holdings.find((x) => x.ticker === h.ticker); const mk = src && curPrice({ ...src, price: null }).v; return src && Number(src.price) > 0 && mk && Math.abs(h.price0 / mk - 1) > 0.05; });
    const Hh = history(), lastHist = Hh.total[Hh.total.length - 1];
    $("#fcWarn").innerHTML = manual.length && lastHist ? `오늘 평가액(${krw(R.V0)}원)이 시세 기준(${krw(lastHist)}원)과 ${spct(R.V0 / lastHist - 1, 0)} 다릅니다. <b>${manual.map((h) => esc(h.ticker)).join(", ")}</b>에 현재가를 직접 넣었기 때문입니다. 매수 단가였다면 설정에서 그 값을 지우고 '평균 매수가' 칸으로 옮겨 주세요.` : "";
    $("#fcWarn").style.display = $("#fcWarn").innerHTML ? "block" : "none";
    $("#fcStatus").textContent = `${SCEN_SHORT[lastForecast.scen] || ""} 시나리오, ${dtStr(lastForecast.at)}` + (fcDirty ? " · 입력이 바뀜, 시나리오를 눌러 다시 계산" : "");
    $("#fcStatus").title = `${SCEN_LAB[lastForecast.scen] || ""} · 경로 ${nf(m.n_paths)}개 × ${md.days.length}거래일 · ${(lastForecast.ms / 1000).toFixed(1)}초`;
    renderForecastView(true);
    // 모형 값
    const scen = m.scenario;
    $("#fcParams").innerHTML = `<tr><th class="l">종목</th><th>비중</th><th>변동성</th><th>현재 정세<br><span class="muted">과거 추세</span></th><th>적용 (${esc(SCEN_SHORT[scen] || scen)})</th></tr>` +
      md.factors.map((f, i) => `<tr><td class="l">${f.kind === "fx" ? "환율 " + f.key : esc(f.key)}${f.cash ? ' <span class="tag">현금성</span>' : ""}</td>
        <td>${f.kind === "asset" ? pct(b.holdings[i].valueKrw / R.V0, 0) : "-"}</td><td>${pct(f.vol, 0)}</td><td>${pct(f.mu.base, 0)}<br><span class="muted">${pct(f.mu.smooth ?? f.mu.base, 0)}</span></td><td><b>${pct(f.mu[scen], 0)}</b></td></tr>`).join("") +
      `<tr><td class="l muted wrapc" colspan="5">상관: ${md.factors.map((f, i) => md.factors.slice(0, i).map((g2, j) => `${f.key}–${g2.key} ${md.corr[i][j].toFixed(2)}`).join(", ")).filter(Boolean).join(" · ")}</td></tr>`;
    renderStrategy();
    if (curAna() === "fx") renderFx();
    if (curAna() === "events") runXfEffect();
    aiRefresh();
  }

  // 내 관점 숫자·렌즈·그래프 (슬라이더를 움직일 때는 이것만 다시 그린다)
  function renderForecastView() {
    if (!lastForecast) return;
    const D = fcDisp(), R = D.R, g = S.state.goal, m = S.state.model, L = lastForecast;
    renderLenses();
    const mv = R.monthly ?? (Number(g.monthly_contribution) || 0), cb = costBasis();
    $("#fcViewLab").innerHTML = `<b>${m.scenario === "blend" ? `내 관점 · 추세 신뢰 ${m.trust}%` : `${esc(SCEN_SHORT[m.scenario] || m.scenario)} 시나리오`}</b>${L.disp ? ' <span class="tag">만약에 적용</span>' : ""}`;
    const lossK = cb ? "목표일에 매수가보다 낮을 확률" : "목표일에 지금보다 낮을 확률";
    const lossS = cb ? `매수 원금 ${krw(cb)}원${mv ? " + 적립" : ""} 기준` : mv ? `지금 ${krw(R.V0)}원 + 적립 기준` : `지금 ${krw(R.V0)}원 기준`;
    $("#fcKpis").innerHTML = [
      ["목표 달성 확률", pct(R.p_goal, 0), `목표일 ${krw(g.amount)}원 이상`],
      ["한 번이라도 도달", pct(R.p_touch, 0), "목표일 전 언제나"],
      ["목표일 중앙값", krw(R.terminal.p50) + "원", `평균 <b>${krw(R.terminal.mean)}원</b>`],
      ["나쁜 경우 (하위 5%)", krw(R.terminal.p5) + "원", `하위 25% ${krw(R.terminal.p25)}원`],
      ["좋은 경우 (상위 5%)", krw(R.terminal.p95) + "원", `상위 25% ${krw(R.terminal.p75)}원`],
      [lossK, pct(R.p_loss, 0), lossS],
      ["최대 낙폭 (중앙값)", pct(R.mdd_median, 0), `나쁜 10%: ${pct(R.mdd_p10, 0)}`],
      ["확률 50% 위한 월 적립", R.req50 == null ? "-" : R.req50 === 0 ? "0원" : krw(R.req50) + "원", R.req50 == null ? "재조정 끄면 계산" : "매월 정액 매수 가정"],
    ].map(([k, v, s2]) => `<div class="kpi"><div class="k">${k}</div><div class="v">${v}</div><div class="s">${s2}</div></div>`).join("");
    // 평균은 오르는데 중앙값이 낮은 이유: 집중·변동성 비용
    const b = L.b, md = b.model, V = b.holdings.reduce((a, h) => a + h.valueKrw, 0);
    const top = b.holdings.map((h, i) => [h, h.valueKrw / V, md.factors[i]]).filter((x) => !x[2].cash).sort((a, c) => c[1] - a[1])[0];
    const gap = R.terminal.mean / R.terminal.p50;
    const ex = gap > 1.2 && top ? `평균(${krw(R.terminal.mean)}원)은 오르지만 절반의 경우는 중앙값(${krw(R.terminal.p50)}원) 아래입니다. ${esc(top[0].ticker)} 비중 ${pct(top[1], 0)}, 변동성 연 ${pct(top[2].vol, 0)}로 결과가 넓게 퍼져서 생기는 차이(변동성 비용)이고, 분산하면 이 차이와 낮을 확률이 함께 줄어듭니다.` : "";
    $("#fcExplain").innerHTML = ex; $("#fcExplain").style.display = ex ? "block" : "none";
    drawFcChart();
  }
  // 종목별 가격 전망 (접어 둔 카드를 펼칠 때 그린다)
  // ------------------------------------------------------------ 종목별 전략 (규칙 기반)
  function renderStrategy() {
    if (!lastForecast) return;
    const { b, withEv: R } = lastForecast, g = S.state.goal, md = b.model, V0 = R.V0;
    const yrs = yearsBetween(md.startDate, g.date), req = (g.amount / V0) ** (1 / yrs) - 1, medC = (R.terminal.p50 / V0) ** (1 / yrs) - 1;
    const w = b.holdings.map((h) => h.valueKrw / V0), risky = b.holdings.map((h, i) => (md.factors[i].cash ? 0 : w[i]));
    const top = risky.indexOf(Math.max(...risky)), cashW = b.holdings.reduce((s2, h, i) => s2 + (md.factors[i].cash ? w[i] : 0), 0);
    const near = (e) => yearsBetween(md.startDate, e.date) <= 0.34;
    const soon = (t) => md.eventList.filter((e) => e.event.target === t && near(e));
    // 시장 전체(공통) 일정은 진단에 한 번만
    const common = []; md.eventList.filter((e) => e.event.target === "ALL" && near(e)).forEach((e) => { const c = common.find((x) => x.k === e.event.kind); if (c) { if (c.d.length < 3) c.d.push(e.date.slice(5)); } else common.push({ k: e.event.kind, d: [e.date.slice(5)] }); });
    const tips = [];
    tips.push(`**목표 확률 ${pct(R.p_goal, 0)}** (${scenName(S.state.model.scenario)} 시나리오). 필요한 연수익률 **${pct(req)}**, 전망 중앙값의 연수익률 **${pct(medC)}**.`);
    if (R.p_goal < 0.5 && R.req50 != null) tips.push(`**적립**: 지금 비중 그대로 확률 50%를 맞추려면 매월 약 **${krw(R.req50)}원**을 더 넣어야 합니다 (월 적립은 자산 추이의 목표 수정에서 입력).`);
    if (risky[top] > 0.45) tips.push(`**집중도**: ${b.holdings[top].ticker} 한 종목이 **${pct(w[top], 0)}**입니다. 하위 5% 결과가 ${krw(R.terminal.p5)}원까지 내려갑니다. '비중 조정'에서 줄였을 때를 확인해 보세요.`);
    if (cashW < 0.03) tips.push(`**현금**: 현금성 자산이 ${pct(cashW, 1)}입니다. 하락장에서 살 여력과 심리적 완충을 위해 3~5%를 권합니다.`);
    tips.push(`**낙폭**: 최대 낙폭 중앙값 ${pct(R.mdd_median, 0)}. 목표일까지 가는 동안 이 정도 하락은 흔하다는 뜻입니다.`);
    const rg = rebalanceGap();
    if (rg && rg.gaps.length && yearsBetween(rg.tg.at, today()) < 0.5) tips.push(`**비중 조정 진행 중**: '${rg.tg.name}' (${rg.tg.at}에 목표로 정함). 남은 차이 ${rg.gaps.map(([t, d]) => `${t} ${d > 0 ? "+" : ""}${(d * 100).toFixed(0)}%p`).join(", ")}.`);
    else if (rg) tips.push(rg.gaps.length ? `**리밸런싱 신호**: 목표로 정한 '${rg.tg.name}' 비중에서 ${rg.gaps.map(([t, d]) => `${t} ${d > 0 ? "+" : ""}${(d * 100).toFixed(0)}%p`).join(", ")} 벗어났습니다. '비중 조정'에서 실행 계획을 보세요.` : `**리밸런싱**: 목표로 정한 '${rg.tg.name}' 비중 안에 있습니다 (±5%p).`);
    if (common.length) tips.push(`**공통 일정** (모든 종목): ${common.slice(0, 4).map((c) => `${c.k} ${c.d.join(", ")}`).join(" · ")}`);
    $("#stratSummary").innerHTML = `<div class="md small">${md2html(tips.map((t) => "- " + t).join("\n"))}</div>`;

    const total = V0;
    $("#stratCards").innerHTML = b.holdings.map((h, i) => {
      const f = md.factors[i], p = S.prices[h.ticker], ind = p ? Model.indicators(p.dates, p.adj) : null, sg = ind?.sig, st = R.stocks[i];
      const ev = soon(h.ticker), lock = ev.find((e) => /보호예수/.test(e.event.kind));
      let act, klass, why = [];
      if (f.cash) { act = "유지 (현금 완충)"; klass = "cash"; why.push(`**성격**: 비중 ${pct(w[i], 1)}, 연 ${pct(f.mu.base)} 수준의 단기 국채형`); }
      else if (f.n < 252) { act = "보유, 추가 매수 보류"; klass = "wait"; why.push(`**이력**: 상장 후 ${f.n}거래일로 짧아 변동성(${pct(f.vol, 0)}) 추정이 불확실`); if (lock) why.push(`**${lock.event.kind}**: ${lock.date} 예정. 물량 출회로 단기 하락 가능, 이후 재판단`); }
      else if (w[i] > 0.45) {
        const tgt = 0.45, sell = Math.ceil(((w[i] - tgt) * total) / (h.valueKrw / h.shares));
        act = `비중 축소 검토 (→ ${pct(tgt, 0)})`; klass = "trim";
        why.push(`**비중**: ${pct(w[i], 0)}로 한 종목 집중. 약 **${nf(sell)}주**를 6개월에 나눠 지수(QQQ 등)로 옮기면 ${pct(tgt, 0)}`);
        why.push("**세금**: 양도세가 있으면 연도를 나눠 매도 (해외주식 연 250만원 공제)");
      } else if (sg && sg.trend === "하락 추세") { act = "추가 매수 보류, 관찰"; klass = "wait"; }
      else if (sg && /상승/.test(sg.trend) && w[i] > 0.25) { act = "보유 (25~30% 넘지 않게)"; klass = "hold"; }
      else { act = "보유"; klass = "hold"; }
      if (sg && !f.cash) why.push(`**상태**: 고점 대비 ${pct(sg.drawdown, 0)}` + (sg.ema200 ? `, 200일선 ${sg.close >= sg.ema200 ? "위" : "아래"} (${spct(sg.close / sg.ema200 - 1, 0)})` : ", 200일선 판단 보류 (이력 짧음)") + `, 비중 ${pct(w[i], 0)}${risky[i] > 0.45 ? " (집중)" : ""}`);
      if (!f.cash) why.push(`**전망**: 목표일 가격 중앙값 ${nf(st.bands.p50[st.bands.p50.length - 1], 2)} ${h.ccy} (현재 ${nf(h.price0, 2)}), 오를 확률 **${pct(st.p_up, 0)}**, 적용 기대수익 연 ${pct(f.mu[S.state.model.scenario])}`);
      ev.filter((e) => e !== lock).slice(0, 2).forEach((e) => why.push(`**${e.event.kind}**: ${e.date} (±${e.event.sd}%)`));
      return `<div class="strat"><h3>${esc(h.ticker)} <span class="muted small">비중 ${pct(w[i], 1)} · ${krw(h.valueKrw)}원</span></h3><div class="act ${klass}">${act}</div><div class="md">${md2html(why.map((x) => "- " + x).join("\n"))}</div></div>`;
    }).join("");
  }

  // ------------------------------------------------------------ AI 의견 (무료 AI 사이트로 질문 보내기)
  function aiPrompt() {
    if (!lastForecast) return "";
    const { b, withEv: R } = lastForecast, g = S.state.goal, md = b.model, V0 = R.V0, yrs = yearsBetween(md.startDate, g.date);
    const lines = [];
    lines.push(`내 미국·한국 주식 포트폴리오의 종목별 투자 전략을 조언해 줘. 아래는 내 도구가 계산한 값이야 (${md.startDate} 기준, 원화).`);
    lines.push(`목표: ${krw(g.amount)}원, 목표일 ${g.date} (${yrs.toFixed(1)}년). 현재 평가액 ${krw(V0)}원, 필요한 연수익률 ${pct((g.amount / V0) ** (1 / yrs) - 1)}.`);
    lines.push(`몬테카를로 전망(환율·외부 요인 포함): 목표 달성 확률 ${pct(R.p_goal, 0)}, 목표일 중앙값 ${krw(R.terminal.p50)}원, 하위5% ${krw(R.terminal.p5)}원, 상위5% ${krw(R.terminal.p95)}원, 최대낙폭 중앙값 ${pct(R.mdd_median, 0)}.`);
    lines.push("종목 (비중 / 추세 / 칼만 기울기 연율 / 변동성 / 고점 대비 / 1년 수익률 / 목표일까지 오를 확률):");
    b.holdings.forEach((h, i) => {
      const p = S.prices[h.ticker], sg = p ? Model.indicators(p.dates, p.adj)?.sig : null, f = md.factors[i];
      lines.push(`- ${h.ticker}: ${pct(h.valueKrw / V0, 0)} / ${sg ? sg.trend : "-"} / ${sg ? spct(sg.slope_ann, 0) : "-"} / ${pct(f.vol, 0)} / ${sg ? pct(sg.drawdown, 0) : "-"} / ${sg && sg.ret_1y != null ? spct(sg.ret_1y, 0) : "-"} / ${pct(R.stocks[i].p_up, 0)}${f.n < 252 ? ` (상장 ${f.n}거래일)` : ""}`);
    });
    const ev = md.eventList.filter((e) => yearsBetween(md.startDate, e.date) <= 0.5).slice(0, 8);
    if (ev.length) lines.push("6개월 내 사건: " + ev.map((e) => `${e.date} ${e.event.target} ${e.event.kind}`).join(", "));
    lines.push("요청: 1) 종목별로 보유·비중 축소·추가 매수 중 무엇이 맞는지 이유와 함께, 2) 목표 확률을 높이면서 위험을 줄이는 비중 조정안, 3) 앞으로 3개월 동안 할 일 3가지. 한국 거주자 세금(해외주식 양도세 250만원 공제)도 고려해서 한국어 마크다운으로 아주 짧게 답해 줘.");
    return lines.join("\n");
  }
  // 페이지별 질문: 수량은 넣지 않고 비중(%)과 지표만
  function sigOf(t) { const p = S.prices[t]; return p ? Model.indicators(p.dates, p.adj)?.sig : null; }
  function aiPromptFor(kind) {
    if (kind === "strategy") return aiPrompt();
    const g = S.state.goal, L = [], tail = "한국어 마크다운으로 아주 짧게 답해 줘.";
    if (kind === "trend") {
      const keys = S.state.holdings.map((h) => h.ticker).filter((t) => S.prices[t]);
      if (!keys.length) return "";
      const { rows } = valuation();
      L.push(`내 보유 종목의 추세 지표야 (${today()} 기준). 칼만 필터(로그가격 수준+기울기)와 EMA로 계산했어.`);
      L.push("종목 (비중 / 추세 판정 / 현재가 / 칼만 수준 대비 / 칼만 기울기 연율 / 기울기 z / EMA50 / EMA200 / EWMA 변동성 / 고점 대비 / 1개월 / 3개월 / 1년):");
      keys.forEach((t) => { const s2 = sigOf(t), r = rows.find((x) => x.h.ticker === t); if (!s2) return;
        L.push(`- ${t}: ${pct(r?.w, 0)} / ${s2.trend} / ${nf(s2.close, 2)} / ${spct(s2.dev_from_kalman)} / ${spct(s2.slope_ann, 0)} / ${s2.slope_z.toFixed(2)} / ${nf(s2.ema50, 2)} / ${s2.ema200 ? nf(s2.ema200, 2) : "-"} / ${pct(s2.vol_ewma, 0)} / ${pct(s2.drawdown, 0)} / ${spct(s2.ret_1m)} / ${spct(s2.ret_3m)} / ${spct(s2.ret_1y)}`); });
      L.push("요청: 1) 종목별 추세가 지금 어떤 국면인지(상승 지속, 조정, 반등, 하락)와 근거, 2) 칼만 수준·EMA 기준으로 매수·축소를 고려할 가격대나 신호, 3) 추세 지표의 한계와 주의점. " + tail);
      return L.join("\n");
    }
    if (kind === "dash") {
      const { rows, total } = valuation(); if (!(total > 0)) return "";
      const H = history(), k = H.dates.length - 1, j3 = Math.max(0, k - 756);
      const Fs = [["내 관점 (" + scenName(S.state.model.scenario) + ")", fcReady(S.state.model.scenario)], ["현재 정세", fcReady("base")], ["과거 추세 (3년 추세선)", fcReady("smooth")]].filter(([, f]) => f && f.R);
      if (!Fs.length) return "";
      const cagr = H.index.length > 30 ? H.index[k] ** (252 / k) - 1 : null;
      L.push(`내 포트폴리오의 과거 3년과 미래(목표일 ${g.date}까지) 원화 평가액 흐름이야 (${today()} 기준, 현재 수량을 과거에 그대로 적용). 목표 ${krw(g.amount)}원.`);
      L.push(`합계: 현재 ${krw(total)}원, ${H.dates[j3]} ${krw(H.total[j3])}원, 1년 전 ${krw(H.total[Math.max(0, k - 252)])}원, 과거 연평균(신규 편입 효과 제외) ${pct(cagr)}.`);
      L.push("종목 (비중 / 과거 3년 연평균 가격 수익률 / 1년 / 고점 대비 / 칼만·EMA 추세 성장률 연 / 3년 스무딩 성장률 연):");
      rows.filter((r) => r.valueKrw > 0).forEach((r) => {
        const p = S.prices[r.h.ticker]; if (!p) return;
        const n = p.adj.length, i0 = Math.max(0, n - 757), c3 = n > 30 ? (p.adj[n - 1] / p.adj[i0]) ** (252 / (n - 1 - i0)) - 1 : null;
        const ind = Model.indicators(p.dates, p.adj), sf = Model.smoothFit(p.dates, p.adj, 3), sg = ind?.sig;
        L.push(`- ${r.h.ticker}: ${pct(r.w, 0)} / ${pct(c3)}${n < 252 ? ` (상장 ${n}거래일)` : ""} / ${sg?.ret_1y != null ? spct(sg.ret_1y, 0) : "-"} / ${sg ? pct(sg.drawdown, 0) : "-"} / ${ind ? spct(Math.exp(Model.trendGrowth(ind)) - 1, 0) : "-"} / ${sf ? spct(Math.exp(sf.slope) - 1, 0) : "-"}`);
      });
      L.push("미래 전망 (몬테카를로, 환율·외부 요인 포함) 기준별: 목표 달성 확률 / 목표일 중앙값 / 하위5% / 상위5%:");
      Fs.forEach(([nm, f]) => L.push(`- ${nm}: ${pct(f.R.p_goal, 0)} / ${krw(f.R.terminal.p50)}원 / ${krw(f.R.terminal.p5)}원 / ${krw(f.R.terminal.p95)}원`));
      L.push(`종목별 목표일 원화 평가액 중앙값 (${Fs.map(([nm]) => nm.split(" ")[0]).join(" / ")}):`);
      Fs[0][1].R.stocks.forEach((s2, i) => L.push(`- ${s2.ticker}: ${Fs.map(([, f]) => krw(f.R.stocks[i]?.valBands.p50.at(-1)) + "원").join(" / ")}`));
      L.push("요청: 1) 과거 3년 합계와 종목별 흐름 요약, 2) 세 가지 미래 전망이 왜 다른지와 어느 쪽이 더 현실적인지, 3) 종목별 시사점(목표 확률을 높이거나 위험을 줄이는 방향). " + tail);
      return L.join("\n");
    }
    if (kind === "fx") {
      const F = fxInfo(); if (!F) return "";
      const sg = F.sg;
      L.push(`원/달러 환율과 내 포트폴리오의 환율 노출이야 (${today()} 기준). 나는 한국 거주자이고 목표는 원화 ${krw(g.amount)}원, 목표일 ${g.date}.`);
      L.push(`현재 ${nf(F.now, 1)}원, 칼만 추세 수준 ${nf(sg.kalman_level, 1)} (괴리 ${spct(sg.dev_from_kalman)}), 칼만 기울기 연 ${spct(sg.slope_ann, 1)} (z ${sg.slope_z.toFixed(2)}), EMA50 ${nf(sg.ema50, 1)}, EMA200 ${sg.ema200 ? nf(sg.ema200, 1) : "-"}, 추세 판정 ${sg.trend}.`);
      L.push(`변화: 1개월 ${spct(sg.ret_1m)}, 3개월 ${spct(sg.ret_3m)}, 1년 ${spct(sg.ret_1y)}. 1년 범위 ${nf(F.lo1, 0)}~${nf(F.hi1, 0)}. EWMA 변동성 연 ${pct(sg.vol_ewma, 1)}.`);
      L.push(`달러 자산 비중 ${pct(F.usdW, 0)}. 원화가 10% 강세가 되면 원화 평가액이 약 ${pct(F.usdW * 0.1, 1)} 줄어. 모형의 목표일 환율 중앙값 ${nf(F.at(F.T, "p50"), 0)} (5~95% ${nf(F.at(F.T, "p5"), 0)}~${nf(F.at(F.T, "p95"), 0)}).`);
      L.push("요청: 1) 지금 환율 수준과 추세 해석, 2) 앞으로 1년 환율에 영향을 줄 요인(금리차, 경상수지, 위험 선호 등), 3) 달러 자산 비중이 이 정도일 때 환헤지·원화 자산 분산·달러 매도 시점 등 대응 방법. " + tail);
      return L.join("\n");
    }
    if (!lastForecast) return "";
    const { b, withEv: R, noEv, hasEv } = lastForecast, md = b.model, V0 = R.V0, yrs = yearsBetween(md.startDate, g.date), scen = S.state.model.scenario;
    const head = `목표 ${krw(g.amount)}원, 목표일 ${g.date} (${yrs.toFixed(1)}년), 현재 평가액 ${krw(V0)}원, 필요한 연수익률 ${pct((g.amount / V0) ** (1 / yrs) - 1)}, 월 적립 ${krw(Number(g.monthly_contribution) || 0)}원.`;
    if (kind === "forecast") {
      L.push(`내 포트폴리오의 3년 몬테카를로 전망 결과야 (다변량 t 분포, 환율·외부 요인 포함, ${nf(S.state.model.n_paths)}경로, ${scenName(scen)} 시나리오). ${head}`);
      L.push(`목표 달성 확률 ${pct(R.p_goal, 0)}, 중간에 한 번이라도 도달 ${pct(R.p_touch, 0)}, 목표일 중앙값 ${krw(R.terminal.p50)}원, 하위5% ${krw(R.terminal.p5)}원, 상위5% ${krw(R.terminal.p95)}원, 목표일에 기준(지금 또는 매수가)보다 낮을 확률 ${pct(R.p_loss, 0)}, 최대낙폭 중앙값 ${pct(R.mdd_median, 0)}.`);
      L.push("연도별 누적 도달 확률: " + R.byYear.map((y) => `${y.year}년 내 ${pct(y.p, 0)}`).join(", "));
      L.push("종목 (비중 / 적용 기대수익 연 / 모형 변동성 / 오를 확률):");
      b.holdings.forEach((h, i) => { const f = md.factors[i]; L.push(`- ${h.ticker}: ${pct(h.valueKrw / V0, 0)} / ${pct(f.mu[scen])} / ${pct(f.vol, 0)} / ${pct(R.stocks[i].p_up, 0)}${f.n < 252 ? ` (상장 ${f.n}거래일)` : ""}`); });
      L.push("요청: 1) 이 결과를 쉽게 해석, 2) 가정(기대수익·변동성)이 낙관적이거나 비관적인 부분, 3) 목표 확률을 높일 현실적인 방법 3가지. " + tail);
      return L.join("\n");
    }
    if (kind === "events") {
      // 화면의 타일·표와 같은 값만 보낸다 (펼친 반복 일정 대신 사건 단위로, 꺼진 사건도 표시)
      L.push(`내 포트폴리오 전망 모형에 넣은 외부 요인 가정과 그 효과야. 요인은 3개 박스로 나눴어: 거시(금융·통화: 금리·통화정책/환율/인플레이션, 원자재: 유가/금값/기타 금속·곡물, 정치·지정학: 선거·정책/전쟁·분쟁/무역·제재), 기업(실적·공시, 신제품·리콜, 주요 KPI), 수급(기관·외국인 매매 동향). ${head}`);
      L.push("보유 비중: " + b.holdings.map((h) => `${h.ticker} ${pct(h.valueKrw / V0, 0)}`).join(", "));
      const B = factorBetas();
      L.push("종목별 민감도 (최근 3년 일별, 시장 외 요인은 시장 움직임 제외): " + b.holdings.map((h) => `${h.ticker} 시장베타 ${(B.stat.mkt[h.ticker]?.beta ?? NaN).toFixed(2)}, 금리+10bp ${spct((B.stat.rate[h.ticker]?.beta ?? NaN) * 10, 2)}, 유가+10% ${spct((B.stat.oil[h.ticker]?.beta ?? NaN) * 0.0953, 2)}, 금+10% ${spct((B.stat.gold[h.ticker]?.beta ?? NaN) * 0.0953, 2)}, 원자재+10% ${spct((B.stat.cmdty[h.ticker]?.beta ?? NaN) * 0.0953, 2)}`).join("; "));
      L.push("사건 (묶음 / 대상 / 종류 / 다음 날짜(추정) / 반복 / 발생 확률 / 평균 영향 / 불확실성 ± / 변동성 확대 / 상태). 요인 사건의 영향은 요인 단위(시장=S&P500 %, 금리=bp, 유가·금·원자재=%)이고 종목별 민감도만큼 반영:");
      S.state.events.forEach((e) => { const o = nextOcc(e), u = e.factor === "rate" ? "bp" : "%";
        L.push(`- ${catName(e.cat)} / ${tgtLab(e)} / ${e.kind} / ${o.next || "지남"} / ${e.repeat && e.repeat !== "none" ? `${repName(e.repeat)}, 목표일까지 ${o.n}회` : "한 번"} / ${e.prob}% / ${e.mean > 0 ? "+" : ""}${e.mean}${u} / ±${e.sd}${u} / ${e.vol_mult && e.vol_mult !== 1 && e.vol_days ? `${e.vol_mult}배 ${e.vol_days}거래일` : "없음"} / ${e.on ? "켜짐" : "꺼짐(모형 제외)"}`); });
      if (!S.state.events.length) L.push("- (사건 없음)");
      L.push("참고: 평균 영향 0인 사건은 방향 없이 변동만 키운다는 뜻이고, 반복 사건의 변동은 과거 변동성에 이미 들어 있어 그만큼 평소 변동성에서 뺐다.");
      if (hasEv) L.push(`몬테카를로 결과 (요인 제외 → 반영): 목표 확률 ${pct(noEv.p_goal, 0)} → ${pct(R.p_goal, 0)}, 목표일 중앙값 ${krw(noEv.terminal.p50)} → ${krw(R.terminal.p50)}원, 하위5% ${krw(noEv.terminal.p5)} → ${krw(R.terminal.p5)}원.`);
      if (xfEff && xfEff.fc === lastForecast) L.push("요인별 영향 (그 박스·버튼만 켰을 때 목표 확률 변화): " + [...Object.entries(xfEff.grp), ...Object.entries(xfEff.by).filter(([, X]) => !Object.values(xfEff.grp).includes(X))].map(([c, X]) => `${xfName(c)} ${spct(X.p_goal - xfEff.none.p_goal, 0)}p`).join(", "));
      L.push("요청: 위 목록과 숫자만 근거로 1) 요인 묶음별로 가정한 확률·영향 크기가 과거 사례에 비춰 적절한지(사건 이름과 숫자를 그대로 인용), 2) 내 포트폴리오가 가장 민감한 외부 요인과 그 이유, 3) 목록에 없지만 넣을 만한 요인(있다면 '추가 고려'로 구분, 날짜는 확인 필요 표시), 4) 요인별 대응. 목록에 없는 사건을 이미 있는 것처럼 말하지 마. " + tail);
      return L.join("\n");
    }
    if (kind === "alloc") {
      if (!lastAlloc) return "";
      L.push(`내 포트폴리오의 비중 조정안을 같은 난수로 시뮬레이션한 비교야 (안별 1,500경로, 사건 포함, 차액은 QQQ로 이동, 조정안은 연 1회 재조정). ${head}`);
      L.push("안 (비중 / 목표 확률 / 목표일 중앙값 / 하위5% / 원금 손실 확률 / 최대낙폭 중앙값):");
      lastAlloc.out.forEach((o) => L.push(`- ${o.name}: ${lastAlloc.base.map((h, i) => (o.w[i] > 0.004 ? `${h.ticker} ${pct(o.w[i], 0)}` : "")).filter(Boolean).join(", ")} / ${pct(o.R.p_goal, 0)} / ${krw(o.R.terminal.p50)} / ${krw(o.R.terminal.p5)} / ${pct(o.R.p_loss, 0)} / ${pct(o.R.mdd_median, 0)}`));
      L.push("요청: 1) 안별 장단점(목표 확률 대 위험), 2) 어떤 안을 추천하는지와 이유, 3) 실행 방법(나눠 매도·매수, 한국 거주자 해외주식 양도세 연 250만원 공제 고려). " + tail);
      return L.join("\n");
    }
    return "";
  }
  // Puter 답 저장소
  const AI_KEY = "asset-tracker-ai";
  // 마크다운 → HTML (AI 답과 규칙 기반 의견에 공통). 소제목, 굵게·기울임·코드·링크, 중첩 목록, 표, 인용, 구분선, 코드 블록
  // AI 답 정리: JSON 으로 온 답, <think> 블록, 답 전체를 감싼 ```markdown 코드 블록, 무료 서비스가 덧붙인 광고 문구
  function cleanAi(t) {
    t = String(t ?? "").replace(/\r/g, "").trim();
    // 따옴표로 감싼 JSON 문자열, JSON 객체, 줄바꿈이 \n 글자로 온 답
    if (/^"[\s\S]*"$/.test(t)) { try { const j = JSON.parse(t); if (typeof j === "string") t = j.trim(); } catch (e) { /* 그대로 */ } }
    if (/^\{[\s\S]*\}$/.test(t)) {
      try { const j = JSON.parse(t); t = String(j.choices?.[0]?.message?.content ?? j.message?.content ?? j.content ?? j.response ?? j.text ?? t); } catch (e) { /* 그대로 */ }
    }
    if (!t.includes("\n") && /\\n/.test(t)) t = t.replace(/\\n/g, "\n").replace(/\\t/g, "  ").replace(/\\"/g, '"');
    t = t.replace(/<think>[\s\S]*?<\/think>/gi, "").replace(/^[\s\S]*?<\/think>/i, "").trim();
    // 답 전체를 감싼 코드 블록 (닫는 ``` 가 잘려 없어도)
    const fence = t.match(/^```[ \t]*(markdown|md|text|html)?[ \t]*\n([\s\S]*?)\n?(```\s*)?$/i);
    if (fence && (t.match(/```/g) || []).length <= 2) t = fence[2];
    const ad = t.search(/\n[^\n]*(Support Pollinations|Powered by Pollinations|🌸\s*\**\s*Ad\b)/i);
    if (ad >= 0) t = t.slice(0, ad).replace(/\n\s*(-{3,}|\*{3,})\s*$/, "");
    return tex2txt(html2md(t)).trim();
  }
  // HTML 로 온 답은 마크다운으로 바꿔 같은 방식으로 그린다 (태그가 글자로 보이지 않게)
  function html2md(t) {
    if (!/<\/?(h[1-6]|p|ul|ol|li|br|div|table|tr|td|th|strong|em|b|i|hr|blockquote|span|section|article)\b[^>]*>/i.test(t)) return t;
    const ent = { amp: "&", lt: "<", gt: ">", quot: '"', "#39": "'", nbsp: " " };
    return t.replace(/<(script|style|head)\b[\s\S]*?<\/\1>/gi, "")
      .replace(/<h[1-6][^>]*>([\s\S]*?)<\/h[1-6]>/gi, (_, x) => `\n### ${x.replace(/<[^>]+>/g, "").trim()}\n`)
      .replace(/<li[^>]*>/gi, "\n- ").replace(/<\/li>/gi, "")
      .replace(/<br\s*\/?>/gi, "\n").replace(/<hr[^>]*>/gi, "\n---\n")
      .replace(/<\/?(strong|b)\b[^>]*>/gi, "**").replace(/<\/?(em|i)\b[^>]*>/gi, "*")
      .replace(/<\/(p|div|ul|ol|table|tr|blockquote|section|article)>/gi, "\n").replace(/<\/t[dh]>/gi, " · ")
      .replace(/<[^>]+>/g, "")
      .replace(/&(amp|lt|gt|quot|#39|nbsp);/g, (_, k) => ent[k])
      .replace(/[ \t]+\n/g, "\n").replace(/\n{3,}/g, "\n\n").trim();
  }
  // LaTeX 수식 (\( \), \[ \], $…$) 을 읽을 수 있는 글자로
  function tex2txt(t) {
    if (!/\\\(|\\\[|\$[^$\n]+\$|\\(frac|text|times|approx|cdot|left|right)/.test(t)) return t;
    const conv = (x) => { let y = x, prev;
      do { prev = y; y = y.replace(/\\[dt]?frac\s*\{([^{}]*)\}\s*\{([^{}]*)\}/g, "($1)/($2)").replace(/\\(text|mathrm|mathbf|operatorname)\s*\{([^{}]*)\}/g, "$2"); } while (y !== prev);
      return y.replace(/\\left|\\right/g, "").replace(/\\times/g, "×").replace(/\\cdot/g, "·").replace(/\\approx/g, "≈").replace(/\\(le|leq)\b/g, "≤").replace(/\\(ge|geq)\b/g, "≥")
        .replace(/\\%/g, "%").replace(/\\sigma/g, "σ").replace(/\\mu/g, "μ").replace(/\\Delta/g, "Δ").replace(/\\to\b|\\rightarrow/g, "→").replace(/\\,|\;|\\!/g, " ")
        .replace(/\^\{([^{}]*)\}/g, "^$1").replace(/_\{([^{}]*)\}/g, "_$1").replace(/[{}]/g, "").replace(/\\([a-zA-Z]+)/g, "$1").trim(); };
    return t.replace(/\$\$([\s\S]+?)\$\$/g, (_, x) => conv(x)).replace(/\\\[([\s\S]+?)\\\]/g, (_, x) => conv(x)).replace(/\\\(([\s\S]+?)\\\)/g, (_, x) => conv(x))
      .replace(/\$([^$\n]*\\[a-zA-Z][^$\n]*)\$/g, (_, x) => conv(x)).replace(/\\(frac|text)\s*\{[^\n]*/g, (x) => conv(x));
  }
  function md2html(t) {
    const inline = (x) => esc(x)
      .replace(/&lt;br\s*\/?&gt;/gi, "<br>")
      .replace(/&lt;(\/?)(b|strong)&gt;/gi, "<$1b>")
      .replace(/&lt;\/?(i|em|u|span|p|div|sup|sub|small|font)(\s[^&]*)?&gt;/gi, "")
      .replace(/`([^`]+)`/g, "<code>$1</code>")
      .replace(/\*\*(.+?)\*\*|__(.+?)__/g, (_, a, b2) => `<b>${a ?? b2}</b>`)
      .replace(/(^|[^*\w])\*(?!\s)([^*]+?)\*(?!\*)/g, "$1<i>$2</i>")
      .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>');
    const lines = String(t).replace(/\r/g, "").split("\n"), out = [], stack = [];
    const closeTo = (ind) => { while (stack.length && stack[stack.length - 1].ind > ind) out.push(`</li></${stack.pop().tag}>`); };
    const cells = (l) => l.trim().replace(/^\||\|$/g, "").split("|").map((c) => c.trim());
    for (let i = 0; i < lines.length; i++) {
      const raw = lines[i], l = raw.trim(), ind = raw.match(/^\s*/)[0].replace(/\t/g, "    ").length;
      if (/^```/.test(l)) {
        closeTo(-1); const lang = l.slice(3).trim().toLowerCase(), code = [];
        while (++i < lines.length && !/^```/.test(lines[i].trim())) code.push(lines[i]);
        // 마크다운을 코드 블록에 담아 보낸 답은 소스 대신 렌더링한다
        if (/^(markdown|md|text)?$/.test(lang) && code.some((c) => /^\s*(#{1,6}\s|[-*+•]\s|\d+[.)]\s|\|)/.test(c) || /\*\*/.test(c))) out.push(md2html(code.join("\n")));
        else if (lang === "html") out.push(md2html(html2md(code.join("\n"))));
        else out.push(`<pre>${esc(code.join("\n"))}</pre>`);
        continue;
      }
      const m = l.match(/^([-*+•]|\d+[.)])\s+(.*)$/);
      if (m && !/^(\*\s*){3,}$|^(-\s*){3,}$/.test(l)) {
        const tag = /\d/.test(m[1]) ? "ol" : "ul", top = stack[stack.length - 1];
        if (!top || ind > top.ind) { out.push(`<${tag}>`); stack.push({ tag, ind }); }
        else {
          closeTo(ind); const t2 = stack[stack.length - 1];
          if (t2 && t2.ind === ind) { if (t2.tag !== tag) { out.push(`</li></${stack.pop().tag}><${tag}>`); stack.push({ tag, ind }); } else out.push("</li>"); }
          else { out.push(`<${tag}>`); stack.push({ tag, ind }); }
        }
        out.push(`<li>${inline(m[2])}`); continue;
      }
      if (!l) { continue; }
      if (stack.length && ind > 0 && !/^[#>|]/.test(l)) { out.push("<br>" + inline(l)); continue; }
      closeTo(-1);
      if (/^(-{3,}|\*{3,}|_{3,})$/.test(l.replace(/\s/g, ""))) { out.push("<hr>"); continue; }
      // 표: 앞뒤 | 가 없는 표도 받는다 (머리줄 다음 줄이 --- 구분선)
      if (l.includes("|") && i + 1 < lines.length && /^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?$/.test(lines[i + 1].trim()) && (l.startsWith("|") || lines[i + 1].includes("|"))) {
        const hd = cells(l); i++; const body = [];
        while (i + 1 < lines.length && lines[i + 1].trim() && lines[i + 1].includes("|")) body.push(cells(lines[++i]));
        out.push(`<table><tr>${hd.map((c) => `<th>${inline(c)}</th>`).join("")}</tr>${body.map((r) => `<tr>${r.map((c) => `<td>${inline(c)}</td>`).join("")}</tr>`).join("")}</table>`);
        continue;
      }
      const h = l.match(/^#{1,6}\s+(.*)$/);
      if (h) { out.push(`<h4>${inline(h[1].replace(/#+$/, ""))}</h4>`); continue; }
      if (l.startsWith(">")) { out.push(`<blockquote>${inline(l.replace(/^>\s?/, ""))}</blockquote>`); continue; }
      out.push(`<p>${inline(l)}</p>`);
    }
    closeTo(-1);
    return out.join("");
  }
  function hashStr(s2) { let h = 0; for (let i = 0; i < s2.length; i++) h = (Math.imul(31, h) + s2.charCodeAt(i)) | 0; return String(h >>> 0); }
  function aiCache() { let c = {}; try { c = JSON.parse(localStorage.getItem(AI_KEY) || "{}"); } catch (e) { /* 무시 */ } if (c.key) c = { strategy: c }; return c; }
  // AI 를 쓸 수 없을 때 보여 줄 규칙 기반 해설 (같은 숫자로 만든 짧은 요약)
  function ruleText(kind) {
    const g = S.state.goal, L = [], B = (x) => `**${x}**`;
    try {
      if (kind === "trend") {
        const { rows } = valuation();
        S.state.holdings.map((h) => h.ticker).filter((t) => S.prices[t]).forEach((t) => { const s2 = sigOf(t), r = rows.find((x) => x.h.ticker === t); if (!s2) return;
          const sig = s2.vol_hist < 0.03 ? "현금성이라 추세 신호를 보지 않습니다." : s2.close < s2.ema200 && (s2.slope_z < 0 || s2.ema50 < s2.ema200) ? "가격이 EMA200 아래이고 추세도 약해 추가 매수는 반등 확인 뒤가 안전합니다."
            : s2.close > s2.ema200 && s2.slope_z > 1 ? "EMA200 위에서 기울기도 뚜렷해 보유 유지 쪽입니다." : s2.dev_from_kalman > 0.1 ? "칼만 추세보다 10% 넘게 높아 단기 과열 구간입니다."
            : s2.dev_from_kalman < -0.1 ? "칼만 추세보다 10% 넘게 낮아 눌림 구간입니다." : "뚜렷한 신호 없이 추세선 근처입니다.";
          L.push(`### ${t} · ${s2.trend}`, `- 비중 ${pct(r?.w, 0)}, 칼만 추세 대비 ${B(spct(s2.dev_from_kalman))}, 기울기 연 ${spct(s2.slope_ann, 0)} (z ${s2.slope_z.toFixed(1)}), 고점 대비 ${pct(s2.drawdown, 0)}`, `- ${sig}`); });
        L.push("- 추세 지표는 뒤늦게 반응하고 횡보장에서 신호가 자주 바뀝니다.");
      } else if (kind === "dash") {
        const { rows, total } = valuation(), H = history(), k = H.dates.length - 1, j3 = Math.max(0, k - 756), yrs = yearsBetween(today(), g.date);
        L.push("### 과거", `- 현재 ${B(krw(total) + "원")}, ${H.dates[j3]} ${krw(H.total[j3])}원에서 ${spct(total / H.total[j3] - 1, 0)}`, `- 목표까지 필요한 연수익률 ${B(pct((g.amount / total) ** (1 / yrs) - 1))}`);
        const top = rows.filter((r) => r.valueKrw > 0).sort((a, b2) => b2.w - a.w)[0]; if (top) L.push(`- 가장 큰 비중 ${top.h.ticker} ${pct(top.w, 0)}: 결과가 이 종목에 크게 좌우됩니다.`);
        const Fs = [["현재 정세", fcReady("base")], ["과거 추세", fcReady("smooth")], [`내 관점 (${S.state.model.scenario === "blend" ? "추세 신뢰 " + S.state.model.trust + "%" : scenName(S.state.model.scenario)})`, fcReady(S.state.model.scenario)]].filter(([, f]) => f && f.R);
        if (Fs.length) { L.push("### 세 렌즈"); Fs.forEach(([n, f]) => L.push(`- ${n}: 목표 확률 ${B(pct(f.R.p_goal, 0))}, 목표일 중앙값 ${krw(f.R.terminal.p50)}원`));
          L.push("- 현재 정세는 과거 수익률을 장기 평균 쪽으로 당긴 값, 과거 추세는 지난 3년 성장 속도가 이어진다는 가정입니다. 어느 쪽을 믿을지는 3년 전망의 슬라이더로 고릅니다."); }
      } else if (kind === "fx") {
        const F = fxInfo(); if (!F) return ""; const sg = F.sg;
        L.push("### 환율", `- 현재 ${B(nf(F.now, 1) + "원")}, 칼만 추세 대비 ${spct(sg.dev_from_kalman)}, 판정 ${sg.trend}`, `- 1년 범위 ${nf(F.lo1, 0)}~${nf(F.hi1, 0)}원, 1년 변화 ${spct(sg.ret_1y)}`,
          "### 내 노출", `- 달러 자산 ${B(pct(F.usdW, 0))}: 원화가 10% 강해지면 평가액 약 ${pct(F.usdW * 0.1, 1)} 감소`, `- ${F.usdW > 0.8 ? "달러 비중이 높아 원화 자산이나 환헤지 상품으로 일부 나누는 것을 검토할 만합니다." : "달러 비중이 과하지 않습니다."}`);
      } else if (kind === "alloc") {
        if (!lastAlloc) return ""; const o = lastAlloc.out, bp = o.reduce((a, b2) => (b2.R.p_goal > a.R.p_goal ? b2 : a)), bs = o.reduce((a, b2) => (b2.R.terminal.p5 > a.R.terminal.p5 ? b2 : a));
        L.push("### 비교", `- 목표 확률이 가장 높은 안: ${B(bp.name)} (${pct(bp.R.p_goal, 0)})`, `- 나쁜 경우(하위 5%)가 가장 나은 안: ${B(bs.name)} (${krw(bs.R.terminal.p5)}원)`,
          `- ${bp === bs ? "두 기준 모두 같은 안이 앞섭니다." : "확률과 안전성이 다른 안을 가리키니 감당할 낙폭을 먼저 정하세요."}`, "- 옮길 때는 여러 번 나눠 팔고, 해외주식 양도차익 연 250만원 공제를 해마다 쓰세요.");
      } else {
        if (!lastForecast) return ""; const { b, withEv: R, noEv, hasEv } = lastForecast, md = b.model, V0 = R.V0;
        if (kind === "events") {
          const on = S.state.events.filter((e) => e.on && evShown(e)), byCat = {};
          on.forEach((e) => (byCat[e.cat] = (byCat[e.cat] || 0) + 1));
          L.push("### 켜진 외부 요인", ...Object.entries(byCat).map(([c, n]) => `- ${catName(c)} ${n}건`));
          if (hasEv) L.push("### 효과", `- 목표 확률 ${pct(noEv.p_goal, 0)} → ${B(pct(R.p_goal, 0))}, 하위 5% ${krw(noEv.terminal.p5)} → ${krw(R.terminal.p5)}원`);
          if (xfEff && xfEff.fc === lastForecast) { const w = Object.entries(xfEff.by).sort((a, b2) => a[1].terminal.p5 - b2[1].terminal.p5)[0]; if (w) L.push(`- 나쁜 경우를 가장 크게 끌어내리는 요인: ${B(xfName(w[0]))}`); }
          const Bt = factorBetas(), hi = b.holdings.map((h) => [h.ticker, Bt.stat.mkt[h.ticker]?.beta]).filter((x) => x[1] != null).sort((a, b2) => b2[1] - a[1])[0];
          if (hi) L.push(`- 시장 충격(정치·전쟁·거시)에 가장 민감한 종목: ${B(hi[0])} (베타 ${hi[1].toFixed(2)})`);
          L.push("- 날짜는 추정이니 실적·인도량·FOMC·선거 날짜는 공시와 일정표로 확인하세요.");
        } else if (kind === "forecast") {
          L.push("### 결과", `- 목표 확률 ${B(pct(R.p_goal, 0))}, 목표일 중앙값 ${krw(R.terminal.p50)}원 (목표의 ${pct(R.terminal.p50 / g.amount, 0)})`, `- 나쁜 경우 5% ${krw(R.terminal.p5)}원, 목표일에 더 낮을 확률 ${pct(R.p_loss, 0)}`);
          const hv = b.holdings.map((h, i) => ({ t: h.ticker, v: md.factors[i].vol, w: h.valueKrw / V0 })).sort((a, b2) => b2.v * b2.w - a.v * a.w)[0];
          L.push("### 시사점", `- 위험의 대부분은 ${hv.t} (비중 ${pct(hv.w, 0)}, 변동성 ${pct(hv.v, 0)})에서 나옵니다.`, R.req50 ? `- 확률 50%에 필요한 월 적립은 약 ${krw(R.req50)}원입니다.` : "- 월 적립을 늘리거나 목표일을 늦추면 확률이 오릅니다.");
        } else if (kind === "strategy") {
          L.push("- 위 '포트폴리오 진단'과 종目별 카드가 같은 계산값으로 만든 규칙 기반 의견입니다.".replace("目", "목"), `- 목표 확률 ${B(pct(R.p_goal, 0))}, 목표일 중앙값 ${krw(R.terminal.p50)}원`);
        } else return "";
      }
    } catch (e) { return ""; }
    return L.join("\n");
  }
  // Puter (버튼을 누를 때만, 첫 사용 때 무료 계정 확인 창)
  let puterP = null;
  function loadPuter() { return (puterP ||= new Promise((res, rej) => { if (window.puter) return res(window.puter); const sc = document.createElement("script"); sc.src = "https://js.puter.com/v2/"; sc.onload = () => res(window.puter); sc.onerror = () => { puterP = null; rej(new Error("Puter를 불러오지 못함")); }; document.head.appendChild(sc); })); }
  const puterText = (r) => (typeof r === "string" ? r : r?.message?.content?.[0]?.text ?? r?.message?.content ?? r?.text ?? String(r ?? ""));
  async function askPuter(sys, q) { const P = await loadPuter(); return puterText(await P.ai.chat([{ role: "system", content: sys }, { role: "user", content: q }])); }
  const aiBusy = {};
  const AI_SYS = "너는 신중한 한국어 투자 조언가다. 주어진 숫자만 근거로 아주 간결하게 답한다. 요청 항목마다 ### 소제목 하나와 한 줄짜리 글머리표 2~3개만 쓰고, 전체 15줄을 넘기지 않는다. 서론·반복·일반론은 빼고 핵심 숫자는 **굵게**. 표, 코드 블록(```), HTML 태그, 수식(LaTeX, $ 기호)은 쓰지 않고 일반 마크다운 글로만 쓴다(좁은 휴대폰 화면). 마지막 줄은 '투자 권유 아님.'";
  // 기본은 계산값으로 만든 규칙 기반 해설. 'Puter 무료 AI로 분석'을 누를 때만 Puter 에 묻는다
  function showRule(box, kind, why) {
    const r = ruleText(kind);
    box.innerHTML = (r ? md2html(r) : "<p class='muted'>분석할 계산 결과가 아직 없습니다.</p>") ;
  }
  async function aiAuto(kind, force, viaPuter) {
    const box = $("#aiOut-" + kind); if (!box) return;
    const off = S.state.ui.ai_auto === false;
    $$(".aicard, #aiAutoCard").forEach((c) => (c.style.display = off || (c.closest("#ana-events") && !heldTks().length) ? "none" : "block"));
    if (off || aiBusy[kind]) return;
    const q = aiPromptFor(kind);
    if (!q) { box.innerHTML = "<p class='muted'>분석할 계산 결과가 아직 없습니다.</p>"; return; }
    const key = hashStr("v6|" + q), cache = aiCache(), c = cache[kind];
    // Puter 답은 입력(종목·수량·목표·사건·시나리오)이 같고 6시간 안이면 다시 보여 준다
    const sig = hashStr("v6|" + kind + JSON.stringify([S.state.holdings.map((h) => [h.ticker, h.shares]), S.state.goal, S.state.events.map((e) => [e.id, e.on, e.date, e.prob, e.mean, e.sd]), S.state.model.scenario, today()]));
    const again = `<button class="sm" data-puter="${kind}">Puter 무료 AI로 분석</button>`;
    if (!viaPuter) {
      showRule(box, kind); return;
    }
    aiBusy[kind] = true; box.innerHTML = "<p class='muted'>Puter로 분석하는 중입니다… (보통 10~30초)</p>";
    let text = "", why = "";
    try { let tm; text = cleanAi(await Promise.race([askPuter(AI_SYS, q), new Promise((_, rej) => { tm = setTimeout(() => rej(new Error("시간 초과")), 90000); })]).finally(() => clearTimeout(tm))); }
    catch (e) { why = e?.message || String(e); }
    aiBusy[kind] = false;
    if (!text) { showRule(box, kind, why || "빈 응답"); return; }
    const cc = aiCache(); cc[kind] = { key, sig, text, at: Date.now(), src: "Puter" };
    try { localStorage.setItem(AI_KEY, JSON.stringify(cc)); } catch (e) { /* 무시 */ }
    box.innerHTML = md2html(text) + `<p class="muted small">${new Date().toLocaleString()} 분석 · Puter ${again}</p>`;
  }
  // 지금 보고 있는 분석 화면의 AI 분석을 채운다
  function aiRefresh() { if ($("#tabs .on")?.dataset.tab === "analysis") aiAuto(curAna(), false); }


  // ------------------------------------------------------------ 인사이트 (뉴스)
  // data/news.json: GitHub Actions(웹) 또는 내 PC 서버가 한 시간마다 RSS·Yahoo 뉴스를 모아 AI 중계로 한글 번역·요약해 둔 파일
  let NEWS = null, newsAt = 0;
  async function loadNews(force) {
    if (!force && NEWS && Date.now() - newsAt < 10 * 60000) return NEWS;
    try {
      const r = await fetch(MODE === "local" ? "/api/news" : "../data/news.json?t=" + Date.now(), { cache: "no-store" });
      if (r.ok) { NEWS = await r.json(); newsAt = Date.now(); }
    } catch (e) { /* 없으면 아래에서 안내 */ }
    return NEWS;
  }
  const ago = (t) => { const m = (Date.now() - Date.parse(t)) / 60000; if (!isFinite(m)) return ""; return m < 60 ? `${Math.max(1, Math.round(m))}분 전` : m < 1440 ? `${Math.round(m / 60)}시간 전` : `${Math.round(m / 1440)}일 전`; };
  // 미래 가치 인사이트: 보유 종목마다 상자 하나. news.json 의 insight.items(보유 종목·관련 업계 기사, 4개 분류)를
  // 종목별로 모으고, 서버가 만든 종합(insight.digest)을 위에, 분류 알약을 아래에, 출처 기사는 접어 둔다.
  // 보유 종목에서 빼면 상자도 바로 빠진다(서버 기사·종합은 7일 보관 뒤 지움).
  const INS_KEY = "naeilo-insight";
  const INS_CAT = { growth: "성장·혁신", market: "시장·산업", fund: "펀더멘탈·리스크", esg: "ESG·무형자산" };
  const insLoad = () => { try { const o = JSON.parse(localStorage.getItem(INS_KEY) || "{}"); return { read: o.read || {} }; } catch (e) { return { read: {} }; } };
  const insSave = (o) => { try { const lim = Date.now() - 30 * 864e5; for (const k in o.read) if (o.read[k] < lim) delete o.read[k]; localStorage.setItem(INS_KEY, JSON.stringify(o)); } catch (e) { /* 무시 */ } };
  // 서버(매시간 수집)에 아직 없는 보유 종목은 이 브라우저가 직접 Yahoo 기사를 받아 AI 중계로 분류·번역한다 (3시간 보관)
  const INS_X = "naeilo-insight-extra2", INS_SKIP = new Set(["QQQ", "SPY", "SGOV", "BIL", "SHV", "TLT", "DBC", "^TNX", "CL=F", "GC=F"]);
  let insBusy = false;
  const insExtra = () => { try { return JSON.parse(localStorage.getItem(INS_X) || "{}"); } catch (e) { return {}; } };
  async function yahooNews(t) {
    const url = "https://query1.finance.yahoo.com/v1/finance/search?" + new URLSearchParams({ q: t, newsCount: 12, quotesCount: 0 });
    for (const p of proxies()) {
      try {
        const ctl = new AbortController(), tm = setTimeout(() => ctl.abort(), 15000);
        const r = await fetch(p + encodeURIComponent(url), { signal: ctl.signal, cache: "no-store" }); clearTimeout(tm);
        const j = await r.json().catch(() => null);
        if (j && Array.isArray(j.news)) return j.news.filter((x) => x.title && x.link).map((x) => ({ title: x.title, link: x.link, source: x.publisher || "", time: new Date((x.providerPublishTime || 0) * 1000).toISOString() }));
      } catch (e) { /* 다음 중계 */ }
    }
    return null; // 모든 중계가 실패
  }
  async function insFetchMissing(tks) {
    const x = insExtra(), cands = [];
    const st = {}; // 종목별 결과: fail(중계 연결 실패) / none(최근 기사 없음)
    for (const t of tks) {
      let news = await yahooNews(t);
      const nm = (S.prices[t]?.name || "").replace(/,?\s*(Inc\.?|Corp\.?|Corporation|Ltd\.?|plc|Holdings?)$/i, "").trim();
      if (news && !news.length && nm && nm.toUpperCase() !== t.toUpperCase()) news = await yahooNews(nm); // 티커로 안 나오면 회사 이름으로
      if (!news) { st[t] = "fail"; continue; }
      const age = (a) => Date.now() - Date.parse(a.time);
      let got = news.filter((a) => age(a) < 7 * 864e5); if (!got.length) got = news.filter((a) => age(a) < 30 * 864e5); // 기사가 적은 종목은 한 달까지
      if (!got.length) st[t] = "none";
      for (const a of got) cands.push({ t, a });
    }
    let picked = null;
    if (cands.length && S.config?.ai) {
      const prompt = "아래는 보유 종목의 최근 기사 후보다. 1~3년 뒤 기업 가치 판단에 도움이 되는 기사만 골라 4개 분류 중 하나로 나눠라. 단기 주가 등락·광고성 기사는 빼라. 최대 20개.\n"
        + "- growth: 성장 동력 및 기술 혁신\n- market: 시장 및 산업 트렌드\n- fund: 펀더멘탈 및 리스크 관리\n- esg: 무형 자산 및 지속 가능성\n"
        + '출력 형식: {"items":[{"i":번호,"cat":"growth|market|fund|esg","ko":"한국어 제목","sum":"핵심 요약 한 문장"}]} JSON만.\n'
        + cands.map(({ t, a }, i) => `${i}. [${t}] [${a.source}] ${a.title}`).join("\n");
      try {
        const r = await fetch(S.config.ai, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ system: "너는 미국 증시 뉴스를 한국 개인 투자자에게 전하는 편집자다. 반드시 JSON 하나만 출력한다.", prompt }) });
        const m = ((await r.json().catch(() => ({}))).text || "").replace(/```(?:json)?/g, "").match(/\{[\s\S]*\}/);
        const j = m ? JSON.parse(m[0]) : null; picked = Array.isArray(j?.items) ? j.items : Array.isArray(j) ? j : null;
      } catch (e) { picked = null; }
    }
    const key = (s) => s.toLowerCase().replace(/[^a-z0-9가-힣]/g, "").slice(0, 60);
    const mk = ({ t, a }, cat, ko, sum) => ({ id: key(a.title), ticker: t, scope: "held", cat, title: ko || a.title, orig: a.title, summary: sum || "", source: a.source, link: a.link, time: a.time });
    // AI 가 고른 기사 (분류가 이상하면 성장·혁신으로). 하나도 못 고르면 원문 제목 그대로 보여 준다
    let got = (picked || []).filter((p) => cands[+p.i]).map((p) => mk(cands[+p.i], INS_CAT[p.cat] ? p.cat : "growth", p.ko, p.sum));
    for (const t of tks) if (!got.some((g) => g.ticker === t)) got = got.concat(cands.filter((c) => c.t === t).slice(0, 6).map((c) => mk(c, "growth")));
    for (const k in x) if (Date.now() - (x[k].at || 0) > 7 * 864e5) delete x[k]; // 보유에서 뺀 종목 기사는 7일 뒤 지움
    for (const t of tks) x[t] = { at: Date.now(), ai: !!picked?.length, fail: st[t] === "fail", none: st[t] === "none", items: got.filter((g) => g.ticker === t) };
    try { localStorage.setItem(INS_X, JSON.stringify(x)); } catch (e) { /* 무시 */ }
  }
  // 서버 종합이 없을 때(브라우저가 직접 받은 종목 등): 기사 요약을 최신순으로 이어 붙인다
  const insRule = (its) => { const xs = its.filter((x) => x.scope === "held").concat(its.filter((x) => x.scope !== "held")); const sm = xs.map((x) => x.summary).filter(Boolean); return (sm.length ? sm : xs.map((x) => x.title)).slice(0, 3).join("\n"); };
  // 종합 글: 문장마다 줄 바꿈, **굵게** 표시. 서버 종합에 굵은 표시가 없으면 숫자(금액·%)를 굵게
  const insFmt = (t) => {
    let h = esc(t).replace(/\*\*(.+?)\*\*/g, "<b>$1</b>");
    if (!/<b>/.test(h)) h = h.replace(/([$₩]?[+-]?\d[\d,.]*\s?(?:%p?|억|조|만|달러|원|배|대|명)?)/g, (m) => (/\d{2,}|%|\$|억|조|달러/.test(m) ? `<b>${m}</b>` : m));
    return h.split(/\n+|(?<=[.!?다])\s+(?=\S)/).map((x) => x.trim()).filter(Boolean).map((x) => (/^위험\s*[:：]/.test(x) ? `<span class="nrisk">${x}</span>` : x)).join("<br>");
  };
  // 서버 요약에 위험 문장이 없을 때(예전 요약·규칙 요약) 숫자로 만든 위험 한 줄
  const insRisk = (t) => {
    const r = valuation().rows.find((x) => x.h.ticker === t), p = S.prices[t], ind = p ? Model.indicators(p.dates, p.adj) : null, sg = ind?.sig;
    const parts = [];
    if (r && r.w > 0.4) parts.push(`비중 ${pct(r.w, 0)}로 이 종목 결과에 자산이 크게 좌우됩니다`);
    if (sg && sg.vol_ewma) parts.push(`변동성 연 ${pct(sg.vol_ewma, 0)}`);
    if (sg && sg.drawdown < -0.15) parts.push(`고점 대비 ${pct(sg.drawdown, 0)}`);
    if (p && p.dates.length < 252) parts.push("상장 1년 미만이라 가격 이력이 짧습니다");
    return parts.length ? parts.join(", ") + "." : "기사에 드러나지 않은 실적·경쟁 위험도 함께 보세요.";
  };
  const insPx = (t) => { const r = valuation().rows.find((x) => x.h.ticker === t); if (!r || r.p.v == null) return "";
    const sym = r.ccy === "USD" ? "$" : r.ccy === "KRW" ? "₩" : "", c = r.dayChg;
    return `<span class="ipx">${sym}${nf(r.p.v, r.ccy === "KRW" ? 0 : 2)}${c != null ? ` <span class="${c > 0 ? "up" : c < 0 ? "dn" : ""}">${spct(c, 1)}</span>` : ""}</span>`; };
  function insBox(t, its, dig, msg) {
    const o = insLoad(), name = S.prices[t]?.name || S.quotes[t]?.name || "";
    its = [...its].sort((a, b) => (a.scope !== "held") - (b.scope !== "held") || (b.time || "").localeCompare(a.time || ""));
    const at = dig?.updated || its[0]?.time, cnt = {};
    its.forEach((x) => (cnt[x.cat] = (cnt[x.cat] || 0) + 1));
    let text = dig?.sum || insRule(its);
    if (!/위험\s*[:：]/.test(text)) text += "\n위험: " + insRisk(t);
    const pills = Object.keys(INS_CAT).filter((c) => cnt[c]).map((c) => `<span class="ncat">${INS_CAT[c]} ${cnt[c]}</span>`).join("");
    const src = its.map((x) => `<li><a class="${o.read[x.id] ? "read" : ""}" href="${esc(x.link)}" target="_blank" rel="noopener noreferrer" data-nid="${esc(x.id)}">${esc(x.title || x.orig)}</a> <span class="nmeta">${x.scope === "held" ? "" : "업계 · "}${esc(x.source || "")}${x.time ? " · " + ago(x.time) : ""}</span></li>`).join("");
    return `<div class="nitem ibox"><div class="ntop">${logo(t, name)}<span class="tk" title="${esc(name)}">${esc(t)}</span>${insPx(t)}<span class="nmeta nago">${at && its.length ? ago(at) : ""}</span></div>
      ${its.length ? `<p class="nsum">${insFmt(text)}</p><div class="pills">${pills}</div><details class="nsrc"><summary>출처 ${its.length}건</summary><ul>${src}</ul></details>` : `<p class="nsum muted">${esc(msg)}</p>`}</div>`;
  }
  async function renderInsight(force) {
    renderBeyora(); bvLoad();
    const N = await loadNews(!!force);
    const held = S.state.holdings.filter((h) => Number(h.shares) > 0).map((h) => h.ticker).filter((t) => !PURGED.has(t));
    const tks = held.filter((t) => !INS_SKIP.has(t.toUpperCase()) && !t.includes("="));
    // 서버 기사 + 이 브라우저가 받은 기사. 지금 보유한 종목 것만 쓴다
    const srv = (N?.insight?.items || []).filter((x) => tks.includes(x.ticker)), X = insExtra(), dig = N?.insight?.digest || {};
    const missing = tks.filter((t) => !srv.some((x) => x.ticker === t && x.scope === "held"));
    const stale = missing.filter((t) => !X[t] || Date.now() - X[t].at > (X[t].fail ? 5 / 60 : X[t].none ? 6 : X[t].ai ? 3 : 0.5) * 3600e3);
    if (stale.length && !insBusy) { insBusy = true; insFetchMissing(stale).finally(() => { insBusy = false; if ($("#tabs .on")?.dataset.tab === "insight") renderInsight(); }); }
    $("#newsFuture").innerHTML = tks.map((t) => {
      const its = missing.includes(t) ? X[t]?.items || [] : srv.filter((x) => x.ticker === t);
      const msg = insBusy && stale.includes(t) ? "기사를 모으는 중입니다…" : X[t]?.fail ? "기사를 받지 못했습니다(기사 중계 연결 실패). 잠시 뒤 다시 열어 주세요." : "최근 한 달 사이 관련 기사가 없습니다.";
      return insBox(t, its, missing.includes(t) ? null : dig[t], msg);
    }).join("") || `<div class="nitem empty">보유 종목을 입력하면 종목마다 인사이트 상자가 생깁니다.</div>`;
    $("#newsMsg").style.display = "none";
    const fu = N?.future_meta?.updated || N?.updated;
    $("#newsNote").textContent = `${fu ? dtStr(fu) + " 수집 · " : ""}한 시간마다 자동 갱신.`;
  }

  // ------------------------------------------------------------ 미래 설계 Beyora (블로그)
  // 글은 저장소의 data/beyora.json 에 둔다. 누구나 읽고, 개발자 토큰이 있는 브라우저(또는 내 PC 프로그램)만 쓴다.
  // 이 브라우저의 "beyora-blog" 에는 아직 저장소에 못 올린 글만 남고, 올리고 나면 비운다.
  const BV_KEY = "beyora-blog", BV_VIEWS = "beyora-views", BV_SORT = "beyora-sort";
  const BV_BASE = [["past", "과거"], ["now", "현재"], ["future", "미래"]];
  const lsGet = (k, d) => { try { return JSON.parse(localStorage.getItem(k) || "null") ?? d; } catch (e) { return d; } };
  const lsSet = (k, v) => { try { if (v == null) localStorage.removeItem(k); else localStorage.setItem(k, JSON.stringify(v)); } catch (e) { toast("브라우저 저장 실패: " + e.message); } };
  const bvNorm = (x) => ({ cats: Array.isArray(x?.cats) ? x.cats.filter((c) => c && c.id) : [], posts: Array.isArray(x?.posts) ? x.posts.filter((p) => p && p.id) : [] });
  // 같은 글은 더 최근에 고친 쪽 (고정·해제도 고친 것으로 본다: pinAt)
  const bvStamp = (p) => ((p.pinAt || "") > (p.updated || "") ? p.pinAt : p.updated || "");
  function bvMergeInto(D, x) {
    x = bvNorm(x);
    x.cats.forEach((c) => { if (!D.cats.some((y) => y.id === c.id)) D.cats.push({ ...c }); });
    x.posts.forEach((p) => { const i = D.posts.findIndex((y) => y.id === p.id); if (i < 0) D.posts.push({ ...p }); else if (bvStamp(p) > bvStamp(D.posts[i])) D.posts[i] = { ...p, views: Math.max(p.views || 0, D.posts[i].views || 0) }; });
    return D;
  }
  let BVR = null, bvLoading = null, bvBusy = false;
  // 모두의 조회수: data/config.json 의 views 중계(Cloudflare KV)가 있으면 거기서 센다. 안 되면 이 브라우저 몫만 더한다
  let BVW = null, BVL = {}, BVC = {};
  const bvApi = () => (S.config?.views ? S.config.views.replace(/\/views\/?$/, "") : "");
  async function bvViewsLoad() {
    const url = S.config?.views, ids = bv().posts.map((p) => p.id); if (!url || !ids.length) return;
    try {
      const got = {};
      for (let i = 0; i < ids.length; i += 50) {
        const r = await fetch(url + "?ids=" + encodeURIComponent(ids.slice(i, i + 50).join(",")), { cache: "no-store" }), j = await r.json();
        if (!r.ok || !j.views) return; Object.assign(got, j.views); Object.assign(BVL, j.likes || {}); Object.assign(BVC, j.comments || {});
      }
      BVW = { ...(BVW || {}), ...got }; renderBeyora();
    } catch (e) { /* 중계가 없으면 이 브라우저 조회수로 */ }
  }
  function bvHit(id) {
    const local = () => { const pv = lsGet(BV_VIEWS, {}); pv[id] = (pv[id] || 0) + 1; lsSet(BV_VIEWS, pv); renderBeyora(); };
    if (!S.config?.views) return local();
    fetch(S.config.views, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ id }) })
      .then((r) => (r.ok ? r.json() : Promise.reject()))
      .then((j) => { if (!(j.n > 0)) throw 0; BVW = { ...(BVW || {}), [id]: j.n }; renderBeyora(); }).catch(local);
  }
  const bvLocal = () => bvNorm(lsGet(BV_KEY, null));
  const bvRemoteOk = () => MODE === "local" || !!(GH && ghToken()); // 저장소(또는 내 PC 파일)에 쓸 수 있나
  const bvCanWrite = () => bvRemoteOk() || (MODE === "static" && !GH); // GitHub Pages 가 아닌 곳에서는 예전처럼 이 브라우저에 쓴다
  // 화면에 보이는 글 = 저장소 글 + 이 브라우저에만 있는 글, 조회수는 이 브라우저에서 아직 못 올린 만큼 더한다
  function bv() {
    const D = bvMergeInto(bvNorm(null), BVR), pv = lsGet(BV_VIEWS, {}), loc = new Set(bvLocal().posts.map((p) => p.id));
    bvMergeInto(D, bvLocal());
    D.posts.forEach((p) => { p.views = (p.views || 0) + (BVW?.[p.id] || 0) + (pv[p.id] || 0); p._local = loc.has(p.id); });
    return D;
  }
  const bvS = { cat: "all", view: "list", id: null, sort: lsGet(BV_SORT, "new"), msg: "" };
  const bvCats = () => [...BV_BASE, ...bv().cats.map((c) => [c.id, c.name])];
  const bvCatName = (k) => bvCats().find(([c]) => c === k)?.[1] || "분류 없음";
  // 고정한 글은 맨 앞 (나중에 고정한 글이 위), 나머지는 고른 순서. 이전·다음 글은 고정과 상관없는 순서로
  const bvSorted = (D, pinFirst = true) => D.posts.filter((p) => bvS.cat === "all" || p.cat === bvS.cat)
    .sort((a, b) => (pinFirst ? (b.pinned || "").localeCompare(a.pinned || "") : 0) || (bvS.sort === "views" ? (b.views || 0) - (a.views || 0) : 0) || (a.created < b.created ? 1 : a.created > b.created ? -1 : 0));
  const bvTime = (t) => { if (!t) return ""; const d = new Date(t), z = (n) => String(n).padStart(2, "0"); return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()} ${z(d.getHours())}:${z(d.getMinutes())}`; };
  // 공감 (이 브라우저에서 누른 글은 다시 누르면 취소)
  const BV_LIKED = "beyora-liked";
  function bvLike(id) {
    if (!bvApi()) return;
    const liked = lsGet(BV_LIKED, []), on = !liked.includes(id);
    lsSet(BV_LIKED, on ? [...liked, id] : liked.filter((x) => x !== id)); BVL[id] = Math.max(0, (BVL[id] || 0) + (on ? 1 : -1)); renderBeyora();
    fetch(bvApi() + "/like", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ id, d: on ? 1 : -1 }) })
      .then((r) => (r.ok ? r.json() : Promise.reject())).then((j) => { BVL[id] = j.n; renderBeyora(); })
      .catch(() => { lsSet(BV_LIKED, liked); BVL[id] = Math.max(0, (BVL[id] || 0) + (on ? -1 : 1)); renderBeyora(); toast("공감을 저장하지 못했습니다"); });
  }
  // 댓글: 익명, 비밀번호로 지움. 개발자(GitHub 토큰)는 비밀번호 없이 지움
  const bvCm = { id: null, list: null, err: "", busy: false };
  async function bvCmLoad(id) {
    if (!bvApi()) return;
    bvCm.id = id; bvCm.list = null; bvCm.err = "";
    try { const r = await fetch(bvApi() + "/comments?id=" + encodeURIComponent(id), { cache: "no-store" }), j = await r.json(); if (!r.ok || !j.comments) throw 0; if (bvCm.id === id) { bvCm.list = j.comments; BVC[id] = j.comments.length; } }
    catch (e) { if (bvCm.id === id) bvCm.err = "댓글을 불러오지 못했습니다."; }
    if (bvS.view === "post" && bvS.id === id) renderBeyora();
  }
  async function bvCmPost(id, fd) {
    const r = await fetch(bvApi() + fd.path, { method: "POST", headers: { "Content-Type": "application/json", ...(fd.tok ? { Authorization: "Bearer " + fd.tok } : {}) }, body: JSON.stringify({ id, ...fd.body }) });
    const j = await r.json().catch(() => ({})); if (!r.ok) throw new Error(j.error || "응답 " + r.status); return j;
  }
  function bvCmHtml(id) {
    if (!bvApi()) return "";
    const L = bvCm.id === id ? bvCm.list : null, dev = !!ghToken() && (!!GH || MODE === "local");
    const items = L ? L.map((c) => `<div class="bvcm"><div class="row between"><span class="muted small">익명 · ${bvTime(c.at)}</span><button class="sm link" data-bvcmdel="${esc(c.cid)}">삭제</button></div><div class="t">${esc(c.text)}</div></div>`).join("") : "";
    return `<div class="bvcms"><h3>댓글${L && L.length ? ` ${L.length}` : ""}</h3>${bvCm.err ? `<p class="muted small">${esc(bvCm.err)}</p>` : !L ? '<p class="muted small">불러오는 중…</p>' : items}
      <div class="bvcmform"><textarea id="bvCmText" maxlength="1000" placeholder="익명으로 댓글 남기기"></textarea>
      <div class="row"><input id="bvCmPw" type="password" placeholder="비밀번호 (삭제용)" autocomplete="new-password" maxlength="64"><button class="primary sm" data-bv="cmadd"${bvCm.busy ? " disabled" : ""}>등록</button></div></div>${dev ? '<p class="muted small">개발자: 비밀번호 없이 삭제 가능</p>' : ""}</div>`;
  }
  const BV_IMG = /\.(jpe?g|png|gif|webp|avif|svg|bmp)(\?\S*)?$/i;
  const bvHost = (u) => { try { return new URL(u).hostname.replace(/^www\./, ""); } catch (e) { return u; } };
  const bvThumb = (t) => { const m = String(t || "").match(/!\[[^\]]*\]\((https?:\/\/[^)\s]+)\)|^\s*(https?:\/\/\S+\.(?:jpe?g|png|gif|webp|avif|svg|bmp)(?:\?\S*)?)\s*$/im); return m ? m[1] || m[2] : ""; };
  const bvExcerpt = (t) => String(t || "").replace(/!\[[^\]]*\]\([^)]*\)/g, " ").replace(/\[([^\]]*)\]\([^)]*\)/g, "$1").replace(/https?:\/\/\S+/g, " ")
    .replace(/[#>*`_|-]+/g, " ").replace(/\s+/g, " ").trim().slice(0, 120);
  // 본문: 이미지 주소(또는 ![설명](주소))는 그림으로, 다른 주소와 [글자](주소)는 링크 버튼으로
  function bvHtml(body) {
    const src = String(body || "").replace(/\r/g, "").replace(/!\[([^\]]*)\]\((https?:\/\/[^)\s]+)\)/g, "\n![$1]($2)\n");
    const out = [], buf = [], flush = () => { if (buf.length) { out.push(md2html(buf.join("\n"))); buf.length = 0; } };
    for (const line of src.split("\n")) {
      const l = line.trim(), m = l.match(/^!\[([^\]]*)\]\((https?:\/\/[^)\s]+)\)$/) || (/^https?:\/\/\S+$/.test(l) && BV_IMG.test(l) ? [l, "", l] : null);
      if (m) { flush(); out.push(`<figure class="bvimg"><img src="${esc(m[2])}" alt="${esc(m[1])}" loading="lazy" referrerpolicy="no-referrer">${m[1] ? `<figcaption>${esc(m[1])}</figcaption>` : ""}</figure>`); continue; }
      buf.push(line.replace(/(^|[\s(])(https?:\/\/[^\s<>()]+)/g, (all, pre, u) => (pre === "(" ? all : `${pre}[${bvHost(u)}](${u})`)));
    }
    flush();
    return out.join("").replace(/<a href=/g, '<a class="bvlink" href=');
  }
  // 저장소에서 읽기: 토큰이 있으면 GitHub API(바로 최신), 없으면 Pages 의 ../data/beyora.json
  async function bvFetch(needSha) {
    if (MODE === "local") return { data: await api("/api/beyora") };
    if (GH && ghToken()) {
      try {
        const j = await ghApi("contents/data/beyora.json?ref=main", { need: "Contents" });
        if (j.encoding !== "base64") return { data: await ghApi("contents/data/beyora.json?ref=main", { raw: true, need: "Contents" }), sha: j.sha };
        const bin = atob(String(j.content || "").replace(/\s/g, ""));
        return { data: JSON.parse(new TextDecoder().decode(Uint8Array.from(bin, (c) => c.charCodeAt(0))) || "{}"), sha: j.sha };
      } catch (e) { if (e.status === 404) return { data: {}, sha: null }; if (needSha) throw e; }
    }
    if (needSha || !GH && MODE === "static") return { data: {} };
    const r = await fetch("../data/beyora.json?t=" + Date.now(), { cache: "no-store" });
    return { data: r.ok ? await r.json() : {} };
  }
  async function bvPut(D, sha, msg) {
    if (MODE === "local") return api("/api/beyora", D);
    const bytes = new TextEncoder().encode(JSON.stringify(D, null, 1) + "\n"); let bin = "";
    bytes.forEach((b) => { bin += String.fromCharCode(b); });
    return ghApi("contents/data/beyora.json", { method: "PUT", need: "Contents", body: JSON.stringify({ message: msg, content: btoa(bin), branch: "main", ...(sha ? { sha } : {}) }) });
  }
  function bvLoad(force) {
    if (bvLoading && !force) return bvLoading;
    bvLoading = bvFetch(false).then(({ data }) => { BVR = bvNorm(data); }).catch((e) => { bvS.msg = "글을 불러오지 못했습니다: " + e.message; })
      .then(() => { renderBeyora(); bvViewsLoad(); const L = bvLocal(); if (bvRemoteOk() && (L.posts.length || L.cats.length)) bvSync(); });
    return bvLoading;
  }
  // 이 브라우저에 쌓인 글·카테고리·조회수를 저장소에 올린다 (op: 지울 글 del, 지울 카테고리 delcat)
  async function bvSync(op = {}) {
    if (!bvRemoteOk() || bvBusy) { if (bvBusy) setTimeout(() => bvSync(op), 1500); return false; }
    bvBusy = true; bvS.msg = MODE === "local" ? "저장하는 중…" : "저장소에 저장하는 중…"; renderBeyora();
    try {
      for (let n = 0; n < 3; n++) {
        const { data, sha } = await bvFetch(true), D = bvMergeInto(bvNorm(data), bvLocal()), pv = lsGet(BV_VIEWS, {});
        D.posts.forEach((p) => { if (pv[p.id]) p.views = (p.views || 0) + pv[p.id]; delete p._local; });
        if (op.del) D.posts = D.posts.filter((p) => p.id !== op.del);
        if (op.delcat) D.cats = D.cats.filter((c) => c.id !== op.delcat);
        const what = op.msg || (op.del ? "글 삭제" : op.delcat ? "카테고리 삭제" : op.title ? `글 저장: ${op.title}` : "글 저장");
        try { await bvPut(D, sha, "naeilo " + what); }
        catch (e) { if ((e.status === 409 || e.status === 422) && n < 2) continue; throw e; }
        BVR = D; lsSet(BV_KEY, null); lsSet(BV_VIEWS, null);
        bvS.msg = (MODE === "local" ? "저장됨 " : "저장소에 저장됨 ") + new Date().toLocaleTimeString() + (MODE === "local" ? "" : " · 다른 사람 화면에는 1~2분 뒤 보입니다");
        return true;
      }
    } catch (e) { bvS.msg = "저장소에 저장하지 못했습니다 (" + e.message + "). 글은 이 브라우저에 남아 있고 다음에 다시 올립니다."; toast("저장소 저장 실패"); }
    finally { bvBusy = false; renderBeyora(); }
    return false;
  }
  function renderBeyora() {
    const host = $("#bvBody"); if (!host) return;
    const D = bv(), can = bvCanWrite(), stat = bvS.msg ? `<p class="muted small bvstat">${esc(bvS.msg)}</p>` : "";
    const keep = [$("#bvCmText")?.value || "", $("#bvCmPw")?.value || ""]; // 다시 그려도 쓰던 댓글은 남긴다
    $("#bvNew").style.display = bvS.view === "list" && can ? "" : "none";
    if (bvS.view === "edit" && can) {
      const p = bvS.id ? D.posts.find((x) => x.id === bvS.id) : null;
      const cur = p?.cat || (bvS.cat !== "all" ? bvS.cat : "future");
      host.innerHTML = `<div class="bvedit">
        <select id="bvCat">${bvCats().map(([k, n]) => `<option value="${esc(k)}" ${k === cur ? "selected" : ""}>${esc(n)}</option>`).join("")}</select>
        <input id="bvTitle" class="t" placeholder="제목" value="${esc(p?.title || "")}">
        <div class="row wrap bvtools"><button type="button" class="sm" data-bv="img">🖼 이미지 링크</button><button type="button" class="sm" data-bv="link">🔗 링크</button><span class="muted small">이미지 주소는 글과 목록에 그림으로, 다른 주소는 링크 버튼으로 보입니다.</span></div>
        <textarea id="bvText" placeholder="내용을 적어 주세요. 빈 줄로 문단을 나누고, # 소제목, - 목록, **굵게** 를 쓸 수 있습니다.">${esc(p?.body || "")}</textarea>
        <div class="row"><button class="primary" data-bv="save">${p ? "수정 저장" : "글 올리기"}</button><button data-bv="cancel">취소</button></div></div>`;
      $("#bvTitle").focus(); return;
    }
    if (bvS.view === "post") {
      const p = D.posts.find((x) => x.id === bvS.id);
      if (p) {
        const list = bvSorted(D, false), i = list.findIndex((x) => x.id === p.id), prev = i >= 0 ? list[i + 1] : null, next = i > 0 ? list[i - 1] : null;
        const nav = (q, cls, lab) => `<button class="${cls}" data-bvgo="${q ? esc(q.id) : ""}" ${q ? "" : "disabled"}><span>${lab}</span><b>${q ? esc(q.title || "(제목 없음)") : "없음"}</b></button>`;
        host.innerHTML = `<div class="bvart">
          <div class="row between wrap"><button class="sm" data-bv="list">← 목록</button>${can ? `<div class="row"><button class="sm" data-bv="pin">${p.pinned ? "고정 해제" : "📌 고정"}</button><button class="sm" data-bv="edit">수정</button><button class="sm danger" data-bv="del">삭제</button></div>` : ""}</div>
          <div style="margin-top:12px">${p.pinned ? '<span class="bvpin">📌 고정</span> ' : ""}<span class="bvcat ${esc(p.cat)}">${esc(bvCatName(p.cat))}</span>${p._local ? ' <span class="bvcat">이 브라우저에만</span>' : ""}</div>
          <h1>${esc(p.title || "(제목 없음)")}</h1>
          <div class="body md">${bvHtml(p.body)}</div>
          <div class="bvmeta"><span>작성 ${bvTime(p.created)}${p.updated && p.updated !== p.created ? ` · 수정 ${bvTime(p.updated)}` : ""}</span><span class="row">조회수 ${nf(p.views || 0)}${BVC[p.id] ? ` · 댓글 ${nf(BVC[p.id])}` : ""}${bvApi() ? `<button class="sm bvlike${lsGet(BV_LIKED, []).includes(p.id) ? " on" : ""}" data-bv="like">♥ 공감 ${nf(BVL[p.id] || 0)}</button>` : ""}</span></div>
          ${bvCmHtml(p.id)}
          <div class="bvnav">${nav(bvS.sort === "new" ? prev : next, "pv", bvS.sort === "new" ? "← 이전 글" : "← 앞 글")}${nav(bvS.sort === "new" ? next : prev, "nx", bvS.sort === "new" ? "다음 글 →" : "뒤 글 →")}</div></div>` + stat;
        if ($("#bvCmText")) { $("#bvCmText").value = keep[0]; $("#bvCmPw").value = keep[1]; }
        return;
      }
      bvS.view = "list";
    }
    const list = bvSorted(D), custom = D.cats.find((c) => c.id === bvS.cat);
    const cnt = (k) => (k === "all" ? D.posts.length : D.posts.filter((p) => p.cat === k).length);
    const localN = D.posts.filter((p) => p._local).length;
    host.innerHTML = `<div class="row seg wrap bvcats">${[["all", "전체"], ...bvCats()].map(([k, n]) => `<button data-bvcat="${esc(k)}" class="${k === bvS.cat ? "on" : ""}">${esc(n)} <span class="n">${cnt(k)}</span></button>`).join("")}${can ? '<button class="add" data-bv="addcat">+ 카테고리</button>' : ""}</div>`
      + `<div class="row between wrap bvbar"><span class="muted small">${list.length}개의 글</span><div class="row seg" id="bvSort"><button data-bvsort="new" class="${bvS.sort === "new" ? "on" : ""}">최신순</button><button data-bvsort="views" class="${bvS.sort === "views" ? "on" : ""}">조회순</button></div></div>`
      + (custom && can ? `<p class="small" style="margin:0 0 8px"><button class="sm danger" data-bv="delcat">'${esc(custom.name)}' 카테고리 지우기</button> <span class="muted">글은 지워지지 않고 '전체'에 남습니다.</span></p>` : "")
      + (list.length ? `<div class="bvlist">${list.map((p) => { const th = bvThumb(p.body); return `<button type="button" class="bvpost${th ? " hasimg" : ""}${p.pinned ? " pinned" : ""}" data-bvgo="${esc(p.id)}">
          ${th ? `<img class="th" src="${esc(th)}" alt="" loading="lazy" referrerpolicy="no-referrer">` : ""}<span class="bvtags">${p.pinned ? '<span class="bvpin">📌 고정</span>' : ""}<span class="bvcat ${esc(p.cat)}">${esc(bvCatName(p.cat))}</span></span><span class="ttl">${esc(p.title || "(제목 없음)")}</span>
          <span class="ex">${esc(bvExcerpt(p.body))}</span><span class="ft">${bvTime(p.created)} · 조회 ${nf(p.views || 0)}${BVC[p.id] ? ` · 댓글 ${nf(BVC[p.id])}` : ""}${BVL[p.id] ? ` · 공감 ${nf(BVL[p.id])}` : ""}${p._local ? " · 이 브라우저에만" : ""}</span></button>`; }).join("")}</div>`
        : `<p class="muted small">${BVR || !GH ? "아직 글이 없습니다." : "글을 불러오는 중…"}${can && (BVR || !GH) ? " '글쓰기'로 지난 투자를 돌아보고(과거), 지금의 생각을 적고(현재), 앞으로의 계획을 세워(미래) 보세요." : ""}</p>`)
      + (localN && !bvRemoteOk() && GH ? `<p class="muted small">이 브라우저에만 있는 글 ${localN}개는 설정 > 개발자용에 GitHub 토큰을 넣으면 저장소로 올라갑니다.</p>` : "") + stat;
  }
  function bvOpen(id) {
    if (!bv().posts.some((x) => x.id === id)) return;
    // 같은 사람이 한 번 열어 둔 동안 여러 번 봐도 한 번만 센다
    let seen = []; try { seen = JSON.parse(sessionStorage.getItem("beyora-seen") || "[]"); } catch (e) { /* 무시 */ }
    if (!seen.includes(id)) { try { sessionStorage.setItem("beyora-seen", JSON.stringify([...seen, id])); } catch (e) { /* 무시 */ } bvHit(id); }
    bvS.view = "post"; bvS.id = id; bvS.msg = ""; bvCm.id = null; renderBeyora(); bvCmLoad(id);
    $("#beyora").scrollIntoView({ block: "start", behavior: "smooth" });
  }
  // 이 브라우저의 글 묶음을 고친다 (저장소에 올리기 전 임시 보관)
  function bvLocalEdit(fn) { const L = bvLocal(); fn(L); lsSet(BV_KEY, L.posts.length || L.cats.length ? L : null); }
  function bvInsert(txt) {
    const ta = $("#bvText"), s = ta.selectionStart ?? ta.value.length, e = ta.selectionEnd ?? s;
    ta.value = ta.value.slice(0, s) + txt + ta.value.slice(e); ta.focus(); ta.selectionStart = ta.selectionEnd = s + txt.length;
  }
  const bvAskUrl = (q) => { const u = (prompt(q) || "").trim(); if (u && !/^https?:\/\/\S+$/i.test(u)) { toast("https:// 로 시작하는 주소를 넣어 주세요"); return ""; } return u; };
  function onBeyora(e) {
    const go = e.target.closest("[data-bvgo]"); if (go) { if (go.dataset.bvgo) bvOpen(go.dataset.bvgo); return; }
    const c = e.target.closest("[data-bvcat]"); if (c) { bvS.cat = c.dataset.bvcat; renderBeyora(); return; }
    const so = e.target.closest("[data-bvsort]"); if (so) { bvS.sort = so.dataset.bvsort; lsSet(BV_SORT, bvS.sort); renderBeyora(); return; }
    const cd = e.target.closest("[data-bvcmdel]"); if (cd) { bvCmDel(bvS.id, cd.dataset.bvcmdel); return; }
    const b = e.target.closest("[data-bv]"); if (!b) return;
    const a = b.dataset.bv;
    if (a === "list") { bvS.view = "list"; renderBeyora(); }
    else if (a === "like") bvLike(bvS.id);
    else if (a === "cmadd") bvCmAdd(bvS.id);
    else if (a === "edit") { bvS.view = "edit"; renderBeyora(); }
    else if (a === "pin") { // 고정·해제는 글쓴이(토큰 있는 브라우저)만. 수정 날짜는 그대로 두고 pinAt 으로 최신을 가린다
      const old = bv().posts.find((x) => x.id === bvS.id); if (!old) return;
      const now = new Date().toISOString(), p = { ...old, pinned: old.pinned ? "" : now, pinAt: now };
      p.views = (BVR?.posts.find((x) => x.id === p.id)?.views) || 0; delete p._local;
      bvLocalEdit((L) => { L.posts = L.posts.filter((x) => x.id !== p.id); L.posts.push(p); });
      renderBeyora(); toast(p.pinned ? "글을 목록 맨 위에 고정했습니다" : "고정을 풀었습니다");
      bvSync({ msg: (p.pinned ? "글 고정: " : "글 고정 해제: ") + (p.title || "") });
    }
    else if (a === "cancel") { bvS.view = bvS.id ? "post" : "list"; renderBeyora(); }
    else if (a === "img") { const u = bvAskUrl("이미지 주소 (https://...jpg, png 등)"); if (u) bvInsert(`\n![](${u})\n`); }
    else if (a === "link") { const u = bvAskUrl("링크 주소 (https://...)"); if (u) { const t = (prompt("버튼에 보일 글자 (비우면 사이트 이름)") || "").trim().replace(/[[\]]/g, ""); bvInsert(`[${t || bvHost(u)}](${u})`); } }
    else if (a === "del") {
      if (!armed(b)) return; const id = bvS.id;
      bvLocalEdit((L) => { L.posts = L.posts.filter((x) => x.id !== id); });
      bvS.view = "list"; bvS.id = null; renderBeyora(); toast("글을 지웠습니다");
      bvSync({ del: id });
    } else if (a === "save") {
      const title = $("#bvTitle").value.trim(), body = $("#bvText").value.replace(/\s+$/, ""), cat = $("#bvCat").value, now = new Date().toISOString();
      if (!title && !body.trim()) { toast("제목이나 내용을 적어 주세요"); return; }
      const old = bvS.id && bv().posts.find((x) => x.id === bvS.id);
      const p = old ? { ...old, title, body, cat, updated: now } : { id: "p" + Date.now().toString(36), title, body, cat, created: now, updated: now, views: 0 };
      if (old) p.views = (BVR?.posts.find((x) => x.id === p.id)?.views) || 0; // 조회수는 저장소 값 + 이 브라우저 몫을 따로 더한다
      delete p._local;
      bvLocalEdit((L) => { L.posts = L.posts.filter((x) => x.id !== p.id); L.posts.push(p); });
      bvS.view = "post"; bvS.id = p.id; renderBeyora();
      bvSync({ title: p.title });
    } else if (a === "addcat") {
      const name = (prompt("새 카테고리 이름") || "").trim(); if (!name) return;
      const have = bvCats().find(([, n]) => n === name);
      if (have) { bvS.cat = have[0]; renderBeyora(); return; }
      const id = "c" + Date.now().toString(36);
      bvLocalEdit((L) => L.cats.push({ id, name })); bvS.cat = id; renderBeyora(); bvSync();
    } else if (a === "delcat") {
      if (!armed(b)) return; const id = bvS.cat;
      bvLocalEdit((L) => { L.cats = L.cats.filter((x) => x.id !== id); }); bvS.cat = "all"; renderBeyora(); bvSync({ delcat: id });
    }
  }
  async function bvCmAdd(id) {
    const text = $("#bvCmText").value.trim(), pw = $("#bvCmPw").value;
    if (!text) return toast("댓글을 적어 주세요");
    if (pw.length < 4) return toast("비밀번호를 4자 이상 넣어 주세요");
    bvCm.busy = true; renderBeyora();
    try { const j = await bvCmPost(id, { path: "/comments", body: { text, pw } }); if (bvCm.id === id && bvCm.list) bvCm.list.push(j.comment); BVC[id] = (BVC[id] || 0) + 1; toast("댓글을 남겼습니다"); if ($("#bvCmText")) { $("#bvCmText").value = ""; $("#bvCmPw").value = ""; } }
    catch (e) { toast("댓글 저장 실패: " + e.message); }
    bvCm.busy = false; renderBeyora();
  }
  async function bvCmDel(id, cid) {
    const tok = GH || MODE === "local" ? ghToken() : "";
    // 개발자 토큰이 있으면 비밀번호 없이
    let pw = "";
    if (!tok) { pw = prompt("댓글 비밀번호") || ""; if (!pw) return; }
    else if (!confirm("이 댓글을 지울까요?")) return;
    try { await bvCmPost(id, { path: "/comments/del", body: { cid, ...(pw ? { pw } : {}) }, tok }); }
    catch (e) { toast("삭제 실패: " + e.message); return; }
    if (bvCm.id === id && bvCm.list) bvCm.list = bvCm.list.filter((c) => c.cid !== cid); BVC[id] = Math.max(0, (BVC[id] || 1) - 1);
    toast("댓글을 지웠습니다"); renderBeyora();
  }
  const bvExport = () => { const D = bv(); D.posts.forEach((p) => delete p._local); return D; };
  // 내보내기 파일에 담긴 글을 합친다 (같은 글은 더 최근에 고친 쪽)
  function bvMerge(x) {
    if (!x || !Array.isArray(x.posts)) return;
    const have = bv(), y = bvNorm(x);
    y.posts = y.posts.filter((p) => { const h = have.posts.find((q) => q.id === p.id); return !h || bvStamp(p) > bvStamp(h); });
    y.cats = y.cats.filter((c) => !have.cats.some((q) => q.id === c.id));
    y.posts.forEach((p) => { delete p._local; p.views = BVR?.posts.find((q) => q.id === p.id)?.views || 0; });
    if (!y.posts.length && !y.cats.length) return;
    bvLocalEdit((L) => bvMergeInto(L, y)); renderBeyora(); bvSync();
  }

  // ------------------------------------------------------------ 비중안 비교
  // 비중 조정안: 집중 종목을 cap 까지 줄이고 차액을 mix 로 (공격·안정·보수). mix 는 설정에서 바꿀 수 있다
  const ALLOC_DEF = { agg: { n: "공격적", cap: 0.45, mix: "SMH 50, QQQ 50", d: "성장 업종으로 옮겨 확률 유지" }, mid: { n: "안정적", cap: 0.35, mix: "QQQ 50, SPY 50", d: "지수로 나쁜 경우 개선" }, con: { n: "보수적", cap: 0.25, mix: "SPY 50, SGOV 30, GLD 20", d: "현금·금으로 하락 방어" } };
  const ALLOC_SUB = { GLD: "GC=F", SMH: "QQQ" }; // 시세가 아직 없을 때 대신 쓸 종목
  const allocMix = (k) => String((S.state.alloc_mix || {})[k] || ALLOC_DEF[k].mix).split(",").map((x) => x.trim().split(/\s+/)).filter((x) => x[0]).map(([t, w]) => [t.toUpperCase(), Number(w) || 1]);
  async function runAlloc() {
    const st = $("#allocStatus"), btn = $("#btnAlloc");
    const want = [...new Set(Object.keys(ALLOC_DEF).flatMap((k) => allocMix(k).map((x) => x[0])))];
    const miss = want.filter((t) => !S.prices[t]);
    if (miss.length && MODE === "static" && !runAlloc.tried) { runAlloc.tried = true; st.textContent = `${miss.join(", ")} 시세를 받는 중...`; try { await browserCollect(miss); } catch (e) { /* 대체 종목으로 */ } }
    const prog = (k, n) => { st.innerHTML = `계산 중... (${k}/${n}) <span class="bar" style="display:inline-block;width:120px;vertical-align:middle"><i style="width:${(k / n) * 100}%"></i></span>`; };
    btn.disabled = true; prog(0, 4); await new Promise((r) => setTimeout(r, 30));
    try {
      const g = S.state.goal, m = S.state.model, { rows } = valuation(), subs = [];
      const res = (t) => (S.prices[t] ? t : ALLOC_SUB[t] && S.prices[ALLOC_SUB[t]] ? (subs.push(`${t}→${ALLOC_SUB[t]}`), ALLOC_SUB[t]) : null);
      const base = rows.filter((r) => r.valueKrw > 0).map((r) => ({ ticker: r.h.ticker, shares: r.sh, price0: r.p.v, ccy: r.ccy, valueKrw: r.valueKrw }));
      const V0 = base.reduce((a, h) => a + h.valueKrw, 0);
      const mixes = {}; for (const k of Object.keys(ALLOC_DEF)) mixes[k] = allocMix(k).map(([t, w]) => [res(t), w]).filter((x) => x[0]);
      for (const t of new Set(Object.values(mixes).flat().map((x) => x[0]))) if (!base.some((h) => h.ticker === t)) { const q = S.prices[t]; base.push({ ticker: t, shares: 0, price0: q.close[q.close.length - 1], ccy: ccyOf(t), valueKrw: 1, extra: true }); }
      const series = {}; for (const k in S.prices) series[k] = { dates: S.prices[k].dates, adj: S.prices[k].adj };
      const model = Model.buildModel({ holdings: base, series, fxOf, settings: m, events: S.state.events, betas: factorBetas().beta, startDate: today(), goalDate: g.date });
      const w0 = base.map((h) => (h.extra ? 0 : h.valueKrw / V0));
      const risky = base.map((h, i) => (model.factors[i].cash || h.extra ? 0 : w0[i])), top = risky.indexOf(Math.max(...risky));
      const plans = [{ k: "keep", name: "현재 유지", w: w0, rb: false }];
      for (const [k, D] of Object.entries(ALLOC_DEF)) {
        const w = [...w0], cut = w0[top] > D.cap ? w0[top] - D.cap : w0[top] * { agg: 0.1, mid: 0.25, con: 0.4 }[k], tot = mixes[k].reduce((a, x) => a + x[1], 0);
        w[top] -= cut; mixes[k].forEach(([t, x]) => { w[base.findIndex((h) => h.ticker === t)] += (cut * x) / tot; });
        plans.push({ k, name: `${D.n} (${base[top].ticker} ${pct(w[top], 0)})`, w, rb: true, cut });
      }
      const out = [];
      for (const pl of plans) {
        const hs = base.map((h, i) => ({ ...h, valueKrw: V0 * pl.w[i] }));
        const R = Model.simulate(model, { holdings: hs, scenario: m.scenario, nPaths: 1500, seed: Number(m.seed) || 1, goal: g.amount, monthly: Number(g.monthly_contribution) || 0, rebalance: pl.rb, withEvents: true, dof: m.t_dof, fxOf });
        out.push({ ...pl, R: { p_goal: R.p_goal, p_loss: R.p_loss, mdd_median: R.mdd_median, terminal: R.terminal, bands: R.bands } });
        prog(out.length, plans.length); await new Promise((r) => setTimeout(r, 10));
      }
      lastAlloc = { base: base.map((h) => ({ ticker: h.ticker, price0: h.price0, ccy: h.ccy, shares: h.shares })), V0, top, out, subs, fd: model.monthDates, at: Date.now() }; allocDirty = false;
      try { localStorage.setItem(AL_KEY, JSON.stringify({ sig: fcSig() + "|" + JSON.stringify(S.state.alloc_mix || {}), ...lastAlloc })); } catch (e) { /* 무시 */ }
      renderAllocTable(); renderAllocChart();
      if (curAna() === "alloc") aiRefresh();
    } catch (e) { st.textContent = "오류: " + e.message; console.error(e); }
    btn.disabled = false;
  }
  const AL_KEY = "naeilo-alloc2";
  function allocRestore() {
    try { const c = JSON.parse(localStorage.getItem(AL_KEY) || "null"); if (!c || c.sig !== fcSig() + "|" + JSON.stringify(S.state.alloc_mix || {})) return false; lastAlloc = c; allocDirty = false; return true; } catch (e) { return false; }
  }
  // 요약 박스 4개 (누르면 실행 계획) + 차액 종목 옵션
  function renderAllocTable() {
    const { base, out } = lastAlloc, pick = S.state.alloc_pick || "mid", tgt = S.state.alloc_target;
    $("#allocBoxes").innerHTML = out.map((o, j) => `<button type="button" class="abox ${o.k === pick ? "on" : ""}" data-ak="${o.k}" style="--ac:${C[j % C.length]}">
      <div class="lh"><b>${esc(o.k === "keep" ? "현재 유지" : ALLOC_DEF[o.k].n)}</b>${tgt && tgt.k === o.k ? '<span class="tag">목표</span>' : ""}</div>
      <div class="lv">${pct(o.R.p_goal, 0)} <small>목표 확률</small></div>
      <p>중앙값 ${krw(o.R.terminal.p50)} · 나쁜 5% ${krw(o.R.terminal.p5)}</p><p>최대 낙폭 ${pct(o.R.mdd_median, 0)} · 낮을 확률 ${pct(o.R.p_loss, 0)}</p>
      <p class="muted">${base.map((h, i) => (o.w[i] > 0.004 ? `${esc(h.ticker)} ${pct(o.w[i], 0)}` : "")).filter(Boolean).join(" · ")}</p></button>`).join("");
    $("#allocStatus").textContent = `${dtStr(lastAlloc.at || Date.now())} 계산 · 안별 1,500경로` + (lastAlloc.subs && lastAlloc.subs.length ? ` · 시세가 없어 대신 씀: ${[...new Set(lastAlloc.subs)].join(", ")}` : "");
    $("#allocMix").innerHTML = Object.entries(ALLOC_DEF).map(([k, D]) => `<label><span class="row between"><span>${D.n} · ${esc(D.d)}</span><span class="muted small">기본 ${esc(D.mix)}</span></span><input type="text" data-mix="${k}" value="${esc((S.state.alloc_mix || {})[k] || D.mix)}" style="width:100%"></label>`).join("") + `<p class="muted small">"종목 비중, 종목 비중" 형식. 바꾸면 다시 계산합니다.</p>`;
    renderAllocPlan();
  }
  // 고른 비중 조정안의 실행 계획: 6개월 분할 + 세금(연도 나누기) + 목표로 정하면 리밸런싱 신호
  function renderAllocPlan() {
    const card = $("#allocPlanCard"); if (!lastAlloc) return;
    const { base, out, top, V0 } = lastAlloc, k = S.state.alloc_pick || "mid", o = out.find((x) => x.k === k);
    if (!o || k === "keep") { card.style.display = "none"; return; }
    card.style.display = "block";
    $("#allocPlanTitle").textContent = `실행 계획 · ${ALLOC_DEF[k].n}`;
    const fx = (h) => fxNow(h.ccy) || 1, w0 = out[0].w, L = [];
    const sells = [], buys = [];
    base.forEach((h, i) => { const dv = (o.w[i] - w0[i]) * V0, sh = dv / (h.price0 * fx(h)); if (Math.abs(sh) < 0.5) return; (dv < 0 ? sells : buys).push({ t: h.ticker, sh: Math.abs(sh), v: Math.abs(dv) }); });
    L.push(`<p><b>6개월 분할</b>: 매월 ${sells.map((x) => `${esc(x.t)} 약 ${nf(Math.ceil(x.sh / 6))}주 매도`).join(", ")} → ${buys.map((x) => `${esc(x.t)} 약 ${nf(Math.ceil(x.sh / 6))}주 (${krw(x.v / 6)}원)`).join(", ")} 매수.</p>`);
    const th = S.state.holdings.find((h) => h.ticker === base[top].ticker), avg = Number(th?.avg_cost) || 0, s0 = sells.find((x) => x.t === base[top].ticker);
    if (s0 && avg) {
      const gps = (base[top].price0 - avg) * fx(base[top]), used = Math.max(0, realizedYear(new Date().getFullYear()));
      const tax = (n, first) => Math.max(0, n * gps - Math.max(0, CGT_DED - first)) * CGT;
      const one = tax(s0.sh, used), two = tax(s0.sh / 2, used) + tax(s0.sh / 2, 0);
      L.push(`<p><b>세금</b>: 올해 안에 모두 팔면 약 ${krw(one)}원, 올해와 내년에 반씩 나누면 약 ${krw(two)}원${one - two > 0 ? ` (<b>${krw(one - two)}원 절약</b>)` : ""}.${used ? ` 올해 이미 실현한 이익 ${krw(used)}원 반영.` : ""}</p>`);
    } else if (s0) L.push(`<p class="muted small">매수 단가를 넣으면 연도별 세금을 계산합니다.</p>`);
    const tgt = S.state.alloc_target;
    L.push(`<div class="row wrap"><button class="primary sm" id="allocSetTgt">${tgt && tgt.k === k ? "목표로 정해 둠 (해제)" : "이 비중 조정안을 목표로 정하기"}</button><span class="muted small">목표로 정하면 비중이 5%p 넘게 벗어날 때 종목 전략에서 알려 줍니다.</span></div>`);
    $("#allocPlan").innerHTML = L.join("");
    $("#allocSetTgt").onclick = () => {
      if (tgt && tgt.k === k) delete S.state.alloc_target;
      else S.state.alloc_target = { k, name: ALLOC_DEF[k].n, at: today(), w: Object.fromEntries(base.map((h, i) => [h.ticker, +o.w[i].toFixed(4)]).filter((x) => x[1] > 0.004)) };
      save(false); renderAllocTable();
    };
  }
  // 리밸런싱 신호: 목표 비중과 지금 비중 차이
  function rebalanceGap() {
    const tg = S.state.alloc_target; if (!tg) return null;
    const { rows } = valuation(), now = {}; rows.forEach((r) => { if (r.w > 0) now[r.h.ticker] = r.w; });
    const all = new Set([...Object.keys(tg.w), ...Object.keys(now)]);
    const gaps = [...all].map((t) => [t, (now[t] || 0) - (tg.w[t] || 0)]).filter((x) => Math.abs(x[1]) > 0.05).sort((a, b) => Math.abs(b[1]) - Math.abs(a[1]));
    return { tg, gaps };
  }

  function renderAllocChart() {
    if (!lastAlloc) return;
    const q = $("#allocQ .on")?.dataset.q || "p50", g = S.state.goal, pick = S.state.alloc_pick || "mid";
    // 고른 안은 굵게, 현재 유지는 기준선, 나머지는 흐리게
    Charts.lineChart($("#allocChart"), { x: lastAlloc.fd, height: 300, yfmt: krwAxis,
      series: lastAlloc.out.map((o, j) => ({ name: o.name, y: o.R.bands[q], color: C[j % C.length], width: o.k === pick ? 3.2 : j === 0 ? 2 : 1.4, dash: o.k === pick || j === 0 ? "" : "5 3", opacity: o.k === pick || j === 0 ? 1 : 0.3 })),
      hlines: [{ y: g.amount, label: "목표 " + krw(g.amount) }] });
  }

  // ------------------------------------------------------------ 거래 기록 (수량 기록 lots 의 차이)
  function priceOn(t, d) {
    const p = S.prices[t]; if (!p) return null;
    let lo = 0, hi = p.dates.length - 1, k = -1; while (lo <= hi) { const m = (lo + hi) >> 1; if (p.dates[m] <= d) { k = m; lo = m + 1; } else hi = m - 1; }
    return k >= 0 ? p.close[k] : p.close[0];
  }
  function trades() {
    const L = S.state.lots || [], out = [];
    for (let i = 1; i < L.length; i++) {
      const a = L[i - 1].h, b = L[i].h;
      for (const t of new Set([...Object.keys(a), ...Object.keys(b)])) {
        const q = (b[t] || 0) - (a[t] || 0); if (!q) continue;
        const px = priceOn(t, L[i].d), fx = fxNow(ccyOf(t)) || 1, avg = Number(S.state.holdings.find((h) => h.ticker === t)?.avg_cost) || null;
        out.push({ i, d: L[i].d, t, q, px, krw: px ? Math.abs(q) * px * fx : null, real: q < 0 && avg && px ? (px - avg) * -q * fx : null });
      }
    }
    return out;
  }
  function realizedYear(y) { return trades().filter((x) => x.real != null && x.d.startsWith(String(y))).reduce((a, x) => a + x.real, 0); }
  function renderTrades() {
    const L = S.state.lots || [], tr = trades();
    $("#tradeSum").textContent = tr.length ? `· ${tr.length}건` : L.length ? `· ${L[0].d} 시작` : "";
    const start = L[0] ? `<tr><td class="l">${L[0].d}</td><td class="l" colspan="5">시작 보유: ${Object.entries(L[0].h).map(([t, q]) => `${esc(t)} ${nf(q)}`).join(", ")}</td></tr>` : "";
    $("#tradeTable").innerHTML = `<tr><th class="l">날짜</th><th class="l">종목</th><th>수량</th><th>가격<br><span class="muted">금액</span></th><th>실현 손익</th><th></th></tr>` + start +
      tr.slice().reverse().map((x) => `<tr><td class="l"><input type="date" data-lot="${x.i}" value="${x.d}" style="width:9.5em"></td><td class="l">${esc(x.t)}</td><td class="${cls(x.q)}">${x.q > 0 ? "+" : ""}${nf(x.q)}</td>
        <td>${x.px ? nf(x.px, 2) : "-"}<br><span class="muted">${x.krw ? krw(x.krw) + "원" : ""}</span></td><td>${x.real != null ? `<span class="${cls(x.real)}">${krw(x.real)}원</span>` : x.q < 0 ? '<span class="muted small">매수 단가 필요</span>' : ""}</td><td><button class="danger x" data-dl="${x.i}" data-dt="${esc(x.t)}" title="이 기록 지우기">✕</button></td></tr>`).join("") +
      (L.length > 1 ? `<tr><td colspan="6" class="l"><button class="sm" id="tradeClear">기록 모두 지우기</button> <span class="muted small">지금 수량을 시작으로 다시 기록합니다.</span></td></tr>` : "");
  }
  // 기록 하나 지우기: 잘못 넣은 수량으로 보고, 그 이전 기록들도 바뀐 뒤 수량으로 맞춘다
  function delTrade(i, t) {
    const L = S.state.lots; if (!L[i] || !confirm(`${L[i].d} ${t} 기록을 지울까요?`)) return;
    const v = L[i].h[t], a = L[i - 1].h[t];
    for (let j = i - 1; j >= 0 && L[j].h[t] === a; j--) { if (v) L[j].h[t] = v; else delete L[j].h[t]; }
    for (let j = L.length - 1; j > 0; j--) if (JSON.stringify(L[j].h) === JSON.stringify(L[j - 1].h)) L.splice(j, 1);
    save(false); renderTrades(); if ($("#tabs .on").dataset.tab === "dash") renderDash();
  }
  function clearTrades() {
    if (!confirm("거래 기록을 모두 지울까요? 목표 진행 그래프도 지금 수량 기준으로 다시 그립니다.")) return;
    S.state.lots = []; save(false); renderTrades(); if ($("#tabs .on").dataset.tab === "dash") renderDash();
  }
  function onTradeDate(e) {
    const i = +e.target.dataset.lot, L = S.state.lots, d = e.target.value; if (!L[i] || !d) return;
    const lo = L[i - 1]?.d || "0000", hi = L[i + 1]?.d || "9999";
    if (d <= lo || d >= hi) { toast(`${lo} 과 ${hi} 사이 날짜만 됩니다`); e.target.value = L[i].d; return; }
    L[i].d = d; save(false); renderTrades(); if ($("#tabs .on").dataset.tab === "dash") renderDash();
  }
  // 백업 알림: 입력값이 이 브라우저에만 있으므로, 바뀐 뒤 14일 넘게 백업하지 않았으면 대시보드에 알린다
  function markBackup() { S.state.ui.last_backup = today(); save(false); renderBackupNag(); }
  function renderBackupNag() {
    const box = $("#backupNag"), ui = S.state.ui, L = S.state.lots || [];
    const lastChange = L.length ? L[L.length - 1].d : null, last = ui.last_backup || null;
    const due = !S.state.sample && lastChange && (!last || last < lastChange) && yearsBetween(last || L[0].d, today()) * 365 >= 14 && !(ui.backup_snooze && ui.backup_snooze > today());
    box.style.display = due ? "block" : "none"; if (!due) return;
    box.innerHTML = `<b>입력값을 백업해 두세요.</b> <span class="small">종목·거래 기록은 이 브라우저에만 저장됩니다${last ? ` (마지막 백업 ${last})` : " (아직 백업 없음)"}. 브라우저 기록을 지우면 사라집니다.</span> <button class="primary sm" id="bkNow">지금 백업</button> <button class="sm" id="bkLater">7일 뒤에</button>`;
    $("#bkNow").onclick = () => $("#btnExport").click();
    $("#bkLater").onclick = () => { ui.backup_snooze = new Date(Date.now() + 7 * 86400e3).toISOString().slice(0, 10); save(false); renderBackupNag(); };
  }

  // ------------------------------------------------------------ 배당·세금
  // 수정종가/종가 비율이 바뀌는 날 = 배당락. 금액 = 전날 종가 × (1 - 전날 비율/당일 비율)
  function dividends(t) {
    const p = S.prices[t]; if (!p || !p.adj) return [];
    const out = [];
    for (let i = 1; i < p.dates.length; i++) {
      const r0 = p.adj[i - 1] / p.close[i - 1], r1 = p.adj[i] / p.close[i];
      if (r0 > 0 && r1 > 0 && r0 / r1 < 0.9995) out.push({ d: p.dates[i], amt: p.close[i - 1] * (1 - r0 / r1) });
    }
    return out;
  }
  const WHT = 0.15, CGT = 0.22, CGT_DED = 2500000;
  function divGrowth(t, ds) {
    // 지난 12개월 합 대비 24~36개월 전 12개월 합으로 연 증가율 (0~15%로 제한)
    const a = Model.addMonths(today(), -12), b = Model.addMonths(today(), -24), c = Model.addMonths(today(), -36);
    const y1 = ds.filter((x) => x.d > a).reduce((s2, x) => s2 + x.amt, 0), y3 = ds.filter((x) => x.d > c && x.d <= b).reduce((s2, x) => s2 + x.amt, 0);
    return y1 > 0 && y3 > 0 ? Math.max(0, Math.min(0.15, Math.sqrt(y1 / y3) - 1)) : 0;
  }
  function renderCash() {
    const { rows, total } = valuation(), hs = rows.filter((r) => r.valueKrw > 0);
    if (!hs.length) { $("#divKpis").innerHTML = ""; $("#taxBox").innerHTML = "<p class='muted'>보유 수량을 넣으면 계산합니다.</p>"; $("#taxGoal").innerHTML = ""; $("#taxYears").innerHTML = ""; return; }
    const span = +($("#divSpan .on")?.dataset.y || 3), yAgo = Model.addMonths(today(), -12), months = []; for (let k = 1; k <= 36; k++) months.push(Model.addMonths(today(), k).slice(0, 7));
    const byM = Object.fromEntries(months.map((m) => [m, 0])), per = [];
    hs.forEach((r) => {
      const all = dividends(r.h.ticker), ds = all.filter((x) => x.d > yAgo), tax = r.ccy === "KRW" ? 0.154 : WHT, gr = divGrowth(r.h.ticker, all);
      let y1 = 0, y3 = 0;
      ds.forEach((x) => { for (let n = 1; n <= 3; n++) { const m = Model.addMonths(x.d, 12 * n).slice(0, 7), v = x.amt * r.sh * (r.fx || 1) * (1 - tax) * (1 + gr) ** (n - 1); if (m in byM) { byM[m] += v; y3 += v; if (n === 1) y1 += v; } } });
      if (ds.length) per.push({ t: r.h.ticker, n: ds.length, y1, y3, gr, yld: ds.reduce((a, x) => a + x.amt, 0) / r.p.v });
    });
    const Y1 = per.reduce((a, x) => a + x.y1, 0), Y3 = per.reduce((a, x) => a + x.y3, 0), shown = months.slice(0, span * 12);
    $("#divTitle").textContent = `앞으로 ${span}년 예상 배당·이자 (세후, 원화)`;
    $("#divKpis").innerHTML = [["1년 예상 (세후)", krw(Y1) + "원", `평가액의 ${pct(Y1 / total, 2)}`], ["3년 합계 (세후)", krw(Y3) + "원", "증가율 반영"], ["월 평균 (1년)", krw(Y1 / 12) + "원", per.map((x) => x.t).join(", ") || "지급 종목 없음"]]
      .map(([k, v, s2]) => `<div class="kpi"><div class="k">${k}</div><div class="v">${v}</div><div class="s">${s2}</div></div>`).join("");
    if (Y1 > 0) { $("#divChart").style.display = "block"; Charts.lineChart($("#divChart"), { x: shown.map((m) => m + "-15"), height: 170, yfmt: krwAxis, legend: false, series: [{ name: "월별", y: shown.map((m) => byM[m]), color: "var(--c3)", width: 2 }] }); }
    else $("#divChart").style.display = "none";
    $("#divTable").innerHTML = per.length ? `<tr><th class="l">종목</th><th>지급<br><span class="muted">지난 12개월</span></th><th>배당률<br><span class="muted">연 증가율</span></th><th>1년 (세후)<br><span class="muted">3년 합계</span></th></tr>` + per.map((x) => `<tr><td class="l">${esc(x.t)}</td><td>${x.n}회</td><td>${pct(x.yld, 2)}<br><span class="muted">${pct(x.gr, 0)}</span></td><td>${krw(x.y1)}원<br><span class="muted">${krw(x.y3)}원</span></td></tr>`).join("") : "<tr><td class='muted'>지난 12개월 배당·이자를 준 종목이 없습니다.</td></tr>";
    // 양도세
    const g = S.state.goal, cb = costBasis(), yNow = new Date().getFullYear(), used = Math.max(0, realizedYear(yNow));
    // 목표일에 모두 현금화하면: 전망 경로별 세금을 빼고 다시 센 세후 목표 확률
    const R = lastForecast && lastForecast.withEv;
    if (R && R.term && R.term.length) {
      const basis = (cb ?? R.V0) + (R.monthly || 0) * Math.max(0, (R.bands.p50.length || 2) - 2), ok = [], taxes = [];
      for (const v of R.term) { const t = Math.max(0, v - basis - CGT_DED) * CGT; taxes.push(t); ok.push(v - t >= g.amount); }
      const pAfter = ok.filter(Boolean).length / ok.length, tMed = Math.max(0, R.terminal.p50 - basis - CGT_DED) * CGT;
      $("#taxGoal").innerHTML = `<div class="kpis">${[["세후 목표 달성 확률", pct(pAfter, 0), `세전 ${pct(R.p_goal, 0)} · 목표일에 모두 판다면`], ["현금화 세금 (중앙값)", krw(tMed) + "원", `중앙값 ${krw(R.terminal.p50)}원 → 세후 ${krw(R.terminal.p50 - tMed)}원`], ["세금 낸 뒤 10억이 되려면", krw(g.amount + Math.max(0, g.amount - basis - CGT_DED) * CGT / (1 - CGT)) + "원", "세전으로 필요한 금액 (근사)"]].map(([k, v, s2]) => `<div class="kpi"><div class="k">${k}</div><div class="v">${v}</div><div class="s">${s2}</div></div>`).join("")}</div><p class="muted small">${cb ? "매수 원금" : "매수 단가가 없어 오늘 평가액"} ${krw(cb ?? R.V0)}원을 원금으로 봄. 내 관점 전망 기준.</p>`;
    } else $("#taxGoal").innerHTML = `<p class="muted small">3년 전망을 한 번 계산하면(분석·전략 › 3년 전망) 세후 목표 달성 확률이 나옵니다.</p>`;
    const wa = hs.filter((r) => r.avg && r.ccy !== "KRW");
    if (!wa.length) { $("#taxBox").innerHTML = `<p>매수 단가를 넣으면 종목별 세금을 계산합니다. <a href="#" data-go="quotes">매수 단가 넣기</a></p>`; $("#taxYears").innerHTML = ""; return; }
    const gains = wa.map((r) => ({ t: r.h.ticker, g: (r.p.v - r.avg) * r.sh * r.fx, gps: (r.p.v - r.avg) * r.fx, sh: r.sh, w: r.w, px: r.p.v * r.fx }));
    const sum = gains.reduce((a, x) => a + x.g, 0), taxAll = Math.max(0, sum - CGT_DED) * CGT;
    const best = gains.filter((x) => x.gps > 0).sort((a, b) => b.g - a.g)[0];
    const L = [`<table class="grid"><tr><th class="l">종목</th><th>평가 이익 (원)</th><th>주당 이익</th></tr>${gains.map((x) => `<tr><td class="l">${esc(x.t)}</td><td class="${cls(x.g)}">${krw(x.g)}</td><td>${krw(x.gps)}</td></tr>`).join("")}</table>`];
    L.push(`<p>지금 모두 판다면 세금 약 <b>${krw(taxAll)}원</b> (이익 합계 ${krw(sum)}원).${used ? ` 올해 이미 실현한 이익 ${krw(used)}원.` : ""}</p>`);
    $("#taxBox").innerHTML = L.join("");
    // 연도별: 해마다 공제 250만원. 계획 매도 = 목표로 정한 비중 조정안(없으면 집중 종목 45%로)을 처음 두 해에 나눠
    const top = gains.filter((x) => x.gps > 0).sort((a, b) => b.w - a.w)[0];
    const tg = S.state.alloc_target, tw = tg && top ? tg.w[top.t] ?? null : null;
    const sellAll = top ? Math.max(0, Math.ceil((((top.w - (tw != null ? tw : Math.min(top.w, 0.45))) * total) / top.px))) : 0;
    const yEnd = +g.date.slice(0, 4), years = []; for (let y = yNow; y <= Math.max(yNow, yEnd); y++) years.push(y);
    const nSplit = Math.min(2, years.length);
    $("#taxYears").innerHTML = `<tr><th class="l">연도</th><th>남은 공제</th><th>세금 없이<br>팔 수 있는 수량</th><th>계획 매도</th><th>예상 세금</th></tr>` + years.map((y, k) => {
      const left = Math.max(0, CGT_DED - (y === yNow ? used : 0)), free = best && best.gps > 0 ? Math.floor(left / best.gps) : 0;
      const sell = top && k < nSplit ? Math.round(sellAll / nSplit) : 0, tax = sell ? Math.max(0, sell * top.gps - left) * CGT : 0;
      return `<tr><td class="l">${y}${y === yNow ? " (올해)" : ""}</td><td>${krw(left)}원</td><td>${best ? `${esc(best.t)} ${nf(free)}주` : "-"}</td><td>${sell ? `${esc(top.t)} ${nf(sell)}주` : "-"}</td><td>${sell ? krw(tax) + "원" : "-"}</td></tr>`;
    }).join("") + `<tr><td class="l muted wrapc" colspan="5">계획 매도: ${tg ? `목표로 정한 '${esc(tg.name)}' 비중 조정안` : "집중 종목을 45%로 줄이는 경우"}을 처음 ${nSplit}년에 나눔. 한 해에 모두 팔면 세금 약 ${top && sellAll ? krw(Math.max(0, sellAll * top.gps - Math.max(0, CGT_DED - used)) * CGT) + "원" : "-"}.</td></tr>`;
  }


  // ------------------------------------------------------------ 환율 (원/달러)
  function fxInfo() {
    const p = S.prices["KRW=X"]; if (!p || p.close.length < 30) return null;
    const ind = Model.indicators(p.dates, p.close), sg = ind.sig, now = fxNow("USD") || sg.close;
    const { rows, total } = valuation();
    const usdW = total ? rows.filter((r) => r.ccy === "USD").reduce((a, r) => a + (r.valueKrw || 0), 0) / total : 0;
    const g = S.state.goal, T = Math.max(0.01, yearsBetween(today(), g.date));
    const ff = lastForecast && lastForecast.b.model.factors.find((f) => f.kind === "fx" && f.key === "KRW=X");
    const vol = ff ? ff.vol : sg.vol_hist * (Number(S.state.model.fx_vol_mult) || 1), mu = ff ? ff.mu[S.state.model.scenario] : (Number(S.state.model.fx_drift) || 0) / 100;
    const z = { p5: -1.645, p25: -0.674, p50: 0, p75: 0.674, p95: 1.645 };
    const at = (t, k) => now * Math.exp((mu - vol * vol / 2) * t + z[k] * vol * Math.sqrt(t));
    const i0 = Math.max(0, p.dates.length - 253), hi1 = Math.max(...p.close.slice(i0)), lo1 = Math.min(...p.close.slice(i0));
    return { p, ind, sg, now, usdW, total, T, vol, mu, at, hi1, lo1, goalDate: g.date };
  }
  function renderFx() {
    const F = fxInfo();
    if (!F) { $("#fxKpis").innerHTML = ""; $("#fxChart").textContent = "환율 시세가 없습니다. 시세 수집을 눌러 주세요."; return; }
    const { sg, now, usdW, total, T, at } = F;
    const effect1y = sg.ret_1y != null ? usdW * sg.ret_1y : null;
    $("#fxKpis").innerHTML = [
      ["현재 원/달러", nf(now, 1), `칼만 추세 ${nf(sg.kalman_level, 1)} (${spct(sg.dev_from_kalman)})`],
      ["1개월 · 3개월", `<span class="${cls(sg.ret_1m)}">${spct(sg.ret_1m)}</span>`, `3개월 <span class="${cls(sg.ret_3m)}">${spct(sg.ret_3m)}</span>`],
      ["1년 변화", `<span class="${cls(sg.ret_1y)}">${spct(sg.ret_1y)}</span>`, `1년 범위 ${nf(F.lo1, 0)}~${nf(F.hi1, 0)}`],
      ["추세", sg.trend.replace(" (추세 판단 대상 아님)", ""), `기울기 연 ${spct(sg.slope_ann, 1)} · 오르면 원화 약세`],
      ["변동성 (연)", pct(sg.vol_ewma, 1), `모형 적용 ${pct(F.vol, 1)}`],
      ["달러 자산 비중", pct(usdW, 0), `${krw(total * usdW)}원`],
      ["지난 1년 환율 효과", effect1y == null ? "-" : `<span class="${cls(effect1y)}">${spct(effect1y)}</span>`, "원화 평가액에 더해진 몫 (근사)"],
      ["목표일 환율 중앙값", nf(at(T, "p50"), 0), `${F.goalDate} · 5~95% ${nf(at(T, "p5"), 0)}~${nf(at(T, "p95"), 0)}`],
      ["원화 10% 강세면", `<span class="dn">${krw(-total * usdW * 0.1)}원</span>`, "주가 변동 없이 환율만"],
    ].map(([k, v, s2]) => `<div class="kpi"><div class="k">${k}</div><div class="v">${v}</div><div class="s">${s2}</div></div>`).join("");
    const n = +($("#fxRange .on")?.dataset.r || 780), ind = F.ind, k0 = Math.max(0, ind.dates.length - n), x = ind.dates.slice(k0);
    const md = []; for (let k = 1; Model.addMonths(today(), k) <= F.goalDate; k++) md.push(Model.addMonths(today(), k));
    if (md[md.length - 1] !== F.goalDate) md.push(F.goalDate);
    const fd = [today(), ...md], ft = fd.map((d) => yearsBetween(today(), d)), band = (k) => ft.map((t) => at(t, k));
    Charts.lineChart($("#fxChart"), { x, height: 300, yfmt: (v) => nf(v, 0),
      bands: [{ x: fd, lo: band("p5"), hi: band("p95"), color: "var(--band)", opacity: 0.13, name: "전망 5~95%" }, { x: fd, lo: band("p25"), hi: band("p75"), color: "var(--band)", opacity: 0.25, name: "25~75%" }],
      series: [{ name: "칼만 추세", y: ind.level.slice(k0), color: "var(--c1)", width: 3 }, { name: "원/달러", y: ind.close.slice(k0), color: "var(--fg)", width: 1 },
        { name: "EMA50", y: ind.ema50.slice(k0), color: "var(--c3)", dash: "4 3" }, ...(ind.dates.length >= 200 ? [{ name: "EMA200", y: ind.ema200.slice(k0), color: "var(--c2)", dash: "6 3" }] : []),
        { name: "전망 중앙값", x: fd, y: band("p50"), color: "var(--c1)", width: 1.6, dash: "2 2" }],
      vlines: [{ x: today(), label: "오늘" }] });
    const steps = [-0.15, -0.1, -0.05, 0.05, 0.1, 0.15];
    $("#fxSens").innerHTML = `<tr><th class="l">환율 변화</th><th>원/달러</th><th>평가액 변화</th><th>평가액</th><th>목표 대비</th></tr>` + steps.map((c) => {
      const d = total * usdW * c, v = total + d;
      return `<tr><td class="l">${c < 0 ? "원화 강세" : "원화 약세"} ${spct(c, 0)}</td><td>${nf(now * (1 + c), 0)}</td><td class="${cls(d)}">${krw(d)}원</td><td>${krw(v)}원</td><td>${pct(v / S.state.goal.amount)}</td></tr>`;
    }).join("");
  }

  // ------------------------------------------------------------ 추세

  // ------------------------------------------------------------ 설정
  function renderGh() {
    const box = $("#ghBox"); if (!box) return;
    if (MODE !== "static") { $("#devCard").style.display = "none"; return; }
    const has = !!ghToken();
    box.innerHTML = `<p class="small">일반 사용자는 필요 없습니다. 토큰을 넣으면 '시세 수집'이 GitHub Actions 수집을 직접 실행하고 저장소 데이터를 갱신합니다(공개 중계 대신). 인사이트의 Beyora 글도 이 토큰으로 저장소(data/beyora.json)에 저장되고, 토큰이 없는 사람은 읽기만 합니다. ${has ? "<b class='good'>연결됨.</b>" : ""} 토큰은 이 브라우저에만 저장됩니다.</p>
      <div class="row wrap"><input id="ghToken" type="password" size="40" placeholder="${has ? "새 토큰으로 바꾸려면 붙여넣기" : "GitHub 토큰 붙여넣기 (github_pat_...)"}">
      <button id="ghSave" class="primary">저장</button>${has ? '<button id="ghTest">연결 확인</button><button id="ghDel" class="danger">연결 해제</button>' : ""}</div>
      <details class="small" ${has ? "" : "open"}><summary>토큰 만드는 법 (1분)</summary><ol>
      <li><a href="https://github.com/settings/personal-access-tokens/new" target="_blank" rel="noopener">GitHub 토큰 만들기</a> 페이지를 엽니다 (Fine-grained token).</li>
      <li>Token name 아무거나, Expiration 원하는 기간, Repository access → <b>Only select repositories</b> → <b>${GH ? esc(GH.repo) : "asset-tracker"}</b>.</li>
      <li>Permissions → Repository permissions → <b>Actions: Read and write</b>, <b>Contents: Read and write</b> (Beyora 글을 저장소에 저장).</li>
      <li>Generate token → 복사해서 위 칸에 붙여넣고 저장.</li></ol></details>`;
    $("#ghSave").onclick = async () => {
      const v = $("#ghToken").value.replace(/\s+/g, ""); if (!v) return toast("토큰을 붙여넣어 주세요");
      if (!/^[A-Za-z0-9_]{20,}$/.test(v)) return toast("토큰 형식이 아닙니다. github_pat_ 로 시작하는 값만 그대로 붙여넣어 주세요");
      try { localStorage.setItem(TOKEN_KEY, v); } catch (e) { return toast("브라우저 저장 실패"); }
      try { await ghApi("actions/workflows/collect.yml"); toast("GitHub 연결됨"); renderGh(); bvLoad(true); const miss = missingTickers(); if (miss.length) { showTab("quotes"); ghCollect(miss); } }
      catch (e) { toast("연결 실패: " + e.message); renderGh(); }
    };
    if (has) {
      $("#ghTest").onclick = async () => { try { await ghApi("actions/workflows/collect.yml"); toast("정상 연결"); } catch (e) { toast("연결 실패: " + e.message); } };
      $("#ghDel").onclick = (e) => { if (armed(e.target)) { try { localStorage.removeItem(TOKEN_KEY); } catch (er) { /* 무시 */ } renderGh(); } };
    }
  }
  function renderSettings() {
    renderGh();
    const m = S.state.model;
    $("#modelForm").innerHTML = MODEL_FIELDS.map(([k, lab, desc, type]) => type === "bool"
      ? `<div><label><span>${lab}</span><input type="checkbox" data-k="${k}" ${m[k] ? "checked" : ""}></label><span class="desc">${desc}</span></div>`
      : `<div><label><span>${lab}</span><input type="number" step="any" data-k="${k}" value="${m[k]}"></label><span class="desc">${desc}</span></div>`).join("");
  }
  function onModelEdit(e) {
    const k = e.target.dataset.k; if (!k) return;
    S.state.model[k] = e.target.type === "checkbox" ? e.target.checked : Number(e.target.value);
    save();
  }

  // ------------------------------------------------------------ 공통
  function renderAll() {
    renderHeader(); renderQuotes(); renderGoalInputs(); renderEvents(); renderSettings();
    const tab = $("#tabs .on").dataset.tab;
    if (tab === "dash") renderDash();
    if (tab === "analysis") renderAnalysis();
  }
  const curAna = () => $("#anaNav .on")?.dataset.a || "forecast";
  function renderAnalysis() {
    const a = curAna();
    $$(".ana").forEach((el) => (el.style.display = el.id === "ana-" + a ? "block" : "none"));
    if (a === "strategy") renderStockPrices();
    if (a === "events") { renderSchedule(); renderSens(); }
    if (a === "fx") renderFx();
    if (a === "cash") renderCash();
    if (a === "strategy" || a === "forecast" || a === "events" || a === "fx") { if (!lastForecast && !fcRestore()) runForecast(); else renderForecast(); } // 리로드해도 저장된 전망을 쓰고, 다시 계산은 시나리오 박스를 누를 때만
    if (a === "alloc") { if ((!lastAlloc || allocDirty) && !allocRestore()) runAlloc(); else { renderAllocTable(); renderAllocChart(); aiRefresh(); } }
    try { localStorage.setItem("ana", a); } catch (e) { /* 무시 */ }
  }
  function showTab(name) {
    if (!$(`#tabs button[data-tab="${name}"]`)) name = "dash";
    $$("#tabs button").forEach((b) => b.classList.toggle("on", b.dataset.tab === name));
    $$(".tab").forEach((t) => t.classList.toggle("on", t.id === "tab-" + name));
    if (name === "dash") renderDash();
    if (name === "analysis") renderAnalysis();
    if (name === "insight") renderInsight();
    try { localStorage.setItem("tab", name); } catch (e) { /* 무시 */ }
  }
  function segClick(id, cb) { $(id).addEventListener("click", (e) => { const b = e.target.closest("button"); if (!b) return; $$(id + " button").forEach((x) => x.classList.toggle("on", x === b)); cb(); }); }

  function bind() {
    $("#tabs").addEventListener("click", (e) => { const b = e.target.closest("button"); if (b) showTab(b.dataset.tab); });
    document.addEventListener("click", (e) => { const g = e.target.closest("[data-go]"); if (!g) return; e.preventDefault(); showTab(g.dataset.go); const d = $("#holdDet"); if (g.dataset.go === "quotes" && d) { d.open = true; d.scrollIntoView({ behavior: "smooth" }); } });
    $("header .logo").onclick = () => { showTab("dash"); window.scrollTo({ top: 0, behavior: "smooth" }); };
    $("#btnCollect").onclick = () => collect(false).finally(foldHold);
    $("#btnQuotes").onclick = () => collect(true).finally(foldHold);
    $("#autoRefresh").value = String(S.state.ui.auto_refresh_min || 0);
    $("#autoRefresh").onchange = (e) => { S.state.ui.auto_refresh_min = +e.target.value; setAuto(+e.target.value); save(false); };
    $("#holdTable").addEventListener("input", onHoldEdit);
    $("#holdTable").addEventListener("change", onHoldEdit);
    $("#holdTable").addEventListener("click", (e) => { const d = e.target.closest("[data-del]"); if (d) delHolding(+d.dataset.del, d); });
    $("#btnAdd").onclick = addHolding;
    $("#addAvg").addEventListener("keydown", (e) => e.key === "Enter" && addHolding());
    $("#addShares").addEventListener("keydown", (e) => e.key === "Enter" && addHolding());
    $("#goalQuick").addEventListener("click", (e) => { const b = e.target.closest("button[data-set]"); if (!b) return; const el = $("#" + b.dataset.set); el.value = b.dataset.v; el.dispatchEvent(new Event("change")); });
    ["#goalAmount", "#goalDate", "#startDate", "#goalYears", "#monthly"].forEach((s) => { $(s).addEventListener("change", onGoalEdit); });
    $("#newsFuture").addEventListener("click", (e) => { // 제목을 누르면 읽은 기사로 표시
      const a = e.target.closest("a[data-nid]"); if (!a) return;
      const o = insLoad(); o.read[a.dataset.nid] = Date.now(); insSave(o); a.classList.add("read");
    });
    segClick("#histRange", renderDash); segClick("#histStep", renderDash); segClick("#histMode", renderDash); segClick("#histCcy", renderDash); segClick("#histBasis", renderDash); // 보기 옵션은 위 기간 버튼을 바꾸지 않는다
    segClick("#stockRange", renderStockPrices); segClick("#allocQ", renderAllocChart); segClick("#fxRange", renderFx); segClick("#divSpan", renderCash);
    segClick("#anaNav", renderAnalysis);
    $("#btnAlloc").onclick = () => { allocDirty = true; runAlloc(); };
    $("#allocBoxes").addEventListener("click", (e) => { const b = e.target.closest("[data-ak]"); if (!b) return; S.state.alloc_pick = b.dataset.ak; save(false); renderAllocTable(); renderAllocChart(); });
    $("#allocMix").addEventListener("change", (e) => { const k = e.target.dataset.mix; if (!k) return; S.state.alloc_mix = { ...(S.state.alloc_mix || {}), [k]: e.target.value.trim() || ALLOC_DEF[k].mix }; save(false); allocDirty = true; runAlloc(); });
    $("#eventTable").addEventListener("input", onEventEdit);
    $("#evTiles").addEventListener("click", (e) => { const t = e.target.closest("[data-evt]"); if (!t) return; const ev = S.state.events[+t.dataset.evt]; ev.on = !ev.on; save(); renderEvents(); if (curAna() === "events") runForecast(); });
    segClick("#fcView", drawFcChart);
    $("#beyora").addEventListener("click", onBeyora);
    $("#beyora").addEventListener("error", (e) => { if (e.target.tagName === "IMG") e.target.classList.add("broken"); }, true); // 열리지 않는 이미지 주소는 숨긴다
    $("#bvNew").onclick = () => { bvS.view = "edit"; bvS.id = null; renderBeyora(); };
    segClick("#xfNav", () => { renderEvTiles(); renderXf(); });
    segClick("#fcTk", drawFcChart);
    $("#eventTable").addEventListener("change", onEventEdit);
    $("#eventTable").addEventListener("click", (e) => { const d = e.target.closest("[data-del]"); if (d && armed(d)) { S.state.events.splice(+d.dataset.del, 1); save(); renderEvents(); } });
    $("#btnAddEvent").onclick = () => {
      const c = curSub().cats[0], fk = CAT_FACTOR[c];
      S.state.events.push({ id: "e" + Date.now(), on: true, cat: c, factor: fk || "none", date: Model.addMonths(today(), 1), target: c === "fx" ? "FX" : fk ? "ALL" : S.state.holdings[0]?.ticker || "ALL", kind: "기타", repeat: "none", prob: 100, mean: 0, sd: fk === "rate" ? 20 : 5, vol_mult: 1, vol_days: 0, note: "" });
      save(); renderEvents();
    };
    $("#scenBox").addEventListener("click", (e) => { const b = e.target.closest("button[data-s]"); if (!b || b.disabled) return; S.state.model.scenario = b.dataset.s; save(false); viewChanged(true); }); // 누르면 아래 공통 설정으로 다시 계산
    const optDirty = () => { const st = $("#fcStatus"); if (lastForecast && !/다시 계산/.test(st.textContent)) st.textContent += " · 설정이 바뀜, 시나리오를 눌러 다시 계산"; };
    $("#trust").oninput = (e) => { S.state.model.trust = +e.target.value; S.state.model.scenario = "blend"; $("#trustVal").textContent = e.target.value + "%"; viewChanged(false); };
    $("#trust").onchange = () => { save(false); viewChanged(true); };
    $("#wiMonthly").oninput = (e) => { WI.monthly = +e.target.value * 1e4; viewChanged(false); };
    $("#wiSell").oninput = (e) => { WI.sell = +e.target.value; viewChanged(false); };
    $("#wiMonthly").onchange = $("#wiSell").onchange = () => viewChanged(true);
    $("#wiReset").onclick = () => { WI.monthly = null; WI.sell = 0; viewChanged(true); };
    $("#fcMarks").onchange = drawFcChart;
    $("#nPaths").onchange = (e) => { S.state.model.n_paths = +e.target.value; save(); optDirty(); };
    $("#rebalance").onchange = (e) => { S.state.model.rebalance_yearly = e.target.checked; save(); optDirty(); };
    $("#modelForm").addEventListener("change", onModelEdit);
    $("#btnResetModel").onclick = (e) => { if (armed(e.target)) { S.state.model = { ...DEFAULT_MODEL }; save(); renderSettings(); } };
    $("#optAi").checked = S.state.ui.ai_auto !== false;
    $("#optAi").onchange = (e) => { S.state.ui.ai_auto = e.target.checked; save(false); };
    ["#tab-analysis", "#tab-dash"].forEach((t) => $(t).addEventListener("click", (e) => { const b2 = e.target.closest("[data-aire]"); if (b2) aiAuto(b2.dataset.aire, true); const b3 = e.target.closest("[data-puter]"); if (b3) aiAuto(b3.dataset.puter, true, true); }));
    $("#optManual").checked = !!S.state.ui.manual_price;
    $("#optManual").onchange = (e) => { S.state.ui.manual_price = e.target.checked; save(); renderAll(); };
    $("#btnExport").onclick = () => {
      const a = document.createElement("a");
      a.href = URL.createObjectURL(new Blob([JSON.stringify({ ...S.state, beyora: bvExport() }, null, 1)], { type: "application/json" }));
      a.download = `자산입력-${today()}.json`; a.click(); markBackup();
    };
    $("#tradeTable").addEventListener("change", (e) => { if (e.target.dataset.lot) onTradeDate(e); });
    $("#tradeTable").addEventListener("click", (e) => { const b = e.target.closest("[data-dl]"); if (b) delTrade(+b.dataset.dl, b.dataset.dt); else if (e.target.id === "tradeClear") clearTrades(); });
    $("#btnXfer").onclick = xferShow;
    $("#btnShare").onclick = () => { const b = $("#shareBox"); b.style.display = b.style.display === "none" ? "block" : "none"; if (b.style.display === "block") drawShare(); };
    $("#shareHide").onchange = drawShare;
    $("#shareSave").onclick = async () => { const a = document.createElement("a"); a.href = URL.createObjectURL(await shareBlob()); a.download = `naeilo-${today()}.png`; a.click(); };
    $("#shareSend").onclick = async () => { const f = new File([await shareBlob()], `naeilo-${today()}.png`, { type: "image/png" }); if (navigator.canShare && navigator.canShare({ files: [f] })) { try { await navigator.share({ files: [f], title: "naeilo" }); } catch (e) { /* 취소 */ } } else $("#shareSave").click(); };
    $("#btnPush").onclick = pushToggle;
    $("#btnPushTest").onclick = async () => { const sub = await pushSub(); if (sub) fetch(pushBase() + "/push/test", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ sub: sub.toJSON() }) }).then(() => toast("시험 알림을 보냈습니다")); };
    $("#xferCopy").onclick = async () => { try { await navigator.clipboard.writeText($("#xferLink").value); toast("복사했습니다"); } catch (e) { $("#xferLink").select(); } };
    $("#xferShare").onclick = async () => { if (navigator.share) { try { await navigator.share({ title: "naeilo 입력값", url: $("#xferLink").value }); } catch (e) { /* 취소 */ } } else { $("#xferCopy").click(); } };
    $("#fileImport").onchange = async (e) => {
      const f = e.target.files[0]; if (!f) return;
      try { const j = JSON.parse(await f.text()); bvMerge(j.beyora); delete j.beyora; S.state = normalize(j); save(); renderAll(); toast("불러왔습니다"); } catch (err) { alert("파일을 읽지 못했습니다: " + err.message); }
    };
    let rz; window.addEventListener("resize", () => { clearTimeout(rz); rz = setTimeout(() => { const t = $("#tabs .on").dataset.tab; if (t === "dash") renderDash(); if (t === "analysis") { if (curAna() === "strategy") renderStockPrices(); if (curAna() === "alloc") renderAllocChart(); if (curAna() === "fx" && !lastForecast) renderFx(); else if (lastForecast) renderForecast(); } }, 200); });
  }

  // ------------------------------------------------------------ 다른 기기로 옮기기 (링크·QR, 서버 저장 없음)
  const b64u = (u8) => btoa(String.fromCharCode(...u8)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
  const unb64u = (s2) => Uint8Array.from(atob(s2.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0));
  async function zip(u8, how) {
    if (typeof CompressionStream === "undefined") return null;
    const st = new Blob([u8]).stream().pipeThrough(how === "d" ? new DecompressionStream("deflate-raw") : new CompressionStream("deflate-raw"));
    return new Uint8Array(await new Response(st).arrayBuffer());
  }
  async function xferEncode() {
    const st = S.state, j = { v: 1, h: st.holdings.filter((h) => !h.sample && Number(h.shares) > 0).map((h) => [h.ticker, Number(h.shares), Number(h.avg_cost) || 0]), g: [st.goal.amount, st.goal.date, st.goal.start_date, Number(st.goal.monthly_contribution) || 0], m: [st.model.scenario, st.model.trust], l: (st.lots || []).slice(-30).map((x) => [x.d, Object.entries(x.h)]) };
    const raw = new TextEncoder().encode(JSON.stringify(j)), z = await zip(raw);
    return z ? "z" + b64u(z) : "j" + b64u(raw);
  }
  async function xferDecode(code) {
    const u8 = unb64u(code.slice(1)), raw = code[0] === "z" ? await zip(u8, "d") : u8;
    return JSON.parse(new TextDecoder().decode(raw));
  }
  async function xferShow() {
    const box = $("#xferBox"); box.style.display = "block";
    const url = location.origin + location.pathname + "#d=" + (await xferEncode());
    $("#xferLink").value = url; markBackup();
    const draw = () => { $("#xferQr").innerHTML = ""; try { new window.QRCode($("#xferQr"), { text: url, width: 200, height: 200, correctLevel: window.QRCode.CorrectLevel.L }); } catch (e) { $("#xferQr").textContent = "QR을 만들지 못했습니다. 링크를 복사해 보내 주세요."; } };
    if (window.QRCode) draw();
    else { const sc = document.createElement("script"); sc.src = "https://cdnjs.cloudflare.com/ajax/libs/qrcodejs/1.0.0/qrcode.min.js"; sc.onload = draw; sc.onerror = () => ($("#xferQr").textContent = "QR 도구를 불러오지 못했습니다. 링크를 복사해 보내 주세요."); document.head.appendChild(sc); }
  }
  async function xferCheck() {
    const m = location.hash.match(/^#d=([A-Za-z0-9_-]+)/); if (!m) return;
    let j; try { j = await xferDecode(m[1]); } catch (e) { toast("옮기기 링크를 읽지 못했습니다"); return; }
    showTab("dash"); const box = $("#xferIn"); box.style.display = "block";
    box.innerHTML = `<b>다른 기기에서 보낸 입력값</b> <span class="small">종목 ${j.h.length}개 (${j.h.map((x) => esc(x[0])).join(", ")}), 목표 ${krw(j.g[0])}원. 지금 이 기기의 입력값을 바꿀까요?</span> <button class="primary" id="xferYes">불러오기</button> <button id="xferNo">무시</button>`;
    const done = () => { box.style.display = "none"; window.history.replaceState(null, "", location.pathname + location.search); };
    $("#xferNo").onclick = done;
    $("#xferYes").onclick = () => {
      const st = S.state; endSample(false);
      st.holdings = j.h.map(([t, sh, avg]) => ({ ticker: t, shares: sh, price: null, avg_cost: avg || null, note: "" }));
      j.h.forEach(([t]) => addEarnEvent(t));
      st.goal = { ...st.goal, amount: j.g[0], date: j.g[1], start_date: j.g[2] || st.goal.start_date, monthly_contribution: j.g[3] || 0 };
      if (j.l) st.lots = j.l.map(([d, h]) => ({ d, h: Object.fromEntries(h) }));
      if (j.m) { st.model.scenario = j.m[0] || st.model.scenario; st.model.trust = j.m[1] ?? st.model.trust; }
      save(); renderAll(); done(); toast("불러왔습니다");
      const miss = missingTickers(); if (miss.length && MODE === "static") browserCollect(miss);
    };
  }

  // ------------------------------------------------------------ 알림 (웹 푸시)
  const pushBase = () => (S.config && S.config.push ? String(S.config.push).replace(/\/$/, "") : "");
  const isIOS = () => /iPhone|iPad|iPod/.test(navigator.userAgent), standalone = () => matchMedia("(display-mode: standalone)").matches || navigator.standalone;
  function pushPayload(sub) {
    const { rows } = valuation(), w = {}; rows.forEach((r) => { if (r.w > 0) w[r.h.ticker] = +r.w.toFixed(4); });
    const held = new Set(Object.keys(w)), until = Model.addMonths(today(), 3), ev = [];
    S.state.events.filter((e) => e.on && held.has(e.target) && /실적|인도량|보호예수/.test(e.kind || "")).forEach((e) => Model.occurrences(e, today(), until).forEach((d) => ev.push({ d, t: e.target, k: e.kind })));
    return { sub, w, ev: ev.slice(0, 60) };
  }
  async function pushSub() { const reg = await navigator.serviceWorker?.getRegistration(); return reg ? reg.pushManager.getSubscription() : null; }
  async function pushRender() {
    const btn = $("#btnPush"), note = $("#pushNote"); if (!btn) return;
    if (!pushBase()) { btn.disabled = true; note.textContent = "알림 서버가 아직 준비되지 않았습니다 (개발자 설정)."; return; }
    if (!("serviceWorker" in navigator) || !("PushManager" in window)) { btn.disabled = true; note.textContent = isIOS() && !standalone() ? "아이폰은 공유 → 홈 화면에 추가한 앱에서 켤 수 있습니다." : "이 브라우저는 알림을 지원하지 않습니다."; return; }
    const sub = await pushSub().catch(() => null);
    btn.disabled = false; btn.textContent = sub ? "알림 끄기" : "알림 켜기"; $("#btnPushTest").style.display = sub ? "" : "none";
    note.textContent = sub ? "켜져 있습니다." : Notification.permission === "denied" ? "브라우저에서 이 사이트 알림이 막혀 있습니다." : "";
  }
  async function pushToggle() {
    const base = pushBase(), note = $("#pushNote");
    try {
      let sub = await pushSub();
      if (sub) { await fetch(base + "/push/unsub", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ endpoint: sub.endpoint }) }).catch(() => {}); await sub.unsubscribe(); toast("알림을 껐습니다"); return pushRender(); }
      if ((await Notification.requestPermission()) !== "granted") { note.textContent = "알림 권한이 필요합니다."; return; }
      const reg = await navigator.serviceWorker.register("sw.js"); await navigator.serviceWorker.ready;
      const { key } = await (await fetch(base + "/push/key")).json();
      sub = await reg.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: unb64u(key) });
      const r = await fetch(base + "/push/sub", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(pushPayload(sub.toJSON())) });
      if (!r.ok) throw new Error((await r.json().catch(() => ({}))).error || r.status);
      try { localStorage.setItem("naeilo-push-sig", fcSig()); } catch (e) { /* 무시 */ }
      toast("알림을 켰습니다");
    } catch (e) { note.textContent = "알림을 켜지 못했습니다: " + e.message; }
    pushRender();
  }
  // 종목·일정이 바뀌면 서버의 비중·일정도 새로 (켜 둔 경우만)
  async function pushSync() {
    if (!pushBase()) return; let sig = ""; try { sig = localStorage.getItem("naeilo-push-sig") || ""; } catch (e) { /* 무시 */ }
    if (sig === fcSig()) return;
    const sub = await pushSub().catch(() => null); if (!sub) return;
    fetch(pushBase() + "/push/sub", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(pushPayload(sub.toJSON())) }).then((r) => { if (r.ok) try { localStorage.setItem("naeilo-push-sig", fcSig()); } catch (e) { /* 무시 */ } }).catch(() => {});
  }

  // ------------------------------------------------------------ 공유 이미지 (금액 숨김 기본)
  function drawShare() {
    const cv = $("#shareCv"), x = cv.getContext("2d"), W = 1080, hide = $("#shareHide").checked;
    const { total } = valuation(), g = S.state.goal, H = history(), k = H.dates.length - 1, my = myReturn();
    const ret = (n) => { if (k < n && k >= n * 0.97) n = k; return k - n >= 0 && H.index[k - n] ? H.index[k] / H.index[k - n] - 1 : null; };
    const F = "-apple-system, 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif", up = "#e5383b", dn = "#2f6fed", fg = "#1d2330", mu = "#6b7385";
    const col = (v) => (v == null ? mu : v >= 0 ? up : dn), sp = (v) => (v == null ? "-" : spct(v));
    x.fillStyle = "#f6f7f9"; x.fillRect(0, 0, W, W);
    x.fillStyle = "#fff"; x.beginPath(); x.roundRect(60, 60, W - 120, W - 120, 36); x.fill();
    x.fillStyle = "#2f6fed"; x.font = `800 54px ${F}`; x.fillText("naeilo", 110, 160);
    x.fillStyle = mu; x.font = `400 32px ${F}`; x.textAlign = "right"; x.fillText(today(), W - 110, 158); x.textAlign = "left";
    const prog = g.amount ? total / g.amount : 0;
    x.fillStyle = mu; x.font = `500 36px ${F}`; x.fillText(hide ? "목표까지" : `목표 ${krw(g.amount)}원까지`, 110, 270);
    x.fillStyle = fg; x.font = `800 132px ${F}`; x.fillText(pct(prog, 1), 104, 400);
    x.fillStyle = "#e3e6ec"; x.beginPath(); x.roundRect(110, 440, W - 220, 26, 13); x.fill();
    x.fillStyle = "#2f6fed"; x.beginPath(); x.roundRect(110, 440, Math.max(26, (W - 220) * Math.min(1, prog)), 26, 13); x.fill();
    const cells = [["지난주", ret(5)], ["지난 1년", ret(252)], [my ? "내 수익률" : "3년", my ? my.r : ret(756)]];
    cells.forEach(([a, v], i) => { const cx = 110 + i * 300; x.fillStyle = mu; x.font = `500 32px ${F}`; x.fillText(a, cx, 540); x.fillStyle = col(v); x.font = `700 56px ${F}`; x.fillText(sp(v), cx, 610); });
    const pg = lastForecast && lastForecast.withEv ? lastForecast.withEv.p_goal : null;
    x.fillStyle = mu; x.font = `500 32px ${F}`; x.fillText(pg != null ? `목표일 ${g.date} · 달성 확률 ${pct(pg, 0)}` : `목표일 ${g.date}`, 110, 680);
    // 지난 1년 흐름 (축 없음)
    const ys = H.total.slice(Math.max(0, k - 252)).filter((v) => v > 0);
    if (ys.length > 2) {
      const lo = Math.min(...ys), hi = Math.max(...ys), X0 = 110, Y0 = 720, CW = W - 220, CH = 190;
      x.strokeStyle = "#2f6fed"; x.lineWidth = 5; x.lineJoin = "round"; x.beginPath();
      ys.forEach((v, i) => { const px = X0 + (i / (ys.length - 1)) * CW, py = Y0 + CH - ((v - lo) / (hi - lo || 1)) * CH; i ? x.lineTo(px, py) : x.moveTo(px, py); }); x.stroke();
      if (!hide) { x.fillStyle = mu; x.font = `400 28px ${F}`; x.textAlign = "right"; x.fillText(`자산 ${krw(total)}원`, W - 110, Y0 - 8); x.textAlign = "left"; }
    }
    x.fillStyle = mu; x.font = `400 28px ${F}`; x.textAlign = "center"; x.fillText("See Tomorrow, Today. · naeilo.com", W / 2, W - 95); x.textAlign = "left";
  }
  const shareBlob = () => new Promise((r) => $("#shareCv").toBlob(r, "image/png"));

  async function init() {
    try { await reload(); }
    catch (e) { document.body.innerHTML = `<div class="card" style="margin:40px auto;max-width:640px"><h2>데이터를 불러오지 못했습니다</h2><p>내 PC에서 쓸 때는 <b>실행 파일</b>(Windows: <code>실행-Windows.bat</code>, Mac: <code>실행-Mac.command</code>)로 열어야 합니다. 웹 버전은 GitHub Actions의 첫 수집이 끝난 뒤 열립니다.</p><p class="muted small">${esc(e.message)}</p></div>`; return; }
    if (S.purged || (!S.state.sample && !(S.state.lots || []).length && S.state.holdings.some((h) => Number(h.shares) > 0))) save(false); // 진행 기록 첫 줄
    bind(); renderAll(); foldHold();
    setAuto(S.state.ui.auto_refresh_min || 0);
    let tab = "dash"; try { tab = localStorage.getItem("tab") || "dash"; const a = localStorage.getItem("ana"); if (a && $(`#anaNav button[data-a="${a}"]`)) $$("#anaNav button").forEach((b) => b.classList.toggle("on", b.dataset.a === a)); } catch (e) { /* 무시 */ }
    showTab(tab);
    xferCheck();
    if ("serviceWorker" in navigator && location.protocol === "https:") navigator.serviceWorker.register("sw.js").then(() => { pushRender(); pushSync(); }).catch(() => {});
    else pushRender();
    if (MODE === "static") {
      $("#btnQuotes").style.display = "none";
      $("#modeNote").innerHTML = `시세는 평일 30분마다 자동 수집, '시세 수집'을 누르면 바로 받음. 입력값은 <b>이 브라우저에만</b> 저장.`;
      $("#modeNote").style.display = "block";
      logLine(`웹 데이터 수집 시각: ${S.dataUpdated ? new Date(S.dataUpdated).toLocaleString() : "-"}`);
      if (S.firstVisit) logLine("처음 여셨습니다. 보유 종목의 수량을 넣어 주세요. 저장해 둔 파일이 있으면 아래 '입력값 불러오기'.", false);
      const miss = missingTickers();
      if (miss.length) {
        if (GH && ghToken()) ghCollect(miss);
        else browserCollect(miss);
      }
      return;
    }
    const newest = Object.values(S.quotes).reduce((m, q) => Math.max(m, q.fetched || 0), 0);
    logLine(newest ? `저장된 시세를 불러왔습니다 (최근 수집 ${new Date(newest * 1000).toLocaleString()}). 새 시세는 '시세 수집'을 누르세요.` : "저장된 시세가 없습니다. '시세 수집'을 눌러 주세요.");
  }
  init();
})();
