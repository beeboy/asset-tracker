// 앱의 외부 요인 / 배당·세금 / 환율 영향 화면이 웹과 같은 숫자를 내도록, 웹이 시세에서 뽑는 값을 data/calc.json 에 저장한다.
// - 요인 민감도: web/app.js factorBetas() 와 같은 식 (시장·금리·유가·금값·원자재)
// - 배당: web/app.js dividends() 와 같은 식 (수정종가/종가 비율이 바뀌는 날 = 배당락, 금액 = 전날 종가 × (1 - 전날 비율/당일 비율)). 지난 36개월
// - 환율: web/model.js indicators() 를 원/달러(KRW=X)에 그대로 돌린 값 (칼만 추세, 1개월·3개월·1년 변화, 변동성) + 지난 3년 종가·추세선
// 앱은 날짜 계산(지난 12개월, 증가율, 앞으로 3년)과 세율을 웹 식 그대로 한다. 원시 시세가 공개 저장소를 떠나도 이 파일만 있으면 된다
// 사용: node tools/site_calc.mjs   (stats.yml 이 매일 돌린다)
import { readFileSync, writeFileSync } from "node:fs";
import vm from "node:vm";

const root = new URL("../", import.meta.url);
const read = (p) => JSON.parse(readFileSync(new URL(p, root), "utf8"));
const ctx = { window: {}, console, Math, Date };
vm.runInNewContext(readFileSync(new URL("web/model.js", root), "utf8"), ctx);
const Model = ctx.window.Model;

const index = read("data/index.json").prices;
const kst = new Date(Date.now() + 9 * 3600e3).toISOString().slice(0, 10);
const r6 = (x) => (x == null || !Number.isFinite(x) ? null : +x.toPrecision(6));

// web/app.js dividends(t) 와 같은 식
export function dividends(p) {
  const out = [];
  if (!p || !p.adj || !p.close) return out;
  for (let i = 1; i < p.dates.length; i++) {
    const r0 = p.adj[i - 1] / p.close[i - 1], r1 = p.adj[i] / p.close[i];
    if (r0 > 0 && r1 > 0 && r0 / r1 < 0.9995) out.push({ d: p.dates[i], amt: p.close[i - 1] * (1 - r0 / r1) });
  }
  return out;
}

const divs = {}, from = Model.addMonths(kst, -37);
let fx = null;
for (const [k, file] of Object.entries(index)) {
  if (k === "^TNX") continue;
  const o = read("data/prices/" + file);
  if (k === "KRW=X") {
    const ind = Model.indicators(o.dates, o.close), s = ind.sig, n = o.dates.length, k0 = Math.max(0, n - 780), i0 = Math.max(0, n - 253);
    fx = {
      date: o.dates.at(-1), close: r6(s.close), kalman_level: r6(s.kalman_level), dev_from_kalman: r6(s.dev_from_kalman),
      ret_1m: r6(s.ret_1m), ret_3m: r6(s.ret_3m), ret_1y: r6(s.ret_1y), vol_ewma: r6(s.vol_ewma), vol_hist: r6(s.vol_hist), slope_ann: r6(s.slope_ann),
      trend: s.trend, hi1: r6(Math.max(...o.close.slice(i0))), lo1: r6(Math.min(...o.close.slice(i0))),
      dates: o.dates.slice(k0), closes: o.close.slice(k0).map(r6), level: ind.level.slice(k0).map(r6),
    };
    continue;
  }
  if (k.endsWith("=X") || k.endsWith("=F")) continue;
  const ds = dividends(o).filter((x) => x.d > from);
  divs[k] = { ccy: o.currency || "USD", last: o.dates.at(-1), price: r6(o.close.at(-1)), events: ds.map((x) => ({ d: x.d, a: r6(x.amt) })) };
}

// web/app.js alignedChanges · factorBetas 와 같은 식: 지난 3년 일별 로그 변화(금리는 bp)로 회귀, 시장 외 요인은 SPY 를 같이 넣은 두 변수 회귀,
// 이력이 짧으면 n/(n+60) 만큼만 믿고 나머지는 기본값(시장 1, 그 외 0)
const FACTORS = { mkt: "SPY", rate: "^TNX", oil: "CL=F", gold: "GC=F", cmdty: "DBC" };
const P = {}; for (const [k, file] of Object.entries(index)) P[k] = read("data/prices/" + file);
const mean = (a) => a.reduce((x, y) => x + y, 0) / a.length;
function alignedChanges(A, B, rateB) {
  if (!A || !B) return null;
  const cut = Model.addMonths(kst, -36), mb = new Map(B.dates.map((d, i) => [d, B.adj[i]]));
  const xs = [], ys = [], ds = []; let pa = null, pb = null;
  for (let i = 0; i < A.dates.length; i++) {
    const d = A.dates[i]; if (d < cut || !mb.has(d)) continue;
    const a = A.adj[i], b = mb.get(d);
    if (pa != null && a > 0 && pa > 0 && b != null && pb != null && (rateB || (b > 0 && pb > 0))) { ys.push(Math.log(a / pa)); xs.push(rateB ? (b - pb) * 100 : Math.log(b / pb)); ds.push(d); }
    pa = a; pb = b;
  }
  return { x: xs, y: ys, d: ds };
}
export function factorBetas(tks) {
  const beta = {};
  for (const [fk, sym] of Object.entries(FACTORS)) {
    beta[fk] = {};
    if (!P[sym]) continue;
    for (const t of tks) {
      if (t === sym) { beta[fk][t] = 1; continue; }
      const r = alignedChanges(P[t], P[sym], fk === "rate"); if (!r || r.y.length < 30) continue;
      const n = r.y.length, mx = mean(r.x), my = mean(r.y);
      let sxy = 0, sxx = 0; for (let i = 0; i < n; i++) { sxy += (r.x[i] - mx) * (r.y[i] - my); sxx += (r.x[i] - mx) ** 2; }
      let b = sxx ? sxy / sxx : 0;
      if (fk !== "mkt" && P.SPY && t !== "SPY") {
        const m2 = alignedChanges(P[t], P.SPY, false), mm = new Map(m2 ? m2.d.map((d, i) => [d, m2.x[i]]) : []);
        const X1 = [], X2 = [], Y = []; r.d.forEach((d, i) => { if (mm.has(d)) { X1.push(r.x[i]); X2.push(mm.get(d)); Y.push(r.y[i]); } });
        if (Y.length > 30) {
          const a1 = mean(X1), a2 = mean(X2), ay = mean(Y); let s11 = 0, s22 = 0, s12 = 0, s1y = 0, s2y = 0;
          for (let i = 0; i < Y.length; i++) { const u = X1[i] - a1, v = X2[i] - a2, w = Y[i] - ay; s11 += u * u; s22 += v * v; s12 += u * v; s1y += u * w; s2y += v * w; }
          const det = s11 * s22 - s12 * s12; if (det > 0) b = (s1y * s22 - s2y * s12) / det;
        }
      }
      const w = n / (n + 60), prior = fk === "mkt" ? 1 : 0;
      beta[fk][t] = r6(w * b + (1 - w) * prior);
    }
  }
  return beta;
}
const betas = factorBetas(Object.keys(index).filter((k) => !k.endsWith("=X") && !k.endsWith("=F") && k !== "^TNX"));

export const out = { date: kst, at: new Date().toISOString(), basis: "web/app.js factorBetas() · dividends() · web/model.js indicators(KRW=X)", betas, divs, fx };
if (process.argv[1] && import.meta.url.endsWith(process.argv[1].split("/").pop())) {
  writeFileSync(new URL("data/calc.json", root), JSON.stringify(out) + "\n");
  console.log("calc.json", kst, Object.entries(divs).filter(([, v]) => v.events.length).map(([k, v]) => `${k} ${v.events.length}회`).join(" · "), "· 환율", fx && fx.close);
}
