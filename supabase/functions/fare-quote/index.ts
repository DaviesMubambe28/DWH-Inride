import { withSupabase } from "@supabase/server";
import { readBookingInput, routeMetrics, bookingFailure, BookingInputError } from "../_shared/booking-input.ts";
import { reportError } from "../_shared/monitoring.ts";

function json(value: unknown, status = 200): Response {
  return Response.json(value, { status, headers: { "Cache-Control": "no-store" } });
}

export default {
  fetch: withSupabase({ auth: "user" }, async (req, ctx) => {
    if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);
    let body;
    try { body = await readBookingInput(req); }
    catch (error) {
      if (error instanceof BookingInputError) return json({ error: error.message }, error.status);
      return json({ error: "The fare request could not be read. Try again." }, 400);
    }
    const userId = ctx.userClaims?.id;
    if (!userId) return json({ error: "Authentication required." }, 401);
    const { data: profile, error: profileError } = await ctx.supabase
      .from("profiles").select("role").eq("id", userId).single();
    if (profileError || profile?.role !== "passenger") {
      return json({ error: "A passenger profile is required to request a fare." }, 403);
    }
    const mapboxToken = Deno.env.get("MAPBOX_SERVER_TOKEN")?.trim();
    if (!mapboxToken) return json({ error: "Routing service is temporarily unavailable." }, 503);
    const coordinates = [
      `${body.pickup.longitude},${body.pickup.latitude}`,
      `${body.dropoff.longitude},${body.dropoff.latitude}`,
    ].join(";");
    const url = new URL(`https://api.mapbox.com/directions/v5/mapbox/driving-traffic/${coordinates}`);
    url.searchParams.set("alternatives", "false");
    url.searchParams.set("overview", "false");
    url.searchParams.set("steps", "false");
    url.searchParams.set("access_token", mapboxToken);
    let response: Response;
    try { response = await fetch(url, { signal: AbortSignal.timeout(8_000) }); }
    catch {
      await reportError("routing_unavailable", "fare-quote", Deno.env.get("SENTRY_DSN") ?? "");
      return json({ error: "Routing service is temporarily unavailable." }, 502);
    }
    if (!response.ok) return json({ error: "Routing service rejected the request." }, 502);
    const metrics = routeMetrics(await response.json().catch(() => null));
    if (!metrics) return json({ error: "No usable route was found between these locations." }, 422);
    const { data: quote, error: quoteError } = await ctx.supabaseAdmin.rpc("create_official_fare_quote", {
      p_passenger_id: userId,
      p_pickup_lat: body.pickup.latitude,
      p_pickup_lng: body.pickup.longitude,
      p_pickup_address: body.pickup.address,
      p_dropoff_lat: body.dropoff.latitude,
      p_dropoff_lng: body.dropoff.longitude,
      p_dropoff_address: body.dropoff.address,
      p_route_distance_meters: metrics.distanceMeters,
      p_route_duration_seconds: metrics.durationSeconds,
    });
    if (quoteError) {
      const failure = bookingFailure(quoteError);
      return json({
        error: failure.status === 500 ? "The fare quote could not be created." : failure.error,
      }, failure.status);
    }
    return json({ quote }, 201);
  }),
};
