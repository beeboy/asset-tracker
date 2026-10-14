// 무거운 계산 전용 worker (비중 조정안, 연구용 비교, 전망 몬테카를로): 시뮬레이션을 화면 스레드 밖에서 돌려 화면이 멈추지 않게 한다
self.window = self;
importScripts("model.js" + self.location.search, "research.js" + self.location.search); // 화면과 같은 버전의 계산 모듈
const fxOf = (ccy) => (!ccy || ccy === "KRW" ? null : ccy === "USD" ? "KRW=X" : ccy + "KRW=X"); // app.js 의 fxOf 와 같다 (함수는 넘길 수 없어서)
const sim = ({ model, opt, cats, slim }) => {
  const R = Model.simulate(model, { ...opt, fxOf, cats: cats && new Set(cats) });
  return slim ? { p_goal: R.p_goal, terminal: R.terminal } : R;
};
self.onmessage = (e) => {
  const { id, inp, kind } = e.data, fn = kind === "sim" ? sim : kind === "stab" ? Research.stabilizer : kind === "reach" ? Model.reachTimes : Model.allocPlans;
  try { const r = fn(inp, (k, n) => self.postMessage({ id, prog: [k, n] })); self.postMessage({ id, done: r }); }
  catch (err) { self.postMessage({ id, err: String(err && err.message || err) }); }
};
