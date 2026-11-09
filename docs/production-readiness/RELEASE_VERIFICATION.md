# Android release verification

Status: **BLOCKED — no signed/minified candidate exists**.

Verified development artifacts remain version `0.1.0-development-no-push` and use `.development` application IDs. They passed unit/build/lint checks but cannot be published or used as release-upgrade evidence.

Candidate creation requires both real Firebase files, the established signing identity, expected certificate fingerprint and protected owner configuration. The resulting Passenger and Rider APKs must pass package/version/SDK, R8 mapping, resource shrinking, non-debuggable manifest, signature/fingerprint, SHA-256 integrity and same-identity upgrade installation. Landing release metadata remains disabled until those receipts and operator approval exist.
