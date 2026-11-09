package zm.co.dhwinride.core.domain

/** Owner-approved pilot policy; the server independently enforces these rules. */
object PilotPolicy {
    const val FARE_NGWEE = 2000
    const val SERVICE_FEE_NGWEE = 300
    const val RIDER_NET_NGWEE = FARE_NGWEE - SERVICE_FEE_NGWEE
    const val CENTER_LATITUDE = -15.3292
    const val CENTER_LONGITUDE = 28.6820
    const val RADIUS_KM = 4.0
    const val SUPPORT_PRIMARY = "+260770001203"
    const val SUPPORT_SECONDARY = "+260975461121"
    const val SUPPORT_EMAIL = "gershmukuka1@gmail.com"

    fun contains(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
            FareCalculator.haversineKilometers(CENTER_LATITUDE, CENTER_LONGITUDE, latitude, longitude) <= RADIUS_KM
}
