// 연구용 4단계: 하방 보호 피드백을 기존 몬테카를로 엔진(web/model.js simulate: 실적·보호예수 사건, t분포 충격, 환율)으로 재검증
// 경로는 simulate 가 만들고(재조정 없음 → 종목별 누적 로그성장 cg), 정책은 그 경로 위에서 1년마다 비중을 바꾼다.
// 매도 차익에는 해외주식 양도세(연 250만 원 공제 후 22%)를 매기고 그해 말 평가액에서 낸다.
// 실행: INPUT=<입력값.json> BASIS=1 node research/ensemble-verify.js [출력.json]
//   BASIS: 취득가 / 현재가 비율 (1 = 지금 산 것으로 봄, 0.3 = 지금 가격의 30% 에 샀다고 봄)
"use strict";
const fs = require("fs");
const F = require("./ensemble-feedback.js");
const { E, drawTrue, choose, m0, O0, Sbar, T, A, mul, matInv } = F;
const { M, set, goal, holdings, V0, common, rngOf, q } = E;
const BASIS = Number(process.env.BASIS ?? 1), TAX = 0.22, DED = 2.5e6;
const LAMBDA = Number(process.env.LAMBDA) || 1;

const w0 = holdings.map((h) => h.valueKrw / V0);
const policies = {
  "지금 비중 그대로": { hold: true },
  "피드백": { feedback: true, lam: 0 },
  "피드백+하방 보호": { feedback: true, lam: LAMBDA },
};
const NT = Number(process.env.TRUE_MEALS) || 300, NP = Number(process.env.PATHS_PER) || 100, rng = rngOf(31337);
const res = Object.fromEntries(Object.keys(policies).map((k) => [k, { pm: [], term: [], tax: [], wy: [[], [], [], []] }]));
const t0 = Date.now();
for (let t = 0; t < NT; t++) {
  const tr = drawTrue(rng);
  const R = M.simulate(tr.model, { ...common, nPaths: NP, seed: 5000 + t, rebalance: false, withEvents: true });
  const X = R.raw, Mn = X.M, P = X.P, yearK = [];
  for (let k = 12; k + 6 < Mn - 1; k += 12) yearK.push(k);
  const cg = (a, k, p) => (k ? X.cg[a][k * P + p] : 0);
  for (const [name, pol] of Object.entries(policies)) {
    let hit = 0;
    for (let p = 0; p < P; p++) {
      let m = m0.slice(), O = O0.map((r) => r.slice()), w = pol.hold ? w0 : choose(Math.log(V0), T, m, O, pol.lam);
      let h = w.map((x) => x * V0), cost = holdings.map((_, a) => w0[a] * V0 * BASIS); // 취득원가 (금액)
      if (!pol.hold) { const s = settle(holdings.map((_, a) => w0[a] * V0), h, cost); cost = s.cost; h = h.map((x, a) => x - s.tax * w[a]); res[name].tax.push(s.tax); }
      if (t < 50 && p < 5 && !pol.hold) res[name].wy[0].push(w);
      let kPrev = 0;
      for (let yi = 0; yi <= yearK.length; yi++) {
        const k = yi < yearK.length ? yearK[yi] : Mn - 1;
        const y = holdings.map((_, a) => cg(a, k, p) - cg(a, kPrev, p));
        h = h.map((x, a) => x * Math.exp(y[a]));
        if (k === Mn - 1) break;
        if (!pol.hold) {
          const V = h.reduce((s, x) => s + x, 0);
          // 칼만 갱신: 관측 = 한 해 로그수익 + ½σ²
          const yy = y.map((x, i) => x + 0.5 * Sbar[i][i]), Rm = Sbar.map((r, i) => r.map((x, j) => x + O[i][j] + (i === j ? 1e-10 : 0)));
          const K = mul(O, matInv(Rm)); m = m.map((x, i) => x + K[i].reduce((s, kk, j) => s + kk * (yy[j] - m[j]), 0));
          const KO = mul(K, O); O = O.map((r, i) => r.map((x, j) => x - KO[i][j]));
          w = choose(Math.log(V), Math.max(0.05, T - (yi + 1)), m, O, pol.lam);
          const tgt = w.map((x) => x * V), s = settle(h, tgt, cost);
          cost = s.cost; h = tgt.map((x, a) => x - s.tax * w[a]); res[name].tax.push(s.tax);
          if (t < 50 && p < 5) res[name].wy[yi + 1].push(w);
        }
        kPrev = k;
      }
      const V = h.reduce((s, x) => s + x, 0);
      if (V >= goal.amount) hit++;
      res[name].term.push(V);
    }
    res[name].pm.push(hit / P);
  }
}
// 보유 h → 목표 tgt 로 바꿀 때 매도 차익 세금 (평균단가법). 반환: 새 원가, 세금
function settle(h, tgt, cost) {
  let gain = 0; const nc = cost.slice();
  for (let a = 0; a < A; a++) {
    if (tgt[a] < h[a] && h[a] > 0) { const sold = (h[a] - tgt[a]) / h[a]; gain += (h[a] - cost[a]) * sold; nc[a] = cost[a] * (1 - sold); }
    else if (tgt[a] > h[a]) nc[a] = cost[a] + (tgt[a] - h[a]);
  }
  return { cost: nc, tax: Math.max(0, gain - DED) * TAX };
}

const pct = (x) => (x * 100).toFixed(0) + "%", eok = (x) => (x / 1e8).toFixed(2) + "억";
const avg = (ws) => (ws.length ? holdings.map((_, i) => ws.reduce((s, w) => s + w[i], 0) / ws.length) : null);
const out = { basis: BASIS, lambda: LAMBDA, setup: { trueMeals: NT, pathsPer: NP }, tickers: holdings.map((h) => h.ticker), policies: {}, seconds: 0 };
console.log(`취득가 = 현재가 × ${BASIS}, 진짜 식단 ${NT} × 경로 ${NP} (사건·t분포·환율 포함)`);
for (const [k, r] of Object.entries(res)) {
  const s = { p10: q(r.pm, 0.1), p50: q(r.pm, 0.5), p90: q(r.pm, 0.9), mean: r.pm.reduce((a, x) => a + x, 0) / r.pm.length, t5: q(r.term, 0.05), t50: q(r.term, 0.5), loss: r.term.filter((x) => x < V0).length / r.term.length,
    taxPerPath: r.tax.reduce((a, x) => a + x, 0) / r.term.length, w: r.wy.map(avg) };
  out.policies[k] = s;
  console.log(`${k.padEnd(10)} P(목표) 하위10/중앙/상위10 ${pct(s.p10)}/${pct(s.p50)}/${pct(s.p90)} 평균 ${pct(s.mean)} | 하위5 ${eok(s.t5)} 중앙 ${eok(s.t50)} 손실 ${pct(s.loss)} | 세금 평균 ${eok(s.taxPerPath)}` + (s.w[0] ? ` | 비중 ${s.w.map((w) => w ? w.map(pct).join("/") : "-").join(" → ")}` : ""));
}
out.seconds = (Date.now() - t0) / 1000;
if (process.argv[2]) fs.writeFileSync(process.argv[2], JSON.stringify(out));
