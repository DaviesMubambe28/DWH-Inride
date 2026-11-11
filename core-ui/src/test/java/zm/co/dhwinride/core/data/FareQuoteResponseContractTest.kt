package zm.co.dhwinride.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FareQuoteResponseContractTest {
    @Test
    fun `deployed fare quote response decodes with the complete pricing contract`() {
        val quote = decodeOfficialFareQuoteResponse(
            """
            {
              "quote": {
                "quote_id": "76000000-0000-0000-0000-000000000001",
                "fare_amount": 30.50,
                "driver_fare": 25.92,
                "service_fee": 4.58,
                "route_distance_meters": 1500,
                "route_duration_seconds": 300,
                "pricing_mode": "DISTANCE_TIME",
                "pricing_version": 2,
                "expires_at": "2026-11-12T12:30:00Z",
                "fare_breakdown": {
                  "base_fare_zmw": 21.00,
                  "distance_charge_zmw": 7.50,
                  "time_charge_zmw": 2.00,
                  "waiting_charge_zmw": 0,
                  "surge_charge_zmw": 0,
                  "night_applied": false,
                  "busy_applied": false,
                  "rain_applied": false,
                  "free_waiting_minutes": 3,
                  "rounding_policy": "distance and journey time round to nearest ngwee"
                }
              }
            }
            """.trimIndent(),
        )

        assertEquals("DISTANCE_TIME", quote.pricingMode)
        assertEquals(3, quote.breakdown.freeWaitingMinutes)
        assertTrue(quote.breakdown.roundingPolicy.isNotBlank())
        assertEquals(30.50, quote.fareAmount, 0.0)
    }
}
