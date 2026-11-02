# Staging recheck — 2 November 2026

**BLOCKED.** Rechecked preserved commit `53cd679` without repeating completed suites. The runtime network policy remains revision 1 without the saved custom domains; Mapbox still receives a pre-TLS proxy CONNECT 403. Required local/injected project, Firebase, monitoring, signing and recovery settings remain absent. Supabase CLI management access is unavailable; GitHub pilot metadata inspection is network-blocked, so hosted settings remain unobserved. No APK, staging deployment, real journey or device result was produced.

The current evidence report lists PASS/FAIL/BLOCKED gates and exact next actions. First apply the saved network settings, then provide the secure configuration from CONFIGURATION_HANDOFF.md. Staging deployment is authorized once the exact target/access is verified; production approval is still withheld. Device inventory preparation is pending. Legal/insurance, operator/safety, retention/privacy and support/on-call approvals remain separate open gates. Upstream metadata has no compatible new patch for the five remaining legacy development-tool findings.

---

# Release operations update — 2 November 2026

**BLOCKED for real trips/public release.** Engineering source `c1ce84cd7fa3b0c1e1638a5a5f8eca3ff71d6356` adds tested notification sending, closure/retention, server monitoring/crash-marker transport and recovery tooling. The final local suite passes 23 migrations, 187 SQL assertions, concurrency, 18 Edge tests, frozen type checks, real HTTP boundary/probe tests and seven recovery tests. A current database-only restore preserves ownership/ACLs and passes all 187 SQL assertions in the restored target; it does not prove full-service recovery.

Modern dependency remediation passes dashboard types/build/lint/3 tests; five moderate legacy development-tool findings remain, with zero production dependency advisories. Mapbox failure is a pre-TLS proxy CONNECT rejection. Gradle configuration passes offline; Android code is still uncompiled, and real Firebase/signing/FCM/staging/monitoring/device/operator/legal evidence is absent.

See [current evidence](production-readiness/CURRENT_RELEASE_EVIDENCE.md), [single configuration handoff](production-readiness/CONFIGURATION_HANDOFF.md), [full recovery procedure](production-readiness/FULL_SERVICE_RECOVERY.md) and [device rehearsal](production-readiness/DEVICE_REHEARSAL.md). Keep public bookings/downloads disabled. Prior records below are historical context.

---

# Current continuation — 2 November 2026 (CAT)

**BLOCKED for real trips and public release.** See [current release evidence](production-readiness/CURRENT_RELEASE_EVIDENCE.md) for results against baseline `0c94e2236dbc595d41f11188d3b2ea355188de3e` plus the recorded working-tree fixes. This section supersedes historical verification claims below.

Current checks: admin types/build/lint/2 unit tests and fixture browser/accessibility checks; landing 7 tests; Edge 6 input tests/type check/13-package advisory inventory; 8 preflight tests; all 19 local migrations and 125 SQL assertions; exactly one winner in a real two-session local acceptance race. Local database-only dump/restore recovered a synthetic marker and migration inventory, without ownership/ACL or Storage recovery certification.

Fixed CI SQL suite discovery, fixture isolation and test-image registry selection. Corrected stale draft terms for flat cash fare/cancellation and privacy text for persisted rider positions. Android now has SDK/full JDK/proxy trust but remains blocked by Mapbox dependency egress. No signed APK or current Android test/lint pass exists. Dashboard moderate dev-tool advisories remain unresolved. Native Deno audit has a CA issue; the exact locked inventory was checked through verified official npm HTTPS instead.

Staging credentials, Firebase/FCM sender, signing, current device journeys, operational monitoring, complete retention/deletion implementation, hosted/Storage recovery and legal/operator approvals remain incomplete. No production action or release approval occurred. [Device rehearsal](production-readiness/DEVICE_REHEARSAL.md) is prepared and unexecuted. Earlier records below are historical context only.

---

# Pilot readiness — 29 October 2026

**NO GO for real trips.** The software has additional tested controls, but legal/publication approval, external configuration, Android validation, and staging/device evidence are still missing. Pilot traffic starts paused.

## Implemented and checked here

- Next.js patched from 16.4.0 to 16.4.3. Dashboard dependency audit: zero vulnerabilities. The compatible security release notes were reviewed.
- Dashboard lint is enforced with the supported Next.js configuration and ESLint 10; no rules were suppressed. A keyed session boundary clears operations state on identity changes.
- Admin-only provisional rider/motorcycle provisioning; audited activation requires document review, safety briefing, and physical-device checks. Rider self-signup is hidden in the pilot app.
- Client direct trip writes and sensitive profile writes revoked. Reproduced/fixed the `CURRENT_ROLE` collision in `apply_as_driver`; tests demonstrate both denied writes and valid rider access.
- Paused-by-default booking control, 06:00–20:00 CAT Monday–Saturday, cash-only new trips, a required configured geographic boundary, and fresh in-zone rider availability for dispatch. Offline availability is removed. Active trips can finish after a pause.
- Admin-only dashboard pause switch with an audit record. Public clients cannot enable the service.
- Request-body limits, null/type/coordinate/payment validation, finite routing metrics, bounded external routing timeout, sanitized booking failures, and a pinned/locked Edge SDK.
- Both app release configurations require externally supplied signing and real Firebase/public-client configuration; unsigned release output is refused. Release shrinking is configured.
- PR/main quality workflow and protected-environment candidate workflow written and actionlint-validated. Neither has run on GitHub yet; branch protection/environment approval settings still require repository-owner setup.
- Privacy location disclosure now describes implemented foreground availability/trip sharing. Legal/publication placeholders remain unresolved.

## Verification evidence

| Check | Result | Limit |
|---|---|---|
| PostgreSQL 17.11 migration replay | 19 migrations passed | Test-only Auth/Storage compatibility bootstrap, not full Supabase services |
| SQL contracts | 125 assertions passed | Includes RLS/role denial, onboarding, hours/zone/cash, stale/offline presence, active-trip completion after pause |
| Concurrent acceptance | Exactly one of two rider sessions succeeded | Real PostgreSQL sessions; not an on-device/hosted race test |
| Edge input/response logic | 6 Node tests passed | Does not validate hosted JWT gateway, actual routing or FCM |
| Edge Function type check | Passed with official Deno 2.9.6 and locked SDK | Host deployment/runtime execution unverified |
| Locked Edge advisory inventory | 13 npm runtime entries, zero advisories | Native Deno audit has a proxy-CA failure; the exact inventory was checked through npm's official HTTPS advisory endpoint without disabling TLS |
| Pilot preflight logic | 8 Python tests passed | Approved configuration passes; final legal/evidence approval remains required |
| Dashboard | Lint/types/unit tests/browser checks passed; production export checked | Browser API requests use isolated fixtures, not a live project |
| Android | Release guard fails clearly on missing signing inputs | App compilation/unit tests/lint/device checks remain blocked by Mapbox egress/Firebase configuration |
| Signed APK, restore, notifications, live trips | Not verified | No artifacts, credentials, staging identities, devices, or operational proof supplied |

Historical records mentioning 31 Android tests/debug builds or staging catalog checks predate the latest changes. They are not a pass for this candidate. The full local Supabase Docker attempt stopped on image-extraction storage exhaustion; no test failures were hidden or replaced with a full-stack readiness claim.

## Required next inputs and work

1. Final operating policy is implemented: ZMW 20 total cash fare, no distance pricing; cancellations create warnings/manual review only, with a two-minute passenger window and five-minute rider no-show wait. No cancellation fee or debt is collected. The existing internal 15% allocation remains within the ZMW 20 total.
2. Exact approved boundary: -15.3292, 28.6820, radius 4.0 km. Support: +260 971 793 185, +260 76 174 1656, garumitech@gmail.com. Complete legal/publication/retention and final safety/go approval.
3. Apply the saved `api.mapbox.com` network allowance. Supply real staging Supabase, Mapbox, Firebase/FCM and signing configuration securely. No secret values belong in chat or Git.
4. Compile/test/lint both Android apps, build a signed/minified candidate, and execute the staging/device/notification/backup-restore and safety rehearsals. Configure the real trusted FCM sender and monitoring; token registration or documentation alone is not delivery evidence.
5. Review the redacted evidence and final preflight. Only the responsible operator may then authorize a limited cohort and enable the configured pilot through a trusted administrative interface. Public release is a separate decision.

See [the runbook](PILOT_RUNBOOK.md) for exact commands, credential names, rehearsal coverage, evidence requirements, stop conditions, and rollback constraints.
