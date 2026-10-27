package zm.co.dhwinride.rider

import android.app.Application
import zm.co.dhwinride.core.ui.configureMapbox

class DhwRiderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val token = BuildConfig.MAPBOX_PUBLIC_TOKEN
        if (token.startsWith("pk.")) {
            configureMapbox(token)
        }
    }
}
