package dev.shivampingale.vcport

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.io.File

data class VaultEntry(
    val name: String,
    val isDir: Boolean,
    val size: Long
)

class MainActivity : AppCompatActivity() {
    private val pathState = mutableStateOf("")
    private val containerUriState = mutableStateOf<Uri?>(null)
    private val statusState = mutableStateOf("Offline. Select a VeraCrypt container, or share an encrypted file as-is.")
    private val incomingState = mutableStateOf<File?>(null)
    private val passwordState = mutableStateOf("")
    private val wrapPasswordState = mutableStateOf("")
    private val generatedPasswordState = mutableStateOf("")
    private val handleState = mutableStateOf(0L)
    private val entriesState = mutableStateOf(listOf<VaultEntry>())
    private val dirPathState = mutableStateOf("")
    private var suppressLock = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Hardening.protectWindow(this)
        val vault = BiometricVault(this)
        handleIncoming(intent)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    var path by pathState
                    var containerUri by containerUriState
                    var password by passwordState
                    var pim by remember { mutableStateOf("0") }
                    var useTextPassword by remember { mutableStateOf(true) }
                    var useBiometric by remember { mutableStateOf(false) }
                    var rememberBio by remember { mutableStateOf(false) }
                    var bioSecret by remember { mutableStateOf<ByteArray?>(null) }
                    var keyfileUris by remember { mutableStateOf(listOf<Uri>()) }
                    var status by statusState
                    var entries by entriesState
                    var handle by handleState
                    var dirPath by dirPathState
                    var wrapPassword by wrapPasswordState
                    var generatedPassword by generatedPasswordState
                    val incoming by incomingState
                    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                        if (uri != null) {
                            ShareHelper.persistRead(this@MainActivity, uri)
                            containerUri = uri
                            path = copyToCache(uri)
                            status = "Container: ${ShareHelper.displayName(this@MainActivity, uri) ?: path}"
                        }
                    }
                    val shareEncPicker = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenMultipleDocuments()
                    ) { uris: List<Uri> ->
                        if (uris.isNotEmpty()) {
                            uris.forEach { ShareHelper.persistRead(this@MainActivity, it) }
                            status = "Sharing ${uris.size} encrypted file(s) as-is."
                            suppressLock = true
                            ShareHelper.shareUris(this@MainActivity, uris, "Share encrypted file")
                        }
                    }
                    val keyfilePicker = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenMultipleDocuments()
                    ) { uris: List<Uri> ->
                        uris.forEach { ShareHelper.persistRead(this@MainActivity, it) }
                        keyfileUris = (keyfileUris + uris).distinct()
                    }
                    val importBioPicker = rememberLauncherForActivityResult(
                        ActivityResultContracts.OpenDocument()
                    ) { uri: Uri? ->
                        if (uri != null) {
                            ShareHelper.persistRead(this@MainActivity, uri)
                            val bytes = KeyfileIo.readLimited(this@MainActivity, uri)
                            if (bytes == null || bytes.isEmpty()) {
                                status = "Could not import keyfile (empty or larger than 1 MiB)."
                            } else {
                                bioSecret = bytes
                                useBiometric = true
                                status = "Imported ${bytes.size} bytes as the biometric password (VeraCrypt keyfile)."
                            }
                        }
                    }
                    val wrapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                        if (uri != null) wrapSelectedFile(uri, wrapPassword) { status = it }
                    }
                    val unwrapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                        if (uri != null) unwrapSelectedFile(uri, wrapPassword) { status = it }
                    }
                    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                        Text("VC Port", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            if (BuildConfig.ENABLE_UPDATE_CHECK)
                                "VeraCrypt-compatible Android client. Offline until you check for updates."
                            else
                                "VeraCrypt-compatible Android client. F-Droid build: no network."
                        )
                        Text(
                            "Stay offline by default. High-threat: screenshots blocked, recents hidden, no backups, no user CAs. Wrap a file, share ciphertext as-is, or panic wipe. Biometrics can be compelled — prefer a long password + keyfile, not Remember. This is not unbreakable.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(onClick = {
                            panicWipe()
                            password = ""
                            wrapPassword = ""
                            generatedPassword = ""
                            handle = 0
                            entries = emptyList()
                            dirPath = ""
                            status = "Panic wipe complete. Keystore, cache, clipboard, and remembered factors are gone."
                        }) { Text("Panic wipe") }
                        Spacer(Modifier.height(12.dp))
                        incoming?.let { file ->
                            Text("Received ${file.name} from another app.")
                            Row {
                                OutlinedButton(onClick = {
                                    path = copyIncomingAsContainer(file)
                                    status = "Using ${file.name} as container."
                                }) { Text("Open as container") }
                                Spacer(Modifier.padding(8.dp))
                                OutlinedButton(onClick = {
                                    beginShare()
                                    ShareHelper.shareFiles(this@MainActivity, listOf(file), "Share encrypted file")
                                }) { Text("Share encrypted") }
                                if (ShareHelper.looksLikeWrap(file.name)) {
                                    Spacer(Modifier.padding(8.dp))
                                    OutlinedButton(onClick = {
                                        unwrapIncomingFile(file, wrapPassword) { status = it }
                                    }) { Text("Decrypt wrap") }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Button(onClick = {
                            shareEncPicker.launch(arrayOf("*/*"))
                        }) { Text("Share encrypted file") }
                        Text(
                            "Sends .hc / .tc / .vera as-is. No password, no decrypt. WhatsApp, Gmail, Drive, and the rest of the share sheet.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Wrap a single file", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Encrypt one file with a password. The result is a .vcpw wrap you can share. Unwrap it later in this app.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            wrapPassword,
                            { wrapPassword = it },
                            label = { Text("Wrap password (never stored)") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrect = false,
                                keyboardType = KeyboardType.Password
                            )
                        )
                        Row {
                            OutlinedButton(onClick = {
                                val generated = NativeBridge.generatePassword(24)
                                if (generated != null) {
                                    wrapPassword = generated
                                    generatedPassword = generated
                                    status = "Generated a 24-character password in memory. It is not saved."
                                } else {
                                    status = "Password generator failed."
                                }
                            }) { Text("Generate strong password") }
                            Spacer(Modifier.padding(8.dp))
                            OutlinedButton(onClick = {
                                if (wrapPassword.isNotEmpty()) {
                                    SensitiveClipboard.copyOnce(this@MainActivity, wrapPassword)
                                    status = "Copied once. Clipboard clears in 30 seconds. No history is kept."
                                }
                            }) { Text("Copy once") }
                        }
                        Row {
                            OutlinedButton(onClick = {
                                SensitiveClipboard.forget(this@MainActivity)
                                wrapPassword = ""
                                generatedPassword = ""
                                status = "Password forgotten. Clipboard cleared."
                            }) { Text("Forget password") }
                            Spacer(Modifier.padding(8.dp))
                            OutlinedButton(onClick = { wrapPicker.launch(arrayOf("*/*")) }) { Text("Encrypt file") }
                            Spacer(Modifier.padding(8.dp))
                            OutlinedButton(onClick = { unwrapPicker.launch(arrayOf("*/*")) }) { Text("Decrypt wrap") }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (BuildConfig.ENABLE_UPDATE_CHECK) {
                            Button(onClick = {
                                status = "Checking for updates (one HTTPS request)..."
                                Thread {
                                    try {
                                        val result = UpdateChecker.check()
                                        runOnUiThread {
                                            status = if (result.newer)
                                                "Update ${result.remoteVersion} available. ${result.notes} Offline again."
                                            else
                                                "Already up to date (${UpdateChecker.LOCAL_VERSION}). Offline again."
                                        }
                                    } catch (e: Exception) {
                                        runOnUiThread { status = "Update check failed: ${e.message}. Offline again." }
                                    }
                                }.start()
                            }) { Text("Check for updates") }
                        }
                        Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Choose container") }
                        OutlinedTextField(path, { path = it }, label = { Text("Container path") }, modifier = Modifier.fillMaxWidth())
                        Text("Unlock factors", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Combine any of: biometric password, text password, keyfiles, and PIM. Same mix VeraCrypt uses on a computer.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(useTextPassword, { useTextPassword = it })
                            Text("Text password")
                        }
                        if (useTextPassword) {
                            OutlinedTextField(
                                password,
                                { password = it },
                                label = { Text("Password") },
                                modifier = Modifier.fillMaxWidth(),
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrect = false,
                                    keyboardType = KeyboardType.Password
                                )
                            )
                        }
                        OutlinedTextField(
                            pim,
                            { pim = it },
                            label = { Text("PIM (0 = default)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Keyfiles", style = MaterialTheme.typography.titleSmall)
                        keyfileUris.forEach { uri ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    ShareHelper.displayName(this@MainActivity, uri) ?: uri.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(onClick = { keyfileUris = keyfileUris.filterNot { it == uri } }) {
                                    Text("Remove")
                                }
                            }
                        }
                        OutlinedButton(onClick = { keyfilePicker.launch(arrayOf("*/*")) }) { Text("Add keyfiles") }
                        if (vault.isAvailable()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(useBiometric, {
                                    useBiometric = it
                                    if (!it) bioSecret = null
                                })
                                Text("Biometric as password")
                            }
                            Text(
                                "Do not use biometrics as the only factor in a danger-state. Fingerprints can be compelled. Mix a password and a keyfile you do not keep on this phone.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Stored in the Android Keystore (StrongBox when present). Mixed as a VeraCrypt keyfile.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                bioSecret?.let { "Biometric password ready (${it.size} bytes)." }
                                    ?: if (path.isNotEmpty() && vault.hasFactors(path))
                                        "A saved factor set exists. Unlock with biometrics to load it."
                                    else
                                        "Create a random biometric password, or import a keyfile you already use.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row {
                                OutlinedButton(onClick = {
                                    if (path.isEmpty()) {
                                        status = "Choose a container first."
                                    } else {
                                        val secret = FactorCodec.randomBiometricKey()
                                        bioSecret = secret
                                        useBiometric = true
                                        vault.store(
                                            this@MainActivity,
                                            path,
                                            FactorBundle(
                                                pim = pim.toIntOrNull() ?: 0,
                                                password = if (useTextPassword) password else "",
                                                biometricKey = secret,
                                                keyfileUris = keyfileUris.map { it.toString() }
                                            )
                                        ) { ok ->
                                            status = if (ok)
                                                "Created a 64-byte biometric password. Export it and add that file as a keyfile when you create the volume."
                                            else
                                                "Could not save the biometric password."
                                        }
                                    }
                                }) { Text("Create") }
                                Spacer(Modifier.padding(8.dp))
                                OutlinedButton(onClick = { importBioPicker.launch(arrayOf("*/*")) }) { Text("Import keyfile") }
                            }
                            Row {
                                OutlinedButton(onClick = {
                                    val secret = bioSecret
                                    if (secret == null) {
                                        status = "Create or import a biometric password first."
                                    } else {
                                        val file = KeyfileIo.writeSecret(this@MainActivity, secret)
                                        beginShare()
                                        ShareHelper.shareFiles(this@MainActivity, listOf(file), "Export biometric keyfile")
                                        status = "Share this keyfile into VeraCrypt on a computer (Add keyfile). Delete it after."
                                    }
                                }) { Text("Export keyfile") }
                                Spacer(Modifier.padding(8.dp))
                                OutlinedButton(onClick = {
                                    if (path.isEmpty()) {
                                        status = "Choose a container first."
                                    } else {
                                        vault.load(this@MainActivity, path) { stored ->
                                            if (stored == null) {
                                                status = "Biometric unlock cancelled."
                                            } else {
                                                password = stored.password
                                                pim = stored.pim.toString()
                                                useTextPassword = stored.password.isNotEmpty()
                                                useBiometric = stored.hasBiometric()
                                                bioSecret = stored.biometricKey
                                                keyfileUris = stored.keyfileUris.mapNotNull { Uri.parse(it) }
                                                status = "Loaded factors with biometrics. Add or remove anything, then Open volume."
                                            }
                                        }
                                    }
                                }) { Text("Unlock with biometrics") }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(rememberBio, { rememberBio = it })
                                Text("Remember this combination")
                            }
                            if (path.isNotEmpty() && vault.hasFactors(path)) {
                                OutlinedButton(onClick = {
                                    vault.clear(path)
                                    status = "Forgot saved factors for this container."
                                }) { Text("Forget saved factors") }
                            }
                        }
                        Button(onClick = {
                            openVolumeWithFactors(
                                vault = vault,
                                path = path,
                                password = password,
                                pimText = pim,
                                useTextPassword = useTextPassword,
                                useBiometric = useBiometric,
                                bioSecret = bioSecret,
                                keyfileUris = keyfileUris,
                                rememberBio = rememberBio,
                                currentHandle = handle,
                                onHandle = { handle = it },
                                onEntries = { entries = it },
                                onStatus = { status = it }
                            )
                        }) { Text("Open volume") }
                        if (containerUri != null || path.isNotEmpty()) {
                            OutlinedButton(onClick = {
                                shareEncryptedVolume(containerUri, path) { status = it }
                            }) { Text("Share this encrypted file") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(status)
                        Spacer(Modifier.height(16.dp))
                        Text("About / licenses", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Portions of this product are based in part on TrueCrypt, freely available at http://www.truecrypt.org/",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "VC Port original code is Apache License 2.0. The volume core is VeraCrypt (Apache 2.0 / TrueCrypt License 3.0). You may not call this app VeraCrypt. Not unbreakable.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "Contact: Shivam Mangesh Pingale — shivampingaledev@proton.me · shivampingaledev@gmail.com",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            if (BuildConfig.ENABLE_UPDATE_CHECK)
                                "No ads, analytics, or crash reporters. Passwords stay on this device. GitHub flavor may make one HTTPS request if you tap Check for updates."
                            else
                                "No ads, analytics, crash reporters, or INTERNET permission. Passwords stay on this device. Updates come from F-Droid.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (handle > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (dirPath.isNotEmpty()) {
                                    OutlinedButton(onClick = {
                                        dirPath = parentDir(dirPath)
                                        loadDir(handle, dirPath, { entries = it }, { status = it })
                                    }) { Text("Up") }
                                    Spacer(Modifier.padding(8.dp))
                                }
                                Text(
                                    if (dirPath.isEmpty()) "/" else "/$dirPath",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        entries.forEach { entry ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (entry.isDir) {
                                            dirPath = joinDir(dirPath, entry.name)
                                            loadDir(handle, dirPath, { entries = it }, { status = it })
                                        } else {
                                            shareVaultFile(handle, dirPath, entry) { status = it }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        if (entry.isDir) "Folder" else formatSize(entry.size),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (!entry.isDir) {
                                    OutlinedButton(onClick = {
                                        shareVaultFile(handle, dirPath, entry) { status = it }
                                    }) { Text("Share decrypted") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    override fun onStop() {
        super.onStop()
        if (suppressLock) {
            suppressLock = false
            return
        }
        lockSession()
    }

    private fun lockSession() {
        val handle = handleState.value
        if (handle > 0) NativeBridge.closeVolume(handle)
        handleState.value = 0L
        entriesState.value = emptyList()
        dirPathState.value = ""
        passwordState.value = ""
        wrapPasswordState.value = ""
        generatedPasswordState.value = ""
        Hardening.wipeSessionFiles(this)
        SensitiveClipboard.forget(this)
        if (!statusState.value.startsWith("Panic")) {
            statusState.value =
                "Locked. Passwords and plaintext cache wiped. Panic wipe also destroys Keystore and ciphertext copies."
        }
    }

    private fun panicWipe() {
        val handle = handleState.value
        if (handle > 0) NativeBridge.closeVolume(handle)
        handleState.value = 0L
        Hardening.panic(this)
    }

    private fun openVolumeWithFactors(
        vault: BiometricVault,
        path: String,
        password: String,
        pimText: String,
        useTextPassword: Boolean,
        useBiometric: Boolean,
        bioSecret: ByteArray?,
        keyfileUris: List<Uri>,
        rememberBio: Boolean,
        currentHandle: Long,
        onHandle: (Long) -> Unit,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit
    ) {
        if (path.isEmpty()) {
            onStatus("Choose a container first.")
            return
        }
        val text = if (useTextPassword) password else ""
        if (text.isEmpty() && !useBiometric && keyfileUris.isEmpty()) {
            onStatus("Choose at least one factor: text password, biometric password, or a keyfile.")
            return
        }
        if (useBiometric && (bioSecret == null || bioSecret.isEmpty())) {
            onStatus("Create or import a biometric password, or tap Unlock with biometrics to load a saved one.")
            return
        }
        onStatus("Opening…")
        Thread {
            val temps = mutableListOf<File>()
            try {
                if (useBiometric && bioSecret != null) {
                    temps.add(KeyfileIo.writeSecret(this, bioSecret))
                }
                for (uri in keyfileUris) {
                    val copied = KeyfileIo.copyUri(this, uri)
                    if (copied == null) {
                        runOnUiThread { onStatus("Could not read a keyfile.") }
                        return@Thread
                    }
                    temps.add(copied)
                }
                if (currentHandle > 0) NativeBridge.closeVolume(currentHandle)
                val result = NativeBridge.openVolume(
                    path,
                    text,
                    pimText.toIntOrNull() ?: 0,
                    false,
                    temps.map { it.absolutePath }.toTypedArray()
                )
                runOnUiThread {
                    if (result <= 0) {
                        onHandle(0)
                        onEntries(emptyList())
                        onStatus(openErrorMessage(result))
                    } else {
                        onHandle(result)
                        val listed = NativeBridge.listDir(result, "/")
                        val parsed = listed.mapNotNull { parseEntry(it) }
                        if (parsed.size == 1 && parsed[0].name == "!error!") {
                            NativeBridge.closeVolume(result)
                            onHandle(0)
                            onEntries(emptyList())
                            onStatus(listErrorMessage(parsed[0].size.toInt()))
                        } else {
                            onEntries(parsed)
                            dirPathState.value = ""
                            onStatus("Opened. Size ${NativeBridge.volumeSize(result)} bytes. Tap a folder to open it, or a file to share the decrypted copy.")
                            if (rememberBio && vault.isAvailable()) {
                                vault.store(
                                    this,
                                    path,
                                    FactorBundle(
                                        pim = pimText.toIntOrNull() ?: 0,
                                        password = text,
                                        biometricKey = if (useBiometric) bioSecret else null,
                                        keyfileUris = keyfileUris.map { it.toString() }
                                    )
                                ) {}
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { onStatus("Open failed.") }
            } finally {
                temps.forEach { KeyfileIo.wipe(it) }
            }
        }.start()
    }

    private fun handleIncoming(intent: Intent?) {
        if (intent == null) return
        val uris = mutableListOf<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris.add(it) }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris.addAll(it) }
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { uris.add(it) }
            }
        }
        if (uris.isEmpty()) return
        val copied = uris.mapNotNull { uri ->
            ShareHelper.copyIncoming(this, uri, null)
        }
        if (copied.isEmpty()) {
            statusState.value = "Could not read the shared file."
            return
        }
        incomingState.value = copied.first()
        val first = copied.first()
        if (uris.isNotEmpty()) {
            ShareHelper.persistRead(this, uris.first())
            containerUriState.value = uris.first()
        }
        if (ShareHelper.looksLikeWrap(first.name) || NativeBridge.isWrap(first.absolutePath)) {
            statusState.value = "Received wrapped file ${first.name}. Enter the wrap password and tap Decrypt wrap."
        } else if (ShareHelper.looksLikeContainer(first.name)) {
            pathState.value = copyIncomingAsContainer(first)
            statusState.value = "Received encrypted file ${first.name}. Share it as-is or open it."
        } else {
            statusState.value = "Received ${copied.joinToString { it.name }}. Wrap it, share it, or open as a container."
        }
    }

    private fun unwrapIncomingFile(file: File, password: String, onStatus: (String) -> Unit) {
        if (password.isEmpty()) {
            onStatus("Enter the wrap password first. It is not stored.")
            return
        }
        onStatus("Unwrapping file…")
        Thread {
            val destDir = File(cacheDir, "unwrapped").apply { mkdirs() }
            val outPath = NativeBridge.unwrapFile(file.absolutePath, destDir.absolutePath, password)
            runOnUiThread {
                if (outPath == null) {
                    onStatus("Unwrap failed. Wrong password or not a VC Port wrap.")
                } else {
                    val plain = File(outPath)
                    onStatus("Unwrapped ${plain.name}. Password was not saved.")
                    beginShare()
                    ShareHelper.shareFiles(this, listOf(plain), "Share unwrapped file")
                }
            }
        }.start()
    }

    private fun wrapSelectedFile(uri: Uri, password: String, onStatus: (String) -> Unit) {
        if (password.length < 16) {
            onStatus("Use Generate strong password, or type at least 16 characters. Nothing is saved.")
            return
        }
        onStatus("Wrapping file…")
        Thread {
            val name = ShareHelper.displayName(this, uri) ?: "file.bin"
            val plain = File(cacheDir, "wrap-in-${ShareHelper.safeName(name)}")
            val wrapped = File(ShareHelper.shareDir(this), ShareHelper.safeName(name) + ".vcpw")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    plain.outputStream().use { output -> input.copyTo(output) }
                } ?: run {
                    runOnUiThread { onStatus("Could not read the file.") }
                    return@Thread
                }
                val rc = NativeBridge.wrapFile(plain.absolutePath, wrapped.absolutePath, password, name)
                KeyfileIo.wipe(plain)
                runOnUiThread {
                    if (rc != 0 || !wrapped.exists()) {
                        onStatus("Wrap failed (code $rc).")
                    } else {
                        onStatus("Wrapped $name. Share the .vcpw file. The password was not saved.")
                        beginShare()
                        ShareHelper.shareFiles(this, listOf(wrapped), "Share wrapped file")
                    }
                }
            } catch (e: Exception) {
                KeyfileIo.wipe(plain)
                runOnUiThread { onStatus("Wrap failed.") }
            }
        }.start()
    }

    private fun unwrapSelectedFile(uri: Uri, password: String, onStatus: (String) -> Unit) {
        if (password.isEmpty()) {
            onStatus("Enter the wrap password first. It is not stored.")
            return
        }
        onStatus("Unwrapping file…")
        Thread {
            val name = ShareHelper.displayName(this, uri) ?: "wrap.vcpw"
            val wrapped = File(cacheDir, ShareHelper.safeName(name))
            val destDir = File(cacheDir, "unwrapped").apply { mkdirs() }
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    wrapped.outputStream().use { output -> input.copyTo(output) }
                } ?: run {
                    runOnUiThread { onStatus("Could not read the wrap.") }
                    return@Thread
                }
                val outPath = NativeBridge.unwrapFile(wrapped.absolutePath, destDir.absolutePath, password)
                wrapped.delete()
                runOnUiThread {
                    if (outPath == null) {
                        onStatus("Unwrap failed. Wrong password or not a VC Port wrap.")
                    } else {
                        val file = File(outPath)
                        onStatus("Unwrapped ${file.name}. Password was not saved.")
                        beginShare()
                        ShareHelper.shareFiles(this, listOf(file), "Share unwrapped file")
                    }
                }
            } catch (e: Exception) {
                wrapped.delete()
                runOnUiThread { onStatus("Unwrap failed.") }
            }
        }.start()
    }

    private fun beginShare() {
        suppressLock = true
    }

    private fun shareEncryptedVolume(uri: Uri?, path: String, onStatus: (String) -> Unit) {
        beginShare()
        when {
            uri != null -> {
                onStatus("Sharing encrypted file as-is.")
                ShareHelper.shareUris(this, listOf(uri), "Share encrypted file")
            }
            path.isNotEmpty() && File(path).exists() -> {
                onStatus("Sharing encrypted file as-is.")
                ShareHelper.shareFiles(this, listOf(File(path)), "Share encrypted file")
            }
            else -> onStatus("Choose an encrypted file first.")
        }
    }

    private fun shareVaultFile(handle: Long, dirPath: String, entry: VaultEntry, onStatus: (String) -> Unit) {
        if (handle <= 0) {
            onStatus("Open a volume first.")
            return
        }
        beginShare()
        if (entry.isDir) {
            onStatus("Open the folder, then share a file inside it.")
            return
        }
        val volumePath = joinDir(dirPath, entry.name)
        onStatus("Preparing ${entry.name}…")
        Thread {
            val dest = File(ShareHelper.shareDir(this), ShareHelper.safeName(entry.name))
            val rc = NativeBridge.exportFile(handle, volumePath, dest.absolutePath)
            runOnUiThread {
                if (rc != 0 || !dest.exists()) {
                    onStatus(extractErrorMessage(entry.name, rc))
                } else {
                    onStatus("Share ${entry.name} with WhatsApp, Gmail, Drive, or any app.")
                    ShareHelper.shareFiles(this, listOf(dest), "Share ${entry.name}")
                }
            }
        }.start()
    }

    private fun loadDir(handle: Long, path: String, onEntries: (List<VaultEntry>) -> Unit, onStatus: (String) -> Unit) {
        if (handle <= 0) return
        val listed = NativeBridge.listDir(handle, if (path.isEmpty()) "/" else path)
        val parsed = listed.mapNotNull { parseEntry(it) }
        if (parsed.size == 1 && parsed[0].name == "!error!") {
            onStatus(listErrorMessage(parsed[0].size.toInt()))
            return
        }
        onEntries(parsed)
    }

    private fun joinDir(dir: String, name: String): String {
        return if (dir.isEmpty()) name else "$dir/$name"
    }

    private fun parentDir(dir: String): String {
        val slash = dir.lastIndexOf('/')
        return if (slash <= 0) "" else dir.substring(0, slash)
    }

    private fun openErrorMessage(code: Long): String {
        return when (code.toInt()) {
            -2 -> "Wrong password, PIM, or keyfile mix."
            -6 -> "This container uses exFAT or another filesystem VC Port does not open. FAT only."
            -1 -> "Could not read the container file."
            -3 -> "Not a VeraCrypt-compatible volume, or the header is damaged."
            -4 -> "Missing path or password argument."
            else -> "Open failed (code $code)."
        }
    }

    private fun listErrorMessage(code: Int): String {
        return when (code) {
            -6 -> "Opened the volume, but the filesystem is exFAT or otherwise unsupported. FAT only."
            else -> "Could not list files (code $code)."
        }
    }

    private fun extractErrorMessage(name: String, rc: Int): String {
        return when (rc) {
            -6 -> "Could not extract $name. FAT only; exFAT is unsupported."
            else -> "Could not extract $name (code $rc)."
        }
    }

    private fun parseEntry(line: String): VaultEntry? {
        val parts = line.split('\t')
        if (parts.isEmpty() || parts[0].isEmpty()) return null
        val isDir = parts.getOrNull(1) == "1"
        val size = parts.getOrNull(2)?.toLongOrNull() ?: 0L
        return VaultEntry(parts[0], isDir, size)
    }

    private fun formatSize(size: Long): String {
        if (size < 1024) return "$size B"
        if (size < 1024 * 1024) return "${size / 1024} KB"
        return "${size / (1024 * 1024)} MB"
    }

    private fun copyIncomingAsContainer(file: File): String {
        val name = if (ShareHelper.looksLikeContainer(file.name)) {
            ShareHelper.safeName(file.name)
        } else {
            "container.hc"
        }
        val outFile = File(cacheDir, name)
        file.copyTo(outFile, overwrite = true)
        return outFile.absolutePath
    }

    private fun copyToCache(uri: Uri): String {
        val display = ShareHelper.displayName(this, uri) ?: "container.hc"
        val name = if (ShareHelper.looksLikeContainer(display)) {
            ShareHelper.safeName(display)
        } else {
            ShareHelper.safeName(display.substringBeforeLast('.') + ".hc")
        }
        val input = contentResolver.openInputStream(uri) ?: return ""
        val outFile = File(cacheDir, name)
        outFile.outputStream().use { output -> input.copyTo(output) }
        input.close()
        return outFile.absolutePath
    }
}
