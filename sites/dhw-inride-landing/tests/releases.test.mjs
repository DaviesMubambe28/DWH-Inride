import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { validateRelease, validateAdminUrl } from "../dist/release-validation.mjs";
import { releases, adminUrl } from "../dist/release-config.mjs";
const apk = { kind: "apk", url: "https://downloads.example.org/passenger-release.apk", version: "0.1.0", android: "8.0" };
test("downloads require HTTPS, release metadata and a non-debug APK", () => {
  assert.equal(validateRelease(apk), true);
  for (const change of [{url:"javascript:alert(1)"}, {url:"http://example.org/a.apk"}, {url:"https://example.org/app-debug.apk"}, {version:""}, {android:""}, {kind:"unknown"}]) {
    assert.equal(validateRelease({...apk, ...change}), false);
  }
});
test("Play links require the real listing URL structure", () => {
  assert.equal(validateRelease({...apk, kind:"play", url:"https://play.google.com/store/apps/details?id=zm.co.dhwinride.passenger"}), true);
  assert.equal(validateRelease({...apk, kind:"play", url:"https://play.google.com/"}), false);
});
test("admin links reject unsafe schemes and embedded credentials", () => {
  assert.equal(validateAdminUrl("https://operations.example.org/"), true);
  assert.equal(validateAdminUrl("javascript:alert(1)"), false);
  assert.equal(validateAdminUrl("https://user:password@example.org"), false);
});
test("production configuration fails closed", () => {
  for (const release of Object.values(releases)) assert.ok(release === null || validateRelease(release));
  assert.ok(adminUrl === null || validateAdminUrl(adminUrl));
});
test("both unavailable apps and admin entries are present without JavaScript", () => {
  const html = readFileSync(new URL("../dist/index.html", import.meta.url), "utf8");
  assert.equal((html.match(/class="admin-entry" href="#admin-access"/g)||[]).length, 2);
  assert.equal((html.match(/disabled aria-describedby="(?:passenger|rider)-status"/g)||[]).length, 2);
  assert.ok(html.includes("Download Passenger App"));
  assert.ok(html.includes("Download Rider App"));
  assert.ok(!html.includes("data-store="));
  const ids = new Set([...html.matchAll(/\bid="([^"]+)"/g)].map(m => m[1]));
  for (const match of html.matchAll(/href="#([^"]+)"/g)) assert.ok(ids.has(match[1]), `Missing anchor ${match[1]}`);
});
