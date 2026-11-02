package zm.co.dhwinride.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.launch
import zm.co.dhwinride.core.data.PasswordRecovery
import zm.co.dhwinride.core.data.RecoveryStep

/** Intentionally not saveable: recovery codes and passwords must not survive process recreation. */
@Composable
internal fun PasswordRecoveryForm(recovery: PasswordRecovery, onBack: () -> Unit) {
    val state by recovery.state.collectAsState()
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    // Returning to this screen always requires a fresh code; never resume a hidden recovery session.
    LaunchedEffect(recovery) { recovery.cancel() }
    BackHandler {
        if (!state.busy) scope.launch { recovery.cancel(); onBack() }
    }
    Text("Reset password")
    when (state.step) {
        RecoveryStep.Request -> {
            Text("Request an email recovery code. Delivery depends on the service's email configuration.")
            OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Email") },
                enabled = !state.busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            PrimaryButton("Request code", { scope.launch { recovery.request(email) } }, enabled = !state.busy)
        }
        RecoveryStep.Code -> {
            Text("If your account can receive recovery email, enter its numeric code. Check spam. No code? Contact support or try again later.")
            OutlinedTextField(code, { code = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Recovery code") },
                enabled = !state.busy, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            PrimaryButton("Verify code", { scope.launch { val value = code; code = ""; recovery.verify(value) } }, enabled = !state.busy)
        }
        RecoveryStep.NewPassword -> {
            OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text("New password (at least 8 characters)") },
                enabled = !state.busy, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            OutlinedTextField(confirmation, { confirmation = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Confirm password") },
                enabled = !state.busy, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            PrimaryButton("Change password", { scope.launch {
                val value = password; val repeated = confirmation; password = ""; confirmation = ""
                recovery.changePassword(value, repeated)
            } }, enabled = !state.busy)
        }
        RecoveryStep.Done -> Text("Password changed. Sign in with your new password.")
    }
    state.error?.let { Text(it) }
    SecondaryButton("Back to sign in", { scope.launch { recovery.cancel(); onBack() } }, enabled = !state.busy)
}
