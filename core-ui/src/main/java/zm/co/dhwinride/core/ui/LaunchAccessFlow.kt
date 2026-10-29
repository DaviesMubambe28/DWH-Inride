package zm.co.dhwinride.core.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import zm.co.dhwinride.core.data.ZambiaPhoneNumber
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import zm.co.dhwinride.core.data.AuthRepository

enum class AppUserRole(val label: String) {
    Passenger("Passenger"),
    Rider("Rider"),
}

private enum class AccessStage { Welcome, SignIn, SignUp, VerifyEmail, Profile, RiderReview }

@Composable
fun LaunchAccessFlow(role: AppUserRole, authRepository: AuthRepository, onReady: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* The app remains usable when notifications are declined. */ }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var stage by remember { mutableStateOf(AccessStage.Welcome) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+260 ") }
    var phoneTouched by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val normalizedPhone = remember(phone) { ZambiaPhoneNumber.normalize(phone) }
    val phoneError = if (phoneTouched && normalizedPhone == null) {
        "Enter a Zambian number, e.g. +260 971 793 185."
    } else {
        null
    }

    fun runAuth(operation: suspend () -> Result<Unit>, onSuccess: () -> Unit) {
        scope.launch {
            isLoading = true
            errorMessage = null
            operation().fold(
                onSuccess = { onSuccess() },
                // Repository failures may originate in HTTP/SDK exceptions. Never display
                // exception messages because they can include request details or headers.
                onFailure = { errorMessage = safeAuthFailureMessage(it.message) },
            )
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DhwSurface)
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Box(
            modifier = Modifier.background(DhwNavy, CircleShape).padding(22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("🏍", fontSize = 36.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DHW", color = DhwOrange, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("-inRide", color = DhwNavy, fontSize = 30.sp, fontWeight = FontWeight.Black)
        }
        StatusPill("${role.label} app", if (role == AppUserRole.Rider) DhwBlue else DhwOrange)

        when (stage) {
            AccessStage.Welcome -> {
                ScreenTitle(
                    title = if (role == AppUserRole.Rider) "Ride. Earn. Safe." else "Your boda, minutes away",
                    subtitle = if (role == AppUserRole.Rider) "Pilot riders are provisioned and verified by operations." else "Create an account or sign in to continue in Chongwe.",
                )
                DhwCard {
                    Text("Secure account", fontWeight = FontWeight.Black)
                    Text("Email verification and role-based access protect passenger, rider and trip information.", color = DhwMuted)
                }
                if (role == AppUserRole.Passenger) {
                    PrimaryButton("Create passenger account", { stage = AccessStage.SignUp })
                    SecondaryButton("I already have an account", { stage = AccessStage.SignIn })
                } else {
                    Text("Bring your NRC, Class A licence, motorcycle registration and insurance to operations for the in-person safety and device checks.", color = DhwMuted)
                    PrimaryButton("Sign in to rider account", { stage = AccessStage.SignIn })
                }
            }

            AccessStage.SignIn, AccessStage.SignUp -> {
                val signingUp = stage == AccessStage.SignUp
                ScreenTitle(
                    if (signingUp) "Create your account" else "Welcome back",
                    if (signingUp) "We will send a verification link to your email." else "Sign in to continue.",
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email address") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                )
                PrimaryButton(
                    text = if (isLoading) "Please wait…" else if (signingUp) "Create account" else "Sign in",
                    enabled = !isLoading && email.contains('@') && password.length >= 8 && (!signingUp || (fullName.trim().length >= 2 && normalizedPhone != null)),
                    onClick = {
                        if (signingUp) {
                            runAuth(
                                operation = { authRepository.signUp(email.trim(), password, fullName.trim(), normalizedPhone.orEmpty(), role.name.lowercase()) },
                                onSuccess = { stage = AccessStage.VerifyEmail },
                            )
                        } else {
                            runAuth(
                                operation = { authRepository.signIn(email.trim(), password, role.name.lowercase()) },
                                onSuccess = {
                                    requestNotificationPermissionIfNeeded()
                                    onReady()
                                },
                            )
                        }
                    },
                )
                if (signingUp) {
                    OutlinedTextField(value = fullName, onValueChange = { fullName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true)
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it; phoneTouched = true },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Phone number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        isError = phoneError != null,
                        supportingText = { phoneError?.let { Text(it, color = Color(0xFFB42318)) } },
                    )
                }
                errorMessage?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
                Text(
                    text = if (signingUp) "Already registered? Sign in" else "New to DHW-inRide? Create account",
                    color = DhwBlue,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        stage = if (signingUp) AccessStage.SignIn else AccessStage.SignUp
                    },
                )
            }

            AccessStage.VerifyEmail -> {
                ScreenTitle("Verify your email", "Open the verification link sent to:")
                Surface(color = DhwOrange.copy(alpha = .12f), shape = RoundedCornerShape(18.dp)) {
                    Text(email, color = DhwOrangeDark, fontWeight = FontWeight.Bold, modifier = Modifier.padding(18.dp))
                }
                DhwCard {
                    Text("Why verify?", fontWeight = FontWeight.Black)
                    Text("Verification helps prevent fake accounts and protects trip history.", color = DhwMuted)
                }
                PrimaryButton(
                    if (isLoading) "Checking…" else "I have verified my email",
                    enabled = !isLoading,
                    onClick = {
                        runAuth(
                            operation = { authRepository.signIn(email.trim(), password, role.name.lowercase()) },
                            onSuccess = {
                                if (role == AppUserRole.Rider) stage = AccessStage.RiderReview else {
                                    requestNotificationPermissionIfNeeded()
                                    onReady()
                                }
                            },
                        )
                    },
                )
                errorMessage?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
                SecondaryButton("Use a different email", { stage = AccessStage.SignUp })
            }

            AccessStage.Profile -> {
                ScreenTitle("Complete your profile", "Use details that match your official information.")
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Full name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it; phoneTouched = true },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Phone number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    isError = phoneError != null,
                    supportingText = { phoneError?.let { Text(it, color = Color(0xFFB42318)) } },
                )
                DhwCard {
                    InfoRow("Account role", role.label)
                    Text(
                        if (role == AppUserRole.Rider) {
                            "Rider access requires motorcycle and identity-document approval."
                        } else {
                            "Passenger accounts can request and review only their own trips."
                        },
                        color = DhwMuted,
                        fontSize = 12.sp,
                    )
                }
                PrimaryButton(
                    if (isLoading) "Saving…" else "Save and continue",
                    enabled = fullName.trim().length >= 2 && normalizedPhone != null,
                    onClick = {
                        scope.launch {
                            val canonicalPhone = normalizedPhone ?: return@launch
                            isLoading = true
                            errorMessage = null
                            runCatching { authRepository.updateProfile(fullName.trim(), canonicalPhone) }
                                .onSuccess {
                                    if (role == AppUserRole.Rider) stage = AccessStage.RiderReview else {
                                        requestNotificationPermissionIfNeeded()
                                        onReady()
                                    }
                                }
                                .onFailure { errorMessage = it.message ?: "Unable to save your profile." }
                            isLoading = false
                        }
                    },
                )
                errorMessage?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
            }

            AccessStage.RiderReview -> {
                ScreenTitle("Rider verification", "Only approved motorbike riders can receive trip requests.")
                listOf(
                    "National Registration Card" to "Required",
                    "Rider licence" to "Required",
                    "Motorcycle registration" to "Required",
                ).forEach { (item, status) ->
                    DhwCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item, fontWeight = FontWeight.Bold)
                            Text(status, color = DhwOrange, fontSize = 12.sp)
                        }
                    }
                }
                Text("Document upload and administrator approval must be completed before this account can receive trips.", color = DhwMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Only forwards known, sanitized repository messages to the screen. */
private fun safeAuthFailureMessage(message: String?): String {
    val normalized = message.orEmpty().lowercase()
    return when {
        ("permission denied" in normalized && "is_admin" in normalized) || "42501" in normalized ->
            "Supabase blocked profile access. The app's profile permissions need to be fixed."

        "invalid login credentials" in normalized || "invalid_grant" in normalized ->
            "Email or password is incorrect. Check both and try again."

        "email not confirmed" in normalized || "email_not_confirmed" in normalized ->
            "Verify your email using the latest confirmation link, then sign in."

        message in setOf(
            "An account with this email already exists. Please sign in.",
            "Password is too short. Use at least 8 characters.",
            "Password must be at least 6 characters.",
            "Please enter a valid email address.",
            "An account with this phone number already exists.",
            "Enter a valid Zambian phone number, for example +260 971 793 185.",
            "Enter a Zambian number, e.g. +260 971 793 185.",
            "We couldn't save your account details. Check your name and phone number, then try again.",
            "We couldn't reach the signup service. Check your connection and try again.",
            "The signup service took too long to respond. Try again.",
            "A secure connection to the signup service couldn't be established. Check your device date and time, then try again.",
            "The signup service couldn't be reached. Try again shortly.",
            "The signup request couldn't be completed. Check your connection and try again.",
            "Too many signup attempts. Wait a moment and try again.",
            "The signup service is temporarily unavailable. Try again shortly.",
            "Check your email, password, and phone number, then try again.",
            "Signup failed. Please try again.",
            "Unable to complete the request. Check your details and connection, then try again.",
        ) -> message.orEmpty()

        else -> "We couldn't sign in. Check your email, password, and confirmation status, then try again."
    }
}
