# Authentication report

Status: **local controls implemented / hosted email journey BLOCKED**.

Fresh hosted configuration on 9 November 2026 shows a localhost Site URL, no redirect allowlist, email auto-confirm enabled and no custom SMTP. This configuration cannot verify real signup confirmation or password recovery.

Android session restoration, logout and Passenger/Rider role-boundary tests have local evidence. Database authorization tests cover cross-user and role denial. Actual inbox confirmation, reset delivery, Android recovery deep links, invalid/expired links and hosted session expiry remain open.

Required configuration is an approved Admin HTTPS origin, final Android recovery URI(s), a controlled SMTP sender and matching Auth templates. Do not enable email confirmation until delivery and recovery are observed end to end.
