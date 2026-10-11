// naeilo 서비스 워커: 오프라인에서도 마지막으로 본 화면·데이터를 연다 (네트워크 우선, 실패하면 저장본)
// 푸시 알림: 내용 없는 푸시를 받으면 알림 서버(워커)에서 문구를 받아 보여 준다
// 판 번호(?v=)가 붙은 파일(app.js·style.css·시세 파일 등)은 내용이 바뀌면 주소가 바뀌므로 저장본을 바로 쓴다 (리로드가 빨라짐).
// 나머지는 네트워크 우선이되 4초 넘게 응답이 없으면 저장본을 먼저 보여 준다 (느린 망에서 무한 대기 방지).
// 저장 이름은 ?t= 같은 꼬리표를 떼고 하나로 (예전 판처럼 리로드마다 저장본이 쌓이지 않게)
const CACHE = "naeilo-v2";
const SHELL = ["./", "./index.html", "./style.css", "./app.js", "./model.js", "./charts.js", "./manifest.json", "./icon-192.png"];
self.addEventListener("install", (e) => { e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).catch(() => {})); self.skipWaiting(); });
self.addEventListener("activate", (e) => { e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim())); });
const keyOf = (u) => { const v = u.searchParams.get("v"); return u.origin + u.pathname + (v ? "?v=" + v : ""); };
async function store(u, res) {
  const c = await caches.open(CACHE), k = keyOf(u);
  if (u.searchParams.get("v")) { const old = await c.keys(); await Promise.all(old.filter((r) => { const o = new URL(r.url); return o.pathname === u.pathname && r.url !== k; }).map((r) => c.delete(r))); } // 같은 파일의 옛 판은 지운다
  await c.put(k, res);
}
self.addEventListener("fetch", (e) => {
  const r = e.request, u = new URL(r.url);
  if (r.method !== "GET" || u.origin !== location.origin) return;
  const net = () => fetch(r).then((res) => { if (res.ok && res.type === "basic") { const cp = res.clone(); const w = store(u, cp).catch(() => {}); try { e.waitUntil(w); } catch (x) { /* 응답이 이미 끝난 뒤면 그냥 저장 */ } } return res; });
  const saved = () => caches.match(keyOf(u)).then((m) => m || caches.match(r, { ignoreSearch: true }));
  if (u.searchParams.get("v")) { e.respondWith(caches.match(keyOf(u)).then((m) => m || net())); return; }
  e.respondWith(new Promise((done) => {
    let over = false;
    const t = setTimeout(() => saved().then((m) => { if (m && !over) { over = true; done(m); } }), 4000);
    net().then((res) => { clearTimeout(t); if (!over) { over = true; done(res); } })
      .catch(() => { clearTimeout(t); if (over) return; saved().then((m) => { over = true; done(m || (r.mode === "navigate" ? caches.match("./index.html") : Response.error())); }); });
  }).then((x) => x || Response.error()));
});
self.addEventListener("push", (e) => {
  e.waitUntil((async () => {
    let msg = null;
    try { msg = e.data ? e.data.json() : null; } catch (err) { msg = e.data ? { body: e.data.text() } : null; }
    if (!msg) {
      try {
        const cfg = await (await fetch("../data/config.json", { cache: "no-store" })).json();
        const sub = await self.registration.pushManager.getSubscription();
        if (cfg.push && sub) msg = await (await fetch(cfg.push.replace(/\/$/, "") + "/push/latest?e=" + encodeURIComponent(sub.endpoint), { cache: "no-store" })).json();
      } catch (err) { /* 무시 */ }
    }
    msg = msg || { title: "naeilo", body: "새 알림이 있습니다." };
    await self.registration.showNotification(msg.title || "naeilo", { body: msg.body || "", icon: "icon-192.png", badge: "icon-192.png", tag: msg.tag || "naeilo", data: { url: msg.url || "./" } });
  })());
});
self.addEventListener("notificationclick", (e) => {
  e.notification.close();
  e.waitUntil(self.clients.matchAll({ type: "window" }).then((cs) => { const c = cs.find((x) => "focus" in x); return c ? c.focus() : self.clients.openWindow(e.notification.data?.url || "./"); }));
});
