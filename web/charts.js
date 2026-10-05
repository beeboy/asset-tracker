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
  //       hlines: [{y, label, color}], vlines: [{x, label}], yfmt, height, log, markers: [{x, label, color}]}
  function lineChart(host, opt) {
    host.innerHTML = "";
    const W = Math.max(220, host.clientWidth || 600), H = opt.height || 280;
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
    yt.forEach((v) => {
      if (v <= 0 && log) return;
      const yy = Y(v); if (yy < m.t - 1 || yy > H - m.b + 1) return;
      el("line", { x1: m.l, x2: W - m.r, y1: yy, y2: yy, class: "grid" }, g);
      if (narrow) el("text", { x: m.l + 2, y: yy - 3, "text-anchor": "start", class: "inlab" }, g).textContent = yfmt(v);
      else el("text", { x: m.l - 6, y: yy + 4, "text-anchor": "end" }, g).textContent = yfmt(v);
    });
    const spanDays = (x1 - x0) / 86400e3, xt = [];
    const d = new Date(x0); d.setUTCDate(1);
    const stepM = spanDays > 1500 ? 12 : spanDays > 700 ? 6 : spanDays > 300 ? 3 : spanDays > 90 ? 1 : 0;
    if (stepM) { d.setUTCMonth(Math.ceil(d.getUTCMonth() / stepM) * stepM); for (; d.getTime() <= x1; d.setUTCMonth(d.getUTCMonth() + stepM)) if (d.getTime() >= x0) xt.push(d.getTime()); }
    else { for (let t = x0; t <= x1; t += Math.max(1, Math.round(spanDays / 6)) * 86400e3) xt.push(t); }
    xt.forEach((t) => {
      const dd = new Date(t), lab = stepM >= 12 ? `${dd.getUTCFullYear()}` : stepM ? `${String(dd.getUTCFullYear()).slice(2)}.${dd.getUTCMonth() + 1}` : `${dd.getUTCMonth() + 1}/${dd.getUTCDate()}`;
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
      el("path", { d: dstr, fill: "none", stroke: s.color, "stroke-width": s.width || 1.6, "stroke-dasharray": s.dash || "", "stroke-linejoin": "round" }, svg);
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
      const rows = [`<b>${day}</b>`];
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

  window.Charts = { lineChart };
})();
