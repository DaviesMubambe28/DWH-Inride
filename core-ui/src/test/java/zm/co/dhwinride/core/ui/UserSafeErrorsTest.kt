package zm.co.dhwinride.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UserSafeErrorsTest {
    @Test
    fun `database details and authorization headers never reach the user`() {
        val raw = "column authorized_trip_details.accepted_fare_snapshot does not exist\n" +
            "URL: https://example.supabase.co/rest/v1/authorized_trip_details\n" +
            "Headers: [Authorization=Bearer secret]"

        assertEquals("Could not restore trips.", userSafeErrorMessage(IllegalStateException(raw), "Could not restore trips."))
    }

    @Test
    fun `short user facing validation remains useful`() {
        assertEquals(
            "Choose both a pickup and destination.",
            userSafeErrorMessage(IllegalArgumentException("Choose both a pickup and destination."), "Try again."),
        )
    }
}
