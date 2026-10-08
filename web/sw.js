// naeilo 서비스 워커: 오프라인에서도 마지막으로 본 화면·데이터를 연다 (네트워크 우선, 실패하면 저장본)
// 푸시 알림: 내용 없는 푸시를 받으면 알림 서버(워커)에서 문구를 받아 보여 준다
const CACHE = "naeilo-v1";
const SHELL = ["./", "./index.html", "./style.css", "./app.js", "./model.js", "./widget-core.js", "./charts.js", "./manifest.json", "./icon-192.png"];
self.addEventListener("install", (e) => { e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).catch(() => {})); self.skipWaiting(); });
self.addEventListener("activate", (e) => { e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim())); });
self.addEventListener("fetch", (e) => {
  const r = e.request, u = new URL(r.url);
  if (r.method !== "GET" || u.origin !== location.origin) return;
  e.respondWith(fetch(r).then((res) => { if (res.ok) { const cp = res.clone(); caches.open(CACHE).then((c) => c.put(r, cp)); } return res; })
    .catch(() => caches.match(r, { ignoreSearch: true }).then((m) => m || caches.match("./index.html"))));
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
