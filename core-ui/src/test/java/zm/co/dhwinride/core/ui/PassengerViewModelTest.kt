package zm.co.dhwinride.core.ui

import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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
    fun `closed pilot cannot create a new trip`() = runTest {
        chooseBookingLocations()
        repository.pilotAvailable = false
        viewModel.refreshOnForeground()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.requestRide()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.activeTrip)
        assertEquals("Service currently unavailable", viewModel.uiState.value.errorMessage)
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
    fun `requestRide creates active trip and navigates to Finding`() = runTest {
        chooseBookingLocations()
        viewModel.setPaymentMethod(RidePaymentMethod.Cash)
        viewModel.requestRide()
        testDispatcher.scheduler.advanceUntilIdle()

        val activeTrip = viewModel.uiState.value.activeTrip
        assertNotNull(activeTrip)
        assertEquals(RidePaymentMethod.Cash, viewModel.uiState.value.paymentMethod)
        assertEquals(PassengerNavScreen.Finding, viewModel.uiState.value.screen)
        assertTrue(activeTrip!!.pin.length == 4)
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
        viewModel.choosePlace(Place("Pickup", "Pickup address", "", -15.3, 28.6))
        viewModel.choosePlace(Place("Destination", "Destination address", "", -15.2, 28.7))
        testDispatcher.scheduler.advanceUntilIdle()
    }
}
