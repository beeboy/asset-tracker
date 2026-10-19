// 종목별 전망 입력값(기대수익 μ·변동성 σ·상관)을 web/model.js 와 같은 계산으로 만들어 data/stats.json 에 저장한다.
// 아이폰 앱은 이 파일을 받아 웹과 같은 몬테카를로 전망(앱 MonteCarlo.swift)을 돌린다. 그래서 웹 첫 방문과 같은
// 전망 설정(model), 기본 사건 목록(events: 실적 발표·외부 요인), 종목별 요인 민감도(betas)도 같이 넣는다.
// 원시 시세가 나중에 공개 저장소를 떠나도 이 파일(파생 통계)만 있으면 앱은 그대로 쓴다.
// 사용: node tools/site_stats.mjs   (GitHub Actions 의 stats.yml 이 매일 돌린다)
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const root = new URL("../", import.meta.url);
const read = (p) => JSON.parse(readFileSync(new URL(p, root), "utf8"));
export function loadModel() {
  const ctx = { window: {}, console, Math, Date };
  vm.runInNewContext(readFileSync(new URL("web/model.js", root), "utf8"), ctx);
  return ctx.window.Model;
}
const Model = loadModel();
export const fxOf = (ccy) => (!ccy || ccy === "KRW" ? null : ccy === "USD" ? "KRW=X" : ccy + "KRW=X");
const SKIP = new Set(["^TNX"]);   // 금리(수익률)는 가격이 아니다
const p12 = (x) => (x == null || !Number.isFinite(x) ? null : +x.toPrecision(12));

/** 사이트 시세 파일 전부 → {series, holdings(종목만)} */
export function siteSeries() {
  const index = read("data/index.json").prices, series = {}, holdings = [];
  for (const [k, file] of Object.entries(index)) {
    const o = read("data/prices/" + file);
    series[k] = { dates: o.dates, adj: o.adj };
    if (SKIP.has(k) || k.endsWith("=X")) continue;
    holdings.push({ ticker: k, ccy: o.currency || "USD", valueKrw: 1 });
  }
  // 한국 종목 (키 없을 때 추정, data/kr_proxy.json): 기준 미국 ETF × 원/달러를 원화 종목처럼. 앱은 한국 종목을 이 키로 계산한다 (웹 synthKR 과 같은 수익률)
  const R = read("data/kr_proxy.json"), fx = series["KRW=X"];
  for (const b of new Set([...R.rules.map((r) => r.proxy), R.market])) {
    if (!series[b]) continue;
    for (const hedged of [false, true]) {
      const k = "KR~" + b + (hedged ? "~H" : ""), v = Model.krSeries(series[b], fx, hedged, "adj");
      series[k] = { dates: v.dates, adj: v.vals };
      holdings.push({ ticker: k, ccy: "KRW", valueKrw: 1 });
    }
  }
  return { series, holdings };
}

/** 웹 첫 방문 입력값(state.default.json 을 웹과 같게 정리)의 전망 설정과 사건 */
export function webDefaults() {
  const st = read("data/state.default.json");
  Model.normEvents(st);
  return { settings: Model.normModel(st.model), events: st.events };
}

/** stats.json 내용. startDate 기준 지난 history_years 년 */
export function buildStats(startDate, { series, holdings } = siteSeries()) {
  const { settings, events } = webDefaults();
  const goal = new Date(Date.parse(startDate) + 400 * 86400e3).toISOString().slice(0, 10);
  // 종목 값은 사건과 무관하게 만든다 (사건 몫 빼기·추세 환산은 앱이 사용자 사건으로 다시 한다: gs 로)
  const model = Model.buildModel({ holdings, series, fxOf, settings, events: [], betas: {}, startDate, goalDate: goal });
  const tickers = {};
  for (const f of model.factors) {
    tickers[f.key] = f.kind === "fx"
      ? { fx: true, mu: p12(f.mu.base), vol: p12(f.vol), n: f.n }
      : { mu: p12(f.mu.base), trend: p12(f.mu.smooth), gs: p12(f.gSmooth), vol: p12(f.vol), n: f.n, cash: f.cash || undefined, ccy: holdings.find((h) => h.ticker === f.key)?.ccy };
  }
  const fb = Model.factorBetas(series, holdings.map((h) => h.ticker), startDate), betas = fb.beta, betaStat = {};
  for (const fk of Object.keys(betas)) for (const t of Object.keys(betas[fk])) betas[fk][t] = p12(betas[fk][t]);
  // 외부 요인 화면의 신뢰도 ("10번 중 N번" = agree, 상관 corr, 날 수 n)
  for (const [fk, o] of Object.entries(fb.stat)) { betaStat[fk] = {}; for (const [t, v] of Object.entries(o)) betaStat[fk][t] = { corr: p12(v.corr), agree: p12(v.agree ?? 1), n: v.n }; }
  // 요인별 보통 한 달 변화 (외부 요인 순위·큰 변화 경고)
  const factors = Object.fromEntries(Object.entries(Model.factorMoves(series, startDate)).map(([k, v]) => [k, { sd: p12(v.sd), max: p12(v.max), n: v.n }]));
  const pick = (o, ks) => Object.fromEntries(ks.filter((k) => o[k] !== undefined).map((k) => [k, o[k]]));
  return {
    date: startDate,
    at: new Date().toISOString(),
    basis: "web/model.js buildModel, 지난 " + settings.history_years + "년 일별 종가, 사전값 수축. 사건은 앱이 events 로 반영",
    // 이 파일에 없는 종목에 웹이 쓰는 값 (μ = 사전값, σ = 기본 변동성, 위험 종목끼리 상관 = 기본 상관)
    defaults: { mu: settings.prior_mu / 100, vol: settings.default_vol / 100, corr: Number(settings.default_corr), trust: settings.trust / 100 },
    model: pick(settings, ["scenario", "trust", "n_paths", "seed", "t_dof", "earnings_adjust", "fx_drift", "fx_vol_mult", "prior_mu", "default_vol", "default_corr", "rebalance_yearly"]),
    events: events.map((e) => pick(e, ["id", "on", "date", "target", "factor", "kind", "cat", "repeat", "prob", "mean", "sd", "vol_mult", "vol_days"])),
    betas,
    betaStat,
    factors,
    tickers,
    corr: { keys: model.factors.map((f) => f.key), m: model.corr.map((r) => r.map(p12)) },
  };
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const kst = new Date(Date.now() + 9 * 3600e3).toISOString().slice(0, 10);
  const out = buildStats(kst);
  writeFileSync(new URL("data/stats.json", root), JSON.stringify(out) + "\n");
  console.log("stats.json", kst, out.corr.keys.length + "종목", out.events.length + "사건", Object.entries(out.tickers).map(([k, v]) => `${k} μ${v.mu?.toFixed(4)} σ${v.vol?.toFixed(4)}`).join(" · "));
}
