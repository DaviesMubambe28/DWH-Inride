# Current blockers

Verified 9 November 2026, 18:07 CAT. These blockers are ordered by dependency and launch impact.

| Priority | Gate | Status | Exact closing evidence |
|---|---|---|---|
| 1 | Source backup and isolated restore | **BLOCKED** | Whole-database archive, role export, before/after inventory match, SHA-256 manifest, PostgreSQL 17/Supabase-compatible isolated restore, and schema/data/ACL/RLS/RPC comparison |
| 2 | Hosted migrations | **BLOCKED by 1** | Apply incident then service-fee migration to development; verify history, objects, RLS, grants, RPC denial paths and ledger integrity |
| 3 | Edge Functions | **BLOCKED by 1/2 and secrets** | Deploy reviewed sources to development; test missing/invalid/correct authorization and idempotent retries; keep schedules disabled |
| 4 | Firebase/FCM | **BLOCKED by configuration and devices** | Correct ignored client files, `FCM_SERVICE_ACCOUNT_JSON`, real foreground/background/terminated receipts and invalid-token cleanup |
| 5 | Auth/email | **BLOCKED by approved URLs and SMTP** | Non-localhost Site/redirect URLs, working sender, real confirmation/recovery/expiry/logout and role isolation |
| 6 | Monitoring | **BLOCKED by `SENTRY_DSN` and owner** | Sanitized remote event, worker/delivery failure event, alert receipt and accountable responder |
| 7 | Hosted journey | **BLOCKED by 1–6** | Passenger/rider/admin synthetic development journey including races, PIN, fare, cash, service fee and cleanup |
| 8 | Release signing/candidates | **BLOCKED by established identity and Firebase files** | Existing signer fingerprint, signed/minified APKs, hashes, package/version checks and upgrade installation |
| 9 | Full-service recovery | **BLOCKED** | Auth, Storage bytes/policies, Functions, secret references, Realtime, Firebase, monitoring and web hosting restored in isolation with measured recovery time |
| 10 | Physical devices | **HUMAN ACTION REQUIRED** | Two-phone installation, GPS/maps/search, accessibility, background/connectivity, push, upgrade and complete journey observations |
| 11 | Operations/legal | **HUMAN APPROVAL REQUIRED** | Entity, transport, rider/motorcycle/licence/insurance, privacy/terms, retention/hold/backup, support, incident and fare/area approvals |

The immediate external requirement is the existing development database password and trusted CA used only in masked prompts on the operator's Windows machine. Neither value belongs in Git or chat.
