package zm.co.dhwinride.core.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import zm.co.dhwinride.core.data.AuthRepository
import zm.co.dhwinride.core.model.PassengerProfile

class SessionRestorationTest {
    @Test
    fun `passenger launch restores a matching persisted session`() = runTest {
        val repository = RestoringAuthRepository(Result.success(true))

        assertTrue(restoreLaunchSession(repository, AppUserRole.Passenger).getOrThrow())
        assertEquals("passenger", repository.requestedRole)
    }

    @Test
    fun `rider launch stays signed out when no persisted session exists`() = runTest {
        val repository = RestoringAuthRepository(Result.success(false))

        assertFalse(restoreLaunchSession(repository, AppUserRole.Rider).getOrThrow())
        assertEquals("rider", repository.requestedRole)
    }

    @Test
    fun `session restoration failure remains explicit`() = runTest {
        val repository = RestoringAuthRepository(Result.failure(IllegalStateException("offline")))

        assertTrue(restoreLaunchSession(repository, AppUserRole.Passenger).isFailure)
    }

    @Test
    fun `sign out clears the authenticated app session`() = runTest {
        val repository = RestoringAuthRepository(Result.success(true))

        assertTrue(signOutAppSession(repository).isSuccess)
        assertEquals(1, repository.signOutCalls)
    }

    @Test
    fun `sign out failure remains explicit`() = runTest {
        val repository = RestoringAuthRepository(
            restoration = Result.success(true),
            signOutFailure = IllegalStateException("offline"),
        )

        assertTrue(signOutAppSession(repository).isFailure)
        assertEquals(1, repository.signOutCalls)
    }

    private class RestoringAuthRepository(
        private val restoration: Result<Boolean>,
        private val signOutFailure: Throwable? = null,
    ) : AuthRepository {
        private val profile = MutableStateFlow(PassengerProfile())
        var requestedRole: String? = null
        var signOutCalls = 0

        override fun getPassengerProfile(): Flow<PassengerProfile> = profile
        override suspend fun restoreSession(expectedRole: String): Result<Boolean> {
            requestedRole = expectedRole
            return restoration
        }
        override suspend fun signUp(email: String, pass: String, fullName: String, phone: String, role: String) = Result.success(Unit)
        override suspend fun signIn(email: String, pass: String, expectedRole: String) = Result.success(Unit)
        override suspend fun signOut() {
            signOutCalls += 1
            signOutFailure?.let { throw it }
        }
        override suspend fun verifyEmail(email: String) = true
        override suspend fun updateProfile(fullName: String, phone: String) = Unit
    }
}
