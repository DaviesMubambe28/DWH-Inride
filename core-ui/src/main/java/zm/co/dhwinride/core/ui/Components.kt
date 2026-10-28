package zm.co.dhwinride.core.ui

import android.util.Log
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.mapbox.maps.plugin.annotation.annotations
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.extension.observable.eventdata.MapLoadingErrorEventData
import com.mapbox.maps.plugin.delegates.listeners.OnMapLoadErrorListener
import com.mapbox.common.MapboxOptions

private const val DEFAULT_MAPBOX_STYLE = "mapbox://styles/mapbox/streets-v12"

fun configureMapbox(publicToken: String) {
    require(publicToken.startsWith("pk.")) { "A Mapbox public token is required." }
    MapboxOptions.accessToken = publicToken
}

@Composable
fun MapLocationPicker(
    modifier: Modifier = Modifier,
    initialLatitude: Double,
    initialLongitude: Double,
    onCameraMoving: () -> Unit,
    onCenterChanged: (latitude: Double, longitude: Double) -> Unit,
) {
    key(initialLatitude, initialLongitude) {
        val currentOnCameraMoving by rememberUpdatedState(onCameraMoving)
        val currentOnCenterChanged by rememberUpdatedState(onCenterChanged)
        NativeMap(
            modifier = modifier.fillMaxSize(),
            latitude = initialLatitude,
            longitude = initialLongitude,
            zoom = 16.0,
            onCameraChanged = { latitude, longitude ->
                currentOnCameraMoving()
                currentOnCenterChanged(latitude, longitude)
            },
        )
    }
}

@Composable
fun BrandHeader(
    subtitle: String? = null,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DhwNavy)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DHW", color = DhwOrange, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("-inRide", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            if (subtitle != null) {
                Text(subtitle, color = Color.White.copy(alpha = .78f), fontSize = 12.sp)
            }
        }
        if (trailing != null) {
            Surface(
                color = Color.White.copy(alpha = .12f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.heightIn(min = 48.dp).clickable(enabled = onTrailingClick != null, role = Role.Button) { onTrailingClick?.invoke() },
            ) {
                Text(trailing, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = DhwOrange),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DhwNavy),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DhwNavy),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DhwCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            content()
        }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, color = DhwInk)
        subtitle?.let { Text(it, color = DhwMuted, fontSize = 14.sp) }
    }
}

@Composable
fun InfoRow(label: String, value: String, valueColor: Color = DhwInk) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = DhwMuted, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Text(value, modifier = Modifier.weight(1f), color = valueColor, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
fun StatusPill(text: String, color: Color = DhwGreen) {
    Surface(color = color.copy(alpha = .12f), shape = RoundedCornerShape(50)) {
        Text(
            text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
fun ConfigurationRequiredScreen(appName: String, detail: String = "$appName is not connected to Supabase. Add SUPABASE_URL and SUPABASE_PUBLISHABLE_KEY to secrets.properties, then rebuild the app.") {
    Column(
        modifier = Modifier.fillMaxSize().background(DhwSurface).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("DHW-inRide", color = DhwNavy, fontSize = 30.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        Text("Configuration required", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            detail,
            color = DhwMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun BottomNav(items: List<String>, selected: String, onSelected: (String) -> Unit) {
    Surface(shadowElevation = 12.dp, color = Color.White) {
        Row(
            modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            items.forEach { item ->
                Column(
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp).selectable(selected = selected == item, role = Role.Tab, onClick = { onSelected(item) }).padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(7.dp).background(if (selected == item) DhwOrange else Color.Transparent, CircleShape),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item,
                        fontSize = 11.sp,
                        fontWeight = if (selected == item) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected == item) DhwNavy else DhwMuted,
                    )
                }
            }
        }
    }
}

@Composable
fun LiveMap(
    modifier: Modifier = Modifier,
    showRoute: Boolean = true,
    label: String = "Chongwe",
    latitude: Double = -15.3292,
    longitude: Double = 28.6821,
    zoom: Double = if (showRoute) 13.5 else 12.5,
    markers: List<MapMarker> = emptyList(),
) {
    Box(modifier = modifier.fillMaxSize().background(DhwMap)) {
        NativeMap(
            modifier = Modifier.fillMaxSize(),
            latitude = latitude,
            longitude = longitude,
            zoom = zoom,
            markers = markers,
        )
        Surface(
            color = Color.White.copy(alpha = .92f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
        ) {
            Text(
                text = label,
                color = DhwNavy,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun NativeMap(
    modifier: Modifier,
    latitude: Double,
    longitude: Double,
    zoom: Double,
    onCameraChanged: ((latitude: Double, longitude: Double) -> Unit)? = null,
    markers: List<MapMarker> = emptyList(),
) {
    var mapError by remember { mutableStateOf(false) }
    var retryCount by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val initialCamera = CameraOptions.Builder()
        .center(Point.fromLngLat(longitude, latitude))
        .zoom(zoom)
        .build()
    val currentCameraChanged by rememberUpdatedState(onCameraChanged)
    val mapView = remember(context) {
        MapView(
            context,
            MapInitOptions(
                context = context,
                cameraOptions = initialCamera,
                // Load the style explicitly below after error listeners are registered.
                styleUri = null,
                // A TextureView composes above and below Compose content reliably.
                textureView = true,
            ),
        ).also { view ->
            view.mapboxMap.addOnMapLoadErrorListener(object : OnMapLoadErrorListener {
                override fun onMapLoadError(eventData: MapLoadingErrorEventData) {
                    mapError = true
                    Log.e("DhwMapbox", "Map style could not be loaded.")
                }
            })
            view.mapboxMap.addOnCameraChangeListener {
                val center = view.mapboxMap.cameraState.center
                currentCameraChanged?.invoke(center.latitude(), center.longitude())
            }
            view.mapboxMap.loadStyleUri(DEFAULT_MAPBOX_STYLE)
        }
    }

    val markerManager = remember(mapView) { mapView.annotations.createPointAnnotationManager() }
    LaunchedEffect(markers) {
        markerManager.deleteAll()
        markers.forEach { marker ->
            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = android.graphics.Color.WHITE
            canvas.drawCircle(32f, 32f, 30f, paint)
            paint.color = android.graphics.Color.rgb(8, 44, 103)
            canvas.drawCircle(32f, 32f, 25f, paint)
            paint.color = android.graphics.Color.WHITE
            paint.textSize = 30f
            paint.textAlign = Paint.Align.CENTER
            paint.isFakeBoldText = true
            canvas.drawText(marker.label, 32f, 43f, paint)
            markerManager.create(PointAnnotationOptions().withPoint(Point.fromLngLat(marker.longitude, marker.latitude)).withIconImage(bitmap))
        }
    }
    LaunchedEffect(latitude, longitude, zoom) {
        if (onCameraChanged == null) mapView.mapboxMap.setCamera(initialCamera)
    }
    LaunchedEffect(retryCount) {
        if (retryCount > 0) mapView.mapboxMap.loadStyleUri(DEFAULT_MAPBOX_STYLE)
    }
    Box(modifier = modifier) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { mapView })
        if (mapError) {
            Surface(color = Color.White, modifier = Modifier.align(Alignment.Center).padding(16.dp)) {
                Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Map unavailable", fontWeight = FontWeight.Bold)
                    Text("Check your connection. Trip details are still available below.", color = DhwMuted)
                    TextButton(onClick = { mapError = false; retryCount++ }) { Text("Retry map") }
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        var destroyed = false
        val observer = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = mapView.onStart()
            override fun onResume(owner: LifecycleOwner) = mapView.onResume()
            override fun onStop(owner: LifecycleOwner) = mapView.onStop()
            override fun onDestroy(owner: LifecycleOwner) {
                if (!destroyed) {
                    mapView.onDestroy()
                    destroyed = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!destroyed) {
                mapView.onStop()
                mapView.onDestroy()
                destroyed = true
            }
        }
    }
}

@Composable
fun LoadingState(text: String) {
    Column(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CircularProgressIndicator(color = DhwOrange)
        Text(text, color = DhwMuted, textAlign = TextAlign.Center)
    }
}

@Composable
fun ListDivider() = HorizontalDivider(color = DhwLine)

@Composable
fun ErrorNotice(message: String, onRefresh: () -> Unit) {
    var dismissed by remember(message) { mutableStateOf(false) }
    if (dismissed) return
    Surface(color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Assertive }.padding(12.dp)) {
            Text("Something needs your attention", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            Row {
                TextButton(onClick = onRefresh) { Text("Refresh trip details") }
                TextButton(onClick = { dismissed = true }) { Text("Dismiss") }
            }
        }
    }
}
