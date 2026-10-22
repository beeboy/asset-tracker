// 입구 페이지 (naeilo.com/): 직접 해보기(목표일 전망) + 출시 알림 신청
// 직접 해보기는 앱 분석 > 목표일 전망(AnalysisView.swift ForecastView·ForecastParts.swift ScenarioChart)과 같은 화면이다.
// 종목 이름 없는 예시 자산 1억, 목표 1.5억, 목표일 3년 뒤. 앱이 종목 없을 때 쓰는 값(신중 연 7%, 지난 3년 연 11%, 흔들림 16%)으로
// 앱 닫힌 식(로그정규)을 그대로 계산한다. 추세 막대를 밀면 '내 설정' 수익률이 두 값 사이(로그 공간)에서 바뀐다. 시세는 받지 않는다.
(function () {
  "use strict";
  const $ = (s) => document.querySelector(s);
  const V0 = 1e8, GOAL = 1.5e8, MB = 0.07, MS = 0.11, SG = 0.16, YEARS = 3;
  const LENS = { base: "신중하게", mine: "내 설정", smooth: "지난 3년처럼" }, SHORT = { base: "신중", mine: "내 설정", smooth: "3년처럼" };
  let lens = "mine", trust = 50;

  // ForecastView.amt: 3.30억 · 11.5억 · 826만
  const amt = (v) => {
    const a = Math.abs(v), s = v < 0 ? "−" : "";
    if (a >= 1e9) return s + (a / 1e8).toFixed(1) + "억";
    if (a >= 1e8) return s + (a / 1e8).toFixed(2) + "억";
    return s + Math.round(a / 1e4).toLocaleString("ko-KR") + "만";
  };
  const sgn0 = (x) => (x >= 0 ? "+" : "−") + Math.abs(Math.round(x * 100)) + "%";
  const Phi = (x) => { // 표준정규 누적 (Abramowitz-Stegun 7.1.26)
    const t = 1 / (1 + 0.3275911 * Math.abs(x) / Math.SQRT2);
    const e = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x / 2);
    return x >= 0 ? (1 + e) / 2 : (1 - e) / 2;
  };
  const end = new Date(new Date().getFullYear() + YEARS, new Date().getMonth(), 1);
  const endShort = `${end.getFullYear()}.${String(end.getMonth() + 1).padStart(2, "0")}`;
  const N = YEARS * 12;
  const muOf = (l) => (l === "base" ? MB : l === "smooth" ? MS : Math.exp((1 - trust / 100) * Math.log(1 + MB) + (trust / 100) * Math.log(1 + MS)) - 1);
  // AppModel.closedForecast: 월별 분위 금액과 목표일 달성 확률
  function forecast(l) {
    const mu = muOf(l), g = mu - SG * SG / 2;
    const q = (z) => Array.from({ length: N + 1 }, (_, i) => (i ? V0 * Math.exp(g * i / 12 + z * SG * Math.sqrt(i / 12)) : V0));
    return { mu, q5: q(-1.645), q25: q(-0.674), q50: q(0), q75: q(0.674), q95: q(1.645), pGoal: Phi((Math.log(V0 / GOAL) + g * YEARS) / (SG * Math.sqrt(YEARS))) };
  }

  // ScenarioChart: 선형 축, 고른 시나리오의 50%·90% 띠와 가운데 선, 다른 시나리오 가운데 선(회색 점선), 목표선, 지금 점
  const step = (top) => { let s = 5e7, k = 0; const m = [2, 2.5, 2]; while (top / s > 3) { s *= m[k % 3]; k++; } return s; };
  const axis = (v) => { if (v <= 0) return "0"; const e = v / 1e8; return e === Math.round(e) ? `${e}억` : `${e.toFixed(1)}억`; };
  function chart(f, others) {
    const SW = 270, SH = 190, left = 36, bottom = 20, W = SW - left, H = SH - bottom;
    const top0 = Math.max(GOAL, ...f.q95, ...others.flatMap((o) => o.med)) * 1.05, st = step(top0), top = Math.max(st, Math.ceil(top0 / st) * st);
    const x = (i) => left + (i / N) * W, y = (v) => H - (Math.min(Math.max(v, 0), top) / top) * H;
    const pts = (a) => a.map((v, i) => `${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(" ");
    const band = (a, b) => pts(a) + " " + b.map((v, i) => `${x(i).toFixed(1)},${y(v).toFixed(1)}`).reverse().join(" ");
    let g = "";
    for (let v = 0; v <= top + 1; v += st) g += `<line class="gl" x1="${left}" y1="${y(v)}" x2="${SW}" y2="${y(v)}"/><text x="${left - 4}" y="${y(v) + 3.5}" text-anchor="end">${axis(v)}</text>`;
    g += `<polygon class="b9" points="${band(f.q95, f.q5)}"/><polygon class="b5" points="${band(f.q75, f.q25)}"/>`;
    others.forEach((o) => { g += `<polyline class="oth" points="${pts(o.med)}"/><text x="${SW - 2}" y="${y(o.med[N]) - 4}" text-anchor="end">${o.label}</text>`; });
    g += `<polyline class="mid" points="${pts(f.q50)}"/><text class="s" x="${SW - 2}" y="${y(f.q50[N]) - 5}" text-anchor="end">${SHORT[lens]}</text>`;
    const gy = y(GOAL);
    g += `<line class="goal" x1="${left}" y1="${gy}" x2="${SW}" y2="${gy}"/><text class="g" x="${left + 4}" y="${gy < 12 ? gy + 11 : gy - 5}">목표</text>`;
    g += [0, 0.2].map((t) => `<circle class="pulse" cx="${x(0)}" cy="${y(V0)}" r="4" style="animation-delay:${t}s"/>`).join(""); // 현재 점 박동 (앱과 같은 규격)
    g += `<circle cx="${x(0)}" cy="${y(V0)}" r="4"/><text class="n" x="${x(0) + 6}" y="${y(V0) + 15}">지금 ${amt(V0)}</text>`;
    const ly = SH - 3;
    g += `<text x="${left}" y="${ly}">지금</text>`;
    for (let k = 1; k < YEARS; k++) g += `<text x="${left + (k / YEARS) * W}" y="${ly}" text-anchor="middle">${k}년 뒤</text>`;
    g += `<text x="${SW}" y="${ly}" text-anchor="end">${endShort}</text>`;
    $("#fcChart").innerHTML = g;
  }

  function draw() {
    const fs = { base: forecast("base"), mine: forecast("mine"), smooth: forecast("smooth") }, f = fs[lens];
    const med = f.q50[N], chg = med / V0 - 1, shown = lens === "base" ? 0 : lens === "smooth" ? 100 : trust;
    document.querySelectorAll("#fcSeg button").forEach((b) => { const on = b.dataset.l === lens; b.classList.toggle("on", on); b.setAttribute("aria-pressed", on); });
    $("#fcTrustIn").value = shown;
    $("#fcTrust").textContent = shown + "%";
    $("#fcWhen").textContent = endShort;
    $("#fcMu").textContent = `그럴듯한 금액 · 연 ${sgn0(f.mu)}`;
    $("#fcMid").textContent = amt(med);
    const c = $("#fcChg"); c.textContent = sgn0(chg); c.classList.toggle("dn", chg < 0);
    $("#fcRng").textContent = `10번 중 9번 ${amt(f.q5[N])} ~ ${amt(f.q95[N])}`;
    chart(f, Object.keys(fs).filter((l) => l !== lens).map((l) => ({ label: SHORT[l], med: fs[l].q50 })));
    // 세 가지 미래
    $("#fcRows").innerHTML = Object.keys(fs).map((l) => { const x = fs[l];
      return `<div class="fc-row${l === lens ? " on" : ""}" data-l="${l}" role="button" tabindex="0"><span>${SHORT[l]}<small>${sgn0(x.mu)}</small></span><span>${amt(x.q50[N])}</span><span>${amt(x.q5[N])}</span><span>${Math.round(x.pGoal * 100)}%</span></div>`; }).join("");
    const below = Object.values(fs).filter((x) => x.q5[N] < V0).length;
    $("#fcMsg").textContent = below === 3 ? `운 나쁘면 지금(${amt(V0)})보다 줄 수도` : below ? `신중하게 보면 지금(${amt(V0)})보다 줄 수도` : `운 나빠도 지금(${amt(V0)})보다 많을 가능성 큼`;
  }
  const pick = (l) => { if (l && LENS[l]) { lens = l; draw(); } };

  // ------------------------------------------------------------ 출시 알림 신청 (중계 /notify, 이메일과 날짜만 저장)
  let config = {};
  fetch("data/config.json", { cache: "no-cache" }).then((r) => (r.ok ? r.json() : {})).then((j) => { config = j || {}; }).catch(() => { /* 무시 */ });
  const notifyUrl = () => config.notify || (config.push ? config.push.replace(/\/$/, "") + "/notify" : "");
  async function notify(del) {
    const msg = $("#notifyMsg"), email = $("#email").value.trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) { msg.textContent = "이메일 주소를 확인해 주세요."; return; }
    const url = notifyUrl(); if (!url) { msg.textContent = "지금은 신청을 받을 수 없어요. 잠시 뒤 다시 해 주세요."; return; }
    const btn = $("#notify .btn"); btn.disabled = true; msg.textContent = "보내는 중이에요…";
    try {
      const r = await fetch(url + (del ? "/del" : ""), { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email }) });
      const j = await r.json().catch(() => ({}));
      if (!r.ok || !j.ok) throw new Error(j.error || r.status);
      msg.textContent = del ? "신청을 취소했어요. 이메일을 지웠어요." : "신청했어요. 앱이 나오면 이 주소로 한 번 알려 드릴게요.";
      if (!del) $("#email").value = "";
    } catch (e) { msg.textContent = "지금은 신청을 받을 수 없어요. 잠시 뒤 다시 해 주세요."; }
    finally { btn.disabled = false; }
  }


  // ------------------------------------------------------------ 맨 위 두 인물의 한마디: 앱 위젯의 오른 날 대사(lines_ko.json widget.up)에서 골라, 열 때마다 하나씩
  const SAYS = {
    sua: ["오른 날이에요.", "오늘은 제때 왔어요.", "왔어요. 오른 날이에요. 천천히 봐요.", "제때 오셨어요. 오른 날이에요.", "오른 날이에요. 천천히 봐요.",
      "조용히 볼게요. 오늘은 제때 왔어요.", "여기 있어요. 오늘은 제때 왔어요. 기록해 둘게요.", "+1.2%예요. 천천히 봐요.", "왔어요. +1.2%예요."],
    sio: ["+1.2. 셌어.", "올랐어. 1.2. 셌어.", "짧게. +1.2. 됐어.", "오늘도. +1.2. 그거면 돼.", "봤어. +1.2. 내일도 셀게.",
      "그래. 올랐어. 1.2. 급할 거 없어.", "왔네. +1.2. 됐어.", "음. +1.2. 세어 둘게.", "짧게. +1.2. 셌어."],
  };
  [["#saySua", SAYS.sua, "수아"], ["#saySio", SAYS.sio, "시오"]].forEach(([sel, l, who]) => {
    const el = $(sel); if (el) el.innerHTML = `${l[Math.floor(Math.random() * l.length)]}<b>${who}</b>`;
  });

  // ------------------------------------------------------------ 아이폰 상단 시계: 보는 사람 기기의 지금 시각 (아이폰처럼 12시간, 오전·오후 없이)
  function tick() {
    const d = new Date(), t = `${d.getHours() % 12 || 12}:${String(d.getMinutes()).padStart(2, "0")}`;
    document.querySelectorAll(".sbar .clock").forEach((el) => { el.textContent = t; });
    setTimeout(tick, 60000 - (d.getSeconds() * 1000 + d.getMilliseconds()) + 50); // 다음 분이 바뀔 때
  }
  tick();

  // ------------------------------------------------------------ 시작
  // 삼성 인터넷·크롬 안드로이드의 '웹 페이지 어둡게'(자동 다크)는 prefers-color-scheme 을 바꾸지 않고 색만 덮는다.
  // 그래서 <picture> 의 다크판이 안 골라지니, 자동 다크면 앱 화면 3장을 다크판으로 직접 바꾼다 (Chrome 문서의 감지법)
  const probe = document.createElement("div");
  probe.style.cssText = "display:none;background-color:canvas;color-scheme:light";
  document.body.appendChild(probe);
  const autoDark = getComputedStyle(probe).backgroundColor !== "rgb(255, 255, 255)";
  probe.remove();
  if (autoDark) document.querySelectorAll(".phone picture").forEach((p) => {
    const src = p.querySelector("source"), img = p.querySelector("img");
    if (src && img) { img.src = src.srcset; src.remove(); }
  });
  document.querySelectorAll(".phone img").forEach((img) => img.addEventListener("error", () => img.classList.add("miss")));
  $("#fcTrustIn").addEventListener("input", (e) => {
    const v = Number(e.target.value);
    if (v <= 0) lens = "base"; else if (v >= 100) lens = "smooth"; else { lens = "mine"; trust = v; }
    draw();
  });
  $("#fcSeg").addEventListener("click", (e) => pick(e.target.closest("button")?.dataset.l));
  $("#fcRows").addEventListener("click", (e) => pick(e.target.closest(".fc-row")?.dataset.l));
  $("#fcRows").addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); pick(e.target.closest(".fc-row")?.dataset.l); } });
  draw();
  $("#notify").addEventListener("submit", (e) => { e.preventDefault(); notify(false); });
  $("#unsub").addEventListener("click", () => notify(true));
})();

// 내 몫 실적 강 (#tryEarn): 앱 EarningsRiver.swift(시안 판16)와 같은 좌표·색·동작을 SVG 로.
// 계산은 tools/earn_calc.mjs 의 view 를 그대로 가져다 쓴다(앱 Earnings.swift 와 같은 답). 자료는 data/earnings/{종목}.json,
// 원화 어림은 data/prices/KRW_X.json 마지막 값. 회사 매출(물높이·수위표 글)은 파일의 분기 매출 그대로.
(function () {
  "use strict";
  const svg = document.getElementById("earnRiver");
  if (!svg) return;
  const $ = (s) => document.querySelector(s);
  const W = 360, BED = 300, TOTAL = 50, MID = 175, END = 2.9;
  const still = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;
  const files = {};
  let calc = null, fx = null, cur = null, raf = 0, seen = false, tmr = 0;

  const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  const seg = (t, a, b) => Math.max(0, Math.min(1, (t - a) / (b - a)));
  const ease = (x) => { // cubic-bezier(.4,0,.2,1)
    if (x <= 0) return 0; if (x >= 1) return 1;
    const bz = (u, p1, p2) => 3 * (1 - u) * (1 - u) * u * p1 + 3 * (1 - u) * u * u * p2 + u * u * u;
    let lo = 0, hi = 1;
    for (let i = 0; i < 20; i++) { const m = (lo + hi) / 2; if (bz(m, 0.4, 0.2) < x) lo = m; else hi = m; }
    return bz((lo + hi) / 2, 0, 1);
  };
  const pop = (x) => { const c = 1.70158; return x <= 0 ? 0 : 1 + (c + 1) * Math.pow(x - 1, 3) + c * Math.pow(x - 1, 2); };
  const fade = (t, at) => seg(t, at, at + 0.4);
  const batchim = (s) => { const c = s.charCodeAt(s.length - 1); return c >= 0xac00 && c <= 0xd7a3 && (c - 0xac00) % 28 !== 0; };
  const eok = (v) => { const e = v / 1e8; return (e >= 10 ? Math.round(e).toLocaleString("en-US") : e.toFixed(1)) + "억"; };
  const T = (s, size, w, c, x, y, a, op = 1, serif = false) => op <= 0 ? "" :
    `<text x="${x}" y="${y}" font-size="${size}" font-weight="${w}" text-anchor="${a === "r" ? "end" : a === "c" ? "middle" : "start"}"${serif ? ' class="sf"' : ""} style="fill:var(--e-${c})${op < 1 ? ";opacity:" + op : ""}">${esc(s)}</text>`;
  // 잘린 선: pathLength 1 기준 dash 로 0~g 만 보이게
  const trim = (g) => (g >= 1 ? "" : ` pathLength="1" stroke-dasharray="${g} 2"`);

  function draw(m, t) {
    const v = m.v, qs = m.j.quarters, name = m.name, fmt = (x) => calc.money(x, v.currency);
    let o = "", defs = "";
    o += T(`${name}${batchim(name) ? "이" : "가"} 번 돈 중 내 몫`, 20, 700, "fg", 24, 42, "l", 1, true);
    // 물높이 lv(v) = 270 − (v−min)/(max−min)×210 (그 분기 앞 4분기 + 그 분기, 회사 매출)
    const i = m.at == null ? qs.length - 1 : m.at;
    const co = qs[i].revenue || 0, gco = qs.slice(Math.max(0, i - 4), i).map((x) => x.revenue || 0);
    const vals = gco.concat([co]), lo = Math.min(...vals), hi = Math.max(...vals);
    const lv = (x) => (hi > lo ? 270 - ((x - lo) / (hi - lo)) * 210 : 165), S = lv(co);
    // 물: 위→아래로 .6초. 기다리는 중이면 강바닥에서 지난 분기 높이까지 2초 차오른다
    const wet = v.pending == null;
    const top = wet ? S : BED - (BED - S) * ease(seg(t, 0, 2)), fill = wet ? ease(seg(t, 0, 0.6)) : 1;
    defs += `<clipPath id="eW"><rect x="0" y="${top}" width="${W}" height="${(BED - top) * fill}"/></clipPath>`;
    o += `<g clip-path="url(#eW)"><rect x="0" y="${top}" width="${W}" height="${BED - top}" style="fill:var(--e-water)"/><path d="M0 230C90 210 200 250 360 225L360 300L0 300Z" style="fill:var(--e-water2)"/></g>`;
    // 강 위 글: 물이 낮아 글이 물 밖이면 진한 글자로
    const base = Math.min(S, 200), c1 = base + 32 > S ? "onwater" : "fg", c2 = base + 58 > S ? "onwater" : "fg";
    if (v.pending) {
      const md = v.pending.split("-").map(Number), day = md.length === 3 ? `${md[1]}월 ${md[2]}일` : v.pending;
      o += T(`실적이 나왔어요 · ${day}`, 12, 400, c1, 24, base + 40, "l", 0.85 * fade(t, 1.8));
      o += T("숫자가 들어오면 강이 차요", 20, 700, c2, 24, base + 70, "l", fade(t, 1.8), true);
      o += gauge(v, gco, co, lv, null, t);
      return `<defs>${defs}</defs>` + o;
    }
    o += T(m.at == null ? `${name} · 이번 분기` : `${name} · ${v.label}`, 12, 400, c1, 24, base + 40, "l", 0.85 * fade(t, 0.3));
    o += T(`매출 ${eok(co)}${v.currency === "USD" ? " 달러" : " " + v.currency}`, 20, 700, c2, 24, base + 70, "l", fade(t, 0.3), true);
    o += gauge(v, gco, co, lv, S, t, m.at == null);
    // 물안개: 남는 돈(이자·세금·기타). 피었다가 실 끝 전에 걷힌다
    if (v.mist > 0 && v.revenue > 0) {
      const mm = Math.sin(Math.PI * seg(t, 0.6, 1.7)) * (still ? 0 : 1);
      if (mm > 0) {
        const r = 14 + 40 * Math.min(1, (v.mist / v.revenue) * 4);
        defs += `<filter id="eB" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="8"/></filter>`;
        o += `<g filter="url(#eB)" style="opacity:${0.55 * mm}">` + [[-50, 0], [0, -8], [52, 2]].map(([dx, dy]) =>
          `<ellipse cx="${MID + dx}" cy="${S - 16 + dy}" rx="${r}" ry="${r * 0.35}" style="fill:var(--e-water2)"/>`).join("") + "</g>";
      }
    }
    // 내 몫 (강바닥 아래)
    o += T(`내 몫 ${fmt(v.revenue || 0)}`, 12, 600, "fg", 216, 326, "l", fade(t, 0.4));
    o += T(`${m.qty.toLocaleString("ko-KR", { maximumFractionDigits: 6 })}주만큼 · 확대`, 11, 400, "muted", 216, 343, "l", fade(t, 0.4));
    // 실: 왼→오 원가·판관비·이익·연구비. 굵기 = 내 몫 매출 안 비율 × 50, 이익 실은 최소 3
    const sc = TOTAL / Math.max(v.revenue || 0, 1e-12), byK = {};
    v.branches.forEach((b) => { byK[b.k] = b.v; });
    const ss = [];
    if (byK.cogs != null) ss.push(["cogs", byK.cogs * sc]);
    if (byK.spent != null) ss.push(["spent", byK.spent * sc]);
    if (byK.sga != null) ss.push(["sga", byK.sga * sc]);
    if (!v.loss) ss.push(["net", Math.max(3, (v.net || 0) * sc)]);
    if (byK.rnd != null) ss.push(["rnd", byK.rnd * sc]);
    let x = MID - ss.reduce((a, s) => a + s[1], 0) / 2, goldX = MID;
    const hollow = v.loss ? v.short * sc : 0;
    let gi = 0;
    // 흐르는 실: strand → 끝 색(옅어짐 또는 떡잎색). 적자 마지막 실은 hollow 만큼 속 빈 테두리
    const flow = (d, w, sx, ex, ey, endC, g, h) => {
      if (g <= 0) return "";
      const tr = trim(g);
      if (h > 0) {
        const c = endC || "strand", solid = Math.max(0, w - h), id = "eH" + gi++;
        let s = "";
        if (solid > 0) s += `<path d="${d}"${tr} transform="translate(${-h / 2} 0)" style="fill:none;stroke:var(--e-${c});stroke-width:${solid}"/>`;
        defs += `<mask id="${id}" maskUnits="userSpaceOnUse" x="-20" y="0" width="400" height="680"><rect x="-20" y="0" width="400" height="680" fill="#fff"/><path d="${d}"${tr} style="fill:none;stroke:#000;stroke-width:${Math.max(0, h - 3)}"/></mask>`;
        return s + `<g transform="translate(${solid / 2} 0)"><path d="${d}"${tr} mask="url(#${id})" style="fill:none;stroke:var(--e-${c});stroke-width:${h}"/></g>`;
      }
      const id = "eG" + gi++;
      defs += `<linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="${sx}" y1="${BED}" x2="${ex}" y2="${ey}"><stop offset="0" style="stop-color:var(--e-strand)"/><stop offset="1" style="stop-color:var(--e-${endC || "strand"});stop-opacity:${endC ? 1 : 0}"/></linearGradient>`;
      return `<path d="${d}"${tr} style="fill:none;stroke:url(#${id});stroke-width:${w}"/>`;
    };
    ss.forEach(([k, w], i) => {
      const sx = x + w / 2; x += w;
      const h = i === ss.length - 1 ? hollow : 0;
      if (k === "cogs" || k === "spent") {
        o += flow(`M${sx} ${BED}L${sx} 350C${sx} 400 70 392 40 440`, w, sx, 40, 440, null, ease(seg(t, 0, 1)), h);
        o += T(k === "cogs" ? `원가 ${fmt(byK.cogs)}` : `쓴 돈 ${fmt(byK.spent)}`, 12, 400, "muted", 24, 466, "l", fade(t, 0.7));
      } else if (k === "sga") {
        o += flow(`M${sx} ${BED}L${sx} 470C${sx} 508 100 506 70 540`, w, sx, 70, 540, null, ease(seg(t, 0.4, 1.7)), h);
        o += T(`판매·관리비 ${fmt(byK.sga)}`, 12, 400, "muted", 24, 564, "l", fade(t, 1.4));
      } else if (k === "rnd") {
        o += flow(`M${sx} ${BED}L${sx} 410C${sx} 450 290 448 320 488`, w, sx, 320, 488, "sprout", ease(seg(t, 0.2, 1.4)), h);
        o += sprout(pop(seg(t, 2.2, 2.8)));
        o += T(`연구비 ${fmt(byK.rnd)}`, 12, 400, "muted", 336, 512, "r", fade(t, 1.1));
      } else { // net: 금색 실이 곧게 내려간다
        goldX = sx;
        const ly = v.lastYearNet, n = v.net;
        if (ly > 0 && n > 0) {
          const g = ease(seg(t, 1.5, 2.1));
          if (g > 0) o += `<path d="M${sx + 14} 520L${sx + 14} 600"${trim(g)} style="fill:none;stroke:var(--e-ghost);stroke-opacity:.6;stroke-width:${w * ly / n}"/>`;
          o += T(`작년 ${fmt(ly)}`, 11, 400, "muted", sx + 20, 560, "l", fade(t, 2));
        }
        const g = ease(seg(t, 0, 2));
        if (g > 0) o += `<path d="M${sx} ${BED}L${sx} 600"${trim(g)} style="fill:none;stroke:var(--e-gold);stroke-width:${w}"/>`;
      }
    });
    // 금색 끝: 큰 금액(+원화) · 설명. 적자면 옅은 손실
    if (v.loss) {
      o += T(fmt(-v.short), 30, 700, "muted", MID, 636, "c", fade(t, 1.9), true);
      o += T(m.at == null ? "이번 분기 내 몫 손실" : "그 분기 내 몫 손실", 12, 400, "muted", MID, 660, "c", fade(t, 2.1));
    } else {
      const op = fade(t, 1.9);
      if (op > 0) o += `<text x="${goldX}" y="636" text-anchor="middle" style="opacity:${op}"><tspan class="sf" font-size="30" font-weight="700" style="fill:var(--e-gold)">${esc(v.netText)}</tspan>` +
        (v.netKrw ? `<tspan font-size="13" font-weight="600" dx="8" style="fill:var(--e-muted)">${esc(v.netKrw)}</tspan>` : "") + "</text>";
      o += T(m.at == null ? "회사가 이번 분기 내 몫으로 번 순이익" : "회사가 그 분기 내 몫으로 번 순이익", 12, 400, "muted", goldX, 660, "c", fade(t, 2.1));
    }
    return `<defs>${defs}</defs>` + o;
  }

  // 수위표: 막대 x268 y62, 눈금 x261~279. S = 지금 물높이 (기다리는 중이면 null)
  function gauge(v, gco, co, lv, S, t, now = true) {
    let o = `<rect x="268" y="62" width="4" height="238" style="fill:var(--e-muted);opacity:.55"/>`;
    const tick = (y, strong) => `<rect x="261" y="${y - (strong ? 1 : 0.75)}" width="18" height="${strong ? 2 : 1.5}" style="fill:var(--e-${strong ? "fg" : "onwater"})${strong ? "" : ";opacity:.6"}"/>`;
    const others = [];
    v.gauge.forEach((g, i) => {
      const y = lv(gco[i]), val = gco[i] ? eok(gco[i]) : "–";
      o += tick(y, g.lastYear);
      if (g.lastYear) o += T(`작년 ${val}`, 11, 400, "muted", 284, y - 5, "l");
      else others.push([y + 4, val]);
    });
    others.sort((a, b) => a[0] - b[0]);
    let last = -100;
    others.forEach((a) => { if (a[0] - last < 13) a[0] = last + 13; last = a[0]; }); // 겹치면 앞보다 13 아래로
    others.forEach(([y, s]) => { o += T(s, 11, 400, "onwater", 284, y, "l", 0.75); });
    if (S != null) {
      const op = fade(t, 0.3);
      o += tick(S, false) + T(`${now ? "지금" : "이때"} ${eok(co)}`, 11, 600, "onwater", 284, S + 17, "l", op);
      if (v.revYoY) o += T(v.revYoY, 11, 600, "onwater", 284, S + 31, "l", op);
    }
    return o;
  }

  // 떡잎 (331,490): 줄기 0,0→0,−11, 잎 (±5,−13) rx5.5 ry2.6 ∓28도
  function sprout(s) {
    if (s <= 0) return "";
    return `<g transform="translate(331 490) scale(${s})" style="fill:var(--e-sprout)"><path d="M0 0L0 -11" style="fill:none;stroke:var(--e-sprout);stroke-width:1.5"/>` +
      [-1, 1].map((d) => `<ellipse transform="translate(${5 * d} -13) rotate(${-28 * d})" rx="5.5" ry="2.6"/>`).join("") + "</g>";
  }

  // 자료 없음(ETF 등): 강 없이 짧은 안내
  function none(sym) {
    cancelAnimationFrame(raf);
    svg.innerHTML = `<text x="180" y="320" text-anchor="middle" font-size="16" font-weight="700" style="fill:var(--e-fg)">${esc(sym)} 실적 자료가 없어요</text>` +
      `<text x="180" y="346" text-anchor="middle" font-size="12" class="earn-none">ETF 는 회사 실적이 없어요 · 앱에선 줄표</text>`;
    svg.setAttribute("aria-label", `${sym} 실적 자료가 없어요`);
  }

  // 한 번 흐르고(기다리는 중이면 물만 차오르고) 멈춘다. 동작 줄이기면 다 흐른 정지 화면
  function play() {
    cancelAnimationFrame(raf);
    if (!cur) return;
    const m = cur, v = m.v;
    svg.setAttribute("aria-label", v.pending ? `${m.name}. ${v.pending} 실적 발표, 숫자를 기다리는 중`
      : `${m.name} ${m.at == null ? v.months + "월" : v.label}. 내 몫 매출 ${calc.money(v.revenue || 0, v.currency)}${v.revYoY ? ", 작년보다 " + v.revYoY : ""}` +
        (v.loss ? `. ${m.at == null ? "이번" : "그"} 분기 내 몫 손실 ${calc.money(v.short, v.currency)}` : `. 내 몫 순이익 ${v.netText}${v.netKrw ? ", " + v.netKrw : ""}`));
    if (still || !seen) { svg.innerHTML = draw(m, still ? 99 : 0); return; }
    const t0 = performance.now();
    const step = (now) => {
      const el = (now - t0) / 1000;
      if (cur !== m) return;
      svg.innerHTML = draw(m, el >= END + 0.1 ? 99 : el);
      if (el < END + 0.1) raf = requestAnimationFrame(step);
    };
    raf = requestAnimationFrame(step);
  }

  async function load() {
    const sym = $("#earnSym").value.trim().toUpperCase(), qty = Number($("#earnQty").value);
    const msg = $("#earnMsg");
    if (!/^[A-Z0-9][A-Z0-9.\-]{0,11}$/.test(sym)) { cur = null; none(sym || "종목"); msg.textContent = "종목 기호를 넣어 주세요. 예: DRNK"; return; }
    if (!(qty > 0)) { msg.textContent = "수량을 넣어 주세요."; return; }
    try {
      if (!calc) calc = await import("./tools/earn_calc.mjs");
      if (!(sym in files)) {
        const r = await fetch(`data/earnings/${encodeURIComponent(sym)}.json`, { cache: "no-cache" });
        files[sym] = r.ok ? await r.json() : null;
      }
    } catch (_) { files[sym] = files[sym] || null; }
    if (sym !== $("#earnSym").value.trim().toUpperCase()) return; // 그사이 바뀜
    const j = files[sym], v = calc && j ? calc.view(j, qty, fx) : null;
    if (!v) { cur = null; none(sym); dots(); msg.textContent = "실적 자료가 있는 미국 종목만 강이 흘러요."; return; }
    // 장: 옛 분기 → 이번(null). 기다리는 중이면 마지막 발표 분기도 다 찬 강으로 한 장 더 (밀어야 보인다)
    const n = j.quarters.length, pages = [...Array(j.pending ? n : n - 1).keys(), null];
    cur = { j, v, qty, at: null, pages, pi: pages.length - 1, name: String(j.name || sym).replace(/\s*\(.*\)\s*$/, "") || sym };
    show();
  }

  // 장 넘기기: 옆으로 밀면 지난 분기 (맨 오른쪽이 이번 분기). 장에 들어올 때마다 그 분기 강이 다시 흐른다
  function go(d) {
    if (!cur) return;
    const pi = cur.pi + d;
    if (pi < 0 || pi >= cur.pages.length) return;
    const at = cur.pages[pi];
    cur = { ...cur, pi, at, v: calc.view(cur.j, cur.qty, fx, at == null ? undefined : at) };
    show();
  }
  function show() {
    const m = cur, msg = $("#earnMsg");
    msg.textContent = m.j.virtual ? "DRNK 는 가상 종목 견본이에요. 미국 종목은 신고서 숫자로." : `${m.v.label} 신고서 숫자 · 주당 값 × 내 수량`;
    dots();
    play();
  }
  function dots() {
    const el = $("#earnDots");
    if (!el) return;
    el.innerHTML = cur && cur.pages.length > 1 ? cur.pages.map((_, i) => `<i${i === cur.pi ? ' class="on"' : ""}></i>`).join("") : "";
  }
  // 손가락·마우스 끌기 (가로 40 넘게), 화살표 키
  let x0 = null, y0 = 0;
  svg.addEventListener("pointerdown", (e) => { x0 = e.clientX; y0 = e.clientY; });
  svg.addEventListener("pointermove", (e) => {
    if (x0 == null || !cur) return;
    const dx = e.clientX - x0;
    if (Math.abs(dx) > Math.abs(e.clientY - y0)) svg.style.transform = `translateX(${Math.max(-60, Math.min(60, dx / 3))}px)`;
  });
  const up = (e) => {
    if (x0 == null) return;
    const dx = e.clientX - x0, dy = e.clientY - y0;
    x0 = null; svg.style.transform = "";
    if (Math.abs(dx) > 40 && Math.abs(dx) > Math.abs(dy)) go(dx > 0 ? -1 : 1);
  };
  svg.addEventListener("pointerup", up);
  svg.addEventListener("pointercancel", () => { x0 = null; svg.style.transform = ""; });
  svg.addEventListener("keydown", (e) => {
    if (e.key === "ArrowLeft") { e.preventDefault(); go(-1); }
    else if (e.key === "ArrowRight") { e.preventDefault(); go(1); }
  });
  const later = () => { clearTimeout(tmr); tmr = setTimeout(load, 300); };

  $("#earnSym").addEventListener("input", later);
  $("#earnQty").addEventListener("input", later);
  $("#earnForm").addEventListener("submit", (e) => { e.preventDefault(); load(); });
  fetch("data/earnings/index.json", { cache: "no-cache" }).then((r) => (r.ok ? r.json() : null)).then((ix) => {
    if (ix && ix.symbols) $("#earnSyms").innerHTML = Object.keys(ix.symbols).sort().map((s) => `<option value="${esc(s)}">`).join("");
  }).catch(() => {});
  // 원화 어림: 오늘 원/달러 (없으면 원화 글 없이)
  const fxReady = fetch("data/prices/KRW_X.json", { cache: "no-cache" }).then((r) => (r.ok ? r.json() : null))
    .then((p) => { const c = p && p.close; fx = c && c.length ? c[c.length - 1] : null; }).catch(() => {});
  // 화면에 들어올 때 한 번 흐른다
  if ("IntersectionObserver" in window && !still) {
    const io = new IntersectionObserver((es) => { if (es.some((e) => e.isIntersecting)) { seen = true; io.disconnect(); play(); } }, { threshold: 0.35 });
    io.observe(svg);
  } else seen = true;
  fxReady.then(load);
})();

// 해 보기 탭: 목표일 전망 · 내 몫 실적 (화살표 키로도 옮김)
(() => {
  const tabs = [...document.querySelectorAll(".try-tabs [role=tab]")];
  if (!tabs.length) return;
  const pick = (t, focus) => {
    tabs.forEach((b) => {
      const on = b === t;
      b.setAttribute("aria-selected", on);
      b.tabIndex = on ? 0 : -1;
      document.getElementById(b.getAttribute("aria-controls")).hidden = !on;
    });
    if (focus) t.focus();
  };
  tabs.forEach((t, i) => {
    t.addEventListener("click", () => pick(t));
    t.addEventListener("keydown", (e) => {
      const d = e.key === "ArrowRight" ? 1 : e.key === "ArrowLeft" ? -1 : 0;
      if (d) { e.preventDefault(); pick(tabs[(i + d + tabs.length) % tabs.length], true); }
    });
  });
})();
