package zm.co.dhwinride.core.push

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object FcmTokenRegistrar {
    private const val PREFERENCES = "dhw_push"
    private const val PENDING_TOKEN = "pending_fcm_token"
    private const val REGISTERED_TOKEN = "registered_fcm_token"

    /** Syncs the latest installation token only when Supabase has an authenticated user. */
    suspend fun registerIfSignedIn(context: Context, client: SupabaseClient, appId: String) {
        if (client.auth.currentUserOrNull() == null) return

        val token = FirebaseMessaging.getInstance().token.await()
        require(token.isNotBlank()) { "Firebase returned an empty registration token." }
        client.postgrest.rpc(
            "register_my_fcm_token",
            buildJsonObject {
                put("p_fcm_token", token)
                put("p_app_id", appId)
            },
        ).decodeAs<Boolean>()

        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(PENDING_TOKEN, token)
            .putString(REGISTERED_TOKEN, token)
            .apply()
    }

    /** Call before Supabase sign-out so the current user's installation row is removed. */
    suspend fun unregisterCurrentTokenIfSignedIn(context: Context, client: SupabaseClient) {
        if (client.auth.currentUserOrNull() == null) return

        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val token = runCatching { FirebaseMessaging.getInstance().token.await() }
            .getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: preferences.getString(REGISTERED_TOKEN, null).orEmpty()
        if (token.isBlank()) return

        client.postgrest.rpc(
            "unregister_my_fcm_token",
            buildJsonObject { put("p_fcm_token", token) },
        ).decodeAs<Boolean>()

        preferences.edit()
            .remove(REGISTERED_TOKEN)
            .remove(PENDING_TOKEN)
            .apply()
    }
}
