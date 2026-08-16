package dev.shivampingale.vcport

import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vault = BiometricVault(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    var path by remember { mutableStateOf("") }
                    var password by remember { mutableStateOf("") }
                    var pim by remember { mutableStateOf("0") }
                    var rememberBio by remember { mutableStateOf(false) }
                    var status by remember { mutableStateOf("Select a VeraCrypt container.") }
                    var entries by remember { mutableStateOf(listOf<String>()) }
                    var handle by remember { mutableStateOf(0L) }
                    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                        if (uri != null) {
                            path = copyToCache(uri)
                            status = "Container: $path"
                        }
                    }
                    Column(Modifier.padding(16.dp)) {
                        Text("VC Port", style = MaterialTheme.typography.headlineMedium)
                        Text("VeraCrypt-compatible Android client with biometric unlock.")
                        Spacer(Modifier.height(12.dp))
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
                            androidx.compose.foundation.layout.Row {
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
                                status = "Opened. Size ${NativeBridge.volumeSize(handle)} bytes."
                                entries = NativeBridge.listRoot(handle).toList()
                                if (rememberBio) {
                                    vault.store(this@MainActivity, path, password, pim.toIntOrNull() ?: 0) {}
                                }
                            }
                        }) { Text("Open volume") }
                        Spacer(Modifier.height(8.dp))
                        Text(status)
                        LazyColumn {
                            items(entries) { Text(it) }
                        }
                    }
                }
            }
        }
    }

    private fun copyToCache(uri: Uri): String {
        val input = contentResolver.openInputStream(uri) ?: return ""
        val outFile = File(cacheDir, "container.hc")
        outFile.outputStream().use { output -> input.copyTo(output) }
        input.close()
        return outFile.absolutePath
    }
}
