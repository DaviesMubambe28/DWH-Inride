package zm.co.dhwinride.rider

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import zm.co.dhwinride.core.data.RiderRepository
import zm.co.dhwinride.core.data.AuthRepository
import zm.co.dhwinride.core.data.SupabaseAuthRepository
import zm.co.dhwinride.core.data.SupabaseClientProvider
import zm.co.dhwinride.core.data.SupabaseRiderRepository
import zm.co.dhwinride.core.data.SupabaseTripRepository
import zm.co.dhwinride.core.data.TripRepository
import zm.co.dhwinride.core.ui.DhwTheme
import zm.co.dhwinride.core.ui.ConfigurationRequiredScreen
import zm.co.dhwinride.core.ui.rider.RiderApp
import zm.co.dhwinride.core.ui.rider.RiderViewModel
import zm.co.dhwinride.core.push.FcmTokenRegistrar
import zm.co.dhwinride.core.location.LocationTracker
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.launch
import android.util.Log

class MainActivity : ComponentActivity() {
    private var pushSupabaseClient: SupabaseClient? = null

    override fun onStart() {
        super.onStart()
        pushSupabaseClient?.let { client ->
            lifecycleScope.launch {
                runCatching {
                    FcmTokenRegistrar.registerIfSignedIn(this@MainActivity, client, BuildConfig.APPLICATION_ID)
                }.onFailure { error ->
                    if (error !is kotlinx.coroutines.CancellationException) {
                        Log.w("FcmToken", "Foreground token sync failed (${error.javaClass.simpleName})")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val configured = SupabaseClientProvider.isConfigured(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY)
        val mapboxConfigured = BuildConfig.MAPBOX_PUBLIC_TOKEN.startsWith("pk.")
        val tripRepository: TripRepository?
        val riderRepository: RiderRepository?
        val authRepository: AuthRepository?
        if (configured) {
            val supabaseClient = SupabaseClientProvider.createClient(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseKey = BuildConfig.SUPABASE_KEY,
            )
            pushSupabaseClient = supabaseClient
            tripRepository = SupabaseTripRepository(
                supabaseClient,
                BuildConfig.MAPBOX_PUBLIC_TOKEN,
                BuildConfig.SUPABASE_URL,
                BuildConfig.SUPABASE_KEY,
            )
            riderRepository = SupabaseRiderRepository(supabaseClient)
            authRepository = SupabaseAuthRepository(supabaseClient, applicationContext, BuildConfig.APPLICATION_ID)
        } else {
            tripRepository = null
            riderRepository = null
            authRepository = null
        }

        setContent {
            DhwTheme {
                if (!mapboxConfigured) {
                    ConfigurationRequiredScreen(
                        appName = "Rider app",
                        detail = "Mapbox is not configured. Add MAPBOX_PUBLIC_TOKEN to secrets.properties and MAPBOX_SERVER_TOKEN to supabase/functions/.env, then rebuild the app.",
                    )
                } else if (tripRepository == null || riderRepository == null || authRepository == null) {
                    ConfigurationRequiredScreen("Rider app")
                } else {
                    val riderViewModel: RiderViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                RiderViewModel(
                                    riderRepository,
                                    tripRepository,
                                    authRepository,
                                    createSavedStateHandle(),
                                    LocationTracker(this@MainActivity),
                                )
                            }
                        },
                    )
                    RiderApp(viewModel = riderViewModel, authRepository = authRepository)
                }
            }
        }
    }
}
