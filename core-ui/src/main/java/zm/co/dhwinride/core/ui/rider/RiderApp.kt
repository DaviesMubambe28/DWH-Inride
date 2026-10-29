package zm.co.dhwinride.core.ui.rider

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import zm.co.dhwinride.core.data.AuthRepository
import zm.co.dhwinride.core.model.RiderEarnings
import zm.co.dhwinride.core.model.RiderProfile
import zm.co.dhwinride.core.model.Trip
import zm.co.dhwinride.core.domain.formatZmw
import zm.co.dhwinride.core.ui.AppUserRole
import zm.co.dhwinride.core.ui.BottomNav
import zm.co.dhwinride.core.ui.BrandHeader
import zm.co.dhwinride.core.ui.DhwBlue
import zm.co.dhwinride.core.ui.DhwCard
import zm.co.dhwinride.core.ui.DhwGreen
import zm.co.dhwinride.core.ui.DhwInk
import zm.co.dhwinride.core.ui.DhwMuted
import zm.co.dhwinride.core.ui.DhwNavy
import zm.co.dhwinride.core.ui.DhwOrange
import zm.co.dhwinride.core.ui.DhwSurface
import zm.co.dhwinride.core.ui.ErrorNotice
import zm.co.dhwinride.core.ui.InfoRow
import zm.co.dhwinride.core.ui.LaunchAccessFlow
import zm.co.dhwinride.core.ui.ListDivider
import zm.co.dhwinride.core.ui.TripMap
import zm.co.dhwinride.core.ui.TripStage
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import zm.co.dhwinride.core.ui.LiveMap
import zm.co.dhwinride.core.ui.LoadingState
import zm.co.dhwinride.core.ui.RiderCancellation
import zm.co.dhwinride.core.ui.PilotSupport
import zm.co.dhwinride.core.ui.PrimaryButton
import zm.co.dhwinride.core.ui.ScreenTitle
import zm.co.dhwinride.core.ui.SecondaryButton
import zm.co.dhwinride.core.ui.StatusPill

@Composable
fun RiderApp(viewModel: RiderViewModel, authRepository: AuthRepository) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshOnForeground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var accessReady by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val availabilityPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            viewModel.toggleOnline(true)
        } else viewModel.showError("Location permission is required for pilot dispatch. Allow location in Android Settings, then go online again.")
    }

    var pendingDocumentType by remember { mutableStateOf<String?>(null) }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val documentType = pendingDocumentType
        pendingDocumentType = null
        if (uri != null && documentType != null) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("The selected document could not be read.")
                viewModel.submitDocument(documentType, uri.lastPathSegment ?: "document.jpg", bytes)
            }.onFailure { error -> viewModel.showError(error.message ?: "The selected document could not be read.") }
        }
    }
    val screen = uiState.screen
    val rootScreens = listOf(RiderNavScreen.Home, RiderNavScreen.Earnings, RiderNavScreen.History, RiderNavScreen.Profile)

    if (!accessReady) {
        LaunchAccessFlow(AppUserRole.Rider, authRepository) { accessReady = true }
        return
    }

    Scaffold(
        containerColor = DhwSurface,
        topBar = {
            Column {
                BrandHeader(
                    subtitle = "Rider app • Chongwe",
                    trailing = if (screen in rootScreens) if (uiState.isOnline) "ONLINE" else "OFFLINE" else "Back",
                    onTrailingClick = { if (screen !in rootScreens) viewModel.navigateTo(RiderNavScreen.Home) },
                )
            }
        },
        bottomBar = {
            if (screen in rootScreens) {
                BottomNav(
                    items = listOf("Ride", "Earnings", "History", "Profile"),
                    selected = when (screen) {
                        RiderNavScreen.Earnings -> "Earnings"
                        RiderNavScreen.History -> "History"
                        RiderNavScreen.Profile -> "Profile"
                        else -> "Ride"
                    },
                    onSelected = {
                        val target = when (it) {
                            "Earnings" -> RiderNavScreen.Earnings
                            "History" -> RiderNavScreen.History
                            "Profile" -> RiderNavScreen.Profile
                            else -> RiderNavScreen.Home
                        }
                        viewModel.navigateTo(target)
                    },
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            uiState.errorMessage?.let { message -> ErrorNotice(message, onRefresh = viewModel::refreshOnForeground) }
            Box(Modifier.weight(1f)) {
                when (screen) {
                    RiderNavScreen.Home -> RiderHome(
                        online = uiState.isOnline,
                        earnings = uiState.earnings,
                        availableRequestCount = uiState.availableRequests.size,
                        isLoadingRequests = uiState.isLoadingRequests,
                        onOnline = { online ->
                            val allowed = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (!online || allowed) viewModel.toggleOnline(online)
                            else availabilityPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        },
                        onViewRequests = { viewModel.navigateTo(RiderNavScreen.Request) },
                    )
                    RiderNavScreen.Request -> IncomingRequest(
                        trip = uiState.selectedRequest,
                        requests = uiState.availableRequests,
                        isLoading = uiState.isLoadingRequests,
                        accepting = uiState.acceptingTripId != null,
                        onSelect = { viewModel.selectRequest(it) },
                        onAccept = { viewModel.acceptRequest() },
                        onDecline = { viewModel.declineRequest() },
                    )
                    RiderNavScreen.Pickup -> PickupNavigation(
                        trip = uiState.activeTrip,
                        onArrive = { viewModel.confirmArrival() },
                        cancelling = uiState.cancellingTrip,
                        onCancel = viewModel::cancelPilotTrip,
                    )
                    RiderNavScreen.Arrival -> ArrivalScreen(
                        trip = uiState.activeTrip,
                        onContinue = { viewModel.navigateTo(RiderNavScreen.Pin) },
                        cancelling = uiState.cancellingTrip,
                        onCancel = viewModel::cancelPilotTrip,
                    )
                    RiderNavScreen.Pin -> RiderPinScreen(
                        trip = uiState.activeTrip,
                        pin = uiState.pinInput,
                        onKey = { viewModel.appendPinDigit(it) },
                        cancelling = uiState.cancellingTrip, onCancel = viewModel::cancelPilotTrip,
                    )
                    RiderNavScreen.ActiveTrip -> RiderActiveTrip(
                        trip = uiState.activeTrip,
                        onComplete = { viewModel.completeTrip() },
                        cancelling = uiState.cancellingTrip, onCancel = viewModel::cancelPilotTrip,
                    )
                    RiderNavScreen.Complete -> CompleteTrip(
                        trip = uiState.activeTrip,
                        onContinue = { viewModel.navigateTo(RiderNavScreen.Cash) },
                    )
                    RiderNavScreen.Cash -> CashConfirmation(
                        trip = uiState.activeTrip,
                        received = uiState.paymentReceived,
                        onToggleReceived = { viewModel.togglePaymentReceived() },
                        onDone = { viewModel.finishCashConfirmation() },
                    )
                    RiderNavScreen.Earnings -> EarningsScreen(earnings = uiState.earnings)
                    RiderNavScreen.History -> RiderHistory(history = uiState.tripHistory)
                    RiderNavScreen.Profile -> RiderProfile(
                        profile = uiState.profile,
                        onRegistration = { viewModel.navigateTo(RiderNavScreen.Registration) },
                        onVerification = { viewModel.navigateTo(RiderNavScreen.Verification) },
                        onSafety = { viewModel.navigateTo(RiderNavScreen.Safety) },
                    )
                    RiderNavScreen.Registration -> RegistrationScreen(
                        profile = uiState.profile,
                        onSave = { make, model, year, plate, color ->
                            viewModel.saveMotorcycle(make, model, year, plate, color) { viewModel.navigateTo(RiderNavScreen.Verification) }
                        },
                    )
                    RiderNavScreen.Verification -> VerificationScreen(
                        submittedDocuments = uiState.submittedDocuments,
                        onPick = { documentType -> pendingDocumentType = documentType; documentPicker.launch("image/*") },
                        onDone = { viewModel.navigateTo(RiderNavScreen.Profile) },
                    )
                    RiderNavScreen.Safety -> RiderSafety()
                }
            }
        }
    }
}

@Composable
private fun RiderHome(
    online: Boolean,
    earnings: RiderEarnings,
    availableRequestCount: Int,
    isLoadingRequests: Boolean,
    onOnline: (Boolean) -> Unit,
    onViewRequests: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        LiveMap(Modifier.fillMaxSize(), showRoute = false, label = if (online) "Waiting for requests" else "You are offline")
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            shadowElevation = 6.dp,
        ) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (online) "You’re online" else "You’re offline", fontWeight = FontWeight.Black, color = if (online) DhwGreen else DhwInk)
                    Text(if (online) "Dispatch needs a fresh position inside the pilot area" else "Go online to receive requests", color = DhwMuted, fontSize = 12.sp)
                }
                Switch(
                    checked = online,
                    modifier = Modifier.semantics { contentDescription = "Available for ride requests" },
                    onCheckedChange = onOnline,
                    colors = SwitchDefaults.colors(checkedTrackColor = DhwGreen, checkedThumbColor = Color.White),
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeight * .68f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DhwCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Today’s earnings", color = DhwMuted)
                        Text("K${formatZmw(earnings.todayAmount)}", color = DhwOrange, fontWeight = FontWeight.Black, fontSize = 28.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${earnings.todayTripsCount} trip${if (earnings.todayTripsCount != 1) "s" else ""}", fontWeight = FontWeight.Bold)
                        Text("${earnings.todayKm} km", color = DhwMuted)
                    }
                }
            }
            if (online) {
                Text(
                    when {
                        availableRequestCount > 0 -> "$availableRequestCount ride request${if (availableRequestCount == 1) "" else "s"} available"
                        isLoadingRequests -> "Checking for ride requests…"
                        else -> "No ride requests right now. We’ll keep checking while you’re online."
                    },
                    color = DhwMuted,
                )
                SecondaryButton("View ride requests", onViewRequests)
            }
        }
    }
}

@Composable
private fun IncomingRequest(
    trip: Trip?,
    requests: List<Trip>,
    isLoading: Boolean,
    accepting: Boolean,
    onSelect: (String) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    var countdown by remember { mutableIntStateOf(20) }
    val passenger = trip?.passenger
    val fare = trip?.fare
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Surface(color = DhwOrange) {
            Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("NEW RIDE REQUEST", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text("Incoming request", color = Color.White.copy(alpha = .85f))
                }
                Surface(color = Color.White, shape = CircleShape) {
                    Text("${countdown}s", color = DhwOrange, fontWeight = FontWeight.Black, modifier = Modifier.padding(12.dp))
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                requests.isEmpty() && isLoading -> LoadingState("Loading available ride requests…")
                requests.isEmpty() -> Text("No ride requests right now. Keep the app online to receive new requests.", color = DhwMuted)
                requests.size > 1 -> requests.forEach { request ->
                    DhwCard(modifier = Modifier.clickable { onSelect(request.id) }) {
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

@Composable
private fun PickupNavigation(trip: Trip?, onArrive: () -> Unit, cancelling: Boolean, onCancel: (String) -> Unit) {
    val passenger = trip?.passenger
    Column(Modifier.fillMaxSize()) {
        TripStage("Travel to pickup", "Stop safely at the pickup point, then confirm you have arrived.")
        TripMap(trip, Modifier.weight(1f).fillMaxWidth())
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DhwCard {
                Text("Passenger pickup", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text(passenger?.name ?: "Passenger", color = DhwMuted)
                InfoRow("Pickup location", trip?.pickupLocation ?: "Chongwe")
                InfoRow("Your earnings", "K${formatZmw(trip?.fare?.driverFare ?: 0)}", DhwGreen)
                Text("Wait at the agreed pickup point before verifying the passenger PIN.", color = DhwNavy)
            }
            PrimaryButton("I have arrived", onArrive, enabled = !cancelling)
            RiderCancellation(atPickup = false, busy = cancelling, onCancel = onCancel)
        }
    }
}

@Composable
private fun ArrivalScreen(trip: Trip?, onContinue: () -> Unit, cancelling: Boolean, onCancel: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(40.dp))
        Box(Modifier.size(90.dp).background(DhwGreen.copy(alpha = .14f), CircleShape), contentAlignment = Alignment.Center) {
            Text("✓", color = DhwGreen, fontSize = 46.sp, fontWeight = FontWeight.Black)
        }
        ScreenTitle("Arrival confirmed", "Wait at pickup and verify passenger’s trip PIN")
        DhwCard {
            InfoRow("Passenger", trip?.passenger?.name ?: "Passenger")
            InfoRow("Pickup", trip?.pickupLocation ?: "Chongwe")
        }
        PrimaryButton("Enter trip PIN", onContinue, enabled = !cancelling)
        RiderCancellation(atPickup = true, busy = cancelling, onCancel = onCancel)
        Text("Do not start moving until PIN is verified.", color = DhwMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RiderPinScreen(trip: Trip?, pin: String, onKey: (String) -> Unit, cancelling: Boolean, onCancel: (String) -> Unit) {
    val passenger = trip?.passenger
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(32.dp))
        ScreenTitle("Verify passenger PIN", "Ask ${passenger?.name ?: "passenger"} for the 4-digit PIN")
        Surface(color = Color.White, shape = RoundedCornerShape(18.dp), shadowElevation = 3.dp) {
            Text(if (pin.isEmpty()) "––––" else pin, fontSize = 36.sp, fontWeight = FontWeight.Black, color = DhwNavy, modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp))
        }
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "✓")).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    Surface(
                        modifier = Modifier.size(68.dp).semantics { contentDescription = when (key) { "⌫" -> "Delete last digit"; "✓" -> "Verify trip PIN"; else -> key } }.clickable(role = Role.Button) { onKey(key) },
                        color = if (key == "✓") DhwOrange else Color.White,
                        shape = CircleShape,
                        shadowElevation = 2.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) { Text(key, color = if (key == "✓") Color.White else DhwInk, fontSize = 23.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        RiderCancellation(atPickup = true, busy = cancelling, onCancel = onCancel)
    }
}

@Composable
private fun RiderActiveTrip(trip: Trip?, onComplete: () -> Unit, cancelling: Boolean, onCancel: (String) -> Unit) {
    val passenger = trip?.passenger
    Column(Modifier.fillMaxSize()) {
        TripStage("Trip in progress", "Drive safely. Complete the trip only after reaching the destination.")
        TripMap(trip, Modifier.weight(1f).fillMaxWidth())
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DhwCard {
                InfoRow("Passenger", passenger?.name ?: "Passenger")
                InfoRow("Destination", trip?.destination ?: "Destination")
                InfoRow("Amount to collect", "K${formatZmw(trip?.fare?.fareAmount ?: 0)}", DhwOrange)
                InfoRow("Payment method", trip?.paymentMethod?.title ?: "Cash")
            }
            PrimaryButton("Complete trip", onComplete, enabled = !cancelling)
            RiderCancellation(atPickup = false, busy = cancelling, onCancel = onCancel)
        }
    }
}

@Composable
private fun CompleteTrip(trip: Trip?, onContinue: () -> Unit) {
    val fare = trip?.fare
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(40.dp))
        Box(Modifier.size(96.dp).background(DhwGreen, CircleShape), contentAlignment = Alignment.Center) {
            Text("✓", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.Black)
        }
        ScreenTitle("Trip completed", "Collect payment from passenger")
        DhwCard {
            InfoRow("Ride fare", "K${formatZmw(fare?.driverFare ?: 0)}")
            InfoRow("Service fee", "K${formatZmw(fare?.serviceFee ?: 0)}")
            ListDivider()
            InfoRow("Passenger pays", "K${formatZmw(fare?.fareAmount ?: 0)}", DhwOrange)
        }
        PrimaryButton("Payment confirmation", onContinue)
    }
}

@Composable
private fun CashConfirmation(
    trip: Trip?,
    received: Boolean,
    onToggleReceived: () -> Unit,
    onDone: () -> Unit,
) {
    val fare = trip?.fare
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ScreenTitle("Payment confirmation", "Confirm once passenger has paid")
        DhwCard {
            Text("Amount due", color = DhwMuted)
            Text("K${formatZmw(fare?.fareAmount ?: 0)}", color = DhwOrange, fontWeight = FontWeight.Black, fontSize = 42.sp)
            InfoRow("Your ride earnings", "K${formatZmw(fare?.driverFare ?: 0)}", DhwGreen)
            InfoRow("Service fee", "K${formatZmw(fare?.serviceFee ?: 0)}")
        }
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { onToggleReceived() },
            color = if (received) DhwGreen.copy(alpha = .12f) else Color.White,
            shape = RoundedCornerShape(18.dp),
        ) {
            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(28.dp).background(if (received) DhwGreen else DhwMuted.copy(alpha = .2f), CircleShape), contentAlignment = Alignment.Center) {
                    Text(if (received) "✓" else "", color = Color.White, fontWeight = FontWeight.Black)
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("I received K${formatZmw(fare?.fareAmount ?: 0)}", fontWeight = FontWeight.Bold)
                    Text("Payment confirmed by rider", color = DhwMuted, fontSize = 11.sp)
                }
            }
        }
        PrimaryButton("Finish ride", onDone, enabled = received)
    }
}

@Composable
private fun EarningsScreen(earnings: RiderEarnings) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Earnings", "Rider earnings breakdown")
        Surface(color = DhwOrange, shape = RoundedCornerShape(22.dp), shadowElevation = 5.dp) {
            Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TODAY’S EARNINGS", color = Color.White.copy(alpha = .82f))
                Text("K${formatZmw(earnings.todayAmount)}", color = Color.White, fontWeight = FontWeight.Black, fontSize = 40.sp)
                Text("${earnings.todayTripsCount} trip${if (earnings.todayTripsCount != 1) "s" else ""} • ${earnings.todayKm} km", color = Color.White)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard("This week", "K${formatZmw(earnings.weekAmount)}", Modifier.weight(1f))
            SummaryCard("This month", "K${formatZmw(earnings.monthAmount)}", Modifier.weight(1f))
        }
        DhwCard {
            Text("Summary", fontWeight = FontWeight.Black)
            InfoRow("Today's gross earnings", "K${formatZmw(earnings.todayAmount)}")
            InfoRow("Completed trips", "${earnings.todayTripsCount}")
            InfoRow("Distance covered", "${earnings.todayKm} km")
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, color = Color.White, shape = RoundedCornerShape(18.dp), shadowElevation = 2.dp) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = DhwMuted, fontSize = 12.sp)
            Text(value, color = DhwNavy, fontWeight = FontWeight.Black, fontSize = 21.sp)
        }
    }
}

@Composable
private fun RiderHistory(history: List<Trip>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Trip history", "Completed trips")
        if (history.isEmpty()) {
            DhwCard {
                Text("No completed trips yet", fontWeight = FontWeight.Bold)
                Text("Your completed trips will appear here.", color = DhwMuted, fontSize = 13.sp)
            }
        } else {
            history.forEach { trip ->
                DhwCard {
                    Text("${trip.passenger.name.ifBlank { "Passenger" }} • ${trip.destination}", fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${trip.distanceKm} km", color = DhwMuted, fontSize = 12.sp)
                        Text("+K${formatZmw(trip.fare.driverFare)}", color = DhwGreen, fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
private fun RiderProfile(
    profile: RiderProfile,
    onRegistration: () -> Unit,
    onVerification: () -> Unit,
    onSafety: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Rider profile", "Motorcycle and account details")
        DhwCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp).background(DhwNavy, CircleShape), contentAlignment = Alignment.Center) {
                    Text(
                        profile.name.take(2).ifBlank { "RD" }.uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 23.sp,
                    )
                }
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(profile.name.ifBlank { "Rider Profile" }, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Text(
                        if (profile.rating > 0.0) "★ ${profile.rating} • ${profile.completedRides} rides" else "No ratings yet",
                        color = DhwMuted,
                    )
                    StatusPill(if (profile.isVerified) "Verified" else "Pending")
                }
            }
        }
        ProfileOption("Registration details", "Personal and contact information", onRegistration)
        ProfileOption("Documents & verification", "Licence, NRC and motorcycle papers", onVerification)
        ProfileOption("Motorcycle", "${profile.motorcycleModel.ifBlank { "Not provided" }} • ${profile.plateNumber.ifBlank { "Not provided" }}", {}, enabled = false)
        ProfileOption("Safety & incidents", "Emergency and support tools", onSafety)
    }
}

@Composable
private fun ProfileOption(title: String, detail: String, onClick: () -> Unit, enabled: Boolean = true) {
    DhwCard(modifier = if (enabled) Modifier.clickable(onClick = onClick) else Modifier) {
        Text(title, fontWeight = FontWeight.Bold)
        Text(detail, color = DhwMuted, fontSize = 13.sp)
    }
}

@Composable
private fun RegistrationScreen(
    profile: RiderProfile,
    onSave: (make: String, model: String, year: String, plate: String, color: String) -> Unit,
) {
    var make by remember(profile.motorcycleMake) { mutableStateOf(profile.motorcycleMake) }
    var model by remember(profile.motorcycleModel) { mutableStateOf(profile.motorcycleModel) }
    var year by remember(profile.motorcycleYear) { mutableStateOf(profile.motorcycleYear?.toString().orEmpty()) }
    var plate by remember(profile.plateNumber) { mutableStateOf(profile.plateNumber) }
    var color by remember(profile.motorcycleColor) { mutableStateOf(profile.motorcycleColor) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Rider registration", "Official details")
        Text("${profile.name.ifBlank { "Rider" }} • ${profile.phone.ifBlank { "Phone not provided" }}", color = DhwMuted)
        OutlinedTextField(make, { make = it }, Modifier.fillMaxWidth(), label = { Text("Motorcycle make") }, singleLine = true)
        OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Motorcycle model") }, singleLine = true)
        OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, Modifier.fillMaxWidth(), label = { Text("Year") }, singleLine = true)
        OutlinedTextField(plate, { plate = it }, Modifier.fillMaxWidth(), label = { Text("Plate number") }, singleLine = true)
        OutlinedTextField(color, { color = it }, Modifier.fillMaxWidth(), label = { Text("Colour") }, singleLine = true)
        PrimaryButton("Save and continue to documents", { onSave(make, model, year, plate, color) })
    }
}

@Composable
private fun VerificationScreen(
    submittedDocuments: Set<String>,
    onPick: (String) -> Unit,
    onDone: () -> Unit,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Document verification", "Driver documents and verification")
        DocumentRow("National Registration Card", if ("nrc" in submittedDocuments) "Submitted" else "Not submitted", if ("nrc" in submittedDocuments) DhwGreen else DhwOrange, { onPick("nrc") })
        DocumentRow("Rider licence", if ("license" in submittedDocuments) "Submitted" else "Not submitted", if ("license" in submittedDocuments) DhwGreen else DhwOrange, { onPick("license") })
        DocumentRow("Motorcycle registration", if ("vehicle_registration" in submittedDocuments) "Submitted" else "Not submitted", if ("vehicle_registration" in submittedDocuments) DhwGreen else DhwOrange, { onPick("vehicle_registration") })
        DhwCard {
            Text("Upload requirements", fontWeight = FontWeight.Bold)
            Text("Clear photo of documents • JPEG or PNG", color = DhwMuted, fontSize = 13.sp)
        }
        PrimaryButton("Done", onDone)
    }
}

@Composable
private fun DocumentRow(name: String, status: String, color: Color, onUpload: () -> Unit) {
    DhwCard(modifier = Modifier.clickable(onClick = onUpload)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold)
                Text(if (status == "Submitted") "Tap to replace" else "Tap to select a photo", color = DhwMuted, fontSize = 11.sp)
            }
            StatusPill(status, color)
        }
    }
}

@Composable
private fun RiderSafety() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Safety & incidents", "Rider safety tools")
        Surface(color = Color(0xFFFFE9E7), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Emergency", color = Color(0xFFB42318), fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("Stop somewhere safe before using your phone.")
                SecondaryButton("Emergency service integration required", {}, enabled = false)
            }
        }
        ProfileOption("Report an incident", "Backend integration required", {}, enabled = false)
        PilotSupport()
        ProfileOption("Vehicle problem", "Backend integration required", {}, enabled = false)
    }
}
