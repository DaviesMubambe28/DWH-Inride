import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

test("static export defines browser security headers", () => {
  const headers = readFileSync(new URL("../public/_headers", import.meta.url), "utf8");
  for (const name of [
    "Content-Security-Policy",
    "Strict-Transport-Security",
    "X-Content-Type-Options",
    "Referrer-Policy",
    "Permissions-Policy",
  ]) {
    assert.match(headers, new RegExp(`^  ${name}:`, "m"), `${name} must be configured`);
  }
  for (const directive of ["default-src 'self'", "frame-ancestors 'none'", "object-src 'none'"]) {
    assert.match(headers, new RegExp(directive.replaceAll("'", "\\'")), `CSP must include ${directive}`);
  }
  assert.match(headers, /connect-src 'self' https:\/\/vhckkmzyabbsbgjuhjmq\.supabase\.co wss:\/\/vhckkmzyabbsbgjuhjmq\.supabase\.co/);
});
