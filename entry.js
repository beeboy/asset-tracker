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
