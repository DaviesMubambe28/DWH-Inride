package zm.co.dhwinride.core.data

import android.content.Context
import io.github.jan.supabase.SupabaseClient
import android.util.Log
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import javax.net.ssl.SSLException
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import zm.co.dhwinride.core.model.FareEstimate
import zm.co.dhwinride.core.model.PassengerProfile
import zm.co.dhwinride.core.model.Place
import zm.co.dhwinride.core.model.RidePaymentMethod
import zm.co.dhwinride.core.model.RiderEarnings
import zm.co.dhwinride.core.model.RiderProfile
import zm.co.dhwinride.core.model.Trip
import zm.co.dhwinride.core.model.TripStatus
import zm.co.dhwinride.core.model.TripLocation
import zm.co.dhwinride.core.domain.FareCalculator
import zm.co.dhwinride.core.push.FcmTokenRegistrar

private const val TRIP_DETAIL_COLUMNS = "id,passenger_id,rider_id,status,pickup_lat,pickup_lng,pickup_address,dropoff_lat,dropoff_lng,dropoff_address,fare_amount,driver_fare,service_fee,route_distance_meters,route_duration_seconds,payment_method,payment_status,started_at,completed_at,created_at,passenger_name,passenger_phone,rider_name,rider_phone"
private const val RIDER_REQUEST_COLUMNS = "id,status,pickup_lat,pickup_lng,dropoff_lat,dropoff_lng,created_at"

object SupabaseClientProvider {
    fun isConfigured(supabaseUrl: String, supabaseKey: String): Boolean {
        return supabaseUrl.startsWith("https://") &&
            supabaseUrl.endsWith(".supabase.co") &&
            supabaseKey.isNotBlank() &&
            !supabaseUrl.contains("your-project", ignoreCase = true) &&
            supabaseKey != "your-publishable-key"
    }

    fun createClient(supabaseUrl: String, supabaseKey: String): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseKey,
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
    }
}

@Serializable
private data class DbProfile(
    val id: String,
    val role: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("phone_number") val phoneNumber: String,
)

@Serializable
private data class DbRiderState(
    val id: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("phone_number") val phone: String,
    @SerialName("is_online") val isOnline: Boolean,
)

@Serializable
private data class DbMotorcycle(
    val make: String,
    val model: String,
    val year: Int,
    @SerialName("plate_number") val plateNumber: String,
    val color: String,
)

@Serializable
private data class DbRiderDocument(
    @SerialName("document_type") val documentType: String,
    @SerialName("verification_status") val verificationStatus: String,
)

@Serializable
private data class DbTrip(
    val id: String,
    @SerialName("passenger_id") val passengerId: String = "",
    @SerialName("rider_id") val riderId: String? = null,
    @SerialName("pickup_lat") val pickupLat: Double,
    @SerialName("pickup_lng") val pickupLng: Double,
    @SerialName("dropoff_lat") val dropoffLat: Double,
    @SerialName("dropoff_lng") val dropoffLng: Double,
    @SerialName("pickup_address") val pickupAddress: String = "",
    @SerialName("dropoff_address") val dropoffAddress: String = "",
    val status: String,
    @SerialName("fare_amount") val fareAmount: Double? = null,
    @SerialName("driver_fare") val driverFare: Double? = null,
    @SerialName("service_fee") val serviceFee: Double? = null,
    @SerialName("route_distance_meters") val distanceMeters: Int? = null,
    @SerialName("route_duration_seconds") val durationSeconds: Int? = null,
    @SerialName("payment_method") val paymentMethod: String = "cash",
    @SerialName("payment_status") val paymentStatus: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("passenger_name") val passengerName: String? = null,
    @SerialName("passenger_phone") val passengerPhone: String? = null,
    @SerialName("rider_name") val riderName: String? = null,
    @SerialName("rider_phone") val riderPhone: String? = null,
)

@Serializable
private data class DbTripLocation(
    val id: Long = 0,
    @SerialName("trip_id") val tripId: String,
    @SerialName("rider_id") val riderId: String,
    val lat: Double,
    val lng: Double,
    @SerialName("recorded_at") val recordedAt: String,
)

@Serializable
private data class RequestTripBody(
    val pickup: RequestCoordinate,
    val dropoff: RequestCoordinate,
    @SerialName("paymentMethod") val paymentMethod: String = "cash",
)

@Serializable
private data class RequestCoordinate(val latitude: Double, val longitude: Double, val address: String)

@Serializable
private data class RequestTripResponse(val trip: RequestTripResult)

@Serializable
private data class RequestTripResult(
    @SerialName("trip_id") val tripId: String,
    @SerialName("pickup_pin") val pickupPin: String,
    @SerialName("fare_amount") val fareAmount: Double,
    @SerialName("driver_fare") val driverFare: Double,
    @SerialName("service_fee") val serviceFee: Double,
    @SerialName("route_distance_meters") val distanceMeters: Int,
    @SerialName("route_duration_seconds") val durationSeconds: Int,
)

class SupabaseTripRepository(
    private val client: SupabaseClient,
    private val mapboxPublicToken: String = "",
    private val supabaseUrl: String = "",
    private val supabaseAnonKey: String = "",
) : TripRepository {

    private val _activeTrip = MutableStateFlow<Trip?>(null)
    private val _availableRequests = MutableStateFlow<List<Trip>>(emptyList())
    private val _tripHistory = MutableStateFlow<List<Trip>>(emptyList())

    override fun getActiveTrip(): Flow<Trip?> = _activeTrip.asStateFlow()
    override fun getAvailableRequests(): Flow<List<Trip>> = _availableRequests.asStateFlow()
    override fun getTripHistory(): Flow<List<Trip>> = _tripHistory.asStateFlow()

    override suspend fun createTrip(
        pickup: Place,
        dropoff: Place,
        paymentMethod: RidePaymentMethod,
        passenger: PassengerProfile,
    ): Result<Trip> = runCatching {
        val user = requireNotNull(client.auth.currentUserOrNull()) { "Sign in before booking a trip." }
        require(user.id == passenger.id) { "The signed-in passenger profile does not match." }
        require(pickup.isValidLocation() && dropoff.isValidLocation()) { "Select valid pickup and drop-off locations before booking." }
        require(FareCalculator.haversineKilometers(pickup.latitude, pickup.longitude, dropoff.latitude, dropoff.longitude) > 0.001) {
            "Choose a drop-off different from the pickup location."
        }

        // The database allows only one active trip per passenger. Reconcile an unknown prior result before retrying.
        refreshPassengerTrips(user.id)
        _activeTrip.value?.let { existing ->
            if (sameRequest(existing, pickup, dropoff)) return@runCatching existing
            error("You already have an active trip. Reopen it before requesting another ride.")
        }

        try {
            requestTrip(pickup, dropoff, paymentMethod, passenger)
        } catch (requestError: Exception) {
            // A read timeout can happen after the server committed. Read the authoritative active trip before allowing retry.
            runCatching { refreshPassengerTrips(user.id) }
            val reconciled = _activeTrip.value
            if (reconciled != null && sameRequest(reconciled, pickup, dropoff)) reconciled
            else throw requestError
        }
    }

    private fun sameRequest(trip: Trip, pickup: Place, dropoff: Place): Boolean =
        kotlin.math.abs(trip.pickupLatitude - pickup.latitude) < 0.000001 &&
            kotlin.math.abs(trip.pickupLongitude - pickup.longitude) < 0.000001 &&
            kotlin.math.abs(trip.dropoffLatitude - dropoff.latitude) < 0.000001 &&
            kotlin.math.abs(trip.dropoffLongitude - dropoff.longitude) < 0.000001

    override fun observePassengerTrips(passengerId: String): Flow<List<Trip>> = flow {
        require(passengerId.isNotBlank()) { "A signed-in passenger is required." }
        val channel = client.channel("passenger-trips-$passengerId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "trips" }
        try {
            channel.subscribe(blockUntilSubscribed = true)
            refreshPassengerTrips(passengerId)
            emit(listOfNotNull(_activeTrip.value) + _tripHistory.value)
            changes.collect {
                refreshPassengerTrips(passengerId)
                emit(listOfNotNull(_activeTrip.value) + _tripHistory.value)
            }
        } finally {
            client.realtime.removeChannel(channel)
        }
    }

    override fun observeAvailableTrips(): Flow<List<Trip>> = flow {
        val channel = client.channel("rider-available-trips")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "trips" }
        try {
            channel.subscribe(blockUntilSubscribed = true)
            refreshRiderTrips()
            emit(_availableRequests.value)
            changes.collect {
                refreshRiderTrips()
                emit(_availableRequests.value)
            }
        } finally {
            client.realtime.removeChannel(channel)
        }
    }

    override suspend fun refreshPassengerTrips(passengerId: String) {
        require(passengerId.isNotBlank()) { "A signed-in passenger is required." }
        val rows = client.from("authorized_trip_details").select(columns = Columns.list(TRIP_DETAIL_COLUMNS)) {
            filter { eq("passenger_id", passengerId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<DbTrip>()
        _tripHistory.value = rows.map(::toDomainTrip).filter { it.status == TripStatus.TRIP_COMPLETED }
        val activeRow = rows.firstOrNull { it.status in ACTIVE_DB_STATUSES }
        _activeTrip.value = activeRow?.let { row ->
            val pin = runCatching {
                client.postgrest.rpc("get_trip_pickup_pin", buildJsonObject { put("p_trip_id", row.id) }).decodeAs<String>()
            }.getOrDefault("")
            toDomainTrip(row).copy(pin = pin)
        }
    }

    override suspend fun refreshRiderTrips(riderId: String) {
        val currentRiderId = riderId.ifBlank {
            requireNotNull(client.auth.currentUserOrNull()) { "Sign in before loading ride requests." }.id
        }
        val available = client.from("rider_available_trips").select(columns = Columns.list(RIDER_REQUEST_COLUMNS)) {
            filter {
                eq("status", "requesting")
            }
            order("created_at", Order.ASCENDING)
        }.decodeList<DbTrip>().map(::toDomainTrip)
        val riderTrips = client.from("authorized_trip_details").select(columns = Columns.list(TRIP_DETAIL_COLUMNS)) {
            filter { eq("rider_id", currentRiderId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<DbTrip>()
        _tripHistory.value = riderTrips.filter { it.status == "completed" }.map(::toDomainTrip)
        val assigned = riderTrips.firstOrNull {
            it.status in setOf("accepted", "arrived", "in_progress") ||
                (it.status == "completed" && it.paymentStatus != "rider_confirmed")
        }?.let(::toDomainTrip)
        _availableRequests.value = available
        _activeTrip.value = assigned
    }

    override suspend fun searchLocations(query: String): List<Place> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        require(mapboxPublicToken.startsWith("pk.")) { "Location search is not configured. Add MAPBOX_PUBLIC_TOKEN and rebuild." }
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val endpoint = URL("https://api.mapbox.com/geocoding/v5/mapbox.places/$encoded.json?country=zm&limit=6&access_token=$mapboxPublicToken")
        val connection = endpoint.openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.requestMethod = "GET"
        try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            check(responseCode in 200..299) { "Location search failed ($responseCode): $body" }
            Json.parseToJsonElement(body).jsonObject["features"]!!.jsonArray.map { feature ->
                val objectValue = feature.jsonObject
                val point = objectValue["geometry"]!!.jsonObject["coordinates"]!!.jsonArray
                Place(
                    name = objectValue["text"]!!.jsonPrimitive.content,
                    detail = objectValue["place_name"]!!.jsonPrimitive.content,
                    distance = "",
                    longitude = point[0].jsonPrimitive.content.toDouble(),
                    latitude = point[1].jsonPrimitive.content.toDouble(),
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun reverseGeocodeLocation(latitude: Double, longitude: Double): Place = withContext(Dispatchers.IO) {
        require(latitude.isFinite() && longitude.isFinite()) { "Choose a valid point on the map." }
        require(mapboxPublicToken.startsWith("pk.")) { "Map address lookup is not configured. Add MAPBOX_PUBLIC_TOKEN and rebuild." }
        val endpoint = URL(
            "https://api.mapbox.com/geocoding/v5/mapbox.places/$longitude,$latitude.json?country=zm&limit=1&access_token=$mapboxPublicToken",
        )
        val connection = endpoint.openConnection() as HttpURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.requestMethod = "GET"
        try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            check(responseCode in 200..299) { "Map address lookup failed ($responseCode): $body" }
            val feature = Json.parseToJsonElement(body).jsonObject["features"]?.jsonArray?.firstOrNull()?.jsonObject
            if (feature == null) {
                Place(
                    name = "Pinned location",
                    detail = "${"%.5f".format(java.util.Locale.US, latitude)}, ${"%.5f".format(java.util.Locale.US, longitude)}",
                    distance = "",
                    latitude = latitude,
                    longitude = longitude,
                )
            } else {
                Place(
                    name = feature["text"]?.jsonPrimitive?.content ?: "Pinned location",
                    detail = feature["place_name"]?.jsonPrimitive?.content ?: "Pinned location",
                    distance = "",
                    latitude = latitude,
                    longitude = longitude,
                )
            }
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun estimateFare(destination: String): FareEstimate {
        throw IntegrationRequiredException(
            "Fare estimates require a configured routing provider and server-side fare rules.",
        )
    }

    override suspend fun requestTrip(
        destination: String,
        paymentMethod: RidePaymentMethod,
        passenger: PassengerProfile,
    ): Trip {
        throw IntegrationRequiredException(
            "Trip booking is disabled until live pickup/drop-off coordinates and server fare calculation are configured.",
        )
    }

    override suspend fun requestTrip(
        pickup: Place,
        dropoff: Place,
        paymentMethod: RidePaymentMethod,
        passenger: PassengerProfile,
    ): Trip {
        val currentUser = requireNotNull(client.auth.currentUserOrNull()) { "Sign in before booking a trip." }
        require(currentUser.id == passenger.id) { "The signed-in passenger profile does not match." }
        require(pickup.isValidLocation() && dropoff.isValidLocation()) { "Select valid pickup and drop-off locations before booking." }
        val session = requireNotNull(client.auth.currentSessionOrNull()) { "Sign in before booking a trip." }
        val requestBody = RequestTripBody(
                pickup = RequestCoordinate(pickup.latitude, pickup.longitude, pickup.detail),
                dropoff = RequestCoordinate(dropoff.latitude, dropoff.longitude, dropoff.detail),
                paymentMethod = if (paymentMethod == RidePaymentMethod.Cash) "cash" else "mobile_money_external",
            )
        val response = withContext(Dispatchers.IO) {
            require(supabaseUrl.startsWith("https://") && supabaseAnonKey.isNotBlank()) { "Supabase configuration is incomplete." }
            val endpoint = URL("${supabaseUrl.trimEnd('/')}/functions/v1/request-trip")
            val connection = endpoint.openConnection() as HttpURLConnection
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("apikey", supabaseAnonKey)
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            try {
                connection.outputStream.use { it.write(Json.encodeToString(RequestTripBody.serializer(), requestBody).toByteArray(Charsets.UTF_8)) }
                val statusCode = connection.responseCode
                val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream.bufferedReader().use { it.readText() }
                if (statusCode !in 200..299) {
                    val error = runCatching { Json.parseToJsonElement(responseText).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
                    throw IllegalStateException(error ?: "Booking request failed ($statusCode): $responseText")
                }
                Json.decodeFromString(RequestTripResponse.serializer(), responseText).trip
            } finally {
                connection.disconnect()
            }
        }
        val trip = Trip(
            id = response.tripId,
            pickupLocation = pickup.detail,
            destination = dropoff.detail,
            distanceKm = response.distanceMeters / 1000.0,
            fare = FareEstimate(
                fareAmount = toNgwee(response.fareAmount),
                driverFare = toNgwee(response.driverFare),
                serviceFee = toNgwee(response.serviceFee),
                distanceKm = response.distanceMeters / 1000.0,
                durationMinutes = (response.durationSeconds / 60.0).toInt(),
            ),
            paymentMethod = paymentMethod,
            passenger = passenger,
            status = TripStatus.FINDING_RIDER,
            pin = response.pickupPin,
            pickupLatitude = pickup.latitude,
            pickupLongitude = pickup.longitude,
            dropoffLatitude = dropoff.latitude,
            dropoffLongitude = dropoff.longitude,
        )
        _activeTrip.value = trip
        return trip
    }

    override suspend fun cancelTrip(tripId: String) {
        val cancelled = client.postgrest.rpc(
            "cancel_trip",
            buildJsonObject { put("p_trip_id", tripId) },
        ).decodeAs<Boolean>()
        check(cancelled) { "This trip can no longer be cancelled." }
        if (_activeTrip.value?.id == tripId) {
            _activeTrip.value = null
        }
        _availableRequests.value = _availableRequests.value.filterNot { it.id == tripId }
    }

    override suspend fun assignRider(tripId: String, rider: RiderProfile): Trip {
        val signedInRiderId = requireNotNull(client.auth.currentUserOrNull()) { "Sign in before accepting a trip." }.id
        val current = _availableRequests.value.firstOrNull { it.id == tripId }
            ?: _activeTrip.value?.takeIf { it.id == tripId }
            ?: throw IllegalStateException("This trip is no longer in the available request feed. Refresh and try again.")
        val accepted = client.postgrest.rpc(
            "accept_trip",
            buildJsonObject { put("p_trip_id", tripId) },
        ).decodeAs<Boolean>()
        check(accepted) { "This trip has already been accepted by another rider." }
        val updated = runCatching { loadAuthorizedTrip(tripId) }
            .getOrElse {
                refreshRiderTrips(signedInRiderId)
                _activeTrip.value?.takeIf { it.id == tripId } ?: throw it
            }
            .copy(rider = rider.copy(id = signedInRiderId), status = TripStatus.RIDER_ASSIGNED)
        _activeTrip.value = updated
        _availableRequests.value = _availableRequests.value.filterNot { it.id == tripId }
        return updated
    }

    override suspend fun confirmRiderArrival(tripId: String) {
        val arrived = client.postgrest.rpc(
            "mark_trip_arrived",
            buildJsonObject { put("p_trip_id", tripId) },
        ).decodeAs<Boolean>()
        check(arrived) { "The trip is not ready for arrival." }
        _activeTrip.value = _activeTrip.value?.copy(status = TripStatus.RIDER_ARRIVED)
    }

    override suspend fun verifyPin(tripId: String, pin: String): Boolean {
        require(pin.matches(Regex("^[0-9]{4}$"))) { "Enter the 4-digit pickup PIN." }
        val verified = client.postgrest.rpc(
            "verify_trip_pin_and_start",
            buildJsonObject {
                put("p_trip_id", tripId)
                put("p_pin", pin)
            },
        ).decodeAs<Boolean>()
        if (verified) _activeTrip.value = _activeTrip.value?.copy(status = TripStatus.TRIP_IN_PROGRESS)
        return verified
    }

    override suspend fun completeTrip(tripId: String) {
        val current = _activeTrip.value?.takeIf { it.id == tripId }
        val completedOnServer = client.postgrest.rpc(
            "complete_trip",
            buildJsonObject { put("p_trip_id", tripId) },
        ).decodeAs<Boolean>()
        check(completedOnServer) { "The trip must be in progress before it can be completed." }
        val completed = current?.copy(status = TripStatus.TRIP_COMPLETED) ?: loadAuthorizedTrip(tripId)
        _activeTrip.value = completed
        _tripHistory.value = listOf(completed) + _tripHistory.value
    }

    override suspend fun markPassengerPaid(tripId: String) {
        val marked = client.postgrest.rpc(
            "mark_passenger_paid",
            buildJsonObject { put("p_trip_id", tripId) },
        ).decodeAs<Boolean>()
        check(marked) { "Payment can only be acknowledged for a completed trip." }
        _activeTrip.value = _activeTrip.value?.copy(passengerMarkedPaid = true)
    }

    override suspend fun confirmRiderPayment(tripId: String) {
        val confirmed = client.postgrest.rpc(
            "confirm_rider_payment",
            buildJsonObject { put("p_trip_id", tripId) },
        ).decodeAs<Boolean>()
        check(confirmed) { "The passenger must mark payment before rider confirmation." }
        _activeTrip.value = _activeTrip.value?.copy(riderConfirmedPayment = true)
    }

    override suspend fun submitRating(tripId: String, rating: Int, feedback: List<String>) {
        require(rating in 1..5) { "Rating must be between 1 and 5." }
        require(tripId.isNotBlank()) { "A completed trip is required to submit a rating." }
        val comment = feedback.map(String::trim).filter(String::isNotBlank).joinToString(", ").ifBlank { null }
        client.postgrest.rpc(
            "submit_trip_rating",
            buildJsonObject {
                put("p_trip_id", tripId)
                put("p_rating_value", rating)
                put("p_comment", comment ?: "")
            },
        ).decodeAs<String>()
    }

    override suspend fun getNearbyPlaces(query: String): List<Place> {
        return searchLocations(query)
    }

    override suspend fun recordRiderLocation(tripId: String, latitude: Double, longitude: Double) {
        val riderId = requireNotNull(client.auth.currentUserOrNull()) { "Sign in before sharing location." }.id
        require(tripId.isNotBlank()) { "An active trip is required to share location." }
        FareCalculator.validateCoordinates(latitude, longitude)
        client.from("trip_locations").insert(
            mapOf(
                "trip_id" to tripId,
                "rider_id" to riderId,
                "lat" to latitude,
                "lng" to longitude,
            ),
        )
    }

    override suspend fun getLatestRiderLocation(tripId: String): TripLocation? {
        require(tripId.isNotBlank()) { "A trip is required to read rider location." }
        return client.from("trip_locations").select {
            filter { eq("trip_id", tripId) }
            order("recorded_at", Order.DESCENDING)
            limit(1)
        }.decodeList<DbTripLocation>().firstOrNull()?.let { row ->
            TripLocation(row.tripId, row.riderId, row.lat, row.lng, row.recordedAt)
        }
    }

    private fun Place.isValidLocation() = latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 && detail.isNotBlank()

    private fun toNgwee(kwacha: Double): Int = BigDecimal.valueOf(kwacha)
        .setScale(2, RoundingMode.HALF_UP).movePointRight(2).intValueExact()

    private fun toDomainTrip(row: DbTrip): Trip {
        val status = when (row.status) {
            "requesting" -> TripStatus.FINDING_RIDER
            "accepted" -> TripStatus.RIDER_ASSIGNED
            "arrived" -> TripStatus.RIDER_ARRIVED
            "in_progress" -> TripStatus.TRIP_IN_PROGRESS
            "completed" -> TripStatus.TRIP_COMPLETED
            "cancelled" -> TripStatus.IDLE
            else -> error("Unknown server trip status: ${row.status}")
        }
        val fareAmount = row.fareAmount?.let(::toNgwee) ?: 0
        val distanceKm = (row.distanceMeters ?: 0) / 1000.0
        return Trip(
            id = row.id,
            pickupLocation = row.pickupAddress.ifBlank { coordinateLabel(row.pickupLat, row.pickupLng) },
            destination = row.dropoffAddress.ifBlank { coordinateLabel(row.dropoffLat, row.dropoffLng) },
            distanceKm = distanceKm,
            fare = FareEstimate(
                fareAmount = fareAmount,
                driverFare = row.driverFare?.let(::toNgwee) ?: 0,
                serviceFee = row.serviceFee?.let(::toNgwee) ?: 0,
                distanceKm = distanceKm,
                durationMinutes = ((row.durationSeconds ?: 0) / 60.0).toInt(),
            ),
            paymentMethod = if (row.paymentMethod == "mobile_money_external") RidePaymentMethod.MobileMoney else RidePaymentMethod.Cash,
            passenger = PassengerProfile(
                id = row.passengerId,
                name = row.passengerName.orEmpty(),
                phone = row.passengerPhone.orEmpty(),
            ),
            rider = row.riderId?.let { RiderProfile(id = it, name = row.riderName.orEmpty(), phone = row.riderPhone.orEmpty()) },
            status = status,
            passengerMarkedPaid = row.paymentStatus in setOf("passenger_marked_paid", "rider_confirmed"),
            riderConfirmedPayment = row.paymentStatus == "rider_confirmed",
            pickupLatitude = row.pickupLat,
            pickupLongitude = row.pickupLng,
            dropoffLatitude = row.dropoffLat,
            dropoffLongitude = row.dropoffLng,
        )
    }

    private suspend fun loadAuthorizedTrip(tripId: String): Trip =
        client.from("authorized_trip_details")
            .select(columns = Columns.list(TRIP_DETAIL_COLUMNS)) { filter { eq("id", tripId) } }
            .decodeSingle<DbTrip>()
            .let(::toDomainTrip)

    private fun coordinateLabel(latitude: Double, longitude: Double): String =
        "${"%.5f".format(java.util.Locale.US, latitude)}, ${"%.5f".format(java.util.Locale.US, longitude)}"

    private companion object {
        val ACTIVE_DB_STATUSES = setOf("requesting", "accepted", "arrived", "in_progress")
    }
}

class SupabaseAuthRepository(
    private val client: SupabaseClient,
    context: Context,
    private val appId: String,
) : AuthRepository {

    private val appContext = context.applicationContext

    private val _passengerProfile = MutableStateFlow(PassengerProfile())

    override fun getPassengerProfile(): Flow<PassengerProfile> = _passengerProfile.asStateFlow()

    override suspend fun signUp(
        email: String,
        pass: String,
        fullName: String,
        phone: String,
        role: String,
    ): Result<Unit> {
        return try {
            val signupFullName = fullName.trim()
            val signupPhoneNumber = requireNotNull(ZambiaPhoneNumber.normalize(phone)) {
                "Enter a valid Zambian phone number, for example +260 971 793 185."
            }
            val metadata = mapOf(
                "full_name" to signupFullName,
                "phone_number" to signupPhoneNumber,
                "role" to role,
            ).filterValues { it.isNotBlank() }
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = pass
                data = buildJsonObject {
                    metadata.forEach { (key, value) -> put(key, value) }
                    // Informational signup intent only. The database trigger always assigns
                    // passenger; rider approval must remain a trusted server-side action.
                }
            }
            if (role == "rider" && client.auth.currentUserOrNull() != null) {
                client.postgrest.rpc("apply_as_driver").decodeAs<String>()
            }
            _passengerProfile.value = PassengerProfile(
                client.auth.currentUserOrNull()?.id.orEmpty(),
                signupFullName,
                signupPhoneNumber,
            )
            Result.success(Unit)
        } catch (e: AuthRestException) {
            Log.w("SupabaseSignup", "Auth signup rejected with HTTP ${e.statusCode}")
            Result.failure(IllegalStateException(authErrorMessage(e.statusCode, "${e.error} ${e.errorDescription}")))
        } catch (e: RestException) {
            Log.w("SupabaseSignup", "Profile signup request rejected with HTTP ${e.statusCode}")
            Result.failure(IllegalStateException(authErrorMessage(e.statusCode, "${e.error} ${e.description}")))
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            val causeType = generateSequence(e as Throwable?) { it.cause }
                .firstOrNull { it is UnknownHostException || it is SocketTimeoutException || it is ConnectException || it is SSLException }
                ?.javaClass?.simpleName ?: e.javaClass.simpleName
            Log.w("SupabaseSignup", "Signup transport failure ($causeType)")
            Result.failure(IllegalStateException(networkErrorMessage(e)))
        } catch (e: Exception) {
            Log.e("SupabaseSignup", "Unexpected signup failure (${e.javaClass.simpleName})")
            Result.failure(IllegalStateException(authErrorMessage(null, e.message.orEmpty())))
        }
    }

    override suspend fun signIn(email: String, pass: String, expectedRole: String): Result<Unit> {
        return try {
            client.auth.signInWith(Email) {
                this.email = email
                this.password = pass
            }
            val user = client.auth.currentUserOrNull()
            if (user != null) {
                var profile = client.from("profiles").select { filter { eq("id", user.id) } }.decodeSingle<DbProfile>()
                if (expectedRole == "rider" && profile.role == "passenger") {
                    client.postgrest.rpc("apply_as_driver").decodeAs<String>()
                    profile = client.from("profiles").select { filter { eq("id", user.id) } }.decodeSingle()
                }
                if (profile.role != expectedRole) {
                    client.auth.signOut()
                    error("This account is registered as ${profile.role}, not $expectedRole.")
                }
                _passengerProfile.value = PassengerProfile(profile.id, profile.fullName, profile.phoneNumber)
            }
            try {
                FcmTokenRegistrar.registerIfSignedIn(appContext, client, appId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("FcmToken", "Token registration failed (${e.javaClass.simpleName})")
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signOut() {
        try {
            FcmTokenRegistrar.unregisterCurrentTokenIfSignedIn(appContext, client)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("FcmToken", "Token unregistration failed (${e.javaClass.simpleName})")
        }
        client.auth.signOut()
        _passengerProfile.value = PassengerProfile()
    }

    override suspend fun verifyEmail(email: String): Boolean = client.auth.currentUserOrNull() != null

    override suspend fun updateProfile(fullName: String, phone: String) {
        _passengerProfile.value = _passengerProfile.value.copy(name = fullName, phone = phone)
        val user = requireNotNull(client.auth.currentUserOrNull()) { "Sign in before updating the profile." }
        client.from("profiles").update(mapOf("full_name" to fullName, "phone_number" to phone)) { filter { eq("id", user.id) } }
    }
}

/** Classifies server errors without returning raw SDK messages or request details. */
private fun authErrorMessage(statusCode: Int?, errorDetails: String): String {
    val normalized = errorDetails.lowercase().replace('-', '_')
    val compact = normalized.replace("_", "").replace(" ", "")

    return when {
        "enter a valid zambian phone number" in normalized ->
            "Enter a valid Zambian phone number, for example +260 971 793 185."

        "useralreadyexists" in compact || "emailexists" in compact || "user_already_exists" in normalized ||
            "user_already_registered" in normalized || "user already registered" in normalized ->
            "An account with this email already exists. Please sign in."

        "phone" in normalized && ("invalid" in normalized || "format" in normalized) ->
            "Enter a valid Zambian phone number, for example +260 971 793 185."

        "weakpassword" in compact || "password should be at least" in normalized ||
            "password_too_short" in normalized ->
            "Password is too short. Use at least 8 characters."

        "invalidemail" in compact || "email_address_invalid" in normalized || "invalid email" in normalized ->
            "Please enter a valid email address."

        "phone_exists" in normalized || "profiles_phone_number_key" in normalized ||
            ("duplicate key" in normalized && "phone" in normalized) ->
            "An account with this phone number already exists."

        "database error saving new user" in normalized || "handle_new_auth_user" in normalized ||
            "unexpected_failure" in normalized || "database_error" in normalized ||
            "signup requires a full name" in normalized || "signup requires a phone number" in normalized ->
            "We couldn't save your account details. Check your name and phone number, then try again."

        statusCode == 429 -> "Too many signup attempts. Wait a moment and try again."
        statusCode != null && statusCode >= 500 -> "The signup service is temporarily unavailable. Try again shortly."
        statusCode != null && statusCode in 400..499 -> "Check your email, password, and phone number, then try again."

        else -> "We couldn't create your account. Please try again."
    }
}

private fun networkErrorMessage(error: IOException): String {
    val causes = generateSequence(error as Throwable?) { it.cause }.toList()
    return when {
        causes.any { it is UnknownHostException } ->
            "We couldn't reach the signup service. Check your connection and try again."
        causes.any { it is SocketTimeoutException } ->
            "The signup service took too long to respond. Try again."
        causes.any { it is SSLException } ->
            "A secure connection to the signup service couldn't be established. Check your device date and time, then try again."
        causes.any { it is ConnectException } ->
            "The signup service couldn't be reached. Try again shortly."
        else -> "The signup request couldn't be completed. Check your connection and try again."
    }
}

class SupabaseRiderRepository(
    private val client: SupabaseClient,
) : RiderRepository {

    private val _isOnline = MutableStateFlow(false)
    private val _riderProfile = MutableStateFlow(RiderProfile())
    private val _riderEarnings = MutableStateFlow(RiderEarnings())

    override fun getRiderProfile(): Flow<RiderProfile> = _riderProfile.asStateFlow()
    override fun getRiderEarnings(): Flow<RiderEarnings> = _riderEarnings.asStateFlow()
    override fun isOnline(): Flow<Boolean> = _isOnline.asStateFlow()

    override suspend fun refreshRiderState() {
        val user = requireNotNull(client.auth.currentUserOrNull()) { "Sign in before loading rider status." }
        val profile = client.from("profiles").select { filter { eq("id", user.id) } }.decodeSingle<DbRiderState>()
        _isOnline.value = profile.isOnline
        val motorcycle = runCatching {
            client.from("motorcycles").select {
                filter { eq("rider_id", user.id) }
                limit(1)
            }.decodeList<DbMotorcycle>().firstOrNull()
        }.getOrNull()
        _riderProfile.value = _riderProfile.value.copy(
            id = profile.id,
            name = profile.fullName,
            phone = profile.phone,
            motorcycleMake = motorcycle?.make.orEmpty(),
            motorcycleModel = motorcycle?.model.orEmpty(),
            motorcycleYear = motorcycle?.year,
            plateNumber = motorcycle?.plateNumber.orEmpty(),
            motorcycleColor = motorcycle?.color.orEmpty(),
        )
    }

    override suspend fun setOnline(online: Boolean) {
        requireNotNull(client.auth.currentUserOrNull()) { "Sign in before changing availability." }
        val persisted = client.postgrest.rpc(
            "set_driver_online",
            buildJsonObject { put("p_online", online) },
        ).decodeAs<Boolean>()
        _isOnline.value = persisted
    }

    override suspend fun updateRiderProfile(
        name: String,
        phone: String,
        motorcycleModel: String,
        plateNumber: String,
    ) {
        val user = requireNotNull(client.auth.currentUserOrNull()) { "Sign in before updating your rider profile." }
        require(name.isNotBlank()) { "Enter your full name." }
        require(phone.isNotBlank()) { "Enter your phone number." }
        client.from("profiles").update(
            mapOf("full_name" to name.trim(), "phone_number" to phone.trim()),
        ) { filter { eq("id", user.id) } }
        _riderProfile.value = _riderProfile.value.copy(
            name = name.trim(),
            phone = phone.trim(),
            motorcycleModel = motorcycleModel,
            plateNumber = plateNumber,
        )
    }

    override suspend fun uploadDocument(path: String, byteArray: ByteArray) {
        val user = requireNotNull(client.auth.currentUserOrNull()) { "Sign in before uploading documents." }
        require(path.startsWith("${user.id}/")) { "Document path must be inside the signed-in rider folder." }
        client.storage.from("rider-documents").upload(path, byteArray)
    }

    override suspend fun updateMotorcycle(
        make: String,
        model: String,
        year: Int,
        plateNumber: String,
        color: String,
    ) {
        val user = requireNotNull(client.auth.currentUserOrNull()) { "Sign in before saving motorcycle details." }
        require(make.isNotBlank() && model.isNotBlank() && plateNumber.isNotBlank() && color.isNotBlank()) {
            "Complete all motorcycle fields."
        }
        require(year in 1950..2100) { "Enter a valid motorcycle year." }
        val existing = client.from("motorcycles").select {
            filter { eq("rider_id", user.id) }
            limit(1)
        }.decodeList<DbMotorcycle>().firstOrNull()
        val values = mapOf(
            "rider_id" to user.id,
            "make" to make.trim(),
            "model" to model.trim(),
            "year" to year,
            "plate_number" to plateNumber.trim().uppercase(),
            "color" to color.trim(),
        )
        if (existing == null) {
            client.from("motorcycles").insert(values)
        } else {
            client.from("motorcycles").update(values - "rider_id") {
                filter { eq("rider_id", user.id) }
            }
        }
        _riderProfile.value = _riderProfile.value.copy(
            motorcycleMake = make.trim(),
            motorcycleModel = model.trim(),
            motorcycleYear = year,
            plateNumber = plateNumber.trim().uppercase(),
            motorcycleColor = color.trim(),
        )
    }

    override suspend fun submitDocument(documentType: String, fileName: String, byteArray: ByteArray) {
        val user = requireNotNull(client.auth.currentUserOrNull()) { "Sign in before submitting documents." }
        require(documentType in setOf("nrc", "license", "vehicle_registration")) { "Unsupported document type." }
        require(byteArray.isNotEmpty()) { "The selected document is empty." }
        val safeName = fileName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "document.jpg" }
        val path = "${user.id}/$documentType-${UUID.randomUUID()}-$safeName"
        client.storage.from("rider-documents").upload(path, byteArray)
        client.from("rider_documents").insert(
            mapOf(
                "rider_id" to user.id,
                "document_type" to documentType,
                "storage_url" to path,
                "verification_status" to "pending",
            ),
        )
    }

    override suspend fun refreshRiderDocuments(): Set<String> {
        val user = requireNotNull(client.auth.currentUserOrNull()) { "Sign in before loading documents." }
        return client.from("rider_documents").select {
            filter { eq("rider_id", user.id) }
        }.decodeList<DbRiderDocument>()
            .filter { it.verificationStatus != "rejected" }
            .map { it.documentType }
            .toSet()
    }
}
