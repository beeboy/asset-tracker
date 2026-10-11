// 종목별 전망 입력값(기대수익 μ·변동성 σ·상관)을 web/model.js 와 같은 계산으로 만들어 data/stats.json 에 저장한다.
// 아이폰 앱은 이 파일을 받아 3년 전망에 쓰므로, 웹과 앱이 같은 종목 값으로 확률을 낸다 (앱은 닫힌 식, 웹은 몬테카를로).
// 원시 시세가 나중에 공개 저장소를 떠나도 이 파일(파생 통계)만 있으면 앱은 그대로 쓴다.
// 사용: node tools/site_stats.mjs   (GitHub Actions 의 stats.yml 이 매일 돌린다)
import { readFileSync, writeFileSync } from "node:fs";
import vm from "node:vm";

const root = new URL("../", import.meta.url);
const read = (p) => JSON.parse(readFileSync(new URL(p, root), "utf8"));
const ctx = { window: {}, console, Math, Date };
vm.runInNewContext(readFileSync(new URL("web/model.js", root), "utf8"), ctx);
const Model = ctx.window.Model;

const settings = read("data/state.default.json").model;   // 웹 첫 방문 기본 설정과 같다
const index = read("data/index.json").prices;
const SKIP = new Set(["^TNX"]);   // 금리(수익률)는 가격이 아니다
const fxOf = (ccy) => (!ccy || ccy === "KRW" ? null : ccy === "USD" ? "KRW=X" : ccy + "KRW=X");

const series = {}, holdings = [];
for (const [k, file] of Object.entries(index)) {
  if (SKIP.has(k)) continue;
  const o = read("data/prices/" + file);
  series[k] = { dates: o.dates, adj: o.adj };
  if (k.endsWith("=X")) continue;
  holdings.push({ ticker: k, ccy: o.currency || "USD", valueKrw: 1 });
}
const kst = new Date(Date.now() + 9 * 3600e3).toISOString().slice(0, 10);
const goal = new Date(Date.parse(kst) + 400 * 86400e3).toISOString().slice(0, 10);
// 사건(실적 발표 등)은 사용자마다 달라 넣지 않는다
const model = Model.buildModel({ holdings, series, fxOf, settings, events: [], betas: {}, startDate: kst, goalDate: goal });

const r4 = (x) => (x == null || !Number.isFinite(x) ? null : +x.toFixed(4));
const tickers = {};
for (const f of model.factors) {
  tickers[f.key] = f.kind === "fx"
    ? { fx: true, mu: r4(f.mu.base), vol: r4(f.vol), n: f.n }
    : { mu: r4(f.mu.base), trend: r4(f.mu.smooth), vol: r4(f.vol), n: f.n, cash: f.cash || undefined, ccy: holdings.find((h) => h.ticker === f.key)?.ccy };
}
const keys = model.factors.map((f) => f.key);
const out = {
  date: kst,
  at: new Date().toISOString(),
  basis: "web/model.js buildModel, 지난 " + settings.history_years + "년 일별 종가, 사전값 수축, 사건 미반영",
  // 이 파일에 없는 종목에 웹이 쓰는 값 (μ = 사전값, σ = 기본 변동성, 위험 종목끼리 상관 = 기본 상관)
  defaults: { mu: settings.prior_mu / 100, vol: settings.default_vol / 100, corr: Number(settings.default_corr), trust: 0.5 },
  tickers,
  corr: { keys, m: model.corr.map((r) => r.map((v) => +v.toFixed(3))) },
};
writeFileSync(new URL("data/stats.json", root), JSON.stringify(out) + "\n");
console.log("stats.json", kst, keys.length + "종목", Object.entries(tickers).map(([k, v]) => `${k} μ${v.mu} σ${v.vol}`).join(" · "));
