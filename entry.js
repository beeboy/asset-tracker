// 입구 페이지 (naeilo.com/): 해 보기 카드 한 장 + 출시 알림 신청
// 계산은 web/model.js 를 그대로 쓴다 (웹 전망·앱 홈과 같은 식). 웹 app.js 의 buildModelNow 를 입구용으로 작게 다시 썼다.
// 넣은 값은 이 브라우저(localStorage)에만 둔다. 주소·서버로 보내지 않는다.
(function () {
  "use strict";
  const $ = (s) => document.querySelector(s);
  const VER = (document.currentScript && document.currentScript.src.split("?v=")[1]) || "";
  const LS_KEY = "naeilo-entry";
  const MAX_ROWS = 5, SAMPLE = [{ t: "DRNK", q: "4" }, { t: "QQQ", q: "1" }], SAMPLE_GOAL = 5e6; // 앱 첫 화면 샘플과 같은 수량·목표
  const NOT_STOCK = new Set(["KRW=X", "^TNX", "CL=F", "GC=F"]); // 요인 시세 (종목 찾기에서 뺀다)
  const today = () => { const d = new Date(); return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10); };
  const isKR = (t) => /\.(KS|KQ)$/.test(t);
  const fxOf = (ccy) => (!ccy || ccy === "KRW" ? null : ccy === "USD" ? "KRW=X" : ccy + "KRW=X"); // app.js 의 fxOf 와 같다
  const nf = (v, d = 0) => Number(v).toLocaleString("ko-KR", { maximumFractionDigits: d });
  const won = (v) => nf(Math.round(v)) + "원";
  const pct = (p) => (p == null ? "-" : Math.round(p * 100) + "%");
  const num = (s) => { const v = Number(String(s || "").replace(/[,\s원$]/g, "")); return isFinite(v) && v > 0 ? v : null; };
  const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);

  // ------------------------------------------------------------ 사이트 데이터 (data/)
  const D = { idx: null, quotes: {}, mar: {}, krList: [], krDaily: {}, krRules: {}, config: {}, def: {}, prices: {} };
  const get = async (f, live) => { const r = await fetch("data/" + f + (live ? "?t=" + Date.now() : ""), { cache: live ? "no-store" : "no-cache" }); if (!r.ok) throw new Error(f + " " + r.status); return r.json(); };
  async function loadBase() {
    const [idx, quotes, mar, kr, krDaily, krRules, config, def] = await Promise.all([get("index.json", 1), get("quotes.json", 1).catch(() => ({})), get("mar.json").catch(() => ({})),
      get("kr_stocks.json").catch(() => ({ stocks: [] })), get("kr_daily.json").catch(() => ({ s: {} })), get("kr_proxy.json").catch(() => ({})), get("config.json").catch(() => ({})), get("state.default.json").catch(() => ({}))]);
    Object.assign(D, { idx, quotes, mar, krList: kr.stocks || [], krDaily: krDaily.s || {}, krRules, config, def });
  }
  // 시세 파일은 index.json 의 판이 같으면 브라우저 캐시를 쓴다 (웹과 같은 방식)
  async function price(sym) {
    if (D.prices[sym] !== undefined) return D.prices[sym];
    const f = D.idx.prices && D.idx.prices[sym]; if (!f) return (D.prices[sym] = null);
    const v = "?v=" + encodeURIComponent((D.idx.v && D.idx.v[sym]) || D.idx.updated || "");
    try { const r = await fetch("data/prices/" + encodeURIComponent(f) + v); D.prices[sym] = r.ok ? await r.json() : null; } catch (e) { D.prices[sym] = null; }
    return D.prices[sym];
  }
  const lastClose = (p) => { const c = (p && p.close) || []; for (let i = c.length - 1; i >= 0; i--) if (c[i] > 0) return { v: c[i], d: p.dates[i] }; return null; };

  // 종목 목록: 사이트가 시세를 모으는 미국 종목 + 국내 개별 종목 (kr_stocks.json). 이름은 앱 한글 목록(stocks.js)
  const STOCKS = window.STOCKS || [];
  const koOf = (s) => STOCKS.find((o) => o.s === s);
  let CAT = [];
  function buildCatalog() {
    const us = Object.keys(D.idx.prices || {}).filter((s) => !NOT_STOCK.has(s) && !isKR(s)).map((s) => ({ s, n: (koOf(s) && koOf(s).k) || (D.quotes[s] && D.quotes[s].name) || (s === "DRNK" ? "가상 종목" : s) }));
    const kr = D.krList.map((o) => ({ s: o.yahoo, n: o.name, code: o.id }));
    CAT = us.concat(kr);
    $("#stockList").innerHTML = CAT.map((o) => `<option value="${esc(label(o))}"></option>`).join("");
  }
  const label = (o) => `${o.n} · ${o.code || o.s}`;
  function findStock(txt) {
    const k = String(txt || "").trim().toLowerCase().replace(/\s+/g, ""); if (!k) return null;
    const keys = (o) => [label(o), o.s, o.code, o.n, koOf(o.s) && koOf(o.s).e].filter(Boolean).map((x) => String(x).toLowerCase().replace(/\s+/g, ""));
    return CAT.find((o) => keys(o).includes(k)) || null;
  }

  // ------------------------------------------------------------ 환율·가격 (웹 전망 기준값과 같은 규칙)
  // 원/달러: 매매기준율(5일 안) > 지금 시세 > 지난 종가
  function fxBase(ccy) {
    if (!fxOf(ccy)) return 1;
    const m = D.mar && D.mar.USD;
    if (ccy === "USD" && m && m.rate > 0 && m.date && (Date.parse(today()) - Date.parse(m.date)) / 864e5 <= 5) return m.rate;
    return fxNow(ccy);
  }
  function fxNow(ccy) {
    const s = fxOf(ccy); if (!s) return 1;
    const q = D.quotes[s]; if (q && q.last) return q.last;
    const l = lastClose(D.prices[s]); return l ? l.v : null;
  }
  // 국내 종목: 시세 파일 대신 사이트 시세(quotes.json) 또는 하루 종가(kr_daily.json), 지난 움직임은 기준 ETF × 원/달러 (웹 synthKR 과 같은 식)
  function krInfo(names) {
    const R = D.krRules || {}, txt = names.filter(Boolean).join(" "), re = (x) => x && new RegExp(x, "i");
    const rule = R.exclude && re(R.exclude).test(txt) ? null : (R.rules || []).find((r) => re(r.match).test(txt));
    return rule ? { proxy: rule.proxy, hedged: !!(R.hedged && re(R.hedged).test(txt)) } : { proxy: null, market: R.market || "EWY" };
  }
  function krPrice(s) {
    const q = D.quotes[s]; if (q && q.last > 0) return q.last;
    const c = D.krDaily[s.replace(/\.(KS|KQ)$/, "")]; return c && c.c && c.c.length ? c.c[c.c.length - 1] : null;
  }
  async function krSeries(s, px) {
    const o = CAT.find((x) => x.s === s), info = krInfo([o && o.n, koOf(s) && koOf(s).k, koOf(s) && koOf(s).e]);
    const base = await price(info.proxy || info.market), fx = await price("KRW=X");
    if (!base || !base.dates.length) return { dates: [today()], adj: [px] };
    const A = window.Model.krSeries(base, fx, info.hedged, "adj");
    const j = A.vals.length - 1, k = A.vals[j] > 0 ? px / A.vals[j] : 1;
    return { dates: A.dates, adj: A.vals.map((v) => (v == null ? null : v * k)) };
  }

  // ------------------------------------------------------------ 입력 줄
  function rowHtml(r) {
    return `<div class="row"><input class="t" list="stockList" placeholder="종목 이름·기호" value="${esc(r.t || "")}" autocomplete="off" aria-label="종목">
      <input class="q" inputmode="decimal" placeholder="수량" value="${esc(r.q || "")}" aria-label="수량">
      <input class="a" inputmode="decimal" placeholder="단가" value="${esc(r.a || "")}" aria-label="평균 단가 (선택)">
      <button type="button" class="x" aria-label="이 줄 지우기">×</button><div class="name"></div></div>`;
  }
  const readRows = () => [...document.querySelectorAll("#rows .row")].map((el) => ({ t: el.querySelector(".t").value, q: el.querySelector(".q").value, a: el.querySelector(".a").value }));
  function save() { try { localStorage.setItem(LS_KEY, JSON.stringify({ rows: readRows(), goal: $("#goal").value })); } catch (e) { /* 무시 */ } }
  function load() { try { return JSON.parse(localStorage.getItem(LS_KEY) || "null"); } catch (e) { return null; } }
  function drawRows(rows) {
    $("#rows").innerHTML = rows.map(rowHtml).join("");
    $("#addRow").style.display = rows.length >= MAX_ROWS ? "none" : "";
  }
  function nameHints() {
    document.querySelectorAll("#rows .row").forEach((el) => {
      const v = el.querySelector(".t").value.trim(), o = findStock(v), n = el.querySelector(".name");
      n.className = "name" + (v && !o ? " bad" : "");
      n.textContent = !v ? "" : !o ? "목록에 없는 종목이에요. 사이트가 시세를 모으는 종목만 계산할 수 있어요." : `${o.n}${isKR(o.s) ? " · 단가는 원" : " · 단가는 달러"}`;
    });
  }

  // ------------------------------------------------------------ 계산 (화면을 막지 않게 worker 에서)
  let worker = null, jobId = 0;
  function getWorker() {
    if (worker) return worker;
    const src = new URL("web/model.js" + (VER ? "?v=" + VER : ""), location.href).href;
    const code = `self.window = self; importScripts(${JSON.stringify(src)});
      const fxOf = ${fxOf.toString()};
      self.onmessage = (e) => {
        const { id, inp } = e.data;
        try {
          const M = self.Model, tks = inp.holdings.map((h) => h.ticker);
          const betas = M.factorBetas(inp.series, tks, inp.startDate).beta;
          const model = M.buildModel({ holdings: inp.holdings, series: inp.series, fxOf, settings: inp.settings, events: inp.events, betas, startDate: inp.startDate, goalDate: inp.goalDate });
          const m = inp.settings;
          const R = M.simulate(model, { holdings: inp.holdings, scenario: m.scenario, nPaths: Number(m.n_paths), seed: Number(m.seed) || 1, goal: inp.goal, monthly: 0, rebalance: !!m.rebalance_yearly, dof: m.t_dof, fxOf, usdKrw0: inp.usd0, withEvents: true });
          let bep = null;
          if (inp.bep) {
            const A = inp.holdings.length, mu = model.factors.slice(0, A).map((f) => (m.scenario === "blend" ? M.blendMu(f.mu.base, f.mu.smooth ?? f.mu.base, Number(m.trust) / 100) : f.mu[m.scenario] ?? f.mu.base));
            bep = M.breakeven(R, { idx: inp.bep.idx, basis: inp.bep.basis, mu, months: [12, 36] });
          }
          self.postMessage({ id, done: { V0: R.V0, p_goal: R.p_goal, p_up: 1 - R.p_loss, bep } });
        } catch (err) { self.postMessage({ id, err: String((err && err.message) || err) }); }
      };`;
    worker = new Worker(URL.createObjectURL(new Blob([code], { type: "text/javascript" })));
    return worker;
  }
  const runWorker = (inp) => new Promise((ok, bad) => {
    const id = ++jobId, w = getWorker();
    const on = (e) => { if (e.data.id !== id) return; w.removeEventListener("message", on); e.data.err ? bad(new Error(e.data.err)) : ok(e.data.done); };
    w.addEventListener("message", on); w.postMessage({ id, inp });
  });

  let timer = null, seq = 0;
  function schedule() { save(); clearTimeout(timer); timer = setTimeout(calc, 500); }
  async function calc() {
    const my = ++seq, res = $("#res");
    nameHints();
    const rows = readRows().map((r) => ({ o: findStock(r.t), sh: num(r.q), avg: num(r.a) })).filter((r) => r.o && r.sh);
    if (!rows.length) { res.innerHTML = `<div class="line muted">종목과 수량을 넣으면 여기에 지금 평가액과 확률이 나와요.</div>`; return; }
    res.innerHTML = `<div class="line muted">계산하는 중이에요…</div>`;
    try {
      const start = today(), goalDate = window.Model.addMonths(start, 36);
      const need = new Set(["KRW=X", ...Object.values(window.Model.FACTOR_SYM)]);
      rows.forEach((r) => { if (!isKR(r.o.s)) need.add(r.o.s); });
      await Promise.all([...need].map(price));
      // 같은 종목을 두 줄에 넣으면 한 줄로 합친다 (평균 단가는 수량 가중)
      const by = new Map();
      rows.forEach((r) => { const x = by.get(r.o.s); if (!x) by.set(r.o.s, { ...r }); else { const cost = (x.avg || 0) * x.sh + (r.avg || 0) * r.sh; x.sh += r.sh; x.avg = x.avg && r.avg ? cost / x.sh : null; } });
      const holdings = [], series = {}, basisRows = [];
      for (const r of by.values()) {
        const s = r.o.s, kr = isKR(s), p = D.prices[s];
        const ccy = kr ? "KRW" : (p && p.currency) || (D.quotes[s] && D.quotes[s].currency) || "USD";
        const p0 = kr ? krPrice(s) : (lastClose(p) || {}).v; // 전망 기준값 = 사이트 마지막 종가 (웹 snapPx 와 같다)
        const fx = fxBase(ccy);
        if (!(p0 > 0) || !fx) continue;
        holdings.push({ ticker: s, shares: r.sh, price0: p0, ccy, valueKrw: r.sh * p0 * fx });
        series[s] = kr ? await krSeries(s, p0) : { dates: p.dates, adj: p.adj };
        if (r.avg) basisRows.push({ i: holdings.length - 1, basis: r.avg * r.sh * fx });
      }
      for (const k of need) if (D.prices[k] && !series[k]) series[k] = { dates: D.prices[k].dates, adj: D.prices[k].adj };
      if (!holdings.length) throw new Error("가격을 찾지 못했어요");
      const V0 = holdings.reduce((a, h) => a + h.valueKrw, 0), goal = num($("#goal").value);
      const settings = window.Model.normModel(JSON.parse(JSON.stringify(D.def.model || {})));
      const st = { events: JSON.parse(JSON.stringify(D.def.events || [])), ext_ver: D.def.ext_ver }; window.Model.normEvents(st);
      const basis = basisRows.reduce((a, x) => a + x.basis, 0), cur = basisRows.reduce((a, x) => a + holdings[x.i].valueKrw, 0);
      $("#asof").textContent = `기준: 사이트 마지막 종가 × ${D.mar && D.mar.USD ? "매매기준율" : "원/달러"}${D.idx.updated ? " · 시세 " + String(D.idx.updated).slice(0, 10) : ""}. 3,000가지 길을 그려 센 확률이에요.`;
      res.innerHTML = `<div><div class="small muted">지금 원화 평가액</div><div class="big">${won(V0)}</div></div><div class="line muted">확률을 계산하는 중이에요…</div>`;
      const R = await runWorker({ holdings, series, settings, events: st.events, startDate: start, goalDate, goal: goal || V0, usd0: fxNow("USD") || 1, bep: basisRows.length ? { idx: basisRows.map((x) => x.i), basis } : null });
      if (my !== seq) return;
      const out = [`<div><div class="small muted">지금 원화 평가액</div><div class="big">${won(V0)}</div></div>`];
      if (basisRows.length) {
        const gap = cur / basis - 1, p12 = R.bep && R.bep.find((x) => x.m === 12), p36 = R.bep && R.bep.find((x) => x.m === 36);
        out.push(gap < 0
          ? `<div class="line">산 돈보다 ${Math.round(-gap * 1000) / 10}% 아래예요. 1년 안에 본전에 닿을 확률 <b>${pct(p12 && p12.p)}</b>, 3년 안 <b>${pct(p36 && p36.p)}</b>.</div>`
          : `<div class="line">산 돈보다 ${Math.round(gap * 1000) / 10}% 위에 있어요.</div>`);
      }
      if (goal) out.push(`<div class="line">3년 뒤(${goalDate.replace(/-/g, ".")})까지 목표 ${won(goal)}에 닿을 확률 <b>${pct(R.p_goal)}</b>.</div>`);
      out.push(`<div class="line">3년 뒤 지금보다 높을 확률 <b>${pct(R.p_up)}</b>.</div>`);
      res.innerHTML = out.join("");
    } catch (e) {
      if (my === seq) res.innerHTML = `<div class="line err">계산하지 못했어요: ${esc(e.message || e)}</div>`;
    }
  }

  // ------------------------------------------------------------ 출시 알림 신청 (중계 /notify, 이메일과 날짜만 저장)
  const notifyUrl = () => { const b = D.config.notify || (D.config.push ? D.config.push.replace(/\/$/, "") + "/notify" : ""); return b; };
  async function notify(del) {
    const msg = $("#notifyMsg"), email = $("#email").value.trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) { msg.textContent = "이메일 주소를 확인해 주세요."; return; }
    const url = notifyUrl(); if (!url) { msg.textContent = "지금은 신청을 받을 수 없어요. 잠시 뒤 다시 해 주세요."; return; }
    const btn = $("#notify .btn"); btn.disabled = true; msg.textContent = "보내는 중이에요…";
    try {
      const r = await fetch(url + (del ? "/del" : ""), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email }) });
      const j = await r.json().catch(() => ({}));
      if (!r.ok || !j.ok) throw new Error(j.error || r.status);
      msg.textContent = del ? "신청을 취소했어요. 이메일을 지웠어요." : "신청했어요. 앱이 나오면 이 주소로 한 번 알려 드릴게요.";
      if (!del) $("#email").value = "";
    } catch (e) { msg.textContent = "지금은 신청을 받을 수 없어요. 잠시 뒤 다시 해 주세요."; }
    finally { btn.disabled = false; }
  }

  // ------------------------------------------------------------ 시작
  function init() {
    document.querySelectorAll(".phone img").forEach((img) => img.addEventListener("error", () => img.classList.add("miss")));
    const st = load();
    drawRows(st && st.rows && st.rows.length ? st.rows.slice(0, MAX_ROWS) : SAMPLE);
    $("#goal").value = st ? st.goal || "" : nf(SAMPLE_GOAL);
    $("#rows").addEventListener("input", schedule);
    $("#rows").addEventListener("change", schedule);
    $("#rows").addEventListener("click", (e) => {
      if (!e.target.classList.contains("x")) return;
      e.target.closest(".row").remove();
      if (!document.querySelector("#rows .row")) drawRows([{ t: "", q: "" }]);
      $("#addRow").style.display = ""; schedule();
    });
    $("#addRow").addEventListener("click", () => { const r = readRows(); if (r.length < MAX_ROWS) { drawRows(r.concat({ t: "", q: "" })); document.querySelector("#rows .row:last-of-type .t").focus(); } });
    $("#goal").addEventListener("input", schedule);
    $("#goal").addEventListener("blur", () => { const v = num($("#goal").value); if (v) $("#goal").value = nf(v); });
    $("#notify").addEventListener("submit", (e) => { e.preventDefault(); notify(false); });
    $("#unsub").addEventListener("click", () => notify(true));
    loadBase().then(() => { buildCatalog(); calc(); }).catch(() => { $("#res").innerHTML = `<div class="line err">시세를 받지 못했어요. 잠시 뒤 다시 열어 주세요.</div>`; });
  }
  init();
})();
