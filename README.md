# DHW-inRide

DHW-inRide is a Chongwe, Zambia motorbike-taxi product with two Android apps, a Supabase backend, an operations dashboard, and a public landing page.

## Release status

**Not ready for public trip operations.** The Android apps, backend, dashboard, and landing page are present, but the production-readiness record and release checklist identify unresolved launch gates. Real passenger or rider use must wait until the intended Supabase project is confirmed, migrations and row-level security are verified there, the live trip lifecycle is exercised with separate passenger/rider/admin accounts, and operational and store ownership is in place.

The current code includes server-side trip creation and database RPCs for dispatch and trip state changes. That implementation has not been verified against the intended hosted project in this workspace. The rider and passenger APKs are version `0.1.0` and have a guarded release signing configuration that requires local secure inputs. The landing page still marks store links as coming soon.

## Project map

- `passenger-app/` — passenger Android application.
- `rider-app/` — rider Android application.
- `core-ui/` — shared Compose interface, models, repositories, and location handling.
- `supabase/` — database migrations, policies, Edge Functions, and database workflow checks.
- `sites/dhw-inride-admin/` — authenticated Next.js operations dashboard.
- `sites/dhw-inride-landing/` — static public marketing page in `dist/`.
- `docs/PRODUCTION_READINESS.md` — architecture, confirmed blockers, and owner actions.
- `docs/PRODUCTION_READINESS_CHECKLIST.md` — release verification evidence and gates.

## Local setup

Open the repository in Android Studio and configure Java 17, Android SDK 37, and the Android secrets described in `secrets.properties.example`. Never put Supabase secret/service-role keys or server-only Mapbox credentials in a mobile app or browser environment.

For the admin dashboard, create `sites/dhw-inride-admin/.env.local` from `.env.example` and use only a Supabase publishable key. Run `npm.cmd install` and `npm.cmd run dev` from that directory.

For local Supabase verification, start Docker Desktop and follow `supabase/README.md`. Do not run destructive reset commands against a linked hosted project.

## Builds

```powershell
.\gradlew.bat :passenger-app:assembleDebug :rider-app:assembleDebug
```

From `sites/dhw-inride-admin/`:

```powershell
npm.cmd run typecheck
npm.cmd run build
```

Build output alone does not establish production readiness. See the release documents above before distributing builds or enabling live trips.
