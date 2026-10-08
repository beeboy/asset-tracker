// 아이폰 위젯용 요약 계산. 화면과 무관한 순수 계산만 둔다 (model.js 필요).
// 사이트는 동기화할 때 이 요약을 입력값과 함께 암호화해 올리고 (1차), 아이폰 앱은 사이트를 안 연 날 같은 파일로 직접 계산한다 (2차).
// 같은 파일·같은 시드로 계산하므로 두 쪽 숫자가 같다. app.js 의 평가·이력·전망 계산을 그대로 옮겼다 (바꿀 때 둘 다 고칠 것).
(function () {
  "use strict";
  const VERSION = 1; // 요약 형식·계산 방식이 바뀌면 올린다. 앱은 버전이 다르면 사이트 요약 대신 직접 계산한다
  const FACTORS = { mkt: "SPY", rate: "^TNX", oil: "CL=F", gold: "GC=F", cmdty: "DBC" };
  const DEFAULT_MODEL = {
    scenario: "blend", trust: 50, n_paths: 3000, seed: 20261004, history_years: 3, prior_mu: 10, prior_tau: 15, conservative_mu: 4,
    new_listing_vol: 60, default_vol: 40, default_corr: 0.3, t_dof: 5, fx_drift: 0, fx_vol_mult: 1, rebalance_yearly: false, earnings_adjust: true,
  };
  const M = () => window.Model;
  const yearsBetween = (a, b) => (Date.parse(b + "T00:00:00Z") - Date.parse(a + "T00:00:00Z")) / (365.25 * 864e5);

  // S: { state, prices, quotes, mar, today }  (사이트의 S 와 같은 모양)
  function ctx(S) {
    const today = S.today || new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 10);
    const st = S.state, prices = S.prices || {}, quotes = S.quotes || {};
    const fxOf = (ccy) => (!ccy || ccy === "KRW" ? null : ccy === "USD" ? "KRW=X" : ccy + "KRW=X");
    const ccyOf = (t) => prices[t]?.currency || quotes[t]?.currency || "USD";
    const lastOf = (sym) => { const p = prices[sym]; return p && p.close.length ? { v: p.close[p.close.length - 1], d: p.dates[p.dates.length - 1] } : null; };
    const fxNow = (ccy) => { const s = fxOf(ccy); if (!s) return 1; const q = quotes[s]; if (q && q.last) return q.last; const l = lastOf(s); return l ? l.v : null; };
    const marUsd = () => { const m = S.mar?.USD; return m && m.rate > 0 && m.date && yearsBetween(m.date, today) * 365 <= 5 ? m : null; };
    const fxBase = (ccy) => (ccy === "USD" && marUsd() ? marUsd().rate : fxNow(ccy));
    const model = Object.assign({}, DEFAULT_MODEL, st.model || {});
    const C = { S, st, prices, quotes, today, fxOf, ccyOf, lastOf, fxNow, fxBase, model };
    C.cashKrw = () => { const c = st.cash; return c ? (Number(c.krw) || 0) + (Number(c.usd) || 0) * (fxBase("USD") || 0) : 0; };
    C.curPrice = (h) => {
      if (st.ui?.manual_price && h.price != null && h.price !== "" && Number(h.price) > 0) return { v: Number(h.price) };
      const q = quotes[h.ticker], l = lastOf(h.ticker);
      if (q && q.last && (!l || (q.last_time || 0) * 1000 >= Date.parse(l.d))) return { v: q.last };
      if (l) return { v: l.v };
      if (q && q.last) return { v: q.last };
      return { v: null };
    };
    return C;
  }

  function valuation(C) {
    const rows = C.st.holdings.map((h) => {
      const ccy = C.ccyOf(h.ticker), p = C.curPrice(h), fx = C.fxBase(ccy), sh = Number(h.shares) || 0;
      const valueKrw = p.v != null && fx ? sh * p.v * fx : null, prev = C.quotes[h.ticker]?.prev_close || null;
      return { h, ccy, p, fx, sh, valueKrw, dayChg: prev && p.v ? p.v / prev - 1 : null };
    });
    return { rows, total: rows.reduce((s, r) => s + (r.valueKrw || 0), 0) };
  }

  // 현재 수량을 과거에 적용한 원화 평가액 + 편입 효과를 뺀 연결 지수 (app.js history 와 같음)
  function history(C) {
    const P = C.prices, hs = C.st.holdings.filter((h) => P[h.ticker] && Number(h.shares) > 0);
    const dset = new Set(); hs.forEach((h) => P[h.ticker].dates.forEach((d) => dset.add(d)));
    const dates = [...dset].sort(), fxMaps = {};
    const getFx = (ccy) => {
      const s = C.fxOf(ccy); if (!s) return () => 1;
      if (!fxMaps[s]) { const p = P[s]; const m = new Map(); if (p) p.dates.forEach((d, i) => m.set(d, p.close[i])); fxMaps[s] = { m, last: null }; }
      return fxMaps[s];
    };
    const each = {}, cur = {}, curFx = {};
    hs.forEach((h) => { each[h.ticker] = []; const m = new Map(); P[h.ticker].dates.forEach((d, i) => m.set(d, P[h.ticker].close[i])); cur[h.ticker] = { m, v: null }; });
    const total = [], index = []; let idx = 1, prevSet = null;
    for (const d of dates) {
      for (const ccy of new Set(hs.map((h) => C.ccyOf(h.ticker)))) {
        const f = getFx(ccy); if (typeof f === "function") { curFx[ccy] = 1; continue; }
        if (f.m.has(d)) f.last = f.m.get(d); curFx[ccy] = f.last ?? (P[C.fxOf(ccy)]?.close[0] || C.fxNow(ccy));
      }
      let t = 0, tPrev = 0; const set = new Set();
      for (const h of hs) {
        const c = cur[h.ticker]; if (c.m.has(d)) c.v = c.m.get(d);
        const v = c.v != null ? c.v * Number(h.shares) * curFx[C.ccyOf(h.ticker)] : null;
        each[h.ticker].push(v);
        if (v != null) { t += v; set.add(h.ticker); if (prevSet && prevSet.has(h.ticker)) tPrev += v; }
      }
      if (prevSet) { const base = [...prevSet].reduce((s, k) => s + (each[k][each[k].length - 2] || 0), 0); if (base > 0) idx *= tPrev / base; }
      total.push(t); index.push(idx); prevSet = set;
    }
    return { dates, total, each, index };
  }
  function actualSeries(C, H) {
    const lots = C.st.lots || []; if (!lots.length) return null;
    const curSh = {}; C.st.holdings.forEach((x) => (curSh[x.ticker] = Number(x.shares) || 0));
    let li = -1;
    return H.dates.map((d, i) => {
      while (li + 1 < lots.length && lots[li + 1].d <= d) li++;
      if (li < 0) return null;
      const h = lots[li].h; let t = 0;
      for (const k of Object.keys(H.each)) { const v = H.each[k][i]; if (v == null || !curSh[k]) continue; t += (v * (h[k] || 0)) / curSh[k]; }
      return t;
    });
  }

  // 요인 민감도 (app.js factorBetas 와 같음)
  function alignedChanges(C, symA, symB, rateB, years = 3) {
    const A = C.prices[symA], B = C.prices[symB]; if (!A || !B) return null;
    const cut = M().addMonths(C.today, -12 * years), mb = new Map(B.dates.map((d, i) => [d, B.adj[i]]));
    const xs = [], ys = [], ds = []; let pa = null, pb = null;
    for (let i = 0; i < A.dates.length; i++) {
      const d = A.dates[i]; if (d < cut || !mb.has(d)) continue;
      const a = A.adj[i], b = mb.get(d);
      if (pa != null && a > 0 && pa > 0 && b != null && pb != null && (rateB || (b > 0 && pb > 0))) { ys.push(Math.log(a / pa)); xs.push(rateB ? (b - pb) * 100 : Math.log(b / pb)); ds.push(d); }
      pa = a; pb = b;
    }
    return { x: xs, y: ys, d: ds };
  }
  function factorBetas(C) {
    const out = {}, tks = C.st.holdings.map((h) => h.ticker).filter((t) => C.prices[t]), mean = M().mean;
    for (const [fk, sym] of Object.entries(FACTORS)) {
      out[fk] = {};
      if (!C.prices[sym]) continue;
      for (const t of tks) {
        if (t === sym) { out[fk][t] = 1; continue; }
        const r = alignedChanges(C, t, sym, fk === "rate"); if (!r || r.y.length < 30) continue;
        const n = r.y.length, mx = mean(r.x), my = mean(r.y);
        let sxy = 0, sxx = 0; for (let i = 0; i < n; i++) { sxy += (r.x[i] - mx) * (r.y[i] - my); sxx += (r.x[i] - mx) ** 2; }
        let beta = sxx ? sxy / sxx : 0;
        if (fk !== "mkt" && C.prices.SPY && t !== "SPY") {
          const m2 = alignedChanges(C, t, "SPY", false), mm = new Map(m2 ? m2.d.map((d, i) => [d, m2.x[i]]) : []);
          const X1 = [], X2 = [], Y = []; r.d.forEach((d, i) => { if (mm.has(d)) { X1.push(r.x[i]); X2.push(mm.get(d)); Y.push(r.y[i]); } });
          if (Y.length > 30) {
            const a1 = mean(X1), a2 = mean(X2), ay = mean(Y); let s11 = 0, s22 = 0, s12 = 0, s1y = 0, s2y = 0;
            for (let i = 0; i < Y.length; i++) { const u = X1[i] - a1, v = X2[i] - a2, w = Y[i] - ay; s11 += u * u; s22 += v * v; s12 += u * v; s1y += u * w; s2y += v * w; }
            const det = s11 * s22 - s12 * s12; if (det > 0) beta = (s1y * s22 - s2y * s12) / det;
          }
        }
        const w = n / (n + 60), prior = fk === "mkt" ? 1 : 0;
        out[fk][t] = w * beta + (1 - w) * prior;
      }
    }
    return out;
  }

  // 대시보드 '내 관점' 미래 전망 (app.js buildModelNow + simCommon + forecastFor 와 같음)
  function forecast(C) {
    const g = C.st.goal, start = C.today;
    if (!g || g.date <= start) return null;
    const { rows } = valuation(C);
    const holdings = rows.filter((r) => r.valueKrw > 0).map((r) => ({ ticker: r.h.ticker, shares: r.sh, price0: r.p.v, ccy: r.ccy, valueKrw: r.valueKrw }));
    if (!holdings.length) return null;
    const series = {}; for (const k in C.prices) series[k] = { dates: C.prices[k].dates, adj: C.prices[k].adj };
    const model = M().buildModel({ holdings, series, fxOf: C.fxOf, settings: C.model, events: C.st.events || [], betas: factorBetas(C), startDate: start, goalDate: g.date });
    const m = C.model;
    const R = M().simulate(model, { holdings, scenario: m.scenario, nPaths: Number(m.n_paths), seed: Number(m.seed) || 1, goal: Math.max(1, g.amount - C.cashKrw()),
      monthly: Number(g.monthly_contribution) || 0, rebalance: !!m.rebalance_yearly, dof: m.t_dof, fxOf: C.fxOf, usdKrw0: C.fxNow("USD"), withEvents: true });
    return { R, md: model.monthDates };
  }

  // 주간 적중 기록 (app.js weekRows·weekRecord 와 같음, 단 기록은 바꾸지 않고 이번 주 칸이 없으면 계산만 한다)
  const closedIdx = (H, now) => { let i = H.dates.length - 1; if (i >= 0 && now < Date.parse(H.dates[i] + "T21:00:00Z")) i--; return i; };
  const idxAtOrBefore = (H, d) => { let i = H.dates.length - 1; while (i > 0 && H.dates[i] > d) i--; return i; };
  function weekdays(from, to) {
    const out = [], d = new Date(from + "T00:00:00Z");
    for (let g = 0; g < 4000; g++) { d.setUTCDate(d.getUTCDate() + 1); const s = d.toISOString().slice(0, 10); if (s > to) break; const w = d.getUTCDay(); if (w && w < 6) out.push(s); }
    return out;
  }
  function weekFri(today) {
    const t = new Date(today + "T00:00:00Z"), w = t.getUTCDay();
    t.setUTCDate(t.getUTCDate() + (w === 6 ? 6 : w === 0 ? 5 : 5 - w)); return t.toISOString().slice(0, 10);
  }
  function hitAt(F, d) { // 마지막 마감일 → d 로그 수익률 중앙·퍼짐 (전망 띠에서)
    const B = F.R.bands, fd = F.md, T = (x) => Date.parse(x + "T00:00:00Z");
    const lr = (k) => Math.log(B.p50[k] / B.p50[0]), vr = (k) => ((Math.log(B.p75[k]) - Math.log(B.p25[k])) / 1.349) ** 2;
    let k = 0; while (k + 1 < fd.length - 1 && fd[k + 1] <= d) k++;
    const a = Math.max(0, Math.min(1, (T(d) - T(fd[k])) / Math.max(1, T(fd[k + 1]) - T(fd[k])))), k2 = Math.min(k + 1, fd.length - 1);
    return { mu: lr(k) + a * (lr(k2) - lr(k)), sd: Math.sqrt(vr(k) + a * (vr(k2) - vr(k))) };
  }
  function weeks(C, H, F, cash, now) {
    const o = closedIdx(H, now), last = o >= 0 ? H.dates[o] : "";
    const rows = (C.st.weekly || []).map((r) => {
      const i0 = H.dates.indexOf(r.d0), base = i0 >= 0 ? H.index[i0] : r.i0, done = last >= r.f;
      const j = done ? idxAtOrBefore(H, r.f) : H.dates.length - 1, act = j >= 0 && base ? Math.round(r.base * (H.index[j] / base) + r.cash) : null;
      return { f: r.f, d0: r.d0, base: r.base, cash: r.cash, p50: r.p50, lo: r.lo, hi: r.hi, act, done, hit: act != null && act >= r.lo && act <= r.hi };
    });
    const f = weekFri(C.today);
    let cur = rows.find((r) => !r.done);
    if (!cur && o >= 0 && F && H.total[o] > 0 && !rows.some((r) => r.f === f)) { // 이번 주를 아직 사이트에서 안 열었으면 같은 방식으로 계산만
      const n = weekdays(H.dates[o], f).length;
      if (n) { const { mu, sd } = hitAt(F, f), b = H.total[o], v = (z) => Math.round(b * Math.exp(mu + z * sd) + cash);
        cur = { f, d0: H.dates[o], base: Math.round(b), cash: Math.round(cash), p50: v(0), lo: v(-0.674), hi: v(0.674), act: Math.round(H.total[H.total.length - 1] + cash), done: false, tent: true }; }
    }
    return { done: rows.filter((r) => r.done).slice(-13), cur };
  }

  // 위젯 요약. pre: 사이트가 이미 계산해 둔 전망 { R, md } (있으면 다시 계산하지 않음)
  function summary(S, pre) {
    const C = ctx(S), now = S.now || Date.now(), g = C.st.goal;
    const H = history(C); if (!H.dates.length) return null;
    const cash = C.cashKrw(), V = valuation(C), k = H.dates.length - 1;
    let F = pre && pre.R && pre.md ? pre : null;
    if (!F) F = forecast(C);
    const r6 = (x) => (x == null || !isFinite(x) ? null : Math.round(x / 1e4)); // 만원 단위 정수 (작게)
    const out = { v: VERSION, at: now, asof: H.dates[k], today: C.today, cash: Math.round(cash), goal: { amount: g.amount, date: g.date, start: g.start_date || C.today } };
    // 목표 시작일 평가액 (필요 경로의 출발점)
    const A = actualSeries(C, H) || H.total, st = out.goal.start; let i0 = H.dates.findIndex((d) => d >= st); if (i0 < 0) i0 = k;
    out.goal.v0 = Math.round((A[i0] ?? H.total[i0]) + cash);
    out.hold = V.rows.filter((r) => r.sh > 0).map((r) => ({ t: r.h.ticker, sh: r.sh, ccy: r.ccy, manual: C.st.ui?.manual_price && Number(r.h.price) > 0 ? Number(r.h.price) : undefined }));
    out.spark = H.total.slice(-63).map((v) => r6(v + cash)); // 최근 3달 일별 합계 (마지막은 최근 종가)
    if (F && F.R && F.R.bands) {
      const B = F.R.bands, add = (a) => a.map((x) => r6(x + cash));
      out.fc = { scen: C.model.scenario, md: F.md, p5: add(B.p5), p25: add(B.p25), p50: add(B.p50), p75: add(B.p75), p95: add(B.p95), pg: F.R.p_goal,
        t: F.R.terminal && { p5: r6(F.R.terminal.p5 + cash), p50: r6(F.R.terminal.p50 + cash), p95: r6(F.R.terminal.p95 + cash) } };
    }
    const W = weeks(C, H, F, cash, now);
    out.hits = W.done.map((r) => ({ f: r.f, h: r.hit ? 1 : 0 }));
    if (W.cur) { const c = W.cur; out.week = { f: c.f, p50: c.p50, lo: c.lo, hi: c.hi, base: c.base, cash: c.cash, t0: Math.round(H.total[Math.max(0, H.dates.indexOf(c.d0))] || c.base), tent: !!c.tent }; }
    out.prev = Math.round((k > 0 ? H.total[k - 1] : H.total[k]) + cash); // 다이어그램 '어제보다' 기준
    const evs = [];
    for (const e of C.st.events || []) { if (!e.on || !e.date) continue; const ds = M().occurrences(e, addDays(C.today, -1), g.date); if (ds.length) evs.push({ d: ds[0], t: e.target || "", k: e.kind || "" }); }
    out.events = evs.sort((a, b) => (a.d < b.d ? -1 : 1)).slice(0, 5);
    return out;
  }
  function addDays(d, n) { const t = new Date(d + "T00:00:00Z"); t.setUTCDate(t.getUTCDate() + n); return t.toISOString().slice(0, 10); }

  window.WidgetCore = { VERSION, summary, forecast, ctx, history, valuation };
})();
