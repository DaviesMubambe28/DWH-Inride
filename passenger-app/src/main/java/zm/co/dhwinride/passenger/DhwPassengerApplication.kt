package zm.co.dhwinride.passenger

import android.app.Application
import zm.co.dhwinride.core.ui.configureMapbox

class DhwPassengerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val token = BuildConfig.MAPBOX_PUBLIC_TOKEN
        if (token.startsWith("pk.")) {
            configureMapbox(token)
        }
    }
}
