# Landing-page implementation verification — 2 November 2026

Started from clean `2c4e157` on `feat/release-operations`. Existing work preserved. Implemented source saved at `439765a`; subsequent commit records documentation/evidence. No public deployment, APK publication, booking enablement, admin credential change or backend/Auth/RLS modification. Public-launch score remains 4.00/10 under the existing rubric; these are local website improvements only.

## Observed results

| Check | Status / exact evidence | Limit |
|---|---|---|
| Static build/security/link tests | PASS: `npm run build` in sites/dhw-inride-landing — ten named tests, zero failures/skips; JS syntax and null release validation | No actual production APK configured; null-state pass does not verify download binaries |
| Delivery/config regressions | PASS: tests reproduce missing attachment/binary-header guard and unknown-app package validation failure before fixes. Dev/unsigned/temporary-query releases rejected; original seven checks preserved | Future approved APK/backend/signing evidence still required |
| Browser layouts | PASS: `UI_TEST_TOOLS=/workspace/release-tools ADMIN_EXPORT_DIR=/workspace/DWH-Inride/sites/dhw-inride-admin/out UI_TEST_ARTIFACTS=/workspace/release-tools/landing-ui/final node sites/dhw-inride-landing/tests/browser-ui.mjs` from repository root — actual isolated Chromium at 320/360/768/1024/1440px | Local fixture/static hosting only; no physical phone |
| Accessibility and keyboard | PASS: zero axe WCAG2A/AA/2.1AA violations at five widths; visible skip-link focus, actual main focus, Admin visibility, menu open/Escape/focus, five-step disclosure | Automated checks are not a complete screen-reader/human audit |
| Console/assets/layout | PASS: zero landing console errors/warnings, no horizontal overflow, page reload succeeds; image/scripts load locally | No deployed CDN/cache/browser matrix certified |
| No JavaScript | PASS:320/1440 widths, visible navigation/Admin and honest disabled releases, no overflow | Future configured downloads require JS metadata activation; static fallback does not pretend to download |
| Actual admin route | PASS locally: both manifest-configured links open existing actual Next static export root `/`, Operations sign in appears; refresh and footer navigation succeed | Owner confirmed unhosted; production `adminUrl=null`. Public links deliberately explain availability. No hosted session/role/RLS journey was repeated |
| Cross-origin transfer | PASS: actual downloads from one local HTTPS origin to another; both fixture bytes/checksums match, attachment filename observed. Only ephemeral test certificate key is trusted | Synthetic non-APK fixture, never in dist/manifest. Not APK validity, real hosting or production download evidence |
| Existing Android artifacts | Inspected: current hashes match prior artifact receipts; both `.development`, debug-signed/no-push | Not published. No suitable signed/minified release APK or release signer available |
| Portable signer invocation | PASS: official SDK apksigner JAR runs on existing development APK; build supports APKSIGNER_JAR/JAVA_HOME | Not Windows execution or release signing; no key generated/replaced |
| Local preview | PASS: `npm run preview`; actual GETs `/`, script/module and photo200 with correct MIME; hidden/traversal/missing-file404 | Loopback only, not a public service; no public deployment |
| Existing privacy/terms URLs | HTTP200 for both current GitHub main links | Explicit drafts; no legal/privacy/operator approval implied |
| Workflow | PASS: actionlint quality.yml/pilot-candidate.yml. Added landing browser step reusing existing isolated test tools | No GitHub run/protected setting certified; feature-branch synchronization uses the owner’s earlier GitHub authorization, with no merge/deployment |
| Slow-network usability | Observed in Chromium emulation:360px,50KiB/s,400ms latency, cold cache; DOMContentLoaded/heading visible1989ms, network idle7288ms, primary actions visible and no overflow | Not actual phone/network or a Core Web Vitals certification |
| Actual APK download/install/upgrade | BLOCKED / NOT VERIFIED | Need owner-approved signed/minified production artifacts, established signer/configuration, actual hosting and real supported phones |

Detailed machine-readable results/source hashes: [browser-verification.json](browser-verification.json). Research: [UI_RESEARCH.md](UI_RESEARCH.md). Exact future uploads, metadata, Windows commands, headers, caching and rollback: [APK_UPLOAD_GUIDE.md](APK_UPLOAD_GUIDE.md).

## Screenshots

[Desktop preview](landing-ui-desktop.jpg) · [Mobile preview](landing-ui-mobile.jpg). Full-page local captures are retained under `/workspace/release-tools/landing-ui/final/`. The images include licensed contextual photography; see [IMAGE_CREDITS.md](IMAGE_CREDITS.md). The prior redesign screenshot/artwork was preserved.

## Remaining external actions

1. Host the actual admin static export at a confirmed HTTPS root, verify anonymous sign-in and non-admin backend denials, then set `adminUrl` and redeploy the landing artifact. No guessed domain or /login path.
2. Complete actual-source backup/restore, reviewed hosted backend fixes and release gates; supply established signing identity and real Firebase/client inputs. Verify production-appropriate artifacts and real-phone journeys before public release.
3. Choose the actual landing/APK hosting target or approve immutable GitHub Release assets; follow APK_UPLOAD_GUIDE.md. Production entries remain null until real HTTP/header/hash/package/signer/backend checks and release approval.
4. Final operator/legal/insurance/privacy/retention/support approval remains required. These UI checks provide no launch approval. Do not enable bookings or publish an APK from this result alone.
