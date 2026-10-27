package zm.co.dhwinride.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import zm.co.dhwinride.core.model.FareEstimate
import zm.co.dhwinride.core.model.PassengerProfile
import zm.co.dhwinride.core.model.Place
import zm.co.dhwinride.core.model.RidePaymentMethod
import zm.co.dhwinride.core.model.RiderEarnings
import zm.co.dhwinride.core.model.RiderProfile
import zm.co.dhwinride.core.model.Trip
import zm.co.dhwinride.core.model.TripStatus

class TestTripRepository : TripRepository {
    var failNextComplete = false
    var requestFailure: Throwable? = null
    private val active = MutableStateFlow<Trip?>(null)
    private val requests = MutableStateFlow<List<Trip>>(emptyList())
    private val history = MutableStateFlow<List<Trip>>(emptyList())
    fun publishAsAvailableOffer() { active.value = null }
    override fun getActiveTrip(): Flow<Trip?> = active.asStateFlow()
    override fun getAvailableRequests(): Flow<List<Trip>> = requests.asStateFlow()
    override fun getTripHistory(): Flow<List<Trip>> = history.asStateFlow()
    override suspend fun refreshPassengerTrips(passengerId: String) = Unit
    override suspend fun refreshRiderTrips(riderId: String) = Unit
    override suspend fun searchLocations(query: String) = emptyList<Place>()
    override suspend fun reverseGeocodeLocation(latitude: Double, longitude: Double) =
        Place("Pinned location", "Test address", "", latitude, longitude)
    override suspend fun estimateFare(destination: String) = FareEstimate(4000, 3400, 600, 2.0, 8)
    override suspend fun requestTrip(destination: String, paymentMethod: RidePaymentMethod, passenger: PassengerProfile): Trip {
        requestFailure?.let { throw it }
        val trip = Trip(id = "test-trip", destination = destination, fare = estimateFare(destination), paymentMethod = paymentMethod, passenger = passenger, status = TripStatus.FINDING_RIDER, pin = "2468")
        active.value = trip; requests.value = listOf(trip); return trip
    }
    override suspend fun requestTrip(pickup: Place, dropoff: Place, paymentMethod: RidePaymentMethod, passenger: PassengerProfile): Trip {
        requestFailure?.let { throw it }
        val trip = Trip(
            id = "test-trip", pickupLocation = pickup.detail, destination = dropoff.detail,
            fare = estimateFare(dropoff.detail), paymentMethod = paymentMethod, passenger = passenger,
            status = TripStatus.FINDING_RIDER, pin = "2468",
            pickupLatitude = pickup.latitude, pickupLongitude = pickup.longitude,
            dropoffLatitude = dropoff.latitude, dropoffLongitude = dropoff.longitude,
        )
        active.value = trip; requests.value = listOf(trip); return trip
    }
    override suspend fun cancelTrip(tripId: String) { active.value = null; requests.value = emptyList() }
    override suspend fun assignRider(tripId: String, rider: RiderProfile): Trip {
        val available = requests.value.firstOrNull { it.id == tripId }
            ?: throw IllegalStateException("This trip has already been accepted by another rider.")
        val assigned = available.copy(rider = rider.copy(id = "rider", name = "Test Rider"), status = TripStatus.RIDER_ASSIGNED)
        active.value = assigned; requests.value = emptyList(); return assigned
    }
    override suspend fun confirmRiderArrival(tripId: String) { active.value = active.value?.copy(status = TripStatus.RIDER_ARRIVED) }
    override suspend fun verifyPin(tripId: String, pin: String): Boolean {
        val valid = active.value?.pin == pin
        if (valid) active.value = active.value?.copy(status = TripStatus.TRIP_IN_PROGRESS)
        return valid
    }
    override suspend fun completeTrip(tripId: String) {
        if (failNextComplete) { failNextComplete = false; error("Server refused trip completion") }
        val done = requireNotNull(active.value).copy(status = TripStatus.TRIP_COMPLETED); active.value = done; history.value = listOf(done)
    }
    override suspend fun markPassengerPaid(tripId: String) { active.value = active.value?.copy(passengerMarkedPaid = true) }
    override suspend fun confirmRiderPayment(tripId: String) { active.value = active.value?.copy(riderConfirmedPayment = true) }
    override suspend fun submitRating(tripId: String, rating: Int, feedback: List<String>) { active.value = active.value?.copy(status = TripStatus.RATING_SUBMITTED) }
    override suspend fun getNearbyPlaces(query: String) = listOf(Place("Result", "Test address", "2 km"))
}

class TestRiderRepository(private val trips: TestTripRepository) : RiderRepository {
    private val online = MutableStateFlow(false)
    private val profile = MutableStateFlow(RiderProfile(id = "rider", name = "Test Rider", isVerified = true))
    override fun getRiderProfile() = profile.asStateFlow()
    override fun getRiderEarnings(): Flow<RiderEarnings> = trips.getTripHistory().map { rows -> RiderEarnings(todayAmount = rows.sumOf { it.fare.driverFare }, todayTripsCount = rows.size) }
    override fun isOnline() = online.asStateFlow()
    override suspend fun setOnline(online: Boolean) { this.online.value = online }
    override suspend fun refreshRiderState() = Unit
    override suspend fun updateRiderProfile(name: String, phone: String, motorcycleModel: String, plateNumber: String) { profile.value = profile.value.copy(name = name, phone = phone, motorcycleModel = motorcycleModel, plateNumber = plateNumber) }
    override suspend fun uploadDocument(path: String, byteArray: ByteArray) = Unit
}

class TestAuthRepository : AuthRepository {
    private val profile = MutableStateFlow(PassengerProfile(id = "passenger", name = "Test Passenger", phone = "+260000000"))
    override fun getPassengerProfile() = profile.asStateFlow()
    override suspend fun signUp(email: String, pass: String, fullName: String, phone: String, role: String) = Result.success(Unit)
    override suspend fun signIn(email: String, pass: String, expectedRole: String) = Result.success(Unit)
    override suspend fun signOut() = Unit
    override suspend fun verifyEmail(email: String) = true
    override suspend fun updateProfile(fullName: String, phone: String) { profile.value = profile.value.copy(name = fullName, phone = phone) }
}
