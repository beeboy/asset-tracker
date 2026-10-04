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
    if (!S.state) {
      let st = null;
      try { st = JSON.parse(localStorage.getItem(LS_KEY) || "null"); } catch (e) { /* 무시 */ }
      S.firstVisit = !st;
      S.state = normalize(st || (await get("state.default.json").catch(() => ({}))));
    }
  }
  function normalize(st) {
    st.holdings = st.holdings || [];
    st.goal = Object.assign({ amount: 1e9, date: Model.addMonths(today(), 36), start_date: today(), monthly_contribution: 0 }, st.goal || {});
    st.events = st.events || [];
    st.model = Object.assign({}, DEFAULT_MODEL, st.model || {});
    st.ui = Object.assign({ auto_refresh_min: 0 }, st.ui || {});
    return st;
  }
  function save(dirtyForecast = true) {
    if (dirtyForecast) fcDirty = true;
    clearTimeout(saveTimer);
    saveTimer = setTimeout(async () => {
      if (MODE === "static") {
        try { localStorage.setItem(LS_KEY, JSON.stringify(S.state)); $("#footer").textContent = "이 브라우저에 저장됨 " + new Date().toLocaleTimeString() + " · 다른 기기에서 쓰려면 ⑥ 설정의 내보내기/불러오기"; }
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
    if (h.price != null && h.price !== "" && Number(h.price) > 0) return { v: Number(h.price), src: "manual" };
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
  const ghLink = (txt) => (GH ? ` <a href="${GH.actions}" target="_blank" rel="noopener">${txt}</a>` : "");
  function missingTickers() {
    if (MODE !== "static") return [];
    const have = new Set((S.tickerCfg?.tickers || []).map((t) => t.toUpperCase()));
    return S.state.holdings.map((h) => h.ticker).filter((t) => !have.has(t));
  }
  async function collectStatic() {
    const btns = [$("#btnCollect"), $("#btnCollectTop")]; btns.forEach((b) => (b.disabled = true));
    try {
      await reload(); fcDirty = true; renderAll();
      logLine(`최신 데이터를 불러왔습니다 (서버 수집 ${S.dataUpdated ? new Date(S.dataUpdated).toLocaleString() : "-"}).`);
    } catch (e) { logLine("불러오기 실패: " + e.message, false); }
    const miss = missingTickers();
    if (miss.length) logLine(`수집 목록에 없는 종목: ${esc(miss.join(", "))}. GitHub에서 'Run workflow'를 누르고 추가 티커 칸에 넣으면 다음부터 함께 수집합니다.${ghLink("수집 실행 페이지 열기")}`, false, true);
    btns.forEach((b) => (b.disabled = false));
  }
  async function collect(quotesOnly, list) {
    if (MODE === "static") return collectStatic();
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
    const head = `<tr><th class="l">티커</th><th class="l">이름</th><th>수량</th><th>현재가</th><th>단가 입력</th><th>평균 매수가</th><th>통화</th><th>평가액 (원)</th><th>비중</th><th>전일 대비</th><th>손익</th><th class="l">메모</th><th></th></tr>`;
    const body = rows.map((r, i) => {
      const p = r.p, tag = p.src ? `<span class="tag ${p.src === "manual" ? "manual" : ""}">${SESS[p.src] || p.src}</span>` : "";
      const pl = r.pl != null ? `<span class="${cls(r.pl)}">${nf(r.pl, 0)} ${r.ccy} (${spct(r.plPct)})</span><br><span class="muted small">${krw(r.pl * (r.fx || 1))}원</span>` : `<span class="muted">-</span>`;
      return `<tr data-i="${i}">
        <td class="l"><b>${esc(r.h.ticker)}</b></td><td class="l small">${esc(r.name).slice(0, 28)}</td>
        <td><input data-f="shares" type="number" step="any" value="${r.h.shares ?? ""}"></td>
        <td>${p.v != null ? nf(p.v, 2) : "-"}${tag}</td>
        <td><input data-f="price" type="number" step="any" placeholder="자동" value="${r.h.price ?? ""}"></td>
        <td><input data-f="avg_cost" type="number" step="any" placeholder="선택" value="${r.h.avg_cost ?? ""}"></td>
        <td>${r.ccy}</td><td><b>${nf(r.valueKrw)}</b></td><td>${pct(r.w)}</td>
        <td class="${cls(r.dayChg)}">${spct(r.dayChg, 2)}</td><td>${pl}</td>
        <td class="l"><input data-f="note" class="wide" value="${esc(r.h.note)}"></td>
        <td><button class="danger" data-del="${i}" title="삭제">삭제</button></td></tr>`;
    }).join("");
    const plTot = rows.filter((r) => r.pl != null).reduce((s, r) => s + r.pl * (r.fx || 1), 0);
    const foot = `<tr><td class="l"><b>합계</b></td><td></td><td></td><td></td><td></td><td></td><td></td><td><b>${nf(total)}</b></td><td>100%</td><td></td><td>${rows.some((r) => r.pl != null) ? `<span class="${cls(plTot)}">${krw(plTot)}원</span>` : ""}</td><td></td><td></td></tr>`;
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
      if (MODE === "static") { showTab("quotes"); logLine(`${esc(t)} 시세가 아직 없습니다. GitHub에서 수집 목록에 추가해 주세요 (Run workflow → 추가 티커에 ${esc(t)}). 그 전까지는 단가 입력칸에 직접 넣으면 평가에 반영됩니다.${ghLink("수집 실행 페이지 열기")}`, false, true); }
      else await collect(false, [t]);
    }
  }
  async function delHolding(i) {
    const h = S.state.holdings[i];
    if (!confirm(`${h.ticker} 종목을 목록에서 삭제할까요?`)) return;
    S.state.holdings.splice(i, 1);
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
  function renderGoal() {
    const g = S.state.goal, { total } = valuation(), yrs = yearsBetween(today(), g.date);
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

    const n = +($("#histRange .on")?.dataset.r || 252), mode = $("#histMode .on")?.dataset.m || "total";
    const k0 = Math.max(0, H.dates.length - 1 - n), x = H.dates.slice(k0);
    let series;
    if (mode === "total") series = [{ name: "합계", y: H.total.slice(k0), color: "var(--c1)", width: 2 }];
    else series = Object.keys(H.each).map((t, i) => ({ name: t, y: H.each[t].slice(k0), color: C[i % C.length] }));
    Charts.lineChart($("#histChart"), { x, series, yfmt: krwAxis, height: 300, hlines: mode === "total" && g.amount <= Math.max(...H.total.slice(k0)) * 1.05 ? [{ y: g.amount, label: "목표" }] : [] });

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
    save(); renderHeader(); renderGoal();
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
    } catch (e) { st.textContent = "오류: " + e.message; console.error(e); }
    btn.disabled = false;
  }
  function renderForecast() {
    const m = S.state.model;
    $("#scenario").value = m.scenario; $("#nPaths").value = String(m.n_paths); $("#rebalance").checked = !!m.rebalance_yearly;
    if (!lastForecast) { $("#fcStatus").textContent = "전망 계산을 눌러 주세요."; return; }
    const { b, withEv: R, noEv, hasEv } = lastForecast, g = S.state.goal, md = b.model;
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
    } else $("#fcEvents").innerHTML = `<p class="muted">켜진 사건이 없습니다. ③ 기업 사건 탭에서 추가하세요.</p>`;

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
  function renderSettings() {
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
    renderHeader(); renderQuotes(); renderGoalInputs(); renderGoal(); renderEvents(); renderSettings();
    const tab = $("#tabs .on").dataset.tab;
    if (tab === "trend") renderTrend();
    if (tab === "forecast") renderForecast();
  }
  function showTab(name) {
    $$("#tabs button").forEach((b) => b.classList.toggle("on", b.dataset.tab === name));
    $$(".tab").forEach((t) => t.classList.toggle("on", t.id === "tab-" + name));
    if (name === "goal") renderGoal();
    if (name === "trend") renderTrend();
    if (name === "events") renderSchedule();
    if (name === "forecast") { if (!lastForecast || fcDirty) runForecast(); else renderForecast(); }
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
    $("#holdTable").addEventListener("click", (e) => { const d = e.target.closest("[data-del]"); if (d) delHolding(+d.dataset.del); });
    $("#btnAdd").onclick = addHolding;
    $("#addAvg").addEventListener("keydown", (e) => e.key === "Enter" && addHolding());
    $("#addShares").addEventListener("keydown", (e) => e.key === "Enter" && addHolding());
    ["#goalAmount", "#goalDate", "#startDate", "#goalYears", "#monthly"].forEach((s) => { $(s).addEventListener("change", onGoalEdit); });
    segClick("#histRange", renderGoal); segClick("#histMode", renderGoal); segClick("#trendRange", renderTrend);
    $("#trendTicker").onchange = renderTrend;
    $("#eventTable").addEventListener("input", onEventEdit);
    $("#eventTable").addEventListener("change", onEventEdit);
    $("#eventTable").addEventListener("click", (e) => { const d = e.target.closest("[data-del]"); if (d && confirm("이 사건을 삭제할까요?")) { S.state.events.splice(+d.dataset.del, 1); save(); renderEvents(); } });
    $("#btnAddEvent").onclick = () => {
      S.state.events.push({ id: "e" + Date.now(), on: true, date: Model.addMonths(today(), 1), target: S.state.holdings[0]?.ticker || "ALL", kind: "기타", repeat: "none", prob: 100, mean: 0, sd: 5, vol_mult: 1, vol_days: 0, note: "" });
      save(); renderEvents();
    };
    $("#scenario").onchange = (e) => { S.state.model.scenario = e.target.value; save(); runForecast(); };
    $("#nPaths").onchange = (e) => { S.state.model.n_paths = +e.target.value; save(); };
    $("#rebalance").onchange = (e) => { S.state.model.rebalance_yearly = e.target.checked; save(); };
    $("#btnForecast").onclick = runForecast;
    $("#modelForm").addEventListener("change", onModelEdit);
    $("#btnResetModel").onclick = () => { if (confirm("모형 설정을 기본값으로 되돌릴까요?")) { S.state.model = { ...DEFAULT_MODEL }; save(); renderSettings(); } };
    $("#btnExport").onclick = () => {
      const a = document.createElement("a");
      a.href = URL.createObjectURL(new Blob([JSON.stringify(S.state, null, 1)], { type: "application/json" }));
      a.download = `자산입력-${today()}.json`; a.click();
    };
    $("#fileImport").onchange = async (e) => {
      const f = e.target.files[0]; if (!f) return;
      try { S.state = normalize(JSON.parse(await f.text())); save(); renderAll(); toast("불러왔습니다"); } catch (err) { alert("파일을 읽지 못했습니다: " + err.message); }
    };
    let rz; window.addEventListener("resize", () => { clearTimeout(rz); rz = setTimeout(() => { const t = $("#tabs .on").dataset.tab; if (t === "goal") renderGoal(); if (t === "trend") renderTrend(); if (t === "forecast" && lastForecast) renderForecast(); }, 200); });
  }

  async function init() {
    try { await reload(); }
    catch (e) { document.body.innerHTML = `<div class="card" style="margin:40px auto;max-width:640px"><h2>데이터를 불러오지 못했습니다</h2><p>내 PC에서 쓸 때는 <b>실행 파일</b>(Windows: <code>실행-Windows.bat</code>, Mac: <code>실행-Mac.command</code>)로 열어야 합니다. 웹 버전은 GitHub Actions의 첫 수집이 끝난 뒤 열립니다.</p><p class="muted small">${esc(e.message)}</p></div>`; return; }
    bind(); renderAll();
    setAuto(S.state.ui.auto_refresh_min || 0);
    let tab = "quotes"; try { tab = localStorage.getItem("tab") || "quotes"; } catch (e) { /* 무시 */ }
    showTab(tab);
    if (MODE === "static") {
      $("#btnQuotes").style.display = "none";
      $("#btnCollect").textContent = $("#btnCollectTop").textContent = "최신 데이터 불러오기";
      $("#modeNote").innerHTML = `웹 버전: 시세는 GitHub가 정해진 시간마다 자동으로 수집합니다. 지금 바로 수집하려면${ghLink("GitHub 수집 실행")}${GH ? "" : " GitHub Actions의 collect 작업을 실행"}하세요. 보유 수량·목표·사건은 <b>이 브라우저에만</b> 저장되고 저장소에는 올라가지 않습니다.`;
      $("#modeNote").style.display = "block";
      logLine(`웹 데이터 수집 시각: ${S.dataUpdated ? new Date(S.dataUpdated).toLocaleString() : "-"}`);
      if (S.firstVisit) logLine("처음 여셨습니다. 아래 표에 보유 수량을 넣거나, ⑥ 모형 설정 탭의 '입력값 불러오기'로 저장해 둔 JSON을 불러오세요.", false);
      const miss = missingTickers();
      if (miss.length) logLine(`수집 목록에 없는 종목: ${esc(miss.join(", "))}.${ghLink("수집 실행 페이지 열기")}`, false, true);
      return;
    }
    const newest = Object.values(S.quotes).reduce((m, q) => Math.max(m, q.fetched || 0), 0);
    logLine(newest ? `저장된 시세를 불러왔습니다 (최근 수집 ${new Date(newest * 1000).toLocaleString()}). 새 시세는 '전체 수집'을 누르세요.` : "저장된 시세가 없습니다. '전체 수집'을 눌러 주세요.");
  }
  init();
})();
