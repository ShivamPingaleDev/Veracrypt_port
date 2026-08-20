package dev.shivampingale.vcport

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role

/**
 * Volume-tab USB disk UI. Isolated so master can drop this file and set
 * ENABLE_OTG_DISK=false without the rest of Open/Mounted changing.
 */
@Composable
fun OtgVolumePanel(
    busy: Boolean,
    devices: List<UsbDevice>,
    candidates: List<OtgCandidate>,
    shareWithFiles: Boolean,
    onShareWithFiles: (Boolean) -> Unit,
    onScan: () -> Unit,
    onPickDevice: (UsbDevice) -> Unit,
    onPickPartition: (OtgCandidate) -> Unit
) {
    Text("Whole USB disk (experimental)", style = MaterialTheme.typography.titleSmall)
    VcHint("See OTG Master. No auto-mount.")
    Button(
        onClick = onScan,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().testTag("scan_usb")
    ) { Text("Scan USB disks") }
    devices.forEach { device ->
        OutlinedButton(
            onClick = { onPickDevice(device) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(device.productName ?: "USB ${device.deviceId}")
        }
    }
    candidates.forEach { cand ->
        OutlinedButton(
            onClick = { onPickPartition(cand) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("${cand.label} (${SizeUnits.formatBytes(cand.byteLength)})") }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = shareWithFiles,
                enabled = !busy,
                role = Role.Checkbox,
                onValueChange = onShareWithFiles
            )
    ) {
        Checkbox(shareWithFiles, onCheckedChange = null, enabled = !busy)
        Text("Allow Files app to browse unlocked volumes (seizure leak; off by default)")
    }
}
