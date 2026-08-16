package dev.shivampingale.vcport

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.delay
import android.provider.OpenableColumns

data class VaultEntry(
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val dosDate: Int = 0,
    val dosTime: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : AppCompatActivity() {
    private val pathState = mutableStateOf("")
    private val containerUriState = mutableStateOf<Uri?>(null)
    private val statusState = mutableStateOf("Stay offline. Select a VeraCrypt container, or share an encrypted file as-is.")
    private val incomingState = mutableStateOf<File?>(null)
    private val passwordState = mutableStateOf("")
    private val wrapPasswordState = mutableStateOf("")
    private val generatedPasswordState = mutableStateOf("")
    private val handleState = mutableStateOf(0L)
    private val entriesState = mutableStateOf(listOf<VaultEntry>())
    private val dirPathState = mutableStateOf("")
    private val listTruncatedState = mutableStateOf(false)
    private val busyState = mutableStateOf(false)
    private val tabState = mutableIntStateOf(0)
    private val lastPlainFilesState = mutableStateOf(listOf<File>())
    private var suppressLock = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Hardening.protectWindow(this)
        val vault = BiometricVault(this)
        handleIncoming(intent)
        setContent {
            VcPortTheme {
                var path by pathState
                var containerUri by containerUriState
                var password by passwordState
                var pim by remember { mutableStateOf("0") }
                var useTextPassword by remember { mutableStateOf(true) }
                var useBiometric by remember { mutableStateOf(false) }
                var rememberBio by remember { mutableStateOf(false) }
                var rememberConfirmOpen by remember { mutableStateOf(false) }
                var rememberConfirmText by remember { mutableStateOf("") }
                var bioSecret by remember { mutableStateOf<ByteArray?>(null) }
                var keyfileUris by remember { mutableStateOf(listOf<Uri>()) }
                var status by statusState
                var entries by entriesState
                var handle by handleState
                var dirPath by dirPathState
                var listTruncated by listTruncatedState
                var wrapPassword by wrapPasswordState
                var generatedPassword by generatedPasswordState
                var busy by busyState
                var tab by tabState
                var moreFactors by remember { mutableStateOf(false) }
                var createCipher by remember { mutableStateOf(NativeBridge.DEFAULT_CIPHER) }
                var createKdf by remember { mutableStateOf(NativeBridge.DEFAULT_KDF) }
                var createSizeMb by remember { mutableStateOf("16") }
                var createPassword by remember { mutableStateOf("") }
                var createPim by remember { mutableStateOf("0") }
                var createHidden by remember { mutableStateOf(false) }
                var createHiddenPassword by remember { mutableStateOf("") }
                var createHiddenPim by remember { mutableStateOf("0") }
                var createHiddenSizeMb by remember { mutableStateOf("4") }
                var createFileName by remember { mutableStateOf("volume.hc") }
                var entropyPercent by remember { mutableIntStateOf(0) }
                var newPassword by remember { mutableStateOf("") }
                var newPim by remember { mutableStateOf("0") }
                var headerKdf by remember { mutableStateOf("(keep current)") }
                var useBackupHeader by remember { mutableStateOf(false) }
                var readOnlyOpen by remember { mutableStateOf(false) }
                var trueCryptMode by remember { mutableStateOf(false) }
                var namePrompt by remember { mutableStateOf<String?>(null) }
                var namePromptValue by remember { mutableStateOf("") }
                var pendingExportFile by remember { mutableStateOf<File?>(null) }
                var pendingSaveName by remember { mutableStateOf("") }
                var pendingSaveMove by remember { mutableStateOf(false) }
                var pendingFromDeviceMove by remember { mutableStateOf(false) }
                var selectedNames by remember { mutableStateOf(setOf<String>()) }
                var lastPlainFiles by lastPlainFilesState
                val incoming by incomingState
                val colors = MaterialTheme.colorScheme
                LaunchedEffect(dirPath, handle) {
                    selectedNames = emptySet()
                }
                var overlayTitle by remember { mutableStateOf("") }
                var overlayPercent by remember { mutableIntStateOf(-1) }
                LaunchedEffect(busy) {
                    if (!busy) {
                        overlayPercent = -1
                        overlayTitle = ""
                        return@LaunchedEffect
                    }
                    overlayTitle = status
                    overlayPercent = NativeBridge.progressPercent()
                    while (true) {
                        val phase = NativeBridge.progressPhase()
                        overlayTitle = phase.ifEmpty { status }
                        overlayPercent = NativeBridge.progressPercent()
                        delay(100)
                    }
                }
                LaunchedEffect(tab) {
                    if (tab == 3) {
                        NativeBridge.resetEntropy()
                        entropyPercent = 0
                    }
                    if (tab == 4) {
                        newPim = pim
                    }
                }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                    if (uri != null) {
                        ShareHelper.persistRead(this@MainActivity, uri)
                        containerUri = uri
                        copyContainerAsync(uri) { copied ->
                            path = copied
                            status = if (copied.isEmpty())
                                "Could not copy the container."
                            else
                                "Container: ${ShareHelper.displayName(this@MainActivity, uri) ?: File(copied).name}"
                        }
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
                val createSaver = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/octet-stream")
                ) { uri: Uri? ->
                    if (uri != null && path.isNotEmpty() && File(path).exists()) {
                        ShareHelper.persistRead(this@MainActivity, uri)
                        try {
                            contentResolver.openOutputStream(uri)?.use { out ->
                                File(path).inputStream().use { input -> input.copyTo(out) }
                            }
                            containerUri = uri
                            status = "Saved encrypted container. Share encrypted from the bar below, or Open volume."
                        } catch (_: Exception) {
                            status = "Created in app cache, but could not save a copy."
                        }
                    }
                }
                val toolSaver = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/octet-stream")
                ) { uri: Uri? ->
                    val file = pendingExportFile
                    if (uri != null && file != null && file.exists()) {
                        ShareHelper.persistRead(this@MainActivity, uri)
                        try {
                            contentResolver.openOutputStream(uri)?.use { out ->
                                file.inputStream().use { input -> input.copyTo(out) }
                            }
                            status = "Saved ${file.name}."
                        } catch (_: Exception) {
                            status = "Could not save ${file.name}."
                        }
                    }
                }
                val restoreHeaderPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        restoreVolumeHeader(
                            volumePath = path,
                            backupUri = uri,
                            password = password,
                            pimText = pim,
                            useTextPassword = useTextPassword,
                            useBiometric = useBiometric,
                            bioSecret = bioSecret,
                            keyfileUris = keyfileUris,
                            currentHandle = handle,
                            onHandle = { handle = it },
                            onEntries = { entries = it },
                            onStatus = { status = it }
                        )
                    }
                }
                val copyFromDevicePicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        importFromDevice(
                            handle = handle,
                            dirPath = dirPath,
                            uri = uri,
                            move = pendingFromDeviceMove,
                            onEntries = { entries = it },
                            onStatus = { status = it }
                        )
                    }
                }
                val saveToDevicePicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/octet-stream")
                ) { uri: Uri? ->
                    val name = pendingSaveName
                    if (uri != null && name.isNotEmpty()) {
                        val entry = entries.firstOrNull { it.name == name && !it.isDir }
                        if (entry != null) {
                            exportToDevice(
                                handle = handle,
                                dirPath = dirPath,
                                entry = entry,
                                uri = uri,
                                move = pendingSaveMove,
                                onEntries = { entries = it },
                                onStatus = { status = it }
                            )
                        }
                    }
                }

                fun runPanic() {
                    panicWipe()
                    password = ""
                    wrapPassword = ""
                    generatedPassword = ""
                    handle = 0
                    entries = emptyList()
                    dirPath = ""
                    status = "Panic wipe complete. Keystore, cache, clipboard, and remembered factors are gone."
                }

                Box(Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.imePadding(),
                    containerColor = colors.background,
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White)
                                    Spacer(Modifier.padding(6.dp))
                                    Column {
                                        Text("VC Port", style = MaterialTheme.typography.titleLarge)
                                        Text(
                                            if (handle > 0)
                                                if (dirPath.isEmpty()) "/" else "/$dirPath"
                                            else if (BuildConfig.ENABLE_UPDATE_CHECK)
                                                "Stay offline until you check for updates."
                                            else
                                                "Stay offline. F-Droid: no network.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.85f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            },
                            actions = {
                                if (handle > 0) {
                                    TextButton(
                                        onClick = { lockSession() },
                                        enabled = !busy,
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                                    ) { Text("Lock") }
                                }
                                TextButton(
                                    onClick = { runPanic() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFFCDD2))
                                ) {
                                    Icon(Icons.Filled.Warning, contentDescription = null)
                                    Spacer(Modifier.padding(4.dp))
                                    Text("Panic wipe")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = VcDesktopBlue,
                                titleContentColor = Color.White,
                                actionIconContentColor = Color.White
                            )
                        )
                    },
                    bottomBar = {
                        val incomingFile = incoming
                        val selectedFiles = entries.filter { it.name in selectedNames && !it.isDir }
                        val cipherName = incomingFile?.name
                            ?: containerUri?.let { ShareHelper.displayName(this@MainActivity, it) }
                            ?: path.takeIf { it.isNotEmpty() }?.let { File(it).name }
                        val inFrontLabel = when {
                            selectedFiles.isNotEmpty() ->
                                selectedFiles.joinToString { it.name } + " — decrypted from the open volume"
                            lastPlainFiles.any { it.exists() } ->
                                lastPlainFiles.joinToString { it.name } + " — decrypted copy"
                            incomingFile != null && ShareHelper.looksLikeWrap(incomingFile.name) ->
                                "${incomingFile.name} — encrypted wrap in front"
                            cipherName != null ->
                                "$cipherName — encrypted file in front"
                            else ->
                                "Nothing in front. Select a container or file, then share from here."
                        }
                        val canDecrypted = selectedFiles.isNotEmpty() ||
                            lastPlainFiles.any { it.exists() } ||
                            (incomingFile != null && ShareHelper.looksLikeWrap(incomingFile.name) && wrapPassword.isNotEmpty())
                        InFrontShareBar(
                            label = inFrontLabel,
                            canShareEncrypted = true,
                            canShareDecrypted = canDecrypted,
                            busy = busy,
                            onShareEncrypted = {
                                shareInFrontEncrypted(
                                    incoming = incomingFile,
                                    containerUri = containerUri,
                                    path = path,
                                    pickEncrypted = { shareEncPicker.launch(arrayOf("*/*")) },
                                    onStatus = { status = it }
                                )
                            },
                            onShareDecrypted = {
                                shareInFrontDecrypted(
                                    handle = handle,
                                    dirPath = dirPath,
                                    selected = selectedFiles,
                                    lastPlain = lastPlainFiles,
                                    incoming = incomingFile,
                                    wrapPassword = wrapPassword
                                ) { status = it }
                            }
                        )
                    }
                ) { inner ->
                    Column(
                        Modifier
                            .padding(inner)
                            .fillMaxSize()
                    ) {
                        StatusBanner(
                            status = status,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        incoming?.let { file ->
                            VcCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Text("Received ${file.name} from another app.", style = MaterialTheme.typography.titleMedium)
                                FilledTonalButton(
                                    onClick = {
                                        path = copyIncomingAsContainer(file)
                                        tab = 0
                                        status = "Using ${file.name} as container."
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Open as container") }
                                OutlinedButton(
                                    onClick = {
                                        beginShare()
                                        ShareHelper.shareFiles(this@MainActivity, listOf(file), "Share encrypted file")
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Share encrypted") }
                                if (ShareHelper.looksLikeWrap(file.name)) {
                                    OutlinedButton(
                                        onClick = {
                                            tab = 1
                                            unwrapIncomingFile(file, wrapPassword) { status = it }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Decrypt wrap") }
                                }
                            }
                        }
                        if (handle > 0) {
                            VaultPane(
                                dirPath = dirPath,
                                entries = entries,
                                selectedNames = selectedNames,
                                truncated = listTruncated,
                                busy = busy,
                                onUp = {
                                    dirPath = parentDir(dirPath)
                                    loadDir(handle, dirPath, { entries = it }, { status = it })
                                },
                                onOpen = { entry ->
                                    if (entry.isDir) {
                                        dirPath = joinDir(dirPath, entry.name)
                                        loadDir(handle, dirPath, { entries = it }, { status = it })
                                    } else {
                                        selectedNames = if (entry.name in selectedNames) {
                                            selectedNames - entry.name
                                        } else {
                                            selectedNames + entry.name
                                        }
                                    }
                                },
                                onShare = { entry ->
                                    selectedNames = setOf(entry.name)
                                    shareVaultFiles(handle, dirPath, listOf(entry)) { status = it }
                                },
                                onCopyFromDevice = {
                                    beginShare()
                                    pendingFromDeviceMove = false
                                    copyFromDevicePicker.launch(arrayOf("*/*"))
                                },
                                onMoveFromDevice = {
                                    beginShare()
                                    pendingFromDeviceMove = true
                                    copyFromDevicePicker.launch(arrayOf("*/*"))
                                },
                                onCopyToDevice = {
                                    val entry = entries.firstOrNull { it.name in selectedNames && !it.isDir }
                                    if (entry == null) {
                                        status = "Tap a file in the volume, then Copy to device."
                                    } else {
                                        beginShare()
                                        pendingSaveName = entry.name
                                        pendingSaveMove = false
                                        saveToDevicePicker.launch(entry.name)
                                    }
                                },
                                onMoveToDevice = {
                                    val entry = entries.firstOrNull { it.name in selectedNames && !it.isDir }
                                    if (entry == null) {
                                        status = "Tap a file in the volume, then Move to device."
                                    } else {
                                        beginShare()
                                        pendingSaveName = entry.name
                                        pendingSaveMove = true
                                        saveToDevicePicker.launch(entry.name)
                                    }
                                },
                                onNewFolder = {
                                    namePrompt = "New folder"
                                    namePromptValue = ""
                                },
                                onRename = {
                                    val entry = entries.firstOrNull { it.name in selectedNames }
                                    if (entry == null) {
                                        status = "Tap a file or folder, then Rename."
                                    } else {
                                        namePrompt = "Rename"
                                        namePromptValue = entry.name
                                    }
                                },
                                onDelete = {
                                    val entry = entries.firstOrNull { it.name in selectedNames }
                                    if (entry == null) {
                                        status = "Tap a file or folder, then Delete."
                                    } else {
                                        deleteVaultEntry(handle, dirPath, entry, { entries = it }, { status = it })
                                    }
                                },
                                onProperties = {
                                    val entry = entries.firstOrNull { it.name in selectedNames }
                                        ?: entries.firstOrNull()
                                    if (entry == null) {
                                        status = "This folder is empty."
                                    } else {
                                        status = formatEntryProperties(entry)
                                    }
                                },
                                onWipeFreeSpace = {
                                    wipeFreeSpace(handle, dirPath, { entries = it }, { status = it })
                                },
                                onMore = {
                                    loadDir(handle, dirPath, { entries = it }, { status = it }, append = true)
                                }
                            )
                        } else {
                            ScrollableTabRow(
                                selectedTabIndex = tab.coerceIn(0, 4),
                                containerColor = colors.background,
                                contentColor = colors.primary,
                                edgePadding = 8.dp
                            ) {
                                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Volume") })
                                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Wrap") })
                                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Share") })
                                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Create") })
                                Tab(selected = tab == 4, onClick = { tab = 4 }, text = { Text("Tools") })
                            }
                            Column(
                                Modifier
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                when (tab) {
                                    1 -> {
                                        VcCard {
                                            Text("Wrap a single file", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "Encrypt one file with a password. The result is a .vcpw wrap you can share. Unwrap it later in this app.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            SecretField(
                                                wrapPassword,
                                                { wrapPassword = it },
                                                "Wrap password (never stored)",
                                                enabled = !busy
                                            )
                                            FilledTonalButton(
                                                onClick = {
                                                    val generated = NativeBridge.generatePassword(24)
                                                    if (generated != null) {
                                                        wrapPassword = generated
                                                        generatedPassword = generated
                                                        status = "Generated a 24-character password in memory. It is not saved."
                                                    } else {
                                                        status = "Password generator failed."
                                                    }
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Generate strong password") }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedButton(
                                                    onClick = {
                                                        if (wrapPassword.isNotEmpty()) {
                                                            SensitiveClipboard.copyOnce(this@MainActivity, wrapPassword)
                                                            status = "Copied once. Clipboard clears in 30 seconds. No history is kept."
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) { Text("Copy once") }
                                                OutlinedButton(
                                                    onClick = {
                                                        SensitiveClipboard.forget(this@MainActivity)
                                                        wrapPassword = ""
                                                        generatedPassword = ""
                                                        status = "Password forgotten. Clipboard cleared."
                                                    },
                                                    modifier = Modifier.weight(1f)
                                                ) { Text("Forget password") }
                                            }
                                            Button(
                                                onClick = { wrapPicker.launch(arrayOf("*/*")) },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Encrypt file") }
                                            OutlinedButton(
                                                onClick = { unwrapPicker.launch(arrayOf("*/*")) },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Decrypt wrap") }
                                        }
                                    }
                                    2 -> {
                                        VcCard {
                                            Text("Share encrypted file", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "Sends the encrypted file as-is, including disguised names (.jpg, .png, .safetensors). No password, no decrypt. WhatsApp, Gmail, Drive, and the rest of the share sheet.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            Button(
                                                onClick = { shareEncPicker.launch(arrayOf("*/*")) },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Filled.Share, contentDescription = null)
                                                Spacer(Modifier.padding(6.dp))
                                                Text("Share encrypted file")
                                            }
                                            if (containerUri != null || path.isNotEmpty()) {
                                                OutlinedButton(
                                                    onClick = {
                                                        shareEncryptedVolume(containerUri, path) { status = it }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) { Text("Share this encrypted file") }
                                            }
                                        }
                                    }
                                    3 -> {
                                        VcCard {
                                            Text("Encryption Options", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "Creates a standard VeraCrypt container. The file name can be anything — volume.hc, photo.jpg, image.png, model.safetensors, adapter.lora. That name is only a disguise: a .jpg volume is not a photo. Open it in VeraCrypt on Windows, macOS, Linux, or another phone with the same password, PIM, and keyfiles. Fingerprint, face, or screen lock only unlock those factors on THIS phone — they are not stored in the volume header.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            Text(
                                                "Same cipher and KDF list as the desktop volume wizard. Default is AES(Twofish(Serpent)) with HMAC-SHA-512, XTS, FAT. Opening uses whichever password you type (outer or nested) — there is no open-time hidden checkbox.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            OptionDropdown(
                                                "Encryption Algorithm",
                                                NativeBridge.CIPHERS,
                                                createCipher,
                                                { createCipher = it },
                                                enabled = !busy
                                            )
                                            OptionDropdown(
                                                "KDF",
                                                NativeBridge.KDFS,
                                                createKdf,
                                                { createKdf = it },
                                                enabled = !busy
                                            )
                                            OptionDropdown(
                                                "File name / disguise",
                                                ShareHelper.DISGUISE_NAMES,
                                                if (createFileName in ShareHelper.DISGUISE_NAMES) createFileName else "volume.hc",
                                                { createFileName = it },
                                                enabled = !busy
                                            )
                                            OutlinedTextField(
                                                createFileName,
                                                { createFileName = it.filterNot { ch -> ch == '/' || ch == '\\' }.take(120) },
                                                label = { Text("File name (any extension)") },
                                                modifier = Modifier.fillMaxWidth(),
                                                enabled = !busy,
                                                singleLine = true
                                            )
                                            Text(
                                                "Opening ignores the extension. The volume is detected only if the password, PIM, and keyfiles decrypt the header.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            OutlinedTextField(
                                                createSizeMb,
                                                { createSizeMb = it.filter { ch -> ch.isDigit() }.take(4) },
                                                label = { Text("Size (MiB)") },
                                                modifier = Modifier.fillMaxWidth(),
                                                enabled = !busy,
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                            )
                                            SecretField(
                                                createPassword,
                                                { createPassword = it },
                                                "Volume password (never stored)",
                                                enabled = !busy
                                            )
                                            OutlinedTextField(
                                                createPim,
                                                { createPim = it },
                                                label = { Text("PIM (0 = default)") },
                                                modifier = Modifier.fillMaxWidth(),
                                                enabled = !busy,
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                            )
                                            FilledTonalButton(
                                                onClick = {
                                                    val generated = NativeBridge.generatePassword(24)
                                                    if (generated != null) {
                                                        createPassword = generated
                                                        status = "Generated a 24-character password in memory. It is not saved."
                                                    }
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Generate strong password") }
                                            Text("Keyfiles", style = MaterialTheme.typography.titleSmall)
                                            keyfileUris.forEach { uri ->
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        ShareHelper.displayName(this@MainActivity, uri) ?: uri.toString(),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    TextButton(onClick = { keyfileUris = keyfileUris.filterNot { it == uri } }) {
                                                        Text("Remove")
                                                    }
                                                }
                                            }
                                            OutlinedButton(
                                                onClick = { keyfilePicker.launch(arrayOf("*/*")) },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Add keyfiles") }
                                            EntropyPad(
                                                percent = entropyPercent,
                                                enabled = !busy,
                                                onSample = { sample ->
                                                    NativeBridge.addEntropy(sample)
                                                    entropyPercent = NativeBridge.entropyPercent()
                                                }
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(createHidden, { createHidden = it }, enabled = !busy)
                                                Text("Nested volume (VeraCrypt hidden volume)")
                                            }
                                            if (createHidden) {
                                                Text(
                                                    "Creates a second volume inside this container, like the desktop hidden-volume wizard. Use a different password. Do not fill the outer volume or you will overwrite the nested one. A PC/Mac opens the outer or nested volume depending only on which password you type.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colors.onSurfaceVariant
                                                )
                                                SecretField(
                                                    createHiddenPassword,
                                                    { createHiddenPassword = it },
                                                    "Nested volume password",
                                                    enabled = !busy
                                                )
                                                OutlinedTextField(
                                                    createHiddenPim,
                                                    { createHiddenPim = it },
                                                    label = { Text("Nested PIM (0 = default)") },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    enabled = !busy,
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                                )
                                                OutlinedTextField(
                                                    createHiddenSizeMb,
                                                    { createHiddenSizeMb = it.filter { ch -> ch.isDigit() }.take(4) },
                                                    label = { Text("Nested size (MiB)") },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    enabled = !busy,
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                                )
                                            }
                                            if (vault.isAvailable()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Checkbox(useBiometric, {
                                                        useBiometric = it
                                                        if (it && bioSecret == null) {
                                                            bioSecret = FactorCodec.randomBiometricKey()
                                                        }
                                                        if (!it) bioSecret = null
                                                    }, enabled = !busy)
                                                    Text("Fingerprint, face, or screen lock")
                                                }
                                                Text(
                                                    "Mixed as a VeraCrypt keyfile. Export it to open this volume on a PC, Mac, or another phone. Do not use phone unlock as the only factor in a danger-state — biometrics can be compelled.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colors.onSurfaceVariant
                                                )
                                                Text(
                                                    bioSecret?.let { "Phone-unlock keyfile ready (${it.size} bytes)." }
                                                        ?: "Check the box to create a random keyfile, or import one you already use on a computer.",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                                OutlinedButton(
                                                    onClick = { importBioPicker.launch(arrayOf("*/*")) },
                                                    enabled = !busy,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) { Text("Import keyfile") }
                                                OutlinedButton(
                                                    onClick = {
                                                        val secret = bioSecret
                                                        if (secret == null) {
                                                            status = "Create or import a biometric password first."
                                                        } else {
                                                            val file = KeyfileIo.writeSecret(this@MainActivity, secret)
                                                            beginShare()
                                                            ShareHelper.shareFiles(this@MainActivity, listOf(file), "Export biometric keyfile")
                                                            status = "Share this keyfile into VeraCrypt on a computer (Add keyfile). Delete it after."
                                                        }
                                                    },
                                                    enabled = !busy,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) { Text("Export keyfile") }
                                            } else {
                                                Text(
                                                    "Fingerprint, face, or screen lock: set a PIN, pattern, password, fingerprint, or face in Android settings to use phone unlock as a VeraCrypt keyfile factor.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = colors.onSurfaceVariant
                                                )
                                            }
                                            Button(
                                                onClick = {
                                                    createContainer(
                                                        vault = vault,
                                                        password = createPassword,
                                                        pimText = createPim,
                                                        sizeMbText = createSizeMb,
                                                        cipher = createCipher,
                                                        kdf = createKdf,
                                                        keyfileUris = keyfileUris,
                                                        useBiometric = useBiometric,
                                                        bioSecret = bioSecret,
                                                        rememberBio = rememberBio,
                                                        hidden = createHidden,
                                                        hiddenPassword = createHiddenPassword,
                                                        hiddenPimText = createHiddenPim,
                                                        hiddenSizeMbText = createHiddenSizeMb,
                                                        fileName = createFileName,
                                                        entropyPercent = entropyPercent,
                                                        onPath = {
                                                            path = it
                                                            password = createPassword
                                                            pim = createPim
                                                        },
                                                        onStatus = { status = it },
                                                        onSaved = { createSaver.launch(ShareHelper.sanitizeDisguiseName(createFileName)) }
                                                    )
                                                },
                                                enabled = !busy && entropyPercent >= 100,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Create volume") }
                                        }
                                    }
                                    4 -> {
                                        VcCard {
                                            Text("Volume header", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "Uses the container and unlock factors from the Volume tab. Close the volume first — these rewrite the header. Keyfiles and biometrics are mixed into the password; they are not stored in the header.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            SecretField(
                                                newPassword,
                                                { newPassword = it },
                                                "New password (empty = keep current)",
                                                enabled = !busy
                                            )
                                            OutlinedTextField(
                                                newPim,
                                                { newPim = it },
                                                label = { Text("New PIM (0 = VeraCrypt default)") },
                                                modifier = Modifier.fillMaxWidth(),
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
                                                        useBiometric = useBiometric,
                                                        bioSecret = bioSecret,
                                                        keyfileUris = keyfileUris,
                                                        useBackupHeader = useBackupHeader,
                                                        currentHandle = handle,
                                                        newPassword = newPassword,
                                                        newPimText = newPim,
                                                        newKdf = "",
                                                        keepKeyfiles = true,
                                                        onHandle = { handle = it },
                                                        onEntries = { entries = it },
                                                        onStatus = { status = it },
                                                        successMessage = "Changed volume password. Open with the new password, same keyfiles, and same biometrics."
                                                    )
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Change volume password") }
                                            OptionDropdown(
                                                "Header KDF",
                                                listOf("(keep current)") + NativeBridge.KDFS,
                                                headerKdf,
                                                { headerKdf = it },
                                                enabled = !busy
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
                                                            useBiometric = useBiometric,
                                                            bioSecret = bioSecret,
                                                            keyfileUris = keyfileUris,
                                                            useBackupHeader = useBackupHeader,
                                                            currentHandle = handle,
                                                            newPassword = "",
                                                            newPimText = newPim,
                                                            newKdf = kdf,
                                                            keepKeyfiles = true,
                                                            onHandle = { handle = it },
                                                            onEntries = { entries = it },
                                                            onStatus = { status = it },
                                                            successMessage = "Set header key derivation algorithm to $kdf."
                                                        )
                                                    }
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Set header key derivation algorithm") }
                                            OutlinedButton(
                                                onClick = {
                                                    runChangeHeader(
                                                        path = path,
                                                        password = password,
                                                        pimText = pim,
                                                        useTextPassword = useTextPassword,
                                                        useBiometric = useBiometric,
                                                        bioSecret = bioSecret,
                                                        keyfileUris = keyfileUris,
                                                        useBackupHeader = useBackupHeader,
                                                        currentHandle = handle,
                                                        newPassword = "",
                                                        newPimText = newPim,
                                                        newKdf = "",
                                                        keepKeyfiles = true,
                                                        onHandle = { handle = it },
                                                        onEntries = { entries = it },
                                                        onStatus = { status = it },
                                                        successMessage = "Applied the current keyfile list (Add/Remove keyfiles) to the volume header."
                                                    )
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Add/Remove keyfiles to/from volume") }
                                            OutlinedButton(
                                                onClick = {
                                                    runChangeHeader(
                                                        path = path,
                                                        password = password,
                                                        pimText = pim,
                                                        useTextPassword = useTextPassword,
                                                        useBiometric = useBiometric,
                                                        bioSecret = bioSecret,
                                                        keyfileUris = keyfileUris,
                                                        useBackupHeader = useBackupHeader,
                                                        currentHandle = handle,
                                                        newPassword = "",
                                                        newPimText = newPim,
                                                        newKdf = "",
                                                        keepKeyfiles = false,
                                                        onHandle = { handle = it },
                                                        onEntries = { entries = it },
                                                        onStatus = { status = it },
                                                        successMessage = "Removed all keyfiles from volume. Open with the password only."
                                                    )
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Remove all keyfiles from volume") }
                                            HorizontalDivider()
                                            Button(
                                                onClick = {
                                                    backupVolumeHeader(
                                                        volumePath = path,
                                                        password = password,
                                                        pimText = pim,
                                                        useTextPassword = useTextPassword,
                                                        useBiometric = useBiometric,
                                                        bioSecret = bioSecret,
                                                        keyfileUris = keyfileUris,
                                                        currentHandle = handle,
                                                        onHandle = { handle = it },
                                                        onEntries = { entries = it },
                                                        onStatus = { status = it },
                                                        onSaved = { file ->
                                                            pendingExportFile = file
                                                            toolSaver.launch("volume-header.bak")
                                                        }
                                                    )
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Backup volume header") }
                                            OutlinedButton(
                                                onClick = { restoreHeaderPicker.launch(arrayOf("*/*")) },
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
                                                        useBiometric = useBiometric,
                                                        bioSecret = bioSecret,
                                                        keyfileUris = keyfileUris,
                                                        currentHandle = handle,
                                                        onHandle = { handle = it },
                                                        onEntries = { entries = it },
                                                        onStatus = { status = it }
                                                    )
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Restore from embedded backup header") }
                                            OutlinedButton(
                                                onClick = {
                                                    if (handle <= 0) {
                                                        status = "Open the volume first for Volume properties."
                                                    } else {
                                                        status = NativeBridge.volumeInfo(handle)
                                                            ?: "Could not read volume properties."
                                                    }
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Volume properties") }
                                        }
                                        VcCard {
                                            Text("Keyfile generator", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "Writes 128 random bytes — same size as the desktop Keyfile Generator default. Share or save the file, then add it as a keyfile.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            Button(
                                                onClick = {
                                                    val dest = File(cacheDir, "random.key")
                                                    beginWork()
                                                    Thread {
                                                        val rc = NativeBridge.generateKeyfile(dest.absolutePath, 128)
                                                        runOnUiThread {
                                                            endWork()
                                                            if (rc != 0) {
                                                                status = "Keyfile generator failed."
                                                            } else {
                                                                pendingExportFile = dest
                                                                status = "Generated a 128-byte keyfile. Save a copy, then Add keyfiles."
                                                                toolSaver.launch("random.key")
                                                            }
                                                        }
                                                    }.start()
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Keyfile generator") }
                                        }
                                        VcCard {
                                            Text("Benchmark / test vectors", style = MaterialTheme.typography.titleMedium)
                                            OutlinedButton(
                                                onClick = {
                                                    beginWork()
                                                    status = "Running encryption benchmark…"
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
                                                    beginWork()
                                                    status = "Running known-answer test vectors…"
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
                                        }
                                        VcCard {
                                            Text("Wipe cached passwords", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "Clears passwords in memory, closes the volume, and wipes the plaintext cache. Same as Lock. Panic wipe also destroys Keystore copies.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    lockSession()
                                                    password = ""
                                                    wrapPassword = ""
                                                    generatedPassword = ""
                                                    handle = 0
                                                    entries = emptyList()
                                                    status = "Wipe cached passwords complete. Volume closed."
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Wipe cached passwords") }
                                        }
                                        VcCard {
                                            Text("Device encryption", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "This app encrypts VeraCrypt container files (any file name). It cannot encrypt the phone's operating system the way VeraCrypt system encryption does on Windows. Android already encrypts the device with your screen lock.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                        }
                                        VcCard {
                                            Text("Security tokens", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "PKCS#11 smart cards and hardware tokens are not available on this phone. Export a keyfile from the token on a computer, then Add keyfiles here.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                        }
                                        VcCard {
                                            Text("Desktop leftovers", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "This is the full file-container port. These desktop items stay on a computer: mount as a drive letter (no FUSE here), Select Device / Auto-Mount All Devices, system encryption, rescue disk, traveler disk, volume expander, in-place partition encrypt/decrypt, hotkeys, language files, NTFS/exFAT/ext filesystems, hidden-volume write protection while the outer is open, PKCS#11 tokens, and a DocumentsProvider / Files.app browse of an unlocked volume (that was a seizure leak). Online help is not fetched while Stay offline. English UI only.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                        }
                                    }
                                    else -> {
                                        VcCard {
                                            Text(
                                                if (BuildConfig.ENABLE_UPDATE_CHECK)
                                                    "VeraCrypt-compatible Android client. Offline until you check for updates."
                                                else
                                                    "VeraCrypt-compatible Android client. F-Droid build: no network."
                                            )
                                            Text(
                                                SourcePin.describeBuild(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            Text(
                                                if (BuildConfig.ENABLE_UPDATE_CHECK)
                                                    "Tap Check for updates for a 20-second HTTPS window to our version.json, official VeraCrypt's latest GitHub release, and GitHub's status page. No other hosts. No redirects. This app does not download or install APKs, never listens, and never fetches src/. Offline again after."
                                                else
                                                    "This APK has no INTERNET permission. F-Droid updates you after the source is tagged. The app never installs itself.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            Text(
                                                "Stay offline by default. High-threat: screenshots blocked, recents hidden, no backups, no user CAs. Wrap a file, share ciphertext as-is, or panic wipe. Biometrics can be compelled — prefer a long password + keyfile, not Remember. This is not unbreakable.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            if (BuildConfig.ENABLE_UPDATE_CHECK) {
                                                OutlinedButton(
                                                    onClick = {
                                                        status = "Checking for updates (≤20s HTTPS window)..."
                                                        Thread {
                                                            try {
                                                                val result = UpdateChecker.check()
                                                                runOnUiThread { status = formatUpdateStatus(result) }
                                                            } catch (e: Exception) {
                                                                runOnUiThread { status = "Update check failed: ${e.message}. Offline again." }
                                                            }
                                                        }.start()
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) { Text("Check for updates") }
                                            }
                                            Button(
                                                onClick = { picker.launch(arrayOf("*/*")) },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Choose container") }
                                            OutlinedButton(
                                                onClick = { picker.launch(arrayOf("*/*")) },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("USB/OTG container") }
                                            Text(
                                                "USB/OTG: pick any file on the stick through Android's file picker — .hc, .jpg, .png, .safetensors, or no extension. This app cannot mount a raw USB disk or auto-mount /dev block devices without root.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            OutlinedTextField(
                                                path,
                                                { path = it },
                                                label = { Text("Container path") },
                                                modifier = Modifier.fillMaxWidth(),
                                                enabled = !busy,
                                                singleLine = true
                                            )
                                        }
                                        VcCard {
                                            Text("Unlock factors", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "Combine any of: fingerprint / face / screen lock, text password, keyfiles, and PIM. The volume itself is a normal VeraCrypt file — same mix a computer uses. Any file name works; the header is detected only if those credentials are correct.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(useTextPassword, { useTextPassword = it }, enabled = !busy)
                                                Text("Text password")
                                            }
                                            if (useTextPassword) {
                                                SecretField(
                                                    password,
                                                    { password = it },
                                                    "Password",
                                                    enabled = !busy
                                                )
                                            }
                                            Button(
                                                onClick = {
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
                                                        useBackupHeader = useBackupHeader,
                                                        readOnly = readOnlyOpen,
                                                        trueCryptMode = trueCryptMode,
                                                        currentHandle = handle,
                                                        onHandle = { handle = it },
                                                        onEntries = { entries = it },
                                                        onStatus = { status = it }
                                                    )
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Open volume") }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(useBackupHeader, { useBackupHeader = it }, enabled = !busy)
                                                Text("Use backup header")
                                            }
                                            Text(
                                                "If the first header is damaged, open using the copy stored at the end of the container.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(readOnlyOpen, { readOnlyOpen = it }, enabled = !busy)
                                                Text("Read-only")
                                            }
                                            Text(
                                                "Same as desktop Mount Options → Read-only. Import, delete, rename, and wipe free space are refused.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(trueCryptMode, { trueCryptMode = it }, enabled = !busy)
                                                Text("TrueCrypt Mode")
                                            }
                                            Text(
                                                "TrueCrypt 6/7 volumes have no PIM — this forces PIM 0. Creating new TrueCrypt volumes is not offered; create a VeraCrypt volume.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            TextButton(onClick = { moreFactors = !moreFactors }) {
                                                Text(if (moreFactors) "Hide extra factors" else "More factors (PIM, keyfiles, biometrics)")
                                            }
                                            if (moreFactors) {
                                                OutlinedTextField(
                                                    pim,
                                                    { pim = it },
                                                    label = { Text("PIM (0 = default)") },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    enabled = !busy,
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                                )
                                                Text("Keyfiles", style = MaterialTheme.typography.titleSmall)
                                                keyfileUris.forEach { uri ->
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            ShareHelper.displayName(this@MainActivity, uri) ?: uri.toString(),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        TextButton(onClick = { keyfileUris = keyfileUris.filterNot { it == uri } }) {
                                                            Text("Remove")
                                                        }
                                                    }
                                                }
                                                OutlinedButton(
                                                    onClick = { keyfilePicker.launch(arrayOf("*/*")) },
                                                    modifier = Modifier.fillMaxWidth()
                                                ) { Text("Add keyfiles") }
                                                if (vault.isAvailable()) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(useBiometric, {
                                                            useBiometric = it
                                                            if (!it) bioSecret = null
                                                        }, enabled = !busy)
                                                        Text("Fingerprint, face, or screen lock")
                                                    }
                                                    Text(
                                                        "Do not use biometrics as the only factor in a danger-state. Fingerprints can be compelled. Mix a password and a keyfile you do not keep on this phone.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colors.onSurfaceVariant
                                                    )
                                                    Text(
                                                        "Stored in the Android Keystore (StrongBox when present). Mixed as a VeraCrypt keyfile so the same file opens on a PC or Mac.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colors.onSurfaceVariant
                                                    )
                                                    Text(
                                                        bioSecret?.let { "Biometric password ready (${it.size} bytes)." }
                                                            ?: if (path.isNotEmpty() && vault.hasFactors(path))
                                                                "A saved factor set exists. Unlock with biometrics to load it."
                                                            else
                                                                "Create a random biometric password, or import a keyfile you already use.",
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                    OutlinedButton(
                                                        onClick = {
                                                            val secret = FactorCodec.randomBiometricKey()
                                                            bioSecret = secret
                                                            useBiometric = true
                                                            if (path.isEmpty() || !rememberBio) {
                                                                status = "Created a 64-byte phone-unlock keyfile in memory. It is not stored unless you type REMEMBER."
                                                            } else {
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
                                                        },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) { Text("Create") }
                                                    OutlinedButton(
                                                        onClick = { importBioPicker.launch(arrayOf("*/*")) },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) { Text("Import keyfile") }
                                                    OutlinedButton(
                                                        onClick = {
                                                            val secret = bioSecret
                                                            if (secret == null) {
                                                                status = "Create or import a biometric password first."
                                                            } else {
                                                                val file = KeyfileIo.writeSecret(this@MainActivity, secret)
                                                                beginShare()
                                                                ShareHelper.shareFiles(this@MainActivity, listOf(file), "Export biometric keyfile")
                                                                status = "Share this keyfile into VeraCrypt on a computer (Add keyfile). Delete it after."
                                                            }
                                                        },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) { Text("Export keyfile") }
                                                    OutlinedButton(
                                                        onClick = {
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
                                                        },
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) { Text("Unlock with fingerprint, face, or screen lock") }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(
                                                            rememberBio,
                                                            {
                                                                if (it) {
                                                                    rememberConfirmText = ""
                                                                    rememberConfirmOpen = true
                                                                } else {
                                                                    rememberBio = false
                                                                }
                                                            },
                                                            enabled = !busy
                                                        )
                                                        Text("Remember this combination")
                                                    }
                                                    Text(
                                                        "Off by default. Type REMEMBER to store factors this session. Compelled biometrics can still open a remembered set.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colors.onSurfaceVariant
                                                    )
                                                    if (path.isNotEmpty() && vault.hasFactors(path)) {
                                                        OutlinedButton(
                                                            onClick = {
                                                                vault.clear(path)
                                                                status = "Forgot saved factors for this container."
                                                            },
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) { Text("Forget saved factors") }
                                                    }
                                                }
                                            }
                                        }
                                        VcCard {
                                            Text("About / licenses", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "“We must defend our own privacy if we expect to have any.” — Eric Hughes, A Cypherpunk’s Manifesto (1993)",
                                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                                color = colors.onSurfaceVariant
                                            )
                                            Text(
                                                "Portions of this product are based in part on TrueCrypt, freely available at http://www.truecrypt.org/",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            Text(
                                                "VC Port original code is Apache License 2.0. The volume core is VeraCrypt (Apache 2.0 / TrueCrypt License 3.0). You may not call this app VeraCrypt. There is no key escrow and no intelligence or police backdoor. A nation-state implant still wins. Not unbreakable.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            Text(
                                                "Contact: Shivam Mangesh Pingale — shivampingaledev@proton.me · shivampingaledev@gmail.com",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                            Text(
                                                if (BuildConfig.ENABLE_UPDATE_CHECK)
                                                    "No ads, analytics, or crash reporters. Passwords stay on this device. GitHub flavor may make one HTTPS request if you tap Check for updates. Source updates become a new app only after a rebuild."
                                                else
                                                    "No ads, analytics, crash reporters, or INTERNET permission. Passwords stay on this device. Updates come from F-Droid.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colors.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            if (rememberConfirmOpen) {
                AlertDialog(
                    onDismissRequest = {
                        rememberConfirmOpen = false
                        rememberConfirmText = ""
                    },
                    title = { Text("Store unlock factors on this phone?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("A compelled fingerprint can open them. Type REMEMBER to store this session only. Volume-path history is never written.")
                            OutlinedTextField(
                                rememberConfirmText,
                                { rememberConfirmText = it },
                                label = { Text("Type REMEMBER") },
                                singleLine = true
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                if (rememberConfirmText.trim() == "REMEMBER") {
                                    rememberBio = true
                                }
                                rememberConfirmOpen = false
                                rememberConfirmText = ""
                            },
                            enabled = rememberConfirmText.trim() == "REMEMBER"
                        ) { Text("Store") }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                rememberConfirmOpen = false
                                rememberConfirmText = ""
                                rememberBio = false
                            }
                        ) { Text("Cancel") }
                    }
                )
            }
            if (namePrompt != null) {
                AlertDialog(
                    onDismissRequest = { namePrompt = null },
                    title = { Text(namePrompt ?: "") },
                    text = {
                        OutlinedTextField(
                            namePromptValue,
                            { namePromptValue = it },
                            label = { Text("Name") },
                            singleLine = true
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val title = namePrompt
                                val typed = namePromptValue.trim()
                                namePrompt = null
                                if (typed.isEmpty()) {
                                    status = "Name is empty."
                                } else if (title == "New folder") {
                                    mkdirInVolume(handle, dirPath, typed, { entries = it }, { status = it })
                                } else if (title == "Rename") {
                                    val entry = entries.firstOrNull { it.name in selectedNames }
                                    if (entry == null) status = "Tap a file or folder, then Rename."
                                    else renameVaultEntry(handle, dirPath, entry, typed, { entries = it }, { status = it })
                                }
                            }
                        ) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { namePrompt = null }) { Text("Cancel") }
                    }
                )
            }
                WorkOverlay(
                    visible = busy,
                    title = overlayTitle.ifEmpty { status },
                    percent = overlayPercent
                )
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
        lastPlainFilesState.value = emptyList()
        endWork()
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

    private fun beginWork() {
        NativeBridge.resetProgress()
        busyState.value = true
    }

    private fun endWork() {
        NativeBridge.resetProgress()
        busyState.value = false
    }

    private fun uriLength(uri: Uri): Long {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) cursor.getLong(idx) else -1L
                } else {
                    -1L
                }
            } ?: -1L
        } catch (_: Exception) {
            -1L
        }
    }

    private fun copyStreamProgress(
        input: InputStream,
        output: OutputStream,
        total: Long,
        phase: String
    ) {
        val buf = ByteArray(64 * 1024)
        var copied = 0L
        var last = -1
        while (true) {
            val n = input.read(buf)
            if (n <= 0) break
            output.write(buf, 0, n)
            copied += n
            val pct = if (total > 0) ((copied * 100L) / total).toInt().coerceIn(0, 100) else -1
            if (pct != last) {
                last = pct
                NativeBridge.setProgress(pct, phase)
            }
        }
    }

    private fun createContainer(
        vault: BiometricVault,
        password: String,
        pimText: String,
        sizeMbText: String,
        cipher: String,
        kdf: String,
        keyfileUris: List<Uri>,
        useBiometric: Boolean,
        bioSecret: ByteArray?,
        rememberBio: Boolean,
        hidden: Boolean,
        hiddenPassword: String,
        hiddenPimText: String,
        hiddenSizeMbText: String,
        fileName: String,
        entropyPercent: Int,
        onPath: (String) -> Unit,
        onStatus: (String) -> Unit,
        onSaved: () -> Unit
    ) {
        val hasBio = useBiometric && bioSecret != null && bioSecret.isNotEmpty()
        if (password.isEmpty() && !hasBio && keyfileUris.isEmpty()) {
            onStatus("Choose at least one factor: text password, phone unlock keyfile, or a keyfile.")
            return
        }
        if (password.isNotEmpty() && password.length < 16 && !hasBio && keyfileUris.isEmpty()) {
            onStatus("Use Generate strong password, or type at least 16 characters. Nothing is saved.")
            return
        }
        if (useBiometric && (bioSecret == null || bioSecret.isEmpty())) {
            onStatus("Create or import a biometric password, or tap Unlock with biometrics to load a saved one.")
            return
        }
        if (entropyPercent < 100) {
            onStatus("Move your finger in the blank area until the randomness bar is full.")
            return
        }
        val mb = sizeMbText.toIntOrNull() ?: 0
        if (mb < 2 || mb > 512) {
            onStatus("Size must be 2–512 MiB.")
            return
        }
        var hiddenBytes = 0L
        if (hidden) {
            if (mb < 8) {
                onStatus("Nested volume needs an outer size of at least 8 MiB.")
                return
            }
            if (hiddenPassword.length < 16) {
                onStatus("Nested volume password must be at least 16 characters, and different from the outer password.")
                return
            }
            if (hiddenPassword == password) {
                onStatus("Use a different password for the nested volume.")
                return
            }
            val hiddenMb = hiddenSizeMbText.toIntOrNull() ?: 0
            if (hiddenMb < 2 || hiddenMb * 2 >= mb) {
                onStatus("Nested size must be at least 2 MiB and less than half the outer size, so the outer volume has room.")
                return
            }
            hiddenBytes = hiddenMb * 1024L * 1024L
        }
        beginWork()
        onStatus("Creating $mb MiB $cipher / $kdf volume…")
        Thread {
            val temps = mutableListOf<File>()
            try {
                if (hasBio && bioSecret != null) {
                    temps.add(KeyfileIo.writeSecret(this, bioSecret))
                }
                for (uri in keyfileUris) {
                    val copied = KeyfileIo.copyUri(this, uri)
                    if (copied == null) {
                        runOnUiThread {
                            endWork()
                            onStatus("Could not read a keyfile.")
                        }
                        return@Thread
                    }
                    temps.add(copied)
                }
                val dest = File(cacheDir, ShareHelper.sanitizeDisguiseName(fileName))
                val rc = NativeBridge.createVolume(
                    dest.absolutePath,
                    password,
                    pimText.toIntOrNull() ?: 0,
                    mb * 1024L * 1024L,
                    cipher,
                    kdf,
                    temps.map { it.absolutePath }.toTypedArray(),
                    if (hidden) hiddenPassword else "",
                    hiddenPimText.toIntOrNull() ?: 0,
                    hiddenBytes,
                    emptyArray()
                )
                runOnUiThread {
                    endWork()
                    if (rc != 0) {
                        onStatus(createErrorMessage(rc))
                    } else {
                        onPath(dest.absolutePath)
                        var msg = "Created $mb MiB $cipher / $kdf FAT volume as ${dest.name} (standard VeraCrypt file; the name is only a disguise). Save a copy, then Open volume or Share encrypted. Same password, PIM, and keyfiles open it on a PC, Mac, or another phone — the extension is ignored."
                        if (hidden) {
                            msg += " Nested volume is inside; open it with the nested password. Do not fill the outer volume."
                        }
                        if (hasBio) {
                            msg += " Export the phone-unlock keyfile for those other devices."
                        }
                        fun finishCreate() {
                            onStatus(msg)
                            onSaved()
                        }
                        if (hasBio && rememberBio && vault.isAvailable()) {
                            vault.store(
                                this,
                                dest.absolutePath,
                                FactorBundle(
                                    pim = pimText.toIntOrNull() ?: 0,
                                    password = password,
                                    biometricKey = bioSecret,
                                    keyfileUris = keyfileUris.map { it.toString() }
                                )
                            ) { finishCreate() }
                        } else {
                            finishCreate()
                        }
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    endWork()
                    onStatus("Create failed.")
                }
            } finally {
                temps.forEach { KeyfileIo.wipe(it) }
            }
        }.start()
    }

    private fun createErrorMessage(rc: Int): String {
        return when (rc) {
            -4 -> "Missing path, password, or size."
            -6 -> "That cipher or KDF is not available in this build."
            -5 -> "Not enough memory to create the volume."
            -2 -> "Password or PIM was rejected."
            -1 -> "Could not write the container file."
            else -> "Create failed (code $rc)."
        }
    }

    private fun headerErrorMessage(rc: Int): String {
        return when (rc) {
            -4 -> "Need a container path and at least a password or keyfile."
            -2 -> "Wrong password, PIM, or keyfile mix."
            -1 -> "Could not read or write the container. Close it first if it is open."
            -3 -> "Not a VeraCrypt-compatible volume, or the header is damaged."
            -6 -> "That KDF is not available in this build."
            -5 -> "Not enough memory."
            else -> "Header operation failed (code $rc)."
        }
    }

    private fun copyUnlockKeyfiles(
        useBiometric: Boolean,
        bioSecret: ByteArray?,
        keyfileUris: List<Uri>
    ): Pair<MutableList<File>, String?> {
        val temps = mutableListOf<File>()
        if (useBiometric && bioSecret != null && bioSecret.isNotEmpty()) {
            temps.add(KeyfileIo.writeSecret(this, bioSecret))
        }
        for (uri in keyfileUris) {
            val copied = KeyfileIo.copyUri(this, uri)
            if (copied == null) {
                temps.forEach { KeyfileIo.wipe(it) }
                return Pair(mutableListOf(), "Could not read a keyfile.")
            }
            temps.add(copied)
        }
        return Pair(temps, null)
    }

    private fun runChangeHeader(
        path: String,
        password: String,
        pimText: String,
        useTextPassword: Boolean,
        useBiometric: Boolean,
        bioSecret: ByteArray?,
        keyfileUris: List<Uri>,
        useBackupHeader: Boolean,
        currentHandle: Long,
        newPassword: String,
        newPimText: String,
        newKdf: String,
        keepKeyfiles: Boolean,
        onHandle: (Long) -> Unit,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit,
        successMessage: String
    ) {
        if (path.isEmpty()) {
            onStatus("Choose a container first.")
            return
        }
        val text = if (useTextPassword) password else ""
        val hasBio = useBiometric && bioSecret != null && bioSecret.isNotEmpty()
        if (text.isEmpty() && !hasBio && keyfileUris.isEmpty()) {
            onStatus("Enter the current password, keyfiles, or biometrics on the Volume tab.")
            return
        }
        val nextPassword = newPassword.ifEmpty { text }
        if (!keepKeyfiles && nextPassword.isEmpty()) {
            onStatus("Removing all keyfiles needs a text password, or the volume cannot be opened.")
            return
        }
        beginWork()
        onStatus("Rewriting volume header…")
        Thread {
            val temps = mutableListOf<File>()
            try {
                val (copied, err) = copyUnlockKeyfiles(useBiometric, bioSecret, keyfileUris)
                if (err != null) {
                    runOnUiThread {
                        endWork()
                        onStatus(err)
                    }
                    return@Thread
                }
                temps.addAll(copied)
                if (currentHandle > 0) NativeBridge.closeVolume(currentHandle)
                val newKeys = if (keepKeyfiles) temps.map { it.absolutePath }.toTypedArray() else emptyArray()
                val rc = NativeBridge.changeHeader(
                    path,
                    text,
                    pimText.toIntOrNull() ?: 0,
                    temps.map { it.absolutePath }.toTypedArray(),
                    useBackupHeader,
                    newPassword,
                    newPimText.toIntOrNull() ?: 0,
                    newKdf,
                    newKeys
                )
                runOnUiThread {
                    endWork()
                    onHandle(0)
                    onEntries(emptyList())
                    dirPathState.value = ""
                    onStatus(if (rc == 0) successMessage else headerErrorMessage(rc))
                }
            } catch (_: Exception) {
                runOnUiThread {
                    endWork()
                    onStatus("Could not rewrite the volume header.")
                }
            } finally {
                temps.forEach { KeyfileIo.wipe(it) }
            }
        }.start()
    }

    private fun backupVolumeHeader(
        volumePath: String,
        password: String,
        pimText: String,
        useTextPassword: Boolean,
        useBiometric: Boolean,
        bioSecret: ByteArray?,
        keyfileUris: List<Uri>,
        currentHandle: Long,
        onHandle: (Long) -> Unit,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit,
        onSaved: (File) -> Unit
    ) {
        if (volumePath.isEmpty()) {
            onStatus("Choose a container first.")
            return
        }
        val text = if (useTextPassword) password else ""
        beginWork()
        onStatus("Backing up volume header…")
        Thread {
            val temps = mutableListOf<File>()
            try {
                val (copied, err) = copyUnlockKeyfiles(useBiometric, bioSecret, keyfileUris)
                if (err != null) {
                    runOnUiThread {
                        endWork()
                        onStatus(err)
                    }
                    return@Thread
                }
                temps.addAll(copied)
                if (currentHandle > 0) NativeBridge.closeVolume(currentHandle)
                val dest = File(cacheDir, "volume-header.bak")
                val rc = NativeBridge.backupHeaders(
                    volumePath,
                    dest.absolutePath,
                    text,
                    pimText.toIntOrNull() ?: 0,
                    temps.map { it.absolutePath }.toTypedArray()
                )
                runOnUiThread {
                    endWork()
                    onHandle(0)
                    onEntries(emptyList())
                    if (rc != 0) {
                        onStatus(headerErrorMessage(rc))
                    } else {
                        onStatus("Header backup ready. Save the .bak file somewhere safe, not only on this phone.")
                        onSaved(dest)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    endWork()
                    onStatus("Backup volume header failed.")
                }
            } finally {
                temps.forEach { KeyfileIo.wipe(it) }
            }
        }.start()
    }

    private fun restoreVolumeHeader(
        volumePath: String,
        backupUri: Uri,
        password: String,
        pimText: String,
        useTextPassword: Boolean,
        useBiometric: Boolean,
        bioSecret: ByteArray?,
        keyfileUris: List<Uri>,
        currentHandle: Long,
        onHandle: (Long) -> Unit,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit
    ) {
        if (volumePath.isEmpty()) {
            onStatus("Choose a container first.")
            return
        }
        val text = if (useTextPassword) password else ""
        beginWork()
        onStatus("Restoring volume header…")
        Thread {
            val temps = mutableListOf<File>()
            try {
                val (copied, err) = copyUnlockKeyfiles(useBiometric, bioSecret, keyfileUris)
                if (err != null) {
                    runOnUiThread {
                        endWork()
                        onStatus(err)
                    }
                    return@Thread
                }
                temps.addAll(copied)
                val backup = File(cacheDir, "restore-header.bak")
                contentResolver.openInputStream(backupUri)?.use { input ->
                    backup.outputStream().use { output -> input.copyTo(output) }
                } ?: run {
                    runOnUiThread {
                        endWork()
                        onStatus("Could not read the header backup file.")
                    }
                    return@Thread
                }
                if (currentHandle > 0) NativeBridge.closeVolume(currentHandle)
                val rc = NativeBridge.restoreHeaders(
                    volumePath,
                    backup.absolutePath,
                    text,
                    pimText.toIntOrNull() ?: 0,
                    temps.map { it.absolutePath }.toTypedArray()
                )
                backup.delete()
                runOnUiThread {
                    endWork()
                    onHandle(0)
                    onEntries(emptyList())
                    onStatus(
                        if (rc == 0)
                            "Restored volume header. Open with the password that was current when the backup was made."
                        else
                            headerErrorMessage(rc)
                    )
                }
            } catch (_: Exception) {
                runOnUiThread {
                    endWork()
                    onStatus("Restore volume header failed.")
                }
            } finally {
                temps.forEach { KeyfileIo.wipe(it) }
            }
        }.start()
    }

    private fun copyContainerAsync(uri: Uri, onDone: (String) -> Unit) {
        beginWork()
        statusState.value = "Copying container into app cache…"
        Thread {
            val copied = try {
                copyToCache(uri)
            } catch (_: Exception) {
                ""
            }
            runOnUiThread {
                endWork()
                onDone(copied)
            }
        }.start()
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
        useBackupHeader: Boolean,
        readOnly: Boolean,
        trueCryptMode: Boolean,
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
        beginWork()
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
                        runOnUiThread {
                            endWork()
                            onStatus("Could not read a keyfile.")
                        }
                        return@Thread
                    }
                    temps.add(copied)
                }
                if (currentHandle > 0) NativeBridge.closeVolume(currentHandle)
                val result = NativeBridge.openVolume(
                    path,
                    text,
                    if (trueCryptMode) 0 else (pimText.toIntOrNull() ?: 0),
                    useBackupHeader,
                    temps.map { it.absolutePath }.toTypedArray(),
                    readOnly
                )
                if (result <= 0) {
                    runOnUiThread {
                        endWork()
                        onHandle(0)
                        onEntries(emptyList())
                        listTruncatedState.value = false
                        onStatus(openErrorMessage(result))
                    }
                    return@Thread
                }
                val listed = NativeBridge.listDir(result, "/")
                val parsed = listed.mapNotNull { parseEntry(it) }
                val truncated = parsed.any { it.name == "!truncated!" }
                val files = parsed.filter { it.name != "!error!" && it.name != "!truncated!" }
                val volumeBytes = NativeBridge.volumeSize(result)
                runOnUiThread {
                    endWork()
                    if (parsed.size == 1 && parsed[0].name == "!error!") {
                        NativeBridge.closeVolume(result)
                        onHandle(0)
                        onEntries(emptyList())
                        listTruncatedState.value = false
                        onStatus(listErrorMessage(parsed[0].size.toInt()))
                    } else {
                        onHandle(result)
                        onEntries(files)
                        dirPathState.value = ""
                        listTruncatedState.value = truncated
                        var msg = "Opened. Size $volumeBytes bytes. Tap a folder to open it, or a file to share the decrypted copy."
                        if (truncated) msg += " Listing truncated at ${NativeBridge.LIST_UI_MAX} entries. Tap Load more."
                        onStatus(msg)
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
            } catch (e: Exception) {
                runOnUiThread {
                    endWork()
                    onStatus("Open failed.")
                }
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
            tabState.intValue = 1
            statusState.value = "Received wrapped file ${first.name}. Enter the wrap password and tap Decrypt wrap."
        } else {
            tabState.intValue = 0
            pathState.value = copyIncomingAsContainer(first)
            statusState.value = "Received ${first.name}. Any extension can be a volume. Open with the correct password, PIM, and keyfiles, or share as-is."
        }
    }

    private fun unwrapIncomingFile(file: File, password: String, onStatus: (String) -> Unit) {
        if (password.isEmpty()) {
            onStatus("Enter the wrap password first. It is not stored.")
            return
        }
        beginWork()
        onStatus("Unwrapping file…")
        Thread {
            val destDir = File(cacheDir, "unwrapped").apply { mkdirs() }
            val outPath = NativeBridge.unwrapFile(file.absolutePath, destDir.absolutePath, password)
            runOnUiThread {
                endWork()
                if (outPath == null) {
                    onStatus("Unwrap failed. Wrong password or not a VC Port wrap.")
                } else {
                    val plain = File(outPath)
                    lastPlainFilesState.value = listOf(plain)
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
        beginWork()
        onStatus("Wrapping file…")
        Thread {
            val name = ShareHelper.displayName(this, uri) ?: "file.bin"
            val plain = File(cacheDir, "wrap-in-${ShareHelper.safeName(name)}")
            val wrapped = File(ShareHelper.shareDir(this), ShareHelper.safeName(name) + ".vcpw")
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    plain.outputStream().use { output ->
                        copyStreamProgress(input, output, uriLength(uri), "Reading file")
                    }
                } ?: run {
                    runOnUiThread {
                        endWork()
                        onStatus("Could not read the file.")
                    }
                    return@Thread
                }
                val rc = NativeBridge.wrapFile(plain.absolutePath, wrapped.absolutePath, password, name)
                KeyfileIo.wipe(plain)
                runOnUiThread {
                    endWork()
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
                runOnUiThread {
                    endWork()
                    onStatus("Wrap failed.")
                }
            }
        }.start()
    }

    private fun unwrapSelectedFile(uri: Uri, password: String, onStatus: (String) -> Unit) {
        if (password.isEmpty()) {
            onStatus("Enter the wrap password first. It is not stored.")
            return
        }
        beginWork()
        onStatus("Unwrapping file…")
        Thread {
            val name = ShareHelper.displayName(this, uri) ?: "wrap.vcpw"
            val wrapped = File(cacheDir, ShareHelper.safeName(name))
            val destDir = File(cacheDir, "unwrapped").apply { mkdirs() }
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    wrapped.outputStream().use { output ->
                        copyStreamProgress(input, output, uriLength(uri), "Reading wrap")
                    }
                } ?: run {
                    runOnUiThread {
                        endWork()
                        onStatus("Could not read the wrap.")
                    }
                    return@Thread
                }
                val outPath = NativeBridge.unwrapFile(wrapped.absolutePath, destDir.absolutePath, password)
                wrapped.delete()
                runOnUiThread {
                    endWork()
                    if (outPath == null) {
                        onStatus("Unwrap failed. Wrong password or not a VC Port wrap.")
                    } else {
                        val file = File(outPath)
                        lastPlainFilesState.value = listOf(file)
                        onStatus("Unwrapped ${file.name}. Password was not saved.")
                        beginShare()
                        ShareHelper.shareFiles(this, listOf(file), "Share unwrapped file")
                    }
                }
            } catch (e: Exception) {
                wrapped.delete()
                runOnUiThread {
                    endWork()
                    onStatus("Unwrap failed.")
                }
            }
        }.start()
    }

    private fun beginShare() {
        suppressLock = true
    }

    private fun shareInFrontEncrypted(
        incoming: File?,
        containerUri: Uri?,
        path: String,
        pickEncrypted: () -> Unit,
        onStatus: (String) -> Unit
    ) {
        when {
            incoming != null -> {
                beginShare()
                onStatus("Sharing ${incoming.name} encrypted as-is.")
                ShareHelper.shareFiles(this, listOf(incoming), "Share encrypted file")
            }
            containerUri != null || path.isNotEmpty() -> shareEncryptedVolume(containerUri, path, onStatus)
            else -> {
                onStatus("Pick the encrypted file in front of you.")
                pickEncrypted()
            }
        }
    }

    private fun shareInFrontDecrypted(
        handle: Long,
        dirPath: String,
        selected: List<VaultEntry>,
        lastPlain: List<File>,
        incoming: File?,
        wrapPassword: String,
        onStatus: (String) -> Unit
    ) {
        val livePlain = lastPlain.filter { it.exists() }
        when {
            handle > 0 && selected.isNotEmpty() -> shareVaultFiles(handle, dirPath, selected, onStatus)
            livePlain.isNotEmpty() -> {
                beginShare()
                onStatus("Sharing decrypted ${livePlain.joinToString { it.name }}.")
                ShareHelper.shareFiles(this, livePlain, "Share decrypted files")
            }
            incoming != null && ShareHelper.looksLikeWrap(incoming.name) ->
                unwrapIncomingFile(incoming, wrapPassword, onStatus)
            else -> onStatus("Tap files in an open volume, or decrypt a wrap, then Share decrypted.")
        }
    }

    private fun shareVaultFiles(
        handle: Long,
        dirPath: String,
        files: List<VaultEntry>,
        onStatus: (String) -> Unit
    ) {
        val toShare = files.filter { !it.isDir }
        if (handle <= 0) {
            onStatus("Open a volume first.")
            return
        }
        if (toShare.isEmpty()) {
            onStatus("Tap a file in the volume, then Share decrypted.")
            return
        }
        beginShare()
        beginWork()
        onStatus("Preparing ${toShare.size} decrypted file(s)…")
        Thread {
            val dests = mutableListOf<File>()
            for (entry in toShare) {
                val dest = File(ShareHelper.shareDir(this), ShareHelper.safeName(entry.name))
                val rc = NativeBridge.exportFile(handle, joinDir(dirPath, entry.name), dest.absolutePath)
                if (rc != 0 || !dest.exists()) {
                    runOnUiThread {
                        endWork()
                        onStatus(extractErrorMessage(entry.name, rc))
                    }
                    return@Thread
                }
                dests.add(dest)
            }
            runOnUiThread {
                endWork()
                lastPlainFilesState.value = dests
                onStatus("Share decrypted ${dests.joinToString { it.name }}.")
                ShareHelper.shareFiles(
                    this,
                    dests,
                    if (dests.size == 1) "Share ${dests[0].name}" else "Share decrypted files"
                )
            }
        }.start()
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
        beginWork()
        onStatus("Preparing ${entry.name}…")
        Thread {
            val dest = File(ShareHelper.shareDir(this), ShareHelper.safeName(entry.name))
            val rc = NativeBridge.exportFile(handle, volumePath, dest.absolutePath)
            runOnUiThread {
                endWork()
                if (rc != 0 || !dest.exists()) {
                    onStatus(extractErrorMessage(entry.name, rc))
                } else {
                    onStatus("Share ${entry.name} with WhatsApp, Gmail, Drive, or any app.")
                    ShareHelper.shareFiles(this, listOf(dest), "Share ${entry.name}")
                }
            }
        }.start()
    }

    private fun loadDir(
        handle: Long,
        path: String,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit,
        append: Boolean = false
    ) {
        if (handle <= 0) return
        beginWork()
        Thread {
            try {
                val offset = if (append) entriesState.value.size else 0
                val listed = NativeBridge.listDir(handle, if (path.isEmpty()) "/" else path, offset)
                val parsed = listed.mapNotNull { parseEntry(it) }
                runOnUiThread {
                    endWork()
                    if (parsed.size == 1 && parsed[0].name == "!error!") {
                        onStatus(listErrorMessage(parsed[0].size.toInt()))
                        return@runOnUiThread
                    }
                    val truncated = parsed.any { it.name == "!truncated!" }
                    val files = parsed.filter { it.name != "!truncated!" }
                    onEntries(if (append) entriesState.value + files else files)
                    listTruncatedState.value = truncated
                    if (truncated) onStatus("Folder listing truncated at ${NativeBridge.LIST_UI_MAX} entries. Tap Load more.")
                }
            } catch (_: Exception) {
                runOnUiThread {
                    endWork()
                    onStatus("Could not list the folder.")
                }
            }
        }.start()
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
            -5 -> "Not enough memory to open the volume."
            else -> "Open failed (code $code)."
        }
    }

    private fun listErrorMessage(code: Int): String {
        return when (code) {
            -6 -> "Opened the volume, but the filesystem is exFAT or otherwise unsupported. FAT only."
            -4 -> "Could not list that folder path."
            -5 -> "Not enough memory to list the folder."
            -1 -> "Could not read the folder from the volume."
            else -> "Could not list files (code $code)."
        }
    }

    private fun extractErrorMessage(name: String, rc: Int): String {
        return when (rc) {
            -6 -> "Could not extract $name. FAT only; exFAT is unsupported."
            -4 -> "Could not extract $name. Bad path."
            -5 -> "Could not extract $name. Not enough memory."
            -1 -> "Could not extract $name. Read failed."
            -2 -> "Could not extract $name. Wrong password or header."
            else -> "Could not extract $name (code $rc)."
        }
    }

    private fun importErrorMessage(name: String, rc: Int): String {
        return when (rc) {
            -6 -> "Could not copy $name. FAT only; folders are not created this way."
            -4 -> "Could not copy $name. Bad name or path."
            -5 -> "Could not copy $name. Volume is full, or the file is larger than 256 MiB."
            -3 -> "A file named $name already exists in this folder."
            -1 -> "Could not copy $name into the volume."
            else -> "Could not copy $name (code $rc)."
        }
    }

    private fun tryDeleteDocument(uri: Uri): Boolean {
        return try {
            DocumentsContract.deleteDocument(contentResolver, uri)
        } catch (_: Exception) {
            false
        }
    }

    private fun importFromDevice(
        handle: Long,
        dirPath: String,
        uri: Uri,
        move: Boolean,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit
    ) {
        if (handle <= 0) {
            onStatus("Open a volume first.")
            return
        }
        beginWork()
        onStatus(if (move) "Moving from device…" else "Copying from device…")
        Thread {
            var cache: File? = null
            try {
                val display = ShareHelper.displayName(this, uri) ?: "file"
                val name = ShareHelper.safeName(display)
                val outFile = File(cacheDir, "from-device-${System.nanoTime()}-$name")
                cache = outFile
                val input = contentResolver.openInputStream(uri)
                if (input == null) {
                    runOnUiThread {
                        endWork()
                        onStatus("Could not read that file from the device.")
                    }
                    return@Thread
                }
                input.use { src ->
                    outFile.outputStream().use { dest ->
                        copyStreamProgress(
                            src,
                            dest,
                            uriLength(uri),
                            if (move) "Reading from device" else "Reading from device"
                        )
                    }
                }
                val destDir = if (dirPath.isEmpty()) "/" else dirPath
                val rc = NativeBridge.importFile(handle, destDir, outFile.absolutePath, name)
                var deletedOriginal = false
                if (rc == 0 && move) {
                    deletedOriginal = tryDeleteDocument(uri)
                }
                runOnUiThread {
                    endWork()
                    when {
                        rc != 0 -> onStatus(importErrorMessage(name, rc))
                        move && !deletedOriginal -> {
                            onStatus("Copied $name into the volume. Could not delete the original; remove it in Files if you meant a move.")
                            loadDir(handle, dirPath, onEntries, onStatus)
                        }
                        move -> {
                            onStatus("Moved $name into the volume.")
                            loadDir(handle, dirPath, onEntries, onStatus)
                        }
                        else -> {
                            onStatus("Copied $name from the device into this folder.")
                            loadDir(handle, dirPath, onEntries, onStatus)
                        }
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    endWork()
                    onStatus("Could not copy that file into the volume.")
                }
            } finally {
                cache?.delete()
            }
        }.start()
    }

    private fun exportToDevice(
        handle: Long,
        dirPath: String,
        entry: VaultEntry,
        uri: Uri,
        move: Boolean,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit
    ) {
        if (handle <= 0) {
            onStatus("Open a volume first.")
            return
        }
        if (entry.isDir) {
            onStatus("Open the folder, then copy a file inside it.")
            return
        }
        beginWork()
        onStatus(if (move) "Moving ${entry.name} to device…" else "Copying ${entry.name} to device…")
        Thread {
            val dest = File(cacheDir, "to-device-${System.nanoTime()}-${ShareHelper.safeName(entry.name)}")
            try {
                val volumePath = joinDir(dirPath, entry.name)
                val rc = NativeBridge.exportFile(handle, volumePath, dest.absolutePath)
                if (rc != 0 || !dest.exists()) {
                    runOnUiThread {
                        endWork()
                        onStatus(extractErrorMessage(entry.name, rc))
                    }
                    return@Thread
                }
                contentResolver.openOutputStream(uri)?.use { out ->
                    dest.inputStream().use { input ->
                        copyStreamProgress(input, out, dest.length(), "Saving to device")
                    }
                } ?: run {
                    runOnUiThread {
                        endWork()
                        onStatus("Could not save ${entry.name} on the device.")
                    }
                    return@Thread
                }
                var deletedInVolume = false
                if (move) {
                    val dlt = NativeBridge.deleteFile(handle, volumePath)
                    deletedInVolume = dlt == 0
                }
                runOnUiThread {
                    endWork()
                    when {
                        move && deletedInVolume -> {
                            onStatus("Moved ${entry.name} to the device.")
                            loadDir(handle, dirPath, onEntries, onStatus)
                        }
                        move -> onStatus("Copied ${entry.name} to the device, but could not remove it from the volume.")
                        else -> onStatus("Copied ${entry.name} to the device.")
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    endWork()
                    onStatus("Could not copy ${entry.name} to the device.")
                }
            } finally {
                dest.delete()
            }
        }.start()
    }

    private fun formatFatStamp(date: Int, time: Int): String {
        if (date == 0) return "unknown"
        val year = 1980 + (date shr 9)
        val month = (date shr 5) and 0xF
        val day = date and 0x1F
        val hour = time shr 11
        val min = (time shr 5) and 0x3F
        return "%04d-%02d-%02d %02d:%02d UTC".format(year, month, day, hour, min)
    }

    private fun formatEntryProperties(entry: VaultEntry): String {
        val kind = if (entry.isDir) "Folder" else "File"
        val size = if (entry.isDir) "" else ", ${formatSize(entry.size)}"
        return "$kind ${entry.name}$size, modified ${formatFatStamp(entry.dosDate, entry.dosTime)}. FAT only; this is not a mounted drive."
    }

    private fun mkdirInVolume(
        handle: Long,
        dirPath: String,
        name: String,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit
    ) {
        if (handle <= 0) {
            onStatus("Open a volume first.")
            return
        }
        beginWork()
        Thread {
            val rc = NativeBridge.mkdir(handle, if (dirPath.isEmpty()) "/" else dirPath, name)
            runOnUiThread {
                endWork()
                if (rc != 0) onStatus(importErrorMessage(name, rc))
                else {
                    onStatus("Created folder $name.")
                    loadDir(handle, dirPath, onEntries, onStatus)
                }
            }
        }.start()
    }

    private fun renameVaultEntry(
        handle: Long,
        dirPath: String,
        entry: VaultEntry,
        newName: String,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit
    ) {
        beginWork()
        Thread {
            val rc = NativeBridge.renameFile(handle, joinDir(dirPath, entry.name), newName)
            runOnUiThread {
                endWork()
                if (rc != 0) onStatus(importErrorMessage(entry.name, rc))
                else {
                    onStatus("Renamed ${entry.name} to $newName.")
                    loadDir(handle, dirPath, onEntries, onStatus)
                }
            }
        }.start()
    }

    private fun deleteVaultEntry(
        handle: Long,
        dirPath: String,
        entry: VaultEntry,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit
    ) {
        beginWork()
        Thread {
            val path = joinDir(dirPath, entry.name)
            val rc = if (entry.isDir) NativeBridge.rmdir(handle, path) else NativeBridge.deleteFile(handle, path)
            runOnUiThread {
                endWork()
                if (rc != 0) {
                    onStatus(
                        if (entry.isDir && rc == -3) "Folder $path is not empty."
                        else importErrorMessage(entry.name, rc)
                    )
                } else {
                    onStatus("Deleted ${entry.name}.")
                    loadDir(handle, dirPath, onEntries, onStatus)
                }
            }
        }.start()
    }

    private fun wipeFreeSpace(
        handle: Long,
        dirPath: String,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit
    ) {
        if (handle <= 0) {
            onStatus("Open a volume first.")
            return
        }
        beginWork()
        onStatus("Wiping free space…")
        Thread {
            val rc = NativeBridge.wipeFreeSpace(handle)
            runOnUiThread {
                endWork()
                if (rc != 0) onStatus("Could not wipe free space (code $rc). Read-only volumes refuse this.")
                else {
                    onStatus("Wiped unused FAT clusters. Deleted file contents in free space are overwritten.")
                    loadDir(handle, dirPath, onEntries, onStatus)
                }
            }
        }.start()
    }

    private fun restoreEmbeddedHeader(
        volumePath: String,
        password: String,
        pimText: String,
        useTextPassword: Boolean,
        useBiometric: Boolean,
        bioSecret: ByteArray?,
        keyfileUris: List<Uri>,
        currentHandle: Long,
        onHandle: (Long) -> Unit,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit
    ) {
        if (volumePath.isEmpty()) {
            onStatus("Choose a container first.")
            return
        }
        val text = if (useTextPassword) password else ""
        beginWork()
        onStatus("Restoring from the embedded backup header…")
        Thread {
            val temps = mutableListOf<File>()
            try {
                val (copied, err) = copyUnlockKeyfiles(useBiometric, bioSecret, keyfileUris)
                if (err != null) {
                    runOnUiThread {
                        endWork()
                        onStatus(err)
                    }
                    return@Thread
                }
                temps.addAll(copied)
                if (currentHandle > 0) NativeBridge.closeVolume(currentHandle)
                val rc = NativeBridge.restoreHeaders(
                    volumePath,
                    "",
                    text,
                    pimText.toIntOrNull() ?: 0,
                    temps.map { it.absolutePath }.toTypedArray()
                )
                runOnUiThread {
                    endWork()
                    onHandle(0)
                    onEntries(emptyList())
                    onStatus(
                        if (rc == 0)
                            "Restored from embedded backup header. Open with the same password, PIM, and keyfiles."
                        else
                            headerErrorMessage(rc)
                    )
                }
            } catch (_: Exception) {
                runOnUiThread {
                    endWork()
                    onStatus("Restore from embedded backup header failed.")
                }
            } finally {
                temps.forEach { KeyfileIo.wipe(it) }
            }
        }.start()
    }

    private fun formatUpdateStatus(result: SourcePin.CheckResult): String {
        val bits = mutableListOf(
            when {
                result.newer ->
                    "Update ${result.remoteVersion} available from source. ${result.notes}"
                result.sourceMoved ->
                    "Same VC Port ${SourcePin.localVersion}, VeraCrypt pin moved to ${result.remoteUpstreamCommit.take(12)}. Rebuild from source."
                else ->
                    "Already up to date (${SourcePin.localVersion})."
            }
        )
        if (result.officialNewer && result.officialVersion.isNotEmpty()) {
            bits.add(
                "Official VeraCrypt ${result.officialVersion} is published. This build still compiles ${SourcePin.upstreamVersion}. Merge with scripts/sync-upstream.sh and rebuild. This app does not fetch their source."
            )
        }
        if (result.sourceDegraded && result.sourceWarning.isNotEmpty()) bits.add(result.sourceWarning)
        if (result.downloadUrl.isNotEmpty()) bits.add(result.downloadUrl)
        if (result.apkSha256.isNotEmpty()) bits.add("SHA-256 ${result.apkSha256}")
        else if (result.newer) bits.add("No APK hash in the manifest yet; GitHub APKs are debug-signed previews.")
        bits.add("This app does not install itself. Offline again.")
        return bits.joinToString(" ")
    }

    private fun parseEntry(line: String): VaultEntry? {
        val parts = line.split('\t')
        if (parts.isEmpty() || parts[0].isEmpty()) return null
        val isDir = parts.getOrNull(1) == "1"
        val size = parts.getOrNull(2)?.toLongOrNull() ?: 0L
        val dosDate = parts.getOrNull(3)?.toIntOrNull() ?: 0
        val dosTime = parts.getOrNull(4)?.toIntOrNull() ?: 0
        return VaultEntry(parts[0], isDir, size, dosDate, dosTime)
    }

    private fun copyIncomingAsContainer(file: File): String {
        val name = ShareHelper.sanitizeDisguiseName(file.name)
        val outFile = File(cacheDir, name)
        file.copyTo(outFile, overwrite = true)
        return outFile.absolutePath
    }

    private fun copyToCache(uri: Uri): String {
        val display = ShareHelper.displayName(this, uri) ?: "volume.hc"
        val name = ShareHelper.sanitizeDisguiseName(display)
        val input = contentResolver.openInputStream(uri) ?: return ""
        val outFile = File(cacheDir, name)
        outFile.outputStream().use { output -> input.copyTo(output) }
        input.close()
        return outFile.absolutePath
    }
}

@Composable
private fun VaultPane(
    dirPath: String,
    entries: List<VaultEntry>,
    selectedNames: Set<String>,
    truncated: Boolean,
    busy: Boolean,
    onUp: () -> Unit,
    onOpen: (VaultEntry) -> Unit,
    onShare: (VaultEntry) -> Unit,
    onCopyFromDevice: () -> Unit,
    onMoveFromDevice: () -> Unit,
    onCopyToDevice: () -> Unit,
    onMoveToDevice: () -> Unit,
    onNewFolder: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
    onWipeFreeSpace: () -> Unit,
    onMore: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dirPath.isNotEmpty()) {
                OutlinedButton(onClick = onUp, enabled = !busy) { Text("Up") }
                Spacer(Modifier.padding(8.dp))
            }
            Text(
                if (dirPath.isEmpty()) "/" else "/$dirPath",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCopyFromDevice,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Copy from device") }
                OutlinedButton(
                    onClick = onMoveFromDevice,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Move from device") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onCopyToDevice,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Copy to device") }
                OutlinedButton(
                    onClick = onMoveToDevice,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Move to device") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onNewFolder,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("New folder") }
                OutlinedButton(
                    onClick = onRename,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Rename") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Delete") }
                OutlinedButton(
                    onClick = onProperties,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Properties") }
            }
            OutlinedButton(
                onClick = onWipeFreeSpace,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Wipe free space") }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(VcDesktopBlue)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text("Name", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("Size", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        if (entries.isEmpty() && !busy) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("This folder is empty.", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tap Copy from device to add a file.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)
        ) {
            itemsIndexed(entries, key = { index, entry -> "$index:${entry.name}" }) { _, entry ->
                val selected = entry.name in selectedNames
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (selected) colors.primaryContainer else colors.surface)
                        .clickable(enabled = !busy) { onOpen(entry) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .padding(end = 12.dp)
                            .size(28.dp)
                            .background(
                                if (selected) colors.primary else colors.surfaceVariant,
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text(
                                if (entry.isDir) "▸" else "•",
                                color = colors.onSurfaceVariant,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (entry.isDir) "Folder — tap to open" else formatSize(entry.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    if (!entry.isDir) {
                        TextButton(onClick = { onShare(entry) }, enabled = !busy) {
                            Text("Share decrypted")
                        }
                    }
                }
                HorizontalDivider(color = colors.outline.copy(alpha = 0.4f))
            }
            if (truncated) {
                item {
                    OutlinedButton(
                        onClick = onMore,
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) { Text("Load more") }
                }
            }
        }
    }
}

private fun formatSize(size: Long): String {
    if (size < 1024) return "$size B"
    if (size < 1024 * 1024) return "${size / 1024} KB"
    return "${size / (1024 * 1024)} MB"
}
