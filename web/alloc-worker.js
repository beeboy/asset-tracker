// 비중 조정안 계산 전용 worker: 무거운 시뮬레이션을 화면 스레드 밖에서 돌려 화면이 멈추지 않게 한다
self.window = self;
importScripts("model.js" + self.location.search); // 화면과 같은 버전의 계산 모듈
self.onmessage = (e) => {
  const { id, inp } = e.data;
  try { const r = Model.allocPlans(inp, (k, n) => self.postMessage({ id, prog: [k, n] })); self.postMessage({ id, done: r }); }
  catch (err) { self.postMessage({ id, err: String(err && err.message || err) }); }
};
