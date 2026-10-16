// 웹(web/app.js)과 아이폰 앱의 화면 계산(평가액·배당·세금·금리 시나리오·위젯 필요 경로)이 같은 입력에 같은 숫자를 내는지 본다.
// mc_parity.mjs 는 전망 몬테카를로만 본다. 이 도구는 그 밖의 화면 계산과 "어느 환율을 쓰나"를 본다.
//   node tools/calc_parity.mjs gen <폴더>   → <폴더>/input.json, <폴더>/web.json, <폴더>/CalcSrc.swift
//   (앱 쪽) swiftc <폴더>/CalcSrc.swift ios-app/tools/calc_parity/main.swift → <폴더>/app.json
//   node tools/calc_parity.mjs cmp <폴더>   → 비교 (다르면 종료 코드 1)
// 양쪽 모두 원본 코드를 그대로 꺼내 돌린다 (check_calc.mjs 처럼). 웹은 web/app.js 의 함수·문장, 앱은 Swift 파일의 함수·줄.
// 그래서 원본 이름이나 모양이 바뀌면 "못 찾음"으로 실패한다. 그때는 아래 꺼내는 곳(marker)을 같이 고친다.
// 입력 자료: data/calc.json(배당), data/macro/rates_earnings.json(금리 식단). 이 파일이 바뀌어도 CI 가 돈다 (ios.yml).
// GitHub Actions 의 ios.yml parity 잡이 macOS 에서 돌린다.
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import vm from "node:vm";

const root = new URL("../", import.meta.url);
const text = (p) => readFileSync(new URL(p, root), "utf8");
const json = (p) => JSON.parse(text(p));
const [cmd, dir] = process.argv.slice(2);
if (!cmd || !dir) { console.error("사용: node tools/calc_parity.mjs gen|cmp <폴더>"); process.exit(2); }

// ---------------------------------------------------------------- 알려진 차이
// 아직 고치지 않은 차이. 여기 적힌 항목이 다르면 실패 대신 경고만 한다. 고쳐져서 같아지면 "지워 주세요" 경고가 뜬다.
// 원인은 docs/architecture.md 6절 번호
const KNOWN = {
};

// ---------------------------------------------------------------- 원본 꺼내기
// marker 부터 첫 { 의 짝 } 까지 (Swift 블록, JS 함수)
function block(src, marker, file) {
  const i = src.indexOf(marker); if (i < 0) throw new Error(`${file} 에서 못 찾음: ${marker}`);
  let k = src.indexOf("{", i), depth = 0;
  for (; k < src.length; k++) { if (src[k] === "{") depth++; else if (src[k] === "}" && --depth === 0) break; }
  return src.slice(i, k + 1);
}
// marker 부터 괄호 밖 첫 ; 까지 (JS 문장 하나)
function stmt(src, marker, file) {
  const i = src.indexOf(marker); if (i < 0) throw new Error(`${file} 에서 못 찾음: ${marker}`);
  let depth = 0, k = i;
  for (; k < src.length; k++) { const c = src[k]; if ("([{".includes(c)) depth++; else if (")]}".includes(c)) depth--; else if (c === ";" && depth === 0) break; }
  return src.slice(i, k + 1);
}
// marker 가 든 줄 하나 (Swift 한 줄 정의)
function line(src, marker, file) {
  const i = src.indexOf(marker); if (i < 0) throw new Error(`${file} 에서 못 찾음: ${marker}`);
  return src.slice(src.lastIndexOf("\n", i) + 1, src.indexOf("\n", i)).trim();
}
// 정규식 첫 묶음
function grab(src, re, file) { const m = src.match(re); if (!m) throw new Error(`${file} 에서 못 찾음: ${re}`); return m[1]; }

// ---------------------------------------------------------------- 같은 입력
const kstToday = () => new Date(Date.now() + 9 * 3600e3).toISOString().slice(0, 10);
const daysAgo = (t, n) => new Date(Date.parse(t + "T00:00:00Z") - n * 86400e3).toISOString().slice(0, 10);

function inputs(T) {
  const calc = json("data/calc.json"), macro = json("data/macro/rates_earnings.json");
  const LIVE = 1402.3, MAR = 1391.5;   // 지금 환율과 매매기준율을 일부러 다르게: 어느 쪽을 쓰는지 드러나게
  const fresh = { rate: MAR, date: daysAgo(T, 1) };
  // 환율 고르기: 매매기준율 날짜가 0·1·5·6·30일 전, 없음, 0원
  const fxRule = [0, 1, 5, 6, 30].map((n) => ({ name: `mar-${n}d`, mar: { rate: MAR, date: daysAgo(T, n) }, live: LIVE }))
    .concat([{ name: "mar-none", mar: null, live: LIVE }, { name: "mar-zero", mar: { rate: 0, date: T }, live: LIVE }]);
  // 평가액: 미국·국내 종목 섞어서, 매매기준율이 새것일 때·묵었을 때
  const hold = [{ t: "TSLA", ccy: "USD", qty: 125.5, last: 251.37, avg: 180.2 }, { t: "SCHD", ccy: "USD", qty: 300, last: 27.41, avg: 29.9 },
    { t: "005930.KS", ccy: "KRW", qty: 30, last: 61000, avg: 72000 }, { t: "SGOV", ccy: "USD", qty: 90, last: 100.52, avg: 0 }];
  const val = [{ name: "fresh", mar: fresh, live: LIVE, rows: hold }, { name: "stale", mar: { rate: MAR, date: daysAgo(T, 9) }, live: LIVE, rows: hold }];
  // 배당: 사이트 calc.json 의 실제 배당락 + 증가율이 걸리는 원화 가짜 종목
  const real = ["SCHD", "SGOV", "QQQ", "AAPL"].filter((k) => calc.divs[k]?.events?.length);
  const fake = []; for (let y = 3; y >= 0; y--) for (const m of ["01-15", "04-15", "07-15", "10-15"]) { const d = `${Number(T.slice(0, 4)) - y}${"-" + m}`; if (d < T) fake.push({ d, a: 300 * (1 + 0.08 * (3 - y)) }); }
  const divs = real.map((k, i) => ({ name: k, ccy: "USD", events: calc.divs[k].events, shares: [300, 90, 7, 12.5][i], price: calc.divs[k].price }))
    .concat([{ name: "KRDIV", ccy: "KRW", events: fake, shares: 40, price: 52000 }]);
  // 세금: 미국 종목만, 올해 실현 이익 0 (실현 이익을 더할지, 미국 밖 외화 종목을 넣을지는 아직 안 정함: 6절 17번. 검사하지 않는다)
  const tax = [{ name: "usd-gain", rows: hold }, { name: "usd-loss", rows: [{ t: "SCHD", ccy: "USD", qty: 300, last: 27.41, avg: 29.9 }] }];
  // 금리: 식단 1000벌, 금리 변화·기대수익 몇 가지. 목표 확률 이동(shift)은 기간 3년(같아야 함)·1.5년(알려진 차이)
  const rate = { coef: macro.meals.coef, g0: macro.meals.g_now, cases: [[-1, 0], [0, 0], [0.5, 0.03], [2, -0.02]].map(([dr, m]) => ({ dr, m })), kb: 1.23, T: [3, 1.5],
    // 금리 충격 테스트 닫힌 식 (목표 가능성 변화·도달 시점·금액) 입력: 기간·변동성·기대·지금 자산·목표·매달 넣는 돈
    closed: [{ T: 3, sg: 0.18, mu: 0.08, V0: 3e8, K: 1e9, M: 2e6 }, { T: 1.5, sg: 0.25, mu: 0.05, V0: 5e8, K: 4e8, M: 0 }, { T: 10, sg: 0.15, mu: 0.07, V0: 1e8, K: 5e8, M: 1e6 },
      { T: 2, sg: 0.2, mu: -0.02, V0: 1e7, K: 1e10, M: 0 }],
    shifts: [-0.03, -0.013, 0, 0.004, 0.02] };
  // 위젯 페이스의 필요 경로 (웹 내 길 goalPath 의 at ↔ 위젯 Snapshot.need): 적립 없음, 적립 있음, 적립 합이 목표보다 큼
  const start = daysAgo(T, 200), goalDate = `${Number(start.slice(0, 4)) + 3}${start.slice(4)}`;
  const dates = [start, daysAgo(T, 100), T, `${Number(T.slice(0, 4)) + 1}${T.slice(4)}`, goalDate];
  const wpath = [0, 3e6, 3e7].map((monthly) => ({ name: `m${monthly}`, start, goalDate, goal: 1e9, v0: 3e8, monthly, dates }));
  return { today: T, live: LIVE, fresh, fxRule, val, divs, tax, rate, wpath };
}

// ---------------------------------------------------------------- 웹 계산 (web/app.js 원본)
function web(I) {
  const src = text("web/app.js"), F = "web/app.js";
  const mctx = { window: {}, console, Math, Date };
  vm.runInNewContext(text("web/model.js"), mctx);
  const Model = mctx.window.Model;
  const parts = {
    yearsBetween: block(src, "function yearsBetween(", F), marUsd: stmt(src, "const marUsd = ", F), fxBase: stmt(src, "const fxBase = ", F),
    valuation: block(src, "function valuation()", F), consts: stmt(src, "const WHT = ", F), divGrowth: block(src, "function divGrowth(", F),
    byM: stmt(src, "const byM = ", F), divLoop: stmt(src, "hs.forEach((r) => {\n      const all = dividends(", F),
    wa: stmt(src, "const wa = hs.filter(", F), gains: stmt(src, "const gains = wa.map(", F), sum: stmt(src, "const sum = gains.reduce(", F),
  };
  const rsrc = block(src, "function rateScenDraw(", F);
  const rate = { head: stmt(rsrc, "const M = d.meals", F), tot: stmt(rsrc, "const tot = ", F), band: stmt(rsrc, "const band = ", F), yr: stmt(rsrc, "const yr = ", F),
    mkt: stmt(rsrc, "const mkt = band(", F), shifts: stmt(rsrc, "const shifts = M.coef.map(", F) };
  const closedSrc = ["function rsNormCDF(", "function rsClosed(", "function rsMonths("].map((k) => block(src, k, F)).join("\n");

  // 웹 상태 하나 (매매기준율·지금 환율·보유)
  const env = (mar, live, rows = []) => {
    const ctx = { Math, Date, Number, Object, Model, S: { mar: mar ? { USD: mar } : null, state: { holdings: rows.map((r) => ({ ticker: r.t, shares: r.qty, avg_cost: r.avg })) }, quotes: {}, prices: {} },
      today: () => I.today, fxNow: (ccy) => (ccy === "USD" ? live : ccy === "KRW" ? 1 : null), isKR: (t) => /\.(KS|KQ)$/.test(t),
      curPrice: (h) => ({ v: rows.find((r) => r.t === h.ticker).last, src: "close" }) };
    vm.runInNewContext(`const ccyOf = (t) => (isKR(t) ? "KRW" : "USD");\n${parts.yearsBetween}\n${parts.marUsd}\n${parts.fxBase}\n${parts.valuation}\n${parts.consts}\n${parts.divGrowth}
      this.fxBase = fxBase; this.valuation = valuation; this.divGrowth = divGrowth; this.C = { WHT, CGT, CGT_DED };`, ctx);
    return ctx;
  };
  const out = { fxRule: [], val: [], divs: [], tax: [], rate: [] };
  for (const c of I.fxRule) out.fxRule.push({ name: c.name, fx: env(c.mar, c.live).fxBase("USD") });
  for (const c of I.val) { const { rows, total } = env(c.mar, c.live, c.rows).valuation(); out.val.push({ name: c.name, rows: rows.map((r) => r.valueKrw), total }); }
  out.consts = env(null, I.live).C;
  // 배당: 종목 하나씩 웹 renderCash 의 반복문을 그대로 (환율은 웹 평가액 행의 r.fx = fxBase)
  for (const c of I.divs) {
    const e = env(I.fresh, I.live, [{ t: c.name === "KRDIV" ? "KRDIV.KS" : c.name, ccy: c.ccy, qty: c.shares, last: c.price }]);
    e.dividends = () => c.events.map((x) => ({ d: x.d, amt: x.a }));
    const r = vm.runInNewContext(`(() => { const { rows } = valuation(), hs = rows, yAgo = Model.addMonths(today(), -12), months = []; for (let k = 1; k <= 36; k++) months.push(Model.addMonths(today(), k).slice(0, 7));
      ${parts.byM}\n${parts.divLoop}\n return { per, byM, fx: rows[0].fx }; })()`, e);
    const p = r.per[0];
    out.divs.push({ name: c.name, fx: r.fx, out: p ? { n: p.n, y1: p.y1, y3: p.y3, gr: p.gr, yld: p.yld, byMonth: Object.fromEntries(Object.entries(r.byM).filter(([, v]) => v)) } : null });
  }
  // 세금: 웹 renderCash 의 "지금 모두 판다면" (평가 이익 합계 → 공제 뒤 22%)
  for (const c of I.tax) {
    const e = env(I.fresh, I.live, c.rows);
    const r = vm.runInNewContext(`(() => { const { rows } = valuation(), hs = rows.filter((r) => r.valueKrw > 0);\n${parts.wa}\n${parts.gains}\n${parts.sum}\n return { sum, taxAll }; })()`, e);
    out.tax.push({ name: c.name, gain: r.sum, tax: r.taxAll });
  }
  // 금리 시나리오: 웹 rateScenDraw 의 식단 계산
  for (const c of I.rate.cases) for (const T of I.rate.T) {
    const ctx = { Math, Float64Array, Model, d: { meals: { coef: I.rate.coef, g_now: I.rate.g0 } }, RS: { dr: c.dr, m: c.m * 100 }, T, kb: I.rate.kb };
    const r = vm.runInNewContext(`(() => { ${rate.head}\n${rate.tot}\n${rate.band}\n${rate.yr}\n${rate.mkt}\n${rate.shifts}
      const drop = Array.from({ length: H }, (_, y) => tot.filter((t) => t[y] < 0).length / tot.length), dropCum = cum.filter((t) => t[H - 1] < 0).length / cum.length;
      return { yr, cum: cb, drop, dropCum, mkt, shifts }; })()`, ctx);
    out.rate.push({ dr: c.dr, m: c.m, T, yr: r.yr, cum: r.cum, drop: r.drop, dropCum: r.dropCum, mkt: r.mkt, shift: r.shifts });
  }
  // 금리 충격 테스트 닫힌 식: 웹 rsClosed·rsMonths
  const cf = vm.runInNewContext(`${closedSrc}\n({ rsClosed, rsMonths })`, { Math });
  out.closed = I.rate.closed.map((c) => ({ p: I.rate.shifts.map((s) => cf.rsClosed(c, s)), months: I.rate.shifts.map((s) => cf.rsMonths(c, s)) }));
  // 위젯 필요 경로: 웹 내 길 goalPath 의 at(날짜). 위젯에는 0~1 비율 f = 시작부터 날짜까지 ÷ 시작부터 목표일까지 로 넘긴다
  const gp = block(src, "function goalPath(", F), yb = block(src, "function yearsBetween(", F);
  out.wpath = I.wpath.map((c) => {
    const ctx = { Math, Model, today: () => I.today, krNow: () => 0, actualSeries: () => null,
      S: { state: { goal: { start_date: c.start, date: c.goalDate, amount: c.goal, monthly_contribution: c.monthly }, kr_log: [] } } };
    const r = vm.runInNewContext(`${yb}\n${gp}\n(() => { const P = goalPath({ dates: [${JSON.stringify(c.start)}], total: [${c.v0}] }), span = yearsBetween(${JSON.stringify(c.start)}, ${JSON.stringify(c.goalDate)});
      return ${JSON.stringify(c.dates)}.map((d) => ({ f: yearsBetween(${JSON.stringify(c.start)}, d) / span, v: P.at(d) })); })()`, ctx);
    return { name: c.name, f: r.map((x) => x.f), need: r.map((x) => x.v) };
  });
  return out;
}

// ---------------------------------------------------------------- 앱 코드 꺼내기 → CalcSrc.swift
function swiftSrc() {
  const sc = text("ios-app/Naeilo/Data/SiteCalc.swift"), det = text("ios-app/Naeilo/Views/AnalysisDetail.swift"), ana = text("ios-app/Naeilo/Views/AnalysisView.swift");
  const mk = text("ios-app/Naeilo/Data/Market.swift"), am = text("ios-app/Naeilo/Data/AppModel.swift"), eng = text("ios-app/WidgetShared/Engine.swift");
  // SiteCalc 의 "웹과 같은 식" 부분 (클래스 끝 } 앞까지)
  const cls = block(sc, "final class SiteCalc {", "SiteCalc.swift"), mark = cls.indexOf("// MARK: 웹과 같은 식");
  if (mark < 0) throw new Error("SiteCalc.swift 에서 못 찾음: // MARK: 웹과 같은 식");
  const siteCalc = cls.slice(mark, cls.lastIndexOf("}"));
  const div = block(det, "struct DividendView: View {", "AnalysisDetail.swift");
  // 화면이 쓰는 환율 식 (Market.shared.fx.last / Market.shared.valFx 등)
  const divFx = grab(div, /\bfx = (Market\.shared\.[\w.]+)/, "DividendView"), sumFx = grab(det, /hubMonthly[\s\S]*?\bfx = (Market\.shared\.[\w.]+)/, "DividendView.hubMonthly");
  const us = line(div, "let us = m.rows.filter", "DividendView"), usU = line(div, "let usU = ", "DividendView");
  const shifts = line(det, "let shifts: [Double] = r.slope.map", "RateScenarioView");
  // 금리 충격 테스트 닫힌 식 (RateScenarioView 의 Closed·closed·months). private 만 떼어 낸다
  const rsv = block(det, "struct RateScenarioView: View {", "AnalysisDetail.swift");
  const closedSw = [line(rsv, "private struct Closed {", "RateScenarioView"), block(rsv, "private static func closed(", "RateScenarioView"), block(rsv, "private static func months(", "RateScenarioView")]
    .map((x) => x.replace(/^private /, "")).join("\n    ");
  // 위젯 필요 경로 (Snapshot.span·need)
  const wpath = `struct WPath {
    var v0: Double?; var start: String?; var goalDate: String; var goal: Double; var monthly: Double
    ${block(eng, "var span: Double? {", "Engine.swift")}
    ${block(eng, "func need(_ f: Double) -> Double {", "Engine.swift")}
}
func widgetNeed(v0: Double, start: String, goalDate: String, goal: Double, monthly: Double, f: [Double]) -> [Double]? {
    let w = WPath(v0: v0, start: start, goalDate: goalDate, goal: goal, monthly: monthly); return f.map { w.need($0) }
}`;
  return `import Foundation
// tools/calc_parity.mjs gen 이 앱 원본에서 그대로 꺼낸 코드. 손으로 고치지 않는다

enum SiteCalc {
    ${line(sc, "struct Ev: Codable", "SiteCalc.swift")}
${siteCalc}
}

${block(det, "struct RateScen {", "AnalysisDetail.swift")}

// RateScenarioView 원본: 금리 충격 테스트 닫힌 식
enum AppModel { ${block(am, "static func normCDF(", "AppModel.swift")} }
enum RSV {
    ${closedSw}
}

${block(eng, "enum Day {", "Engine.swift")}

// Market 대신: 매매기준율·지금 환율만 가진 상태. 아래 두 줄은 Market.swift 원본
struct MarFile: Codable { var rate: Double; var date: String }
struct FxQuote { var last: Double }
struct TossFx { var rate: Double }
struct FxState {
    var mar: MarFile?
    var fx: FxQuote
    var tossFx: TossFx? = nil   // 내 토스 키 환율: 웹에는 없어서 비교는 키 없는 경우만
    ${line(mk, "var valFx: Double", "Market.swift")}
    ${line(mk, "var marRate: Double?", "Market.swift")}
}
enum Market { static var shared = FxState(mar: nil, fx: FxQuote(last: 0)) }

// AppModel 행 대신
struct Symbol { enum Ccy { case usd, krw }; var currency: Ccy; var last: Double }
struct Hold { var avg: Double; var qty: Double }
struct Row { var id: String; var sym: Symbol; var h: Hold }
struct M { var rows: [Row] }
enum App {
    // AppModel.swift 원본
    ${line(am, "func krw(_ s: Symbol, _ x: Double) -> Double", "AppModel.swift").replace(/^func /, "static func ")}
    // 배당·세금 화면(DividendView)과 분석 허브 배당(DividendView.hubMonthly)이 쓰는 환율
    static func divFx() -> Double { ${divFx} }
    static func sumFx() -> Double { ${sumFx} }
    // DividendView 원본: 미국 종목 평가 이익
    static func usGain(_ m: M) -> Double {
        let fx = divFx()
        ${us}
        ${usU}
        return usU
    }
    // RateScenarioView 원본: 식단마다 목표 확률을 옮기는 양
    static func shifts(_ r: RateScen, kb: Double, d: Double, mm: Double) -> [Double] {
        ${shifts}
        return shifts
    }
}

// 위젯 Engine.swift 원본
${wpath}
`;
}

// ---------------------------------------------------------------- gen / cmp
if (cmd === "gen") {
  mkdirSync(dir, { recursive: true });
  const I = inputs(kstToday());
  writeFileSync(dir + "/input.json", JSON.stringify(I));
  writeFileSync(dir + "/web.json", JSON.stringify(web(I)));
  writeFileSync(dir + "/CalcSrc.swift", swiftSrc());
  console.log(`웹 계산 ${I.today}: 환율 ${I.fxRule.length} · 평가액 ${I.val.length} · 배당 ${I.divs.length} · 세금 ${I.tax.length} · 금리 ${I.rate.cases.length * I.rate.T.length} · 위젯 필요 경로 ${I.wpath.length}`);
} else if (cmd === "cmp") {
  const W = JSON.parse(readFileSync(dir + "/web.json", "utf8")), A = JSON.parse(readFileSync(dir + "/app.json", "utf8"));
  let bad = 0, n = 0;
  const knownHit = new Set(), known = (path) => Object.keys(KNOWN).find((k) => path.startsWith(k));
  const fail = (path, a, b) => {
    const k = known(path);
    if (k) { if (!knownHit.has(k)) console.log(`알려진 차이 ${k} (${KNOWN[k]}): 웹 ${a} · 앱 ${b}`); knownHit.add(k); return; }
    if (bad++ < 40) console.error(`다름 ${path}: 웹 ${a} · 앱 ${b}`);
  };
  const num = (path, a, b, rel = 1e-9) => {
    n++;
    if (a == null || b == null) { if (a != b) fail(path, a, b); return; }
    if (Math.abs(a - b) > rel * Math.max(1, Math.abs(a), Math.abs(b))) fail(path, a, b);
  };
  const arr = (path, a, b, rel) => { if ((a || []).length !== (b || []).length) return fail(path + ".length", a?.length, b?.length); a.forEach((v, i) => num(`${path}[${i}]`, v, b[i], rel)); };
  const by = (list, name) => (list || []).find((x) => x.name === name);

  for (const k of ["WHT", "CGT", "CGT_DED"]) num(`세율.${k}`, W.consts[k], A.consts?.[k]);
  for (const w of W.fxRule) num(`환율고르기.${w.name}`, w.fx, by(A.fxRule, w.name)?.fx);
  for (const w of W.val) { const a = by(A.val, w.name); arr(`평가액.${w.name}.rows`, w.rows, a?.rows); num(`평가액.${w.name}.total`, w.total, a?.total); }
  for (const w of W.divs) {
    const a = by(A.divs, w.name), p = `배당.${w.name}`;
    num(p + ".환율", w.fx, a?.fx);
    num(p + ".분석첫화면환율", w.fx, a?.sumFx);
    if (!w.out || !a?.out) { if (!!w.out !== !!a?.out) fail(p, !!w.out, !!a?.out); continue; }
    num(p + ".n", w.out.n, a.out.n); for (const k of ["y1", "y3", "gr", "yld"]) num(`${p}.${k}`, w.out[k], a.out[k]);
    const keys = [...new Set([...Object.keys(w.out.byMonth), ...Object.keys(a.out.byMonth)])].sort();
    for (const m of keys) num(`${p}.byMonth.${m}`, w.out.byMonth[m] ?? 0, a.out.byMonth[m] ?? 0);
  }
  for (const w of W.tax) { const a = by(A.tax, w.name); num(`세금.${w.name}.이익`, w.gain, a?.gain); num(`세금.${w.name}.세금`, w.tax, a?.tax); }
  for (const w of W.rate) {
    const a = (A.rate || []).find((x) => x.dr === w.dr && x.m === w.m && x.T === w.T), p = `rate.${w.dr}.${w.m}.T${w.T}`;
    if (!a) { fail(p, "있음", "없음"); continue; }
    if (w.T === 3) {   // 식단 띠는 기간과 무관: 한 번만
      w.yr.forEach((b, y) => arr(`${p}.yr[${y}]`, b, a.yr[y])); w.cum.forEach((b, y) => arr(`${p}.cum[${y}]`, b, a.cum[y]));
      arr(p + ".drop", w.drop, a.drop); num(p + ".dropCum", w.dropCum, a.dropCum); arr(p + ".mkt", w.mkt, a.mkt);
    }
    arr(`rate.shift.T${w.T}.${w.dr}.${w.m}`, w.shift, a.shift, 1e-9);
  }
  (W.closed || []).forEach((w, i) => {
    const a = (A.closed || [])[i], p = `금리충격.닫힌식[${i}]`;
    if (!a) return fail(p, "있음", "없음");
    arr(p + ".p", w.p, a.p); arr(p + ".months", w.months, a.months);
  });
  for (const w of W.wpath || []) {
    const a = by(A.wpath, w.name);
    arr(`위젯필요경로.${w.name}`, w.need, a?.need);
  }
  for (const k of Object.keys(KNOWN)) if (!knownHit.has(k)) console.log(`::warning::알려진 차이 "${k}" 가 이제 같아요. tools/calc_parity.mjs KNOWN 에서 지워 주세요`);
  console.log(`비교 ${n}개, 다름 ${bad}개, 알려진 차이 ${knownHit.size}개`);
  process.exit(bad ? 1 : 0);
}
