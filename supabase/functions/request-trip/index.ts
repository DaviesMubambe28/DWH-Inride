import "@supabase/functions-js/edge-runtime.d.ts";
import { withSupabase } from "@supabase/server";

type Coordinate = { latitude: number; longitude: number; address: string };
type RequestTripBody = { pickup: Coordinate; dropoff: Coordinate; paymentMethod?: "cash" | "mobile_money_external" };

function isCoordinate(value: unknown): value is Coordinate {
  if (!value || typeof value !== "object") return false;
  const point = value as Record<string, unknown>;
  return typeof point.latitude === "number" && Number.isFinite(point.latitude) &&
    point.latitude >= -90 && point.latitude <= 90 &&
    typeof point.longitude === "number" && Number.isFinite(point.longitude) &&
    point.longitude >= -180 && point.longitude <= 180 &&
    typeof point.address === "string" && point.address.trim().length > 0 &&
    point.address.trim().length <= 300;
}

function json(message: unknown, status = 200): Response {
  return Response.json(message, { status, headers: { "Cache-Control": "no-store" } });
}

export default {
  fetch: withSupabase({ auth: "user" }, async (req, ctx) => {
    if (req.method !== "POST") return json({ error: "Method not allowed" }, 405);

    const mapboxToken = Deno.env.get("MAPBOX_SERVER_TOKEN")?.trim();
    if (!mapboxToken) {
      return json({
        error: "Configuration Required",
        detail: "MAPBOX_SERVER_TOKEN is not configured for the request-trip function.",
      }, 503);
    }

    let body: RequestTripBody;
    try {
      body = await req.json();
    } catch {
      return json({ error: "Request body must be valid JSON." }, 400);
    }

    if (!isCoordinate(body.pickup) || !isCoordinate(body.dropoff)) {
      return json({ error: "Valid pickup and drop-off coordinates and addresses are required." }, 400);
    }
    if (body.paymentMethod && !["cash", "mobile_money_external"].includes(body.paymentMethod)) {
      return json({ error: "Unsupported payment method." }, 400);
    }

    const userId = ctx.userClaims?.id;
    if (!userId) return json({ error: "Authentication required." }, 401);

    const { data: profile, error: profileError } = await ctx.supabase
      .from("profiles").select("role").eq("id", userId).single();
    if (profileError || profile?.role !== "passenger") {
      return json({ error: "A passenger profile is required to request a trip." }, 403);
    }

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
      return json({ error: "Routing service is temporarily unavailable." }, 502);
    }
    if (!directionsResponse.ok) {
      return json({ error: "Routing service rejected the request." }, 502);
    }

    const directions = await directionsResponse.json() as {
      code?: string;
      routes?: Array<{ distance?: number; duration?: number }>;
    };
    const route = directions.routes?.[0];
    const distanceMeters = Math.round(route?.distance ?? 0);
    const durationSeconds = Math.round(route?.duration ?? 0);
    if (directions.code !== "Ok" || distanceMeters <= 0 || durationSeconds <= 0) {
      return json({ error: "No usable route was found between these locations." }, 422);
    }

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
      const duplicate = createError.code === "23505";
      return json({
        error: duplicate ? "You already have an active trip." : "The trip could not be created.",
      }, duplicate ? 409 : 500);
    }

    return json({ trip }, 201);
  }),
};
