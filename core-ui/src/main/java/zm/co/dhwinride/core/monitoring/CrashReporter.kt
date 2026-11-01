package zm.co.dhwinride.core.monitoring

import android.content.Context
import android.os.Process
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import kotlin.system.exitProcess

/** Records only that a fatal error occurred, never a throwable/message or user data.
 * Reports on the next authenticated foreground sync; does not promise crash-time delivery.
 */
object CrashReporter {
    @Volatile private var installed = false

    @Synchronized fun install(context: Context) {
        if (installed) return
        installed = true
        val preferences = context.getSharedPreferences("dhw_crash_marker", Context.MODE_PRIVATE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                preferences.edit().putString("pending_event", UUID.randomUUID().toString()).commit()
            } finally {
                if (previous != null) previous.uncaughtException(thread, error)
                else {
                    Process.killProcess(Process.myPid())
                    exitProcess(10)
                }
            }
        }
    }

    suspend fun flushIfSignedIn(context: Context, client: SupabaseClient) {
        if (client.auth.currentUserOrNull() == null) return
        val preferences = context.getSharedPreferences("dhw_crash_marker", Context.MODE_PRIVATE)
        val event = preferences.getString("pending_event", null) ?: return
        val recorded = client.postgrest.rpc("report_my_app_crash", buildJsonObject { put("p_event_id", event) }).decodeAs<Boolean>()
        if (recorded && preferences.getString("pending_event", null) == event) {
            preferences.edit().remove("pending_event").apply()
        }
    }
}
