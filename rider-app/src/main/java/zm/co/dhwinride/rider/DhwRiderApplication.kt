package zm.co.dhwinride.rider

import android.app.Application
import zm.co.dhwinride.core.monitoring.CrashReporter
import zm.co.dhwinride.core.ui.configureMapbox

class DhwRiderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        val token = BuildConfig.MAPBOX_PUBLIC_TOKEN
        if (token.startsWith("pk.")) {
            configureMapbox(token)
        }
    }
}
