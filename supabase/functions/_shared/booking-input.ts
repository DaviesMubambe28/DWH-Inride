export type Coordinate = { latitude: number; longitude: number; address: string };
export type BookingInput = { pickup: Coordinate; dropoff: Coordinate; paymentMethod: "cash" };

export class BookingInputError extends Error {
  status: number;
  constructor(status: number, message: string) { super(message); this.status = status; }
}

function object(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function coordinate(value: unknown): value is Coordinate {
  return object(value) && typeof value.latitude === "number" && Number.isFinite(value.latitude)
    && value.latitude >= -90 && value.latitude <= 90
    && typeof value.longitude === "number" && Number.isFinite(value.longitude)
    && value.longitude >= -180 && value.longitude <= 180
    && typeof value.address === "string" && value.address.trim().length > 0 && value.address.trim().length <= 300;
}

export async function readBookingInput(request: Request): Promise<BookingInput> {
  const limit = 8192;
  if (!/^application\/json(?:\s*;|$)/i.test(request.headers.get("Content-Type") ?? "")) {
    throw new BookingInputError(415, "Content-Type must be application/json.");
  }
  if (Number(request.headers.get("Content-Length")) > limit) throw new BookingInputError(413, "Booking request is too large.");
  const reader = request.body?.getReader();
  if (!reader) throw new BookingInputError(400, "Request body must be valid JSON.");
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      size += value.length;
      if (size > limit) { await reader.cancel(); throw new BookingInputError(413, "Booking request is too large."); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  let body: unknown;
  try { body = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes)); }
  catch { throw new BookingInputError(400, "Request body must be valid JSON."); }
  if (!object(body) || !coordinate(body.pickup) || !coordinate(body.dropoff)) {
    throw new BookingInputError(400, "Valid pickup and drop-off coordinates and addresses are required.");
  }
  if (body.paymentMethod !== undefined && body.paymentMethod !== "cash") {
    throw new BookingInputError(400, "The pilot accepts cash only.");
  }
  return {
    pickup: { latitude: body.pickup.latitude, longitude: body.pickup.longitude, address: body.pickup.address.trim() },
    dropoff: { latitude: body.dropoff.latitude, longitude: body.dropoff.longitude, address: body.dropoff.address.trim() },
    paymentMethod: "cash",
  };
}

export function routeMetrics(value: unknown): { distanceMeters: number; durationSeconds: number } | null {
  if (!object(value) || value.code !== "Ok" || !Array.isArray(value.routes)) return null;
  const route = value.routes[0];
  if (!object(route) || typeof route.distance !== "number" || typeof route.duration !== "number"
    || !Number.isFinite(route.distance) || !Number.isFinite(route.duration)) return null;
  const distanceMeters = Math.round(route.distance), durationSeconds = Math.round(route.duration);
  return distanceMeters > 0 && distanceMeters <= 2147483647 && durationSeconds > 0 && durationSeconds <= 2147483647 ? { distanceMeters, durationSeconds } : null;
}

export function bookingFailure(error: { message?: string; code?: string }): { status: number; error: string } {
  if (error.message === "PILOT_UNAVAILABLE") return { status: 503, error: "Service currently unavailable. Pilot hours are 06:00–20:00 CAT, Monday–Saturday." };
  if (error.message === "PILOT_OUTSIDE_SERVICE_AREA") return { status: 422, error: "Choose pickup and destination points inside the pilot service area." };
  if (error.message === "PILOT_CASH_ONLY") return { status: 400, error: "The pilot accepts cash only." };
  if (error.code === "23505") return { status: 409, error: "You already have an active trip." };
  return { status: 500, error: "The trip could not be created." };
}
