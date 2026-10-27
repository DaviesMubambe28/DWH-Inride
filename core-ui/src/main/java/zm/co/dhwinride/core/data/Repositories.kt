package zm.co.dhwinride.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import zm.co.dhwinride.core.model.FareEstimate
import zm.co.dhwinride.core.model.PassengerProfile
import zm.co.dhwinride.core.model.RidePaymentMethod
import zm.co.dhwinride.core.model.RiderEarnings
import zm.co.dhwinride.core.model.RiderProfile
import zm.co.dhwinride.core.model.Trip
import zm.co.dhwinride.core.model.Place
import zm.co.dhwinride.core.model.TripLocation

interface TripRepository {
    fun getActiveTrip(): Flow<Trip?>
    fun getAvailableRequests(): Flow<List<Trip>>
    fun observeAvailableTrips(): Flow<List<Trip>> = getAvailableRequests()
    fun getTripHistory(): Flow<List<Trip>>
    fun observePassengerTrips(passengerId: String): Flow<List<Trip>> = getTripHistory()
    suspend fun refreshPassengerTrips(passengerId: String)
    suspend fun refreshRiderTrips(riderId: String = "")
    suspend fun searchLocations(query: String): List<Place>
    suspend fun reverseGeocodeLocation(latitude: Double, longitude: Double): Place
    suspend fun createTrip(pickup: Place, dropoff: Place, paymentMethod: RidePaymentMethod, passenger: PassengerProfile): Result<Trip> =
        runCatching { requestTrip(pickup, dropoff, paymentMethod, passenger) }
    suspend fun getPassengerTrips(passengerId: String): Result<List<Trip>> = runCatching {
        refreshPassengerTrips(passengerId)
        listOfNotNull(getActiveTrip().firstOrNullValue()) + getTripHistory().firstOrNullValue()
    }
    suspend fun getAvailableTripsForRider(): Result<List<Trip>> = runCatching {
        refreshRiderTrips()
        getAvailableRequests().firstOrNullValue()
    }
    suspend fun acceptTrip(tripId: String, riderId: String): Result<Trip> = runCatching {
        assignRider(tripId, RiderProfile(id = riderId))
    }
    suspend fun estimateFare(destination: String): FareEstimate
    suspend fun requestTrip(destination: String, paymentMethod: RidePaymentMethod, passenger: PassengerProfile = PassengerProfile()): Trip
    suspend fun requestTrip(pickup: Place, dropoff: Place, paymentMethod: RidePaymentMethod, passenger: PassengerProfile): Trip
    suspend fun cancelTrip(tripId: String)
    suspend fun assignRider(tripId: String, rider: RiderProfile = RiderProfile()): Trip
    suspend fun confirmRiderArrival(tripId: String)
    suspend fun verifyPin(tripId: String, pin: String): Boolean
    suspend fun completeTrip(tripId: String)
    suspend fun markPassengerPaid(tripId: String)
    suspend fun confirmRiderPayment(tripId: String)
    suspend fun submitRating(tripId: String, rating: Int, feedback: List<String>)
    suspend fun getNearbyPlaces(query: String): List<Place>
    suspend fun recordRiderLocation(tripId: String, latitude: Double, longitude: Double) {
        throw IntegrationRequiredException("Rider location recording is not configured.")
    }
    suspend fun getLatestRiderLocation(tripId: String): TripLocation? = null
}

private suspend fun <T> Flow<T>.firstOrNullValue(): T = first()

interface RiderRepository {
    fun getRiderProfile(): Flow<RiderProfile>
    fun getRiderEarnings(): Flow<RiderEarnings>
    fun isOnline(): Flow<Boolean>
    suspend fun setOnline(online: Boolean)
    suspend fun refreshRiderState()
    suspend fun updateRiderProfile(name: String, phone: String, motorcycleModel: String, plateNumber: String)
    suspend fun uploadDocument(path: String, byteArray: ByteArray)
    suspend fun updateMotorcycle(make: String, model: String, year: Int, plateNumber: String, color: String) {
        throw IntegrationRequiredException("Motorcycle registration is not configured.")
    }
    suspend fun submitDocument(documentType: String, fileName: String, byteArray: ByteArray) {
        throw IntegrationRequiredException("Document submission is not configured.")
    }
    suspend fun refreshRiderDocuments(): Set<String> = emptySet()
}

interface AuthRepository {
    fun getPassengerProfile(): Flow<PassengerProfile>
    suspend fun signUp(email: String, pass: String, fullName: String, phone: String, role: String): Result<Unit>
    suspend fun signIn(email: String, pass: String, expectedRole: String): Result<Unit>
    suspend fun signOut()
    suspend fun verifyEmail(email: String): Boolean
    suspend fun updateProfile(fullName: String, phone: String)
}

class IntegrationRequiredException(message: String) : IllegalStateException(message)
