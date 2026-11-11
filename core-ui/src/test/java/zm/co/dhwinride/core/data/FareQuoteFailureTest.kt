package zm.co.dhwinride.core.data

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Test

class FareQuoteFailureTest {
    @Test
    fun `structured backend failures have distinct passenger messages`() {
        assertEquals(
            "Choose pickup and destination points inside Chongwe District.",
            fareQuoteFailureMessage(422, "INVALID_LOCATION"),
        )
        assertEquals(
            "No usable road route was found between these locations.",
            fareQuoteFailureMessage(422, "ROUTE_NOT_FOUND"),
        )
        assertEquals(
            "The fare could not be saved. Try again shortly.",
            fareQuoteFailureMessage(500, "FARE_QUOTE_CREATION_FAILED"),
        )
        assertEquals(
            "Your sign-in expired. Sign in again before requesting a fare.",
            fareQuoteFailureMessage(401, null),
        )
    }

    @Test
    fun `transport and response failures are not mislabeled as one generic error`() {
        assertEquals(
            "The fare request timed out. Check your connection and try again.",
            fareQuoteTransportMessage(SocketTimeoutException()),
        )
        assertEquals(
            "No internet connection. Reconnect and try again.",
            fareQuoteTransportMessage(UnknownHostException()),
        )
        assertEquals(
            "The fare service returned an invalid response. Try again.",
            fareQuoteTransportMessage(SerializationException("invalid payload")),
        )
    }
}
