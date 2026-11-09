# Android emulator runtime attempt — 7 November 2026 (CAT)

Status: **BLOCKED by the managed executor's lack of hardware virtualization**. This does not replace the two-phone rehearsal.

The installed Android 35 Google APIs x86_64 image was configured as a Pixel 6 AVD and started headlessly with software CPU/GPU emulation (`-accel off`, SwiftShader). ADB connected to `emulator-5554` and reported Android 15. The guest reached `zygote64`, `surfaceflinger`, and `system_server`, but `sys.boot_completed` remained empty through two bounded 45-second waits after the initial boot period. Error-only logcat repeatedly showed the activity AIDL service unavailable. The executor has no `/dev/kvm`, so the emulator was stopped after the bounded attempt; no APK was installed and no Android UI behavior was marked verified.

The next valid runtime evidence remains installation of the recorded development APKs on actual phones, followed by portrait/landscape, large-text, TalkBack, keyboard, map/GPS, backgrounding, and passenger/rider journey checks. Production publication and bookings remain disabled.

---

# Android interaction remediation — 6 November 2026 (UTC)

Verdict remains **BLOCKED** for public release. The work below improves both Android interfaces and produces development-only APKs for guided rehearsal. It does not provide signed/minified release candidates, live backend journey evidence, or physical-device validation.

Source baseline before this uncommitted remediation: `d47c911e1bd51213dd3a8f543eab310735a93a36` on `feat/release-operations`.

| Gate | Status | Current evidence | Remaining limit |
|---|---|---|---|
| Passenger location selection | PASS (automated/local) | Existing debounced repository search is now available in the pickup/destination map sheet; search result selection clears transient state and advances the existing booking flow. Two focused ViewModel regressions pass. | Real Mapbox result quality, keyboard behavior, GPS/map gestures, and the full hosted trip remain device/staging checks. |
| Rider request presentation | PASS (source/local) | Removed the unchanging 20-second UI countdown because no server expiry exists. Multiple offers now expose an explicit visual and semantic selection before acceptance. | Competing rider acceptance remains a hosted authorization/race gate; visual behavior needs device rehearsal. |
| Rider onboarding hierarchy | PASS (source/local) | Application is split into account, motorcycle, and review steps; review repeats submitted details and preserves the existing operations-approval restriction. | Email delivery, document upload, admin approval, and physical verification still require configured staging and operator evidence. |
| Notification permission context | PASS (source/local) | Android 13+ permission is requested only from an explanation screen with an available “Not now” path. Declining does not block use. | Actual FCM delivery/background behavior remains unverified. |
| Accessibility baseline | PASS (static/local) | Custom map controls have 48 dp minimum targets, bottom navigation/helper copy is at least 12 sp, decorative glyphs are removed from TalkBack semantics, request selection uses radio-button semantics, and the location sheet is capped relative to available height for short and landscape screens. Android lint passes for both apps. | TalkBack, large text, contrast under device settings, landscape, and small-screen checks need physical execution. |
| Android development builds | PASS | `:core-ui:testDebugUnitTest`, `:passenger-app:assembleDevelopment`, `:rider-app:assembleDevelopment`, `:passenger-app:lintDevelopment`, and `:rider-app:lintDevelopment` completed successfully with cached dependencies. Passenger APK SHA-256 `95cee8584f3ad9c9a07f6c75975afd9097d18ddeabdc59e8102a7d487257546f`; rider APK SHA-256 `e847c610993834df43913938b3e4f7bcd17907aea78ab25fa7beec64bf106d78`. Both are versionCode 1, versionName `0.1.0-development-no-push`, minSdk 26. | These are development artifacts with `.development` application IDs. They are not public distribution or release evidence. |

Design decisions and official reference boundaries are recorded in `ANDROID_UI_DESIGN_CONTRACT.md`. No production deployment, public booking enablement, or APK publication occurred.

---

# Hosted-deployment resume — 4 November 2026 (CAT)

Engineering source is `38f9af7` on `feat/release-operations`; evidence documentation follows in a later commit. The current executor is the managed Linux cloud environment. Its HTTP policy is unrestricted and enforced, but `/etc/codex/network-policy.json` has VPN disabled and no TCP domain or IP grants. The public Session pooler therefore remains ineligible for this runtime's TCP proxy. No unchanged connection probe was repeated, no TLS setting was weakened and no hosted write occurred.

The reviewed Windows helper still matches SHA-256 `bc49a0c35bc0c995c2ebf77ffc706bea9f61bdb34f977bdd60c1c16596c2388e`. The isolated-restore procedure now uses the reviewed Supabase PostgreSQL 17.11 image digest instead of a mutable tag; all five PowerShell blocks parse with PowerShell 7.5.4. The real backup and isolated restore remain **BLOCKED**, so hosted migrations remain **12/28** and Edge Functions remain **1/4**.

**Next operator action:** on the Windows computer, open PowerShell 7 by running `pwsh`, then return only the output of `$PSVersionTable.PSVersion.ToString()` and `docker info --format '{{.OSType}}'`. Do not enter or send a password, certificate, archive or signing material at this step. Sanitized receipt: [hosted deployment resume](evidence/hosted-deployment-resume-20261104/verification.json).

---

# Backend/release continuation — 4 November 2026 (CAT)

Engineering source is `8e9efea5f2c73cd1195bff05bcb93aed07375964` on `feat/release-operations`; documentation follows in a later commit. The hosted backup gate remains BLOCKED by the same-day read-only evidence, so zero migrations or functions were deployed. The managed runtime reports an enforced unrestricted network policy and ready `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `SUPABASE_ACCESS_TOKEN`, and `MAPBOX_PUBLIC_TOKEN` bindings without exposing their values. Firebase application files/sender, SMTP, monitoring, worker token and established release-signing inputs remain unavailable.

Completed independent release engineering: the protected candidate workflow now records a UTC build timestamp and generates a human-readable Android release manifest only after both signed/minified APKs pass the existing fail-closed package, version, SDK, integrity, debuggability, mapping and certificate checks. All **20** recovery/preflight/candidate tests pass and official actionlint accepts both workflows. The checked-in [Android release manifest](ANDROID_RELEASE_MANIFEST.md) truthfully remains BLOCKED; no release APK, signer claim, publication or physical result was invented.

Sanitized receipt: [backend/release continuation](evidence/backend-release-continuation-20261104/verification.json). Readiness remains **4.00/10 — Not ready**.

---

# Launch-readiness continuation — 4 November 2026 (CAT)

Engineering source is `f585aefe2cb9b8a92b89d6b8cf137d7a5beaf118` on `feat/release-operations`; documentation follows in a later commit. Read-only hosted inspection confirms zero backups/PITR, one deployed function (`request-trip` v2), **12 hosted records / 28 repository migrations**, localhost Auth origin, no redirect allowlist or custom SMTP, and none of the new pilot/retention/outbox tables. No hosted mutation occurred.

Actual isolated local Auth/PostgREST/Storage rehearsal passes rider signup, passenger-role boundary, pending application creation, private document upload, self-approval denial and cross-user read denial. All synthetic users and objects were removed and verified at zero. Container health remains a managed-environment false negative because internal localhost probes inherit the HTTP proxy and receive proxy 403; direct loopback Auth/REST/Storage endpoints each returned HTTP 200.

Fixed Android's 10 MB document acceptance mismatch with the configured 5 MB private Storage bucket. The regression failed before the fix and passes afterward. Current Android result: **70 tests**, both development builds and both lint tasks pass. Updated APKs are recorded in [LAUNCH_READINESS_REPORT.md](LAUNCH_READINESS_REPORT.md) and the [current verification receipt](evidence/launch-readiness-20261104/verification.json).

The consolidated eight-area tracker is [LAUNCH_READINESS_REPORT.md](LAUNCH_READINESS_REPORT.md). The fixed readiness remains **4.00/10 — Not ready** because hosted recovery/deployment, live integrations, established release signing, physical testing, hosting and accountable approvals remain blocked.

---

# Rider self-application evidence — 4 November 2026 (CAT)

Engineering source is `6eb81ba5a714bb5304e1054f378b1c138cb374d9` on `feat/release-operations`; this evidence update follows in a documentation commit. The fixed rubric remains **4.00/10 — Not ready** because hosted recovery/deployment, live integrations, release signing, physical devices, hosting and accountable approvals remain unverified.

Verified in this slice:

- Added a secure rider self-application flow across Android, Supabase and Admin. Auth metadata creates a passenger profile plus a pending application and never grants rider trip access. Admin review provisions only a pending rider; final activation still requires the existing in-person document, safety and physical-device checks.
- Closed the booking/provisioning race by taking the same profile-row lock before either active-trip validation or trip creation. Rejection reasons are enforced by the RPC. Applicant document rows and Storage objects remain private, lose applicant access after rejection, and the application is removed when account retention reaches anonymization.
- Replayed all **28 repository migrations** from an empty isolated local database. The focused application file passes **30 assertions** and the complete suite passes **15 files / 249 assertions**. This was database-only local verification; full local Auth, Storage API and vector service startup initially timed out and no hosted migration was applied.
- Android `:core-ui:testDebugUnitTest` passes **69 tests** with zero failures, errors or skips. Both explicitly labeled development/no-push APKs assemble and verify with APK Signature Scheme v2. A broad normal-debug test attempt correctly stops because both real Firebase `google-services.json` files are absent.
- Admin lint, typecheck, three unit tests and the production build pass. The fixture-backed Chromium test passes four responsive/axe viewports and exercises application approval/rejection, verification, failure recovery and access-denied states with no runtime test errors.
- Final diff review found and drove fixes for the provisioning race, raw upload-error disclosure, RPC audit-note enforcement and direct applicant document/retention policy coverage. The follow-up review found no remaining blocking or material issue. Changed-source and APK scans found no management token, server key, service-role marker or private-key marker.

Current installable development artifacts (not release candidates; no push; no phone install):

| App | Path | SHA-256 | Bytes |
|---|---|---|---:|
| Passenger | `passenger-app/build/outputs/apk/development/passenger-app-development.apk` | `9e460f5b30d05678bc07a18d369039970e014e4f9bcb835fe3f956736dc28e01` | 125778931 |
| Rider | `rider-app/build/outputs/apk/development/rider-app-development.apk` | `73df7500178a57698f93a1a8c858610fa8a8e2e07a0c4fc88c6c9eb88a365064` | 125778927 |

Both are version `0.1.0-development-no-push`, code 1, minimum SDK 26, target SDK 37, debuggable and V2-signed by the current local Android Debug certificate `1b6488a0…fd14f`. That certificate differs from the earlier recorded local development signer, so these APKs are **not proven upgrade-compatible** with earlier development installs. It does not replace or verify the established release signing identity.

No Android target is connected. No hosted mutation/deployment, real email/FCM/monitoring delivery, physical install, public booking enablement, Admin/landing hosting, APK publication, merge to `main`, or production action occurred. Sanitized machine-readable evidence: [`evidence/rider-self-application-20261104/verification.json`](evidence/rider-self-application-20261104/verification.json).

---

# Engineering continuation evidence — 3 November 2026 (CAT)

Current engineering source is `b6de8a0ae71b4319395221d38d57b036f4d5b5e4` on `feat/release-operations`; evidence documentation follows in later commits. The fixed rubric remains **4.00/10 — Not ready** because no local result can close hosted backup/full-service recovery, migration/function deployment, live email/FCM/monitoring, established release signing, physical-device, hosting or accountable approval gates.

New verified engineering results:

- Fixed stale push delivery after sign-out, expired sessions, restoration failures and cross-app role rejection. Local notification display is disabled before remote cleanup, so an offline unregister cannot show the previous user's ride events. Four new push-gate tests bring the Android total to **64**, all passing; core/app lint and both development builds pass.
- Added a fail-closed signed-candidate verifier and protected `PILOT_RELEASE_CERT_SHA256` input. The workflow now proves exact package/version/SDK, non-debuggable release state, minification mappings, established signer, APK integrity/hash/size and source commit before uploading a protected artifact.
- Replayed all **26 migrations** from an empty isolated local database; **14 files / 218 SQL assertions** pass. The current local Auth/PostgREST/request handler/live-Mapbox trip, cash, race and PIN-lock rehearsal passes and removes its fixtures.
- Fresh complete regression: 18 Edge tests and frozen checks pass; three real loopback worker authorization checks pass; 17 recovery/preflight/candidate tests pass; Admin lint/typecheck/build/3 tests/4-view browser checks pass; landing 10 tests/5-view browser and accessibility checks pass.
- Fresh development-only hosted inspection remains unchanged: ACTIVE_HEALTHY, **12 recorded / 26 repository migrations**, only `request-trip` v2, no listed backups/PITR, localhost Auth URL, no redirects/custom SMTP, and missing FCM/worker/Sentry secrets. Anonymous profiles and missing-JWT function access return 401; anonymous private Storage exposes zero entries. No hosted mutation or deployment occurred.
- Root and 13 locked Edge npm packages have zero advisories. Admin retains five moderate legacy ESLint/minimatch development-tool paths; the offered remediation is an incompatible major/downgrade, while lint, tests and production build pass.

Rebuilt development APKs (not release candidates; no push; no phone install):

| App | Path | SHA-256 | Bytes |
|---|---|---|---:|
| Passenger | `passenger-app/build/outputs/apk/development/passenger-app-development.apk` | `8f529ab7f1c6e369722ed634299c06aab56d68f07b1c8ed79a4f3d21487dd95a` | 125856688 |
| Rider | `rider-app/build/outputs/apk/development/rider-app-development.apk` | `91f60f3002d325d1a964814b5bbdc678dfd24065fb8b73077530b85779ad4f51` | 125856684 |

Both are version `0.1.0-development-no-push`, code 1, minimum SDK 26, target SDK 37, debuggable, and signed by preserved development certificate `18847e…85c9`. Scans found no configured management token, service-role marker or private-key marker.

Sanitized machine-readable evidence: [`evidence/engineering-continuation-20261103/verification.json`](evidence/engineering-continuation-20261103/verification.json). Production bookings, production deployment, public APK publication and merge to `main` remain disabled.

---

# Current master-remediation evidence — 2 November 2026 (UTC)

Current source is `945d14961bc029c5685f778ef2c94514087bbbea` on `feat/release-operations`; evidence documentation follows in later commits. The fixed rubric remains **4.00/10 — Not ready**. No local-only result closes the actual backup/full-service recovery, hosted migration/function, signed release, live delivery, physical-device, hosting or accountable approval gates.

Fresh checks on the current source:

- 60 Android unit tests pass with zero failures/errors/skips; core, passenger and rider lint pass; both development APKs build with the preserved debug signer `18847e…85c9`.
- Passenger development APK: `passenger-app/build/outputs/apk/development/passenger-app-development.apk`, SHA-256 `9ae7d4064917f9fe49c39b7219e4bd6d115cc9aaf3aaffc0f38c25387f8af760`, 125856533 bytes.
- Rider development APK: `rider-app/build/outputs/apk/development/rider-app-development.apk`, SHA-256 `f6dd20dfa3a9535e4549db382fed6e5a24ae2ac71c4758cddda394de3e0ec59d`, 125856529 bytes.
- Both APKs are debuggable package-suffixed `0.1.0-development-no-push`, code 1, minimum SDK 26, target SDK 37. They contain the expected public bindings and no configured management token or service-role marker. They are not release candidates and have not been installed on physical phones.
- New source behavior restores persisted sessions after Auth initialization, enforces passenger/rider role separation, and provides actual sign-out actions in both apps. Five session/sign-out tests and two role-boundary tests were added; inverting the role predicate made both role tests fail before restoration.
- Admin typecheck, lint, production export, three unit tests and four-view fixture browser checks pass. The ES-module package declaration removes the previous Node reparsing warning.
- Landing build has ten passing tests; five-view browser/axe/no-overflow checks pass. Passenger/rider downloads and public Admin remain intentionally unavailable.
- Local SQL: 14 files / 218 assertions pass. Edge: 18 tests pass. Recovery/preflight: 15 tests pass. The real local Auth/PostgREST/current handler/live-Mapbox trip, cash acknowledgement, concurrency and PIN-lock rehearsal passes and cleans its fixtures.
- Mapbox API authentication and the exact 11.33.0 Maven POM each return HTTP 200. The configured Supabase URL matches confirmed development-only project `vhckkmzyabbsbgjuhjmq`; hosted state remains 12 of 26 migrations and only `request-trip`. Auth remains localhost/auto-confirm/no custom SMTP.
- Root dependency audit has zero findings. Admin still has five moderate development-tool paths through legacy ESLint plugins and `minimatch 3.1.5`; npm's offered fix is an incompatible Next/ESLint downgrade. No high or critical finding remains.

Sanitized machine-readable evidence and hashes: [`evidence/master-remediation-20261102/verification.json`](evidence/master-remediation-20261102/verification.json). No hosted write/deployment, booking enablement, public hosting, APK publication, email/FCM/monitoring delivery or real-phone check occurred.

---

# Landing UI follow-up — 2 November 2026

Source `439765a` updates the landing UI, attributed real Zambia photography, visible mobile Admin, future download/checksum/installation handling, local preview and browser CI checks. Ten named build tests pass; real Chromium checks at320/360/768/1024/1440 have zero detected axe violations/landing console errors/overflow. Both configured Admin links reached the actual local exported root sign-in and refreshed. Private cross-origin HTTPS attachment fixture passed size/hash checks; **not a production APK**. Owner confirms admin is unhosted; `adminUrl` and both public releases remain null. No actual public download/phone install, public deployment, APK publication or booking enablement. The initial GitHub push was rejected before execution; following the user’s continuation instruction, all four UI commits were synchronized successfully to `feat/release-operations` through `78b8c41`. No merge or release action was taken. Android inputs/artifact hashes remain unchanged; previous backend/release/operational gates and **4.00/10 — Not ready** persist.

Screenshots, exact tested scope, research and future upload guide: [landing UI verification](../../sites/dhw-inride-landing/docs/UI_VERIFICATION.md). The source hashes in its browser receipt correspond to this website follow-up; earlier entries below retain their original scopes.

---

# Current continuation checkpoint — 2 November 2026 (CAT)

**Blocked below 9/10: 4.00/10 — Not ready.** Fixed [rubric/requirements](LAUNCH_REQUIREMENTS.md); [audit](LAUNCH_READINESS_AUDIT_2026-11-02.md); [resume checkpoint](LAUNCH_CHECKPOINT.md); [external handoff](REMEDIATION_HANDOFF_2026-11-02.md); [prepared deployment plan](DEVELOPMENT_DEPLOYMENT_PLAN.md). Began clean at `4cf13cb`; current source through `4aaef22`, subsequent changes documentation/evidence only. No push, hosted write/deploy, publication, real booking enablement or external messages.

| Gate | Result | Current evidence and scope |
|---|---|---|
| Rider saved-state/privacy and action guards | PASS locally | Nine new regressions; persisted PIN removed, acceptance/PIN rapid duplicates gated, stale/removed/started/completed trips and restart navigation reconciled. Inverted guard caught; original restored |
| Android unit/lint/development artifacts | PASS development only | 53 tests, zero failures/errors/skips; core lint 0 errors/37 warnings, each app 0 errors/4 warnings. Both development APKs rebuilt with unchanged debug identity; signatures/16KiB alignment/public bindings/known-secret scan pass. [Current hashes and source provenance](evidence/continuation-20261102/android-artifacts.json) |
| Current APK installation, same-identity release upgrade | BLOCKED | Zero connected devices; release signer/history/Firebase absent. Old emulator installs/ANR apply to older hashes only. No production candidate or current install claim |
| Local trip and concurrency | PASS local API scope | Real local Auth/PostgREST/current handler/live Mapbox; full trip/cash and role/fare/revocation/duplicates. Nine parallel cancellation races with both winner outcomes observed; six wrong-PIN calls persist five and lock, correct PIN denied while locked. Fixtures removed; original local policy paused |
| SQL/site/admin/worker/dependency checks | Retained, not rerun | No corresponding SQL/site/worker/dependency changes. Previous 26 migrations/218 SQL assertions, seven landing and admin/browser/worker results remain scoped historical evidence; five moderate dev-tool findings still open |
| Fresh hosted safety/configuration | FAIL/BLOCKED | 12 records vs 26 local, 14 pending; legacy cash/PIN/start behavior and only request-trip v2. No listed backup/PITR/custom SMTP/new workers; missing FCM/operations/Sentry inputs. Read-only inspection only; existing Mapbox secret preserved |
| Backup/restore path | Prepared, actual gate BLOCKED | Native psql is optional using new pinned Docker PostgreSQL17.11 helper. Four versions, file mapping with spaces and invalid-archive rejection pass on Linux; five guide blocks/helper syntax pass. Actual Windows mount/TLS/source archive/isolated source restore/full-service recovery remain unverified |
| Email/FCM/monitoring/hosted two-phone journey | BLOCKED | Missing configuration and real recipients/devices/operator observations; local generated OTP test did not send email |
| Operator/legal/insurance, privacy/retention, support | BLOCKED separately | Required accountable approvals and actual drills remain absent; local code/fixtures cannot close these gates |

Operator returned PS5.1.26100.9711, pwsh available, Docker29.8.2, CLI2.121.0, native psql absent. **Next step only:** launch `pwsh`, then return `$PSVersionTable.PSVersion.ToString()` and `docker info --format '{{.OSType}}'`. Do not enter a database password or start a dump yet. We then guide the reviewed helper versions, CA/native verified-TLS connection, masked whole-database/role capture and isolated restore in order. No native PostgreSQL installation is required for this alternative.

Earlier sections below are historical and do not override current evidence.

---

# TCP capability and Windows backup fallback — 2 November 2026 (CAT)

Continuation from preserved `533a2a5`. **Release and hosted migrations remain BLOCKED.** No hosted writes, network configuration changes, environment publication or production/APK publication were performed.

| Gate | Status | Current evidence |
|---|---|---|
| TCP capability diagnosis | PASS | Detailed runtime reference requires VPN plus a TCP grant and private IPv4 destinations only. This public pooler is ineligible. VPN false, grants empty; unchanged proxy probe not repeated |
| Public TCP activation | BLOCKED | No supported public-TCP activation tool or documented UI setting/click path. Previous suggestion to add a pooler TCP grant was incorrect and is superseded |
| Supabase endpoint identity | PASS | Management API reconfirmed actual host, database, username and transaction port 6543. Official docs establish session port 5432; operator must cross-check project Connect panel. PostgreSQL 17.11 and 12 migration records confirmed |
| Native PostgreSQL TLS/login | BLOCKED | No eligible cloud transport. Windows procedure requires certificate validation and identity check before export; no successful native connection is claimed |
| Windows whole-database backup/restore procedure | PREPARED | Secure native password prompts, verify-full TLS, schema+data archive, roles, private inventories/checksums and isolated restore/ownership/RLS checks documented. PowerShell 7 syntax PASS; actual Windows capture/restore NOT RUN |
| Inventory SQL | PASS | New query validated via read-only hosted Management endpoint; required catalog and table keys present. This does not verify native Windows role permissions or a backup |
| Hosted backup/migrations/functions/journeys | BLOCKED | No verified recoverable archive, so no history repair, SQL/Edge deployment or synthetic journey writes |
| Android core release variant | PASS | assembleRelease and lintRelease succeed: shared AAR, zero lint errors, 37 warnings. Hash recorded in evidence; not an application APK or minification/signing result |
| Passenger/rider release candidates | BLOCKED | Real Firebase files and established signing identity inputs still required; no replacement keys generated |
| Live FCM/monitoring/full-service/device/operator approvals | BLOCKED | Prior limitations remain; no simulated delivery/recovery/physical or legal/support approval substituted |

Use [WINDOWS_SUPABASE_BACKUP.md](WINDOWS_SUPABASE_BACKUP.md). It corrects the previous TCP-grant recommendation, provides verified connection fields and the exact local capture/isolated-restore procedure, and distinguishes full database schema/data from Storage bytes/platform secrets/full-service recovery. Start by cross-checking the project's **Connect → Session pooler → View parameters** and obtaining its trusted CA from Database settings. Enter the existing database password only at the native tools' masked prompts. No password needs to be sent in chat or stored in Git.

Current metadata: `evidence/tcp-windows-fallback/verification.json`. Earlier sections are historical and do not override this correction. Existing passed Android debug/SQL/Edge/web/recovery suites were not repeated.

---

# Development Supabase phase — 2 November 2026

Current engineering commit: `debebd7` (based on preserved `bbc181a`). Target `vhckkmzyabbsbgjuhjmq` is owner-confirmed development-only. The owner has authorized reviewed non-production backup/migration/function work. **No hosted changes were made because the required recoverable backup is blocked.**

| Gate | Status | Verified result |
|---|---|---|
| Management/HTTP/Mapbox dependencies | PASS | Existing management binding works; effective unrestricted HTTP policy; real Mapbox origin POM 200 and Android dependency resolution/compilation succeed |
| Development backup | BLOCKED | Provider lists no backups; PITR off. TCP grants empty and advertised TCP proxy refuses connection before CONNECT response. No recoverable hosted database backup created |
| Eleven pending migrations compared | PASS | New objects/alter preconditions checked. Hardening body, security attributes, all column/table grants and execution restrictions already match. Other replacement bodies match recorded baseline; substantive pending work remains |
| Migration reconciliation/application | BLOCKED | Backup gate prevents history repair or SQL deployment; 12 recorded versions remain unchanged |
| Function source inspection | PASS | Existing request-trip source exported outside Git; lacks local pilot precheck, so update is required after backup. MAPBOX_SERVER_TOKEN preserved |
| Three worker/function deployments and hosted journeys | BLOCKED | No deployment/test identities/data mutations before backup; synthetic rider/approval, trip authorization, acceptance race and fare checks not run hosted |
| Android shared core | PASS | Compiled; 34 tests, 0 failures/errors/skips; lint 0 errors, 37 warnings. Fixed notification permission declaration and geographically invalid test fixtures; added outside-area denial coverage |
| Signed/minified passenger and rider candidates | BLOCKED | Both Firebase JSON files and existing signing inputs absent; no replacement keys or APKs generated |
| Real FCM/monitoring | BLOCKED | Hosted FCM_SERVICE_ACCOUNT_JSON, OPERATIONS_WORKER_TOKEN and SENTRY_DSN still missing; no delivery/alert claim |
| Auth email/callback journeys | BLOCKED | localhost Site URL, empty redirects, no custom SMTP; Android confirmation/reset deep link not implemented. Password sign-in requires no redirect. Confirmation remains disabled pending verified email delivery |
| Full-service recovery | BLOCKED | Prior local database-only restore carried forward; hosted export and isolated Auth/Storage/functions/config recovery unverified |
| Operator/legal/support/device approvals | BLOCKED | Actual physical-device, operator/safety, support coverage, insurance/licensing and privacy/retention evidence remain outstanding |
| Production/public bookings/APK publication | BLOCKED | Release not approved; no production resources or public publication enabled |

See [DEVELOPMENT_SUPABASE_PHASE.md](DEVELOPMENT_SUPABASE_PHASE.md) and `evidence/development-supabase-phase/` for exact reconciliation, Auth/secret handoff and backup limitations. **Single next action:** enable development TCP session access to `aws-0-eu-west-1.pooler.supabase.com:5432` and activate its TCP proxy through supported environment settings/admin support. No HTTP draft edit was made. Attempt temporary CLI login before requesting a database password. After this gate, proceed with verified backup and authorized hosted work, then FCM configuration first, one service at a time.

Older sections below are historical observations and do not override these current results. Previously passing, unaffected SQL/Edge/recovery/web suites were not repeated.

---

# Current Supabase inspection — 2 November 2026

**PASS:** management read access with the configured token and project URL. **Owner-confirmed development-only target:** `vhckkmzyabbsbgjuhjmq`; no production service exists yet. There is only one visible project and no branches, so a separate staging/production pair is not established.

Read-only inspection found 12 recorded/23 local migrations (11 pending), only request-trip v2 deployed, and no new pilot/retention/outbox tables. The apply_as_driver fix already matches a locally pending hardening migration, demonstrating migration-history drift. Existing MAPBOX_SERVER_TOKEN is present and must be preserved. Hosted FCM_SERVICE_ACCOUNT_JSON, OPERATIONS_WORKER_TOKEN and SENTRY_DSN are absent. Auth auto-confirm is enabled, Site URL is localhost, redirect allowlist is empty and custom SMTP is unconfigured. No hosted changes or user-data queries were performed.

See **SUPABASE_INSPECTION.md** and `evidence/supabase-inspection/` for verified metadata and the pending migration list. Earlier statements below that management access/networking are blocked are historical; they do not override this inspection. **Release remains BLOCKED** pending deployment reconciliation, live journeys, actual candidates, notifications/monitoring, full recovery and operator/legal evidence.

---

# Staging configuration recheck — 2 November 2026 (CAT)

Tested commit: `53cd67932ff90165600122fb6672639437c834d2` on `feat/release-operations`, initially clean and preserved. Time: `2026-11-02T01:57:58.063409+02:00`. **Release remains BLOCKED.** The user has authorized reviewed staging migration/function deployment once the exact staging target/access is available. Production deployment, booking enablement and APK publication remain unauthorized. No additional blanket staging approval is needed.

## Current gates

| Gate | Status | Current observation / next action |
|---|---|---|
| Repository preservation | PASS | Correct branch/commit and clean initial tree confirmed; changes this run are evidence/handoff only |
| Mapbox runtime network change | FAIL | Current enforced runtime revision 1 still has no custom hosts. Saved draft contains Mapbox/Google additions but is unapplied. Fresh curl returns proxy CONNECT 403 before TLS |
| Mapbox origin authentication | BLOCKED | Origin never reached. Apply `api.mapbox.com` in cloud environment settings first; then distinguish origin access from proxy access without printing headers/tokens |
| Mapbox artifact/dependency resolution | BLOCKED | Test POM/artifact access and Gradle resolution separately after origin access; no new build retry on unchanged network failure |
| Staging Supabase identity/access | BLOCKED | No local/injected URL/public key/management token. Existing CLI configuration returns AccessTokenRequiredError. Remote project/secret state cannot be inspected |
| Firebase/FCM validation | BLOCKED | Both real google-services.json files and local FCM_SERVICE_ACCOUNT_JSON absent. Hosted bindings unobserved without management access |
| Monitoring destination | BLOCKED | SENTRY_DSN and OPERATIONS_WORKER_TOKEN unavailable locally; no remote probe/alert receipt claimed |
| Existing signing identity | BLOCKED | No configured keystore path/password/alias or root secrets.properties. No replacement key generated. Owner must identify established certificate/distribution history |
| Passenger signed/minified candidate | BLOCKED | Source `zm.co.dhwinride.passenger`, versionName 0.1.0/versionCode 1. No release APK, hash or signature result exists |
| Rider signed/minified candidate | BLOCKED | Source `zm.co.dhwinride.rider`, versionName 0.1.0/versionCode 1. No release APK, hash or signature result exists |
| Staging migrations/functions | BLOCKED | Authorized in confirmed staging, but target/access missing. No hosted read/mutation/deployment performed |
| Staging passenger/rider/admin and authorization journeys | BLOCKED | No configured target/test identities/candidates; historical/local tests are not substituted |
| Real FCM/device and monitoring delivery | BLOCKED | No sender/device token/configuration or actual destination receipt; no synthetic result labelled real |
| Isolated full-service recovery | BLOCKED | Exact isolated recovery target and service bindings missing. Previous current-schema database-only restore remains PASS as previously evidenced; Auth HTTP, Storage bytes, functions/config recovery remain untested |
| GitHub protected pilot configuration | BLOCKED | Existing gh authentication used for metadata-only requests; proxy denies API access. No inference that remote secrets are absent. `api.github.com` saved as a pending network addition |
| Five moderate dev-tool findings | FAIL | Registry check shows the same pinned latest legacy plugin releases and minimatch 3.1.5; no compatible newer patch found. No unsafe override/downgrade, rule suppression or audit exclusion. See DEPENDENCY_REVIEW.md |
| Previously verified local engineering | PASS (carried forward) | Code unchanged from prior 187 SQL /18 Edge /7 recovery tests and database-only restore. Not rerun or represented as hosted/device evidence |

Expected future APK paths (not produced artifacts): `passenger-app/build/outputs/apk/release/passenger-app-release.apk` and `rider-app/build/outputs/apk/release/rider-app-release.apk`. Hashes, signing fingerprints, build commit and actual package metadata will be recorded only after successful builds. Confirm highest distributed versionCode and existing certificate before an update; do not infer first release from absent local files.

## Exact next configuration actions

1. **First:** Cloud environment settings → Internet/network access: apply the saved custom allowlist, including `api.mapbox.com`, `oauth2.googleapis.com`, `fcm.googleapis.com`, plus newly saved `api.supabase.com` and `api.github.com`; preserve existing rules/presets. Review/save and publish the development environment version as required by that UI. Saving a draft alone did not apply runtime revision 1. The legal-source domains are also preserved. This does not publish apps.
2. Configure `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY` and `MAPBOX_PUBLIC_TOKEN` in secure environment bindings or ignored root `secrets.properties`. Supply `SUPABASE_ACCESS_TOKEN` through a secure CLI/management binding and `SUPABASE_DB_PASSWORD` if the selected database workflow requires it. Confirm this is staging. Add its exact hostname to the allowlist; do not guess from historical records.
3. Add real `passenger-app/google-services.json` and `rider-app/google-services.json` through ignored configuration; provide existing `DHWINRIDE_RELEASE_KEYSTORE_PATH`, `DHWINRIDE_RELEASE_STORE_PASSWORD`, `DHWINRIDE_RELEASE_KEY_ALIAS`, `DHWINRIDE_RELEASE_KEY_PASSWORD` via the secure signing setup. Existing identity must be preserved. Protected GitHub candidate environment details are in the handoff; its actual settings could not be inspected here.
4. In that staging project's Function Secrets, configure `MAPBOX_SERVER_TOKEN`, `FCM_SERVICE_ACCOUNT_JSON`, `OPERATIONS_WORKER_TOKEN`, `SENTRY_DSN`, and the described trusted schedules. Use Supabase-managed server bindings; never expose service-role/server/build credentials in client configuration. Configure the exact isolated recovery target and keys under the `RECOVERY_*` names in the handoff.

Use CONFIGURATION_HANDOFF.md for all exact names, locations, scopes and safe verification. Credentials must not be pasted into chat. Once access changes, first recheck effective policy and origin responses, then inspect staging inventory/dry-run and resume authorized work.

## Outstanding human approvals and guided device work

- **Operator/safety:** BLOCKED — no final accountable go/no-go or safety drill evidence. Preserve already established price, zone, hours and manual onboarding decisions; those are not being requested again.
- **Legal/insurance:** BLOCKED — actual legal entity, transport eligibility/licensing, rider/motorcycle insurance and publication approval need responsible operator/adviser evidence, not a software certification.
- **Retention/privacy:** BLOCKED — approved calendar-year schedules, location/audit/provider-log/backup retention, legal holds and published privacy/terms remain required. No approval record supplied; hosted policy state is unobserved.
- **Support/on-call:** BLOCKED — existing contact details do not prove tested coverage, response/escalation ownership or working alert channels.
- **Physical devices:** BLOCKED — the user is checking two Android versions and existing installed app versions. Prior built APK version 0.1.0 for both apps is reported as historical context, not an installation result. No install, upgrade, GPS, network-loss, trip or notification check performed. DEVICE_REHEARSAL.md procedure step 1 cannot begin without actual signed APKs/hashes. Device inventory can be collected now, one preparation step at a time.

Sanitized current evidence: `evidence/staging-recheck/configuration-status.json`. The earlier engineering evidence below remains historical/carry-forward context; it does not fill the blocked external gates.

---

# Release operations continuation — 2 November 2026 (CAT)

Verdict: **BLOCKED** for controlled pilot and public release. Engineering source commit: `c1ce84cd7fa3b0c1e1638a5a5f8eca3ff71d6356` on `feat/release-operations`. Tests ran against the same source content before/after its reviewable commits. No APK, deployed candidate, production change, public booking enablement or publication approval exists. Earlier work was preserved and committed in separate concerns; the historical record below remains context.

## Current engineering and verification

| Requirement | Status | Fresh evidence | Remaining limit |
|---|---|---|---|
| Durable authorized FCM sender | PASS locally | Transactional trip-event outbox, eligible recipients, fenced leases, stable dedupe fingerprints, six-attempt bounded retries/Retry-After, explicit invalid-token removal, stale-offer cancellation, fixed Google OAuth/FCM endpoints | Real service account, FCM acceptance/device delivery and Android compilation unverified |
| Multi-device duplicate handling | PASS locally | Review reproduced raw-token scrub/unique-key collision; new multi-device regression now passes. Stable fingerprints permit both acknowledgements after raw token erasure | FCM transport remains at-least-once; Android stable event suppression is implemented but uncompiled/unexercised |
| Account closure/retention | PASS locally | Active-trip rejection, caller-only/idempotent closure, immediate restrictive RLS/write boundaries, Auth tombstones, legal/dispute holds, fenced resumable Storage→rows→Auth erasure, approved bounded cleanup | Real Auth ban/delete and Storage API erasure unverified; policy/adviser approval required; purge defaults disabled |
| Retention safety | PASS locally | Rejects 360-day interval shortcut, honors calendar-year floor and longer current periods, locks policy/hold changes against active erasure leases; actually removes aged rows while preserving holds/unresolved-review GPS | Approved location interval/log/backup retention and ongoing audit-purpose review remain owner inputs |
| SQL/migrations/concurrency | PASS | All 23 migrations replayed from scratch; 11 suites/187 assertions pass both in disposable contract runner and real local Supabase DB schema. Exactly one of two concurrent rider sessions accepts | No hosted JWT, Auth HTTP, Storage HTTP or device claims |
| Edge logic and types | PASS | 18 Node tests; all four function entrypoints pass frozen Deno checks. Union of 13 exact locked npm packages: no advisories at official verified HTTPS endpoint | Native Deno audit CA issue persists in this environment; no verification bypass |
| HTTP authorization and probe | PASS locally | Real Deno entrypoints reject wrong methods/missing or wrong worker tokens; no arbitrary recipient/subject parameters. Deliberate monitoring probe reaches structured logs; unconfigured remote sink returns 503 | Live Sentry project receipt and alert delivery unverified |
| Monitoring/crash engineering | PASS server-side; Android BLOCKED | Sanitized taxonomy, private queue/backlog/heartbeat metrics; generic authenticated/rate-limited crash-marker queue; no throwable/user/coordinate payload. Worker credentials never reach clients | Android handler/notification changes need compile and device crash/restart checks. No crash-time delivery guarantee |
| Recovery tooling | PASS tests | Seven Python tests cover checksum/path safety, nested inventory, resume/non-overwrite, distinct-target confirmation and synthetic API byte roundtrip; database-only receipt cannot satisfy full-service gates | Live hosted Storage/Auth/function/config recovery untested |
| Current database-only restore | PASS | Original ownership/ACLs preserved; synthetic marker and 23 migrations recovered; 187 SQL assertions pass on restored target. Initial no-owner attempt exposed ownership mismatch and was corrected | Existing local cluster roles reused; cross-machine role restoration, Auth/session behavior, object bytes and provider configuration remain unverified |
| Dependency remediation | PARTIAL | Scoped modern minimatch 11.0.1 upgrade; dashboard 3 tests/lint/types/export pass; production dependency audit zero | Five moderate legacy dev-tool findings remain; v11 removes APIs those plugins require. See DEPENDENCY_REVIEW.md |
| Mapbox and Gradle configuration | PASS diagnosis; build BLOCKED | Proxy returns CONNECT 403 before TLS, not a Mapbox authentication response; current enforced policy excludes the host. Optional build-only Downloads:Read binding/repository filtering added; offline Gradle help/configuration passes with the optional binding absent and a synthetic offline fixture present | Apply egress setting, then test actual origin/entitlement/artifacts. No token value printed. No full Android build/lint/unit pass claimed |
| Signing/distribution | BLOCKED | Existing shared signing setup inspected; no key generated/replaced. First-release-only creation/encrypted backup/restore procedure documented | Owner must confirm established certificate/distribution history and supply secure signing/Firebase inputs |
| Workflow and source hygiene | PASS locally | Actionlint syntax/expressions pass; reviewed diff/credential-pattern scan found no actual keys/JWTs/private keys; no tests or security checks removed | Optional ShellCheck and actual GitHub runs not executed; no production release authorization |

## Exact handoff and next gates

Use **CONFIGURATION_HANDOFF.md** as the single configuration checklist: exact names/locations, public versus secret classification, least access/scopes, verification and manual network changes. Use FULL_SERVICE_RECOVERY.md for the isolated restore procedure, and DEVICE_REHEARSAL.md for actual candidate devices.

Four new migrations and changed/new functions are local only. Reconcile the entire intended staging inventory and reviewed dry-run before applying them; do not assume older pilot migrations are already hosted. Keep bookings paused and `private.retention_policy.approved=false` until actual approval. Schedulers and destination alerts must be configured and tested; no remote delivery is inferred from local tests.

Local full-service startup remains limited: Auth's literal-loopback health probe was proven to use the egress proxy; correcting only that invocation allowed Auth health. Storage still failed startup health, so no whole-stack readiness is claimed. Database-only development remains available and was reset to final migration source. No failing health check was labelled passed.

Native Android changes are source-only until Mapbox access and real Firebase/signing configuration unlock compilation/minification/tests. Preserve existing signing identity and use a higher versionCode for any distributed update. No artifact location/hash exists yet.

Preserved operating decisions: ZMW 20 cash fare, established zone/hours, manual rider onboarding, warnings/manual review rather than automatic cancellation charges, confirmed support contacts. Legal/operator approvals, retention schedule, actual support/alert ownership, physical-device journeys, hosted recovery and reviewed pilot outcomes remain separate gates. Public release still requires explicit approval after the actual candidate/evidence are reviewable.

Evidence files and hashes: `evidence/release-operations/`. These are engineering receipts, not final owner approval or device evidence. No new check was marked passed solely from a document/template.

---

## Historical onboarding/earlier continuation record (superseded where updated above)

# Current release evidence — 2 November 2026 (CAT)

Verdict: **BLOCKED** for controlled pilot and public release. No production change, hosted migration, public booking enablement, APK publication, or human approval occurred.

Source baseline: `0c94e2236dbc595d41f11188d3b2ea355188de3e`, with the working-tree changes listed below. A final release commit/artifact does not yet exist. Historical results are context only. Environment: managed Linux cloud, Node 24.19.0, Java 21, local Supabase PostgreSQL 17.11.0.004. Evidence below was obtained during this execution; local results do not certify hosted services.

## Changes and reproduced failures

The CI SQL runner selected support/bootstrap SQL as standalone TAP suites. Restricting it to `supabase/tests/database` exposed a second defect: the CLI mounts only that selected directory, so four suites could not include `../support/open-pilot-fixture.sql`. Each affected suite now contains the same five-line transaction-local fixture explicitly. No assertions or policies were removed. The corrected command passes all six suites/125 assertions. CI also preserves the Docker Hub registry override for the pg_prove image; otherwise its default ECR download was denied.

## Current verification

| Gate | Status | Procedure and evidence | Remaining action |
|---|---|---|---|
| Admin static checks | PASS | In `sites/dhw-inride-admin`: `npm run typecheck`, `npm test` (2 tests), `npm run build`, `npm run lint`; export generated | Live backend authorization remains separate |
| Admin browser/accessibility | PASS (fixtures) | Existing `tests/browser-ui.mjs`, changed only localhost target to port 3001 in an external temporary copy; 4 viewport/axe checks, keyboard/focus, filters, failure recovery, sign-in/access-denial, no runtime errors | Execute with actual staging identities; intercepted fixtures are not server authorization proof |
| Landing/distribution guards | PASS | `npm run build --prefix sites/dhw-inride-landing`: 7 tests, JS syntax, release validation; both releases null | Real APKs, hashes, admin HTTPS URL and approved legal URLs unavailable |
| Root dependency audit | PASS | `npm audit --audit-level=high`: zero vulnerabilities | Recheck candidate locks before publication |
| Dashboard dependency audit | FAIL (moderate dev-tool advisory) | New minimatch advisory GHSA-g3hv-75wg-m249; 15 affected dependency paths, no high findings; compatible `npm audit fix` did not resolve it | Review upstream compatible remediation; do not force downgrade ESLint or silently override incompatible APIs |
| Edge input logic | PASS | `node --test --test-isolation=none supabase/functions/tests/*.test.mjs`: 6 passed, 0 skipped | Actual JWT gateway/routing execution requires configuration |
| Edge types | PASS | Official Deno 2.9.6 `check --frozen` with committed config/lock | Hosted execution unverified |
| Edge advisory inventory | PASS (alternate verified transport) | Exact 13 npm package versions from Deno lock submitted to official npm HTTPS bulk endpoint using Node CA trust/environment proxy: empty advisory response | Native Deno audit remains BLOCKED by UnknownIssuer even with supported CA options; TLS was never disabled |
| Pilot preflight | PASS (logic/configuration only) | `python3 -m unittest discover -s scripts/tests -v`: 8 passed | Final preflight must fail until real signed/device/legal evidence exists |
| Migrations | PASS (local) | All 19 migrations replayed into local Supabase PostgreSQL; migration inventory count 19 | Reconcile intended hosted inventory and review dry-run before changing staging |
| SQL authorization/workflows | PASS (local) | `SUPABASE_INTERNAL_IMAGE_REGISTRY=docker.io DO_NOT_TRACK=1 ./node_modules/.bin/supabase db test --local supabase/tests/database`: Files=6, Tests=125, Result PASS | Hosted JWT/RLS and mobile behavior remain unverified |
| Concurrent acceptance | PASS (local) | Existing `scripts/run-sql-contract-tests.py` via local Docker psql adapter: 19 migrations, 125 assertions, exactly one winner in two concurrent rider sessions | Hosted/device race still required |
| Database restore | PASS (local mechanics only) | Custom-format pg_dump restored to separate `release_restore_rehearsal` database; synthetic marker recovered and 19 migrations counted; disposable target/probe removed. Restore required local `supabase_admin` for internal Realtime function settings | No ACL/ownership certification (`--no-owner --no-acl`); no real data, hosted recovery, Storage bytes, cross-machine or production credential recovery tested |
| Full local Supabase service stack | BLOCKED | Migrations succeeded but Auth/Storage health checks failed and CLI shut stack down; database-only startup succeeds | Diagnose service health before claiming GoTrue/Storage/Realtime HTTP readiness |
| Intended hosted project/Auth | BLOCKED | No Supabase project/client/management bindings present; no hosted mutation attempted | Secure intended staging configuration and separate test identities |
| Mapbox/routing | BLOCKED | `api.mapbox.com` proxy CONNECT returned 403; no token present | Apply saved egress addition, configure tokens securely, validate real routes |
| Android current candidate | BLOCKED | SDK 37/build tools installed; Gradle 9.6 downloaded with Java proxy/CA; installed full checksum-verified Temurin JDK 21.0.9+10 because original runtime lacks javac | Full-JDK retry reached core AAR metadata resolution and failed with HTTP 403 for both Mapbox 11.33.0 artifacts; no unit tests/lint completed. Apply egress addition; real Firebase config needed for app builds |
| Signing/install/upgrade/device | BLOCKED | Both `validatePilotRelease` tasks executed and correctly refused missing keystore/password/alias settings (expected guard rejection, not release-build success). No Firebase config/device/emulator supplied; no /dev/kvm; no APK produced | Secure signing configuration and real two-phone rehearsal |
| FCM dispatch/monitoring | BLOCKED | No configured trusted FCM sender/monitoring destination or end-to-end delivery evidence | Complete sender integration and provider configuration, test delivery/alerts; token registration is insufficient |
| Retention/account deletion | BLOCKED | Policy placeholders and no verified end-to-end deletion/retention implementation | Owner approves retention/legal holds; implement and test Auth/profile/documents/location cleanup consistent with those rules |
| Zambia legal/publication | BLOCKED | Existing privacy/terms remain drafts; official RTSA notice and DPC requests returned HTTP 403 through restricted proxy | Apply saved network additions and verify current official requirements with qualified local adviser/operator; no legal compliance certification |
| Public go/no-go | BLOCKED | Owner approval false; signed/staging/device/safety/restore/publication gates incomplete | Review controlled pilot evidence first; public release requires separate explicit approval |

## `apply_as_driver` diagnosis

The older migration declared `current_role`, colliding with SQL CURRENT_ROLE (type `name`), producing the enum comparison error. Migration `20261029100000_pilot_access_hardening.sql` already uses `actor_role`. Current local tests verify denial of passenger self-promotion and access by an admin-provisioned rider. A passenger 403 is intentional under the established manual-onboarding policy. Do not grant broader permissions. A specific hosted 403 cannot be diagnosed without confirming deployed function definition, authenticated identity/role and migration inventory.

## External handoff and work still required

1. In environment settings, apply saved custom egress additions `api.mapbox.com`, `www.rtsa.org.zm`, `www.dataprotection.gov.zm`. Preset package-manager permissions were preserved. Saving the draft did not apply or publish it.
2. Configure the intended staging URL/public key, secure Supabase management access, public/server Mapbox tokens, both real Firebase application configs, trusted FCM sender and signing identity through secure settings. Never paste values in chat. CI names are enumerated in PILOT_RUNBOOK.md.
3. Provide legal entity/registration, adviser-confirmed RTSA vehicle/operator eligibility and insurance, DPC/controller/processor and cross-border processing requirements, approved retention/deletion rules, publication URLs, named safety/on-call owners and monitored delivery destinations. Existing support contacts/pricing/zone/hours remain unchanged; no new approval was inferred.
4. Supply actual candidate devices/test accounts and perform DEVICE_REHEARSAL.md. These require human/device evidence. Notification sender, deletion/retention and live monitoring integration still need engineering and verification; they are not completed merely by supplying credentials.
5. Rerun current Android tests/lint/release builds after network/configuration is available. Record source SHA, APK hashes, package/version/minimum SDK and signing certificate; no artifact location exists yet.
6. Restore database AND private Storage object bytes to an isolated target using the real operational backup path. Prove permissions, access, monitoring, pause/resume and rollback compatibility there. Local database-only mechanics do not satisfy that release gate.

No READY verdict may be inferred from passing local checks. Continue from the recorded blockers with no production deployment until a reviewable candidate and explicit release approval exist.

## Retained evidence

Redacted SQL suite, concurrency and Android failure logs are in `evidence/current-cloud/` with SHA-256 inventory. These logs apply to the stated baseline plus working-tree fixes, not an approved signed candidate. No production release commit or APK hash is available.

## 8 November 2026 — isolated 24/7 migration verification

| Check | Result | Scope and limitation |
|---|---|---|
| Docker/database availability | PASS (isolated PostgreSQL) | Docker is available. Dedicated PostgreSQL 17.11 container `dhwinride-24x7-verify-20261108-db` is healthy on `127.0.0.1:65322`. Host port 54322 is open and the existing `New_DHW_Inride` database is ready; its Vector container is restarting. That existing stack was not reset or changed. |
| Temporary Supabase CLI startup | BLOCKED | Supabase CLI 2.121.0 startup stalled while pulling images and left no temporary project database container. `supabase status --workdir` reports the missing `supabase_db_dhwinride24x7verify` container. No repeated startup attempt was made; database checks used the isolated PostgreSQL container instead. |
| Existing migration replay | PASS (local) | Replayed all 28 pre-existing repository migrations successfully into a fresh isolated database using the test-only compatibility schema. |
| 24/7 migration and initial state | PASS (local) | Applied `20261108150000_24_7_service_availability.sql`; verified it sets `enabled=false`, 00:00–23:59:59.999999 and all seven weekdays, exposes the 24/7 policy, keeps service unavailable while paused, and preserves a synthetic Auth/profile sentinel. |
| Pause, resume, authorization and schedule | PASS (SQL simulation) | Confirmed pause blocks new bookings; authorized admin pause/resume succeeds and is audited; passenger resume/status access is denied. `Africa/Lusaka` assertions cover Sunday midnight, 03:00 and 23:59, and Monday 06:00, noon and 18:00. |
| Rollback and reapplication | PASS (isolated transaction) | On a fresh replay database, injected a failure at the end of the migration inside a single transaction and verified that settings, old Sunday restriction, and function set were restored. The migration then applied successfully. Reapplication also succeeded and paused service again. This verifies failed-transaction rollback, not a post-commit downgrade; after a committed failure, follow the deployment checklist's pause-and-forward-fix/recovery procedure. |
| Complete SQL suite | PASS | All 15 `supabase/tests/database/*.test.sql` files passed: **266/266 assertions**, 0 failures. Includes booking/status controls, authorized pause/resume, rider dispatch eligibility, trip acceptance/arrival/PIN/start/completion, cash acknowledgement, rider approval/revocation, and security/RLS regressions. |
| Android | PASS (development only) | Reran `:core-ui:testDebugUnitTest`, passenger/rider development builds, and both development lint tasks; Gradle build successful. The unit suite contains 75 tests. No physical-device test was performed. |
| Edge Functions | PASS (unit only) | `node --test --test-isolation=none supabase/functions/tests/*.test.mjs`: 18 passed, 0 failed. Hosted Edge execution remains unverified. |
| Admin | PASS (static/unit only) | `npm run typecheck`, `npm test` (4 passed), `npm run lint`, and `npm run build` all passed in `sites/dhw-inride-admin`. The changed browser fixture passed `node --check` only; Playwright/Chromium prerequisites are unavailable here. Hosted authorization remains unverified. |
| Git/scope | PASS (pre-commit) | `git diff --check` passes. No migration was applied to the existing stack or a hosted project; no service resume, deployment, or push occurred. The pre-existing `sites/dhw-inride-admin/.env.example` change is excluded from the local evidence commit. |
| Candidate verifier and Python tooling | PASS | Fixed Windows test portability: `.py` fixture tools run through the current Python interpreter. `python -m unittest discover -s scripts/tests -v`: **20 passed**. |
| Landing site | PASS (local) | `npm run build` in `sites/dhw-inride-landing`: **12 tests passed**, JavaScript syntax checks passed, and both release links correctly remain “Coming soon.” |
| Dependency audit | PASS (high threshold) | `npm audit --audit-level=high` reports 0 vulnerabilities in both root and Admin dependency trees. Recheck candidate lockfiles/CI before release. |
| Local/hosted migration inventory | BLOCKED for hosted | Repository contains 29 migrations. The existing local Supabase ledger contains 11 entries through `20261024120000`; it was not modified. Last hosted read-only inventory (4 Nov) counted 12, but exact current hosted versions/effects were not queried. Reconciliation details and per-migration gates are in [development deployment plan](DEVELOPMENT_DEPLOYMENT_PLAN.md). |
| Credential/configuration availability | PARTIAL (presence only) | Secure local files contain Android/Admin public endpoint configuration, but no release-signing bindings, Firebase app files, worker/monitoring settings or Supabase CLI management credential were available through checked environment/file locations. Values were not read or printed. The project-ref marker matches the documented development target; this is not hosted access or authorization. |

The SQL environment uses the repository's test-only local platform compatibility schema. It validates PostgreSQL migration behavior and database functions, but is not a full Supabase Auth/Storage/API deployment, hosted rehearsal, recovery-point test, or real passenger/rider journey. The 24/7 migration is not deployed; production/public bookings remain unauthorized. Evidence does not change the **4.0/10 — not ready** launch score.
