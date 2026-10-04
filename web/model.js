// 계산 모듈: 화면과 무관한 순수 계산만 둔다 (칼만 필터, 이동평균, 몬테카를로 전망).
// 모든 계산은 브라우저 안에서 끝나며 외부 호출이 없다.
(function () {
  "use strict";
  const TD = 252; // 연간 거래일

  // ------------------------------------------------------------ 기본 도구
  const mean = (a) => a.reduce((s, x) => s + x, 0) / a.length;
  const std = (a) => {
    if (a.length < 2) return 0;
    const m = mean(a);
    return Math.sqrt(a.reduce((s, x) => s + (x - m) * (x - m), 0) / (a.length - 1));
  };
  function ema(a, span) {
    const k = 2 / (span + 1), out = new Array(a.length);
    let e = a[0];
    for (let i = 0; i < a.length; i++) { e = i ? e + k * (a[i] - e) : a[0]; out[i] = e; }
    return out;
  }
  function quantileSorted(s, q) {
    const pos = (s.length - 1) * q, lo = Math.floor(pos), hi = Math.ceil(pos);
    return s[lo] + (s[hi] - s[lo]) * (pos - lo);
  }
  const iso = (d) => d.toISOString().slice(0, 10);
  const parseDate = (s) => new Date(s + "T00:00:00Z");
  function addMonths(s, n) {
    const d = parseDate(s), day = d.getUTCDate();
    d.setUTCDate(1); d.setUTCMonth(d.getUTCMonth() + n);
    const last = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0)).getUTCDate();
    d.setUTCDate(Math.min(day, last));
    return iso(d);
  }

  // 시드 고정 난수 (xoshiro128**) + 정규분포 (Box-Muller)
  function makeRng(seed) {
    let a = seed >>> 0, b = 0x9e3779b9, c = 0x243f6a88, d = 0xb7e15162;
    const next = () => {
      const r = Math.imul(rotl(Math.imul(b, 5), 7), 9) >>> 0;
      const t = b << 9;
      c ^= a; d ^= b; b ^= c; a ^= d; c ^= t; d = rotl(d, 11);
      return r / 4294967296;
    };
    function rotl(x, k) { return (x << k) | (x >>> (32 - k)); }
    for (let i = 0; i < 20; i++) next();
    let spare = null;
    const normal = () => {
      if (spare !== null) { const s = spare; spare = null; return s; }
      let u = 0; while (u === 0) u = next();
      const v = next(), r = Math.sqrt(-2 * Math.log(u)), th = 2 * Math.PI * v;
      spare = r * Math.sin(th);
      return r * Math.cos(th);
    };
    return { next, normal };
  }

  function cholesky(A) {
    const n = A.length, L = A.map(() => new Array(n).fill(0));
    for (let i = 0; i < n; i++) {
      for (let j = 0; j <= i; j++) {
        let s = A[i][j];
        for (let k = 0; k < j; k++) s -= L[i][k] * L[j][k];
        if (i === j) { if (s <= 1e-10) return null; L[i][i] = Math.sqrt(s); }
        else L[i][j] = s / L[j][j];
      }
    }
    return L;
  }

  // ------------------------------------------------------------ 칼만 필터 (국소 선형 추세)
  // 로그가격 y 에 대해 상태 [수준, 기울기] 를 추정. x_t = F x_{t-1} + w, y_t = x_t[0] + v
  function kalmanTrend(y, qL, qS, r) {
    let x0 = y[0], x1 = 0, p00 = r * 10, p01 = 0, p11 = 1e-4, ll = 0;
    const n = y.length, lv = new Array(n), sl = new Array(n), slv = new Array(n);
    for (let t = 0; t < n; t++) {
      if (t > 0) {
        x0 = x0 + x1;
        const n00 = p00 + 2 * p01 + p11 + qL, n01 = p01 + p11, n11 = p11 + qS;
        p00 = n00; p01 = n01; p11 = n11;
      }
      const s = p00 + r, e = y[t] - x0, k0 = p00 / s, k1 = p01 / s;
      x0 += k0 * e; x1 += k1 * e;
      const q00 = p00 - k0 * p00, q01 = p01 - k0 * p01, q11 = p11 - k1 * p01;
      p00 = q00; p01 = q01; p11 = q11;
      if (t > 20) ll += -0.5 * (Math.log(2 * Math.PI * s) + (e * e) / s);
      lv[t] = x0; sl[t] = x1; slv[t] = p11;
    }
    return { lv, sl, slv, ll };
  }
  function fitKalman(y) {
    const d = []; for (let i = 1; i < y.length; i++) d.push(y[i] - y[i - 1]);
    const v = Math.max(std(d) ** 2, 1e-12);
    let best = null;
    for (const a of [0.5, 0.8, 0.95]) for (const b of [1e-6, 1e-5, 1e-4, 1e-3]) {
      const qL = a * v, qS = b * v, r = (1 - a) * v / 2 + 1e-10;
      const ll = kalmanTrend(y, qL, qS, r).ll;
      if (!best || ll > best.ll) best = { ll, qL, qS, r };
    }
    // 최대우도는 기울기 잡음을 거의 0 으로 고르는 경향이 있어, 수개월 단위 추세 변화를 따라가도록 하한을 둔다
    const qS = Math.max(best.qS, 1e-5 * v);
    return kalmanTrend(y, best.qL, qS, best.r);
  }

  // 종목별 지표: 이동평균, 칼만 수준·기울기, EWMA 변동성, 추세 판정
  function indicators(dates, close) {
    const n = close.length;
    if (n < 3) return null;
    const y = close.map(Math.log);
    const k = fitKalman(y);
    const ret = [0]; for (let i = 1; i < n; i++) ret.push(y[i] - y[i - 1]);
    const ewv = []; let v2 = ret.slice(1, 21).reduce((s, x) => s + x * x, 0) / Math.max(1, Math.min(20, n - 1));
    for (let i = 0; i < n; i++) { v2 = 0.94 * v2 + 0.06 * ret[i] * ret[i]; ewv.push(Math.sqrt(v2 * TD)); }
    const e20 = ema(close, 20), e50 = ema(close, 50), e200 = ema(close, 200);
    const level = k.lv.map(Math.exp), slopeAnn = k.sl.map((s) => s * TD), slopeSd = k.slv.map((s) => Math.sqrt(s) * TD);
    const last = n - 1, volHist = std(ret.slice(1)) * Math.sqrt(TD);
    let peak = -Infinity, mdd = 0;
    for (const c of close) { peak = Math.max(peak, c); mdd = Math.min(mdd, c / peak - 1); }
    const sig = {
      n, close: close[last], ema20: e20[last], ema50: e50[last], ema200: n >= 200 ? e200[last] : null,
      kalman_level: level[last], slope_ann: slopeAnn[last], slope_sd_ann: slopeSd[last],
      slope_z: slopeAnn[last] / slopeSd[last], dev_from_kalman: Math.log(close[last] / level[last]),
      vol_hist: volHist, vol_ewma: ewv[last], drawdown: close[last] / Math.max(...close) - 1, mdd,
      ret_1m: close[last] / close[Math.max(0, n - 22)] - 1, ret_3m: close[last] / close[Math.max(0, n - 64)] - 1,
      ret_1y: n > 253 ? close[last] / close[n - 253] - 1 : null,
    };
    sig.trend = trendLabel(sig);
    return { dates, close, ema20: e20, ema50: e50, ema200: e200, level, slopeAnn, slopeSd, ewmaVol: ewv, sig };
  }
  function trendLabel(s) {
    if (s.vol_hist < 0.03) return "현금성 (추세 판단 대상 아님)";
    if (s.n < 200) return "판단 보류 (데이터 200일 미만)";
    const k = [s.close > s.ema200, s.ema50 > s.ema200, s.slope_z > 1].filter(Boolean).length;
    return ["하락 추세", "약세 전환 주의", "약한 상승", "상승 추세"][k];
  }

  // 스무딩: 최근 years 년 로그가격에 직선을 맞춘 값 (연 성장률 slope, 날짜별 맞춘 가격 fitAt)
  function smoothFit(dates, close, years = 3) {
    if (!dates.length) return null;
    const cut = iso(new Date(parseDate(dates[dates.length - 1]).getTime() - years * 365.25 * 86400e3));
    const t = [], y = [];
    dates.forEach((d, i) => { if (d >= cut && close[i] > 0) { t.push(parseDate(d).getTime() / (365.25 * 86400e3)); y.push(Math.log(close[i])); } });
    if (t.length < 20) return null;
    const mt = mean(t), my = mean(y); let sxy = 0, sxx = 0;
    for (let i = 0; i < t.length; i++) { sxy += (t[i] - mt) * (y[i] - my); sxx += (t[i] - mt) ** 2; }
    const b = sxx > 0 ? sxy / sxx : 0, a = my - b * mt;
    return { slope: b, n: t.length, from: cut, fitAt: (d) => (d >= cut ? Math.exp(a + b * parseDate(d).getTime() / (365.25 * 86400e3)) : null) };
  }
  // 추세 (칼만·EMA): 칼만 기울기와 EMA50·EMA200 간격으로 본 기울기의 평균 (연, 로그)
  function trendGrowth(ind) {
    const s = ind.sig, k = s.slope_ann;
    if (!s.ema200) return k;
    return (k + Math.log(s.ema50 / s.ema200) / (75 / TD)) / 2; // EMA50 과 EMA200 의 무게중심 차이 약 75거래일
  }

  // ------------------------------------------------------------ 전망 모형 입력 만들기
  // holdings: [{ticker, shares, price0, ccy, valueKrw}], series: {sym: {dates, adj}}, fxSym(ccy)
  function buildModel(opt) {
    const { holdings, series, fxOf, settings, events, startDate, goalDate } = opt;
    const m = settings;
    const yrsWin = Number(m.history_years) || 3;
    const cutoff = iso(new Date(parseDate(startDate).getTime() - yrsWin * 365.25 * 86400e3));

    // 요인: 보유 종목 + 쓰이는 환율
    const factors = holdings.map((h) => ({ key: h.ticker, kind: "asset", ccy: h.ccy }));
    const fxKeys = [...new Set(holdings.map((h) => fxOf(h.ccy)).filter(Boolean))];
    for (const f of fxKeys) factors.push({ key: f, kind: "fx" });

    // 공통 날짜축에 맞춘 로그수익률
    const allDates = new Set();
    for (const f of factors) { const s = series[f.key]; if (s) s.dates.forEach((d) => d >= cutoff && allDates.add(d)); }
    const cal = [...allDates].sort();
    const idx = new Map(cal.map((d, i) => [d, i]));
    for (const f of factors) {
      const s = series[f.key];
      f.ret = new Array(cal.length).fill(null);
      if (!s) { f.n = 0; continue; }
      let prev = null;
      for (let i = 0; i < s.dates.length; i++) {
        const d = s.dates[i], v = s.adj[i];
        if (d < cutoff || !(v > 0)) continue;
        if (prev !== null) f.ret[idx.get(d)] = Math.log(v / prev);
        prev = v;
      }
      const r = f.ret.filter((x) => x !== null);
      f.n = r.length;
      f.volRaw = r.length > 1 ? std(r) * Math.sqrt(TD) : null;
      f.meanD = r.length ? mean(r) : 0;
      // 최근 1년 수익률 (현금성 판단용)
      if (s.adj.length > 1) {
        const last = s.adj[s.adj.length - 1], j = Math.max(0, s.adj.length - 253);
        f.ret1y = (last / s.adj[j]) ** (TD / Math.max(1, s.adj.length - 1 - j)) - 1;
      }
    }

    const newVol = m.new_listing_vol / 100, defVol = m.default_vol / 100, defCorr = Number(m.default_corr);
    for (const f of factors) {
      f.cash = f.kind === "asset" && f.volRaw !== null && f.n > 20 && f.volRaw < 0.03;
      if (f.kind === "fx") {
        f.vol = (f.volRaw ?? 0.10) * (Number(m.fx_vol_mult) || 1);
      } else if (f.volRaw === null || f.n < 5) {
        f.vol = defVol;
      } else if (f.n < TD && !f.cash) {
        const w = f.n / (f.n + TD); // 짧은 이력은 기준 변동성과 섞는다
        f.vol = Math.sqrt(w * f.volRaw ** 2 + (1 - w) * newVol ** 2);
      } else f.vol = f.volRaw;
    }

    // 상관행렬 (쌍별로 겹치는 날만)
    const F = factors.length, C = [];
    for (let i = 0; i < F; i++) {
      C.push(new Array(F).fill(0)); C[i][i] = 1;
      for (let j = 0; j < i; j++) {
        const a = [], b = [];
        for (let t = 0; t < cal.length; t++) {
          const x = factors[i].ret[t], y = factors[j].ret[t];
          if (x !== null && y !== null) { a.push(x); b.push(y); }
        }
        const riskPair = !(factors[i].cash || factors[j].cash || factors[i].kind === "fx" || factors[j].kind === "fx");
        const prior = riskPair ? defCorr : 0;
        let c = prior;
        if (a.length >= 40) {
          const ma = mean(a), mb = mean(b);
          let sab = 0, saa = 0, sbb = 0;
          for (let t = 0; t < a.length; t++) { sab += (a[t] - ma) * (b[t] - mb); saa += (a[t] - ma) ** 2; sbb += (b[t] - mb) ** 2; }
          c = saa > 0 && sbb > 0 ? sab / Math.sqrt(saa * sbb) : 0;
          if (a.length < TD) { const w = a.length / (a.length + TD); c = w * c + (1 - w) * prior; }
        }
        C[i][j] = C[j][i] = Math.max(-0.95, Math.min(0.95, c));
      }
    }
    let L = null, lam = 0;
    while (!(L = cholesky(C.map((r, i) => r.map((v, j) => (i === j ? 1 : v * (1 - lam))))))) lam += 0.05;

    // 기대수익률 (연, 산술): 과거 평균을 사전값 쪽으로 수축 (베이즈 정규-정규)
    const prior = m.prior_mu / 100, tau = m.prior_tau / 100;
    for (const f of factors) {
      if (f.kind === "fx") { f.mu = { base: m.fx_drift / 100, conservative: m.fx_drift / 100, history: m.fx_drift / 100 }; f.muHist = null; continue; }
      if (f.cash) { const rf = f.ret1y ?? 0.04; f.mu = { base: rf, conservative: rf, history: rf }; f.muHist = rf; f.shrink = 1; continue; }
      if (f.n < 5) { f.mu = { base: prior, conservative: m.conservative_mu / 100, history: prior }; f.muHist = null; f.shrink = 0; continue; }
      const arith = f.meanD * TD + 0.5 * f.volRaw ** 2;
      const se2 = f.vol ** 2 / (f.n / TD), k = tau * tau / (tau * tau + se2);
      f.muHist = arith; f.shrink = k;
      const base = k * arith + (1 - k) * prior;
      f.mu = { base, conservative: m.conservative_mu / 100, history: f.n >= TD ? arith : base };
    }

    // 미래 거래일(평일)과 월 경계
    const days = [];
    for (let d = new Date(parseDate(startDate).getTime() + 86400e3); iso(d) <= goalDate; d = new Date(d.getTime() + 86400e3)) {
      const w = d.getUTCDay(); if (w !== 0 && w !== 6) days.push(iso(d));
    }
    const monthIdx = [0], monthDates = [startDate];
    for (let i = 1; i < days.length; i++) if (days[i].slice(0, 7) !== days[i - 1].slice(0, 7)) { monthIdx.push(i + 1); monthDates.push(days[i]); }
    if (monthIdx[monthIdx.length - 1] !== days.length) { monthIdx.push(days.length); monthDates.push(days[days.length - 1]); }
    // monthIdx 는 경로 배열 기준 (0 = 시작, i+1 = days[i] 장마감)

    // 사건 펼치기 (분기 반복 포함) → 거래일 번호
    const dayOf = (s) => { let lo = 0, hi = days.length; while (lo < hi) { const mid = (lo + hi) >> 1; if (days[mid] < s) lo = mid + 1; else hi = mid; } return lo; };
    const keyIdx = new Map(factors.map((f, i) => [f.key, i]));
    const fxIdx = (ccy) => keyIdx.get(fxOf(ccy));
    const sched = [], eventList = [];
    for (const e of events || []) {
      if (!e.on || !e.date) continue;
      let targets;
      if (e.target === "ALL") targets = factors.map((f, i) => (f.kind === "asset" && !f.cash ? i : -1)).filter((i) => i >= 0);
      else if (e.target === "FX") targets = [fxIdx("USD")].filter((i) => i !== undefined);
      else targets = [keyIdx.get(e.target)].filter((i) => i !== undefined && factors[i].kind === "asset");
      if (!targets.length) continue;
      const sd = Math.max(0, Number(e.sd) || 0) / 100, mn = (Number(e.mean) || 0) / 100;
      const ev = { prob: Math.min(1, Math.max(0, (Number.isFinite(Number(e.prob)) && e.prob !== "" ? Number(e.prob) : 100) / 100)), jm: Math.log(Math.max(0.01, 1 + mn)) - 0.5 * sd * sd,
        js: sd, vm: Math.max(0.1, Number(e.vol_mult) || 1), vd: Math.max(0, Math.round(Number(e.vol_days) || 0)), targets, src: e };
      for (let k = 0, d = e.date; d <= goalDate && k < 400; k++, d = addMonths(e.date, 3 * k)) {
        if (d <= startDate) { if (e.repeat !== "quarterly") break; continue; }
        const di = dayOf(d); if (di >= days.length) break;
        sched.push({ day: di, ev }); eventList.push({ date: days[di], event: e });
        if (e.repeat !== "quarterly") break;
      }
      // 반복 실적 사건은 과거 변동성에 이미 들어 있으므로 평소 변동성에서 그만큼 뺀다
      if (m.earnings_adjust && e.repeat === "quarterly") for (const t of targets) {
        factors[t].evVar = (factors[t].evVar || 0) + 4 * ev.prob * sd * sd;
      }
    }
    for (const f of factors) {
      f.volDiff = f.evVar ? Math.sqrt(Math.max(f.vol ** 2 - f.evVar, 0.5 * f.vol ** 2)) : f.vol;
    }
    // 추종 시나리오: 스무딩(3년 직선) 또는 추세(칼만·EMA)의 연 성장률을 그대로 이어 간다.
    // 중앙값 성장률이 g 가 되도록 산술 기대수익으로 바꾸고, 이력이 1년보다 짧으면 기준 시나리오와 섞는다
    for (const f of factors) {
      const s = series[f.key];
      if (f.kind === "fx" || f.cash || f.n < 5 || !s) { f.mu.smooth = f.mu.trend = f.mu.base; continue; }
      const sf = smoothFit(s.dates, s.adj, yrsWin), ind = indicators(s.dates, s.adj);
      f.gSmooth = sf ? sf.slope : null; f.gTrend = ind ? trendGrowth(ind) : null;
      const w = Math.min(1, f.n / TD);
      const conv = (g) => (g == null ? f.mu.base : w * (Math.exp(Math.max(-0.9, Math.min(1.2, g)) + 0.5 * f.volDiff ** 2) - 1) + (1 - w) * f.mu.base);
      f.mu.smooth = conv(f.gSmooth); f.mu.trend = conv(f.gTrend);
    }
    const byDay = new Map();
    for (const s of sched) { if (!byDay.has(s.day)) byDay.set(s.day, []); byDay.get(s.day).push(s.ev); }

    return { factors, L, corr: C, corrShrink: lam, days, monthIdx, monthDates, byDay, eventList, startDate, goalDate };
  }

  // ------------------------------------------------------------ 몬테카를로
  // 일별 다변량 t 충격 + 사건 점프. 반환: 월별 원화 평가액 백분위, 목표 확률 등
  function simulate(model, opt) {
    const { holdings, scenario, nPaths, seed, goal, monthly, rebalance, withEvents, dof } = opt;
    const { factors, L, days, monthIdx, byDay } = model;
    const F = factors.length, A = holdings.length, D = days.length, M = monthIdx.length;
    const nu = Math.max(3, Number(dof) || 5), tAdj = Math.sqrt((nu - 2) / nu);
    const rng = makeRng(seed);
    const sig = factors.map((f) => f.volDiff / Math.sqrt(TD));
    const muD = factors.map((f) => Math.log(1 + f.mu[scenario]) / TD);
    const fxCol = holdings.map((h) => factors.findIndex((f) => f.kind === "fx" && f.key === opt.fxOf(h.ccy)));
    const v0 = holdings.map((h) => h.valueKrw), V0 = v0.reduce((s, x) => s + x, 0);
    const w = v0.map((x) => x / V0);
    const isMonth = new Int32Array(D + 1).fill(-1); monthIdx.forEach((d, k) => (isMonth[d] = k));

    const port = new Float64Array(M * nPaths), noContrib = new Float64Array(nPaths), annuity = new Float64Array(nPaths);
    const stock = holdings.map(() => new Float64Array(M * nPaths));
    const valK = holdings.map(() => new Float64Array(M * nPaths)), fxLv = new Float64Array(M * nPaths);
    const usd0 = Number(opt.usdKrw0) || 1, usdCol = factors.findIndex((f) => f.kind === "fx" && f.key === "KRW=X");
    const firstHit = new Int32Array(nPaths).fill(-1), mddArr = new Float64Array(nPaths), touched = new Uint8Array(nPaths);
    const z = new Float64Array(F), x = new Float64Array(F), lg = new Float64Array(F), lr = new Float64Array(F);
    const boostUntil = new Int32Array(F), boostMult = new Float64Array(F);
    const h = new Float64Array(A), G = new Float64Array(A), S = new Float64Array(A);

    for (let p = 0; p < nPaths; p++) {
      lg.fill(0); boostUntil.fill(-1); boostMult.fill(1);
      for (let a = 0; a < A; a++) { h[a] = v0[a]; G[a] = 1; S[a] = 0; }
      let peak = V0, mdd = 0;
      port[p] = V0; fxLv[p] = usd0; for (let a = 0; a < A; a++) { stock[a][p] = holdings[a].price0; valK[a][p] = v0[a]; }
      for (let d = 0; d < D; d++) {
        for (let f = 0; f < F; f++) z[f] = rng.normal();
        let chi = 0; for (let k = 0; k < nu; k++) { const g = rng.normal(); chi += g * g; }
        const ts = tAdj / Math.sqrt(chi / nu);
        for (let f = 0; f < F; f++) {
          let s = 0; const Lf = L[f]; for (let k = 0; k <= f; k++) s += Lf[k] * z[k];
          const sd = sig[f] * (d <= boostUntil[f] ? boostMult[f] : 1);
          lr[f] = muD[f] - 0.5 * sd * sd + sd * s * ts;
        }
        if (withEvents) {
          const evs = byDay.get(d);
          if (evs) for (const ev of evs) {
            if (rng.next() >= ev.prob) continue;
            const j = ev.jm + ev.js * rng.normal(); // 대상이 여럿이면 같은 충격
            for (const t of ev.targets) {
              lr[t] += j;
              if (ev.vd > 0) { boostUntil[t] = d + ev.vd; boostMult[t] = ev.vm; }
            }
          }
        }
        let V = 0;
        for (let a = 0; a < A; a++) {
          const g = lr[a] + (fxCol[a] >= 0 ? lr[fxCol[a]] : 0);
          const e = Math.exp(g); h[a] *= e; G[a] *= e; V += h[a];
        }
        for (let f = 0; f < F; f++) lg[f] += lr[f];
        const k = isMonth[d + 1];
        if (k > 0 && k < M - 1) { // 월초 적립 (마지막 날 제외)
          if (monthly > 0) { for (let a = 0; a < A; a++) h[a] += monthly * w[a]; V += monthly; }
          for (let a = 0; a < A; a++) S[a] += 1 / G[a];
        }
        if (rebalance && (d + 1) % TD === 0) for (let a = 0; a < A; a++) h[a] = V * w[a];
        if (V > peak) peak = V; else mdd = Math.min(mdd, V / peak - 1);
        if (V >= goal) { touched[p] = 1; if (firstHit[p] < 0) firstHit[p] = d + 1; }
        if (k > 0) {
          port[k * nPaths + p] = V; fxLv[k * nPaths + p] = usdCol >= 0 ? usd0 * Math.exp(lg[usdCol]) : usd0;
          for (let a = 0; a < A; a++) { stock[a][k * nPaths + p] = holdings[a].price0 * Math.exp(lg[a]); valK[a][k * nPaths + p] = h[a]; }
        }
      }
      mddArr[p] = mdd;
      let nc = 0, an = 0;
      for (let a = 0; a < A; a++) { nc += v0[a] * G[a]; an += w[a] * G[a] * S[a]; }
      noContrib[p] = nc; annuity[p] = an;
    }

    const qs = [0.05, 0.25, 0.5, 0.75, 0.95], names = ["p5", "p25", "p50", "p75", "p95"];
    const bandsOf = (arr, scale = 1) => {
      const out = Object.fromEntries(names.map((n) => [n, []]));
      const tmp = new Float64Array(nPaths);
      for (let k = 0; k < M; k++) {
        for (let p = 0; p < nPaths; p++) tmp[p] = arr[k * nPaths + p];
        tmp.sort();
        qs.forEach((q, i) => out[names[i]].push(quantileSorted(tmp, q) * scale));
      }
      return out;
    };
    const bands = bandsOf(port);
    const perUsd = (arr) => arr.map((v, i) => v / fxLv[i]);
    const bandsUsd = bandsOf(perUsd(port));
    const term = Array.from(port.subarray((M - 1) * nPaths)).sort((a, b) => a - b);
    const invested = V0 + monthly * Math.max(0, M - 2);
    const nMonthsContrib = Math.max(0, M - 2);
    // 목표확률 50% 를 맞추는 월 적립액 (재조정 없음 가정, 이분법)
    let req50 = null;
    if (!rebalance) {
      const ok = (c) => { let n = 0; for (let p = 0; p < nPaths; p++) if (noContrib[p] + c * annuity[p] >= goal) n++; return n / nPaths >= 0.5; };
      if (ok(0)) req50 = 0; else { let lo = 0, hi = 1e9; for (let i = 0; i < 60; i++) { const c = (lo + hi) / 2; if (ok(c)) hi = c; else lo = c; } req50 = hi; }
    }
    // 연도별 누적 도달 확률 (경로 중 한 번이라도 목표 이상)
    const byYear = [];
    for (let y = 1; ; y++) {
      const target = addMonths(model.startDate, 12 * y);
      let lim = 0; while (lim < D && days[lim] <= target) lim++;
      if (lim === 0) continue;
      let n = 0; for (let p = 0; p < nPaths; p++) if (firstHit[p] >= 0 && firstHit[p] <= lim) n++;
      byYear.push({ year: y, date: days[lim - 1], p: n / nPaths });
      if (lim >= D) break;
    }
    const mdds = Array.from(mddArr).sort((a, b) => a - b);
    return {
      V0, invested, monthsContrib: nMonthsContrib, bands, bandsUsd,
      p_goal: term.filter((v) => v >= goal).length / nPaths,
      p_touch: touched.reduce((s, x) => s + x, 0) / nPaths,
      p_loss: term.filter((v) => v < invested).length / nPaths,
      terminal: { p5: quantileSorted(term, 0.05), p25: quantileSorted(term, 0.25), p50: quantileSorted(term, 0.5), p75: quantileSorted(term, 0.75), p95: quantileSorted(term, 0.95), mean: mean(term) },
      mdd_median: quantileSorted(mdds, 0.5), mdd_p10: quantileSorted(mdds, 0.1),
      req50, byYear,
      stocks: holdings.map((hd, a) => {
        const b = bandsOf(stock[a]);
        let up = 0; for (let p = 0; p < nPaths; p++) if (stock[a][(M - 1) * nPaths + p] > hd.price0) up++;
        return { ticker: hd.ticker, bands: b, p_up: up / nPaths, valBands: bandsOf(valK[a]), valBandsUsd: bandsOf(perUsd(valK[a])) };
      }),
    };
  }

  window.Model = { TD, indicators, buildModel, simulate, addMonths, quantileSorted, mean, std, smoothFit, trendGrowth };
})();
