package zm.co.dhwinride.core.ui.passenger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import zm.co.dhwinride.core.domain.formatZmw
import zm.co.dhwinride.core.model.Place
import zm.co.dhwinride.core.model.Trip
import zm.co.dhwinride.core.ui.DhwBlue
import zm.co.dhwinride.core.ui.DhwCard
import zm.co.dhwinride.core.ui.DhwInk
import zm.co.dhwinride.core.ui.DhwMuted
import zm.co.dhwinride.core.ui.DhwNavy
import zm.co.dhwinride.core.ui.DhwOrange
import zm.co.dhwinride.core.ui.DhwSurface
import zm.co.dhwinride.core.ui.InfoRow
import zm.co.dhwinride.core.ui.ListDivider
import zm.co.dhwinride.core.ui.LiveMap
import zm.co.dhwinride.core.ui.MapLocationPicker
import zm.co.dhwinride.core.ui.PrimaryButton
import zm.co.dhwinride.core.ui.SecondaryButton

@Composable
internal fun HomeScreen(
    history: List<Trip>,
    pilotAvailable: Boolean,
    serviceMessage: String,
    onRefresh: () -> Unit,
    onDestination: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        LiveMap(modifier = Modifier.fillMaxSize(), showRoute = false, label = "Chongwe • Pickup")
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .heightIn(max = maxHeight * .82f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DhwCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).background(DhwBlue.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                        Text("●", color = DhwBlue)
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("Pickup", color = DhwMuted, fontSize = 12.sp)
                        Text("Choose pickup location", fontWeight = FontWeight.Bold)
                        Text("Pickup is selected in the next step", color = DhwMuted, fontSize = 12.sp)
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onDestination),
                    color = DhwSurface,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Where are you going?", color = DhwInk, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Search an address or choose it on the map", color = DhwMuted, fontSize = 12.sp)
                    }
                }
                InfoRow("Payment", "Cash only during the pilot", DhwOrange)
                if (history.isNotEmpty()) {
                    ListDivider()
                    val lastTrip = history.first()
                    Text("Recent ride", color = DhwMuted, fontSize = 12.sp)
                    InfoRow("${lastTrip.destination} • ${lastTrip.rider?.name ?: "Rider"}", "K${formatZmw(lastTrip.fare.fareAmount)}")
                }
            }
            Text(serviceMessage, color = DhwNavy)
            PrimaryButton(if (pilotAvailable) "Choose destination" else "View destination map", onDestination)
            if (!pilotAvailable) SecondaryButton("Check availability again", onRefresh)
        }
    }
}

@Composable
internal fun DestinationScreen(
    choosingPickup: Boolean,
    searchQuery: String,
    searchResults: List<Place>,
    isSearching: Boolean,
    mapSelection: Place?,
    isResolvingMapSelection: Boolean,
    initialLatitude: Double,
    initialLongitude: Double,
    isFetchingLocation: Boolean,
    onMapCenterChanging: () -> Unit,
    onMapCenterChanged: (Double, Double) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onChoose: (Place) -> Unit,
    onBack: () -> Unit,
    onUseCurrentLocation: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetMaxHeight = maxHeight * .72f
        MapLocationPicker(
            modifier = Modifier.fillMaxSize(),
            initialLatitude = initialLatitude,
            initialLongitude = initialLongitude,
            onCameraMoving = onMapCenterChanging,
            onCenterChanged = onMapCenterChanged,
        )

        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
            color = Color.White.copy(alpha = .94f),
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 3.dp,
        ) {
            Text("Swipe to move map", color = DhwInk, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
        }

        Surface(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 16.dp, top = 18.dp)
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .semantics { contentDescription = "Back to passenger home" }
                .clickable(role = Role.Button, onClick = onBack),
            color = Color.White,
            shape = CircleShape,
            shadowElevation = 4.dp,
        ) {
            Text("←", color = DhwInk, fontSize = 24.sp, modifier = Modifier.padding(horizontal = 17.dp, vertical = 8.dp).clearAndSetSemantics { })
        }

        if (choosingPickup) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 18.dp)
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "Use current location" }
                    .clickable(enabled = !isFetchingLocation, role = Role.Button, onClick = onUseCurrentLocation),
                color = Color.White,
                shape = CircleShape,
                shadowElevation = 4.dp,
            ) {
                Text(if (isFetchingLocation) "…" else "◎", color = DhwBlue, fontSize = 23.sp, modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp).clearAndSetSemantics { })
            }
        }

        Box(
            modifier = Modifier.align(Alignment.Center).padding(bottom = 16.dp)
                .size(25.dp)
                .shadow(4.dp, CircleShape)
                .background(DhwOrange, CircleShape)
                .padding(4.dp)
                .background(Color.White, CircleShape)
                .padding(4.dp)
                .background(DhwOrange, CircleShape),
        )

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                shadowElevation = 12.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = sheetMaxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        if (choosingPickup) "PICKUP ADDRESS" else "DESTINATION ADDRESS",
                        color = DhwInk,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                    )
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (choosingPickup) "Search pickup" else "Search destination") },
                        placeholder = { Text("Street, landmark or business") },
                        singleLine = true,
                        supportingText = {
                            Text(if (isSearching) "Searching…" else "Or move the map to place the pin")
                        },
                    )
                    searchResults.take(4).forEach { place ->
                        Surface(
                            modifier = Modifier.fillMaxWidth()
                                .clickable(role = Role.Button) { onChoose(place) },
                            color = DhwSurface,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Text(place.name, color = DhwInk, fontWeight = FontWeight.Bold)
                                Text(place.detail, color = DhwMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (place.distance.isNotBlank()) Text(place.distance, color = DhwBlue, fontSize = 12.sp)
                            }
                        }
                    }
                    if (searchQuery.isNotBlank() && !isSearching && searchResults.isEmpty()) {
                        Text("No matching locations yet. Refine the search or use the map pin.", color = DhwMuted, fontSize = 13.sp)
                    }
                    ListDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).background(DhwOrange.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(14.dp).background(DhwOrange, CircleShape))
                        }
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(
                                if (isResolvingMapSelection) "Finding address…" else mapSelection?.name ?: "Move map to choose",
                                fontWeight = FontWeight.Medium,
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                mapSelection?.detail ?: "Drag the map until the pin is on your location",
                                color = DhwMuted,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    PrimaryButton(
                        text = if (choosingPickup) "Use this pickup" else "Use this destination",
                        onClick = { mapSelection?.let(onChoose) },
                        enabled = mapSelection != null && !isResolvingMapSelection,
                    )
                }
            }
        }
    }
}
