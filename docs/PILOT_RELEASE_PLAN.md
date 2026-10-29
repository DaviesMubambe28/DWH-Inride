# Pilot release preparation

Target: a controlled passenger/rider pilot, selected by the owner. Public release requires the same technical evidence plus completed publication and operating approvals. Preparing software does not authorize production database changes or real trip operations.

## Work and acceptance gates

1. Patch the affected Next.js dependency without a major upgrade. Audit, existing presentation tests, browser checks, and production export must pass.
2. Run the checked-in database workflow on an isolated local Supabase stack; repair reproducible migration or test defects. Expand denial coverage where missing. No hosted resets or invented credentials.
3. Add repeatable CI gates for web, backend, and Android. Configure Android release signing from secure inputs and make missing release configuration fail clearly.
4. Correct the privacy policy's description of implemented foreground trip-location sharing. Keep legal identity, retention, contact, and approval fields explicitly pending owner completion.
5. Prepare a concrete pilot operator runbook, rollback/backup procedures, and an evidence-based release preflight. A missing account, device check, operational owner, or approved fare policy must remain a failing launch gate.
6. Reconcile the readiness record with current results. Report passed, failed, blocked, and unrun checks separately.

## External prerequisites checked

No Supabase project configuration, Mapbox token, Firebase app configuration, or Android signing bindings are present in this machine. The active network policy still denies Mapbox artifact downloads; the required domain is already saved in the environment draft. The owner-approved flat fare, warning-only cancellation, exact boundary and monitored contacts are implemented. Final legal/go approval and staging/device evidence remain pending. Supply credentials through secure settings or ignored files, never this document or chat.

## Verification

Use frozen npm installs with lifecycle scripts disabled; inspect dependency changes. Run unit tests before production builds; preserve `next-env.d.ts` during generated-type/build commands. Backend tests must execute against local services with RLS enabled. Android checks must retain the pinned SDK and existing assertions. Run real browser tests with isolated profiles and intercept fixtures only in test code. Do not claim live API, notification, device, restore, or release-signature success from static checks.
