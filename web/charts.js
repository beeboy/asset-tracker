// 작은 SVG 차트 도구 (외부 라이브러리 없이 오프라인 동작)
(function () {
  "use strict";
  const NS = "http://www.w3.org/2000/svg";
  const el = (tag, attrs = {}, parent) => {
    const e = document.createElementNS(NS, tag);
    for (const k in attrs) e.setAttribute(k, attrs[k]);
    if (parent) parent.appendChild(e);
    return e;
  };
  const toT = (s) => Date.parse(s + "T00:00:00Z");

  function niceTicks(lo, hi, n) {
    const span = hi - lo || Math.abs(hi) || 1, step0 = span / n, mag = 10 ** Math.floor(Math.log10(step0));
    const step = [1, 2, 2.5, 5, 10].map((m) => m * mag).find((s) => span / s <= n) || 10 * mag;
    const out = []; for (let v = Math.ceil(lo / step) * step; v <= hi + 1e-9; v += step) out.push(+v.toFixed(10));
    return out;
  }

  // opt: {x: [날짜], series: [{name, y, color, width, dash, x?}], bands: [{lo, hi, color, opacity, x?}],
  //       hlines: [{y, label, color}], vlines: [{x, label}], yfmt, height, log, markers: [{x, label, color}], xlab(Date), tipx(day)}
  // 상자 폭이 바뀌면(브라우저 확대·축소, 창 크기, 숨었다 보임) 같은 옵션으로 다시 그린다
  const ro = window.ResizeObserver ? new ResizeObserver((es) => es.forEach((e) => {
    const h = e.target, w = h.clientWidth; if (!h._opt || !w || Math.abs(w - h._w) <= 2) return;
    (h._bar ? barChart : lineChart)(h, h._opt);
  })) : null;
  function lineChart(host, opt) {
    host.innerHTML = "";
    const W = Math.max(220, host.clientWidth || 600), H = opt.height || 280;
    host._opt = opt; host._w = host.clientWidth; if (ro && !host._ro) { host._ro = true; ro.observe(host); }
    // 좁은 화면(휴대폰 세로)은 y축 글자를 그래프 안쪽 왼쪽에 얹어 좌우 여백을 문단 여백에 맞춘다
    const narrow = W < 480, m = { l: narrow ? 1 : 64, r: narrow ? 2 : 20, t: narrow ? 14 : 12, b: 28 };
    const svg = el("svg", { width: W, height: H, viewBox: `0 0 ${W} ${H}`, class: "chart" }, host);
    const xs = [], ys = [];
    const collect = (x, y) => { for (let i = 0; i < y.length; i++) if (y[i] != null && isFinite(y[i])) { xs.push(toT(x[i])); ys.push(y[i]); } };
    (opt.series || []).forEach((s) => collect(s.x || opt.x, s.y));
    (opt.bands || []).forEach((b) => { collect(b.x || opt.x, b.lo); collect(b.x || opt.x, b.hi); });
    (opt.hlines || []).forEach((h) => opt.hlinesInRange !== false && ys.push(h.y));
    if (!xs.length) { el("text", { x: 10, y: 20, class: "muted" }, svg).textContent = "표시할 데이터가 없습니다"; return; }
    const log = !!opt.log && ys.every((v) => v > 0);
    const f = log ? Math.log : (v) => v;
    let x0 = Math.min(...xs), x1 = Math.max(...xs), y0 = Math.min(...ys.map(f)), y1 = Math.max(...ys.map(f));
    if (opt.ymin != null) y0 = Math.min(y0, f(opt.ymin));
    const pad = (y1 - y0) * 0.06 || 1; y0 -= pad; y1 += pad;
    if (x1 === x0) x1 = x0 + 86400e3;
    const X = (t) => m.l + ((t - x0) / (x1 - x0)) * (W - m.l - m.r);
    const Y = (v) => m.t + (1 - (f(v) - y0) / (y1 - y0)) * (H - m.t - m.b);
    const yfmt = opt.yfmt || ((v) => v.toLocaleString());

    // 축
    const g = el("g", { class: "axis" }, svg);
    const yt = log ? niceTicks(Math.exp(y0), Math.exp(y1), 5) : niceTicks(y0, y1, 5);
    // axisOut: 좁은 화면에서도 y축 글자를 그래프 밖 왼쪽에 둔다 (선이 글자를 덮지 않게). 글자 폭만큼만 여백
    const out = narrow && opt.axisOut;
    // 한글(억·만)은 글자 폭이 넓어 따로 센다. 넓은 화면도 글자가 길면 여백을 넓혀 선이 글자를 덮지 않게
    const lw = Math.max(...yt.map((v) => [...String(yfmt(v))].reduce((a, c) => a + (/[\u3131-\uD79D]/.test(c) ? 12 : 7.6), 0))) + 10;
    if (out) m.l = lw; else if (!narrow) m.l = Math.max(m.l, lw);
    yt.forEach((v) => {
      if (v <= 0 && log) return;
      const yy = Y(v); if (yy < m.t - 1 || yy > H - m.b + 1) return;
      el("line", { x1: m.l, x2: W - m.r, y1: yy, y2: yy, class: "grid" }, g);
      if (narrow && !out) el("text", { x: m.l + 2, y: yy - 3, "text-anchor": "start", class: "inlab" }, g).textContent = yfmt(v);
      else el("text", { x: m.l - 6, y: yy + 4, "text-anchor": "end" }, g).textContent = yfmt(v);
    });
    const spanDays = (x1 - x0) / 86400e3, xt = [];
    const d = new Date(x0); d.setUTCDate(1);
    // 긴 기간(목표 10년 등)은 글자가 겹치지 않게 몇 년 간격으로
    const yStep = Math.max(1, Math.ceil(spanDays / 365.25 / Math.max(3, Math.floor((W - m.l - m.r) / 42))));
    const stepM = spanDays > 1500 ? 12 * yStep : spanDays > 700 ? 6 : spanDays > 300 ? 3 : spanDays > 90 ? 1 : 0;
    if (stepM >= 12) { d.setUTCMonth(0); d.setUTCFullYear(Math.ceil(d.getUTCFullYear() / yStep) * yStep); for (; d.getTime() <= x1; d.setUTCFullYear(d.getUTCFullYear() + yStep)) if (d.getTime() >= x0) xt.push(d.getTime()); }
    else if (stepM) { d.setUTCMonth(Math.ceil(d.getUTCMonth() / stepM) * stepM); for (; d.getTime() <= x1; d.setUTCMonth(d.getUTCMonth() + stepM)) if (d.getTime() >= x0) xt.push(d.getTime()); }
    else { for (let t = x0; t <= x1; t += Math.max(1, Math.round(spanDays / 6)) * 86400e3) xt.push(t); }
    xt.forEach((t) => {
      const dd = new Date(t), lab = opt.xlab ? opt.xlab(dd) : stepM >= 12 ? `${dd.getUTCFullYear()}` : stepM ? `${String(dd.getUTCFullYear()).slice(2)}.${dd.getUTCMonth() + 1}` : `${dd.getUTCMonth() + 1}/${dd.getUTCDate()}`;
      const xx = X(t), edge = narrow && xx < 14 ? "start" : narrow && xx > W - 14 ? "end" : "middle";
      el("text", { x: xx, y: H - 8, "text-anchor": edge }, g).textContent = lab;
    });

    // 밴드
    (opt.bands || []).forEach((b) => {
      const x = b.x || opt.x, pts = [];
      for (let i = 0; i < x.length; i++) if (b.hi[i] != null) pts.push(`${X(toT(x[i]))},${Y(b.hi[i])}`);
      for (let i = x.length - 1; i >= 0; i--) if (b.lo[i] != null) pts.push(`${X(toT(x[i]))},${Y(b.lo[i])}`);
      el("polygon", { points: pts.join(" "), fill: b.color, "fill-opacity": b.opacity ?? 0.2, stroke: "none" }, svg);
    });
    (opt.hlines || []).forEach((h) => {
      const yy = Y(h.y); if (yy < m.t || yy > H - m.b) return;
      el("line", { x1: m.l, x2: W - m.r, y1: yy, y2: yy, stroke: h.color || "var(--accent2)", "stroke-dasharray": "5 4", "stroke-width": 1.2 }, svg);
      if (h.label) el("text", { x: W - m.r - 4, y: yy - 4, "text-anchor": "end", class: "hlabel", fill: h.color || "var(--accent2)" }, svg).textContent = h.label;
    });
    (opt.vlines || []).forEach((v) => {
      const xx = X(toT(v.x)); if (xx < m.l || xx > W - m.r) return;
      el("line", { x1: xx, x2: xx, y1: m.t, y2: H - m.b, class: "vline" }, svg);
      if (v.label) el("text", { x: xx + 4, y: m.t + 10, class: "hlabel" }, svg).textContent = v.label;
    });
    // 선
    (opt.series || []).forEach((s) => {
      const x = s.x || opt.x; let dstr = "", pen = false;
      for (let i = 0; i < s.y.length; i++) {
        if (s.y[i] == null || !isFinite(s.y[i])) { pen = false; continue; }
        dstr += `${pen ? "L" : "M"}${X(toT(x[i])).toFixed(1)},${Y(s.y[i]).toFixed(1)}`; pen = true;
      }
      el("path", { d: dstr, fill: "none", stroke: s.color, "stroke-width": s.width || 1.6, "stroke-dasharray": s.dash || "", "stroke-opacity": s.opacity ?? 1, "stroke-linejoin": "round" }, svg);
      if (s.lastDot) { // 마지막 점을 속이 빈 동그라미로 (오늘 실시간 값처럼 아직 확정 안 된 값)
        let i = s.y.length - 1; while (i >= 0 && (s.y[i] == null || !isFinite(s.y[i]))) i--;
        if (i >= 0 && x[i] === s.lastDot) el("circle", { cx: X(toT(x[i])), cy: Y(s.y[i]), r: 3.6, fill: "var(--card)", stroke: s.color, "stroke-width": 1.8 }, svg);
      }
    });
    (opt.markers || []).forEach((mk) => {
      const xx = X(toT(mk.x)); if (xx < m.l || xx > W - m.r) return;
      el("path", { d: `M${xx - 5},${H - m.b} L${xx + 5},${H - m.b} L${xx},${H - m.b - 8}Z`, fill: mk.color || "var(--warn)" }, svg)
        .appendChild(el("title")).textContent = mk.label;
    });

    // 안쪽 y축 글자는 선 위에 보이도록 맨 위로
    if (narrow) { const top = el("g", { class: "axis" }, svg); g.querySelectorAll("text.inlab").forEach((t) => top.appendChild(t)); }

    // 마우스 위치 값 보기
    const tip = document.createElement("div"); tip.className = "tip"; host.appendChild(tip);
    const cross = el("line", { y1: m.t, y2: H - m.b, class: "cross", visibility: "hidden" }, svg);
    const allX = [...new Set([...(opt.series || []).flatMap((s) => s.x || opt.x), ...(opt.bands || []).flatMap((b) => b.x || opt.x)])].sort();
    const allT = allX.map(toT);
    svg.addEventListener("mousemove", (ev) => {
      const r = svg.getBoundingClientRect(), px = ev.clientX - r.left;
      const t = x0 + ((px - m.l) / (W - m.l - m.r)) * (x1 - x0);
      let lo = 0, hi = allT.length - 1;
      while (lo < hi) { const mid = (lo + hi) >> 1; if (allT[mid] < t) lo = mid + 1; else hi = mid; }
      if (lo > 0 && Math.abs(allT[lo - 1] - t) < Math.abs(allT[lo] - t)) lo--;
      const day = allX[lo]; if (!day) return;
      const rows = [`<b>${opt.tipx ? opt.tipx(day) : day}</b>`];
      const pick = (x, y) => { const i = x.indexOf(day); return i >= 0 ? y[i] : null; };
      (opt.series || []).forEach((s) => { const v = pick(s.x || opt.x, s.y); if (v != null && s.name) rows.push(`<span style="color:${s.color}">●</span> ${s.name}: ${yfmt(v)}`); });
      (opt.bands || []).forEach((b) => { if (!b.name) return; const lo2 = pick(b.x || opt.x, b.lo), hi2 = pick(b.x || opt.x, b.hi); if (lo2 != null) rows.push(`${b.name}: ${yfmt(lo2)} ~ ${yfmt(hi2)}`); });
      tip.innerHTML = rows.join("<br>");
      const xx = X(toT(day));
      cross.setAttribute("x1", xx); cross.setAttribute("x2", xx); cross.setAttribute("visibility", "visible");
      tip.style.display = "block";
      tip.style.left = Math.min(xx + 12, W - tip.offsetWidth - 4) + "px";
      tip.style.top = "8px";
    });
    svg.addEventListener("mouseleave", () => { tip.style.display = "none"; cross.setAttribute("visibility", "hidden"); });

    if (opt.legend !== false) {
      const lg = document.createElement("div"); lg.className = "legend";
      lg.innerHTML = [...(opt.series || []).filter((s) => s.name).map((s) => `<span><i style="background:${s.color}"></i>${s.name}</span>`),
        ...(opt.bands || []).filter((b) => b.name).map((b) => `<span><i style="background:${b.color};opacity:${(b.opacity ?? 0.2) + 0.2}"></i>${b.name}</span>`)].join("");
      host.appendChild(lg);
    }
  }

  // 0을 가운데 두는 +/− 막대. 날짜는 같은 간격으로 놓는다 (주말·휴일 빈칸 없이)
  // opt: {x: [날짜], y: [값], yfmt, height, xlab(day), tipx(day), labelLast, dots: [{x, label}], pos, neg}
  function barChart(host, opt) {
    host.innerHTML = "";
    const W = Math.max(220, host.clientWidth || 600), H = opt.height || 180;
    host._opt = opt; host._w = host.clientWidth; host._bar = true;
    if (ro && !host._ro) { host._ro = true; ro.observe(host); }
    const x = opt.x, y = opt.y, n = x.length, yfmt = opt.yfmt || ((v) => v.toLocaleString());
    const svg = el("svg", { width: W, height: H, viewBox: `0 0 ${W} ${H}`, class: "chart" }, host);
    if (!n) { el("text", { x: 10, y: 20, class: "muted" }, svg).textContent = "표시할 데이터가 없습니다"; return; }
    const narrow = W < 480, m = { l: 0, r: narrow ? 2 : 12, t: 18, b: 30 };
    let lo = Math.min(0, ...y), hi = Math.max(0, ...y); if (lo === hi) { hi = 1; lo = -1; }
    const pad = (hi - lo) * 0.12; hi += pad; lo -= pad;
    const yt = niceTicks(lo, hi, 4);
    const lw = Math.max(...yt.map((v) => [...String(yfmt(v))].reduce((a, c) => a + (/[\u3131-\uD79D]/.test(c) ? 12 : 7.6), 0))) + 10;
    m.l = lw;
    const Y = (v) => m.t + (1 - (v - lo) / (hi - lo)) * (H - m.t - m.b);
    const slot = (W - m.l - m.r) / n, bw = Math.max(2, Math.min(slot * 0.72, 46)), X = (i) => m.l + slot * (i + 0.5);
    const g = el("g", { class: "axis" }, svg);
    yt.forEach((v) => {
      const yy = Y(v); if (yy < m.t - 1 || yy > H - m.b + 1) return;
      el("line", { x1: m.l, x2: W - m.r, y1: yy, y2: yy, class: "grid" }, g);
      el("text", { x: m.l - 6, y: yy + 4, "text-anchor": "end" }, g).textContent = yfmt(v);
    });
    el("line", { x1: m.l, x2: W - m.r, y1: Y(0), y2: Y(0), stroke: "var(--muted)", "stroke-width": 1 }, svg);
    // 아래 날짜 글자: 겹치지 않게 몇 칸 걸러서, 마지막 칸은 항상
    const every = Math.max(1, Math.ceil(n / Math.max(2, Math.floor((W - m.l - m.r) / 44))));
    x.forEach((d, i) => {
      if ((n - 1 - i) % every) return;
      el("text", { x: X(i), y: H - 10, "text-anchor": narrow && i === n - 1 ? "end" : "middle" }, g).textContent = opt.xlab ? opt.xlab(d) : d.slice(5).replace("-", "/");
    });
    const bars = y.map((v, i) => {
      const y0 = Y(0), y1 = Y(v), live = opt.live && x[i] === opt.live;
      return el("rect", { x: X(i) - bw / 2, y: Math.min(y0, y1), width: bw, height: Math.max(1, Math.abs(y1 - y0)), rx: Math.min(3, bw / 4),
        fill: v >= 0 ? opt.pos || "var(--up)" : opt.neg || "var(--dn)", "fill-opacity": live ? 0.45 : 0.85,
        stroke: live ? (v >= 0 ? opt.pos || "var(--up)" : opt.neg || "var(--dn)") : "none", "stroke-dasharray": live ? "3 2" : "" }, svg);
    });
    if (opt.labelLast) {
      const i = n - 1, v = y[i], yy = Y(v) + (v >= 0 ? -5 : 13);
      el("text", { x: Math.min(X(i), W - m.r - 2), y: yy, "text-anchor": narrow || X(i) > W - 60 ? "end" : "middle", class: "hlabel", fill: v >= 0 ? opt.pos || "var(--up)" : opt.neg || "var(--dn)" }, svg).textContent = opt.labelLast;
    }
    (opt.dots || []).forEach((dt) => {
      const i = x.indexOf(dt.x); if (i < 0) return;
      el("circle", { cx: X(i), cy: H - m.b + 5, r: 3, fill: "var(--warn)" }, svg).appendChild(el("title")).textContent = dt.label;
    });
    const tip = document.createElement("div"); tip.className = "tip"; host.appendChild(tip);
    svg.addEventListener("mousemove", (ev) => {
      const r = svg.getBoundingClientRect(), i = Math.max(0, Math.min(n - 1, Math.floor((ev.clientX - r.left - m.l) / slot)));
      bars.forEach((b, j) => b.setAttribute("opacity", j === i ? 1 : 0.6));
      tip.innerHTML = `<b>${opt.tipx ? opt.tipx(x[i]) : x[i]}</b><br>${opt.tipy ? opt.tipy(i) : yfmt(y[i])}`;
      tip.style.display = "block"; tip.style.left = Math.max(4, Math.min(X(i) + 12, W - tip.offsetWidth - 4)) + "px"; tip.style.top = "8px";
    });
    svg.addEventListener("mouseleave", () => { tip.style.display = "none"; bars.forEach((b) => b.setAttribute("opacity", 1)); });
  }

  window.Charts = { lineChart, barChart };
})();
