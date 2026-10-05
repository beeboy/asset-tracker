// 화면 로직: 입력 편집, 저장, 시세 수집 요청, 화면 그리기.
(function () {
  "use strict";
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => [...document.querySelectorAll(s)];
  const C = ["var(--c1)", "var(--c2)", "var(--c3)", "var(--c4)", "var(--c5)", "var(--c6)", "var(--c7)"];
  const DEFAULT_MODEL = {
    scenario: "base", n_paths: 3000, seed: 20261004, history_years: 3, prior_mu: 10, prior_tau: 15, conservative_mu: 4,
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
    "OPEC+ 회의", "유가 급등", "금값 급등", "원자재 급등", "선거", "관세·정책", "전쟁·지정학", "기타"];
  // 외부 요인 묶음. 요인 사건(factor)은 요인 충격 × 종목별 민감도만큼 움직인다
  const CATS = [["corp", "기업 실적·공시"], ["rate", "금리·거시경제"], ["tsla", "테슬라 인도량"], ["product", "신제품·리콜"], ["oil", "유가"], ["gold", "금값"], ["cmdty", "원자재"], ["politics", "정치·선거"], ["war", "전쟁·지정학"]];
  const catName = (c) => (CATS.find(([k]) => k === c) || [, c])[1];
  const FACTORS = {
    mkt: { sym: "SPY", name: "미국 시장 (S&P500, SPY)", unit: "%", shock: -5, shockTxt: "S&P500 -5%" },
    rate: { sym: "^TNX", name: "미국 10년 국채 금리", unit: "bp", shock: 25, shockTxt: "10년 금리 +0.25%p" },
    oil: { sym: "CL=F", name: "WTI 유가", unit: "%", shock: 10, shockTxt: "유가 +10%" },
    gold: { sym: "GC=F", name: "금 선물", unit: "%", shock: 10, shockTxt: "금값 +10%" },
    cmdty: { sym: "DBC", name: "원자재 지수 (DBC)", unit: "%", shock: 10, shockTxt: "원자재 +10%" },
  };
  const FACTOR_SYMS = Object.values(FACTORS).map((f) => f.sym);
  const CAT_FACTOR = { rate: "rate", oil: "oil", gold: "gold", cmdty: "cmdty", politics: "mkt", war: "mkt" };
  const catOfKind = (k) => (/신제품|리콜/.test(k) ? "product" : /인도량/.test(k) ? "tsla" : /FOMC|CPI|고용|금리/.test(k) ? "rate" : /OPEC|유가/.test(k) ? "oil" : /금값/.test(k) ? "gold" : /원자재/.test(k) ? "cmdty" : /선거|관세/.test(k) ? "politics" : /전쟁/.test(k) ? "war" : "corp");
  // 기본 외부 요인 사건 (날짜·크기는 추정. 시장 요인은 S&P500 기준 %, 금리는 bp)
  const EXT_EVENTS = [
    { id: "x_fomc", cat: "rate", factor: "mkt", target: "ALL", kind: "FOMC 금리 결정", date: "2026-10-28", repeat: "6w", prob: 100, mean: 0, sd: 1.0, vol_mult: 1, vol_days: 0, note: "6주마다 (날짜 근사). 발표일 S&P500 ±1.0% 가정" },
    { id: "x_cpi", cat: "rate", factor: "mkt", target: "ALL", kind: "물가(CPI) 발표", date: "2026-10-14", repeat: "monthly", prob: 100, mean: 0, sd: 0.8, vol_mult: 1, vol_days: 0, note: "매달 중순 (날짜 근사). S&P500 ±0.8%" },
    { id: "x_jobs", cat: "rate", factor: "mkt", target: "ALL", kind: "고용 지표", date: "2026-11-06", repeat: "monthly", prob: 100, mean: 0, sd: 0.6, vol_mult: 1, vol_days: 0, note: "매달 첫 금요일 무렵. S&P500 ±0.6%" },
    { id: "x_rate", cat: "rate", factor: "rate", target: "ALL", kind: "금리 급등", date: "2027-03-15", repeat: "yearly", prob: 20, mean: 40, sd: 20, vol_mult: 1.2, vol_days: 20, note: "해마다 20% 확률로 10년 금리 +0.4%p (bp 단위)" },
    { id: "x_dlv", cat: "tsla", target: "TSLA", kind: "차량 인도량", date: "2027-01-04", repeat: "quarterly", prob: 100, mean: 0, sd: 4, vol_mult: 1, vol_days: 0, note: "분기 첫 달 2일 무렵 발표 (날짜 추정)" },
    { id: "x_gtc", cat: "product", target: "NVDA", kind: "신제품·행사", date: "2027-03-16", repeat: "yearly", prob: 100, mean: 0, sd: 4, vol_mult: 1, vol_days: 0, note: "GTC 신제품 발표 (날짜 추정)" },
    { id: "x_tprod", cat: "product", target: "TSLA", kind: "신제품·행사", date: "2027-06-15", repeat: "yearly", prob: 60, mean: 0, sd: 6, vol_mult: 1, vol_days: 0, note: "로보택시·옵티머스 등 공개 행사 (가정)" },
    { id: "x_recall", cat: "product", target: "TSLA", kind: "리콜", date: "2027-02-15", repeat: "yearly", prob: 40, mean: -2, sd: 3, vol_mult: 1, vol_days: 0, note: "대규모 리콜·조사 (가정)" },
    { id: "x_ship", cat: "product", target: "SPCX", kind: "신제품·행사", date: "2026-12-15", repeat: "quarterly", prob: 70, mean: 0, sd: 5, vol_mult: 1, vol_days: 0, note: "스타십 발사·신규 서비스 (가정)" },
    { id: "x_opec", cat: "oil", factor: "oil", target: "ALL", kind: "OPEC+ 회의", date: "2026-11-30", repeat: "semi", prob: 100, mean: 0, sd: 4, vol_mult: 1, vol_days: 0, note: "반년마다. 유가 ±4% (날짜 추정)" },
    { id: "x_oil", cat: "oil", factor: "oil", target: "ALL", kind: "유가 급등", date: "2027-05-17", repeat: "yearly", prob: 15, mean: 25, sd: 12, vol_mult: 1.2, vol_days: 20, note: "해마다 15% 확률로 유가 +25% (중동·감산)" },
    { id: "x_gold", cat: "gold", factor: "gold", target: "ALL", kind: "금값 급등", date: "2027-08-16", repeat: "yearly", prob: 20, mean: 10, sd: 6, vol_mult: 1, vol_days: 0, note: "해마다 20% 확률로 금값 +10% (안전자산 쏠림)" },
    { id: "x_cmdty", cat: "cmdty", factor: "cmdty", target: "ALL", kind: "원자재 급등", date: "2027-07-15", repeat: "yearly", prob: 15, mean: 12, sd: 8, vol_mult: 1, vol_days: 0, note: "해마다 15% 확률로 원자재 +12% (공급망)" },
    { id: "x_mid", cat: "politics", factor: "mkt", target: "ALL", kind: "선거", date: "2026-11-04", repeat: "none", prob: 100, mean: 0, sd: 1.5, vol_mult: 1.2, vol_days: 15, note: "미국 중간선거 결과 (11/3 투표)" },
    { id: "x_pres", cat: "politics", factor: "mkt", target: "ALL", kind: "선거", date: "2028-11-08", repeat: "none", prob: 100, mean: 0, sd: 2, vol_mult: 1.3, vol_days: 20, note: "미국 대통령 선거 결과 (11/7 투표)" },
    { id: "x_tariff", cat: "politics", factor: "mkt", target: "ALL", kind: "관세·정책", date: "2027-04-05", repeat: "yearly", prob: 20, mean: -3, sd: 3, vol_mult: 1.4, vol_days: 20, note: "해마다 20% 확률로 관세·규제 충격 S&P500 -3%" },
    { id: "x_war", cat: "war", factor: "mkt", target: "ALL", kind: "전쟁·지정학", date: "2027-09-15", repeat: "yearly", prob: 10, mean: -5, sd: 4, vol_mult: 1.6, vol_days: 30, note: "해마다 10% 확률로 전쟁·분쟁 충격 S&P500 -5% (날짜는 임의)" },
  ];
  const REPEATS = [["none", "한 번"], ["monthly", "매달"], ["6w", "6주"], ["quarterly", "분기"], ["semi", "반년"], ["yearly", "매년"]];
  const repName = (r) => (REPEATS.find(([k]) => k === r) || [, "한 번"])[1];
  // 과거 반응을 볼 날짜 (공개 기록 기준, 미국 장 마감 기준으로 반영된 날)
  const REF_DAYS = {
    rate: ["2023-11-01", "2023-12-13", "2024-01-31", "2024-03-20", "2024-05-01", "2024-06-12", "2024-07-31", "2024-09-18", "2024-11-07", "2024-12-18", "2025-01-29", "2025-03-19", "2025-05-07", "2025-06-18", "2025-07-30", "2025-09-17", "2025-10-29", "2025-12-10", "2026-01-28", "2026-03-18", "2026-04-29", "2026-06-17", "2026-07-29", "2026-09-16"].map((d) => [d, "FOMC 결정"]),
    product: [["2023-11-30", "테슬라 사이버트럭 첫 인도 행사"], ["2023-12-13", "테슬라 오토파일럿 200만 대 리콜"], ["2024-03-19", "엔비디아 GTC 2024 (블랙웰) 다음 날"], ["2024-10-11", "테슬라 로보택시 공개 다음 날"], ["2025-03-18", "엔비디아 GTC 2025 기조연설"]],
    politics: [["2024-11-06", "미국 대선 결과"], ["2025-04-03", "상호관세 발표 다음 날"], ["2025-04-04", "관세 충격 이틀째"], ["2025-04-09", "관세 90일 유예"]],
    war: [["2023-10-09", "하마스 이스라엘 공격 뒤 첫 거래일"], ["2024-04-15", "이란의 이스라엘 공격 뒤 첫 거래일"], ["2024-10-01", "이란 미사일 공격"], ["2025-06-13", "이스라엘의 이란 공습"], ["2025-06-23", "미국의 이란 핵시설 공습 뒤 첫 거래일"]],
  };
  const SESS = { pre: "프리", regular: "정규", post: "애프터", close: "종가", manual: "수동" };

  let S = { state: null, prices: {}, quotes: {} };
  // 실행 방식: "local" = 내 PC 의 server.py, "static" = GitHub Pages 같은 정적 사이트 (입력은 이 브라우저에 저장)
  let MODE = "local";
  const LS_KEY = "asset-tracker-state";
  const GH = (() => {
    const m = location.hostname.match(/^([^.]+)\.github\.io$/), repo = location.pathname.split("/").filter(Boolean)[0];
    return m && repo ? { owner: m[1], repo, actions: `https://github.com/${m[1]}/${repo}/actions/workflows/collect.yml` } : null;
  })();
  let saveTimer = null, autoTimer = null, lastForecast = null, fcDirty = true, lastAlloc = null, allocDirty = true;
  let fcCache = {}; // 시나리오별 전망 (대시보드 미래 표시·AI 용). 입력이나 시세가 바뀌면 비운다
  const markDirty = () => { fcDirty = allocDirty = true; fcCache = {}; };
  const SCEN = { base: "기준", conservative: "보수", history: "과거 반복", smooth: "과거 스무딩 추종", trend: "추세 (칼만·EMA) 추종" };
  const scenName = (k) => SCEN[k] || k;

  // ------------------------------------------------------------ 형식
  const nf = (v, d = 0) => (v == null || !isFinite(v) ? "-" : Number(v).toLocaleString("ko-KR", { minimumFractionDigits: d, maximumFractionDigits: d }));
  const pct = (v, d = 1) => (v == null || !isFinite(v) ? "-" : (v * 100).toFixed(d) + "%");
  const spct = (v, d = 1) => (v == null || !isFinite(v) ? "-" : (v >= 0 ? "+" : "") + (v * 100).toFixed(d) + "%");
  const cls = (v) => (v == null ? "" : v >= 0 ? "good" : "bad");
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
    }
  }
  function normalize(st) {
    st.holdings = st.holdings || [];
    st.holdings.forEach((h) => { if (h.note === "수량을 입력하세요") h.note = ""; }); // 예전 기본 문구 정리
    st.goal = Object.assign({ amount: 1e9, date: Model.addMonths(today(), 36), start_date: today(), monthly_contribution: 0 }, st.goal || {});
    st.events = st.events || [];
    // 외부 요인 기본 사건을 한 번 넣는다 (이미 지운 사건은 다시 넣지 않도록 버전으로 표시)
    if ((st.ext_ver || 0) < 1) { const have = new Set(st.events.map((e) => e.id)); EXT_EVENTS.forEach((e) => { if (!have.has(e.id)) st.events.push({ on: true, ...e }); }); st.ext_ver = 1; }
    st.events.forEach((e) => { if (!e.cat) e.cat = catOfKind(e.kind || ""); });
    st.model = Object.assign({}, DEFAULT_MODEL, st.model || {});
    st.ui = Object.assign({ auto_refresh_min: 0, manual_price: false }, st.ui || {});
    return st;
  }
  function save(dirtyForecast = true) {
    if (dirtyForecast) markDirty();
    clearTimeout(saveTimer);
    saveTimer = setTimeout(async () => {
      if (MODE === "static") {
        try { localStorage.setItem(LS_KEY, JSON.stringify(S.state)); $("#footer").textContent = "이 브라우저에 저장됨 " + new Date().toLocaleTimeString() + " · 다른 기기에서 쓰려면 시세 수집 아래 설정의 내보내기/불러오기"; }
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
    $("#headKpi").innerHTML = `<span>평가액 <b>${krw(total)}원</b></span><span>목표 대비 <b>${pct(prog)}</b></span>${my ? `<span>내 수익률 <b class="${cls(my.r)}">${spct(my.r)}</b></span>` : ""}`;
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
    const r = await fetch(`https://api.github.com/repos/${GH.owner}/${GH.repo}/${path}`, {
      ...opt, cache: "no-store",
      headers: { Accept: opt.raw ? "application/vnd.github.raw+json" : "application/vnd.github+json", Authorization: "Bearer " + ghToken(), "X-GitHub-Api-Version": "2022-11-28", ...(opt.body ? { "Content-Type": "application/json" } : {}) },
    });
    if (!r.ok) {
      const msg = r.status === 401 ? "토큰이 맞지 않습니다" : r.status === 403 || r.status === 404 ? "토큰 권한 부족 (Actions 읽기·쓰기 필요)" : "GitHub 오류 " + r.status;
      throw new Error(msg);
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
    } catch (e) { logLine("GitHub 수집 실행 실패: " + esc(e.message) + ". 시세 수집 아래 설정의 GitHub 연결을 확인하세요.", false, true); }
    finally { ghBusy = false; btns.forEach((b) => b && (b.disabled = false)); }
    return false;
  }
  const ghLink = (txt) => (GH ? ` <a href="${GH.actions}" target="_blank" rel="noopener">${txt}</a>` : "");
  // 토큰 없이 브라우저에서 Yahoo 시세를 받는다: 개발자가 정한 중계 주소(data/config.json) → 공개 CORS 중계 순서로 시도
  const EXTRA_KEY = "asset-tracker-extra";
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
  function renderQuotes() {
    const { rows, total } = valuation();
    const fx = fxNow("USD"), fq = S.quotes["KRW=X"];
    $("#fxLine").textContent = `원/달러 ${nf(fx, 2)}${fq?.last_time ? " (" + new Date(fq.last_time * 1000).toLocaleString() + ")" : ""}`;
    const adv = !!S.state.ui.manual_price;
    const head = `<tr><th></th><th class="l">종목</th><th>수량</th><th>매수 단가</th><th>현재가</th>${adv ? "<th>현재가 직접 입력</th>" : ""}<th>평가액 (원)</th><th>비중</th><th>전일 대비</th><th>손익</th><th class="l">이름</th></tr>`;
    const body = rows.map((r, i) => {
      const mk = r.p.src === "manual" ? curPrice({ ...r.h, price: null }).v : null;
      const gap = mk ? r.p.v / mk - 1 : 0;
      const warn = Math.abs(gap) > 0.05 ? `<br><span class="bad small">시세 ${nf(mk, 2)}와 ${spct(gap, 0)} 차이</span>` : "";
      const p = r.p, tag = p.src ? `<span class="tag ${p.src === "manual" ? "manual" : ""}">${SESS[p.src] || p.src}</span>` : "";
      const pl = r.pl != null ? `<span class="${cls(r.pl)}">${nf(r.pl, 0)} ${r.ccy} (${spct(r.plPct)})</span><br><span class="muted small">${krw(r.pl * (r.fx || 1))}원</span>` : `<span class="muted">-</span>`;
      return `<tr data-i="${i}">
        <td><button class="danger" data-del="${i}" title="이 종목 삭제">삭제</button></td>
        <td class="l"><b>${esc(r.h.ticker)}</b></td>
        <td><input data-f="shares" type="text" inputmode="decimal" placeholder="수량" value="${r.h.shares ? r.h.shares : ""}"></td>
        <td><input data-f="avg_cost" type="text" inputmode="decimal" placeholder="선택" value="${r.h.avg_cost ?? ""}"></td>
        <td>${p.v != null ? nf(p.v, 2) + " <span class='muted small'>" + r.ccy + "</span>" : "-"}${tag}</td>
        ${adv ? `<td><input data-f="price" type="text" inputmode="decimal" placeholder="자동" value="${r.h.price ?? ""}">${warn}</td>` : ""}
        <td><b>${nf(r.valueKrw)}</b></td><td>${pct(r.w)}</td>
        <td class="${cls(r.dayChg)}">${spct(r.dayChg, 2)}</td><td>${pl}</td>
        <td class="l small muted">${esc(r.name).slice(0, 28)}</td></tr>`;
    }).join("");
    const plTot = rows.filter((r) => r.pl != null).reduce((s, r) => s + r.pl * (r.fx || 1), 0);
    const foot = `<tr><td></td><td class="l"><b>합계</b></td><td></td><td></td><td></td>${adv ? "<td></td>" : ""}<td><b>${nf(total)}</b></td><td>100%</td><td></td><td>${rows.some((r) => r.pl != null) ? `<span class="${cls(plTot)}">${krw(plTot)}원</span>` : ""}</td><td></td></tr>`;
    $("#holdTable").innerHTML = head + body + foot;
  }
  function onHoldEdit(e) {
    const tr = e.target.closest("tr[data-i]"); if (!tr) return;
    const h = S.state.holdings[+tr.dataset.i], f = e.target.dataset.f; if (!f) return;
    if (f === "note") h.note = e.target.value;
    else { const v = parseDec(e.target.value); if (v === undefined) return; h[f] = v; }
    save(f !== "note"); renderHeader();
    if (e.type === "change") renderQuotes();
  }
  async function addHolding() {
    const t = $("#addTicker").value.trim().toUpperCase(), sh = parseDec($("#addShares").value), avg = parseDec($("#addAvg").value);
    if (!t) return toast("티커를 넣어 주세요");
    if (!(sh > 0)) return toast("수량을 넣어 주세요");
    const ex = S.state.holdings.find((h) => h.ticker === t);
    if (ex) {
      const old = Number(ex.shares) || 0;
      // 매수 단가를 같이 넣으면 기존 단가와 수량 가중 평균 (기존 단가가 없으면 새 단가)
      if (avg > 0) ex.avg_cost = Number(ex.avg_cost) > 0 && old > 0 ? (Number(ex.avg_cost) * old + avg * sh) / (old + sh) : avg;
      ex.shares = old + sh; toast(`${t} 수량을 더했습니다`);
    }
    else S.state.holdings.push({ ticker: t, shares: sh, price: null, avg_cost: avg || null, note: "" });
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
    S.state.holdings.splice(i, 1);
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
  const basisScen = (b) => (b === "trend" ? "trend" : b === "smooth" ? "smooth" : S.state.model.scenario);
  const BASIS = { model: "모형 전망", trend: "추세 반영 (칼만·EMA)", smooth: "스무딩 (3년)" };
  function simCommon(b, scen) {
    const m = S.state.model, g = S.state.goal;
    return { holdings: b.holdings, scenario: scen, nPaths: Number(m.n_paths), seed: Number(m.seed) || 1, goal: g.amount,
      monthly: Number(g.monthly_contribution) || 0, rebalance: !!m.rebalance_yearly, dof: m.t_dof, fxOf, usdKrw0: fxNow("USD") };
  }
  // 계산해 둔 전망만 돌려준다 (없으면 null)
  function fcReady(scen) {
    if (lastForecast && !fcDirty && lastForecast.scen === scen) return { R: lastForecast.withEv, model: lastForecast.b.model };
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
  function renderDash() {
    const g = S.state.goal, { total } = valuation(), yrs = yearsBetween(today(), g.date);
    $("#dashEmpty").style.display = total > 0 ? "none" : "block";
    const need = g.amount - total, req = yrs > 0 && total > 0 ? (g.amount / total) ** (1 / yrs) - 1 : null;
    const H = history();
    const ret = (n) => { const k = H.index.length - 1; if (k < n && k >= n * 0.97) n = k; return k - n >= 0 ? H.index[k] / H.index[k - n] - 1 : null; };
    const pastCagr = H.index.length > 30 ? H.index[H.index.length - 1] ** (252 / (H.index.length - 1)) - 1 : null;
    $("#goalKpis").innerHTML = [
      ["현재 평가액", krw(total) + "원", nf(total) + "원"],
      ["목표 대비", pct(total / g.amount), `<div class="bar"><i style="width:${Math.min(100, (total / g.amount) * 100)}%"></i></div>`],
      ["남은 금액", krw(Math.max(0, need)) + "원", `목표 ${krw(g.amount)}원`],
      ["남은 기간", yrs > 0 ? yrs.toFixed(1) + "년" : "지남", g.date],
      ["필요 연평균 수익률", req != null ? pct(req) : "-", "적립 없이 지금 자산만으로"],
      ["과거 연평균 (원화)", pct(pastCagr), `${H.dates[0] || "-"} 이후, 편입 효과 제외`],
    ].map(([k, v, s]) => `<div class="kpi"><div class="k">${k}</div><div class="v">${v}</div><div class="s">${s}</div></div>`).join("");

    const rsel = $("#histRange .on")?.dataset.r || "252", step = $("#histStep .on")?.dataset.s || "d", mode = $("#histMode .on")?.dataset.m || "total";
    const inUsd = $("#histCcy .on")?.dataset.c === "usd", basis = $("#histBasis .on")?.dataset.b || "model";
    const fxNowUsd = fxNow("USD") || 1, conv = (v, i) => (v == null ? null : inUsd ? v / H.usdK[i] : v), money = inUsd ? usd : krwAxis;
    const future = rsel === "future", n = future ? 780 : +rsel;
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
        notes.push(`오른쪽은 <b>${BASIS[basis]}</b>${basis === "model" ? ` (${scenName(scen)} 시나리오)` : " 전망"}입니다. 환율·외부 요인을 넣은 ${nf(S.state.model.n_paths)}경로 몬테카를로이며 ${mode === "total" ? "진한 띠 25~75%, 옅은 띠 5~95%, 점선은 중앙값입니다." : mode === "each" ? "점선은 종목별 중앙값, 띠는 25~75%입니다." : "쌓은 띠는 종목별 중앙값입니다(합계 중앙값과 조금 다를 수 있음)."}`);
        if (basis === "trend") notes.push("추세 반영: 종목마다 칼만 기울기와 EMA50·EMA200 기울기의 평균 성장률이 목표일까지 이어진다고 봅니다.");
        if (basis === "smooth") notes.push("스무딩: 종목마다 과거 3년 로그가격에 맞춘 추세선의 성장률이 이어진다고 봅니다.");
        if (Number(g.monthly_contribution) > 0) notes.push(`월 적립 ${krw(Number(g.monthly_contribution))}원 포함.`);
      } else if (total > 0) notes.push("전망을 계산하는 중입니다…");
      if (mode === "total" && basis !== "model") {
        const pf = pastFit(H, basis);
        opt.series.push({ name: basis === "trend" ? "칼만 추세 (과거)" : "스무딩 (과거)", y: ix.map((i) => conv(pf[i], i)), color: "var(--c7)", width: 1.4, dash: "4 3" });
      }
      const md = []; for (let k = 0; k <= 36 && Model.addMonths(today(), k) <= g.date; k++) md.push(Model.addMonths(today(), k));
      if (md[md.length - 1] !== g.date) md.push(g.date);
      if (V0 > 0 && mode !== "each") opt.series.push({ name: "필요 경로", x: [last, ...md], y: [conv(H.total[H.total.length - 1], H.total.length - 1), ...md.map((d) => (V0 * (g.amount / V0) ** (yearsBetween(today(), d) / Math.max(0.01, yearsBetween(today(), g.date)))) / (inUsd ? fxNowUsd : 1))], color: "var(--accent2)", dash: "5 4", width: 1.3 });
    } else {
      if (mode !== "each" && goalV <= maxV * 1.05) opt.hlines.push({ y: goalV, label: "목표" });
      notes.push("현재 보유 수량을 과거에 그대로 적용한 값입니다(매매 이력 미반영). 늦게 상장한 종목은 상장일부터 합계에 들어갑니다.");
    }
    notes.push(inUsd ? "달러 환산은 그날 원/달러 환율, 미래는 환율 전망 경로를 씁니다." : "원화 환산은 그날 환율을 씁니다.");
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
    const need = [...new Set([S.state.model.scenario, "trend", "smooth"])].filter((k) => !fcReady(k));
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
      Charts.lineChart(box.querySelector(".chartbox"), { x: xs, height: 170, legend: false, yfmt: priceAxis([ys]), series: [{ name: h.ticker, y: ys, color: C[j % C.length], width: 1.6 }] });
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
    return base + (e.mean ? `평균 <b class="${(e.factor === "rate" || e.factor === "oil" ? -e.mean : e.mean) > 0 ? "good" : "bad"}">${e.mean > 0 ? "+" : ""}${e.mean}${u}</b> ` : "방향 중립 ") + `±${e.sd}${u}`;
  };
  function renderEvents() {
    const opts = [...S.state.holdings.map((h) => h.ticker), "ALL", "FX"];
    const lab = { ALL: "전체", FX: "환율" };
    const fopts = [["none", "직접 (종목)"], ...Object.entries(FACTORS).map(([k, f]) => [k, f.name.split(" (")[0]])];
    const head = `<tr><th>사용</th><th class="l">묶음</th><th>날짜</th><th class="l">대상</th><th class="l">요인</th><th class="l">종류</th><th class="l">반복</th><th>발생 확률 %</th><th>평균 영향</th><th>불확실성 ±</th><th>변동성 배수</th><th>지속 (거래일)</th><th class="l">메모</th><th></th></tr>`;
    const body = S.state.events.map((e, i) => `<tr data-i="${i}">
      <td><input type="checkbox" data-f="on" ${e.on ? "checked" : ""}></td>
      <td class="l"><select data-f="cat">${CATS.map(([k, n]) => `<option value="${k}" ${k === e.cat ? "selected" : ""}>${n}</option>`).join("")}</select></td>
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
  const curCat = () => $("#xfNav .on")?.dataset.c || "all";
  function renderEvTiles() {
    const cat = curCat();
    const items = S.state.events.map((e, i) => ({ e, i, ...nextOcc(e) })).filter(({ e }) => cat === "all" || e.cat === cat).sort((a, b) => ((a.next || "9") < (b.next || "9") ? -1 : 1));
    const first = items.find((x) => x.e.on && x.next);
    $("#evTiles").innerHTML = items.map(({ e, i, next, n }) => {
      const earn = /실적/.test(e.kind), q = earn && next ? quarterOf(next) : null;
      const eff = effTxt(e) + (Number(e.prob) < 100 ? ` · 확률 ${e.prob}%` : "") + (e.vol_mult && e.vol_mult !== 1 && e.vol_days ? ` · 변동성 ${e.vol_mult}배 ${e.vol_days}일` : "");
      const when = next ? `${next}${q ? ` (${q.label})` : ""}${e.repeat && e.repeat !== "none" ? ` · ${repName(e.repeat)} · 남은 ${n}회` : ""}` : "지난 사건";
      return `<button type="button" class="evtile ${e.repeat && e.repeat !== "none" ? "earn" : "once"} ${e.on ? "" : "off"} ${next ? "" : "past"} ${first && first.i === i ? "next" : ""}" data-evt="${i}" title="누르면 ${e.on ? "끄기" : "켜기"}">
        <div class="top"><span class="tk">${esc(tgtLab(e))}</span><span class="kd">${esc(e.kind)}</span></div>
        <div class="dt">${when}</div><div class="ef">${eff}</div>${e.note ? `<div class="nt">${esc(e.note)}</div>` : ""}</button>`;
    }).join("") || `<p class="muted">이 묶음에 사건이 없습니다. 아래 '사건 직접 편집'에서 추가할 수 있습니다.</p>`;
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
    return REF_DAYS[cat] || [];
  }
  function renderXf() {
    const cat = curCat(), host = $("#xfAnalysis"); if (!host) return;
    if (cat === "all") { host.innerHTML = ""; host.style.display = "none"; return; }
    host.style.display = "block";
    const { rows, total } = valuation(), held = rows.filter((r) => r.valueKrw > 0), H = [];
    H.push(`<h2>${esc(catName(cat))} 분석</h2>`);
    const fk = CAT_FACTOR[cat], F = FACTORS[fk];
    if (F) {
      const p = S.prices[F.sym];
      if (!p) H.push(`<p class="muted small">${esc(F.name)} 시세가 아직 없습니다. 다음 자동 수집 뒤에 나옵니다.</p>`);
      else {
        const last = p.close.at(-1), at = (k) => p.close[Math.max(0, p.close.length - 1 - k)], ch = (k) => (fk === "rate" ? `${((last - at(k)) * 100) >= 0 ? "+" : ""}${nf((last - at(k)) * 100)}bp` : spct(last / at(k) - 1));
        H.push(`<p class="small"><b>${esc(F.name)}</b> ${fk === "rate" ? nf(last, 2) + "%" : nf(last, 2)} · 1개월 ${ch(21)} · 3개월 ${ch(63)} · 1년 ${ch(252)} <span class="muted">(${p.dates.at(-1)})</span></p><div id="xfChart" class="chartbox"></div>`);
        const B = factorBetas(), st = B.stat[fk] || {};
        let port = 0;
        const tr = held.map((r) => { const t = r.h.ticker, s2 = st[t]; if (!s2) return `<tr><td class="l">${esc(t)}</td><td colspan="3" class="muted">이력 부족</td></tr>`;
          const effPct = fk === "rate" ? s2.beta * F.shock : s2.beta * Math.log(1 + F.shock / 100);
          port += r.w * effPct;
          return `<tr><td class="l">${esc(t)}</td><td>${s2.corr.toFixed(2)}</td><td>${fk === "rate" ? spct(s2.beta * 10, 2) : s2.beta.toFixed(2)}</td><td class="${cls(effPct)}">${spct(Math.exp(effPct) - 1)}</td></tr>`; }).join("");
        H.push(`<div class="tablewrap"><table class="grid"><tr><th class="l">종목</th><th>상관</th><th>민감도${fk === "mkt" ? " (베타)" : fk === "rate" ? " (+10bp당, 시장 제외)" : " (시장 제외)"}</th><th>${esc(F.shockTxt)}일 때</th></tr>${tr}
          <tr><td class="l"><b>내 포트폴리오</b></td><td></td><td></td><td class="${cls(port)}"><b>${spct(Math.exp(port) - 1)}</b> (${krw(total * (Math.exp(port) - 1))}원)</td></tr></table></div>
          <p class="muted small">최근 3년 일별 변화로 구한 값입니다. ${fk === "mkt" ? "베타 1.5는 시장이 1% 움직일 때 평균 1.5% 움직인다는 뜻입니다." : "시장 전체 움직임을 뺀 뒤 이 요인만의 영향을 본 값이라, 시장과 같이 움직이는 몫은 '정치·선거·전쟁'의 시장 베타에 들어 있습니다."} ${fk === "rate" ? "금리 민감도는 10년 금리가 0.1%p(10bp) 오를 때의 수익률입니다." : ""} 이력이 짧은 종목은 기본값 쪽으로 당겼습니다.</p>`);
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
          <p class="muted small">${rr.length}번 중 최근 ${Math.min(12, rr.length)}번. 평소보다 크게 움직였다면 그만큼 전망에 사건으로 넣을 이유가 있습니다. 날짜는 공개 기록 기준이며 장 마감 뒤 발표는 다음 거래일에 반영됩니다.</p>`);
      }
    }
    if (H.length === 1) H.push(`<p class="muted small">이 묶음은 종목별 사건(실적·보호예수·규제 등)으로 반영합니다. 과거 실적 발표일 자료가 없어 반응 분석은 생략합니다.</p>`);
    host.innerHTML = H.join("");
    if (F && S.prices[F.sym] && $("#xfChart")) {
      const p = S.prices[F.sym], k = Math.max(0, p.dates.length - 756);
      Charts.lineChart($("#xfChart"), { x: p.dates.slice(k), height: 170, legend: false, yfmt: (v) => (fk === "rate" ? nf(v, 2) + "%" : nf(v, v < 100 ? 1 : 0)), series: [{ name: F.name, y: p.close.slice(k), color: "var(--c2)", width: 1.5 }] });
    }
  }
  // 요인 묶음별 영향: 같은 난수로 '사건 없음'과 '그 묶음만 켬'을 비교
  let xfEff = null, xfBusy = false;
  async function runXfEffect() {
    const host = $("#xfEffect"); if (!host || !lastForecast) return;
    if (xfEff && xfEff.fc === lastForecast) return drawXfEffect();
    if (xfBusy) return; xfBusy = true;
    const { b } = lastForecast, common = { ...simCommon(b, S.state.model.scenario) }; common.nPaths = Math.min(common.nPaths, 1500);
    const cats = CATS.map(([k]) => k).filter((c) => b.model.eventList.some((x) => (x.event.cat || "corp") === c));
    host.innerHTML = "<p class='muted small'>요인별 영향을 계산하는 중입니다…</p>";
    const res = { fc: lastForecast, none: null, all: null, by: {} };
    const run = (set) => Model.simulate(b.model, { ...common, withEvents: true, cats: set });
    await new Promise((r) => setTimeout(r, 20));
    res.none = run(new Set()); res.all = run(null);
    for (const c of cats) { await new Promise((r) => setTimeout(r, 0)); if (lastForecast !== res.fc) { xfBusy = false; return; } res.by[c] = run(new Set([c])); }
    xfEff = res; xfBusy = false; drawXfEffect();
  }
  function drawXfEffect() {
    const host = $("#xfEffect"); if (!host || !xfEff) return;
    const { none, all, by } = xfEff, md = lastForecast.b.model;
    const cnt = (c) => md.eventList.filter((x) => (x.event.cat || "corp") === c).length;
    const row = (n, R, k) => `<tr><td class="l">${n}</td><td>${k ?? ""}</td><td class="${cls(R.p_goal - none.p_goal)}">${spct(R.p_goal - none.p_goal, 0).replace("%", "%p")}</td><td class="${cls(R.terminal.p50 - none.terminal.p50)}">${R.terminal.p50 >= none.terminal.p50 ? "+" : ""}${krw(R.terminal.p50 - none.terminal.p50)}</td><td class="${cls(R.terminal.p5 - none.terminal.p5)}">${R.terminal.p5 >= none.terminal.p5 ? "+" : ""}${krw(R.terminal.p5 - none.terminal.p5)}</td></tr>`;
    host.innerHTML = `<div class="tablewrap"><table class="grid"><tr><th class="l">요인</th><th>반영 횟수</th><th>목표 확률</th><th>목표일 중앙값</th><th>나쁜 경우 5%</th></tr>
      ${Object.entries(by).map(([c, R]) => row(esc(catName(c)), R, cnt(c))).join("")}${row("<b>모두 반영</b>", all, md.eventList.length)}</table></div>
      <p class="muted small">외부 요인을 하나도 넣지 않은 전망(목표 확률 ${pct(none.p_goal, 0)}, 중앙값 ${krw(none.terminal.p50)}원) 대비 변화입니다. 같은 난수로 묶음 하나씩만 켜서 계산했고(경로 ${nf(Math.min(S.state.model.n_paths, 1500))}개), 반복 사건은 그만큼 평소 변동성을 줄여 이중 계산을 피했습니다.</p>`;
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
      const byKind = {}; list.forEach((x) => (byKind[x.event.kind] = (byKind[x.event.kind] || 0) + 1));
      $("#schedSum").textContent = "· " + Object.entries(byKind).map(([k, n]) => `${k} ${n}건`).join(", ");
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
      }).join("") + `<p class="muted small">회색 칩은 분기 실적(발표 예상 월, 직전 분기 실적), 주황 칩은 한 번 있는 사건입니다. 테두리가 진한 칩이 가장 가까운 사건입니다. 칩을 길게 누르거나 마우스를 올리면 확률·영향이 나옵니다. NVDA처럼 회계연도가 다른 회사는 회사 발표 분기 이름과 다를 수 있습니다.</p>`;
    } catch (e) { $("#eventSchedule").textContent = e.message; }
  }
  // 사건을 넣은 예상 그래프: 포트폴리오 또는 사건이 있는 종목. 요인 반영(띠·실선)과 요인 제외(점선) 비교
  function renderEvChart() {
    const host = $("#evChart"); if (!host) return;
    if (!lastForecast) { host.innerHTML = "<p class='muted'>전망을 계산하는 중입니다…</p>"; return; }
    const { b, withEv: R, noEv, hasEv } = lastForecast, md = b.model, fx = md.monthDates;
    const tks = b.holdings.map((h, i) => ({ t: h.ticker, i })).filter(({ t }) => md.eventList.some((x) => x.event.target === t || x.event.target === "ALL"));
    const views = [["port", "포트폴리오"], ...tks.map(({ t }) => [t, t])];
    let cur = $("#evView .on")?.dataset.v; if (!views.some(([v]) => v === cur)) cur = "port";
    $("#evView").innerHTML = views.map(([v, n]) => `<button data-v="${esc(v)}" class="${v === cur ? "on" : ""}">${esc(n)}</button>`).join("");
    if (!hasEv) { host.innerHTML = "<p class='muted'>켜진 사건이 없습니다. 위 타일을 눌러 켜 주세요.</p>"; $("#evNote").textContent = ""; return; }
    const g = S.state.goal;
    if (cur === "port") {
      Charts.lineChart(host, { x: fx, height: 300, yfmt: krwAxis,
        bands: [{ lo: R.bands.p5, hi: R.bands.p95, color: "var(--band)", opacity: 0.12, name: "요인 반영 5~95%" }, { lo: R.bands.p25, hi: R.bands.p75, color: "var(--band)", opacity: 0.24, name: "요인 반영 25~75%" }],
        series: [{ name: "요인 반영 중앙값", y: R.bands.p50, color: "var(--c1)", width: 2.2 }, { name: "요인 제외 중앙값", y: noEv.bands.p50, color: "var(--fg)", width: 1.3, dash: "5 4" },
          { name: "요인 제외 5%·95%", y: noEv.bands.p5, color: "var(--muted)", width: 1, dash: "2 3" }, { name: "", y: noEv.bands.p95, color: "var(--muted)", width: 1, dash: "2 3" }],
        hlines: [{ y: g.amount, label: "목표 " + krw(g.amount) }],
        markers: md.eventList.map((e) => ({ x: e.date, label: `${e.date} ${e.event.target} ${e.event.kind}` })) });
      $("#evNote").innerHTML = `목표 달성 확률 <b>${pct(noEv.p_goal, 0)} → ${pct(R.p_goal, 0)}</b>, 목표일 중앙값 ${krw(noEv.terminal.p50)} → <b>${krw(R.terminal.p50)}원</b>, 나쁜 경우 5% ${krw(noEv.terminal.p5)} → <b>${krw(R.terminal.p5)}원</b> (요인 제외 → 반영). 삼각형은 사건 날짜입니다.`;
    } else {
      const i = b.holdings.findIndex((h) => h.ticker === cur), s1 = R.stocks[i], s0 = noEv.stocks[i], h = b.holdings[i], c = C[i % C.length];
      const p = S.prices[cur], kk = p ? Math.max(0, p.dates.length - 130) : 0;
      Charts.lineChart(host, { x: fx, height: 300, log: true, yfmt: priceAxis([s1.bands.p5, s1.bands.p95]),
        bands: [{ lo: s1.bands.p5, hi: s1.bands.p95, color: c, opacity: 0.12, name: "요인 반영 5~95%" }, { lo: s1.bands.p25, hi: s1.bands.p75, color: c, opacity: 0.24, name: "요인 반영 25~75%" }],
        series: [...(p ? [{ name: "과거", x: [...p.dates.slice(kk), md.startDate], y: [...p.close.slice(kk), h.price0], color: "var(--fg)", width: 1.2 }] : []),
          { name: "요인 반영 중앙값", y: s1.bands.p50, color: c, width: 2.2 }, { name: "요인 제외 중앙값", y: s0.bands.p50, color: "var(--fg)", width: 1.3, dash: "5 4" }],
        vlines: [{ x: md.startDate, label: "오늘" }],
        markers: md.eventList.filter((e) => e.event.target === cur || e.event.target === "ALL").map((e) => ({ x: e.date, label: `${e.date} ${e.event.kind}` })) });
      const T = s1.bands.p50.length - 1, at = (bb, k) => nf(bb[k][Math.min(T, 6)], 2);
      $("#evNote").innerHTML = `${esc(cur)} (${h.ccy}) 6개월 뒤 중앙값 ${at(s0.bands, "p50")} → <b>${at(s1.bands, "p50")}</b>, 나쁜 경우 5% ${at(s0.bands, "p5")} → <b>${at(s1.bands, "p5")}</b>; 목표일 중앙값 ${nf(s0.bands.p50[T], 2)} → <b>${nf(s1.bands.p50[T], 2)}</b> (요인 제외 → 반영).`;
    }
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
  async function runForecast() {
    const st = $("#fcStatus"), btn = $("#btnForecast");
    btn.disabled = true; st.textContent = "계산 중...";
    await new Promise((r) => setTimeout(r, 30));
    try {
      const t0 = performance.now(), b = buildModelNow();
      if (!b) { st.textContent = "평가액이 있는 종목이 없습니다."; btn.disabled = false; return; }
      const m = S.state.model, common = simCommon(b, m.scenario);
      const withEv = Model.simulate(b.model, { ...common, withEvents: true });
      const hasEv = b.model.eventList.length > 0;
      const noEv = hasEv ? Model.simulate(b.model, { ...common, withEvents: false }) : withEv;
      lastForecast = { b, withEv, noEv, hasEv, scen: m.scenario, at: new Date(), ms: performance.now() - t0 };
      fcDirty = false; fcCache[m.scenario] = { R: withEv, model: b.model };
      renderForecast();
      if ($("#tabs .on").dataset.tab === "dash") renderDash();
    } catch (e) { st.textContent = "오류: " + e.message; console.error(e); }
    btn.disabled = false;
  }
  function renderForecast() {
    const m = S.state.model;
    $("#scenario").value = m.scenario; $("#nPaths").value = String(m.n_paths); $("#rebalance").checked = !!m.rebalance_yearly;
    if (!lastForecast) { $("#fcStatus").textContent = "전망 계산을 눌러 주세요."; return; }
    const { b, withEv: R, noEv, hasEv } = lastForecast, g = S.state.goal, md = b.model;
    // 직접 입력한 현재가 때문에 오늘 평가액이 시세 기준과 크게 다르면 알린다 (차트가 오늘에서 꺾이는 원인)
    const manual = b.holdings.filter((h) => { const src = S.state.holdings.find((x) => x.ticker === h.ticker); const mk = src && curPrice({ ...src, price: null }).v; return src && Number(src.price) > 0 && mk && Math.abs(h.price0 / mk - 1) > 0.05; });
    const Hh = history(), lastHist = Hh.total[Hh.total.length - 1];
    $("#fcWarn").innerHTML = manual.length && lastHist ? `오늘 평가액(${krw(R.V0)}원)이 시세 기준(${krw(lastHist)}원)과 ${spct(R.V0 / lastHist - 1, 0)} 다릅니다. <b>${manual.map((h) => esc(h.ticker)).join(", ")}</b>에 현재가를 직접 넣었기 때문입니다. 매수 단가였다면 시세 수집에서 그 값을 지우고 '평균 매수가' 칸으로 옮겨 주세요.` : "";
    $("#fcWarn").style.display = $("#fcWarn").innerHTML ? "block" : "none";
    $("#fcStatus").textContent = `${lastForecast.at.toLocaleString()} 계산 · 경로 ${nf(m.n_paths)}개 × ${md.days.length}거래일 · ${(lastForecast.ms / 1000).toFixed(1)}초` + (fcDirty ? " · 입력이 바뀌었습니다. 다시 계산하세요." : "");
    const contrib = Number(g.monthly_contribution) || 0;
    $("#fcKpis").innerHTML = [
      ["목표 달성 확률", pct(R.p_goal, 0), `목표일 ${g.date}에 ${krw(g.amount)}원 이상`],
      ["중간에 한 번이라도 도달", pct(R.p_touch, 0), "목표일 전 어느 시점이든"],
      ["목표일 중앙값", krw(R.terminal.p50) + "원", `평균 ${krw(R.terminal.mean)}원`],
      ["나쁜 경우 (하위 5%)", krw(R.terminal.p5) + "원", `하위 25% ${krw(R.terminal.p25)}원`],
      ["좋은 경우 (상위 5%)", krw(R.terminal.p95) + "원", `상위 25% ${krw(R.terminal.p75)}원`],
      ["원금 손실 확률", pct(R.p_loss, 0), contrib ? `투입 ${krw(R.invested)}원 대비` : "현재 평가액 대비"],
      ["최대 낙폭 (중앙값)", pct(R.mdd_median, 0), `나쁜 10%: ${pct(R.mdd_p10, 0)}`],
      ["확률 50%에 필요한 월 적립", R.req50 == null ? "-" : R.req50 === 0 ? "0원" : krw(R.req50) + "원", R.req50 == null ? "재조정 끄면 계산" : "같은 비중으로 매월 매수 가정"],
    ].map(([k, v, s]) => `<div class="kpi"><div class="k">${k}</div><div class="v">${v}</div><div class="s">${s}</div></div>`).join("");

    // 부채꼴 차트: 지난 1년 + 미래
    const H = history(), k0 = Math.max(0, H.dates.length - 253);
    const fx = md.monthDates, V0 = R.V0, yrs = yearsBetween(md.startDate, g.date);
    const reqPath = fx.map((d) => V0 * (g.amount / V0) ** (yearsBetween(md.startDate, d) / yrs));
    Charts.lineChart($("#fcChart"), {
      x: fx, height: 340, yfmt: krwAxis,
      bands: [{ lo: R.bands.p5, hi: R.bands.p95, color: "var(--band)", opacity: 0.13, name: "5~95%" }, { lo: R.bands.p25, hi: R.bands.p75, color: "var(--band)", opacity: 0.25, name: "25~75%" }],
      series: [{ name: "과거", x: [...H.dates.slice(k0), md.startDate], y: [...H.total.slice(k0), V0], color: "var(--fg)", width: 1.4 },
        { name: "중앙값", y: R.bands.p50, color: "var(--c1)", width: 2.2 },
        { name: "필요 경로", y: reqPath, color: "var(--accent2)", dash: "5 4", width: 1.3 }],
      hlines: [{ y: g.amount, label: "목표 " + krw(g.amount) }],
      vlines: [{ x: md.startDate, label: "오늘" }],
      markers: md.eventList.map((e) => ({ x: e.date, label: `${e.date} ${e.event.target} ${e.event.kind}` })),
    });
    $("#fcYears").innerHTML = R.byYear.map((y) => `<div class="probbar"><span>${y.year}년 내 (${y.date})</span><div class="b"><i style="width:${y.p * 100}%"></i></div><span>${pct(y.p, 0)}</span></div>`).join("") || "기간이 1년 미만입니다.";
    if (hasEv) {
      const rows = [["목표 달성 확률", pct(noEv.p_goal, 0), pct(R.p_goal, 0)], ["목표일 중앙값", krw(noEv.terminal.p50), krw(R.terminal.p50)],
        ["하위 5%", krw(noEv.terminal.p5), krw(R.terminal.p5)], ["상위 5%", krw(noEv.terminal.p95), krw(R.terminal.p95)], ["원금 손실 확률", pct(noEv.p_loss, 0), pct(R.p_loss, 0)]];
      $("#fcEvents").innerHTML = `<table class="grid"><tr><th class="l"></th><th>요인 제외</th><th>요인 반영</th></tr>${rows.map((r) => `<tr><td class="l">${r[0]}</td><td>${r[1]}</td><td><b>${r[2]}</b></td></tr>`).join("")}</table>
        <p class="muted small">같은 난수로 사건만 빼고 다시 계산한 비교입니다(요인 제외 쪽은 실적 변동을 평소 변동성에 그대로 둠). 켜진 사건 ${md.eventList.length}건 (반복 포함).</p>`;
    } else $("#fcEvents").innerHTML = `<p class="muted">켜진 사건이 없습니다. '외부 요인'에서 켜 주세요.</p>`;

    $("#fcStocksSum").textContent = R.stocks.map((x) => `${x.ticker} 오를 확률 ${pct(x.p_up, 0)}`).join(" · ");
    if ($("#fcStocksCard").open) renderFcStocks();
    // 모형 값
    const scen = m.scenario;
    $("#fcParams").innerHTML = `<tr><th class="l">요인</th><th>비중</th><th>표본 (일)</th><th>과거 변동성</th><th>모형 변동성</th><th>요인 제외 평소 변동성</th><th>과거 평균 (연)</th><th>3년 스무딩 (연)</th><th>칼만·EMA 추세 (연)</th><th>사전값 비중</th><th>적용 기대수익 (${scenName(scen)})</th></tr>` +
      md.factors.map((f, i) => `<tr><td class="l">${f.kind === "fx" ? "환율 " + f.key : esc(f.key)}${f.cash ? ' <span class="tag">현금성</span>' : ""}</td>
        <td>${f.kind === "asset" ? pct(b.holdings[i].valueKrw / R.V0) : "-"}</td><td>${f.n}</td><td>${pct(f.volRaw)}</td><td>${pct(f.vol)}</td><td>${pct(f.volDiff)}</td>
        <td>${pct(f.muHist)}</td><td>${f.gSmooth == null ? "-" : spct(Math.exp(f.gSmooth) - 1)}</td><td>${f.gTrend == null ? "-" : spct(Math.exp(f.gTrend) - 1)}</td><td>${f.shrink == null ? "-" : pct(1 - f.shrink, 0)}</td><td><b>${pct(f.mu[scen])}</b></td></tr>`).join("") +
      `<tr><td class="l muted" colspan="11">상관행렬 ${md.corrShrink > 0 ? `(양의 정부호 보정 ${pct(md.corrShrink, 0)})` : ""}: ${md.factors.map((f, i) => md.factors.slice(0, i).map((g2, j) => `${f.key}–${g2.key} ${md.corr[i][j].toFixed(2)}`).join(", ")).filter(Boolean).join(" · ")}</td></tr>`;
    renderStrategy();
    if (curAna() === "fx") renderFx();
    if (curAna() === "events") { renderEvChart(); runXfEffect(); }
    aiRefresh();
  }

  // 종목별 가격 전망 (접어 둔 카드를 펼칠 때 그린다)
  function renderFcStocks() {
    if (!lastForecast) return;
    const { b, withEv: R } = lastForecast, md = b.model, fx = md.monthDates;
    const host = $("#fcStocks"); host.innerHTML = "";
    R.stocks.forEach((s, i) => {
      const box = document.createElement("div"), h = b.holdings[i];
      box.innerHTML = `<h3>${esc(s.ticker)} <span class="muted small">현재 ${nf(h.price0, 2)} ${h.ccy} · 목표일 중앙값 ${nf(s.bands.p50[s.bands.p50.length - 1], 2)} · 오를 확률 ${pct(s.p_up, 0)}</span></h3><div class="chartbox"></div>`;
      host.appendChild(box);
      const p = S.prices[s.ticker], kk = p ? Math.max(0, p.dates.length - 253) : 0;
      Charts.lineChart(box.querySelector(".chartbox"), {
        x: fx, height: 200, legend: false, log: true, yfmt: priceAxis([s.bands.p5, s.bands.p95]),
        bands: [{ lo: s.bands.p5, hi: s.bands.p95, color: C[i % C.length], opacity: 0.13, name: "5~95%" }, { lo: s.bands.p25, hi: s.bands.p75, color: C[i % C.length], opacity: 0.25, name: "25~75%" }],
        series: [...(p ? [{ name: "과거", x: [...p.dates.slice(kk), md.startDate], y: [...p.close.slice(kk), h.price0], color: "var(--fg)", width: 1.2 }] : []), { name: "중앙값", y: s.bands.p50, color: C[i % C.length], width: 2 }],
        markers: md.eventList.filter((e) => e.event.target === s.ticker || e.event.target === "ALL").map((e) => ({ x: e.date, label: `${e.date} ${e.event.kind}` })),
      });
    });

  }

  // ------------------------------------------------------------ 종목별 전략 (규칙 기반)
  function renderStrategy() {
    if (!lastForecast) return;
    const { b, withEv: R } = lastForecast, g = S.state.goal, md = b.model, V0 = R.V0;
    const yrs = yearsBetween(md.startDate, g.date), req = (g.amount / V0) ** (1 / yrs) - 1, medC = (R.terminal.p50 / V0) ** (1 / yrs) - 1;
    const w = b.holdings.map((h) => h.valueKrw / V0), risky = b.holdings.map((h, i) => (md.factors[i].cash ? 0 : w[i]));
    const top = risky.indexOf(Math.max(...risky)), cashW = b.holdings.reduce((s2, h, i) => s2 + (md.factors[i].cash ? w[i] : 0), 0);
    const soon = (t) => md.eventList.filter((e) => (e.event.target === t || e.event.target === "ALL") && yearsBetween(md.startDate, e.date) <= 0.34);
    const tips = [];
    tips.push(`**목표 확률 ${pct(R.p_goal, 0)}** (${scenName(S.state.model.scenario)} 시나리오). 필요한 연수익률 **${pct(req)}**, 전망 중앙값의 연수익률 **${pct(medC)}**.`);
    if (R.p_goal < 0.5 && R.req50 != null) tips.push(`**적립**: 지금 비중 그대로 확률 50%를 맞추려면 매월 약 **${krw(R.req50)}원**을 더 넣어야 합니다 (월 적립은 자산 추이의 목표 수정에서 입력).`);
    if (risky[top] > 0.45) tips.push(`**집중도**: ${b.holdings[top].ticker} 한 종목이 **${pct(w[top], 0)}**입니다. 하위 5% 결과가 ${krw(R.terminal.p5)}원까지 내려갑니다. '비중안 비교'에서 줄였을 때를 확인해 보세요.`);
    if (cashW < 0.03) tips.push(`**현금**: 현금성 자산이 ${pct(cashW, 1)}입니다. 하락장에서 살 여력과 심리적 완충을 위해 3~5%를 권합니다.`);
    tips.push(`**낙폭**: 최대 낙폭 중앙값 ${pct(R.mdd_median, 0)}. 목표일까지 가는 동안 이 정도 하락은 흔하다는 뜻입니다.`);
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
      if (sg && !f.cash) why.push(`**추세**: ${sg.trend} (칼만 기울기 연 ${spct(sg.slope_ann, 0)}, z ${sg.slope_z.toFixed(1)}), 고점 대비 ${pct(sg.drawdown, 0)}`);
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
      const Fs = [["모형 (" + scenName(S.state.model.scenario) + ")", fcReady(S.state.model.scenario)], ["추세 반영 (칼만·EMA)", fcReady("trend")], ["스무딩 (3년 추세선)", fcReady("smooth")]].filter(([, f]) => f && f.R);
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
      L.push(`목표 달성 확률 ${pct(R.p_goal, 0)}, 중간에 한 번이라도 도달 ${pct(R.p_touch, 0)}, 목표일 중앙값 ${krw(R.terminal.p50)}원, 하위5% ${krw(R.terminal.p5)}원, 상위5% ${krw(R.terminal.p95)}원, 원금 손실 확률 ${pct(R.p_loss, 0)}, 최대낙폭 중앙값 ${pct(R.mdd_median, 0)}.`);
      L.push("연도별 누적 도달 확률: " + R.byYear.map((y) => `${y.year}년 내 ${pct(y.p, 0)}`).join(", "));
      L.push("종목 (비중 / 적용 기대수익 연 / 모형 변동성 / 오를 확률):");
      b.holdings.forEach((h, i) => { const f = md.factors[i]; L.push(`- ${h.ticker}: ${pct(h.valueKrw / V0, 0)} / ${pct(f.mu[scen])} / ${pct(f.vol, 0)} / ${pct(R.stocks[i].p_up, 0)}${f.n < 252 ? ` (상장 ${f.n}거래일)` : ""}`); });
      L.push("요청: 1) 이 결과를 쉽게 해석, 2) 가정(기대수익·변동성)이 낙관적이거나 비관적인 부분, 3) 목표 확률을 높일 현실적인 방법 3가지. " + tail);
      return L.join("\n");
    }
    if (kind === "events") {
      // 화면의 타일·표와 같은 값만 보낸다 (펼친 반복 일정 대신 사건 단위로, 꺼진 사건도 표시)
      L.push(`내 포트폴리오 전망 모형에 넣은 외부 요인(기업 사건, 금리·거시, 테슬라 인도량, 신제품·리콜, 유가, 금값, 원자재, 정치·선거, 전쟁) 가정과 그 효과야. ${head}`);
      L.push("보유 비중: " + b.holdings.map((h) => `${h.ticker} ${pct(h.valueKrw / V0, 0)}`).join(", "));
      const B = factorBetas();
      L.push("종목별 민감도 (최근 3년 일별, 시장 외 요인은 시장 움직임 제외): " + b.holdings.map((h) => `${h.ticker} 시장베타 ${(B.stat.mkt[h.ticker]?.beta ?? NaN).toFixed(2)}, 금리+10bp ${spct((B.stat.rate[h.ticker]?.beta ?? NaN) * 10, 2)}, 유가+10% ${spct((B.stat.oil[h.ticker]?.beta ?? NaN) * 0.0953, 2)}, 금+10% ${spct((B.stat.gold[h.ticker]?.beta ?? NaN) * 0.0953, 2)}, 원자재+10% ${spct((B.stat.cmdty[h.ticker]?.beta ?? NaN) * 0.0953, 2)}`).join("; "));
      L.push("사건 (묶음 / 대상 / 종류 / 다음 날짜(추정) / 반복 / 발생 확률 / 평균 영향 / 불확실성 ± / 변동성 확대 / 상태). 요인 사건의 영향은 요인 단위(시장=S&P500 %, 금리=bp, 유가·금·원자재=%)이고 종목별 민감도만큼 반영:");
      S.state.events.forEach((e) => { const o = nextOcc(e), u = e.factor === "rate" ? "bp" : "%";
        L.push(`- ${catName(e.cat)} / ${tgtLab(e)} / ${e.kind} / ${o.next || "지남"} / ${e.repeat && e.repeat !== "none" ? `${repName(e.repeat)}, 목표일까지 ${o.n}회` : "한 번"} / ${e.prob}% / ${e.mean > 0 ? "+" : ""}${e.mean}${u} / ±${e.sd}${u} / ${e.vol_mult && e.vol_mult !== 1 && e.vol_days ? `${e.vol_mult}배 ${e.vol_days}거래일` : "없음"} / ${e.on ? "켜짐" : "꺼짐(모형 제외)"}`); });
      if (!S.state.events.length) L.push("- (사건 없음)");
      L.push("참고: 평균 영향 0인 사건은 방향 없이 변동만 키운다는 뜻이고, 반복 사건의 변동은 과거 변동성에 이미 들어 있어 그만큼 평소 변동성에서 뺐다.");
      if (hasEv) L.push(`몬테카를로 결과 (요인 제외 → 반영): 목표 확률 ${pct(noEv.p_goal, 0)} → ${pct(R.p_goal, 0)}, 목표일 중앙값 ${krw(noEv.terminal.p50)} → ${krw(R.terminal.p50)}원, 하위5% ${krw(noEv.terminal.p5)} → ${krw(R.terminal.p5)}원.`);
      if (xfEff && xfEff.fc === lastForecast) L.push("묶음별 영향 (그 묶음만 켰을 때 목표 확률 변화): " + Object.entries(xfEff.by).map(([c, X]) => `${catName(c)} ${spct(X.p_goal - xfEff.none.p_goal, 0)}p`).join(", "));
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
  // 페이지 안 자동 분석: 키 없이 쓰는 공개 AI 엔드포인트 (Pollinations, OpenAI 호환). 같은 질문의 답은 저장해 재사용
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
        const Fs = [["모형", fcReady(S.state.model.scenario)], ["추세 반영", fcReady("trend")], ["스무딩", fcReady("smooth")]].filter(([, f]) => f && f.R);
        if (Fs.length) { L.push("### 미래 기준별"); Fs.forEach(([n, f]) => L.push(`- ${n}: 목표 확률 ${B(pct(f.R.p_goal, 0))}, 목표일 중앙값 ${krw(f.R.terminal.p50)}원`));
          L.push("- 추세·스무딩은 최근 성장률을 그대로 잇는 낙관적 가정이고, 모형은 과거 수익률을 보수적으로 줄인 값입니다."); }
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
          const on = S.state.events.filter((e) => e.on), byCat = {};
          on.forEach((e) => (byCat[e.cat] = (byCat[e.cat] || 0) + 1));
          L.push("### 켜진 외부 요인", ...Object.entries(byCat).map(([c, n]) => `- ${catName(c)} ${n}건`));
          if (hasEv) L.push("### 효과", `- 목표 확률 ${pct(noEv.p_goal, 0)} → ${B(pct(R.p_goal, 0))}, 하위 5% ${krw(noEv.terminal.p5)} → ${krw(R.terminal.p5)}원`);
          if (xfEff && xfEff.fc === lastForecast) { const w = Object.entries(xfEff.by).sort((a, b2) => a[1].terminal.p5 - b2[1].terminal.p5)[0]; if (w) L.push(`- 나쁜 경우를 가장 크게 끌어내리는 요인: ${B(catName(w[0]))}`); }
          const Bt = factorBetas(), hi = b.holdings.map((h) => [h.ticker, Bt.stat.mkt[h.ticker]?.beta]).filter((x) => x[1] != null).sort((a, b2) => b2[1] - a[1])[0];
          if (hi) L.push(`- 시장 충격(정치·전쟁·거시)에 가장 민감한 종목: ${B(hi[0])} (베타 ${hi[1].toFixed(2)})`);
          L.push("- 날짜는 추정이니 실적·인도량·FOMC·선거 날짜는 공시와 일정표로 확인하세요.");
        } else if (kind === "forecast") {
          L.push("### 결과", `- 목표 확률 ${B(pct(R.p_goal, 0))}, 목표일 중앙값 ${krw(R.terminal.p50)}원 (목표의 ${pct(R.terminal.p50 / g.amount, 0)})`, `- 나쁜 경우 5% ${krw(R.terminal.p5)}원, 원금 손실 확률 ${pct(R.p_loss, 0)}`);
          const hv = b.holdings.map((h, i) => ({ t: h.ticker, v: md.factors[i].vol, w: h.valueKrw / V0 })).sort((a, b2) => b2.v * b2.w - a.v * a.w)[0];
          L.push("### 시사점", `- 위험의 대부분은 ${hv.t} (비중 ${pct(hv.w, 0)}, 변동성 ${pct(hv.v, 0)})에서 나옵니다.`, R.req50 ? `- 확률 50%에 필요한 월 적립은 약 ${krw(R.req50)}원입니다.` : "- 월 적립을 늘리거나 목표일을 늦추면 확률이 오릅니다.");
        } else if (kind === "strategy") {
          L.push("- 위 '포트폴리오 진단'과 종目별 카드가 같은 계산값으로 만든 규칙 기반 의견입니다.".replace("目", "목"), `- 목표 확률 ${B(pct(R.p_goal, 0))}, 목표일 중앙값 ${krw(R.terminal.p50)}원`);
        } else return "";
      }
    } catch (e) { return ""; }
    return L.join("\n");
  }
  // 무료 AI 순서: 개발자 중계(config.json "ai", 키는 중계에만) → Puter (한 번 로그인하면 자동) → Pollinations. 모두 안 되면 규칙 기반 해설
  let puterP = null;
  function loadPuter() { return (puterP ||= new Promise((res, rej) => { if (window.puter) return res(window.puter); const sc = document.createElement("script"); sc.src = "https://js.puter.com/v2/"; sc.onload = () => res(window.puter); sc.onerror = () => { puterP = null; rej(new Error("Puter를 불러오지 못함")); }; document.head.appendChild(sc); })); }
  const puterText = (r) => (typeof r === "string" ? r : r?.message?.content?.[0]?.text ?? r?.message?.content ?? r?.text ?? String(r ?? ""));
  async function askPuter(sys, q) { const P = await loadPuter(); return puterText(await P.ai.chat([{ role: "system", content: sys }, { role: "user", content: q }])); }
  const puterReady = () => { try { return !!window.puter?.auth?.isSignedIn?.(); } catch (e) { return false; } };
  const aiBusy = {}, aiFail = {};
  let aiQueue = Promise.resolve(); // 공개 엔드포인트는 동시 요청을 막을 수 있어 한 번에 하나씩
  const AI_SYS = "너는 신중한 한국어 투자 조언가다. 주어진 숫자만 근거로 아주 간결하게 답한다. 요청 항목마다 ### 소제목 하나와 한 줄짜리 글머리표 2~3개만 쓰고, 전체 15줄을 넘기지 않는다. 서론·반복·일반론은 빼고 핵심 숫자는 **굵게**. 표, 코드 블록(```), HTML 태그, 수식(LaTeX, $ 기호)은 쓰지 않고 일반 마크다운 글로만 쓴다(좁은 휴대폰 화면). 마지막 줄은 '투자 권유 아님.'";
  function showFallback(box, kind, why) {
    const r = ruleText(kind);
    loadPuter().catch(() => {}); // 버튼을 누르면 바로 로그인 창이 뜨도록 미리 불러 둔다
    box.innerHTML = (r ? md2html(r) : "") + `<p class="muted small">무료 AI에 연결하지 못해 계산값으로 만든 해설을 보여 줍니다 (${esc(why)}). <button class="sm" data-puter="${kind}">Puter 무료 AI로 분석</button></p>`;
  }
  async function aiAuto(kind, force, viaPuter) {
    const box = $("#aiOut-" + kind); if (!box) return;
    const off = S.state.ui.ai_auto === false;
    $$(".aicard, #aiAutoCard").forEach((c) => (c.style.display = off ? "none" : "block"));
    if (off || aiBusy[kind]) return;
    const q = aiPromptFor(kind);
    if (!q) { box.innerHTML = "<p class='muted'>분석할 계산 결과가 아직 없습니다.</p>"; return; }
    const key = hashStr("v5|" + q), cache = aiCache(), c = cache[kind];
    // 시세가 조금 바뀌어 질문 숫자가 달라져도, 입력(종목·수량·목표·사건·시나리오)이 같고 6시간 안이면 저장된 답을 그대로 쓴다 (빠르고 요청 수 절약)
    const sig = hashStr("v5|" + kind + JSON.stringify([S.state.holdings.map((h) => [h.ticker, h.shares]), S.state.goal, S.state.events.map((e) => [e.id, e.on, e.date, e.prob, e.mean, e.sd]), S.state.model.scenario, today()]));
    if (!force && aiFail[kind] && aiFail[kind].key === key && Date.now() - aiFail[kind].at < 600000) { showFallback(box, kind, aiFail[kind].why); return; } // 방금 실패한 같은 질문은 자동으로 다시 묻지 않음
    if (!force && c && c.text && (c.key === key || (c.sig === sig && Date.now() - c.at < 6 * 3600e3))) { box.innerHTML = md2html(cleanAi(c.text)) + `<p class="muted small">${new Date(c.at).toLocaleString()} 분석${c.src ? " · " + esc(c.src) : ""}</p>`; return; }
    aiBusy[kind] = true; box.innerHTML = "<p class='muted'>AI가 분석하는 중입니다… (보통 10~30초)</p>";
    const sys = AI_SYS;
    const timed = (pr) => { let tm; return Promise.race([pr, new Promise((_, rej) => { tm = setTimeout(() => rej(new Error("시간 초과")), 90000); })]).finally(() => clearTimeout(tm)); };
    const run = async () => {
      const tries = [];
      // 로그인 창은 버튼을 누를 때만 (Puter 는 첫 사용 때 무료 계정 확인 창을 띄움)
      if (viaPuter) tries.push(["Puter", () => askPuter(sys, q)]);
      if (S.config?.ai) tries.push(["AI 중계", async () => {
        for (let k = 0; ; k++) { // 붐빔(429·503)이면 잠깐 쉬고 한 번 더
          const r = await fetch(S.config.ai, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ system: sys, prompt: q }) }); const j = await r.json().catch(() => ({}));
          if (r.ok && j.text) return j.text;
          if (k < 1 && (r.status === 429 || r.status >= 500)) { await new Promise((ok) => setTimeout(ok, 3000)); continue; }
          throw new Error("응답 " + r.status + (j.error ? ": " + j.error : ""));
        } }]);
      if (!viaPuter && puterReady()) tries.push(["Puter", () => askPuter(sys, q)]);
      if (!S.config?.ai) tries.push(["Pollinations", async () => { const r = await fetch("https://text.pollinations.ai/openai", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ model: "openai", messages: [{ role: "system", content: sys }, { role: "user", content: q }], private: true, max_tokens: 1200 }) });
          if (!r.ok) throw new Error("응답 " + r.status); const j = await r.json(); return j.choices?.[0]?.message?.content || ""; }]);
      let text = "", err = null, src = "";
      for (const [nm, t] of tries) {
        try { text = cleanAi(await timed(t())); if (text) { src = nm; break; } }
        catch (e) { err = new Error(`${nm} ${e?.message || e}`); }
      }
      return { text, err, src };
    };
    const job = viaPuter ? run() : aiQueue.then(run); if (!viaPuter) aiQueue = job.catch(() => {});
    const { text, err, src } = await job;
    aiBusy[kind] = false;
    if (!text) { const why = err?.message || "빈 응답"; aiFail[kind] = { key, at: Date.now(), why }; showFallback(box, kind, why); return; }
    const cc = aiCache(); cc[kind] = { key, sig, text, at: Date.now(), src };
    try { localStorage.setItem(AI_KEY, JSON.stringify(cc)); } catch (e) { /* 무시 */ }
    box.innerHTML = md2html(text) + `<p class="muted small">${new Date().toLocaleString()} 분석 · ${esc(src)}</p>`;
  }
  // 지금 보고 있는 분석 화면의 AI 분석을 채운다
  function aiRefresh() { if ($("#tabs .on")?.dataset.tab === "analysis") aiAuto(curAna(), false); }


  // ------------------------------------------------------------ 인사이트 (뉴스)
  // data/news.json: GitHub Actions(웹) 또는 내 PC 서버가 한 시간마다 RSS·Yahoo 뉴스·유튜브 RSS 를 모아 AI 중계로 한글 번역·요약해 둔 파일
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
  const newsLi = (it) => `<li><a href="${esc(it.link)}" target="_blank" rel="noopener noreferrer">${esc(it.title || it.orig)}</a>
    <div class="nmeta">${esc(it.broker ? it.broker + " · " : "")}${esc(it.source || "")}${it.time ? " · " + ago(it.time) : ""}</div>${it.summary ? `<div class="nsum">${esc(it.summary)}</div>` : ""}</li>`;
  async function renderInsight() {
    const N = await loadNews();
    if (!N) {
      const msg = "<li class='muted'>아직 모은 뉴스가 없습니다. 한 시간마다 자동으로 모읍니다.</li>";
      $("#newsMedia").innerHTML = $("#newsBroker").innerHTML = msg; $("#newsFuture").innerHTML = $("#newsYt").innerHTML = ""; $("#newsNote").textContent = ""; return;
    }
    const rg = $("#newsRange .on")?.dataset.r || "realtime", sec = N[rg] || {};
    const empty = "<li class='muted'>해당 기사가 없습니다.</li>";
    $("#newsMedia").innerHTML = (sec.media || []).slice(0, 5).map(newsLi).join("") || empty;
    $("#newsBroker").innerHTML = (sec.broker || []).slice(0, 5).map(newsLi).join("") || empty;
    // 미래 가치: 내 보유 종목 순서대로 (수집 목록에 없는 종목은 다음 수집 때부터)
    const held = S.state.holdings.filter((h) => Number(h.shares) > 0).map((h) => h.ticker);
    const fut = (N.future || []).filter((f) => !held.length || held.includes(f.ticker)).sort((a, b) => held.indexOf(a.ticker) - held.indexOf(b.ticker));
    $("#newsFuture").innerHTML = fut.map((f) => `<div class="futrow"><h3>${esc(f.ticker)}${f.name ? ` <span class="muted small">${esc(f.name)}</span>` : ""}</h3>${f.view ? `<p class="small fview">${esc(f.view)}</p>` : ""}<ul class="newslist">${(f.items || []).map(newsLi).join("") || empty}</ul></div>`).join("")
      || "<p class='muted small'>보유 종목 관련 기사가 아직 없습니다.</p>";
    const Y = N.youtube || {};
    $("#newsYt").innerHTML = (Y.points?.length ? `<ul class="ytpts">${Y.points.map((x) => `<li>${esc(x)}</li>`).join("")}</ul>` : "")
      + (Y.keywords?.length ? `<p class="small">${Y.keywords.map((k) => `<span class="tag">${esc(k)}</span>`).join(" ")}</p>` : "")
      + (Y.videos?.length ? `<details class="opt"><summary>오늘 올라온 영상 ${Y.videos.length}개</summary><ul class="newslist">${Y.videos.map((v) => `<li><a href="${esc(v.link)}" target="_blank" rel="noopener noreferrer">${esc(v.title)}</a><div class="nmeta">${esc(v.channel || "")}${v.time ? " · " + ago(v.time) : ""}</div></li>`).join("")}</ul></details>` : "<p class='muted small'>오늘 올라온 영상이 아직 없습니다.</p>");
    $("#newsNote").textContent = `${N.updated ? new Date(N.updated).toLocaleString() + " 수집" : ""}${rg === "weekly" && sec.updated ? ` · 주간 목록 ${new Date(sec.updated).toLocaleString()}` : ""} · 한 시간마다 자동으로 모으고 무료 AI로 한글 번역·요약합니다${N.ai && N.ai !== "ok" ? ` (이번 번역 실패: ${N.ai})` : ""}. 링크는 원래 기사로 새 창에서 열립니다.`;
  }

  // ------------------------------------------------------------ 비중안 비교
  async function runAlloc() {
    const st = $("#allocStatus"), btn = $("#btnAlloc");
    if (!S.prices.QQQ) { st.textContent = "QQQ 시세가 없어 비교할 수 없습니다. 시세 수집에서 QQQ를 수집 목록에 넣어 주세요."; return; }
    btn.disabled = true; st.textContent = "계산 중... (안 5개)"; await new Promise((r) => setTimeout(r, 30));
    try {
      const g = S.state.goal, m = S.state.model, { rows } = valuation();
      const base = rows.filter((r) => r.valueKrw > 0).map((r) => ({ ticker: r.h.ticker, shares: r.sh, price0: r.p.v, ccy: r.ccy, valueKrw: r.valueKrw }));
      if (!base.some((h) => h.ticker === "QQQ")) { const q = S.prices.QQQ; base.push({ ticker: "QQQ", shares: 0, price0: q.close[q.close.length - 1], ccy: "USD", valueKrw: 1 }); }
      const series = {}; for (const k in S.prices) series[k] = { dates: S.prices[k].dates, adj: S.prices[k].adj };
      const model = Model.buildModel({ holdings: base, series, fxOf, settings: m, events: S.state.events, betas: factorBetas().beta, startDate: today(), goalDate: g.date });
      const V0 = base.reduce((s2, h) => s2 + (h.ticker === "QQQ" && h.shares === 0 ? 0 : h.valueKrw), 0);
      const w0 = base.map((h) => (h.ticker === "QQQ" && h.shares === 0 ? 0 : h.valueKrw / V0)), qi = base.findIndex((h) => h.ticker === "QQQ");
      const risky = base.map((h, i) => (model.factors[i].cash || i === qi ? 0 : w0[i])), top = risky.indexOf(Math.max(...risky));
      const capTop = (cap) => { const w = [...w0]; const cut = Math.max(0, w[top] - cap); w[top] -= cut; w[qi] += cut; return w; };
      const plans = [["A 현재 유지", w0, false], ["E 현재 비중 연 1회 재조정", w0, true]];
      if (w0[top] > 0.45) plans.push([`B ${base[top].ticker} 45%로 (차액 QQQ)`, capTop(0.45), true]);
      if (w0[top] > 0.35) plans.push([`C ${base[top].ticker} 35%로 (차액 QQQ)`, capTop(0.35), true]);
      plans.push(["D 지수 중심 (QQQ 60%)", w0.map((x, i) => (i === qi ? 0.6 + 0.4 * x : 0.4 * x)), true]);
      const out = [];
      for (const [name, w, rb] of plans) {
        const hs = base.map((h, i) => ({ ...h, valueKrw: V0 * w[i] }));
        const R = Model.simulate(model, { holdings: hs, scenario: m.scenario, nPaths: 1500, seed: Number(m.seed) || 1, goal: g.amount, monthly: Number(g.monthly_contribution) || 0, rebalance: rb, withEvents: true, dof: m.t_dof, fxOf });
        out.push({ name, w, R });
        st.textContent = `계산 중... (${out.length}/${plans.length})`; await new Promise((r) => setTimeout(r, 10));
      }
      $("#allocTable").innerHTML = `<tr><th class="l">안</th><th class="l">비중</th><th>목표 확률</th><th>중앙값</th><th>하위 5%</th><th>원금 손실 확률</th><th>최대 낙폭 중앙값</th></tr>` + out.map((o) => `<tr><td class="l"><b>${esc(o.name)}</b></td>
        <td class="l small">${base.map((h, i) => (o.w[i] > 0.004 ? `${esc(h.ticker)} ${pct(o.w[i], 0)}` : "")).filter(Boolean).join(" · ")}</td>
        <td><b>${pct(o.R.p_goal, 0)}</b></td><td>${krw(o.R.terminal.p50)}</td><td>${krw(o.R.terminal.p5)}</td><td>${pct(o.R.p_loss, 0)}</td><td>${pct(o.R.mdd_median, 0)}</td></tr>`).join("");
      st.textContent = `${new Date().toLocaleString()} 계산 · 안별 1,500경로, 같은 난수, 사건 포함`;
      lastAlloc = { base, out, fd: model.monthDates }; allocDirty = false;
      renderAllocChart();
      if (curAna() === "alloc") aiRefresh();
    } catch (e) { st.textContent = "오류: " + e.message; console.error(e); }
    btn.disabled = false;
  }

  function renderAllocChart() {
    if (!lastAlloc) return;
    const q = $("#allocQ .on")?.dataset.q || "p50", g = S.state.goal;
    Charts.lineChart($("#allocChart"), { x: lastAlloc.fd, height: 300, yfmt: krwAxis,
      series: lastAlloc.out.map((o, j) => ({ name: o.name, y: o.R.bands[q], color: C[j % C.length], width: j === 0 ? 2.4 : 1.8, dash: j === 0 ? "" : j % 2 ? "6 3" : "" })),
      hlines: [{ y: g.amount, label: "목표 " + krw(g.amount) }] });
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
      ["원화 10% 강세면", `<span class="bad">${krw(-total * usdW * 0.1)}원</span>`, "주가 변동 없이 환율만"],
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
  function renderTrend() {
    const sel = $("#trendTicker"), keys = Object.keys(S.prices).filter((k) => S.state.holdings.some((h) => h.ticker === k && Number(h.shares) > 0)).sort((a, b) => { // 보유 종목만 (비교용 QQQ·SPY 등은 빼고)
      const ha = S.state.holdings.findIndex((h) => h.ticker === a), hb = S.state.holdings.findIndex((h) => h.ticker === b);
      return (ha < 0 ? 99 : ha) - (hb < 0 ? 99 : hb);
    });
    const cur = keys.includes(sel.value) ? sel.value : keys[0];
    sel.innerHTML = keys.map((k) => `<option ${k === cur ? "selected" : ""}>${esc(k)}</option>`).join("");
    const sig = [];
    for (const k of keys) { const p = S.prices[k], ind = Model.indicators(p.dates, p.adj); if (ind) sig.push([k, ind.sig, ind]); }
    const one = sig.find((s) => s[0] === (sel.value || cur));
    if (one) {
      const ind = one[2], n = +($("#trendRange .on")?.dataset.r || 252), k0 = Math.max(0, ind.dates.length - n), x = ind.dates.slice(k0);
      Charts.lineChart($("#trendChart"), { x, height: 300, log: true, yfmt: (v) => nf(v, v < 10 ? 2 : 0), series: [
        { name: "칼만 수준", y: ind.level.slice(k0), color: "var(--c1)", width: 3 },
        { name: "수정종가", y: ind.close.slice(k0), color: "var(--fg)", width: 1 },
        { name: "EMA50", y: ind.ema50.slice(k0), color: "var(--c3)", dash: "4 3" },
        ...(ind.dates.length >= 200 ? [{ name: "EMA200", y: ind.ema200.slice(k0), color: "var(--c2)", dash: "6 3" }] : [])] });
      const sl = ind.slopeAnn.slice(k0), sd = ind.slopeSd.slice(k0);
      Charts.lineChart($("#slopeChart"), { x, height: 170, yfmt: (v) => (v * 100).toFixed(0) + "%",
        bands: [{ lo: sl.map((v, i) => v - sd[i]), hi: sl.map((v, i) => v + sd[i]), color: "var(--c1)", opacity: 0.18, name: "±1 표준편차" }],
        series: [{ name: "칼만 기울기 (연율)", y: sl, color: "var(--c1)", width: 1.8 }], hlines: [{ y: 0, label: "0", color: "var(--muted)" }] });
    }
    $("#sigTable").innerHTML = `<tr><th class="l">종목</th><th class="l">추세 판정</th><th>현재</th><th>칼만 수준</th><th>칼만 대비</th><th>기울기 (연)</th><th>기울기 z</th><th>EMA50</th><th>EMA200</th><th>변동성 (EWMA)</th><th>고점 대비</th><th>1개월</th><th>3개월</th><th>1년</th></tr>` +
      sig.filter(([k]) => S.state.holdings.some((h) => h.ticker === k)).map(([k, s]) => `<tr><td class="l"><b>${esc(k)}</b></td><td class="l">${s.trend}</td><td>${nf(s.close, 2)}</td><td>${nf(s.kalman_level, 2)}</td><td class="${cls(s.dev_from_kalman)}">${spct(s.dev_from_kalman)}</td>
        <td class="${cls(s.slope_ann)}">${spct(s.slope_ann, 0)}</td><td>${s.slope_z.toFixed(2)}</td><td>${nf(s.ema50, 2)}</td><td>${s.ema200 ? nf(s.ema200, 2) : "-"}</td><td>${pct(s.vol_ewma, 0)}</td>
        <td class="bad">${pct(s.drawdown)}</td><td class="${cls(s.ret_1m)}">${spct(s.ret_1m)}</td><td class="${cls(s.ret_3m)}">${spct(s.ret_3m)}</td><td class="${cls(s.ret_1y)}">${spct(s.ret_1y)}</td></tr>`).join("");
  }

  // ------------------------------------------------------------ 설정
  function renderGh() {
    const box = $("#ghBox"); if (!box) return;
    if (MODE !== "static") { $("#devCard").style.display = "none"; return; }
    const has = !!ghToken();
    box.innerHTML = `<p class="small">일반 사용자는 필요 없습니다. 토큰을 넣으면 '시세 수집'이 GitHub Actions 수집을 직접 실행하고 저장소 데이터를 갱신합니다(공개 중계 대신). ${has ? "<b class='good'>연결됨.</b>" : ""} 토큰은 이 브라우저에만 저장됩니다.</p>
      <div class="row wrap"><input id="ghToken" type="password" size="40" placeholder="${has ? "새 토큰으로 바꾸려면 붙여넣기" : "GitHub 토큰 붙여넣기 (github_pat_...)"}">
      <button id="ghSave" class="primary">저장</button>${has ? '<button id="ghTest">연결 확인</button><button id="ghDel" class="danger">연결 해제</button>' : ""}</div>
      <details class="small" ${has ? "" : "open"}><summary>토큰 만드는 법 (1분)</summary><ol>
      <li><a href="https://github.com/settings/personal-access-tokens/new" target="_blank" rel="noopener">GitHub 토큰 만들기</a> 페이지를 엽니다 (Fine-grained token).</li>
      <li>Token name 아무거나, Expiration 원하는 기간, Repository access → <b>Only select repositories</b> → <b>${GH ? esc(GH.repo) : "asset-tracker"}</b>.</li>
      <li>Permissions → Repository permissions → <b>Actions: Read and write</b> (Contents는 Read-only 자동).</li>
      <li>Generate token → 복사해서 위 칸에 붙여넣고 저장.</li></ol></details>`;
    $("#ghSave").onclick = async () => {
      const v = $("#ghToken").value.trim(); if (!v) return toast("토큰을 붙여넣어 주세요");
      try { localStorage.setItem(TOKEN_KEY, v); } catch (e) { return toast("브라우저 저장 실패"); }
      try { await ghApi("actions/workflows/collect.yml"); toast("GitHub 연결됨"); renderGh(); const miss = missingTickers(); if (miss.length) { showTab("quotes"); ghCollect(miss); } }
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
  const curAna = () => $("#anaNav .on")?.dataset.a || "strategy";
  function renderAnalysis() {
    const a = curAna();
    $$(".ana").forEach((el) => (el.style.display = el.id === "ana-" + a ? "block" : "none"));
    if (a === "strategy") renderStockPrices();
    if (a === "trend") { renderTrend(); aiRefresh(); }
    if (a === "events") renderSchedule();
    if (a === "fx") renderFx();
    if (a === "strategy" || a === "forecast" || a === "events" || a === "fx") { if (!lastForecast || fcDirty) runForecast(); else renderForecast(); }
    if (a === "alloc") { if (!lastAlloc || allocDirty) runAlloc(); else { renderAllocChart(); aiRefresh(); } }
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
    $("#btnCollect").onclick = () => collect(false);
    $("#btnQuotes").onclick = () => collect(true);
    $("#autoRefresh").value = String(S.state.ui.auto_refresh_min || 0);
    $("#autoRefresh").onchange = (e) => { S.state.ui.auto_refresh_min = +e.target.value; setAuto(+e.target.value); save(false); };
    $("#holdTable").addEventListener("input", onHoldEdit);
    $("#holdTable").addEventListener("change", onHoldEdit);
    $("#holdTable").addEventListener("click", (e) => { const d = e.target.closest("[data-del]"); if (d) delHolding(+d.dataset.del, d); });
    $("#btnAdd").onclick = addHolding;
    $("#addAvg").addEventListener("keydown", (e) => e.key === "Enter" && addHolding());
    $("#addShares").addEventListener("keydown", (e) => e.key === "Enter" && addHolding());
    ["#goalAmount", "#goalDate", "#startDate", "#goalYears", "#monthly"].forEach((s) => { $(s).addEventListener("change", onGoalEdit); });
    segClick("#histRange", renderDash); segClick("#histStep", renderDash); segClick("#histMode", renderDash); segClick("#histCcy", renderDash); segClick("#histBasis", () => { const f = $("#histRange button[data-r=future]"); if (!f.classList.contains("on")) f.click(); else renderDash(); });
    segClick("#stockRange", renderStockPrices); segClick("#allocQ", renderAllocChart); segClick("#trendRange", renderTrend); segClick("#fxRange", renderFx);
    $("#trendTicker").onchange = renderTrend;
    segClick("#anaNav", renderAnalysis);
    $("#btnAlloc").onclick = runAlloc;
    $("#eventTable").addEventListener("input", onEventEdit);
    $("#evTiles").addEventListener("click", (e) => { const t = e.target.closest("[data-evt]"); if (!t) return; const ev = S.state.events[+t.dataset.evt]; ev.on = !ev.on; save(); renderEvents(); if (curAna() === "events") runForecast(); });
    segClick("#evView", renderEvChart);
    segClick("#newsRange", renderInsight);
    segClick("#xfNav", () => { renderEvTiles(); renderXf(); });
    $("#fcStocksCard").addEventListener("toggle", (e) => { if (e.target.open) renderFcStocks(); });
    $("#eventTable").addEventListener("change", onEventEdit);
    $("#eventTable").addEventListener("click", (e) => { const d = e.target.closest("[data-del]"); if (d && armed(d)) { S.state.events.splice(+d.dataset.del, 1); save(); renderEvents(); } });
    $("#btnAddEvent").onclick = () => {
      const c = curCat() === "all" ? "corp" : curCat(), fk = CAT_FACTOR[c];
      S.state.events.push({ id: "e" + Date.now(), on: true, cat: c, factor: fk || "none", date: Model.addMonths(today(), 1), target: fk ? "ALL" : S.state.holdings[0]?.ticker || "ALL", kind: "기타", repeat: "none", prob: 100, mean: 0, sd: fk === "rate" ? 20 : 5, vol_mult: 1, vol_days: 0, note: "" });
      save(); renderEvents();
    };
    $("#scenario").onchange = (e) => { S.state.model.scenario = e.target.value; save(); runForecast(); };
    $("#nPaths").onchange = (e) => { S.state.model.n_paths = +e.target.value; save(); };
    $("#rebalance").onchange = (e) => { S.state.model.rebalance_yearly = e.target.checked; save(); };
    $("#btnForecast").onclick = runForecast;
    $("#modelForm").addEventListener("change", onModelEdit);
    $("#btnResetModel").onclick = (e) => { if (armed(e.target)) { S.state.model = { ...DEFAULT_MODEL }; save(); renderSettings(); } };
    $("#optAi").checked = S.state.ui.ai_auto !== false;
    $("#optAi").onchange = (e) => { S.state.ui.ai_auto = e.target.checked; save(false); };
    ["#tab-analysis", "#tab-dash"].forEach((t) => $(t).addEventListener("click", (e) => { const b2 = e.target.closest("[data-aire]"); if (b2) aiAuto(b2.dataset.aire, true); const b3 = e.target.closest("[data-puter]"); if (b3) aiAuto(b3.dataset.puter, true, true); }));
    $("#optManual").checked = !!S.state.ui.manual_price;
    $("#optManual").onchange = (e) => { S.state.ui.manual_price = e.target.checked; save(); renderAll(); };
    $("#btnExport").onclick = () => {
      const a = document.createElement("a");
      a.href = URL.createObjectURL(new Blob([JSON.stringify(S.state, null, 1)], { type: "application/json" }));
      a.download = `자산입력-${today()}.json`; a.click();
    };
    $("#fileImport").onchange = async (e) => {
      const f = e.target.files[0]; if (!f) return;
      try { S.state = normalize(JSON.parse(await f.text())); save(); renderAll(); toast("불러왔습니다"); } catch (err) { alert("파일을 읽지 못했습니다: " + err.message); }
    };
    let rz; window.addEventListener("resize", () => { clearTimeout(rz); rz = setTimeout(() => { const t = $("#tabs .on").dataset.tab; if (t === "dash") renderDash(); if (t === "analysis") { if (curAna() === "strategy") renderStockPrices(); if (curAna() === "alloc") renderAllocChart(); if (curAna() === "trend") renderTrend(); else if (curAna() === "fx" && !lastForecast) renderFx(); else if (lastForecast) renderForecast(); } }, 200); });
  }

  async function init() {
    try { await reload(); }
    catch (e) { document.body.innerHTML = `<div class="card" style="margin:40px auto;max-width:640px"><h2>데이터를 불러오지 못했습니다</h2><p>내 PC에서 쓸 때는 <b>실행 파일</b>(Windows: <code>실행-Windows.bat</code>, Mac: <code>실행-Mac.command</code>)로 열어야 합니다. 웹 버전은 GitHub Actions의 첫 수집이 끝난 뒤 열립니다.</p><p class="muted small">${esc(e.message)}</p></div>`; return; }
    bind(); renderAll();
    setAuto(S.state.ui.auto_refresh_min || 0);
    let tab = "dash"; try { tab = localStorage.getItem("tab") || "dash"; const a = localStorage.getItem("ana"); if (a && $(`#anaNav button[data-a="${a}"]`)) $$("#anaNav button").forEach((b) => b.classList.toggle("on", b.dataset.a === a)); } catch (e) { /* 무시 */ }
    showTab(tab);
    if (MODE === "static") {
      $("#btnQuotes").style.display = "none";
      $("#modeNote").innerHTML = `시세는 평일 30분마다 자동으로 모이고, '시세 수집'을 누르면 지금 시세를 바로 받습니다. 입력한 종목·수량·매수 단가는 <b>이 브라우저에만</b> 저장됩니다.`;
      $("#modeNote").style.display = "block";
      logLine(`웹 데이터 수집 시각: ${S.dataUpdated ? new Date(S.dataUpdated).toLocaleString() : "-"}`);
      if (S.firstVisit) logLine("처음 여셨습니다. 보유 종목의 수량(과 매수 단가)을 넣어 주세요. 저장해 둔 파일이 있으면 아래 설정의 '입력값 불러오기'를 쓰면 됩니다.", false);
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
