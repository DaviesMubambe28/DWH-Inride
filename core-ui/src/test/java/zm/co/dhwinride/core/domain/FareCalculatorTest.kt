package zm.co.dhwinride.core.domain

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import zm.co.dhwinride.core.model.Place

class FareCalculatorTest {
    private val origin = Place("Origin", "Origin", "", 0.0, 0.0)
    private fun config(base: String, perKm: String, minimum: String) = FareConfig(
        BigDecimal(base), BigDecimal(perKm), BigDecimal(minimum),
    )

    private fun calculator(base: String, perKm: String, minimum: String) = FareCalculator(config(base, perKm, minimum))

    @Test fun `short trip is charged with configured base and distance rate`() = kotlinx.coroutines.runBlocking {
        val km = FareCalculator.haversineKilometers(0.0, 0.0, 0.0, 0.01)
        assertEquals(534L, calculator("2", "3", "1").calculateNgwee(origin, origin.copy(latitude = 0.0, longitude = 0.01)))
        assertTrue(km in 1.1..1.12)
    }

    @Test fun `long trip uses kilometre units`() = kotlinx.coroutines.runBlocking {
        assertEquals(22339L, calculator("1", "2", "1").calculateNgwee(origin, origin.copy(longitude = 1.0)))
    }

    @Test fun `minimum fare is a floor`() = kotlinx.coroutines.runBlocking {
        assertEquals(1000L, calculator("1", "1", "10").calculateNgwee(origin, origin.copy(longitude = 0.01)))
    }

    @Test fun `zero distance charges the greater of base and minimum`() = kotlinx.coroutines.runBlocking {
        assertEquals(1000L, calculator("10", "5", "8").calculateNgwee(origin, origin))
    }

    @Test fun `identical coordinates have zero distance`() {
        assertEquals(0.0, FareCalculator.haversineKilometers(-15.0, 28.0, -15.0, 28.0), 0.0)
    }

    @Test fun `negative coordinates are valid`() {
        assertTrue(FareCalculator.haversineKilometers(-15.0, -28.0, -15.01, -28.02) > 0.0)
    }

    @Test fun `geographic boundary coordinates remain finite`() {
        assertTrue(FareCalculator.haversineKilometers(90.0, 180.0, 89.999, -180.0).isFinite())
    }

    @Test fun `latitude outside range is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { FareCalculator.haversineKilometers(90.01, 0.0, 0.0, 0.0) }
    }

    @Test fun `longitude outside range is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { FareCalculator.haversineKilometers(0.0, -180.01, 0.0, 0.0) }
    }

    @Test fun `non finite coordinates are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { FareCalculator.haversineKilometers(Double.NaN, 0.0, 0.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { FareCalculator.haversineKilometers(0.0, 0.0, 0.0, Double.POSITIVE_INFINITY) }
    }

    @Test fun `negative fare configuration is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { config("-0.01", "1", "1") }
        assertThrows(IllegalArgumentException::class.java) { config("1", "-0.01", "1") }
    }

    @Test fun `monetary rounding uses half up to ngwee`() = kotlinx.coroutines.runBlocking {
        val calculator = FareCalculator(config("1", "1", "0"), RouteDistanceProvider { _, _ -> 0.005 })
        assertEquals(101L, calculator.calculateNgwee(origin, origin))
    }

    @Test fun `straight line distance is kilometres using earth radius`() {
        assertEquals(111.195, FareCalculator.haversineKilometers(0.0, 0.0, 0.0, 1.0), 0.01)
    }

    @Test fun `formatter renders ZMW with two decimal places`() {
        assertEquals("12.34", formatZmw(1234))
        assertEquals("0.05", formatZmw(5))
    }
}
