package zm.co.dhwinride.core.data

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

enum class RecoveryStep { Request, Code, NewPassword, Done }
data class RecoveryState(val step: RecoveryStep = RecoveryStep.Request, val busy: Boolean = false, val error: String? = null)

internal interface RecoveryGateway {
    suspend fun request(email: String)
    suspend fun verify(email: String, code: String)
    suspend fun updatePassword(password: String)
    suspend fun clear()
}

/** Recovery credentials stay in memory and never enter public UI state or normal sign-in storage. */
class PasswordRecovery internal constructor(private val gateway: RecoveryGateway) {
    private val mutableState = MutableStateFlow(RecoveryState())
    val state = mutableState.asStateFlow()
    private val lock = Mutex()
    private var email: String? = null

    private suspend fun operation(action: suspend () -> Unit) {
        if (!lock.tryLock()) return
        mutableState.value = mutableState.value.copy(busy = true, error = null)
        try {
            action()
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { reset() }
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = mutableState.value.copy(error = "Unable to continue. Check your connection and try again.")
        } finally {
            mutableState.value = mutableState.value.copy(busy = false)
            lock.unlock()
        }
    }

    private suspend fun reset() {
        email = null
        mutableState.value = RecoveryState()
        gateway.clear()
    }

    suspend fun request(value: String) = operation {
        reset()
        val normalized = value.trim()
        if (normalized.length > 254 || !Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+").matches(normalized)) {
            mutableState.value = RecoveryState(error = "Enter a valid email address.")
            return@operation
        }
        gateway.request(normalized)
        email = normalized
        mutableState.value = RecoveryState(RecoveryStep.Code)
    }

    suspend fun verify(code: String) = operation {
        val address = email ?: return@operation
        if (mutableState.value.step != RecoveryStep.Code) return@operation
        if (!Regex("[0-9]{6,10}").matches(code)) {
            mutableState.value = mutableState.value.copy(error = "Enter the numeric recovery code from your email.")
            return@operation
        }
        gateway.verify(address, code)
        mutableState.value = RecoveryState(RecoveryStep.NewPassword)
    }

    suspend fun changePassword(password: String, confirmation: String) = operation {
        if (mutableState.value.step != RecoveryStep.NewPassword) return@operation
        if (password.length < 8 || password != confirmation) {
            mutableState.value = mutableState.value.copy(error = "Use at least 8 characters and matching passwords.")
            return@operation
        }
        try {
            gateway.updatePassword(password)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            reset()
            mutableState.value = RecoveryState(error = "Password change could not be confirmed. Try signing in, or request another code.")
            return@operation
        }
        reset()
        mutableState.value = RecoveryState(RecoveryStep.Done)
    }

    suspend fun cancel() = operation { reset() }
}

internal fun supabasePasswordRecovery(url: String, key: String): PasswordRecovery {
    val recoveryClient = createSupabaseClient(url, key) {
        defaultLogLevel = LogLevel.NONE
        install(Auth) {
            autoLoadFromStorage = false
            autoSaveToStorage = false
            alwaysAutoRefresh = false
            sessionManager = MemorySessionManager()
        }
    }
    return PasswordRecovery(object : RecoveryGateway {
        override suspend fun request(email: String) = recoveryClient.auth.resetPasswordForEmail(email, redirectUrl = null)
        override suspend fun verify(email: String, code: String) {
            recoveryClient.auth.verifyEmailOtp(OtpType.Email.RECOVERY, email, code)
        }
        override suspend fun updatePassword(password: String) {
            recoveryClient.auth.updateUser { this.password = password }
            recoveryClient.auth.signOut(SignOutScope.GLOBAL)
        }
        override suspend fun clear() { recoveryClient.auth.clearSession() }
    })
}
