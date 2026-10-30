// Run during every production build. Never enables a release from filename alone.
import { releases } from "../dist/release-config.mjs";
import { validateRelease } from "../dist/release-validation.mjs";
import { mkdtemp, writeFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
const androidVersions = {26:"8.0",27:"8.1",28:"9",29:"10",30:"11",31:"12",32:"12L",33:"13",34:"14",35:"15",36:"16",37:"17"};
for (const [app, release] of Object.entries(releases)) {
  if (release === null) { console.log(`${app}: Coming soon (no APK configured)`); continue; }
  if (!validateRelease(release, app)) throw new Error(`${app}: invalid release metadata`);
  const response = await fetch(release.url, { signal: AbortSignal.timeout(120000) });
  if (!response.ok || !response.url.startsWith("https:")) throw new Error(`${app}: HTTPS APK request failed`);
  if ((response.headers.get("content-type") || "").includes("text/html")) throw new Error(`${app}: download returned HTML`);
  const bytes = Buffer.from(await response.arrayBuffer());
  if (bytes.length !== release.bytes || bytes.readUInt32LE(0) !== 0x04034b50) throw new Error(`${app}: wrong APK size or ZIP signature`);
  if (createHash("sha256").update(bytes).digest("hex") !== release.sha256.toLowerCase()) throw new Error(`${app}: APK checksum mismatch`);
  const directory = await mkdtemp(join(tmpdir(), "inride-apk-"));
  try {
    const file = join(directory, `${app}.apk`);
    await writeFile(file, bytes);
    // Supply these paths if Android SDK build tools are not on PATH.
    const badging = execFileSync(process.env.AAPT_PATH || "aapt", ["dump", "badging", file], { encoding:"utf8" });
    const pkg = badging.match(/package: name='([^']+)'[^\n]*versionName='([^']+)'/);
    const sdk = badging.match(/sdkVersion:'(\d+)'/);
    if (!pkg || pkg[1] !== release.packageName || pkg[2] !== release.version) throw new Error(`${app}: package/version mismatch`);
    if (!sdk || androidVersions[sdk[1]] !== release.android) throw new Error(`${app}: minimum Android version mismatch`);
    if (/application-debuggable/.test(badging)) throw new Error(`${app}: debug APK refused`);
    const signing = execFileSync(process.env.APKSIGNER_PATH || "apksigner", ["verify", "--verbose", "--print-certs", file], { encoding:"utf8" });
    const certificates = [...signing.matchAll(/certificate SHA-256 digest: ([a-f0-9]+)/gi)].map(m=>m[1].toLowerCase());
    if (!certificates.includes(release.certificateSha256.toLowerCase()) || /Android Debug/i.test(signing)) throw new Error(`${app}: signing certificate mismatch or debug certificate`);
    console.log(`${app}: HTTPS file, checksum, package, version, minimum SDK and release signature verified`);
  } finally { await rm(directory, { recursive:true, force:true }); }
}
