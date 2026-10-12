// 웹(web/model.js)과 아이폰 앱(ios-app/Naeilo/Data/MonteCarlo.swift)의 전망 몬테카를로가 같은 입력에 같은 결과를 내는지 본다.
//   node tools/mc_parity.mjs gen <폴더>   → <폴더>/input.json (앱이 받는 stats.json 모양 + 보유·목표), <폴더>/web.json (웹 결과)
//   (앱 쪽) ios-app/tools/mc_parity 로 <폴더>/input.json → <폴더>/app.json
//   node tools/mc_parity.mjs cmp <폴더>   → 두 결과를 비교 (다르면 종료 코드 1)
// 웹은 시세 원본으로 buildModel 을 돌리고, 앱은 그 시세로 만든 stats.json 값으로 모형을 만든다 (실제 쓰임과 같은 경로).
// GitHub Actions 의 ios.yml 이 macOS 에서 돌린다.
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { loadModel, fxOf, siteSeries, webDefaults, buildStats } from "./site_stats.mjs";

const Model = loadModel();
const [cmd, dir] = process.argv.slice(2);
if (!cmd || !dir) { console.error("사용: node tools/mc_parity.mjs gen|cmp <폴더>"); process.exit(2); }

const START = "2026-10-12";
function cases() {
  const { series } = siteSeries(), last = (k) => series[k].adj[series[k].adj.length - 1], usd = last("KRW=X");
  const h = (ticker, shares, ccy = "USD", price) => { const p = price ?? last(ticker); return { ticker, ccy, price0: p, valueKrw: shares * p * (ccy === "USD" ? usd : 1) }; };
  const { events } = webDefaults();
  return [
    // 개발자 실제 구성과 같은 종목 (실적·보호예수·외부 요인 사건이 모두 걸린다)
    { name: "dev", holdings: [h("TSLA", 687), h("NVDA", 300), h("SPCX", 317), h("SGOV", 90)], goal: 1e9, goalDate: "2029-10-04", monthly: 0, nPaths: 3000, events },
    // 샘플 + 사이트 통계에 없는 종목, 월 적립, 사건 하나 더 (변동성 키우기·이틀 뒤·확률 문자열)
    { name: "sample", holdings: [h("DRNK", 4), h("QQQ", 1), h("ZZZZ", 3, "USD", 120)], goal: 5e6, goalDate: Model.addMonths(START, 36), monthly: 3e5, nPaths: 1000,
      events: [...events, { id: "t1", on: true, date: "2026-10-14", target: "ZZZZ", kind: "실적", cat: "corp", repeat: "semi", prob: "80", mean: -3, sd: 6, vol_mult: 1.5, vol_days: 10 }] },
    // 원화 종목(환율 요인 없음)·현금성, 5년 목표
    { name: "krw", holdings: [h("SMH", 5), h("SGOV", 20), h("005930.KS", 30, "KRW", 61000)], goal: 3e7, goalDate: Model.addMonths(START, 60), monthly: 1e5, nPaths: 600, events },
  ];
}

const pickR = (R) => R && { p_goal: R.p_goal, p_touch: R.p_touch, terminal: R.terminal, bands: R.bands, byYear: R.byYear.map((y) => ({ year: y.year, date: y.date, p: y.p })), mdd_median: R.mdd_median };

if (cmd === "gen") {
  mkdirSync(dir, { recursive: true });
  const stats = buildStats(START), { settings } = webDefaults(), { series } = siteSeries();
  const input = { stats: JSON.parse(JSON.stringify(stats)), start: START, cases: [] }, web = { cases: [] };
  for (const c of cases()) {
    const tks = c.holdings.map((x) => x.ticker).filter((t) => series[t]);
    const betas = Model.factorBetas(series, tks, START).beta;
    const model = Model.buildModel({ holdings: c.holdings, series, fxOf, settings, events: c.events, betas, startDate: START, goalDate: c.goalDate });
    const common = { holdings: c.holdings, scenario: settings.scenario, nPaths: c.nPaths, seed: Number(settings.seed), goal: c.goal, monthly: c.monthly, rebalance: false, dof: settings.t_dof, fxOf, usdKrw0: 1 };
    const t0 = performance.now();
    const sim = Model.simulate(model, { ...common, withEvents: true }), simNo = Model.simulate(model, { ...common, withEvents: false });
    const ms = performance.now() - t0, A = c.holdings.length;
    const mu = (key) => model.factors.slice(0, A).map((f) => (typeof key === "number" ? Model.blendMu(f.mu.base, f.mu.smooth ?? f.mu.base, key / 100) : f.mu[key] ?? f.mu.base));
    const views = {};
    for (const [nm, s] of [["ev", sim], ["no", simNo]]) for (const key of ["base", "smooth", settings.trust]) views[nm + ":" + key] = pickR(Model.reweight(s, { mu: mu(key), goal: c.goal, monthly: c.monthly }));
    // 앱 회복 확률·목표 적립 (웹 본전 카드 breakeven, reweight req50): 전부 묶음 / 첫 종목만
    const muT = mu(settings.trust), V0 = sim.V0, rw = Model.reweight(sim, { mu: muT, goal: c.goal, monthly: c.monthly });
    const bep = { all: Model.breakeven(sim, { idx: c.holdings.map((_, i) => i), basis: V0 * 1.15, mu: muT, months: [6, 12, 36] }).map((x) => x.p),
      first: Model.breakeven(sim, { idx: [0], basis: c.holdings[0].valueKrw * 1.1, mu: muT, months: [6, 12, 36] }).map((x) => x.p), req50: rw.req50 };
    input.cases.push({ name: c.name, holdings: c.holdings.map(({ ticker, ccy, valueKrw, price0 }) => ({ ticker, ccy, valueKrw, price0 })), events: c.events, goal: c.goal, goalDate: c.goalDate, monthly: c.monthly, nPaths: c.nPaths, trust: settings.trust });
    web.cases.push({
      name: c.name, ms,
      model: { days: model.days.length, monthDates: model.monthDates, events: model.eventList.length,
        factors: model.factors.map((f) => ({ key: f.key, vol: f.vol, base: f.mu.base, smooth: f.mu.smooth, blend: f.mu.blend, evVar: f.evVar })) },
      sim: pickR(sim), simNo: pickR(simNo), views, bep,
    });
    console.log(`웹 ${c.name}: 경로 ${c.nPaths} · ${model.days.length}거래일 · 사건 ${model.eventList.length}회 · ${ms.toFixed(0)}ms · 목표 확률 ${(sim.p_goal * 100).toFixed(1)}%`);
  }
  writeFileSync(dir + "/input.json", JSON.stringify(input));
  writeFileSync(dir + "/web.json", JSON.stringify(web));
} else if (cmd === "cmp") {
  const web = JSON.parse(readFileSync(dir + "/web.json", "utf8")), app = JSON.parse(readFileSync(dir + "/app.json", "utf8"));
  let bad = 0, n = 0, worst = 0;
  const fail = (path, a, b) => { if (bad++ < 30) console.error(`다름 ${path}: 웹 ${a} · 앱 ${b}`); };
  // 금액·변동성은 상대 오차 1e-9, 확률은 경로 하나 차이까지 (libm 의 마지막 자리 차이로 경계 경로가 갈릴 수 있다)
  const num = (path, a, b, tol) => {
    n++;
    if (a == null || b == null) { if (a != b) fail(path, a, b); return; }
    const d = Math.abs(a - b) / Math.max(1e-300, tol.abs ? 1 : Math.abs(a));
    worst = Math.max(worst, tol.abs ? 0 : d);
    if (d > (tol.abs ?? tol.rel)) fail(path, a, b);
  };
  const res = (path, a, b, P) => {
    if (!a || !b) { if (!!a !== !!b) fail(path, !!a, !!b); return; }
    const pt = { abs: 1.5 / P }, rt = { rel: 1e-9 };
    num(path + ".p_goal", a.p_goal, b.p_goal, pt); num(path + ".p_touch", a.p_touch, b.p_touch, pt); num(path + ".mdd_median", a.mdd_median, b.mdd_median, rt);
    for (const k of Object.keys(a.terminal)) num(`${path}.terminal.${k}`, a.terminal[k], b.terminal[k], rt);
    for (const k of Object.keys(a.bands)) {
      if (a.bands[k].length !== b.bands[k]?.length) { fail(`${path}.bands.${k}.length`, a.bands[k].length, b.bands[k]?.length); continue; }
      a.bands[k].forEach((v, i) => num(`${path}.bands.${k}[${i}]`, v, b.bands[k][i], rt));
    }
    if (a.byYear.length !== b.byYear.length) fail(path + ".byYear.length", a.byYear.length, b.byYear.length);
    a.byYear.forEach((y, i) => { const z = b.byYear[i] || {}; if (y.date !== z.date) fail(`${path}.byYear[${i}].date`, y.date, z.date); num(`${path}.byYear[${i}].p`, y.p, z.p, pt); });
  };
  for (const w of web.cases) {
    const a = app.cases.find((x) => x.name === w.name);
    if (!a) { fail(w.name, "있음", "없음"); continue; }
    const P = a.nPaths;
    if (w.model.days !== a.model.days) fail(w.name + ".days", w.model.days, a.model.days);
    if (w.model.events !== a.model.events) fail(w.name + ".events", w.model.events, a.model.events);
    if (JSON.stringify(w.model.monthDates) !== JSON.stringify(a.model.monthDates)) fail(w.name + ".monthDates", w.model.monthDates.join(), a.model.monthDates.join());
    w.model.factors.forEach((f, i) => { const g = a.model.factors[i] || {}; if (f.key !== g.key) fail(`${w.name}.factor[${i}]`, f.key, g.key);
      for (const k of ["vol", "base", "smooth", "blend", "evVar"]) num(`${w.name}.${f.key}.${k}`, f[k], g[k], { rel: 1e-9 }); });
    res(w.name + ".sim", w.sim, a.sim, P); res(w.name + ".simNo", w.simNo, a.simNo, P);
    for (const k of Object.keys(w.views)) res(`${w.name}.${k}`, w.views[k], a.views[k], P);
    // 회복 확률 (breakeven) · 월 경계 평가액(앱 PathSet.values = reweight) · 목표 적립(req50)
    for (const k of ["all", "first"]) w.bep[k].forEach((v, i) => num(`${w.name}.bep.${k}[${i}]`, v, a.bep?.[k]?.[i], { abs: 1.5 / P }));
    num(`${w.name}.bep.req50`, w.bep.req50, a.bep?.req50, { rel: 1e-6 });
    res(`${w.name}.paths`, w.views["ev:" + a.trust], a.paths, P);
    console.log(`${w.name}: 웹 ${w.model.days}거래일 ${w.ms.toFixed(0)}ms · 앱 ${a.ms.toFixed(0)}ms · 목표 확률 웹 ${(w.sim.p_goal * 100).toFixed(2)}% 앱 ${(a.sim?.p_goal * 100).toFixed(2)}%`);
  }
  console.log(`비교 ${n}개, 다름 ${bad}개, 금액 최대 상대 오차 ${worst.toExponential(2)}`);
  process.exit(bad ? 1 : 0);
}
