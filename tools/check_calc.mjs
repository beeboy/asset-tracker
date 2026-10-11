// data/calc.json 을 만드는 식(tools/site_calc.mjs)이 웹(web/app.js)의 dividends · Model.indicators 와 같은 값을 내는지 확인한다.
// web/app.js 에서 그 함수 원문을 그대로 꺼내 같은 시세로 돌려 비교한다. 다르면 실패 (stats.yml 이 calc.json 을 올리기 전에 돌린다)
// 사용: node tools/check_calc.mjs
import { readFileSync } from "node:fs";
import vm from "node:vm";
import { out } from "./site_calc.mjs";

const root = new URL("../", import.meta.url);
const read = (p) => JSON.parse(readFileSync(new URL(p, root), "utf8"));
const src = readFileSync(new URL("web/app.js", root), "utf8");
function fn(name) {
  const i = src.indexOf(`function ${name}(`); if (i < 0) throw new Error(`web/app.js 에 ${name} 없음`);
  let k = src.indexOf("{", src.indexOf(")", i)), depth = 0;
  for (; k < src.length; k++) { if (src[k] === "{") depth++; else if (src[k] === "}" && --depth === 0) break; }
  return src.slice(i, k + 1);
}

const mctx = { window: {}, console, Math, Date };
vm.runInNewContext(readFileSync(new URL("web/model.js", root), "utf8"), mctx);
const index = read("data/index.json").prices, prices = {};
for (const [k, f] of Object.entries(index)) prices[k] = read("data/prices/" + f);
const ctx = { Math, S: { prices } };
vm.runInNewContext(`${fn("dividends")}\nthis.dividends = dividends;`, ctx);

let bad = 0, n = 0;
const close = (a, b, tol) => a != null && b != null && Math.abs(a - b) <= tol * Math.max(1, Math.abs(a), Math.abs(b));
for (const [t, d] of Object.entries(out.divs)) {
  const ws = ctx.dividends(t).filter((x) => x.d > mctx.window.Model.addMonths(out.date, -37));
  n++;
  if (ws.length !== d.events.length || ws.some((x, i) => x.d !== d.events[i].d || !close(x.amt, d.events[i].a, 1e-5))) { bad++; console.log("배당 다름", t, ws.length, d.events.length); }
}
const ind = mctx.window.Model.indicators(prices["KRW=X"].dates, prices["KRW=X"].close).sig;
for (const k of ["close", "kalman_level", "ret_1m", "ret_3m", "ret_1y", "vol_ewma", "slope_ann"]) { n++; if (!close(ind[k], out.fx[k], 1e-5)) { bad++; console.log("환율 다름", k, ind[k], out.fx[k]); } }
console.log(bad ? `웹과 다른 값 ${bad}개 / ${n}` : `웹과 같음 (${n}개 확인)`);
process.exit(bad ? 1 : 0);
