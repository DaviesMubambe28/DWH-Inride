package zm.co.dhwinride.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun PassengerCancellation(onCancel: () -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    SecondaryButton("Cancel ride", { confirming = true })
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("Cancel this ride?") },
        text = { Text("Cancellation within two minutes of acceptance is free. Later cancellations create a warning for manual review. No fee is collected during the pilot.") },
        confirmButton = { TextButton(onClick = { confirming = false; onCancel() }) { Text("Cancel ride") } },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Keep ride") } },
    )
}

@Composable
fun RiderCancellation(atPickup: Boolean, busy: Boolean, onCancel: (String) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf<String?>(null) }
    SecondaryButton(if (busy) "Confirming cancellation…" else "Cancel with reason", { choosing = true }, enabled = !busy)
    if (choosing) AlertDialog(
        onDismissRequest = { choosing = false },
        title = { Text("Choose cancellation reason") },
        text = { Column {
            if (atPickup) TextButton(onClick = { reason = "passenger_no_show"; choosing = false }) { Text("Passenger no-show · after 5 minutes") }
            TextButton(onClick = { reason = "motorcycle_problem"; choosing = false }) { Text("Motorcycle problem") }
            TextButton(onClick = { reason = "safety_concern"; choosing = false }) { Text("Safety concern") }
            Text("The server checks the waiting period. Other cancellations are sent to operations for manual review.")
        } },
        confirmButton = { TextButton(onClick = { choosing = false }) { Text("Keep ride") } },
    )
    reason?.let { selected -> AlertDialog(
        onDismissRequest = { reason = null }, title = { Text("Confirm cancellation?") },
        text = { Text("Cancel this trip with the selected reason? No fee will be collected.") },
        confirmButton = { TextButton(onClick = { reason = null; onCancel(selected) }) { Text("Cancel trip") } },
        dismissButton = { TextButton(onClick = { reason = null }) { Text("Keep ride") } },
    ) }
}
