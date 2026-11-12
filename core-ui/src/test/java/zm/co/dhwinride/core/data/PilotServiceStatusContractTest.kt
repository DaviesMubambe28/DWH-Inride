package zm.co.dhwinride.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PilotServiceStatusContractTest {
    @Test
    fun `hosted pilot status decimal minimum fare decodes to ngwee`() {
        val status = decodePilotServiceStatusResponse(
            """
            {
              "available": true,
              "message": "Service is available",
              "riders_available": true,
              "service_24_7": true,
              "pricing_mode": "DISTANCE_TIME",
              "minimum_fare_zmw": 21.0
            }
            """.trimIndent(),
        )

        assertTrue(status.available)
        assertEquals(2_100, status.minimumFareNgwee)
    }

    @Test
    fun `missing pilot minimum fare uses the safe twenty kwacha default`() {
        val status = decodePilotServiceStatusResponse(
            """
            {
              "available": true,
              "message": "Service is available"
            }
            """.trimIndent(),
        )

        assertEquals(2_000, status.minimumFareNgwee)
    }
}
