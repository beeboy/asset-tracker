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
// 8) 구독 원고 (/store/*): 아이폰 앱 구독(인앱 결제)을 확인하고 유료 원고를 건넨다. 같은 VIEWS KV 를 쓰고, 비밀값 설정은 없다.
//       구매 확인은 앱이 보낸 StoreKit 2 서명 거래(JWS)를 Apple 루트 인증서(G3)까지 따라가 직접 검증한다 (Apple API 키 불필요).
//       유료 원고(외전 6장부터·앱 연재판 1권)는 저장소에 두지 않고 KV(st:book:*)에만 둔다. 올리기는 web/store-admin.html.
//       선택 변수: BUNDLE_ID (기본 com.naeilo.widget), NO_SANDBOX=1 (TestFlight 샌드박스 구매를 받지 않음).
// 9) 판 깨기 순위판 (/scores): 아이폰 앱 게임의 익명 주간 순위. 같은 VIEWS KV, 비밀값 없음.
//       저장값은 점수·별명(선택, 3글자)·선수 인물 id·날짜뿐. 계정·기기 ID·IP 는 받지도 두지도 않는다. 단계·난이도·주마다 상위 20개, 5주 뒤 지워짐.
// 12) 출시 알림 신청 (/notify): 입구 페이지(naeilo.com)의 "앱 받기". 같은 VIEWS KV, 비밀값 없음.
//       저장값은 이메일과 신청 날짜뿐 (KV 키 n:<이메일 해시>). 출시 알림 한 번 보내고 지운다. 신청 취소는 /notify/del.
//       목록: GET /notify/list (Authorization: Bearer <ADMIN_KEY 또는 저장소 쓰기 권한 GitHub 토큰>).
// 11) Claude 사용량 (/cu): 본인용 사용량 위젯 앱(usage-app/)이 15분마다 묻는다. 5시간·주간 한도 사용률(%)과 리셋 시각만 돌려준다.
//       비밀값 CLAUDE_TOKEN = 맥 터미널에서 `claude setup-token` 으로 받은 토큰 (sk-ant-oat01-…, 1년). 폰에는 토큰을 두지 않는다.
//       먼저 OAuth 사용량 주소를 묻고, 안 되면 가장 작은 메시지 한 번(1토큰)의 응답 머리글(unified 한도)에서 읽는다. 결과는 5분 기억.
// 13) 동네 신문 (/heads): 구글 뉴스 한국판 전체 상위 기사 제목만 하루 한 번(한국 7시 뒤 첫 cron) 받아 5일 둔다.
//       모두가 같은 값을 받는다 (KV 키 heads, 하루 쓰기 1번). 앱 동네 주민이 그 제목을 말한다. 비밀값 없음.
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
    if (u.pathname.startsWith("/store/")) return env.VIEWS ? store(req, env, u, out, cors).catch((e) => out({ error: "후원 처리 중 오류: " + (e?.message || e) }, 500)) : noKv();
    // 동기화 GET: 내용이 그대로면 304 (아이폰 위젯이 30분마다 묻는다. ETag = 저장된 값의 해시)
    const cached = async (raw) => {
      const tag = '"' + [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(raw || "{}")))].slice(0, 12).map((b) => b.toString(16).padStart(2, "0")).join("") + '"';
      if ((req.headers.get("If-None-Match") || "").split(/,\s*/).includes(tag)) return new Response(null, { status: 304, headers: { ...cors, ETag: tag } });
      return new Response(raw || "{}", { headers: { ...cors, "Content-Type": "application/json; charset=utf-8", ETag: tag, "Cache-Control": "no-cache" } });
    };
    if (u.pathname.startsWith("/push/")) {
      if (!env.VIEWS) return noKv();
      const id = async (ep) => [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(ep)))].slice(0, 12).map((b) => b.toString(16).padStart(2, "0")).join("");
      if (u.pathname === "/push/key") { const r = out({ key: (await vapid(env)).pub }); r.headers.set("Cache-Control", "public, max-age=86400"); return r; }
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
    if (u.pathname === "/heads") { if (!env.VIEWS) return noKv(); const v = JSON.parse((await env.VIEWS.get("heads")) || '{"at":"","h":[]}'), keep = new Date(Date.now() + 9 * 3600e3 - 4 * 86400e3).toISOString().slice(0, 10); for (const k of ["h", "e"]) v[k] = (Array.isArray(v[k]) ? v[k] : []).filter((x) => x.d >= keep); const r = out(v); r.headers.set("Cache-Control", "public, max-age=1800"); return r; } // 받기만 (모으기는 cron)
    if (u.pathname === "/mar") { const m = await mar(), r = out(m || { error: "매매기준율을 받지 못했습니다" }, 200); if (m) r.headers.set("Cache-Control", "public, max-age=600"); return r; } // 받은 값은 브라우저도 10분 기억
    if (u.pathname === "/collect") return out({ secret: !!env.GH_DISPATCH, last: env.VIEWS ? JSON.parse((await env.VIEWS.get("collect:last")) || "null") : "VIEWS 없음", crons: env.VIEWS ? JSON.parse((await env.VIEWS.get("cron:log")) || "[]") : null }); // 시세 수집 깨우기 확인 (10번)
    if (u.pathname === "/cu") return claudeUsage(env, out).catch((e) => out({ error: "사용량 오류: " + (e?.message || e) }, 502));
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
    if (u.pathname === "/notify" || u.pathname === "/notify/del" || u.pathname === "/notify/list") {
      // POST /notify {"email":"a@b.c"} → { ok } (같은 이메일은 한 줄), POST /notify/del {"email"} → { ok }
      if (!env.VIEWS) return noKv();
      if (u.pathname === "/notify/list") {
        const tok = (req.headers.get("Authorization") || "").replace(/^Bearer\s+/i, "");
        if (!tok || !((env.ADMIN_KEY && tok === env.ADMIN_KEY) || (await ghWho(env, tok)))) return out({ error: "관리자만 볼 수 있습니다" }, 403);
        const rows = []; let cur;
        do { const r = await env.VIEWS.list({ prefix: "n:", cursor: cur }); for (const k of r.keys) { const v = JSON.parse((await env.VIEWS.get(k.name)) || "null"); if (v) rows.push(v); } cur = r.list_complete ? null : r.cursor; } while (cur);
        return out({ n: rows.length, rows });
      }
      if (req.method !== "POST") return out({ error: "POST 만 받습니다" }, 400);
      const { email } = await req.json().catch(() => ({}));
      const e = String(email || "").trim().toLowerCase();
      if (e.length > 120 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e)) return out({ error: "이메일 주소가 맞지 않습니다" }, 400);
      const k = "n:" + [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(e)))].slice(0, 16).map((b) => b.toString(16).padStart(2, "0")).join("");
      if (u.pathname === "/notify/del") { await env.VIEWS.delete(k); return out({ ok: true }); }
      if (!(await env.VIEWS.get(k))) await env.VIEWS.put(k, JSON.stringify({ email: e, at: new Date().toISOString().slice(0, 10) }));
      return out({ ok: true });
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
    if (u.pathname === "/scores") {
      // GET /scores?lv=normal&n=3 → { week, top: [{ nick, p, score }] },  POST /scores {"lv","n","score","nick","p"} → { ok, rank }
      if (!env.VIEWS) return noKv();
      const d = new Date(), wd = (d.getUTCDay() + 6) % 7;
      const week = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate() - wd)).toISOString().slice(0, 10);
      const b = req.method === "POST" ? await req.json().catch(() => ({})) : {};
      const lv = req.method === "POST" ? b.lv : u.searchParams.get("lv"), n = Number(req.method === "POST" ? b.n : u.searchParams.get("n"));
      if (!["easy", "normal", "hard"].includes(lv) || !Number.isInteger(n) || n < 0 || n > 11) return out({ error: "단계·난이도가 맞지 않습니다" }, 400);
      const key = `gs:${week}:${lv}:${n}`, top = JSON.parse((await env.VIEWS.get(key)) || "[]");
      if (req.method === "GET") { const r = out({ week, top }); r.headers.set("Cache-Control", "public, max-age=60"); return r; }
      if (req.method !== "POST") return out({ error: "GET 또는 POST" }, 400);
      const score = Number(b.score);
      // 한 판 최고는 벽돌 1000칸 × 3점 × 1.5 × 2 + 목숨 보너스 정도라 그보다 크면 받지 않는다
      if (!Number.isInteger(score) || score < 1 || score > 10000) return out({ error: "점수가 맞지 않습니다" }, 400);
      const nick = /^[가-힣A-Za-z0-9]{1,3}$/.test(b.nick || "") ? b.nick : "";
      const p = ["seri", "sio", "seonbae", "ir", "sua", "me"].includes(b.p) ? b.p : "seri";
      if (top.length >= 20 && score <= top[top.length - 1].score) return out({ ok: true, rank: null });
      top.push({ nick, p, score, at: d.toISOString().slice(0, 10) });
      top.sort((a, c) => c.score - a.score); top.length = Math.min(top.length, 20);
      await env.VIEWS.put(key, JSON.stringify(top), { expirationTtl: 35 * 86400 });
      return out({ ok: true, rank: top.findIndex((e) => e.score === score && e.nick === nick && e.p === p) + 1 });
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
      const call = (model, think) => fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`, { method: "POST", headers: { "Content-Type": "application/json", "x-goog-api-key": env.GEMINI_KEY.trim() }, body: make(model, think), signal: AbortSignal.timeout(60000) }).catch(() => new Response("{}", { status: 504 })); // 응답 없으면 60초 뒤 다음 모델로
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
    const get = (url) => fetch(url, { headers: { "User-Agent": "Mozilla/5.0" }, cf: { cacheTtl: 60, cacheEverything: true }, signal: AbortSignal.timeout(8000) }).catch(() => null); // 8초 넘게 응답 없으면 다른 서버로
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
  // Cron Trigger (15분): 시세 수집을 깨우고 (10번), 미국 장중 30분마다 구독마다 하루 변동·실적 하루 전을 검사한다
  async scheduled(event, env, ctx) {
    const now = new Date(), log = { cron: event.cron, at: now.toISOString() };
    ctx.waitUntil(wakeCollect(env, log)); // 수집 깨우기 (시간 판단은 wakeCollect 안에서)
    if (env.VIEWS) ctx.waitUntil(heads(env).catch(() => null)); // 동네 신문 (하루 한 번은 heads 안에서)
    // cron 은 15분 하나뿐이라 (여러 개 두면 알림 cron 만 불렸다) 알림 검사는 평일 UTC 13~22시의 :00, :30 에만
    const wd = now.getUTCDay(), h = now.getUTCHours();
    if (!(wd >= 1 && wd <= 5 && h >= 13 && h <= 22 && now.getUTCMinutes() % 30 < 10)) return;
    if (!env.VIEWS) return;
    const q = await fetch(env.SITE_QUOTES || "https://naeilo.com/data/quotes.json", { cf: { cacheTtl: 60, cacheEverything: true }, signal: AbortSignal.timeout(10000) }).then((r) => r.json()).catch(() => null);
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

// 13) 동네 신문: 구글 뉴스 한국판(h)·미국판(e, 영어 앱) 전체·분야별 상위 기사 제목. 하루 50개씩, 5일 둔다 (최대 250개씩)
async function heads(env) {
  const kst = new Date(Date.now() + 9 * 3600e3), today = kst.toISOString().slice(0, 10);
  const old = JSON.parse((await env.VIEWS.get("heads")) || "null") || { at: "", h: [] };
  if (old.at === today || kst.getUTCHours() < 7) return old;
  const keep = new Date(kst.getTime() - 4 * 86400e3).toISOString().slice(0, 10); // 오늘 포함 5일
  const dec = (t) => t.replace(/<!\[CDATA\[|\]\]>/g, "").replace(/&#(\d+);/g, (_, n) => String.fromCharCode(n)).replace(/&quot;/g, '"').replace(/&#39;|&apos;/g, "'").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&amp;/g, "&").trim();
  // 전체 상위 한 줄은 30개 남짓이라, 분야별 상위를 차례로 섞어 하루 50개를 채운다 (받기 9번, 하루 한 번)
  const feeds = ["", ...["NATION", "WORLD", "BUSINESS", "TECHNOLOGY", "SCIENCE", "HEALTH", "SPORTS", "ENTERTAINMENT"].map((t) => "/headlines/section/topic/" + t)];
  // 한국판(h)과 영어 앱용 미국판(e)을 같은 방식으로 (받기 18번, 하루 한 번)
  const pull = async (loc, max) => {
    const lists = await Promise.all(feeds.map(async (f) => {
      const xml = await fetch(`https://news.google.com/rss${f}?${loc}`, { headers: { "User-Agent": "Mozilla/5.0 naeilo-worker" }, signal: AbortSignal.timeout(10000) }).then((r) => (r.ok ? r.text() : "")).catch(() => "");
      return [...xml.matchAll(/<item>[\s\S]*?<title>([\s\S]*?)<\/title>/g)].map((m) => dec(m[1]).replace(/\s+-\s+[^-]+$/, "").trim()).filter((t) => t.length >= 8 && t.length <= max); // 제목만, " - 언론사" 꼬리는 뺀다
    }));
    const seen = new Set(), got = [];
    for (let i = 0; got.length < 50 && lists.some((l) => i < l.length); i++) for (const l of lists) if (i < l.length && got.length < 50 && !seen.has(l[i])) { seen.add(l[i]); got.push(l[i]); }
    return got;
  };
  const [ko, en] = await Promise.all([pull("hl=ko&gl=KR&ceid=KR:ko", 70), pull("hl=en-US&gl=US&ceid=US:en", 110)]);
  if (!ko.length && !en.length) return old; // 못 받으면 쓰지 않고 다음 cron 에 다시
  const merge = (got, past) => { const seen = new Set(got); return [...got.map((t) => ({ d: today, t })), ...(Array.isArray(past) ? past : []).filter((x) => x.d >= keep && !seen.has(x.t))].slice(0, 250); };
  const v = { at: today, h: merge(ko, old.h), e: merge(en, old.e) };
  await env.VIEWS.put("heads", JSON.stringify(v));
  return v;
}

// 10) 시세 수집 깨우기: GitHub 예약 실행은 밀리거나 빠질 때가 많아서, 이 중계의 Cron 이 collect 작업을 직접 실행시킨다.
//       cron 은 proxy/wrangler.toml 의 15분 하나. 비밀값 GH_DISPATCH =
//       GitHub fine-grained 토큰 (이 저장소만, Actions: Read and write). 수집 시간은 collect.yml 예약과 같다 (평일 UTC 8~23시 + 다음날 0시대).
//       배포 버튼(Actions worker)을 쓰면 proxy/wrangler.toml 의 cron 이 그대로 들어간다
//       잘 되는지는 /collect 로 확인: cron 기록(최근 12번)이 없으면 cron 이 안 돈다, status 가 204 가 아니면 토큰 문제.
//       collect.yml 도 실행할 때마다 /collect 를 로그에 찍어서 GitHub 기록만 보고 확인할 수 있다
async function wakeCollect(env, log) {
  const d = new Date(), wd = d.getUTCDay(), h = d.getUTCHours();
  const note = async (v) => {
    if (!env.VIEWS) return;
    if (v.status || v.error) await env.VIEWS.put("collect:last", JSON.stringify({ at: d.toISOString(), ...v }));
    const past = JSON.parse((await env.VIEWS.get("cron:log")) || "[]");
    await env.VIEWS.put("cron:log", JSON.stringify([{ ...log, ...v }, ...past].slice(0, 12)));
  };
  if (!env.GH_DISPATCH) return note({ error: "비밀값 GH_DISPATCH 없음" });
  if (!((wd >= 1 && wd <= 5 && h >= 8) || (wd >= 2 && wd <= 6 && h === 0 && d.getUTCMinutes() < 30))) return note({ skip: "수집 시간 아님" });
  const prev = env.VIEWS && JSON.parse((await env.VIEWS.get("collect:last")) || "null");
  if (prev && prev.status && d - new Date(prev.at) < 10 * 60e3) return note({ skip: "10분 안 중복" });
  const r = await fetch(`https://api.github.com/repos/${env.REPO || "beeboy/asset-tracker"}/actions/workflows/collect.yml/dispatches`, {
    method: "POST", body: JSON.stringify({ ref: "main" }), signal: AbortSignal.timeout(10000),
    headers: { Authorization: "Bearer " + env.GH_DISPATCH.trim(), Accept: "application/vnd.github+json", "User-Agent": "naeilo-worker", "Content-Type": "application/json" },
  }).catch(() => null);
  await note(r ? { status: r.status, body: r.ok ? "" : (await r.text()).slice(0, 200) } : { error: "GitHub 연결 실패" });
}

// ------------------------------------------------------------ GitHub 토큰 확인: 저장소 쓰기 권한이 있으면 계정 이름 (10분 기억)
async function ghWho(env, tok) {
  if (!tok || tok.length > 300) return null;
  const ck = new Request("https://gh-who.cache/" + [...new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(tok)))].map((b) => b.toString(16).padStart(2, "0")).join(""));
  const hit = await caches.default.match(ck); if (hit) return (await hit.text()) || null;
  const h = { Authorization: "Bearer " + tok, "User-Agent": "naeilo-worker", Accept: "application/vnd.github+json" };
  const [r, me] = await Promise.all([fetch(`https://api.github.com/repos/${env.REPO || "beeboy/asset-tracker"}`, { headers: h, signal: AbortSignal.timeout(8000) }), fetch("https://api.github.com/user", { headers: h, signal: AbortSignal.timeout(8000) })]).catch(() => [null, null]);
  if (!r || !me) return null; // GitHub 응답이 없으면 이번엔 거절 (기억하지 않음)
  const j = await r.json().catch(() => ({})), m = await me.json().catch(() => ({}));
  const who = r.ok && (j.permissions?.push || j.permissions?.admin) ? m.login || "dev" : "";
  await caches.default.put(ck, new Response(who, { headers: { "Cache-Control": "max-age=600" } }));
  return who || null;
}

// ------------------------------------------------------------ 매매기준율 (서울외국환중개, 1시간 캐시)
async function mar() {
  const kst = new Date(Date.now() + 9 * 3600e3), end = kst.toISOString().slice(0, 10), st = new Date(kst.getTime() - 10 * 86400e3).toISOString().slice(0, 10);
  const r = await fetch(`http://www.smbs.biz/ExRate/StdExRate_xml.jsp?arr_value=USD_${st}_${end}`, { headers: { "User-Agent": "Mozilla/5.0", Referer: "http://www.smbs.biz/ExRate/StdExRate.jsp" }, cf: { cacheTtl: 3600, cacheEverything: true }, signal: AbortSignal.timeout(8000) }).catch(() => null);
  const t = r && r.ok ? await r.text() : "";
  const sets = [...t.matchAll(/label=['"]([^'"]+)['"][^>]*?value=['"]([\d,.]+)['"]/g)];
  if (!sets.length) return null;
  const pick = ([, lab, val]) => { const n = lab.match(/\d+/g) || [], rate = Number(val.replace(/,/g, "")); return { rate, date: n.length >= 3 ? `${+n[0] < 100 ? 2000 + +n[0] : n[0]}-${n[1].padStart(2, "0")}-${n[2].padStart(2, "0")}` : end }; };
  const { rate, date } = pick(sets[sets.length - 1]);
  if (!(rate > 500 && rate < 5000)) return null;
  // 앞 날짜 고시: 새 고시는 전날 오후에 나오지만 토스는 그날 오전 9시부터 쓴다 (앱이 9시 전엔 이 값)
  const p = sets.length > 1 ? pick(sets[sets.length - 2]) : null;
  return { USD: { rate, date, src: "서울외국환중개" }, ...(p && p.rate > 500 && p.rate < 5000 && p.date < date ? { USD_prev: p } : {}) };
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
  return fetch(sub.endpoint, { method: "POST", headers: { TTL: "43200", Urgency: "normal", "Content-Length": "0", Authorization: `vapid t=${head}.${body}.${b64u(sig)}, k=${v.pub}` }, signal: AbortSignal.timeout(10000) }).catch(() => ({ ok: false, status: 0 })); // 알림 서버가 멈춰도 다음 구독으로
}

// ------------------------------------------------------------ 구독 원고 (/store/*)
// 구독 (2026-10-16 결정): 월 구독(sub.monthly)·연 구독(sub.yearly), 자동 갱신. 커피 후원 등급은 없앴다.
//   외전 6장부터 = 구독 중. 앱 연재판 1권 = 연 구독 중, 또는 월 구독을 9번 이상 결제 (끝난 결제도 센다)
// 앱이 부르는 것 (jws 는 지금 구독의 jwsRepresentation + 1권 자격용 지난 월 구독 결제 최근 9개)
//   POST /store/book {jws:[..], book:"side"|"vol1", lang:"ko"|"en"} → { tier, book:{lang, from, chapters} }  (구독이 모자라면 403 { error, tier })
//       tier: 0 없음, 1 구독 중, 2 1권 자격
//   GET  /store/sponsors → { sponsors:[{ name, logo:url|null }] }   GET /store/logo/<id> → 그림  (예전 후원자, 새 신청은 받지 않음)
// 개발자 (Authorization: Bearer <저장소 쓰기 권한 GitHub 토큰 또는 ADMIN_KEY>)
//   POST /store/admin/book {book, lang, data:{lang, from, chapters}}   GET /store/admin/books
//   GET  /store/admin/sponsors   POST /store/admin/sponsor {id, action:"approve"|"reject"|"remove"}
//   POST /store/book 에 개발자 토큰을 붙이면 구매 없이 받는다 (TestFlight·Xcode 빌드 확인용)
const STORE_PLANS = ["monthly", "yearly"];
const STORE_BOOKS = { side: { need: 1, langs: ["ko", "en"] }, vol1: { need: 2, langs: ["ko", "en"] } };   // need: 오류 문구 고르기용
const VOL1_MONTHS = 9;

async function store(req, env, u, out, cors) {
  const p = u.pathname.slice("/store/".length);
  const kv = env.VIEWS;
  const tok = (req.headers.get("Authorization") || "").replace(/^Bearer\s+/i, "").trim();
  const isAdmin = async () => !!tok && ((env.ADMIN_KEY && tok === env.ADMIN_KEY) || !!(await ghWho(env, tok)));
  const logoUrl = (id, at) => `${u.origin}/store/logo/${id}?v=${encodeURIComponent(at || "")}`;

  if (req.method === "GET" && p === "sponsors") {
    const list = JSON.parse((await kv.get("st:sponsors")) || "[]");
    return new Response(JSON.stringify({ sponsors: list.map((s) => ({ name: s.name, logo: s.logo ? logoUrl(s.id, s.at) : null })) }), { headers: { ...cors, "Content-Type": "application/json; charset=utf-8", "Cache-Control": "public, max-age=600" } });
  }
  if (req.method === "GET" && p.startsWith("logo/")) {
    const id = p.slice(5);
    if (!/^[0-9a-f]{18}$/.test(id)) return out({ error: "모르는 주소" }, 404);
    const { value, metadata } = await kv.getWithMetadata(`st:logo:${id}:a`, "arrayBuffer");
    if (!value) return out({ error: "그림이 없습니다" }, 404);
    return new Response(value, { headers: { ...cors, "Content-Type": metadata?.type || "image/png", "Cache-Control": "public, max-age=86400" } });
  }

  // ---- 개발자
  if (p.startsWith("admin/")) {
    if (!(await isAdmin())) return out({ error: "개발자 토큰이 필요합니다" }, 403);
    const a = p.slice(6);
    if (a === "books" && req.method === "GET") {
      const r = await kv.list({ prefix: "st:book:" });
      return out({ books: r.keys.map((k) => ({ key: k.name.slice(8), ...(k.metadata || {}) })) });
    }
    if (a === "sponsors" && req.method === "GET") {
      // 신청 전부 (검토할 것 먼저). 검토 중 로고는 data: 주소로 같이 준다
      const r = await kv.list({ prefix: "st:sp:" }), all = [];
      for (const k of r.keys) {
        const rec = JSON.parse((await kv.get(k.name)) || "null"); if (!rec) continue;
        if (rec.pending?.logo) {
          const { value, metadata } = await kv.getWithMetadata(`st:logo:${rec.id}:p`, "arrayBuffer");
          if (value) { let s = ""; for (const x of new Uint8Array(value)) s += String.fromCharCode(x); rec.pending.logoData = `data:${metadata?.type || "image/png"};base64,${btoa(s)}`; }
        }
        if (rec.approved?.logo) rec.approved.logoUrl = logoUrl(rec.id, rec.approved.at);
        all.push(rec);
      }
      all.sort((x, y) => (y.pending ? 1 : 0) - (x.pending ? 1 : 0) || String(y.pending?.at || y.approved?.at).localeCompare(String(x.pending?.at || x.approved?.at)));
      return out({ sponsors: all });
    }
    if (req.method !== "POST") return out({ error: "POST 만 받습니다" }, 400);
    const b = await req.json().catch(() => ({}));
    if (a === "book") {
      const def = STORE_BOOKS[b.book], d = b.data;
      if (!def || !def.langs.includes(b.lang)) return out({ error: "book·lang 이 맞지 않습니다" }, 400);
      if (!d || !Array.isArray(d.chapters) || !d.chapters.length || !Number.isInteger(d.from)) return out({ error: "data 에 from·chapters 가 필요합니다" }, 400);
      if (b.book === "side" && d.from < 6) return out({ error: "외전은 6장부터만 올립니다 (5장까지는 앱·사이트에 공개)" }, 400);
      const raw = JSON.stringify({ lang: b.lang, from: d.from, source: String(d.source || "").slice(0, 80), chapters: d.chapters });
      const at = new Date().toISOString();
      await kv.put(`st:book:${b.book}:${b.lang}`, raw, { metadata: { from: d.from, chapters: d.chapters.length, bytes: new TextEncoder().encode(raw).length, at } });
      return out({ ok: true, at, chapters: d.chapters.length });
    }
    if (a === "sponsor") {
      if (!/^[0-9a-f]{18}$/.test(b.id || "")) return out({ error: "id 가 맞지 않습니다" }, 400);
      const k = "st:sp:" + b.id, rec = JSON.parse((await kv.get(k)) || "null");
      if (!rec) return out({ error: "없는 신청입니다" }, 404);
      if (b.action === "approve") {
        if (!rec.pending) return out({ error: "검토할 신청이 없습니다" }, 400);
        if (rec.pending.logo) {
          const { value, metadata } = await kv.getWithMetadata(`st:logo:${b.id}:p`, "arrayBuffer");
          if (value) await kv.put(`st:logo:${b.id}:a`, value, { metadata });
        } else await kv.delete(`st:logo:${b.id}:a`);
        rec.approved = { ...rec.pending, at: new Date().toISOString() }; rec.pending = null;
      } else if (b.action === "reject") {
        rec.pending = null;
      } else if (b.action === "remove") {
        rec.approved = null; await kv.delete(`st:logo:${b.id}:a`);
      } else return out({ error: "action 은 approve·reject·remove" }, 400);
      if (!rec.pending) await kv.delete(`st:logo:${b.id}:p`);
      await kv.put(k, JSON.stringify(rec));
      // 공개 목록 다시 만들기 (먼저 승인된 순)
      const list = JSON.parse((await kv.get("st:sponsors")) || "[]").filter((s) => s.id !== b.id);
      if (rec.approved) list.push({ id: b.id, name: rec.approved.name, logo: !!rec.approved.logo, at: rec.approved.at, since: rec.since });
      list.sort((x, y) => String(x.since).localeCompare(String(y.since)));
      await kv.put("st:sponsors", JSON.stringify(list));
      return out({ ok: true, record: rec });
    }
    return out({ error: "모르는 주소" }, 404);
  }

  // ---- 앱 (구매 확인)
  if (req.method !== "POST") return out({ error: "POST 만 받습니다" }, 400);
  const b = await req.json().catch(() => ({}));
  const tx = await storeEntitlements(env, b.jws), now = Date.now();
  const active = tx.filter((t) => !t.expiresDate || t.expiresDate > now);
  const months = new Set(tx.filter((t) => t.plan === "monthly").map((t) => t.transactionId)).size;
  const vol1Ok = active.some((t) => t.plan === "yearly") || months >= VOL1_MONTHS;
  const tier = vol1Ok ? 2 : active.length ? 1 : 0;
  const can = { side: active.length > 0, vol1: vol1Ok };   // 외전은 지금 구독 중일 때만

  if (p === "book") {
    const def = STORE_BOOKS[b.book];
    if (!def) return out({ error: "book 은 side·vol1" }, 400);
    const admin = !can[b.book] && (await isAdmin());
    if (!can[b.book] && !admin) return out({ error: def.need === 1 ? "구독하면 열려요" : "연 구독, 또는 월 구독 9개월 이상이면 열려요", tier }, 403);
    const lang = def.langs.includes(b.lang) ? b.lang : def.langs[0];
    // 번역본을 아직 안 올렸으면 한글 원고를 준다 (앱은 받은 book.lang 으로 저장한다)
    const raw = (await kv.get(`st:book:${b.book}:${lang}`)) || (lang !== def.langs[0] ? await kv.get(`st:book:${b.book}:${def.langs[0]}`) : null);
    if (!raw) return out({ error: "원고가 아직 서버에 올라가지 않았습니다", tier }, 404);
    return new Response(`{"tier":${admin ? 2 : tier},"book":${raw}}`, { headers: { ...cors, "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" } });
  }
  return out({ error: "모르는 주소" }, 404);
}

// 앱이 보낸 서명 거래 중 검증되고, 이 앱의 구독 상품이며, 환불·취소되지 않은 것만 { plan, … } 로 (끝난 것도 넣는다, 판단은 부르는 쪽)
async function storeEntitlements(env, list) {
  const bundle = env.BUNDLE_ID || "com.naeilo.widget", prefix = bundle + ".sub.";
  const res = [];
  for (const jws of (Array.isArray(list) ? list : [list]).filter((x) => typeof x === "string").slice(0, 12)) {
    const t = await verifyAppleJWS(jws).catch(() => null);
    if (!t || t.bundleId !== bundle || t.revocationDate || !String(t.productId || "").startsWith(prefix)) continue;
    if (t.environment !== "Production" && (env.NO_SANDBOX || t.environment !== "Sandbox")) continue;
    const plan = t.productId.slice(prefix.length);
    if (STORE_PLANS.includes(plan)) res.push({ ...t, plan });
  }
  return res;
}

// ------------------------------------------------------------ StoreKit 2 서명(JWS) 검증
// x5c 인증서 사슬: 잎(P-256) ← Apple WWDR 중간 인증서 ← Apple Root CA - G3 (SHA-256 지문 고정).
// 각 인증서가 다음 인증서 키로 서명됐는지, 발급자·주체가 맞는지, 거래 서명 시각에 유효했는지, Apple 표시 확장(OID)이 있는지 본다.
const APPLE_ROOT_G3 = "63343abfb89a6a03ebb57e9b3f5fa7be7c4f5c756f3017b3a8c488c3653e9179";
async function verifyAppleJWS(jws, rootFp = APPLE_ROOT_G3) {
  const [h, pl, sg] = String(jws).split(".");
  if (!h || !pl || !sg) throw new Error("JWS 모양이 아닙니다");
  const ub = (s) => Uint8Array.from(atob(s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4)), (c) => c.charCodeAt(0));
  const head = JSON.parse(new TextDecoder().decode(ub(h)));
  if (head.alg !== "ES256" || !Array.isArray(head.x5c) || head.x5c.length !== 3) throw new Error("x5c 가 없습니다");
  const certs = head.x5c.map((c) => x509(Uint8Array.from(atob(c), (ch) => ch.charCodeAt(0))));
  const fp = [...new Uint8Array(await crypto.subtle.digest("SHA-256", certs[2].der))].map((b) => b.toString(16).padStart(2, "0")).join("");
  if (fp !== rootFp) throw new Error("Apple 루트 인증서가 아닙니다");
  if (!hasBytes(certs[0].tbs, OID_LEAF) || !hasBytes(certs[1].tbs, OID_WWDR)) throw new Error("Apple 표시 확장이 없습니다");
  const payload = JSON.parse(new TextDecoder().decode(ub(pl)));
  const when = Number(payload.signedDate) || Date.now();
  for (let i = 0; i < 3; i++) {
    const c = certs[i], issuer = certs[Math.min(i + 1, 2)];
    if (!(c.notBefore <= when && when <= c.notAfter)) throw new Error("인증서 기간이 아닙니다");
    if (!eqBytes(c.issuer, issuer.subject)) throw new Error("발급자가 맞지 않습니다");
    const key = await crypto.subtle.importKey("spki", issuer.spki, { name: "ECDSA", namedCurve: issuer.curve }, false, ["verify"]);
    const ok = await crypto.subtle.verify({ name: "ECDSA", hash: c.hash }, key, rawSig(c.sig, issuer.curve === "P-384" ? 48 : 32), c.tbs);
    if (!ok) throw new Error("인증서 서명이 맞지 않습니다");
  }
  if (certs.some((c) => !c.curve || !c.hash)) throw new Error("모르는 키·서명 방식입니다");
  if (certs[0].curve !== "P-256") throw new Error("잎 인증서 키가 P-256 이 아닙니다");
  const leaf = await crypto.subtle.importKey("spki", certs[0].spki, { name: "ECDSA", namedCurve: "P-256" }, false, ["verify"]);
  if (!(await crypto.subtle.verify({ name: "ECDSA", hash: "SHA-256" }, leaf, ub(sg), new TextEncoder().encode(h + "." + pl)))) throw new Error("거래 서명이 맞지 않습니다");
  return payload;
}
const OID_LEAF = [0x06, 0x0a, 0x2a, 0x86, 0x48, 0x86, 0xf7, 0x63, 0x64, 0x06, 0x0b, 0x01]; // 1.2.840.113635.100.6.11.1
const OID_WWDR = [0x06, 0x0a, 0x2a, 0x86, 0x48, 0x86, 0xf7, 0x63, 0x64, 0x06, 0x02, 0x01]; // 1.2.840.113635.100.6.2.1
const OID_P256 = [0x2a, 0x86, 0x48, 0xce, 0x3d, 0x03, 0x01, 0x07], OID_P384 = [0x2b, 0x81, 0x04, 0x00, 0x22];
const OID_SHA256 = [0x2a, 0x86, 0x48, 0xce, 0x3d, 0x04, 0x03, 0x02], OID_SHA384 = [0x2a, 0x86, 0x48, 0xce, 0x3d, 0x04, 0x03, 0x03];
const eqBytes = (a, b) => a.length === b.length && a.every((x, i) => x === b[i]);
const hasBytes = (a, n) => { for (let i = 0; i + n.length <= a.length; i++) { let j = 0; while (j < n.length && a[i + j] === n[j]) j++; if (j === n.length) return true; } return false; };
// DER 한 칸: { tag, start(머리 포함), body(내용 시작), end }
function tlv(d, i) {
  const tag = d[i]; let len = d[i + 1], body = i + 2;
  if (len & 0x80) { const n = len & 0x7f; len = 0; for (let k = 0; k < n; k++) len = len * 256 + d[body + k]; body += n; }
  if (body + len > d.length) throw new Error("DER 길이가 맞지 않습니다");
  return { tag, start: i, body, end: body + len };
}
const kids = (d, t) => { const r = []; for (let i = t.body; i < t.end; ) { const c = tlv(d, i); r.push(c); i = c.end; } return r; };
function x509(der) {
  const [tbsT, algT, sigT] = kids(der, tlv(der, 0));
  const f = kids(der, tbsT), o = f[0].tag === 0xa0 ? 1 : 0; // [0] 버전이 있으면 한 칸 밀린다
  const [issuer, validity, subject, spki] = [f[o + 2], f[o + 3], f[o + 4], f[o + 5]]; // 일련번호, 서명 방식, 발급자, 기간, 주체, 공개 키
  const time = (t) => {
    const s = new TextDecoder().decode(der.slice(t.body, t.end)), g = t.tag === 0x18;
    const y = g ? +s.slice(0, 4) : (+s.slice(0, 2) < 50 ? 2000 : 1900) + +s.slice(0, 2), r = s.slice(g ? 4 : 2);
    return Date.UTC(y, +r.slice(0, 2) - 1, +r.slice(2, 4), +r.slice(4, 6), +r.slice(6, 8), +r.slice(8, 10));
  };
  const [nb, na] = kids(der, validity);
  const alg = der.slice(algT.body, algT.end), sk = der.slice(spki.start, spki.end);
  return {
    der, tbs: der.slice(tbsT.start, tbsT.end), issuer: der.slice(issuer.start, issuer.end), subject: der.slice(subject.start, subject.end),
    notBefore: time(nb), notAfter: time(na), spki: sk,
    curve: hasBytes(sk, OID_P384) ? "P-384" : hasBytes(sk, OID_P256) ? "P-256" : "",
    hash: hasBytes(alg, OID_SHA384) ? "SHA-384" : hasBytes(alg, OID_SHA256) ? "SHA-256" : "",
    sig: der.slice(sigT.body + 1, sigT.end), // BIT STRING 첫 바이트(남는 비트 수) 빼고
  };
}
// DER ECDSA 서명 SEQUENCE{r, s} → WebCrypto 가 받는 r‖s (각 n 바이트)
function rawSig(d, n) {
  const [r, s] = kids(d, tlv(d, 0)), out = new Uint8Array(2 * n);
  for (const [k, t] of [[0, r], [1, s]]) {
    let v = d.slice(t.body, t.end); while (v.length > n && v[0] === 0) v = v.slice(1);
    if (v.length > n) throw new Error("서명 길이가 맞지 않습니다");
    out.set(v, k * n + n - v.length);
  }
  return out;
}

// 11) Claude 사용량: 공식 공개 API 가 아니라 바뀔 수 있다. 응답 형식 { five: { p, reset }, week: { p, reset }, at, src }
async function claudeUsage(env, out) {
  const tok = (env.CLAUDE_TOKEN || "").replace(/\s+/g, "").replace(/^["']|["']$/g, "").replace(/^Bearer/i, ""); // 터미널에서 줄바꿈된 채 복사해도 된다
  if (!tok) return out({ error: "중계에 CLAUDE_TOKEN 비밀값이 없어요" }, 400);
  const key = new Request("https://cu.internal/usage"), cache = caches.default;
  const hit = await cache.match(key);
  if (hit) return out(await hit.json());
  const iso = (v) => { if (v == null || v === "") return null; const n = Number(v); const d = isNaN(n) ? new Date(v) : new Date(n < 1e12 ? n * 1000 : n); return isNaN(d) ? null : d.toISOString().replace(/\.\d{3}Z$/, "Z"); }; // 초 아래는 뺀다 (앱의 ISO 8601 읽기)
  const auth = { Authorization: "Bearer " + tok, "anthropic-beta": "oauth-2025-04-20", "anthropic-version": "2023-06-01", "User-Agent": "naeilo-usage" };
  let res = null;
  // 1) OAuth 사용량 주소 (utilization 은 0~100)
  const r1 = await fetch("https://api.anthropic.com/api/oauth/usage", { headers: auth, signal: AbortSignal.timeout(10000) }).catch(() => null);
  if (r1 && r1.ok) {
    const j = await r1.json().catch(() => null);
    if (j && j.five_hour && j.seven_day) res = { five: { p: Number(j.five_hour.utilization) || 0, reset: iso(j.five_hour.resets_at) }, week: { p: Number(j.seven_day.utilization) || 0, reset: iso(j.seven_day.resets_at) }, src: "oauth" };
  }
  // 2) 가장 작은 메시지 응답의 한도 머리글 (utilization 은 0~1)
  let why = r1 ? "usage " + r1.status : "usage 연결 실패";
  if (!res) {
    const body = JSON.stringify({ model: env.CLAUDE_MODEL || "claude-haiku-4-5", max_tokens: 1, system: "You are Claude Code, Anthropic's official CLI for Claude.", messages: [{ role: "user", content: "." }] });
    const r2 = await fetch("https://api.anthropic.com/v1/messages", { method: "POST", headers: { ...auth, "Content-Type": "application/json" }, body, signal: AbortSignal.timeout(15000) }).catch(() => null);
    const h = (k) => r2 && r2.headers.get("anthropic-ratelimit-unified-" + k);
    if (r2 && h("5h-utilization") != null) {
      const pc = (v) => { const n = Number(v) || 0; return n <= 1.5 ? n * 100 : n; };
      res = { five: { p: pc(h("5h-utilization")), reset: iso(h("5h-reset")) }, week: { p: pc(h("7d-utilization")), reset: iso(h("7d-reset")) }, src: "headers" };
    } else why += ", messages " + (r2 ? r2.status : "연결 실패");
  }
  // 토큰 모양만 알려 준다 (값은 보이지 않는다): sk-ant-oat01- 로 시작하고 100자 남짓이어야 한다
  if (!res) return out({ error: "Claude 사용량을 받지 못했어요 (" + why + ")", token: tok.slice(0, 13) + "… " + tok.length + "자" }, 502);
  res.at = new Date().toISOString().replace(/\.\d{3}Z$/, "Z");
  await cache.put(key, new Response(JSON.stringify(res), { headers: { "Content-Type": "application/json", "Cache-Control": "max-age=300" } }));
  return out(res);
}
