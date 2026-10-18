// 입구 페이지 (naeilo.com/): 직접 해보기(목표일 전망) + 출시 알림 신청
// 직접 해보기는 종목 이름 없는 예시 자산이다. 해마다 평균 7% 오르고 18% 흔들린다고 두고(로그정규),
// 앱 목표일 전망처럼 가운데 금액, 10번 중 5번·9번 범위, 목표에 닿을 확률을 보여 준다. 시세는 받지 않는다.
(function () {
  "use strict";
  const $ = (s) => document.querySelector(s);
  const V0 = 1e7, GOAL = 1.5e7, MU = 0.07, SIG = 0.18, MAX_M = 60; // 예시 자산 1,000만 원, 목표 1,500만 원
  const Z5 = 0.6745, Z9 = 1.6449; // 10번 중 5번(25~75%), 9번(5~95%)

  const man = (v) => {
    const m = Math.round(v / 1e4), eok = Math.floor(m / 1e4), r = m % 1e4;
    return eok ? `${eok}억${r ? " " + r.toLocaleString("ko-KR") + "만" : ""}` : m.toLocaleString("ko-KR") + "만";
  };
  // 표준정규 누적 (Abramowitz-Stegun 7.1.26)
  const Phi = (x) => {
    const t = 1 / (1 + 0.3275911 * Math.abs(x) / Math.SQRT2);
    const e = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x / 2);
    return x >= 0 ? (1 + e) / 2 : (1 - e) / 2;
  };
  const q = (y, z) => V0 * Math.exp((MU - SIG * SIG / 2) * y + z * SIG * Math.sqrt(y)); // y년 뒤 분위 금액
  const addMonths = (d, m) => new Date(d.getFullYear(), d.getMonth() + m, 1);
  const ym = (d) => `${d.getFullYear()}년 ${d.getMonth() + 1}월`;

  // 차트: 가로는 늘 5년, 고른 목표일까지만 부채꼴을 그린다 (멀수록 넓어지는 게 보이게)
  const L = 8, R = 292, T = 8, B = 196;
  const yMax = q(MAX_M / 12, Z9) * 1.03, yMin = q(MAX_M / 12, -Z9) * 0.85; // 세로는 5년 뒤 범위에 맞춘다
  const X = (m) => L + (R - L) * (m / MAX_M);
  const Y = (v) => B - (B - T) * ((v - yMin) / (yMax - yMin));
  function band(m, zLo, zHi) {
    const up = [], dn = [];
    for (let i = 0; i <= m; i++) { const y = i / 12; up.push(`${X(i).toFixed(1)},${Y(q(y, zHi)).toFixed(1)}`); dn.unshift(`${X(i).toFixed(1)},${Y(q(y, zLo)).toFixed(1)}`); }
    return up.concat(dn).join(" ");
  }
  function line(m, z) { const p = []; for (let i = 0; i <= m; i++) p.push(`${X(i).toFixed(1)},${Y(q(i / 12, z)).toFixed(1)}`); return p.join(" "); }

  function draw() {
    const m = Number($("#fcMonths").value), y = m / 12, when = addMonths(new Date(), m);
    const mid = q(y, 0), lo = q(y, -Z9), hi = q(y, Z9);
    const pGoal = 1 - Phi((Math.log(GOAL / V0) - (MU - SIG * SIG / 2) * y) / (SIG * Math.sqrt(y)));
    const chg = mid / V0 - 1;
    $("#fcWhen").textContent = ym(when);
    $("#fcDate").textContent = `${ym(when)} · ${m % 12 ? m + "개월" : y + "년"} 뒤`;
    $("#fcMid").textContent = man(mid) + " 원";
    $("#fcChg").textContent = `지금보다 ${chg >= 0 ? "+" : ""}${Math.round(chg * 100)}%`;
    $("#fcRng").textContent = `10번 중 9번은 ${man(lo)} ~ ${man(hi)} 사이`;
    $("#fcGoal").innerHTML = `목표 1,500만 원에 닿을 확률 <b>${Math.round(pGoal * 100)}%</b>`;
    const gy = Y(GOAL), sx = X(m), yr = [12, 24, 36, 48, 60];
    $("#fcChart").innerHTML = `
      <line class="axis" x1="${L}" y1="${B}" x2="${R}" y2="${B}"/>
      ${yr.map((k) => `<text x="${X(k)}" y="${B + 13}" text-anchor="${k === 60 ? "end" : "middle"}">${k / 12}년</text>`).join("")}
      <text x="${L}" y="${B + 13}">지금</text>
      <polygon class="b9" points="${band(m, -Z9, Z9)}"/>
      <polygon class="b5" points="${band(m, -Z5, Z5)}"/>
      <line class="goal" x1="${L}" y1="${gy}" x2="${R}" y2="${gy}"/>
      <text class="g" x="${L + 2}" y="${gy - 4}">목표</text>
      <polyline class="mid" points="${line(m, 0)}"/>
      <line class="sel" x1="${sx}" y1="${T}" x2="${sx}" y2="${B}"/>
      <circle cx="${L}" cy="${Y(V0)}" r="3"/>
      <circle cx="${sx}" cy="${Y(mid)}" r="3"/>
      <text class="s" x="${L + 4}" y="${Y(V0) + 13}">지금 1,000만</text>`;
  }

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
  $("#fcMonths").addEventListener("input", draw);
  draw();
  $("#notify").addEventListener("submit", (e) => { e.preventDefault(); notify(false); });
  $("#unsub").addEventListener("click", () => notify(true));
})();
