package zm.co.dhwinride.core.model

enum class TripStatus {
    IDLE,
    DESTINATION_SELECTED,
    ESTIMATE_READY,
    FINDING_RIDER,
    RIDER_ASSIGNED,
    RIDER_ARRIVED,
    PIN_VERIFIED,
    TRIP_IN_PROGRESS,
    TRIP_COMPLETED,
    RATING_SUBMITTED,
}

enum class RidePaymentMethod(val title: String, val description: String) {
    Cash("Cash", "Pay the rider in cash after the trip"),
    MobileMoney("Mobile money", "Pay the rider outside DHW-inRide after arrival"),
}

data class Place(
    val name: String,
    val detail: String,
    val distance: String,
    val latitude: Double = Double.NaN,
    val longitude: Double = Double.NaN,
)

data class FareEstimate(
    /** Amounts are integer ngwee (100 ngwee = 1 ZMW). */
    val fareAmount: Int = 0,
    val driverFare: Int = 0,
    val serviceFee: Int = 0,
    val distanceKm: Double = 0.0,
    val durationMinutes: Int = 0,
)

data class RiderProfile(
    val id: String = "",
    val name: String = "",
    val rating: Double = 0.0,
    val completedRides: Int = 0,
    val motorcycleModel: String = "",
    val plateNumber: String = "",
    val motorcycleMake: String = "",
    val motorcycleYear: Int? = null,
    val motorcycleColor: String = "",
    val phone: String = "",
    val isVerified: Boolean = false,
)

data class PassengerProfile(
    val id: String = "",
    val name: String = "",
    val phone: String = "",
)

data class Trip(
    val id: String = "",
    val pickupLocation: String = "Current location",
    val destination: String = "",
    val distanceKm: Double = 0.0,
    val fare: FareEstimate = FareEstimate(),
    val paymentMethod: RidePaymentMethod = RidePaymentMethod.Cash,
    val passenger: PassengerProfile = PassengerProfile(),
    val rider: RiderProfile? = null,
    val status: TripStatus = TripStatus.IDLE,
    val pin: String = "",
    val passengerMarkedPaid: Boolean = false,
    val riderConfirmedPayment: Boolean = false,
    val pickupLatitude: Double = Double.NaN,
    val pickupLongitude: Double = Double.NaN,
    val dropoffLatitude: Double = Double.NaN,
    val dropoffLongitude: Double = Double.NaN,
)

/** Latest server-recorded rider position for an authorized active trip. */
data class TripLocation(
    val tripId: String,
    val riderId: String,
    val latitude: Double,
    val longitude: Double,
    val recordedAt: String,
)

data class RiderEarnings(
    /** Monetary amounts are integer ngwee (100 ngwee = 1 ZMW). */
    val todayAmount: Int = 0,
    val todayTripsCount: Int = 0,
    val todayKm: Double = 0.0,
    val weekAmount: Int = 0,
    val monthAmount: Int = 0,
)


/** Server-reported pilot service availability; no client clock authority. */
data class PilotServiceStatus(val available: Boolean, val message: String)
