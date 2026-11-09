# Edge Function verification

Verified 9 November 2026 against source `95453aa` and fresh read-only hosted metadata.

| Function | Local configuration | Hosted | Status |
|---|---|---|---|
| `request-trip` | Gateway JWT required; server-authoritative routing/fare/trip creation | Active v4, JWT required | **PARTIALLY VERIFIED** — hosted source journey not rerun |
| `notification-worker` | Gateway JWT disabled; exact operations bearer enforced in code | Absent | **BLOCKED** |
| `account-deletion` | Gateway JWT disabled; validates the caller bearer and active-trip rules | Absent | **BLOCKED** |
| `operations-worker` | Gateway JWT disabled; exact operations bearer enforced in code | Absent | **BLOCKED** |

Existing local tests cover worker authorization, notification classification/retry/dead handling, account-erasure safeguards, booking input and monitoring sanitization. They were not repeated because no Function source changed in this continuation. Hosted secret names confirm `MAPBOX_SERVER_TOKEN` is present and FCM, worker-token and Sentry bindings are absent.

Deployment remains blocked by the source backup/restore gate. After that gate, deploy to development only, verify missing/wrong/correct credentials and cross-role denial, and keep schedules disabled until live monitoring and idempotency receipts exist.
