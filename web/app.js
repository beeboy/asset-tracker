// 화면 로직: 입력 편집, 저장, 시세 수집 요청, 화면 그리기.
(function () {
  "use strict";
  // 응답 없는 서버를 끝없이 기다리지 않게: 따로 신호를 주지 않은 fetch 는 20초 뒤 끊는다 (본문 받기까지 포함)
  if (!window.fetch.__to) {
    const f0 = window.fetch.bind(window);
    const lim = (ms) => { if (AbortSignal.timeout) return AbortSignal.timeout(ms); const c = new AbortController(); setTimeout(() => c.abort(), ms); return c.signal; };
    window.fetch = Object.assign((u, o = {}) => f0(u, o.signal ? o : { ...o, signal: lim(20000) }), { __to: true });
  }
  const $ = (s) => document.querySelector(s);
  const $$ = (s) => [...document.querySelectorAll(s)];
  const C = ["var(--c1)", "var(--c2)", "var(--c3)", "var(--c4)", "var(--c5)", "var(--c6)", "var(--c7)"];
  const DEFAULT_MODEL = Model.DEFAULT_MODEL; // 전망 계산 기본 설정 (model.js)
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
    mkt: { sym: Model.FACTOR_SYM.mkt, name: "미국 시장 (S&P500, SPY)", unit: "%", shock: -5, shockTxt: "S&P500 -5%" },
    rate: { sym: Model.FACTOR_SYM.rate, name: "미국 10년 국채 금리", unit: "bp", shock: 25, shockTxt: "10년 금리 +0.25%p" },
    oil: { sym: Model.FACTOR_SYM.oil, name: "WTI 유가", unit: "%", shock: 10, shockTxt: "유가 +10%" },
    gold: { sym: Model.FACTOR_SYM.gold, name: "금 선물", unit: "%", shock: 10, shockTxt: "금값 +10%" },
    cmdty: { sym: Model.FACTOR_SYM.cmdty, name: "원자재 지수 (DBC)", unit: "%", shock: 10, shockTxt: "원자재 +10%" },
  };
  const FACTOR_SYMS = Object.values(FACTORS).map((f) => f.sym);
  const CAT_FACTOR = { rate: "rate", infl: "rate", oil: "oil", gold: "gold", cmdty: "cmdty", politics: "mkt", war: "mkt", trade: "mkt", flow: "mkt" };
  // 환율 묶음은 요인 민감도 대신 원/달러(KRW=X)에 직접 반영한다 (사건 대상 FX)
  const FX_F = { sym: "KRW=X", name: "원/달러 환율", unit: "%", shock: 5, shockTxt: "원/달러 +5%" };
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
  const SESS = { pre: "프리", regular: "정규", post: "애프터", close: "종가", manual: "수동", toss: "토스 시세" };

  let S = { state: null, prices: {}, quotes: {} };
  // 실행 방식: "local" = 내 PC 의 server.py, "static" = GitHub Pages 같은 정적 사이트 (입력은 이 브라우저에 저장)
  let MODE = "local";
  const LS_KEY = "asset-tracker-state";
  const SAMPLE_QTY = { DRNK: 4, QQQ: 1 }, SAMPLE_GOAL = 5e6; // 첫 방문 샘플 (앱 Sample.startHoldings 와 같은 수량)
  const GH = (() => {
    const m = location.hostname.match(/^([^.]+)\.github\.io$/), repo = location.pathname.split("/").filter(Boolean)[0];
    // 내 도메인(naeilo.com 등)으로 열면 주소에 저장소 이름이 없으므로 이 앱의 저장소를 쓴다
    const [owner, name] = m && repo ? [m[1], repo] : /^(localhost|127\.|\[::1\]|$)/.test(location.hostname) ? [] : ["beeboy", "asset-tracker"];
    return owner ? { owner, repo: name, actions: `https://github.com/${owner}/${name}/actions/workflows/collect.yml` } : null;
  })();
  let saveTimer = null, autoTimer = null, lastForecast = null, fcDirty = true, lastAlloc = null, allocDirty = true;
  let fcCache = {}; // 시나리오별 전망 (대시보드 미래 표시·AI 용). 입력이나 시세가 바뀌면 비운다
  const markDirty = () => { fcDirty = allocDirty = true; fcCache = {}; allocWarm(); };
  const SCEN = { blend: "내 관점", base: "기준", conservative: "보수", history: "과거 반복", smooth: "스무딩 추종", trend: "추세 추종" };
  const scenName = (k) => SCEN[k] || k;

  // ------------------------------------------------------------ 형식
  const nf = (v, d = 0) => (v == null || !isFinite(v) ? "-" : Number(v).toLocaleString("ko-KR", { minimumFractionDigits: d, maximumFractionDigits: d }));
  const pct = (v, d = 1) => (v == null || !isFinite(v) ? "-" : (v * 100).toFixed(d) + "%");
  const spct = (v, d = 1) => (v == null || !isFinite(v) ? "-" : (v >= 0 ? "+" : "") + (v * 100).toFixed(d) + "%");
  const cls = (v) => (v == null ? "" : v > 0 ? "up" : v < 0 ? "dn" : "");
  // 남은 기간: 1년 넘으면 소수 한 자리 년, 안 되면 개월·일 (앱 내 길과 같은 표시)
  const leftStr = (y) => (!(y > 0) ? "지남" : y >= 1 ? y.toFixed(1) + "년" : y * 12 >= 1 ? Math.floor(y * 12) + "개월" : Math.ceil(y * 365.25) + "일");
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
  // 종목별 사이트 마지막 종가 (전망 기준값. 앱도 같은 data/prices 파일의 마지막 종가를 쓴다)
  function lastCloses(P) {
    const out = {};
    for (const [k, p] of Object.entries(P || {})) { const c = p.close || []; for (let i = c.length - 1; i >= 0; i--) if (c[i] > 0) { out[k] = c[i]; break; } }
    return out;
  }
  // 불러온 뒤 한국 종목 추정 시세를 만든다 (규칙 파일은 처음 한 번)
  async function reload() {
    await reloadData();
    await tossQuotes();
    if (!S.krRules) S.krRules = await fetch("../data/kr_proxy.json", { cache: "no-cache" }).then((r) => r.json()).catch(() => ({}));
    synthKR();
  }
  // 토스증권 국내·미국 지금 가격 (개발자 전용: 키를 넣은 PC 의 server.py 에서만 값이 온다). 지수·환율·토스에 없는 종목, 실패·3초 넘김은 Yahoo 값 그대로
  // 정적 사이트(naeilo.com)는 이 브라우저에 PC 의 Tailscale 주소를 넣어 둔 경우만: naeilo.com/web/?toss=https://my-pc….ts.net 을 한 번 열면 저장 (?toss= 빈 값이면 지움)
  async function tossQuotes() {
    let base = "";
    if (MODE !== "local") { try { const u = new URL(location.href), t = u.searchParams.get("toss"); if (t != null) { if (/^https:\/\/[\w.-]+\.ts\.net\/?$/.test(t)) localStorage.setItem("toss_base", t); else localStorage.removeItem("toss_base"); u.searchParams.delete("toss"); history.replaceState(null, "", u); } } catch (e) { /* 무시 */ }
      try { base = (localStorage.getItem("toss_base") || "").replace(/\/$/, ""); } catch (e) { /* 무시 */ } if (!base) return; }
    const ks = (S.state?.holdings || []).map((h) => h.ticker).filter((t) => t && t !== KR_SUM && !/[\^=]/.test(t));
    if (!ks.length) return;
    const ac = new AbortController(), tm = setTimeout(() => ac.abort(), 3000);
    try {
      const r = await fetch(base + "/api/toss/prices?symbols=" + encodeURIComponent(ks.join(",")), { signal: ac.signal }), j = await r.json();
      // Yahoo 전일 종가 등은 두고 지금 가격·통화만 토스 값으로
      for (const [k, q] of Object.entries(j.quotes || {})) S.quotes[k] = { ...(S.quotes[k] || {}), ...q };
      if (j.error) logLine("토스 시세: " + j.error, false);
    } catch (e) { /* 무시 */ } finally { clearTimeout(tm); }
  }
  async function reloadData() {
    if (MODE === "local") {
      try {
        const d = await api("/api/data");
        S.prices = d.prices || {}; S.quotes = d.quotes || {}; S.mar = d.mar || {}; S.siteClose = lastCloses(S.prices);
        if (!S.state) S.state = normalize(d.state || {});
        S.config = d.config || {};
        return;
      } catch (e) { if (S.state) throw e; MODE = "static"; }
    }
    // 정적 사이트: ../data/ 의 파일을 읽는다 (GitHub Actions 가 주기적으로 갱신)
    // 자주 바뀌는 시세(index·quotes)는 CDN 캐시도 건너뛰고(?t=), 나머지는 no-cache: 서버에 확인만 하고 그대로면(304) 본문을 다시 받지 않는다
    const base = "../data/", get = async (f, live) => { const r = await fetch(base + f + (live ? "?t=" + Date.now() : ""), { cache: live ? "no-store" : "no-cache" }); if (!r.ok) throw new Error(f + " " + r.status); return r.json(); };
    // 서로 상관없는 파일은 한꺼번에 받는다 (차례로 기다리지 않게)
    const side = Promise.all([get("quotes.json", 1).catch(() => ({})), get("tickers.json").catch(() => ({ tickers: [] })), get("config.json").catch(() => ({})), get("mar.json").catch(() => ({}))]);
    const idx = await get("index.json", 1), prices = {};
    // 시세 파일은 종목별 판(index.json 의 v, 없으면 수집 시각 updated)이 같으면 브라우저 캐시를 그대로 쓴다. 바뀐 종목만 주소가 바뀌어 새로 받는다
    const ver = (sym) => "?v=" + encodeURIComponent(idx.v?.[sym] || idx.updated || Date.now());
    const getPx = async (sym, f) => { const r = await fetch(base + "prices/" + encodeURIComponent(f) + ver(sym)); if (!r.ok) throw new Error(f + " " + r.status); return r.json(); };
    await Promise.all(Object.entries(idx.prices || {}).map(async ([sym, f]) => { try { prices[sym] = await getPx(sym, f); } catch (e) { /* 없는 파일 무시 */ } }));
    S.prices = prices; S.dataUpdated = idx.updated; S.siteClose = lastCloses(prices); // 전망 기준값: 브라우저가 따로 받은 시세를 섞기 전 사이트 종가
    [S.quotes, S.tickerCfg, S.config, S.mar] = await side;
    mergeExtra();
    if (!S.state) {
      let st = null;
      try { st = JSON.parse(localStorage.getItem(LS_KEY) || "null"); } catch (e) { /* 무시 */ }
      S.firstVisit = !st;
      S.state = normalize(st || (await get("state.default.json").catch(() => ({}))));
      // 첫 방문: 빈 화면 대신 앱과 같은 샘플 (가상 종목 DRNK 4주·QQQ 1주). 내 종목을 처음 넣거나 고치면 샘플은 사라진다
      if (S.firstVisit && !S.state.holdings.length) { S.state.holdings = [{ ticker: "DRNK", shares: SAMPLE_QTY.DRNK, price: null, avg_cost: null, note: "가상 종목", sample: true }, { ticker: "QQQ", shares: SAMPLE_QTY.QQQ, price: null, avg_cost: null, note: "", sample: true }]; S.state.sample = true;
        S.state.sample_goal = S.state.goal.amount; S.state.goal.amount = SAMPLE_GOAL; } // 샘플 평가액(약 240만원)에 맞춰 목표도 500만원으로. 샘플이 끝나면 원래 목표로
    }
    // 예전 샘플(DRNK 60주·QQQ 10주, 목표 1억)을 저장해 둔 방문자도 앱처럼 소액 샘플로 본다 (불러올 때마다 맞추므로 따로 저장하지 않는다)
    if (S.state.sample && S.state.holdings.some((h) => h.sample && SAMPLE_QTY[h.ticker] && Number(h.shares) !== SAMPLE_QTY[h.ticker])) {
      S.state.holdings.forEach((h) => { if (h.sample && SAMPLE_QTY[h.ticker]) h.shares = SAMPLE_QTY[h.ticker]; });
      if (S.state.goal.amount === 1e8) S.state.goal.amount = SAMPLE_GOAL;
      delete S.state.weekly;
    }
    if (S.state.sample) await sampleSeed();
  }
  // 샘플(DRNK 4주·QQQ 1주)은 누구에게나 같은 값이라, 정해 둔 날짜에 미리 계산한 결과(data/sample_calc.json)를 저장 칸에 넣어 두고
  // 그대로 쓴다 (전망·미래 그래프·비중 조정안을 다시 계산하지 않음). 내 종목을 넣으면 지금 값으로 새로 계산한다.
  // 새로 만들 때: node tools/sample_snapshot.mjs (주소에 ?fresh 를 붙이면 미리 계산한 값을 쓰지 않는다)
  async function sampleSeed() {
    if (/[?&]fresh\b/.test(location.search)) return;
    try {
      const r = await fetch((MODE === "static" ? "../data/" : "/data/") + "sample_calc.json", { cache: "no-cache" }); if (!r.ok) return;
      const c = await r.json(); S.sampleCalc = c.date;
      const put = (k, v) => v && localStorage.setItem(k, JSON.stringify(v));
      put(FC_KEY, c.forecast && { ...c.forecast, sig: fcSig() });
      put(FCD_KEY, c.fcdash && { ...c.fcdash, sig: fcdSig() });
      put(AL_KEY, c.alloc && { ...c.alloc, sig: fcSig() + "|" + JSON.stringify(S.state.alloc_mix || {}) });
    } catch (e) { /* 없으면 평소처럼 계산 */ }
  }
  function normalize(st) {
    st.holdings = st.holdings || [];
    st.holdings.forEach((h) => { if (h.note === "수량을 입력하세요") h.note = ""; }); // 예전 기본 문구 정리
    st.goal = Object.assign({ amount: 1e9, date: Model.addMonths(today(), 36), start_date: today(), monthly_contribution: 0 }, st.goal || {});
    Model.normEvents(st); // 외부 요인 기본 사건 넣기·묶음 정리 (model.js, 앱이 받는 stats.json 도 같은 함수)
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
    st.model = Model.normModel(st.model);
    st.ui = Object.assign({ auto_refresh_min: 0, manual_price: false }, st.ui || {});
    // 국내 주식 합계: 날짜별 금액 기록. 기록 없이 금액 하나만 있던 예전 저장값은 그 금액·날짜를 첫 줄로
    const ks = st.holdings.find((h) => h.ticker === "KOREA.KS");
    if (!st.kr_log?.length && ks && Number(ks.price) > 0) st.kr_log = [{ d: ks.price_date || today(), v: Number(ks.price) }];
    if (st.kr_log) st.kr_log = Model.krNorm(st.kr_log);
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
  // naeilo를 쓴 날부터의 내 기록: 하루 한 줄 {d, v 평가액, c 넣은 돈, q {티커: [수량, 원화 주가]}, f {티커: [산·판 수량, 원화 금액]}}
  // 넣은 돈 = 전날 넣은 돈 + 그날 '더 샀어요·팔았어요' 금액(그때 가격) + 그 밖에 바뀐 수량(직접 고치기·추가·삭제)×그날 가격. 첫날은 그날 평가액.
  // 이 브라우저에만 쌓이고 백업 파일·기기 동기화에 같이 들어간다. 앱(iOS)과 같은 모양. 바뀐 게 새 날이면 true (그때만 저장)
  function recordDay() {
    const st = S.state, tr = S.pendTrade; S.pendTrade = null;
    if (st.sample) return false;
    const q = {};
    for (const r of valuation().rows) { if (!(r.sh > 0)) continue; if (!(r.valueKrw > 0)) return false; q[r.h.ticker] = [r.sh, Math.round((r.valueKrw / r.sh) * 100) / 100]; }
    const D = (st.daily = st.daily || []), d = today();
    if (D.length && D[D.length - 1].d > d) return false; // 기기 시계가 거꾸로 가면 건드리지 않는다
    const cur = D.length && D[D.length - 1].d === d ? D[D.length - 1] : null, prev = D[D.length - (cur ? 2 : 1)];
    if (!prev && !Object.keys(q).length) return false;
    const f = { ...(cur?.f || {}) };
    if (tr) { const a = f[tr.t] || [0, 0]; f[tr.t] = [a[0] + tr.q, a[1] + tr.k]; }
    const pq = prev?.q || {};
    let c = prev ? prev.c : 0, v = 0;
    for (const t of new Set([...Object.keys(q), ...Object.keys(pq), ...Object.keys(f)])) {
      const now = q[t]?.[0] || 0, fl = f[t] || [0, 0], px = q[t]?.[1] ?? pq[t]?.[1] ?? (fl[0] ? fl[1] / fl[0] : 0);
      c += fl[1] + (now - (pq[t]?.[0] || 0) - fl[0]) * px; v += now * (q[t]?.[1] || 0);
    }
    const rec = { d, v: Math.round(v), c: Math.round(c), q };
    if (Object.keys(f).length) rec.f = f;
    if (cur) D[D.length - 1] = rec; else D.push(rec);
    if (D.length > 1200) D.splice(0, D.length - 1200);
    return !cur;
  }
  // 동기화로 다른 기기 값을 받을 때: 날짜별로 합친다 (같은 날은 받은 쪽)
  const mergeKr = (a, b) => Model.krNorm([...(b || []), ...(a || [])]); // 국내 주식 합계 기록도 날짜별로 (같은 날은 받은 쪽 a)
  const mergeDaily = (a, b) => { const m = new Map(); (b || []).forEach((x) => m.set(x.d, x)); (a || []).forEach((x) => m.set(x.d, x)); return [...m.values()].sort((x, y) => (x.d < y.d ? -1 : 1)).slice(-1200); };
  function save(dirtyForecast = true) {
    recordLot(); recordDay();
    if (dirtyForecast) markDirty();
    clearTimeout(saveTimer);
    saveTimer = setTimeout(async () => {
      if (MODE === "static") {
        syncMark();
        try { localStorage.setItem(LS_KEY, JSON.stringify(S.state)); $("#footer").textContent = "이 브라우저에 저장됨 " + new Date().toLocaleTimeString() + " · 다른 기기에서 쓰려면 위 '입력값 저장·불러오기'"; }
        catch (e) { $("#footer").textContent = "브라우저 저장 실패: " + e.message; }
        return;
      }
      try { await api("/api/state", S.state); $("#footer").textContent = "저장됨 " + new Date().toLocaleTimeString(); }
      catch (e) { $("#footer").textContent = "저장 실패: " + e.message + " (프로그램 창이 켜져 있는지 확인)"; }
    }, 400);
  }

  // ------------------------------------------------------------ 개발자 기기 자동 동기화
  // GitHub 토큰(저장소 쓰기 권한)이 있는 기기끼리만 중계 KV 에 입력값을 두고 맞춘다. 일반 사용자는 해당 없음.
  // 나중에 고친 쪽이 이긴다 (ui.sync_at). 화면 설정(ui)은 기기마다 따로.
  // 일반 사용자: 동기화 비밀번호로 기기 안에서 암호화(AES-GCM)한 값만 중계에 둔다. 중계는 내용을 읽을 수 없다.
  let syncLast = null, syncTimer = null;
  const ES_KEY = "naeilo-esync";
  const esGet = () => { try { return JSON.parse(localStorage.getItem(ES_KEY) || "null"); } catch (e) { return null; } };
  const relay = () => (S.config?.push ? S.config.push.replace(/\/$/, "") : null);
  const syncDev = () => !!(MODE === "static" && GH && ghToken());
  const syncUrl = () => (MODE !== "static" || !relay() ? null : syncDev() ? relay() + "/sync" : esGet() ? relay() + "/esync?id=" + esGet().id : null);
  const syncSig = () => hashStr(JSON.stringify({ ...S.state, ui: null }));
  const b64 = (u8) => btoa(String.fromCharCode(...new Uint8Array(u8))), unb64 = (t) => Uint8Array.from(atob(t), (c) => c.charCodeAt(0));
  async function esDerive(pw) { // 비밀번호 → 저장 위치(id) + 암호 키. 비밀번호 자체는 어디에도 저장하지 않는다
    const base = await crypto.subtle.importKey("raw", new TextEncoder().encode(pw.normalize("NFC")), "PBKDF2", false, ["deriveBits"]);
    const bits = new Uint8Array(await crypto.subtle.deriveBits({ name: "PBKDF2", salt: new TextEncoder().encode("naeilo-sync-v1"), iterations: 300000, hash: "SHA-256" }, base, 512));
    const id = [...new Uint8Array(await crypto.subtle.digest("SHA-256", bits.slice(0, 32)))].slice(0, 20).map((b) => b.toString(16).padStart(2, "0")).join("");
    return { id, k: b64(bits.slice(32)) };
  }
  const esKey = () => crypto.subtle.importKey("raw", unb64(esGet().k), "AES-GCM", false, ["encrypt", "decrypt"]);
  async function syncCall(opt = {}) {
    let body = opt.body;
    if (!syncDev() && body) { const o = JSON.parse(body), iv = crypto.getRandomValues(new Uint8Array(12)); const c = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, await esKey(), new TextEncoder().encode(JSON.stringify(o.state))); body = JSON.stringify({ c: b64(iv) + "." + b64(c), at: o.at }); }
    const r = await fetch(syncUrl(), { ...opt, body, headers: { "Content-Type": "application/json", ...(syncDev() ? { Authorization: "Bearer " + ghToken() } : {}) } });
    const j = await r.json().catch(() => ({})); if (!r.ok) throw new Error(j.error || "중계 " + r.status);
    if (!syncDev() && j.c) { const [iv, c] = j.c.split("."); try { j.state = JSON.parse(new TextDecoder().decode(await crypto.subtle.decrypt({ name: "AES-GCM", iv: unb64(iv) }, await esKey(), unb64(c)))); } catch (e) { throw new Error("풀 수 없음 (비밀번호 확인)"); } }
    if (j.state && j.state._w) delete j.state._w; // 예전 위젯 요약이 남아 있는 동기화 자료도 입력값만 받는다
    return j;
  }
  function syncMark() {
    if (!syncUrl() || syncLast == null) return;
    const sig = syncSig(); if (sig === syncLast) return;
    syncLast = sig; S.state.ui.sync_at = Date.now();
    clearTimeout(syncTimer); syncTimer = setTimeout(syncPush, 4000);
  }
  async function syncPush() {
    if (!syncUrl()) return;
    try {
      const st = { ...S.state }; delete st.ui;
      const j = await syncCall({ method: "POST", body: JSON.stringify({ state: st, at: S.state.ui.sync_at || Date.now() }) }); S.sync = { ok: true, at: Date.now() };
      if (j.stale) syncPull();
    }
    catch (e) { S.sync = { ok: false, err: e.message }; }
    if (onTab("settings")) { renderGh(); renderEsync(); }
  }
  async function syncPull() {
    if (!syncUrl()) return;
    if (syncLast == null) syncLast = syncSig();
    try {
      const j = await syncCall(), mine = Number(S.state.ui.sync_at) || 0;
      if (j.state && j.at > mine && hashStr(JSON.stringify({ ...normalize(j.state), ui: null })) !== syncLast) {
        // 이 기기에서 처음 켤 때만 묻는다 (이 기기 값이 더 맞을 수 있으니)
        if (!mine && !confirm(`다른 기기 입력값(${new Date(j.at).toLocaleString()})으로 이 기기를 맞출까요?\n취소하면 이 기기 값을 다른 기기로 보냅니다.`)) { S.state.ui.sync_at = Date.now(); save(false); return syncPush(); }
        const ui = S.state.ui, myDaily = S.state.daily, myKr = S.state.kr_log; S.state = normalize(j.state); S.state.ui = { ...ui, sync_at: j.at }; if (myDaily?.length) S.state.daily = mergeDaily(S.state.daily, myDaily); if (myKr?.length) { S.state.kr_log = mergeKr(S.state.kr_log, myKr); krHold(); } syncLast = syncSig();
        try { localStorage.setItem(LS_KEY, JSON.stringify(S.state)); } catch (e) { /* 무시 */ }
        markDirty(); renderAll(); toast("다른 기기 입력값으로 맞췄습니다");
      } else if (!j.state || j.at < mine) { if (!mine) S.state.ui.sync_at = Date.now(); await syncPush(); }
      S.sync = { ok: true, at: Date.now() };
    } catch (e) { S.sync = { ok: false, err: e.message }; }
    if (onTab("settings")) { renderGh(); renderEsync(); }
  }
  function renderEsync() {
    const box = $("#esyncBox"); if (!box) return;
    if (MODE !== "static" || !relay()) { box.style.display = "none"; return; }
    if (syncDev()) { box.innerHTML = `<p class="small"><b>기기 자동 동기화</b>: 개발자 GitHub 연결로 동기화 중.</p>`; return; }
    const on = !!esGet();
    box.innerHTML = `<p class="small"><b>기기 자동 동기화</b> ${on ? (S.sync?.ok ? `<b class="good">켜짐</b> · 마지막 ${new Date(S.sync.at).toLocaleTimeString()}` : S.sync ? `<span class="dn">오류: ${esc(S.sync.err)}</span>` : "켜짐") : ""}<br>
      ${on ? "같은 비밀번호를 넣은 기기끼리 입력값이 자동으로 맞춰집니다." : "쓰는 기기마다 같은 동기화 비밀번호를 넣으면 입력값이 자동으로 맞춰집니다. 이 기기에서 암호화한 값만 서버에 두어 서버는 보유 내역을 볼 수 없고, 비밀번호를 잊으면 복구할 수 없습니다."}</p>
      <div class="row wrap">${on ? `<button id="esOff" class="danger sm">이 기기 동기화 끄기</button>` : `<input id="esPw" type="password" size="22" placeholder="동기화 비밀번호 (10자 이상)" autocomplete="new-password"><button id="esOn" class="primary sm">켜기</button>`}</div>`;
    if (on) $("#esOff").onclick = () => { try { localStorage.removeItem(ES_KEY); } catch (e) { /* 무시 */ } S.sync = null; S.state.ui.sync_at = 0; save(false); renderEsync(); toast("이 기기 동기화를 껐습니다"); };
    else $("#esOn").onclick = async () => {
      const pw = $("#esPw").value; if (pw.length < 10) return toast("비밀번호는 10자 이상 (남이 짐작하기 어렵게)");
      $("#esOn").disabled = true; $("#esOn").textContent = "준비 중…";
      try { localStorage.setItem(ES_KEY, JSON.stringify(await esDerive(pw))); } catch (e) { toast("이 브라우저에서 쓸 수 없습니다"); return renderEsync(); }
      S.state.ui.sync_at = 0; syncLast = null; await syncPull(); renderEsync();
    };
  }

  // ------------------------------------------------------------ 평가
  const fxOf = (ccy) => (!ccy || ccy === "KRW" ? null : ccy === "USD" ? "KRW=X" : ccy + "KRW=X");
  const ccyOf = (t) => (isKR(t) ? "KRW" : S.prices[t]?.currency || S.quotes[t]?.currency || "USD");
  const lastOf = (sym) => { const p = S.prices[sym]; return p && p.close.length ? { v: p.close[p.close.length - 1], d: p.dates[p.dates.length - 1] } : null; };
  function fxNow(ccy) {
    const s = fxOf(ccy); if (!s) return 1;
    const q = S.quotes[s]; if (q && q.last) return q.last;
    const l = lastOf(s); return l ? l.v : null;
  }
  // 매매기준율(서울외국환중개 시장평균환율). 5일 넘게 묵었거나 없으면 null → 기존 환율로 대체
  const marUsd = () => { const m = S.mar?.USD; return m && m.rate > 0 && m.date && yearsBetween(m.date, today()) * 365 <= 5 ? m : null; };
  const fxBase = (ccy) => (ccy === "USD" && marUsd() ? marUsd().rate : fxNow(ccy));
  async function marFetch() { // 저장소 값이 없거나 묵었으면 중계에서 한 번 더
    const base = S.config?.push; if ((marUsd() && marUsd().date >= today()) || !base) return; // 오늘 고시가 아직 저장소에 없으면 중계에서 한 번 더
    try { const r = await fetch(base.replace(/\/$/, "") + "/mar"); const j = await r.json(); if (j?.USD?.rate > 0 && (!S.mar?.USD?.date || j.USD.date > S.mar.USD.date)) { S.mar = j; renderAll(); } } catch (e) { /* 기존 환율 유지 */ }
  }
  function curPrice(h) {
    if (h.ticker === KR_SUM) return { v: Model.krAt(S.state.kr_log, today()) || null, src: "input" };
    if (S.state?.ui?.manual_price && !isKR(h.ticker) && h.price != null && h.price !== "" && Number(h.price) > 0) return { v: Number(h.price), src: "manual" };
    const q = S.quotes[h.ticker], l = lastOf(h.ticker);
    if (q && q.last && (!l || (q.last_time || 0) * 1000 >= Date.parse(l.d))) return { v: q.last, src: q.last_session || "regular", t: q.last_time };
    if (l) return { v: l.v, src: "close", d: l.d };
    if (q && q.last) return { v: q.last, src: q.last_session || "regular", t: q.last_time };
    return { v: null, src: null };
  }
  function valuation() {
    const rows = S.state.holdings.map((h) => {
      const ccy = ccyOf(h.ticker), p = curPrice(h), fx = fxBase(ccy), sh = Number(h.shares) || 0;
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
    const total = valuation().total + cashKrw(), g = S.state.goal, fx = fxNow("USD");
    const prog = g.amount ? total / g.amount : 0;
    const my = myReturn();
    $("#headKpi").innerHTML = `<span class="hasset" id="hasset" role="button" title="누르면 금액 ${hideAmt() ? "보이기" : "감추기"}">자산 <b>${hideAmt() ? "•••" : krw(total)}</b>${my ? ` <b class="${cls(my.r)}">${spct(my.r)}</b>` : ""}</span><span class="hpill" id="hpill" role="button" title="목표 ${krw(g.amount)} 대비 · 누르면 시세 수집"><i style="width:${Math.max(0, Math.min(100, prog * 100)).toFixed(1)}%"></i><b>${pct(prog)}</b></span>`;
  }

  // ------------------------------------------------------------ 시세·종목
  function logLine(msg, ok = true, html = false) {
    const box = $("#collectLog"), div = document.createElement("div");
    div.innerHTML = `<span class="t">${new Date().toLocaleTimeString()}</span> ${html ? msg : esc(msg)}`;
    if (!ok) div.className = "err";
    box.appendChild(div); box.scrollTop = box.scrollHeight;
    const last = $("#logLast"); if (last) last.textContent = "· " + div.textContent.trim(); // 접힌 채로도 마지막 줄은 보이게
  }
  function symbolsToCollect(list) {
    const ts = (list || S.state.holdings.map((h) => h.ticker)).filter((t) => !isKR(t));
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
        S.prices = prices; S.siteClose = lastCloses(prices); S.quotes = await ghApi("contents/data/quotes.json?ref=main", { raw: true }); S.dataUpdated = idx.updated;
        S.tickerCfg = await ghApi("contents/data/tickers.json?ref=main", { raw: true });
        S.mar = await ghApi("contents/data/mar.json?ref=main", { raw: true }).catch(() => S.mar);
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
  // 마지막으로 성공한 중계를 기억해 다음 종목은 그곳부터 시도한다 (실패한 중계를 종목마다 다시 기다리지 않게)
  let goodProxy = null;
  async function yahoo(sym, params) {
    const url = "https://query1.finance.yahoo.com/v8/finance/chart/" + encodeURIComponent(sym) + "?" + new URLSearchParams(params);
    let last = null;
    const list = proxies(), order = goodProxy && list.includes(goodProxy) ? [goodProxy, ...list.filter((p) => p !== goodProxy)] : list;
    for (const p of order) {
      const ctl = new AbortController(), t = setTimeout(() => ctl.abort(), 8000);
      try {
        const r = await fetch(p + encodeURIComponent(url), { signal: ctl.signal, cache: "no-store" });
        const j = await r.json().catch(() => null); // 본문을 다 받을 때까지 시간 제한을 유지한다
        if (j && j.chart) { goodProxy = p; if (!j.chart.result) throw Object.assign(new Error("티커를 찾지 못함"), { notFound: true }); return j.chart.result[0]; }
        last = new Error("중계 응답 " + r.status);
      } catch (e) { if (e.notFound) throw e; last = e; }
      finally { clearTimeout(t); }
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
    // 한국 종목은 시세를 받지 않는다 (계약 없음): 예전에 이 브라우저가 Yahoo 에서 받아 둔 값도 지운다
    if (Object.keys(x.prices).concat(Object.keys(x.quotes)).some(isKR)) { for (const k of Object.keys(x.prices)) if (isKR(k)) delete x.prices[k]; for (const k of Object.keys(x.quotes)) if (isKR(k)) delete x.quotes[k]; saveExtra(x); }
    for (const [k, p] of Object.entries(x.prices)) { const cur = S.prices[k]; if (!cur || (p.dates.at(-1) || "") > (cur.dates.at(-1) || "")) S.prices[k] = p; }
    for (const [k, q] of Object.entries(x.quotes)) { if (!S.quotes[k] || (q.last_time || 0) > (S.quotes[k].last_time || 0)) S.quotes[k] = q; }
  }
  let bBusy = false;
  async function browserCollect(newSyms) {
    if (bBusy) return; bBusy = true;
    const btns = [$("#btnCollect")]; btns.forEach((b) => b && (b.disabled = true));
    const x = loadExtra(), held = S.state.holdings.map((h) => h.ticker).filter((t) => !isKR(t));
    newSyms = newSyms.filter((t) => !isKR(t));
    const need = [...new Set([...newSyms, ...held.filter((t) => !S.prices[t])])];
    let ok = 0, bad = [];
    logLine(`시세 받는 중: ${esc([...new Set([...need, ...held])].join(", "))}`, true, true);
    // 종목마다 기다리지 않고 한꺼번에 요청한다
    await Promise.all(need.map(async (t) => {
      try { const h = await browserHistory(t); if (!h.dates.length) throw new Error("일봉 없음"); x.prices[t] = h; S.prices[t] = h; ok++; }
      catch (e) { bad.push(`${t} (${e.message})`); }
    }));
    // 새 통화의 환율
    const fxNeed = [...new Set(held.map((t) => fxOf(ccyOf(t))).filter((f) => f && !S.prices[f]))];
    await Promise.all(fxNeed.map(async (f) => { try { const h = await browserHistory(f); x.prices[f] = h; S.prices[f] = h; } catch (e) { bad.push(`${f} (${e.message})`); } }));
    // 현재가 (보유 종목 + 환율)
    await Promise.all(symbolsToCollect(held).map(async (t) => { try { const q = await browserQuote(t); x.quotes[t] = q; S.quotes[t] = q; ok++; } catch (e) { /* 지난 종가로 계속 보인다 */ } }));
    saveExtra(x);
    markDirty(); renderAll();
    logLine(bad.length ? `받지 못한 항목: ${esc(bad.join(", "))}. 티커를 확인하거나 잠시 뒤 다시 '시세 수집'을 눌러 주세요.` : "시세를 받았습니다.", !bad.length, true);
    bBusy = false; btns.forEach((b) => b && (b.disabled = false));
  }
  function missingTickers() {
    if (MODE !== "static") return [];
    const have = new Set((S.tickerCfg?.tickers || []).map((t) => t.toUpperCase()));
    return S.state.holdings.map((h) => h.ticker).filter((t) => !isKR(t) && !have.has(t) && !S.prices[t]);
  }
  async function collectStatic(manual) {
    // 개발자용 토큰이 있어도 내 보유 종목 시세는 브라우저에서 바로 받는다.
    // 저장소에 없는 새 종목이 있을 때만 GitHub 수집(전체 종목을 도는 작업)을 뒤에서 실행한다
    if (GH && ghToken()) { if (manual) { const miss = missingTickers(); await browserCollect(miss); if (miss.length) ghCollect(miss); } return; }
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
  // 샘플 끝내기: keep=true 면 샘플 종목을 남기고(사용자가 그 수량을 고친 경우) 표시만 지운다
  function endSample(keep) {
    if (!S.state.sample) return;
    if (!keep) S.state.holdings = S.state.holdings.filter((h) => !h.sample);
    if (!S.state.holdings.some((h) => h.sample)) delete S.state.weekly; // 샘플 종목이 없어지면 샘플 수량으로 적은 주간 예측도 지운다 (수량만 고친 경우는 남겨 다시 맞춘다)
    S.state.holdings.forEach((h) => delete h.sample); delete S.state.sample;
    if (S.state.sample_goal) { if (S.state.goal.amount === SAMPLE_GOAL || S.state.goal.amount === 1e8) S.state.goal.amount = S.state.sample_goal; delete S.state.sample_goal; } // 샘플 동안 목표를 고치지 않았으면 원래 목표로
    lastForecast = null; fcDirty = true; try { localStorage.removeItem(FC_KEY); } catch (e) { /* 무시 */ } // 샘플로 계산한 전망을 버려 전망 탭을 열면 내 종목으로 다시 계산
  }
  // 종목 로고: 티커로 받는 무료 로고 이미지 → 다른 곳 → 실패하면 첫 글자
  const LOGO_DOMAIN = { SPCX: "spacex.com" };
  // 가상 종목 DRNK 는 바깥 로고를 찾지 않는다 (같은 티커의 다른 회사 로고가 나올 수 있어 첫 글자로)
  const LOGO_SRC = (t) => t === "DRNK" ? [] : [...(LOGO_DOMAIN[t] ? [`https://www.google.com/s2/favicons?domain=${LOGO_DOMAIN[t]}&sz=64`] : []), `https://financialmodelingprep.com/image-stock/${encodeURIComponent(t)}.png`, `https://assets.parqet.com/logos/symbol/${encodeURIComponent(t)}?format=png`];
  window.logoErr = (img) => { const n = LOGO_SRC(img.dataset.t)[+img.dataset.k + 1]; if (n) { img.dataset.k = +img.dataset.k + 1; img.src = n; } else img.remove(); };
  const logo = (t, name) => `<span class="tlogo" data-c="${esc(t.replace(/[^A-Z0-9]/gi, "").slice(0, 1))}" title="${esc(name || t)}">${LOGO_SRC(t).length ? `<img src="${LOGO_SRC(t)[0]}" data-t="${esc(t)}" data-k="0" alt="" loading="lazy" onerror="logoErr(this)">` : ""}</span>`;
  // 정렬: 표에서 보이는 순서만 바꾼다 (data-i 는 원래 순서 번호, 그래프 색은 넣은 순 그대로)
  const HOLD_SORT = {
    value: (a, b) => (b.valueKrw || 0) - (a.valueKrw || 0),
    name: (a, b) => a.h.ticker.localeCompare(b.h.ticker),
    ret: (a, b) => (b.plPct ?? -Infinity) - (a.plPct ?? -Infinity),
    today: (a, b) => (b.dayChg ?? -Infinity) - (a.dayChg ?? -Infinity),
  };
  function renderQuotes() {
    const { rows, total } = valuation();
    const ks = S.state.holdings.find((h) => h.ticker === KR_SUM);
    $("#krSumRow").hidden = !S.state.ui.kr_direct && !ks;
    if (document.activeElement !== $("#krSum")) $("#krSum").value = ks ? nf(Number(ks.price)) : "";
    const fx = fxNow("USD"), fq = S.quotes["KRW=X"];
    $("#fxLine").textContent = `원/달러 ${nf(fx, 2)}${fq?.last_time ? " (" + dtStr(fq.last_time * 1000) + ")" : ""}`;
    const adv = !!S.state.ui.manual_price, hasPl = rows.some((r) => r.pl != null); // 매수 단가가 하나도 없으면 손익 칸은 숨김
    const sortBy = S.state.ui.hold_sort || "added"; $("#holdSort").value = sortBy;
    const order = rows.map((r, i) => ({ ...r, i }));
    if (HOLD_SORT[sortBy]) order.sort(HOLD_SORT[sortBy]);
    const cols = 6 + (adv ? 1 : 0) + (hasPl ? 1 : 0);
    const head = `<tr><th class="l">종목</th><th>수량</th><th>매수 단가</th><th>현재가<br><span class="muted">평가액</span></th>${adv ? "<th>현재가 직접 입력</th>" : ""}<th>비중<br><span class="muted">전일</span></th>${hasPl ? "<th>손익</th>" : ""}<th></th></tr>`;
    const body = order.map((r) => {
      const i = r.i, open = S.holdEdit?.i === i;
      const mk = r.p.src === "manual" ? curPrice({ ...r.h, price: null }).v : null;
      const gap = mk ? r.p.v / mk - 1 : 0;
      const warn = Math.abs(gap) > 0.05 ? `<br><span class="bad small">시세 ${nf(mk, 2)}와 ${spct(gap, 0)} 차이</span>` : "";
      const p = r.p, tag = p.src && p.src !== "regular" ? `<span class="tag ${p.src === "manual" ? "manual" : ""}">${SESS[p.src] || p.src}</span>` : "";
      const pl = r.pl != null ? `<span class="${cls(r.pl)}" title="${nf(r.pl, 0)} ${r.ccy}">${spct(r.plPct, 0)}</span><br><span class="muted small">${krw(r.pl * (r.fx || 1))}</span>` : `<span class="muted">-</span>`;
      // 수량·단가는 글자로만 보인다. 누르면 ✎ 와 같이 그 줄 아래에 고치기 칸이 펼쳐진다 (산 것·판 것을 구분해 남기려고)
      return `<tr data-i="${i}"${open ? ' class="hon"' : ""}>
        <td class="l tkc">${logo(r.h.ticker, r.name)}<b>${esc(isKR(r.h.ticker) ? r.h.name || r.name || codeOf(r.h.ticker) : r.h.ticker)}</b></td>
        <td class="hnum"${r.h.ticker === KR_SUM ? "><span class='muted'>합계</span>" : ` data-pen="${i}">`}${r.h.ticker === KR_SUM ? "" : r.sh ? r.sh.toLocaleString("ko-KR", { maximumFractionDigits: 4 }) : '<span class="muted">수량 없음</span>'}</td>
        <td class="hnum" data-pen="${i}">${r.avg ? nf(r.avg, 2) : '<span class="muted">-</span>'}</td>
        <td>${isKR(r.h.ticker) ? krCell(r) : `${p.v != null ? nf(p.v, 2) + " <span class='muted small'>" + r.ccy + "</span>" : "-"}${tag}<br><b>${krw(r.valueKrw)}</b>`}</td>
        ${adv && isKR(r.h.ticker) ? "<td></td>" : adv ? `<td><input data-f="price" type="text" inputmode="decimal" placeholder="자동" value="${r.h.price ?? ""}">${warn}</td>` : ""}
        <td>${pct(r.w, 0)}<br><span class="${cls(r.dayChg)}">${spct(r.dayChg, 1)}</span></td>${hasPl ? `<td>${pl}</td>` : ""}<td class="hbtn"><button class="x pen${open ? " on" : ""}" data-pen="${i}" title="수량·단가 고치기">✎</button><button class="danger x" data-del="${i}" title="이 종목 삭제">✕</button></td></tr>` +
        (open ? holdEditRow(r, cols) : "");
    }).join("");
    const plTot = rows.filter((r) => r.pl != null).reduce((s, r) => s + r.pl * (r.fx || 1), 0);
    const foot = `<tr><td class="l"><b>합계</b></td><td></td><td></td><td><b>${krw(total)}</b></td>${adv ? "<td></td>" : ""}<td>100%</td>${hasPl ? `<td><span class="${cls(plTot)}">${krw(plTot)}</span></td>` : ""}<td></td></tr>`;
    $("#holdTable").innerHTML = head + body + foot;
    renderTrades();
  }
  // 한국 종목 현재가 칸: 넣은 가격 하나로 계산 (추정·입력 표시). 가격은 언제든 다시 넣을 수 있다
  function krCell(r) {
    const P = S.prices[r.h.ticker], inp = (ph) => `<input data-f="kprice" type="text" inputmode="decimal" placeholder="${ph}" size="8">`;
    if (!P || r.p.v == null) return `${inp("지금 가격(원)")}<br><span class="muted small">한국 시세는 받지 않아요. 가격을 한 번 넣으면 계산해요</span>`;
    if (r.h.ticker === KR_SUM) { const L = S.state.kr_log || [], ld = L[L.length - 1]?.d, ago = ld ? Math.round(yearsBetween(ld, today()) * 365.25) : 0;
      return `<span class="tag" title="${esc(`${ld || ""}에 넣은 금액 · 전망은 EWY×원/달러`)}">입력</span><br><b>${krw(r.valueKrw)}</b>${ago > 0 ? `<br><span class="muted small">${ago}일 전 금액</span>` : ""}<br>${inp("합계 다시 넣기")}`; }
    const tip = P.est === "proxy" ? `${r.h.price_date || ""}에 넣은 ${nf(Number(r.h.price))}원에서 ${P.basis}${P.hedged ? "" : " × 원/달러"} 움직임으로 추정한 값이에요` : `${r.h.price_date || ""}에 넣은 가격 그대로예요. 움직임 예측은 한국 시장 평균(${P.basis})으로 해요`;
    return `${nf(r.p.v, 0)} <span class='muted small'>KRW</span><span class="tag" title="${esc(tip)}">${P.est === "proxy" ? "추정" : "입력"}</span><br><b>${krw(r.valueKrw)}</b><br>${inp("가격 다시 넣기")}`;
  }
  // ✎ 고치기: 직접 고치기(오타·처음 입력, 매매로 세지 않음) / 더 샀어요 / 팔았어요 (iOS HoldingEdit 와 같은 계산)
  const HOLD_MODES = { fix: "직접 고치기", buy: "더 샀어요", sell: "팔았어요" };
  const numStr = (v) => (v == null || !isFinite(v) ? "" : String(Math.round(v * 1e4) / 1e4));
  function holdEditStart(i, mode) {
    const h = S.state.holdings[i]; if (!h) return;
    const px = curPrice(h).v;
    S.holdEdit = mode === "fix" ? { i, mode, q: numStr(Number(h.shares) || 0), p: h.avg_cost ? numStr(Number(h.avg_cost)) : "" } : { i, mode, q: "", p: numStr(px) };
  }
  function holdEditCalc() {
    const E = S.holdEdit, h = S.state.holdings[E.i], ccy = ccyOf(h.ticker);
    const q0 = Number(h.shares) || 0, p0 = Number(h.avg_cost) > 0 ? Number(h.avg_cost) : null;
    const qi = parseDec(E.q), pi = parseDec(E.p), money = (v) => `${nf(v, 2)} ${ccy}`;
    let q1 = q0, p1 = p0, bad = false, note = "";
    if (E.mode === "fix") {
      bad = !(qi >= 0) || (E.p !== "" && !(pi > 0));
      if (!bad) { q1 = qi; p1 = E.p === "" ? null : pi; note = `수량 ${nf(q0)} → ${nf(q1)}주${p1 !== p0 ? `, 매수 단가 ${p0 ? money(p0) : "없음"} → ${p1 ? money(p1) : "없음"}` : ""}. 매매로 세지 않아요.`; }
    } else if (E.mode === "buy") {
      bad = !(qi > 0 && pi > 0);
      if (!bad) {
        q1 = q0 + qi; p1 = p0 ? (q0 * p0 + qi * pi) / q1 : q0 ? null : pi;
        note = `수량 ${nf(q0)} → ${nf(q1)}주, ${p1 ? `평균 단가 ${p0 ? money(p0) : "없음"} → ${money(p1)}` : "평균 단가는 이전 단가가 없어 비워 둬요"}.`;
      }
    } else {
      bad = !(qi > 0 && qi <= q0 && pi > 0);
      if (!bad) {
        q1 = q0 - qi;
        const g = p0 ? qi * (pi - p0) * (fxNow(ccy) || 1) : null;
        note = `수량 ${nf(q0)} → ${nf(q1)}주${q1 ? "" : " (종목이 목록에서 빠져요)"}. ` + (g == null ? "매수 단가가 없어 판 부분 손익은 계산하지 않아요." : `판 부분의 ${g >= 0 ? "이익" : "손실"}은 ${krw(Math.abs(g))}원이에요.` + (ccy === "KRW" ? " 국내 상장주식은 대주주가 아니면 양도세가 없어요." : " 올해 해외주식 실현 이익에 더해 세금 계산에 반영돼요."));
      } else if (qi > q0) note = `가진 수량(${nf(q0)}주)보다 많이 팔 수는 없어요.`;
    }
    return { qi, pi, q1, p1, bad, note, ccy };
  }
  function holdEditRow(r, cols) {
    const E = S.holdEdit, c = holdEditCalc(), trade = E.mode !== "fix";
    return `<tr class="hedit"><td colspan="${cols}" class="l">
      <div class="row hedbox"><div class="seg">${Object.entries(HOLD_MODES).map(([k, v]) => `<button data-hmode="${k}" class="${E.mode === k ? "on" : ""}">${v}</button>`).join("")}</div>
      <label>${trade ? (E.mode === "buy" ? "산 수량" : "판 수량") : "수량"} <input data-e="q" type="text" inputmode="decimal" value="${esc(E.q)}" placeholder="${trade ? "몇 주" : "수량"}" size="6"></label>
      <label>${trade ? "가격" : "매수 단가"} <input data-e="p" type="text" inputmode="decimal" value="${esc(E.p)}" placeholder="${trade ? "주당" : "선택"}" size="7"> <span class="muted small">${c.ccy}</span></label>
      <button class="primary" data-esave ${c.bad ? "disabled" : ""}>저장</button><button data-eclose>닫기</button></div>
      <div class="hpv muted small">${esc(c.note)}</div></td></tr>`;
  }
  function holdEditSave() {
    const E = S.holdEdit, c = holdEditCalc(); if (c.bad) return;
    const h = S.state.holdings[E.i];
    if (S.state.sample) endSample(true);
    if (E.mode !== "fix") {
      // 산 것·판 것만 따로 남긴다 (나중에 평정 지수가 이것만 센다. 직접 고치기는 남기지 않음)
      const log = (S.state.trade_log = S.state.trade_log || []);
      log.push({ d: today(), t: h.ticker, q: E.mode === "buy" ? c.qi : -c.qi, px: c.pi });
      S.pendTrade = { t: h.ticker, q: E.mode === "buy" ? c.qi : -c.qi, k: (E.mode === "buy" ? 1 : -1) * c.qi * c.pi * (fxBase(c.ccy) || 1) }; // 내 기록의 넣은 돈
      if (log.length > 1000) log.splice(0, log.length - 1000);
    }
    h.shares = c.q1;
    if (c.p1 > 0) h.avg_cost = Math.round(c.p1 * 1e4) / 1e4; else delete h.avg_cost;
    const msg = E.mode === "fix" ? `${h.ticker} 고침` : `${h.ticker} ${c.qi}주 ${E.mode === "buy" ? "산" : "판"} 것 기록함`;
    if (!(c.q1 > 0) && E.mode === "sell") S.state.holdings.splice(E.i, 1);
    S.holdEdit = null; toast(msg);
    save(); renderAll();
  }
  function onHoldEdit(e) {
    const ed = e.target.dataset.e;
    if (ed && S.holdEdit) { // 고치기 칸: 전체를 다시 그리지 않고 미리 보기·저장 버튼만 바꾼다
      S.holdEdit[ed] = e.target.value;
      const c = holdEditCalc(), box = e.target.closest("tr.hedit");
      box.querySelector(".hpv").textContent = c.note; box.querySelector("[data-esave]").disabled = c.bad;
      return;
    }
    const tr = e.target.closest("tr[data-i]"); if (!tr) return;
    const h = S.state.holdings[+tr.dataset.i], f = e.target.dataset.f; if (!f) return;
    if (f === "kprice") {
      if (e.type !== "change") return;
      const v = parseDec(e.target.value); if (!(v > 0)) return;
      if (h.ticker === KR_SUM) { setTimeout(() => setKrSum(v), 0); return; }
      h.price = v; h.price_date = today(); synthKR(); markDirty(); save(); setTimeout(renderAll, 0); return; // 입력칸이 바뀌는 중에 표를 다시 그리지 않게
    }
    if (S.state.sample && f !== "note") endSample(true);
    if (f === "note") h.note = e.target.value;
    else { const v = parseDec(e.target.value); if (v === undefined) return; h[f] = v; }
    save(f !== "note"); renderHeader();
    if (e.type === "change") renderQuotes();
  }
  function onHoldClick(e) {
    const d = e.target.closest("[data-del]"); if (d) { if (S.holdEdit?.i === +d.dataset.del) S.holdEdit = null; return delHolding(+d.dataset.del, d); }
    const pen = e.target.closest("[data-pen]");
    if (pen) { const i = +pen.dataset.pen; if (S.holdEdit?.i === i && pen.tagName === "BUTTON") S.holdEdit = null; else if (S.holdEdit?.i !== i) holdEditStart(i, "fix"); renderQuotes(); const inp = $("#holdTable [data-e='q']"); if (inp) inp.focus(); return; }
    const m = e.target.closest("[data-hmode]"); if (m && S.holdEdit) { holdEditStart(S.holdEdit.i, m.dataset.hmode); renderQuotes(); $("#holdTable [data-e='q']")?.focus(); return; }
    if (e.target.closest("[data-esave]")) return holdEditSave();
    if (e.target.closest("[data-eclose]")) { S.holdEdit = null; renderQuotes(); }
  }
  // 새 종목: 그 종목 분기 실적 사건을 함께 넣는다 (날짜는 1·4·7·10월 하순으로 추정). 지수·현금성 ETF 는 제외
  function addEarnEvent(t) {
    if (INS_SKIP.has(t) || isKR(t) || t.includes("=") || S.state.events.some((e) => e.target === t && /실적/.test(e.kind))) return;
    const d = new Date(today()); let y = d.getFullYear(), m = [0, 3, 6, 9].find((k) => k > d.getMonth() || (k === d.getMonth() && d.getDate() < 25));
    if (m == null) { m = 0; y++; }
    S.state.events.push({ id: "auto_" + t, on: true, date: `${y}-${String(m + 1).padStart(2, "0")}-25`, target: t, kind: "실적", cat: "corp", repeat: "quarterly", prob: 100, mean: 0, sd: 8, vol_mult: 1, vol_days: 0, note: "분기 실적 (날짜 추정)" });
  }
  // 한국 종목 (시세 계약 없음, data/kr_proxy.json · iOS KoreaEstimate 와 같은 규칙): 한국 가격은 받지 않고 사용자가 넣은 가격 하나에 맞춘다
  //  · 미국 지수를 따르는 국내 상장 ETF: 그 미국 ETF(SPY·QQQ 등) × 원/달러 움직임으로 오늘 가격을 추정 (환헤지 (H)면 환율 빼고)
  //  · 그 밖의 국내 종목 (설정에서 켠 경우만 추가): 넣은 가격 그대로. 지난 움직임·변동성은 미국 상장 EWY(MSCI Korea) × 원/달러로 대신
  const isKR = (t) => /\.(KS|KQ)$/i.test(t || "");
  function krInfo(t, names) {
    const R = S.krRules || {}, txt = names.filter(Boolean).join(" "), re = (x) => x && new RegExp(x, "i");
    const rule = R.exclude && re(R.exclude).test(txt) ? null : (R.rules || []).find((r) => re(r.match).test(txt));
    return rule ? { proxy: rule.proxy, label: rule.label, hedged: !!(R.hedged && re(R.hedged).test(txt)) } : { proxy: null, market: R.market || "EWY" };
  }
  const krOf = (h) => krInfo(h.ticker, [h.name, koOf(h.ticker)?.k, koOf(h.ticker)?.e]);
  const krOk = (o) => !isKR(o.s) || !!krInfo(o.s, [o.k, o.e]).proxy;
  // 국내 주식 합계 (숨긴 선택 항목): 증권사 앱의 국내 주식 평가액 합계를 한 줄(수량 1, 가격 = 금액)로 (앱 KOREA 와 같다)
  //  · 지난 기록은 현금처럼: 넣은 금액을 날짜별로 남긴다 (state.kr_log). 그날 값 = 그날까지 마지막 금액, 첫 기록 전은 0 (처음 넣은 금액은 들어온 돈)
  //  · 전망·민감도는 마지막 금액에서 EWY × 원/달러 움직임으로 (사이트 stats.json 의 KR~EWY 와 같은 수익률)
  const KR_SUM = "KOREA.KS";
  function krPut(v, d = today()) { S.state.kr_log = Model.krNorm([...(S.state.kr_log || []), { d, v: Math.max(0, v || 0) }]); }
  // 기록의 마지막 금액에 표의 한 줄을 맞춘다 (0원이면 줄을 뺀다)
  function krHold() {
    const L = S.state.kr_log || [], last = L[L.length - 1], i = S.state.holdings.findIndex((h) => h.ticker === KR_SUM);
    if (!last || !(last.v > 0)) { if (i >= 0) S.state.holdings.splice(i, 1); return; }
    if (i >= 0) Object.assign(S.state.holdings[i], { price: last.v, price_date: last.d });
    else S.state.holdings.push({ ticker: KR_SUM, shares: 1, price: last.v, price_date: last.d, avg_cost: null, note: "", name: "국내 주식 합계" });
  }
  function setKrSum(v) {
    if (v > 0 && S.state.sample) endSample(false);
    krPut(v > 0 ? v : 0); krHold();
    synthKR(); markDirty(); save(); renderAll();
  }
  // 지금 국내 주식 합계 (전망에서 그대로 두는 금액). 샘플·줄 없음은 0
  const krNow = () => (S.state.sample || !S.state.holdings.some((h) => h.ticker === KR_SUM) ? 0 : Model.krAt(S.state.kr_log, today()));
  function synthKR() {
    for (const k of Object.keys(S.prices)) if (isKR(k)) delete S.prices[k];
    S.krMove = null;
    const fx = S.prices["KRW=X"];
    for (const h of S.state?.holdings || []) {
      if (h.ticker === KR_SUM) { krSynth(h, fx); continue; }
      if (!isKR(h.ticker) || !(Number(h.price) > 0)) continue;
      const info = krOf(h), base = S.prices[info.proxy || info.market], a = h.price_date || today(), px = Number(h.price);
      // 기준 시세가 아직 없으면 (새로 넣은 EWY·SCHD·SOXX 첫 수집 전) 넣은 가격 하나만 (앱 rebuildKR 과 같다)
      if (!base || !base.dates.length) { S.prices[h.ticker] = { symbol: h.ticker, currency: "KRW", name: h.name || koOf(h.ticker)?.k || h.ticker, dates: [a], close: [px], adj: [px], est: "input", basis: info.proxy || info.market, hedged: info.hedged }; continue; }
      // 가격(close): 기준 종가 × 원/달러, 넣은 날 값 = 넣은 가격. 직접 입력 종목은 넣은 날 뒤로 넣은 가격 그대로
      // 수익률(adj): 기준 수정 종가 × 원/달러 (전망·지난 움직임). 사이트 stats.json 의 KR~SPY 등과 같은 값
      const C = Model.krSeries(base, fx, info.hedged, "close"), A = Model.krSeries(base, fx, info.hedged, "adj"), dates = C.dates;
      let j = dates.length - 1; while (j > 0 && dates[j] > a) j--;
      if (!(C.vals[j] > 0) || !(A.vals[j] > 0)) continue;
      const kc = px / C.vals[j], ka = px / A.vals[j];
      const close = C.vals.map((v, i) => (!info.proxy && i > j ? px : v == null ? null : v * kc)), adj = A.vals.map((v) => (v == null ? null : v * ka));
      S.prices[h.ticker] = { symbol: h.ticker, currency: "KRW", name: h.name || koOf(h.ticker)?.k || h.ticker, dates, close, adj, est: info.proxy ? "proxy" : "input", basis: info.proxy || info.market, hedged: info.hedged };
    }
  }

  // 국내 주식 합계의 날짜별 값: 첫 기록 날부터 원/달러 거래일마다 그날까지 마지막 금액 (앱 rebuildKR 과 같다). 첫 기록 전 날짜는 없다 (지난 그래프에서 들어온 돈으로)
  // 지금 값은 curPrice 가 기록에서 바로 (아직 시세가 없는 오늘 넣은 금액도)
  function krSynth(h, fx) {
    const L = S.state.kr_log || []; if (!L.length) return;
    const d0 = L[0].d, dates = fx?.dates?.length ? fx.dates.filter((d) => d >= d0) : L.map((x) => x.d), close = dates.map((d) => Model.krAt(L, d));
    S.prices[KR_SUM] = { symbol: KR_SUM, currency: "KRW", name: h.name || "국내 주식 합계", dates, close, adj: close, est: "input", basis: "입력" };
    const base = S.prices[krOf(h).market || "EWY"], A = base?.dates?.length && fx ? Model.krSeries(base, fx, false, "adj") : null;
    S.krMove = A ? { dates: A.dates, adj: A.vals } : { dates: [L[L.length - 1].d], adj: [L[L.length - 1].v || 1] }; // 기준 시세가 아직 없으면 한 점 (전망 기본값)
  }
  // 전망·민감도에 쓰는 시세: 국내 주식 합계만 넣은 금액 대신 EWY × 원/달러 움직임
  function mcPrices() {
    const o = {}; for (const k in S.prices) o[k] = k === KR_SUM && S.krMove ? S.krMove : S.prices[k];
    return o;
  }

  // 종목 찾기 (iOS 앱과 같은 방식): 앱의 한글 이름 목록(stocks.js) + Yahoo 검색(영문 이름·티커·종목 코드). 미국 주식·ETF 와 코스피·코스닥만
  const STOCKS = window.STOCKS || [];
  const US_EX = new Set(["NMS", "NYQ", "NGM", "NCM", "PCX", "ASE", "BTS", "NAS", "NYS"]);
  const snorm = (x) => String(x || "").toLowerCase().replace(/\s+/g, "");
  const codeOf = (sym) => sym.replace(/\.(KS|KQ)$/, "");
  const koOf = (sym) => STOCKS.find((o) => o.s === sym);
  // 앱 안 목록에서 찾기: 기호·코드·이름이 똑같으면 먼저, 앞이 같으면 그다음, 들어 있으면 그 뒤
  function stockLocal(q) {
    const k = snorm(q); if (!k) return [];
    const rank = (o) => {
      const keys = [o.s, codeOf(o.s), o.k, o.a, o.e].map(snorm);
      return keys.includes(k) ? 0 : keys.some((x) => x.startsWith(k)) ? 1 : keys.some((x) => x.includes(k)) ? 2 : 9;
    };
    return STOCKS.map((o) => [rank(o), o]).filter((x) => x[0] < 9 && krOk(x[1])).sort((a, b) => a[0] - b[0]).map((x) => x[1]);
  }
  // Yahoo 검색 (한글은 못 찾는다). 중계 순서는 시세와 같다
  async function stockRemote(q) {
    const k = q.trim();
    if (!k || !/^[\x20-\x7e]+$/.test(k)) return [];
    const url = "https://query1.finance.yahoo.com/v1/finance/search?" + new URLSearchParams({ q: k, quotesCount: 12, newsCount: 0 });
    const list = proxies(), order = goodProxy && list.includes(goodProxy) ? [goodProxy, ...list.filter((p) => p !== goodProxy)] : list;
    for (const p of order) {
      const ctl = new AbortController(), t = setTimeout(() => ctl.abort(), 6000);
      try {
        const j = await (await fetch(p + encodeURIComponent(url), { signal: ctl.signal })).json();
        if (!j || !Array.isArray(j.quotes)) continue;
        return j.quotes.map((x) => {
          const sym = x.symbol, type = x.quoteType, name = x.longname || x.shortname || sym;
          if (!sym || (type !== "EQUITY" && type !== "ETF")) return null;
          if (/\.(KS|KQ)$/.test(sym)) { const ko = koOf(sym); return { s: sym, k: ko?.k || name, e: name, m: (sym.endsWith(".KS") ? "코스피" : "코스닥") + (type === "ETF" ? " ETF" : "") }; }
          if (!US_EX.has(x.exchange) || sym.includes(".")) return null;
          const ko = koOf(sym); return { s: sym, k: ko?.k || name, e: name, m: type === "ETF" ? "미국 ETF" : "미국 주식" };
        }).filter((o) => o && krOk(o));
      } catch (e) { /* 다음 중계 */ } finally { clearTimeout(t); }
    }
    return [];
  }
  // 입력칸 아래 후보 목록
  const sug = { list: [], i: -1, seq: 0, timer: 0 };
  function sugRender() {
    const box = $("#addSug"); if (!box) return;
    if (!sug.list.length) { box.hidden = true; box.innerHTML = ""; return; }
    box.hidden = false;
    box.innerHTML = sug.list.map((o, i) => {
      const t = o.s.endsWith(".KS") || o.s.endsWith(".KQ") ? codeOf(o.s) : o.s, sub = o.e && o.e !== o.k ? o.e : "";
      return `<button type="button" role="option" data-i="${i}" class="${i === sug.i ? "on" : ""}"><b>${esc(o.k)}</b> <span class="muted">${esc(t)} · ${esc(o.m)}${isKR(o.s) ? (krInfo(o.s, [o.k, o.e]).proxy ? " · 미국 지수로 추정" : " · 가격 직접 입력") : ""}</span>${sub ? `<br><span class="muted small">${esc(sub)}</span>` : ""}</button>`;
    }).join("");
  }
  function sugUpdate() {
    const q = $("#addTicker").value, my = ++sug.seq;
    sug.list = stockLocal(q).slice(0, 8); sug.i = -1; sugRender();
    clearTimeout(sug.timer);
    if (!/^[\x20-\x7e]+$/.test(q.trim())) return;
    sug.timer = setTimeout(async () => {
      const r = await stockRemote(q);
      if (my !== sug.seq) return;
      const seen = new Set(sug.list.map((o) => codeOf(o.s)));
      sug.list = [...sug.list, ...r.filter((o) => !seen.has(codeOf(o.s)) && seen.add(codeOf(o.s)))].slice(0, 10); sugRender();
    }, 300);
  }
  function sugPick(o) {
    S.picked = o; $("#addTicker").value = o.s; sug.list = []; sug.seq++; sugRender();
    $("#addShares").focus();
  }
  // 고르지 않고 바로 '종목 추가'를 누른 경우: 이름·종목 코드를 Yahoo 기호로 바꾼다
  async function resolveTicker(raw) {
    const q = raw.trim(), up = q.toUpperCase();
    if (!q) return "";
    if (STOCKS.some((o) => o.s === up)) return up;
    const exact = stockLocal(q).find((o) => [o.s, codeOf(o.s), o.k, o.a].map(snorm).includes(snorm(q)));
    if (exact) return exact.s;
    if (/^\d{6}$/.test(q)) { const r = await stockRemote(q); return (r.find((o) => codeOf(o.s) === q) || { s: q + ".KS" }).s; }
    if (!/^[\x20-\x7e]+$/.test(q)) { const l = stockLocal(q); return l.length === 1 ? l[0].s : ""; }
    return up;
  }
  async function addHolding() {
    const raw = $("#addTicker").value; sug.list = []; sug.seq++; sugRender();
    const t = await resolveTicker(raw), sh = parseDec($("#addShares").value), avg = parseDec($("#addAvg").value);
    if (!t) return toast(raw.trim() ? "종목을 찾지 못했습니다. 목록에서 골라 주세요" : "종목을 넣어 주세요");
    if (!(sh > 0)) return toast("수량을 넣어 주세요");
    const pk = S.picked && S.picked.s === t ? S.picked : koOf(t); S.picked = null;
    if (isKR(t) && !krOk({ s: t, k: pk?.k, e: pk?.e })) return toast("국내 개별 종목은 설정 > 표시 옵션의 '국내 주식 합계 직접 입력'을 켜고 합계 금액으로 넣어 주세요");
    endSample(false);
    const ex = S.state.holdings.find((h) => h.ticker === t);
    if (ex) {
      const old = Number(ex.shares) || 0;
      // 매수 단가를 같이 넣으면 기존 단가와 수량 가중 평균 (기존 단가가 없으면 새 단가)
      if (avg > 0) ex.avg_cost = Number(ex.avg_cost) > 0 && old > 0 ? (Number(ex.avg_cost) * old + avg * sh) / (old + sh) : avg;
      ex.shares = old + sh; toast(`${t} 수량을 더했습니다`);
    }
    else { S.state.holdings.push({ ticker: t, shares: sh, price: null, avg_cost: avg || null, note: "", ...(isKR(t) ? { name: pk?.k || pk?.e || "" } : {}) }); addEarnEvent(t); }
    $("#addTicker").value = $("#addShares").value = $("#addAvg").value = "";
    save(); renderAll();
    if (isKR(t)) { if (!S.prices[t]) { showTab("stocks"); toast("한국 시세는 받지 않아요. 표에 지금 가격을 한 번 넣어 주세요"); $("#holdTable [data-f='kprice']")?.focus(); } return; }
    if (!S.prices[t]) {
      if (MODE === "static") {
        showTab("stocks");
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
    if (h.ticker === KR_SUM) krPut(0); // 국내 주식 합계 줄을 지우면 0원으로 남긴다 (지난 기록은 그대로)
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
    return { holdings: b.holdings, scenario: scen, nPaths: Number(m.n_paths), seed: Number(m.seed) || 1, goal: Math.max(1, g.amount - cashKrw()), // 현금은 그대로 있다고 보고 목표에서 뺀다
      monthly: Number(g.monthly_contribution) || 0, rebalance: !!m.rebalance_yearly, dof: m.t_dof, fxOf, usdKrw0: fxNow("USD") };
  }
  // 계산해 둔 전망만 돌려준다 (없으면 null)
  function fcReady(scen) {
    if (lastForecast && !fcDirty) {
      if (lastForecast.scen === scen) return { R: lastForecast.withEv, model: lastForecast.b.model };
      const lr = lastForecast.lens && lastForecast.lens[scen]; if (lr) return { R: lr, model: lastForecast.b.model };
    }
    return fcCache[scen] || fcDashLoad(scen);
  }
  // 대시보드 미래 그래프용 전망을 저장해 두고, 입력·시세·날짜가 그대로면 리로드해도 다시 계산하지 않는다
  const FCD_KEY = "naeilo-fcdash";
  // 머리글 '자산'을 누르면 금액 감추기 (이 브라우저에만 기억)
  const HIDE_KEY = "naeilo-hide", hideAmt = () => { try { return localStorage.getItem(HIDE_KEY) === "1"; } catch (e) { return false; } };
  const fcdSig = () => fcSig() + "|" + today() + "|" + Object.keys(S.prices).sort().map((t) => t + (S.prices[t].dates || []).slice(-1)[0]).join();
  function fcDashLoad(scen) {
    try {
      const c = JSON.parse(localStorage.getItem(FCD_KEY) || "null"), e = c && c.sig === fcdSig() && c.r[scen];
      if (!e) return null;
      return (fcCache[scen] = { R: e.R, model: { monthDates: e.md } });
    } catch (err) { return null; }
  }
  function fcDashSave(scen) {
    const F = fcCache[scen]; if (!F || !F.R || F.err) return;
    const strip = (k, v) => (k === "raw" || k === "term" ? undefined : v);
    try {
      const sig = fcdSig(); let c = JSON.parse(localStorage.getItem(FCD_KEY) || "null");
      if (!c || c.sig !== sig) c = { sig, r: {} };
      c.r[scen] = { R: F.R, md: F.model.monthDates };
      localStorage.setItem(FCD_KEY, JSON.stringify(c, strip));
    } catch (err) { try { localStorage.removeItem(FCD_KEY); } catch (e2) { /* 무시 */ } }
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
    setTimeout(() => { try { if (!forecastFor(scen)) fcCache[scen] = { err: "평가액이 있는 종목이 없습니다." }; else fcDashSave(scen); } catch (e) { fcCache[scen] = { err: e.message }; } const cbs = fcPending[scen]; delete fcPending[scen]; cbs.forEach((f) => f()); }, 30);
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
  // 목표 경로: 목표 시작일의 실제(그때 수량) 평가액에서 목표일 목표액까지 같은 속도로 불어나는 길
  function goalPath(H, cash = 0) {
    const g = S.state.goal; if (!H.dates.length) return null;
    const A = (actualSeries(H) || H.total).map((v) => (v == null ? v : v + cash)), start = g.start_date || today();
    let i0 = H.dates.findIndex((d) => d >= start); if (i0 < 0) i0 = H.dates.length - 1;
    const V0 = A[i0] || H.total[i0] + cash, span = yearsBetween(start, g.date);
    if (!(V0 > 0) || !(span > 0)) return { A, start, i0, V0, at: () => null };
    // 매달 넣는 돈은 그대로 쌓고, 나머지(목표 − 넣을 돈)를 같은 속도로 불린다 (앱 내 길과 같은 식)
    const M = (Number(g.monthly_contribution) || 0) * 12, kAdj = g.amount - M * span;
    // 목표 시작일 뒤에 처음 넣은 국내 주식 합계는 들어온 돈: 그날부터 길에도 그 금액을 더한다 (앞선 것으로 보이지 않게. 앱 내 길과 같다)
    const KL = S.state.kr_log || [], kin = krNow() && !Model.krAt(KL, start) ? KL.find((x) => x.d > start && x.v > 0) : null;
    const at = (d) => { const t = yearsBetween(start, d); return M * t + (kAdj > 0 ? V0 * (kAdj / V0) ** (t / span) : V0 + (g.amount - V0) * t / span) + (kin && d >= kin.d ? kin.v : 0); };
    return { A, start, i0, V0, at }; // 시작일 전은 같은 속도로 거꾸로 늘인 길
  }
  let progAsked = "";
  // ---- 내 길: 판정 + 구성에 따라 언제 닿을까 + 비중 바꿔 비교 (앱 내 길과 같은 화면. 점 = 보통 닿는 때, 막대 = 10번 중 8번)
  // 성장 중심·안정 중심은 비중안 비교(공격적·보수적)와 같은 규칙. 저장(모의 바구니)은 앱에만 있다
  let etaOpen = false, etaMore = false, etaRes = null, etaSig = "", etaRun = 0, etaW0 = null, etaW = null, etaTimer = null;
  const etaVerdict = (p) => (p >= 0.7 ? "높아요" : p >= 0.3 ? "반반이에요" : "낮아요");
  const etaYear = (d) => (d ? +d.slice(0, 4) + (+d.slice(5, 7) - 1) / 12 : null);
  const etaText = (d, cap) => (d ? `${d.slice(0, 4)}년 ${+d.slice(5, 7)}월` : `${cap}년 넘게`);
  function etaBase() {
    const { rows } = valuation(), base = rows.filter((r) => r.valueKrw > 0).map((r) => ({ ticker: r.h.ticker, shares: r.sh, price0: r.p.v, ccy: r.ccy, valueKrw: r.valueKrw }));
    const mixes = {}, defs = {};
    for (const k of ["agg", "con"]) { mixes[k] = allocMix(k).map(([t, w]) => [S.prices[t] ? t : ALLOC_SUB[t] && S.prices[ALLOC_SUB[t]] ? ALLOC_SUB[t] : null, w]).filter((x) => x[0]); defs[k] = { cap: ALLOC_DEF[k].cap, cutSmall: { agg: 0.1, con: 0.4 }[k] }; }
    for (const t of new Set(Object.values(mixes).flat().map((x) => x[0]))) if (!base.some((h) => h.ticker === t)) { const q = S.prices[t]; base.push({ ticker: t, shares: 0, price0: q.close[q.close.length - 1], ccy: ccyOf(t), valueKrw: 1, extra: true }); }
    return { base, mixes, defs };
  }
  function etaCompute(custom) {
    const g = S.state.goal, yrs = Math.max(10, Math.ceil(yearsBetween(today(), g.date) * 2));
    const { base, mixes, defs } = etaBase();
    const series = {}; { const P = mcPrices(); for (const k in P) series[k] = { dates: P[k].dates, adj: P[k].adj }; }
    const inp = { base, series, settings: S.state.model, events: S.state.events, betas: factorBetas().beta, startDate: today(), horizon: Model.addMonths(today(), 12 * yrs), goal: g, mixes, defs, custom: custom ? base.map((h) => custom[h.ticker] || 0) : null };
    return allocCompute(inp, null, "eta").then((r) => ({ r, base, yrs }));
  }
  function renderEta(pg, now) {
    const card = $("#etaCard"), g = S.state.goal;
    if (!card) return;
    if (!(now > 0) || !(g.amount > 0) || routeOf() === "recover") { card.style.display = "none"; return; }
    card.style.display = "block";
    const sig = fcSig() + JSON.stringify(S.state.alloc_mix || {});
    if (sig !== etaSig) {
      etaSig = sig; etaRes = null; etaW0 = etaW = null; const my = ++etaRun;
      etaCompute(null).then((x) => { if (my !== etaRun) return; etaRes = x; renderEta(pg, now); }).catch((e) => { console.error(e); });
    }
    const left = yearsBetween(today(), g.date), gy = etaYear(g.date), R = etaRes?.r;
    const keep = R?.find((o) => o.k === "keep"), cap = etaRes?.yrs || 10;
    if (R && !etaW0) { etaW0 = {}; etaRes.base.forEach((h, i) => { if (!h.extra) etaW0[h.ticker] = Math.round(keep.w[i] * 100); }); const top = Object.keys(etaW0).sort((a, b) => etaW0[b] - etaW0[a])[0]; if (top) etaW0[top] += 100 - Object.values(etaW0).reduce((a, x) => a + x, 0); etaW = { ...etaW0 }; }
    const cust = R?.find((o) => o.k === "custom");
    const rows = R ? [["지금 구성", keep, true], ["성장 중심", R.find((o) => o.k === "agg"), false], ["안정 중심", R.find((o) => o.k === "con"), false]].filter((x) => x[1]) : [];
    const all = rows.map((x) => x[1]).concat(cust ? [cust] : []);
    let a0 = gy, a1 = gy;
    for (const o of all) { a0 = Math.min(a0, etaYear(o.eta.p10) ?? a0); a1 = Math.max(a1, etaYear(o.eta.p90) ?? etaYear(Model.addMonths(today(), 12 * cap))); }
    a0 = Math.floor(a0); a1 = Math.max(a0 + 2, Math.ceil(a1));
    const X = (y) => Math.min(100, Math.max(0, ((y - a0) / (a1 - a0)) * 100));
    const line = (name, o, strong) => { const lo = X(etaYear(o.eta.p10) ?? a1), hi = o.eta.p90 ? X(etaYear(o.eta.p90)) : 100, mid = o.eta.p50 ? X(etaYear(o.eta.p50)) : 100;
      return `<div class="etarow"><div class="row between"><b>${esc(name)}</b><span class="${strong ? "pos" : "muted"}">보통 ${etaText(o.eta.p50, cap)}</span></div><div class="etabar"><i style="left:${lo}%;width:${Math.max(1, hi - lo)}%" class="${strong ? "on" : ""}"></i><b style="left:calc(${mid}% - 6px)" class="${strong ? "on" : ""}"></b><u style="left:${X(gy)}%"></u></div></div>`; };
    const ticks = []; for (let y = a0; y <= a1; y += a1 - a0 > 6 ? 2 : 1) ticks.push(`<span style="left:${X(y)}%">${y}</span>`);
    const sum = etaW ? Object.values(etaW).reduce((a, x) => a + x, 0) : 0;
    let cmp = "";
    if (etaW) {
      const ed = (t) => { const d = (etaW[t] || 0) - (etaW0[t] || 0); return d === 0 ? "그대로" : d > 0 ? `+${d}%p` : `${d}%p`; };
      const m = cust || keep, goalM = gy, mid = etaYear(m.eta.p50) ?? Infinity, midNow = etaYear(keep.eta.p50) ?? Infinity;
      const head = mid <= goalM ? "보통 목표일 안에 닿아요" : mid < midNow - 0.04 ? "지금보다 빨라지지만, 보통 목표일은 넘겨요" : mid > midNow + 0.04 ? "지금보다 늦어지고, 보통 목표일을 넘겨요" : "지금과 비슷해요. 보통 목표일을 넘겨요";
      const wd = m.eta.p90 && m.eta.p10 ? etaYear(m.eta.p90) - etaYear(m.eta.p10) : Infinity;
      cmp = `<details class="etacmp"${etaOpen ? " open" : ""}><summary>내 구성 바꿔서 비교해 보기</summary>
        <div class="row between" style="margin-top:8px"><b>내 종목 비중</b><button class="sm" data-eta="reset">처음으로</button></div>
        ${Object.keys(etaW).map((t) => `<div class="row between etaw"><span>${esc(t)} <span class="muted small">${ed(t)}</span></span><span class="row"><button class="sm" data-eta="-" data-t="${esc(t)}" aria-label="${esc(t)} 비중 5% 줄이기">−</button><b style="width:3em;text-align:center">${etaW[t]}%</b><button class="sm" data-eta="+" data-t="${esc(t)}" aria-label="${esc(t)} 비중 5% 늘리기">+</button></span></div>`).join("")}
        <div class="small ${sum === 100 ? "pos" : "neg"}" style="margin:6px 0">${sum === 100 ? "합계 100%" : `합계 ${sum}% · 100%가 되도록 맞춰 주세요 (시점은 비율대로 계산했어요)`}</div>
        <div class="etaaxis">${ticks.join("")}</div>${line("지금 비중", keep, false)}${line("바꾼 비중", m, true)}
        <p style="margin:8px 0 2px"><b>${head}</b></p><p class="small muted" style="margin:0">${wd > 4 ? "대신 결과 차이가 커요. 아주 빨리 닿을 수도, 한참 늦어질 수도 있어요." : wd > 2 ? "결과 차이는 보통 수준이에요." : "결과가 비교적 고르게 모여 있어요."}</p>
        <p class="small muted">이 비중을 모의 바구니로 저장하는 건 앱 내 길에서 할 수 있어요. 과거 수익률로 만든 예상이에요.</p></details>`;
    }
    card.innerHTML = `<div class="etahead"><div class="small">내 목표 · ${krw(g.amount)}원 · ${esc(g.date)}까지 · 남은 ${leftStr(left)}</div>
      <h2>${pg != null ? `목표까지 가능성은 ${etaVerdict(pg)}` : "목표까지 가능성을 계산하고 있어요"}</h2>
      <div class="bar"><i style="width:${Math.min(100, (now / g.amount) * 100)}%"></i></div><div class="row between small"><span>지금 ${krw(now)}원</span><span>목표 ${krw(g.amount)}원</span></div>
      <p class="small">${keep ? `지금 포트폴리오 그대로라면 ${krw(g.amount)}원에 닿는 시점은 보통 ${etaText(keep.eta.p50, cap)}이에요.` : "닿는 시점을 계산하고 있어요."}</p></div>
      <details class="opt etamore"${etaMore ? " open" : ""}><summary>자세히 · 구성에 따라 언제 닿을까?</summary><p class="small muted">점은 보통 닿는 시점, 막대는 10번 중 8번이 들어오는 범위예요. 점선 = 목표일 ${esc(g.date.slice(0, 7))}</p>
      ${R ? `<div class="etaaxis">${ticks.join("")}</div>${rows.map(([n, o, st]) => line(n, o, st)).join("")}<p class="small muted">빨리 닿을 수도 있는 구성은 범위도 넓어요. 일찍 닿는 대신 크게 늦어질 수도 있어요.</p>${cmp}` : `<p class="muted small">계산 중...</p>`}</details>`;
    card.querySelector(".etamore")?.addEventListener("toggle", (e) => { etaMore = e.target.open; });
    card.querySelector(".etacmp")?.addEventListener("toggle", (e) => { etaOpen = e.target.open; });
    card.querySelectorAll("[data-eta]").forEach((b) => b.addEventListener("click", () => {
      const k = b.dataset.eta, t = b.dataset.t;
      if (k === "reset") etaW = { ...etaW0 }; else etaW[t] = Math.max(0, Math.min(100, (etaW[t] || 0) + (k === "+" ? 5 : -5)));
      const same = JSON.stringify(etaW) === JSON.stringify(etaW0);
      if (same) { etaRes.r = etaRes.r.filter((o) => o.k !== "custom"); renderEta(pg, now); return; }
      renderEta(pg, now);
      clearTimeout(etaTimer); const my = etaRun;
      etaTimer = setTimeout(() => etaCompute({ ...etaW }).then((x) => { if (my !== etaRun || !etaRes) return; const c = x.r.find((o) => o.k === "custom"); etaRes.r = etaRes.r.filter((o) => o.k !== "custom").concat(c ? [c] : []); renderEta(pg, now); }).catch((e) => console.error(e)), 400);
    }));
  }

  function renderProgress(H, total, cash = 0) {
    const card = $("#progCard"), g = S.state.goal;
    if (!(total > 0) || !H.dates.length || routeOf() === "recover") { card.style.display = "none"; $("#etaCard").style.display = "none"; return; } // 회복 길에서는 본전 카드만. 샘플(DRNK 4주·QQQ 1주)에도 보여 준다
    const P = goalPath(H, cash), { A, start, i0, V0 } = P;
    const k = H.dates.length - 1, now = total + cash, td = today();
    const need = P.at(td > start ? td : start), gap = need ? now / need - 1 : null;
    // 그사이 처음 넣은 국내 주식 합계는 오른 것이 아니라 들어온 돈이라 뺀다 (그 뒤 금액 변화는 시장 움직임)
    const KL = S.state.kr_log || [], krIn = (j) => (krNow() && !Model.krAt(KL, H.dates[j]) ? KL.find((x) => x.d > H.dates[j] && x.v > 0)?.v || 0 : 0), r0 = (now - krIn(i0)) / V0 - 1;
    const back = (n) => { const j = k - n; if (j < 0) return null; const a = A[j] ?? H.total[j]; return a ? (A[k] - krIn(j)) / a - 1 : null; };
    if (!lastForecast) fcRestore();
    const pg = lastForecast && fcDirty ? null : fcReady(S.state.model.scenario)?.R?.p_goal ?? null; // 입력이 바뀐 뒤의 저장값은 쓰지 않는다. // 앱 내 길의 목표 달성 확률과 같은 값. 아직 없으면 '계산 중'으로 두고 한 번 계산
    if (pg == null && !S.state.model.rebalance_yearly && progAsked !== fcSig()) { progAsked = fcSig(); fcDirty = true; setTimeout(runForecast, 0); }
    card.style.display = "block";
    renderEta(pg, now);
    $("#progSub").textContent = `${start} 시작 · 바뀐 날 수량 기준`;
    // 한 줄 결론: 계획선과의 차이 (없으면 시작 대비). 나머지 숫자·기간 막대·공유는 자세히 안에
    $("#progLine").innerHTML = need && gap != null ? `시작보다 <b class="${cls(r0)}">${spct(r0)}</b>, 계획선보다 <b class="${cls(gap)}">${krw(Math.abs(now - need))}원 ${now >= need ? "앞서" : "뒤처져"}</b> 있어요.` : `시작보다 <b class="${cls(r0)}">${spct(r0)}</b> 움직였어요.`;
    $("#prog").innerHTML = [
      ["시작 대비", spct(r0), `${krw(V0)}원 → ${krw(now)}원${krIn(i0) ? ` (국내 합계 ${krw(krIn(i0))}원 들어옴)` : ""}`],
      ["계획선 대비", gap == null ? "-" : `${gap >= 0 ? "앞섬" : "뒤처짐"} ${pct(Math.abs(gap))}`, need ? `계획선보다 ${now >= need ? "+" : "−"}${krw(Math.abs(now - need))}원` : ""],
      ["목표 달성 확률", pg != null ? pct(pg, 0) : "계산 중", "목표일 전망과 같은 값"],
      ["지난주", spct(back(5)), "실제 수량 기준"],
      ["지난달", spct(back(21)), "실제 수량 기준"],
    ].map(([a, v, s2]) => `<div class="kpi"><div class="k">${a}</div><div class="v ${a === "계획선 대비" ? cls(gap) : a === "시작 대비" ? cls(r0) : ""}">${v}</div><div class="s">${s2}</div></div>`).join("");
    // 경로 대비 +/− 막대: 날짜별 (실제 − 필요 경로). 1년은 주, 3년은 달 단위로 묶고(그 주·달 마지막 값), 끝에 오늘 실시간 막대
    const rsel = $("#progRange .on")?.dataset.r || "66", n = +rsel, unit = n >= 780 ? "m" : n >= 252 ? "w" : "d";
    const key = (d) => { if (unit === "m") return d.slice(0, 7); const t = new Date(d + "T00:00:00Z"); t.setUTCDate(t.getUTCDate() - ((t.getUTCDay() + 6) % 7)); return t.toISOString().slice(0, 10); };
    let pts = [];
    for (let i = Math.max(0, k - n); i <= k; i++) { const nd = P.at(H.dates[i]), v = A[i] ?? (H.total[i] + cash); if (nd != null && v != null) pts.push([H.dates[i], v - nd]); }
    if (need && td > H.dates[k]) pts.push([td, now - need]);
    if (unit !== "d") pts = pts.filter((p, j) => j === pts.length - 1 || key(p[0]) !== key(pts[j + 1][0]));
    const lab = { 5: "1주", 22: "1달", 66: "3달", 252: "1년", 780: "3년" }[rsel] || "";
    const sg = (v) => `${v >= 0 ? "+" : "−"}${krw(Math.abs(v))}원`;
    if (pts.length) {
      const first = pts[0][1], last = pts[pts.length - 1][1], diff = last - first;
      // 오늘 차이 금액은 위 요약과 막대 끝 라벨에만 (여기서는 기간 동안 바뀐 만큼만)
      $("#progTrend").innerHTML = pts.length < 2 ? "" : `${lab} 전보다 <b class="${cls(diff)}">${sg(diff)}</b>`;
      $("#progChart").style.display = "block";
      // 수량 바뀐 날을 그 날짜가 든 막대(1년은 그 주, 3년은 그 달)에 모은다
      const tr = trades(), evAt = {};
      [...new Set(tr.map((t) => t.d))].forEach((d) => {
        const j = pts.findIndex((p) => (unit === "d" ? p[0] >= d : key(p[0]) >= key(d))); if (j < 0 || (j === 0 && d < pts[0][0] && (unit === "d" || key(d) !== key(pts[0][0])))) return;
        (evAt[j] ||= []).push(`${d} ${tr.filter((t) => t.d === d).map((t) => `${t.t} ${t.q > 0 ? "+" : ""}${t.q}`).join(", ")}`);
      });
      Charts.barChart($("#progChart"), { x: pts.map((p) => p[0]), y: pts.map((p) => p[1]), height: 170, yfmt: krwAxis, live: td,
        xlab: (d) => (d === td ? "오늘" : unit === "m" ? `${d.slice(2, 4)}.${+d.slice(5, 7)}` : `${+d.slice(5, 7)}/${+d.slice(8)}`),
        tipx: (d) => (d === td ? `${d} (오늘 실시간)` : unit === "m" ? `${d.slice(0, 7)} 말` : unit === "w" ? `${d} 주 마지막` : d),
        tipy: (i) => `계획선보다 ${sg(pts[i][1])}` + (evAt[i] ? `<br>▲ ${evAt[i].map(esc).join("<br>▲ ")}` : "") + (pts[i][0] < start ? `<br><span class="muted">목표 시작 전 (거꾸로 늘인 경로)</span>` : ""),
        labelLast: (pts[pts.length - 1][0] === td ? "오늘 " : "") + sg(last),
        dots: Object.keys(evAt).map((j) => ({ x: pts[j][0], label: evAt[j].join("\n") })) });
    } else { $("#progTrend").textContent = ""; $("#progChart").style.display = "none"; }
  }
  // naeilo를 쓴 날부터의 내 기록 (지금 구성으로 본 지난 3년과 따로): 그날그날 남긴 평가액과 넣은 돈
  function renderMyRec() {
    if (recordDay()) save(false); // 하루 첫 기록만 저장(동기화)한다. 같은 날 시세가 바뀌면 화면 값만 고치고 다음 저장 때 같이 남는다
    const card = $("#recCard"), D = S.state.sample ? [] : S.state.daily || [], K = S.state.sample ? [] : S.state.kr_log || [];
    if (!D.length && !K.length) { card.style.display = "none"; return; }
    card.style.display = "block";
    $("#recMode").hidden = !K.length; $("#recKrIo").hidden = !K.length;
    if (K.length && ($("#recMode .on")?.dataset.m === "kr" || !D.length)) return renderKrRec(K);
    if (!K.length) $$("#recMode button").forEach((b) => b.classList.toggle("on", b.dataset.m === "rec"));
    const L = D[D.length - 1], gain = L.c > 0 ? L.v / L.c - 1 : null;
    $("#recSub").textContent = `${D[0].d}부터 · ${D.length}일 기록`;
    $("#recKpis").innerHTML = [
      ["지금 평가액", krw(L.v) + "원", L.d === today() ? "오늘" : L.d],
      ["넣은 돈", krw(L.c) + "원", "산 것 − 판 것 (첫날은 그날 평가액)"],
      ["넣은 돈 대비", gain == null ? "-" : spct(gain), `${L.v >= L.c ? "+" : "−"}${krw(Math.abs(L.v - L.c))}원`],
    ].map(([a, v, s2]) => `<div class="kpi"><div class="k">${a}</div><div class="v ${a === "넣은 돈 대비" ? cls(gain) : ""}">${v}</div><div class="s">${s2}</div></div>`).join("");
    if (D.length < 2) { $("#recChart").style.display = "none"; $("#recNote").textContent = "오늘부터 쌓기 시작했어요. 내일 다시 열면 선이 그려져요. 열지 않은 날은 건너뛰고 이어서 그려요."; return; }
    $("#recChart").style.display = "block";
    $("#recNote").textContent = "naeilo를 연 날마다 남긴 실제 값이에요. 위 '지금 구성으로 본' 그래프는 지금 수량을 지난 가격에 대입한 값이라 다릅니다. 열지 않은 날은 선으로 잇습니다.";
    Charts.lineChart($("#recChart"), { x: D.map((x) => x.d), height: 220, yfmt: krwAxis, series: [
      { name: "평가액", y: D.map((x) => x.v), color: "var(--c1)", width: 2 },
      { name: "넣은 돈", y: D.map((x) => x.c), color: "var(--fg)", width: 1.2, dash: "4 3" }] });
  }
  // 내 기록 '국내 주식 합계': 넣은 금액을 날짜별 선으로 (넣은 날은 아래 삼각형, 안 넣은 날은 앞 금액을 이어 그림). 앱 내 기록 카드와 같다
  function renderKrRec(K) {
    $$("#recMode button").forEach((b) => b.classList.toggle("on", b.dataset.m === "kr"));
    const L = K[K.length - 1], F = K[0], td = today(), x = [];
    for (let t = new Date(F.d + "T00:00:00Z"); x.length < 4000; t.setUTCDate(t.getUTCDate() + 1)) { const d = t.toISOString().slice(0, 10); if (d > td) break; x.push(d); } // 날마다 (금액이 바뀐 날만 계단처럼 오르내린다)
    const y = x.map((d) => Model.krAt(K, d));
    const prev = K.length > 1 ? K[K.length - 2].v : null, chg = prev > 0 ? L.v / prev - 1 : null;
    $("#recSub").textContent = `${F.d}부터 · ${K.length}번 넣음`;
    $("#recKpis").innerHTML = [
      ["지금 합계", krw(L.v) + "원", L.d === td ? "오늘 넣음" : `${Math.round(yearsBetween(L.d, td) * 365.25)}일 전 금액`],
      ["처음 넣은 금액", krw(F.v) + "원", F.d],
      ["지난번 대비", chg == null ? "-" : spct(chg), prev != null ? `${krw(prev)}원 → ${krw(L.v)}원` : "한 번 넣음"],
    ].map(([a, v, s2]) => `<div class="kpi"><div class="k">${a}</div><div class="v ${a === "지난번 대비" ? cls(chg) : ""}">${v}</div><div class="s">${s2}</div></div>`).join("");
    $("#recChart").style.display = "block";
    Charts.lineChart($("#recChart"), { x, height: 220, yfmt: krwAxis, ymin: 0, series: [{ name: "국내 주식 합계", y, color: "var(--c1)", width: 2, lastDot: x.length < 2 ? x[0] : null }], // 첫날은 점 하나
      markers: K.map((k) => ({ x: k.d, label: `${k.d} ${krw(k.v)}원 넣음`, color: "var(--c1)" })) });
    $("#recNote").textContent = x.length < 2 ? "오늘 처음 넣었어요. 다음에 넣으면 선이 그려져요." : "삼각형 = 넣은 날. 안 넣은 날은 앞 금액 그대로예요.";
  }
  // 국내 주식 합계 기록 엑셀: 날짜·금액 두 칸. .xlsx 는 SheetJS 를 그때 불러와 읽고 쓴다 (못 불러오면 .csv)
  let xlsxP = null;
  const xlsxLib = () => (window.XLSX ? Promise.resolve(window.XLSX) : (xlsxP ||= new Promise((ok, no) => { const sc = document.createElement("script"); sc.src = "https://cdnjs.cloudflare.com/ajax/libs/xlsx/0.18.5/xlsx.full.min.js"; sc.onload = () => ok(window.XLSX); sc.onerror = () => { xlsxP = null; no(new Error("엑셀 도구를 불러오지 못했어요")); }; document.head.appendChild(sc); })));
  async function krExport() {
    const K = S.state.kr_log || [], rows = [["날짜", "금액(원)"], ...K.map((k) => [k.d, k.v])], name = `국내주식합계_${today()}`;
    const dl = (blob, f) => { const a = document.createElement("a"); a.href = URL.createObjectURL(blob); a.download = f; document.body.appendChild(a); a.click(); a.remove(); setTimeout(() => URL.revokeObjectURL(a.href), 1000); };
    try { const X = await xlsxLib(), wb = X.utils.book_new(); X.utils.book_append_sheet(wb, X.utils.aoa_to_sheet(rows), "국내 주식 합계"); X.writeFile(wb, name + ".xlsx"); }
    catch (e) { dl(new Blob(["\ufeff" + rows.map((r) => r.join(",")).join("\r\n")], { type: "text/csv" }), name + ".csv"); }
  }
  // 날짜 칸: 2026-10-12 · 2026.10.12 · 2026/10/12 · 엑셀 날짜(숫자). 금액 칸: 쉼표·원 허용. 같은 날은 파일 값으로
  function krRowsParse(rows) {
    const out = [], pad = (n) => String(n).padStart(2, "0");
    for (const r of rows) {
      let [d, v] = r; if (d == null) continue;
      if (d instanceof Date) d = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
      else if (typeof d === "number" && d > 20000 && d < 80000) { const t = new Date(Math.round((d - 25569) * 86400e3)); d = t.toISOString().slice(0, 10); }
      else { const m = String(d).trim().match(/^(\d{4})[-./ ]\s*(\d{1,2})[-./ ]\s*(\d{1,2})/); if (!m) continue; d = `${m[1]}-${pad(m[2])}-${pad(m[3])}`; }
      const n = typeof v === "number" ? v : parseNum(v);
      if (n != null && n >= 0 && d <= today()) out.push({ d, v: n });
    }
    return out;
  }
  // CSV 한 줄 → 칸 (쉼표·탭·세미콜론, 따옴표 안 쉼표는 그대로: "45,000,000")
  function csvCells(l) {
    const out = []; let c = "", q = false;
    for (let i = 0; i < l.length; i++) {
      const ch = l[i];
      if (q) { if (ch === '"' && l[i + 1] === '"') { c += '"'; i++; } else if (ch === '"') q = false; else c += ch; }
      else if (ch === '"') q = true; else if (",\t;".includes(ch)) { out.push(c.trim()); c = ""; } else c += ch;
    }
    out.push(c.trim()); return out;
  }
  async function krImport(f) {
    try {
      let rows;
      if (/\.csv$/i.test(f.name) || f.type === "text/csv") rows = (await f.text()).replace(/^\ufeff/, "").split(/\r?\n/).map(csvCells);
      else { const X = await xlsxLib(), wb = X.read(await f.arrayBuffer(), { cellDates: true }); rows = X.utils.sheet_to_json(wb.Sheets[wb.SheetNames[0]], { header: 1, raw: true }); }
      const add = krRowsParse(rows);
      if (!add.length) return toast("날짜·금액 줄을 찾지 못했어요");
      if (S.state.sample) endSample(false);
      S.state.kr_log = Model.krNorm([...(S.state.kr_log || []), ...add]); krHold();
      synthKR(); markDirty(); save(); renderAll(); toast(`${add.length}줄 불러왔어요`);
    } catch (e) { toast("불러오지 못했어요: " + e.message); }
  }
  // 웹 첫 화면에만 세리 한 명 (앱의 인물 다섯 명 중 원칙형). 내 수량을 넣으면 이 카드째 사라진다
  const SERI_WEB = ["숫자부터 넣어 볼까요. 판단은 그다음이에요.", "수량만 알려 주세요. 나머지는 제가 계산할게요.", "처음 오셨군요. 할 일은 하나예요. 수량 넣기.", "지금 보이는 건 샘플이에요. 내 숫자로 바꿔 보세요.", "어서 오세요. 숫자 하나면 시작돼요.", "추측은 나중에요. 먼저 숫자요.", "보유 수량만요. 그 이상은 묻지 않을게요.", "기록이 있어야 볼 수 있어요. 수량부터요.", "샘플은 샘플일 뿐이에요. 내 것을 넣어 보세요.", "드링커는 가상 종목이에요. 내 종목으로 바꿔 보세요.", "한 줄이면 돼요. 종목과 수량.", "오래 안 걸려요. 수량만 넣으면 끝나요.", "관찰은 숫자에서 시작해요.", "처음이시군요. 수량을 넣으면 오늘 값으로 다시 볼게요.", "판단은 제가 하지 않아요. 숫자를 보여 줄 뿐이에요.", "기준이 있어야 흔들리지 않아요. 먼저 지금을 적어요.", "숫자를 넣으면 목표까지 얼마 남았는지 보여요.", "잠깐요. 수량부터 넣고 이야기해요.", "사실만 볼게요. 내 수량을 알려 주세요.", "한 번 넣어 두면 다음부터는 보기만 하면 돼요.", "어서 오세요. 내일을 보려면 오늘 숫자가 필요해요."];
  const seriCard = () => `<div class="seri"><img src="seri.png" alt="세리" width="60" height="70"><div><div class="seri-say"><b>세리</b> ${esc(SERI_WEB[(S.seriPick ??= Math.floor(Math.random() * SERI_WEB.length))])}</div><div class="seri-body">`;

  function renderDash() {
    const g = S.state.goal, { total } = valuation(), yrs = yearsBetween(today(), g.date), cash = cashKrw(), tot = total + cash;
    $("#dashEmpty").style.display = total > 0 && !S.state.sample ? "none" : "block";
    $("#dashEmpty").innerHTML = seriCard() + (S.state.sample ? `<b>샘플 화면 (가상 종목 DRNK 4주·QQQ 1주)</b> <span class="small">${S.sampleCalc ? `${S.sampleCalc === today() ? "오늘" : `${+S.sampleCalc.slice(5, 7)}/${+S.sampleCalc.slice(8)}`} 9시 기준 계산값 · ` : ""}내 종목을 넣으면 지금 값으로 다시 계산합니다.</span> <button class="primary" data-go="stocks">내 수량 넣기</button>`
      : `<b>보유 수량을 넣어 주세요.</b> <span class="small">종목 탭에서 종목별 수량만 넣으면 나머지는 자동.</span> <button class="primary" data-go="stocks">수량 입력하러 가기</button>`) + `<div class="seri-app muted small">naeilo 앱에서는 세리 말고도 네 명이 더 함께해요.</div></div></div></div>`;
    // 필요 연수익률: 남은 기간에 넣을 적립을 뺀 나머지를 지금 자산이 불려야 하는 속도 (앱 내 길과 같은 식)
    const need = g.amount - tot, kAdj = g.amount - (Number(g.monthly_contribution) || 0) * 12 * Math.max(0, yrs), req = yrs > 0 && tot > 0 ? (Math.max(1, kAdj) / tot) ** (1 / yrs) - 1 : null;
    const H = history(), M = patModel(H);
    // 미래 기준을 직접 고른 적이 없고, 지난 12달 채점에서 패턴이 추세만보다 나으면 패턴을 기본으로
    if (!S.basisAuto) { S.basisAuto = true; let mine = null; try { mine = localStorage.getItem("naeilo-basis"); } catch (e) { /* 무시 */ } if (!mine && patWins(M)) $$("#histBasis button").forEach((b) => b.classList.toggle("on", b.dataset.b === "pattern")); }
    $("#basisPat").style.display = M ? "" : "none";
    if (!M && $("#histBasis .on")?.dataset.b === "pattern") $$("#histBasis button").forEach((b) => b.classList.toggle("on", b.dataset.b === "model"));
    const ret = (n) => { const k = H.index.length - 1; if (k < n && k >= n * 0.97) n = k; return k - n >= 0 ? H.index[k] / H.index[k - n] - 1 : null; };
    const kc = H.index.length - 1, jc = Math.max(0, kc - 756); // 과거 연평균은 최근 3년 (이력이 더 길어도)
    const pastCagr = kc - jc > 30 ? (H.index[kc] / H.index[jc]) ** (252 / (kc - jc)) - 1 : null;
    renderRoute(); renderProgress(H, total, cash); renderBackupNag(); renderBep(); renderMyRec();
    $("#goalKpis").innerHTML = [
      ["현재 평가액", `<a href="#" class="plain" data-go="stocks" data-add="1" title="종목 추가">${krw(tot)}원</a>`, cash ? `현금 ${krw(cash)}원 포함` : marUsd() ? `매매기준율 ${nf(marUsd().rate, 2)}원` : `매매기준율 없음 · 현재 환율 ${nf(fxNow("USD"), 1)}원`],
      ["목표 대비", pct(tot / g.amount), `<div class="bar"><i style="width:${Math.min(100, (tot / g.amount) * 100)}%"></i></div>`],
      ["남은 금액", krw(Math.max(0, need)) + "원", `목표 ${krw(g.amount)}원`],
      ["남은 기간", leftStr(yrs), g.date],
      ["필요 연수익률", req != null ? spct(req) : "-", Number(g.monthly_contribution) > 0 ? "적립 포함" : "지금 자산만으로"],
      ["과거 연평균 (원화)", pct(pastCagr), `${H.dates[jc] || "-"} 이후`],
    ].map(([k, v, s]) => `<div class="kpi"><div class="k">${k}</div><div class="v">${v}</div><div class="s">${s}</div></div>`).join("");

    const rsel = $("#histRange .on")?.dataset.r || "252", step = rsel === "future" || +rsel >= 780 ? "m" : +rsel >= 252 ? "w" : "d", mode = $("#histMode .on")?.dataset.m || "total";
    const inUsd = $("#histCcy .on")?.dataset.c === "usd", basis = $("#histBasis .on")?.dataset.b || "model";
    const fxNowUsd = fxNow("USD") || 1, conv = (v, i) => (v == null ? null : inUsd ? v / H.usdK[i] : v), money = inUsd ? usd : krwAxis;
    const future = rsel === "future", n = future ? 780 : +rsel; // 미래: 과거 3년 + 목표일까지
    // 과거 선은 지금 수량 × 지난 가격이라 "내가 지나온 길"이 아니다. 제목에서 그렇게 읽히게
    $("#histTitle").textContent = future ? "지금 구성으로 본 지난 3년과 앞으로" : `지금 구성으로 본 지난 ${{ 5: "1주", 22: "1달", 66: "3달", 252: "1년", 780: "3년" }[n] || "기간"}`;
    const fcOn = $("#histFc .on")?.dataset.f === "on"; // 미래 보기에서 목표일 전망 띠를 겹칠지 (기본: 내 길만)
    $("#histFc").style.display = future ? "" : "none"; $("#histBasis").style.display = future && !fcOn ? "none" : "";
    const k0 = Math.max(0, H.dates.length - 1 - n);
    // 간격: 3년·미래는 월간(그 달의 마지막 거래일 값), 1년은 주간(그 주 마지막 거래일), 3달 이하는 일간
    const key = (d) => { if (step === "m") return d.slice(0, 7); const t = new Date(d + "T00:00:00Z"); t.setUTCDate(t.getUTCDate() - ((t.getUTCDay() + 6) % 7)); return t.toISOString().slice(0, 10); };
    let ix = []; for (let i = k0; i < H.dates.length; i++) ix.push(i);
    if (step !== "d") {
      ix = ix.filter((i, j) => j === ix.length - 1 || key(H.dates[i]) !== key(H.dates[ix[j + 1]]));
    }
    // 과거 보기 끝에 오늘 실시간 값(현재 평가액)을 한 점 더 붙인다. 아직 마감 전이라 속이 빈 점으로 (i = -1)
    const td = today(), live = !future && tot > 0 && H.dates.length && td > H.dates[H.dates.length - 1];
    if (live) { if (step !== "d" && ix.length && key(H.dates[ix[ix.length - 1]]) === key(td)) ix.pop(); ix.push(-1); }
    const liveEach = {}; if (live) valuation().rows.forEach((r) => (liveEach[r.h.ticker] = (liveEach[r.h.ticker] || 0) + (r.valueKrw || 0)));
    const lconv = (v) => (v == null ? null : inUsd ? v / fxNowUsd : v);
    const tv = (i) => (i < 0 ? lconv(tot) : conv(H.total[i] + cash, i)), ev = (t, i) => (i < 0 ? lconv(liveEach[t] ?? null) : conv(H.each[t][i], i));
    const x = ix.map((i) => (i < 0 ? td : H.dates[i])), tick = Object.keys(H.each), col = (t) => C[tick.indexOf(t) % C.length];
    let series = [], bands = [];
    if (mode === "total") series = [{ name: cash ? "합계 (현금 포함)" : "합계", y: ix.map(tv), color: "var(--c1)", width: 2, lastDot: live ? td : null }];
    else if (mode === "each") series = tick.map((t) => ({ name: t, y: ix.map((i) => ev(t, i)), color: col(t), lastDot: live ? td : null }));
    else { // 누적: 종목 값을 쌓아 올린 띠
      let lo = ix.map(() => 0);
      tick.forEach((t) => { const hi = ix.map((i, k) => lo[k] + (ev(t, i) || 0)); bands.push({ lo, hi, color: col(t), opacity: 0.55, name: t }); lo = hi; });
      series = [{ y: lo, color: "var(--fg)", width: 1 }];
    }
    const opt = { x, series, bands, yfmt: money, height: 320, axisOut: true, hlines: [], vlines: [], ymin: mode === "total" ? undefined : 0, tipx: (d) => (live && d === td ? `${d} (오늘 실시간)` : d) };
    const goalV = inUsd ? g.amount / fxNowUsd : g.amount, goalLab = inUsd ? `목표 ${usd(goalV)} (지금 환율)` : "목표 " + krw(g.amount);
    const maxV = Math.max(...ix.map(tv));
    const notes = [];
    if (future) {
      const V0 = tot, last = x[x.length - 1] || today(), cashU = inUsd ? cash / fxNowUsd : cash, addC = (a) => (cashU ? a.map((v) => (v == null ? v : v + cashU)) : a);
      opt.vlines.push({ x: today(), label: "오늘" });
      if (mode !== "each") opt.hlines.push({ y: goalV, label: goalLab });
      const scen = basisScen(basis), F = fcOn && total > 0 && basis !== "pattern" ? fcReady(scen) : null;
      if (fcOn && basis === "pattern") {
        if (M && total > 0) {
          const pf = patFuture(M, g.date), u = inUsd ? fxNowUsd : 1, at = (k, z) => (total * Math.exp(pf.cum[k] + z * M.sd * Math.sqrt(k)) + cash) / u;
          if (mode === "total") {
            opt.bands.push({ x: pf.md, lo: pf.md.map((_, k) => at(k, -1.645)), hi: pf.md.map((_, k) => at(k, 1.645)), color: "var(--band)", opacity: 0.13, name: "패턴 5~95%" },
              { x: pf.md, lo: pf.md.map((_, k) => at(k, -0.674)), hi: pf.md.map((_, k) => at(k, 0.674)), color: "var(--band)", opacity: 0.25, name: "패턴 25~75%" });
            opt.series.push({ name: "패턴 예측", x: pf.md, y: pf.md.map((_, k) => at(k, 0)), color: "var(--c1)", width: 2, dash: "2 2" });
          } else notes.push("패턴 예측은 합계에서만 그립니다.");
          patScores(M);
          const yg = patYearGap(M, total);
          notes.push(`<b>패턴</b>: 작년·재작년 같은 날의 움직임(가중 ${M.p.a1}·${M.p.a2}) + 추세. 최근 12달 적중 ${M.sp.hit}/${M.folds.length} (추세만 ${M.st.hit}/${M.folds.length})`
            + (yg ? ` · 연초 예측 대비 연말 예상 <b class="${cls(yg.gap)}">${spct(yg.gap)}</b>` : "") + ". 띠는 지난 12달 오차 크기.");
        } else notes.push("패턴 예측에는 1년 반 넘는 시세 이력이 필요합니다.");
      }
      if (fcOn && !F && total > 0 && basis !== "pattern") forecastLater(scen, () => { if (dashOn()) renderDash(); });
      if (F && F.err) notes.push("전망 계산 실패: " + esc(F.err));
      else if (F) {
        const R = F.R, fd = F.model.monthDates, B = inUsd ? R.bandsUsd : R.bands;
        const byT = new Map(R.stocks.map((s2) => [s2.ticker, s2]));
        const vb = (t) => { const s2 = byT.get(t); return s2 && (inUsd ? s2.valBandsUsd : s2.valBands); };
        if (mode === "total") {
          opt.bands.push({ x: fd, lo: addC(B.p5), hi: addC(B.p95), color: "var(--band)", opacity: 0.13, name: "전망 5~95%" }, { x: fd, lo: addC(B.p25), hi: addC(B.p75), color: "var(--band)", opacity: 0.25, name: "전망 25~75%" });
          opt.series.push({ name: "전망 중앙값", x: fd, y: addC(B.p50), color: "var(--c1)", width: 2, dash: "2 2" });
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
      } else if (fcOn && total > 0 && basis !== "pattern") notes.push("전망을 계산하는 중입니다…");
      if (!fcOn) notes.push("보라 점선은 <b>계획선</b>: 목표 시작일의 평가액에서 목표일 목표액까지 정한 대로 가는 길. 시장이 줄 수 있는 범위는 '예보 겹치기'를 누르면 목표일 전망 띠로 겹쳐 봅니다.");
      if (fcOn && mode === "total" && basis === "smooth") {
        const pf = pastFit(H, basis);
        opt.series.push({ name: "3년 추세선 (과거)", y: ix.map((i) => conv(pf[i], i)), color: "var(--c7)", width: 1.4, dash: "4 3" });
      }
      const md = []; for (let k = 0; k <= 1200 && Model.addMonths(today(), k) <= g.date; k++) md.push(Model.addMonths(today(), k));
      if (md[md.length - 1] !== g.date) md.push(g.date);
      // 내 길: 목표 진행·목표일 전망과 같은 길 (목표 시작일부터 목표일까지). 시작일 전은 그리지 않는다
      const GP = V0 > 0 && mode !== "each" ? goalPath(H, cash) : null;
      if (GP && GP.at(today()) != null) {
        const px = [...x.filter((d) => d >= GP.start), ...md.filter((d) => d > last)], u = inUsd ? fxNowUsd : 1;
        opt.series.push({ name: "계획선", x: px, y: px.map((d) => GP.at(d) / u), color: "var(--goal)", dash: "5 4", width: 1.8 });
      } else if (V0 > 0 && mode !== "each") opt.series.push({ name: "계획선", x: [last, ...md], y: [conv(H.total[H.total.length - 1] + cash, H.total.length - 1), ...md.map((d) => (V0 * (g.amount / V0) ** (yearsBetween(today(), d) / Math.max(0.01, yearsBetween(today(), g.date)))) / (inUsd ? fxNowUsd : 1))], color: "var(--goal)", dash: "5 4", width: 1.8 });
    } else {
      if (mode !== "each" && goalV <= maxV * 1.05) opt.hlines.push({ y: goalV, label: "목표" });
      notes.push("지금 수량을 지난 가격에 대입한 값이에요. 실제로 지나온 기록과는 달라요" + (inUsd ? " (달러 환산)." : "."));
      if (live) notes.push("· 맨 끝 빈 점은 오늘 실시간 값 (장 마감 전).");
      // 필요 경로: 목표 진행과 같은 길 (목표 시작일부터). 시작일 전은 그리지 않는다
      const P = mode !== "each" && tot > 0 ? goalPath(H, cash) : null;
      if (P) {
        const py = ix.map((i, k) => { const v = P.at(x[k]); return v == null ? null : i < 0 ? lconv(v) : conv(v, i); });
        if (py.filter((v) => v != null).length >= 2) { opt.series.push({ name: "계획선", y: py, color: "var(--goal)", dash: "5 4", width: 1.8 }); notes.push(`· 보라 점선은 계획선 (${P.start} 시작, 그 전은 같은 속도로 거꾸로 늘인 길).`); }
      }
    }
    $("#histNote").innerHTML = notes.join(" ");
    Charts.lineChart($("#histChart"), opt);
    weekRecord(H, M, cash); renderHit(H, M, cash); renderDia(H, total, cash);

    const periods = [["1일", 1], ["1주", 5], ["1개월", 21], ["3개월", 63], ["6개월", 126], ["1년", 252], ["3년", 756]];
    $("#periodTable").innerHTML = `<tr>${periods.map((p) => `<th>${p[0]}</th>`).join("")}</tr><tr>${periods.map((p) => { const v = ret(p[1]); return `<td class="${cls(v)}">${spct(v)}</td>`; }).join("")}</tr>`;
  }
  // 종목 탭 종목 진단: 종목별 가격 (현지 통화)
  function renderStockPrices() {
    const H = history(), n = +($("#stockRange .on")?.dataset.r || 252), from = H.dates[Math.max(0, H.dates.length - 1 - n)];
    const host = $("#dashStocks"); host.innerHTML = "";
    if (!from) { host.innerHTML = "<p class='muted small'>보유 수량이 있는 종목의 시세가 필요합니다.</p>"; return; }
    S.state.holdings.filter((h) => S.prices[h.ticker]).forEach((h, j) => {
      const p = S.prices[h.ticker], i0 = Math.max(0, p.dates.findIndex((d) => d >= from)), xs = p.dates.slice(i0), ys = p.close.slice(i0);
      const ch = ys.length > 1 ? ys[ys.length - 1] / ys[0] - 1 : null, box = document.createElement("div");
      box.innerHTML = `<h3>${esc(h.ticker === KR_SUM ? "국내 주식 합계" : h.ticker)} <span class="muted small">${nf(ys[ys.length - 1], 2)} ${ccyOf(h.ticker)} · 기간 <span class="${cls(ch)}">${spct(ch)}</span></span></h3><div class="chartbox"></div>`;
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
  function alignedChanges(symA, symB, rateB, years = 3) { return Model.alignedChanges(S.prices, symA, symB, rateB, today(), years); }
  function factorBetas() {
    const stamp = Object.values(S.prices).map((p) => p.dates.at(-1)).join() + S.state.holdings.map((h) => h.ticker).join();
    if (betaCache && betaCache.stamp === stamp) return betaCache;
    const tks = S.state.holdings.map((h) => h.ticker).filter((t) => S.prices[t]);
    return (betaCache = { stamp, ...Model.factorBetas(mcPrices(), tks, today()) }); // 계산은 model.js (사이트 통계 stats.json 도 같은 함수)
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
  // 외부 요인: 요인 순위(보통 한 달 변화 × 내 종목 민감도)와 요인 상세 (앱 외부 요인 화면과 같은 식)
  const XF_NAMES = { mkt: "미국 시장", fx: "원/달러", rate: "미국 금리", oil: "유가", gold: "금값" };
  const XF_TINY = 0.001, XF_WEAK = 0.05; // 평가액 0.1% 미만이면 '거의 그대로', 같은 방향 비율이 50%±5%p 안이면 '뚜렷한 관계 없음'
  const XS = { k: null, v: null, custom: false };
  const josa = (k, a, b) => (k === "rate" || k === "fx" ? a : b);
  const man = (v) => { const a = Math.abs(v); return a >= 1e8 ? `${Math.floor(a / 1e8)}억${Math.round((a % 1e8) / 1e4) ? " " + nf(Math.round((a % 1e8) / 1e4)) + "만" : ""}원` : `${nf(Math.round(a / 1e4))}만원`; };
  const manS = (v) => (v > 0 ? "+" : v < 0 ? "−" : "") + man(v);
  // 요인이 v 만큼 움직일 때 종목 하나의 변화율 (금리 %p, 원/달러 원, 나머지 %). 모르면 null
  function xfMove(k, r, v) {
    if (k === "fx") { const fx = fxNow("USD"); return r.ccy === "USD" && fx ? v / fx : 0; }
    const b = factorBetas().beta[k]?.[r.h.ticker]; if (b == null) return null;
    return Math.exp(b * (k === "rate" ? v * 100 : Math.log(1 + v / 100))) - 1;
  }
  // 요인의 '보통 한 달 변화'를 상세와 같은 단위로
  let xfMovesCache = null;
  function xfMonth(k) {
    const stamp = Object.values(S.prices).map((p) => p.dates.at(-1)).join();
    if (!xfMovesCache || xfMovesCache.stamp !== stamp) xfMovesCache = { stamp, m: Model.factorMoves(mcPrices(), today()) };
    return xfMovesCache.m[k];
  }
  function xfMonthShock(k) {
    const sd = xfMonth(k)?.sd; if (!(sd > 0)) return null;
    return k === "rate" ? sd / 100 : k === "fx" ? fxNow("USD") * (Math.exp(sd) - 1) : (Math.exp(sd) - 1) * 100;
  }
  const xfAgree = (k, r) => (k === "fx" ? null : factorBetas().stat[k]?.[r.h.ticker]?.agree ?? null);
  function xfRanks(hs, total) {
    return ["mkt", "fx", "rate", "oil", "gold"].map((k) => {
      const v = xfMonthShock(k); if (v == null) return { k, amt: 0, gross: 0, weak: true };
      const dvs = hs.map((r) => r.valueKrw * (xfMove(k, r, v) ?? 0)), tot = dvs.reduce((a, x) => a + x, 0), gross = dvs.reduce((a, x) => a + Math.abs(x), 0);
      const agreeWeak = k !== "fx" && hs.every((r) => { const g = xfAgree(k, r); return g == null || Math.abs(g - 0.5) < XF_WEAK; });
      return { k, amt: Math.abs(tot), gross, weak: Math.abs(tot) < total * XF_TINY || agreeWeak };
    }).sort((a, b) => (b.weak ? 0 : 1) - (a.weak ? 0 : 1) || b.amt - a.amt);
  }
  function xfDesc(x, hs, total) {
    if (x.weak) return "지난 3년 동안 내 종목과 일정한 관계가 보이지 않았어요.";
    if (x.k === "mkt") { const bm = factorBetas().beta.mkt || {}, b = hs.reduce((a, r) => a + r.w * (bm[r.h.ticker] ?? 1), 0); return b > 1.15 ? `내 종목은 시장보다 ${b.toFixed(1)}배쯤 크게 움직여요.` : b < 0.85 ? "내 종목은 시장보다 작게 움직여요." : "내 종목은 시장과 비슷하게 움직여요."; }
    if (x.k === "fx") return `달러 자산이 ${pct(hs.filter((r) => r.ccy === "USD").reduce((a, r) => a + r.valueKrw, 0) / total, 0)}라서 환율이 그대로 반영돼요. 시장이 급락할 땐 오히려 손실을 막아주기도 해요.`;
    if (x.gross > x.amt * 2) return `종목별로는 ±${man(x.gross / 2)}씩 움직이지만, 서로 반대로 움직여서 상쇄돼요.`;
    return `${XF_NAMES[x.k]}${josa(x.k, "가", "이")} 움직이면 내 종목 대부분이 같은 쪽으로 반응해요.`;
  }
  function xfSummary(rs) {
    const strong = rs.filter((x) => !x.weak), top = strong[0]; if (!top || !(top.amt > 0)) return null;
    const n = XF_NAMES[top.k], rest = strong.slice(1).reduce((a, x) => a + x.amt, 0), tail = " 요인마다 따로 움직였을 때의 영향이에요.";
    if (strong.length === 1) return `내 자산을 흔드는 건 사실상 ${n} 하나예요.` + tail;
    if (rest < top.amt / 3) return `내 자산이 흔들리는 이유는 대부분 ${n} 하나예요. 다른 요인을 다 합쳐도 ${n}의 3분의 1이 안 돼요.` + tail;
    if (rest < top.amt) return `내 자산을 가장 크게 흔드는 건 ${n}이고, 나머지를 다 합쳐도 그보다 작아요.` + tail;
    return `${n}과 ${XF_NAMES[strong[1].k]}이 비슷하게 내 자산을 흔들어요.` + tail;
  }
  function xfPresets(k) {
    const fx = fxNow("USD") || 1300;
    if (k === "rate") { const y = rateChange1y(null), yr = Number.isFinite(y) ? Math.round(y * 10) / 10 : 0; return [["내린다", -0.5], ["오른다", 0.5], [yr ? (yr > 0 ? "작년만큼 또 오른다" : "작년만큼 또 바뀐다") : "크게 오른다", yr || 1], ["2022년처럼 급등", 2.5]]; }
    if (k === "fx") return [["1,300원", 1300 - fx], ["1,500원", 1500 - fx], ["10% 내린다", -fx * 0.1], ["2022년처럼 +20%", fx * 0.2]];
    if (k === "mkt") return [["흔한 나쁜 날", -2], ["나쁜 한 달", -8], ["2022년처럼", -25], ["좋은 한 달", 8]];
    return [["10% 내린다", -10], ["10% 오른다", 10], ["30% 내린다", -30], ["30% 오른다", 30]];
  }
  const xfRange = (k) => (k === "rate" ? [-1.5, 1.5, 0.25] : k === "fx" ? [-300, 300, 20] : k === "oil" ? [-40, 40, 5] : [-30, 30, 5]);
  function xfText(k, v, short) {
    const sg = v > 0 ? "+" : v < 0 ? "−" : "", a = Math.abs(v);
    if (k === "rate") return sg + Number(a.toFixed(2)) + "%p";
    if (k === "fx") return short ? `${sg}${nf(a)}원` : `${nf(fxNow("USD") + v)}원 (${sg}${nf(a)}원)`;
    return sg + Number(a.toFixed(1)) + "%";
  }
  function xfHuge(k, v) {
    const mx = xfMonth(k)?.max; if (!(mx > 0)) return false;
    return k === "rate" ? Math.abs(v * 100) > mx : k === "fx" ? Math.abs(Math.log((fxNow("USD") + v) / fxNow("USD"))) > mx : Math.abs(Math.log(1 + v / 100)) > mx;
  }
  function renderSens() {
    const card = $("#sensCard"); if (!card) return;
    const { rows, total } = valuation(), hs = rows.filter((r) => r.valueKrw > 0);
    if (!(total > 0)) { card.style.display = "none"; return; }
    card.style.display = "block";
    $("#sens").innerHTML = XS.k ? xfDetail(XS.k, hs, total) : xfMain(hs, total);
  }
  function xfMain(hs, total) {
    const rs = xfRanks(hs, total), strong = rs.filter((x) => !x.weak), scale = Math.max(1, ...strong.map((x) => x.amt)), sum = xfSummary(rs);
    return `<h2 class="xq">바깥에서 무슨 일이 생기면, 지금 내 돈은 얼마나 움직일까요?</h2>
      <p class="muted small">지난 3년 동안 실제로 하루하루 움직인 만큼으로 계산해요. 목표까지의 영향은 목표일 전망에서 봐요.</p>
      ${xfToday(hs, total)}<h3 class="xh">내 자산을 흔드는 요인</h3><p class="muted small">요인마다 '보통 한 달에 움직이는 만큼'으로 맞춰서 비교했어요.</p>` +
      rs.map((x) => { const no = strong.indexOf(x) + 1;
        return `<button type="button" class="xrank${x.weak ? " weak" : ""}" data-xf="${x.k}"><span class="no${no === 1 ? " one" : ""}">${no || "–"}</span><span class="bd">
          <span class="row between"><b>${XF_NAMES[x.k]}</b><span class="${x.weak ? "muted" : "amt"}">${x.weak ? "뚜렷한 관계 없음" : "±" + man(x.amt)}</span></span>
          ${x.weak ? "" : `<span class="bar"><i style="width:${Math.max(1, (100 * x.amt) / scale)}%"></i></span>`}<span class="muted small">${xfDesc(x, hs, total)}</span></span><span class="chev">›</span></button>`; }).join("") +
      (sum ? `<p class="xsum">${sum}</p>` : !xfMonth("mkt") ? `<p class="muted small">요인 자료를 불러오는 중이에요.</p>` : "") +
      xfReplays(hs, total) + xfUpcoming(hs) +
      `<p class="muted small">예측이 아니라 '만약에'를 보는 화면이에요. 지난 3년 하루 단위 움직임을 바탕으로 했고, 큰 변화일수록 실제와 다를 수 있어요.</p>`;
  }
  // 일정별 '보통 움직임': 확률 100%·날짜가 정해진 시장 전체 일정만. 발표일 요인 변화 = EXT_EVENTS.sd (고정 가정값). 앱 SiteCalc.eventMoves 와 같은 값
  function xfEvents() {
    const t0 = today(), d = new Date(t0 + "T00:00:00Z"), ds = (n) => new Date(d.getTime() + n * 864e5).toISOString().slice(0, 10), fx = fxNow("USD") || 1300;
    return Model.EXT_EVENTS.filter((e) => e.prob === 100 && (e.target === "ALL" || e.target === "FX") && (e.factor || e.target === "FX"))
      .flatMap((e) => { const k = e.factor || "fx", v = k === "fx" ? -fx * e.sd / 100 : -e.sd;
        return Model.occurrences(e, ds(-1), ds(45)).map((day) => ({ e, k, v, day, n: Math.round((new Date(day + "T00:00:00Z") - d) / 864e5) })); })
      .sort((a, b) => (a.day < b.day ? -1 : a.day > b.day ? 1 : 0));
  }
  const xfEvAmt = (x, hs) => Math.abs(hs.reduce((a, r) => a + r.valueKrw * (xfMove(x.k, r, x.v) ?? (x.k === "mkt" ? x.v / 100 : 0)), 0));
  const xfWhen = (n, day) => (n === 0 ? "오늘" : n === 1 ? "내일" : `${+day.slice(5, 7)}/${+day.slice(8)} (D-${n})`);
  const XF_EV_BASE = { mkt: "미국 시장", fx: "원/달러", oil: "유가" };
  // 오늘의 이벤트 카드: 가장 가까운 일정 하나. 45일 안에 없으면 숨김
  function xfToday(hs, total) {
    const x = xfEvents()[0]; if (!x) return "";
    const bm = factorBetas().beta.mkt || {}, b = hs.reduce((a, r) => a + r.w * (bm[r.h.ticker] ?? 1), 0);
    const why = `발표일 ${XF_EV_BASE[x.k]} 보통 ±${x.e.sd}%` + (x.k === "mkt" ? `, 내 종목은 시장의 ${b.toFixed(1)}배쯤` : "");
    return `<div class="xtoday"><span class="small">${xfWhen(x.n, x.day)} · ${x.e.kind}</span><p class="small">결과에 따라 내 자산은 보통</p><b>약 ±${man(xfEvAmt(x, hs))}</b>
      <p class="small">${why} (가정값)</p><button type="button" data-xe="${x.k}:${x.v}">이 상황 미리 보기</button></div>`;
  }
  // 다가오는 일, 미리 겪어보기
  function xfUpcoming(hs) {
    const xs = xfEvents().slice(0, 8);
    return `<h3 class="xh">다가오는 일, 미리 겪어보기</h3>` + (xs.length ? xs.map((x) => `<div class="xev"><span class="muted small">${xfWhen(x.n, x.day)}</span><span class="bd"><b>${x.e.kind}</b><span class="muted small">${XF_EV_BASE[x.k]}${x.k === "mkt" ? "을" : "를"} 통해 보통 ±${man(xfEvAmt(x, hs))}</span></span><button type="button" data-xe="${x.k}:${x.v}">미리 보기</button></div>`).join("")
      : `<p class="muted small">45일 안에 정해진 일정이 없어요.</p>`) + `<p class="muted small">일정마다 발표일 보통 움직임은 고정 가정값이에요. 미리 보기는 나쁘게 나온 쪽으로 열어요.</p>`;
  }
  // 과거 재생: 그때 함께 움직인 요인 변화 × 지금 내 종목 민감도
  function xfReplays(hs, total) {
    const fb = factorBetas().beta;
    return `<h3 class="xh">그때 그 일이 지금 일어나면</h3><p class="muted small">그때 실제로 함께 움직인 만큼을 지금 내 구성에 적용했어요.</p>` +
      Model.REPLAYS.map((rp) => {
        let tot = 0, fx = 0;
        hs.forEach((r) => { const o = Model.replayMove(rp, r.ccy === "USD", (k) => fb[k]?.[r.h.ticker] ?? null); tot += r.valueKrw * o.total; fx += r.valueKrw * o.fx; });
        const chips = ["mkt", "rate", "fx"].filter((k) => k !== "rate" || Math.abs(rp.mv.rate) >= 1).map((k) => `${XF_NAMES[k]} ${xfText(k === "fx" ? "pct" : k, rp.mv[k])}`);
        const small = Math.abs(fx) < total * XF_TINY, good = !small && (tot < 0 ? fx > 0 : fx > 0);
        const note = small ? "달러 자산이 거의 없어 환율 영향은 작았어요." : tot < 0 ? (fx > 0 ? `환율이 오르면서 ${man(fx)}을 막아줬어요.` : `이번엔 환율도 함께 내려서 손실을 ${man(fx)} 더 키웠어요.`) : fx > 0 ? `환율이 오르면서 ${man(fx)}을 보탰어요.` : `환율이 내리면서 ${man(fx)}을 깎았어요.`;
        return `<div class="xrep"><div class="row between"><span class="muted small">${rp.when} · ${rp.name}</span><b class="${cls(tot)}">${manS(tot)}</b></div>
          <div class="xchips">${chips.map((c) => `<span>${c}</span>`).join("")}</div><p class="small ${good ? "xok" : "xbad"}">${note}</p></div>`;
      }).join("") + `<p class="muted small">요인마다 따로가 아니라 그때 함께 움직인 값을 썼어요. 지난 3년 시세가 없는 종목은 시장과 같이 움직인다고 봤어요.</p>`;
  }
  function xfDetail(k, hs, total) {
    const name = XF_NAMES[k], ps = xfPresets(k), v = XS.v ?? ps[1][1], ups = k === "rate" ? "금리가 오르면" : k === "fx" ? "환율이 오르면" : `${name}이 오르면`;
    const rows = hs.map((r) => { const mv = xfMove(k, r, v), dv = r.valueKrw * (mv ?? 0), ag = xfAgree(k, r);
      return { r, dv, ag, known: mv != null, weak: mv == null || Math.abs(dv) < total * XF_TINY || (ag != null && Math.abs(ag - 0.5) < XF_WEAK) }; }).sort((a, b) => Math.abs(b.dv) - Math.abs(a.dv));
    const tot = rows.reduce((a, x) => a + x.dv, 0), up = rows.filter((x) => x.dv > 0).reduce((a, x) => a + x.dv, 0), dn = rows.filter((x) => x.dv < 0).reduce((a, x) => a + x.dv, 0), rr = tot / total;
    const verdict = Math.abs(rr) < XF_TINY ? "거의 그대로예요" : Math.abs(rr) < 0.02 ? (rr > 0 ? "조금 늘어요" : "조금 줄어요") : rr > 0 ? "많이 늘어요" : "많이 줄어요";
    const allWeak = rows.every((x) => x.weak), weak = rows.filter((x) => x.weak), shown = weak.length > 3 ? rows.filter((x) => !x.weak) : rows;
    const sMax = Math.max(up, -dn, 1), scale = Math.max(1, ...rows.map((x) => Math.abs(x.dv))), sgn = v >= 0 ? 1 : -1;
    const lab = (x) => !x.known ? "지난 3년 시세가 부족해 0으로 봤어요" : k === "fx" ? (x.dv === 0 ? "원화 자산이라 영향 없어요" : "달러 자산이라 환율만큼 움직여요") : x.weak ? "뚜렷한 관계 없음" : `${ups} ${(x.dv > 0) === (sgn > 0) ? "함께 올랐어요" : "내렸어요"}`;
    const side = (t, x, c) => `<div class="xside"><span>${t}</span><span class="bar"><i style="width:${(100 * Math.abs(x)) / sMax}%;background:var(--${c})"></i></span><b>${manS(x)}</b></div>`;
    const [lo, hi, st] = xfRange(k), sv = Math.min(hi, Math.max(lo, v));
    const desc = k === "rate" ? "미국 10년 금리 기준이에요. 시장 전체 움직임은 빼고, 지난 3년 하루하루 실제로 움직인 만큼으로 계산해요." : k === "fx" ? `달러 자산은 환율이 바뀐 만큼 원화 평가액이 바뀌어요. 지금 환율 ${nf(fxNow("USD"))}원 기준이에요.` : k === "mkt" ? "S&amp;P500 기준이에요. 지난 3년 하루하루 실제로 함께 움직인 만큼으로 계산해요." : "시장 전체 움직임은 빼고, 지난 3년 하루하루 실제로 움직인 만큼으로 계산해요.";
    return `<button type="button" class="sm" data-xf="">‹ 외부 요인</button>
      <h2 class="xq">${name}${josa(k, "가", "이")} 이렇게 되면, 지금 내 돈은 얼마나 움직일까요?</h2><p class="muted small">${desc}</p>
      <div class="xpre">${ps.map(([t, p], i) => `<button type="button" class="${!XS.custom && Math.abs(v - p) < 1e-9 ? "on" : ""}" data-xp="${i}"><b>${t}</b><span>${xfText(k, p, true)}</span></button>`).join("")}</div>
      <div class="xverd"><p>${name} ${xfText(k, v)}이면, 내 자산은</p><div class="big">${allWeak && k !== "fx" ? "뚜렷한 관계 없음" : verdict}</div>
        <p class="${cls(tot)}"><b>내 자산 ${manS(tot)} · ${spct(rr, 2)}</b></p>${up > 0 || dn < 0 ? side("오르는 쪽", up, "up") + side("내리는 쪽", dn, "dn") : ""}
        ${up > 0 && dn < 0 && Math.abs(tot) < Math.min(up, -dn) ? `<p>종목끼리 반대로 움직여서 서로 상당 부분 상쇄돼요.</p>` : ""}</div>
      ${xfHuge(k, v) ? `<p class="xwarn">이만큼 큰 변화는 지난 3년 안에 한 달 사이에 없었어요. 그동안의 관계가 그대로 이어진다는 가정이라 실제와 많이 다를 수 있어요.</p>` : ""}
      <h3 class="xh">종목별로 보면</h3>${shown.map((x) => { const w = 50 * Math.min(1, Math.abs(x.dv) / scale);
        return `<div class="xstk${x.weak ? " weak" : ""}"><div class="row between"><b>${esc(x.r.h.ticker)}</b><span class="${x.weak ? "muted" : cls(x.dv)}">${x.weak ? "뚜렷한 관계 없음" : manS(x.dv)}</span></div>
          <span class="cbar"><i style="${x.dv >= 0 ? "left:50%" : `left:${50 - w}%`};width:${Math.max(0.5, w)}%;background:var(--${x.dv >= 0 ? "up" : "dn"})"></i></span>
          <span class="muted small">${lab(x)}${x.ag != null && !x.weak ? ` · 10번 중 ${Math.round(x.ag * 10)}번 이 방향이었어요` : ""}</span></div>`; }).join("")}
      ${weak.length > 3 ? `<p class="muted small">나머지 ${weak.length}개 · 뚜렷한 관계 없음</p>` : ""}${rows.length ? "" : `<p class="muted small">보유 종목이 없어요.</p>`}
      <h3 class="xh">${k === "rate" ? "금리 시나리오와" : "목표일 전망과"} 숫자가 왜 달라요?</h3>
      <div class="xcmp"><div><b>이 화면</b><span>그날그날 실제로 함께 움직인 만큼. 오늘~한 달 사이의 영향</span></div><div><b>${k === "rate" ? "금리 시나리오" : "목표일 전망"}</b><span>${k === "rate" ? "금리가 회사 이익을 바꾸는 길로 본 3년 영향" : "모든 요인이 함께 움직이는 3년 뒤 분포"}</span></div></div>
      <p class="muted small">보는 기간과 길이 달라서 방향이 다를 수 있어요. 목표에 닿는지는 그 화면에서 봐요. <button type="button" class="sm" data-jump="${k === "rate" ? "sec-rs" : "sec-fc"}">목표에 미치는 영향 보기</button></p>
      <details class="xcust"${XS.custom ? " open" : ""}><summary><b>${name} 변화 직접 정하기</b></summary><label class="row between"><span>직접 정한 값</span><b id="xsV">${xfText(k, v)}</b></label><input type="range" id="xsR" min="${lo}" max="${hi}" step="${st}" value="${sv}"></details>
      <p class="muted small">예측이 아니라 '만약에'를 보는 화면이에요. 요인 하나만 바뀐다고 가정한 값이고, 실제로는 여러 요인이 함께 움직여요.</p>`;
  }
  document.addEventListener("click", (e) => {
    const f = e.target.closest("[data-xf]"), p = e.target.closest("[data-xp]");
    const xe = e.target.closest("[data-xe]");
    if (xe) { const [k, v] = xe.dataset.xe.split(":"); XS.k = k; XS.v = +v; XS.custom = true; renderSens(); $("#sensCard")?.scrollIntoView({ block: "start" }); return; }
    if (f) { XS.k = f.dataset.xf || null; XS.v = null; XS.custom = false; renderSens(); $("#sensCard")?.scrollIntoView({ block: "start" }); }
    else if (p && XS.k) { XS.v = xfPresets(XS.k)[+p.dataset.xp][1]; XS.custom = false; renderSens(); }
  });
  document.addEventListener("change", (e) => { if (e.target.id === "xsR" && XS.k) { XS.v = +e.target.value; XS.custom = true; renderSens(); } });
  document.addEventListener("input", (e) => { if (e.target.id === "xsR" && XS.k) $("#xsV").textContent = xfText(XS.k, +e.target.value); });
  function renderXf() {
    const sub = curSub(), host = $("#xfAnalysis"); if (!host || !heldTks().length) return;
    host.style.display = "block";
    const seen = new Set(), charts = [];
    host.innerHTML = `<h2>${esc(sub.g.n)} · ${esc(sub.n)} 분석</h2>` + sub.cats.map((c, j) => xfSection(c, seen, j, charts, sub.cats.length > 1)).join("") +
      (sub.k === "fin" ? `<p class="small" style="margin-top:12px">금리가 움직이면 시장 이익과 내 목표 확률이 어떻게 바뀌는지는 위 <b>금리 충격 테스트</b>에서 봅니다. <button type="button" class="sm" data-jump="sec-rs">보러 가기</button></p>` : "");
    charts.forEach((f) => f());
  }
  // ------------------------------------------------------------ 금리 시나리오: 금리→이익 관계식 식단 1000벌 (data/macro, 실러 데이터)
  // 식단 j, y년째 S&P 실질 EPS 변화(로그 %) = a + b·Δ금리 + c·지금 EPS 증가율. 무작위 경로 없이 식단마다 답 하나
  const RS = { pick: 2, custom: 0, dr: 0, m: 0 };   // pick: 프리셋 번호 (기본 "작년만큼 또 오른다"), -1 이면 직접 정한 값. 시장 공통 기대수익(m)은 이 화면에서 뺐다
  // 금리 충격 테스트 닫힌 식 (앱 RateScenarioView 의 closed·months 와 같은 식. tools/calc_parity.mjs 가 비교)
  // c = { T 햇수, sg 연 변동성, mu 연 기대, V0 지금 자산, K 목표, M 매달 넣는 돈 }
  function rsNormCDF(x) {
    const t = 1 / (1 + 0.2316419 * Math.abs(x)), d = 0.3989423 * Math.exp(-x * x / 2);
    const p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))));
    return x > 0 ? 1 - p : p;
  }
  function rsClosed(c, shift) {
    const T = c.T, sg = c.sg, g = c.mu - sg * sg / 2 + shift;
    const eff = c.V0 + c.M * 12 * T * Math.exp(-g * T / 2);
    if (!(eff > 0) || !(c.K > 0)) return 0;
    return rsNormCDF((Math.log(eff / c.K) + g * T) / (sg * Math.sqrt(T)));
  }
  // 보통의 경우(로그 기대 + shift) 매달 M씩 넣으며 K에 처음 닿는 개월. 50년 안에 못 닿으면 null
  function rsMonths(c, shift) {
    if (!(c.K > 0)) return null;
    if (c.V0 >= c.K) return 0;
    const gm = Math.exp((c.mu - c.sg * c.sg / 2 + shift) / 12);
    let v = c.V0;
    for (let n = 1; n <= 600; n++) { v = v * gm + c.M; if (v >= c.K) return n; }
    return null;
  }
  const rsPresets = (y) => [["내린다", "10년물 −0.5%p", -0.5], ["그대로", "변화 없음", 0], ["작년만큼 또 오른다", `${y >= 0 ? "+" : "−"}${Math.abs(y).toFixed(1)}%p`, y], ["2022년처럼 급등", "+2.5%p", 2.5]];
  // 다가오는 금리 일정: 앱 MarketEvents 와 같은 고정 규칙 (FOMC·한국은행, 6주 반복). 실제 일정과 하루이틀 다를 수 있다
  function rsEvents(days) {
    const t0 = today(), until = new Date(Date.parse(t0) + days * 86400e3).toISOString().slice(0, 10), out = [];
    for (const [d0, kind] of [["2026-10-28", "미국 금리 결정(FOMC)"], ["2026-10-22", "한국은행 금리 결정"]]) {
      for (let k = -60; k < 60; k++) { const d = new Date(Date.parse(d0) + k * 42 * 86400e3).toISOString().slice(0, 10); if (d >= t0 && d <= until) out.push({ d, kind }); }
    }
    return out.sort((a, b) => (a.d < b.d ? -1 : 1));
  }
  async function renderRateScen() {
    const box = $("#rsBox"); if (!box) return;
    let d; try { d = await loadMacro(); } catch (e) { box.innerHTML = `<p class="muted small">금리 자료를 불러오지 못했습니다 (${esc(e.message)}).</p>`; return; }
    const M = d.meals; if (!M || !M.coef || !$("#rsBox")) return;
    const now = rateChange1y(d), y = Number.isFinite(now) ? Math.round(now * 10) / 10 : 0, P = rsPresets(y);
    box.innerHTML = `<h2 style="margin:0 0 4px">금리가 이렇게 되면, 내 목표는 얼마나 흔들릴까요?</h2>
      <p class="muted small" style="margin:0 0 10px">목표일 전망의 내 설정을 기준으로, 금리만 바꿔 봤을 때 달라지는 만큼만 보여줘요.</p>
      <div id="rsPick" style="display:grid;grid-template-columns:1fr 1fr;gap:8px">${P.map(([l, s2], i) => `<button type="button" data-i="${i}" style="text-align:left;padding:10px;border-radius:12px"><b>${esc(l)}</b><br><span class="small muted">${esc(s2)}</span></button>`).join("")}</div>
      <div id="rsOut"></div>
      <details style="margin-top:10px"><summary><b>금리 변화 직접 정하기</b></summary>
        <label><span class="row between"><span>앞으로 1년 10년물 금리 변화</span><b id="rsDrV"></b></span><input type="range" id="rsDr" min="-2" max="3" step="0.1" value="${RS.custom}"></label>
        ${Number.isFinite(now) ? `<p class="muted small">지난 1년 실제 변화 ${now >= 0 ? "+" : "−"}${Math.abs(now).toFixed(2)}%p</p>` : ""}
      </details>
      <p class="muted small">예측이 아니라 '만약에'를 보는 화면이에요. 금리 말고 다른 조건은 목표일 전망의 내 설정과 같아요. 1960년 이후 금리와 S&amp;P500 이익의 관계식 1000개로 계산 (이익 자료 ${esc(d.eps_last || "")}까지, 출처 Robert J. Shiller). 종목 추천이 아닙니다.</p>`;
    const draw = RS.draw = () => { RS.dr = RS.pick >= 0 ? P[RS.pick][2] : RS.custom; RS.m = 0; rateScenDraw(d, P); };
    box.querySelectorAll("#rsPick button").forEach((b) => (b.onclick = () => { RS.pick = +b.dataset.i; draw(); }));
    $("#rsDr").oninput = (e) => { RS.custom = +e.target.value; RS.pick = -1; draw(); };
    draw();
  }
  function rateScenDraw(d, P) {
    const M = d.meals, H = M.coef[0].length, dr = RS.dr, m = RS.m / 100, g0 = M.g_now, q = (a, p) => Model.quantileSorted(Float64Array.from(a).sort(), p);
    const sgn = (v, u, k) => `${v >= 0 ? "+" : "−"}${Math.abs(v).toFixed(k)}${u}`;
    $("#rsDrV").textContent = sgn(dr, "%p", 1);
    $("#rsPick").querySelectorAll("button").forEach((b, i) => { const on = i === RS.pick; b.style.border = on ? "2px solid var(--accent)" : "1px solid var(--line)"; b.setAttribute("aria-pressed", on); });
    const tot = M.coef.map((c) => c.map(([a, b, cc]) => a + b * dr + cc * g0)), cum = tot.map((t) => t.reduce((x, y, i) => (x.push((x[i - 1] || 0) + y), x), []));
    const band = (arr) => [0.1, 0.5, 0.9].map((p) => q(arr, p));
    const yr = Array.from({ length: H }, (_, y) => band(tot.map((t) => t[y]))), cb = Array.from({ length: H }, (_, y) => band(cum.map((t) => t[y])));
    // 시장 연 수익 ≈ 3년 누적 이익 변화/3 + 배당 1.5% + 금리 외 공통 기대수익
    const mkt = band(cum.map((t) => Math.exp(t[H - 1] / 100 / H) - 1 + 0.015 + m));
    // 포트폴리오 시장 민감도 β (외부 요인 화면과 같은 값)
    const { rows, total } = valuation(), bm = factorBetas().beta.mkt || {}, held = rows.filter((r) => r.valueKrw > 0);
    const kb = held.length ? held.reduce((s2, r) => s2 + r.w * (Number.isFinite(bm[r.h.ticker]) ? bm[r.h.ticker] : 1), 0) : 1;
    // 식단마다 1년 기대수익을 옮기는 양 (앱 RateScenarioView 와 같은 식: 3년 동안의 b 합 × Δ금리 ÷ 3)
    const shifts = M.coef.map((c) => kb * (c.reduce((s2, x) => s2 + x[1], 0) * dr / 3 / 100 + m));
    const mid = band(shifts)[1], eps = H > 0 ? Math.exp(cb[H - 1][1] / 100) - 1 : 0;
    const when = RS.pick === 0 ? "금리가 내리면" : RS.pick === 1 ? "금리가 그대로면" : RS.pick === 2 ? "금리가 작년만큼 또 오르면" : RS.pick === 3 ? "금리가 2022년처럼 급등하면" : `금리가 ${sgn(dr, "%p", 1)} 움직이면`;
    // 기준선: 목표일 전망 결과. 닫힌 식 입력은 전망 만기 분포에서 거꾸로 구한다 (변동성 = 중앙과 하위 5%의 거리)
    const R = lastForecast && lastForecast.withEv, g = S.state.goal, t0 = today(), cash = cashKrw();
    let res = `<p class="muted small" style="margin:12px 0">목표일 전망을 한 번 계산하면 내 목표 가능성 변화가 여기에 나와요.</p>`;
    if (R && R.terminal && R.terminal.p50 > 0 && R.terminal.p5 > 0 && g && g.date > t0) {
      const T = Math.max(1 / 12, yearsBetween(t0, g.date)), V0 = total + cash, sgT = Math.max(0.01, Math.log(R.terminal.p50 / R.terminal.p5) / 1.645), sg = Math.max(0.01, sgT / Math.sqrt(T));
      const ci = { T, sg, mu: Math.log((R.terminal.p50 + cash) / Math.max(1, V0)) / T + sg * sg / 2, V0, K: g.amount, M: Number(g.monthly_contribution) || 0 };
      const prob = (s2) => Math.max(0, Math.min(1, R.p_goal + rsClosed(ci, s2) - rsClosed(ci, 0)));
      const base = prob(0), pb = band(shifts.map(prob)), delta = Math.round((pb[1] - base) * 100);
      const m0 = rsMonths(ci, 0), m1 = rsMonths(ci, mid), endAmt = R.terminal.p50 + cash;
      const timing = m0 == null || m1 == null ? (m1 == null && m0 != null ? "50년 안에 닿기 어려워요" : "계산할 수 없어요") : m0 === 0 ? "이미 닿았어요" : m1 === m0 ? "거의 같아요"
        : (() => { const n = m1 - m0, yy = Math.floor(Math.abs(n) / 12), mo = Math.abs(n) % 12; return `약 ${yy ? yy + "년" : ""}${yy && mo ? " " : ""}${mo ? mo + "개월" : ""} ${n > 0 ? "늦어져요" : "빨라져요"}`; })();
      const col = delta < 0 ? "var(--accent2)" : delta > 0 ? "var(--good)" : "var(--muted)";
      const bar = (k, p, c) => `<div class="row" style="gap:8px;align-items:center"><span class="small muted" style="width:44px">${k}</span><span style="flex:1;height:8px;border-radius:4px;background:var(--line);position:relative"><span style="position:absolute;left:0;top:0;bottom:0;border-radius:4px;width:${(p * 100).toFixed(1)}%;background:${c}"></span></span></div>`;
      res = `<div style="margin:12px 0;padding:14px;border-radius:14px;border:1px solid var(--line)">
        <p class="small muted" style="margin:0">${when}</p>
        <p style="margin:4px 0 8px">목표 가능성 <span class="muted">${pct(base, 0)}</span> → <b style="font-size:1.6rem">${pct(pb[1], 0)}</b>
          <span class="small" style="margin-left:6px;padding:2px 8px;border-radius:10px;background:${col};color:#fff">${delta === 0 ? "변화 없음" : (delta > 0 ? "+" : "−") + Math.abs(delta) + "%p"}</span></p>
        ${bar("지금", base, "var(--muted)")}${bar("이 경우", pb[1], col)}
        <p class="small row between" style="margin:10px 0 2px"><span class="muted">${krw(g.amount)}원에 닿는 시점</span><b>${timing}</b></p>
        <p class="small row between" style="margin:2px 0"><span class="muted">${g.date.slice(0, 4)}년 ${Number(g.date.slice(5, 7))}월 그럴듯한 금액</span><b>${krw(endAmt)}원 → ${krw(endAmt * Math.exp(mid * T))}원</b></p></div>`;
    }
    const step = (n, t, v, note) => `<div class="row" style="gap:10px;align-items:flex-start;margin:8px 0"><span style="flex:none;width:22px;height:22px;border-radius:11px;background:var(--accent);color:#fff;text-align:center;font-size:.8rem;line-height:22px">${n}</span><span><span class="small muted">${t}</span><br><b>${v}</b><br><span class="small muted">${note}</span></span></div>`;
    const r0 = d.now && Number.isFinite(d.now.rate) ? d.now.rate : null;
    const chain = `<h3 style="margin:14px 0 2px">어떻게 내 자산까지 오나요?</h3><p class="small muted" style="margin:0">금리는 세 단계를 거쳐 내 목표에 닿아요.</p>` +
      step(1, "금리", r0 != null ? `${r0.toFixed(2)}% → ${(r0 + dr).toFixed(2)}%` : sgn(dr, "%p", 1), "미국 10년물 국채 금리 기준") +
      step(2, "기업이 버는 돈", Math.abs(eps) < 0.005 ? `${H}년 동안 이익이 거의 그대로예요` : `${H}년 동안 이익이 약 ${Math.round(Math.abs(eps) * 100)}% ${eps < 0 ? "줄어요" : "늘어요"}`, "빌리는 돈이 비싸지면 이익이 줄어드는 경향이 있어요") +
      step(3, "내 자산", Math.abs(mid) < 0.0005 ? "1년 기대수익이 거의 그대로예요" : `1년 기대수익이 약 ${(Math.abs(mid) * 100).toFixed(1)}%p ${mid < 0 ? "낮아져요" : "높아져요"}`, "그래서 목표 가능성이 위 숫자만큼 달라져요");
    // 근거 (2단계): 그래프 + 해마다 변화·누적 수준·줄어 있을 확률
    const dropCumYr = Array.from({ length: H }, (_, y) => cum.filter((t) => t[y] < 0).length / cum.length);
    const sg1 = (v) => `${v >= 0 ? "+" : "−"}${nf(Math.abs(v), 1)}%`;
    const why = `<details style="margin:6px 0"><summary class="small"><b>2단계 근거 자세히 보기</b></summary>
      <div id="rsChart" class="chartbox"></div><p class="muted small" style="margin:2px 0 8px">S&amp;P500 실질 이익 (오늘 = 100). 진한 선은 보통의 경우, 띠는 10번 중 8번이 드는 범위</p>
      <div class="tablewrap"><table class="grid"><tr><th class="l">해</th><th>그 해 변화</th><th>오늘 대비 수준</th><th>오늘보다 줄어 있을 확률</th></tr>` +
      yr.map((b, y) => `<tr><td class="l">${y + 1}년째</td><td>${sg1(b[1])}</td><td><b>${nf(100 * Math.exp(cb[y][1] / 100), 0)}</b></td><td>${pct(dropCumYr[y], 0)}</td></tr>`).join("") +
      `</table></div><p class="muted small">시장 1년 기대수익 (보통의 경우) ${spct(mkt[1], 1)}</p></details>`;
    // 내 종목 중 금리에 약한 곳: 금리 민감도(시장 제외)가 낮은 순. 비율·민감도로만 (종목 조언 아님)
    const st = factorBetas().stat.rate || {}, list = held.filter((r) => st[r.h.ticker]).map((r) => ({ r, c: st[r.h.ticker].beta })).sort((a, b) => a.c - b.c);
    let weak = "";
    if (list.length) {
      const lo = list[0].c, hi = list[list.length - 1].c, span = Math.max(1e-4, hi - lo);
      weak = `<h3 style="margin:14px 0 2px">내 종목 중 금리에 약한 곳</h3><p class="small muted" style="margin:0 0 6px">금리가 오를 때 더 크게 흔들리는 순서예요.</p>` + list.slice(0, 6).map((e) => {
        const t = (e.c - lo) / span, tier = list.length < 2 ? 1 : t < 1 / 3 ? 0 : t > 2 / 3 ? 2 : 1, c = ["var(--accent2)", "var(--muted)", "var(--good)"][tier];
        return `<div style="margin:6px 0"><p class="small row between" style="margin:0"><b>${esc(e.r.h.ticker)}</b><b style="color:${c}">${["크게 흔들려요", "보통이에요", "덜 흔들려요"][tier]}</b></p>
          <span style="display:block;height:8px;border-radius:4px;background:var(--line);position:relative"><span style="position:absolute;left:0;top:0;bottom:0;border-radius:4px;width:${(15 + 85 * (1 - t)).toFixed(1)}%;background:${c}"></span></span>
          <span class="small muted">비중 ${pct(e.r.w, 0)}</span></div>`; }).join("");
    }
    const ev = rsEvents(60).slice(0, 4);
    const evs = `<h3 style="margin:14px 0 2px">다가오는 금리 일정</h3>` + (ev.length ? ev.map((e) => { const n = Math.round(yearsBetween(t0, e.d) * 365.25);
      return `<p class="small" style="margin:4px 0"><b style="color:var(--accent)">${n === 0 ? "오늘" : n === 1 ? "내일" : `${Number(e.d.slice(5, 7))}/${Number(e.d.slice(8))} (D-${n})`}</b> ${esc(e.kind)}</p>`; }).join("") : `<p class="small muted">60일 안에 정해진 금리 일정이 없어요.</p>`) +
      `<p class="muted small">날짜는 정해진 주기로 계산해 실제 일정과 하루이틀 다를 수 있어요.</p>`;
    $("#rsOut").innerHTML = res + chain + why + weak + evs;
    const xs = [t0, ...Array.from({ length: H }, (_, y) => String(Number(t0.slice(0, 4)) + y + 1) + t0.slice(4))];
    const lvl = (k) => [100, ...cb.map((b) => 100 * Math.exp(b[k] / 100))];
    Charts.lineChart($("#rsChart"), { x: xs, height: 190, yfmt: (v) => nf(v, 0), bands: [{ lo: lvl(0), hi: lvl(2), color: "var(--c2)", opacity: 0.12 }], series: [
      { name: "10번 중 9번 이 아래", y: lvl(2), color: "var(--c2)", width: 1, dash: "4 3" }, { name: "보통의 경우", y: lvl(1), color: "var(--c1)", width: 2 }, { name: "10번 중 1번 이 아래", y: lvl(0), color: "var(--c2)", width: 1, dash: "4 3" }] });
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
    // 입력·시세·날짜가 같으면 저장해 둔 결과를 바로 쓴다
    try { const c = JSON.parse(localStorage.getItem(XF_KEY) || "null"); if (c && c.sig === xfSig()) { xfEff = { ...c, fc: lastForecast }; return drawXfEffect(); } } catch (e) { /* 다시 계산 */ }
    if (xfBusy) return; xfBusy = true;
    const { b } = lastForecast, common = { ...simCommon(b, S.state.model.scenario) }; common.nPaths = Math.min(common.nPaths, 1500);
    const has = (cats) => b.model.eventList.some((x) => cats.includes(x.event.cat || "corp"));
    host.innerHTML = "<p class='muted small'>요인별 영향을 계산하는 중입니다…</p>";
    const res = { fc: lastForecast, none: null, all: null, grp: {}, by: {} };
    // 묶음별 계산은 worker 에서 한꺼번에 (화면은 그대로 움직인다). 결과는 목표 확률·분포만
    const run = (cats) => simAsync(b.model, { ...common, withEvents: true }, cats, true);
    const jobs = [["none", null, run([])], ["all", null, run(null)]];
    for (const g of XGROUPS) {
      const gc = grpCats(g.k); if (!has(gc)) continue;
      const gp = run(gc); jobs.push(["grp", g.k, gp]);
      for (const [k, , cats] of g.subs) if (has(cats)) jobs.push(["by", k, g.subs.length === 1 ? gp : run(cats)]);
    }
    try { for (const [t, k, pr] of jobs) { const R = await pr; if (k == null) res[t] = R; else res[t][k] = R; } }
    catch (e) { xfBusy = false; host.innerHTML = `<p class='muted small'>계산 오류: ${esc(e.message)}</p>`; return; }
    if (lastForecast !== res.fc) { xfBusy = false; return; }
    xfEff = res; xfBusy = false; drawXfEffect();
    const slim = (R) => R && { p_goal: R.p_goal, terminal: R.terminal }, om = (o) => Object.fromEntries(Object.entries(o).map(([k, R]) => [k, slim(R)]));
    try { localStorage.setItem(XF_KEY, JSON.stringify({ sig: xfSig(), none: slim(res.none), all: slim(res.all), grp: om(res.grp), by: om(res.by) })); } catch (e) { /* 무시 */ }
  }
  const XF_KEY = "naeilo-xfeff", xfSig = () => fcdSig() + "|" + S.state.model.n_paths;
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
    // 전망 기준값 = 사이트 마지막 종가 × 매매기준율 (앱과 같은 값. 계산한 시각의 실시간 시세에 따라 웹·앱 숫자가 갈리지 않게)
    const holdings = rows.filter((r) => r.valueKrw > 0).map((r) => { const p0 = snapPx(r); return { ticker: r.h.ticker, shares: r.sh, price0: p0, ccy: r.ccy, valueKrw: r.sh * p0 * r.fx }; });
    if (!holdings.length) return null;
    const series = {};
    { const P = mcPrices(); for (const k in P) series[k] = { dates: P[k].dates, adj: P[k].adj }; }
    const model = Model.buildModel({ holdings, series, fxOf, settings: S.state.model, events: S.state.events, betas: factorBetas().beta, startDate: start, goalDate: g.date });
    return { holdings, model };
  }
  const SCEN_LAB = { blend: "내 관점 (현재 정세와 과거 추세 사이)", base: "기준 (과거+사전값 절충)", conservative: "보수 (위험 프리미엄 없음)", history: "과거 반복 (지난 수익률 그대로)", smooth: "스무딩 추종 (3년 추세선)", trend: "추세 추종 (칼만·EMA)" };
  const SCEN_SHORT = { blend: "내 관점", base: "기준", conservative: "보수", history: "과거 반복", smooth: "스무딩 추종", trend: "추세 추종" };
  function dtStr(t) { const d = new Date(t), z = (n) => String(n).padStart(2, "0"); return `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()} ${z(d.getHours())}:${z(d.getMinutes())}`; }
  // 전망 결과를 이 브라우저에 저장해 두고 리로드 때 다시 쓴다. 종목 구성이 바뀌었을 때만 새로 계산
  const FC_KEY = "naeilo-forecast";
  // 전망 기준 가격: 직접 넣은 현재가 > 사이트 마지막 종가 > 지금 시세
  const snapPx = (r) => (r.p.src === "manual" || !(S.siteClose?.[r.h.ticker] > 0) ? r.p.v : S.siteClose[r.h.ticker]);
  const fcSig = () => hashStr(JSON.stringify([S.state.holdings.map((h) => [h.ticker, h.shares, S.siteClose?.[h.ticker] ?? (isKR(h.ticker) ? [h.price, h.price_date] : null)]), S.state.goal, S.state.events.map((e) => [e.id, e.on, e.date, e.prob, e.mean, e.sd, e.repeat]), S.state.model, cashKrw(), krNow(), fxBase("USD")]));
  function fcStore() {
    const L = lastForecast; if (!L) return;
    const strip = (k, v) => (k === "raw" || k === "term" ? undefined : v);
    try { localStorage.setItem(FC_KEY, JSON.stringify({ sig: fcSig(), tks: L.b.holdings.map((h) => h.ticker).join(), scen: L.scen, at: L.at.getTime(), ms: L.ms, hasEv: L.hasEv, withEv: L.withEv, noEv: L.hasEv ? L.noEv : null, lens: L.lens, bep: L.bep }, strip)); }
    catch (e) { try { localStorage.removeItem(FC_KEY); } catch (e2) { /* 무시 */ } }
  }
  function fcRestore() {
    try {
      const c = JSON.parse(localStorage.getItem(FC_KEY) || "null"); if (!c || !c.withEv) return false;
      // 다른 날 계산한 전망은 쓰지 않는다 (오늘 평가액·오늘부터의 날짜로 다시. 앱도 하루 한 번 새로 계산)
      const at = new Date(c.at); if (new Date(at.getTime() - at.getTimezoneOffset() * 60000).toISOString().slice(0, 10) !== today()) return false;
      const b = buildModelNow(); if (!b || b.holdings.map((h) => h.ticker).join() !== c.tks) return false;
      const common = simCommon(b, c.scen);
      lastForecast = { b, sim: null, simNo: null, withEv: c.withEv, noEv: c.noEv || c.withEv, lens: c.lens || {}, bep: c.bep || null, hasEv: c.hasEv, common, grp: {}, scen: c.scen, at: new Date(c.at), ms: c.ms };
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
  // ------------------------------------------------------------ 본전 카드 (관찰 숫자만: 매수 단가를 넣은 종목의 본전 거리·기한별 본전 확률)
  // 매수 단가를 넣은 종목만 묶어 원금(단가×수량×지금 환율)과 지금 평가액을 비교한다
  function bepRows() {
    const by = {};
    valuation().rows.forEach((r) => { if (!(r.avg > 0) || !(r.valueKrw > 0) || !r.fx) return; const t = r.h.ticker, o = (by[t] = by[t] || { t, basis: 0, cur: 0 }); o.basis += r.avg * r.sh * r.fx; o.cur += r.valueKrw; });
    const rows = Object.values(by), basis = rows.reduce((a, x) => a + x.basis, 0), cur = rows.reduce((a, x) => a + x.cur, 0);
    return rows.length ? { rows, basis, cur, n: rows.length, all: rows.length === valuation().rows.filter((r) => r.valueKrw > 0).length } : null;
  }
  // ------------------------------------------------------------ 홈 첫 질문: 회복 길(본전까지) / 목표 길 (앱 첫 질문과 같은 길. 앱의 '시작 전'은 웹에선 목표 길)
  // 고른 길에 맞는 카드만 홈에 둔다. 고르기 전에는 두 카드 모두. 샘플은 목표 길
  const routeOf = () => (S.state.sample ? "goal" : S.state.route === "recover" || S.state.route === "goal" ? S.state.route : null);
  function renderRoute() {
    const card = $("#routeCard"), r = routeOf(), B = S.state.sample ? null : bepRows();
    $("#goalKpis").style.display = r === "recover" ? "none" : "";
    if (S.state.sample || !(valuation().total > 0)) { card.style.display = "none"; return; } // 샘플·빈 화면은 세리 카드가 먼저
    card.style.display = "block";
    const gap = B ? B.cur / B.basis - 1 : null;
    if (!r) {
      card.className = "card routeq";
      card.innerHTML = `<h2>지금 내 투자, 플러스인가요 마이너스인가요?</h2>
        <div class="routeopts"><button type="button" data-route="recover"><b>마이너스예요</b><span>본전까지 가는 길을 같이 봐요</span></button><button type="button" data-route="goal"><b>플러스예요</b><span>목표 금액까지 가는 길을 그려요</span></button></div>
        <p class="small muted" style="margin:8px 0 0">${gap == null ? "잘 모르겠다면 종목 탭에서 매수 단가를 넣어 보세요. 어느 쪽인지 알려 드려요." : `넣은 매수 단가로 보면 지금 <b class="${cls(gap)}">${gap < 0 ? "마이너스" : "플러스"}</b>예요 (${spct(gap, 1)}).`} 고른 길에 맞는 카드만 홈에 보여 드려요.</p>`;
    } else {
      card.className = "routebar";
      card.innerHTML = `<span class="small muted">내 길 · <b>${r === "recover" ? "본전까지 (회복)" : "목표까지"}</b></span><button type="button" class="sm" data-route="">길 다시 고르기</button>`;
    }
    card.querySelectorAll("[data-route]").forEach((b) => b.addEventListener("click", () => {
      if (b.dataset.route) S.state.route = b.dataset.route; else delete S.state.route;
      save(false); renderDash();
    }));
  }
  const bepSig = () => fcSig() + "|" + hashStr(JSON.stringify(S.state.holdings.map((h) => [h.ticker, h.shares, h.avg_cost])));
  // 전망 경로(내 관점)에서 그 종목들만 들고 있을 때 월말 평가액이 원금 이상이 되는 첫 달 → 6개월·1년·3년 안 확률
  function calcBep(L) {
    const B = bepRows(); if (!B || !L || !L.sim || !L.sim.raw) return null;
    const idx = B.rows.map((x) => L.b.holdings.findIndex((h) => h.ticker === x.t)); if (idx.some((i) => i < 0)) return null;
    const pr = Model.breakeven(L.sim, { idx, basis: B.basis * (L.sim.raw.v0 ? idx.reduce((a, i) => a + L.sim.raw.v0[i], 0) / B.cur : 1), mu: viewMu(L.b.model, viewKey()), months: [6, 12, 36] });
    return pr && { sig: bepSig(), pr, view: viewKey() };
  }
  let bepAsked = "";
  function renderBep() {
    const card = $("#bepCard"), B = S.state.sample ? null : bepRows(), r = routeOf();
    if (r === "goal") { card.style.display = "none"; return; } // 목표 길에서는 목표 카드만
    if (!B) {
      // 회복 길을 골랐는데 매수 단가가 없으면 넣는 곳으로 안내
      card.style.display = r === "recover" && valuation().total > 0 ? "block" : "none";
      card.innerHTML = `<h2 style="margin:0">본전까지</h2><p class="small">매수 단가를 넣으면 본전까지 거리와 6개월·1년·3년 안에 본전에 닿을 확률을 보여 드려요.</p><button class="primary" data-go="stocks">매수 단가 넣으러 가기</button>`;
      return;
    }
    card.style.display = "block";
    const gap = B.cur / B.basis - 1, need = B.basis / B.cur - 1, sub = B.all ? "매수 단가를 넣은 모든 종목 기준" : `매수 단가를 넣은 ${B.n}종목 기준 (${B.rows.map((x) => esc(x.t)).join(", ")})`;
    if (!lastForecast) fcRestore();
    const L = lastForecast, sig = bepSig();
    let bp = L && L.bep && L.bep.sig === sig ? L.bep : null;
    if (!bp && L && L.sim && L.sim.raw) { L.bep = calcBep(L); bp = L.bep; }
    let probs;
    if (gap >= 0) probs = `<p class="small" style="margin:6px 0 0">지금 원금보다 <b class="${cls(gap)}">${spct(gap, 1)}</b> 위에 있어요.</p>`;
    else if (S.state.model.rebalance_yearly) probs = `<p class="muted small">설정의 '연 1회 재조정'을 끄면 본전 확률을 계산해요.</p>`;
    else if (!bp) {
      probs = `<p class="muted small">본전 확률 계산 중…</p>`;
      if (bepAsked !== sig) { bepAsked = sig; fcDirty = true; setTimeout(runForecast, 0); } // 전망 탭을 열지 않아도 한 번 계산 (worker)
    } else probs = `<div class="bepp">${bp.pr.map((x) => `<div class="probbar"><span>${x.m < 12 ? x.m + "개월" : x.m / 12 + "년"} 안</span><div class="b"><i style="width:${(x.p || 0) * 100}%"></i></div><span>${x.p == null ? "-" : pct(x.p, 0)}</span></div>`).join("")}</div>` +
        (bp.pr.some((x) => x.p == null) ? `<p class="muted small">목표일이 그보다 가까워 '-'로 둡니다.</p>` : "");
    card.innerHTML = `<div class="row between wrap"><div class="row wrap" style="gap:4px 8px"><h2 style="margin:0">본전까지</h2><span class="muted small">${sub}</span></div></div>
      <div class="bepv"><b class="${cls(gap)}">${spct(gap, 1)}</b>${gap < 0 ? `<span class="small muted">원금 대비 · 본전이 되려면 <b>${spct(need, 1)}</b> 올라야 해요</span>` : ""}</div>` + probs +
      `<p class="muted small">관찰용 숫자예요. 지금 구성 그대로(적립·매매 없이) 들고 있을 때 월말 평가액이 매수 원금에 닿을 확률로, 분석 › 목표일 전망의 내 관점과 같은 계산이에요. 매수 때 환율은 모르니 지금 환율로 원금을 셉니다.</p>`;
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
      const gK = Math.max(1, g.amount - cashKrw()); // 현금은 그대로 있다고 보고 목표에서 뺀다 (simCommon·앱과 같다)
      const rw = (sim, key, wi) => Model.reweight(sim, { mu: viewMu(md, key), goal: gK, monthly: wi ? wi.monthly : plain, move: wi ? wi.move : null, lossRef });
      const k = viewKey(), wi = wiOpt();
      L.withEv = rw(L.sim, k); L.noEv = L.hasEv ? rw(L.simNo, k) : L.withEv;
      if (full || !L.lens || !L.lens.base) L.lens = { base: rw(L.sim, "base"), smooth: rw(L.sim, "smooth") };
      L.disp = wi ? { R: rw(L.sim, k, wi), No: L.hasEv ? rw(L.simNo, k, wi) : null, base: rw(L.sim, "base", wi), smooth: rw(L.sim, "smooth", wi) } : null;
    } else {
      L.withEv = L.sim; L.noEv = L.simNo; L.disp = null;
      if (full) { const one = (scen) => (L.scen === scen ? L.sim : Model.simulate(md, { ...L.common, scenario: scen, nPaths: Math.min(L.common.nPaths, 1500), withEvents: true })); L.lens = { base: one("base"), smooth: one("smooth") }; }
    }
    L.scen = S.state.model.scenario;
    L.bep = calcBep(L) || L.bep;
    if (RS.draw && $("#rsOut")) RS.draw(); // 금리 시나리오의 내 목표 확률도 새 전망으로
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
      ? `<div class="lv">${pct(R.p_goal, 0)} <small>목표 확률</small></div><p>목표일 중앙값 ${krw(R.terminal.p50)}원</p><p>외부 요인이 없으면 ${pct(No.p_goal, 0)} · ${krw(No.terminal.p50)}원</p>`
      : `<p>켜진 외부 요인 없음.</p>`) + "</div>";
    $("#lenses").innerHTML = lens("base", "현재 정세", `과거 수익률을 장기 평균(연 ${m.prior_mu}%) 쪽으로`, portMu("base"), D.base) + lens("smooth", "과거 추세", "지난 3년 성장 속도가 이어지면", portMu("smooth"), D.smooth) + shock;
    const on = m.scenario === "blend";
    $("#trust").value = String(m.trust); $("#trustVal").textContent = on ? `${m.trust}% · 연 기대 ${spct(portMu(viewKey()), 0)}` : "-";
    $(".trust").classList.toggle("off", !on);
    $("#trustNote").textContent = on ? "" : `'${SCEN_SHORT[m.scenario] || m.scenario}' 시나리오 사용 중. 움직이면 내 관점으로.`;
    // 만약에
    const ok = !!(L.sim && L.sim.raw) || !L.sim;
    const { top, cash } = wiTop(), th = L.b.holdings[top], plain = Number(S.state.goal.monthly_contribution) || 0;
    const mv = WI.monthly == null ? plain : WI.monthly;
    $("#wiMonthly").value = String(Math.round(mv / 1e4)); $("#wiMonthlyV").textContent = mv ? krw(mv) + "원" : "0원";
    $("#wiSellLab").textContent = `가장 큰 종목 비중 줄이기 (${cash >= 0 ? "현금성 종목" : "현금(연 3.5%)"}으로)`;
    $("#wiSell").value = String(WI.sell); const tw = th ? th.valueKrw / Math.max(1, L.b.holdings.reduce((a, h) => a + h.valueKrw, 0)) : 0;
    $("#wiSellV").textContent = WI.sell && th ? `비중 ${pct(tw, 0)}→${pct(tw * (1 - WI.sell / 100), 0)}` : "0%";
    $$("#wiDet input").forEach((x) => (x.disabled = !ok || m.rebalance_yearly));
    $("#wiNote").textContent = m.rebalance_yearly ? "연 1회 재조정 중엔 꺼집니다." : "저장 안 되는 가정. 세금·수수료 제외.";
    $("#wiSum").textContent = L.disp ? `· 적용 중 (월 ${krw(mv)}원${WI.sell ? `, 가장 큰 종목 비중 ${WI.sell}% 줄임` : ""})` : "";
  }
  let fcSeq = 0;
  async function runForecast() {
    const st = $("#fcStatus"), btns = $$("#fcOpt button[data-s]");
    btns.forEach((x) => (x.disabled = true)); st.textContent = `${SCEN_SHORT[S.state.model.scenario] || ""} 시나리오, 계산 중...`;
    await new Promise((r) => setTimeout(r, 30));
    try {
      const t0 = performance.now(), b = buildModelNow();
      if (!b) { st.textContent = "평가액이 있는 종목이 없습니다."; btns.forEach((x) => (x.disabled = false)); return; }
      const m = S.state.model, common = simCommon(b, m.scenario), my = ++fcSeq, sig0 = fcSig();
      const hasEv = b.model.eventList.length > 0; // 두 번의 시뮬레이션은 worker 에서 (화면이 멈추지 않게)
      const [sim, simNo] = await Promise.all([simAsync(b.model, { ...common, withEvents: true }), hasEv ? simAsync(b.model, { ...common, withEvents: false }) : null]).then(([a, n]) => [a, n || a]);
      if (my !== fcSeq) return; // 기다리는 사이 새 계산이 시작됐으면 그쪽 결과를 쓴다
      if (fcSig() !== sig0) { btns.forEach((x) => (x.disabled = false)); return runForecast(); } // 계산 중 입력이 바뀌었으면 다시
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
      if (dashOn()) renderDash();
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
  // 원화 평가액 전망 그래프는 과거 3년 + 오늘부터 목표일까지 (목표일이 3년보다 멀어도 확률과 같은 날짜까지 그린다)
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
      const H = history(), k0 = Math.max(0, H.dates.length - 781), V0 = R.V0, yrs = yearsBetween(md.startDate, g.date);
      const GP = goalPath(H, cashKrw()), gpOk = GP && GP.at(md.startDate) != null; // 내 길: 자산 추이·목표 진행과 같은 길 (목표 시작일부터). 전망 값에는 현금이 빠져 있어 현금만큼 뺀다
      const reqPath = fx.map((d) => gpOk ? GP.at(d) - cashKrw() : V0 * (g.amount / V0) ** (yearsBetween(md.startDate, d) / yrs));
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
          { name: "계획선", y: reqPath, color: "var(--goal)", dash: "5 4", width: 1.8 }],
        hlines: [{ y: g.amount, label: "목표 " + krw(g.amount) }],
        vlines: [{ x: md.startDate, label: "오늘" }],
        markers: marks ? evs.map((e) => ({ x: e.date, label: `${e.date} ${e.event.target} ${e.event.kind}` })) : [],
      });
      $("#fcViewNote").innerHTML = !hasEv ? `<span class="muted">켜진 외부 요인이 없어 미반영 전망과 같습니다. '외부 요인'에서 켜 주세요.</span>`
        : `<b>${FCV[v]}</b>: 목표 달성 확률 <b>${pct(R.p_goal, 0)}</b>, 목표일 중앙값 <b>${krw(R.terminal.p50)}원</b>, 나쁜 5% ${krw(R.terminal.p5)}원` +
          (v === "none" ? "" : ` <span class="muted">(미반영 ${pct(noEv.p_goal, 0)} · ${krw(noEv.terminal.p50)}원${v === "all" ? "" : `, 반영 사건 ${evs.length}건`})</span>`);
      $("#fcYearsTitle").textContent = `목표에 한 번이라도 닿을 확률 (${vName})`;
      $("#fcYears").innerHTML = R.byYear.map((y) => `<div class="probbar" title="${y.date}까지"><span>${y.year}년 안</span><div class="b"><i style="width:${y.p * 100}%"></i></div><span>${pct(y.p, 0)}</span></div>`).join("") || "기간이 1년 미만입니다.";
      $("#fcEvTitle").textContent = "외부 요인 반영 효과 (전체)";
      $("#fcEvents").innerHTML = !hasEv ? `<p class="muted">켜진 사건이 없습니다. '외부 요인'에서 켜 주세요.</p>`
        : evTable([["목표 달성 확률", pct(noEv.p_goal, 0), pct(Rc.p_goal, 0)], ["목표일 중앙값", krw(noEv.terminal.p50), krw(Rc.terminal.p50)], ["하위 5%", krw(noEv.terminal.p5), krw(Rc.terminal.p5)],
          ["상위 5%", krw(noEv.terminal.p95), krw(Rc.terminal.p95)], ["목표일에 더 낮을 확률", pct(noEv.p_loss, 0), pct(Rc.p_loss, 0)]],
          `사건만 빼고 다시 계산한 비교(미반영 쪽은 실적 변동=평소 변동성). 반영 사건 ${md.eventList.filter(inC).length}건 (반복 포함).`);
    } else {
      const i = b.holdings.findIndex((h) => h.ticker === tk), h = b.holdings[i], s1 = R.stocks[i], s0 = noEv.stocks[i], sc = Rc.stocks[i], c = C[i % C.length];
      $("#fcTitle").textContent = `${tk} 가격 전망 (${h.ccy}, 외부 요인 포함)`;
      const p = S.prices[tk], kk = p ? Math.max(0, p.dates.length - 781) : 0;
      const onTk = (e) => e.event.target === tk || e.event.target === "ALL";
      const evs = v === "none" ? [] : md.eventList.filter((e) => inV(e) && onTk(e));
      Charts.lineChart($("#fcChart"), {
        x: fx, height: 340, log: true, yfmt: priceAxis([s1.bands.p5, s1.bands.p95]),
        bands: [{ lo: s1.bands.p5, hi: s1.bands.p95, color: c, opacity: 0.13, name: "5~95%" }, { lo: s1.bands.p25, hi: s1.bands.p75, color: c, opacity: 0.25, name: "25~75%" }],
        series: [...(p ? [{ name: "과거", x: [...p.dates.slice(kk), md.startDate], y: [...p.close.slice(kk), h.price0], color: "var(--fg)", width: 1.4 }] : []),
          { name: lab, y: s1.bands.p50, color: c, width: 2.2 },
          ...(hasEv && v !== "none" ? [{ name: "미반영 중앙값", y: s0.bands.p50, color: "var(--muted)", width: 1.2, dash: "2 3" }] : [])],
        hlines: [{ y: h.price0, label: "현재 " + nf(h.price0, 2), color: "var(--muted)" }],
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
    $$("#fcOpt button[data-s]").forEach((x) => x.classList.toggle("on", x.dataset.s === m.scenario)); if ($("#scenMore button.on")) $("#scenMore").open = true; $("#nPaths").value = String(m.n_paths); $("#rebalance").checked = !!m.rebalance_yearly;
    if (!lastForecast) { $("#fcStatus").textContent = `${SCEN_SHORT[m.scenario] || ""} 시나리오`; return; }
    const { b, withEv: R, noEv, hasEv } = lastForecast, g = S.state.goal, md = b.model;
    // 직접 입력한 현재가 때문에 오늘 평가액이 시세 기준과 크게 다르면 알린다 (차트가 오늘에서 꺾이는 원인)
    const manual = b.holdings.filter((h) => { const src = S.state.holdings.find((x) => x.ticker === h.ticker); const mk = src && curPrice({ ...src, price: null }).v; return src && Number(src.price) > 0 && mk && Math.abs(h.price0 / mk - 1) > 0.05; });
    const Hh = history(), lastHist = Hh.total[Hh.total.length - 1];
    $("#fcWarn").innerHTML = manual.length && lastHist ? `오늘 평가액(${krw(R.V0)}원)이 시세 기준(${krw(lastHist)}원)과 ${spct(R.V0 / lastHist - 1, 0)} 다릅니다. <b>${manual.map((h) => esc(h.ticker)).join(", ")}</b>에 현재가를 직접 넣었기 때문입니다. 매수 단가였다면 종목 탭에서 그 값을 지우고 '매수 단가' 칸으로 옮겨 주세요.` : "";
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
    if (onTab("analysis")) { renderFx(); runXfEffect(); }
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
      ["한 번이라도 닿을 확률", pct(R.p_touch, 0), "목표일 전 언제나"],
      ["목표일 중앙값", krw(R.terminal.p50) + "원", `평균 <b>${krw(R.terminal.mean)}원</b>`],
      ["나쁜 5%", krw(R.terminal.p5) + "원", `하위 25% ${krw(R.terminal.p25)}원`],
      ["좋은 5%", krw(R.terminal.p95) + "원", `상위 25% ${krw(R.terminal.p75)}원`],
      [lossK, pct(R.p_loss, 0), lossS],
      ["최대 낙폭 (중앙값)", pct(R.mdd_median, 0), `나쁜 10%: ${pct(R.mdd_p10, 0)}`],
      ["확률 50% 위한 월 적립", R.req50 == null ? "-" : R.req50 === 0 ? "0원" : krw(R.req50) + "원", R.req50 == null ? "재조정 끄면 계산" : "매월 정액 매수 가정"],
    ].map(([k, v, s2]) => `<div class="kpi"><div class="k">${k}</div><div class="v">${v}</div><div class="s">${s2}</div></div>`).join("");
    // 평균은 오르는데 중앙값이 낮은 이유: 집중·변동성 비용
    const b = L.b, md = b.model, V = b.holdings.reduce((a, h) => a + h.valueKrw, 0);
    const top = b.holdings.map((h, i) => [h, h.valueKrw / V, md.factors[i]]).filter((x) => !x[2].cash).sort((a, c) => c[1] - a[1])[0];
    const gap = R.terminal.mean / R.terminal.p50;
    const ex = gap > 1.2 && top ? `평균이 중앙값보다 높은 건 ${esc(top[0].ticker)} 비중 ${pct(top[1], 0)}, 변동성 연 ${pct(top[2].vol, 0)} 때문. 분산하면 줄어듭니다.` : "";
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
    if (R.p_goal < 0.5 && R.req50 != null) tips.push(`**적립**: 지금 비중 그대로 확률 50%를 맞추려면 매월 약 **${krw(R.req50)}원**을 더 넣어야 합니다 (월 적립은 내 길의 목표 수정에서 입력).`);
    if (risky[top] > 0.45) tips.push(`**집중도**: ${b.holdings[top].ticker} 한 종목이 **${pct(w[top], 0)}**입니다. 하위 5% 결과가 ${krw(R.terminal.p5)}원까지 내려갑니다. '비중 조정'에서 비중을 바꿨을 때의 계산을 볼 수 있습니다.`);
    if (cashW < 0.03) tips.push(`**현금**: 현금성 자산이 ${pct(cashW, 1)}입니다. 하락장 대비 여유분으로 흔히 3~5%를 기준으로 삼습니다.`);
    tips.push(`**낙폭**: 최대 낙폭 중앙값 ${pct(R.mdd_median, 0)}. 목표일까지 가는 동안 이 정도 하락은 흔하다는 뜻입니다.`);
    const rg = rebalanceGap();
    if (rg && rg.gaps.length && yearsBetween(rg.tg.at, today()) < 0.5) tips.push(`**기준 비중과 차이**: '${rg.tg.name}' (${rg.tg.at}에 기준으로 기록). 남은 차이 ${rg.gaps.map(([t, d]) => `${t} ${d > 0 ? "+" : ""}${(d * 100).toFixed(0)}%p`).join(", ")}.`);
    else if (rg) tips.push(rg.gaps.length ? `**기준 비중과 차이**: 기준으로 기록한 '${rg.tg.name}' 비중에서 ${rg.gaps.map(([t, d]) => `${t} ${d > 0 ? "+" : ""}${(d * 100).toFixed(0)}%p`).join(", ")} 벗어났습니다. '비중 조정'에서 비중별 확률 차이를 볼 수 있습니다.` : `**기준 비중**: 기록한 '${rg.tg.name}' 비중 안에 있습니다 (±5%p).`);
    if (common.length) tips.push(`**공통 일정** (모든 종목): ${common.slice(0, 4).map((c) => `${c.k} ${c.d.join(", ")}`).join(" · ")}`);
    $("#stratSummary").innerHTML = `<div class="md small">${md2html(tips.map((t) => "- " + t).join("\n"))}</div>`;

    const total = V0;
    $("#stratCards").innerHTML = b.holdings.map((h, i) => {
      const f = md.factors[i], p = S.prices[h.ticker], ind = p ? Model.indicators(p.dates, p.adj) : null, sg = ind?.sig, st = R.stocks[i];
      const ev = soon(h.ticker), lock = ev.find((e) => /보호예수/.test(e.event.kind));
      let act, klass, why = [];
      if (f.cash) { act = "현금성 (완충)"; klass = "cash"; why.push(`**성격**: 비중 ${pct(w[i], 1)}, 연 ${pct(f.mu.base)} 수준의 단기 국채형`); }
      else if (f.n < 252) { act = "이력 짧음 · 추정 불확실"; klass = "wait"; why.push(`**이력**: 상장 후 ${f.n}거래일로 짧아 변동성(${pct(f.vol, 0)}) 추정이 불확실`); if (lock) why.push(`**${lock.event.kind}**: ${lock.date} 예정. 과거에는 물량 출회로 단기 하락이 잦았음`); }
      else if (w[i] > 0.45) {
        const tgt = 0.45;
        act = `한 종목 집중 (비중 ${pct(w[i], 0)})`; klass = "trim";
        why.push(`**비중**: ${pct(w[i], 0)}로 한 종목 집중. 비중 ${pct(w[i], 0)}→${pct(tgt, 0)}일 때 확률이 어떻게 달라지는지는 '비중 조정'에서 비교 (계산값)`);
        why.push("**세금**: 해외주식 양도차익은 연 250만원까지 공제 (연도별 계산은 '배당·세금')");
      } else if (sg && sg.trend === "하락 추세") { act = "하락 추세"; klass = "wait"; }
      else if (sg && /상승/.test(sg.trend) && w[i] > 0.25) { act = `상승 추세 · 비중 ${pct(w[i], 0)}`; klass = "hold"; }
      else { act = sg ? sg.trend : "신호 없음"; klass = "hold"; }
      if (sg && !f.cash) why.push(`**상태**: 고점 대비 ${pct(sg.drawdown, 0)}` + (sg.ema200 ? `, 200일선 ${sg.close >= sg.ema200 ? "위" : "아래"} (${spct(sg.close / sg.ema200 - 1, 0)})` : ", 200일선 판단 보류 (이력 짧음)") + `, 비중 ${pct(w[i], 0)}${risky[i] > 0.45 ? " (집중)" : ""}`);
      if (!f.cash) why.push(`**전망**: 목표일 가격 중앙값 ${nf(st.bands.p50[st.bands.p50.length - 1], 2)} ${h.ccy} (현재 ${nf(h.price0, 2)}), 오를 확률 **${pct(st.p_up, 0)}**, 적용 기대수익 연 ${pct(f.mu[S.state.model.scenario])}`);
      ev.filter((e) => e !== lock).slice(0, 2).forEach((e) => why.push(`**${e.event.kind}**: ${e.date} (±${e.event.sd}%)`));
      return `<div class="strat"><h3>${esc(h.ticker)} <span class="muted small">비중 ${pct(w[i], 1)} · ${krw(h.valueKrw)}원</span></h3><div class="act ${klass}">${act}</div><div class="md">${md2html(why.map((x) => "- " + x).join("\n"))}</div></div>`;
    }).join("");
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
  // 마크다운 → HTML (전략 요약과 블로그 글에 공통). 소제목, 굵게·기울임·코드·링크, 중첩 목록, 표
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

  // 지수·현금성 ETF·요인 대리 지표 (실적 사건을 자동으로 넣지 않는 종목)
  const INS_SKIP = new Set(["QQQ", "SPY", "SGOV", "BIL", "SHV", "TLT", "DBC", "^TNX", "CL=F", "GC=F"]);

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
  // 비중 조정안은 탭을 열기 전에 뒤에서 미리 계산해 둔다: 처음 접속(샘플 포함)과 보유 종목·시나리오·데이터가 바뀔 때.
  // 같은 입력이면 저장해 둔 결과(allocRestore)를 쓰고 다시 계산하지 않는다
  var allocTimer = null, allocRun = null; // var: markDirty 가 먼저 불려도 되게
  function allocWarm(delay = 1500) {
    clearTimeout(allocTimer);
    allocTimer = setTimeout(() => {
      if (allocRun || !S.state || !(valuation().total > 0)) return;
      if (lastAlloc && !allocDirty) return;
      if (allocRestore()) { if (anaOn("road")) { renderAllocTable(); renderAllocChart(); } return; }
      const go = () => runAlloc();
      if (window.requestIdleCallback) requestIdleCallback(go, { timeout: 3000 }); else go();
    }, delay);
  }
  function runAlloc() { // 이미 계산 중이면 그 계산을 같이 기다린다 (두 번 돌리지 않게)
    return (allocRun ||= runAllocNow().finally(() => { allocRun = null; }));
  }
  // 계산은 worker 에서 (화면이 멈추지 않게). worker 를 못 쓰는 환경이면 같은 코드를 화면에서 돌린다
  // 전망 시뮬레이션(sim)은 비중 조정안과 다른 worker 들(코어 수만큼, 최대 3개)에 나눠 돌려, 긴 비중 계산 뒤에 줄 서지 않고 동시에 끝나게 한다
  const WK = {}, SIM_N = Math.min(3, Math.max(1, (navigator.hardwareConcurrency || 2) - 1)); let allocSeq = 0, simRr = 0;
  function allocCompute(inp, prog, kind = "alloc") {
    const local = () => (kind === "sim" ? simLocal(inp) : kind === "stab" ? Research.stabilizer(inp, prog) : Model.allocPlans(inp, prog));
    const slot = kind === "sim" ? "sim" + (simRr++ % SIM_N) : "alloc";
    if (WK[slot] === undefined) { try { WK[slot] = new Worker("alloc-worker.js?v=" + (document.querySelector('script[src^="model.js"]')?.src.split("?v=")[1] || "")); } catch (e) { WK[slot] = false; } }
    const wk = WK[slot];
    if (!wk) return new Promise((res, rej) => setTimeout(() => { try { res(local()); } catch (e) { rej(e); } }, 30));
    const id = ++allocSeq;
    return new Promise((res, rej) => {
      const off = () => { wk.removeEventListener("message", on); wk.removeEventListener("error", bad); };
      const on = (e) => { const d = e.data; if (d.id !== id) return; if (d.prog) return prog && prog(...d.prog); off(); d.err ? rej(new Error(d.err)) : res(d.done); };
      const bad = (e) => { off(); wk.terminate(); WK[slot] = false; e.preventDefault?.(); try { res(local()); } catch (e2) { rej(e2); } };
      wk.addEventListener("message", on); wk.addEventListener("error", bad);
      try { wk.postMessage({ id, inp, kind }); } catch (e) { off(); try { res(local()); } catch (e2) { rej(e2); } } // 넘길 수 없는 값이면 화면에서
    });
  }
  // 몬테카를로 한 번: worker 에서 (화면이 멈추지 않게). slim 이면 목표 확률·목표일 분포만 돌려받는다
  const simLocal = ({ model, opt, cats, slim }) => { const R = Model.simulate(model, { ...opt, fxOf, cats: cats && new Set(cats) }); return slim ? { p_goal: R.p_goal, terminal: R.terminal } : R; };
  const simAsync = (model, opt, cats, slim) => { const { fxOf: _f, cats: _c, ...o } = opt; return allocCompute({ model, opt: o, cats: cats ? [...cats] : null, slim }, null, "sim"); };
  async function runAllocNow() {
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
      const series = {}; { const P = mcPrices(); for (const k in P) series[k] = { dates: P[k].dates, adj: P[k].adj }; }
      const defs = {}; for (const [k, D] of Object.entries(ALLOC_DEF)) defs[k] = { cap: D.cap, cutSmall: { agg: 0.1, mid: 0.25, con: 0.4 }[k] };
      const inp = { base, series, settings: m, events: S.state.events, betas: factorBetas().beta, startDate: today(), goal: g, mixes, defs };
      const r = await allocCompute(inp, prog), top = r.top;
      const out = r.out.map((o) => ({ ...o, name: o.k === "keep" ? "현재 유지" : `${ALLOC_DEF[o.k].n} (가장 큰 종목 ${pct(o.w[top], 0)})` }));
      const model = { monthDates: r.fd };
      lastAlloc = { base: base.map((h) => ({ ticker: h.ticker, price0: h.price0, ccy: h.ccy, shares: h.shares })), V0, top, out, subs, fd: model.monthDates, at: Date.now() }; allocDirty = false;
      try { localStorage.setItem(AL_KEY, JSON.stringify({ sig: fcSig() + "|" + JSON.stringify(S.state.alloc_mix || {}), ...lastAlloc })); } catch (e) { /* 무시 */ }
      renderAllocTable(); renderAllocChart();
    } catch (e) { st.textContent = "오류: " + e.message; console.error(e); }
    btn.disabled = false;
  }
  // ------------------------------------------------------------ 연구용 비교: 세금 고려 + 하방 보호 (research.js)
  const ST_KEY = "naeilo-stab1";
  const stabRateOn = () => $("#stabRate")?.checked !== false;
  const stabSig = () => fcSig() + "|" + hashStr(JSON.stringify(S.state.holdings.map((h) => [h.ticker, h.avg_cost]))) + (stabRateOn() ? "|r" : "");
  // 금리→이익 관계식 식단 (data/macro/rates_earnings.json, 매달 갱신) + 오늘 기준 1년 10년물 금리 변화
  let macroP = null;
  const loadMacro = () => (macroP ||= fetch((MODE === "static" ? "../data/macro/rates_earnings.json" : "/api/macro") + "?t=" + Date.now(), { cache: "no-store" })
    .then((r) => { if (!r.ok) throw new Error("금리 자료(macro) " + r.status); return r.json(); }).catch((e) => { macroP = null; throw e; }));
  // 오늘 기준 1년 10년물 금리 변화 (%p). 시세가 없으면 macro 파일에 적힌 값
  function rateChange1y(d) {
    let dr = d && d.now && d.now.change_1y; const t = S.prices["^TNX"];
    if (t && t.close && t.close.length) { const i = t.close.length - 1, y0 = String(Number(t.dates[i].slice(0, 4)) - 1) + t.dates[i].slice(4); let j = i; while (j > 0 && t.dates[j] > y0) j--; if (t.close[i] && t.close[j]) dr = t.close[i] - t.close[j]; }
    return dr;
  }
  async function stabRate() {
    if (!stabRateOn()) return null;
    const d = await loadMacro(), b = d.meals && d.meals.b; if (!b || !b.length) throw new Error("금리 자료에 식단이 없습니다.");
    const dr = rateChange1y(d);
    if (!Number.isFinite(dr)) throw new Error("10년물 금리 시세가 없습니다.");
    return { b, dr, epsLast: d.eps_last, src: d.source };
  }
  async function runStab() {
    const st = $("#stabStatus"), btn = $("#btnStab");
    const prog = (k, n) => { st.innerHTML = `계산 중... <span class="bar" style="display:inline-block;width:120px;vertical-align:middle"><i style="width:${(k / n) * 100}%"></i></span>`; };
    btn.disabled = true; prog(0, 1); await new Promise((r) => setTimeout(r, 30));
    try {
      const g = S.state.goal, { rows } = valuation();
      const holdings = rows.filter((r) => r.valueKrw > 0).map((r) => ({ ticker: r.h.ticker, shares: r.sh, price0: r.p.v, ccy: r.ccy, valueKrw: r.valueKrw, avgCost: Number(r.h.avg_cost) > 0 ? Number(r.h.avg_cost) : null }));
      if (holdings.length < 2) throw new Error("종목이 2개 이상 있어야 비교할 수 있습니다.");
      if (g.date <= today()) throw new Error("목표일이 오늘 이후여야 합니다.");
      const series = {}; { const P = mcPrices(); for (const k in P) series[k] = { dates: P[k].dates, adj: P[k].adj }; }
      const fb = factorBetas().beta;
      const rate = await stabRate();
      const inp = { holdings, series, settings: S.state.model, events: S.state.events, betas: fb, mktBeta: fb.mkt || {}, startDate: today(), goal: { amount: Math.max(1, g.amount - cashKrw()), date: g.date }, usdKrw0: fxNow("USD"), rate: rate && { b: rate.b, dr: rate.dr } };
      const r = await allocCompute(inp, prog, "stab");
      lastStab = { ...r, at: Date.now(), epsLast: rate && rate.epsLast };
      try { localStorage.setItem(ST_KEY, JSON.stringify({ sig: stabSig(), ...lastStab })); } catch (e) { /* 무시 */ }
      renderStab();
    } catch (e) { st.textContent = "오류: " + e.message; console.error(e); }
    btn.disabled = false;
  }
  let lastStab = null;
  function stabRestore() { try { const c = JSON.parse(localStorage.getItem(ST_KEY) || "null"); if (c && c.sig === stabSig()) { lastStab = c; renderStab(); } } catch (e) { /* 무시 */ } }
  function renderStab() {
    const r = lastStab; if (!r) return;
    $("#stabStatus").textContent = `${dtStr(r.at)} 계산 · 식단 ${r.setup.trueMeals}개 × 경로 ${r.setup.paths}개`;
    const row = (name, x, tax) => `<tr><td class="l">${name}</td><td>${pct(x.p10, 0)} / <b>${pct(x.p50, 0)}</b> / ${pct(x.p90, 0)}</td><td>${krw(x.a5)}</td><td>${krw(x.a50)}</td><td>${tax == null ? "-" : krw(tax)}</td></tr>`;
    const wStr = (w) => w.map((x, i) => `${esc(r.tickers[i])} ${pct(x, 0)}`).join(" · ");
    const steps = [["지금", r.w0], ["처음 옮길 비중", r.first], ...r.ctrl.wy.map((w, i) => [`${i + 1}년 뒤 (평균)`, w])];
    $("#stabOut").innerHTML = `<div class="tablewrap"><table class="grid"><tr><th class="l">정책</th><th>목표 확률<br><span class="muted">식단별 하위10 / 중앙 / 상위10</span></th><th>청산 후<br>하위 5%</th><th>청산 후<br>중앙값</th><th>낸 세금<br>(평균)</th></tr>` +
      (r.hold0 ? row("지금 그대로 보유 · 금리 미반영", r.hold0, null) + row("지금 그대로 보유 · 금리 반영", r.hold, null) + row("세금 고려 + 하방 보호 · 금리 반영", r.ctrl, r.ctrl.tax)
        : row("지금 그대로 보유", r.hold, null) + row("세금 고려 + 하방 보호", r.ctrl, r.ctrl.tax)) + `</table></div>` +
      (r.rate ? `<p class="small">금리 반영: 10년물 금리 1년 변화 ${r.rate.dr >= 0 ? "+" : ""}${r.rate.dr.toFixed(2)}%p → 시장 기대수익 연 ${pct(r.rate.p50, 1)} 조정 (식단 하위10~상위10 ${pct(r.rate.p10, 1)} ~ ${pct(r.rate.p90, 1)}, 종목은 시장 민감도 β 배). 이익 자료 ${esc(r.epsLast || "")}까지, 출처 Robert J. Shiller.</p>` : "") +
      `<p class="small"><b>제어기가 고른 비중</b></p><ul class="small stabw">${steps.map(([l, w]) => `<li><span class="muted">${l}</span> ${wStr(w)}</li>`).join("")}</ul>` +
      `<p class="small">처음 옮길 때 예상 양도세 ${krw(r.firstTax)}. 외란 방향(시장 공통 오차에 가장 크게 흔들리는 조합): ${r.disturbance.map((x, i) => `${esc(r.tickers[i])} ${x.toFixed(2)}`).join(" · ")}.` +
      (r.hasAvg ? "" : ` <span class="bad">평균 단가가 없어 세금을 0에 가깝게 계산했습니다. 종목 탭의 보유 종목에서 매수 단가를 넣으면 더 정확해집니다.</span>`) + `</p>`;
  }
  const AL_KEY = "naeilo-alloc2";
  function allocRestore() {
    try { const c = JSON.parse(localStorage.getItem(AL_KEY) || "null"); if (!c || c.sig !== fcSig() + "|" + JSON.stringify(S.state.alloc_mix || {})) return false; lastAlloc = c; allocDirty = false; return true; } catch (e) { return false; }
  }
  // 요약 박스 4개 (누르면 실행 계획) + 차액 종목 옵션
  function renderAllocTable() {
    const { base, out } = lastAlloc, pick = S.state.alloc_pick || "mid", tgt = S.state.alloc_target;
    $("#allocBoxes").innerHTML = out.map((o, j) => `<button type="button" class="abox ${o.k === pick ? "on" : ""}" data-ak="${o.k}" style="--ac:${C[j % C.length]}">
      <div class="lh"><b>${esc(o.k === "keep" ? "현재 유지" : ALLOC_DEF[o.k].n)}</b>${tgt && tgt.k === o.k ? '<span class="tag">기준</span>' : ""}</div>
      <div class="lv">${pct(o.R.p_goal, 0)} <small>목표 확률</small></div>
      <p>중앙값 ${krw(o.R.terminal.p50)} · 나쁜 5% ${krw(o.R.terminal.p5)}</p><p>최대 낙폭 ${pct(o.R.mdd_median, 0)} · 낮을 확률 ${pct(o.R.p_loss, 0)}</p>
      <p class="muted">${[...base.map((h, i) => (!h.extra && o.w[i] > 0.004 ? `${esc(h.ticker)} ${pct(o.w[i], 0)}` : "")).filter(Boolean), ...(() => { const x = base.reduce((a, h, i) => a + (h.extra ? o.w[i] : 0), 0); return x > 0.004 ? [`옮긴 몫 ${pct(x, 0)}`] : []; })()].join(" · ")}</p></button>`).join("");
    $("#allocStatus").textContent = `${dtStr(lastAlloc.at || Date.now())} 계산 · 안별 1,500경로` + (lastAlloc.subs && lastAlloc.subs.length ? ` · 시세가 없어 대신 씀: ${[...new Set(lastAlloc.subs)].join(", ")}` : "");
    $("#allocMix").innerHTML = Object.entries(ALLOC_DEF).map(([k, D]) => `<label><span class="row between"><span>${D.n} · ${esc(D.d)}</span><span class="muted small">기본 ${esc(D.mix)}</span></span><input type="text" data-mix="${k}" value="${esc((S.state.alloc_mix || {})[k] || D.mix)}" style="width:100%"></label>`).join("") + `<p class="muted small">"종목 비중, 종목 비중" 형식. 기본값은 계산에 쓰는 대표 지수 예시이며 추천 종목이 아닙니다.</p>`;
    renderAllocPlan();
  }
  // 고른 비중 조정안: 비중·확률 변화 + 세금 예시(연도 나누기) + 기준으로 기록하면 벗어날 때 알림. 종목별 수량·금액은 보이지 않는다
  function renderAllocPlan() {
    const card = $("#allocPlanCard"); if (!lastAlloc) return;
    const { base, out, top, V0 } = lastAlloc, k = S.state.alloc_pick || "mid", o = out.find((x) => x.k === k);
    if (!o || k === "keep") { card.style.display = "none"; return; }
    card.style.display = "block";
    $("#allocPlanTitle").textContent = `비중·확률 비교 · ${ALLOC_DEF[k].n}`;
    const fx = (h) => fxNow(h.ccy) || 1, w0 = out[0].w, L = [];
    const sells = [], buys = [];
    base.forEach((h, i) => { const dv = (o.w[i] - w0[i]) * V0, sh = dv / (h.price0 * fx(h)); if (Math.abs(sh) < 0.5) return; (dv < 0 ? sells : buys).push({ t: h.ticker, sh: Math.abs(sh), v: Math.abs(dv) }); });
    const R0 = out[0].R, R1 = o.R;
    L.push(`<p>가장 큰 종목 비중 <b>${pct(w0[top], 0)}→${pct(o.w[top], 0)}</b>이면 목표 확률 <b>${pct(R0.p_goal, 0)}→${pct(R1.p_goal, 0)}</b>, 나쁜 경우 5% ${krw(R0.terminal.p5)}→${krw(R1.terminal.p5)}, 최대 낙폭 ${pct(R0.mdd_median, 0)}→${pct(R1.mdd_median, 0)}로 달라져요. 매매 권유가 아닌 계산값입니다.</p>`);
    const th = S.state.holdings.find((h) => h.ticker === base[top].ticker), avg = Number(th?.avg_cost) || 0, s0 = sells.find((x) => x.t === base[top].ticker);
    if (s0 && avg) {
      const gps = (base[top].price0 - avg) * fx(base[top]), used = Math.max(0, realizedYear(new Date().getFullYear()));
      const tax = (n, first) => Math.max(0, n * gps - Math.max(0, CGT_DED - first)) * CGT;
      const one = tax(s0.sh, used), two = tax(s0.sh / 2, used) + tax(s0.sh / 2, 0);
      L.push(`<p><b>세금 예시</b>: 이 비중으로 줄이는 부분의 이익을 한 해에 모두 실현하면 약 ${krw(one)}원, 두 해에 나누면 약 ${krw(two)}원 (해마다 공제 250만원).${used ? ` 올해 이미 실현한 이익 ${krw(used)}원 반영.` : ""}</p>`);
    } else if (s0) L.push(`<p class="muted small">매수 단가를 넣으면 연도별 세금을 계산합니다.</p>`);
    const tgt = S.state.alloc_target;
    L.push(`<div class="row wrap"><button class="primary sm" id="allocSetTgt">${tgt && tgt.k === k ? "기준으로 기록해 둠 (해제)" : "이 비율을 내 기준으로 기록"}</button><span class="muted small">기록한 비율에서 5%p 넘게 벗어나면 알려 줍니다.</span></div>`);
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
        const lg = (S.state.trade_log || []).find((x) => x.d === L[i].d && x.t === t); // ✎ 로 넣은 매매는 그 가격, 아니면 그날 종가
        const px = lg?.px || priceOn(t, L[i].d), fx = fxNow(ccyOf(t)) || 1, avg = Number(S.state.holdings.find((h) => h.ticker === t)?.avg_cost) || null;
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
    save(false); renderTrades(); if (dashOn()) renderDash();
  }
  function clearTrades() {
    if (!confirm("거래 기록을 모두 지울까요? 목표 진행 그래프도 지금 수량 기준으로 다시 그립니다.")) return;
    S.state.lots = []; save(false); renderTrades(); if (dashOn()) renderDash();
  }
  function onTradeDate(e) {
    const i = +e.target.dataset.lot, L = S.state.lots, d = e.target.value; if (!L[i] || !d) return;
    const lo = L[i - 1]?.d || "0000", hi = L[i + 1]?.d || "9999";
    if (d <= lo || d >= hi) { toast(`${lo} 과 ${hi} 사이 날짜만 됩니다`); e.target.value = L[i].d; return; }
    L[i].d = d; save(false); renderTrades(); if (dashOn()) renderDash();
  }
  // 백업 알림: 입력값이 이 브라우저에만 있으므로, 바뀐 뒤 14일 넘게 백업하지 않았으면 대시보드에 알린다
  function markBackup() { S.state.ui.last_backup = today(); save(false); renderBackupNag(); }
  function renderBackupNag() {
    const box = $("#backupNag"), ui = S.state.ui, L = S.state.lots || [];
    const lastChange = L.length ? L[L.length - 1].d : null, last = ui.last_backup || null;
    const due = !S.state.sample && lastChange && (!last || last < lastChange) && yearsBetween(last || L[0].d, today()) * 365 >= 14 && !(ui.backup_snooze && ui.backup_snooze > today());
    box.style.display = due ? "block" : "none"; if (!due) return;
    box.innerHTML = `<b>입력값을 백업해 두세요.</b> <span class="small">이 브라우저에만 저장됩니다${last ? ` · 마지막 ${last}` : ""}.</span> <button class="primary sm" id="bkNow">지금 백업</button> <button class="sm" id="bkLater">7일 뒤에</button>`;
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
      $("#taxGoal").innerHTML = `<div class="kpis">${[["세후 목표 달성 확률", pct(pAfter, 0), `세전 ${pct(R.p_goal, 0)} · 목표일에 모두 판다면`], ["현금화 세금 (중앙값)", krw(tMed) + "원", `중앙값 ${krw(R.terminal.p50)}원 → 세후 ${krw(R.terminal.p50 - tMed)}원`], [`세후 ${krw(g.amount)}이 되려면`, krw(g.amount + Math.max(0, g.amount - basis - CGT_DED) * CGT / (1 - CGT)) + "원", "세전으로 필요한 금액 (근사)"]].map(([k, v, s2]) => `<div class="kpi"><div class="k">${k}</div><div class="v">${v}</div><div class="s">${s2}</div></div>`).join("")}</div><p class="muted small">원금 ${krw(cb ?? R.V0)}원${cb ? "" : "(오늘 평가액)"} 기준.</p>`;
    } else $("#taxGoal").innerHTML = `<p class="muted small">목표일 전망을 계산하면 세후 확률이 나옵니다.</p>`;
    const wa = hs.filter((r) => r.avg && r.ccy !== "KRW");
    if (!wa.length) { $("#taxBox").innerHTML = `<p>매수 단가를 넣으면 종목별 세금을 계산합니다. <a href="#" data-go="stocks">매수 단가 넣기</a></p>`; $("#taxYears").innerHTML = ""; return; }
    const gains = wa.map((r) => ({ t: r.h.ticker, g: (r.p.v - r.avg) * r.sh * r.fx, gps: (r.p.v - r.avg) * r.fx, sh: r.sh, w: r.w, px: r.p.v * r.fx }));
    const sum = gains.reduce((a, x) => a + x.g, 0), taxAll = Math.max(0, sum - CGT_DED) * CGT;
    const L = [`<table class="grid"><tr><th class="l">종목</th><th>평가 이익 (원)</th><th>주당 이익</th></tr>${gains.map((x) => `<tr><td class="l">${esc(x.t)}</td><td class="${cls(x.g)}">${krw(x.g)}</td><td>${krw(x.gps)}</td></tr>`).join("")}</table>`];
    L.push(`<p>지금 모두 판다면 세금 약 <b>${krw(taxAll)}원</b> (이익 합계 ${krw(sum)}원).${used ? ` 올해 이미 실현한 이익 ${krw(used)}원.` : ""}</p>`);
    $("#taxBox").innerHTML = L.join("");
    // 연도별: 해마다 공제 250만원. 비중을 줄이는 경우 = 기준으로 기록한 비중 조정안(없으면 집중 종목 45%로)을 처음 두 해에 나눠. 수량 대신 %p로 보인다
    const top = gains.filter((x) => x.gps > 0).sort((a, b) => b.w - a.w)[0];
    const tg = S.state.alloc_target, tw = tg && top ? tg.w[top.t] ?? null : null;
    const sellAll = top ? Math.max(0, Math.ceil((((top.w - (tw != null ? tw : Math.min(top.w, 0.45))) * total) / top.px))) : 0;
    const yEnd = +g.date.slice(0, 4), years = []; for (let y = yNow; y <= Math.max(yNow, yEnd); y++) years.push(y);
    const nSplit = Math.min(2, years.length);
    $("#taxYears").innerHTML = `<tr><th class="l">연도</th><th>남은 공제</th><th>비중 줄이는 몫</th><th>예상 세금</th></tr>` + years.map((y, k) => {
      const left = Math.max(0, CGT_DED - (y === yNow ? used : 0));
      const sell = top && k < nSplit ? Math.round(sellAll / nSplit) : 0, tax = sell ? Math.max(0, sell * top.gps - left) * CGT : 0;
      return `<tr><td class="l">${y}${y === yNow ? " (올해)" : ""}</td><td>${krw(left)}원</td><td>${sell ? `−${((sell * top.px) / total * 100).toFixed(0)}%p` : "-"}</td><td>${sell ? krw(tax) + "원" : "-"}</td></tr>`;
    }).join("") + `<tr><td class="l muted wrapc" colspan="4">계산 예시: ${tg ? `기준으로 기록한 '${esc(tg.name)}' 비중` : "가장 큰 종목 비중을 45%로 줄이는 경우"}을 ${nSplit}년에 나눈 세금. 한 해에 모두면 약 ${top && sellAll ? krw(Math.max(0, sellAll * top.gps - Math.max(0, CGT_DED - used)) * CGT) + "원" : "-"}. 매매 권유가 아닙니다.</td></tr>`;
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
      ["현재 원/달러", nf(now, 1), `추세 ${nf(sg.kalman_level, 1)} (${spct(sg.dev_from_kalman)})`],
      ["매매기준율", marUsd() ? nf(marUsd().rate, 2) : "-", marUsd() ? `${marUsd().date} · 서울외국환중개 · 평가액에 적용` : "받지 못함 · 평가액은 현재 환율"],
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
    // 기간 버튼은 과거 구간만 정하고, 전망은 늘 오늘부터 3년
    const md = []; for (let k = 1; k <= 36; k++) md.push(Model.addMonths(today(), k));
    const fd = [today(), ...md], ft = fd.map((d) => yearsBetween(today(), d)), band = (k) => ft.map((t) => at(t, k));
    Charts.lineChart($("#fxChart"), { x, height: 300, yfmt: (v) => nf(v, 0),
      bands: [{ x: fd, lo: band("p5"), hi: band("p95"), color: "var(--band)", opacity: 0.13, name: "전망 5~95%" }, { x: fd, lo: band("p25"), hi: band("p75"), color: "var(--band)", opacity: 0.25, name: "25~75%" }],
      series: [{ name: "추세", y: ind.level.slice(k0), color: "var(--c1)", width: 3 }, { name: "원/달러", y: ind.close.slice(k0), color: "var(--fg)", width: 1 },
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
    const has = !!ghToken();
    // 개발자용 카드는 일반 사용자에게 숨긴다. 토큰을 넣은 기기이거나 주소에 ?dev=1 을 붙여 연 기기에서만 (?dev=0 으로 끔)
    let dev = has;
    try { const q = new URLSearchParams(location.search).get("dev"); if (q === "1") localStorage.setItem("naeilo-dev", "1"); else if (q === "0") localStorage.removeItem("naeilo-dev"); dev ||= localStorage.getItem("naeilo-dev") === "1"; } catch (e) { /* 무시 */ }
    $("#devCard").style.display = MODE === "static" && dev ? "" : "none";
    $("#collectCard").style.display = dev || MODE === "local" ? "" : "none"; // 시세 수집·로그는 개발자용 (내 PC 서버에서는 늘 보임)
    if (MODE !== "static" || !dev) return;
    box.innerHTML = `<p class="small">일반 사용자는 필요 없습니다. 토큰을 넣으면 '시세 수집'이 GitHub Actions 수집을 직접 실행하고 저장소 데이터를 갱신합니다(공개 중계 대신). 기록 탭의 미래 설계 글도 이 토큰으로 저장소(data/beyora.json)에 저장되고, 토큰이 없는 사람은 읽기만 합니다. ${has ? "<b class='good'>연결됨.</b>" : ""} 토큰은 이 브라우저에만 저장됩니다.</p>
      ${has ? `<p class="small">기기 자동 동기화: ${!S.config?.push ? "중계 주소 없음" : !S.sync ? "확인 중…" : S.sync.ok ? `<b class="good">켜짐</b> · 이 토큰을 넣은 기기끼리 보유 수량·목표를 자동으로 맞춤 (마지막 ${new Date(S.sync.at).toLocaleTimeString()})` : `꺼짐 (${esc(S.sync.err)}) · 중계를 다시 배포해야 할 수 있음`}</p>` : ""}
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
      try { await ghApi("actions/workflows/collect.yml"); toast("GitHub 연결됨"); renderGh(); bvLoad(true); syncPull(); const miss = missingTickers(); if (miss.length) { showTab("stocks"); ghCollect(miss); } }
      catch (e) { toast("연결 실패: " + e.message); renderGh(); }
    };
    if (has) {
      $("#ghTest").onclick = async () => { try { await ghApi("actions/workflows/collect.yml"); toast("정상 연결"); } catch (e) { toast("연결 실패: " + e.message); } };
      $("#ghDel").onclick = (e) => { if (armed(e.target)) { try { localStorage.removeItem(TOKEN_KEY); } catch (er) { /* 무시 */ } renderGh(); } };
    }
  }
  // 새 소식: 사이트 data/app_news.json (앱 소식함과 같은 개발자 소식). 말풍선·업적 소식은 앱에만
  let newsP = null;
  function renderNews() {
    const box = $("#newsList"); if (!box) return;
    (newsP ||= fetch((MODE === "static" ? "../data/" : "/data/") + "app_news.json?t=" + Date.now(), { cache: "no-store" }).then((r) => (r.ok ? r.json() : [])).catch(() => []))
      .then((xs) => {
        xs = (Array.isArray(xs) ? xs : []).filter((x) => x && x.text).sort((a, b) => String(b.date).localeCompare(String(a.date)));
        const wk = new Date(Date.now() - 7 * 864e5).toISOString().slice(0, 10); // 7일 안 = 새
        $("#newsN").textContent = xs.length ? `· ${xs.length}건` : "";
        box.innerHTML = xs.length ? `<ul class="newslist">${xs.slice(0, 20).map((x) => `<li><span class="muted small">${esc(String(x.date || "").slice(5).replace("-", "."))}</span> ${x.date >= wk ? '<b class="newtag">새</b> ' : ""}${esc(x.text)}</li>`).join("")}</ul>`
          : `<p class="muted small">아직 새 소식이 없습니다.</p>`;
      });
  }
  function renderSettings() {
    renderGh(); renderNews();
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
  // ------------------------------------------------------------ 엑셀 대시보드에서 온 기능
  // 선택 기능(현금). 지금은 모두에게 켠다 (나중에 무료·유료를 나눌 때 여기 한 곳만)
  const premium = () => true;
  function cashKrw() {
    const c = S.state.cash; if (!premium() || !c) return 0;
    return (Number(c.krw) || 0) + (Number(c.usd) || 0) * (fxBase("USD") || 0);
  }
  function renderPrem() {
    const card = $("#premCard"); if (!card) return;
    card.style.display = premium() ? "" : "none"; if (!premium()) return;
    const c = S.state.cash || {};
    $("#premBox").innerHTML = `<h3 style="margin:0 0 4px">현금</h3><div class="row wrap"><label class="small">원화 <input class="num" id="cashKrw" inputmode="decimal" value="${c.krw ? nf(c.krw) : ""}" placeholder="0" style="width:9em"></label><label class="small">달러 <input class="num" id="cashUsd" inputmode="decimal" value="${c.usd ? nf(c.usd, 2) : ""}" placeholder="0" style="width:7em"></label></div>
      <p class="small muted" style="margin:4px 0 0">총자산·목표 대비·전망에 더합니다 (전망에서는 그대로 있다고 봄).</p>`;
  }
  function onPrem(e) {
    const t = e.target;
    if (e.type === "change" && (t.id === "cashKrw" || t.id === "cashUsd")) {
      const v = Number(String(t.value).replace(/,/g, "")) || 0;
      S.state.cash = { ...(S.state.cash || {}), [t.id === "cashKrw" ? "krw" : "usd"]: v }; save(); renderAll();
    }
  }

  // ------------------------------------------------------------ 패턴 예측 (엑셀 '26 예측'을 고친 것)
  // 엑셀은 총자산의 작년·재작년 같은 날 변동을 0.78·0.22로 섞었다. 여기서는
  //  · 총자산 대신 지금 보유 종목을 과거에 적용한 지수(입출금·매매 영향 없음)의 하루 로그 수익률을 쓰고
  //  · 같은 날 움직임은 그해 평균을 뺀 '모양'만 가져오며, 추세는 지난 1년 속도와 연 10% 사이에서 고르고
  //  · 가중(a1·a2)과 추세 비율(keep)은 최근 12달을 한 달씩 빼고 맞춘 뒤 뺀 달을 맞히는지로 고른다 (교차 검증)
  const LN_LONG = Math.log(1.1) / 252, PGRID = [0, 0.25, 0.5, 0.75, 1];
  let patMemo = null;
  function patData(H) {
    const n = H.index.length; if (n < 400) return null;
    const lr = [0], md = {}, cs = [0];
    for (let i = 1; i < n; i++) { const r = Math.log(H.index[i] / H.index[i - 1]) || 0; lr.push(r); const d = H.dates[i]; (md[d.slice(0, 4)] ||= {})[d.slice(5)] = r; }
    lr.forEach((r, i) => cs.push(cs[i] + r));
    return { H, md, first: H.dates[1], mean: (a, b) => (b - a > 20 ? (cs[b] - cs[a]) / (b - a) : null) };
  }
  // 기준일(o) 뒤 날짜들 T 의 특징: k년 전(기준일 이전에 있는 가장 가까운 해부터) 같은 날 수익률 − 그해 평균의 합
  function patFeat(P, o, T) {
    const od = P.H.dates[o], m1 = P.mean(Math.max(1, o - 251), o + 1), m2 = o > 272 ? P.mean(Math.max(1, o - 503), o - 251) : null, s = [0, 0];
    for (const d of T) {
      const mdk = d.slice(5); let k = 0;
      for (let Y = +d.slice(0, 4) - 1; k < 2; Y--) {
        const src = `${Y}-${mdk}`; if (src < P.first) break; if (src > od) continue;
        const r = P.md[Y]?.[mdk], m = k === 0 ? m1 : m2;
        if (r != null && m != null) s[k] += r - m;
        k++;
      }
    }
    return { s1: s[0], s2: m2 == null ? 0 : s[1], n: T.length, m1: m1 ?? LN_LONG };
  }
  const patPred = (f, p) => p.a1 * f.s1 + p.a2 * f.s2 + f.n * (p.keep * f.m1 + (1 - p.keep) * LN_LONG);
  function patFit(folds, shape) {
    let best = null;
    for (const a1 of shape ? PGRID : [0]) for (const a2 of shape ? PGRID : [0]) for (const keep of PGRID) {
      const p = { a1, a2, keep }; let e = 0; folds.forEach((x) => (e += Math.abs(patPred(x.f, p) - x.act)));
      if (!best || e < best.e - 1e-12) best = { ...p, e };
    }
    return best;
  }
  function patModel(H) {
    const sig = H.dates.length + "|" + H.dates[H.dates.length - 1] + "|" + H.index[H.index.length - 1] + "|" + today().slice(0, 7);
    if (patMemo && patMemo.sig === sig) return patMemo.v;
    const P = patData(H); let v = null;
    if (P) {
      const byM = {}; H.dates.forEach((d, i) => { if (i > 0) (byM[d.slice(0, 7)] ||= []).push(i); });
      const folds = Object.keys(byM).filter((m) => m < today().slice(0, 7)).sort().slice(-12).map((m) => {
        const ix = byM[m], s = ix[0], e = ix[ix.length - 1]; if (s < 260) return null;
        return { m, d1: H.dates[e], f: patFeat(P, s - 1, ix.map((i) => H.dates[i])), act: Math.log(H.index[e] / H.index[s - 1]), v1: H.total[e] };
      }).filter(Boolean);
      if (folds.length >= 6) {
        const cv = (shape) => folds.map((x, j) => patPred(x.f, patFit(folds.filter((_, i) => i !== j), shape)));
        const pc = cv(true), tc = cv(false);
        const sd = Math.sqrt(folds.reduce((a, x, j) => a + (pc[j] - x.act) ** 2, 0) / folds.length);
        v = { P, folds, pc, tc, p: patFit(folds, true), t: patFit(folds, false), sd };
      }
    }
    patMemo = { sig, v }; return v;
  }
  function weekdays(from, to) { // from 다음 날부터 to 까지 평일
    const out = [], d = new Date(from + "T00:00:00Z");
    for (let g = 0; g < 4000; g++) { d.setUTCDate(d.getUTCDate() + 1); const s = d.toISOString().slice(0, 10); if (s > to) break; const w = d.getUTCDay(); if (w && w < 6) out.push(s); }
    return out;
  }
  // 마지막 거래일에서 출발한 패턴 예측: 월별 날짜와 누적 로그 수익률
  function patFuture(M, toDate, p = M.p) {
    const H = M.P.H, o = H.dates.length - 1, md = [];
    for (let k = 0; k <= 1200 && Model.addMonths(today(), k) <= toDate; k++) md.push(Model.addMonths(today(), k));
    if (md[md.length - 1] !== toDate) md.push(toDate);
    const cum = [0]; let prev = H.dates[o];
    for (let k = 1; k < md.length; k++) { cum.push(cum[k - 1] + patPred(patFeat(M.P, o, weekdays(prev, md[k])), p)); prev = md[k]; }
    return { md, cum };
  }
  // 칸 크기: 목표의 1/30 (엑셀처럼 1억에 3칸)
  const hitBin = () => S.state.goal.amount / 30; // 패턴·추세만 비교용 (기본 미래 고르기)
  // ------------------------------------------------------------ 주간 적중 기록판 (엑셀 X/O 격자처럼 미리 적고 나중에 체크)
  // 그 주에 처음 열 때, 지금 보는 미래 기준으로 '이번 주 금요일 마감 값'과 범위(25~75%)를 저장한다. 저장한 예측은 바꾸지 않는다.
  // 금요일 마감 뒤: 범위 안이면 적중(날짜), 벗어나면 예측 칸 X·실제 칸 O. 실제는 예측한 날 수량 기준(그 주 매매 영향 제외) + 그때 현금
  const weekFri = () => { // 이번 주 금요일 (토·일이면 다음 주 금요일)
    const t = new Date(today() + "T00:00:00Z"), w = t.getUTCDay();
    t.setUTCDate(t.getUTCDate() + (w === 6 ? 6 : w === 0 ? 5 : 5 - w)); return t.toISOString().slice(0, 10);
  };
  const closedIdx = (H) => { let i = H.dates.length - 1; if (i >= 0 && Date.now() < Date.parse(H.dates[i] + "T21:00:00Z")) i--; return i; };
  const idxAtOrBefore = (H, d) => { let i = H.dates.length - 1; while (i > 0 && H.dates[i] > d) i--; return i; };
  // 지금 보는 미래 기준으로 '마지막 마감일 o → 날짜 d' 로그 수익률의 중앙(mu)과 퍼짐(sd). 전망이 아직 없으면 null
  function hitPath(H, M, o) {
    const basis = $("#histBasis .on")?.dataset.b || "model";
    if (basis === "pattern" && M) return { name: "패턴", at: (d) => { const w = weekdays(H.dates[o], d); return { mu: patPred(patFeat(M.P, o, w), M.p), sd: M.sd * Math.sqrt(w.length / 21) }; } };
    const scen = basisScen(basis), F = fcReady(scen), B = F && !F.err && F.R?.bands, fd = F && F.model?.monthDates;
    if (!B || !fd) { if (!F) forecastLater(scen, () => { if (dashOn()) renderDash(); }); return null; }
    const lr = (k) => Math.log(B.p50[k] / B.p50[0]), vr = (k) => ((Math.log(B.p75[k]) - Math.log(B.p25[k])) / 1.349) ** 2, T = (d) => Date.parse(d + "T00:00:00Z");
    return { name: BASIS[basis] || "패턴", at: (d) => { // 달 사이는 날짜 비율로 잇는다 (분산은 시간에 비례)
      let k = 0; while (k + 1 < fd.length - 1 && fd[k + 1] <= d) k++;
      const a = Math.max(0, Math.min(1, (T(d) - T(fd[k])) / Math.max(1, T(fd[k + 1]) - T(fd[k])))), k2 = Math.min(k + 1, fd.length - 1);
      return { mu: lr(k) + a * (lr(k2) - lr(k)), sd: Math.sqrt(vr(k) + a * (vr(k2) - vr(k))) };
    } };
  }
  const hitVals = (base, cash, mu, sd) => { const v = (z) => Math.round(base * Math.exp(mu + z * sd) + cash); return { p50: v(0), lo: v(-0.674), hi: v(0.674) }; };
  function weekRecord(H, M, cash) {
    if (!H.dates.length) return; // 샘플 동안 적은 예측은 샘플을 지울 때 같이 지운다 (endSample)
    const W = (S.state.weekly ||= []), f = weekFri(), o = closedIdx(H);
    // 아직 마감 전인 주: 수량·현금이 바뀌면 예측 % 는 그대로 두고 지금 수량·현금 기준으로 금액을 다시 맞춘다 (마감한 주는 그대로)
    const open = W.find((r) => o < 0 || r.f > H.dates[o]), i0 = open ? H.dates.indexOf(open.d0) : -1, nb = i0 >= 0 ? H.total[i0] : 0;
    if (open && nb > 0 && open.base > 0 && (Math.abs(nb / open.base - 1) > 1e-4 || Math.abs(cash - open.cash) >= 1)) {
      const k = nb / open.base, sc = (v) => Math.round((v - open.cash) * k + cash);
      Object.assign(open, { p50: sc(open.p50), lo: sc(open.lo), hi: sc(open.hi), base: Math.round(nb), i0: H.index[i0], cash: Math.round(cash) });
      save(false);
    }
    if (W.some((r) => r.f === f) || o < 0) return;
    const base = H.total[o], n = weekdays(H.dates[o], f).length; if (!(base > 0) || !n) return;
    const pa = hitPath(H, M, o); if (!pa) return;
    const { mu, sd } = pa.at(f);
    W.push({ f, d0: H.dates[o], i0: H.index[o], base: Math.round(base), cash: Math.round(cash), ...hitVals(base, cash, mu, sd), by: pa.name });
    if (W.length > 104) W.splice(0, W.length - 104);
    save(false);
  }
  function weekRows(H) {
    const o = closedIdx(H), last = o >= 0 ? H.dates[o] : "";
    return (S.state.weekly || []).map((r) => {
      const i0 = H.dates.indexOf(r.d0), base = i0 >= 0 ? H.index[i0] : r.i0, done = last >= r.f;
      const j = done ? idxAtOrBefore(H, r.f) : H.dates.length - 1, act = j >= 0 && base ? Math.round(r.base * (H.index[j] / base) + r.cash) : null;
      return { ...r, act, done, hit: act != null && act >= r.lo && act <= r.hi };
    });
  }
  const addDays = (d, n) => { const t = new Date(d + "T00:00:00Z"); t.setUTCDate(t.getUTCDate() + n); return t.toISOString().slice(0, 10); };
  const monthEnd = (m) => { const t = new Date(Date.UTC(+m.slice(0, 4), +m.slice(5, 7), 0)); return t.toISOString().slice(0, 10); };
  // 가예측: 아직 오지 않은 칸. 지금(마지막 마감) 값에서 그 날짜까지 지금 보는 미래 기준으로 그린다. 그 주가 시작되면 주간 예측으로 잠긴다
  function hitTent(H, M, cash, dates, lab) {
    const o = closedIdx(H); if (o < 0 || !(H.total[o] > 0)) return [];
    const pa = hitPath(H, M, o); if (!pa) return [];
    return dates.map((d) => { const { mu, sd } = pa.at(d); return { f: d, lab: lab(d), tent: true, by: pa.name, ...hitVals(H.total[o], cash, mu, sd) }; });
  }
  function hitWeekCols(H, M, cash) {
    const rows = weekRows(H).slice(-26).map((r) => ({ ...r, lab: hmd(r.f) }));
    const end = today().slice(0, 4) + "-12-31", fr = [];
    let d = rows.length ? addDays(rows[rows.length - 1].f, 7) : weekFri();
    while (d <= end || fr.length < 8) { fr.push(d); d = addDays(d, 7); }
    return rows.concat(hitTent(H, M, cash, fr, hmd));
  }
  // 월 보기: 이번 달부터 2년 뒤 12월까지 가예측
  function hitMonthCols(H, M, cash) {
    const cur = today().slice(0, 7), y = +cur.slice(0, 4), cols = [], ml = (d) => `${+d.slice(5, 7)}월`;
    const fut = []; for (let m = cur; m <= `${y + 2}-12`; m = Model.addMonths(m + "-01", 1).slice(0, 7)) fut.push(monthEnd(m));
    return cols.concat(hitTent(H, M, cash, fut, ml));
  }
  const hmd = (d) => `${+d.slice(5, 7)}/${+d.slice(8)}`;
  function renderHit(H, M, cash) {
    const card = $("#hitCard"), rows = weekRows(H);
    if (!rows.length) { card.style.display = "none"; return; }
    card.style.display = "";
    const mode = $("#hitMode .on")?.dataset.m || "w";
    const gap = (r) => `${spct(r.act / r.p50 - 1)}, ${r.act >= r.p50 ? "+" : "-"}${krw(Math.abs(r.act - r.p50))}`;
    const cur = rows.find((r) => !r.done), done = rows.filter((r) => r.done), lastDone = done[done.length - 1];
    const list = mode === "m" ? hitMonthCols(H, M, cash) : hitWeekCols(H, M, cash);
    const sum = [];
    if (mode === "w") {
      if (cur) sum.push(`<b>이번 주 (${hmd(cur.f)} 마감) 예측</b> ${krw(cur.p50)} (${krw(cur.lo)}~${krw(cur.hi)}, ${esc(cur.by)}) · 지금 ${cur.act != null ? `${krw(cur.act)}, 예측보다 <b class="${cls(cur.act - cur.p50)}">${gap(cur)}</b>` : "-"}`);
      if (lastDone) sum.push(`<b>${hmd(lastDone.f)} 마감</b> 예측 ${krw(lastDone.p50)} → 실제 ${krw(lastDone.act)} · ${lastDone.hit ? "<b class=\"good\">적중</b>" : "빗나감"} (<span class="${cls(lastDone.act - lastDone.p50)}">${gap(lastDone)}</span>)`);
      if (done.length) sum.push(`지금까지 적중 <b>${done.filter((r) => r.hit).length}/${done.length}</b> · 평균 차이 ${pct(done.reduce((a, r) => a + Math.abs(r.act / r.p50 - 1), 0) / done.length)}`);
      else sum.push(`<span class="muted">첫 체크는 이번 주 금요일 마감 뒤.</span>`);
    } else {
      const ye = list.find((r) => r.tent && r.f.endsWith("-12-31")), last = list[list.length - 1];
      if (ye) sum.push(`<b>올해 말 예상</b> ${krw(ye.p50)} (${krw(ye.lo)}~${krw(ye.hi)}, ${esc(ye.by)})`);
      if (last && last.tent && last !== ye) sum.push(`<b>${last.f.slice(0, 4)}년 말 예상</b> ${krw(last.p50)} (${krw(last.lo)}~${krw(last.hi)})`);
    }
    $("#hitSum").innerHTML = sum.join("<br>");
    if (!list.length) { $("#hitGrid").innerHTML = ""; $("#hitNote").textContent = "전망을 계산하는 중입니다…"; return; }
    const vals = list.flatMap((r) => [r.lo, r.hi, r.act].filter((v) => v != null && isFinite(v)));
    let bin = S.state.goal.amount / 100; const maxRows = mode === "m" ? 24 : 14; while ((Math.max(...vals) - Math.min(...vals)) / bin > maxRows) bin *= 2;
    const b = (v) => Math.floor(v / bin), lo = b(Math.min(...vals)), hi = b(Math.max(...vals));
    const nowI = Math.max(0, list.findIndex((r) => !r.done));
    let h = `<table class="hit"><tr><th></th>${list.map((r, i) => `<th class="${i === nowI ? "now" : r.tent ? "tent" : ""}" title="${r.tent ? "가예측" : r.d0 ? r.d0 + " 기준" : r.f}${r.by ? " · " + esc(r.by) : ""}">${mode === "m" && (i === 0 || i === nowI || r.f.slice(5, 7) === "01") ? r.f.slice(0, 4) + "년<br>" : ""}${r.lab}</th>`).join("")}</tr>`;
    for (let k = hi; k >= lo; k--) {
      h += `<tr><th>${krw(k * bin)}</th>` + list.map((r) => {
        const has = r.p50 != null, inR = has && k >= b(r.lo) && k <= b(r.hi), pb = has ? b(r.p50) : null, ab = r.act != null ? b(r.act) : null;
        const tip = (has ? `${r.tent ? "가예측" : r.retro ? "사후 계산" : "예측"} ${krw(r.p50)} (${krw(r.lo)}~${krw(r.hi)})` : "예측 없음") + (r.act != null ? ` · ${r.done ? "실제" : "지금"} ${krw(r.act)}` : "");
        const c = [inR ? "rng" : "", r.tent ? "tent" : "", r.retro ? "retro" : ""].filter(Boolean).join(" "), td = (cl, t) => `<td class="${[cl, c].filter(Boolean).join(" ")}" title="${tip}">${t}</td>`;
        if (!r.done) return pb === k ? td("pend", "?") : `<td class="${c}"></td>`;
        if (!has) return ab === k ? td("o", "O") : `<td class="${c}"></td>`;
        if (r.hit && ab === k) return td("ok", r.lab);
        if (!r.hit && pb === k) return td("x", "X");
        if (!r.hit && ab === k) return td("o", "O");
        return `<td class="${c}"></td>`;
      }).join("") + "</tr>";
    }
    $("#hitGrid").innerHTML = h + "</table>";
    const wrap = $("#hitGrid").parentElement, th = $("#hitGrid th.now");
    if (wrap && th) wrap.scrollLeft = Math.max(0, th.offsetLeft - wrap.clientWidth / 2 + th.offsetWidth / 2);
    $("#hitNote").innerHTML = `한 칸 ${krw(bin)}원 · 옅은 칸 = 예측 범위(25~75%) · 날짜 = 적중, X·O = 빗나간 예측·실제 · `
      + (mode === "w" ? "점선 칸은 가예측(그 주에 처음 열 때 잠김, 마감 전 수량·현금 변경은 반영)" : "점선 칸은 가예측");
  }
  function patScores(M) { // 다시 맞춰 본 12달: 칸 적중 수와 평균 오차 (패턴 sp, 추세만 st)
    const bin = hitBin(), sc = (pr) => ({ hit: M.folds.filter((x, j) => Math.floor((x.v1 * Math.exp(pr[j] - x.act)) / bin) === Math.floor(x.v1 / bin)).length,
      mae: M.folds.reduce((a, x, j) => a + Math.abs(Math.exp(pr[j] - x.act) - 1), 0) / M.folds.length });
    M.sp = sc(M.pc); M.st = sc(M.tc); return M;
  }
  const patWins = (M) => { if (!M) return false; patScores(M); return M.sp.hit >= M.st.hit && M.sp.mae < M.st.mae; };
  // 엑셀 '대쉬보드' 지표: 연초에 세운 예측(고정) 대비 오늘 다시 맞춘 예측(변동)의 연말 값 차이
  function patYearGap(M, hold) {
    const H = M.P.H, k = H.dates.length - 1, end = today().slice(0, 4) + "-12-31", s = H.dates.findIndex((d) => d >= today().slice(0, 4) + "-01-01");
    if (s < 260) return null;
    const v0 = hold / (H.index[k] / H.index[s - 1]); // 연초 평가액 (지금 수량 기준)
    const fEnd = v0 * Math.exp(patPred(patFeat(M.P, s - 1, [...H.dates.slice(s), ...weekdays(H.dates[k], end)]), M.p));
    const vr = patFuture(M, end), vEnd = hold * Math.exp(vr.cum[vr.cum.length - 1]);
    return { fEnd, vEnd, gap: vEnd / fEnd - 1 };
  }

  // ------------------------------------------------------------ 다이어그램: 목표를 1000칸으로
  function spiralOrder(n) { // n×n 바깥에서 안으로 시계 방향
    const out = []; let t = 0, b = n - 1, l = 0, r = n - 1;
    while (t <= b && l <= r) {
      for (let c = l; c <= r; c++) out.push([t, c]); t++;
      for (let q = t; q <= b; q++) out.push([q, r]); r--;
      if (t <= b) { for (let c = r; c >= l; c--) out.push([b, c]); b--; }
      if (l <= r) { for (let q = b; q >= t; q--) out.push([q, l]); l++; }
    }
    return out;
  }
  function renderDia(H, hold, cash) {
    const card = $("#diaCard"), g = S.state.goal, tot = hold + cash;
    card.style.display = ""; // 보유 종목을 다 지워도 빈 칸으로 남긴다
    const mode = $("#diaMode .on")?.dataset.d || "spiral", N = 1000, unit = g.amount / N;
    const k = H.total.length - 1, prevTot = k > 0 ? H.total[k - 1] + cash : tot;
    const f = Math.min(N, Math.floor(tot / unit)), fp = Math.min(N, Math.floor(prevTot / unit));
    // 칸마다 색: 종목별이면 큰 종목부터 차례로 칸을 차지
    const colOf = []; if (mode === "stock") {
      const parts = valuation().rows.filter((r) => r.valueKrw > 0).sort((a, b2) => b2.valueKrw - a.valueKrw).map((r, i) => [r.h.ticker, r.valueKrw, C[i % C.length]]);
      if (cash > 0) parts.push(["현금", cash, "var(--muted)"]);
      let acc = 0; parts.forEach(([, v, c]) => { const to = Math.min(N, Math.floor((acc + v) / unit)); for (let i = Math.floor(acc / unit); i < to; i++) colOf[i] = c; acc += v; });
      $("#diaNote").innerHTML = parts.map(([t, v, c]) => `<span class="nowrap"><i class="dot" style="background:${c}"></i>${esc(t)} ${Math.floor(v / unit)}칸</span>`).join(" ");
    }
    let cells;
    if (mode === "rows") { cells = []; for (let i = 0; i < N; i++) cells.push([Math.floor(i / 50), i % 50]); }
    else cells = spiralOrder(32).slice(0, N);
    const cols = mode === "rows" ? 50 : 32, rowsN = mode === "rows" ? 20 : 32, W = 640, cs = W / cols, Ht = rowsN * cs + (mode === "rows" ? 0 : 0);
    let s = `<svg viewBox="0 0 ${W + (mode === "rows" ? 46 : 0)} ${Ht}" class="dia" role="img" aria-label="목표 1000칸 중 ${f}칸">`;
    const ox = mode === "rows" ? 46 : 0;
    cells.forEach(([r, c], i) => {
      const on = i < f, chg = (i >= Math.min(f, fp) && i < Math.max(f, fp)), up = f >= fp;
      const fill = on ? colOf[i] || "var(--c1)" : "var(--line)";
      s += `<rect x="${ox + c * cs + 0.6}" y="${r * cs + 0.6}" width="${cs - 1.2}" height="${cs - 1.2}" rx="${cs * 0.18}" fill="${fill}" fill-opacity="${on ? (chg ? 1 : 0.78) : 0.45}"${chg ? ` stroke="${up ? "var(--up)" : "var(--dn)"}" stroke-width="1.6"` : ""}/>`;
    });
    if (mode === "rows") for (let r = 1; r <= 20; r++) if (r % 2 === 0) s += `<text x="40" y="${r * cs - 2}" text-anchor="end" class="dialab">${krw((g.amount / 20) * r)}</text>`;
    if (mode !== "rows") { const [r, c] = cells[N - 1]; s += `<text x="${c * cs + cs / 2}" y="${r * cs + cs * 0.78}" text-anchor="middle" class="dialab">★</text>`; }
    $("#diaBox").innerHTML = s + "</svg>";
    const d = f - fp;
    $("#diaSub").textContent = `1칸 = ${krw(unit)}원`;
    if (mode !== "stock") $("#diaNote").innerHTML = `<b>${nf(f)}칸</b> 채움 / ${nf(N)}칸 · ${d ? `<span class="${cls(d)}">어제보다 ${d > 0 ? "+" : ""}${d}칸</span> · ` : ""}목표까지 ${nf(N - f)}칸` + (mode === "spiral" ? " · 바깥에서 안쪽 ★으로" : "");
  }
  function renderAll() {
    synthKR(); renderHeader(); renderQuotes(); renderGoalInputs(); renderEvents(); renderSettings(); renderPrem();
    if (onTab("dash")) renderHome();
    if (onTab("stocks")) renderStocksTab();
    if (onTab("analysis")) renderAnalysisTab();
  }
  const onTab = (t) => $("#tabs .on")?.dataset.tab === t;
  // 분석 탭은 목록(허브)과 항목 화면 하나. anaPage 가 "" 이면 목록
  const ANA_PAGES = ["road", "forecast", "rate", "events", "fx", "cash"];
  let anaPage = "";
  const anaOn = (k) => onTab("analysis") && anaPage === k;
  // renderDash 는 홈(목표 카드)과 분석 › 내 길(그래프·내 기록·적중 기록판·1000칸)을 함께 그린다
  const dashOn = () => onTab("dash") || anaOn("road");
  // 리로드해도 저장된 전망을 쓰고, 다시 계산은 시나리오 박스를 누를 때만
  const ensureForecast = () => { if ((!lastForecast && !fcRestore()) || fcDirty) runForecast(); else renderForecast(); }; // 입력·기준값이 바뀌었으면 바로 다시 (앱처럼)
  // 홈: 목표 카드 + 가까운 일정
  function renderHome() { renderDash(); renderUpcoming(); }
  // 종목 탭: 보유 종목 · 종목 진단 · 기간 수익률(renderDash 가 계산) · 거래 기록
  function renderStocksTab() { renderStockPrices(); renderDash(); ensureForecast(); }
  // 분석 탭: 고른 화면 하나만 그린다 (목록에서는 아무것도 계산하지 않음)
  function renderAnalysisTab() {
    const k = anaPage; if (!k) return;
    if (k === "road") {
      renderDash();
      if ((!lastAlloc || allocDirty) && !allocRestore()) runAlloc(); else { renderAllocTable(); renderAllocChart(); }
    } else if (k === "cash") renderCash();
    else { renderSchedule(); renderSens(); renderFx(); renderRateScen(); }
    ensureForecast();
  }
  function openAna(k) {
    anaPage = ANA_PAGES.includes(k) ? k : "";
    $("#anaHub").hidden = !!anaPage;
    $$(".anapage").forEach((p) => (p.hidden = p.dataset.ana !== anaPage));
    renderAnalysisTab();
  }
  // 앞으로 45일 동안의 내 종목·시장 일정 (켜 둔 사건 + 지난해 배당일로 짐작한 배당). 숫자와 날짜만, 매매 권유 없음
  function renderUpcoming() {
    const box = $("#upList"), lim = new Date(Date.now() + 45 * 864e5).toISOString().slice(0, 10), td = today();
    const rows = [], val = valuation().rows.filter((r) => r.valueKrw > 0), held = new Set(val.map((r) => r.h.ticker));
    try {
      const m = buildModelNow();
      if (m) m.model.eventList.filter((x) => x.date >= td && x.date <= lim).forEach((x) => {
        const e = x.event, earn = /실적/.test(e.kind);
        rows.push({ d: x.date, who: tgtLab(e), mine: held.has(e.target), what: earn ? quarterOf(x.date).label + " 실적" : e.kind, note: `±${e.sd}${e.factor === "rate" ? "bp" : "%"}`, cls: e.repeat && e.repeat !== "none" ? "earn" : "once" });
      });
    } catch (e) { /* 목표일 없음 등: 일정만 건너뜀 */ }
    const yAgo = Model.addMonths(td, -12);
    val.forEach((r) => {
      dividends(r.h.ticker).filter((x) => x.d > yAgo).forEach((x) => {
        const d = Model.addMonths(x.d, 12); if (d < td || d > lim) return;
        const v = x.amt * r.sh * (r.fx || 1) * (1 - (r.ccy === "KRW" ? 0.154 : WHT));
        rows.push({ d, who: r.h.ticker, mine: true, what: "배당", note: `세후 약 ${krw(v)}원`, cls: "div" });
      });
    });
    // 앱 소식함 일정 띠처럼 보유 종목 일정을 먼저, 그 안에서 날짜순 (진한 테두리는 그대로 가장 가까운 일정)
    const all = rows.length; // 홈에는 날짜가 가장 가까운 3개만 (전체는 분석 › 외부 요인 › 펼친 일정)
    rows.sort((a, b) => a.d.localeCompare(b.d)); rows.splice(3);
    rows.sort((a, b) => (b.mine - a.mine) || a.d.localeCompare(b.d));
    const near = rows.reduce((n, x) => (!n || x.d < n.d ? x : n), null);
    $("#upSum").textContent = all ? `· 앞으로 45일 ${all}건${all > rows.length ? ` 중 가까운 ${rows.length}건` : ""}` : "";
    // 펼친 일정(외부 요인)과 같은 모양: 대상별 한 줄, 날짜순 작은 칩. 가장 가까운 일정은 진한 테두리
    const groups = new Map(); rows.forEach((x) => { if (!groups.has(x.who)) groups.set(x.who, []); groups.get(x.who).push(x); });
    const dow = (d) => "일월화수목금토"[new Date(d + "T00:00:00").getDay()];
    const chip = (x) => `<span class="chip ${x.cls} ${x === near ? "next" : ""}" title="${esc(`${x.d} ${x.what} · ${x.note}`)}"><b>${esc(x.what)}</b><span>${x.d.slice(5).replace("-", ".")} ${dow(x.d)} · ${esc(x.note)}</span></span>`;
    // 대상 묶음은 줄을 따로 쓰지 않고 옆으로 이어 붙인다 (빈 오른쪽을 다음 묶음이 채움)
    box.innerHTML = rows.length ? `<div class="upflow">${[...groups].map(([t, xs]) => `<div class="upgrp"><div class="schedhead"><b>${esc(t)}</b> <span class="muted">${xs.length}건</span></div><div class="chips">${xs.map(chip).join("")}</div></div>`).join("")}</div>`
      + `<p class="muted small">보유 종목 일정이 먼저. 회색 = 분기 실적, 주황 = 한 번 있는 사건, 초록 = 배당, 진한 테두리 = 가장 가까운 일정. ±는 그날 평소 움직임. 실적 날짜는 회사 발표 전까지 추정, 배당은 지난해 같은 때를 기준으로 짐작한 값.</p>`
      : `<p class="muted small">앞으로 45일 안에 켜 둔 일정이 없습니다. 일정은 '분석 › 외부 요인'에서 켜고 끕니다.</p>`;
  }
  // 예전 탭 이름(전망·기록, 시세 수집 등)으로 저장된 값을 새 탭으로
  const TAB_OLD = { forecast: "analysis", record: "dash", insight: "dash", quotes: "stocks" };
  function showTab(name) {
    if (TAB_OLD[name]) name = TAB_OLD[name];
    if (!$("#tab-" + name)) name = "dash"; // 미래 설계 글(blog)은 탭 줄에 없지만 열 수 있다
    $$("#tabs button").forEach((b) => b.classList.toggle("on", b.dataset.tab === name));
    $$(".tab").forEach((t) => t.classList.toggle("on", t.id === "tab-" + name));
    if (name === "dash") renderHome();
    if (name === "stocks") renderStocksTab();
    if (name === "analysis") renderAnalysisTab();
    if (name === "blog") { renderBeyora(); bvLoad(); }
    try { localStorage.setItem("tab", name); } catch (e) { /* 무시 */ }
  }
  function segClick(id, cb) { $(id).addEventListener("click", (e) => { const b = e.target.closest("button"); if (!b) return; $$(id + " button").forEach((x) => x.classList.toggle("on", x === b)); cb(); }); }

  function bind() {
    $("#tabs").addEventListener("click", (e) => { const b = e.target.closest("button"); if (!b) return; if (b.dataset.tab === "analysis" && onTab("analysis")) { openAna(""); return; } showTab(b.dataset.tab); }); // 분석에서 분석을 다시 누르면 목록으로
    $("#anaHub").addEventListener("click", (e) => { const b = e.target.closest("[data-ana]"); if (b) { openAna(b.dataset.ana); window.scrollTo({ top: 0 }); } });
    document.addEventListener("click", (e) => { if (e.target.closest("[data-ana-back]")) { openAna(""); window.scrollTo({ top: 0 }); } });
    document.addEventListener("click", (e) => { const g = e.target.closest("[data-go]"); if (!g) return; e.preventDefault(); showTab(g.dataset.go); const d = $("#holdDet"); if (g.dataset.go === "stocks" && d) { d.open = true; d.scrollIntoView({ behavior: "smooth" }); if (g.dataset.add) setTimeout(() => $("#addTicker")?.focus({ preventScroll: true }), 300); } });
    $("header .logo").onclick = () => { showTab("dash"); window.scrollTo({ top: 0, behavior: "smooth" }); };
    $("#btnCollect").onclick = () => collect(false);
    $("#headKpi").addEventListener("click", (e) => { if (e.target.closest("#hasset")) { try { localStorage.setItem(HIDE_KEY, hideAmt() ? "" : "1"); } catch (e2) { /* 무시 */ } renderHeader(); } });
    $("#headKpi").addEventListener("click", (e) => { // 머리글 알약 = 시세 수집
      const p = e.target.closest("#hpill"); if (!p || p.classList.contains("busy")) return;
      p.classList.add("busy"); toast("시세를 받는 중…");
      collect(false).then(() => toast("시세 수집 끝")).catch(() => toast("시세 수집 실패")).finally(() => { $("#hpill")?.classList.remove("busy"); });
    });
    $("#btnQuotes").onclick = () => collect(true);
    $("#autoRefresh").value = String(S.state.ui.auto_refresh_min || 0);
    $("#autoRefresh").onchange = (e) => { S.state.ui.auto_refresh_min = +e.target.value; setAuto(+e.target.value); save(false); };
    $("#holdTable").addEventListener("input", onHoldEdit);
    $("#holdTable").addEventListener("change", onHoldEdit);
    $("#holdTable").addEventListener("click", onHoldClick);
    $("#holdTable").addEventListener("keydown", (e) => { if (e.key === "Enter" && e.target.dataset.e) holdEditSave(); else if (e.key === "Escape" && S.holdEdit) { S.holdEdit = null; renderQuotes(); } });
    $("#holdSort").onchange = (e) => { S.state.ui.hold_sort = e.target.value; save(false); renderQuotes(); };
    $("#btnAdd").onclick = addHolding;
    $("#btnKrSum").onclick = () => { const v = parseDec($("#krSum").value); setKrSum(v > 0 ? v : 0); $("#krSum").blur(); toast(v > 0 ? "국내 주식 합계를 넣었어요" : "국내 주식 합계를 지웠어요"); };
    $("#krSum").addEventListener("keydown", (e) => e.key === "Enter" && $("#btnKrSum").click());
    $("#krToRec").onclick = (e) => { e.preventDefault(); showTab("analysis"); openAna("road"); $$("#recMode button").forEach((b) => b.classList.toggle("on", b.dataset.m === "kr")); renderMyRec(); $("#recCard").scrollIntoView({ behavior: "smooth" }); };
    $("#addTicker").addEventListener("input", sugUpdate);
    // 한글 입력 중 엔터: 글자 조합이 끝난 뒤에 고른다 (먼저 수량 칸으로 옮기면 마지막 글자가 그쪽에 들어간다)
    let enterAfterIme = false;
    const tickerEnter = () => { if (sug.list.length) sugPick(sug.list[Math.max(0, sug.i)]); else $("#addShares").focus(); };
    $("#addTicker").addEventListener("compositionend", () => { if (enterAfterIme) { enterAfterIme = false; setTimeout(() => { sugUpdate(); tickerEnter(); }, 0); } });
    $("#addTicker").addEventListener("keydown", (e) => {
      if (e.key === "Enter" && e.isComposing) { e.preventDefault(); enterAfterIme = true; return; }
      if (e.key === "Enter") { e.preventDefault(); enterAfterIme = false; setTimeout(tickerEnter, 0); return; }
      if (!sug.list.length) return;
      if (e.key === "ArrowDown" || e.key === "ArrowUp") { e.preventDefault(); sug.i = (sug.i + (e.key === "ArrowDown" ? 1 : -1) + sug.list.length) % sug.list.length; sugRender(); }
      else if (e.key === "Escape") { sug.list = []; sug.seq++; sugRender(); }
    });
    $("#addSug").addEventListener("mousedown", (e) => e.preventDefault()); // 고르기 전에 입력칸이 포커스를 잃지 않게
    $("#addSug").addEventListener("click", (e) => { const b = e.target.closest("[data-i]"); if (b) sugPick(sug.list[+b.dataset.i]); });
    $("#addTicker").addEventListener("blur", () => setTimeout(() => { sug.list = []; sug.seq++; sugRender(); }, 150));
    $("#addAvg").addEventListener("keydown", (e) => e.key === "Enter" && addHolding());
    $("#addShares").addEventListener("keydown", (e) => e.key === "Enter" && addHolding());
    $("#goalQuick").addEventListener("click", (e) => { const b = e.target.closest("button[data-set]"); if (!b) return; const el = $("#" + b.dataset.set); el.value = b.dataset.v; el.dispatchEvent(new Event("change")); });
    ["#goalAmount", "#goalDate", "#startDate", "#goalYears", "#monthly"].forEach((s) => { $(s).addEventListener("change", onGoalEdit); });
    // 접는 카드 (적중 기록판·다이어그램). 기본은 펼침, 접은 것만 이 기기에 기억
    const foldKey = "naeilo-fold", foldGet = () => { try { return JSON.parse(localStorage.getItem(foldKey) || "[]"); } catch (e) { return []; } };
    foldGet().forEach((id) => $("#" + id)?.classList.add("folded"));
    document.addEventListener("click", (e) => {
      const b = e.target.closest("[data-fold]"); if (!b) return; const card = b.closest(".card"); if (!card?.id) return;
      const on = card.classList.toggle("folded"); b.title = on ? "펼치기" : "접기";
      try { const ids = new Set(foldGet()); on ? ids.add(card.id) : ids.delete(card.id); localStorage.setItem(foldKey, JSON.stringify([...ids])); } catch (e2) { /* 무시 */ }
    });
    segClick("#recMode", renderMyRec);
    $("#krExport").onclick = (e) => { e.preventDefault(); krExport(); };
    $("#krImport").onclick = (e) => { e.preventDefault(); $("#krFile").click(); };
    $("#krFile").onchange = (e) => { const f = e.target.files[0]; e.target.value = ""; if (f) krImport(f); };
    segClick("#histRange", renderDash); segClick("#histFc", () => { try { localStorage.setItem("naeilo-histfc", $("#histFc .on").dataset.f); } catch (e) { /* 무시 */ } renderDash(); }); segClick("#progRange", () => { const H = history(); renderProgress(H, valuation().total, cashKrw()); }); segClick("#histMode", renderDash); segClick("#histCcy", renderDash); segClick("#histBasis", () => { try { localStorage.setItem("naeilo-basis", $("#histBasis .on").dataset.b); } catch (e) { /* 무시 */ } renderDash(); }); // 보기 옵션은 위 기간 버튼을 바꾸지 않는다
    segClick("#diaMode", renderDash); segClick("#hitMode", renderDash);
    $("#premBox").addEventListener("change", onPrem);
    segClick("#stockRange", renderStockPrices); segClick("#allocQ", renderAllocChart); segClick("#fxRange", renderFx); segClick("#divSpan", renderCash);
    document.addEventListener("click", (e) => { // 다른 분석 화면에 있는 곳이면 그 화면을 열고 간다
      const b = e.target.closest("[data-jump]"); if (!b) return; const el = $("#" + b.dataset.jump); if (!el) return;
      const pg = el.closest(".anapage"); if (pg) { if (!onTab("analysis")) showTab("analysis"); if (anaPage !== pg.dataset.ana) openAna(pg.dataset.ana); }
      if (el.tagName === "DETAILS") el.open = true;
      el.scrollIntoView({ behavior: "smooth", block: "start" });
    });
    $("#btnAlloc").onclick = () => { allocDirty = true; runAlloc(); };
    $("#btnStab").onclick = runStab; $("#stabRate").onchange = () => { lastStab = null; $("#stabOut").innerHTML = ""; $("#stabStatus").textContent = "계산 전 (몇 초 걸립니다)"; stabRestore(); }; stabRestore();
    $("#allocBoxes").addEventListener("click", (e) => { const b = e.target.closest("[data-ak]"); if (!b) return; S.state.alloc_pick = b.dataset.ak; save(false); renderAllocTable(); renderAllocChart(); });
    $("#allocMix").addEventListener("change", (e) => { const k = e.target.dataset.mix; if (!k) return; S.state.alloc_mix = { ...(S.state.alloc_mix || {}), [k]: e.target.value.trim() || ALLOC_DEF[k].mix }; save(false); allocDirty = true; runAlloc(); });
    $("#eventTable").addEventListener("input", onEventEdit);
    $("#evTiles").addEventListener("click", (e) => { const t = e.target.closest("[data-evt]"); if (!t) return; const ev = S.state.events[+t.dataset.evt]; ev.on = !ev.on; save(); renderEvents(); if (onTab("forecast")) runForecast(); });
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
    $("#fcOpt").addEventListener("click", (e) => { const b = e.target.closest("button[data-s]"); if (!b || b.disabled) return; S.state.model.scenario = b.dataset.s; save(false); viewChanged(true); }); // 누르면 아래 공통 설정으로 다시 계산
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
    $("#optManual").checked = !!S.state.ui.manual_price;
    $("#optManual").onchange = (e) => { S.state.ui.manual_price = e.target.checked; save(); renderAll(); };
    $("#optKrDirect").checked = !!S.state.ui.kr_direct;
    $("#optKrDirect").onchange = (e) => { S.state.ui.kr_direct = e.target.checked; save(false); renderQuotes(); };
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
    $$("#shareKind button").forEach((b) => (b.onclick = () => { $$("#shareKind button").forEach((o) => o.classList.toggle("on", o === b)); drawShare(); }));
    $("#shareSave").onclick = async () => { const a = document.createElement("a"); a.href = URL.createObjectURL(await shareBlob()); a.download = `naeilo-${today()}.png`; a.click(); };
    $("#shareSend").onclick = async () => { // 파일만 보낸다 (제목·글을 함께 넣으면 카카오톡·메시지 등은 글만 받고 이미지를 버림)
      const f = shareFile || new File([await shareBlob()], `naeilo-${today()}.png`, { type: "image/png" });
      if (navigator.canShare && navigator.canShare({ files: [f] })) { try { await navigator.share({ files: [f] }); } catch (e) { if (e.name !== "AbortError") $("#shareSave").click(); } } else $("#shareSave").click();
    };
    $("#btnPush").onclick = pushToggle;
    $("#btnPushTest").onclick = async () => { const sub = await pushSub(); if (sub) fetch(pushBase() + "/push/test", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ sub: sub.toJSON() }) }).then(() => toast("시험 알림을 보냈습니다")); };
    $("#xferCopy").onclick = async () => { try { await navigator.clipboard.writeText($("#xferLink").value); toast("복사했습니다"); } catch (e) { $("#xferLink").select(); } };
    $("#xferShare").onclick = async () => { if (navigator.share) { try { await navigator.share({ title: "naeilo 입력값", url: $("#xferLink").value }); } catch (e) { /* 취소 */ } } else { $("#xferCopy").click(); } };
    $("#fileImport").onchange = async (e) => {
      const f = e.target.files[0]; if (!f) return;
      try { const j = JSON.parse(await f.text()); bvMerge(j.beyora); delete j.beyora; S.state = normalize(j); save(); renderAll(); toast("불러왔습니다"); } catch (err) { alert("파일을 읽지 못했습니다: " + err.message); }
    };
    let rz; window.addEventListener("resize", () => { clearTimeout(rz); rz = setTimeout(() => { const t = $("#tabs .on").dataset.tab; if (t === "dash") renderDash(); if (t === "stocks") { renderStockPrices(); if (lastForecast) renderForecast(); } if (t === "analysis") renderAnalysisTab(); }, 200); });
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
    const st = S.state, j = { v: 1, h: st.holdings.filter((h) => !h.sample && Number(h.shares) > 0).map((h) => [h.ticker, Number(h.shares), Number(h.avg_cost) || 0]), g: [st.goal.amount, st.goal.date, st.goal.start_date, Number(st.goal.monthly_contribution) || 0], m: [st.model.scenario, st.model.trust], l: (st.lots || []).slice(-30).map((x) => [x.d, Object.entries(x.h)]), k: (st.kr_log || []).slice(-30).map((x) => [x.d, x.v]) }; // k: 국내 주식 합계 기록
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
      st.kr_log = Model.krNorm((j.k || []).map(([d, v]) => ({ d, v }))); krHold(); synthKR();
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
    if ($("#shareKind .on")?.dataset.k === "cells" && window.NaeiloShareCard) return drawCellsShare();
    const cv = $("#shareCv"); if (cv.height !== 1080) { cv.width = 1080; cv.height = 1080; }
    const x = cv.getContext("2d"), W = 1080, hide = $("#shareHide").checked;
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
    // 과거 3년 + 미래(목표일까지) 단순 그래프: 실선 = 지난 3년, 점선·띠 = 전망 중앙값·25~75%, 가로 점선 = 목표
    const k0 = Math.max(0, k - 756), px0 = H.dates.slice(k0), py0 = H.total.slice(k0);
    const scS = basisScen($("#histBasis .on")?.dataset.b || "model"), Fc = total > 0 ? fcReady(scS) : null, R = Fc && !Fc.err ? Fc.R : null;
    if (!Fc && total > 0) forecastLater(scS, () => { if ($("#shareBox").style.display !== "none") drawShare(); });
    const fd = R ? Fc.model.monthDates : [], B = R ? R.bands : null;
    if (px0.length > 2) {
      const X0 = 110, Y0 = 730, CW = W - 220, CH = 180, t0 = Date.parse(px0[0]), t1 = Date.parse(fd.length ? fd[fd.length - 1] : g.date > today() ? g.date : today());
      const vals = [...py0, g.amount, ...(B ? [...B.p25, ...B.p75] : [])].filter((v) => v > 0), lo = Math.min(...vals) * 0.95, hi = Math.max(...vals) * 1.02;
      const X = (d) => X0 + ((Date.parse(d) - t0) / (t1 - t0 || 1)) * CW, Y = (v) => Y0 + CH - ((v - lo) / (hi - lo || 1)) * CH;
      const path = (xs, ys) => { x.beginPath(); xs.forEach((d, i) => (i ? x.lineTo(X(d), Y(ys[i])) : x.moveTo(X(d), Y(ys[i])))); };
      if (B) { x.fillStyle = "rgba(47,111,237,.13)"; x.beginPath(); fd.forEach((d, i) => (i ? x.lineTo(X(d), Y(B.p75[i])) : x.moveTo(X(d), Y(B.p75[i])))); for (let i = fd.length - 1; i >= 0; i--) x.lineTo(X(fd[i]), Y(B.p25[i])); x.fill(); }
      x.lineWidth = 3; x.strokeStyle = "#aab2c0"; x.setLineDash([10, 8]); x.beginPath(); x.moveTo(X0, Y(g.amount)); x.lineTo(X0 + CW, Y(g.amount)); x.stroke();
      if (B) { x.strokeStyle = "#2f6fed"; x.lineWidth = 4; x.setLineDash([4, 8]); path(fd, B.p50); x.stroke(); }
      x.setLineDash([]); x.strokeStyle = "#2f6fed"; x.lineWidth = 5; x.lineJoin = "round"; path(px0, py0); x.stroke();
      const tx = X(px0[px0.length - 1]), ty = Y(py0[py0.length - 1]);
      x.fillStyle = "#2f6fed"; x.beginPath(); x.arc(tx, ty, 11, 0, 7); x.fill();
      x.font = `700 30px ${F}`; x.textAlign = tx > X0 + CW * 0.7 ? "right" : "left"; x.fillText(`오늘 ${pct(prog, 1)}`, tx + (x.textAlign === "right" ? -18 : 18), ty - 18);
      x.fillStyle = mu; x.font = `500 26px ${F}`; x.textAlign = "right"; x.fillText(hide ? "목표 100%" : `목표 ${krw(g.amount)}원`, X0 + CW, Y(g.amount) - 12);
      x.font = `400 24px ${F}`; x.textAlign = "left"; x.fillText("3년 전", X0, Y0 + CH + 34); x.textAlign = "right"; x.fillText(B ? `${String(fd[fd.length - 1]).slice(0, 7)} 전망` : "오늘", X0 + CW, Y0 + CH + 34); x.textAlign = "left";
    }
    x.fillStyle = mu; x.font = `400 28px ${F}`; x.textAlign = "center"; x.fillText("See Tomorrow, Today. · naeilo.com", W / 2, W - 95); x.textAlign = "left";
    shareFile = null; cv.toBlob((b) => { if (b) shareFile = new File([b], `naeilo-${today()}.png`, { type: "image/png" }); }, "image/png");
  }
  // 1000칸 공유 카드 (앱과 같은 모양, web/sharecard.js): 1칸 = 목표의 1/1000. 진한 칸 = 내가 넣은 돈(평단 기준), 연한 칸 = 시장이 준 칸
  function drawCellsShare() {
    const cv = $("#shareCv"), { total } = valuation(), g = S.state.goal, unit = (g.amount || 0) / 1000, hide = $("#shareHide").checked;
    const cl = (v) => (unit > 0 ? Math.max(0, Math.min(1000, Math.floor(v / unit))) : 0), n = cl(total), cb = costBasis();
    const mine = cb != null ? Math.min(n, cl(cb)) : n;
    NaeiloShareCard.draw(cv, { filled: n, mine, market: n - mine, split: cb != null, hide, kicker: "목표까지 가는 중", amountLine: `목표 ${krw(g.amount)}원 중 ${krw(total)}원 모았어요.`, date: today().slice(5).replace(/^0?(\d+)-0?(\d+)$/, "$1월 $2일") });
    shareFile = null; cv.toBlob((b) => { if (b) shareFile = new File([b], `naeilo-1000-${today()}.png`, { type: "image/png" }); }, "image/png");
  }
  // 공유할 파일은 그린 직후 미리 만들어 둔다: 버튼을 누른 뒤 기다리면 iOS 가 사용자 동작을 잃어 앱으로 파일이 안 넘어간다
  let shareFile = null;
  const shareBlob = () => new Promise((r) => $("#shareCv").toBlob(r, "image/png"));

  async function init() {
    try { await reload(); }
    catch (e) { document.body.innerHTML = `<div class="card" style="margin:40px auto;max-width:640px"><h2>데이터를 불러오지 못했습니다</h2><p>내 PC에서 쓸 때는 <b>실행 파일</b>(Windows: <code>실행-Windows.bat</code>, Mac: <code>실행-Mac.command</code>)로 열어야 합니다. 웹 버전은 GitHub Actions의 첫 수집이 끝난 뒤 열립니다.</p><p class="muted small">${esc(e.message)}</p></div>`; return; }
    if (S.purged || (!S.state.sample && !(S.state.lots || []).length && S.state.holdings.some((h) => Number(h.shares) > 0))) save(false); // 진행 기록 첫 줄
    try { const bs = localStorage.getItem("naeilo-basis"); if (bs && $(`#histBasis button[data-b="${bs}"]`)) $$("#histBasis button").forEach((b) => b.classList.toggle("on", b.dataset.b === bs)); } catch (e) { /* 무시 */ } // 평가액 추이 미래 기준은 리로드해도 유지
    try { const hf = localStorage.getItem("naeilo-histfc"); if (hf) $$("#histFc button").forEach((b) => b.classList.toggle("on", b.dataset.f === hf)); } catch (e) { /* 무시 */ } // 예보 겹치기도 유지
    if (window.themeUI) themeUI($("#themeBox"));
    bind(); renderAll(); marFetch(); syncPull(); renderEsync(); allocWarm(2500);
    document.addEventListener("visibilitychange", () => { if (document.visibilityState === "visible") syncPull(); });
    setAuto(S.state.ui.auto_refresh_min || 0);
    let tab = "dash"; try { tab = localStorage.getItem("tab") || "dash"; } catch (e) { /* 무시 */ }
    showTab(tab);
    xferCheck();
    if ("serviceWorker" in navigator && location.protocol === "https:") navigator.serviceWorker.register("sw.js").then(() => { pushRender(); pushSync(); }).catch(() => {});
    else pushRender();
    if (MODE === "static") {
      $("#btnQuotes").style.display = "none";
      $("#modeNote").innerHTML = `시세는 평일 30분마다 자동 수집, '시세 수집'을 누르면 바로 받음. 입력값은 <b>이 브라우저에만</b> 저장.`;
      $("#modeNote").style.display = "block";
      logLine(`웹 데이터 수집 시각: ${S.dataUpdated ? new Date(S.dataUpdated).toLocaleString() : "-"}`);
      if (S.firstVisit) logLine("처음 여셨습니다. 보유 종목의 수량을 넣어 주세요. 저장해 둔 파일이 있으면 설정 → '입력값 저장·불러오기'.", false);
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
