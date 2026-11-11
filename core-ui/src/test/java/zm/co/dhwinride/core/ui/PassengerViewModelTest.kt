package zm.co.dhwinride.core.ui

import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import zm.co.dhwinride.core.data.TestAuthRepository
import zm.co.dhwinride.core.data.TestTripRepository
import zm.co.dhwinride.core.model.RidePaymentMethod
import zm.co.dhwinride.core.model.Place
import zm.co.dhwinride.core.model.PricingMode
import zm.co.dhwinride.core.ui.passenger.PassengerNavScreen
import zm.co.dhwinride.core.ui.passenger.PassengerViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class PassengerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: TestTripRepository
    private lateinit var viewModel: PassengerViewModel
    private lateinit var viewModelStore: ViewModelStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = TestTripRepository()
        viewModel = PassengerViewModel(repository, TestAuthRepository(), enableLiveLocationPolling = false)
        viewModelStore = ViewModelStore().also { it.put("passenger", viewModel) }
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `passenger booking respects the server operational pause`() = runTest {
        chooseBookingLocations()
        repository.pilotAvailable = false
        viewModel.refreshOnForeground()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.requestRide()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.pilotAvailable)
        assertEquals("Service temporarily paused or not configured. Please try again later.", viewModel.uiState.value.pilotServiceMessage)
        assertNull(viewModel.uiState.value.activeTrip)
        assertEquals(viewModel.uiState.value.pilotServiceMessage, viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `pilot rejects mobile money instead of silently changing payment`() = runTest {
        chooseBookingLocations()
        viewModel.setPaymentMethod(RidePaymentMethod.MobileMoney)
        viewModel.requestRide()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.activeTrip)
        assertTrue(viewModel.uiState.value.errorMessage.orEmpty().contains("cash only"))
    }

    @Test
    fun `selectDestination updates destination and navigates to Estimate`() = runTest {
        viewModel.selectDestination("Chongwe Hospital")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Chongwe Hospital", viewModel.uiState.value.destination)
        assertEquals(PassengerNavScreen.Estimate, viewModel.uiState.value.screen)
        assertTrue(viewModel.uiState.value.fareEstimate.fareAmount > 0)
    }

    @Test
    fun `location search is debounced and exposes repository results`() = runTest {
        val result = Place("Chongwe Market", "Great East Road", "1.2 km", -15.33, 28.68)
        repository.locationSearchResults = listOf(result)

        viewModel.searchPlaces("Chongwe")
        advanceTimeBy(349)
        assertNull(repository.lastLocationQuery)
        advanceTimeBy(1)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Chongwe", repository.lastLocationQuery)
        assertEquals(listOf(result), viewModel.uiState.value.nearbyPlaces)
    }

    @Test
    fun `choosing a search result clears search and advances booking`() = runTest {
        val pickup = Place("Market", "Chongwe Market", "", -15.3292, 28.6820)
        val destination = Place("Hospital", "Chongwe Hospital", "", -15.3300, 28.6900)

        viewModel.searchPlaces("Market")
        viewModel.choosePlace(pickup)
        viewModel.searchPlaces("Hospital")
        viewModel.choosePlace(destination)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.searchQuery)
        assertTrue(viewModel.uiState.value.nearbyPlaces.isEmpty())
        assertEquals(false, viewModel.uiState.value.isLoading)
        assertEquals(destination, viewModel.uiState.value.dropoff)
        assertEquals(PassengerNavScreen.Estimate, viewModel.uiState.value.screen)
    }

    @Test
    fun `repeated destination taps create only one fare quote while request is active`() = runTest {
        val pickup = Place("Market", "Chongwe Market", "", -15.3292, 28.6820)
        val destination = Place("Hospital", "Chongwe Hospital", "", -15.3275, 28.6813)
        val gate = CompletableDeferred<Unit>()
        repository.fareQuoteGate = gate

        viewModel.choosePlace(pickup)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.choosePlace(destination)
        viewModel.choosePlace(destination)
        testDispatcher.scheduler.runCurrent()

        assertEquals(1, repository.createFareQuoteCalls)
        assertTrue(viewModel.uiState.value.isCreatingFareQuote)

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.isCreatingFareQuote)
        assertEquals(PassengerNavScreen.Estimate, viewModel.uiState.value.screen)
    }

    @Test
    fun `requestRide creates active trip and navigates to Finding`() = runTest {
        chooseBookingLocations()
        viewModel.setPaymentMethod(RidePaymentMethod.Cash)
        viewModel.requestRide()
        testDispatcher.scheduler.advanceUntilIdle()

        val activeTrip = viewModel.uiState.value.activeTrip
        assertNotNull(activeTrip)
        assertTrue(viewModel.uiState.value.pilotAvailable)
        assertTrue(viewModel.uiState.value.pilotServiceMessage.contains("24/7"))
        assertEquals(RidePaymentMethod.Cash, viewModel.uiState.value.paymentMethod)
        assertEquals(PassengerNavScreen.Finding, viewModel.uiState.value.screen)
        assertTrue(activeTrip!!.pin.length == 4)
    }

    @Test
    fun `booking remains allowed when service is open but no riders are available`() = runTest {
        chooseBookingLocations()
        repository.ridersAvailable = false
        viewModel.refreshOnForeground()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pilotAvailable)
        assertEquals(false, viewModel.uiState.value.ridersAvailable)
        assertTrue(viewModel.uiState.value.pilotServiceMessage.contains("No riders are currently available"))
        viewModel.requestRide()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.activeTrip)
        assertEquals(PassengerNavScreen.Finding, viewModel.uiState.value.screen)
    }

    @Test
    fun `passenger app only advertises 24 7 after backend confirms the policy`() = runTest {
        repository.service24x7 = false
        viewModel.refreshOnForeground()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.pilotAvailable)
        assertEquals(false, viewModel.uiState.value.service24x7)
        assertTrue(viewModel.uiState.value.pilotServiceMessage.contains("Operating hours are controlled by operations"))
        assertTrue(!viewModel.uiState.value.pilotServiceMessage.contains("24/7"))
    }

    @Test
    fun `service pause clears stale fares and blocks booking`() = runTest {
        chooseBookingLocations()
        assertTrue(viewModel.uiState.value.fareEstimate.fareAmount > 0)

        repository.pilotAvailable = false
        viewModel.refreshOnForeground()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.fareEstimate.fareAmount)
        assertEquals(false, viewModel.uiState.value.pilotAvailable)
        viewModel.requestRide()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.activeTrip)
        assertEquals("Service temporarily paused or not configured. Please try again later.", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `dynamic pricing remains bookable through a trusted server quote`() = runTest {
        chooseBookingLocations()
        repository.pricingMode = PricingMode.DISTANCE_TIME
        viewModel.refreshOnForeground()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.pilotAvailable)
        assertEquals("test-quote", viewModel.uiState.value.fareEstimate.quoteId)

        viewModel.requestRide()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.activeTrip)
    }

    @Test
    fun `outside pilot area cannot create a trip`() = runTest {
        chooseBookingLocations()
        viewModel.choosePlace(Place("Outside destination", "Outside address", "", -15.2, 28.7))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.requestRide()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.activeTrip)
        assertTrue(viewModel.uiState.value.errorMessage.orEmpty().contains("within 4 km"))
    }

    @Test
    fun `booking timeout shows reconciliation guidance and does not navigate to success`() = runTest {
        chooseBookingLocations()
        repository.requestFailure = java.net.SocketTimeoutException("Read timed out")

        viewModel.requestRide()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(PassengerNavScreen.Estimate, viewModel.uiState.value.screen)
        assertTrue(viewModel.uiState.value.errorMessage.orEmpty().contains("could not confirm the result"))
        assertNull(viewModel.uiState.value.activeTrip)
    }

    @Test
    fun `booking pause race is reported without showing success`() = runTest {
        chooseBookingLocations()
        repository.requestFailure = IllegalStateException("New bookings are temporarily paused or unavailable.")

        viewModel.requestRide()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(PassengerNavScreen.Estimate, viewModel.uiState.value.screen)
        assertEquals("New bookings are temporarily paused. Please try again later.", viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.activeTrip)
    }

    @Test
    fun `cancelRequest clears active trip and returns to Home`() = runTest {
        chooseBookingLocations()
        viewModel.requestRide()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.cancelRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.activeTrip)
        assertEquals(PassengerNavScreen.Home, viewModel.uiState.value.screen)
    }

    @Test
    fun `verifyPin with valid pin navigates to ActiveTrip`() = runTest {
        chooseBookingLocations()
        viewModel.requestRide()
        testDispatcher.scheduler.advanceUntilIdle()

        repository.assignRider("test-trip")
        repository.confirmRiderArrival("test-trip")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.verifyPin()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(PassengerNavScreen.ActiveTrip, viewModel.uiState.value.screen)
    }

    @Test
    fun `completeTrip updates trip history and navigates to Receipt`() = runTest {
        chooseBookingLocations()
        viewModel.requestRide()
        testDispatcher.scheduler.advanceUntilIdle()

        repository.assignRider("test-trip")
        repository.confirmRiderArrival("test-trip")
        viewModel.verifyPin()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.completeTrip()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(PassengerNavScreen.Receipt, viewModel.uiState.value.screen)
        assertTrue(viewModel.uiState.value.tripHistory.isNotEmpty())
    }

    @Test
    fun `passenger form restores from saved state handle`() {
        val restored = PassengerViewModel(
            repository,
            TestAuthRepository(),
            savedStateHandle = androidx.lifecycle.SavedStateHandle(
                mapOf(
                    "passenger.screen" to "Estimate",
                    "passenger.destination" to "Drop-off address",
                    "passenger.searchQuery" to "search text",
                    "passenger.payment" to "Cash",
                    "passenger.fare" to 4500,
                    "passenger.distance" to 3.5,
                    "passenger.pickup.name" to "Pickup",
                    "passenger.pickup.detail" to "Pickup address",
                    "passenger.pickup.lat" to -15.3,
                    "passenger.pickup.lng" to 28.6,
                    "passenger.dropoff.name" to "Drop-off",
                    "passenger.dropoff.detail" to "Drop-off address",
                    "passenger.dropoff.lat" to -15.2,
                    "passenger.dropoff.lng" to 28.7,
                ),
            ),
            enableLiveLocationPolling = false,
        )
        assertEquals(PassengerNavScreen.Estimate, restored.uiState.value.screen)
        assertEquals("search text", restored.uiState.value.searchQuery)
        assertEquals("Pickup address", restored.uiState.value.pickup?.detail)
        assertEquals("Drop-off address", restored.uiState.value.dropoff?.detail)
        assertEquals(4500, restored.uiState.value.fareEstimate.fareAmount)
        assertEquals(false, restored.uiState.value.isLoading)
    }

    private suspend fun chooseBookingLocations() {
        viewModel.choosePlace(Place("Pickup", "Pickup address", "", -15.3292, 28.6820))
        viewModel.choosePlace(Place("Destination", "Destination address", "", -15.3300, 28.6900))
        testDispatcher.scheduler.advanceUntilIdle()
    }
}
