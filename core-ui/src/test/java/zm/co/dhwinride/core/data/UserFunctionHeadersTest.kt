package zm.co.dhwinride.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UserFunctionHeadersTest {
    @Test
    fun `user functions send only the bearer credential accepted by the server wrapper`() {
        val headers = userFunctionHeaders("user-jwt")

        assertEquals("application/json", headers["Content-Type"])
        assertEquals("Bearer user-jwt", headers["Authorization"])
        assertFalse(headers.keys.any { it.equals("apikey", ignoreCase = true) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank access token is rejected before opening a user function request`() {
        userFunctionHeaders("  ")
    }
}
