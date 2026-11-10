package zm.co.dhwinride.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicFareSchemaCompatibilityTest {
    @Test
    fun `missing new fare columns activate legacy query compatibility`() {
        assertTrue(
            IllegalStateException("column authorized_trip_details.accepted_fare_snapshot does not exist")
                .isMissingDynamicFareSchema(),
        )
        assertTrue(
            IllegalStateException("column rider_available_trips.route_duration_seconds does not exist")
                .isMissingDynamicFareSchema(),
        )
    }

    @Test
    fun `authorization and connectivity failures never activate schema fallback`() {
        assertFalse(IllegalStateException("permission denied for view authorized_trip_details").isMissingDynamicFareSchema())
        assertFalse(IllegalStateException("Unable to resolve host").isMissingDynamicFareSchema())
    }
}
