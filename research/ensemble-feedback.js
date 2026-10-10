// 연구용 3단계: 시장 공통 μ 오차 + 피드백(매년 베이즈 갱신 → 비중 다시 고르기) 제어
// 진짜 식단 θ 는 제어기가 모른다. 제어기는 식단 분포(사전)만 알고, 매년 실현 수익으로 μ 믿음을 갱신한다.
// μ_a = μ̂_a + β_a·m + e_a   (m: 시장 전체 드리프트 오차, 모든 종목에 β 만큼 공통으로 실린다)
// 실행: INPUT=<입력값.json> node research/ensemble-feedback.js [출력.json]
"use strict";
const fs = require("fs"), path = require("path");
const E = require("./ensemble-forecast.js");
const { TD, set, goal, start, holdings, V0, model, rngOf, q, drawMeal } = E;
const A = holdings.length, scen = set.scenario;
const fxi = model.factors.findIndex((f) => f.kind === "fx" && f.key === "KRW=X");
const T = (Date.parse(goal.date) - Date.parse(start)) / (365.25 * 86400e3), lnG = Math.log(goal.amount);
const TAU_M = (Number(process.env.TAU_M) || 6) / 100; // 시장 연 기대수익 자체의 불확실성 (표준편차)
const Phi = (x) => { const t = 1 / (1 + 0.2316419 * Math.abs(x)), d = 0.3989423 * Math.exp(-x * x / 2), p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274)))); return x > 0 ? 1 - p : p; };
const cash = holdings.map((_, a) => !!model.factors[a].cash);

// ---------------------------------------------------------------- 시장 베타 (SPY 대비 일별 로그수익 회귀)
const spy = JSON.parse(fs.readFileSync(path.join(__dirname, "../data/prices/SPY.json"), "utf8"));
const spyR = new Map(); for (let i = 1; i < spy.dates.length; i++) spyR.set(spy.dates[i], Math.log(spy.adj[i] / spy.adj[i - 1]));
const beta = holdings.map((h, a) => {
  if (cash[a]) return 0;
  const p = JSON.parse(fs.readFileSync(path.join(__dirname, "../data/prices", h.ticker + ".json"), "utf8"));
  let sx = 0, sy = 0, sxx = 0, sxy = 0, n = 0;
  for (let i = 1; i < p.dates.length; i++) { const x = spyR.get(p.dates[i]); if (x === undefined) continue; const y = Math.log(p.adj[i] / p.adj[i - 1]); sx += x; sy += y; sxx += x * x; sxy += x * y; n++; }
  return (sxy - sx * sy / n) / (sxx - sx * sx / n);
});

// ---------------------------------------------------------------- 식단: Σ 는 부트스트랩, μ 는 공통 + 고유 오차
function krwParams(fsx, C, mu) {
  const sd = fsx.map((f) => f.vol), alFx = Math.log(1 + fsx[fxi].mu.base);
  const cov = (i, j) => C[i][j] * sd[i] * sd[j], inK = (a) => holdings[a].ccy !== "KRW";
  const alpha = holdings.map((_, a) => Math.log(1 + mu[a]) + (inK(a) ? alFx + cov(a, fxi) : 0));
  const S = holdings.map((_, i) => holdings.map((_, j) => cov(i, j) + (inK(i) ? cov(fxi, j) : 0) + (inK(j) ? cov(i, fxi) : 0) + (inK(i) && inK(j) ? cov(fxi, fxi) : 0)));
  return { alpha, S };
}
const postVar = holdings.map((_, a) => { const f = model.factors[a]; if (cash[a]) return 0; const se2 = f.vol ** 2 / (f.n / TD), tau = set.prior_tau / 100; return tau * tau * se2 / (tau * tau + se2); });
const idioVar = holdings.map((_, a) => Math.max(postVar[a] - beta[a] ** 2 * TAU_M ** 2, 0.25 * postVar[a]));
function drawTrue(rng) {
  const m = drawMeal(rng), mk = TAU_M * rng.normal();
  const mu = holdings.map((_, a) => (cash[a] ? model.factors[a].mu[scen] : Math.max(-0.6, model.factors[a].mu[scen] + beta[a] * mk + Math.sqrt(idioVar[a]) * rng.normal())));
  m.fs.forEach((f, a) => { if (a < A) for (const k of Object.keys(f.mu)) f.mu[k] = mu[a]; });
  return { ...krwParams(m.fs, m.C, mu), model: m.model };
}

// ---------------------------------------------------------------- 선형대수 도구
const matInv = (M0) => { const n = M0.length, a = M0.map((r, i) => [...r, ...M0.map((_, j) => (i === j ? 1 : 0))]);
  for (let c = 0; c < n; c++) { let p = c; for (let r = c + 1; r < n; r++) if (Math.abs(a[r][c]) > Math.abs(a[p][c])) p = r; [a[c], a[p]] = [a[p], a[c]];
    const d = a[c][c]; for (let k = 0; k < 2 * n; k++) a[c][k] /= d;
    for (let r = 0; r < n; r++) if (r !== c) { const f = a[r][c]; for (let k = 0; k < 2 * n; k++) a[r][k] -= f * a[c][k]; } }
  return a.map((r) => r.slice(n)); };
const mul = (X, Y) => X.map((r) => Y[0].map((_, j) => r.reduce((s, x, k) => s + x * Y[k][j], 0)));
const quad = (w, M0) => { let v = 0; for (let i = 0; i < A; i++) for (let j = 0; j < A; j++) v += w[i] * w[j] * M0[i][j]; return v; };
function cholL(M0) { const n = M0.length, L = M0.map(() => new Array(n).fill(0)); for (let i = 0; i < n; i++) for (let j = 0; j <= i; j++) { let s = M0[i][j]; for (let k = 0; k < j; k++) s -= L[i][k] * L[j][k]; if (i === j) L[i][i] = Math.sqrt(Math.max(s, 1e-14)); else L[i][j] = s / L[j][j]; } return L; }

// ---------------------------------------------------------------- 제어기의 사전 믿음 = 식단 분포
const MEALS = Number(process.env.MEALS) || 1000, rng = rngOf((Number(set.seed) || 1) ^ 0xfeed);
const prior = Array.from({ length: MEALS }, () => drawTrue(rng));
const m0 = holdings.map((_, i) => prior.reduce((s, P) => s + P.alpha[i], 0) / MEALS);
const O0 = m0.map((_, i) => m0.map((_, j) => prior.reduce((s, P) => s + (P.alpha[i] - m0[i]) * (P.alpha[j] - m0[j]), 0) / (MEALS - 1)));
const Sbar = holdings.map((_, i) => holdings.map((_, j) => prior.reduce((s, P) => s + P.S[i][j], 0) / MEALS));
// 외란 방향 = Ω0 의 첫 고유벡터
let u = m0.map(() => 1); for (let k = 0; k < 300; k++) { const v = O0.map((r) => r.reduce((s, x, j) => s + x * u[j], 0)); const n = Math.hypot(...v); u = v.map((x) => x / n); }

// 비중 후보 (STEP% 간격)
const STEP = Number(process.env.STEP) || 10, cands = [];
(function rec(i, left, cur) { if (i === A - 1) { cands.push([...cur, left].map((x) => x / 100)); return; } for (let x = 0; x <= left; x += STEP) rec(i + 1, left - x, [...cur, x]); })(0, 100, []);
// 예측 목표 확률: μ ~ N(m, Ω) 를 적분하면 분산에 τ²·wᵀΩw 가 더해진다 (식단 전체를 한 식으로 겹친 것)
const predP = (w, lnV, tau, m, O) => { const g = w.reduce((s, x, i) => s + x * m[i], 0) - 0.5 * quad(w, Sbar), v = quad(w, Sbar) * tau + tau * tau * quad(w, O); return v > 1e-14 ? Phi((lnV + g * tau - lnG) / Math.sqrt(v)) : (lnV + g * tau >= lnG ? 1 : 0); };
// 하방 보호: 만기 평가액이 FLOOR(지금의 60%) 아래로 갈 예측 확률을 LAMBDA 만큼 벌점
const FLOOR = Math.log(V0 * (Number(process.env.FLOOR) || 0.6)), LAMBDA = Number(process.env.LAMBDA) || 1;
const predBelow = (w, lnV, tau, m, O) => { const g = w.reduce((s, x, i) => s + x * m[i], 0) - 0.5 * quad(w, Sbar), v = quad(w, Sbar) * tau + tau * tau * quad(w, O); return v > 1e-14 ? Phi((FLOOR - lnV - g * tau) / Math.sqrt(v)) : (lnV + g * tau < FLOOR ? 1 : 0); };
const choose = (lnV, tau, m, O, lam = 0) => { let b = null, bp = -Infinity; for (const w of cands) { const p = predP(w, lnV, tau, m, O) - (lam ? lam * predBelow(w, lnV, tau, m, O) : 0); if (p > bp + 1e-12) { bp = p; b = w; } } return b; };

module.exports = { E, beta, drawTrue, choose, predP, m0, O0, Sbar, u, cands, T, A, mul, matInv };
if (require.main === module) {
// ---------------------------------------------------------------- 정책
const w0 = holdings.map((h) => h.valueKrw / V0), wStatic = choose(Math.log(V0), T, m0, O0);
const YEARS = Math.ceil(T - 1e-9), MON = Math.round(T * 12);
const policies = {
  "지금 비중 그대로": { w: w0, hold: true },
  "지금 비중 매년 재조정": { w: w0 },
  "고정 최적(갱신 없음)": { w: wStatic },
  "피드백(매년 갱신)": { feedback: true },
  "피드백+하방 보호": { feedback: true, lam: LAMBDA },
};
const NT = Number(process.env.TRUE_MEALS) || 300, NP = Number(process.env.PATHS_PER) || 60, sim = rngOf(9001);
const truths = Array.from({ length: NT }, () => drawTrue(sim));
const res = Object.fromEntries(Object.keys(policies).map((k) => [k, { pm: [], term: [], wy: [] }]));
const z = new Float64Array(A);
for (let t = 0; t < NT; t++) {
  const P = truths[t], L = cholL(P.S.map((r) => r.map((x) => x / 12))), drift = P.alpha.map((a, i) => (a - 0.5 * P.S[i][i]) / 12);
  // 경로 충격은 정책끼리 같게 (공정 비교)
  const shocks = Array.from({ length: NP }, () => Array.from({ length: MON }, () => { for (let i = 0; i < A; i++) z[i] = sim.normal(); return L.map((r) => r.reduce((s, x, k) => s + x * z[k], 0)); }));
  for (const [k, pol] of Object.entries(policies)) {
    let hit = 0;
    for (let p = 0; p < NP; p++) {
      let m = m0.slice(), O = O0.map((r) => r.slice()), V = V0, w = pol.feedback ? choose(Math.log(V0), T, m, O, pol.lam) : pol.w;
      let h = w.map((x) => x * V), yr = holdings.map(() => 0);
      for (let mo = 0; mo < MON; mo++) {
        const e = shocks[p][mo];
        for (let i = 0; i < A; i++) { const lr = drift[i] + e[i]; h[i] *= Math.exp(lr); yr[i] += lr; }
        V = h.reduce((s, x) => s + x, 0);
        if ((mo + 1) % 12 === 0 && mo + 1 < MON) {
          if (pol.feedback) { // 칼만 갱신: 관측 y = 1년 로그수익 + ½σ² ~ N(α, Σ̄)
            const y = yr.map((x, i) => x + 0.5 * Sbar[i][i]), R = Sbar.map((r, i) => r.map((x, j) => x + O[i][j] + (i === j ? 1e-10 : 0)));
            const K = mul(O, matInv(R)); m = m.map((x, i) => x + K[i].reduce((s, kk, j) => s + kk * (y[j] - m[j]), 0));
            const KO = mul(K, O); O = O.map((r, i) => r.map((x, j) => x - KO[i][j]));
            w = choose(Math.log(V), T - (mo + 1) / 12, m, O, pol.lam);
            if (t < 40 && p === 0) res[k].wy.push({ year: (mo + 1) / 12, w });
          }
          if (!pol.hold) h = w.map((x) => x * V);
          yr = holdings.map(() => 0);
        }
      }
      if (V >= goal.amount) hit++;
      res[k].term.push(V);
    }
    res[k].pm.push(hit / NP);
  }
}

const pct = (x) => (x * 100).toFixed(0) + "%", eok = (x) => (x / 1e8).toFixed(2) + "억";
const out = { start, T, TAU_M, beta, disturbance: u, tickers: holdings.map((h) => h.ticker), w0, wStatic, setup: { prior: MEALS, trueMeals: NT, pathsPer: NP, step: STEP }, policies: {} };
console.log(`β ${beta.map((b) => b.toFixed(2)).join("/")}  외란 방향 ${u.map((x) => x.toFixed(2)).join("/")} (${out.tickers.join("/")})  고정 최적 ${wStatic.map(pct).join("/")}`);
for (const [k, r] of Object.entries(res)) {
  const s = { p10: q(r.pm, 0.1), p50: q(r.pm, 0.5), p90: q(r.pm, 0.9), mean: r.pm.reduce((a, x) => a + x, 0) / r.pm.length, t5: q(r.term, 0.05), t50: q(r.term, 0.5), loss: r.term.filter((x) => x < V0).length / r.term.length };
  out.policies[k] = s;
  console.log(`${k.padEnd(14)} 식단별 P(목표) 하위10/중앙/상위10 ${pct(s.p10)}/${pct(s.p50)}/${pct(s.p90)} 평균 ${pct(s.mean)} | 평가액 하위5 ${eok(s.t5)} 중앙 ${eok(s.t50)} | 손실 ${pct(s.loss)}`);
}
const avgW = (y, fb) => { const xs = fb.filter((x) => x.year === y); return xs.length ? holdings.map((_, i) => xs.reduce((s, x) => s + x.w[i], 0) / xs.length) : null; };
for (const k of ["피드백(매년 갱신)", "피드백+하방 보호"]) for (const y of [0, 1, 2]) { const w = y ? avgW(y, res[k].wy) : choose(Math.log(V0), T, m0, O0, policies[k].lam); out.policies[k]["w" + y] = w; console.log(`${k} ${y}년 뒤 평균 비중 ${w.map(pct).join("/")}`); }
if (process.argv[2]) fs.writeFileSync(process.argv[2], JSON.stringify(out));
}
