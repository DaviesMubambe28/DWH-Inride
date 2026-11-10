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
import zm.co.dhwinride.core.model.RiderServiceFeeAccount
import zm.co.dhwinride.core.model.Trip
import zm.co.dhwinride.core.model.TripStatus
import zm.co.dhwinride.core.ui.userSafeErrorMessage
import zm.co.dhwinride.core.location.LocationTracker
import java.math.BigDecimal
import java.math.RoundingMode

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
    val verifyingPin: Boolean = false,
    val serviceFeeAccount: RiderServiceFeeAccount = RiderServiceFeeAccount(),
    val serviceFeeLoading: Boolean = false,
    val serviceFeeSubmitting: Boolean = false,
    val serviceFeeMessage: String? = null,
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
    private var serviceFeeJob: Job? = null

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
                            startServiceFeeFeed()
                        } catch (error: Throwable) {
                            updateState { it.copy(errorMessage = userSafeErrorMessage(error, "Could not restore rider trips.")) }
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
                    state.copy(
                        activeTrip = trip,
                        screen = reconciledScreen(state.screen, trip),
                        pinInput = if (trip?.status != TripStatus.RIDER_ARRIVED || trip.id != state.activeTrip?.id) "" else state.pinInput,
                        paymentReceived = if (trip == null || trip.id != state.activeTrip?.id) false else state.paymentReceived,
                    )
                }
                syncRiderLocation(trip)
            }
        }
        if (authRepository == null) startRequestFeed()
    }

    private fun reconciledScreen(current: RiderNavScreen, trip: Trip?): RiderNavScreen {
        val tripScreen = current in setOf(
            RiderNavScreen.Pickup, RiderNavScreen.Arrival, RiderNavScreen.Pin,
            RiderNavScreen.ActiveTrip, RiderNavScreen.Complete, RiderNavScreen.Cash,
        )
        // Profile/history navigation is independent; active-trip screens follow server state.
        if (!tripScreen && current !in setOf(RiderNavScreen.Home, RiderNavScreen.Request)) return current
        if (trip == null) return if (tripScreen) RiderNavScreen.Home else current
        return when (trip.status) {
            TripStatus.RIDER_ASSIGNED -> RiderNavScreen.Pickup
            TripStatus.RIDER_ARRIVED -> if (current == RiderNavScreen.Arrival) current else RiderNavScreen.Pin
            TripStatus.TRIP_IN_PROGRESS -> RiderNavScreen.ActiveTrip
            TripStatus.TRIP_COMPLETED -> if (trip.passengerMarkedPaid && !trip.riderConfirmedPayment) RiderNavScreen.Cash else RiderNavScreen.Complete
            else -> if (tripScreen) RiderNavScreen.Home else RiderNavScreen.Request
        }
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
                            errorMessage = userSafeErrorMessage(error, "Could not load ride requests. Check your connection and retry."),
                        )
                    }
                }
                if (isActive) delay(5_000)
            }
        }
    }

    private fun startServiceFeeFeed() {
        serviceFeeJob?.cancel()
        serviceFeeJob = viewModelScope.launch {
            try {
                tripRepository.observeRiderServiceFeeAccount().collect { account ->
                    updateState { it.copy(serviceFeeAccount = account, serviceFeeLoading = false, errorMessage = null) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                refreshServiceFeeAccount()
            }
        }
    }

    private fun restoreState(): RiderUiState {
        // Scrub legacy saved-state credentials; a pickup PIN must remain memory-only.
        savedStateHandle.remove<String>("rider.pinInput")
        val screen = savedStateHandle.get<String>("rider.screen")?.let { runCatching { RiderNavScreen.valueOf(it) }.getOrNull() } ?: RiderNavScreen.Home
        return RiderUiState(
            screen = screen,
            pinInput = "",
            paymentReceived = savedStateHandle["rider.paymentReceived"] ?: false,
            isLoadingRequests = false,
        )
    }

    private fun persistState(state: RiderUiState) {
        savedStateHandle["rider.screen"] = state.screen.name
        savedStateHandle.remove<String>("rider.pinInput")
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
                .onSuccess { syncRiderLocation(_uiState.value.activeTrip); refreshServiceFeeAccount() }
                .onFailure { error -> updateState { it.copy(availableRequests = emptyList(), errorMessage = userSafeErrorMessage(error, "Could not refresh ride requests.")) } }
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
                .onFailure { error -> updateState { it.copy(errorMessage = userSafeErrorMessage(error, "Could not accept this ride request.")) } }
        }
    }

    fun acceptRequest() {
        val current = _uiState.value
        val tripId = current.selectedRequest?.id ?: return
        if (current.acceptingTripId != null) return
        // Set the guard before dispatch so two taps in the same frame cannot race.
        updateState { it.copy(acceptingTripId = tripId, errorMessage = null) }
        viewModelScope.launch {
            try {
                val assigned = tripRepository.acceptTrip(tripId, current.profile.id).getOrThrow()
                updateState { state -> state.copy(activeTrip = assigned, selectedRequest = null, screen = RiderNavScreen.Pickup) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                updateState { state ->
                    val stillAvailable = state.availableRequests.any { it.id == tripId }
                    state.copy(
                        selectedRequest = if (stillAvailable) state.selectedRequest else null,
                        errorMessage = "This trip could not be accepted. Refresh and try again.",
                    )
                }
            } finally {
                updateState { it.copy(acceptingTripId = null) }
            }
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
                .onFailure { error -> updateState { it.copy(errorMessage = userSafeErrorMessage(error, "Could not confirm rider arrival.")) } }
        }
    }

    fun appendPinDigit(digit: String) {
        if (_uiState.value.verifyingPin) return
        val currentPin = _uiState.value.pinInput
        if (digit == "⌫") {
            updateState { it.copy(pinInput = currentPin.dropLast(1)) }
        } else if (digit == "✓") {
            verifyPin()
        } else if (currentPin.length < 4 && digit.matches(Regex("[0-9]"))) {
            val updated = currentPin + digit
            updateState { it.copy(pinInput = updated) }
            if (updated.length == 4) {
                verifyPin()
            }
        }
    }

    fun verifyPin() {
        val state = _uiState.value
        val tripId = state.activeTrip?.id ?: return
        val code = state.pinInput
        if (state.verifyingPin || !code.matches(Regex("[0-9]{4}"))) return
        updateState { it.copy(verifyingPin = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val verified = tripRepository.verifyPin(tripId, code)
                // An interrupted/cancelled trip must not be resurrected by a late response.
                val authoritativeTrip = _uiState.value.activeTrip
                if (authoritativeTrip?.id == tripId && (
                    authoritativeTrip.status == TripStatus.RIDER_ARRIVED ||
                        (verified && authoritativeTrip.status == TripStatus.TRIP_IN_PROGRESS)
                )) {
                    updateState { it.copy(
                        screen = if (verified) RiderNavScreen.ActiveTrip else it.screen,
                        pinInput = "",
                        errorMessage = if (verified) null else "That pickup PIN was not accepted. Check the code and try again.",
                    ) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (_uiState.value.activeTrip?.id == tripId) updateState {
                    it.copy(pinInput = "", errorMessage = "Could not verify the pickup PIN. Refresh trip details and try again.")
                }
            } finally {
                updateState { it.copy(verifyingPin = false) }
            }
        }
    }

    fun completeTrip() {
        viewModelScope.launch {
            val tripId = _uiState.value.activeTrip?.id ?: return@launch
            runCatching { tripRepository.completeTrip(tripId) }
                .onSuccess { updateState { it.copy(screen = RiderNavScreen.Complete, errorMessage = null) }; refreshServiceFeeAccount() }
                .onFailure { error -> updateState { it.copy(errorMessage = userSafeErrorMessage(error, "The server could not complete this trip.")) } }
        }
    }

    fun togglePaymentReceived() {
        updateState { it.copy(paymentReceived = !it.paymentReceived) }
    }

    fun finishCashConfirmation() {
        viewModelScope.launch {
            val tripId = _uiState.value.activeTrip?.id ?: return@launch
            runCatching { tripRepository.confirmRiderPayment(tripId) }
                .onSuccess { updateState { it.copy(screen = RiderNavScreen.Earnings, paymentReceived = false, errorMessage = null) }; refreshServiceFeeAccount() }
                .onFailure { error -> updateState { it.copy(errorMessage = userSafeErrorMessage(error, "Payment confirmation could not be saved.")) } }
        }
    }

    fun refreshServiceFeeAccount() {
        if (_uiState.value.serviceFeeLoading) return
        updateState { it.copy(serviceFeeLoading = true) }
        viewModelScope.launch {
            runCatching { tripRepository.getRiderServiceFeeAccount() }
                .onSuccess { account -> updateState { it.copy(serviceFeeAccount = account, serviceFeeLoading = false, errorMessage = null) } }
                .onFailure { error -> updateState { it.copy(serviceFeeLoading = false, errorMessage = userSafeErrorMessage(error, "Could not refresh service-fee balance.")) } }
        }
    }

    fun submitServiceFeePayment(amountText: String, transactionReference: String) {
        if (_uiState.value.serviceFeeSubmitting) return
        val amountNgwee = runCatching {
            BigDecimal(amountText.trim()).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).intValueExact()
        }.getOrNull()
        if (amountNgwee == null || amountNgwee <= 0) {
            updateState { it.copy(errorMessage = "Enter a valid ZMW amount with no more than two decimal places.") }
            return
        }
        if (transactionReference.trim().length !in 4..120) {
            updateState { it.copy(errorMessage = "Enter the mobile-money transaction reference.") }
            return
        }
        updateState { it.copy(serviceFeeSubmitting = true, serviceFeeMessage = null, errorMessage = null) }
        viewModelScope.launch {
            runCatching { tripRepository.submitRiderServiceFeePayment(amountNgwee, transactionReference) }
                .onSuccess {
                    updateState { it.copy(serviceFeeSubmitting = false, serviceFeeMessage = "Payment submitted for administrator verification. Your balance changes only after verification.") }
                    refreshServiceFeeAccount()
                }
                .onFailure { error -> updateState { it.copy(serviceFeeSubmitting = false, errorMessage = userSafeErrorMessage(error, "Payment submission could not be saved.")) } }
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
            catch (error: Exception) { updateState { it.copy(errorMessage = userSafeErrorMessage(error, "Cancellation could not be confirmed. Refresh trip details.")) } }
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
                updateState { it.copy(errorMessage = userSafeErrorMessage(error, "Could not save motorcycle details.")) }
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
                    updateState { it.copy(errorMessage = userSafeErrorMessage(error, "Could not submit document.")) }
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
