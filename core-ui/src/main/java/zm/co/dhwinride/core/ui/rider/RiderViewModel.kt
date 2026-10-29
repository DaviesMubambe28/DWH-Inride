package zm.co.dhwinride.core.ui.rider

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import zm.co.dhwinride.core.data.RiderRepository
import zm.co.dhwinride.core.data.TripRepository
import zm.co.dhwinride.core.data.AuthRepository
import zm.co.dhwinride.core.model.RiderEarnings
import zm.co.dhwinride.core.model.RiderProfile
import zm.co.dhwinride.core.model.Trip
import zm.co.dhwinride.core.model.TripStatus
import zm.co.dhwinride.core.location.LocationTracker

enum class RiderNavScreen {
    Home, Request, Pickup, Arrival, Pin, ActiveTrip, Complete, Cash,
    Earnings, History, Profile, Registration, Verification, Safety
}

data class RiderUiState(
    val screen: RiderNavScreen = RiderNavScreen.Home,
    val isOnline: Boolean = false,
    val profile: RiderProfile = RiderProfile(),
    val submittedDocuments: Set<String> = emptySet(),
    val earnings: RiderEarnings = RiderEarnings(),
    val activeTrip: Trip? = null,
    val selectedRequest: Trip? = null,
    val availableRequests: List<Trip> = emptyList(),
    val tripHistory: List<Trip> = emptyList(),
    val pinInput: String = "",
    val paymentReceived: Boolean = false,
    val errorMessage: String? = null,
    val isLoadingRequests: Boolean = false,
    val acceptingTripId: String? = null,
    val cancellingTrip: Boolean = false,
)

class RiderViewModel(
    private val riderRepository: RiderRepository,
    private val tripRepository: TripRepository,
    private val authRepository: AuthRepository? = null,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val locationTracker: LocationTracker? = null,
) : ViewModel() {

    private var requestFeedJob: Job? = null
    private var locationUpdateJob: Job? = null

    private val _uiState = MutableStateFlow(restoreState())
    val uiState: StateFlow<RiderUiState> = _uiState.asStateFlow()

    init {
        authRepository?.let { auth ->
            viewModelScope.launch {
                auth.getPassengerProfile().collect { profile ->
                    if (profile.id.isNotBlank()) {
                        try {
                            riderRepository.refreshRiderState()
                            val submittedDocuments = riderRepository.refreshRiderDocuments()
                            updateState { it.copy(submittedDocuments = submittedDocuments) }
                            tripRepository.getAvailableTripsForRider().getOrThrow()
                        } catch (error: Throwable) {
                            updateState { it.copy(errorMessage = error.message ?: "Could not restore rider trips.") }
                        }
                        startRequestFeed()
                    }
                }
            }
        }
        viewModelScope.launch {
            riderRepository.isOnline().collect { online ->
                updateState { it.copy(isOnline = online) }
                syncRiderLocation(_uiState.value.activeTrip)
            }
        }
        viewModelScope.launch {
            riderRepository.getRiderProfile().collect { profile ->
                updateState { it.copy(profile = profile) }
                syncRiderLocation(_uiState.value.activeTrip)
            }
        }
        viewModelScope.launch {
            riderRepository.getRiderEarnings().collect { earnings ->
                updateState { it.copy(earnings = earnings) }
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
                    val recoveredScreen = when (trip?.status) {
                        zm.co.dhwinride.core.model.TripStatus.RIDER_ASSIGNED -> RiderNavScreen.Pickup
                        zm.co.dhwinride.core.model.TripStatus.RIDER_ARRIVED -> RiderNavScreen.Pin
                        zm.co.dhwinride.core.model.TripStatus.TRIP_IN_PROGRESS -> RiderNavScreen.ActiveTrip
                        zm.co.dhwinride.core.model.TripStatus.TRIP_COMPLETED ->
                            if (trip.passengerMarkedPaid && !trip.riderConfirmedPayment) RiderNavScreen.Cash
                            else RiderNavScreen.Complete
                        else -> null
                    }
                    state.copy(
                        activeTrip = trip,
                        screen = if (trip != null && state.screen in setOf(RiderNavScreen.Home, RiderNavScreen.Request)) {
                            recoveredScreen ?: RiderNavScreen.Request
                        } else state.screen,
                    )
                }
                syncRiderLocation(trip)
            }
        }
        if (authRepository == null) startRequestFeed()
    }

    private fun startRequestFeed() {
        requestFeedJob?.cancel()
        requestFeedJob = viewModelScope.launch {
            while (isActive) {
                updateState { it.copy(isLoadingRequests = true) }
                try {
                    tripRepository.observeAvailableTrips().collect { requests ->
                        updateState { state ->
                            val next = state.copy(availableRequests = requests, isLoadingRequests = false, errorMessage = null)
                            val selected = state.selectedRequest?.let { current -> requests.firstOrNull { it.id == current.id } }
                                ?: requests.firstOrNull()
                            next.copy(
                                selectedRequest = selected,
                                screen = if (state.isOnline && state.activeTrip == null && selected != null) RiderNavScreen.Request else next.screen,
                            )
                        }
                    }
                    if (isActive) updateState { it.copy(availableRequests = emptyList(), isLoadingRequests = false, errorMessage = "Ride request updates stopped. Reconnecting…") }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    updateState {
                        it.copy(
                            availableRequests = emptyList(),
                            isLoadingRequests = false,
                            errorMessage = error.message ?: "Could not load ride requests. Check your connection and retry.",
                        )
                    }
                }
                if (isActive) delay(5_000)
            }
        }
    }

    private fun restoreState(): RiderUiState {
        val screen = savedStateHandle.get<String>("rider.screen")?.let { runCatching { RiderNavScreen.valueOf(it) }.getOrNull() } ?: RiderNavScreen.Home
        return RiderUiState(
            screen = screen,
            pinInput = savedStateHandle["rider.pinInput"] ?: "",
            paymentReceived = savedStateHandle["rider.paymentReceived"] ?: false,
            isLoadingRequests = false,
        )
    }

    private fun persistState(state: RiderUiState) {
        savedStateHandle["rider.screen"] = state.screen.name
        savedStateHandle["rider.pinInput"] = state.pinInput
        savedStateHandle["rider.paymentReceived"] = state.paymentReceived
    }

    private inline fun updateState(crossinline transform: (RiderUiState) -> RiderUiState) {
        _uiState.update { current -> transform(current).also(::persistState) }
    }

    fun navigateTo(screen: RiderNavScreen) {
        updateState { it.copy(screen = screen) }
    }

    fun showError(message: String) {
        updateState { it.copy(errorMessage = message) }
    }

    fun refreshOnForeground() {
        viewModelScope.launch {
            runCatching { riderRepository.refreshRiderState(); tripRepository.refreshRiderTrips() }
                .onSuccess { syncRiderLocation(_uiState.value.activeTrip) }
                .onFailure { error -> updateState { it.copy(availableRequests = emptyList(), errorMessage = error.message ?: "Could not refresh ride requests.") } }
        }
    }

    fun selectRequest(tripId: String) {
        val trip = _uiState.value.availableRequests.firstOrNull { it.id == tripId } ?: return
        updateState { it.copy(selectedRequest = trip, screen = RiderNavScreen.Request, errorMessage = null) }
    }

    fun toggleOnline(online: Boolean) {
        viewModelScope.launch {
            runCatching { riderRepository.setOnline(online) }
                .onSuccess {
                    updateState { state ->
                        val selected = state.selectedRequest?.takeIf { request -> state.availableRequests.any { it.id == request.id } }
                            ?: state.availableRequests.firstOrNull()
                        if (online && selected != null && state.activeTrip == null) state.copy(isOnline = true, selectedRequest = selected, screen = RiderNavScreen.Request, errorMessage = null)
                        else state.copy(isOnline = online, errorMessage = null)
                    }
                }
                .onFailure { error -> updateState { it.copy(errorMessage = error.message) } }
        }
    }

    fun acceptRequest() {
        val current = _uiState.value
        val tripId = current.selectedRequest?.id ?: return
        if (current.acceptingTripId != null) return
        viewModelScope.launch {
            updateState { it.copy(acceptingTripId = tripId, errorMessage = null) }
            tripRepository.acceptTrip(tripId, current.profile.id)
                .onSuccess { assigned -> updateState { state -> state.copy(activeTrip = assigned, selectedRequest = null, screen = RiderNavScreen.Pickup, acceptingTripId = null) } }
                .onFailure { error -> updateState { state ->
                    val stillAvailable = state.availableRequests.any { it.id == tripId }
                    state.copy(
                        selectedRequest = if (stillAvailable) state.selectedRequest else null,
                        acceptingTripId = null,
                        errorMessage = error.message ?: "This trip could not be accepted. Refresh and try again.",
                    )
                } }
        }
    }

    fun declineRequest() {
        // Declining a request is local to this rider; it must not cancel the passenger's trip.
        updateState { it.copy(selectedRequest = null, activeTrip = null, screen = RiderNavScreen.Home) }
    }

    fun confirmArrival() {
        viewModelScope.launch {
            val tripId = _uiState.value.activeTrip?.id ?: return@launch
            runCatching { tripRepository.confirmRiderArrival(tripId) }
                .onSuccess { updateState { it.copy(screen = RiderNavScreen.Arrival, errorMessage = null) } }
                .onFailure { error -> updateState { it.copy(errorMessage = error.message ?: "Could not confirm rider arrival.") } }
        }
    }

    fun appendPinDigit(digit: String) {
        val currentPin = _uiState.value.pinInput
        if (digit == "⌫") {
            updateState { it.copy(pinInput = currentPin.dropLast(1)) }
        } else if (digit == "✓") {
            verifyPin()
        } else if (currentPin.length < 4) {
            val updated = currentPin + digit
            updateState { it.copy(pinInput = updated) }
            if (updated.length == 4) {
                verifyPin()
            }
        }
    }

    fun verifyPin() {
        viewModelScope.launch {
            val currentTrip = _uiState.value.activeTrip ?: return@launch
            runCatching { tripRepository.verifyPin(currentTrip.id, _uiState.value.pinInput) }
                .onSuccess { verified ->
                    if (verified) updateState { it.copy(screen = RiderNavScreen.ActiveTrip, pinInput = "", errorMessage = null) }
                    else updateState { it.copy(errorMessage = "That pickup PIN was not accepted. Check the code and try again.") }
                }
                .onFailure { error -> updateState { it.copy(errorMessage = error.message ?: "Could not verify the pickup PIN.") } }
        }
    }

    fun completeTrip() {
        viewModelScope.launch {
            val tripId = _uiState.value.activeTrip?.id ?: return@launch
            runCatching { tripRepository.completeTrip(tripId) }
                .onSuccess { updateState { it.copy(screen = RiderNavScreen.Complete, errorMessage = null) } }
                .onFailure { error -> updateState { it.copy(errorMessage = error.message ?: "The server could not complete this trip.") } }
        }
    }

    fun togglePaymentReceived() {
        updateState { it.copy(paymentReceived = !it.paymentReceived) }
    }

    fun finishCashConfirmation() {
        viewModelScope.launch {
            val tripId = _uiState.value.activeTrip?.id ?: return@launch
            runCatching { tripRepository.confirmRiderPayment(tripId) }
                .onSuccess { updateState { it.copy(screen = RiderNavScreen.Earnings, paymentReceived = false, errorMessage = null) } }
                .onFailure { error -> updateState { it.copy(errorMessage = error.message ?: "Payment confirmation could not be saved.") } }
        }
    }

    fun cancelPilotTrip(reason: String) {
        if (_uiState.value.cancellingTrip) return
        val trip = _uiState.value.activeTrip ?: return
        updateState { it.copy(cancellingTrip = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                tripRepository.cancelRiderTrip(trip.id, reason)
                updateState { it.copy(activeTrip = null, selectedRequest = null, screen = RiderNavScreen.Home) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { updateState { it.copy(errorMessage = error.message ?: "Cancellation could not be confirmed. Refresh trip details.") } }
            finally { updateState { it.copy(cancellingTrip = false) } }
        }
    }

    fun updateProfile(name: String, phone: String, motorcycleModel: String, plateNumber: String) {
        viewModelScope.launch {
            riderRepository.updateRiderProfile(name, phone, motorcycleModel, plateNumber)
        }
    }

    fun saveMotorcycle(make: String, model: String, year: String, plateNumber: String, color: String, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            runCatching {
                riderRepository.updateMotorcycle(make, model, year.toIntOrNull() ?: 0, plateNumber, color)
            }.onSuccess {
                updateState { it.copy(errorMessage = null) }
                onSaved()
            }.onFailure { error ->
                updateState { it.copy(errorMessage = error.message ?: "Could not save motorcycle details.") }
            }
        }
    }

    fun submitDocument(documentType: String, fileName: String, bytes: ByteArray) {
        viewModelScope.launch {
            runCatching { riderRepository.submitDocument(documentType, fileName, bytes) }
                .onSuccess {
                    updateState { it.copy(submittedDocuments = it.submittedDocuments + documentType, errorMessage = null) }
                }
                .onFailure { error ->
                    updateState { it.copy(errorMessage = error.message ?: "Could not submit document.") }
                }
        }
    }

    private fun syncRiderLocation(trip: Trip?) {
        locationUpdateJob?.cancel()
        val activeTrip = trip?.takeIf { it.status in setOf(
            TripStatus.RIDER_ASSIGNED, TripStatus.RIDER_ARRIVED, TripStatus.TRIP_IN_PROGRESS,
        ) }
        val state = _uiState.value
        if (activeTrip == null && (!state.isOnline || !state.profile.isVerified)) return
        val tracker = locationTracker ?: return
        locationUpdateJob = viewModelScope.launch {
            try {
                tracker.getLocationUpdates().collect { location ->
                    if (_uiState.value.isOnline) {
                        try {
                            tripRepository.recordRiderAvailability(location.latitude, location.longitude)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            updateState { it.copy(errorMessage = "Your pilot location could not be updated. Dispatch pauses until a fresh in-zone position is received.") }
                        }
                    }
                    if (activeTrip != null) {
                        try {
                            tripRepository.recordRiderLocation(activeTrip.id, location.latitude, location.longitude)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) {
                            updateState { it.copy(errorMessage = "Rider location could not be shared.") }
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                updateState { it.copy(errorMessage = "Rider location is unavailable. Enable location permission and refresh trip details.") }
            }
        }
    }

    override fun onCleared() {
        locationUpdateJob?.cancel()
        requestFeedJob?.cancel()
        super.onCleared()
    }
}
