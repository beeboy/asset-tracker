// 샘플(TSLA 1,000주) 계산값을 미리 만들어 data/sample_calc.json 에 저장한다.
// 첫 방문 사용자는 이 값을 그대로 보고(다시 계산 안 함), 내 종목을 넣으면 지금 값으로 새로 계산한다.
// 사용: 저장소 루트를 정적 서버로 띄운 뒤  BASE=http://localhost:8123 node tools/sample_snapshot.mjs
// (GitHub Actions 의 sample.yml 이 매일 한국 시간 9시에 돌린다)
import { chromium } from "playwright";
import { writeFileSync } from "node:fs";

const BASE = process.env.BASE || "http://localhost:8123";
const b = await chromium.launch(process.env.CHROMIUM ? { executablePath: process.env.CHROMIUM } : {});
const ctx = await b.newContext({ timezoneId: "Asia/Seoul", viewport: { width: 1200, height: 1400 } });
const p = await ctx.newPage();
p.on("pageerror", (e) => console.error("page error:", e.message));
const ls = (k) => p.evaluate((k) => { try { return JSON.parse(localStorage.getItem(k) || "null"); } catch (e) { return null; } }, k);
const until = async (f, ms = 180000) => { const t = Date.now(); while (Date.now() - t < ms) { if (await f()) return true; await p.waitForTimeout(500); } throw new Error("시간 초과"); };

await p.goto(`${BASE}/web/index.html?fresh`);
await p.waitForSelector("#tabs button[data-tab=dash]");
await p.click("#tabs button[data-tab=dash]");
await p.click('#histRange button[data-r="future"]');
const scen = { model: null, base: "base", smooth: "smooth" };
for (const bs of ["model", "base", "smooth"]) {
  if (!(await p.$(`#histBasis button[data-b="${bs}"]`))) continue;
  await p.click(`#histBasis button[data-b="${bs}"]`);
  await until(async () => { const c = await ls("naeilo-fcdash"); return c && Object.keys(c.r || {}).length >= (bs === "model" ? 1 : bs === "base" ? 2 : 3); });
}
await p.click("#tabs button[data-tab=analysis]");
await p.click("#anaNav button[data-a=forecast]");
await until(async () => !!(await ls("naeilo-forecast"))?.withEv);
await until(async () => !!(await ls("naeilo-alloc2"))?.out);
const strip = (o) => { if (!o) return o; const { sig, ...r } = o; return r; };
const kst = new Date(Date.now() + 9 * 3600e3).toISOString().slice(0, 10);
const out = { date: kst, at: new Date().toISOString(), forecast: strip(await ls("naeilo-forecast")), fcdash: strip(await ls("naeilo-fcdash")), alloc: strip(await ls("naeilo-alloc2")) };
writeFileSync(new URL("../data/sample_calc.json", import.meta.url), JSON.stringify(out));
console.log("sample_calc.json", kst, Math.round(JSON.stringify(out).length / 1024) + "KB", Object.keys(out.fcdash?.r || {}));
await b.close();
