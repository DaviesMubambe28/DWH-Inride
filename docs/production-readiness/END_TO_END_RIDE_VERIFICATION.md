# End-to-end ride verification

Date: 2026-11-12  
Branch: `feat/release-operations`  
Verdict: **PARTIAL — the software-controlled journey passes in the isolated local integration environment; hosted and physical-device verification is incomplete.**

## A. Initial assessment

The repository contained the passenger/rider UI, protected database state machine and current dynamic-fare flow, but the complete journey could not initially be demonstrated. The rehearsal still called the superseded `request-trip` path and assumed a fixed K20 fare, while the applications use `fare-quote` followed by authenticated `accept_fare_quote`. The existing local Supabase state also had migration history without the application schemas and an outdated Auth schema. Interrupted rehearsals exposed two verifier defects: numeric PostgREST values were compared as native numbers and fixture cleanup deleted accepted quotes before their trips.

The isolated environment was repaired without changing hosted data: all 37 repository migrations were replayed, the local Auth service applied its bundled migrations, and the current fare handler was exercised with live Mapbox routing. The verifier was updated to select either booking function, accept a quote through the authenticated RPC, validate dynamic accepted/final fares, and remove only labelled synthetic fixtures safely.

No physical Android app, hosted authenticated journey, Firebase delivery, email delivery or real GPS journey was performed. A build is not used as proof of those behaviours.

## B. Improvements implemented

- `scripts/serve-local-request-trip.py` can serve either `request-trip` or `fare-quote` on a selected loopback port while retaining the fixed local-only safety checks.
- `scripts/rehearse-local-trip.py` now follows the production application contract: live route/quote, authenticated quote acceptance and idempotent retry.
- Dynamic fare checks now preserve and compare the accepted snapshot while validating the server-finalized fare, rider earnings and 15% service-fee allocation.
- Dispatch checks prove that two eligible online riders see the request, an offline rider loses feed and acceptance access, and simultaneous acceptance produces exactly one winner.
- Location checks prove only the assigned rider can insert an active-trip position, the passenger can read it and an unrelated user cannot.
- Completion checks cover arrival, wrong PIN persistence, approval revocation, start, completion, two-party cash acknowledgement and duplicate-event rejection.
- History checks prove the passenger, assigned rider and admin read the same completed/payment state; an unrelated account sees no trip; the rider remains online and eligible.
- The race suite covers concurrent cancellation/start/completion and PIN-lock calls. Cleanup now handles rider applications and respects trip/quote foreign-key ordering. A guarded local-only cleanup option removes interrupted `sprint-*@test.invalid` fixtures only when their synthetic metadata also matches.

## C. Test evidence

All identities, trips and locations below were synthetic and were removed after the rehearsal. The service-open setting was restored to its original paused state.

| Stage | Result | Evidence | Remaining issue |
|---|---|---|---|
| Passenger login | PASS (local integration) | Real local Supabase Auth admin-create and password-token exchange; metadata privilege escalation denied by profile result | Android signup, email confirmation/recovery and hosted Auth were not exercised |
| Pickup/destination and route | PARTIAL | `fare-quote` called live Mapbox Directions for two Chongwe coordinates; route distance/time and dynamic fare returned | No device location permission, search interaction, GPS accuracy or map rendering test |
| Request ride | PASS (local integration) | Quote accepted by authenticated `accept_fare_quote`; one requesting trip persisted; repeated quote acceptance returned the same trip; a second active booking was rejected | Hosted development mutation remains gated by verified backup/restore |
| Rider receives booking | PASS (local HTTP feed) | Both approved online riders read the trip from `rider_available_trips`; after going offline, Rider B saw no request and `accept_trip` returned HTTP 403 | Actual Supabase Realtime websocket reconnect and background FCM delivery are not verified |
| Rider accepts | PASS (local integration) | Two authenticated riders called `accept_trip` concurrently; results were exactly one `true`, one `false`; loser could not mark arrival | Physical two-phone synchronization remains unverified |
| Passenger sees match/location | PASS (backend authorization) | Assigned rider inserted `trip_locations`; passenger read the row; loser/outsider were denied; authorized trip view linked one rider | Passenger Android presentation and real GPS movement remain unverified |
| Rider arrives | PASS (local integration) | Only assigned rider moved `accepted` to `arrived`; repeated arrival returned false | Physical rider controls were not tapped |
| Passenger pickup / PIN | PASS (local integration) | Passenger retrieved the PIN; wrong PIN persisted an attempt; other rider denied; approval revocation blocked start; correct PIN started once | PIN display/entry on two phones remains unverified |
| Journey progress | PARTIAL | Backend reached `in_progress`; assigned-rider location RLS and active-trip recovery queries are covered; app repository has Realtime plus reload fallback | No emulator/device restart, mobile-network loss or continuous simulated GPS run |
| Trip completion | PASS (local integration) | Assigned rider completed; unassigned rider denied; duplicate completion returned false; accepted snapshot stayed immutable and final fare was server-authored | Final screens on real phones remain unverified |
| Cash payment record | PASS (local integration) | Outsider/other rider denied; passenger acknowledgement then assigned-rider confirmation produced `rider_confirmed`; repeats returned false | Real cash handling/operator procedure requires a person |
| Ride history/admin | PASS (local authorization) | Passenger, assigned rider and admin each read the same completed trip/payment state; outsider received no row | Hosted admin with real authenticated operator was not used |
| Rider available again | PASS (local integration) | Winning rider remained approved/online after completion and had no active trip | Actual next dispatch on a phone was not attempted |

Primary integrated command:

```text
python3 scripts/serve-local-request-trip.py --deno <deno> --function fare-quote --port 8004
python3 scripts/rehearse-local-trip.py --live-fare-quote --race-checks
```

Result: **PASS**, including nine concurrent cancellation/start/completion calls and six concurrent wrong-PIN calls. Exact race winners vary by scheduling; every run asserts one durable legal outcome.

Regression evidence:

| Check | Result |
|---|---|
| `supabase db test --local supabase/tests/database` | PASS — 21 files, 386 assertions |
| `node --test supabase/functions/tests/*.test.mjs` | PASS — 5 test files (20 assertions within the suites) |
| `:core-ui:testDebugUnitTest` | PASS — 93 tests |
| `:core-ui:lintDebug` | PASS |
| Passenger/Rider `assembleDevelopment` | PASS |
| Admin test/lint/typecheck/build | PASS — 4 tests and static export |
| Admin browser suite | PASS when run against the local built export |
| Landing build | PASS — download links remain honestly unavailable |
| Landing browser suite | PASS — 320/360/768/1024/1440 px, no overflow, no axe violations or console errors |

Development APKs (not public release candidates):

- `passenger-app/build/outputs/apk/development/passenger-app-development.apk` — SHA-256 `4aabc374bf2e1eb83936f82a128cfff5224b73e4589a9cf6cd5c3b891e5b5dac`
- `rider-app/build/outputs/apk/development/rider-app-development.apk` — SHA-256 `babaad956a4a105ead7bd09eeeb61544daf514c444f986dab8b53f5c4b3bf57a`

Both development builds explicitly reported missing `google-services.json`; they compile, but this is evidence that FCM is not configured, not evidence of notification delivery. No emulator or `adb` device was available.

## D. Remaining requirements

1. Complete and verify the hosted-development database backup and isolated restore before any further hosted mutation or journey fixtures.
2. Run the same journey with separate passenger/rider test identities on the hosted development project and capture sanitized Auth, RLS, Realtime and Edge Function evidence.
3. Add the genuine passenger and rider Firebase `google-services.json` files to their ignored source-set locations and configure the backend FCM service account; verify foreground/background delivery, token refresh, invalid-token removal and cancellation notifications on real phones.
4. Perform the two-phone rehearsal: permissions, destination search, Mapbox rendering, dispatch, background/foreground transitions, process restart, weak/lost mobile data, GPS movement, PIN, final fare, cash acknowledgement and histories.
5. Verify SMTP confirmation and password-recovery deep links on both app packages.
6. Verify the existing Android release signing identity and produce/install signed, minified release candidates. Development APKs above must not be published.
7. Exercise hosted admin authentication and observe the same synthetic journey. Keep the admin protected and public bookings paused.
8. Validate operational rider approval, cash reconciliation, support/incident response, legal/insurance and launch authorization with responsible people.

Request expiration remains only partially covered: fare quotes expire, but a created `requesting` trip has no independently verified dispatch-timeout worker in this rehearsal. Realtime reconnect is implemented with reload/recovery paths but was not exercised over an actual dropped websocket. These are release gaps, not claimed passes.

## E. Final verdict

**PARTIAL — Journey works in tested areas but verification is incomplete.**

The strongest available isolated integration test proves the core server-controlled path from authenticated quote through single-rider dispatch, location, arrival, PIN start, completion, dynamic final fare, cash acknowledgement, histories and admin visibility. It does not prove the Android UI on phones, hosted Realtime/background notifications, real GPS/mobile networks, email, release signing or operational readiness. Public bookings, production deployment, APK publication and merge to `main` remain disabled.
