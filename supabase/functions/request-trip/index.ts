import { withSupabase } from "@supabase/server";
import { readBookingInput, routeMetrics, bookingAvailability, bookingFailure, BookingInputError } from "../_shared/booking-input.ts";

import { reportError } from "../_shared/monitoring.ts";
const report = (code: string) => reportError(code, "request-trip", Deno.env.get("SENTRY_DSN") ?? "");

function json(message: unknown, status = 200): Response {
  return Response.json(message, { status, headers: { "Cache-Control": "no-store" } });
}

export default {
  fetch: withSupabase({ auth: "user" }, async (req, ctx) => {
    if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

    let body;
    try { body = await readBookingInput(req); }
    catch (error) {
      if (error instanceof BookingInputError) return json({ error: error.message }, error.status);
      return json({ error: "The booking request could not be read. Try again." }, 400);
    }

    const userId = ctx.userClaims?.id;
    if (!userId) return json({ error: "Authentication required." }, 401);

    const { data: profile, error: profileError } = await ctx.supabase
      .from("profiles").select("role").eq("id", userId).single();
    if (profileError || profile?.role !== "passenger") {
      return json({ error: "A passenger profile is required to request a trip." }, 403);
    }

    const { data: service, error: serviceError } = await ctx.supabase.rpc("pilot_service_status");
    const availability = bookingAvailability(serviceError ? null : service);
    if (!availability.allowed) {
      if (service?.available === true) await report("unsupported_pricing_mode");
      if (service?.available === true) return json({ error: availability.error }, 409);
      return json({ error: "New bookings are temporarily paused or unavailable. Please try again later." }, 503);
    }
    const mapboxToken = Deno.env.get("MAPBOX_SERVER_TOKEN")?.trim();
    if (!mapboxToken) return json({ error: "Routing service is temporarily unavailable." }, 503);

    const coordinates = [
      `${body.pickup.longitude},${body.pickup.latitude}`,
      `${body.dropoff.longitude},${body.dropoff.latitude}`,
    ].join(";");
    const directionsUrl = new URL(
      `https://api.mapbox.com/directions/v5/mapbox/driving-traffic/${coordinates}`,
    );
    directionsUrl.searchParams.set("alternatives", "false");
    directionsUrl.searchParams.set("overview", "false");
    directionsUrl.searchParams.set("steps", "false");
    directionsUrl.searchParams.set("access_token", mapboxToken);

    let directionsResponse: Response;
    try {
      directionsResponse = await fetch(directionsUrl, { signal: AbortSignal.timeout(8_000) });
    } catch {
      await report("routing_unavailable");
      return json({ error: "Routing service is temporarily unavailable." }, 502);
    }
    if (!directionsResponse.ok) {
      await report("routing_unavailable");
      return json({ error: "Routing service rejected the request." }, 502);
    }

    let directions: unknown;
    try { directions = await directionsResponse.json(); }
    catch { return json({ error: "Routing service returned an invalid response." }, 502); }
    const metrics = routeMetrics(directions);
    if (!metrics) return json({ error: "No usable route was found between these locations." }, 422);
    const { distanceMeters, durationSeconds } = metrics;

    const { data: trip, error: createError } = await ctx.supabaseAdmin
      .rpc("create_routed_trip", {
        p_passenger_id: userId,
        p_pickup_lat: body.pickup.latitude,
        p_pickup_lng: body.pickup.longitude,
        p_pickup_address: body.pickup.address.trim(),
        p_dropoff_lat: body.dropoff.latitude,
        p_dropoff_lng: body.dropoff.longitude,
        p_dropoff_address: body.dropoff.address.trim(),
        p_route_distance_meters: distanceMeters,
        p_route_duration_seconds: durationSeconds,
        p_payment_method: body.paymentMethod ?? "cash",
      }).single();

    if (createError) {
      const failure = bookingFailure(createError);
      if (failure.status === 500) await report("booking_write_failed");
      return json({ error: failure.error }, failure.status);
    }

    return json({ trip }, 201);
  }),
};
