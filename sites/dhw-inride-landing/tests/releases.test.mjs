import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync, existsSync } from "node:fs";
import { validateRelease, validateAdminUrl } from "../dist/release-validation.mjs";
import { releases, adminUrl } from "../dist/release-config.mjs";
const apk = { kind: "apk", url: "https://downloads.example.org/passenger-release.apk", version: "0.1.0", bytes: 1024, android: "8.0", releaseDate: "2026-10-29", packageName: "zm.co.dhwinride.passenger", sha256: "a".repeat(64), certificateSha256: "b".repeat(64) };
test("direct downloads reject unsafe links, debug APKs, store redirects and missing metadata", () => {
  assert.equal(validateRelease(apk, "passenger"), true);
  for (const change of [{url:"javascript:alert(1)"}, {url:"http://example.org/a.apk"}, {url:"https://example.org/app-debug.apk"}, {kind:"play"}, {version:""}, {android:""}, {bytes:0}, {bytes:"123"}, {releaseDate:"2026-02-30"}, {sha256:""}, {certificateSha256:""}]) assert.equal(validateRelease({...apk,...change}, "passenger"), false);
});
test("Passenger and Rider releases cannot be interchanged", () => {
  assert.equal(validateRelease(apk, "rider"), false);
  assert.equal(validateRelease({...apk,packageName:"zm.co.dhwinride.rider"}, "rider"), true);
});
test("admin links reject unsafe schemes and embedded credentials", () => {
  assert.equal(validateAdminUrl("https://operations.example.org/"), true);
  assert.equal(validateAdminUrl("javascript:alert(1)"), false);
  assert.equal(validateAdminUrl("https://user:password@example.org"), false);
});
test("production configuration fails closed", () => {
  for (const [app,release] of Object.entries(releases)) assert.ok(release === null || validateRelease(release, app));
  assert.ok(adminUrl === null || validateAdminUrl(adminUrl));
});
const html = readFileSync(new URL("../dist/index.html", import.meta.url), "utf8");
test("unavailable releases remain honest without JavaScript", () => {
  assert.equal((html.match(/disabled aria-describedby="(?:passenger|rider)-status"/g)||[]).length, 2);
  assert.ok(html.includes("Download Passenger APK")); assert.ok(html.includes("Download Rider APK"));
  assert.ok(!html.includes("play.google.com")); assert.ok(!html.includes("data-store="));
});
test("all navigation anchors and local production assets exist", () => {
  const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map(m=>m[1]);
  assert.equal(ids.length, new Set(ids).size, "duplicate HTML IDs");
  for (const match of html.matchAll(/href="#([^"]+)"/g)) assert.ok(ids.includes(match[1]), `Missing anchor ${match[1]}`);
  for (const match of html.matchAll(/(?:src|href)="((?:assets\/|styles\.|script\.)[^"]+)"/g)) assert.ok(existsSync(new URL(`../dist/${match[1]}`,import.meta.url)), `Missing asset ${match[1]}`);
  assert.equal((html.match(/class="admin-entry"/g)||[]).length, 2);
});
test("Android requirement is grounded in both app configurations", () => {
  for (const app of ["passenger", "rider"]) {
    const gradle = readFileSync(new URL(`../../../${app}-app/build.gradle.kts`,import.meta.url),"utf8");
    assert.match(gradle,/minSdk = 26/);
  }
});
