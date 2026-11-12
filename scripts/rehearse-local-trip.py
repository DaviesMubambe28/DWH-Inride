#!/usr/bin/env python3
"""Exercise the real local Auth/PostgREST trip lifecycle; refuses hosted targets.

Creates only labelled synthetic accounts, restores the local paused service
settings and deletes its fixtures afterward. Never prints credentials, PINs or
HTTP bodies. This is not hosted, Android/device, email, FCM or routing evidence.
"""
import argparse
import json
import os
from pathlib import Path
import secrets
import subprocess
import uuid
from concurrent.futures import ThreadPoolExecutor
from urllib.error import HTTPError
from urllib.request import Request, build_opener, ProxyHandler

ROOT = Path(__file__).resolve().parents[1]
CONTAINER = "supabase_db_New_DHW_Inride"
ORIGIN = "http://127.0.0.1:54321"


DOCKER = ["docker", "--host=unix:///var/run/docker.sock"]


def sql(statement):
    result = subprocess.run(
        DOCKER + ["exec", "-i", CONTAINER, "psql", "-X", "-U", "postgres", "-d", "postgres", "-v", "ON_ERROR_STOP=1", "-At"],
        input=statement, capture_output=True, text=True, check=False,
    )
    if result.returncode:
        detail = next((line for line in reversed(result.stderr.splitlines()) if line.strip()), "unknown database error")
        raise RuntimeError("Local fixture SQL failed: " + detail)
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--keep-browser-fixture", type=Path,
                        help="Keep successful synthetic fixtures and write credentials outside Git (0600)")
    parser.add_argument("--cleanup-browser-fixture", type=Path,
                        help="Remove the synthetic fixtures from a previous private receipt")
    parser.add_argument("--cleanup-stale-synthetic", action="store_true",
                        help="Remove only labelled sprint-*@test.invalid fixtures left by interrupted local rehearsals")
    parser.add_argument("--live-request-trip", action="store_true",
                        help="Book through the real current handler on fixed loopback port 8003, including live routing")
    parser.add_argument("--live-fare-quote", action="store_true",
                        help="Quote on loopback port 8004 and accept through the authenticated current RPC")
    parser.add_argument("--race-checks", action="store_true",
                        help="Exercise real concurrent local cancellation/start/completion and PIN-lock calls")
    args = parser.parse_args()
    if args.race_checks and args.keep_browser_fixture:
        parser.error("Race fixtures must be cleaned, not retained for browser use")
    if sum(bool(value) for value in (args.keep_browser_fixture, args.cleanup_browser_fixture,
                                     args.cleanup_stale_synthetic)) > 1:
        parser.error("Choose fixture creation or one cleanup mode")
    if args.keep_browser_fixture and (args.keep_browser_fixture.resolve().is_relative_to(ROOT)
                                      or args.keep_browser_fixture.exists()):
        parser.error("Private fixture output must be a new file outside the checkout")
    status = subprocess.run(
        [str(ROOT / "node_modules/.bin/supabase"), "status", "--output", "json"],
        cwd=ROOT, capture_output=True, text=True, check=False,
        env={**os.environ, "DO_NOT_TRACK": "1"},
    )
    if status.returncode:
        raise RuntimeError("Start the local Supabase Auth/PostgREST/Kong services first")
    config = json.loads(status.stdout)
    if config.get("API_URL") != ORIGIN:
        raise RuntimeError("This rehearsal requires the fixed loopback-only Supabase API origin")
    if "SERVICE_ROLE_KEY" not in config or "ANON_KEY" not in config:
        raise RuntimeError("Local API credentials are unavailable")
    # Deliberately local only. This opener cannot be pointed at a remote project.
    opener = build_opener(ProxyHandler({}))

    def request(path, body=None, *, token=None, method=None, expected=(200,), extra=None, edge=False):
        if not path.startswith("/") or path.startswith("//"):
            raise ValueError("A local relative API path is required")
        edge_origin = "http://127.0.0.1:8004" if args.live_fare_quote else "http://127.0.0.1:8003"
        req = Request((edge_origin if edge else ORIGIN) + path,
                      data=json.dumps(body).encode() if body is not None else None,
                      method=method or ("POST" if body is not None else "GET"),
                      headers={"apikey": config["ANON_KEY"], "Authorization": "Bearer " + (token or config["ANON_KEY"]),
                               "Content-Type": "application/json", **(extra or {})})
        try:
            with opener.open(req, timeout=20) as response:
                code, raw = response.status, response.read()
        except HTTPError as error:
            code, raw = error.code, error.read()
        if code not in expected:
            raise RuntimeError("Local API check failed: " + path.split("?", 1)[0] + " HTTP " + str(code))
        return json.loads(raw) if raw else None

    def rpc(name, body, token, expected=(200,)):
        return request("/rest/v1/rpc/" + name, body, token=token, expected=expected)

    def passed(label):
        print("PASS: " + label, flush=True)

    def cleanup(identities):
        if not identities:
            return
        quoted = ",".join("'" + str(uuid.UUID(identity)) + "'" for identity in identities)
        # Each condition is restricted to newly generated synthetic IDs.
        sql("begin; select set_config('app.service_fee_write_authorized', 'true', true);"
            "delete from private.push_outbox where trip_id in (select id from public.trips where passenger_id in (" + quoted + "));"
            "delete from private.service_fee_payment_audit where actor_id in (" + quoted + ") or payment_id in "
            "(select id from public.rider_service_fee_payments where rider_id in (" + quoted + ") or reviewed_by in (" + quoted + "));"
            "delete from public.rider_service_fee_ledger where rider_id in (" + quoted + ") or trip_id in "
            "(select id from public.trips where passenger_id in (" + quoted + "));"
            "delete from public.rider_service_fee_payments where rider_id in (" + quoted + ") or reviewed_by in (" + quoted + ");"
            "delete from private.incident_audit where actor_id in (" + quoted + ") or incident_id in "
            "(select id from public.incidents where trip_id in (select id from public.trips where passenger_id in (" + quoted + ")) "
            "or reporter_id in (" + quoted + ") or recorded_by in (" + quoted + ") or assigned_admin_id in (" + quoted + "));"
            "delete from public.incidents where trip_id in (select id from public.trips where passenger_id in (" + quoted + ")) "
            "or reporter_id in (" + quoted + ") or recorded_by in (" + quoted + ") or assigned_admin_id in (" + quoted + ");"
            "delete from public.pilot_cancellation_reviews where actor_id in (" + quoted + ") or reviewed_by in (" + quoted + ") "
            "or trip_id in (select id from public.trips where passenger_id in (" + quoted + "));"
            "delete from public.trips where passenger_id in (" + quoted + ");"
            "delete from private.fare_quotes where passenger_id in (" + quoted + ");"
            "delete from private.pilot_operations_audit where actor_id in (" + quoted + ") or subject_id in (" + quoted + ");"
            "delete from private.pilot_rider_checks where rider_id in (" + quoted + ") or reviewed_by in (" + quoted + ");"
            "delete from public.rider_applications where applicant_id in (" + quoted + ") or reviewed_by in (" + quoted + ");"
            "delete from public.motorcycles where rider_id in (" + quoted + ");"
            "delete from public.profiles where id in (" + quoted + ");commit;")
        for identity in identities:
            request("/auth/v1/admin/users/" + identity, token=config["SERVICE_ROLE_KEY"], method="DELETE", expected=(200, 204))
        remaining = sql(
            "select count(*) from public.profiles where id in (" + quoted + ") "
            "union all select count(*) from auth.users where id in (" + quoted + ")"
        ).splitlines()
        if remaining != ["0", "0"]:
            raise RuntimeError("Local synthetic fixture cleanup did not remove every generated identity")

    if args.cleanup_stale_synthetic:
        identities = sql(
            "select id from auth.users where email like 'sprint-%@test.invalid' "
            "and raw_user_meta_data->>'full_name' like 'Synthetic Sprint %'"
        ).splitlines()
        cleanup(identities)
        passed("interrupted local rehearsal fixtures removed")
        return

    if args.cleanup_browser_fixture:
        fixture = json.loads(args.cleanup_browser_fixture.read_text())
        if fixture.get("origin") != ORIGIN or fixture.get("synthetic_fixture") is not True:
            raise RuntimeError("Receipt does not identify the fixed local synthetic fixture")
        identities = []
        for user in fixture["users"].values():
            identity = str(uuid.UUID(user["id"]))
            actual = request("/auth/v1/admin/users/" + identity, token=config["SERVICE_ROLE_KEY"])
            if not actual.get("email", "").startswith(fixture["prefix"]) or not actual.get("user_metadata", {}).get("full_name", "").startswith("Synthetic Sprint "):
                raise RuntimeError("Cleanup refused: user is not this labelled synthetic fixture")
            identities.append(identity)
        cleanup(identities)
        args.cleanup_browser_fixture.unlink()
        passed("retained synthetic browser fixture removed")
        return

    original = json.loads(sql("select to_jsonb(s) from private.pilot_service_settings s where singleton"))
    if original["enabled"]:
        raise RuntimeError("Refusing to alter an already-enabled local service; use an isolated paused instance")
    created, ids, tokens, browser_users = [], {}, {}, {}
    keep_fixture = False
    trip_id = None
    prefix = "sprint-" + uuid.uuid4().hex
    try:
        for index, name in enumerate(("passenger", "rider1", "rider2", "outsider", "admin"), 1):
            password = secrets.token_urlsafe(32)
            payload = {"email": prefix + "-" + name + "@test.invalid", "password": password,
                       "email_confirm": True,
                       "user_metadata": {"full_name": "Synthetic Sprint " + name,
                                         "phone_number": "+26097" + f"{secrets.randbelow(10**7):07d}", "role": "admin"}}
            user = request("/auth/v1/admin/users", payload, token=config["SERVICE_ROLE_KEY"], expected=(200, 201))
            identity = str(uuid.UUID(user["id"]))
            created.append(identity)
            ids[name] = identity
            browser_users[name] = {"id": identity, "email": payload["email"], "password": password}
            session = request("/auth/v1/token?grant_type=password", {"email": payload["email"], "password": password}, expected=(200,))
            tokens[name] = session["access_token"]
            profile = request("/rest/v1/profiles?select=role,approval_status&id=eq." + identity, token=tokens[name])
            assert profile[0]["role"] == "passenger", "User-editable metadata assigned a privileged role"
        passed("real local Auth account creation/password login; signup metadata cannot assign admin")
        rpc("apply_as_driver", {}, tokens["rider1"], expected=(403,))
        provision = {"p_user_id": ids["rider1"], "p_make": "Synthetic", "p_model": "Fixture", "p_year": 2025,
                     "p_plate": prefix[:14] + "-1", "p_color": "Blue"}
        rpc("admin_provision_pilot_rider", provision, tokens["outsider"], expected=(403,))
        sql("update public.profiles set role='admin' where id='" + ids["admin"] + "';")
        for index, name in enumerate(("rider1", "rider2"), 1):
            provision.update(p_user_id=ids[name], p_plate=prefix[:14] + "-" + str(index))
            assert rpc("admin_provision_pilot_rider", provision, tokens["admin"]) == ids[name]
            assert rpc("apply_as_driver", {}, tokens[name]) == "pending"
            rpc("set_driver_online", {"p_online": True}, tokens[name], expected=(403,))
            activation = {"p_rider_id": ids[name], "p_documents_checked": True,
                          "p_safety_briefing_completed": False, "p_physical_device_tested": True}
            rpc("admin_activate_pilot_rider", activation, tokens["admin"], expected=(400,))
            activation["p_safety_briefing_completed"] = True
            rpc("admin_activate_pilot_rider", activation, tokens[name], expected=(403,))
            assert rpc("admin_activate_pilot_rider", activation, tokens["admin"]) == "approved"
            assert rpc("set_driver_online", {"p_online": True}, tokens[name]) is True
            rpc("record_pilot_rider_presence", {"p_lat": -15.3292, "p_lng": 28.6820}, tokens[name], expected=(200, 204))
        passed("rider application/admin provisioning/approval boundaries and availability; synthetic attestations only")
        edge_booking = {"pickup": {"latitude": -15.3292, "longitude": 28.6820, "address": "Synthetic pickup"},
                        "dropoff": {"latitude": -15.3300, "longitude": 28.6900, "address": "Synthetic destination"},
                        "paymentMethod": "cash"}
        if args.live_request_trip or args.live_fare_quote:
            request("/", edge_booking, token="invalid-jwt", expected=(401,), edge=True)
            request("/", edge_booking, token=tokens["rider1"], expected=(403,), edge=True)
            request("/", edge_booking, token=tokens["passenger"], expected=(503,), edge=True)
            passed("real booking handler rejects invalid JWT/rider booking and paused service")
        sql("""update private.pilot_service_settings set enabled=true, opens='00:00', closes='23:59:59.999999',
            weekdays=array[1,2,3,4,5,6,7] where singleton;""")
        booking = {"p_passenger_id": ids["passenger"], "p_pickup_lat": -15.3292, "p_pickup_lng": 28.6820,
                   "p_pickup_address": "Synthetic pickup", "p_dropoff_lat": -15.3300, "p_dropoff_lng": 28.6900,
                   "p_dropoff_address": "Synthetic destination", "p_route_distance_meters": 1000,
                   "p_route_duration_seconds": 300, "p_payment_method": "cash"}
        rpc("create_routed_trip", booking, tokens["passenger"], expected=(401, 403, 404))
        if args.live_fare_quote:
            quote = request("/", edge_booking, token=tokens["passenger"], expected=(201,), edge=True)["quote"]
            quote_id = str(uuid.UUID(quote["quote_id"]))
            trip = rpc("accept_fare_quote", {"p_quote_id": quote_id, "p_idempotency_key": quote_id}, tokens["passenger"])[0]
        elif args.live_request_trip:
            trip = request("/", edge_booking, token=tokens["passenger"], expected=(201,), edge=True)["trip"]
        else:
            trip = rpc("create_routed_trip", booking, config["SERVICE_ROLE_KEY"])[0]
        trip_id = str(uuid.UUID(trip["trip_id"]))
        passed(("current fare-quote handler plus authenticated quote acceptance, actual local Auth/PostgREST and live Mapbox route" if args.live_fare_quote
                else "current request-trip handler, actual local Auth/PostgREST and live Mapbox route" if args.live_request_trip
                else "trusted booking RPC via local HTTP")
               + "; passenger cannot call trusted route-metrics RPC")
        if args.live_fare_quote:
            repeated = rpc("accept_fare_quote", {"p_quote_id": quote_id, "p_idempotency_key": quote_id}, tokens["passenger"])[0]
            assert repeated["trip_id"] == trip_id
        rpc("create_routed_trip", booking, config["SERVICE_ROLE_KEY"], expected=(409,))
        assert sql("select count(*) from public.trips where passenger_id='" + ids["passenger"] + "' and status in ('requesting','accepted','arrived','in_progress')") == "1"
        passed("duplicate booking rejected with one active trip")
        for name in ("rider1", "rider2"):
            feed = request("/rest/v1/rider_available_trips?select=id&id=eq." + trip_id, token=tokens[name])
            assert [row["id"] for row in feed] == [trip_id]
        assert rpc("set_driver_online", {"p_online": False}, tokens["rider2"]) is False
        assert request("/rest/v1/rider_available_trips?select=id&id=eq." + trip_id, token=tokens["rider2"]) == []
        rpc("accept_trip", {"p_trip_id": trip_id}, tokens["rider2"], expected=(403,))
        assert rpc("set_driver_online", {"p_online": True}, tokens["rider2"]) is True
        passed("request reaches both eligible riders; offline rider loses feed and acceptance access")
        details = request("/rest/v1/authorized_trip_details?select=id,fare_amount,service_fee,driver_fare&id=eq." + trip_id,
                          token=tokens["passenger"])[0]
        expected_fare = float(trip["fare_amount"])
        expected_fee = float(trip["service_fee"])
        expected_driver_fare = float(trip["driver_fare"])
        assert float(details["fare_amount"]) == expected_fare
        assert float(details["service_fee"]) == expected_fee
        assert float(details["driver_fare"]) == expected_driver_fare
        assert round(expected_driver_fare + expected_fee, 2) == expected_fare
        assert request("/rest/v1/authorized_trip_details?select=id&id=eq." + trip_id, token=tokens["outsider"]) == []
        request("/rest/v1/trips?id=eq." + trip_id, {"fare_amount": 1}, method="PATCH", token=tokens["passenger"], expected=(403,))
        passed("cross-user trip privacy and server dynamic-fare/commission protection")
        def accept(name):
            return name, rpc("accept_trip", {"p_trip_id": trip_id}, tokens[name])
        with ThreadPoolExecutor(max_workers=2) as pool:
            accepted = list(pool.map(accept, ("rider1", "rider2")))
        assert sorted(result for _, result in accepted) == [False, True]
        winner = next(name for name, result in accepted if result)
        loser = next(name for name, result in accepted if not result)
        location = {"trip_id": trip_id, "rider_id": ids[winner], "lat": -15.3290, "lng": 28.6840}
        request("/rest/v1/trip_locations", location, method="POST", token=tokens[loser], expected=(403,))
        request("/rest/v1/trip_locations", location, method="POST", token=tokens[winner], expected=(201,))
        passenger_location = request("/rest/v1/trip_locations?select=trip_id,rider_id,lat,lng&trip_id=eq." + trip_id,
                                     token=tokens["passenger"])
        assert len(passenger_location) == 1 and passenger_location[0]["rider_id"] == ids[winner]
        assert request("/rest/v1/trip_locations?select=trip_id&trip_id=eq." + trip_id,
                       token=tokens["outsider"]) == []
        passed("assigned rider location reaches passenger; losing rider and outsider are denied")
        assert rpc("mark_trip_arrived", {"p_trip_id": trip_id}, tokens[loser]) is False
        assert rpc("mark_trip_arrived", {"p_trip_id": trip_id}, tokens[winner]) is True
        assert rpc("accept_trip", {"p_trip_id": trip_id}, tokens[winner]) is False
        assert rpc("mark_trip_arrived", {"p_trip_id": trip_id}, tokens[winner]) is False
        passed("competing HTTP acceptance produces one winner; only assigned rider can arrive")
        pin = rpc("get_trip_pickup_pin", {"p_trip_id": trip_id}, tokens["passenger"])
        assert isinstance(pin, str) and len(pin) == 4
        assert rpc("get_trip_pickup_pin", {"p_trip_id": trip_id}, tokens["outsider"]) is None
        wrong_pin = "0000" if pin != "0000" else "0001"
        assert rpc("verify_trip_pin_and_start", {"p_trip_id": trip_id, "p_pin": wrong_pin}, tokens[winner]) is False
        assert sql("select pin_failed_attempts from public.trips where id='" + trip_id + "'") == "1"
        assert rpc("verify_trip_pin_and_start", {"p_trip_id": trip_id, "p_pin": pin}, tokens[loser]) is False
        sql("update public.profiles set approval_status='rejected' where id='" + ids[winner] + "';")
        rpc("verify_trip_pin_and_start", {"p_trip_id": trip_id, "p_pin": pin}, tokens[winner], expected=(403,))
        sql("update public.profiles set approval_status='approved' where id='" + ids[winner] + "';")
        assert rpc("verify_trip_pin_and_start", {"p_trip_id": trip_id, "p_pin": pin}, tokens[winner]) is True
        assert rpc("verify_trip_pin_and_start", {"p_trip_id": trip_id, "p_pin": pin}, tokens[winner]) is False
        assert rpc("complete_trip", {"p_trip_id": trip_id}, tokens[loser]) is False
        assert rpc("complete_trip", {"p_trip_id": trip_id}, tokens[winner]) is True
        passed("passenger PIN retrieval, four-digit wrong-PIN counter persistence, other-rider denial, start and completion")
        assert rpc("mark_passenger_paid", {"p_trip_id": trip_id}, tokens["outsider"]) is False
        assert rpc("mark_passenger_paid", {"p_trip_id": trip_id}, tokens["passenger"]) is True
        assert rpc("confirm_rider_payment", {"p_trip_id": trip_id}, tokens[loser]) is False
        assert rpc("confirm_rider_payment", {"p_trip_id": trip_id}, tokens[winner]) is True
        final = request("/rest/v1/authorized_trip_details?select=status,payment_status,fare_amount,service_fee,driver_fare,accepted_fare_snapshot,final_fare_breakdown&id=eq." + trip_id,
                        token=tokens["passenger"])[0]
        assert final["status"] == "completed"
        assert final["payment_status"] == "rider_confirmed"
        final_fare = float(final["fare_amount"])
        assert final_fare > 0
        assert round(float(final["driver_fare"]) + float(final["service_fee"]), 2) == final_fare
        assert float(final["accepted_fare_snapshot"]["fare_amount_zmw"]) == expected_fare
        assert final["final_fare_breakdown"]["pricing_version"] == final["accepted_fare_snapshot"]["pricing_version"]
        assert rpc("complete_trip", {"p_trip_id": trip_id}, tokens[winner]) is False
        assert rpc("mark_passenger_paid", {"p_trip_id": trip_id}, tokens["passenger"]) is False
        assert rpc("confirm_rider_payment", {"p_trip_id": trip_id}, tokens[winner]) is False
        passed("duplicate arrival/start/completion/payment are harmless; approval revocation blocks start")
        passed("cash recording by correct passenger and assigned rider; completed state persisted")
        for name in ("passenger", winner, "admin"):
            history = request("/rest/v1/authorized_trip_details?select=id,status,payment_status&id=eq." + trip_id,
                              token=tokens[name])
            assert history == [{"id": trip_id, "status": "completed", "payment_status": "rider_confirmed"}]
        assert request("/rest/v1/authorized_trip_details?select=id&id=eq." + trip_id,
                       token=tokens["outsider"]) == []
        availability = request("/rest/v1/profiles?select=is_online&id=eq." + ids[winner], token=tokens[winner])
        assert availability == [{"is_online": True}]
        passed("passenger/rider histories and admin view agree; outsider is denied; rider remains eligible")
        if args.race_checks:
            def race_trip():
                row = rpc("create_routed_trip", booking, config["SERVICE_ROLE_KEY"])[0]
                identity = str(uuid.UUID(row["trip_id"]))
                assert rpc("accept_trip", {"p_trip_id": identity}, tokens["rider1"]) is True
                assert rpc("mark_trip_arrived", {"p_trip_id": identity}, tokens["rider1"]) is True
                return identity, rpc("get_trip_pickup_pin", {"p_trip_id": identity}, tokens["passenger"])

            outcomes = {"start_wins": 0, "cancel_before_start": 0, "complete_wins": 0, "cancel_before_complete": 0}
            def parallel(first, second):
                # Independent urllib calls/connections and PostgREST transactions.
                from threading import Barrier
                gate = Barrier(2)
                def run(action):
                    gate.wait(timeout=10)
                    return action()
                with ThreadPoolExecutor(max_workers=2) as pool:
                    return list(pool.map(run, (first, second)))

            for _ in range(3):
                identity, pickup_code = race_trip()
                cancelled, started = parallel(
                    lambda: rpc("cancel_pilot_trip", {"p_trip_id": identity, "p_reason": "passenger_cancelled"}, tokens["passenger"]),
                    lambda: rpc("verify_trip_pin_and_start", {"p_trip_id": identity, "p_pin": pickup_code}, tokens["rider1"]),
                )
                assert sorted([cancelled, started]) == [False, True]
                state = sql("select status::text from public.trips where id='" + identity + "'")
                assert state == ("cancelled" if cancelled else "in_progress")
                assert sql("select count(*) from public.pilot_cancellation_reviews where trip_id='" + identity + "'") == ("1" if cancelled else "0")
                outcomes["cancel_before_start" if cancelled else "start_wins"] += 1
                if started:
                    assert rpc("complete_trip", {"p_trip_id": identity}, tokens["rider1"]) is True

                identity, pickup_code = race_trip()
                assert rpc("verify_trip_pin_and_start", {"p_trip_id": identity, "p_pin": pickup_code}, tokens["rider1"]) is True
                cancelled, completed = parallel(
                    lambda: rpc("cancel_pilot_trip", {"p_trip_id": identity, "p_reason": "safety_concern"}, tokens["rider1"]),
                    lambda: rpc("complete_trip", {"p_trip_id": identity}, tokens["rider1"]),
                )
                assert sorted([cancelled, completed]) == [False, True]
                assert sql("select status::text from public.trips where id='" + identity + "'") == ("cancelled" if cancelled else "completed")
                assert sql("select count(*) from public.pilot_cancellation_reviews where trip_id='" + identity + "'") == ("1" if cancelled else "0")
                outcomes["cancel_before_complete" if cancelled else "complete_wins"] += 1

                identity, _ = race_trip()
                def cancel_once():
                    return rpc("cancel_pilot_trip", {"p_trip_id": identity, "p_reason": "passenger_cancelled"}, tokens["passenger"])
                assert sorted(parallel(cancel_once, cancel_once)) == [False, True]
                assert sql("select count(*) from public.pilot_cancellation_reviews where trip_id='" + identity + "'") == "1"
                race_fare = json.loads(sql("select json_build_object('fare',fare_amount,'fee',service_fee,'driver',driver_fare) from public.trips where id='" + identity + "'"))
                assert float(race_fare["fare"]) > 0
                assert round(float(race_fare["driver"]) + float(race_fare["fee"]), 2) == float(race_fare["fare"])
            passed("nine concurrent local HTTP cancellation/start/completion races; one durable outcome/review; observed " + json.dumps(outcomes, sort_keys=True))

            identity, pickup_code = race_trip()
            wrong = "0000" if pickup_code != "0000" else "0001"
            def attempt(_):
                return rpc("verify_trip_pin_and_start", {"p_trip_id": identity, "p_pin": wrong}, tokens["rider1"], expected=(200, 403))
            with ThreadPoolExecutor(max_workers=6) as pool:
                results = list(pool.map(attempt, range(6)))
            assert sum(result is False for result in results) == 5
            assert sum(isinstance(result, dict) and result.get("code") == "42501" for result in results) == 1
            assert sql("select pin_failed_attempts from public.trips where id='" + identity + "'") == "5"
            rpc("verify_trip_pin_and_start", {"p_trip_id": identity, "p_pin": pickup_code}, tokens["rider1"], expected=(403,))
            assert sql("select status::text from public.trips where id='" + identity + "'") == "arrived"
            passed("six concurrent wrong-PIN calls persist five attempts and lock; correct PIN denied while locked; no actual expiry wait")
        if args.keep_browser_fixture:
            receipt = {"origin": ORIGIN, "public_key": config["ANON_KEY"], "synthetic_fixture": True,
                       "prefix": prefix, "users": browser_users}
            with os.fdopen(os.open(args.keep_browser_fixture, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w") as output:
                json.dump(receipt, output)
            keep_fixture = True
            passed("browser fixture credentials retained only in the requested private off-repository file")
    finally:
        fields = ("enabled", "opens", "closes", "weekdays")
        encoded = json.dumps(original).replace("'", "''")
        sql("update private.pilot_service_settings set (" + ",".join(fields) + ")=(select " + ",".join(fields)
            + " from jsonb_populate_record(null::private.pilot_service_settings,'" + encoded + "'::jsonb)) where singleton;")
        if not keep_fixture:
            cleanup(created)
        assert sql("select enabled from private.pilot_service_settings where singleton") == "f"
        passed("original paused local service settings restored; fixtures " + ("retained for browser check" if keep_fixture else "removed"))
    passed("local HTTP lifecycle" + (" plus live routing" if args.live_request_trip or args.live_fare_quote else "; Edge routing untested")
           + "; hosted/device/FCM/email checks remain separate")


if __name__ == "__main__":
    main()
