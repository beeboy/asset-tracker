// 연구용 2단계: 식단(θ=μ,Σ) 1000개에 대해 비중 w 를 고른다 (정적 강건 제어)
// 식단마다 목표 확률을 로그정규 닫힌식으로 계산해 비중 후보 전부를 한꺼번에 평가하고,
// 고른 비중은 기존 몬테카를로(web/model.js simulate, 매년 재조정)로 다시 확인한다.
// 실행: INPUT=<입력값.json> node research/ensemble-robust.js [출력.json]
"use strict";
const fs = require("fs");
const E = require("./ensemble-forecast.js");
const { M, TD, set, goal, start, holdings, V0, model, common, rngOf, q, drawMeal } = E;
const A = holdings.length, scen = set.scenario;
const fxi = model.factors.findIndex((f) => f.kind === "fx" && f.key === "KRW=X");
const T = (Date.parse(goal.date) - Date.parse(start)) / (365.25 * 86400e3), need = Math.log(goal.amount / V0);
const Phi = (x) => { const t = 1 / (1 + 0.2316419 * Math.abs(x)), d = 0.3989423 * Math.exp(-x * x / 2), p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274)))); return x > 0 ? 1 - p : p; };

// 식단 → 원화 기준 연 산술 드리프트 α 와 공분산 Σ (종목 + 환율 합)
function krwParams(fsx, C) {
  const sd = fsx.map((f) => f.vol), al = fsx.map((f) => Math.log(1 + (f.kind === "fx" ? f.mu.base : f.mu[scen])));
  const cov = (i, j) => C[i][j] * sd[i] * sd[j];
  const inK = (a) => holdings[a].ccy !== "KRW" && fxi >= 0;
  const alpha = holdings.map((_, a) => al[a] + (inK(a) ? al[fxi] + cov(a, fxi) : 0));
  const S = holdings.map((_, i) => holdings.map((_, j) => cov(i, j) + (inK(i) ? cov(fxi, j) : 0) + (inK(j) ? cov(i, fxi) : 0) + (inK(i) && inK(j) ? cov(fxi, fxi) : 0)));
  return { alpha, S };
}
const pGoal = (P, w) => {
  let a = 0, v = 0; for (let i = 0; i < A; i++) { a += w[i] * P.alpha[i]; for (let j = 0; j < A; j++) v += w[i] * w[j] * P.S[i][j]; }
  const g = a - 0.5 * v; return v > 1e-12 ? Phi((g * T - need) / Math.sqrt(v * T)) : (g * T >= need ? 1 : 0);
};

// 식단 1000개
const MEALS = Number(process.env.MEALS) || 1000, rng = rngOf((Number(set.seed) || 1) ^ 0xc0ffee);
const meals = Array.from({ length: MEALS }, () => { const m = drawMeal(rng); return krwParams(m.fs, m.C); });
const nominal = krwParams(model.factors, model.corr);

// 비중 후보: 5% 간격 전체 (합 100%)
const STEP = 5, cands = [];
(function rec(i, left, cur) { if (i === A - 1) { cands.push([...cur, left].map((x) => x / 100)); return; } for (let x = 0; x <= left; x += STEP) rec(i + 1, left - x, [...cur, x]); })(0, 100, []);
const w0 = holdings.map((h) => h.valueKrw / V0);
const stats = (w) => { const ps = meals.map((P) => pGoal(P, w)); return { w, nom: pGoal(nominal, w), mean: ps.reduce((s, x) => s + x, 0) / ps.length, p10: q(ps, 0.1), p50: q(ps, 0.5), p90: q(ps, 0.9) }; };
const all = cands.map(stats), cur = stats(w0);
const best = (key) => all.reduce((b, x) => (x[key] > b[key] ? x : b));
// μ 오차의 주된 방향 (외란 방향): 식단별 α 공분산의 첫 고유벡터 (거듭제곱법)
const am = meals[0].alpha.map((_, i) => meals.reduce((s, P) => s + P.alpha[i], 0) / MEALS);
const Om = am.map((_, i) => am.map((_, j) => meals.reduce((s, P) => s + (P.alpha[i] - am[i]) * (P.alpha[j] - am[j]), 0) / (MEALS - 1)));
let u = am.map(() => 1); for (let k = 0; k < 200; k++) { const v = Om.map((r) => r.reduce((s, x, j) => s + x * u[j], 0)); const n = Math.hypot(...v); u = v.map((x) => x / n); }
const cos = (w) => Math.abs(w.reduce((s, x, i) => s + x * u[i], 0)) / Math.hypot(...w);
const muSd = (w) => Math.sqrt(w.reduce((s, x, i) => s + w.reduce((t, y, j) => t + x * y * Om[i][j], 0), 0));
// 분포 폭을 가장 좁히되 중앙값은 지금보다 나쁘지 않게
const narrow = all.filter((x) => x.p50 >= cur.p50).reduce((b, x) => (x.p90 - x.p10 < b.p90 - b.p10 ? x : b));
const picks = { 현재: cur, 기존방식최적: best("nom"), 평균최적: best("mean"), 강건하위10: best("p10"), 폭최소: narrow };

// 몬테카를로 검증: 식단 NV 개 × 경로 NP 개, 매년 재조정
const NV = Number(process.env.VERIFY_MEALS) || 100, NP = Number(process.env.VERIFY_P) || 200, vr = rngOf(4242);
const vMeals = Array.from({ length: NV }, () => drawMeal(vr));
function verify(w) {
  const hs = holdings.map((h, a) => ({ ...h, valueKrw: V0 * w[a] }));
  const ps = vMeals.map((m, k) => M.simulate(m.model, { ...common, holdings: hs, rebalance: true, nPaths: NP, seed: 777 + k }).p_goal);
  const fixed = M.simulate(model, { ...common, holdings: hs, rebalance: true, nPaths: 3000, seed: 777 }).p_goal;
  return { same: fixed, mean: ps.reduce((s, x) => s + x, 0) / ps.length, p10: q(ps, 0.1), p50: q(ps, 0.5), p90: q(ps, 0.9) };
}
const pct = (x) => (x * 100).toFixed(0) + "%";
const out = { start, T, V0, tickers: holdings.map((h) => h.ticker), disturbance: u, picks: {} };
console.log(`기간 ${T.toFixed(2)}년, 필요 연성장 ${pct(Math.exp(need / T) - 1)}, 외란 방향 ${u.map((x) => x.toFixed(2)).join("/")} (${out.tickers.join("/")})`);
for (const [k, s] of Object.entries(picks)) {
  const v = verify(s.w);
  out.picks[k] = { ...s, cos: cos(s.w), muSd: muSd(s.w), mc: v };
  console.log(`${k.padEnd(8)} w ${s.w.map(pct).join("/").padEnd(16)} 닫힌식 P 하위10/중앙/상위10 ${pct(s.p10)}/${pct(s.p50)}/${pct(s.p90)}  μ흔들림 ${pct(muSd(s.w))} cos ${cos(s.w).toFixed(2)} | MC 같은식단 ${pct(v.same)} 다른식단 ${pct(v.p10)}/${pct(v.p50)}/${pct(v.p90)}`);
}
if (process.argv[2]) fs.writeFileSync(process.argv[2], JSON.stringify(out));
