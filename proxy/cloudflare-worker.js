// 개발자용 시세 중계 (Cloudflare Workers 무료 요금제로 충분)
// 브라우저는 Yahoo Finance 를 직접 부를 수 없어서(CORS) 이 중계를 거친다. Yahoo 차트 주소만 통과시킨다.
// 배포: Cloudflare 대시보드 → Workers → Create → 이 코드 붙여넣기 → Deploy
//       나온 주소를 data/config.json 의 "proxy" 에  "https://<이름>.workers.dev/?url="  형태로 넣고 커밋.
const ALLOW = /^https:\/\/query[12]\.finance\.yahoo\.com\/v8\/finance\/chart\//;
export default {
  async fetch(req) {
    const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Methods": "GET, OPTIONS" };
    if (req.method === "OPTIONS") return new Response(null, { headers: cors });
    const target = new URL(req.url).searchParams.get("url") || "";
    if (!ALLOW.test(target)) return new Response("not allowed", { status: 400, headers: cors });
    const r = await fetch(target, { headers: { "User-Agent": "Mozilla/5.0" }, cf: { cacheTtl: 60 } });
    return new Response(r.body, { status: r.status, headers: { ...cors, "Content-Type": "application/json", "Cache-Control": "max-age=60" } });
  },
};
