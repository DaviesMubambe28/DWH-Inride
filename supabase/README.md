# Supabase backend

The timestamped migrations in `migrations/` create the DHW-inRide core schema,
private rider-document storage, PIN helpers, least-privilege grants, RLS policies,
indexes, automatic passenger profile creation, and Realtime publication.

No remote project credentials are stored in this repository.

## Apply to a hosted Supabase project

The CLI is pinned in the repository root. Run these commands from the repository root:

```powershell
node_modules\.bin\supabase.cmd login
node_modules\.bin\supabase.cmd link --project-ref YOUR_PROJECT_REF
node_modules\.bin\supabase.cmd db push --linked --dry-run
node_modules\.bin\supabase.cmd db push --linked
node_modules\.bin\supabase.cmd migration list
```

`db push --dry-run` should always be reviewed before the real push. Do not use
`db reset --linked` against production because it is destructive.

## Validate locally

Start Docker Desktop, then run:

```powershell
supabase start
supabase db reset
supabase db advisors --local --type security
supabase db advisors --local --type performance
```

The local reset recreates the database and replays every migration in order.

## Operational assumptions

- New client-created profiles always start as `passenger`.
- A trusted server/admin operation promotes an approved user to `rider` or
  `admin`; clients cannot write `profiles.role`.
- The Storage object path format is `<rider-user-id>/<filename>`.
- `rider_documents.storage_url` stores that private object path, never a public
  URL or signed URL.
- PIN hashing and verification functions are in the non-exposed `private`
  schema and executable only by `service_role`.
- Passenger clients can create a request and cancel an eligible trip. Rider
  clients can mark an assigned accepted trip as arrived. Assignment, fare,
  PIN verification, trip start/completion, ratings, totals, and ledger changes
  remain server-authoritative.
- Client SELECT grants deliberately exclude `trips.trip_pin_hash`.

Security requirements:

- Keep rider ID documents in a private Storage bucket.
- Never ship `service_role` or secret keys to Android or browser clients.
- Do not use user-editable metadata for authorization.
- Give `UPDATE` policies both `USING` and `WITH CHECK` predicates.
- Index ownership, trip-participant and foreign-key columns used by RLS.
- Keep privileged functions in a non-exposed schema with an empty `search_path` and explicit execution grants.
- Store only a salted hash of trip PINs.
- Make trip-status, fare and ledger transitions server-authoritative.
- Use cursor pagination for trip history and administration tables.
- Test cross-user denial as well as allowed access.
