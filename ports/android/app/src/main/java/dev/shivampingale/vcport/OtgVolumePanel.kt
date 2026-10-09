package dev.shivampingale.vcport

import android.hardware.usb.UsbDevice
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    promptOpen: Boolean,
    promptDevices: List<UsbDevice>,
    previewLabel: String,
    listedPreview: String,
    deviceHasPermission: (UsbDevice) -> Boolean,
    candidates: List<OtgCandidate>,
    shareWithFiles: Boolean,
    onShareWithFiles: (Boolean) -> Unit,
    onScan: () -> Unit,
    onDismissPrompt: () -> Unit,
    onChoosePreview: () -> Unit,
    onChoosePromptDevice: (UsbDevice) -> Unit,
    onPickDevice: (UsbDevice) -> Unit,
    onPickPartition: (OtgCandidate) -> Unit
) {
    Text("Whole USB disk (experimental)", style = MaterialTheme.typography.titleSmall)
    VcHint("See OTG Master. No auto-mount. Scan, pick a disk, then grant USB permission. Only allowed disks stay in USB devices.")
    Button(
        onClick = onScan,
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().testTag("scan_usb")
    ) { Text("Scan USB disks") }
    if (devices.isNotEmpty() || listedPreview.isNotEmpty()) {
        Text("USB devices", style = MaterialTheme.typography.titleSmall)
    }
    if (listedPreview.isNotEmpty()) {
        OutlinedButton(
            onClick = {},
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("usb_device")
        ) {
            Text(listedPreview)
        }
    }
    devices.forEach { device ->
        OutlinedButton(
            onClick = { onPickDevice(device) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("usb_device")
        ) {
            Text(OtgUsb.label(device))
        }
    }
    if (promptOpen) {
        AlertDialog(
            onDismissRequest = onDismissPrompt,
            title = { Text("Select USB device") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().testTag("usb_select_prompt")) {
                    Text("Choose one disk. VC Port asks for USB permission next. The disk shows up under USB devices only after you allow it. Nothing auto-mounts.")
                    if (previewLabel.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onChoosePreview,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().testTag("usb_prompt_device")
                        ) {
                            Text("$previewLabel — Needs permission")
                        }
                    }
                    promptDevices.forEach { device ->
                        val access = if (deviceHasPermission(device)) "Allowed" else "Needs permission"
                        OutlinedButton(
                            onClick = { onChoosePromptDevice(device) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().testTag("usb_prompt_device")
                        ) {
                            Text("${OtgUsb.label(device)} — $access")
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismissPrompt) { Text("Cancel") }
            }
        )
    }
    candidates.forEach { cand ->
        OutlinedButton(
            onClick = { onPickPartition(cand) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("otg_partition")
        ) { Text("${cand.label} (${SizeUnits.formatBytes(cand.byteLength)})") }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("files_app_share")
            .toggleable(
                value = shareWithFiles,
                enabled = !busy,
                role = Role.Checkbox,
                onValueChange = onShareWithFiles
            )
    ) {
        Checkbox(shareWithFiles, onCheckedChange = null, enabled = !busy)
        Text("Show unlocked volumes in the Files app (off until you tick this)")
    }
}
