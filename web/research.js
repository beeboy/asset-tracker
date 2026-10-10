// 연구용 비교: 세금 고려 + 하방 보호 피드백 (확률적 파라미터 앙상블 위의 제어기)
// 기존 3년 전망(model.js simulate)은 그대로 두고, 그 엔진이 만든 경로 위에서 "지금 그대로 보유"와 비교만 한다.
// 1) 식단(θ = 변동성·상관·기대수익) 을 여러 벌 뽑는다: 지난 3년 수익률 블록 재표본 + 기대수익 사후분포 + 시장 공통 오차 β·m
// 2) 제어기는 진짜 식단을 모르고 식단 분포(평균 m, 공분산 Ω)만 안다. 목표 확률은 μ 를 적분한 닫힌식으로 계산
//    (분산에 τ²·wᵀΩw 가 붙어 외란 방향 노출을 직접 줄인다)
// 3) 해마다: 실현 수익으로 m, Ω 를 칼만 갱신 → 비중 후보마다 옮길 때 낼 양도세를 빼고
//    점수 = 목표 확률 − λ·(만기 평가액이 지금의 60% 아래일 확률) 이 가장 큰 비중으로 옮긴다
// 4) (선택) 금리 반영: data/macro 의 금리→이익 관계식 식단(실러 데이터, 1000벌) 중 하나를 식단마다 뽑아
//    "1년 금리 변화 × 기울기" 로 나온 앞으로 1~3년 S&P 이익 변화를 목표 기간 연평균으로 바꿔 시장 공통 오차에 더한다 (β 배).
//    같은 식단에서 금리 몫만 뺀 쌍둥이로 "금리 미반영 보유" 도 같이 돌려 비교한다
(function () {
  "use strict";
  const M = window.Model, TD = M.TD;
  const TAX = 0.22, DED = 2.5e6, BLOCK = 21;

  function rngOf(seed) {
    let a = seed >>> 0, spare = null;
    const next = () => { a = (a + 0x6d2b79f5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
    const normal = () => { if (spare !== null) { const s = spare; spare = null; return s; } let u, v, s; do { u = 2 * next() - 1; v = 2 * next() - 1; s = u * u + v * v; } while (!s || s >= 1); const k = Math.sqrt(-2 * Math.log(s) / s); spare = v * k; return u * k; };
    return { next, normal };
  }
  const q = (arr, p) => { const a = Float64Array.from(arr).sort(); return M.quantileSorted(a, p); };
  const Phi = (x) => { const t = 1 / (1 + 0.2316419 * Math.abs(x)), d = 0.3989423 * Math.exp(-x * x / 2), p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274)))); return x > 0 ? 1 - p : p; };
  function cholesky(A) {
    const n = A.length, L = A.map(() => new Array(n).fill(0));
    for (let i = 0; i < n; i++) for (let j = 0; j <= i; j++) {
      let s = A[i][j]; for (let k = 0; k < j; k++) s -= L[i][k] * L[j][k];
      if (i === j) { if (s <= 1e-10) return null; L[i][i] = Math.sqrt(s); } else L[i][j] = s / L[j][j];
    }
    return L;
  }
  const matInv = (A0) => { const n = A0.length, a = A0.map((r, i) => [...r, ...A0.map((_, j) => (i === j ? 1 : 0))]);
    for (let c = 0; c < n; c++) { let p = c; for (let r = c + 1; r < n; r++) if (Math.abs(a[r][c]) > Math.abs(a[p][c])) p = r; [a[c], a[p]] = [a[p], a[c]];
      const d = a[c][c]; for (let k = 0; k < 2 * n; k++) a[c][k] /= d;
      for (let r = 0; r < n; r++) if (r !== c) { const f = a[r][c]; for (let k = 0; k < 2 * n; k++) a[r][k] -= f * a[c][k]; } }
    return a.map((r) => r.slice(n)); };
  const mul = (X, Y) => X.map((r) => Y[0].map((_, j) => r.reduce((s, x, k) => s + x * Y[k][j], 0)));

  // inp: {holdings: [{ticker, ccy, price0, valueKrw, avgCost}], series, settings, events, betas, mktBeta: {ticker: β}, startDate, goal: {amount, date}, opt,
  //       rate?: {b: [[b12, b24, b36], ...] (%/%p), dr: 지금 1년 금리 변화 %p}}
  function stabilizer(inp, onProg) {
    const o = { meals: 300, trueMeals: 60, paths: 25, step: 10, lambda: 1, floor: 0.6, tauM: 0.06, ...(inp.opt || {}) };
    const set = inp.settings, scen = set.scenario, holdings = inp.holdings, A = holdings.length, goal = inp.goal;
    const fxOf = (ccy) => (!ccy || ccy === "KRW" ? null : ccy === "USD" ? "KRW=X" : ccy + "KRW=X");
    const model = M.buildModel({ holdings, series: inp.series, fxOf, settings: set, events: inp.events, betas: inp.betas, startDate: inp.startDate, goalDate: goal.date });
    const F = model.factors.length, fxi = model.factors.findIndex((f) => f.kind === "fx" && f.key === "KRW=X");
    const V0 = holdings.reduce((s, h) => s + h.valueKrw, 0), w0 = holdings.map((h) => h.valueKrw / V0);
    const T = (Date.parse(goal.date) - Date.parse(inp.startDate)) / (365.25 * 86400e3), lnG = Math.log(goal.amount), floorLn = Math.log(V0 * o.floor);
    const cash = model.factors.slice(0, A).map((f) => !!f.cash);
    const beta = holdings.map((h, a) => (cash[a] ? 0 : Number(inp.mktBeta?.[h.ticker]) || 1));
    const ciIdx = cash.indexOf(true);
    const rng = rngOf((Number(set.seed) || 1) ^ 0x5eed);
    const prog = (k, n) => onProg && onProg(k, n);
    // 금리 몫: 식단 j 의 앞으로 y년째 이익 변화(로그 %) = b[j][y]·dr. 목표 기간 T 에 걸친 합을 연평균으로
    const rate = inp.rate && inp.rate.b && inp.rate.b.length && Number.isFinite(inp.rate.dr) ? inp.rate : null;
    const rateShift = (j) => { const b = rate.b[j]; let s = 0; for (let y = 0; y < b.length; y++) s += b[y] * rate.dr * Math.max(0, Math.min(1, T - y)); return s / 100 / Math.max(T, 0.25); };

    // ---- 식단 한 벌: Σ 는 블록 재표본, μ 는 μ̂ + β·m + 고유 오차
    const Tn = model.factors[0].ret.length;
    const postVar = holdings.map((_, a) => { const f = model.factors[a]; if (cash[a] || !(f.n >= 5)) return 0; const se2 = f.vol ** 2 / (f.n / TD), tau = set.prior_tau / 100; return tau * tau * se2 / (tau * tau + se2); });
    const idioVar = holdings.map((_, a) => Math.max(postVar[a] - beta[a] ** 2 * o.tauM ** 2, 0.25 * postVar[a]));
    function drawMeal() {
      const idx = []; while (idx.length < Tn) { const s = Math.floor(rng.next() * Math.max(1, Tn - BLOCK)); for (let k = 0; k < BLOCK && idx.length < Tn; k++) idx.push(s + k); }
      const rets = model.factors.map((f) => idx.map((t) => f.ret[t]));
      const mk = o.tauM * rng.normal(), rs = rate ? rateShift(Math.floor(rng.next() * rate.b.length)) : 0;
      const fs0 = [], fs = model.factors.map((f, i) => {
        const g = { ...f, mu: { ...f.mu } }, g0 = { ...f, mu: { ...f.mu } }, r = rets[i].filter((x) => x !== null);
        if (f.volRaw && r.length > 20 && !f.cash) g.vol = g0.vol = f.vol * (M.std(r) * Math.sqrt(TD)) / f.volRaw;
        if (i < A && !cash[i] && f.n >= 5) {
          const mu0 = f.mu[scen] + beta[i] * mk + Math.sqrt(idioVar[i]) * rng.normal(), mu = Math.max(-0.6, mu0 + beta[i] * rs);
          for (const k of Object.keys(g.mu)) { g.mu[k] = mu; g0.mu[k] = Math.max(-0.6, mu0); }
        }
        fs0.push(g0); return g;
      });
      const C = fs.map(() => new Array(F).fill(0));
      for (let i = 0; i < F; i++) { C[i][i] = 1; for (let j = 0; j < i; j++) {
        const a = [], b = []; for (let t = 0; t < Tn; t++) { const x = rets[i][t], y = rets[j][t]; if (x !== null && y !== null) { a.push(x); b.push(y); } }
        const riskPair = !(fs[i].cash || fs[j].cash || fs[i].kind === "fx" || fs[j].kind === "fx"), pr = riskPair ? Number(set.default_corr) : 0;
        let c = pr;
        if (a.length >= 40) { const ma = M.mean(a), mb = M.mean(b); let sab = 0, saa = 0, sbb = 0; for (let t = 0; t < a.length; t++) { sab += (a[t] - ma) * (b[t] - mb); saa += (a[t] - ma) ** 2; sbb += (b[t] - mb) ** 2; } c = saa > 0 && sbb > 0 ? sab / Math.sqrt(saa * sbb) : 0; if (a.length < TD) { const w = a.length / (a.length + TD); c = w * c + (1 - w) * pr; } }
        C[i][j] = C[j][i] = Math.max(-0.95, Math.min(0.95, c));
      } }
      let L = null, lam = 0; while (!(L = cholesky(C.map((r, i) => r.map((v, j) => (i === j ? 1 : v * (1 - lam))))))) lam += 0.05;
      // 원화 기준 연 드리프트 α 와 공분산 S
      const sd = fs.map((f) => f.vol), cov = (i, j) => C[i][j] * sd[i] * sd[j], inK = (a) => holdings[a].ccy !== "KRW" && fxi >= 0;
      const alFx = fxi >= 0 ? Math.log(1 + fs[fxi].mu.base) : 0;
      const alpha = holdings.map((_, a) => Math.log(1 + fs[a].mu[scen]) + (inK(a) ? alFx + cov(a, fxi) : 0));
      const S = holdings.map((_, i) => holdings.map((_, j) => cov(i, j) + (inK(i) ? cov(fxi, j) : 0) + (inK(j) ? cov(i, fxi) : 0) + (inK(i) && inK(j) ? cov(fxi, fxi) : 0)));
      return { model: { ...model, factors: fs, L, corr: C, corrShrink: lam }, model0: rate ? { ...model, factors: fs0, L, corr: C, corrShrink: lam } : null, alpha, S, rs };
    }

    // ---- 제어기의 믿음 = 식단 분포
    const prior = []; for (let k = 0; k < o.meals; k++) prior.push(drawMeal());
    prog(1, o.trueMeals + 2);
    const m0 = holdings.map((_, i) => M.mean(prior.map((P) => P.alpha[i])));
    const O0 = m0.map((_, i) => m0.map((_, j) => prior.reduce((s, P) => s + (P.alpha[i] - m0[i]) * (P.alpha[j] - m0[j]), 0) / (o.meals - 1)));
    const Sbar = holdings.map((_, i) => holdings.map((_, j) => M.mean(prior.map((P) => P.S[i][j]))));
    let u = m0.map(() => 1); for (let k = 0; k < 300; k++) { const v = O0.map((r) => r.reduce((s, x, j) => s + x * u[j], 0)); const n = Math.hypot(...v) || 1; u = v.map((x) => x / n); }
    if (u.reduce((s, x) => s + x, 0) < 0) u = u.map((x) => -x);

    const quad = (w, Q) => { let v = 0; for (let i = 0; i < A; i++) for (let j = 0; j < A; j++) v += w[i] * w[j] * Q[i][j]; return v; };
    const cands = []; (function rec(i, left, cur) { if (i === A - 1) { cands.push([...cur, left].map((x) => x / 100)); return; } for (let x = 0; x <= left; x += o.step) rec(i + 1, left - x, [...cur, x]); })(0, 100, []);
    function settle(h, tgt, cost) { // h → tgt 로 바꿀 때 실현 차익 (평균단가법)
      let gain = 0; const nc = cost.slice();
      for (let a = 0; a < A; a++) {
        if (tgt[a] < h[a] && h[a] > 0) { const sold = (h[a] - tgt[a]) / h[a]; gain += (h[a] - cost[a]) * sold; nc[a] = cost[a] * (1 - sold); }
        else if (tgt[a] > h[a]) nc[a] = cost[a] + (tgt[a] - h[a]);
      }
      return { cost: nc, tax: Math.max(0, gain - DED) * TAX };
    }
    function choose(h, cost, tau, m, O) {
      const V = h.reduce((s, x) => s + x, 0); let best = null, bs = -Infinity;
      for (const w of cands) {
        const lnV = Math.log(V - settle(h, w.map((x) => x * V), cost).tax);
        const g = w.reduce((s, x, i) => s + x * m[i], 0) - 0.5 * quad(w, Sbar), v = quad(w, Sbar) * tau + tau * tau * quad(w, O), sv = Math.sqrt(Math.max(v, 1e-14));
        const sc = Phi((lnV + g * tau - lnG) / sv) - o.lambda * Phi((floorLn - lnV - g * tau) / sv);
        if (sc > bs + 1e-12) { bs = sc; best = w; }
      }
      return best;
    }

    // ---- 진짜 식단 위에서 비교 (경로는 기존 엔진: 사건·t 충격·환율 포함)
    const common = { holdings, scenario: scen, goal: goal.amount, monthly: 0, rebalance: false, withEvents: true, dof: set.t_dof, fxOf, usdKrw0: inp.usdKrw0 };
    const cost0 = holdings.map((h, a) => (h.avgCost > 0 ? h.valueKrw * h.avgCost / h.price0 : h.valueKrw));
    const res = { hold0: { pm: [], after: [], term: [] }, hold: { pm: [], after: [], term: [] }, ctrl: { pm: [], after: [], term: [], tax: [], wy: [] } };
    const first = choose(holdings.map((h) => h.valueKrw), cost0, T, m0, O0);
    for (let t = 0; t < o.trueMeals; t++) {
      const tr = drawMeal(), R = M.simulate(tr.model, { ...common, nPaths: o.paths, seed: 5000 + t });
      if (tr.model0) { // 금리 미반영 쌍둥이: 같은 식단·같은 난수, 금리 몫만 뺀다. 그대로 보유만
        const X0 = M.simulate(tr.model0, { ...common, nPaths: o.paths, seed: 5000 + t }).raw, k = X0.M - 1; let hit = 0;
        for (let p = 0; p < X0.P; p++) {
          let V = 0, gain = 0; holdings.forEach((h, a) => { const v = h.valueKrw * Math.exp(X0.cg[a][k * X0.P + p]); V += v; gain += v - cost0[a]; });
          if (V >= goal.amount) hit++; res.hold0.term.push(V); res.hold0.after.push(V - Math.max(0, gain - DED) * TAX);
        }
        res.hold0.pm.push(hit / X0.P);
      }
      const X = R.raw, Mn = X.M, P = X.P, yearK = []; for (let k = 12; k + 6 < Mn - 1; k += 12) yearK.push(k);
      const cg = (a, k, p) => (k ? X.cg[a][k * P + p] : 0);
      for (const pol of ["hold", "ctrl"]) {
        let hit = 0;
        for (let p = 0; p < P; p++) {
          let m = m0.slice(), O = O0.map((r) => r.slice()), cost = cost0.slice(), h = holdings.map((x) => x.valueKrw), taxSum = 0;
          if (pol === "ctrl") { const s = settle(h, first.map((x) => x * V0), cost); cost = s.cost; h = first.map((x) => x * V0 - s.tax * x); taxSum += s.tax; }
          let kPrev = 0;
          for (let yi = 0; yi <= yearK.length; yi++) {
            const k = yi < yearK.length ? yearK[yi] : Mn - 1, y = holdings.map((_, a) => cg(a, k, p) - cg(a, kPrev, p));
            h = h.map((x, a) => x * Math.exp(y[a]));
            if (k === Mn - 1) break;
            if (pol === "ctrl") {
              const yy = y.map((x, i) => x + 0.5 * Sbar[i][i]), Rm = Sbar.map((r, i) => r.map((x, j) => x + O[i][j] + (i === j ? 1e-10 : 0)));
              const K = mul(O, matInv(Rm)); m = m.map((x, i) => x + K[i].reduce((s, kk, j) => s + kk * (yy[j] - m[j]), 0));
              const KO = mul(K, O); O = O.map((r, i) => r.map((x, j) => x - KO[i][j]));
              const V = h.reduce((s, x) => s + x, 0), w = choose(h, cost, Math.max(0.05, T - (yi + 1)), m, O);
              const s = settle(h, w.map((x) => x * V), cost); cost = s.cost; h = w.map((x) => x * V - s.tax * x); taxSum += s.tax;
              if (p < 5) (res.ctrl.wy[yi] ||= []).push(w);
            }
            kPrev = k;
          }
          const V = h.reduce((s, x) => s + x, 0), fin = settle(h, h.map(() => 0), cost).tax;
          if (V >= goal.amount) hit++;
          res[pol].term.push(V); res[pol].after.push(V - fin); if (pol === "ctrl") res.ctrl.tax.push(taxSum);
        }
        res[pol].pm.push(hit / P);
      }
      prog(t + 2, o.trueMeals + 2);
    }
    const sum = (r) => ({ p10: q(r.pm, 0.1), p50: q(r.pm, 0.5), p90: q(r.pm, 0.9), mean: M.mean(r.pm), a5: q(r.after, 0.05), a50: q(r.after, 0.5), loss: r.term.filter((x) => x < V0).length / r.term.length });
    const avgW = (ws) => holdings.map((_, i) => M.mean(ws.map((w) => w[i])));
    return {
      tickers: holdings.map((h) => h.ticker), V0, T, w0, first, disturbance: u, beta, cashIdx: ciIdx, hasAvg: holdings.some((h) => h.avgCost > 0),
      firstTax: settle(holdings.map((h) => h.valueKrw), first.map((x) => x * V0), cost0).tax,
      hold: sum(res.hold), hold0: rate ? sum(res.hold0) : null,
      rate: rate ? (() => { const v = prior.map((P) => P.rs); return { dr: rate.dr, p10: q(v, 0.1), p50: q(v, 0.5), p90: q(v, 0.9) }; })() : null,
      ctrl: { ...sum(res.ctrl), tax: M.mean(res.ctrl.tax), wy: res.ctrl.wy.map(avgW) },
      setup: { meals: o.meals, trueMeals: o.trueMeals, paths: o.paths, lambda: o.lambda, floor: o.floor, tauM: o.tauM, step: o.step },
    };
  }
  window.Research = { stabilizer };
})();
