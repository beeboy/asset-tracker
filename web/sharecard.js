// 1000칸 공유 카드 (앱 ShareCard.swift 와 같은 모양): 기본은 금액을 숨기고 칸 수와 %만, 종목 이름은 넣지 않는다.
// 공유 이미지는 화면 모드와 상관없이 같은 어두운 카드.
(function () {
  "use strict";
  const F = "-apple-system, 'Apple SD Gothic Neo', 'Malgun Gothic', sans-serif";
  const C = { bg: "#0e161e", card: "#15202B", fg: "#ffffff", sub: "#C9D0D6", mint: "#5FD0C4", market: "#A8E8E1", slate: "#3A4651", yellow: "#F2C14E" };
  const nf = (v) => Number(v).toLocaleString("ko-KR");

  // o: { filled, mine, market, hide, kicker, amountLine, date }
  function draw(cv, o) {
    const W = 1080, H = 1350, x = cv.getContext("2d");
    if (cv.width !== W || cv.height !== H) { cv.width = W; cv.height = H; }
    const n = Math.max(0, Math.min(1000, o.filled | 0)), mine = Math.max(0, Math.min(n, o.mine | 0)), mkt = Math.max(0, Math.min(n - mine, o.market | 0));
    x.textAlign = "left"; x.textBaseline = "alphabetic"; x.setLineDash([]);
    x.fillStyle = C.bg; x.fillRect(0, 0, W, H);
    x.fillStyle = C.card; x.beginPath(); x.roundRect(54, 54, W - 108, H - 108, 56); x.fill();
    const L = 110, R = W - 110;
    x.fillStyle = C.fg; x.font = `800 50px ${F}`; x.fillText("naeilo", L, 168);
    x.fillStyle = C.sub; x.font = `400 34px ${F}`; x.textAlign = "right"; x.fillText(o.date || "", R, 166); x.textAlign = "left";
    x.fillStyle = C.mint; x.font = `700 38px ${F}`; x.fillText(o.kicker || "목표까지 가는 중", L, 250);
    x.fillStyle = C.fg; x.font = `800 112px ${F}`; x.fillText(nf(n), L - 4, 368);
    const nw = x.measureText(nf(n)).width;
    x.fillStyle = C.sub; x.font = `400 40px ${F}`; x.fillText("/ 1,000칸", L + nw + 14, 366);
    // 40×25 칸, 아래 줄부터 채움. 오른쪽 위 마지막 칸 = 목표 (노란 점)
    const gw = R - L, s = gw / 40, gy = 410;
    for (let i = 0; i < 1000; i++) {
      x.fillStyle = i < mine ? C.mint : i < mine + mkt ? C.market : C.slate;
      x.fillRect(L + (i % 40) * s + 1.5, gy + (24 - Math.floor(i / 40)) * s + 1.5, s - 3, s - 3);
    }
    x.fillStyle = C.slate; x.fillRect(L + 39 * s + 1.5, gy + 1.5, s - 3, s - 3);
    x.fillStyle = C.yellow; x.beginPath(); x.arc(L + 39.5 * s, gy + 0.5 * s, s / 2 - 4, 0, 7); x.fill();
    let y = gy + 25 * s + 60;
    // 범례: 시장이 준 칸을 나눌 수 없을 때(평단 없음)는 한 줄만
    const leg = mkt > 0 || o.split ? [[C.mint, `내가 넣은 돈 ${nf(mine)}칸`], [C.market, `시장이 준 칸 ${nf(mkt)}칸`]] : [[C.mint, `채운 칸 ${nf(n)}칸`]];
    leg.push([C.yellow, "마지막 칸 = 목표"]);
    x.font = `500 30px ${F}`; let lx = L;
    leg.forEach(([c, t], i) => { if (lx > L && lx + 36 + x.measureText(t).width > R) { lx = L; y += 50; } x.fillStyle = c; if (i === leg.length - 1) { x.beginPath(); x.arc(lx + 12, y - 10, 12, 0, 7); x.fill(); } else x.fillRect(lx, y - 22, 24, 24); x.fillStyle = C.sub; x.fillText(t, lx + 36, y); lx += 36 + x.measureText(t).width + 34; });
    y += 86;
    const line = o.hide || !o.amountLine ? `${(n / 10).toFixed(1)}% 채웠어요.` + (n >= 100 && n < 1000 ? ` ${Math.floor(n / 100) * 100}칸 넘었어요!` : n >= 1000 ? " 다 채웠어요!" : "") : o.amountLine;
    let fs = 46; x.font = `700 ${fs}px ${F}`; while (fs > 28 && x.measureText(line).width > R - L) x.font = `700 ${(fs -= 2)}px ${F}`;
    x.fillStyle = C.fg; x.fillText(line, L, y);
    y += 70;
    x.fillStyle = C.sub; x.font = `400 34px ${F}`; x.fillText(n >= 1000 ? "1,000칸, 다 채웠어요." : `다음 100칸까지 ${100 - (n % 100)}칸.`, L, y);
    x.fillStyle = C.sub; x.font = `400 30px ${F}`; x.textAlign = "center"; x.fillText("See Tomorrow, Today. · naeilo.com", W / 2, H - 112); x.textAlign = "left";
  }

  window.NaeiloShareCard = { draw };
})();
