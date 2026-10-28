package zm.co.dhwinride.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zm.co.dhwinride.core.model.Trip
import zm.co.dhwinride.core.model.TripLocation
import kotlin.math.log2

data class MapMarker(val label: String, val latitude: Double, val longitude: Double)

@Composable
fun TripMap(trip: Trip?, modifier: Modifier = Modifier, riderLocation: TripLocation? = null) {
    val markers = buildList {
        if (trip != null) {
            add(MapMarker("P", trip.pickupLatitude, trip.pickupLongitude))
            add(MapMarker("D", trip.dropoffLatitude, trip.dropoffLongitude))
        }
        if (riderLocation != null) add(MapMarker("R", riderLocation.latitude, riderLocation.longitude))
    }.filter { it.latitude.isFinite() && it.longitude.isFinite() && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }
    val latitude = markers.map { it.latitude }.average().takeIf { it.isFinite() } ?: -15.3292
    val longitude = markers.map { it.longitude }.average().takeIf { it.isFinite() } ?: 28.6821
    val span = if (markers.isEmpty()) 0.05 else maxOf(
        markers.maxOf { it.latitude } - markers.minOf { it.latitude },
        markers.maxOf { it.longitude } - markers.minOf { it.longitude },
    )
    val zoom = (log2(180.0 / span.coerceAtLeast(0.005)) - 1).coerceIn(3.0, 15.0)
    Column(modifier) {
        LiveMap(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = if (markers.isEmpty()) "Chongwe area · locations pending" else "Pickup · destination · rider",
            latitude = latitude, longitude = longitude, zoom = zoom, markers = markers,
        )
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("P Pickup  ·  D Destination  ·  R Rider", style = MaterialTheme.typography.labelMedium, color = DhwNavy)
                Text("Location overview, not turn-by-turn directions. Rider positions may be delayed.", style = MaterialTheme.typography.bodySmall, color = DhwMuted)
            }
        }
    }
}

@Composable
fun TripStage(title: String, nextAction: String) {
    Surface(color = DhwNavy) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
            Text(nextAction, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
