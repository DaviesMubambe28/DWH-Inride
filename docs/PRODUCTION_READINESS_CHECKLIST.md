# DHW inRide Production Readiness Checklist

**Audit date:** 18 October 2026  
**Verdict:** **Partially functional — do not use with real passengers or riders.**

Current-run evidence is limited to source inspection and one blocked Gradle/ADB attempt. A source correction to rider offer state is implemented but unverified. “Verified complete, with evidence” is reserved for evidence obtained in this execution; source implementation alone is not verification.

## P0 — Core workflows

- [ ] **Incomplete or not yet verified.** Passenger authentication and role enforcement against intended development Auth.
- [ ] **Incomplete or not yet verified.** Passenger booking UI-to-Supabase request, server response, and failure/retry reconciliation.
- [ ] **Incomplete or not yet verified.** Duplicate booking behavior under timeout and process restoration; no general idempotency key exists.
- [ ] **Incomplete or not yet verified.** Rider feed loading, empty, populated, reconnect, stale-data and conflict states against backend.
- [ ] **Incomplete or not yet verified.** Rider selected request is now separate from assigned active trip in source; compile and unit test are blocked.
- [ ] **Incomplete or not yet verified.** Realtime delivery and refresh behavior in an authenticated rider session.
- [ ] **Incomplete or not yet verified.** Atomic acceptance race test with two distinct riders.
- [ ] **Incomplete or not yet verified.** Full trip lifecycle and unauthorized/invalid transition denials.
- [ ] **Incomplete or not yet verified.** Authenticated passenger/rider state restoration, server reconciliation, and no duplicate booking.

## P0 — Data security and integrity

- [ ] **Blocked by project mismatch.** The connected Supabase account lists one project, but its reference differs from the host configured in local `secrets.properties`. Inspect deployed RLS, grants, helper functions, RPC permissions, storage policies, migration history and Edge Function only after confirming the intended project.
- [ ] **Incomplete or not yet verified.** Apply `supabase/migrations/20261017120000_rider_available_feed_policy.sql` to intended development only after inspecting deployed policy state.
- [ ] **Incomplete or not yet verified.** Cross-user passenger/rider/admin RLS denial tests and database workflow tests.
- [ ] **Incomplete or not yet verified.** Confirm client apps contain only publishable Supabase and public Mapbox tokens; configuration values were not printed or validated in this execution.
- [ ] **Incomplete or not yet verified.** Auth session expiry/revocation, account deletion, database backup and recovery.
- [ ] **Incomplete or not yet verified.** Confirm trip fare cannot be changed by a client after acceptance and invalid status transitions are rejected remotely.

## P1 — Fare, dispatch and location

- [ ] **Incomplete or not yet verified.** Fare calculator tests, rounding and coordinate boundary cases; no Gradle tests ran now.
- [ ] **Blocked by product decision.** Approve base/minimum/distance/time/commission rates, service area, and cancellation policy.
- [ ] **Incomplete or not yet verified.** Confirm server pricing uses intended routed distance/time and Mapbox coverage/failure behavior.
- [ ] **Incomplete or not yet verified.** Configure `MAPBOX_SERVER_TOKEN` as a Supabase function secret and verify a real route response.
- [ ] **Incomplete or not yet verified.** Verify pickup GPS source, permission prompt/denial/revocation, stale location, and accuracy.
- [ ] **Incomplete or not yet verified.** Verify eligible broadcast visibility, first-winner assignment, and losing-rider feed refresh.

## P1 — Authentication and rider operations

- [ ] **Incomplete or not yet verified.** Passenger/rider sign-up, email confirmation, sign-in, session restoration and role checks.
- [ ] **Incomplete or not yet verified.** Rider profile/motorcycle persistence, document upload and admin approval lifecycle.
- [ ] **Incomplete or not yet verified.** Earnings and trip history reconcile against server data.
- [ ] **Incomplete or not yet verified.** Payment processor and payment confirmation authorization; external mobile-money acknowledgment is not payment processing.
- [ ] **Incomplete or not yet verified.** Persisted ratings, customer support, emergency and incident workflows.

## P1 — Verification results for this execution

- [ ] **Blocked by Gradle distribution network access.** Requested full command exited 1 before compilation; wrapper could not download Gradle 9.6.0 (`Permission denied: getsockopt`). No current test counts, APKs, or lint results.
- [ ] **Blocked by Android user directory permissions.** ADB could not create `\.android`; no emulator state, install, launch, or UI test was performed.
- [ ] **Blocked by project mismatch and CLI filesystem/engine access.** The configured URL does not match the connected project; the PowerShell shim was blocked, the `.cmd` CLI could not write telemetry under the protected profile, and Docker could not connect to its engine. No remote database query or migration was performed.
- [ ] **Incomplete or not yet verified.** Compile and tests for rider selected-request correction.
- [ ] **Incomplete or not yet verified.** Release build/signing, dependency review and lint warning review.

## P2 — Operations and release

- [ ] **Incomplete or not yet verified.** Privacy policy, location retention, deletion and user consent.
- [ ] **Incomplete or not yet verified.** Crash monitoring, alert ownership, backups and recovery exercise.
- [ ] **Incomplete or not yet verified.** Push notifications, offline offer behavior, contact and safety flows.
- [ ] **Incomplete or not yet verified.** Production Supabase identifiers, signing ownership, store policy and release configuration.
- [ ] **Incomplete or not yet verified.** Device coverage, operational support and incident handling.

## Top five next actions

1. Restore access to Gradle 9.6.0 (or make the matching distribution available in cache), rerun the documented full command, and fix any failures.
2. Point ADB to a writable `ANDROID_USER_HOME`, verify the emulator/device, then complete rotation, background, process restoration and install/launch checks.
3. Align the configured Supabase URL with the intended development project, then inspect actual deployed schema, policies, functions and migration history without exposing credentials.
4. Apply the reviewed rider-feed migration to development and run RLS, trip lifecycle, and two-rider concurrency integration tests.
5. Configure server-only Mapbox credentials, approve prices and operational rules, and test the complete passenger/rider workflow with separate development accounts.

## Release gate

**Do not use with real passengers or riders.** This execution could not compile or run tests, could not access the emulator, and did not reach Supabase. The rider-state correction is source-only until built and tested. Core security, pricing and operational checks remain incomplete or unverified.
