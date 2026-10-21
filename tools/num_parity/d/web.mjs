// 숫자 맞추기 D 단계 (웹 쪽): web/app.js 의 b64·esDerive 원문을 꺼내 같은 비밀번호로 돌리고,
// syncCall 과 같은 AES-GCM(WebCrypto)으로 같은 키·iv 로 암호화해 web.json 을 쓴다. 사용: node tools/num_parity/d/web.mjs <d 폴더>
import { readFileSync, writeFileSync } from "node:fs";

const dir = process.argv[2];
const src = readFileSync(new URL("../../../web/app.js", import.meta.url), "utf8");
const b64Line = src.split("\n").find((l) => l.includes("const b64 = (u8)"));
const start = src.indexOf("async function esDerive(pw)");
let i = src.indexOf("{", start), d = 0;
for (; i < src.length; i++) { if (src[i] === "{") d++; else if (src[i] === "}" && --d === 0) break; }
const esDerive = new Function(b64Line + "\n" + src.slice(start, i + 1) + "\nreturn { esDerive, b64, unb64 };")();

const inp = JSON.parse(readFileSync(dir + "/input.json", "utf8"));
const out = { derive: [], aes: [] };
for (const pw of inp.pws) out.derive.push(await esDerive.esDerive(pw));
const k = (await esDerive.esDerive(inp.aesPw)).k;
const key = await crypto.subtle.importKey("raw", esDerive.unb64(k), "AES-GCM", false, ["encrypt", "decrypt"]);
const iv = new Uint8Array(inp.aesIv);
for (const p of inp.plains) {
  const c = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, new TextEncoder().encode(p));
  out.aes.push({ c: esDerive.b64(iv) + "." + esDerive.b64(c) });
}
writeFileSync(dir + "/web.json", JSON.stringify(out));
console.log(`D 단계 (웹): 비밀번호 ${out.derive.length} · 암호 ${out.aes.length}`);
