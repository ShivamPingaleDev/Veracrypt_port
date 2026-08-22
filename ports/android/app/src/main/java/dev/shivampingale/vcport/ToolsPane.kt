package dev.shivampingale.vcport

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
internal fun MainActivity.ToolsPane(
    busy: Boolean,
    path: String,
    password: String,
    pim: String,
    useTextPassword: Boolean,
    keyfileUris: List<Uri>,
    useBackupHeader: Boolean,
    headerKdf: String,
    onHeaderKdf: (String) -> Unit,
    handle: Long,
    onHandle: (Long) -> Unit,
    onEntries: (List<VaultEntry>) -> Unit,
    skin: VcSkin,
    onSkin: (VcSkin) -> Unit,
    onPickRestoreHeader: () -> Unit,
    onPickKeyfiles: () -> Unit,
    onSaveGeneratedKeyfile: (File, String) -> Unit,
) {
    var status by statusState
    var newPassword by newPasswordState
    var newPim by newPimState
    var keyfileGenName by keyfileGenNameState
    var keyfileGenCount by keyfileGenCountState
    var pimEstimateResult by pimEstimateResultState
    var createKdf by createKdfState
    var createPim by createPimState
    val colors = MaterialTheme.colorScheme
    VcCard {

        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        VcHint("Original is the VeraCrypt-like look. Dark mode is a dark theme. Pick is stored on this phone only.")
        VcSkin.entries.forEach { option ->
            val selected = skin == option
            if (selected) {

                Button(
                    onClick = {

                        onSkin(option)
                        saveSkin(option)
                    },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(option.tag),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    )
                ) { Text(if (selected) "●  ${option.picker}" else option.picker) }

            } else {

                OutlinedButton(
                    onClick = {

                        onSkin(option)
                        saveSkin(option)
                    },
                    enabled = !busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(option.tag)
                ) { Text(if (selected) "●  ${option.picker}" else option.picker) }

            }

        }

    }

    VcCard {

        Text("Volume header", style = MaterialTheme.typography.titleMedium)
        SecretField(
            newPassword,
            { newPassword = it },
            "New password (empty = keep current)",
            modifier = Modifier.testTag("tools_new_password"),
            enabled = !busy
        )
        OutlinedTextField(
            newPim,
            { newPim = it },
            label = { Text("New PIM (0 = VeraCrypt default)") },
            modifier = Modifier.fillMaxWidth().testTag("tools_new_pim"),
            enabled = !busy,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Button(
            onClick = {

                runChangeHeader(
                    path = path,
                    password = password,
                    pimText = pim,
                    useTextPassword = useTextPassword,
                    keyfileUris = keyfileUris,
                    useBackupHeader = useBackupHeader,
                    newPassword = newPassword,
                    newPimText = newPim,
                    newKdf = "",
                    keepKeyfiles = true,
                    onHandle = { onHandle(it) },
                    onEntries = { onEntries(it) },
                    onStatus = { status = it },
                    successMessage = "Changed volume password. Open with the new password and the same keyfiles."
                )
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("tools_change_password")
        ) { Text("Change volume password") }

        OptionDropdown(
            "Header KDF",
            listOf("(keep current)") + NativeBridge.KDFS,
            headerKdf,
            { onHeaderKdf(it) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("tools_header_kdf")
        )
        OutlinedButton(
            onClick = {

                val kdf = if (headerKdf == "(keep current)") "" else headerKdf
                if (kdf.isEmpty()) {

                    status = "Pick a KDF other than keep current."
                } else {

                    runChangeHeader(
                        path = path,
                        password = password,
                        pimText = pim,
                        useTextPassword = useTextPassword,
                                keyfileUris = keyfileUris,
                        useBackupHeader = useBackupHeader,
                        newPassword = "",
                        newPimText = newPim,
                        newKdf = kdf,
                        keepKeyfiles = true,
                        onHandle = { onHandle(it) },
                        onEntries = { onEntries(it) },
                        onStatus = { status = it },
                        successMessage = "Set header key derivation algorithm to $kdf."
                    )
                }

            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("tools_set_kdf")
        ) { Text("Set header key derivation algorithm") }

        OutlinedButton(
            onClick = {

                runChangeHeader(
                    path = path,
                    password = password,
                    pimText = pim,
                    useTextPassword = useTextPassword,
                    keyfileUris = keyfileUris,
                    useBackupHeader = useBackupHeader,
                    newPassword = "",
                    newPimText = newPim,
                    newKdf = "",
                    keepKeyfiles = true,
                    applySessionKeyfiles = true,
                    onHandle = { onHandle(it) },
                    onEntries = { onEntries(it) },
                    onStatus = { status = it },
                    successMessage = "Applied the current keyfile list (Add/Remove keyfiles) to the volume header."
                )
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("tools_apply_keyfiles")
        ) { Text("Add/Remove keyfiles to/from volume") }

        OutlinedButton(
            onClick = {

                runChangeHeader(
                    path = path,
                    password = password,
                    pimText = pim,
                    useTextPassword = useTextPassword,
                    keyfileUris = keyfileUris,
                    useBackupHeader = useBackupHeader,
                    newPassword = "",
                    newPimText = newPim,
                    newKdf = "",
                    keepKeyfiles = false,
                    onHandle = { onHandle(it) },
                    onEntries = { onEntries(it) },
                    onStatus = { status = it },
                    successMessage = "Removed all keyfiles from volume. Open with the password only."
                )
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("tools_remove_all_keyfiles")
        ) { Text("Remove all keyfiles from volume") }

        HorizontalDivider()
        Button(
            onClick = {

                backupVolumeHeader(
                    volumePath = path,
                    password = password,
                    pimText = pim,
                    useTextPassword = useTextPassword,
                    keyfileUris = keyfileUris,
                    onHandle = { onHandle(it) },
                    onEntries = { onEntries(it) },
                    onStatus = { status = it },
                    onSaved = { file ->
                        if (!testingSkipSystemPickers) onSaveGeneratedKeyfile(file, "volume-header.bak")
                    }

                )
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("tools_backup_header")
        ) { Text("Backup volume header") }

        OutlinedButton(
            onClick = {

                holdLockForPicker()
                onPickRestoreHeader()
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Restore volume header") }

        OutlinedButton(
            onClick = {

                restoreEmbeddedHeader(
                    volumePath = path,
                    password = password,
                    pimText = pim,
                    useTextPassword = useTextPassword,
                    keyfileUris = keyfileUris,
                    onHandle = { onHandle(it) },
                    onEntries = { onEntries(it) },
                    onStatus = { status = it }

                )
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("tools_restore_embedded")
        ) { Text("Restore from embedded backup header") }

        OutlinedButton(
            onClick = {

                if (!NativeBridge.isOpen(handle)) {

                    status = "Open the volume first for Volume properties."
                } else {

                    status = NativeBridge.volumeInfo(handle)
                        ?: "Could not read volume properties."
                }

            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("tools_volume_properties")
        ) { Text("Volume properties") }

    }

    VcCard {

        Text("Keyfile generator", style = MaterialTheme.typography.titleMedium)
        VcHint("Any extension. Generate several, then Add keyfiles if they are not already in this session.")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            OutlinedTextField(
                keyfileGenName,
                { keyfileGenName = it.take(120) },
                label = { Text("Keyfile name (any extension)") },
                modifier = Modifier.weight(1f).testTag("tools_keyfile_name"),
                enabled = !busy,
                singleLine = true
            )
            OutlinedTextField(
                keyfileGenCount,
                { keyfileGenCount = it.filter { ch -> ch.isDigit() }.take(1) },
                label = { Text("How many") },
                modifier = Modifier.width(96.dp),
                enabled = !busy,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        Button(
            onClick = {

                val files = generateSessionKeyfiles(keyfileGenCount, keyfileGenName, nested = false)
                if (files.isEmpty()) {

                    if (!status.startsWith("Already in this session")) {
                        status = "Keyfile generator failed."
                    }
                } else if (testingSkipSystemPickers) {

                    status = "Generated and added ${files.first().name}. Save a copy."
                } else {

                    offerGeneratedKeyfileCopies(files, { status = it }) { name ->
                        onSaveGeneratedKeyfile(files.first(), name)
                    }

                }

            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("tools_generate_keyfile")
        ) { Text("Keyfile generator") }

    }

    VcCard {

        Text("Benchmark / test vectors", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = {

                beginWork("Running encryption benchmark…")
                Thread {

                    val result = NativeBridge.benchmark() ?: "Benchmark failed."
                    runOnUiThread {

                        endWork()
                        status = result
                    }

                }.start()
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Benchmark") }

        OutlinedButton(
            onClick = {

                beginWork("Running known-answer test vectors…")
                Thread {

                    val rc = NativeBridge.testVectors()
                    runOnUiThread {

                        endWork()
                        status = if (rc == 0)
                            "Test vectors passed. AES, Serpent, Twofish, Camellia, Kuznyechik, and XTS match the VeraCrypt known-answer tests."
                        else
                            "Test vectors failed."
                    }

                }.start()
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Test vectors") }

        VcHint(PimEstimator.describe(createKdf, createPim))
        if (pimEstimateResult.isNotEmpty()) {

            Text(
                pimEstimateResult,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("pim_estimate_result")
            )
        }

        OutlinedButton(
            onClick = {

                beginWork("Estimating header iterations…")
                Thread {

                    val text = PimEstimator.describe(
                        createKdfState.value,
                        createPimState.value
                    )
                    NativeBridge.setProgress(100, text)
                    runOnUiThread {

                        pimEstimateResult = text
                        status = text
                        endWork()
                    }

                }.start()
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("tools_pim_estimate")
        ) { Text("PIM iteration estimate") }

    }

    VcCard {

        Text("Wipe cached passwords", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(
            onClick = {

                closeOpenVolumes("Wipe cached passwords complete. Volume closed.")
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Wipe cached passwords") }

    }

    VcCard {

        Text(
            "“We must defend our own privacy if we expect to have any.” — Eric Hughes, A Cypherpunk’s Manifesto (1993)",
            style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
            color = colors.onSurfaceVariant
        )
        Text(
            "Shivam Mangesh Pingale — shivampingaledev@proton.me · shivampingaledev@gmail.com",
            style = MaterialTheme.typography.bodySmall
        )
        VcHint("https://github.com/ShivamPingaleDev/Veracrypt_port")
        VcHint("Portions of this product are based in part on TrueCrypt, freely available at http://www.truecrypt.org/")
        VcHint("Apache-2.0 / TrueCrypt License 3.0. Not named VeraCrypt. The app does not install itself.")
        VcHint(SourcePin.describeBuild())
    }

}
