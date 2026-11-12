#!/usr/bin/env python3
"""Run a booking Edge Function export against local Supabase and live Mapbox.

This is a loopback Deno development harness, not an Edge platform deployment.
Uses actual local Auth verification keys and API keys. Routing uses the existing
MAPBOX_SERVER_TOKEN, or the configured public token for this local test only.
Never prints keys and never changes hosted configuration or service pause state.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile
from urllib.request import build_opener, ProxyHandler, Request


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--deno", required=True, type=Path)
    parser.add_argument("--function", choices=("request-trip", "fare-quote"), default="request-trip")
    parser.add_argument("--port", type=int, default=8003)
    args = parser.parse_args()
    if not args.deno.is_file():
        parser.error("Supply the existing Deno executable")
    root = Path(__file__).resolve().parents[1]
    status = subprocess.run([str(root / "node_modules/.bin/supabase"), "status", "--output", "json"],
                            cwd=root, capture_output=True, text=True,
                            env={**os.environ, "DO_NOT_TRACK": "1"}, check=False)
    if status.returncode:
        raise RuntimeError("Start local Supabase Auth/PostgREST/Kong first")
    config = json.loads(status.stdout)
    if config.get("API_URL") != "http://127.0.0.1:54321":
        raise RuntimeError("Only the fixed local API origin is supported")
    routing_token = os.environ.get("MAPBOX_SERVER_TOKEN") or os.environ.get("MAPBOX_PUBLIC_TOKEN")
    if not routing_token:
        raise RuntimeError("A configured token with actual Mapbox Directions access is required")
    local = build_opener(ProxyHandler({}))  # Fixed literal loopback only.
    request = Request(config["API_URL"] + "/auth/v1/.well-known/jwks.json",
                      headers={"apikey": config["ANON_KEY"]})
    with local.open(request, timeout=10) as response:
        jwks = json.load(response)
    if not jwks.get("keys"):
        raise RuntimeError("Local Auth did not return verification keys")
    # No hosted Supabase/worker/FCM/monitoring credentials enter the child.
    env = {k: v for k, v in os.environ.items()
           if not k.startswith("SUPABASE_") and k not in ("FCM_SERVICE_ACCOUNT_JSON", "OPERATIONS_WORKER_TOKEN", "SENTRY_DSN")}
    env.update(SUPABASE_URL=config["API_URL"], SUPABASE_PUBLISHABLE_KEY=config["PUBLISHABLE_KEY"],
               SUPABASE_SECRET_KEY=config["SECRET_KEY"], SUPABASE_ANON_KEY=config["ANON_KEY"],
               SUPABASE_SERVICE_ROLE_KEY=config["SERVICE_ROLE_KEY"], SUPABASE_JWKS=json.dumps(jwks),
               MAPBOX_SERVER_TOKEN=routing_token, SENTRY_DSN="")
    if env.get("CODEX_PROXY_CERT"):
        env["DENO_CERT"] = env["CODEX_PROXY_CERT"]
    env["NO_PROXY"] = ",".join(filter(None, [env.get("NO_PROXY", ""), "127.0.0.1", "localhost"]))
    function_root = root / "supabase/functions" / args.function
    with tempfile.NamedTemporaryFile(mode="w", suffix=".ts", prefix="inride-local-booking-", delete=False) as wrapper:
        wrapper.write("import handler from " + json.dumps((function_root / "index.ts").as_uri()) + ";\n"
                      f'Deno.serve({{hostname:"127.0.0.1",port:{args.port}}},handler.fetch);\n')
    try:
        result = subprocess.run([str(args.deno.resolve()), "run", "--cached-only", "--frozen", "--allow-env", "--allow-net",
                                 "--config", str(function_root / "deno.json"),
                                 "--lock", str(function_root / "deno.lock"), wrapper.name],
                                env=env, check=False)
        if result.returncode:
            raise RuntimeError("Local " + args.function + " process exited with a failure")
    finally:
        Path(wrapper.name).unlink(missing_ok=True)


if __name__ == "__main__":
    main()
