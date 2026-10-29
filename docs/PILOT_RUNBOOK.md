# Controlled pilot operator runbook

Status: **paused; real passenger/rider trips are not approved yet**. This is preparation for a controlled pilot. It is not public-release authorization or evidence of transport/legal approval.

## Confirmed operating rules

- Hours: Monday–Saturday, 06:00–20:00 CAT (`Africa/Lusaka`). New requests and dispatch stop outside that window; already active trips can finish.
- Payment: cash only. No mobile-money booking option during the pilot.
- Riders: manually provisioned and approved by an administrator. Check NRC, valid Class A licence, motorcycle registration/insurance, an in-person safety briefing, and the physical device. The backend requires these attestations before activation and records the operator.
- Dispatch: both trip endpoints must be inside the owner-approved radius. An approved online rider must have an in-zone availability position recorded within two minutes. Going offline removes that availability position.
- Operations can use **Pause new bookings** in the dashboard. The server verifies admin authority and records the pause. Existing trips remain supported.

## Finalized policy and remaining approval

The owner approved ZMW 20 flat cash fare within 4.0 km of latitude -15.3292, longitude 28.6820. Distance and duration do not change the fare. The existing internal 15% allocation is included in that total.

Passenger cancellation within two minutes of acceptance is free; later cancellation produces a warning and manual review, with no automatic fee/debt. Riders can report no-show only five minutes after server-recorded arrival. Other rider cancellations require manual review. Operations acknowledges reviews in the dashboard.

Monitored support: primary/WhatsApp +260 971 793 185; secondary +260 76 174 1656; email garumitech@gmail.com. The app provides dial/email actions.

Copy `config/pilot-owner.example.json` into ignored `.pilot/owner.json`. Approved operating parameters are populated and configuration-only validation passes. Keep `owner_approved` false until legal entity, published privacy/terms, retention/deletion, operating authorization, insurance, safety ownership and candidate evidence are reviewed. Never put API credentials, keystores, passwords or rider documents in this policy file.

## Technical setup still required

Supply the intended development/staging Supabase URL and public key, real Mapbox routing/public tokens, both Firebase `google-services.json` files, and a trusted FCM sender through secure settings/ignored files. This machine currently has none of those bindings. Save the pending `api.mapbox.com` network addition in environment settings and retry Android checks. Do not infer hosted state from the historical staging record.

Before a hosted migration, confirm the target project read-only and review the exact migration diff/dry run. Take a backup and verify the restore path first. Migrations `20261029100000` through `20261029150000` have been tested locally and have **not** been applied to a hosted project. They tighten client write grants, enforce manual activation, and initialize a paused pilot.

The full Supabase Docker test stack could not fit its PostgreSQL image on this environment's storage driver. SQL contracts were tested using PostgreSQL 17.11 with a clearly identified test-only Auth/Storage compatibility schema. This proves SQL logic and database-role behavior, not GoTrue, Storage HTTP, Realtime, Edge gateway, or hosted JWT integration. Run the full Supabase CI job and authenticated staging tests separately.

## Onboarding a rider

1. An authorized operator creates and verifies the Auth identity using the secure administrator console. Never ask a rider to share their password with operations.
2. In dashboard Rider verification, select that identity and enter the motorcycle details. **Create provisional rider** records a pending rider and motorcycle atomically. Accounts with an active passenger trip cannot be provisioned.
3. Review the actual documents/insurance, complete the in-person briefing, install the candidate on the rider's device, and test it. Check each corresponding confirmation only after doing it.
4. Approve/activate. The backend records the reviewing administrator. The rider must then sign in, grant location permission, and deliberately go online. Activation itself does not put the rider online.
5. On the staging project, prove that a pending/rejected rider and an out-of-zone/stale-location rider receive no dispatch. Do not use public-key possession as evidence of authorization.

## Candidate build and verification

The `Pilot quality gates` workflow is written for PRs/main and validates web lint/types/tests/build/audit, browser accessibility/flows, Edge checks, Supabase pgTAP, and Android shared tests/lint. Its YAML has been checked locally; the workflow has not run on GitHub yet. Require its three jobs in branch protection; that repository setting is not configured by a workflow file.

Create a protected GitHub environment named `pilot` with required reviewers. The manual `Build signed pilot candidate` workflow requires:

- Environment variable `PILOT_SUPABASE_URL` and JSON variable `PILOT_OWNER_CONFIG_JSON` containing the completed non-secret owner policy.
- Secure public-client configuration bindings `PILOT_SUPABASE_PUBLISHABLE_KEY` and `PILOT_MAPBOX_PUBLIC_TOKEN`.
- `PASSENGER_GOOGLE_SERVICES_B64`, `RIDER_GOOGLE_SERVICES_B64`, and `DHWINRIDE_RELEASE_KEYSTORE_B64`.
- `DHWINRIDE_RELEASE_STORE_PASSWORD`, `DHWINRIDE_RELEASE_KEY_ALIAS`, and `DHWINRIDE_RELEASE_KEY_PASSWORD`.

The workflow checks Firebase package IDs, refuses missing signing configuration, builds/minifies/lints both release APKs, verifies signatures, and records hashes/source commit. Do not put service-role keys in client bindings. It produces a candidate for testing; no Play upload, production deployment, migration, or traffic enablement is automated. No signed candidate has been produced here.

Before distributing an update, set and verify a higher Android `versionCode` for both apps and retain the same signing identity. Test install and upgrade on physical devices. Do not roll back Android by asking users to uninstall and lose local state; pause new bookings first and ship a tested forward fix with a higher version code.

## Staging rehearsal and release evidence

Use separate passenger, second passenger, approved rider, second rider, pending rider, and admin accounts in the intended staging project. Cover:

- Sign-up/verification/sign-in, expiry/sign-out, and no cross-account data leakage.
- Flat ZMW 20 quote and confirmed fare (routing must not alter pricing), inside/outside service boundary, cash-only requests, closed hours, and the selected cancellation policy.
- Two riders accepting the same request; exactly one may succeed. Denied direct writes must stay denied.
- Arrival, invalid/valid PIN, completion after closing, cash acknowledgements, valid/duplicate/unauthorized ratings.
- Foreground GPS, permission revocation, stale/out-of-zone/offline dispatch, process death/restart, and weak/lost connectivity on physical devices.
- Actual FCM dispatch delivery to an offline app, failed delivery behavior, and recovery. Token-registration source alone does not prove notification sending.
- Support-contact test and an operator safety/outage drill with the responsible person on duty.
- Database **and private Storage object** backup/restore into an isolated restore target. A schema dump or a backup checkbox is not a restore drill; database backups do not by themselves prove object-byte recovery.

Keep redacted candidate-specific evidence under `docs/production-readiness/evidence/`. Record source commit, test identities (no credentials), expected/observed result, reviewer, timestamp, and file SHA-256. Never publish JWTs, PINs, phone numbers of test participants, private rider documents, or signing material in logs/artifacts.

Run configuration validation first:

```sh
python3 scripts/pilot-preflight.py --owner-config .pilot/owner.json --configuration-only
```

That validates the approved operating configuration, without requiring final legal/go approval. For the final evidence gate, supply an ignored JSON manifest:

```json
{
  "commit": "FULL_CANDIDATE_COMMIT",
  "checks": {
    "staging_trip_lifecycle": {
      "status": "passed",
      "reviewer": "ACTUAL_REVIEWER",
      "verified_at": "ISO_TIMESTAMP_WITH_TIMEZONE",
      "artifact": "docs/production-readiness/evidence/REDACTED_FILE",
      "sha256": "ACTUAL_FILE_SHA256"
    }
  }
}
```

Populate every gate listed in `REQUIRED_EVIDENCE` in the script, using current evidence from this candidate. The script checks commit, paths, dates, and hashes; an accountable human must review the content. Passing it does not manufacture integration or safety evidence.

```sh
python3 scripts/pilot-preflight.py --owner-config .pilot/owner.json --evidence .pilot/evidence.json
```

## Go, hold, and stop

Keep `private.pilot_service_settings.enabled=false` until every staging/device, legal and operational evidence gate is complete. Only the responsible operator may authorize the small initial cohort and configure the actual boundary/contacts using a trusted database administrative interface. No public client can enable the pilot.

During service hours, an operator must monitor new/stuck/unmatched requests, rider availability, Auth/Edge errors, notification failures, and participant support. The monitoring provider, delivery channel, and on-call person still need owner configuration. A written runbook is not evidence that alerts are delivered.

**Pause immediately** for unauthorized access, double acceptance, a trip starting without a valid PIN, unexpected fare/charge, missing safety support, or a serious service outage. Confirm the dashboard pause succeeded or use the trusted database administrative interface to set `enabled=false`. Do not strand active trips; contact participants through verified support channels. Preserve redacted incident evidence.

For rollback, stop new bookings first. Retain signed artifact hashes and the last verified web/backend versions. Review migration compatibility before changing backend code; do not reset a hosted database or automatically run destructive down migrations. Rehearse restoration on an isolated target. Resume only after the responsible operator reviews the fix and the critical staging/device checks pass again.

## Verified source references

- Next.js security release: https://github.com/vercel/next.js/releases/tag/v16.4.1 (security fixes), followed by compatible 16.4.3 backports: https://github.com/vercel/next.js/releases/tag/v16.4.3.
- Supabase server SDK 1.9.2: https://github.com/supabase/server. The published SDK README verifies `withSupabase({ auth: 'user' })` and `ctx.userClaims.id`; no speculative change to the identity claim was made.
- Supabase CLI registry support: https://github.com/supabase/cli/blob/main/apps/cli-go/internal/utils/docker.go. The local Docker attempt used the supported `SUPABASE_INTERNAL_IMAGE_REGISTRY=docker.io` override.
- Installed Next.js documentation at `node_modules/next/dist/docs/01-app/03-api-reference/06-cli/next.md` documents `next typegen`; generated type files are preserved during local checks.
