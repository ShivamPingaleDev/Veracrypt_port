package dev.shivampingale.vcport

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
internal fun MainActivity.CreateVolumePane(
    busy: Boolean,
    onPickBasket: () -> Unit,
    onPickKeyfiles: () -> Unit,
    onPickHiddenKeyfiles: () -> Unit,
    onSaveGeneratedKeyfile: (File, String) -> Unit,
    onSaveCreatedVolume: (String) -> Unit,
) {
    var path by pathState
    var containerLabel by containerLabelState
    var status by statusState
    var basketUris by basketUrisState
    var basketHashes by basketHashesState
    var hiddenKeyfileUris by hiddenKeyfileUrisState
    var keyfileUris by keyfileUrisState
    var keyfileGenName by keyfileGenNameState
    var keyfileGenCount by keyfileGenCountState
    var createCipher by createCipherState
    var createKdf by createKdfState
    var createSizeAmount by createSizeAmountState
    var createSizeUnit by createSizeUnitState
    var createFilesystem by createFilesystemState
    var createHiddenSizeAmount by createHiddenSizeAmountState
    var createHiddenSizeUnit by createHiddenSizeUnitState
    var createPassword by createPasswordState
    var createPim by createPimState
    var createHidden by createHiddenState
    var createHiddenPassword by createHiddenPasswordState
    var createHiddenPim by createHiddenPimState
    var createFileName by createFileNameState
    var entropyPercent by entropyPercentState
    val colors = MaterialTheme.colorScheme
    VcCard {

        Text("File basket", style = MaterialTheme.typography.titleMedium)
        VcHint("Copied into the new volume. Originals stay. SHA-256 is session-only; BASKET.sha256 is written inside.")
        if (basketUris.isEmpty()) {

            Text("No files in the basket.", style = MaterialTheme.typography.bodySmall)
        } else {

            Text(
                basketSummary(
                    basketUris,
                    if (createHidden) {

                        SizeUnits.toBytes(
                            createHiddenSizeAmount.toLongOrNull() ?: 0L,
                            createHiddenSizeUnit
                        )
                    } else {

                        0L
                    }

                ),
                style = MaterialTheme.typography.bodySmall
            )
            basketUris.forEach { uri ->
                val hex = basketHashes[uri.toString()]
                Row(verticalAlignment = Alignment.CenterVertically) {

                    Column(Modifier.weight(1f)) {

                        Text(
                            ShareHelper.displayName(this@CreateVolumePane, uri) ?: uri.toString(),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            hex?.let { "SHA-256 ${BasketHash.shortHex(it)}" } ?: "SHA-256 …",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }

                    TextButton(onClick = {

                        basketUris = basketUris.filterNot { it == uri }

                        basketHashes = basketHashes - uri.toString()
                    }) { Text("Remove") }

                }

            }

        }

        OutlinedButton(
            onClick = {

                holdLockForPicker()
                onPickBasket()
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add files to basket") }

        if (basketUris.isNotEmpty()) {

            OutlinedButton(
                onClick = {

                    basketUris = emptyList()
                    basketHashes = emptyMap()
                    status = "Basket emptied. Files on the phone were not deleted."
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Empty basket") }

        }

    }

    VcCard {

        Text("Encryption Options", style = MaterialTheme.typography.titleMedium)
        VcHint("Opening ignores the extension. Opening uses whichever password you type — there is no open-time hidden checkbox.")
        OptionDropdown(
            "Encryption Algorithm",
            NativeBridge.CIPHERS,
            createCipher,
            { createCipher = it },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("create_cipher")
        )
        OptionDropdown(
            "KDF",
            NativeBridge.KDFS,
            createKdf,
            { createKdf = it },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("create_kdf")
        )
        OptionDropdown(
            "Inside the volume",
            listOf("FAT", "exFAT"),
            createFilesystem,
            { createFilesystem = it },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("create_filesystem")
        )
        VcHint("exFAT if a file is over 4 GiB.")
        OutlinedTextField(
            createFileName,
            { createFileName = it.filterNot { ch -> ch == '/' || ch == '\\' }.take(120) },
            label = { Text("File name (any extension)") },
            modifier = Modifier.fillMaxWidth().testTag("create_filename"),
            enabled = !busy,
            singleLine = true
        )
        VcHint("The name is only a disguise — volume.hc, photo.jpg, image.png, model.safetensors, adapter.lora.")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            OutlinedTextField(
                createSizeAmount,
                { createSizeAmount = it.filter { ch -> ch.isDigit() }.take(6) },
                label = { Text("Size") },
                modifier = Modifier.weight(1f).testTag("create_size"),
                enabled = !busy,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            SizeUnitPicker(
                createSizeUnit,
                { createSizeUnit = it },
                enabled = !busy
            )
        }

        VcHint("2 MiB–64 GiB.")
        SecretField(
            createPassword,
            { createPassword = it },
            "Volume password (never stored)",
            modifier = Modifier.testTag("create_password"),
            enabled = !busy
        )
        Text(PasswordEntropy.label(createPassword), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            createPim,
            { createPim = it },
            label = { Text("PIM (0 = default)") },
            modifier = Modifier.fillMaxWidth().testTag("create_pim"),
            enabled = !busy,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        FilledTonalButton(
            onClick = {

                val generated = NativeBridge.generatePassword(64)
                if (generated != null) {

                    createPassword = generated
                    status = PasswordEntropy.label(generated) + " Generated a 64-character password in memory. Copy once if you need it elsewhere. It is not saved."
                }

            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Generate strong password") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

            OutlinedButton(
                onClick = {
                    if (createPassword.isNotEmpty()) {
                        status = "Copied once. Clipboard clears in 30 seconds. No history is kept."
                        val text = createPassword
                        window.decorView.post {
                            SensitiveClipboard.copyOnce(this@CreateVolumePane, text)
                        }
                    }
                },
                modifier = Modifier.weight(1f).testTag("copy_once")
            ) { Text("Copy once") }

            OutlinedButton(
                onClick = {

                    SensitiveClipboard.forget(this@CreateVolumePane)
                    createPassword = ""
                    status = "Password forgotten. Clipboard cleared."
                },
                modifier = Modifier.weight(1f)
            ) { Text("Forget password") }

        }

        Text("Keyfiles", style = MaterialTheme.typography.titleSmall)
        VcHint("Pick several in Files (long-press). Any extension. VeraCrypt mixes the first 1 MiB of each. Generate more below.")
        if (keyfileUris.isEmpty()) {

            Text("No keyfiles in this session.", style = MaterialTheme.typography.bodySmall)
        }

        keyfileUris.forEach { uri ->
            Row(verticalAlignment = Alignment.CenterVertically) {

                Text(
                    ShareHelper.displayName(this@CreateVolumePane, uri) ?: uri.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { keyfileUris = keyfileUris.filterNot { it == uri } }) {

                    Text("Remove")
                }

            }

        }

        OutlinedButton(
            onClick = {

                holdLockForPicker()
                onPickKeyfiles()
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Add keyfiles") }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            OutlinedTextField(
                keyfileGenName,
                { keyfileGenName = it.take(120) },
                label = { Text("Keyfile name (any extension)") },
                modifier = Modifier.weight(1f).testTag("create_keyfile_name"),
                enabled = !busy,
                singleLine = true
            )
            OutlinedTextField(
                keyfileGenCount,
                { keyfileGenCount = it.filter { ch -> ch.isDigit() }.take(1) },
                label = { Text("How many") },
                modifier = Modifier.width(96.dp).testTag("create_keyfile_count"),
                enabled = !busy,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

        OutlinedButton(
            onClick = {

                val files = generateSessionKeyfiles(keyfileGenCount, keyfileGenName, nested = false)
                if (files.isEmpty()) {

                    status = "Keyfile generator failed."
                } else if (testingSkipSystemPickers) {

                    status = "Generated and added ${files.first().name}. Save a copy."
                } else {

                    offerGeneratedKeyfileCopies(files, { status = it }) { name ->
                        onSaveGeneratedKeyfile(files.first(), name)
                    }

                }

            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("create_generate_keyfile")
        ) { Text("Generate keyfile and add") }

        EntropyPad(
            percent = entropyPercent,
            enabled = !busy,
            onSample = { sample ->
                NativeBridge.addEntropy(sample)
                entropyPercent = NativeBridge.entropyPercent()
            }

        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("create_hidden")
                .toggleable(
                    value = createHidden,
                    enabled = !busy,
                    role = Role.Checkbox,
                    onValueChange = { createHidden = it }

                )
        ) {

            Checkbox(
                createHidden,
                onCheckedChange = null,
                enabled = !busy
            )
            Text("Nested volume (VeraCrypt hidden volume)")
        }

        if (createHidden) {

            Text(
                "Same cipher and KDF. Different password. Do not fill the outer volume.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
            SecretField(
                createHiddenPassword,
                { createHiddenPassword = it },
                "Nested volume password",
                modifier = Modifier.testTag("create_hidden_password"),
                enabled = !busy
            )
            Text(PasswordEntropy.label(createHiddenPassword), style = MaterialTheme.typography.bodySmall)
            FilledTonalButton(
                onClick = {

                    val generated = NativeBridge.generatePassword(64)
                    if (generated != null) {

                        createHiddenPassword = generated
                        status = PasswordEntropy.label(generated) + " Nested password generated in memory. Copy once if you need it elsewhere. It is not saved."
                    }

                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Generate nested password") }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                OutlinedButton(
                    onClick = {
                        if (createHiddenPassword.isNotEmpty()) {
                            status = "Copied nested password once. Clipboard clears in 30 seconds. No history is kept."
                            val text = createHiddenPassword
                            window.decorView.post {
                                SensitiveClipboard.copyOnce(this@CreateVolumePane, text)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).testTag("copy_nested_once")
                ) { Text("Copy nested once") }

                OutlinedButton(
                    onClick = {

                        SensitiveClipboard.forget(this@CreateVolumePane)
                        createHiddenPassword = ""
                        status = "Nested password forgotten. Clipboard cleared."
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("Forget nested") }

            }

            OutlinedTextField(
                createHiddenPim,
                { createHiddenPim = it },
                label = { Text("Nested PIM (0 = default)") },
                modifier = Modifier.fillMaxWidth().testTag("create_hidden_pim"),
                enabled = !busy,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                OutlinedTextField(
                    createHiddenSizeAmount,
                    { createHiddenSizeAmount = it.filter { ch -> ch.isDigit() }.take(6) },
                    label = { Text("Nested size") },
                    modifier = Modifier.weight(1f).testTag("create_hidden_size"),
                    enabled = !busy,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                SizeUnitPicker(
                    createHiddenSizeUnit,
                    { createHiddenSizeUnit = it },
                    enabled = !busy
                )
            }

            Text("Nested keyfiles", style = MaterialTheme.typography.titleSmall)
            hiddenKeyfileUris.forEach { uri ->
                Row(verticalAlignment = Alignment.CenterVertically) {

                    Text(
                        ShareHelper.displayName(this@CreateVolumePane, uri) ?: uri.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { hiddenKeyfileUris = hiddenKeyfileUris.filterNot { it == uri } }) {

                        Text("Remove")
                    }

                }

            }

            OutlinedButton(
                onClick = {

                    holdLockForPicker()
                    onPickHiddenKeyfiles()
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add nested keyfiles") }

            OutlinedButton(
                onClick = {

                    val files = generateSessionKeyfiles(keyfileGenCount, keyfileGenName, nested = true)
                    if (files.isEmpty()) {

                        status = "Nested keyfile generator failed."
                    } else if (testingSkipSystemPickers) {

                        status = "Generated nested keyfile ${files.first().name}. Save a copy."
                    } else {

                        offerGeneratedKeyfileCopies(files, { status = it }) { name ->
                            onSaveGeneratedKeyfile(files.first(), name)
                        }

                    }

                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("create_generate_nested_keyfile")
            ) { Text("Generate nested keyfile and add") }

        }

        Button(
            onClick = {

                createContainer(
                    password = createPassword,
                    pimText = createPim,
                    sizeBytes = SizeUnits.toBytes(
                        createSizeAmount.toLongOrNull() ?: 0L,
                        createSizeUnit
                    ),
                    cipher = createCipher,
                    kdf = createKdf,
                    keyfileUris = keyfileUris,
                    hidden = createHidden,
                    hiddenPassword = createHiddenPassword,
                    hiddenPimText = createHiddenPim,
                    hiddenSizeBytes = SizeUnits.toBytes(
                        createHiddenSizeAmount.toLongOrNull() ?: 0L,
                        createHiddenSizeUnit
                    ),
                    hiddenKeyfileUris = hiddenKeyfileUris,
                    fileName = createFileName,
                    filesystem = createFilesystem,
                    entropyPercent = entropyPercent,
                    basketUris = basketUris,
                    onPath = {

                        path = it
                        containerLabel = File(it).name
                    },
                    onStatus = { status = it },
                    onSaved = {

                        NativeBridge.resetEntropy()
                        entropyPercent = 0
                        if (!testingSkipSystemPickers) {

                            holdLockForPicker()
                            window.decorView.post {

                                holdLockForPicker()
                                onSaveCreatedVolume(ShareHelper.sanitizeDisguiseName(createFileName))
                            }

                        }

                    }

                )
            },
            enabled = !busy && entropyPercent >= 100,
            modifier = Modifier.fillMaxWidth().testTag("create_volume")
        ) { Text("Create volume") }

    }

}
