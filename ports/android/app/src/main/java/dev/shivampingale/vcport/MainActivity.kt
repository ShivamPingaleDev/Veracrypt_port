package dev.shivampingale.vcport

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.painterResource
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
    private val pimState = mutableStateOf("0")
    private val createPimState = mutableStateOf("0")
    private val createHiddenPimState = mutableStateOf("0")
    private val newPimState = mutableStateOf("0")
    private val hiddenProtectPimState = mutableStateOf("0")
    private val keyfileUrisState = mutableStateOf(listOf<Uri>())
    private val basketUrisState = mutableStateOf(listOf<Uri>())
    private val basketHashesState = mutableStateOf(mapOf<String, String>())
    private val hiddenKeyfileUrisState = mutableStateOf(listOf<Uri>())
    private val keyfileGenNameState = mutableStateOf("keyfile.bin")
    private val keyfileGenCountState = mutableStateOf("1")
    private val containerLabelState = mutableStateOf("")
    private val handleState = mutableStateOf(0L)
    private val entriesState = mutableStateOf(listOf<VaultEntry>())
    private val dirPathState = mutableStateOf("")
    private val listTruncatedState = mutableStateOf(false)
    private val busyState = mutableStateOf(false)
    private val tabState = mutableIntStateOf(0)
    private val lastPlainFilesState = mutableStateOf(listOf<File>())
    private val createPasswordState = mutableStateOf("")
    private val createHiddenPasswordState = mutableStateOf("")
    private val createCipherState = mutableStateOf(NativeBridge.DEFAULT_CIPHER)
    private val createKdfState = mutableStateOf(NativeBridge.DEFAULT_KDF)
    private val createFilesystemState = mutableStateOf("FAT")
    private val createFileNameState = mutableStateOf("volume.hc")
    private val createSizeAmountState = mutableStateOf("16")
    private val createSizeUnitState = mutableStateOf(SizeUnit.MiB)
    private val createHiddenState = mutableStateOf(false)
    private val createHiddenSizeAmountState = mutableStateOf("4")
    private val createHiddenSizeUnitState = mutableStateOf(SizeUnit.MiB)
    private val entropyPercentState = mutableIntStateOf(0)
    private val newPasswordState = mutableStateOf("")
    private val hiddenProtectPasswordState = mutableStateOf("")
    private var suppressLock = false
    private var wrapHold = ""
    private var containerPfd: ParcelFileDescriptor? = null
    /** File pickers stop this activity. Do not wipe the wrap password in that gap. */
    private fun holdLockForPicker() {
        suppressLock = true
    }

    /** Instrumented tests add basket files without the system picker. */
    @androidx.annotation.VisibleForTesting
    fun testingAddBasketFiles(files: List<File>) {
        val uris = files.map { Uri.fromFile(it) }
        runOnUiThread {
            basketUrisState.value = basketUrisState.value + uris
            val extra = uris.associate { uri ->
                uri.toString() to (BasketHash.sha256(this, uri) ?: "")
            }
            basketHashesState.value = basketHashesState.value + extra
            tabState.intValue = 1
        }
    }

    private fun lookPrefs() = getSharedPreferences("vc_port_look", MODE_PRIVATE)

    private fun loadSkin(): VcSkin {
        if (!BuildConfig.ENABLE_SKINS) return VcSkin.Desktop
        val name = lookPrefs().getString("skin", VcSkin.Desktop.name) ?: VcSkin.Desktop.name
        return VcSkin.entries.find { it.name == name } ?: VcSkin.Desktop
    }

    private fun saveSkin(skin: VcSkin) {
        if (!BuildConfig.ENABLE_SKINS) return
        lookPrefs().edit().putString("skin", skin.name).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Hardening.protectWindow(this)
        handleIncoming(intent)
        setContent {
            var skin by remember { mutableStateOf(loadSkin()) }
            VcPortTheme(skin = skin) {
                var path by pathState
                var containerUri by containerUriState
                var password by passwordState
                var pim by pimState
                var wrapPassword by wrapPasswordState
                var keyfileUris by keyfileUrisState
                var basketUris by basketUrisState
                var basketHashes by basketHashesState
                var hiddenKeyfileUris by hiddenKeyfileUrisState
                var keyfileGenName by keyfileGenNameState
                var keyfileGenCount by keyfileGenCountState
                var containerLabel by containerLabelState
                var useTextPassword by remember { mutableStateOf(true) }
                var status by statusState
                var entries by entriesState
                var handle by handleState
                var dirPath by dirPathState
                var listTruncated by listTruncatedState
                var busy by busyState
                var tab by tabState
                val tabScroll = rememberScrollState()
                LaunchedEffect(tab) { tabScroll.scrollTo(0) }
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
                LaunchedEffect(basketUris, createHidden, createHiddenSizeAmount, createHiddenSizeUnit) {
                    if (basketUris.isEmpty()) return@LaunchedEffect
                    val hidden = if (createHidden) {
                        SizeUnits.toBytes(createHiddenSizeAmount.toLongOrNull() ?: 0L, createHiddenSizeUnit)
                    } else {
                        0L
                    }
                    val need = volumeBytesForBasket(SizeUnits.MIN_VOLUME, basketUris, hidden)
                    val (n, unit) = SizeUnits.fit(need)
                    createSizeAmount = n.toString()
                    createSizeUnit = unit
                }
                var entropyPercent by entropyPercentState
                var newPassword by newPasswordState
                var newPim by newPimState
                var headerKdf by remember { mutableStateOf("(keep current)") }
                var useBackupHeader by remember { mutableStateOf(false) }
                var readOnlyOpen by remember { mutableStateOf(false) }
                var trueCryptMode by remember { mutableStateOf(false) }
                var protectHidden by remember { mutableStateOf(false) }
                var hiddenProtectPassword by hiddenProtectPasswordState
                var hiddenProtectPim by hiddenProtectPimState
                var namePrompt by remember { mutableStateOf<String?>(null) }
                var namePromptValue by remember { mutableStateOf("") }
                var pendingExportFile by remember { mutableStateOf<File?>(null) }
                var pendingSaveName by remember { mutableStateOf("") }
                var pendingSaveMove by remember { mutableStateOf(false) }
                var pendingFromDeviceMove by remember { mutableStateOf(false) }
                var selectedNames by remember { mutableStateOf(setOf<String>()) }
                var lastPlainFiles by lastPlainFilesState
                var incoming by incomingState
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
                        val nextTitle = phase.ifEmpty { status }
                        val nextPct = NativeBridge.progressPercent()
                        if (overlayTitle != nextTitle) overlayTitle = nextTitle
                        if (overlayPercent != nextPct) overlayPercent = nextPct
                        delay(100)
                    }
                }
                LaunchedEffect(tab) {
                    if (tab == 2) {
                        newPim = pim
                    }
                }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                    if (uri != null) {
                        ShareHelper.persistRead(this@MainActivity, uri)
                        incoming = null
                        containerUri = uri
                        containerLabel = ShareHelper.displayName(this@MainActivity, uri) ?: "container"
                        copyContainerAsync(uri) { copied ->
                            path = copied
                            status = if (copied.isEmpty())
                                "Could not open the container. Not enough free space, or the Files picker could not be read."
                            else
                                "Selected $containerLabel. Open volume to browse folders here."
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
                    val kept = keyfileUris.toMutableList()
                    var failed: String? = null
                    for (uri in uris) {
                        ShareHelper.persistRead(this@MainActivity, uri)
                        val copied = KeyfileIo.copyOwned(this@MainActivity, uri)
                        if (copied == null) {
                            failed = ShareHelper.displayName(this@MainActivity, uri) ?: "keyfile"
                        } else {
                            val owned = Uri.fromFile(copied)
                            if (owned !in kept) kept += owned
                        }
                    }
                    keyfileUris = kept
                    status = if (failed != null)
                        "Could not read $failed. Pick it again, or open it from the Files app with VC Port. Any file can be a keyfile; VeraCrypt uses the first 1 MiB."
                    else
                        "Keyfile(s) added. Any file works; only the first 1 MiB is mixed, same as VeraCrypt on a computer."
                }
                val hiddenKeyfilePicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenMultipleDocuments()
                ) { uris: List<Uri> ->
                    val kept = hiddenKeyfileUris.toMutableList()
                    var failed: String? = null
                    for (uri in uris) {
                        ShareHelper.persistRead(this@MainActivity, uri)
                        val copied = KeyfileIo.copyOwned(this@MainActivity, uri)
                        if (copied == null) {
                            failed = ShareHelper.displayName(this@MainActivity, uri) ?: "keyfile"
                        } else {
                            val owned = Uri.fromFile(copied)
                            if (owned !in kept) kept += owned
                        }
                    }
                    hiddenKeyfileUris = kept
                    status = if (failed != null)
                        "Could not read $failed as a nested keyfile."
                    else
                        "Nested keyfile(s) added. Same rule as outer: first 1 MiB."
                }
                val basketPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenMultipleDocuments()
                ) { uris: List<Uri> ->
                    if (uris.isEmpty()) return@rememberLauncherForActivityResult
                    val kept = basketUris.toMutableList()
                    for (uri in uris) {
                        ShareHelper.persistRead(this@MainActivity, uri)
                        if (uri !in kept) kept += uri
                    }
                    basketUris = kept
                    val hidden = if (createHidden) {
                        SizeUnits.toBytes(
                            createHiddenSizeAmount.toLongOrNull() ?: 0L,
                            createHiddenSizeUnit
                        )
                    } else {
                        0L
                    }
                    status = "Basket: ${basketSummary(kept, hidden)}. SHA-256 runs in this session only."
                    Thread {
                        val extra = kept.associate { uri ->
                            uri.toString() to (BasketHash.sha256(this@MainActivity, uri) ?: "")
                        }.filterValues { it.isNotEmpty() }
                        runOnUiThread {
                            basketHashes = basketHashes + extra
                        }
                    }.start()
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
                            incoming = null
                            containerUri = uri
                            containerLabel = ShareHelper.displayName(this@MainActivity, uri)
                                ?: File(path).name
                            holdLockForPicker()
                            copyContainerAsync(uri) { copied ->
                                if (copied.isNotEmpty()) path = copied
                                status = "Saved $containerLabel. That file is selected. Open volume, or Share encrypted."
                            }
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
                val unwrapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                    val secret = wrapHold.ifEmpty { wrapPassword }
                    if (uri != null) {
                        unwrapSelectedFile(uri, secret, { status = it }) { plain ->
                            pendingExportFile = plain
                            lastPlainFiles = listOf(plain)
                            holdLockForPicker()
                            window.decorView.post {
                                holdLockForPicker()
                                toolSaver.launch(plain.name)
                            }
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
                    createPassword = ""
                    createHiddenPassword = ""
                    hiddenProtectPassword = ""
                    newPassword = ""
                    handle = 0
                    entries = emptyList()
                    dirPath = ""
                    basketUris = emptyList()
                    status = "Panic wipe complete. Cache, clipboard, and leftovers are gone."
                }

                Box(Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.imePadding(),
                    containerColor = Color.Transparent,
                    topBar = {
                        Box(Modifier.background(skinHeaderBrush(skin))) {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.onPrimary)
                                    Spacer(Modifier.padding(6.dp))
                                    Column {
                                        Text(
                                            if (BuildConfig.ENABLE_SKINS) "VC Port Looks" else "VC Port",
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Text(
                                            if (NativeBridge.isOpen(handle))
                                                "Mounted in this app"
                                            else
                                                "Stay offline. F-Droid: no network.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colors.onPrimary.copy(alpha = 0.85f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            },
                            actions = {
                                if (NativeBridge.isOpen(handle)) {
                                    TextButton(
                                        onClick = { lockSession() },
                                        enabled = !busy,
                                        colors = ButtonDefaults.textButtonColors(contentColor = colors.onPrimary)
                                    ) { Text("Dismount") }
                                }
                                TextButton(
                                    onClick = { runPanic() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = colors.error)
                                ) {
                                    Icon(Icons.Filled.Warning, contentDescription = null)
                                    Spacer(Modifier.padding(4.dp))
                                    Text("Panic wipe")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color.Transparent,
                                titleContentColor = colors.onPrimary,
                                actionIconContentColor = colors.onPrimary
                            )
                        )
                        }
                    },
                    bottomBar = {
                        val incomingFile = incoming
                        val selectedFiles = entries.filter { it.name in selectedNames && !it.isDir }
                        val cipherName = when {
                            incomingFile != null && ShareHelper.looksLikeWrap(incomingFile.name) ->
                                incomingFile.name
                            containerLabel.isNotEmpty() -> containerLabel
                            incomingFile != null -> incomingFile.name
                            else -> containerUri?.let { ShareHelper.displayName(this@MainActivity, it) }
                                ?: path.takeIf { it.isNotEmpty() && !nativePathIsInternal(it) }?.let { File(it).name }
                        }
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
                                    pickEncrypted = {
                                        holdLockForPicker()
                                        shareEncPicker.launch(arrayOf("*/*"))
                                    },
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
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                        incoming?.let { file ->
                            VcCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Text("Received ${file.name} from another app.", style = MaterialTheme.typography.titleMedium)
                                FilledTonalButton(
                                    onClick = {
                                        path = copyIncomingAsContainer(file)
                                        containerLabel = file.name
                                        containerUri = null
                                        tab = 0
                                        status = "Selected ${file.name}. Open volume to browse folders here."
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
                                    SecretField(
                                        wrapPassword,
                                        { wrapPassword = it },
                                        "Wrap password (never stored)",
                                        enabled = !busy
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            unwrapIncomingFile(file, wrapPassword) { status = it }
                                        },
                                        enabled = !busy,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Decrypt wrap") }
                                }
                            }
                        }
                        if (NativeBridge.isOpen(handle)) {
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
                                onGoToPath = { target ->
                                    dirPath = target
                                    selectedNames = emptySet()
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
                                selectedTabIndex = tab.coerceIn(0, 2),
                                containerColor = if (skin == VcSkin.Desktop) {
                                    colors.surface.copy(alpha = 0.94f)
                                } else {
                                    colors.background.copy(alpha = 0.35f)
                                },
                                contentColor = colors.primary,
                                edgePadding = 8.dp,
                                indicator = { positions ->
                                    val i = tab.coerceIn(0, positions.lastIndex.coerceAtLeast(0))
                                    if (positions.isNotEmpty()) {
                                        SkinTabIndicator(positions[i])
                                    }
                                }
                            ) {
                                Tab(selected = tab == 0, onClick = { tab = 0 }, modifier = Modifier.testTag("tab_volume"), text = { Text("Volume") })
                                Tab(selected = tab == 1, onClick = { tab = 1 }, modifier = Modifier.testTag("tab_create"), text = { Text("Create") })
                                Tab(selected = tab == 2, onClick = { tab = 2 }, modifier = Modifier.testTag("tab_tools"), text = { Text("Tools") })
                            }
                            Column(
                                Modifier
                                    .verticalScroll(tabScroll)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                when (tab) {
                                    1 -> {
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
                                                                ShareHelper.displayName(this@MainActivity, uri) ?: uri.toString(),
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
                                                    basketPicker.launch(arrayOf("*/*"))
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
                                                            SensitiveClipboard.copyOnce(this@MainActivity, createPassword)
                                                            status = "Copied once. Clipboard clears in 30 seconds. No history is kept."
                                                        }
                                                    },
                                                    modifier = Modifier.weight(1f).testTag("copy_once")
                                                ) { Text("Copy once") }
                                                OutlinedButton(
                                                    onClick = {
                                                        SensitiveClipboard.forget(this@MainActivity)
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
                                                onClick = {
                                                    holdLockForPicker()
                                                    keyfilePicker.launch(arrayOf("*/*"))
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
                                                    } else {
                                                        offerGeneratedKeyfileCopies(files, { status = it }) { name ->
                                                            pendingExportFile = files.first()
                                                            holdLockForPicker()
                                                            window.decorView.post {
                                                                holdLockForPicker()
                                                                toolSaver.launch(name)
                                                            }
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
                                                                SensitiveClipboard.copyOnce(this@MainActivity, createHiddenPassword)
                                                                status = "Copied nested password once. Clipboard clears in 30 seconds. No history is kept."
                                                            }
                                                        },
                                                        modifier = Modifier.weight(1f).testTag("copy_nested_once")
                                                    ) { Text("Copy nested once") }
                                                    OutlinedButton(
                                                        onClick = {
                                                            SensitiveClipboard.forget(this@MainActivity)
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
                                                            ShareHelper.displayName(this@MainActivity, uri) ?: uri.toString(),
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
                                                        hiddenKeyfilePicker.launch(arrayOf("*/*"))
                                                    },
                                                    enabled = !busy,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) { Text("Add nested keyfiles") }
                                                OutlinedButton(
                                                    onClick = {
                                                        val files = generateSessionKeyfiles(keyfileGenCount, keyfileGenName, nested = true)
                                                        if (files.isEmpty()) {
                                                            status = "Nested keyfile generator failed."
                                                        } else {
                                                            offerGeneratedKeyfileCopies(files, { status = it }) { name ->
                                                                pendingExportFile = files.first()
                                                                holdLockForPicker()
                                                                window.decorView.post {
                                                                    holdLockForPicker()
                                                                    toolSaver.launch(name)
                                                                }
                                                            }
                                                        }
                                                    },
                                                    enabled = !busy,
                                                    modifier = Modifier.fillMaxWidth()
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
                                                            password = createPassword
                                                            pim = createPim
                                                        },
                                                        onStatus = { status = it },
                                                        onSaved = {
                                                            NativeBridge.resetEntropy()
                                                            entropyPercent = 0
                                                            holdLockForPicker()
                                                            window.decorView.post {
                                                                holdLockForPicker()
                                                                createSaver.launch(ShareHelper.sanitizeDisguiseName(createFileName))
                                                            }
                                                        }
                                                    )
                                                },
                                                enabled = !busy && entropyPercent >= 100,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Create volume") }
                                        }
                                    }
                                    2 -> {
                                        if (BuildConfig.ENABLE_SKINS) {
                                            VcCard {
                                                Text("Looks (this phone)", style = MaterialTheme.typography.titleMedium)
                                                VcHint("Desktop, Cyberpunk, Matrix, MAGI, Signal. Same app id — this APK replaces the Desktop-only install. Inspired drawing, not affiliated.")
                                                VcSkin.entries.forEach { option ->
                                                    val selected = skin == option
                                                    if (selected) {
                                                        Button(
                                                            onClick = {
                                                                skin = option
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
                                                        ) { LookPickerLabel(option, selected = true) }
                                                    } else {
                                                        OutlinedButton(
                                                            onClick = {
                                                                skin = option
                                                                saveSkin(option)
                                                            },
                                                            enabled = !busy,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .testTag(option.tag)
                                                        ) { LookPickerLabel(option, selected = false) }
                                                    }
                                                }
                                            }
                                        }
                                        VcCard {
                                            Text("Volume header", style = MaterialTheme.typography.titleMedium)
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
                                                        successMessage = "Changed volume password. Open with the new password and the same keyfiles."
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
                                                        keyfileUris = keyfileUris,
                                                        currentHandle = handle,
                                                        onHandle = { handle = it },
                                                        onEntries = { entries = it },
                                                        onStatus = { status = it },
                                                        onSaved = { file ->
                                                            pendingExportFile = file
                                                            holdLockForPicker()
                                                            window.decorView.post {
                                                                holdLockForPicker()
                                                                toolSaver.launch("volume-header.bak")
                                                            }
                                                        }
                                                    )
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Backup volume header") }
                                            OutlinedButton(
                                                onClick = {
                                                    holdLockForPicker()
                                                    restoreHeaderPicker.launch(arrayOf("*/*"))
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
                                                    if (!NativeBridge.isOpen(handle)) {
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
                                            VcHint("Any extension. Generate several, then Add keyfiles if they are not already in this session.")
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedTextField(
                                                    keyfileGenName,
                                                    { keyfileGenName = it.take(120) },
                                                    label = { Text("Keyfile name (any extension)") },
                                                    modifier = Modifier.weight(1f),
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
                                                        status = "Keyfile generator failed."
                                                    } else {
                                                        offerGeneratedKeyfileCopies(files, { status = it }) { name ->
                                                            pendingExportFile = files.first()
                                                            holdLockForPicker()
                                                            window.decorView.post {
                                                                holdLockForPicker()
                                                                toolSaver.launch(name)
                                                            }
                                                        }
                                                    }
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
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
                                        }
                                        VcCard {
                                            Text("Wipe cached passwords", style = MaterialTheme.typography.titleMedium)
                                            OutlinedButton(
                                                onClick = {
                                                    lockSession()
                                                    password = ""
                                                    wrapPassword = ""
                                                    handle = 0
                                                    entries = emptyList()
                                                    status = "Wipe cached passwords complete. Volume closed."
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Wipe cached passwords") }
                                        }
                                        VcCard {
                                            Text("Leftover wrap", style = MaterialTheme.typography.titleMedium)
                                            VcHint("Decrypt a leftover .vcpw.")
                                            SecretField(
                                                wrapPassword,
                                                { wrapPassword = it },
                                                "Wrap password (never stored)",
                                                enabled = !busy
                                            )
                                            OutlinedButton(
                                                onClick = {
                                                    if (wrapPassword.isEmpty()) {
                                                        status = "Enter the wrap password first. It is not stored."
                                                    } else {
                                                        wrapHold = wrapPassword
                                                        holdLockForPicker()
                                                        unwrapPicker.launch(arrayOf("*/*"))
                                                    }
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Decrypt wrap") }
                                        }
                                        VcCard {
                                            Text("Not on this phone", style = MaterialTheme.typography.titleMedium)
                                            VcHint("Root / jailbreak: this app will not ask for superuser.")
                                            VcHint("Device encryption: this app encrypts VeraCrypt container files (any file name). It cannot encrypt the phone's operating system. Android already encrypts the device with your screen lock.")
                                            VcHint("Security tokens: PKCS#11 smart cards are not available here. Export a keyfile on a computer, then Add keyfiles.")
                                            VcHint("Desktop leftovers: mount as a drive, Select Device / Auto-Mount All Devices, system encryption, rescue disk, traveler disk, volume expander, Quick Format, dynamic sparse containers, favorite volumes, driver password cache, VeraCrypt background task, in-place partition encrypt/decrypt, hotkeys, language files, NTFS/ext, PKCS#11 tokens, and a DocumentsProvider browse of an unlocked volume. Phone volumes are FAT or exFAT file containers. Online help is not fetched while Stay offline. English UI only.")
                                        }
                                        VcCard {
                                            Text("About / licenses", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "“We must defend our own privacy if we expect to have any.” — Eric Hughes, A Cypherpunk’s Manifesto (1993)",
                                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                                color = colors.onSurfaceVariant
                                            )
                                            VcHint("Portions of this product are based in part on TrueCrypt, freely available at http://www.truecrypt.org/")
                                            VcHint("VC Port original code is Apache License 2.0. The volume core is VeraCrypt (Apache 2.0 / TrueCrypt License 3.0). You may not call this app VeraCrypt. There is no key escrow and no intelligence or police backdoor. A nation-state implant still wins. Not unbreakable.")
                                            VcHint("Contact: Shivam Mangesh Pingale — shivampingaledev@proton.me · shivampingaledev@gmail.com")
                                            VcHint("Footnote: A programming noob still doing a five-year IT engineering degree (graduate summer 2027). Just trying to make something better that he likes to use, without much knowledge. Open to suggestions and advice.")
                                            VcHint(SourcePin.describeBuild())
                                            VcHint("No ads, analytics, crash reporters, or INTERNET permission. Passwords stay on this device. Updates come from a rebuild of this source. The app does not install itself. Merge with scripts/sync-upstream.sh and rebuild.")
                                        }
                                    }
                                    else -> {
                                        VcCard {
                                            Text("VeraCrypt-compatible. F-Droid: no network.")
                                            VcHint("Stay offline by default. A compelled password still wins — prefer a long password and a keyfile. This is not unbreakable.")
                                            Button(
                                                onClick = {
                                                    holdLockForPicker()
                                                    picker.launch(arrayOf("*/*"))
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Choose container") }
                                            if (containerLabel.isNotEmpty()) {
                                                Text("Selected: $containerLabel", style = MaterialTheme.typography.bodyMedium)
                                                val shownPath = path.takeIf { it.isNotEmpty() && !nativePathIsInternal(it) }
                                                if (shownPath != null) {
                                                    Text(shownPath, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                            VcHint("USB/OTG: a file on the stick, not the whole disk.")
                                            Button(
                                                onClick = {
                                                    holdLockForPicker()
                                                    shareEncPicker.launch(arrayOf("*/*"))
                                                },
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
                                        VcCard {
                                            Text("Volume password", style = MaterialTheme.typography.titleMedium)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    useTextPassword,
                                                    { useTextPassword = it },
                                                    enabled = !busy
                                                )
                                                Text("Password")
                                            }
                                            if (useTextPassword) {
                                                SecretField(
                                                    password,
                                                    { password = it },
                                                    "Password",
                                                    enabled = !busy
                                                )
                                            }
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
                                            VcHint("Same as desktop: pick several in Files (long-press). Any extension. First 1 MiB of each.")
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
                                                onClick = {
                                                    holdLockForPicker()
                                                    keyfilePicker.launch(arrayOf("*/*"))
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Add keyfiles") }
                                            Text("Mount options", style = MaterialTheme.typography.titleSmall)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(useBackupHeader, { useBackupHeader = it }, enabled = !busy)
                                                Text("Use backup header")
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(readOnlyOpen, { readOnlyOpen = it }, enabled = !busy)
                                                Text("Read-only")
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(trueCryptMode, { trueCryptMode = it }, enabled = !busy)
                                                Text("TrueCrypt Mode")
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(protectHidden, { protectHidden = it }, enabled = !busy)
                                                Text("Protect hidden volume against damage caused by writing to outer volume")
                                            }
                                            if (protectHidden) {
                                                SecretField(
                                                    hiddenProtectPassword,
                                                    { hiddenProtectPassword = it },
                                                    "Password to hidden volume",
                                                    enabled = !busy
                                                )
                                                OutlinedTextField(
                                                    hiddenProtectPim,
                                                    { hiddenProtectPim = it },
                                                    label = { Text("Hidden volume PIM (0 = default)") },
                                                    modifier = Modifier.fillMaxWidth(),
                                                    enabled = !busy,
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                                )
                                            }
                                            Button(
                                                onClick = {
                                                    openVolumeWithFactors(
                                                        path = path,
                                                        password = password,
                                                        pimText = pim,
                                                        useTextPassword = useTextPassword,
                                                        keyfileUris = keyfileUris,
                                                        useBackupHeader = useBackupHeader,
                                                        readOnly = readOnlyOpen,
                                                        trueCryptMode = trueCryptMode,
                                                        protectHidden = protectHidden,
                                                        hiddenPassword = hiddenProtectPassword,
                                                        hiddenPimText = hiddenProtectPim,
                                                        currentHandle = handle,
                                                        onHandle = { handle = it },
                                                        onEntries = { entries = it },
                                                        onStatus = { status = it }
                                                    )
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Open volume") }
                                        }
                                    }
                                }
                            }
                        }
                    }
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

    override fun onDestroy() {
        releaseContainerPfd()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post {
            if (!busyState.value) {
                suppressLock = false
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (suppressLock || busyState.value) {
            return
        }
        dismountOnLeave()
    }

    private fun closeMountedVolume() {
        val handle = handleState.value
        if (NativeBridge.isOpen(handle)) NativeBridge.closeVolume(handle)
        handleState.value = 0L
        entriesState.value = emptyList()
        dirPathState.value = ""
        listTruncatedState.value = false
    }

    /**
     * Home / Recents: close a mounted volume so plaintext is not sitting in RAM.
     * Keep the Create wizard (generated passwords, nested checkbox, cipher/KDF/PIM,
     * basket, size) so Copy once can be pasted into Notes
     * and creation can continue. Dismount and Panic wipe still call [lockSession].
     */
    private fun dismountOnLeave() {
        val wasOpen = NativeBridge.isOpen(handleState.value)
        closeMountedVolume()
        lastPlainFilesState.value.forEach { Hardening.wipeFile(it) }
        lastPlainFilesState.value = emptyList()
        passwordState.value = ""
        hiddenProtectPasswordState.value = ""
        pimState.value = "0"
        hiddenProtectPimState.value = "0"
        if (wasOpen && !statusState.value.startsWith("Panic")) {
            statusState.value =
                "Dismounted. Create form kept. Dismount or Panic wipe also clears the generated password."
        }
    }

    private fun wipeRamSecrets() {
        createPasswordState.value = ""
        createHiddenPasswordState.value = ""
        hiddenProtectPasswordState.value = ""
        newPasswordState.value = ""
        passwordState.value = ""
        wrapPasswordState.value = ""
        wrapHold = ""
        pimState.value = "0"
        createPimState.value = "0"
        createHiddenPimState.value = "0"
        newPimState.value = "0"
        hiddenProtectPimState.value = "0"
        keyfileUrisState.value = emptyList()
        hiddenKeyfileUrisState.value = emptyList()
        basketHashesState.value = emptyMap()
        basketUrisState.value = emptyList()
        lastPlainFilesState.value.forEach { Hardening.wipeFile(it) }
        lastPlainFilesState.value = emptyList()
        resetCreateWizard()
    }

    private fun resetCreateWizard() {
        createCipherState.value = NativeBridge.DEFAULT_CIPHER
        createKdfState.value = NativeBridge.DEFAULT_KDF
        createFilesystemState.value = "FAT"
        createFileNameState.value = "volume.hc"
        createSizeAmountState.value = "16"
        createSizeUnitState.value = SizeUnit.MiB
        createHiddenState.value = false
        createHiddenSizeAmountState.value = "4"
        createHiddenSizeUnitState.value = SizeUnit.MiB
        entropyPercentState.intValue = 0
        NativeBridge.resetEntropy()
    }

    private fun lockSession() {
        closeMountedVolume()
        wipeRamSecrets()
        endWork()
        Hardening.wipeSessionFiles(this)
        if (!statusState.value.startsWith("Panic")) {
            statusState.value =
                "Dismounted. Passwords, keyfiles in memory, and decrypted copies wiped. Ciphertext stays."
        }
    }

    private fun panicWipe() {
        closeMountedVolume()
        wipeRamSecrets()
        releaseContainerPfd()
        Hardening.panic(this)
        basketUrisState.value = emptyList()
    }

    private fun beginWork(title: String = "") {
        NativeBridge.resetProgress()
        if (title.isNotEmpty()) statusState.value = title
        busyState.value = true
    }

    private fun endWork() {
        NativeBridge.resetProgress()
        busyState.value = false
    }

    private fun uriLength(uri: Uri): Long {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) {
                        val n = cursor.getLong(idx)
                        if (n > 0L) return n
                    }
                }
            }
        } catch (_: Exception) {
        }
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                if (pfd.statSize > 0L) return pfd.statSize
            }
        } catch (_: Exception) {
        }
        if (uri.scheme == "file") {
            val path = uri.path ?: return -1L
            val n = File(path).length()
            if (n > 0L) return n
        }
        return -1L
    }

    private fun volumeBytesForBasket(askedBytes: Long, uris: List<Uri>, hiddenBytes: Long): Long {
        var payload = 0L
        for (uri in uris) {
            val n = uriLength(uri)
            payload += if (n > 0) n else 1L shl 20
        }
        val overhead = if (uris.isEmpty()) 0L else 5L shl 20
        val need = payload + overhead + hiddenBytes
        var bytes = maxOf(askedBytes, need, SizeUnits.MIN_VOLUME)
        if (hiddenBytes > 0) bytes = maxOf(bytes, hiddenBytes * 2 + SizeUnits.MIN_VOLUME)
        return bytes
    }

    private fun basketSummary(uris: List<Uri>, hiddenBytes: Long = 0L): String {
        var bytes = 0L
        var unknown = false
        for (uri in uris) {
            val n = uriLength(uri)
            if (n > 0) bytes += n else unknown = true
        }
        val size = if (unknown && bytes == 0L) "size unknown" else SizeUnits.formatBytes(bytes)
        val files = if (uris.size == 1) "1 file" else "${uris.size} files"
        val need = volumeBytesForBasket(SizeUnits.MIN_VOLUME, uris, hiddenBytes)
        return "$files, about $size. Volume will be at least ${SizeUnits.formatBytes(need)}."
    }

    private fun uniqueDestName(raw: String, used: MutableSet<String>): String {
        var name = ShareHelper.safeName(raw).ifEmpty { "file" }
        if (name !in used) {
            used += name
            return name
        }
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 2
        while (true) {
            val cand = "$stem-$n$ext"
            if (cand !in used) {
                used += cand
                return cand
            }
            n++
        }
    }

    private fun importUriIntoVolume(handle: Long, uri: Uri, usedNames: MutableSet<String>): String? {
        val display = ShareHelper.displayName(this, uri) ?: "file"
        val name = uniqueDestName(display, usedNames)
        var cache: File? = null
        return try {
            var rc: Int? = null
            try {
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    if (pfd.statSize >= 0L) {
                        rc = NativeBridge.importFile(handle, "/", "/proc/self/fd/${pfd.fd}", name)
                    }
                }
            } catch (_: Exception) {
                rc = null
            }
            if (rc == null || rc == -1) {
                val outFile = File(cacheDir, "basket-${System.nanoTime()}-$name")
                cache = outFile
                val input = KeyfileIo.openReadable(this, uri)
                    ?: return "Could not read $display. Pick it again from Files."
                input.use { src ->
                    outFile.outputStream().use { dest ->
                        copyStreamProgress(src, dest, uriLength(uri), "Copying $name into volume")
                    }
                }
                rc = NativeBridge.importFile(handle, "/", outFile.absolutePath, name)
            }
            if (rc == 0) null else importErrorMessage(name, rc ?: -1, handle)
        } catch (_: Exception) {
            "Could not copy $display into the volume."
        } finally {
            cache?.let { KeyfileIo.wipe(it) }
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
        password: String,
        pimText: String,
        sizeBytes: Long,
        cipher: String,
        kdf: String,
        keyfileUris: List<Uri>,
        hidden: Boolean,
        hiddenPassword: String,
        hiddenPimText: String,
        hiddenSizeBytes: Long,
        hiddenKeyfileUris: List<Uri> = emptyList(),
        fileName: String,
        filesystem: String = "FAT",
        entropyPercent: Int,
        basketUris: List<Uri> = emptyList(),
        onPath: (String) -> Unit,
        onStatus: (String) -> Unit,
        onSaved: () -> Unit
    ) {
        if (password.isEmpty() && keyfileUris.isEmpty()) {
            onStatus("Type a volume password, or add a keyfile.")
            return
        }
        if (password.isNotEmpty() && password.length < 16 && keyfileUris.isEmpty()) {
            onStatus("Use Generate strong password, or type at least 16 characters. Nothing is saved.")
            return
        }
        if (entropyPercent < 100) {
            onStatus("Move your finger in the blank area until the randomness bar is full.")
            return
        }
        var nestedBytes = 0L
        if (hidden) {
            if (hiddenPassword.length < 16 && hiddenKeyfileUris.isEmpty()) {
                onStatus("Nested volume password must be at least 16 characters, and different from the outer password.")
                return
            }
            if (hiddenPassword.isNotEmpty() && hiddenPassword == password) {
                onStatus("Use a different password for the nested volume.")
                return
            }
            nestedBytes = hiddenSizeBytes
            if (nestedBytes < SizeUnits.MIN_VOLUME) {
                onStatus("Nested size must be at least 2 MiB and less than half the outer size, so the outer volume has room.")
                return
            }
        }
        if (basketUris.isEmpty() && (sizeBytes < SizeUnits.MIN_VOLUME || sizeBytes > SizeUnits.MAX_VOLUME)) {
            onStatus("Size must be at least 2 MiB and at most 64 GiB on this phone.")
            return
        }
        val bytes = volumeBytesForBasket(sizeBytes, basketUris, nestedBytes)
        if (bytes > SizeUnits.MAX_VOLUME) {
            onStatus("Basket is too large for a 64 GiB phone volume. Remove files.")
            return
        }
        if (bytes > cacheDir.usableSpace - (32L shl 20)) {
            onStatus("Not enough free space on this phone for ${SizeUnits.formatBytes(bytes)}.")
            return
        }
        if (hidden) {
            if (bytes < 8L * 1024L * 1024L) {
                onStatus("Nested volume needs an outer size of at least 8 MiB.")
                return
            }
            if (nestedBytes * 2 >= bytes) {
                onStatus("Nested size must be at least 2 MiB and less than half the outer size, so the outer volume has room.")
                return
            }
        }
        beginWork("Creating ${SizeUnits.formatBytes(bytes)} $cipher / $kdf volume…")
        Thread {
            val temps = mutableListOf<File>()
            val hiddenTemps = mutableListOf<File>()
            try {
                for (uri in keyfileUris) {
                    val copied = KeyfileIo.copyUri(this, uri)
                    if (copied == null) {
                        runOnUiThread {
                            endWork()
                            val name = ShareHelper.displayName(this, uri) ?: "keyfile"
                            onStatus("Could not read $name. Pick it again, or open it from the Files app with VC Port. Any file can be a keyfile.")
                        }
                        return@Thread
                    }
                    temps.add(copied)
                }
                if (hidden) {
                    for (uri in hiddenKeyfileUris) {
                        val copied = KeyfileIo.copyUri(this, uri)
                        if (copied == null) {
                            runOnUiThread {
                                endWork()
                                val name = ShareHelper.displayName(this, uri) ?: "keyfile"
                                onStatus("Could not read nested keyfile $name.")
                            }
                            return@Thread
                        }
                        hiddenTemps.add(copied)
                    }
                }
                val dest = File(cacheDir, ShareHelper.sanitizeDisguiseName(fileName))
                var fs = if (filesystem.equals("exFAT", ignoreCase = true)) "exFAT" else "FAT"
                if (bytes >= 4L * 1024L * 1024L * 1024L) fs = "exFAT"
                for (uri in basketUris) {
                    val n = uriLength(uri)
                    if (n > SizeUnits.FAT_MAX_FILE) fs = "exFAT"
                }
                val rc = NativeBridge.createVolume(
                    dest.absolutePath,
                    password,
                    pimText.toIntOrNull() ?: 0,
                    bytes,
                    cipher,
                    kdf,
                    temps.map { it.absolutePath }.toTypedArray(),
                    if (hidden) hiddenPassword else "",
                    hiddenPimText.toIntOrNull() ?: 0,
                    if (hidden) nestedBytes else 0L,
                    hiddenTemps.map { it.absolutePath }.toTypedArray(),
                    fs
                )
                var packed = 0
                var packFail: String? = null
                if (rc == 0 && basketUris.isNotEmpty()) {
                    NativeBridge.setProgress(0, "Copying basket into volume")
                    val handle = NativeBridge.openVolume(
                        dest.absolutePath,
                        password,
                        pimText.toIntOrNull() ?: 0,
                        false,
                        temps.map { it.absolutePath }.toTypedArray(),
                        false,
                        false,
                        "",
                        0
                    )
                    if (!NativeBridge.isOpen(handle)) {
                        packFail = "Created the volume, but could not open it to copy the basket."
                    } else {
                        val used = mutableSetOf<String>()
                        for ((index, uri) in basketUris.withIndex()) {
                            NativeBridge.setProgress(
                                (index * 100) / basketUris.size,
                                "Copying into volume"
                            )
                            val err = importUriIntoVolume(handle, uri, used)
                            if (err != null) {
                                packFail = err
                                break
                            }
                            packed++
                        }
                        val proof = File(cacheDir, "BASKET.sha256")
                        proof.writeText(
                            basketUris.mapNotNull { uri ->
                                val name = ShareHelper.displayName(this, uri) ?: "file"
                                val hex = basketHashesState.value[uri.toString()]
                                    ?: BasketHash.sha256(this, uri)
                                hex?.let { "$it  $name" }
                            }.joinToString("\n") + "\n"
                        )
                        NativeBridge.importFile(handle, "/", proof.absolutePath, "BASKET.sha256")
                        Hardening.wipeFile(proof)
                        NativeBridge.closeVolume(handle)
                    }
                }
                runOnUiThread {
                    endWork()
                    if (rc != 0) {
                        onStatus(createErrorMessage(rc))
                    } else {
                        onPath(dest.absolutePath)
                        var msg = "Created ${SizeUnits.formatBytes(bytes)} $cipher / $kdf $fs volume as ${dest.name} (standard VeraCrypt file; the name is only a disguise). Save a copy, then Open volume or Share encrypted. Same password, PIM, and keyfiles open it on a PC, Mac, or another phone — the extension is ignored."
                        if (packed > 0) {
                            msg += " Copied $packed file(s) from the basket into the volume. SHA-256 proof is BASKET.sha256 inside the volume."
                            if (packFail == null) {
                                basketUrisState.value = emptyList()
                                basketHashesState.value = emptyMap()
                            }
                        }
                        if (packFail != null) {
                            msg += " $packFail"
                        }
                        if (hidden) {
                            msg += " Nested volume is inside; open it with the nested password. Do not fill the outer volume."
                        }
                        onStatus(msg)
                        onSaved()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    endWork()
                    onStatus("Create failed.")
                }
            } finally {
                temps.forEach { KeyfileIo.wipe(it) }
                hiddenTemps.forEach { KeyfileIo.wipe(it) }
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

    private fun generateSessionKeyfiles(countText: String, pattern: String, nested: Boolean): List<File> {
        val n = (countText.toIntOrNull() ?: 1).coerceIn(1, 8)
        val name = ShareHelper.sanitizeKeyfileName(pattern)
        val dir = KeyfileIo.keyfileDir(this)
        val files = mutableListOf<File>()
        for (i in 1..n) {
            val dest = KeyfileIo.uniqueNamed(dir, KeyfileIo.numberedName(name, i, n))
            if (NativeBridge.generateKeyfile(dest.absolutePath, 128) != 0) {
                files.forEach { KeyfileIo.wipe(it) }
                return emptyList()
            }
            files += dest
        }
        val uris = files.map { Uri.fromFile(it) }
        if (nested) {
            hiddenKeyfileUrisState.value = hiddenKeyfileUrisState.value + uris
        } else {
            keyfileUrisState.value = keyfileUrisState.value + uris
        }
        return files
    }

    private fun offerGeneratedKeyfileCopies(
        files: List<File>,
        onStatus: (String) -> Unit,
        saveOne: (String) -> Unit
    ) {
        if (files.size == 1) {
            onStatus(
                "Generated and added ${files[0].name}. Save a copy. Change the name and generate again for another. Any extension is fine."
            )
            saveOne(files[0].name)
        } else {
            onStatus(
                "Generated ${files.size} keyfiles and added them. Save copies. Any extension is fine (.jpg, .bin, .key, …)."
            )
            beginShare()
            ShareHelper.shareFiles(this, files, "Save generated keyfiles")
        }
    }

    private fun copyUnlockKeyfiles(
        keyfileUris: List<Uri>
    ): Pair<MutableList<File>, String?> {
        val temps = mutableListOf<File>()
        for (uri in keyfileUris) {
            val copied = KeyfileIo.copyUri(this, uri)
            if (copied == null) {
                temps.forEach { KeyfileIo.wipe(it) }
                return Pair(mutableListOf(), "Could not read a keyfile. Pick it again, or open it from the Files app with VC Port. Any file can be a keyfile.")
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
        if (text.isEmpty() && keyfileUris.isEmpty()) {
            onStatus("Enter the current password or keyfiles on the Volume tab.")
            return
        }
        val nextPassword = newPassword.ifEmpty { text }
        if (!keepKeyfiles && nextPassword.isEmpty()) {
            onStatus("Removing all keyfiles needs a text password, or the volume cannot be opened.")
            return
        }
        val nextPim = if (newPassword.isEmpty() && (newPimText.isBlank() || newPimText == "0")) {
            pimText.toIntOrNull() ?: 0
        } else {
            newPimText.toIntOrNull() ?: 0
        }
        beginWork("Rewriting volume header…")
        Thread {
            val temps = mutableListOf<File>()
            try {
                val (copied, err) = copyUnlockKeyfiles(keyfileUris)
                if (err != null) {
                    runOnUiThread {
                        endWork()
                        onStatus(err)
                    }
                    return@Thread
                }
                temps.addAll(copied)
                if (NativeBridge.isOpen(currentHandle)) NativeBridge.closeVolume(currentHandle)
                val newKeys = if (keepKeyfiles) temps.map { it.absolutePath }.toTypedArray() else emptyArray()
                val rc = NativeBridge.changeHeader(
                    path,
                    text,
                    pimText.toIntOrNull() ?: 0,
                    temps.map { it.absolutePath }.toTypedArray(),
                    useBackupHeader,
                    newPassword,
                    nextPim,
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
        beginWork("Backing up volume header…")
        Thread {
            val temps = mutableListOf<File>()
            try {
                val (copied, err) = copyUnlockKeyfiles(keyfileUris)
                if (err != null) {
                    runOnUiThread {
                        endWork()
                        onStatus(err)
                    }
                    return@Thread
                }
                temps.addAll(copied)
                if (NativeBridge.isOpen(currentHandle)) NativeBridge.closeVolume(currentHandle)
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
        beginWork("Restoring volume header…")
        Thread {
            val temps = mutableListOf<File>()
            try {
                val (copied, err) = copyUnlockKeyfiles(keyfileUris)
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
                if (NativeBridge.isOpen(currentHandle)) NativeBridge.closeVolume(currentHandle)
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
        beginWork("Opening container…")
        Thread {
            val copied = try {
                bindContainer(uri)
            } catch (_: Exception) {
                ""
            }
            runOnUiThread {
                endWork()
                onDone(copied)
            }
        }.start()
    }

    private fun releaseContainerPfd() {
        try {
            containerPfd?.close()
        } catch (_: Exception) {
        }
        containerPfd = null
    }

    private fun nativePathIsInternal(nativePath: String): Boolean {
        return nativePath.startsWith("/proc/self/fd/") ||
            nativePath.startsWith(cacheDir.absolutePath) ||
            nativePath.startsWith(filesDir.absolutePath)
    }

    private fun bindContainer(uri: Uri): String {
        releaseContainerPfd()
        if (uri.scheme == "file") {
            val p = uri.path
            if (!p.isNullOrEmpty() && File(p).canRead()) return p
        }
        val writable = bindContainerFd(uri, "rw")
        if (writable.isNotEmpty()) return writable
        val copied = copyToCache(uri)
        if (copied.isNotEmpty()) return copied
        return bindContainerFd(uri, "r")
    }

    private fun bindContainerFd(uri: Uri, mode: String): String {
        return try {
            val pfd = contentResolver.openFileDescriptor(uri, mode)
            if (pfd != null && pfd.statSize >= 0L) {
                containerPfd = pfd
                "/proc/self/fd/${pfd.fd}"
            } else {
                pfd?.close()
                ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun openVolumeWithFactors(
        path: String,
        password: String,
        pimText: String,
        useTextPassword: Boolean,
        keyfileUris: List<Uri>,
        useBackupHeader: Boolean,
        readOnly: Boolean,
        trueCryptMode: Boolean,
        protectHidden: Boolean,
        hiddenPassword: String,
        hiddenPimText: String,
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
        if (text.isEmpty() && keyfileUris.isEmpty()) {
            onStatus("Type the volume password, or add a keyfile.")
            return
        }
        beginWork("Opening volume…")
        Thread {
            val temps = mutableListOf<File>()
            try {
                for (uri in keyfileUris) {
                    val copied = KeyfileIo.copyUri(this, uri)
                    if (copied == null) {
                        runOnUiThread {
                            endWork()
                            val name = ShareHelper.displayName(this, uri) ?: "keyfile"
                            onStatus("Could not read $name. Pick it again, or open it from the Files app with VC Port. Any file can be a keyfile.")
                        }
                        return@Thread
                    }
                    temps.add(copied)
                }
                if (NativeBridge.isOpen(currentHandle)) NativeBridge.closeVolume(currentHandle)
                val result = NativeBridge.openVolume(
                    path,
                    text,
                    if (trueCryptMode) 0 else (pimText.toIntOrNull() ?: 0),
                    useBackupHeader,
                    temps.map { it.absolutePath }.toTypedArray(),
                    readOnly,
                    protectHidden,
                    if (protectHidden) hiddenPassword else "",
                    if (protectHidden) (hiddenPimText.toIntOrNull() ?: 0) else 0
                )
                if (!NativeBridge.isOpen(result)) {
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
                        var msg = "Mounted in this app. Size $volumeBytes bytes. Tap a folder to open it, or a file to share the decrypted copy. Copy to device puts a file where Files can open it."
                        if (protectHidden) msg = "Hidden volume is being protected against damage. $msg"
                        if (truncated) msg += " Listing truncated at ${NativeBridge.LIST_UI_MAX} entries. Tap Load more."
                        onStatus(msg)
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
            containerLabelState.value = first.name
            statusState.value = "Received ${first.name}. Any extension can be a volume. Open with the correct password, PIM, and keyfiles, or share as-is."
        }
    }

    private fun unwrapIncomingFile(file: File, password: String, onStatus: (String) -> Unit) {
        if (password.isEmpty()) {
            onStatus("Enter the wrap password first. It is not stored.")
            return
        }
        beginWork("Unwrapping file…")
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

    private fun wrapSelectedFile(
        uri: Uri,
        password: String,
        onStatus: (String) -> Unit,
        onSaved: (File) -> Unit
    ) {
        if (password.length < 16) {
            onStatus("Use Generate strong password, or type at least 16 characters. Nothing is saved.")
            return
        }
        beginWork("Wrapping file…")
        Thread {
            val name = ShareHelper.displayName(this, uri) ?: "file.bin"
            val plain = File(cacheDir, "wrap-in-${ShareHelper.safeName(name)}")
            val wrapped = File(ShareHelper.shareDir(this), ShareHelper.safeName(name) + ".vcpw")
            try {
                val input = KeyfileIo.openReadable(this, uri)
                if (input == null) {
                    runOnUiThread {
                        endWork()
                        onStatus("Could not read $name. Pick it again, or open it from the Files app with VC Port.")
                    }
                    return@Thread
                }
                input.use { stream ->
                    plain.outputStream().use { output ->
                        copyStreamProgress(stream, output, uriLength(uri), "Reading file")
                    }
                }
                val rc = NativeBridge.wrapFile(plain.absolutePath, wrapped.absolutePath, password, name)
                KeyfileIo.wipe(plain)
                runOnUiThread {
                    endWork()
                    if (rc != 0 || !wrapped.exists()) {
                        onStatus("Wrap failed (code $rc).")
                    } else {
                        onStatus("Wrapped $name. Save the .vcpw copy, then share it from the bar. The password was not saved.")
                        onSaved(wrapped)
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

    private fun unwrapSelectedFile(
        uri: Uri,
        password: String,
        onStatus: (String) -> Unit,
        onSaved: (File) -> Unit
    ) {
        if (password.isEmpty()) {
            onStatus("Enter the wrap password first. It is not stored.")
            return
        }
        beginWork("Unwrapping file…")
        Thread {
            val name = ShareHelper.displayName(this, uri) ?: "wrap.vcpw"
            val wrapped = File(cacheDir, ShareHelper.safeName(name))
            val destDir = File(cacheDir, "unwrapped").apply { mkdirs() }
            try {
                val input = KeyfileIo.openReadable(this, uri)
                if (input == null) {
                    runOnUiThread {
                        endWork()
                        onStatus("Could not read $name. Pick it again, or open it from the Files app with VC Port.")
                    }
                    return@Thread
                }
                input.use { stream ->
                    wrapped.outputStream().use { output ->
                        copyStreamProgress(stream, output, uriLength(uri), "Reading wrap")
                    }
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
                        onStatus("Unwrapped ${file.name}. Save a copy. The password was not saved.")
                        onSaved(file)
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
            NativeBridge.isOpen(handle) && selected.isNotEmpty() -> shareVaultFiles(handle, dirPath, selected, onStatus)
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
        if (!NativeBridge.isOpen(handle)) {
            onStatus("Open a volume first.")
            return
        }
        if (toShare.isEmpty()) {
            onStatus("Tap a file in the volume, then Share decrypted.")
            return
        }
        beginShare()
        beginWork("Preparing ${toShare.size} decrypted file(s)…")
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

    private fun loadDir(
        handle: Long,
        path: String,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit,
        append: Boolean = false
    ) {
        if (!NativeBridge.isOpen(handle)) return
        beginWork(if (append) "Loading more…" else "Reading folder…")
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
            -6 -> "This container uses NTFS, ext, or another filesystem VC Port does not open. FAT and exFAT are supported."
            -1 -> "Could not read the container file."
            -3 -> "Not a VeraCrypt-compatible volume, or the header is damaged."
            -4 -> "Missing path or password argument."
            -5 -> "Not enough memory to open the volume."
            else -> "Open failed (code $code)."
        }
    }

    private fun listErrorMessage(code: Int): String {
        return when (code) {
            -6 -> "Opened the volume, but the filesystem is NTFS or otherwise unsupported. FAT and exFAT work here."
            -4 -> "Could not list that folder path."
            -5 -> "Not enough memory to list the folder."
            -1 -> "Could not read the folder from the volume."
            else -> "Could not list files (code $code)."
        }
    }

    private fun extractErrorMessage(name: String, rc: Int): String {
        return when (rc) {
            -6 -> "Could not extract $name. NTFS/ext are unsupported; FAT and exFAT work."
            -4 -> "Could not extract $name. Bad path."
            -5 -> "Could not extract $name. Not enough memory."
            -1 -> "Could not extract $name. Read failed."
            -2 -> "Could not extract $name. Wrong password or header."
            else -> "Could not extract $name (code $rc)."
        }
    }

    private fun importErrorMessage(name: String, rc: Int, handle: Long = 0L): String {
        if (NativeBridge.isOpen(handle) && NativeBridge.protectionTriggered(handle)) {
            return "Hidden volume protection triggered. The outer volume is now write-protected until you dismount."
        }
        return when (rc) {
            -6 -> "Could not copy $name. Folders are not created this way."
            -4 -> "Could not copy $name. Bad name or path."
            -5 -> "Could not copy $name. Volume is full, or the file is larger than 4 GiB (FAT limit)."
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
        if (!NativeBridge.isOpen(handle)) {
            onStatus("Open a volume first.")
            return
        }
        beginWork(if (move) "Moving from device…" else "Copying from device…")
        Thread {
            var cache: File? = null
            try {
                val display = ShareHelper.displayName(this, uri) ?: "file"
                val name = ShareHelper.safeName(display)
                val destDir = if (dirPath.isEmpty()) "/" else dirPath
                var rc: Int? = null
                try {
                    contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                        if (pfd.statSize >= 0L) {
                            NativeBridge.setProgress(0, "Copying into volume")
                            rc = NativeBridge.importFile(
                                handle,
                                destDir,
                                "/proc/self/fd/${pfd.fd}",
                                name
                            )
                        }
                    }
                } catch (_: Exception) {
                    rc = null
                }
                if (rc == null || rc == -1) {
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
                            copyStreamProgress(src, dest, uriLength(uri), "Reading from device")
                        }
                    }
                    rc = NativeBridge.importFile(handle, destDir, outFile.absolutePath, name)
                }
                var deletedOriginal = false
                if (rc == 0 && move) {
                    deletedOriginal = tryDeleteDocument(uri)
                }
                val result = rc ?: -1
                runOnUiThread {
                    endWork()
                    when {
                        result != 0 -> onStatus(importErrorMessage(name, result, handle))
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
        if (!NativeBridge.isOpen(handle)) {
            onStatus("Open a volume first.")
            return
        }
        if (entry.isDir) {
            onStatus("Open the folder, then copy a file inside it.")
            return
        }
        beginWork(if (move) "Moving ${entry.name} to device…" else "Copying ${entry.name} to device…")
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
        return "$kind ${entry.name}$size, modified ${formatFatStamp(entry.dosDate, entry.dosTime)}. Browsed in this app; this is not a mounted drive."
    }

    private fun mkdirInVolume(
        handle: Long,
        dirPath: String,
        name: String,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit
    ) {
        if (!NativeBridge.isOpen(handle)) {
            onStatus("Open a volume first.")
            return
        }
        beginWork("Creating folder $name…")
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
        beginWork("Renaming ${entry.name}…")
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
        beginWork(if (entry.isDir) "Deleting folder ${entry.name}…" else "Deleting ${entry.name}…")
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
        if (!NativeBridge.isOpen(handle)) {
            onStatus("Open a volume first.")
            return
        }
        beginWork("Wiping free space…")
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
        beginWork("Restoring from the embedded backup header…")
        Thread {
            val temps = mutableListOf<File>()
            try {
                val (copied, err) = copyUnlockKeyfiles(keyfileUris)
                if (err != null) {
                    runOnUiThread {
                        endWork()
                        onStatus(err)
                    }
                    return@Thread
                }
                temps.addAll(copied)
                if (NativeBridge.isOpen(currentHandle)) NativeBridge.closeVolume(currentHandle)
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
        if (file.absolutePath.startsWith(cacheDir.absolutePath) ||
            file.absolutePath.startsWith(filesDir.absolutePath)
        ) {
            return file.absolutePath
        }
        val outFile = File(cacheDir, name)
        if (file.length() > 0 && cacheDir.usableSpace < file.length() + (32L shl 20)) {
            return ""
        }
        file.copyTo(outFile, overwrite = true)
        return outFile.absolutePath
    }

    private fun copyToCache(uri: Uri): String {
        val display = ShareHelper.displayName(this, uri) ?: "volume.hc"
        val name = ShareHelper.sanitizeDisguiseName(display)
        val len = uriLength(uri)
        if (len > 0 && cacheDir.usableSpace < len + (32L shl 20)) {
            return ""
        }
        val input = KeyfileIo.openReadable(this, uri) ?: return ""
        val outFile = File(cacheDir, name)
        outFile.outputStream().use { output ->
            copyStreamProgress(input, output, len, "Copying container")
        }
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
    onGoToPath: (String) -> Unit,
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
        Text(
            "Mounted in this app",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dirPath.isNotEmpty()) {
                OutlinedButton(onClick = onUp, enabled = !busy) { Text("Up") }
                Spacer(Modifier.padding(8.dp))
            }
            val parts = dirPath.split('/').filter { it.isNotEmpty() }
            TextButton(
                onClick = { onGoToPath("") },
                enabled = !busy && dirPath.isNotEmpty()
            ) { Text("/") }
            parts.forEachIndexed { index, part ->
                Text("›", color = colors.onSurfaceVariant)
                val target = parts.take(index + 1).joinToString("/")
                TextButton(
                    onClick = { onGoToPath(target) },
                    enabled = !busy && index < parts.lastIndex
                ) { Text(part) }
            }
        }
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onCopyFromDevice,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Copy from device", maxLines = 1, style = MaterialTheme.typography.labelLarge) }
                OutlinedButton(
                    onClick = onMoveFromDevice,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Move from device", maxLines = 1, style = MaterialTheme.typography.labelLarge) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onCopyToDevice,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Copy to device", maxLines = 1, style = MaterialTheme.typography.labelLarge) }
                OutlinedButton(
                    onClick = onMoveToDevice,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Move to device", maxLines = 1, style = MaterialTheme.typography.labelLarge) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onNewFolder,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("New folder", maxLines = 1, style = MaterialTheme.typography.labelLarge) }
                OutlinedButton(
                    onClick = onRename,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Rename", maxLines = 1, style = MaterialTheme.typography.labelLarge) }
                OutlinedButton(
                    onClick = onDelete,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Delete", maxLines = 1, style = MaterialTheme.typography.labelLarge) }
                OutlinedButton(
                    onClick = onProperties,
                    enabled = !busy,
                    modifier = Modifier.weight(1f)
                ) { Text("Properties", maxLines = 1, style = MaterialTheme.typography.labelLarge) }
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
                .background(colors.primary)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text("Name", color = colors.onPrimary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("Size", color = colors.onPrimary, style = MaterialTheme.typography.labelSmall)
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
                    "Tap a folder to open it, or Copy from device to add a file. Copy to device writes a file Files can open.",
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

@Composable
private fun LookPickerLabel(option: VcSkin, selected: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (option == VcSkin.Evangelion) {
            Icon(
                painter = painterResource(R.drawable.ic_look_magi),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = Color.Unspecified
            )
            Icon(
                painter = painterResource(R.drawable.ic_look_unit01),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = Color.Unspecified
            )
        }
        Text(if (selected) "●  ${option.picker}" else option.picker)
    }
}

private fun formatSize(size: Long): String {
    if (size < 1024) return "$size B"
    if (size < 1024 * 1024) return "${size / 1024} KB"
    if (size < 1024L * 1024 * 1024) return "${size / (1024 * 1024)} MB"
    return "${size / (1024L * 1024 * 1024)} GB"
}
