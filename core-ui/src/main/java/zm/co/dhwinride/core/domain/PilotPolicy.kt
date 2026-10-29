package zm.co.dhwinride.core.domain

/** Owner-approved pilot policy; the server independently enforces these rules. */
object PilotPolicy {
    const val FARE_NGWEE = 2000
    const val CENTER_LATITUDE = -15.3292
    const val CENTER_LONGITUDE = 28.6820
    const val RADIUS_KM = 4.0
    const val SUPPORT_PRIMARY = "+260971793185"
    const val SUPPORT_SECONDARY = "+260761741656"
    const val SUPPORT_EMAIL = "garumitech@gmail.com"

    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            FareCalculator.haversineKilometers(CENTER_LATITUDE, CENTER_LONGITUDE, latitude, longitude) <= RADIUS_KM
}
