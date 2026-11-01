# Configuration handoff — release operations

No production bookings, migrations, deployment or APK publication is authorized by this handoff. Supply values only through the stated secure settings, never chat or Git. Existing public pricing/zone/hours/support decisions remain unchanged. A project URL identifies an environment but is not proof that it is the intended staging target.

## Application and build inputs

| Exact name/file | Configure here | Classification and minimum access | Safe verification |
|---|---|---|---|
| `SUPABASE_URL` | Ignored root `secrets.properties`, environment or Gradle property | Public HTTPS staging project origin | Inspect only hostname; compare with intended project in Supabase console before build |
| `SUPABASE_PUBLISHABLE_KEY` (legacy alias `SUPABASE_ANON_KEY`) | Same Android input paths | Public client key/legacy anon JWT, protected by RLS; never service-role/secret key | Release guard validates supported public-key form; authenticate synthetic accounts and test RLS |
| `MAPBOX_PUBLIC_TOKEN` | Same Android input paths | Public `pk.` token for Maps/Styles/Tiles/Fonts and Geocoding/Directions; no secret scopes | Check prefix/presence only, then test maps/search/routes; never print the token |
| `MAPBOX_DOWNLOADS_TOKEN` | Optional build environment or user Gradle properties; GitHub repository secret `MAPBOX_DOWNLOADS_TOKEN` for quality and protected candidate environment as needed. Fork PRs do not receive it. **Not** `secrets.properties` or app BuildConfig | Build-only secret with `Downloads:Read`, only if Mapbox origin actually requires it | After proxy access works, compare unauthenticated origin response with authenticated Gradle resolution. Never pass token on command line or put it in a URL |
| `passenger-app/google-services.json` | Ignored app file; Firebase Console download | Public client project/API configuration, not a service account | Package must be `zm.co.dhwinride.passenger`; Firebase project/sender must match sender configuration. Restrict client API usage appropriately |
| `rider-app/google-services.json` | Ignored app file | Same, package `zm.co.dhwinride.rider` | Verify package/project names only, no file dump |
| `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` | Ignored `sites/dhw-inride-admin/.env.local`; deployment build environment for static export | Public client configuration only | Rebuild export; test admin access plus passenger/rider denials against actual backend |
| `NEXT_PUBLIC_MAPBOX_ACCESS_TOKEN` | Same dashboard build environment, if enabled | Public Mapbox web token, restricted to actual dashboard origins | Check configured hostname and browser map behavior; do not expose server/build tokens |
| `DHWINRIDE_RELEASE_KEYSTORE_PATH`, `DHWINRIDE_RELEASE_STORE_PASSWORD`, `DHWINRIDE_RELEASE_KEY_ALIAS`, `DHWINRIDE_RELEASE_KEY_PASSWORD` | Local secret manager/environment or ignored root `secrets.properties` | Existing Android release signing identity; path/alias metadata and secret passwords/private key | Compare certificate SHA-256 with distributed APK/Play record, then build, verify signature and install/upgrade. See release-signing/README.md; no replacement key is authorized |
| `.pilot/owner.json` | Ignored local file copied from `config/pilot-owner.example.json` | Non-secret approved operating/legal policy; keep `owner_approved=false` until actual review | `python3 scripts/pilot-preflight.py --owner-config .pilot/owner.json --configuration-only`; final gate additionally needs real candidate evidence |

Android release signing is shared by both modules today. If distribution records show different existing certificates, reconcile configuration before building; do not replace either identity. First-release keystore creation/backup instructions apply only after the owner confirms no established identity exists.

## Server and scheduler inputs

Local Edge configuration belongs in ignored `supabase/functions/.env`. Hosted values belong in the **intended staging project's Supabase Function Secrets**, not web hosting or APK configuration.

| Exact name | Classification/minimum access | Verification without exposing values |
|---|---|---|
| `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_ROLE_KEY` | Supabase-managed Edge runtime bindings. Public origin/anon key; service-role key is a privileged server secret bypassing RLS. Never send it to mobile/browser/scheduler | Confirm bindings exist in the runtime; use authenticated synthetic calls and denied RPC tests. Do not print values |
| `MAPBOX_SERVER_TOKEN` | Dedicated routing token; default public `pk.` capabilities suffice for Directions, but treat this server binding as protected. No browser-origin restriction on server egress | Synthetic in-zone Directions/request-trip test and quota/failure checks |
| `FCM_SERVICE_ACCOUNT_JSON` | Entire JSON for a dedicated Google service account in the Firebase project used by both apps. Minimum IAM permission `cloudmessaging.messages.create` (custom project role where available); OAuth scope `https://www.googleapis.com/auth/firebase.messaging`. No Owner/Editor role. Private key is a secret | Enable FCM HTTP v1 API, confirm project/package/sender alignment, then send to synthetic account tokens via queued database events. Inspect only aggregate sent/dead counts and actual device receipt; no token/log dumps |
| `OPERATIONS_WORKER_TOKEN` | At least 32 cryptographically random bytes encoded as URL-safe text; secret shared only by workers and trusted scheduler | Missing/wrong bearer must return 401. Correct bearer invokes bounded queued work; never takes arbitrary recipients/messages/user IDs |
| `SENTRY_DSN` | Optional server error-ingest DSN. Ingest capability only; never a Sentry management/auth token | Authorized `POST /functions/v1/operations-worker?action=probe`; require `delivered=true` plus matching event ID visible in the intended Sentry project and actual alert delivery. Without configuration, only local logs are verified and probe returns 503 |

Deploy reviewed `notification-worker`, `account-deletion`, `operations-worker`, and changed `request-trip` only to confirmed staging after migration review. Their committed Deno locks must remain frozen. The three new functions set `verify_jwt=false` because they validate their own credentials: account-deletion validates the user bearer with Auth; worker endpoints validate the distinct operations token before privileged access. Do not make them anonymous by removing those checks.

Run the trusted scheduler (secret vault binding, no token in URL/logs):

- Every minute: `POST /functions/v1/notification-worker`, `Authorization: Bearer <OPERATIONS_WORKER_TOKEN>`. The worker processes at most ten leased deliveries; retries respect bounded backoff/Retry-After and stop after six attempts. Offers expire after two minutes and obey fresh rider availability. No claim of exactly-once FCM delivery is made; stable event IDs/collapse keys plus Android event suppression handle ambiguous transport retries.
- Hourly: `POST /functions/v1/operations-worker?action=retention` with the same worker credential. No purge occurs before policy approval. Erasure leases expire after ten minutes; partial object batches/errors resume safely. Monitor failures rather than forcing completion.
- Every minute: authorized `POST /functions/v1/operations-worker?action=health`. Alert for any `push_dead`, pending delivery older than five minutes, expired leases, or a missing/stale notification heartbeat (>5 minutes). Alert if the hourly retention heartbeat is >2 hours old. Alert for client-error backlog older than five minutes or a missing `client-errors` heartbeat (>5 minutes). A provider/worker configuration error is an actionable failure, not a successful heartbeat.

Configure an actual named on-call owner, notification channel, escalation backup and service-hours coverage in the monitoring/scheduler consoles. Run a probe and a missed-heartbeat drill; documentation does not establish alert delivery. Android records a generic fatal-error marker locally and submits it on the next authenticated foreground sync through `report_my_app_crash`. No throwable/message, user ID or coordinates are stored in the report queue. Add a per-minute authorized `POST /functions/v1/operations-worker?action=errors` schedule to deliver those markers using stable Sentry event IDs. Android compilation, deliberate crash/restart and actual remote alert delivery still need candidate/device verification; no crash-time delivery is promised.

## Retention policy and closure

Apply all four new migrations to staging only after inspecting target state and dry-run. Use trusted database administration to review `private.retention_policy`:

- `approved` defaults false. Keep false until the responsible operator/adviser approves the actual schedule.
- `account_retention` and `trip_retention` default one calendar year and cannot be lower. Use calendar-year intervals such as `1 year` or `2 years` with nonnegative components; SQL deliberately rejects `360 days`, which PostgreSQL otherwise compares equal to twelve months. Increase for applicable tax/transport/dispute obligations; the worker respects the longest current approved period when erasing a closed account.
- `location_retention` defaults null, so location-only cleanup is disabled until an explicit interval of at least one year is approved, consistent with the current policy's stated minimum. Do not silently invent a shorter location schedule.
- `private.data_holds(user_id, reason)` supports `dispute`, `safety`, `legal`, `licensing`. Closure-specific `legal_hold` also blocks erasure. Unresolved cancellation reviews block affected erasure/trip retirement. Pause the worker and wait for active erasure leases before changing a hold/policy; SQL rejects a change racing an active erasure lease.

An authenticated user calls `POST /functions/v1/account-deletion` with their own bearer, without a target user ID. Active trips return 409. Otherwise normal data access/new activity is blocked immediately, tokens/presence are removed, and Auth is banned where available. Erasure happens after retention/holds, Storage bytes first, then personal fields, then Auth. Previously issued private-document signed URLs can remain valid for their existing five-minute TTL; do not describe account closure as revoking every previously shared link instantly. Pseudonymous profile/audit references may remain for continuing record purposes; their applicable retention needs operator review. Backup rotation/provider-log retention must also be configured; deleting live records does not erase old backups.

## Management, Auth and recovery

| Exact name/configuration | Where/classification/minimum access | Safe verification |
|---|---|---|
| `SUPABASE_ACCESS_TOKEN` | Secure CLI/CI management binding for a dedicated least-privilege project member. Supabase PATs inherit account access; do not assume a PAT is project-scoped | CLI list/migration inventory read-only first; confirm project reference and dry-run. Do not print token |
| `SUPABASE_DB_PASSWORD` | CLI/secure database connection settings if required by the selected managed workflow | Connect with TLS certificate verification; never put a password/connection string into logs/history |
| Auth Site URL, additional Redirect URLs, SMTP host/port/username/password/from address, email templates/verification settings | Supabase Auth dashboard of staging; SMTP password is a secret. These are managed Auth settings, not new invented application environment variables | Synthetic signup/confirmation/reset email at actual inbox, correct Android/website redirects, expiry/revocation tests |
| `RECOVERY_SOURCE_SUPABASE_URL`, `RECOVERY_SOURCE_SERVICE_ROLE_KEY` | Secure operator shell for `scripts/service-recovery.py backup-storage`; public project origin and privileged read secret | Create encrypted/off-repository bundle; tool prints only counts and verifies hashes, with a 64 MiB per-object limit |
| `RECOVERY_TARGET_SUPABASE_URL`, `RECOVERY_TARGET_SERVICE_ROLE_KEY` | Separate isolated recovery project's secure operator shell | `restore-storage --confirm-isolated-host EXACT_TARGET_HOST`; source and target must differ. Initial target bucket/object data must be empty; resumed exact matching data is verified and never overwritten |
| Recovery bundle, DB backup/role export, provider settings inventory, function sources/locks, secret-manager references | Encrypted off-repo storage and approved vault; never commit actual object bytes/Auth data/private keys | Follow FULL_SERVICE_RECOVERY.md, capture redacted component receipts and verify their integrity. Database-only recovery is insufficient |

## GitHub protected candidate environment

In GitHub environment `pilot`, configure variables `PILOT_SUPABASE_URL`, `PILOT_OWNER_CONFIG_JSON`; secrets `PILOT_SUPABASE_PUBLISHABLE_KEY`, `PILOT_MAPBOX_PUBLIC_TOKEN`, `PASSENGER_GOOGLE_SERVICES_B64`, `RIDER_GOOGLE_SERVICES_B64`, `DHWINRIDE_RELEASE_KEYSTORE_B64`, `DHWINRIDE_RELEASE_STORE_PASSWORD`, `DHWINRIDE_RELEASE_KEY_ALIAS`, `DHWINRIDE_RELEASE_KEY_PASSWORD`. Base64 files are still secrets/configuration, not encrypted backups. Require reviewers and branch-protection quality jobs. Workflows do not themselves configure repository protection. No workflow publishes APKs or enables production traffic.

## Network changes and Mapbox diagnosis

The observed request returned `CONNECT tunnel failed, response 403`, `server: envoy`, before TLS. No Mapbox response body, TLS identity, artifact response or token validation occurred. A fresh supported environment observation reports enforced policy and `api.mapbox.com` absent from effective allowed hosts, corroborating the proxy-policy diagnosis. Both Gradle artifact URLs use the documented Mapbox Maven repository and 11.33.0 coordinates. The optional build-only credential path now exists; it does not cure a proxy rejection. After the proxy allows the host, distinguish origin 401/403 (credentials/scope/entitlement), origin 404 (artifact/version), and origin 200. Never expose Authorization headers or token query strings during diagnosis.

Apply pending custom `api.mapbox.com` access in environment settings; required Google sender hosts are `oauth2.googleapis.com` and `fcm.googleapis.com`. Add the **exact confirmed staging Supabase hostname**, `api.supabase.com` if using management API, and the actual Sentry DSN hostname once known; do not guess a project or allow all `*.supabase.co`. Recovery additionally needs the exact isolated target hostname. Existing `www.rtsa.org.zm` and `www.dataprotection.gov.zm` additions support authoritative legal-source verification. Preserve package-manager presets and all existing custom rules. Saving a draft does not apply runtime access.

A separate local Supabase issue was diagnosed: container health probes to loopback were routed through the egress proxy. This is not a hosted Supabase failure or evidence of an application bug. Keep actual health checks; use a local-only routing correction, preserving external proxy enforcement. Record full-service health/function/Auth/Storage results separately from SQL contracts.

The Mapbox/legal-source and Google OAuth/FCM domain additions are saved in the development-environment draft. Review/save and publish that environment version as required by environment settings; this does not publish APKs or enable bookings. Project-specific/Sentry/recovery hostnames still need the actual configuration before they can be added precisely.
