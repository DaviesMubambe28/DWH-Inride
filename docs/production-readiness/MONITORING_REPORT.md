# Monitoring report

Status: **IMPLEMENTED locally / remote delivery BLOCKED**.

Functions use sanitized fixed error codes and avoid sending raw caught errors or credentials. The operations worker supports a monitoring probe and queued client crash markers; notification and retention failures call the same reporting boundary. Local sanitization tests are retained.

`SENTRY_DSN` is absent from development Function Secrets. No remote event ID, alert receipt, accountable alert owner or escalation response has been observed. Configure a development ingest DSN with no management access, nominate the alert recipient, send the sanitized probe, and verify worker, notification and client-crash paths before enabling schedules.
