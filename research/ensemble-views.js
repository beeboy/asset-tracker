// 연구용 6단계: 사용자 전망을 종목별 μ(t) 의 "모양"으로 넣고, 코어 유지 + SGOV 완충 규칙을 비교한다.
// 질량-스프링-댐퍼에서 m 이 천천히 줄지, 갑자기 줄지, 그대로 있다가 줄지 전망을 넣는 것과 같은 구조:
//   NVDA 완만한 상승: μ(t) 가 처음 μ0 에서 3년 동안 서서히 줄어든다
//   TSLA 점프 앞둠:   평소 μ 는 낮고, 3년 안에 확률 p 로 한 번 큰 점프 (시점·크기 무작위)
//   SPCX 횡보 후 상승: d 개월 동안 μ≈0, 그 뒤 μ 가 커진다
//   공통 외란: 시장 전체 오차 β·m (식단마다 하나), 변동성·상관·실적 사건·t 충격은 기존 엔진 그대로
// 실행: INPUT=<입력값.json> AVG=236.25,47.44,106.64,106.60 node research/ensemble-views.js [출력.json]
"use strict";
const fs = require("fs");
const F = require("./ensemble-feedback.js");
const { E, beta } = F;
const { M, TD, set, goal, holdings, V0, common, rngOf, q, drawMeal } = E;
const A = holdings.length, scen = set.scenario, ix = (t) => holdings.findIndex((h) => h.ticker === t);
const iT = ix("TSLA"), iN = ix("NVDA"), iS = ix("SPCX"), iC = holdings.findIndex((h, a) => E.model.factors[a].cash);
const TAX = 0.22, DED = 2.5e6, TAU_M = 0.06;
const AVG = process.env.AVG ? process.env.AVG.split(",").map(Number) : holdings.map((h) => h.price0);
const lnG = Math.log(goal.amount), Ty = (Date.parse(goal.date) - Date.parse(E.start)) / (365.25 * 86400e3);

// 전망 한 벌 뽑기 → 종목별 시점 t(년) 까지의 누적 로그 드리프트 함수와 점프
function drawView(r) {
  const nvMu0 = 0.15 + 0.05 * r.normal(), nvDecay = 0.2 + 0.3 * r.next(); // 3년 뒤 μ 가 처음의 (1-decay)
  const tsBase = 0.03 + 0.08 * r.normal(), tsP = 0.4 + 0.3 * r.next(), tsJump = r.next() < tsP ? { t: r.next() * Ty, size: Math.log(1.6) + 0.25 * r.normal() } : null;
  const spFlat = (3 + 9 * r.next()) / 12, spMu = 0.25 + 0.10 * r.normal();
  const L = (mu) => Math.log(1 + Math.max(-0.6, mu));
  return {
    // 누적 드리프트 ∫₀ᵗ ln(1+μ(s)) ds (월 단위로 충분히 매끄럽다)
    cum(a, t) {
      if (a === iN) { const n = 24, dt = t / n; let s = 0; for (let k = 0; k < n; k++) { const u = (k + 0.5) * dt; s += L(nvMu0 * (1 - nvDecay * u / Ty)) * dt; } return s; }
      if (a === iT) return L(tsBase) * t + (tsJump && t >= tsJump.t ? tsJump.size : 0);
      if (a === iS) return t <= spFlat ? 0 : L(spMu) * (t - spFlat);
      return null; // 그 밖의 종목은 엔진 그대로
    },
    info: { nvMu0, nvDecay, tsBase, tsP, tsJump: !!tsJump, spFlat, spMu },
  };
}

// 매도 세금 (평균단가법, 연 공제 후 22%) — 해마다 실현 차익을 모아 연말에 낸다
function sellGain(h, cost, a, amt) { const frac = Math.min(1, amt / h[a]), g = (h[a] - cost[a]) * frac; cost[a] *= 1 - frac; h[a] -= amt; return g; }

const strategies = {
  "그대로 보유": {},
  "규칙1 공제 안에서 차익 실현→SGOV": { harvest: true },
  "규칙2 목표 경로 앞선 몫의 절반→SGOV": { ahead: 0.5 },
  "규칙2+3 (급락 때 SGOV→주식)": { ahead: 0.5, reentry: 0.2 },
};
const NT = Number(process.env.TRUE_MEALS) || 300, NP = Number(process.env.PATHS_PER) || 100, rng = rngOf(2718);
const res = Object.fromEntries(Object.keys(strategies).map((k) => [k, { pm: [], after: [], term: [], tax: [], hedged: [] }]));
const viewsLog = [];
for (let t = 0; t < NT; t++) {
  const meal = drawMeal(rng), mk = TAU_M * rng.normal(), view = drawView(rng);
  // 엔진 μ 는 시장 공통 오차만 남기고 (회사 고유 부분은 전망이 대신한다)
  meal.fs.forEach((f, a) => { if (a < A && !f.cash) for (const k of Object.keys(f.mu)) f.mu[k] = Math.exp(beta[a] * mk) - 1; });
  const R = M.simulate(meal.model, { ...common, nPaths: NP, seed: 9100 + t, rebalance: false, withEvents: true });
  const X = R.raw, Mn = X.M, P = X.P, tk = X.monthIdx.map((d) => d / TD);
  const over = holdings.map((_, a) => tk.map((tt) => view.cum(a, tt))); // 전망 덧씌우기 (없으면 null)
  const g = (a, k, p) => (k ? X.cg[a][k * P + p] : 0) + (over[a][k] ?? 0);
  viewsLog.push(view.info);
  for (const [name, st] of Object.entries(strategies)) {
    let hit = 0;
    for (let p = 0; p < P; p++) {
      const h = holdings.map((hd) => hd.valueKrw), cost = holdings.map((hd, a) => (hd.valueKrw * AVG[a]) / hd.price0);
      let gainY = 0, taxSum = 0, peakRisk = 0, hedged = 0, maxHedged = 0;
      for (let k = 1; k < Mn; k++) {
        for (let a = 0; a < A; a++) h[a] *= Math.exp(g(a, k, p) - g(a, k - 1, p));
        const V = h.reduce((s, x) => s + x, 0), risk = V - (iC >= 0 ? h[iC] : 0);
        peakRisk = Math.max(peakRisk, risk);
        const quarter = k % 3 === 0 && k < Mn - 1, yearEnd = k % 12 === 0 || k === Mn - 1;
        if (quarter && st.ahead) {
          const path = Math.exp(Math.log(V0) + (lnG - Math.log(V0)) * tk[k] / Ty), ex = Math.min((V - path) * st.ahead, risk * 0.95);
          if (ex > 0 && risk > 0) { for (let a = 0; a < A; a++) if (a !== iC && h[a] > 0) { const amt = ex * h[a] / risk; gainY += sellGain(h, cost, a, amt); h[iC] += amt; cost[iC] += amt; } hedged += ex; peakRisk = risk - ex; }
        }
        if (quarter && st.reentry && hedged > 0 && risk < peakRisk * (1 - st.reentry)) {
          const amt = Math.min(hedged, h[iC] * 0.95), rk = risk; gainY += sellGain(h, cost, iC, amt);
          for (let a = 0; a < A; a++) if (a !== iC && h[a] > 0) { const add = amt * h[a] / rk; h[a] += add; cost[a] += add; }
          hedged -= amt; peakRisk = risk + amt;
        }
        if (yearEnd && k < Mn - 1 && st.harvest) {
          // 공제(250만) 를 채우는 만큼만 차익이 큰 종목을 팔아 SGOV 로
          let room = Math.max(0, DED - gainY);
          for (const a of [iT, iN, iS]) { if (room <= 0 || h[a] <= 0) continue; const gr = 1 - cost[a] / h[a]; if (gr <= 0) continue; const amt = Math.min(h[a], room / gr); gainY += sellGain(h, cost, a, amt); h[iC] += amt; cost[iC] += amt; room = Math.max(0, DED - gainY); }
        }
        if (yearEnd) { const Vn = h.reduce((s2, x) => s2 + x, 0), tax = Math.max(0, gainY - DED) * TAX; if (tax > 0) { h[iC] >= tax ? (h[iC] -= tax) : h.forEach((x, a) => (h[a] -= tax * x / Vn)); taxSum += tax; } gainY = 0; }
        maxHedged = Math.max(maxHedged, h[iC] / h.reduce((s, x) => s + x, 0));
      }
      const V = h.reduce((s, x) => s + x, 0);
      let finG = 0; for (let a = 0; a < A; a++) finG += h[a] - cost[a];
      if (V >= goal.amount) hit++;
      res[name].term.push(V); res[name].after.push(V - Math.max(0, finG - DED) * TAX); res[name].tax.push(taxSum); res[name].hedged.push(maxHedged);
    }
    res[name].pm.push(hit / P);
  }
}
const pct = (x) => (x * 100).toFixed(0) + "%", eok = (x) => (x / 1e8).toFixed(2) + "억", mean = (a) => a.reduce((s, x) => s + x, 0) / a.length;
const out = { setup: { trueMeals: NT, pathsPer: NP, TAU_M }, avg: AVG, viewSample: viewsLog.slice(0, 20), cond: null, strategies: {} };
console.log(`전망 반영, 진짜 식단 ${NT} × 경로 ${NP}, TSLA 점프 실제 발생 비율 ${pct(mean(viewsLog.map((v) => (v.tsJump ? 1 : 0))))}`);
for (const [k, r] of Object.entries(res)) {
  const s = { p10: q(r.pm, 0.1), p50: q(r.pm, 0.5), p90: q(r.pm, 0.9), mean: mean(r.pm), a5: q(r.after, 0.05), a50: q(r.after, 0.5), t50: q(r.term, 0.5), taxMean: mean(r.tax), hedgedMed: q(r.hedged, 0.5) };
  out.strategies[k] = s;
  console.log(`${k.padEnd(22)} P(목표) ${pct(s.p10)}/${pct(s.p50)}/${pct(s.p90)} 평균 ${pct(s.mean)} | 청산 후 하위5 ${eok(s.a5)} 중앙 ${eok(s.a50)} | 매년 세금 합 ${eok(s.taxMean)} | SGOV 최대비중 중앙 ${pct(s.hedgedMed)}`);
}
const hold = res["그대로 보유"].pm, by = (f) => { const xs = hold.filter((_, i) => f(viewsLog[i])); return xs.length ? mean(xs) : null; };
out.cond = { jump: by((v) => v.tsJump), noJump: by((v) => !v.tsJump), spFlatShort: by((v) => v.spFlat < 0.5), spFlatLong: by((v) => v.spFlat >= 0.5), nvStrong: by((v) => v.nvMu0 >= 0.15), nvWeak: by((v) => v.nvMu0 < 0.15) };
console.log(`그대로 보유 P(목표) 조건부: TSLA 점프 있음 ${pct(out.cond.jump)} / 없음 ${pct(out.cond.noJump)}, SPCX 횡보 6개월 미만 ${pct(out.cond.spFlatShort)} / 이상 ${pct(out.cond.spFlatLong)}, NVDA μ0 15% 이상 ${pct(out.cond.nvStrong)} / 미만 ${pct(out.cond.nvWeak)}`);
if (process.argv[2]) fs.writeFileSync(process.argv[2], JSON.stringify(out));
