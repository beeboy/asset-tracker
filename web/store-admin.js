// 개발자용: 유료 원고(외전 6장부터·본편 1권)를 중계 KV 에 올리고, 고급 스페셜티 커피 후원자 이름·로고를 승인한다.
// 원고는 이 브라우저에서 바로 읽어 중계로만 보낸다 (저장소·사이트에는 남지 않는다). 토큰은 앱 설정 > 개발자용 의 GitHub 토큰.
// 원고 → 블록 규칙은 ios-app/tools/story.py 와 같다 (앱 서재가 같은 모양으로 그린다):
//   h 절 제목, q 인용(>), p 문단, s 장면 나눔, t 표(rows). 줄 끝 공백 두 칸은 줄바꿈.

export const SOURCES = {
  side: { ko: "원고/side_story_v2.1.md", en: "원고/Side Story - Resonance Across Kinds.md" },
  vol1: { ko: "원고/vol1_v8.1.md" },
};
export const SIDE_FREE = 6; // 프롤로그~5장은 앱·사이트에 공개. 6장부터만 올린다

/** 마크다운 원고 → [{title, name, blocks}]. vol1 은 '## N부.' 아래 '## 제N장 — 이름' 으로 장이 나뉜다 */
export function parseBook(md, kind) {
  const vol = kind === "vol1", lvl = vol ? "## " : "# ";
  const chapters = [];
  let cur = null, part = null, pre = null, buf = [], k = "p";
  const target = () => cur || pre;
  const flush = () => {
    const tg = target();
    if (tg && buf.length && k === "t") {
      const rows = buf.map((l) => l.trim().replace(/^\|+|\|+$/g, "").split("|").map((c) => c.trim())).filter((r) => !r.every((c) => /^:?-+:?$/.test(c)));
      tg.blocks.push({ k: "t", t: "", rows });
    } else if (tg && buf.length) {
      const text = buf.map((l) => l.replace(/\s+$/, "") + (l.endsWith("  ") ? "\n" : " ")).join("").trim().replace(/\s*\n\s*/g, "\n");
      if (text) tg.blocks.push({ k, t: text });
    }
    buf = []; k = "p";
  };
  for (const line of md.split(/\r?\n/)) {
    if (line.startsWith(lvl) || (vol && line.startsWith("# "))) {
      flush();
      const head = line.replace(/^#+ /, "").trim();
      if (vol && line.startsWith("# ")) continue; // 책 제목
      const pm = vol && /^(\d+)부\.?\s*(.*)$/.exec(head);
      if (pm) { part = pm[1]; cur = null; pre = { blocks: [{ k: "h", t: head }] }; continue; }
      const parts = head.split(/\s+[―—]\s+/);
      if (parts.length >= 2 && !/^(중첩된 현실|Nested Reality)/.test(head)) {
        const name = parts.slice(1).join(" — ");
        cur = { title: vol && part ? `${part}부 ${parts[0]}` : parts[0], name, blocks: pre ? pre.blocks : [] };
        pre = null; chapters.push(cur);
      }
      continue;
    }
    if (!target()) continue;
    if (/^#{2,4} /.test(line)) { flush(); target().blocks.push({ k: "h", t: line.replace(/^#+ /, "").trim() }); }
    else if (["---", "***", "⸻", "* * *"].includes(line.trim())) { flush(); target().blocks.push({ k: "s", t: "" }); }
    else if (!line.trim()) flush();
    else if (line.startsWith("|")) { if (buf.length && k !== "t") flush(); k = "t"; buf.push(line); }
    else if (line.trim() === ">") flush(); // 인용 안의 빈 줄은 인용 문단을 나눈다 (영문본은 같은 자리가 문단 여러 개라 블록 수가 맞는다)
    else if (line.startsWith(">")) { if (buf.length && k !== "q") flush(); k = "q"; buf.push(line.replace(/^>+/, "").replace(/^ +/, "")); }
    else { if (buf.length && (k === "q" || k === "t")) flush(); buf.push(line); }
  }
  flush();
  return chapters;
}

/** 외전 한글·영문을 맞춰 본다 (story.py 와 같은 검사). 영문은 인용 표시가 없어서 한글 쪽을 따른다 */
export function alignSide(ko, en) {
  if (ko.length !== en.length) throw new Error(`장 수가 다름 (한 ${ko.length} / 영 ${en.length})`);
  ko.forEach((a, i) => {
    const b = en[i];
    if (a.blocks.length !== b.blocks.length) throw new Error(`${a.title}: 블록 수가 다름 (한 ${a.blocks.length} / 영 ${b.blocks.length})`);
    a.blocks.forEach((x, j) => {
      const y = b.blocks[j];
      if ((/[ht]/.test(x.k) || /[ht]/.test(y.k)) && x.k !== y.k) throw new Error(`${a.title}: 블록 종류가 다름 (${j}번)`);
      if (x.k === "q") y.k = "q";
    });
  });
}

/** 세 원고 → 올릴 책들 [{book, lang, data}] */
export function buildBooks(texts) {
  const side = { ko: parseBook(texts.side.ko, "side"), en: parseBook(texts.side.en, "side") };
  alignSide(side.ko, side.en);
  if (side.ko.length <= SIDE_FREE) throw new Error(`외전 장이 ${side.ko.length}개뿐 (6장부터가 없음)`);
  const vol1 = parseBook(texts.vol1.ko, "vol1");
  if (!vol1.length) throw new Error("본편 1권에서 장을 찾지 못했습니다");
  const name = (p) => p.split("/").pop();
  return [
    ...["ko", "en"].map((lang) => ({ book: "side", lang, data: { from: SIDE_FREE, source: name(SOURCES.side[lang]), chapters: side[lang].slice(SIDE_FREE) } })),
    { book: "vol1", lang: "ko", data: { from: 0, source: name(SOURCES.vol1.ko), chapters: vol1 } },
  ];
}

// ------------------------------------------------------------ 화면
if (typeof document !== "undefined") {
  const $ = (s) => document.querySelector(s);
  const esc = (s) => String(s ?? "").replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
  const TOKEN_KEY = "asset-tracker-gh-token";
  const token = () => { try { return localStorage.getItem(TOKEN_KEY) || ""; } catch (e) { return ""; } };
  const tok = () => $("#tok").value.trim() || token();
  let base = "";
  const api = async (path, body) => {
    const r = await fetch(base + "/store/" + path, { method: body ? "POST" : "GET", headers: { ...(tok() ? { Authorization: "Bearer " + tok() } : {}), ...(body ? { "Content-Type": "application/json" } : {}) }, body: body && JSON.stringify(body) });
    const j = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(j.error || "HTTP " + r.status);
    return j;
  };
  const say = (el, t, bad) => { el.textContent = t; el.style.color = bad ? "var(--down, #d33)" : ""; };

  async function books() {
    try {
      const j = await api("admin/books");
      $("#books").innerHTML = j.books.length ? j.books.map((b) => `<li>${esc(b.key)} · ${b.from ? esc(b.from) + "장부터 " : ""}${esc(b.chapters)}개 장 · ${Math.round((b.bytes || 0) / 1024)}KB · ${esc((b.at || "").slice(0, 16).replace("T", " "))}</li>`).join("") : "<li>아직 올린 원고가 없습니다</li>";
    } catch (e) { $("#books").innerHTML = `<li>${esc(e.message)}</li>`; }
  }
  async function readVault(path) {
    const r = await fetch("https://api.github.com/repos/beeboy/MyVault/contents/" + path.split("/").map(encodeURIComponent).join("/"), { headers: { Accept: "application/vnd.github.raw+json", Authorization: "Bearer " + token() } });
    if (!r.ok) throw new Error(`MyVault 에서 ${path} 를 읽지 못했습니다 (${r.status}). 아래에서 파일을 직접 골라 주세요`);
    return r.text();
  }
  async function upload(fromFiles) {
    const msg = $("#upmsg");
    try {
      say(msg, "원고 읽는 중…");
      const texts = { side: {}, vol1: {} };
      if (fromFiles) {
        for (const [id, b, l] of [["#f-side-ko", "side", "ko"], ["#f-side-en", "side", "en"], ["#f-vol1", "vol1", "ko"]]) {
          const f = $(id).files[0]; if (!f) throw new Error("파일 세 개를 모두 골라 주세요");
          texts[b][l] = await f.text();
        }
      } else {
        for (const b of Object.keys(SOURCES)) for (const l of Object.keys(SOURCES[b])) texts[b][l] = await readVault(SOURCES[b][l]);
      }
      const list = buildBooks(texts);
      for (const x of list) {
        say(msg, `${x.book} ${x.lang} 올리는 중…`);
        await api("admin/book", x);
      }
      say(msg, "올렸습니다: " + list.map((x) => `${x.book} ${x.lang} ${x.data.chapters.length}장`).join(", "));
      books();
    } catch (e) { say(msg, e.message, true); }
  }
  async function sponsors() {
    const box = $("#sponsors");
    try {
      const j = await api("admin/sponsors");
      box.innerHTML = j.sponsors.length ? j.sponsors.map((s) => `<div class="card" data-id="${esc(s.id)}">
        <div class="row wrap"><b>${esc(s.pending?.name || s.approved?.name)}</b><span class="muted small">${s.env === "Sandbox" ? "샌드박스(TestFlight) 구매 · " : ""}후원 ${esc((s.since || "").slice(0, 10))}</span></div>
        ${s.pending ? `<p class="small">검토할 신청: <b>${esc(s.pending.name)}</b> ${s.pending.logoData ? `<br><img src="${esc(s.pending.logoData)}" alt="" style="max-height:64px;max-width:200px;margin-top:6px">` : "(로고 없음)"}</p>
          <div class="row"><button data-a="approve">승인</button><button data-a="reject">거절</button></div>` : ""}
        ${s.approved ? `<p class="small">공개 중: ${esc(s.approved.name)} ${s.approved.logoUrl ? `<img src="${esc(s.approved.logoUrl)}" alt="" style="max-height:32px;vertical-align:middle">` : ""}</p><button data-a="remove">공개 내리기</button>` : ""}
      </div>`).join("") : '<p class="muted small">신청이 없습니다</p>';
    } catch (e) { box.innerHTML = `<p class="small">${esc(e.message)}</p>`; }
  }
  $("#sponsors").addEventListener("click", async (ev) => {
    const a = ev.target.closest("button[data-a]")?.dataset.a, id = ev.target.closest("[data-id]")?.dataset.id;
    if (!a || !id) return;
    if (a === "remove" && !confirm("이 후원자를 앱 정보 화면에서 내릴까요?")) return;
    try { await api("admin/sponsor", { id, action: a }); } catch (e) { alert(e.message); }
    sponsors();
  });
  $("#up-vault").onclick = () => upload(false);
  $("#up-files").onclick = () => upload(true);
  $("#reload").onclick = () => { books(); sponsors(); };
  if (token()) $("#tok").placeholder = "앱에 저장된 GitHub 토큰을 씁니다";
  fetch("../data/config.json").then((r) => r.json()).then((c) => {
    base = (c.store || c.push || (c.ai || "").replace(/\/ai$/, "")).replace(/\/$/, "");
    $("#base").textContent = base || "data/config.json 에 중계 주소가 없습니다";
    if (base) { books(); sponsors(); }
  });
}
