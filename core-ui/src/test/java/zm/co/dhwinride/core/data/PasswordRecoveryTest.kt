package zm.co.dhwinride.core.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PasswordRecoveryTest {
    private class Gateway : RecoveryGateway {
        var requests = 0
        var verifications = 0
        var updates = 0
        var clears = 0
        var rejectCode = false
        var rejectUpdate = false
        var waitForRequest: CompletableDeferred<Unit>? = null
        override suspend fun request(email: String) { requests++; waitForRequest?.await() }
        override suspend fun verify(email: String, code: String) {
            verifications++
            if (rejectCode) error("PRIVATE_PROVIDER_BODY")
        }
        override suspend fun updatePassword(password: String) { updates++; if (rejectUpdate) error("PRIVATE_PROVIDER_BODY") }
        override suspend fun clear() { clears++ }
    }
    @Test fun `password update requires a verified recovery code`() = runTest {
        val gateway = Gateway(); val recovery = PasswordRecovery(gateway)
        recovery.changePassword("synthetic-password", "synthetic-password")
        assertEquals(0, gateway.updates)
        assertEquals(RecoveryStep.Request, recovery.state.value.step)
    }
    @Test fun `bad code cannot authorize a password update or leak provider text`() = runTest {
        val gateway = Gateway().apply { rejectCode = true }; val recovery = PasswordRecovery(gateway)
        recovery.request("synthetic@example.invalid"); recovery.verify("123456")
        recovery.changePassword("synthetic-password", "synthetic-password")
        assertEquals(0, gateway.updates)
        assertEquals(RecoveryStep.Code, recovery.state.value.step)
        assertFalse(recovery.state.value.toString().contains("PRIVATE_PROVIDER_BODY"))
    }
    @Test fun `verified recovery validates confirmation before a single update and clears the session`() = runTest {
        val gateway = Gateway(); val recovery = PasswordRecovery(gateway)
        recovery.request("synthetic@example.invalid"); recovery.verify("123456")
        recovery.changePassword("short", "short")
        recovery.changePassword("synthetic-password", "different-password")
        assertEquals(0, gateway.updates)
        recovery.changePassword("synthetic-password", "synthetic-password")
        assertEquals(RecoveryStep.Done, recovery.state.value.step)
        assertEquals(1, gateway.updates)
        recovery.changePassword("replayed-password", "replayed-password")
        assertEquals(1, gateway.updates)
        assertTrue(gateway.clears >= 2)
    }
    @Test fun `new recovery request invalidates previous verification`() = runTest {
        val gateway = Gateway(); val recovery = PasswordRecovery(gateway)
        recovery.request("first@example.invalid"); recovery.verify("123456")
        recovery.request("second@example.invalid")
        recovery.changePassword("synthetic-password", "synthetic-password")
        assertEquals(RecoveryStep.Code, recovery.state.value.step)
        assertEquals(0, gateway.updates)
    }
    @Test fun `cancel removes recovery authorization and private state`() = runTest {
        val gateway = Gateway(); val recovery = PasswordRecovery(gateway)
        recovery.request("synthetic@example.invalid"); recovery.verify("123456"); recovery.cancel()
        recovery.changePassword("synthetic-password", "synthetic-password")
        assertEquals(0, gateway.updates)
        assertEquals(RecoveryStep.Request, recovery.state.value.step)
        assertFalse(recovery.state.value.toString().contains("synthetic@example.invalid"))
    }
    @Test fun `rapid duplicate request is ignored while the first request is pending`() = runTest {
        val gateway = Gateway().apply { waitForRequest = CompletableDeferred() }; val recovery = PasswordRecovery(gateway)
        val first = launch { recovery.request("synthetic@example.invalid") }; runCurrent()
        recovery.request("synthetic@example.invalid")
        assertEquals(1, gateway.requests)
        gateway.waitForRequest!!.complete(Unit); first.join()
        assertFalse(recovery.state.value.busy)
    }
    @Test fun `cancellation releases busy state and clears authorization`() = runTest {
        val gateway = Gateway().apply { waitForRequest = CompletableDeferred() }; val recovery = PasswordRecovery(gateway)
        val first = launch { recovery.request("synthetic@example.invalid") }; runCurrent(); first.cancel(); first.join()
        assertFalse(recovery.state.value.busy)
        assertEquals(RecoveryStep.Request, recovery.state.value.step)
        assertTrue(gateway.clears > 0)
    }
    @Test fun `unknown password write outcome is not reported as success`() = runTest {
        val gateway = Gateway().apply { rejectUpdate = true }; val recovery = PasswordRecovery(gateway)
        recovery.request("synthetic@example.invalid"); recovery.verify("123456")
        recovery.changePassword("synthetic-password", "synthetic-password")
        assertEquals(RecoveryStep.Request, recovery.state.value.step)
        assertFalse(recovery.state.value.toString().contains("PRIVATE_PROVIDER_BODY"))
        assertNotNull(recovery.state.value.error)
    }
    @Test fun `invalid email and malformed code do not reach the provider`() = runTest {
        val gateway = Gateway(); val recovery = PasswordRecovery(gateway)
        recovery.request("invalid"); assertEquals(0, gateway.requests)
        recovery.request("synthetic@example.invalid"); recovery.verify("not-a-code")
        assertEquals(0, gateway.verifications)
    }
}
