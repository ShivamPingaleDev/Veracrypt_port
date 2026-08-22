package dev.shivampingale.vcport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

/**
 * One Open-volume form. Used on the Volume tab and in the Mounted Empty popup
 * so both hit [MainActivity.openVolumeWithFactors] with every option.
 */
@Composable
fun OpenVolumeForm(
    busy: Boolean,
    mountedSlot: Boolean,
    containerLabel: String,
    shownPath: String?,
    onChooseContainer: () -> Unit,
    onShareEncryptedPick: () -> Unit,
    onShareThis: (() -> Unit)?,
    otgSlot: @Composable () -> Unit,
    useTextPassword: Boolean,
    onUseTextPassword: (Boolean) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    pim: String,
    onPim: (String) -> Unit,
    keyfileLabels: List<String>,
    onRemoveKeyfile: (Int) -> Unit,
    onAddKeyfiles: () -> Unit,
    biometricSlot: @Composable () -> Unit,
    useBackupHeader: Boolean,
    onBackupHeader: (Boolean) -> Unit,
    readOnly: Boolean,
    onReadOnly: (Boolean) -> Unit,
    trueCryptMode: Boolean,
    onTrueCryptMode: (Boolean) -> Unit,
    protectHidden: Boolean,
    onProtectHidden: (Boolean) -> Unit,
    hiddenPassword: String,
    onHiddenPassword: (String) -> Unit,
    hiddenPim: String,
    onHiddenPim: (String) -> Unit,
    idleAmount: String,
    onIdleAmount: (String) -> Unit,
    idleUnit: SessionIdle.Unit,
    onIdleUnit: (SessionIdle.Unit) -> Unit,
    onOpen: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("open_volume_form"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        VcCard {
            Text("VeraCrypt-compatible. This build has no network.")
            VcHint("Stay offline. A compelled password still wins. Not unbreakable.")
            Button(
                onClick = onChooseContainer,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Choose container") }
            if (containerLabel.isNotEmpty()) {
                Text("Selected: $containerLabel", style = MaterialTheme.typography.bodyMedium)
                if (!shownPath.isNullOrEmpty()) {
                    Text(shownPath, style = MaterialTheme.typography.bodySmall)
                }
            }
            VcHint("USB/OTG file on a stick: Choose container. Whole-disk: Scan USB disks. See OTG Master. Nothing auto-mounts.")
            otgSlot()
            Button(
                onClick = onShareEncryptedPick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Spacer(Modifier.padding(6.dp))
                Text("Share encrypted file")
            }
            if (onShareThis != null) {
                OutlinedButton(
                    onClick = onShareThis,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Share this encrypted file") }
            }
        }
        VcCard {
            Text("Volume password", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(useTextPassword, onUseTextPassword, enabled = !busy)
                Text("Password")
            }
            if (useTextPassword) {
                SecretField(
                    password,
                    onPassword,
                    "Password",
                    modifier = Modifier.testTag("volume_password"),
                    enabled = !busy
                )
            }
            OutlinedTextField(
                pim,
                onPim,
                label = { Text("PIM (0 = default)") },
                modifier = Modifier.fillMaxWidth().testTag("volume_pim"),
                enabled = !busy,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Text("Keyfiles", style = MaterialTheme.typography.titleSmall)
            VcHint("Same as VeraCrypt on a computer: pick several in Files (long-press). Any extension. First 1 MiB of each.")
            keyfileLabels.forEachIndexed { index, label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onRemoveKeyfile(index) }) { Text("Remove") }
                }
            }
            OutlinedButton(
                onClick = onAddKeyfiles,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("add_keyfiles")
            ) { Text("Add keyfiles") }
            biometricSlot()
            Text("Mount options", style = MaterialTheme.typography.titleSmall)
            OptionRow("Use backup header", "use_backup_header", useBackupHeader, busy, onBackupHeader)
            OptionRow("Read-only", "read_only", readOnly, busy, onReadOnly)
            OptionRow("TrueCrypt Mode", "truecrypt_mode", trueCryptMode, busy, onTrueCryptMode)
            OptionRow(
                "Protect hidden volume against damage caused by writing to outer volume",
                "protect_hidden",
                protectHidden,
                busy,
                onProtectHidden
            )
            if (protectHidden) {
                SecretField(
                    hiddenPassword,
                    onHiddenPassword,
                    "Password to hidden volume",
                    modifier = Modifier.testTag("hidden_protect_password"),
                    enabled = !busy
                )
                OutlinedTextField(
                    hiddenPim,
                    onHiddenPim,
                    label = { Text("Hidden volume PIM (0 = default)") },
                    modifier = Modifier.fillMaxWidth().testTag("hidden_protect_pim"),
                    enabled = !busy,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Text("Idle dismount", style = MaterialTheme.typography.titleSmall)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    idleAmount,
                    { onIdleAmount(it.filter { ch -> ch.isDigit() }.take(4)) },
                    label = { Text("Idle") },
                    modifier = Modifier.weight(1f).testTag("idle_amount"),
                    enabled = !busy,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                IdleUnitPicker(
                    selected = idleUnit,
                    onSelect = onIdleUnit,
                    enabled = !busy
                )
            }
            VcHint("0 = Off. Home and screen lock already close. Idle is for walking away with the app still in front.")
            Button(
                onClick = onOpen,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("open_volume")
            ) { Text("Open volume") }
            if (onCancel != null) {
                TextButton(
                    onClick = onCancel,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("mounted_open_cancel")
                ) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    tag: String,
    value: Boolean,
    busy: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .toggleable(
                value = value,
                enabled = !busy,
                role = Role.Checkbox,
                onValueChange = onChange
            )
    ) {
        Checkbox(value, onCheckedChange = null, enabled = !busy)
        Text(label)
    }
}

@Composable
private fun IdleUnitPicker(
    selected: SessionIdle.Unit,
    onSelect: (SessionIdle.Unit) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .width(84.dp)
            .height(52.dp)
            .testTag("idle_unit"),
        contentAlignment = Alignment.Center
    ) {
        OutlinedButton(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(selected.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SessionIdle.Unit.entries.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit.label) },
                    onClick = {
                        onSelect(unit)
                        expanded = false
                    }
                )
            }
        }
    }
}
