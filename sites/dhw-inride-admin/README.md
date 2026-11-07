# DHW-inRide Admin Dashboard

Authenticated operations dashboard for DHW-inRide. It reads persisted Supabase profiles, trips, rider documents, locations, and durable incident records. Access requires a signed-in account whose database profile has the `admin` role. Incident writes use admin-only audited RPCs; before that migration is deployed, the dashboard presents a safe support/runbook fallback. The dashboard does not use sample operational records.

## Run locally

1. Copy `.env.example` to `.env.local`.
2. Set `NEXT_PUBLIC_SUPABASE_URL` and `NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY` for the intended development project. Use a publishable key only; never place a secret or service-role key in a `NEXT_PUBLIC_` variable.
3. Run:

```powershell
npm.cmd install
npm.cmd run dev
```

`npm.cmd run typecheck` checks TypeScript, and `npm.cmd run build` creates a static export in `out/`. A successful export does not verify live Auth, RLS, Realtime, or production hosting configuration. Use the release gates in the repository's `docs/PRODUCTION_READINESS_CHECKLIST.md` before deployment.

## Real local backend browser checks

`npm run test:browser:local` tests real Auth/PostgREST access with isolated Chromium sessions, rather than mocked responses. It verifies admin sign-in, session restoration and sign-out, plus passenger/rider rejection from the operations dashboard. It refuses hosted API/dashboard URLs and requires an explicitly synthetic fixture. This does not verify Realtime, email, physical devices or a hosted deployment.

In the cloud/Linux development workspace, start the local Supabase services and use `scripts/rehearse-local-trip.py --keep-browser-fixture /PRIVATE-OFF-REPOSITORY/fixture.json` from the repository root. The helper creates labelled synthetic accounts and saves their credentials in a new private file. Keep that file out of Git and logs. Set `INRIDE_LOCAL_BROWSER_FIXTURE` to its path, change into `sites/dhw-inride-admin`, then start the dashboard on loopback using only its public configuration:

```sh
python3 - <<'PY'
import json, os
from pathlib import Path
fixture = json.loads(Path(os.environ['INRIDE_LOCAL_BROWSER_FIXTURE']).read_text())
assert fixture['synthetic_fixture'] is True
assert fixture['origin'] == 'http://127.0.0.1:54321'
environment = dict(os.environ, NEXT_PUBLIC_SUPABASE_URL=fixture['origin'],
                   NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=fixture['public_key'])
os.execvpe('npm', ['npm', 'run', 'dev', '--', '--hostname', '127.0.0.1', '--port', '3002'], environment)
PY
```

From another terminal in this directory, with the same fixture-path binding, run `npm run test:browser:local`. `UI_TEST_TOOLS` selects an existing tools directory containing playwright-core; `CHROMIUM_PATH` selects the installed browser. Optional `INRIDE_BROWSER_REPORT` selects the sanitized report path. The default dashboard is `http://127.0.0.1:3002`; an alternative loopback origin may be supplied as `INRIDE_ADMIN_BROWSER_URL`. Only the local app/API request paths and status codes are recorded, never credentials or request headers/bodies. External HTTP resources such as fonts are blocked during this focused check.

After the test, stop the dashboard and run `python3 scripts/rehearse-local-trip.py --cleanup-browser-fixture /PRIVATE-OFF-REPOSITORY/fixture.json` from the repository root. The helper verifies the synthetic-account labels before cleanup and removes the credential file. Retain only the sanitized result as evidence.
