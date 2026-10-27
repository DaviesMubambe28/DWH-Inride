# DHW-inRide production-readiness record

## Current verdict

**Ready for local UI and authentication testing after configuration. Not ready for staging or production trips.**

The apps no longer fabricate booking, dispatch, fare, map, or payment outcomes. Source code now includes Mapbox geocoding/routing, server-side routed trip creation, fare calculation, rider approval, dispatch acceptance, PIN verification, and trip lifecycle operations. These workflows remain blocked for real use until they are verified against the intended hosted project and exercised end to end.

## Architecture found

- Passenger and rider Android apps: Kotlin, Jetpack Compose, Android SDK 37, Java 17.
- Shared mobile module: Compose UI, ViewModels, Google fused location/maps dependencies, Supabase Kotlin SDK.
- Operations dashboard: Next.js 16, React 19, TypeScript, Supabase JavaScript client.
- Backend: Supabase Auth, PostgreSQL, PostgREST, Realtime, private Storage, SQL migrations, and RLS.

## Completed safe changes

- Removed the runtime local/mock repository fallback from both Android entry points.
- Moved repository fakes to Android unit-test sources only.
- Removed the admin dashboard mock-data module and all invented KPIs, users, trips, finance records, incidents, charts, and action-success toasts.
- Connected the admin UI to authenticated Supabase reads for profiles, trips, rider documents, and locations.
- Added admin role rejection, sign-in, sign-out, refresh, Realtime refresh, loading, database error, configuration-required, and empty states.
- Removed the universal trip PIN and random rider/fare/place behavior from the Supabase repository.
- Authentication failures now return failures instead of fabricating users or reporting success.
- Location permission failure no longer emits invented Chongwe coordinates.
- Removed shared placeholder credentials. Mobile secrets now come from ignored `secrets.properties`; web values come from ignored `.env.local`.
- Added an idempotent migration for auth-to-profile creation and Realtime publication.

## Confirmed blockers

| Priority | Blocker | Impact | Required resolution |
|---|---|---|---|
| Critical | The configured Supabase project does not match the connected project; hosted schema, migration history, grants, RLS, Storage, Realtime, and functions are unverified | The apps may target the wrong backend or rely on missing/insecure hosted state | Confirm the intended development project, inspect it read-only, then apply reviewed migrations and verify policies there before production planning |
| Critical | Mapbox geocoding/routing and server-authoritative trip workflow exist in source but have not been exercised with live credentials or end-to-end accounts | Coordinates, route/fare, dispatch races, PIN, and trip state are not proven reliable in operation | Configure public and server Mapbox tokens, check real Chongwe routes, and run passenger/rider/admin workflow and denial tests in development |
| High | Rider approval and trip lifecycle RPCs exist in migrations, but their deployed state and security have not been verified | A source implementation alone does not establish safe rider activation or trip integrity | Verify migration state, grants, RLS, and lifecycle transitions against the intended development project |
| High | Local Supabase verification is unavailable because Docker Desktop is not running | Migrations and RLS cannot be integration-tested locally | Start Docker Desktop and run the commands below |
| High | FCM is not configured | Offline riders will not receive dispatch notifications | Configure Firebase apps and a trusted server notification sender, then test delivery and failure handling |
| High | Ratings, support/incidents, dispatch offer records, audit events, and rider ledger/payout workflows are absent or incomplete | Related screens cannot be treated as operational workflows | Define required workflows, add schema/RLS/server operations, and verify them before enabling those features |
| Medium | Android release signing, store setup, privacy/support information, hosting/domain, monitoring, backup ownership, and incident response are not confirmed | No accountable public release path is configured | Confirm release ownership and prepare signed candidate builds and deployment configuration |
| Medium | The folder is not a Git worktree | No reliable rollback or review history | Restore the intended checkout or establish a remote and protected release history before deployment |

## Owner action checklist

### 1. Critical â€” configure and verify Supabase

1. Open the Supabase project intended for DHW-inRide.
2. In **Project Settings â†’ API**, copy the Project URL and an enabled **publishable** key. Do not use or share the secret/service-role key.
3. Copy `secrets.properties.example` to `secrets.properties` and replace the two Supabase placeholders locally.
4. Copy `sites/dhw-inride-admin/.env.example` to `.env.local` in that same folder and replace the two placeholders locally.
5. In Auth URL configuration, set the production Site URL and add Android deep-link callback URLs after application IDs are finalized.
6. Verify by opening the apps: the configuration-required screen should disappear. Verify the dashboard shows its sign-in form.

Blocked until complete: real authentication tests and every database-backed client workflow.

### 2. Critical — configure and verify Mapbox

Mapbox geocoding and Directions calls are implemented. Configure the public token for Android location search and the server token as a Supabase Edge Function secret. Test real Chongwe pickup/drop-off pairs, route failures, quota behavior, and fare results against the documented fare formula. Keep the server token outside client code.

Blocked until complete: reliable live destination search and routed fare/trip creation.

### 3. Critical — verify workflow and operating rules

The source migrations define fare, rider approval, PIN, and trip lifecycle operations. Verify the formulas and transitions against the intended business rules, then test with separate passenger, rider, and admin accounts. Confirm remaining operating decisions: service area, cancellations/refunds, rider eligibility documents, safety escalation and response time, retention/deletion, and handling of cash or external mobile-money disputes. Do not provide secrets.

Blocked until complete: safe real-world trip operations and support response.
### 4. High â€” enable local backend verification

1. Start Docker Desktop.
2. From the repository root run `node_modules\.bin\supabase.cmd start`.
3. Run `node_modules\.bin\supabase.cmd db reset` (local database only).
4. Run `node_modules\.bin\supabase.cmd db lint --local --level warning --fail-on error`.
5. Confirm every migration completes and retain the command output for review.

Never run `db reset --linked` against the hosted project.

### 5. High â€” Firebase notifications

Create separate passenger and rider Android apps in Firebase, download each `google-services.json` into the matching app module, enable Cloud Messaging, and provide the project identifier (not a server key). A trusted Edge Function/server must send notifications.

### 6. Medium â€” release ownership

Obtain/confirm the Android signing keystores, Google Play Console account, privacy-policy URL, support contact, production domain, hosting account, backup owner, and incident-response contact. Never commit keystores or passwords.

## Verification commands

```powershell
# Android compile and unit tests
$env:GRADLE_USER_HOME = (Resolve-Path '.gradle').Path
$env:ANDROID_USER_HOME = (Resolve-Path '.android-sdk-home').Path
.\gradlew.bat '-Pkotlin.compiler.execution.strategy=in-process' :core-ui:testDebugUnitTest :passenger-app:compileDebugKotlin :rider-app:compileDebugKotlin

# Admin checks
cd sites\dhw-inride-admin
npm run typecheck
npm run build
```

## Deployment preparation

1. Apply migrations to a development branch or local Supabase first; review `db push --linked --dry-run` before any hosted push.
2. Run cross-user RLS tests for passenger, rider, and admin accounts.
3. Set dashboard environment variables in the hosting provider; deploy the static export only after authenticated API calls and refresh behavior are browser-tested.
4. Add a release signing configuration that reads secrets outside version control; build Android App Bundles with `bundleRelease`.
5. Test sign-up/verification/sign-in, empty database, denied permissions, network loss, duplicate submissions, cancellation, complete trip, payment acknowledgements, and unauthorized access.
6. Back up the database before each production migration and document rollback SQL for additive changes.

## Decisions still required

- Whether external mobile money remains acknowledgement-only.
- Incident/support workflow and retention period.
- Hosting, domain, app-store ownership, and monitoring provider.

Mapbox, the production fare/commission formula, the driver approval lifecycle, and the pickup PIN workflow are now defined. See [MAPBOX_SETUP.md](MAPBOX_SETUP.md) for the exact token configuration.

## Continuation prompt

> Continue the DHW-inRide release preparation from `docs/PRODUCTION_READINESS.md`. First confirm the intended development Supabase project and inspect its deployed state read-only. Reconcile the source migrations and functions with that project, then verify security and end-to-end passenger/rider/admin workflows in development. Implement the remaining support, ratings, notifications, and operations domains only after their business rules are confirmed. Do not reintroduce mock runtime data, do not swallow backend errors, and do not deploy or change production without explicit approval.


