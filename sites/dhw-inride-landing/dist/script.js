import { releases, adminUrl } from "./release-config.mjs";
import { validateRelease, validateAdminUrl } from "./release-validation.mjs";

const menu = document.querySelector("#main-menu");
const toggle = document.querySelector(".menu-toggle");
const mobile = window.matchMedia("(max-width: 1020px)");
let menuOpen = false;
function syncMenu() {
  menu.hidden = mobile.matches && !menuOpen;
  toggle.setAttribute("aria-expanded", String(mobile.matches && menuOpen));
  toggle.querySelector(".menu-label").textContent = menuOpen ? "Close" : "Menu";
}
toggle.hidden = false;
syncMenu();
toggle.addEventListener("click", () => { menuOpen = !menuOpen; syncMenu(); });
mobile.addEventListener("change", () => { menuOpen = false; syncMenu(); });
menu.addEventListener("click", (event) => {
  if (event.target.closest("a") && mobile.matches) { menuOpen = false; syncMenu(); }
});
document.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && menuOpen) { menuOpen = false; syncMenu(); toggle.focus(); }
});

for (const [app, release] of Object.entries(releases)) {
  if (!release || !validateRelease(release, app)) continue;
  const card = document.querySelector(`[data-app="${app}"]`);
  if (!card) continue;
  const button = card.querySelector("button");
  const link = document.createElement("a");
  link.className = button.className;
  link.append(...button.childNodes);
  link.href = release.url;
  link.setAttribute("download", `dhw-inride-${app}-${release.version}.apk`);
  link.setAttribute("aria-describedby", `${app}-status`);
  button.replaceWith(link);
  card.querySelector(".release-badge").textContent = "Available";
  for (const [field, value] of Object.entries({
    version: release.version,
    size: `${(release.bytes / 1048576).toFixed(1)} MB`,
    android: `${release.android}+`,
    date: new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" }).format(new Date(release.releaseDate)),
  })) card.querySelector(`[data-field="${field}"]`).textContent = value;
  card.querySelector(".release-status").textContent = "Signed Android APK · direct download";
}

if (adminUrl && validateAdminUrl(adminUrl)) {
  document.querySelectorAll(".admin-entry").forEach((link) => { link.href = adminUrl; });
  const link = document.createElement("a");
  link.href = adminUrl;
  link.textContent = "Admin Login";
  const status = document.querySelector("#admin-status");
  status.textContent = "Sign in to operations: ";
  status.append(link);
}
document.querySelector("#year").textContent = new Date().getFullYear();
