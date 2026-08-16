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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vault = BiometricVault(this)
        handleIncoming(intent)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    var path by pathState
                    var containerUri by containerUriState
                    var password by remember { mutableStateOf("") }
                    var pim by remember { mutableStateOf("0") }
                    var rememberBio by remember { mutableStateOf(false) }
                    var status by statusState
                    var entries by remember { mutableStateOf(listOf<VaultEntry>()) }
                    var handle by remember { mutableStateOf(0L) }
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
                            ShareHelper.shareUris(this@MainActivity, uris, "Share encrypted file")
                        }
                    }
                    Column(Modifier.padding(16.dp)) {
                        Text("VC Port", style = MaterialTheme.typography.headlineMedium)
                        Text("VeraCrypt-compatible Android client. Offline until you check for updates.")
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
                                    ShareHelper.shareFiles(this@MainActivity, listOf(file), "Share encrypted file")
                                }) { Text("Share encrypted") }
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
                        Spacer(Modifier.height(8.dp))
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
                        Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Choose container") }
                        OutlinedTextField(path, { path = it }, label = { Text("Container path") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(pim, { pim = it }, label = { Text("PIM") }, modifier = Modifier.fillMaxWidth())
                        if (vault.isAvailable()) {
                            Button(onClick = {
                                if (path.isNotEmpty()) {
                                    vault.load(this@MainActivity, path) { stored ->
                                        if (stored != null) {
                                            password = stored.first
                                            pim = stored.second.toString()
                                            status = "Password loaded with biometrics."
                                        } else {
                                            status = "Biometric unlock cancelled."
                                        }
                                    }
                                }
                            }) { Text("Unlock with biometrics") }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(rememberBio, { rememberBio = it })
                                Text("Remember password with biometrics")
                            }
                        }
                        Button(onClick = {
                            if (handle > 0) NativeBridge.closeVolume(handle)
                            val result = NativeBridge.openVolume(path, password, pim.toIntOrNull() ?: 0, false)
                            if (result <= 0) {
                                handle = 0
                                status = "Open failed (code $result). Wrong password or unsupported format."
                                entries = emptyList()
                            } else {
                                handle = result
                                status = "Opened. Size ${NativeBridge.volumeSize(handle)} bytes. Tap a file to share the decrypted copy."
                                entries = NativeBridge.listRoot(handle).mapNotNull { parseEntry(it) }
                                if (rememberBio) {
                                    vault.store(this@MainActivity, path, password, pim.toIntOrNull() ?: 0) {}
                                }
                            }
                        }) { Text("Open volume") }
                        if (containerUri != null || path.isNotEmpty()) {
                            OutlinedButton(onClick = {
                                shareEncryptedVolume(containerUri, path) { status = it }
                            }) { Text("Share this encrypted file") }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(status)
                        LazyColumn {
                            items(entries, key = { it.name }) { entry ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !entry.isDir) {
                                            shareVaultFile(handle, entry) { status = it }
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
                                            shareVaultFile(handle, entry) { status = it }
                                        }) { Text("Share decrypted") }
                                    }
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
        if (ShareHelper.looksLikeContainer(first.name)) {
            pathState.value = copyIncomingAsContainer(first)
            statusState.value = "Received encrypted file ${first.name}. Share it as-is or open it."
        } else {
            statusState.value = "Received ${copied.joinToString { it.name }}. Share it onward or open as a container."
        }
    }

    private fun shareEncryptedVolume(uri: Uri?, path: String, onStatus: (String) -> Unit) {
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

    private fun shareVaultFile(handle: Long, entry: VaultEntry, onStatus: (String) -> Unit) {
        if (handle <= 0) {
            onStatus("Open a volume first.")
            return
        }
        if (entry.isDir) {
            onStatus("Folders cannot be shared yet.")
            return
        }
        onStatus("Preparing ${entry.name}…")
        Thread {
            val dest = File(ShareHelper.shareDir(this), ShareHelper.safeName(entry.name))
            val rc = NativeBridge.exportFile(handle, entry.name, dest.absolutePath)
            runOnUiThread {
                if (rc != 0 || !dest.exists()) {
                    onStatus("Could not extract ${entry.name} (code $rc). FAT/exFAT only.")
                } else {
                    onStatus("Share ${entry.name} with WhatsApp, Gmail, Drive, or any app.")
                    ShareHelper.shareFiles(this, listOf(dest), "Share ${entry.name}")
                }
            }
        }.start()
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
