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
