/* ==============================================================
   LOCKIN — website behavior
   3D tilt, scroll reveal, theme, nav. No dependencies, no CDN.
   ============================================================== */
(function () {
  "use strict";

  var reduced = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  /* ---------- theme: system default, manual override, persisted ---------- */
  var root = document.documentElement;
  var saved = null;
  try { saved = localStorage.getItem("lockin-theme"); } catch (e) {}
  if (saved === "dark" || saved === "light") {
    root.setAttribute("data-theme", saved);
  } else if (window.matchMedia("(prefers-color-scheme: dark)").matches) {
    root.setAttribute("data-theme", "dark");
  }

  var toggle = document.getElementById("themeToggle");
  if (toggle) {
    toggle.addEventListener("click", function () {
      var next = root.getAttribute("data-theme") === "dark" ? "light" : "dark";
      root.setAttribute("data-theme", next);
      try { localStorage.setItem("lockin-theme", next); } catch (e) {}
    });
  }

  /* ---------- mobile nav ---------- */
  var burger = document.getElementById("navBurger");
  var nav = document.getElementById("siteNav");
  if (burger && nav) {
    burger.addEventListener("click", function () {
      var open = nav.classList.toggle("open");
      burger.setAttribute("aria-expanded", open ? "true" : "false");
    });
    nav.addEventListener("click", function (e) {
      if (e.target.tagName === "A") {
        nav.classList.remove("open");
        burger.setAttribute("aria-expanded", "false");
      }
    });
  }

  /* ---------- scroll reveal ---------- */
  var reveals = document.querySelectorAll(".reveal");
  if (reduced || !("IntersectionObserver" in window)) {
    reveals.forEach(function (el) { el.classList.add("in"); });
  } else {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (en.isIntersecting) {
          en.target.classList.add("in");
          io.unobserve(en.target);
        }
      });
    }, { threshold: 0.12, rootMargin: "0px 0px -40px 0px" });
    reveals.forEach(function (el) { io.observe(el); });
  }

  /* ---------- 3D tilt (pointer-driven, subtle) ---------- */
  if (!reduced && window.matchMedia("(pointer: fine)").matches) {
    document.querySelectorAll("[data-tilt]").forEach(function (scene) {
      var max = scene.classList.contains("tilt-scene") ? 7 : 5;
      scene.addEventListener("mousemove", function (e) {
        var r = scene.getBoundingClientRect();
        var px = (e.clientX - r.left) / r.width - 0.5;
        var py = (e.clientY - r.top) / r.height - 0.5;
        scene.style.transform =
          "rotateY(" + (px * max).toFixed(2) + "deg)" +
          " rotateX(" + (-py * max).toFixed(2) + "deg)";
        scene.style.transition = "transform .08s linear";
      });
      scene.addEventListener("mouseleave", function () {
        scene.style.transition = "transform .4s ease";
        scene.style.transform = "rotateY(0deg) rotateX(0deg)";
      });
    });

    /* hero parallax: artwork drifts slightly slower than the page */
    var heroCard = document.querySelector(".tilt-card");
    if (heroCard) {
      var ticking = false;
      window.addEventListener("scroll", function () {
        if (ticking) return;
        ticking = true;
        requestAnimationFrame(function () {
          var y = Math.min(window.scrollY, 500);
          heroCard.style.setProperty("translate", "0 " + (y * -0.04).toFixed(1) + "px");
          ticking = false;
        });
      }, { passive: true });
    }
  }

  /* ---------- FAQ: one open at a time (feels tidier) ---------- */
  var faq = document.querySelectorAll(".faq details");
  faq.forEach(function (d) {
    d.addEventListener("toggle", function () {
      if (d.open) {
        faq.forEach(function (o) { if (o !== d) o.open = false; });
      }
    });
  });
})();
