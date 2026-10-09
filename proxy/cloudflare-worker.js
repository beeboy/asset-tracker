// 개발자용 중계 (Cloudflare Workers 무료 요금제로 충분). 사용자는 아무것도 설정하지 않는다.
// 1) 시세: 브라우저는 Yahoo Finance 를 직접 부를 수 없어서(CORS) 이 중계를 거친다. Yahoo 차트 주소만 통과시킨다.
// 2) AI (/ai): 페이지의 AI 분석을 Google Gemini 무료 등급으로 답한다. 키는 이 중계의 비밀값에만 둔다.
// 배포: Cloudflare 대시보드 → Workers → Create → 이 코드 붙여넣기 → Deploy
//       Settings → Variables → Secret 에 GEMINI_KEY (https://aistudio.google.com/apikey 에서 무료 발급)
//       data/config.json 의 "proxy" 에 "https://<이름>.workers.dev/?url=", "ai" 에 "https://<이름>.workers.dev/ai" 를 넣고 커밋.
// 3) 조회수 (/views): Beyora 글 조회수를 모든 사람 것으로 합친다. Workers KV 무료 등급(하루 쓰기 1,000번)으로 충분.
//       Storage & Databases → KV → Create (이름 아무거나) → 이 Worker 의 Settings → Bindings → Add → KV namespace,
//       Variable name 을 VIEWS 로 정하고 방금 만든 KV 를 고른 뒤 Deploy. data/config.json 의 "views" 에 "https://<이름>.workers.dev/views".
// 4) 댓글 (/comments)·공감 (/like): 같은 VIEWS KV 를 쓴다. 익명 댓글은 비밀번호(해시만 저장)로 지우고,
//       개발자는 저장소에 쓰기 권한이 있는 GitHub 토큰(앱의 설정 > 개발자용)으로 비밀번호 없이 지운다.
//       선택: 변수 REPO (기본 beeboy/asset-tracker), 비밀값 SALT (비밀번호 해시용), ADMIN_KEY (토큰 대신 쓸 관리자 키).
// 5) 알림 (/push/*): 웹 푸시. 같은 VIEWS KV 를 쓰고, VAPID 키는 처음 부를 때 중계가 스스로 만들어 KV 에 둔다 (비밀값 설정 없음).
//       Settings → Triggers → Cron Triggers 에 "*/30 13-22 * * 1-5" (미국 장중, UTC) 를 넣으면 30분마다 검사해 보낸다.
//       data/config.json 의 "push" 에 "https://<이름>.workers.dev" 를 넣고 커밋. 아이폰은 홈 화면에 추가한 앱에서만 받는다.
//       보내는 알림: 내 종목 가중 하루 변동 -5% 이하(하루 한 번), 켜 둔 실적 일정 하루 전. 저장값은 종목 비중·일정뿐 (수량·금액 없음).
// 6) 기기 동기화 (/sync): 개발자 기기끼리만. 저장소 쓰기 권한이 있는 GitHub 토큰으로 확인하고, 같은 VIEWS KV 에 GitHub 계정별로 입력값을 둔다.
//       일반 사용자는 /esync: 동기화 비밀번호로 브라우저에서 암호화한 값(c)만 받는다. 중계는 내용을 읽을 수 없다 (KV 키 e:<id>, id 도 비밀번호에서 만든 해시).
// 7) 매매기준율 (/mar): 서울외국환중개 고시 미국 달러 매매기준율. 저장소 수집(data/mar.json)이 못 받았을 때 화면이 부른다.
const ALLOW = /^https:\/\/query[12]\.finance\.yahoo\.com\/(v8\/finance\/chart|v1\/finance\/search)/;
// 구글이 모델을 바꾸면 차례로 시도한다. 비밀값/변수 GEMINI_MODEL 을 넣으면 그 모델을 먼저 쓴다
const MODELS = ["gemini-3.8-flash", "gemini-flash-latest", "gemini-flash-lite-latest"];
export default {
  async fetch(req, env) {
    const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Methods": "GET, POST, OPTIONS", "Access-Control-Allow-Headers": "Content-Type, Authorization, If-None-Match", "Access-Control-Expose-Headers": "ETag" };
    if (req.method === "OPTIONS") return new Response(null, { headers: cors });
    const u = new URL(req.url);
    const out = (o, st = 200) => new Response(JSON.stringify(o), { status: st, headers: { ...cors, "Content-Type": "application/json; charset=utf-8" } });
    const okId = (id) => typeof id === "string" && /^[A-Za-z0-9_-]{1,40}$/.test(id);
    const noKv = () => out({ error: "중계에 VIEWS KV 연결이 없습니다 (Settings → Bindings)" }, 400);
    // 동기화 GET: 내용이 그대로면 304 (아이폰 위젯이 30분마다 묻는다. ETag = 저장된 값의 해시)
    const cached = async (raw) => {
      const tag = '"' + [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(raw || "{}")))].slice(0, 12).map((b) => b.toString(16).padStart(2, "0")).join("") + '"';
      if ((req.headers.get("If-None-Match") || "").split(/,\s*/).includes(tag)) return new Response(null, { status: 304, headers: { ...cors, ETag: tag } });
      return new Response(raw || "{}", { headers: { ...cors, "Content-Type": "application/json; charset=utf-8", ETag: tag, "Cache-Control": "no-cache" } });
    };
    if (u.pathname.startsWith("/push/")) {
      if (!env.VIEWS) return noKv();
      const id = async (ep) => [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(ep)))].slice(0, 12).map((b) => b.toString(16).padStart(2, "0")).join("");
      if (u.pathname === "/push/key") return out({ key: (await vapid(env)).pub });
      if (u.pathname === "/push/latest") { const ep = u.searchParams.get("e") || ""; const m = await env.VIEWS.get("pm:" + (await id(ep))); return out(m ? JSON.parse(m) : { title: "naeilo", body: "새 알림이 있습니다." }); }
      if (req.method !== "POST") return out({ error: "POST 만 받습니다" }, 400);
      const b = await req.json().catch(() => ({}));
      const ep = b.sub?.endpoint || b.endpoint || "";
      if (!/^https:\/\//.test(ep) || ep.length > 1000) return out({ error: "구독 정보가 맞지 않습니다" }, 400);
      const k = "p:" + (await id(ep));
      if (u.pathname === "/push/unsub") { await env.VIEWS.delete(k); return out({ ok: true }); }
      if (u.pathname === "/push/sub") {
        const w = {}; for (const [t, v] of Object.entries(b.w || {}).slice(0, 40)) if (/^[A-Z0-9.^=_-]{1,12}$/i.test(t) && v > 0 && v <= 1) w[t] = +v;
        const ev = (b.ev || []).slice(0, 60).filter((e) => /^\d{4}-\d{2}-\d{2}$/.test(e.d)).map((e) => ({ d: e.d, t: String(e.t || "").slice(0, 12), k: String(e.k || "").slice(0, 30) }));
        await env.VIEWS.put(k, JSON.stringify({ sub: b.sub, w, ev, drop: Math.min(20, Math.max(2, Number(b.drop) || 5)), at: new Date().toISOString() }));
        return out({ ok: true });
      }
      if (u.pathname === "/push/test") { const r = await sendPush(env, b.sub || { endpoint: ep }, k.slice(2), { title: "naeilo 알림 시험", body: "알림이 잘 옵니다." }); return out({ ok: r.ok, status: r.status }); }
      return out({ error: "모르는 주소" }, 404);
    }
    if (u.pathname === "/mar") return out((await mar()) || { error: "매매기준율을 받지 못했습니다" }, 200);
    if (u.pathname === "/sync") {
      // GET /sync → { state, at },  POST /sync {"state","at"} → { ok, at } (더 새 값이 이미 있으면 { stale: true })
      if (!env.VIEWS) return noKv();
      const who = await ghWho(env, (req.headers.get("Authorization") || "").replace(/^Bearer\s+/i, "").trim());
      if (!who) return out({ error: "저장소 쓰기 권한이 있는 GitHub 토큰이 아닙니다" }, 403);
      const key = "s:" + who, raw = await env.VIEWS.get(key), cur = JSON.parse(raw || "null");
      if (req.method === "GET") return cached(raw);
      if (req.method !== "POST") return out({ error: "GET 또는 POST" }, 400);
      const b = await req.json().catch(() => ({}));
      const at = Number(b.at) || Date.now(), body = JSON.stringify({ state: b.state, at });
      if (!b.state || typeof b.state !== "object" || body.length > 400000) return out({ error: "입력값이 없거나 너무 큽니다" }, 400);
      if (cur && cur.at > at) return out({ stale: true, at: cur.at });
      await env.VIEWS.put(key, body);
      return out({ ok: true, at });
    }
    if (u.pathname === "/esync") {
      // GET /esync?id= → { c, at },  POST /esync?id= {"c","at"} → { ok, at } (더 새 값이 이미 있으면 { stale: true })
      if (!env.VIEWS) return noKv();
      const id = u.searchParams.get("id") || "";
      if (!/^[0-9a-f]{40}$/.test(id)) return out({ error: "동기화 id 가 맞지 않습니다" }, 400);
      const raw = await env.VIEWS.get("e:" + id), cur = JSON.parse(raw || "null");
      if (req.method === "GET") return cached(raw);
      if (req.method !== "POST") return out({ error: "GET 또는 POST" }, 400);
      const b = await req.json().catch(() => ({}));
      const at = Number(b.at) || Date.now();
      if (typeof b.c !== "string" || !/^[A-Za-z0-9+/=]+\.[A-Za-z0-9+/=]+$/.test(b.c) || b.c.length > 500000) return out({ error: "암호문이 맞지 않습니다" }, 400);
      if (cur && cur.at > at) return out({ stale: true, at: cur.at });
      await env.VIEWS.put("e:" + id, JSON.stringify({ c: b.c, at }), { expirationTtl: 86400 * 400 }); // 400일 안 쓰면 지움
      return out({ ok: true, at });
    }
    if (u.pathname === "/like") {
      // POST /like {"id":"a","d":1|-1} → 공감 수 { id, n }
      if (!env.VIEWS) return noKv();
      if (req.method !== "POST") return out({ error: "POST 만 받습니다" }, 400);
      const { id, d } = await req.json().catch(() => ({}));
      if (!okId(id)) return out({ error: "글 id 가 맞지 않습니다" }, 400);
      const n = Math.max(0, (Number(await env.VIEWS.get("l:" + id)) || 0) + (d === -1 ? -1 : 1));
      await env.VIEWS.put("l:" + id, String(n));
      return out({ id, n });
    }
    if (u.pathname === "/comments" || u.pathname === "/comments/del") {
      // GET /comments?id=a → { comments: [{ cid, at, text }] }
      // POST /comments {"id","text","pw"} → { comment },  POST /comments/del {"id","cid","pw"} (또는 Authorization: Bearer <GitHub 토큰>) → { ok }
      if (!env.VIEWS) return noKv();
      const load = async (id) => { try { return JSON.parse((await env.VIEWS.get("c:" + id)) || "[]"); } catch (e) { return []; } };
      const hash = async (cid, pw) => [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`${env.SALT || "naeilo"}|${cid}|${pw}`)))].map((b) => b.toString(16).padStart(2, "0")).join("");
      const pub = (c) => ({ cid: c.cid, at: c.at, text: c.text });
      if (req.method === "GET") {
        const id = u.searchParams.get("id");
        if (!okId(id)) return out({ error: "글 id 가 맞지 않습니다" }, 400);
        return out({ comments: (await load(id)).map(pub) });
      }
      if (req.method !== "POST") return out({ error: "GET 또는 POST" }, 400);
      const b = await req.json().catch(() => ({}));
      if (!okId(b.id)) return out({ error: "글 id 가 맞지 않습니다" }, 400);
      const list = await load(b.id);
      if (u.pathname === "/comments") {
        const text = String(b.text || "").trim(), pw = String(b.pw || "");
        if (!text || text.length > 1000) return out({ error: "댓글은 1~1,000자" }, 400);
        if (pw.length < 4 || pw.length > 64) return out({ error: "비밀번호는 4자 이상" }, 400);
        if (list.length >= 500) return out({ error: "댓글이 너무 많습니다" }, 400);
        const cid = Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
        const c = { cid, at: new Date().toISOString(), text, h: await hash(cid, pw) };
        list.push(c); await env.VIEWS.put("c:" + b.id, JSON.stringify(list));
        return out({ comment: pub(c) });
      }
      const i = list.findIndex((c) => c.cid === b.cid);
      if (i < 0) return out({ error: "댓글이 없습니다" }, 404);
      let ok = !!b.pw && (await hash(b.cid, String(b.pw))) === list[i].h;
      const tok = (req.headers.get("Authorization") || "").replace(/^Bearer\s+/i, "").trim();
      if (!ok && tok && env.ADMIN_KEY && tok === env.ADMIN_KEY) ok = true;
      if (!ok && tok) ok = !!(await ghWho(env, tok)); // 개발자: 저장소에 쓰기 권한이 있는 GitHub 토큰이면 비밀번호 없이
      if (!ok) return out({ error: "비밀번호가 맞지 않습니다" }, 403);
      list.splice(i, 1); await env.VIEWS.put("c:" + b.id, JSON.stringify(list));
      return out({ ok: true });
    }
    if (u.pathname === "/views") {
      // GET /views?ids=a,b → { views: { a: 3 }, likes: { a: 1 }, comments: { a: 2 } },  POST /views {"id":"a"} → 한 번 더하고 { id, n }
      if (!env.VIEWS) return noKv();
      if (req.method === "GET") {
        const ids = [...new Set((u.searchParams.get("ids") || "").split(","))].filter(okId).slice(0, 100);
        const [vals, lk, cm] = await Promise.all(["v:", "l:", "c:"].map((p) => Promise.all(ids.map((id) => env.VIEWS.get(p + id)))));
        const cnt = (x) => { try { return JSON.parse(x || "[]").length; } catch (e) { return 0; } };
        return out({ views: Object.fromEntries(ids.map((id, i) => [id, Number(vals[i]) || 0])), likes: Object.fromEntries(ids.map((id, i) => [id, Number(lk[i]) || 0])), comments: Object.fromEntries(ids.map((id, i) => [id, cnt(cm[i])])) });
      }
      if (req.method === "POST") {
        const { id } = await req.json().catch(() => ({}));
        if (!okId(id)) return out({ error: "글 id 가 맞지 않습니다" }, 400);
        const n = (Number(await env.VIEWS.get("v:" + id)) || 0) + 1;
        await env.VIEWS.put("v:" + id, String(n));
        return out({ id, n });
      }
      return out({ error: "GET 또는 POST" }, 400);
    }
    if (u.pathname === "/ai") {
      if (!env.GEMINI_KEY) return out({ error: "중계에 GEMINI_KEY 비밀값이 없습니다 (Settings → Variables and Secrets)" }, 400);
      // 브라우저로 /ai?test=1 을 열면 Gemini 연결을 바로 점검한다
      const test = req.method === "GET" && u.searchParams.has("test");
      if (req.method !== "POST" && !test) return out({ error: "POST 만 받습니다. 점검은 /ai?test=1" }, 400);
      const { system = "", prompt = "" } = test ? { prompt: "한국어로 '연결 성공' 한 마디만" } : await req.json().catch(() => ({}));
      if (!prompt || prompt.length > 12000) return out({ error: "질문이 비었거나 너무 깁니다" }, 400);
      // 생각(thinking)을 줄여 빠르게: 3세대는 thinkingLevel, 그 전 모델은 thinkingBudget. 거절하면 설정 없이 다시
      const make = (model, think) => JSON.stringify({ ...(system ? { systemInstruction: { parts: [{ text: system }] } } : {}), contents: [{ role: "user", parts: [{ text: prompt }] }],
        generationConfig: { maxOutputTokens: 4096, temperature: 0.4, ...(think ? { thinkingConfig: /gemini-3/.test(model) ? { thinkingLevel: "low" } : { thinkingBudget: 0 } } : {}) } });
      const call = (model, think) => fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, { method: "POST", headers: { "Content-Type": "application/json", "x-goog-api-key": env.GEMINI_KEY.trim() }, body: make(model, think) });
      const errs = []; let st = 502;
      for (const model of [...new Set([env.GEMINI_MODEL, ...MODELS].filter(Boolean))]) {
        let r = await call(model, true), j = await r.json().catch(() => ({}));
        if (r.status === 400) { r = await call(model, false); j = await r.json().catch(() => ({})); } // 생각 설정을 거절하면 설정 없이
        if (r.status === 503 || r.status === 429) { await new Promise((ok) => setTimeout(ok, 1500)); r = await call(model, false); j = await r.json().catch(() => ({})); } // 붐비면 잠깐 뒤 한 번 더
        const text = (j.candidates?.[0]?.content?.parts || []).filter((p) => !p.thought).map((p) => p.text || "").join("");
        if (r.ok && text) return out({ text, model });
        errs.push(`${model} ${r.status}: ${(j.error?.message || j.candidates?.[0]?.finishReason || "빈 응답").slice(0, 160)}`);
        if (errs.length === 1) st = r.ok ? 502 : r.status;
        if (r.status === 401 || r.status === 403) break; // 키 문제는 다른 모델도 같다
      }
      return out({ error: "Gemini " + errs.join(" / "), colo: req.cf?.colo }, st);
    }
    const target = u.searchParams.get("url") || "";
    if (!ALLOW.test(target)) return new Response("not allowed", { status: 400, headers: cors });
    // Yahoo 가 429(요청 과다)·5xx 를 주거나 연결이 끊기면 잠깐 쉬고 다른 서버(query1 ↔ query2)로 한 번 더
    const get = (url) => fetch(url, { headers: { "User-Agent": "Mozilla/5.0" }, cf: { cacheTtl: 60 } }).catch(() => null);
    const bad = (x) => !x || x.status === 429 || x.status >= 500;
    let r = await get(target);
    if (bad(r)) {
      await new Promise((ok) => setTimeout(ok, 700));
      const r2 = await get(target.replace(/^https:\/\/query([12])/, (m, n) => "https://query" + (n === "1" ? "2" : "1")));
      if (r2 && (!bad(r2) || !r)) r = r2;
    }
    if (!r) return out({ error: "Yahoo 에 연결하지 못했습니다" }, 502);
    return new Response(r.body, { status: r.status, headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "max-age=60" } });
  },
  // 30분마다 (Cron Trigger): 구독마다 하루 변동·실적 하루 전 검사
  async scheduled(event, env, ctx) {
    if (!env.VIEWS) return;
    const q = await fetch(env.SITE_QUOTES || "https://naeilo.com/data/quotes.json", { cf: { cacheTtl: 60 } }).then((r) => r.json()).catch(() => null);
    const kst = new Date(Date.now() + 9 * 3600e3), today = kst.toISOString().slice(0, 10), tmr = new Date(kst.getTime() + 86400e3).toISOString().slice(0, 10);
    let cursor;
    do {
      const page = await env.VIEWS.list({ prefix: "p:", cursor }); cursor = page.list_complete ? null : page.cursor;
      for (const key of page.keys) {
        const rec = JSON.parse((await env.VIEWS.get(key.name)) || "null"); if (!rec || !rec.sub) continue;
        const sid = key.name.slice(2), msgs = [];
        if (q) {
          let ch = 0, ws = 0;
          for (const [t, w] of Object.entries(rec.w || {})) { const x = q[t]; const px = x && (x.regular ?? x.last); if (px && x.prev_close) { ch += w * (px / x.prev_close - 1); ws += w; } }
          if (ws > 0.5 && ch <= -(rec.drop || 5) / 100 && rec.dropSent !== today) { msgs.push({ title: "naeilo · 큰 하락", body: `내 종목이 오늘 ${(ch * 100).toFixed(1)}% 움직였습니다. 전망과 비중을 확인해 보세요.`, tag: "drop" }); rec.dropSent = today; }
        }
        const soon = (rec.ev || []).filter((e) => e.d === tmr && !(rec.evSent || []).includes(e.d + e.t + e.k));
        if (soon.length) { msgs.push({ title: "naeilo · 내일 일정", body: soon.map((e) => `${e.t} ${e.k}`).join(", ") + " (날짜는 추정일 수 있음)", tag: "ev" }); rec.evSent = [...(rec.evSent || []).slice(-40), ...soon.map((e) => e.d + e.t + e.k)]; }
        for (const m of msgs) {
          const r = await sendPush(env, rec.sub, sid, m);
          if (r.status === 404 || r.status === 410) { await env.VIEWS.delete(key.name); break; } // 구독이 사라짐
        }
        if (msgs.length) await env.VIEWS.put(key.name, JSON.stringify(rec));
      }
    } while (cursor);
  },
};

// ------------------------------------------------------------ GitHub 토큰 확인: 저장소 쓰기 권한이 있으면 계정 이름 (10분 기억)
async function ghWho(env, tok) {
  if (!tok || tok.length > 300) return null;
  const ck = new Request("https://gh-who.cache/" + [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(tok)))].map((b) => b.toString(16).padStart(2, "0")).join(""));
  const hit = await caches.default.match(ck); if (hit) return (await hit.text()) || null;
  const h = { Authorization: "Bearer " + tok, "User-Agent": "naeilo-worker", Accept: "application/vnd.github+json" };
  const [r, me] = await Promise.all([fetch(`https://api.github.com/repos/${env.REPO || "beeboy/asset-tracker"}`, { headers: h }), fetch("https://api.github.com/user", { headers: h })]);
  const j = await r.json().catch(() => ({})), m = await me.json().catch(() => ({}));
  const who = r.ok && (j.permissions?.push || j.permissions?.admin) ? m.login || "dev" : "";
  await caches.default.put(ck, new Response(who, { headers: { "Cache-Control": "max-age=600" } }));
  return who || null;
}

// ------------------------------------------------------------ 매매기준율 (서울외국환중개, 1시간 캐시)
async function mar() {
  const kst = new Date(Date.now() + 9 * 3600e3), end = kst.toISOString().slice(0, 10), st = new Date(kst.getTime() - 10 * 86400e3).toISOString().slice(0, 10);
  const r = await fetch(`http://www.smbs.biz/ExRate/StdExRate_xml.jsp?arr_value=USD_${st}_${end}`, { headers: { "User-Agent": "Mozilla/5.0", Referer: "http://www.smbs.biz/ExRate/StdExRate.jsp" }, cf: { cacheTtl: 3600, cacheEverything: true } }).catch(() => null);
  const t = r && r.ok ? await r.text() : "";
  const sets = [...t.matchAll(/label=['"]([^'"]+)['"][^>]*?value=['"]([\d,.]+)['"]/g)];
  if (!sets.length) return null;
  const [, lab, val] = sets[sets.length - 1], n = lab.match(/\d+/g) || [], rate = Number(val.replace(/,/g, ""));
  if (!(rate > 500 && rate < 5000)) return null;
  const date = n.length >= 3 ? `${+n[0] < 100 ? 2000 + +n[0] : n[0]}-${n[1].padStart(2, "0")}-${n[2].padStart(2, "0")}` : end;
  return { USD: { rate, date, src: "서울외국환중개" } };
}

// ------------------------------------------------------------ 웹 푸시 (내용 없는 푸시 + 문구는 /push/latest 에서)
const b64u = (u8) => btoa(String.fromCharCode(...new Uint8Array(u8))).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
async function vapid(env) {
  let v = JSON.parse((await env.VIEWS.get("vapid")) || "null");
  if (!v) {
    const kp = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
    v = { jwk: await crypto.subtle.exportKey("jwk", kp.privateKey), pub: b64u(await crypto.subtle.exportKey("raw", kp.publicKey)) };
    await env.VIEWS.put("vapid", JSON.stringify(v));
  }
  return v;
}
async function sendPush(env, sub, sid, msg) {
  const v = await vapid(env), ep = new URL(sub.endpoint);
  await env.VIEWS.put("pm:" + sid, JSON.stringify(msg), { expirationTtl: 86400 * 3 });
  const enc = (o) => b64u(new TextEncoder().encode(JSON.stringify(o)));
  const head = enc({ typ: "JWT", alg: "ES256" }), body = enc({ aud: ep.origin, exp: Math.floor(Date.now() / 1000) + 12 * 3600, sub: env.VAPID_SUB || "mailto:admin@naeilo.com" });
  const key = await crypto.subtle.importKey("jwk", v.jwk, { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, key, new TextEncoder().encode(head + "." + body));
  return fetch(sub.endpoint, { method: "POST", headers: { TTL: "43200", Urgency: "normal", "Content-Length": "0", Authorization: `vapid t=${head}.${body}.${b64u(sig)}, k=${v.pub}` } });
}
