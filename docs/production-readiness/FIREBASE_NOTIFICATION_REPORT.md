# Firebase and notification report

Status: **IMPLEMENTED locally / BLOCKED for live verification**.

The repository implements device-token ownership, registration/refresh, an outbox, deduplication, leases, bounded exponential retry, provider `Retry-After`, invalid-token handling and dead delivery. Both Android apps require their real Firebase client files for normal debug/release builds; development variants deliberately omit push and are labelled `development-no-push`.

Missing inputs:

- `passenger-app/google-services.json` for `zm.co.dhwinride.passenger`
- `rider-app/google-services.json` for `zm.co.dhwinride.rider`
- `FCM_SERVICE_ACCOUNT_JSON` in development Supabase Function Secrets with messaging-send scope only
- two authorized Android devices

No Firebase file or credential was fabricated. Foreground, background, terminated-app delivery, navigation, correct user targeting and invalid-token cleanup remain unverified on real devices.
