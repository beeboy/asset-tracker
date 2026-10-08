// 화면 모드(자동·라이트·다크)와 색 조합. CSS 를 그리기 전에 <head> 에서 바로 적용해 깜빡임이 없게 한다.
// 이 기기에만 기억 (localStorage "naeilo-theme" = {mode, pal})
(function () {
  var KEY = "naeilo-theme", r = document.documentElement, mq = window.matchMedia ? matchMedia("(prefers-color-scheme: dark)") : null, t = {};
  try { t = JSON.parse(localStorage.getItem(KEY) || "{}") || {}; } catch (e) { t = {}; }
  function meta() { var c = getComputedStyle(r).getPropertyValue("--card").trim(); if (c) document.querySelectorAll('meta[name="theme-color"]').forEach(function (m) { m.setAttribute("content", c); }); }
  function apply() {
    r.setAttribute("data-mode", t.mode === "dark" || (t.mode !== "light" && mq && mq.matches) ? "dark" : "light");
    if (t.pal && t.pal !== "default") r.setAttribute("data-pal", t.pal); else r.removeAttribute("data-pal");
    setTimeout(meta, 0);
  }
  apply();
  if (mq && mq.addEventListener) mq.addEventListener("change", apply);
  window.addEventListener("load", meta);
  var PALS = [["default", "기본", "#2f6fed", "#6c9cff"], ["ocean", "바다", "#0e7490", "#38bdf8"], ["forest", "숲", "#2f855a", "#68d391"], ["sunset", "노을", "#c2410c", "#fb923c"], ["mono", "모노", "#374151", "#d1d5db"], ["paper", "종이", "#9a3412", "#f59e0b"]];
  // 설정 화면의 고르기 칸을 그린다
  window.themeUI = function (box) {
    if (!box) return;
    var mode = t.mode || "auto", pal = t.pal || "default", dark = r.getAttribute("data-mode") === "dark";
    box.innerHTML = '<p class="small" style="margin:0 0 6px">모드</p><div class="row seg" data-th="mode">'
      + [["auto", "브라우저 설정 따르기"], ["light", "라이트"], ["dark", "다크"]].map(function (m) { return '<button data-v="' + m[0] + '"' + (m[0] === mode ? ' class="on"' : "") + ">" + m[1] + "</button>"; }).join("")
      + '</div><p class="small" style="margin:10px 0 6px">색 조합</p><div class="palrow" data-th="pal">'
      + PALS.map(function (p) { return '<button class="palbtn' + (p[0] === pal ? " on" : "") + '" data-v="' + p[0] + '"><i style="background:' + (dark ? p[3] : p[2]) + '"></i>' + p[1] + "</button>"; }).join("") + "</div>";
    box.onclick = function (e) {
      var b = e.target.closest("button[data-v]"); if (!b) return; var k = b.parentElement.getAttribute("data-th");
      if (k === "mode") t.mode = b.getAttribute("data-v") === "auto" ? undefined : b.getAttribute("data-v"); else t.pal = b.getAttribute("data-v");
      try { localStorage.setItem(KEY, JSON.stringify(t)); } catch (e2) { /* 무시 */ }
      apply(); window.themeUI(box);
    };
  };
})();
