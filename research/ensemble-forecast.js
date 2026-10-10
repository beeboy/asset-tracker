// 연구용: "같은 식단 1000번" (기존 3년 전망) vs "다른 식단 1000개" (파라미터 앙상블)
// 기존 web/model.js 는 그대로 쓰고 고치지 않는다. 식단 = 변동성·상관(Σ)·기대수익(μ) 한 벌.
// 실행: node research/ensemble-forecast.js <입력값.json> [출력.json]
//   입력값.json: 앱의 '내보내기' 파일 (holdings·goal·events·model). 실제 보유는 저장소에 넣지 않는다.
"use strict";
const fs = require("fs"), path = require("path");
global.window = {};
require(path.join(__dirname, "../web/model.js"));
const M = window.Model, TD = M.TD;

const inPath = process.env.INPUT || process.argv[2], outPath = process.argv[3];
const inp = JSON.parse(fs.readFileSync(inPath, "utf8"));
const DEF = { scenario: "blend", trust: 50, n_paths: 3000, seed: 20261004, history_years: 3, prior_mu: 10, prior_tau: 15, conservative_mu: 4,
  new_listing_vol: 60, default_vol: 40, default_corr: 0.3, t_dof: 5, fx_drift: 0, fx_vol_mult: 1, rebalance_yearly: false, earnings_adjust: true };
const set = { ...DEF, ...(inp.model || {}) };
const goal = inp.goal, start = process.env.START || new Date().toISOString().slice(0, 10);
const events = inp.events || JSON.parse(fs.readFileSync(path.join(__dirname, "../data/state.default.json"), "utf8")).events;

// ---------------------------------------------------------------- 시세·보유
const load = (t) => JSON.parse(fs.readFileSync(path.join(__dirname, "../data/prices", t.replace(/[=^]/g, (c) => (c === "=" ? "_" : "_")) + ".json"), "utf8"));
const fxOf = (ccy) => (!ccy || ccy === "KRW" ? null : ccy === "USD" ? "KRW=X" : ccy + "KRW=X");
const series = {};
const fx = load("KRW=X"); series["KRW=X"] = { dates: fx.dates, adj: fx.adj };
const usd = fx.close[fx.close.length - 1];
const holdings = inp.holdings.filter((h) => Number(h.shares) > 0).map((h) => {
  const p = load(h.ticker); series[h.ticker] = { dates: p.dates, adj: p.adj };
  const price0 = p.close[p.close.length - 1], ccy = p.currency || "USD";
  return { ticker: h.ticker, ccy, shares: h.shares, price0, valueKrw: h.shares * price0 * (ccy === "KRW" ? 1 : usd) };
});
const V0 = holdings.reduce((s, h) => s + h.valueKrw, 0);

// ---------------------------------------------------------------- 도구
function rngOf(seed) {
  let a = seed >>> 0, spare = null;
  const next = () => { a = (a + 0x6d2b79f5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
  const normal = () => { if (spare !== null) { const s = spare; spare = null; return s; } let u, v, s; do { u = 2 * next() - 1; v = 2 * next() - 1; s = u * u + v * v; } while (!s || s >= 1); const k = Math.sqrt(-2 * Math.log(s) / s); spare = v * k; return u * k; };
  return { next, normal };
}
const q = (arr, p) => { const a = Float64Array.from(arr).sort(); const i = (a.length - 1) * p, lo = Math.floor(i); return a[lo] + (a[Math.min(lo + 1, a.length - 1)] - a[lo]) * (i - lo); };
function cholesky(A) {
  const n = A.length, L = A.map(() => new Array(n).fill(0));
  for (let i = 0; i < n; i++) for (let j = 0; j <= i; j++) {
    let s = A[i][j]; for (let k = 0; k < j; k++) s -= L[i][k] * L[j][k];
    if (i === j) { if (s <= 1e-10) return null; L[i][i] = Math.sqrt(s); } else L[i][j] = s / L[j][j];
  }
  return L;
}
function cholShrink(C) { let L, lam = 0; while (!(L = cholesky(C.map((r, i) => r.map((v, j) => (i === j ? 1 : v * (1 - lam))))))) lam += 0.05; return { L, lam }; }
// 대칭행렬 고유값 (야코비 회전)
function eigSym(A0) {
  const n = A0.length, A = A0.map((r) => r.slice());
  for (let sweep = 0; sweep < 100; sweep++) {
    let off = 0; for (let i = 0; i < n; i++) for (let j = i + 1; j < n; j++) off += A[i][j] ** 2;
    if (off < 1e-18) break;
    for (let p = 0; p < n; p++) for (let r = p + 1; r < n; r++) {
      if (Math.abs(A[p][r]) < 1e-15) continue;
      const th = (A[r][r] - A[p][p]) / (2 * A[p][r]), t = Math.sign(th || 1) / (Math.abs(th) + Math.sqrt(th * th + 1)), c = 1 / Math.sqrt(t * t + 1), s = t * c;
      for (let k = 0; k < n; k++) { const akp = A[k][p], akr = A[k][r]; A[k][p] = c * akp - s * akr; A[k][r] = s * akp + c * akr; }
      for (let k = 0; k < n; k++) { const apk = A[p][k], ark = A[r][k]; A[p][k] = c * apk - s * ark; A[r][k] = s * apk + c * ark; }
    }
  }
  return A.map((r, i) => r[i]).sort((a, b) => b - a);
}

// ---------------------------------------------------------------- 기준 모형 (기존 방식 그대로)
const model = M.buildModel({ holdings, series, fxOf, settings: set, events, betas: {}, startDate: start, goalDate: goal.date });
const F = model.factors.length, A = holdings.length, scen = set.scenario, dof = set.t_dof;
const common = { holdings, scenario: scen, goal: goal.amount, monthly: 0, rebalance: false, withEvents: true, dof, fxOf, usdKrw0: usd };

// 경로별 월말 평가액 (재조정·적립 없음): V = Σ v0 · exp(누적 로그성장)
function portPaths(R) {
  const X = R.raw, out = [];
  for (let p = 0; p < X.P; p++) {
    const v = new Float64Array(X.M); v[0] = V0;
    for (let k = 1; k < X.M; k++) { let s = 0; for (let a = 0; a < A; a++) s += X.v0[a] * Math.exp(X.cg[a][k * X.P + p]); v[k] = s; }
    out.push(v);
  }
  return out;
}

// ---------------------------------------------------------------- 식단 한 벌 뽑기
// Σ: 과거 3년 일별 수익률을 21일 블록으로 다시 뽑아(블록 부트스트랩) 변동성·상관을 다시 잰다
// μ: 기존 베이즈 수축의 사후분포 N(μ̂, k·se²) 에서 뽑는다
const T = model.factors[0].ret.length, BLOCK = 21;
const corrOf = (rets, fs) => {
  const C = fs.map(() => new Array(F).fill(0));
  for (let i = 0; i < F; i++) { C[i][i] = 1; for (let j = 0; j < i; j++) {
    const a = [], b = []; for (let t = 0; t < rets[i].length; t++) { const x = rets[i][t], y = rets[j][t]; if (x !== null && y !== null) { a.push(x); b.push(y); } }
    const riskPair = !(fs[i].cash || fs[j].cash || fs[i].kind === "fx" || fs[j].kind === "fx"), prior = riskPair ? Number(set.default_corr) : 0;
    let c = prior;
    if (a.length >= 40) {
      const ma = M.mean(a), mb = M.mean(b); let sab = 0, saa = 0, sbb = 0;
      for (let t = 0; t < a.length; t++) { sab += (a[t] - ma) * (b[t] - mb); saa += (a[t] - ma) ** 2; sbb += (b[t] - mb) ** 2; }
      c = saa > 0 && sbb > 0 ? sab / Math.sqrt(saa * sbb) : 0;
      if (a.length < TD) { const w = a.length / (a.length + TD); c = w * c + (1 - w) * prior; }
    }
    C[i][j] = C[j][i] = Math.max(-0.95, Math.min(0.95, c));
  } }
  return C;
};
function drawMeal(rng) {
  const idx = []; while (idx.length < T) { const s = Math.floor(rng.next() * (T - BLOCK)); for (let k = 0; k < BLOCK && idx.length < T; k++) idx.push(s + k); }
  const rets = model.factors.map((f) => idx.map((t) => f.ret[t]));
  const fs = model.factors.map((f, i) => {
    const g = { ...f, mu: { ...f.mu } };
    const r = rets[i].filter((x) => x !== null);
    if (f.volRaw && r.length > 20 && !f.cash) g.vol = f.vol * (M.std(r) * Math.sqrt(TD)) / f.volRaw;
    if (f.kind === "asset" && !f.cash && f.n >= 5) {
      const se2 = f.vol ** 2 / (f.n / TD), tau = set.prior_tau / 100, k = tau * tau / (tau * tau + se2);
      const d = Math.sqrt(k * se2) * rng.normal();
      for (const s of Object.keys(g.mu)) g.mu[s] = Math.max(-0.6, f.mu[s] + d);
    }
    return g;
  });
  const C = corrOf(rets, fs), { L, lam } = cholShrink(C);
  return { model: { ...model, factors: fs, L, corr: C, corrShrink: lam }, C, fs };
}

module.exports = { M, TD, set, goal, start, holdings, V0, usd, model, common, fxOf, rngOf, q, eigSym, drawMeal, portPaths };
if (require.main === module) {
// ---------------------------------------------------------------- 실행
const t0 = Date.now();
const NP = Number(process.env.PATHS) || 3000, MEALS = Number(process.env.MEALS) || 1000, PER = Math.max(1, Math.round(NP / MEALS));
const NEST_MEALS = Number(process.env.NEST_MEALS) || 200, NEST_P = Number(process.env.NEST_P) || 200;
const seed = Number(set.seed) || 1;

// A. 기존: 같은 식단 NP 번
const RA = M.simulate(model, { ...common, nPaths: NP, seed });
const pathsA = portPaths(RA);

// B. 새 방식: 다른 식단 MEALS 개 × PER 경로 (총 경로 수는 같게)
const rng = rngOf(seed ^ 0x5eed);
const pathsB = [], mealStats = [];
const riskyIdx = model.factors.map((f, i) => (f.kind === "asset" && !f.cash ? i : -1)).filter((i) => i >= 0);
const subCorr = (C) => riskyIdx.map((i) => riskyIdx.map((j) => C[i][j]));
const effBets = (ev) => { const s = ev.reduce((a, x) => a + Math.max(x, 0), 0), p = ev.map((x) => Math.max(x, 0) / s); return Math.exp(-p.reduce((a, x) => a + (x > 0 ? x * Math.log(x) : 0), 0)); };
for (let m = 0; m < MEALS; m++) {
  const meal = drawMeal(rng);
  const R = M.simulate(meal.model, { ...common, nPaths: PER, seed: seed + 7919 * (m + 1) });
  pathsB.push(...portPaths(R));
  const ev = eigSym(subCorr(meal.C));
  mealStats.push({ lam1: ev[0] / ev.length, eff: effBets(ev), vol: meal.fs.slice(0, A).map((f) => f.vol), mu: meal.fs.slice(0, A).map((f) => f.mu[scen]) });
}

// C. 식단별 목표 확률 분포: NEST_MEALS 식단 × NEST_P 경로
const pg = [];
for (let m = 0; m < NEST_MEALS; m++) {
  const meal = drawMeal(rng);
  const R = M.simulate(meal.model, { ...common, nPaths: NEST_P, seed: seed + 104729 * (m + 1) });
  pg.push(R.p_goal);
}

// ---------------------------------------------------------------- 요약
const Mn = pathsA[0].length;
const bands = (paths) => Object.fromEntries([["p5", 0.05], ["p25", 0.25], ["p50", 0.5], ["p75", 0.75], ["p95", 0.95]].map(([n, p]) => [n, Array.from({ length: Mn }, (_, k) => q(paths.map((v) => v[k]), p))]));
const term = (paths) => paths.map((v) => v[Mn - 1]);
const summ = (paths) => { const t = term(paths); return { p_goal: t.filter((x) => x >= goal.amount).length / t.length, p_loss: t.filter((x) => x < V0).length / t.length, p5: q(t, 0.05), p25: q(t, 0.25), p50: q(t, 0.5), p75: q(t, 0.75), p95: q(t, 0.95) }; };
const evBase = eigSym(subCorr(model.corr));
const out = {
  start, goal, V0, usdKrw: usd, scenario: scen, holdings: holdings.map((h) => ({ ticker: h.ticker, w: h.valueKrw / V0 })),
  setup: { paths: NP, meals: MEALS, per_meal: PER, nest: [NEST_MEALS, NEST_P], block_days: BLOCK },
  monthDates: model.monthDates,
  same: { ...summ(pathsA), bands: bands(pathsA) },
  diff: { ...summ(pathsB), bands: bands(pathsB) },
  p_goal_by_meal: { p5: q(pg, 0.05), p25: q(pg, 0.25), p50: q(pg, 0.5), p75: q(pg, 0.75), p95: q(pg, 0.95), all: pg },
  eig: { base: { lam1: evBase[0] / evBase.length, eff: effBets(evBase), ev: evBase }, tickers: riskyIdx.map((i) => model.factors[i].key),
    lam1: [0.05, 0.5, 0.95].map((p) => q(mealStats.map((s) => s.lam1), p)), eff: [0.05, 0.5, 0.95].map((p) => q(mealStats.map((s) => s.eff), p)) },
  params: holdings.map((h, a) => ({ ticker: h.ticker, vol0: model.factors[a].vol, mu0: model.factors[a].mu[scen],
    vol: [0.05, 0.5, 0.95].map((p) => q(mealStats.map((s) => s.vol[a]), p)), mu: [0.05, 0.5, 0.95].map((p) => q(mealStats.map((s) => s.mu[a]), p)) })),
  seconds: (Date.now() - t0) / 1000,
};
const brief = (s) => `P(목표) ${(s.p_goal * 100).toFixed(1)}%  P(손실) ${(s.p_loss * 100).toFixed(1)}%  p5 ${(s.p5 / 1e8).toFixed(2)}억  p50 ${(s.p50 / 1e8).toFixed(2)}억  p95 ${(s.p95 / 1e8).toFixed(2)}억`;
console.log(`시작 ${start}, 현재 ${(V0 / 1e8).toFixed(2)}억, 시나리오 ${scen}, ${out.seconds}s`);
console.log("같은 식단  " + brief(out.same));
console.log("다른 식단  " + brief(out.diff));
console.log("식단별 P(목표) p5/p50/p95: " + [out.p_goal_by_meal.p5, out.p_goal_by_meal.p50, out.p_goal_by_meal.p95].map((x) => (x * 100).toFixed(0) + "%").join(" / "));
console.log("λ1 몫 기준 " + out.eig.base.lam1.toFixed(2) + " 앙상블 " + out.eig.lam1.map((x) => x.toFixed(2)).join("/") + ", 실질 베팅 수 기준 " + out.eig.base.eff.toFixed(2) + " 앙상블 " + out.eig.eff.map((x) => x.toFixed(2)).join("/"));
for (const p of out.params) console.log(`${p.ticker}: vol ${(p.vol0 * 100).toFixed(0)}% → ${p.vol.map((x) => (x * 100).toFixed(0)).join("/")}  mu ${(p.mu0 * 100).toFixed(1)}% → ${p.mu.map((x) => (x * 100).toFixed(1)).join("/")}`);
if (outPath) fs.writeFileSync(outPath, JSON.stringify(out));
}
