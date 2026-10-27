package zm.co.dhwinride.core.domain

import java.math.BigDecimal
import java.math.RoundingMode
import zm.co.dhwinride.core.model.Place

/** Monetary rates are in ZMW; the calculated result is an integer number of ngwee. */
data class FareConfig(
    val baseFareZmw: BigDecimal,
    val perKilometerZmw: BigDecimal,
    val minimumFareZmw: BigDecimal,
    val currency: String = "ZMW",
    val roundingMode: RoundingMode = RoundingMode.HALF_UP,
) {
    init {
        require(currency == "ZMW") { "Only ZMW is supported by this fare service." }
        require(baseFareZmw >= BigDecimal.ZERO && perKilometerZmw >= BigDecimal.ZERO && minimumFareZmw >= BigDecimal.ZERO) {
            "Fare rates must be non-negative."
        }
    }
}

fun interface RouteDistanceProvider {
    /** Returns route distance in kilometres. */
    suspend fun distanceKilometers(from: Place, to: Place): Double
}

class HaversineDistanceProvider : RouteDistanceProvider {
    override suspend fun distanceKilometers(from: Place, to: Place): Double =
        FareCalculator.haversineKilometers(from.latitude, from.longitude, to.latitude, to.longitude)
}

class FareCalculator(
    private val config: FareConfig,
    private val distanceProvider: RouteDistanceProvider = HaversineDistanceProvider(),
) {
    /** fare = max(minimum, base + km * rate), rounded to 2 ZMW decimals (ngwee). */
    suspend fun calculateNgwee(from: Place, to: Place): Long {
        validateCoordinates(from.latitude, from.longitude)
        validateCoordinates(to.latitude, to.longitude)
        val distanceKm = distanceProvider.distanceKilometers(from, to)
        require(distanceKm.isFinite() && distanceKm >= 0.0) { "Distance must be finite and non-negative." }
        val distance = BigDecimal.valueOf(distanceKm)
        val fareZmw = config.baseFareZmw.add(config.perKilometerZmw.multiply(distance))
            .max(config.minimumFareZmw)
            .setScale(2, config.roundingMode)
        return fareZmw.movePointRight(2).longValueExact()
    }

    companion object {
        fun validateCoordinates(latitude: Double, longitude: Double) {
            require(latitude.isFinite() && latitude in -90.0..90.0) { "Latitude must be between -90 and 90 degrees." }
            require(longitude.isFinite() && longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees." }
        }

        /** Returns great-circle distance in kilometres; road distance may be substantially longer. */
        fun haversineKilometers(latitudeA: Double, longitudeA: Double, latitudeB: Double, longitudeB: Double): Double {
            validateCoordinates(latitudeA, longitudeA)
            validateCoordinates(latitudeB, longitudeB)
            val earthRadiusKm = 6371.0088
            val latitudeDelta = Math.toRadians(latitudeB - latitudeA)
            val longitudeDelta = Math.toRadians(longitudeB - longitudeA)
            val a = kotlin.math.sin(latitudeDelta / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(latitudeA)) * kotlin.math.cos(Math.toRadians(latitudeB)) *
                kotlin.math.sin(longitudeDelta / 2).let { it * it }
            return 2 * earthRadiusKm * kotlin.math.asin(kotlin.math.sqrt(a.coerceIn(0.0, 1.0)))
        }
    }
}

/** Format integer ngwee as ZMW with exactly two decimals. */
fun formatZmw(ngwee: Int): String = BigDecimal.valueOf(ngwee.toLong(), 2).setScale(2).toPlainString()
