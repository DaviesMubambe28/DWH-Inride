import { releases, adminUrl } from "./release-config.mjs";
import { validateRelease, validateAdminUrl } from "./release-validation.mjs";

for (const [app, release] of Object.entries(releases)) {
  if (!release || !validateRelease(release)) continue;
  const card = document.querySelector(`[data-app="${app}"]`);
  if (!card) continue;
  const button = card.querySelector("button");
  const link = document.createElement("a");
  link.className = button.className;
  link.textContent = button.textContent;
  link.href = release.url;
  link.setAttribute("aria-describedby", `${app}-status`);
  button.replaceWith(link);
  card.querySelector(".release-status").textContent =
    `Version ${release.version} · Android ${release.android} or later · ${release.kind === "apk" ? "APK download" : "Google Play"}`;
}

if (adminUrl && validateAdminUrl(adminUrl)) {
  document.querySelectorAll(".admin-entry").forEach((link) => { link.href = adminUrl; });
  const link = document.createElement("a");
  link.className = "text-link";
  link.href = adminUrl;
  link.textContent = "Admin login";
  const status = document.querySelector("#admin-status");
  status.textContent = "Sign in to the operations dashboard. ";
  status.append(link);
}

const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

if (reducedMotion || !("IntersectionObserver" in window)) {
  document.querySelectorAll(".reveal").forEach((element) => element.classList.add("visible"));
} else {
  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          entry.target.classList.add("visible");
          observer.unobserve(entry.target);
        }
      });
    },
    { threshold: 0.13 },
  );

  document.querySelectorAll(".reveal").forEach((element) => observer.observe(element));
}

document.querySelector("#year").textContent = new Date().getFullYear();
