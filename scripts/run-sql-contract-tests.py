#!/usr/bin/env python3
"""Run SQL contracts in a new local-only PostgreSQL database; never target hosted data."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import uuid
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--psql", default=shutil.which("psql"))
    parser.add_argument("--socket", default=os.environ.get("PGHOST", "/workspace/pilot-pg-socket"))
    args = parser.parse_args()
    if not args.psql or not Path(args.psql).is_file():
        parser.error("Supply the PostgreSQL 17 psql executable with --psql")
    if not Path(args.socket).is_absolute() or not Path(args.socket).is_dir():
        parser.error("A local Unix socket directory is required; TCP/hosted databases are refused")
    root = Path(__file__).resolve().parents[1]
    name = "dwh_contract_" + uuid.uuid4().hex
    command = [args.psql, "-X", "-w", "-h", args.socket, "-v", "ON_ERROR_STOP=1", "-At"]

    def sql(database, *arguments):
        result = subprocess.run(command + ["-d", database, *arguments], capture_output=True, text=True)
        if result.returncode:
            raise RuntimeError(result.stderr.strip())
        return result.stdout

    sql("postgres", "-c", f"CREATE DATABASE {name}")
    try:
        version = sql(name, "-c", "SHOW server_version_num").strip()
        if not version.startswith("17"):
            raise RuntimeError("This contract check requires PostgreSQL 17")
        sql("postgres", "-c", f"ALTER DATABASE {name} SET search_path TO public, extensions")
        # The roles belong to the local cluster and may exist from an earlier run.
        bootstrap = (root / "supabase/tests/support/local-platform.sql").read_text()
        for role in ("anon", "authenticated", "service_role"):
            if sql(name, "-c", f"SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='{role}')").strip() == "t":
                bootstrap = re.sub(rf"create role {role} [^;]+;", "", bootstrap)
        sql(name, "-c", bootstrap)
        migrations = sorted((root / "supabase/migrations").glob("*.sql"))
        for migration in migrations:
            sql(name, "-f", str(migration))
        print(f"PASS: {len(migrations)} migrations replayed on PostgreSQL 17")
        total = 0
        for test in sorted((root / "supabase/tests/database").glob("*.sql")):
            output = sql(name, "-f", str(test))
            plans = re.findall(r"^1\.\.(\d+)$", output, re.MULTILINE)
            passed = re.findall(r"^ok \d+\b", output, re.MULTILINE)
            failed = re.findall(r"^not ok .*$", output, re.MULTILINE)
            if len(plans) != 1 or int(plans[0]) == 0 or len(passed) != int(plans[0]) or failed:
                diagnostics = "\n".join(line for line in output.splitlines() if line.startswith(("not ok", "#")))
                raise RuntimeError(f"{test.name}: incomplete/failed TAP run\n{diagnostics}")
            total += len(passed)
            print(f"PASS: {test.name}: {len(passed)} assertions")
        if total == 0:
            raise RuntimeError("No database assertions ran")
        # Separate PostgreSQL sessions exercise real row locking and acceptance.
        fixture = (root / "supabase/tests/support/open-pilot-fixture.sql").read_text()
        sql(name, "-c", fixture + """
          insert into auth.users(id, raw_user_meta_data)
          select ('90000000-0000-0000-0000-' || lpad(n::text, 12, '0'))::uuid,
            jsonb_build_object('full_name', 'Concurrent Test ' || n, 'phone_number', '+26097911100' || n)
          from generate_series(1, 3) n;
          update public.profiles set role='rider', approval_status='approved', is_online=true
          where id in ('90000000-0000-0000-0000-000000000002', '90000000-0000-0000-0000-000000000003');
          insert into private.pilot_rider_presence(rider_id, latitude, longitude)
          select id, -15.3292, 28.6821 from public.profiles where role='rider';
          set role service_role;
          do $$ begin perform public.create_routed_trip('90000000-0000-0000-0000-000000000001',
            -15.3292, 28.6821, 'Concurrent pickup', -15.33, 28.69, 'Concurrent destination', 4000, 600, 'cash'); end $$;
          reset role;
        """)
        trip = sql(name, "-c", "select id from public.trips where passenger_id='90000000-0000-0000-0000-000000000001'").strip()
        barrier = Barrier(2)

        def accept(suffix):
            actor = "90000000-0000-0000-0000-" + suffix
            barrier.wait(timeout=10)
            output = sql(name, "-c", f"SET ROLE authenticated; SELECT set_config('request.jwt.claim.sub', '{actor}', false); SELECT public.accept_trip('{trip}');")
            return output.strip().splitlines()[-1]

        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(accept, ("000000000002", "000000000003")))
        if sorted(results) != ["f", "t"]:
            raise RuntimeError(f"Concurrent acceptance did not produce exactly one winner: {results}")
        print("PASS: two concurrent rider sessions produce exactly one successful acceptance")
        print(f"PASS: {total} SQL contract assertions. Auth/Storage HTTP integration remains separate.")
    finally:
        sql("postgres", "-c", f"DROP DATABASE {name} WITH (FORCE)")


if __name__ == "__main__":
    main()
