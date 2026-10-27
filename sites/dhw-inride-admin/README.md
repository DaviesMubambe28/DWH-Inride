# DHW-inRide Admin Dashboard

Authenticated operations dashboard for DHW-inRide. It reads persisted Supabase profiles, trips, rider documents, and locations. Access requires a signed-in account whose database profile has the `admin` role. Unsupported operations such as incident management are presented as unavailable; the dashboard does not use sample operational records.

## Run locally

1. Copy `.env.example` to `.env.local`.
2. Set `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` for the intended development project. Use a publishable key only; never place a secret or service-role key in a `NEXT_PUBLIC_` variable.
3. Run:

```powershell
npm.cmd install
npm.cmd run dev
```

`npm.cmd run typecheck` checks TypeScript, and `npm.cmd run build` creates a static export in `out/`. A successful export does not verify live Auth, RLS, Realtime, or production hosting configuration. Use the release gates in the repository's `docs/PRODUCTION_READINESS_CHECKLIST.md` before deployment.
