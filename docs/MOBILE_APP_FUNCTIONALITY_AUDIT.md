# DHW inRide Mobile Functionality and Readiness Audit

**Audit date:** 18 October 2026  
**Current verdict:** **Partially functional**  
**Can real passengers and riders safely use it today? No.** This execution could not run Gradle or start ADB. The connected Supabase account lists a project, but the project URL configured in local secrets points to a different project, so I did not inspect or modify either remote database. Source review found and corrected a rider-state defect, but the correction remains uncompiled and untested.

Evidence labels: **IMPLEMENTED** means code exists; **UNIT-TESTED**, **INTEGRATION-TESTED**, **EMULATOR-VERIFIED**, and **MANUALLY VERIFIED** require that evidence in the current run; **BLOCKED** identifies a check prevented by unavailable tooling or external access; **FAILED** means a check ran and failed.

## 1. Original blockers

| Area | Status in this execution | Evidence and limits |
|---|---|---|
| Passenger booking | **Partial — IMPLEMENTED, BLOCKED for tests/integration.** | Existing code calls authenticated `request-trip`, validates locations, checks the current Auth user, reconciles an active trip before retry, and reports errors. No current build, test, or backend request could run. |
| Rider request feed | **Partial — IMPLEMENTED; source defect corrected, verification BLOCKED.** | Existing repository uses Supabase Realtime and queries the feed; database source includes an atomic `accept_trip` RPC and a pending feed-policy migration. The ViewModel previously copied an unaccepted offer into `activeTrip`. It now tracks `selectedRequest` separately and sets `activeTrip` only after successful acceptance. No compile, test, RLS, Realtime, or race check ran. |
| State persistence | **Partial — IMPLEMENTED, BLOCKED for lifecycle checks.** | Passenger and rider ViewModels use `SavedStateHandle`; passenger trip state is fetched from Supabase. No rotation, process restoration, or authenticated state reconciliation was observed in this execution. |
| Fare and dispatch | **Partial — IMPLEMENTED, BLOCKED for tests/integration.** | Fare calculator uses configurable ZMW rates, Haversine kilometres, decimal arithmetic and ngwee rounding. Server-side route pricing is present in source. Rates are not owner-approved and backend integrity was not tested. |

## 2. Relevant implementation files

- `core-ui/src/main/java/zm/co/dhwinride/core/data/Repositories.kt` — repository contracts.
- `core-ui/src/main/java/zm/co/dhwinride/core/data/SupabaseRepositories.kt` — Supabase trip creation, feed, Realtime, reconciliation and RPC operations.
- `core-ui/src/main/java/zm/co/dhwinride/core/ui/passenger/PassengerViewModel.kt` — booking and passenger state.
- `core-ui/src/main/java/zm/co/dhwinride/core/ui/rider/RiderViewModel.kt` — rider feed and acceptance state; now separates a selected offer from an assigned active trip.
- `core-ui/src/main/java/zm/co/dhwinride/core/ui/rider/RiderApp.kt` — request screen displays `selectedRequest`.
- `core-ui/src/main/java/zm/co/dhwinride/core/domain/FareCalculator.kt` — configurable fare arithmetic and Haversine distance.
- `supabase/functions/request-trip/index.ts` — authenticated request endpoint and Mapbox route lookup.
- `supabase/migrations/20261016013132_production_trip_workflow.sql` — source for protected lifecycle operations and atomic rider acceptance.
- `supabase/migrations/20261017120000_rider_available_feed_policy.sql` — stricter unassigned-request visibility policy; source file only, deployment not verified.

## 3. Booking and duplicate prevention

The passenger repository derives the user from the Supabase Auth session, validates coordinates, refreshes active trips before creating a request, and attempts to reconcile an ambiguous request failure by reading server state. The Edge Function validates its request and uses a server-only route token. The server workflow migration defines a unique active-trip constraint and authoritative pricing. These are **source-review findings**, not deployed-backend evidence.

There is no general idempotency key. If a request commits but the app cannot read the resulting active trip, the outcome may remain unknown. Do not blindly retry such a request. Current execution did not validate this path against Supabase.

## 4. Rider feed and dispatch

The intended broadcast feed allows multiple eligible riders to see a request. PostgreSQL RLS must enforce approval and online status; client filtering is not authorization. `accept_trip` is designed to use the authenticated rider and conditionally assign an unassigned requesting trip. The losing rider must receive a conflict and refresh.

This execution found that the ViewModel had treated an offer as an assigned trip before server acceptance. The state was separated: selecting/displaying an offer uses `selectedRequest`; `activeTrip` is populated only from the successful acceptance result or the authoritative active-trip stream. A test was updated to assert that distinction. **Neither change has been compiled or tested here.**

The rider-feed migration is present but its application status is unknown. No live Realtime delivery, RLS isolation, or concurrent database acceptance was verified.

## 5. State restoration

The passenger ViewModel persists screen, selections, labels and trip ID in `SavedStateHandle`; rider state persists limited form/navigation values. Server-owned trip status must be reloaded after restoration. Passwords and access tokens should not be placed in saved UI state.

Rotation, navigation, background/foreground, process restoration, and no-duplicate-booking behavior were **BLOCKED** in this execution because ADB could not initialize its Android user directory. No emulator behavior is claimed.

## 6. Fare and pricing

The formula implemented in the core service is `max(minimumFare, baseFare + distanceKm × perKmRate)`. It uses straight-line Haversine distance in kilometres, not road distance, and rounds ZMW to two decimal places (integer ngwee) using the configured rounding mode. The server function is intended to calculate authoritative fare from routed distance/time. Product approval of rates, fees, service area, and cancellation policy is outstanding; server configuration and data integrity remain unverified.

## 7. Verification performed in this execution

Requested command:

```powershell
.\gradlew.bat --no-daemon "-Pkotlin.compiler.execution.strategy=in-process" clean :core-ui:testDebugUnitTest :passenger-app:testDebugUnitTest :rider-app:testDebugUnitTest :passenger-app:assembleDebug :rider-app:assembleDebug :passenger-app:lintDebug :rider-app:lintDebug
```

**Result: BLOCKED before Gradle configuration or compilation; exit code 1.** The wrapper attempted to download Gradle 9.6.0 from `services.gradle.org`; the environment denied the network socket (`java.net.SocketException: Permission denied: getsockopt`). Setting `GRADLE_USER_HOME` to the temporary directory made its cache writable but did not provide network access. Thus this execution has no test counts, APK build result, or lint result. Earlier results written in a previous version of this audit are historical and are not counted as current verification.

ADB was present at `C:\Android\Sdk\platform-tools\adb.exe`, but `adb devices -l` failed with `Cannot mkdir '\.android': Permission denied`. No emulator, AVD, install, launch, or UI test was verified.

The connected Supabase account returned one project, but its project reference does not match the host configured in local `secrets.properties`. I did not query the connected project's schema because it may not be the intended development backend. The `supabase` PowerShell shim was blocked by execution policy; calling `supabase.cmd --version` directly then failed because the CLI could not write its telemetry file under the protected user profile. Docker could not connect to the local engine pipe. The intended remote/local DB state was not verified. No SQL migration was applied.

## 8. Remaining defects and risks

### Critical

- Intended Supabase project, deployed Edge Function, migration history, RLS, RPC permissions, and real passenger/rider workflows are unverified.
- The rider selected-request correction is not compiled or tested.
- Authoritative pricing and the production pricing policy are unapproved/unverified.

### High

- No end-to-end idempotency key; ambiguous booking outcomes depend on active-trip reconciliation.
- Authenticated booking-state restoration, rider assignment race behavior, Realtime delivery, and lifecycle transitions have no current-run evidence.
- GPS permission/current-location behavior, rider onboarding and profile persistence, payment processing, and rating persistence remain incomplete or unverified.

### Operational

- Privacy and location retention, support/emergency response, crash monitoring, backups/recovery, release signing, and device coverage remain incomplete or unverified.
- Both debug lint tasks could not run, so current warnings are unknown.

## 9. Required next actions

1. Restore access to the Gradle 9.6 distribution (or provide a pre-cached distribution) and rerun the exact command above; resolve any compile/test failures.
2. Set `ANDROID_USER_HOME` to a writable Android user directory and verify `adb devices -l`, then perform authenticated lifecycle tests on the intended AVD/device.
3. Align Supabase access/configuration to the intended development project (the configured host and the single connected project differ), then inspect deployed migration/function state; do not share keys or tokens.
4. Apply the feed-policy migration to development only after reviewing the actual deployed policies, then run database RLS and two-rider concurrency tests.
5. Configure the server-only Mapbox token through Supabase function secrets, approve pricing and operational rules, and run two-account booking/dispatch tests.

## 10. Assessment

**Partially functional.** Core code for booking, feeds, acceptance, persistence, and fare handling exists, but current compile/test/emulator/backend evidence is unavailable and a rider-state defect was discovered during inspection. **Can real passengers and riders safely use this application today? No.**
