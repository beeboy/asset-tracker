// 앱의 배당·세금 / 환율 영향 화면이 웹과 같은 숫자를 내도록, 웹이 시세에서 뽑는 값을 data/calc.json 에 저장한다.
// (외부 요인 민감도는 site_stats.mjs 가 Model.factorBetas 로 stats.json 에 넣는다)
// - 배당: web/app.js dividends() 와 같은 식 (수정종가/종가 비율이 바뀌는 날 = 배당락, 금액 = 전날 종가 × (1 - 전날 비율/당일 비율)). 지난 36개월
// - 환율: web/model.js indicators() 를 원/달러(KRW=X)에 그대로 돌린 값 (칼만 추세, 1개월·3개월·1년 변화, 변동성) + 지난 3년 종가·추세선
// 앱은 날짜 계산(지난 12개월, 증가율, 앞으로 3년)과 세율을 웹 식 그대로 한다. 원시 시세가 공개 저장소를 떠나도 이 파일만 있으면 된다
// 사용: node tools/site_calc.mjs   (stats.yml 이 매일 돌린다)
// 공개 판 가상 샘플: DATA_DIR=data/sample node tools/site_calc.mjs
import { readFileSync, writeFileSync } from "node:fs";
import vm from "node:vm";

const root = new URL("../", import.meta.url);
const read = (p) => JSON.parse(readFileSync(new URL(p, root), "utf8"));
const dir = (process.env.DATA_DIR || "data").replace(/\/+$/, "") + "/";   // 시세 폴더 (index.json·prices/)
const ctx = { window: {}, console, Math, Date };
vm.runInNewContext(readFileSync(new URL("web/model.js", root), "utf8"), ctx);
const Model = ctx.window.Model;

const index = read(dir + "index.json").prices;
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
  const o = read(dir + "prices/" + file);
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

export const out = { date: kst, at: new Date().toISOString(), basis: "web/app.js dividends() · web/model.js indicators(KRW=X)", divs, fx };
if (process.argv[1] && import.meta.url.endsWith(process.argv[1].split("/").pop())) {
  writeFileSync(new URL(dir + "calc.json", root), JSON.stringify(out) + "\n");
  console.log("calc.json", kst, Object.entries(divs).filter(([, v]) => v.events.length).map(([k, v]) => `${k} ${v.events.length}회`).join(" · "), "· 환율", fx && fx.close);
}
