# DHW-inRide landing page

Static HTML/CSS/ES modules for Chongwe motorbike transport; navy/orange/white, original design, no runtime dependencies or remote fonts. Existing working content/release safeguards preserved. Current page: `dist/index.html`; central public metadata: `dist/release-config.mjs`.

## Build and local preview

Use Node24.19.0 (the CI version). No npm dependency installation is needed for the site:

```sh
npm run build
npm run preview
```

Preview binds only `http://127.0.0.1:4173/`; set `LANDING_PREVIEW_PORT` if occupied. This is not public hosting. `dist/` is the complete deployable artifact; there is no bundle step. The build runs ten named tests, JS syntax and configured **real** APK download/package/signature checks. Null release entries pass only the pending-state checks. Serve over HTTP/HTTPS, not file://.

## Design, research and imagery

- [UI_RESEARCH.md](docs/UI_RESEARCH.md): actual Yango Ndola/Bolt observations, official Android sources and the design contract.
- [IMAGE_CREDITS.md](docs/IMAGE_CREDITS.md): Mary Milanzi's real helmeted-motorcyclist photograph from **Mbala, Zambia**, CC BY-SA4.0. Locally served 640/1280 variants; visible photographer/source/license/location credit. No DHW-inRide affiliation or Chongwe-photo claim. Previous artwork preserved, not requested by the page.
- Visible Admin outside the mobile menu; clear separate passenger/rider paths; larger mobile text/focus/touch areas; consistent rounded controls and no decorative animation.
- Current launch status, policy drafts and support information remain honest. No invented fares, testimonials, rider/customer counts, availability or safety guarantees. Support contacts follow docs/PILOT_STATUS.md; no contact was made.

## Download configuration and future uploads

[APK_UPLOAD_GUIDE.md](docs/APK_UPLOAD_GUIDE.md) has exact release output paths, static destinations/versioned filenames, existing public GitHub Release asset option, metadata/hash/signature commands, Windows signer-JAR configuration, headers/cache rules, deployment verification and rollback.

Both production release entries remain `null`: disabled buttons/Coming soon with no fake version, size, date or hash. Hero actions lead to the corresponding availability cards. Actual development output APKs are `.development`, debug-signed, push-disabled and **not** suitable for public release. Do not copy them to downloads or substitute them into the manifest.

A configured valid release shows exact bytes/MiB, version, Android minimum, date and expandable SHA-256. Cross-origin links require attachment/binary headers at the final file host; HTML's download attribute alone is insufficient. The build fetches each actual HTTPS APK and verifies size/ZIP/hash/package/version/minSdk/non-debuggable/signing certificate. It fails on missing tools/headers/files or mismatches. `APKSIGNER_JAR` + JAVA_HOME supports invoking the SDK signer without a Windows .bat shell. Actual production APK/download/install is still unverified.

## Admin routing and hosting

The owner explicitly confirmed the dashboard **has not been hosted**. Actual sign-in route is `/`, from `sites/dhw-inride-admin/app/page.tsx` → `AdminDashboard`; no `/login` exists. Both Admin entries read the single `adminUrl` manifest setting. Leave it null until the actual HTTPS root is deployed and verified: currently links open the honest availability explanation. No localhost/guessed public hostname is committed.

A public link grants no role. Existing Auth/profile checks and backend RLS/RPC restrictions remain unchanged. Local browser checks navigated both configured links to the actual existing admin static export's root sign-in and refreshed it. That is **not** hosted admin/Auth/RLS verification.

No landing/admin deployment target was configured or created. GitHub metadata shows a public repository, Pages false and no deployment records. `dist/_headers` works only on supporting hosts; equivalent headers must be configured elsewhere. No production deployment, APK publication, booking enablement or public release approval occurred.

## Browser verification

The isolated real Chromium suite lives in `tests/browser-ui.mjs`. Test-only tools are installed outside this website. With an existing test-tools directory containing playwright-core and @axe-core/playwright, Chromium and OpenSSL:

```sh
UI_TEST_TOOLS=/path/to/ui-tools CHROMIUM_PATH=/path/to/chromium \
UI_TEST_ARTIFACTS=/tmp/inride-ui \
ADMIN_EXPORT_DIR=/absolute/path/to/sites/dhw-inride-admin/out npm run test:browser
```

`ADMIN_EXPORT_DIR` is optional; without it no actual admin export navigation is certified. The suite starts only private loopback servers and isolated profiles; temporary fixture certificates/files are removed. A pinned ephemeral public key trusts only the local HTTPS fixture, not arbitrary certificates.

Verified: 320/360/768/1024/1440 widths, no horizontal overflow, zero detected WCAG A/AA violations, zero landing console errors/warnings, keyboard skip/focus/menu/Escape, pending states, disclosure, refresh, no-JS320/1440, and both links to actual local admin sign-in/root refresh. Actual cross-origin HTTPS attachment transfers match a **synthetic non-APK fixture's** bytes/hash; this does not certify any release binary. No connected Android hardware, actual phone install/upgrade or deployed website was verified. Screenshots/results: docs/UI_VERIFICATION.md.
