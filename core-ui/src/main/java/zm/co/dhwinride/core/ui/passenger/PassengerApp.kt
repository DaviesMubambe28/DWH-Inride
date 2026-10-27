package zm.co.dhwinride.core.ui.passenger

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import zm.co.dhwinride.core.data.AuthRepository
import zm.co.dhwinride.core.model.FareEstimate
import zm.co.dhwinride.core.model.PassengerProfile
import zm.co.dhwinride.core.model.Place
import zm.co.dhwinride.core.model.RidePaymentMethod
import zm.co.dhwinride.core.model.Trip
import zm.co.dhwinride.core.model.TripLocation
import zm.co.dhwinride.core.domain.formatZmw
import zm.co.dhwinride.core.ui.AppUserRole
import zm.co.dhwinride.core.ui.BottomNav
import zm.co.dhwinride.core.ui.BrandHeader
import zm.co.dhwinride.core.ui.DhwBlue
import zm.co.dhwinride.core.ui.DhwCard
import zm.co.dhwinride.core.ui.DhwGreen
import zm.co.dhwinride.core.ui.DhwInk
import zm.co.dhwinride.core.ui.DhwLine
import zm.co.dhwinride.core.ui.DhwMuted
import zm.co.dhwinride.core.ui.DhwNavy
import zm.co.dhwinride.core.ui.DhwOrange
import zm.co.dhwinride.core.ui.DhwSurface
import zm.co.dhwinride.core.ui.InfoRow
import zm.co.dhwinride.core.ui.LaunchAccessFlow
import zm.co.dhwinride.core.ui.ListDivider
import zm.co.dhwinride.core.ui.LoadingState
import zm.co.dhwinride.core.ui.LiveMap
import zm.co.dhwinride.core.ui.MapLocationPicker
import zm.co.dhwinride.core.ui.PrimaryButton
import zm.co.dhwinride.core.ui.ScreenTitle
import zm.co.dhwinride.core.ui.SecondaryButton
import zm.co.dhwinride.core.ui.StatusPill

@Composable
fun PassengerApp(viewModel: PassengerViewModel, authRepository: AuthRepository) {
    val context = LocalContext.current
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
    val snackbarHostState = remember { SnackbarHostState() }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            viewModel.fetchCurrentLocation()
        } else {
            viewModel.setLocationError(
                "Location permission was denied. Enable it in Android Settings or enter your pickup manually.",
            )
        }
    }
    LaunchedEffect(uiState.locationError) {
        uiState.locationError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearLocationError()
        }
    }
    val screen = uiState.screen
    val showBottom = screen in listOf(
        PassengerNavScreen.Home,
        PassengerNavScreen.History,
        PassengerNavScreen.Profile,
        PassengerNavScreen.Safety,
    )

    if (!accessReady) {
        LaunchAccessFlow(AppUserRole.Passenger, authRepository) { accessReady = true }
        return
    }

    Scaffold(
        containerColor = DhwSurface,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (screen != PassengerNavScreen.Destination) {
                Column {
                    BrandHeader(
                        subtitle = when (screen) {
                            PassengerNavScreen.Home -> "Your boda, minutes away"
                            PassengerNavScreen.Tracking, PassengerNavScreen.ActiveTrip -> "Live trip • Chongwe"
                            else -> "Passenger app"
                        },
                        trailing = if (screen == PassengerNavScreen.Home) "SOS" else "Back",
                        onTrailingClick = {
                            viewModel.navigateTo(if (screen == PassengerNavScreen.Home) PassengerNavScreen.Safety else PassengerNavScreen.Home)
                        },
                    )
                }
            }
        },
        bottomBar = {
            if (showBottom) {
                BottomNav(
                    items = listOf("Home", "Rides", "Safety", "Profile"),
                    selected = when (screen) {
                        PassengerNavScreen.History -> "Rides"
                        PassengerNavScreen.Safety -> "Safety"
                        PassengerNavScreen.Profile -> "Profile"
                        else -> "Home"
                    },
                    onSelected = {
                        val target = when (it) {
                            "Rides" -> PassengerNavScreen.History
                            "Safety" -> PassengerNavScreen.Safety
                            "Profile" -> PassengerNavScreen.Profile
                            else -> PassengerNavScreen.Home
                        }
                        viewModel.navigateTo(target)
                    },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                PassengerNavScreen.Home -> HomeScreen(
                    history = uiState.tripHistory,
                    onDestination = { viewModel.navigateTo(PassengerNavScreen.Destination) },
                )
                PassengerNavScreen.Destination -> DestinationScreen(
                    choosingPickup = uiState.pickup == null,
                    mapSelection = uiState.mapSelection,
                    isResolvingMapSelection = uiState.isResolvingMapSelection,
                    initialLatitude = uiState.pickup?.latitude ?: -15.3292,
                    initialLongitude = uiState.pickup?.longitude ?: 28.6821,
                    isFetchingLocation = uiState.isFetchingLocation,
                    onMapCenterChanging = { viewModel.onMapCenterChanging() },
                    onMapCenterChanged = { latitude, longitude -> viewModel.resolveMapCenter(latitude, longitude) },
                    onChoose = { viewModel.choosePlace(it) },
                    onBack = { viewModel.navigateTo(PassengerNavScreen.Home) },
                    onUseCurrentLocation = {
                        val fineGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                        val coarseGranted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (fineGranted || coarseGranted) {
                            viewModel.fetchCurrentLocation()
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        }
                    },
                )
                PassengerNavScreen.Estimate -> EstimateScreen(
                    destination = uiState.destination,
                    estimate = uiState.fareEstimate,
                    selectedPayment = uiState.paymentMethod,
                    isLoading = uiState.isLoading,
                    hasSelectedLocations = uiState.pickup != null && uiState.dropoff != null,
                    onPaymentSelected = { viewModel.setPaymentMethod(it) },
                    onBook = { viewModel.requestRide() },
                )
                PassengerNavScreen.Finding -> FindingScreen(
                    onCancel = { viewModel.cancelRequest() },
                )
                PassengerNavScreen.Confirmed -> ConfirmedScreen(
                    activeTrip = uiState.activeTrip,
                    onTrack = { viewModel.navigateTo(PassengerNavScreen.Tracking) },
                )
                PassengerNavScreen.Tracking -> TrackingScreen(
                    activeTrip = uiState.activeTrip,
                    riderLocation = uiState.riderLocation,
                )
                PassengerNavScreen.Pin -> PinScreen(
                    activeTrip = uiState.activeTrip,
                    onConfirm = { viewModel.verifyPin() },
                )
                PassengerNavScreen.ActiveTrip -> ActiveTripScreen(
                    activeTrip = uiState.activeTrip,
                    paymentMethod = uiState.paymentMethod,
                    riderLocation = uiState.riderLocation,
                )
                PassengerNavScreen.Receipt -> ReceiptScreen(
                    activeTrip = uiState.activeTrip,
                    paymentMethod = uiState.paymentMethod,
                    markedPaid = uiState.activeTrip?.passengerMarkedPaid ?: false,
                    onMarkPaid = { viewModel.markPaymentSent() },
                    onRate = { viewModel.navigateTo(PassengerNavScreen.Rating) },
                    onHome = { viewModel.navigateTo(PassengerNavScreen.Home) },
                )
                PassengerNavScreen.Rating -> RatingScreen(
                    activeTrip = uiState.activeTrip,
                    onDone = { rating -> viewModel.submitRating(rating) },
                )
                PassengerNavScreen.History -> HistoryScreen(
                    history = uiState.tripHistory,
                    onReceipt = { viewModel.navigateTo(PassengerNavScreen.Receipt) },
                )
                PassengerNavScreen.Profile -> ProfileScreen(
                    profile = uiState.passengerProfile,
                )
                PassengerNavScreen.Safety -> SafetyScreen()
            }
            uiState.errorMessage?.let { message ->
                Surface(
                    color = Color(0xFFFFE8E8),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
                ) { Text(message, color = Color(0xFF9B1C1C), modifier = Modifier.padding(12.dp), fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    history: List<Trip>,
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
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onDestination),
                    color = DhwSurface,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Where are you going?", color = DhwMuted, fontSize = 16.sp, modifier = Modifier.padding(16.dp))
                }
                InfoRow("Payment", "Cash or external mobile money", DhwOrange)
                if (history.isNotEmpty()) {
                    ListDivider()
                    val lastTrip = history.first()
                    Text("Recent ride", color = DhwMuted, fontSize = 12.sp)
                    InfoRow("${lastTrip.destination} • ${lastTrip.rider?.name ?: "Rider"}", "K${formatZmw(lastTrip.fare.fareAmount)}")
                }
            }
            PrimaryButton("Choose destination", onDestination)
        }
    }
}

@Composable
private fun DestinationScreen(
    choosingPickup: Boolean,
    mapSelection: Place?,
    isResolvingMapSelection: Boolean,
    initialLatitude: Double,
    initialLongitude: Double,
    isFetchingLocation: Boolean,
    onMapCenterChanging: () -> Unit,
    onMapCenterChanged: (Double, Double) -> Unit,
    onChoose: (Place) -> Unit,
    onBack: () -> Unit,
    onUseCurrentLocation: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
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
                .semantics { contentDescription = "Back to passenger home" }
                .clickable(onClick = onBack),
            color = Color.White,
            shape = CircleShape,
            shadowElevation = 4.dp,
        ) {
            Text("←", color = DhwInk, fontSize = 24.sp, modifier = Modifier.padding(horizontal = 17.dp, vertical = 8.dp))
        }

        if (choosingPickup) {
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(end = 16.dp, top = 18.dp)
                    .semantics { contentDescription = "Use current location" }
                    .clickable(enabled = !isFetchingLocation, onClick = onUseCurrentLocation),
                color = Color.White,
                shape = CircleShape,
                shadowElevation = 4.dp,
            ) {
                Text(if (isFetchingLocation) "…" else "◎", color = DhwBlue, fontSize = 23.sp, modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp))
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
                    modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        if (choosingPickup) "PICKUP ADDRESS" else "DESTINATION ADDRESS",
                        color = DhwInk,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                    )
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
                        text = "Done",
                        onClick = { mapSelection?.let(onChoose) },
                        enabled = mapSelection != null && !isResolvingMapSelection,
                    )
                }
            }
        }
    }
}

@Composable
private fun EstimateScreen(
    destination: String,
    estimate: FareEstimate,
    selectedPayment: RidePaymentMethod,
    isLoading: Boolean,
    hasSelectedLocations: Boolean,
    onPaymentSelected: (RidePaymentMethod) -> Unit,
    onBook: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LiveMap(Modifier.weight(1f).fillMaxWidth(), label = destination)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DhwCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Boda ride", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("Straight-line estimate • ${estimate.distanceKm} km", color = DhwMuted)
                    }
                    Text("K${formatZmw(estimate.fareAmount)}", color = DhwOrange, fontWeight = FontWeight.Black, fontSize = 25.sp)
                }
                ListDivider()
                Text("Indicative ZMW estimate using straight-line distance; final fare is calculated by the server using road routing.", color = DhwMuted, fontSize = 12.sp)
                Text("Payment after arrival", fontWeight = FontWeight.Bold)
                RidePaymentMethod.entries.forEach { method ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().clickable { onPaymentSelected(method) },
                        color = if (selectedPayment == method) DhwOrange.copy(alpha = .12f) else DhwSurface,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(22.dp).background(if (selectedPayment == method) DhwOrange else DhwLine, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selectedPayment == method) Text("✓", color = Color.White, fontWeight = FontWeight.Black)
                            }
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(method.title, fontWeight = FontWeight.Bold)
                                Text(method.description, color = DhwMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
                if (selectedPayment == RidePaymentMethod.MobileMoney) {
                    Text(
                        "Send K${formatZmw(estimate.fareAmount)} directly to the rider using your mobile-money app or USSD after arrival.",
                        color = DhwBlue,
                        fontSize = 12.sp,
                    )
                }
            }
            PrimaryButton(
                if (isLoading) "Requesting ride…" else "Request rider • K${formatZmw(estimate.fareAmount)}",
                onBook,
                enabled = hasSelectedLocations && !isLoading,
            )
        }
    }
}

@Composable
private fun FindingScreen(onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LiveMap(Modifier.fillMaxSize(), label = "Searching nearby")
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 8.dp,
            ) {
                Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                    LoadingState("Finding your rider…")
                }
            }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Notifying nearby riders in Chongwe...", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = DhwNavy, fontWeight = FontWeight.Bold)
            SecondaryButton("Cancel request", onCancel)
        }
    }
}

@Composable
private fun ConfirmedScreen(activeTrip: Trip?, onTrack: () -> Unit) {
    val rider = activeTrip?.rider
    Column(Modifier.fillMaxSize()) {
        LiveMap(Modifier.weight(1f).fillMaxWidth(), label = "Rider assigned")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DhwCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(58.dp).background(DhwNavy, CircleShape), contentAlignment = Alignment.Center) {
                        Text(
                            text = rider?.name?.take(2)?.uppercase() ?: "RD",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(rider?.name ?: "Assigned Rider", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(
                            if ((rider?.rating ?: 0.0) > 0.0) "★ ${rider?.rating} • ${rider?.completedRides ?: 0} rides" else "No ratings yet",
                            color = DhwMuted,
                        )
                        Text("${rider?.motorcycleModel ?: "Motorcycle"} • ${rider?.plateNumber ?: "Plate Number"}", color = DhwNavy, fontWeight = FontWeight.Bold)
                    }
                    StatusPill("Confirmed")
                }
                InfoRow("Total fare", "K${formatZmw(activeTrip?.fare?.fareAmount ?: 0)}")
                Text("Check rider and motorcycle details before boarding.", color = DhwMuted, fontSize = 12.sp)
            }
            PrimaryButton("Live tracking", onTrack)
        }
    }
}

@Composable
private fun TrackingScreen(activeTrip: Trip?, riderLocation: TripLocation?) {
    val rider = activeTrip?.rider
    Column(Modifier.fillMaxSize()) {
        Surface(color = DhwOrange) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("LIVE TRACKING", color = Color.White, fontWeight = FontWeight.Black)
                Text("En route", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        LiveMap(Modifier.weight(1f).fillMaxWidth(), label = if (riderLocation == null) "Waiting for rider location" else "Rider location updated")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DhwCard {
                Text("${rider?.name ?: "Rider"} is on the way", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("${rider?.motorcycleModel ?: "Motorcycle"} • ${rider?.plateNumber ?: ""}", color = DhwMuted)
                RiderLocationReadout(riderLocation)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill("Call", DhwBlue)
                    StatusPill("Message", DhwBlue)
                }
            }
        }
    }
}

@Composable
private fun PinScreen(activeTrip: Trip?, onConfirm: () -> Unit) {
    val pinCode = activeTrip?.pin?.chunked(1)?.joinToString(" ") ?: "– – – –"
    val rider = activeTrip?.rider
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        StatusPill("Rider has arrived")
        ScreenTitle("Your trip PIN", "Share this PIN with your rider to start the trip")
        Surface(color = DhwNavy, shape = RoundedCornerShape(24.dp), shadowElevation = 8.dp) {
            Text(pinCode, color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black, letterSpacing = 8.sp, modifier = Modifier.padding(30.dp))
        }
        DhwCard {
            InfoRow("Rider", rider?.name ?: "Rider")
            InfoRow("Motorcycle", rider?.motorcycleModel ?: "Motorcycle")
            InfoRow("Plate", rider?.plateNumber ?: "Plate", DhwOrange)
        }
        PrimaryButton("I gave PIN to rider", onConfirm)
        Text("Never share your PIN by phone or chat.", color = DhwMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ActiveTripScreen(activeTrip: Trip?, paymentMethod: RidePaymentMethod, riderLocation: TripLocation?) {
    Column(Modifier.fillMaxSize()) {
        Surface(color = DhwGreen) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("TRIP IN PROGRESS", color = Color.White, fontWeight = FontWeight.Black)
                Text("In progress", color = Color.White)
            }
        }
        LiveMap(Modifier.weight(1f).fillMaxWidth(), label = if (riderLocation == null) activeTrip?.destination ?: "Destination" else "Rider location updated")
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DhwCard {
                InfoRow("Destination", activeTrip?.destination ?: "Destination")
                InfoRow("Amount due", "K${formatZmw(activeTrip?.fare?.fareAmount ?: 0)}", DhwOrange)
                InfoRow("Pay after arrival", paymentMethod.title)
                RiderLocationReadout(riderLocation)
            }
        }
    }
}

@Composable
private fun RiderLocationReadout(location: TripLocation?) {
    if (location == null) {
        Text("Waiting for the rider's first location update.", color = DhwMuted, fontSize = 12.sp)
    } else {
        Text(
            "Last location: ${"%.5f".format(location.latitude)}, ${"%.5f".format(location.longitude)}",
            color = DhwMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun ReceiptScreen(
    activeTrip: Trip?,
    paymentMethod: RidePaymentMethod,
    markedPaid: Boolean,
    onMarkPaid: () -> Unit,
    onRate: () -> Unit,
    onHome: () -> Unit,
) {
    val fare = activeTrip?.fare ?: FareEstimate()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle(
            "Trip complete",
            if (paymentMethod == RidePaymentMethod.MobileMoney) "Pay the rider directly, outside DHW-inRide" else "Thanks for riding with DHW-inRide",
        )
        DhwCard {
            Text("RECEIPT", color = DhwNavy, fontWeight = FontWeight.Black)
            InfoRow("Ride ID", activeTrip?.id ?: "#DHW")
            InfoRow("To", activeTrip?.destination ?: "Destination")
            InfoRow("Distance", "${fare.distanceKm} km")
            ListDivider()
            InfoRow("Driver fare", "K${formatZmw(fare.driverFare)}")
            InfoRow("Service fee", "K${formatZmw(fare.serviceFee)}")
            InfoRow("Payment method", paymentMethod.title)
            InfoRow(if (markedPaid) "Payment sent" else "Amount to pay", "K${formatZmw(fare.fareAmount)}", if (markedPaid) DhwGreen else DhwOrange)
        }
        if (paymentMethod == RidePaymentMethod.MobileMoney && !markedPaid) {
            DhwCard {
                Text("Pay outside the app", fontWeight = FontWeight.Black)
                Text("Send K${formatZmw(fare.fareAmount)} directly to the rider using mobile money.", color = DhwMuted)
            }
            PrimaryButton("I sent K${formatZmw(fare.fareAmount)} to the rider", onMarkPaid)
        } else {
            StatusPill(
                if (paymentMethod == RidePaymentMethod.MobileMoney) "Payment acknowledged" else "Pay rider K${formatZmw(fare.fareAmount)} in cash",
                DhwOrange,
            )
            PrimaryButton("Rate your rider", onRate)
        }
        SecondaryButton("Back home", onHome)
    }
}

@Composable
private fun RatingScreen(activeTrip: Trip?, onDone: (Int) -> Unit) {
    var rating by remember { mutableIntStateOf(5) }
    val rider = activeTrip?.rider
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.height(30.dp))
        Box(Modifier.size(76.dp).background(DhwNavy, CircleShape), contentAlignment = Alignment.Center) {
            Text(rider?.name?.take(2)?.uppercase() ?: "RD", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
        ScreenTitle("How was your ride?", "Your feedback keeps Chongwe rides safe")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { value ->
                Text(
                    if (value <= rating) "★" else "☆",
                    color = DhwOrange,
                    fontSize = 40.sp,
                    modifier = Modifier.clickable { rating = value },
                )
            }
        }
        DhwCard {
            Text("Quick feedback", fontWeight = FontWeight.Bold)
        }
        PrimaryButton("Submit rating", { onDone(rating) })
    }
}

@Composable
private fun HistoryScreen(history: List<Trip>, onReceipt: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Your rides", "Trip history and receipts")
        if (history.isEmpty()) {
            DhwCard {
                Text("No past rides yet", fontWeight = FontWeight.Bold)
                Text("Your completed trips will appear here.", color = DhwMuted, fontSize = 13.sp)
            }
        } else {
            history.forEach { trip ->
                DhwCard(modifier = Modifier.clickable(onClick = onReceipt)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(trip.destination.ifBlank { "Ride" }, fontWeight = FontWeight.Bold)
                            Text(trip.rider?.name ?: "Completed trip", color = DhwMuted, fontSize = 12.sp)
                        }
                        Text("K${formatZmw(trip.fare.fareAmount)}", color = DhwOrange, fontWeight = FontWeight.Black)
                    }
                    StatusPill("Completed")
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(profile: PassengerProfile) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Profile", "Account and settings")
        DhwCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp).background(DhwOrange, CircleShape), contentAlignment = Alignment.Center) {
                    Text(
                        profile.name.take(2).ifBlank { "PA" }.uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                    )
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text(profile.name.ifBlank { "Passenger Profile" }, fontWeight = FontWeight.Black, fontSize = 19.sp)
                    Text(profile.phone.ifBlank { "Phone not registered" }, color = DhwMuted)
                }
            }
        }
        SettingsCard("Personal details", "Name, phone number and details")
        SettingsCard("Saved places", "Home, work and favourite destinations")
        SettingsCard("Privacy", "Location sharing and data controls")
        SettingsCard("Notifications", "Ride and safety alerts")
        SettingsCard("Sign out", "End this session")
    }
}

@Composable
private fun SafetyScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ScreenTitle("Safety & support", "Help is available before, during and after every ride")
        Surface(color = Color(0xFFFFE9E7), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Emergency assistance", color = Color(0xFFB42318), fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("Use your phone's emergency services if you are in immediate danger.", color = DhwInk)
                SecondaryButton("Emergency service integration required", {}, enabled = false)
            }
        }
        SettingsCard("Share trip", "Send a live trip link to someone you trust")
        SettingsCard("Report a safety issue", "Tell our safety team what happened")
        SettingsCard("Trip support", "Get help with a past or active ride")
        SettingsCard("Lost item", "Contact support about something left behind")
    }
}

@Composable
private fun SettingsCard(title: String, detail: String) {
    DhwCard {
        Text(title, fontWeight = FontWeight.Bold)
        Text(detail, color = DhwMuted, fontSize = 13.sp)
    }
}
