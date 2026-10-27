package zm.co.dhwinride.core.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.flow.first
import zm.co.dhwinride.core.data.TestRiderRepository
import zm.co.dhwinride.core.data.TestTripRepository
import zm.co.dhwinride.core.model.RidePaymentMethod
import zm.co.dhwinride.core.ui.rider.RiderNavScreen
import zm.co.dhwinride.core.ui.rider.RiderViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class RiderViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var tripRepo: TestTripRepository
    private lateinit var riderRepo: TestRiderRepository
    private lateinit var viewModel: RiderViewModel
    private lateinit var viewModelStore: ViewModelStore

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tripRepo = TestTripRepository()
        riderRepo = TestRiderRepository(tripRepo)
        viewModel = RiderViewModel(riderRepo, tripRepo)
        viewModelStore = ViewModelStore().also { it.put("rider", viewModel) }
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `toggleOnline updates online state`() = runTest {
        viewModel.toggleOnline(true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOnline)
    }

    @Test
    fun `online rider receives an available request`() = runTest {
        viewModel.toggleOnline(true)
        testDispatcher.scheduler.advanceUntilIdle()
        tripRepo.requestTrip("Test destination", RidePaymentMethod.Cash)
        testDispatcher.scheduler.advanceUntilIdle()
        tripRepo.publishAsAvailableOffer()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOnline)
        assertNotNull(viewModel.uiState.value.selectedRequest)
        assertEquals(null, viewModel.uiState.value.activeTrip)
        assertEquals(RiderNavScreen.Request, viewModel.uiState.value.screen)
    }

    @Test
    fun `acceptRequest navigates to Pickup`() = runTest {
        viewModel.toggleOnline(true)
        testDispatcher.scheduler.advanceUntilIdle()
        tripRepo.requestTrip("Test destination", RidePaymentMethod.Cash)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.acceptRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(RiderNavScreen.Pickup, viewModel.uiState.value.screen)
        assertNotNull(viewModel.uiState.value.activeTrip)
        assertEquals(null, viewModel.uiState.value.selectedRequest)
    }

    @Test
    fun `restored rider with an assigned trip returns to pickup navigation`() = runTest {
        viewModel.toggleOnline(true)
        testDispatcher.scheduler.advanceUntilIdle()
        tripRepo.requestTrip("Test destination", RidePaymentMethod.Cash)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.acceptRequest()
        testDispatcher.scheduler.advanceUntilIdle()

        val restoredViewModel = RiderViewModel(
            riderRepository = riderRepo,
            tripRepository = tripRepo,
            savedStateHandle = SavedStateHandle(mapOf("rider.screen" to RiderNavScreen.Home.name)),
        )
        viewModelStore.put("restored", restoredViewModel)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(RiderNavScreen.Pickup, restoredViewModel.uiState.value.screen)
        assertNotNull(restoredViewModel.uiState.value.activeTrip)
    }

    @Test
    fun `failed server completion does not navigate to success screen`() = runTest {
        viewModel.toggleOnline(true)
        testDispatcher.scheduler.advanceUntilIdle()
        tripRepo.requestTrip("Test destination", RidePaymentMethod.Cash)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.acceptRequest()
        testDispatcher.scheduler.advanceUntilIdle()
        tripRepo.failNextComplete = true

        viewModel.completeTrip()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(RiderNavScreen.Pickup, viewModel.uiState.value.screen)
        assertEquals("Server refused trip completion", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `second accept attempt after a request is claimed is rejected`() = runTest {
        riderRepo.setOnline(true)
        tripRepo.requestTrip("Test destination", RidePaymentMethod.Cash)
        val rider = riderRepo.getRiderProfile().first()
        tripRepo.assignRider("test-trip", rider)

        val secondAttempt = runCatching { tripRepo.assignRider("test-trip", rider) }
        assertTrue(secondAttempt.isFailure)
        assertEquals("This trip has already been accepted by another rider.", secondAttempt.exceptionOrNull()?.message)
    }

    @Test
    fun `appendPinDigit accumulates input and verifies pin on 4 digits`() = runTest {
        viewModel.toggleOnline(true)
        testDispatcher.scheduler.advanceUntilIdle()
        tripRepo.requestTrip("Test destination", RidePaymentMethod.Cash)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.acceptRequest()
        viewModel.confirmArrival()
        testDispatcher.scheduler.advanceUntilIdle()

        val pin = requireNotNull(viewModel.uiState.value.activeTrip).pin
        pin.forEach { digit ->
            viewModel.appendPinDigit(digit.toString())
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(RiderNavScreen.ActiveTrip, viewModel.uiState.value.screen)
    }

    @Test
    fun `finishCashConfirmation completes trip and updates earnings`() = runTest {
        viewModel.toggleOnline(true)
        testDispatcher.scheduler.advanceUntilIdle()
        tripRepo.requestTrip("Test destination", RidePaymentMethod.Cash)
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.acceptRequest()
        viewModel.confirmArrival()
        val pin = requireNotNull(viewModel.uiState.value.activeTrip).pin
        pin.forEach { digit -> viewModel.appendPinDigit(digit.toString()) }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.completeTrip()
        viewModel.togglePaymentReceived()
        viewModel.finishCashConfirmation()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.paymentReceived)
        assertEquals(RiderNavScreen.Earnings, viewModel.uiState.value.screen)
        assertTrue(viewModel.uiState.value.earnings.todayAmount > 0)
    }
}
