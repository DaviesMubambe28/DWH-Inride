package zm.co.dhwinride.core.ui.rider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zm.co.dhwinride.core.domain.formatZmw
import zm.co.dhwinride.core.model.Trip
import zm.co.dhwinride.core.ui.DhwCard
import zm.co.dhwinride.core.ui.DhwMuted
import zm.co.dhwinride.core.ui.DhwNavy
import zm.co.dhwinride.core.ui.DhwOrange
import zm.co.dhwinride.core.ui.InfoRow
import zm.co.dhwinride.core.ui.ListDivider
import zm.co.dhwinride.core.ui.LoadingState
import zm.co.dhwinride.core.ui.PrimaryButton
import zm.co.dhwinride.core.ui.SecondaryButton
import zm.co.dhwinride.core.ui.StatusPill

@Composable
internal fun IncomingRequest(
    trip: Trip?,
    requests: List<Trip>,
    isLoading: Boolean,
    accepting: Boolean,
    onSelect: (String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val passenger = trip?.passenger
    val fare = trip?.fare
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Surface(color = DhwOrange) {
            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("NEW RIDE REQUEST", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("Incoming request", color = Color.White.copy(alpha = .85f))
                }
                StatusPill("Review request", Color.White)
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                requests.isEmpty() && isLoading -> LoadingState("Loading available ride requests…")
                requests.isEmpty() -> Text("No ride requests right now. Keep the app online to receive new requests.", color = DhwMuted)
                requests.size > 1 -> requests.forEach { request ->
                    val selected = request.id == trip?.id
                    DhwCard(
                        modifier = Modifier.selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(request.id) },
                        ),
                        containerColor = if (selected) DhwOrange.copy(alpha = .10f) else Color.White,
                    ) {
                        Text(if (selected) "SELECTED REQUEST" else "TAP TO REVIEW", color = if (selected) DhwOrange else DhwMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("${request.pickupLocation} → ${request.destination}", fontWeight = FontWeight.Bold, color = DhwNavy)
                        Text("K${formatZmw(request.fare.fareAmount)} • ${request.fare.distanceKm} km", color = DhwMuted)
                    }
                }
            }
            if (trip == null) return@Column
            DhwCard {
                Text("Passenger request", color = DhwNavy, fontWeight = FontWeight.Black)
                InfoRow("Passenger", passenger?.name?.ifBlank { "Passenger" } ?: "Passenger")
                InfoRow("Phone", passenger?.phone?.ifBlank { "Available after accept" } ?: "Available after accept")
            }
            DhwCard {
                Text("Pickup", color = DhwOrange, fontWeight = FontWeight.Black)
                Text(trip?.pickupLocation ?: "Chongwe", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Distance: ${fare?.distanceKm ?: 0.0} km", color = DhwMuted)
                ListDivider()
                InfoRow("Destination area", trip?.destination ?: "Destination")
            }
            Surface(color = DhwOrange, shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("YOUR EARNINGS", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                    Text("K${formatZmw(fare?.driverFare ?: 0)}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 32.sp)
                    Text("${fare?.distanceKm ?: 0.0} km • ${trip?.paymentMethod?.title ?: "Cash"}", color = Color.White)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Decline", onDecline, Modifier.weight(1f))
                PrimaryButton(if (accepting) "Accepting…" else "Accept ride", onAccept, Modifier.weight(1f), enabled = !accepting)
            }
            Text("Ride ID: ${trip?.id ?: "#DHW"}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = DhwMuted, fontSize = 12.sp)
        }
    }
}
