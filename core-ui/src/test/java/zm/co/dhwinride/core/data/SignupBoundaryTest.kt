package zm.co.dhwinride.core.data

import android.content.Context
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class SignupBoundaryTest {
    @Test
    fun `rider signup is rejected before any Auth account is created`() = runTest {
        val context = mockk<Context>()
        every { context.applicationContext } returns context
        val client = mockk<SupabaseClient>()
        val repository = SupabaseAuthRepository(client, context, "test-app")
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        try {
            val result = repository.signUp("synthetic@example.invalid", "unused-test-password", "Synthetic", "+260979111111", "rider")
            assertTrue(result.isFailure)
            verify { client wasNot Called }
        } finally {
            unmockkStatic(Log::class)
        }
    }
}
