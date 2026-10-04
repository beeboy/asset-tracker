// 개발자용 중계 (Cloudflare Workers 무료 요금제로 충분). 사용자는 아무것도 설정하지 않는다.
// 1) 시세: 브라우저는 Yahoo Finance 를 직접 부를 수 없어서(CORS) 이 중계를 거친다. Yahoo 차트 주소만 통과시킨다.
// 2) AI (/ai): 페이지의 AI 분석을 Google Gemini 무료 등급으로 답한다. 키는 이 중계의 비밀값에만 둔다.
// 배포: Cloudflare 대시보드 → Workers → Create → 이 코드 붙여넣기 → Deploy
//       Settings → Variables → Secret 에 GEMINI_KEY (https://aistudio.google.com/apikey 에서 무료 발급)
//       data/config.json 의 "proxy" 에 "https://<이름>.workers.dev/?url=", "ai" 에 "https://<이름>.workers.dev/ai" 를 넣고 커밋.
const ALLOW = /^https:\/\/query[12]\.finance\.yahoo\.com\/v8\/finance\/chart\//;
// 구글이 모델을 바꾸면 차례로 시도한다. 비밀값/변수 GEMINI_MODEL 을 넣으면 그 모델을 먼저 쓴다
const MODELS = ["gemini-3.8-flash", "gemini-flash-latest", "gemini-flash-lite-latest"];
export default {
  async fetch(req, env) {
    const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Methods": "GET, POST, OPTIONS", "Access-Control-Allow-Headers": "Content-Type" };
    if (req.method === "OPTIONS") return new Response(null, { headers: cors });
    const u = new URL(req.url);
    if (u.pathname === "/ai") {
      const out = (o, st = 200) => new Response(JSON.stringify(o), { status: st, headers: { ...cors, "Content-Type": "application/json; charset=utf-8" } });
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
    const r = await fetch(target, { headers: { "User-Agent": "Mozilla/5.0" }, cf: { cacheTtl: 60 } });
    return new Response(r.body, { status: r.status, headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "max-age=60" } });
  },
};
