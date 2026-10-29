package zm.co.dhwinride.core.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import zm.co.dhwinride.core.domain.PilotPolicy

@Composable
fun PilotSupport() {
    val context = LocalContext.current
    fun open(action: String, uri: String) {
        runCatching { context.startActivity(Intent(action, Uri.parse(uri))) }
            .onFailure { Toast.makeText(context, "No compatible app is installed. Use the contact details shown here.", Toast.LENGTH_LONG).show() }
    }
    DhwCard {
        ScreenTitle("Pilot support", "Mon–Sat, 06:00–20:00 CAT")
        Text("Primary / WhatsApp: ${PilotPolicy.SUPPORT_PRIMARY}")
        Text("Secondary: ${PilotPolicy.SUPPORT_SECONDARY}")
        Text(PilotPolicy.SUPPORT_EMAIL)
        SecondaryButton("Call primary support", { open(Intent.ACTION_DIAL, "tel:${PilotPolicy.SUPPORT_PRIMARY}") })
        SecondaryButton("Call secondary support", { open(Intent.ACTION_DIAL, "tel:${PilotPolicy.SUPPORT_SECONDARY}") })
        SecondaryButton("Email pilot support", { open(Intent.ACTION_SENDTO, "mailto:${PilotPolicy.SUPPORT_EMAIL}") })
        Text("In immediate danger, use your phone’s emergency services. Pilot support is not an emergency dispatch service.")
    }
}
