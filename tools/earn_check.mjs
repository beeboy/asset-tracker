// 내 몫 실적 계산 검사: tools/earn_fixtures.json 의 기대값과 tools/earn_calc.mjs 의 답이 같은지 본다 (금액은 1e-9 이내)
//   node tools/earn_check.mjs
import fs from "fs";
import { view } from "./earn_calc.mjs";

const F = JSON.parse(fs.readFileSync(new URL("./earn_fixtures.json", import.meta.url)));
const same = (a, b) =>
  typeof a === "number" && typeof b === "number" ? Math.abs(a - b) < 1e-9
  : Array.isArray(b) ? Array.isArray(a) && a.length === b.length && b.every((x, i) => same(a[i], x))
  : b && typeof b === "object" ? !!a && Object.keys(b).every((k) => same(a[k], b[k]))
  : a === b;
let bad = 0;
for (const c of F.cases) {
  const got = view(c.input, c.qty, c.fx, c.at);
  const miss = c.expect === null ? (got === null ? [] : ["null 이어야 함"]) : Object.keys(c.expect).filter((k) => !same(got && got[k], c.expect[k]));
  if (miss.length) { bad++; console.log(`실패 ${c.name}: ` + miss.map((k) => `${k} 기대 ${JSON.stringify(c.expect?.[k])} 받음 ${JSON.stringify(got?.[k])}`).join(" / ")); }
  else console.log(`통과 ${c.name}`);
}
process.exit(bad ? 1 : 0);
