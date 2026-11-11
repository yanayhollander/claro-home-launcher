/* Compare the same interactive storyboard without copying its screens or actions. */
(() => {
  "use strict";
  const layouts = [
    ["classic", "A. Classic", "Compact: softkeys and call keys flank one connected navigation pad."],
    ["wide", "B. Wide softkeys", "Full-width Back / Confirm row above navigation, with call keys beside the pad."],
    ["lower", "C. Softkeys below", "Back / Confirm nearest the number keys; call keys flank navigation above."],
    ["stacked", "D. Integrated pad", "Wide corner keys with arrows fitted between them, saving a separate navigation row."]
  ];
  const picker = document.getElementById("comparison-scene");
  const grid = document.getElementById("comparison-grid");
  for (const [layout, title, description] of layouts) {
    const card = document.createElement("article");
    card.className = "comparison-card";
    card.dataset.layout = layout;
    const heading = document.createElement("h2");
    heading.textContent = title;
    const detail = document.createElement("p");
    detail.className = "comparison-description";
    detail.textContent = description;
    const viewport = document.createElement("div");
    viewport.className = "comparison-viewport";
    const frame = document.createElement("iframe");
    frame.title = `${title}: interactive Claro phone`;
    const error = document.createElement("p");
    error.className = "comparison-error";
    error.setAttribute("role", "alert");
    error.hidden = true;
    error.textContent = "This preview could not load. Open the full-size storyboard or reload the page.";
    const link = document.createElement("a");
    link.className = "comparison-open";
    link.textContent = `Open ${title} full-size`;
    viewport.append(frame);
    card.append(heading, detail, viewport, error, link);
    grid.append(card);
  }
  const observer = new ResizeObserver(entries => {
    for (const entry of entries) entry.target.style.setProperty("--preview-scale", String(entry.contentRect.width / 386));
  });
  grid.querySelectorAll(".comparison-viewport").forEach(viewport => observer.observe(viewport));
  function showScene() {
    const scene = [...picker.options].some(option => option.value === location.hash.slice(1)) ? location.hash.slice(1) : "home";
    picker.value = scene;
    for (const card of grid.children) {
      const url = new URL("index.html", location.href);
      url.searchParams.set("version", "classic");
      url.searchParams.set("layout", card.dataset.layout);
      url.hash = scene;
      card.querySelector("a").href = url.href;
      url.searchParams.set("embedded", "1");
      const error = card.querySelector(".comparison-error");
      error.hidden = true;
      const previous = card.querySelector("iframe");
      const frame = document.createElement("iframe");
      frame.title = previous.title;
      frame.addEventListener("load", () => {
        error.hidden = Boolean(frame.contentDocument?.querySelector("#classic-controls button"));
      });
      frame.src = url.href;
      previous.replaceWith(frame);
    }
  }
  picker.addEventListener("change", () => {
    history.replaceState(null, "", `#${picker.value}`);
    showScene();
  });
  window.addEventListener("hashchange", showScene);
  showScene();
})();
