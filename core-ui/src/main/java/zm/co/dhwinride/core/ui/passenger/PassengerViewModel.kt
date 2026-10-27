package zm.co.dhwinride.core.ui.passenger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import zm.co.dhwinride.core.data.AuthRepository
import zm.co.dhwinride.core.data.TripRepository
import zm.co.dhwinride.core.model.FareEstimate
import zm.co.dhwinride.core.model.PassengerProfile
import zm.co.dhwinride.core.model.Place
import zm.co.dhwinride.core.model.RidePaymentMethod
import zm.co.dhwinride.core.model.Trip
import zm.co.dhwinride.core.model.TripStatus
import zm.co.dhwinride.core.model.TripLocation
import zm.co.dhwinride.core.domain.FareCalculator
import zm.co.dhwinride.core.domain.FareConfig
import zm.co.dhwinride.core.location.LocationResult
import zm.co.dhwinride.core.location.LocationTracker
import java.math.BigDecimal
import java.io.IOException
import java.net.SocketTimeoutException

enum class PassengerNavScreen {
    Home, Destination, Estimate, Finding, Confirmed, Tracking, Pin, ActiveTrip,
    Receipt, History, Rating, Profile, Safety
}

data class PassengerUiState(
    val screen: PassengerNavScreen = PassengerNavScreen.Home,
    val destination: String = "",
    val searchQuery: String = "",
    val pickup: Place? = null,
    val dropoff: Place? = null,
    val paymentMethod: RidePaymentMethod = RidePaymentMethod.Cash,
    val fareEstimate: FareEstimate = FareEstimate(),
    val activeTrip: Trip? = null,
    val riderLocation: TripLocation? = null,
    val restoredTripId: String? = null,
    val nearbyPlaces: List<Place> = emptyList(),
    val mapSelection: Place? = null,
    val isResolvingMapSelection: Boolean = false,
    val tripHistory: List<Trip> = emptyList(),
    val passengerProfile: PassengerProfile = PassengerProfile(),
    val pinInput: String = "",
    val ratingInput: Int = 5,
    val isLoading: Boolean = false,
    val isFetchingLocation: Boolean = false,
    val locationError: String? = null,
    val errorMessage: String? = null,
)

class PassengerViewModel(
    private val tripRepository: TripRepository,
    private val authRepository: AuthRepository,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val locationTracker: LocationTracker? = null,
    private val enableLiveLocationPolling: Boolean = true,
    // Development-only estimate rates. The server remains authoritative; product must approve published pricing.
    private val fareCalculator: FareCalculator = FareCalculator(FareConfig(
        baseFareZmw = BigDecimal("15.00"), perKilometerZmw = BigDecimal("5.00"), minimumFareZmw = BigDecimal("20.00"),
    )),
) : ViewModel() {

    private val _uiState = MutableStateFlow(restoreState())
    val uiState: StateFlow<PassengerUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var mapSelectionJob: Job? = null
    private var passengerTripsJob: Job? = null
    private var riderLocationJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.getPassengerProfile().collect { profile ->
                updateState { it.copy(passengerProfile = profile) }
                if (profile.id.isNotBlank()) {
                    passengerTripsJob?.cancel()
                    passengerTripsJob = viewModelScope.launch {
                        tripRepository.getPassengerTrips(profile.id)
                            .onFailure { error -> updateState { it.copy(errorMessage = error.message ?: "Could not restore trips.") } }
                        tripRepository.observePassengerTrips(profile.id)
                            .catch { error -> updateState { it.copy(errorMessage = error.message ?: "Could not watch trip updates.") } }
                            .collect { }
                    }
                }
            }
        }
        viewModelScope.launch {
            tripRepository.getTripHistory().collect { history ->
                updateState { it.copy(tripHistory = history) }
            }
        }
        viewModelScope.launch {
            tripRepository.getActiveTrip().collect { trip ->
                    updateState { state ->
                    val nextScreen = when (trip?.status) {
                        TripStatus.FINDING_RIDER -> PassengerNavScreen.Finding
                        TripStatus.RIDER_ASSIGNED -> if (state.screen == PassengerNavScreen.Tracking) PassengerNavScreen.Tracking else PassengerNavScreen.Confirmed
                        TripStatus.RIDER_ARRIVED -> PassengerNavScreen.Pin
                        TripStatus.TRIP_IN_PROGRESS -> PassengerNavScreen.ActiveTrip
                        TripStatus.TRIP_COMPLETED -> PassengerNavScreen.Receipt
                        TripStatus.RATING_SUBMITTED -> PassengerNavScreen.Home
                        else -> state.screen
                    }
                    state.copy(
                        activeTrip = trip,
                        restoredTripId = trip?.id,
                        screen = if (trip == null && state.restoredTripId != null) PassengerNavScreen.Home else nextScreen,
                    )
                    }
                    syncRiderLocation(trip)
            }
        }
    }

    private fun restoreState(): PassengerUiState {
        val screen = savedStateHandle.get<String>("passenger.screen")?.let { stored ->
            runCatching { PassengerNavScreen.valueOf(stored) }.getOrNull()
        } ?: PassengerNavScreen.Home
        return PassengerUiState(
            screen = screen,
            restoredTripId = savedStateHandle["passenger.activeTripId"],
            destination = savedStateHandle["passenger.destination"] ?: "",
            searchQuery = savedStateHandle["passenger.searchQuery"] ?: "",
            pickup = readPlace("passenger.pickup"),
            dropoff = readPlace("passenger.dropoff"),
            paymentMethod = savedStateHandle.get<String>("passenger.payment")?.let { runCatching { RidePaymentMethod.valueOf(it) }.getOrNull() } ?: RidePaymentMethod.Cash,
            fareEstimate = FareEstimate(
                fareAmount = savedStateHandle["passenger.fare"] ?: 0,
                distanceKm = savedStateHandle["passenger.distance"] ?: 0.0,
            ),
            // A loading request does not survive process death; server state is reloaded after auth.
            isLoading = false,
            isFetchingLocation = false,
            locationError = savedStateHandle["passenger.locationError"],
        )
    }

    private fun readPlace(prefix: String): Place? {
        val name: String? = savedStateHandle["$prefix.name"]
        val detail: String? = savedStateHandle["$prefix.detail"]
        val latitude: Double? = savedStateHandle["$prefix.lat"]
        val longitude: Double? = savedStateHandle["$prefix.lng"]
        return if (name != null && detail != null && latitude != null && longitude != null) {
            Place(name, detail, "", latitude, longitude)
        } else null
    }

    private fun persistState(state: PassengerUiState) {
        savedStateHandle["passenger.screen"] = state.screen.name
        savedStateHandle["passenger.activeTripId"] = state.activeTrip?.id ?: state.restoredTripId
        savedStateHandle["passenger.destination"] = state.destination
        savedStateHandle["passenger.searchQuery"] = state.searchQuery
        savedStateHandle["passenger.payment"] = state.paymentMethod.name
        savedStateHandle["passenger.fare"] = state.fareEstimate.fareAmount
        savedStateHandle["passenger.distance"] = state.fareEstimate.distanceKm
        savedStateHandle["passenger.locationError"] = state.locationError
        savePlace("passenger.pickup", state.pickup)
        savePlace("passenger.dropoff", state.dropoff)
    }

    private fun savePlace(prefix: String, place: Place?) {
        savedStateHandle["$prefix.name"] = place?.name
        savedStateHandle["$prefix.detail"] = place?.detail
        savedStateHandle["$prefix.lat"] = place?.latitude
        savedStateHandle["$prefix.lng"] = place?.longitude
    }

    private inline fun updateState(crossinline transform: (PassengerUiState) -> PassengerUiState) {
        _uiState.update { current -> transform(current).also(::persistState) }
    }

    fun navigateTo(screen: PassengerNavScreen) {
        if (screen != PassengerNavScreen.Destination) {
            mapSelectionJob?.cancel()
        }
        updateState {
            it.copy(
                screen = screen,
                mapSelection = if (screen == PassengerNavScreen.Destination) null else it.mapSelection,
                isResolvingMapSelection = if (screen == PassengerNavScreen.Destination) false else it.isResolvingMapSelection,
            )
        }
    }

    fun refreshOnForeground() {
        val passengerId = _uiState.value.passengerProfile.id
        if (passengerId.isBlank()) return
        viewModelScope.launch {
            tripRepository.getPassengerTrips(passengerId).onFailure { error ->
                updateState { it.copy(errorMessage = error.message ?: "Could not refresh trip status.") }
            }
        }
    }

    private fun syncRiderLocation(trip: Trip?) {
        riderLocationJob?.cancel()
        if (!enableLiveLocationPolling) return
        if (trip == null || trip.rider == null || trip.status !in setOf(
                TripStatus.RIDER_ASSIGNED,
                TripStatus.RIDER_ARRIVED,
                TripStatus.TRIP_IN_PROGRESS,
            )) {
            updateState { it.copy(riderLocation = null) }
            return
        }
        riderLocationJob = viewModelScope.launch {
            while (isActive) {
                runCatching { tripRepository.getLatestRiderLocation(trip.id) }
                    .onSuccess { location -> updateState { it.copy(riderLocation = location) } }
                    .onFailure { error ->
                        updateState { state ->
                            if (state.errorMessage == null) state.copy(errorMessage = "Live rider location is temporarily unavailable.") else state
                        }
                    }
                delay(5_000L)
            }
        }
    }

    override fun onCleared() {
        riderLocationJob?.cancel()
        super.onCleared()
    }

    fun updateSearchQuery(query: String) {
        updateState { it.copy(searchQuery = query) }
    }

    fun setLocationError(message: String?) {
        updateState { it.copy(locationError = message) }
    }

    fun clearLocationError() {
        updateState { it.copy(locationError = null) }
    }

    fun fetchCurrentLocation() {
        val tracker = locationTracker
        if (tracker == null) {
            updateState {
                it.copy(
                    isFetchingLocation = false,
                    locationError = "Current location is unavailable. Enter your pickup manually.",
                )
            }
            return
        }
        if (_uiState.value.isFetchingLocation) return

        viewModelScope.launch {
            updateState { it.copy(isFetchingLocation = true, locationError = null) }
            when (val result = tracker.fetchCurrentLocation()) {
                is LocationResult.Success -> {
                    mapSelectionJob?.cancel()
                    val currentLocation = Place(
                        name = "Current location",
                        detail = result.address.formatted,
                        distance = "",
                        latitude = result.latLng.latitude,
                        longitude = result.latLng.longitude,
                    )
                    updateState {
                        it.copy(
                            pickup = currentLocation,
                            mapSelection = null,
                            isResolvingMapSelection = false,
                            searchQuery = "",
                            nearbyPlaces = emptyList(),
                            isFetchingLocation = false,
                            locationError = null,
                            errorMessage = null,
                        )
                    }
                }

                LocationResult.PermissionDenied -> updateState {
                    it.copy(
                        isFetchingLocation = false,
                        locationError = "Location permission was denied. Enable it in Android Settings or enter your pickup manually.",
                    )
                }

                LocationResult.LocationDisabled -> updateState {
                    it.copy(
                        isFetchingLocation = false,
                        locationError = "Location services are disabled. Enable GPS or Location Services, then try again.",
                    )
                }

                is LocationResult.Error -> updateState {
                    it.copy(
                        isFetchingLocation = false,
                        locationError = result.message,
                    )
                }
            }
        }
    }

    fun choosePlace(place: Place) {
        viewModelScope.launch {
            val current = _uiState.value
            if (current.pickup == null) {
                updateState {
                    it.copy(
                        pickup = place,
                        mapSelection = null,
                        isResolvingMapSelection = false,
                        searchQuery = "",
                        errorMessage = null,
                    )
                }
                return@launch
            }
            updateState { it.copy(isLoading = true, errorMessage = null) }
            runCatching {
                val kilometers = FareCalculator.haversineKilometers(
                    current.pickup.latitude, current.pickup.longitude, place.latitude, place.longitude,
                )
                require(kilometers > 0.001) { "Choose a drop-off different from the pickup location." }
                val amount = fareCalculator.calculateNgwee(current.pickup, place).also { require(it <= Int.MAX_VALUE) }
                FareEstimate(fareAmount = amount.toInt(), distanceKm = kilometers)
            }.onSuccess { estimate ->
                updateState { it.copy(dropoff = place, destination = place.detail, fareEstimate = estimate, screen = PassengerNavScreen.Estimate, searchQuery = "", isLoading = false) }
            }.onFailure { error -> updateState { it.copy(errorMessage = error.message, isLoading = false) } }
        }
    }

    fun resolveMapCenter(latitude: Double, longitude: Double) {
        if (!latitude.isFinite() || !longitude.isFinite()) return
        mapSelectionJob?.cancel()
        updateState { it.copy(mapSelection = null, isResolvingMapSelection = true) }
        mapSelectionJob = viewModelScope.launch {
            delay(350)
            try {
                val place = tripRepository.reverseGeocodeLocation(latitude, longitude)
                updateState { state ->
                    if (state.screen == PassengerNavScreen.Destination) {
                        state.copy(mapSelection = place, isResolvingMapSelection = false)
                    } else state
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val coordinateLabel = "${"%.5f".format(java.util.Locale.US, latitude)}, ${"%.5f".format(java.util.Locale.US, longitude)}"
                updateState { state ->
                    if (state.screen == PassengerNavScreen.Destination) {
                        state.copy(
                            mapSelection = Place("Pinned location", coordinateLabel, "", latitude, longitude),
                            isResolvingMapSelection = false,
                        )
                    } else state
                }
            }
        }
    }

    fun onMapCenterChanging() {
        val current = _uiState.value
        if (current.mapSelection == null && current.isResolvingMapSelection) return
        mapSelectionJob?.cancel()
        updateState { it.copy(mapSelection = null, isResolvingMapSelection = true) }
    }

    fun searchPlaces(query: String) {
        updateState { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            updateState { it.copy(nearbyPlaces = emptyList(), isLoading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            updateState { it.copy(isLoading = true, errorMessage = null) }
            runCatching { tripRepository.searchLocations(query) }
                .onSuccess { places -> if (_uiState.value.searchQuery == query) updateState { it.copy(nearbyPlaces = places, errorMessage = null, isLoading = false) } }
                .onFailure { error -> if (_uiState.value.searchQuery == query) updateState { it.copy(nearbyPlaces = emptyList(), errorMessage = error.message ?: "Location search failed.", isLoading = false) } }
        }
    }

    fun selectDestination(destination: String) {
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, errorMessage = null) }
            runCatching { tripRepository.estimateFare(destination) }
                .onSuccess { estimate -> updateState { it.copy(destination = destination, fareEstimate = estimate, screen = PassengerNavScreen.Estimate, isLoading = false) } }
                .onFailure { error -> updateState { it.copy(errorMessage = error.message, isLoading = false) } }
        }
    }

    fun setPaymentMethod(method: RidePaymentMethod) {
        updateState { it.copy(paymentMethod = method) }
    }

    fun requestRide() {
        if (_uiState.value.isLoading) return
        val current = _uiState.value
        if (current.pickup == null || current.dropoff == null) {
            updateState { it.copy(errorMessage = "Choose both a pickup and drop-off location before booking.") }
            return
        }
        viewModelScope.launch {
            updateState { it.copy(isLoading = true, errorMessage = null) }
            tripRepository.createTrip(current.pickup, current.dropoff, current.paymentMethod, current.passengerProfile).onSuccess { trip ->
                updateState { it.copy(activeTrip = trip, restoredTripId = trip.id, screen = PassengerNavScreen.Finding, isLoading = false) }
            }.onFailure { error -> updateState { it.copy(errorMessage = bookingErrorMessage(error), isLoading = false) } }
        }
    }

    private fun bookingErrorMessage(error: Throwable): String {
        val detail = error.message.orEmpty()
        val text = detail.lowercase()
        return when {
            error is SocketTimeoutException || text.contains("timed out") || text.contains("timeout") ->
                "Booking response timed out. The app could not confirm the result; refresh trips before retrying."
            error is IOException || text.contains("unable to resolve host") || text.contains("network is unreachable") ->
                "No network connection. Check internet access and retry; the app will check for an existing trip first."
            text.contains("401") || text.contains("authentication") || text.contains("session") ->
                "Your sign-in may have expired. Sign in again before booking."
            text.contains("403") || text.contains("42501") || text.contains("permission") || text.contains("not allowed") ->
                "This account is not permitted to book a trip. Check that you are signed in as a passenger."
            text.contains("409") || text.contains("already have an active trip") || text.contains("already been accepted") ->
                "An active trip already exists or this request is no longer available. Refresh your trips."
            text.contains("400") || text.contains("422") || text.contains("coordinate") || text.contains("route") ->
                detail.ifBlank { "Check the pickup and drop-off locations and try again." }
            text.contains("500") || text.contains("502") || text.contains("503") || text.contains("server") ->
                "The booking service is temporarily unavailable. Try again shortly."
            else -> detail.ifBlank { "Booking failed. Check your connection and try again." }
        }
    }

    fun cancelRequest() {
        viewModelScope.launch {
            val tripId = _uiState.value.activeTrip?.id ?: return@launch
            runCatching { tripRepository.cancelTrip(tripId) }
                .onSuccess { updateState { it.copy(activeTrip = null, restoredTripId = null, screen = PassengerNavScreen.Home, errorMessage = null) } }
                .onFailure { error -> updateState { it.copy(errorMessage = error.message ?: "Could not cancel this trip. Refresh and try again.") } }
        }
    }

    fun verifyPin() {
        viewModelScope.launch {
            val currentTrip = _uiState.value.activeTrip ?: return@launch
            val pinToVerify = if (_uiState.value.pinInput.isNotBlank()) _uiState.value.pinInput else currentTrip.pin
            runCatching { tripRepository.verifyPin(currentTrip.id, pinToVerify) }
                .onSuccess { verified ->
                    if (verified) updateState { it.copy(screen = PassengerNavScreen.ActiveTrip, errorMessage = null) }
                    else updateState { it.copy(errorMessage = "That pickup PIN was not accepted. Check the code and try again.") }
                }
                .onFailure { error -> updateState { it.copy(errorMessage = error.message ?: "Could not verify the pickup PIN.") } }
        }
    }

    fun completeTrip() {
        viewModelScope.launch {
            _uiState.value.activeTrip?.id?.let {
                runCatching { tripRepository.completeTrip(it) }
                    .onSuccess { updateState { state -> state.copy(screen = PassengerNavScreen.Receipt, errorMessage = null) } }
                    .onFailure { error -> updateState { state -> state.copy(errorMessage = error.message ?: "The server could not complete this trip.") } }
            }
        }
    }

    fun markPaymentSent() {
        viewModelScope.launch {
            _uiState.value.activeTrip?.id?.let {
                runCatching { tripRepository.markPassengerPaid(it) }
                    .onFailure { error -> updateState { state -> state.copy(errorMessage = error.message ?: "Payment confirmation could not be saved.") } }
            }
        }
    }

    fun submitRating(rating: Int) {
        viewModelScope.launch {
            _uiState.value.activeTrip?.id?.let {
                runCatching { tripRepository.submitRating(it, rating, listOf("Safe ride")) }
                    .onSuccess { updateState { state -> state.copy(ratingInput = rating, screen = PassengerNavScreen.Home, errorMessage = null) } }
                    .onFailure { error -> updateState { state -> state.copy(errorMessage = error.message ?: "Could not save your rating.") } }
            }
        }
    }

    fun updateProfile(fullName: String, phone: String) {
        viewModelScope.launch {
            authRepository.updateProfile(fullName, phone)
        }
    }
}
