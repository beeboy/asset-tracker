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
  const KINDS = ["실적", "규제", "보호예수 해제", "소송", "신제품·행사", "지수 편입·제외", "기타"];
  const SESS = { pre: "프리", regular: "정규", post: "애프터", close: "종가", manual: "수동" };

  let S = { state: null, prices: {}, quotes: {} };
  // 실행 방식: "local" = 내 PC 의 server.py, "static" = GitHub Pages 같은 정적 사이트 (입력은 이 브라우저에 저장)
  let MODE = "local";
  const LS_KEY = "asset-tracker-state";
  const GH = (() => {
    const m = location.hostname.match(/^([^.]+)\.github\.io$/), repo = location.pathname.split("/").filter(Boolean)[0];
    return m && repo ? { owner: m[1], repo, actions: `https://github.com/${m[1]}/${repo}/actions/workflows/collect.yml` } : null;
  })();
  let saveTimer = null, autoTimer = null, lastForecast = null, fcDirty = true;

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
    st.model = Object.assign({}, DEFAULT_MODEL, st.model || {});
    st.ui = Object.assign({ auto_refresh_min: 0, manual_price: false }, st.ui || {});
    return st;
  }
  function save(dirtyForecast = true) {
    if (dirtyForecast) fcDirty = true;
    clearTimeout(saveTimer);
    saveTimer = setTimeout(async () => {
      if (MODE === "static") {
        try { localStorage.setItem(LS_KEY, JSON.stringify(S.state)); $("#footer").textContent = "이 브라우저에 저장됨 " + new Date().toLocaleTimeString() + " · 다른 기기에서 쓰려면 ③ 설정의 내보내기/불러오기"; }
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
    const total = [], index = []; let idx = 1, prevTotal = null, prevSet = null;
    for (const d of dates) {
      for (const ccy of new Set(hs.map((h) => ccyOf(h.ticker)))) {
        const f = getFx(ccy); if (typeof f === "function") { curFx[ccy] = 1; continue; }
        if (f.m.has(d)) f.last = f.m.get(d); curFx[ccy] = f.last ?? (S.prices[fxOf(ccy)]?.close[0] || fxNow(ccy));
      }
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
    return { dates, total, each, index };
  }

  // ------------------------------------------------------------ 머리글
  function renderHeader() {
    const { total } = valuation(), g = S.state.goal, fx = fxNow("USD");
    const prog = g.amount ? total / g.amount : 0;
    const latest = Object.values(S.quotes).reduce((m, q) => Math.max(m, q.fetched || 0), 0);
    $("#headKpi").innerHTML = `<span>평가액 <b>${krw(total)}원</b></span><span>목표 대비 <b>${pct(prog)}</b></span><span>원/달러 <b>${nf(fx, 1)}</b></span>
      <span class="muted">최근 수집 ${latest ? new Date(latest * 1000).toLocaleString() : "-"}</span>`;
  }

  // ------------------------------------------------------------ ① 시세·종목
  function logLine(msg, ok = true, html = false) {
    const box = $("#collectLog"), div = document.createElement("div");
    div.innerHTML = `<span class="t">${new Date().toLocaleTimeString()}</span> ${html ? msg : esc(msg)}`;
    if (!ok) div.className = "err";
    box.appendChild(div); box.scrollTop = box.scrollHeight;
  }
  function symbolsToCollect(list) {
    const ts = list || S.state.holdings.map((h) => h.ticker);
    const fx = new Set(["KRW=X"]); ts.forEach((t) => { const f = fxOf(ccyOf(t)); if (f) fx.add(f); });
    return [...new Set([...ts, ...fx])];
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
    const btns = [$("#btnCollect"), $("#btnCollectTop")]; btns.forEach((b) => (b.disabled = true));
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
        fcDirty = true; renderAll();
        const still = add.filter((t) => !S.prices[t]);
        logLine(`수집 완료 (${new Date(idx.updated).toLocaleString()}).` + (still.length ? ` 시세를 찾지 못한 종목: ${still.join(", ")} (티커 확인)` : ""), !still.length);
        return true;
      }
      logLine("수집이 오래 걸립니다. 잠시 뒤 '최신 데이터 불러오기'를 눌러 주세요." + ghLink("진행 상황 보기"), false, true);
    } catch (e) { logLine("GitHub 수집 실행 실패: " + esc(e.message) + ". ③ 설정 탭의 GitHub 연결을 확인하세요.", false, true); }
    finally { ghBusy = false; btns.forEach((b) => (b.disabled = false)); }
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
    const btns = [$("#btnCollect"), $("#btnCollectTop")]; btns.forEach((b) => b && (b.disabled = true));
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
    fcDirty = true; renderAll();
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
    const btns = [$("#btnCollect"), $("#btnCollectTop")]; btns.forEach((b) => (b.disabled = true));
    try {
      await reload(); fcDirty = true; renderAll();
      logLine(`최신 데이터를 불러왔습니다 (서버 수집 ${S.dataUpdated ? new Date(S.dataUpdated).toLocaleString() : "-"}).`);
    } catch (e) { logLine("불러오기 실패: " + e.message, false); }
    const miss = missingTickers();
    if (miss.length) browserCollect(miss);
    btns.forEach((b) => (b.disabled = false));
  }
  async function collect(quotesOnly, list) {
    if (MODE === "static") return collectStatic(!quotesOnly);
    const btns = [$("#btnCollect"), $("#btnQuotes"), $("#btnCollectTop")]; btns.forEach((b) => (b.disabled = true));
    const syms = symbolsToCollect(list);
    logLine(`${quotesOnly ? "현재가" : "일봉+현재가"} 수집 시작: ${syms.join(", ")}`);
    try {
      const r = await api("/api/collect", { symbols: syms, years: Math.max(3, Number(S.state.model.history_years) || 3), quotes_only: quotesOnly });
      r.log.forEach((l) => logLine(l.msg, l.ok));
      await reload();
      // 처음 추가한 종목의 통화가 원화가 아니면 그 환율도 받는다
      const missingFx = symbolsToCollect(list).filter((s) => !syms.includes(s));
      if (missingFx.length) { const r2 = await api("/api/collect", { symbols: missingFx, years: 3, quotes_only: false }); r2.log.forEach((l) => logLine(l.msg, l.ok)); await reload(); }
      fcDirty = true; renderAll();
    } catch (e) { logLine("수집 실패: " + e.message + " (인터넷 연결 또는 프로그램 창 확인)", false); }
    btns.forEach((b) => (b.disabled = false));
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
        <td><input data-f="shares" type="number" step="any" placeholder="수량" value="${r.h.shares ? r.h.shares : ""}"></td>
        <td><input data-f="avg_cost" type="number" step="any" placeholder="선택" value="${r.h.avg_cost ?? ""}"></td>
        <td>${p.v != null ? nf(p.v, 2) + " <span class='muted small'>" + r.ccy + "</span>" : "-"}${tag}</td>
        ${adv ? `<td><input data-f="price" type="number" step="any" placeholder="비우면 자동" value="${r.h.price ?? ""}">${warn}</td>` : ""}
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
    else { const v = e.target.value.trim(); h[f] = v === "" ? null : Number(v); }
    save(f !== "note"); renderHeader();
    if (e.type === "change") renderQuotes();
  }
  async function addHolding() {
    const t = $("#addTicker").value.trim().toUpperCase(), sh = parseNum($("#addShares").value), avg = parseNum($("#addAvg").value);
    if (!t) return toast("티커를 넣어 주세요");
    if (!(sh > 0)) return toast("수량을 넣어 주세요");
    const ex = S.state.holdings.find((h) => h.ticker === t);
    if (ex) { ex.shares = (Number(ex.shares) || 0) + sh; toast(`${t} 수량을 더했습니다`); }
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

  // ------------------------------------------------------------ ② 목표·현황
  function yearsBetween(a, b) { return (Date.parse(b) - Date.parse(a)) / (365.25 * 86400e3); }
  function renderGoalInputs() {
    const g = S.state.goal;
    $("#goalAmount").value = nf(g.amount); $("#goalAmountKr").textContent = `= ${krw(g.amount)}원`;
    $("#startDate").value = g.start_date || today(); $("#goalDate").value = g.date;
    $("#goalYears").value = Math.round(yearsBetween(today(), g.date) * 10) / 10;
    $("#monthly").value = nf(g.monthly_contribution || 0);
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
    const future = rsel === "future", n = future ? 780 : +rsel;
    const k0 = Math.max(0, H.dates.length - 1 - n);
    // 간격: 주·월은 그 기간의 마지막 거래일 값
    let ix = []; for (let i = k0; i < H.dates.length; i++) ix.push(i);
    if (step !== "d") {
      const key = (d) => { if (step === "m") return d.slice(0, 7); const t = new Date(d + "T00:00:00Z"); t.setUTCDate(t.getUTCDate() - ((t.getUTCDay() + 6) % 7)); return t.toISOString().slice(0, 10); };
      ix = ix.filter((i, j) => j === ix.length - 1 || key(H.dates[i]) !== key(H.dates[ix[j + 1]]));
    }
    const x = ix.map((i) => H.dates[i]), tick = Object.keys(H.each);
    let series = [], bands = [];
    if (mode === "total") series = [{ name: "합계", y: ix.map((i) => H.total[i]), color: "var(--c1)", width: 2 }];
    else if (mode === "each") series = tick.map((t, j) => ({ name: t, y: ix.map((i) => H.each[t][i]), color: C[j % C.length] }));
    else { // 누적: 종목 값을 쌓아 올린 띠
      let lo = ix.map(() => 0);
      tick.forEach((t, j) => { const hi = ix.map((i, k) => lo[k] + (H.each[t][i] || 0)); bands.push({ lo, hi, color: C[j % C.length], opacity: 0.55, name: t }); lo = hi; });
      series = [{ y: lo, color: "var(--fg)", width: 1 }];
    }
    const opt = { x, series, bands, yfmt: krwAxis, height: 320, hlines: [], vlines: [], ymin: mode === "total" ? undefined : 0 };
    const maxV = Math.max(...ix.map((i) => H.total[i]));
    if (future) {
      const V0 = total, last = x[x.length - 1] || today();
      opt.vlines.push({ x: today(), label: "오늘" });
      opt.hlines.push({ y: g.amount, label: "목표 " + krw(g.amount) });
      const fdates = lastForecast && !fcDirty ? lastForecast.b.model.monthDates : null;
      if (fdates) {
        const R = lastForecast.withEv;
        opt.bands.push({ x: fdates, lo: R.bands.p5, hi: R.bands.p95, color: "var(--band)", opacity: 0.13, name: "전망 5~95%" }, { x: fdates, lo: R.bands.p25, hi: R.bands.p75, color: "var(--band)", opacity: 0.25, name: "전망 25~75%" });
        opt.series.push({ name: "전망 중앙값", x: fdates, y: R.bands.p50, color: "var(--c1)", width: 2, dash: "2 2" });
      }
      const md = []; for (let k = 0; k <= 36 && Model.addMonths(today(), k) <= g.date; k++) md.push(Model.addMonths(today(), k));
      if (md[md.length - 1] !== g.date) md.push(g.date);
      if (V0 > 0) opt.series.push({ name: "필요 경로", x: [last, ...md], y: [H.total[H.total.length - 1], ...md.map((d) => V0 * (g.amount / V0) ** (yearsBetween(today(), d) / Math.max(0.01, yearsBetween(today(), g.date))))], color: "var(--accent2)", dash: "5 4", width: 1.3 });
      $("#histNote").textContent = fdates ? "오른쪽은 ② 분석·전략의 3년 전망(환율·사건 포함) 결과입니다." : "② 분석·전략 탭을 열면 전망 띠가 함께 그려집니다.";
    } else {
      if (mode !== "each" && g.amount <= maxV * 1.05) opt.hlines.push({ y: g.amount, label: "목표" });
      $("#histNote").textContent = "현재 보유 수량을 과거에 그대로 적용한 값입니다(매매 이력 미반영). 원화 환산은 그날 환율을 씁니다. 늦게 상장한 종목은 상장일부터 합계에 들어갑니다.";
    }
    Charts.lineChart($("#histChart"), opt);

    // 종목별 가격 (같은 기간)
    const host = $("#dashStocks"); host.innerHTML = "";
    const from = H.dates[k0];
    S.state.holdings.filter((h) => S.prices[h.ticker]).forEach((h, j) => {
      const p = S.prices[h.ticker], i0 = Math.max(0, p.dates.findIndex((d) => d >= from)), xs = p.dates.slice(i0), ys = p.close.slice(i0);
      const ch = ys.length > 1 ? ys[ys.length - 1] / ys[0] - 1 : null, box = document.createElement("div");
      box.innerHTML = `<h3>${esc(h.ticker)} <span class="muted small">${nf(ys[ys.length - 1], 2)} ${ccyOf(h.ticker)} · 기간 <span class="${cls(ch)}">${spct(ch)}</span></span></h3><div class="chartbox"></div>`;
      host.appendChild(box);
      Charts.lineChart(box.querySelector(".chartbox"), { x: xs, height: 170, legend: false, yfmt: (v) => nf(v, v < 10 ? 2 : 0), series: [{ name: h.ticker, y: ys, color: C[j % C.length], width: 1.6 }] });
    });

    const periods = [["1일", 1], ["1주", 5], ["1개월", 21], ["3개월", 63], ["6개월", 126], ["1년", 252], ["3년", 756]];
    $("#periodTable").innerHTML = `<tr>${periods.map((p) => `<th>${p[0]}</th>`).join("")}</tr><tr>${periods.map((p) => { const v = ret(p[1]); return `<td class="${cls(v)}">${spct(v)}</td>`; }).join("")}</tr>`;
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

  // ------------------------------------------------------------ ③ 사건
  function renderEvents() {
    const opts = [...S.state.holdings.map((h) => h.ticker), "ALL", "FX"];
    const lab = { ALL: "전체", FX: "환율" };
    const head = `<tr><th>사용</th><th>날짜</th><th class="l">대상</th><th class="l">종류</th><th class="l">반복</th><th>발생 확률 %</th><th>평균 영향 %</th><th>불확실성 ±%</th><th>변동성 배수</th><th>지속 (거래일)</th><th class="l">메모</th><th></th></tr>`;
    const body = S.state.events.map((e, i) => `<tr data-i="${i}">
      <td><input type="checkbox" data-f="on" ${e.on ? "checked" : ""}></td>
      <td><input type="date" class="date" data-f="date" value="${e.date || ""}"></td>
      <td class="l"><select data-f="target">${[...new Set([...opts, e.target])].map((o) => `<option value="${esc(o)}" ${o === e.target ? "selected" : ""}>${lab[o] || esc(o)}</option>`).join("")}</select></td>
      <td class="l"><select data-f="kind">${[...new Set([...KINDS, e.kind])].map((o) => `<option ${o === e.kind ? "selected" : ""}>${esc(o)}</option>`).join("")}</select></td>
      <td class="l"><select data-f="repeat"><option value="none" ${e.repeat !== "quarterly" ? "selected" : ""}>한 번</option><option value="quarterly" ${e.repeat === "quarterly" ? "selected" : ""}>분기</option></select></td>
      <td><input type="number" data-f="prob" min="0" max="100" value="${e.prob ?? 100}" style="width:5em"></td>
      <td><input type="number" data-f="mean" step="any" value="${e.mean ?? 0}" style="width:5em"></td>
      <td><input type="number" data-f="sd" step="any" min="0" value="${e.sd ?? 0}" style="width:5em"></td>
      <td><input type="number" data-f="vol_mult" step="0.1" min="0.1" value="${e.vol_mult ?? 1}" style="width:5em"></td>
      <td><input type="number" data-f="vol_days" step="1" min="0" value="${e.vol_days ?? 0}" style="width:5em"></td>
      <td class="l"><input class="wide" data-f="note" value="${esc(e.note)}"></td>
      <td><button class="danger" data-del="${i}">삭제</button></td></tr>`).join("");
    $("#eventTable").innerHTML = head + body;
    renderSchedule();
  }
  function renderSchedule() {
    try {
      const m = buildModelNow();
      if (!m) { $("#eventSchedule").textContent = "종목 시세가 있어야 일정을 펼칠 수 있습니다."; return; }
      const list = m.model.eventList.sort((a, b) => (a.date < b.date ? -1 : 1));
      const lab = { ALL: "전체", FX: "환율" };
      $("#eventSchedule").innerHTML = list.length ? `<table class="grid"><tr><th class="l">거래일</th><th class="l">대상</th><th class="l">종류</th><th>확률</th><th>평균</th><th>±</th></tr>${list.map((x) => `<tr><td class="l">${x.date}</td><td class="l">${lab[x.event.target] || esc(x.event.target)}</td><td class="l">${esc(x.event.kind)}</td><td>${x.event.prob}%</td><td>${x.event.mean}%</td><td>${x.event.sd}%</td></tr>`).join("")}</table>` : "켜진 사건이 없거나 모두 지난 날짜입니다.";
    } catch (e) { $("#eventSchedule").textContent = e.message; }
  }
  function onEventEdit(e) {
    const tr = e.target.closest("tr[data-i]"); if (!tr) return;
    const ev = S.state.events[+tr.dataset.i], f = e.target.dataset.f; if (!f) return;
    if (f === "on") ev.on = e.target.checked;
    else if (["prob", "mean", "sd", "vol_mult", "vol_days"].includes(f)) ev[f] = e.target.value === "" ? 0 : Number(e.target.value);
    else ev[f] = e.target.value;
    save(f !== "note");
    if (e.type === "change") renderSchedule();
  }

  // ------------------------------------------------------------ ④ 전망
  function buildModelNow() {
    const g = S.state.goal, start = today();
    if (g.date <= start) throw new Error("목표일이 오늘 이후여야 합니다.");
    const { rows } = valuation();
    const holdings = rows.filter((r) => r.valueKrw > 0).map((r) => ({ ticker: r.h.ticker, shares: r.sh, price0: r.p.v, ccy: r.ccy, valueKrw: r.valueKrw }));
    if (!holdings.length) return null;
    const series = {};
    for (const k in S.prices) series[k] = { dates: S.prices[k].dates, adj: S.prices[k].adj };
    const model = Model.buildModel({ holdings, series, fxOf, settings: S.state.model, events: S.state.events, startDate: start, goalDate: g.date });
    return { holdings, model };
  }
  async function runForecast() {
    const st = $("#fcStatus"), btn = $("#btnForecast");
    btn.disabled = true; st.textContent = "계산 중...";
    await new Promise((r) => setTimeout(r, 30));
    try {
      const t0 = performance.now(), b = buildModelNow();
      if (!b) { st.textContent = "평가액이 있는 종목이 없습니다."; btn.disabled = false; return; }
      const m = S.state.model, g = S.state.goal;
      const common = { holdings: b.holdings, scenario: m.scenario, nPaths: Number(m.n_paths), seed: Number(m.seed) || 1, goal: g.amount,
        monthly: Number(g.monthly_contribution) || 0, rebalance: !!m.rebalance_yearly, dof: m.t_dof, fxOf };
      const withEv = Model.simulate(b.model, { ...common, withEvents: true });
      const hasEv = b.model.eventList.length > 0;
      const noEv = hasEv ? Model.simulate(b.model, { ...common, withEvents: false }) : withEv;
      lastForecast = { b, withEv, noEv, hasEv, at: new Date(), ms: performance.now() - t0 };
      fcDirty = false;
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
    $("#fcWarn").innerHTML = manual.length && lastHist ? `오늘 평가액(${krw(R.V0)}원)이 시세 기준(${krw(lastHist)}원)과 ${spct(R.V0 / lastHist - 1, 0)} 다릅니다. <b>${manual.map((h) => esc(h.ticker)).join(", ")}</b>에 현재가를 직접 넣었기 때문입니다. 매수 단가였다면 ④ 시세 수집에서 그 값을 지우고 '평균 매수가' 칸으로 옮겨 주세요.` : "";
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
      $("#fcEvents").innerHTML = `<table class="grid"><tr><th class="l"></th><th>사건 제외</th><th>사건 반영</th></tr>${rows.map((r) => `<tr><td class="l">${r[0]}</td><td>${r[1]}</td><td><b>${r[2]}</b></td></tr>`).join("")}</table>
        <p class="muted small">같은 난수로 사건만 빼고 다시 계산한 비교입니다. 켜진 사건 ${md.eventList.length}건 (반복 포함).</p>`;
    } else $("#fcEvents").innerHTML = `<p class="muted">켜진 사건이 없습니다. '기업 사건'에서 추가하세요.</p>`;

    // 종목별
    const host = $("#fcStocks"); host.innerHTML = "";
    R.stocks.forEach((s, i) => {
      const box = document.createElement("div"), h = b.holdings[i];
      box.innerHTML = `<h3>${esc(s.ticker)} <span class="muted small">현재 ${nf(h.price0, 2)} ${h.ccy} · 목표일 중앙값 ${nf(s.bands.p50[s.bands.p50.length - 1], 2)} · 오를 확률 ${pct(s.p_up, 0)}</span></h3><div class="chartbox"></div>`;
      host.appendChild(box);
      const p = S.prices[s.ticker], kk = p ? Math.max(0, p.dates.length - 253) : 0;
      Charts.lineChart(box.querySelector(".chartbox"), {
        x: fx, height: 200, legend: false, log: true, yfmt: (v) => nf(v, v < 10 ? 2 : 0),
        bands: [{ lo: s.bands.p5, hi: s.bands.p95, color: C[i % C.length], opacity: 0.13, name: "5~95%" }, { lo: s.bands.p25, hi: s.bands.p75, color: C[i % C.length], opacity: 0.25, name: "25~75%" }],
        series: [...(p ? [{ name: "과거", x: [...p.dates.slice(kk), md.startDate], y: [...p.close.slice(kk), h.price0], color: "var(--fg)", width: 1.2 }] : []), { name: "중앙값", y: s.bands.p50, color: C[i % C.length], width: 2 }],
        markers: md.eventList.filter((e) => e.event.target === s.ticker || e.event.target === "ALL").map((e) => ({ x: e.date, label: `${e.date} ${e.event.kind}` })),
      });
    });

    // 모형 값
    const scen = m.scenario;
    $("#fcParams").innerHTML = `<tr><th class="l">요인</th><th>비중</th><th>표본 (일)</th><th>과거 변동성</th><th>모형 변동성</th><th>사건 제외 평소 변동성</th><th>과거 평균 (연)</th><th>사전값 비중</th><th>적용 기대수익 (${$("#scenario").selectedOptions[0].textContent.split(" ")[0]})</th></tr>` +
      md.factors.map((f, i) => `<tr><td class="l">${f.kind === "fx" ? "환율 " + f.key : esc(f.key)}${f.cash ? ' <span class="tag">현금성</span>' : ""}</td>
        <td>${f.kind === "asset" ? pct(b.holdings[i].valueKrw / R.V0) : "-"}</td><td>${f.n}</td><td>${pct(f.volRaw)}</td><td>${pct(f.vol)}</td><td>${pct(f.volDiff)}</td>
        <td>${pct(f.muHist)}</td><td>${f.shrink == null ? "-" : pct(1 - f.shrink, 0)}</td><td><b>${pct(f.mu[scen])}</b></td></tr>`).join("") +
      `<tr><td class="l muted" colspan="9">상관행렬 ${md.corrShrink > 0 ? `(양의 정부호 보정 ${pct(md.corrShrink, 0)})` : ""}: ${md.factors.map((f, i) => md.factors.slice(0, i).map((g2, j) => `${f.key}–${g2.key} ${md.corr[i][j].toFixed(2)}`).join(", ")).filter(Boolean).join(" · ")}</td></tr>`;
    renderStrategy();
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
    tips.push(`목표 확률 <b>${pct(R.p_goal, 0)}</b> (${$("#scenario").selectedOptions[0].textContent.split(" ")[0]} 시나리오). 목표에 필요한 연수익률 <b>${pct(req)}</b>, 전망 중앙값의 연수익률 <b>${pct(medC)}</b>.`);
    if (R.p_goal < 0.5 && R.req50 != null) tips.push(`지금 비중 그대로 확률 50%를 맞추려면 매월 약 <b>${krw(R.req50)}원</b>을 더 넣어야 합니다 (월 적립은 ① 관찰 대시보드에서 입력).`);
    if (risky[top] > 0.45) tips.push(`<b>${esc(b.holdings[top].ticker)}</b> 한 종목이 ${pct(w[top], 0)}입니다. 집중도가 높아 하위 5% 결과가 ${krw(R.terminal.p5)}원까지 내려갑니다. '비중안 비교'에서 줄였을 때를 확인해 보세요.`);
    if (cashW < 0.03) tips.push(`현금성 자산이 ${pct(cashW, 1)}입니다. 하락장에서 살 여력과 심리적 완충을 위해 3~5%를 권합니다.`);
    tips.push(`최대 낙폭 중앙값 ${pct(R.mdd_median, 0)}: 목표일까지 가는 동안 이 정도 하락은 흔하다는 뜻입니다.`);
    $("#aiPrompt").textContent = aiPrompt();
    $("#stratSummary").innerHTML = `<ul class="small">${tips.map((t) => `<li>${t}</li>`).join("")}</ul>`;

    const total = V0;
    $("#stratCards").innerHTML = b.holdings.map((h, i) => {
      const f = md.factors[i], p = S.prices[h.ticker], ind = p ? Model.indicators(p.dates, p.adj) : null, sg = ind?.sig, st = R.stocks[i];
      const ev = soon(h.ticker), lock = ev.find((e) => /보호예수/.test(e.event.kind));
      let act, klass, why = [];
      if (f.cash) { act = "유지 (현금 완충)"; klass = "cash"; why.push(`비중 ${pct(w[i], 1)}, 연 ${pct(f.mu.base)} 수준의 단기 국채형`); }
      else if (f.n < 252) { act = "보유, 추가 매수 보류"; klass = "wait"; why.push(`상장 후 ${f.n}거래일로 이력이 짧아 변동성(${pct(f.vol, 0)}) 추정이 불확실`); if (lock) why.push(`${lock.date} ${esc(lock.event.kind)} 예정: 물량 출회로 단기 하락 가능, 이후 재판단`); }
      else if (w[i] > 0.45) {
        const tgt = 0.45, sell = Math.ceil(((w[i] - tgt) * total) / (h.valueKrw / h.shares));
        act = `비중 축소 검토 (→ ${pct(tgt, 0)})`; klass = "trim";
        why.push(`비중 ${pct(w[i], 0)}로 한 종목 집중. 약 ${nf(sell)}주를 6개월에 나눠 지수(QQQ 등)로 옮기면 ${pct(tgt, 0)}`);
        why.push("양도세가 있으면 연도를 나눠 매도 (해외주식 연 250만원 공제)");
      } else if (sg && sg.trend === "하락 추세") { act = "추가 매수 보류, 관찰"; klass = "wait"; }
      else if (sg && /상승/.test(sg.trend) && w[i] > 0.25) { act = "보유 (25~30% 넘지 않게)"; klass = "hold"; }
      else { act = "보유"; klass = "hold"; }
      if (sg && !f.cash) why.push(`추세: ${sg.trend} (칼만 기울기 연 ${spct(sg.slope_ann, 0)}, z ${sg.slope_z.toFixed(1)}), 고점 대비 ${pct(sg.drawdown, 0)}`);
      if (!f.cash) why.push(`목표일 가격 중앙값 ${nf(st.bands.p50[st.bands.p50.length - 1], 2)} ${h.ccy} (현재 ${nf(h.price0, 2)}), 오를 확률 ${pct(st.p_up, 0)}, 적용 기대수익 연 ${pct(f.mu[S.state.model.scenario])}`);
      ev.filter((e) => e !== lock).slice(0, 2).forEach((e) => why.push(`${e.date} ${esc(e.event.kind)} (±${e.event.sd}%)`));
      return `<div class="strat"><h3>${esc(h.ticker)} <span class="muted small">비중 ${pct(w[i], 1)} · ${krw(h.valueKrw)}원</span></h3><div class="act ${klass}">${act}</div><ul>${why.map((x) => `<li>${x}</li>`).join("")}</ul></div>`;
    }).join("");
  }

  // ------------------------------------------------------------ AI 의견 (무료 AI 사이트로 질문 보내기)
  function aiPrompt() {
    if (!lastForecast) return "";
    const { b, withEv: R } = lastForecast, g = S.state.goal, md = b.model, V0 = R.V0, yrs = yearsBetween(md.startDate, g.date);
    const lines = [];
    lines.push(`내 미국·한국 주식 포트폴리오의 종목별 투자 전략을 조언해 줘. 아래는 내 도구가 계산한 값이야 (${md.startDate} 기준, 원화).`);
    lines.push(`목표: ${krw(g.amount)}원, 목표일 ${g.date} (${yrs.toFixed(1)}년). 현재 평가액 ${krw(V0)}원, 필요한 연수익률 ${pct((g.amount / V0) ** (1 / yrs) - 1)}.`);
    lines.push(`몬테카를로 전망(환율·기업 사건 포함): 목표 달성 확률 ${pct(R.p_goal, 0)}, 목표일 중앙값 ${krw(R.terminal.p50)}원, 하위5% ${krw(R.terminal.p5)}원, 상위5% ${krw(R.terminal.p95)}원, 최대낙폭 중앙값 ${pct(R.mdd_median, 0)}.`);
    lines.push("종목 (비중 / 추세 / 칼만 기울기 연율 / 변동성 / 고점 대비 / 1년 수익률 / 목표일까지 오를 확률):");
    b.holdings.forEach((h, i) => {
      const p = S.prices[h.ticker], sg = p ? Model.indicators(p.dates, p.adj)?.sig : null, f = md.factors[i];
      lines.push(`- ${h.ticker}: ${pct(h.valueKrw / V0, 0)} / ${sg ? sg.trend : "-"} / ${sg ? spct(sg.slope_ann, 0) : "-"} / ${pct(f.vol, 0)} / ${sg ? pct(sg.drawdown, 0) : "-"} / ${sg && sg.ret_1y != null ? spct(sg.ret_1y, 0) : "-"} / ${pct(R.stocks[i].p_up, 0)}${f.n < 252 ? ` (상장 ${f.n}거래일)` : ""}`);
    });
    const ev = md.eventList.filter((e) => yearsBetween(md.startDate, e.date) <= 0.5).slice(0, 8);
    if (ev.length) lines.push("6개월 내 사건: " + ev.map((e) => `${e.date} ${e.event.target} ${e.event.kind}`).join(", "));
    lines.push("요청: 1) 종목별로 보유·비중 축소·추가 매수 중 무엇이 맞는지 이유와 함께, 2) 목표 확률을 높이면서 위험을 줄이는 비중 조정안, 3) 앞으로 3개월 동안 할 일 3가지. 한국 거주자 세금(해외주식 양도세 250만원 공제)도 고려해서 한국어로 간단히 답해 줘.");
    return lines.join("\n");
  }
  async function askAi(kind) {
    const q = aiPrompt(); if (!q) return toast("먼저 전망을 계산해 주세요");
    try { await navigator.clipboard.writeText(q); } catch (e) { /* 복사 실패는 무시 */ }
    const enc = encodeURIComponent(q);
    const url = { claude: "https://claude.ai/new?q=" + enc, chatgpt: "https://chatgpt.com/?q=" + enc, gemini: "https://gemini.google.com/app", copilot: "https://copilot.microsoft.com/?q=" + enc, meta: "https://www.meta.ai/" }[kind];
    if (url) window.open(url, "_blank", "noopener");
    toast(["copy", "gemini", "meta"].includes(kind) ? "질문을 복사했습니다. 붙여넣기 하세요." : "질문을 채워 열었습니다 (복사도 해 둠)");
  }

  // ------------------------------------------------------------ 비중안 비교
  async function runAlloc() {
    const st = $("#allocStatus"), btn = $("#btnAlloc");
    if (!S.prices.QQQ) { st.textContent = "QQQ 시세가 없어 비교할 수 없습니다. ④ 시세 수집에서 QQQ를 수집 목록에 넣어 주세요."; return; }
    btn.disabled = true; st.textContent = "계산 중... (안 5개)"; await new Promise((r) => setTimeout(r, 30));
    try {
      const g = S.state.goal, m = S.state.model, { rows } = valuation();
      const base = rows.filter((r) => r.valueKrw > 0).map((r) => ({ ticker: r.h.ticker, shares: r.sh, price0: r.p.v, ccy: r.ccy, valueKrw: r.valueKrw }));
      if (!base.some((h) => h.ticker === "QQQ")) { const q = S.prices.QQQ; base.push({ ticker: "QQQ", shares: 0, price0: q.close[q.close.length - 1], ccy: "USD", valueKrw: 1 }); }
      const series = {}; for (const k in S.prices) series[k] = { dates: S.prices[k].dates, adj: S.prices[k].adj };
      const model = Model.buildModel({ holdings: base, series, fxOf, settings: m, events: S.state.events, startDate: today(), goalDate: g.date });
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
    } catch (e) { st.textContent = "오류: " + e.message; console.error(e); }
    btn.disabled = false;
  }

  // ------------------------------------------------------------ ⑤ 추세
  function renderTrend() {
    const sel = $("#trendTicker"), keys = Object.keys(S.prices).filter((k) => !k.includes("=")).sort((a, b) => {
      const ha = S.state.holdings.findIndex((h) => h.ticker === a), hb = S.state.holdings.findIndex((h) => h.ticker === b);
      return (ha < 0 ? 99 : ha) - (hb < 0 ? 99 : hb);
    });
    const cur = sel.value || keys[0];
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
      sig.map(([k, s]) => `<tr><td class="l"><b>${esc(k)}</b>${S.state.holdings.some((h) => h.ticker === k) ? "" : ' <span class="tag">비교용</span>'}</td><td class="l">${s.trend}</td><td>${nf(s.close, 2)}</td><td>${nf(s.kalman_level, 2)}</td><td class="${cls(s.dev_from_kalman)}">${spct(s.dev_from_kalman)}</td>
        <td class="${cls(s.slope_ann)}">${spct(s.slope_ann, 0)}</td><td>${s.slope_z.toFixed(2)}</td><td>${nf(s.ema50, 2)}</td><td>${s.ema200 ? nf(s.ema200, 2) : "-"}</td><td>${pct(s.vol_ewma, 0)}</td>
        <td class="bad">${pct(s.drawdown)}</td><td class="${cls(s.ret_1m)}">${spct(s.ret_1m)}</td><td class="${cls(s.ret_3m)}">${spct(s.ret_3m)}</td><td class="${cls(s.ret_1y)}">${spct(s.ret_1y)}</td></tr>`).join("");
  }

  // ------------------------------------------------------------ ⑥ 설정
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
    if (a === "trend") renderTrend();
    if (a === "events") renderSchedule();
    if (a === "strategy" || a === "forecast") { if (!lastForecast || fcDirty) runForecast(); else renderForecast(); }
    try { localStorage.setItem("ana", a); } catch (e) { /* 무시 */ }
  }
  function showTab(name) {
    if (!$(`#tabs button[data-tab="${name}"]`)) name = "dash";
    $$("#tabs button").forEach((b) => b.classList.toggle("on", b.dataset.tab === name));
    $$(".tab").forEach((t) => t.classList.toggle("on", t.id === "tab-" + name));
    if (name === "dash") renderDash();
    if (name === "analysis") renderAnalysis();
    try { localStorage.setItem("tab", name); } catch (e) { /* 무시 */ }
  }
  function segClick(id, cb) { $(id).addEventListener("click", (e) => { const b = e.target.closest("button"); if (!b) return; $$(id + " button").forEach((x) => x.classList.toggle("on", x === b)); cb(); }); }

  function bind() {
    $("#tabs").addEventListener("click", (e) => { const b = e.target.closest("button"); if (b) showTab(b.dataset.tab); });
    $("#btnCollect").onclick = () => collect(false);
    $("#btnCollectTop").onclick = () => collect(false);
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
    segClick("#histRange", renderDash); segClick("#histStep", renderDash); segClick("#histMode", renderDash); segClick("#trendRange", renderTrend);
    $("#trendTicker").onchange = renderTrend;
    segClick("#anaNav", renderAnalysis);
    $("#btnAlloc").onclick = runAlloc;
    $("#aiBtns").addEventListener("click", (e) => { const b2 = e.target.closest("[data-ai]"); if (b2) askAi(b2.dataset.ai); });
    $("#eventTable").addEventListener("input", onEventEdit);
    $("#eventTable").addEventListener("change", onEventEdit);
    $("#eventTable").addEventListener("click", (e) => { const d = e.target.closest("[data-del]"); if (d && armed(d)) { S.state.events.splice(+d.dataset.del, 1); save(); renderEvents(); } });
    $("#btnAddEvent").onclick = () => {
      S.state.events.push({ id: "e" + Date.now(), on: true, date: Model.addMonths(today(), 1), target: S.state.holdings[0]?.ticker || "ALL", kind: "기타", repeat: "none", prob: 100, mean: 0, sd: 5, vol_mult: 1, vol_days: 0, note: "" });
      save(); renderEvents();
    };
    $("#scenario").onchange = (e) => { S.state.model.scenario = e.target.value; save(); runForecast(); };
    $("#nPaths").onchange = (e) => { S.state.model.n_paths = +e.target.value; save(); };
    $("#rebalance").onchange = (e) => { S.state.model.rebalance_yearly = e.target.checked; save(); };
    $("#btnForecast").onclick = runForecast;
    $("#modelForm").addEventListener("change", onModelEdit);
    $("#btnResetModel").onclick = (e) => { if (armed(e.target)) { S.state.model = { ...DEFAULT_MODEL }; save(); renderSettings(); } };
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
    let rz; window.addEventListener("resize", () => { clearTimeout(rz); rz = setTimeout(() => { const t = $("#tabs .on").dataset.tab; if (t === "dash") renderDash(); if (t === "analysis") { if (curAna() === "trend") renderTrend(); else if (lastForecast) renderForecast(); } }, 200); });
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
      if (S.firstVisit) logLine("처음 여셨습니다. 보유 종목의 수량(과 매수 단가)을 넣어 주세요. 저장해 둔 파일이 있으면 ③ 설정의 '입력값 불러오기'를 쓰면 됩니다.", false);
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
