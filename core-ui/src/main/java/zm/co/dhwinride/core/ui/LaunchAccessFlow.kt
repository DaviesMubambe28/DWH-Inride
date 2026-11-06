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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import zm.co.dhwinride.core.data.AuthRepository
import zm.co.dhwinride.core.data.RiderAccessState
import zm.co.dhwinride.core.data.RiderApplicationDetails
import zm.co.dhwinride.core.data.RIDER_DOCUMENT_MAX_BYTES

enum class AppUserRole(val label: String) {
    Passenger("Passenger"),
    Rider("Rider"),
}

private enum class AccessStage { RestoringSession, Welcome, SignIn, SignUp, Recovery, VerifyEmail, Profile, Notifications, RiderReview }

internal suspend fun restoreLaunchSession(
    authRepository: AuthRepository,
    role: AppUserRole,
): Result<Boolean> = authRepository.restoreSession(role.name.lowercase())

internal suspend fun signOutAppSession(authRepository: AuthRepository): Result<Unit> {
    return try {
        authRepository.signOut()
        Result.success(Unit)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}

internal fun readRiderDocument(stream: InputStream, maxBytes: Int = RIDER_DOCUMENT_MAX_BYTES): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val count = stream.read(buffer)
        if (count < 0) break
        total += count
        require(total <= maxBytes) { "Choose a document that is 5 MB or smaller." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal fun safeRiderDocumentFailureMessage(message: String?): String = when (message) {
    "Choose a document that is 5 MB or smaller.",
    "The selected document is empty.",
    "The selected document could not be read.",
    -> message
    else -> "The document could not be submitted. Check your connection and try again."
}

@Composable
fun LaunchAccessFlow(role: AppUserRole, authRepository: AuthRepository, onReady: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var stage by remember { mutableStateOf(AccessStage.RestoringSession) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { onReady() }

    fun continueToApp() {
        val needsNotificationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) stage = AccessStage.Notifications else onReady()
    }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("+260 ") }
    var phoneTouched by remember { mutableStateOf(false) }
    var motorcycleMake by remember { mutableStateOf("") }
    var motorcycleModel by remember { mutableStateOf("") }
    var motorcycleYear by remember { mutableStateOf("") }
    var motorcyclePlate by remember { mutableStateOf("") }
    var motorcycleColor by remember { mutableStateOf("") }
    var drivingLicenceNumber by remember { mutableStateOf("") }
    var riderSignupStep by remember { mutableStateOf(0) }
    var riderState by remember { mutableStateOf(RiderAccessState.NotSubmitted) }
    var submittedDocuments by remember { mutableStateOf(emptySet<String>()) }
    var pendingDocumentType by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val normalizedPhone = remember(phone) { ZambiaPhoneNumber.normalize(phone) }
    val phoneError = if (phoneTouched && normalizedPhone == null) {
        "Enter a Zambian number, e.g. +260 971 793 185."
    } else {
        null
    }
    val riderDocumentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val documentType = pendingDocumentType
        pendingDocumentType = null
        if (uri != null && documentType != null) {
            scope.launch {
                isLoading = true
                errorMessage = null
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { stream -> readRiderDocument(stream) }
                        ?: error("The selected document could not be read.")
                    authRepository.submitRiderDocument(documentType, uri.lastPathSegment ?: "document.jpg", bytes)
                    submittedDocuments = authRepository.getSubmittedRiderDocuments()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    errorMessage = safeRiderDocumentFailureMessage(error.message)
                }
                isLoading = false
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(authRepository, role) {
        restoreLaunchSession(authRepository, role).fold(
            onSuccess = { restored ->
                if (restored) {
                    // Do not interrupt every cold start after a previous "Not now" choice.
                    // The contextual prompt is shown after an interactive sign-in instead.
                    onReady()
                } else {
                    if (role == AppUserRole.Rider) {
                        riderState = runCatching { authRepository.getRiderAccessState() }.getOrDefault(RiderAccessState.NotSubmitted)
                        if (riderState != RiderAccessState.NotSubmitted) {
                            submittedDocuments = runCatching { authRepository.getSubmittedRiderDocuments() }.getOrDefault(emptySet())
                            stage = AccessStage.RiderReview
                        } else stage = AccessStage.Welcome
                    } else stage = AccessStage.Welcome
                }
            },
            onFailure = {
                errorMessage = "Your saved session could not be restored. Sign in again."
                stage = AccessStage.Welcome
            },
        )
    }

    fun runAuth(operation: suspend () -> Result<Unit>, onSuccess: () -> Unit) {
        if (isLoading) return
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                operation().fold(
                onSuccess = { onSuccess() },
                // Repository failures may originate in HTTP/SDK exceptions. Never display
                // exception messages because they can include request details or headers.
                    onFailure = { errorMessage = safeAuthFailureMessage(it.message) },
                )
            } finally {
                isLoading = false
            }
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
            modifier = Modifier.background(DhwNavy, CircleShape).padding(22.dp)
                .semantics { contentDescription = "DHW-inRide" },
            contentAlignment = Alignment.Center,
        ) {
            Text("🏍", fontSize = 36.sp, modifier = Modifier.clearAndSetSemantics { })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DHW", color = DhwOrange, fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("-inRide", color = DhwNavy, fontSize = 30.sp, fontWeight = FontWeight.Black)
        }
        StatusPill("${role.label} app", if (role == AppUserRole.Rider) DhwBlue else DhwOrange)

        when (stage) {
            AccessStage.RestoringSession -> {
                ScreenTitle("Restoring your session", "Checking whether you are already signed in.")
                LoadingState("Checking account access…")
            }

            AccessStage.Welcome -> {
                ScreenTitle(
                    title = if (role == AppUserRole.Rider) "Ride. Earn. Safe." else "Your boda, minutes away",
                    subtitle = if (role == AppUserRole.Rider) "Apply securely, submit verification documents, and wait for operations approval." else "Create an account or sign in to continue in Chongwe.",
                )
                DhwCard {
                    Text("Secure account", fontWeight = FontWeight.Black)
                    Text("Role-based access protects passenger, rider and trip information. Follow email confirmation instructions if your account requires them.", color = DhwMuted)
                }
                errorMessage?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
                if (role == AppUserRole.Passenger) {
                    PrimaryButton("Create passenger account", { riderSignupStep = 0; stage = AccessStage.SignUp })
                    SecondaryButton("I already have an account", { stage = AccessStage.SignIn })
                } else {
                    Text("Your application does not grant trip access. Bring your NRC, Class A licence, motorcycle registration and insurance to operations for final in-person checks.", color = DhwMuted)
                    PrimaryButton("Create Rider Account", { riderSignupStep = 0; stage = AccessStage.SignUp })
                    SecondaryButton("Sign in to rider account", { stage = AccessStage.SignIn })
                }
            }

            AccessStage.SignIn, AccessStage.SignUp -> {
                val signingUp = stage == AccessStage.SignUp
                val riderSignup = signingUp && role == AppUserRole.Rider
                val accountDetailsComplete = email.contains('@') && password.length >= 8 &&
                    fullName.trim().length >= 2 && normalizedPhone != null
                val riderDetailsComplete = motorcycleMake.isNotBlank() && motorcycleModel.isNotBlank() &&
                    motorcycleYear.toIntOrNull()?.let { it in 1950..2100 } == true &&
                    motorcyclePlate.trim().length >= 2 && motorcycleColor.isNotBlank() &&
                    drivingLicenceNumber.trim().length >= 3
                ScreenTitle(
                    when {
                        !signingUp -> "Welcome back"
                        riderSignup && riderSignupStep == 1 -> "Motorcycle details"
                        riderSignup && riderSignupStep == 2 -> "Review application"
                        else -> "Create your account"
                    },
                    when {
                        !signingUp -> "Sign in to continue."
                        riderSignup -> "Complete step ${riderSignupStep + 1} of 3. Operations will verify these details before activation."
                        else -> "Use your own email address. Follow confirmation instructions if your account requires them."
                    },
                )
                if (!riderSignup || riderSignupStep == 0) {
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
                        supportingText = { if (signingUp) Text("Use at least 8 characters") },
                    )
                }
                if (signingUp && (!riderSignup || riderSignupStep == 0)) {
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
                if (riderSignup && riderSignupStep == 1) {
                    OutlinedTextField(value = motorcycleMake, onValueChange = { motorcycleMake = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Motorcycle make") }, singleLine = true)
                    OutlinedTextField(value = motorcycleModel, onValueChange = { motorcycleModel = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Motorcycle model") }, singleLine = true)
                    OutlinedTextField(value = motorcycleYear, onValueChange = { motorcycleYear = it.filter(Char::isDigit).take(4) }, modifier = Modifier.fillMaxWidth(), label = { Text("Motorcycle year") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    OutlinedTextField(value = motorcyclePlate, onValueChange = { motorcyclePlate = it.uppercase() }, modifier = Modifier.fillMaxWidth(), label = { Text("Registration number") }, singleLine = true)
                    OutlinedTextField(value = motorcycleColor, onValueChange = { motorcycleColor = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Motorcycle colour") }, singleLine = true)
                    OutlinedTextField(value = drivingLicenceNumber, onValueChange = { drivingLicenceNumber = it.uppercase() }, modifier = Modifier.fillMaxWidth(), label = { Text("Driving licence number") }, singleLine = true)
                }
                if (riderSignup && riderSignupStep == 2) {
                    DhwCard {
                        Text("Account", fontWeight = FontWeight.Black)
                        InfoRow("Name", fullName.trim())
                        InfoRow("Phone", normalizedPhone.orEmpty())
                        InfoRow("Email", email.trim())
                    }
                    DhwCard {
                        Text("Motorcycle", fontWeight = FontWeight.Black)
                        InfoRow("Vehicle", "$motorcycleMake $motorcycleModel")
                        InfoRow("Year and colour", "$motorcycleYear • $motorcycleColor")
                        InfoRow("Registration", motorcyclePlate.trim())
                        InfoRow("Licence", drivingLicenceNumber.trim())
                    }
                    Text("After email verification, sign in to submit private NRC, licence and motorcycle-registration files. Operations must approve the application before trip access.", color = DhwMuted, fontSize = 12.sp)
                }

                when {
                    riderSignup && riderSignupStep < 2 -> {
                        PrimaryButton(
                            text = if (riderSignupStep == 0) "Continue to motorcycle" else "Review application",
                            enabled = !isLoading && if (riderSignupStep == 0) accountDetailsComplete else riderDetailsComplete,
                            onClick = { riderSignupStep += 1 },
                        )
                        if (riderSignupStep > 0) SecondaryButton("Back", { riderSignupStep -= 1 }, enabled = !isLoading)
                    }
                    else -> {
                        PrimaryButton(
                            text = if (isLoading) "Please wait…" else if (signingUp) {
                                if (riderSignup) "Submit rider application" else "Create account"
                            } else "Sign in",
                            enabled = !isLoading && email.contains('@') && password.length >= 8 &&
                                (!signingUp || (accountDetailsComplete && (!riderSignup || riderDetailsComplete))),
                            onClick = {
                                if (signingUp) {
                                    val operation: suspend () -> Result<Unit> = if (role == AppUserRole.Rider) ({
                                        authRepository.signUpRider(
                                            email.trim(), password, fullName.trim(), normalizedPhone.orEmpty(),
                                            RiderApplicationDetails(motorcycleMake, motorcycleModel, motorcycleYear.toIntOrNull() ?: 0, motorcyclePlate, motorcycleColor, drivingLicenceNumber),
                                        )
                                    }) else ({ authRepository.signUp(email.trim(), password, fullName.trim(), normalizedPhone.orEmpty(), "passenger") })
                                    runAuth(operation = operation, onSuccess = { stage = AccessStage.VerifyEmail })
                                } else {
                                    runAuth(
                                        operation = { authRepository.signIn(email.trim(), password, role.name.lowercase()) },
                                        onSuccess = {
                                            if (role == AppUserRole.Rider) scope.launch {
                                                riderState = authRepository.getRiderAccessState()
                                                if (riderState == RiderAccessState.Active) continueToApp() else {
                                                    submittedDocuments = runCatching { authRepository.getSubmittedRiderDocuments() }.getOrDefault(emptySet())
                                                    stage = AccessStage.RiderReview
                                                }
                                            } else continueToApp()
                                        },
                                    )
                                }
                            },
                        )
                        if (riderSignup) SecondaryButton("Edit motorcycle details", { riderSignupStep = 1 }, enabled = !isLoading)
                    }
                }
                if (!signingUp && authRepository.passwordRecovery != null) {
                    SecondaryButton("Forgot password?", { password = ""; stage = AccessStage.Recovery }, enabled = !isLoading)
                }
                errorMessage?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
                Text(
                        text = if (signingUp) "Already registered? Sign in" else if (role == AppUserRole.Rider) "New rider? Create Rider Account" else "New to DHW-inRide? Create account",
                        color = DhwBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = !isLoading) {
                            riderSignupStep = 0
                            stage = if (signingUp) AccessStage.SignIn else AccessStage.SignUp
                        },
                    )
            }

            AccessStage.Recovery -> {
                authRepository.passwordRecovery?.let { recovery ->
                    PasswordRecoveryForm(recovery) { stage = AccessStage.SignIn }
                }
            }

            AccessStage.VerifyEmail -> {
                ScreenTitle("Continue to sign in", "If confirmation is required, follow your email instructions first:")
                Surface(color = DhwOrange.copy(alpha = .12f), shape = RoundedCornerShape(18.dp)) {
                    Text(email, color = DhwOrangeDark, fontWeight = FontWeight.Bold, modifier = Modifier.padding(18.dp))
                }
                DhwCard {
                    Text("Account confirmation", fontWeight = FontWeight.Black)
                    Text("Some environments require email confirmation. Continue to sign in to check your account; no email delivery is assumed.", color = DhwMuted)
                }
                PrimaryButton(
                    if (isLoading) "Checking…" else "Continue to sign in",
                    enabled = !isLoading,
                    onClick = {
                        runAuth(
                            operation = { authRepository.signIn(email.trim(), password, role.name.lowercase()) },
                            onSuccess = {
                                if (role == AppUserRole.Rider) scope.launch {
                                    riderState = authRepository.getRiderAccessState()
                                    submittedDocuments = runCatching { authRepository.getSubmittedRiderDocuments() }.getOrDefault(emptySet())
                                    if (riderState == RiderAccessState.Active) continueToApp() else stage = AccessStage.RiderReview
                                } else continueToApp()
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
                                        continueToApp()
                                    }
                                }
                                .onFailure { errorMessage = it.message ?: "Unable to save your profile." }
                            isLoading = false
                        }
                    },
                )
                errorMessage?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
            }

            AccessStage.Notifications -> {
                ScreenTitle(
                    "Stay updated on your ride",
                    if (role == AppUserRole.Rider) {
                        "Notifications alert you to new requests and trip changes while the app is in the background."
                    } else {
                        "Notifications alert you when a rider accepts, arrives, or when your trip changes."
                    },
                )
                DhwCard {
                    Text("You stay in control", fontWeight = FontWeight.Black)
                    Text("You can continue without notifications and change this later in Android Settings.", color = DhwMuted)
                }
                PrimaryButton("Enable notifications", {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
                SecondaryButton("Not now", onReady)
            }

            AccessStage.RiderReview -> {
                ScreenTitle(
                    if (riderState == RiderAccessState.Rejected) "Application needs attention" else "Application under review",
                    if (riderState == RiderAccessState.Rejected) "Operations did not approve this application. Contact support before submitting a new application."
                    else "Only fully approved motorbike riders can receive trip requests.",
                )
                listOf(
                    "nrc" to "National Registration Card",
                    "license" to "Rider licence",
                    "vehicle_registration" to "Motorcycle registration",
                ).forEach { (type, item) ->
                    DhwCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item, fontWeight = FontWeight.Bold)
                            Text(if (type in submittedDocuments) "Submitted" else "Required", color = if (type in submittedDocuments) Color(0xFF18794E) else DhwOrange, fontSize = 12.sp)
                        }
                        if (type !in submittedDocuments && riderState != RiderAccessState.Rejected) SecondaryButton(
                            if (isLoading) "Uploading…" else "Choose file",
                            onClick = { pendingDocumentType = type; riderDocumentPicker.launch(arrayOf("image/jpeg", "image/png", "application/pdf")) },
                            enabled = !isLoading,
                        )
                    }
                }
                Text("Documents are stored privately. Administrator review, safety briefing and physical-device checks must be completed before this account can receive trips.", color = DhwMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
                errorMessage?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
                SecondaryButton("Sign out", onClick = {
                    scope.launch { runCatching { authRepository.signOut() }; stage = AccessStage.Welcome }
                })
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
            "Rider accounts must be provisioned by operations.",
            "Use an account registered for this app.",
            "An account with this email already exists. Please sign in.",
            "Password is too short. Use at least 8 characters.",
            "Password must be at least 6 characters.",
            "Please enter a valid email address.",
            "An account with this phone number already exists.",
            "Enter a valid Zambian phone number, for example +260 971 793 185.",
            "Enter a Zambian number, e.g. +260 971 793 185.",
            "Enter the motorcycle make.",
            "Enter the motorcycle model.",
            "Enter a valid motorcycle year.",
            "Enter the motorcycle registration number.",
            "Enter the motorcycle colour.",
            "Enter the driving licence number.",
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
