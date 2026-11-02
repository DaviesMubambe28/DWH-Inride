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
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import zm.co.dhwinride.core.data.TestTripRepository
import zm.co.dhwinride.core.data.TestRiderRepository
import zm.co.dhwinride.core.data.TripRepository
import zm.co.dhwinride.core.model.RidePaymentMethod
import zm.co.dhwinride.core.model.Trip
import zm.co.dhwinride.core.ui.rider.RiderNavScreen
import zm.co.dhwinride.core.ui.rider.RiderViewModel

@OptIn(ExperimentalCoroutinesApi::class)
class RiderRecoveryTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: ViewModelStore
    private lateinit var trips: TestTripRepository
    private lateinit var rider: TestRiderRepository
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        store = ViewModelStore(); trips = TestTripRepository(); rider = TestRiderRepository(trips)
    }
    @After fun cleanup() { store.clear(); Dispatchers.resetMain() }
    private fun model(repository: TripRepository = trips, state: SavedStateHandle = SavedStateHandle()) =
        RiderViewModel(rider, repository, savedStateHandle = state).also { store.put("rider", it) }
    private suspend fun offer(vm: RiderViewModel) {
        vm.toggleOnline(true); dispatcher.scheduler.advanceUntilIdle()
        trips.requestTrip("Synthetic destination", RidePaymentMethod.Cash)
        dispatcher.scheduler.advanceUntilIdle()
    }
    @Test fun `pickup PIN is not restored from or retained in saved state`() = runTest {
        val state = SavedStateHandle(mapOf("rider.pinInput" to "1234"))
        val vm = model(state = state)
        assertEquals("", vm.uiState.value.pinInput)
        vm.appendPinDigit("1")
        assertNull(state.get<String>("rider.pinInput"))
    }
    @Test fun `authoritative trip removal clears stale rider screen and PIN`() = runTest {
        val vm = model(); offer(vm); vm.acceptRequest(); dispatcher.scheduler.advanceUntilIdle()
        vm.navigateTo(RiderNavScreen.Pin); vm.appendPinDigit("1")
        trips.cancelTrip("test-trip"); dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.activeTrip)
        assertEquals("", vm.uiState.value.pinInput)
        assertEquals(RiderNavScreen.Home, vm.uiState.value.screen)
    }
    @Test fun `two accept taps before dispatch issue one request`() = runTest {
        var calls = 0
        val repo = object : TripRepository by trips {
            override suspend fun acceptTrip(tripId: String, riderId: String): Result<Trip> {
                calls++; return trips.acceptTrip(tripId, riderId)
            }
        }
        val vm = model(repo); offer(vm)
        vm.acceptRequest(); vm.acceptRequest(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(RiderNavScreen.Pickup, vm.uiState.value.screen)
        assertNull(vm.uiState.value.errorMessage)
    }
    @Test fun `automatic PIN submit and confirm tap issue one verification`() = runTest {
        var calls = 0
        val repo = object : TripRepository by trips {
            override suspend fun verifyPin(tripId: String, pin: String): Boolean {
                calls++; return trips.verifyPin(tripId, pin)
            }
        }
        val vm = model(repo); offer(vm); vm.acceptRequest(); dispatcher.scheduler.advanceUntilIdle()
        vm.confirmArrival(); dispatcher.scheduler.advanceUntilIdle()
        "2468".forEach { vm.appendPinDigit(it.toString()) }
        vm.verifyPin(); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(RiderNavScreen.ActiveTrip, vm.uiState.value.screen)
        assertNull(vm.uiState.value.errorMessage)
    }
    @Test fun `late PIN response cannot revive an authoritatively removed trip`() = runTest {
        val response = kotlinx.coroutines.CompletableDeferred<Boolean>()
        val repo = object : TripRepository by trips {
            override suspend fun verifyPin(tripId: String, pin: String): Boolean = response.await()
        }
        val vm = model(repo); offer(vm); vm.acceptRequest(); dispatcher.scheduler.advanceUntilIdle()
        vm.confirmArrival(); dispatcher.scheduler.advanceUntilIdle()
        "2468".forEach { vm.appendPinDigit(it.toString()) }
        dispatcher.scheduler.runCurrent()
        trips.cancelTrip("test-trip"); dispatcher.scheduler.runCurrent()
        response.complete(true); dispatcher.scheduler.advanceUntilIdle()
        assertNull(vm.uiState.value.activeTrip)
        assertEquals(RiderNavScreen.Home, vm.uiState.value.screen)
        assertEquals("", vm.uiState.value.pinInput)
        assertFalse(vm.uiState.value.verifyingPin)
    }
    @Test fun `malformed keypad input never sends a PIN request`() = runTest {
        var calls = 0
        val repo = object : TripRepository by trips {
            override suspend fun verifyPin(tripId: String, pin: String): Boolean { calls++; return false }
        }
        val vm = model(repo); offer(vm); vm.acceptRequest(); dispatcher.scheduler.advanceUntilIdle()
        vm.appendPinDigit("1234"); vm.appendPinDigit("A"); vm.verifyPin()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, calls)
        assertEquals("", vm.uiState.value.pinInput)
    }

    @Test fun `authoritative start advances a stale PIN screen`() = runTest {
        val vm = model(); offer(vm); vm.acceptRequest(); dispatcher.scheduler.advanceUntilIdle()
        vm.navigateTo(RiderNavScreen.Pin)
        trips.verifyPin("test-trip", "2468"); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(RiderNavScreen.ActiveTrip, vm.uiState.value.screen)
    }
    @Test fun `authoritative completion leaves stale active navigation`() = runTest {
        val vm = model(); offer(vm); vm.acceptRequest(); dispatcher.scheduler.advanceUntilIdle()
        trips.verifyPin("test-trip", "2468"); vm.navigateTo(RiderNavScreen.ActiveTrip)
        dispatcher.scheduler.advanceUntilIdle()
        trips.completeTrip("test-trip"); dispatcher.scheduler.advanceUntilIdle()
        assertEquals(RiderNavScreen.Complete, vm.uiState.value.screen)
    }
    @Test fun `restored PIN navigation follows authoritative in-progress trip`() = runTest {
        val vm = model(); offer(vm); vm.acceptRequest(); dispatcher.scheduler.advanceUntilIdle()
        trips.verifyPin("test-trip", "2468"); dispatcher.scheduler.advanceUntilIdle()
        val restored = model(state = SavedStateHandle(mapOf("rider.screen" to RiderNavScreen.Pin.name)))
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(RiderNavScreen.ActiveTrip, restored.uiState.value.screen)
    }

}
