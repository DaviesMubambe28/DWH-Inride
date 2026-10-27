# DHW-inRide delivery phases

This file tracks implementation phases; it does not certify a production release. Current architecture and blockers are recorded in `PRODUCTION_READINESS.md`, and evidence-based go/no-go gates are in `PRODUCTION_READINESS_CHECKLIST.md`.

## Implemented in source

- Kotlin passenger and rider applications with shared Compose UI and Supabase repositories.
- Supabase schema, row-level security policies, private rider-document storage, auth profile handling, trip financial calculations, rider approval, dispatch acceptance, pickup PIN verification, and trip lifecycle RPCs.
- An authenticated Next.js admin dashboard that reads persisted records and reports unsupported workflows without fabricating data.
- A public static landing page whose store links remain marked as coming soon.

Implementation in source does not mean the hosted project, security policies, or user journeys have been verified.

## Release sequence

1. **Establish source and release ownership.** Restore or create the intended Git remote and protect the release branch. Confirm Android signing, store accounts, website domain/hosting, support contact, privacy policy, backup owner, and incident-response owner.
2. **Confirm the development backend.** Reconcile the project reference in local configuration with the intended Supabase project. Inspect hosted schema, migration history, grants, RLS, Storage, Realtime, and Edge Function state before changing anything.
3. **Verify end-to-end workflows in development.** Apply reviewed migrations to development, then exercise signup, email confirmation, role enforcement, rider approval, booking, two-rider acceptance race, PIN/start/completion, cancellation, history, network recovery, and cross-user denials. Preserve evidence in the release checklist.
4. **Complete operational workflows and decisions.** Resolve remaining items in the readiness record, including support/incidents, notifications, retention/deletion, pricing and cancellation decisions, monitoring, backup and recovery, and payment acknowledgement handling.
5. **Prepare public website and store listings.** Publish the privacy and support information, replace coming-soon links only with approved listings, and verify production Auth redirect URLs and hosting configuration.
6. **Prepare signed candidate builds.** Configure release signing from secrets outside version control, produce Android App Bundles, and complete store policy and device checks. Keep debug APKs out of public distribution.
7. **Release in stages.** Use a controlled pilot with monitoring and rollback ownership before broad availability. Promote to general availability only after the checklist gates are complete.

## Production changes

Do not push migrations, deploy functions/sites, or publish store builds until the target project, target environment, and release owner are confirmed. For hosted Supabase changes, inspect `db push --linked --dry-run` output before any push and keep production backups and rollback steps available.
