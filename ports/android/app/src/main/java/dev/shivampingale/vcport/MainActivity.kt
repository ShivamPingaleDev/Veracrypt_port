package dev.shivampingale.vcport

import android.content.Intent
import android.content.ActivityNotFoundException
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

data class MountedVolume(
    val handle: Long,
    val label: String,
    val path: String,
    val uriKey: String,
    val dirPath: String = "",
    val entries: List<VaultEntry> = emptyList(),
    val truncated: Boolean = false
)

/** Session slot list. This session only; not a system drive letter. */
private const val MOUNT_SLOTS = 8

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : AppCompatActivity() {
    private val pathState = mutableStateOf("")
    private val containerUriState = mutableStateOf<Uri?>(null)
    private val statusState = mutableStateOf("Stay offline. Select a VeraCrypt container, or share an encrypted file as-is.")
    private val incomingState = mutableStateOf<File?>(null)
    private val passwordState = mutableStateOf("")
    private val pimState = mutableStateOf("0")
    private val createPimState = mutableStateOf("0")
    private val createHiddenPimState = mutableStateOf("0")
    private val newPimState = mutableStateOf("0")
    private val hiddenProtectPimState = mutableStateOf("0")
    private val keyfileUrisState = mutableStateOf(listOf<Uri>())
    private val headerKeyfileUrisState = mutableStateOf(listOf<Uri>())
    private val basketUrisState = mutableStateOf(listOf<Uri>())
    private val basketHashesState = mutableStateOf(mapOf<String, String>())
    private val hiddenKeyfileUrisState = mutableStateOf(listOf<Uri>())
    private val keyfileGenNameState = mutableStateOf("keyfile.bin")
    private val keyfileGenCountState = mutableStateOf("1")
    private val containerLabelState = mutableStateOf("")
    private val handleState = mutableStateOf(0L)
    private val mountedVolumesState = mutableStateOf(listOf<MountedVolume>())
    private val activeMountIndexState = mutableIntStateOf(0)
    private val entriesState = mutableStateOf(listOf<VaultEntry>())
    private val selectedNamesState = mutableStateOf(setOf<String>())
    private val dirPathState = mutableStateOf("")
    private val listTruncatedState = mutableStateOf(false)
    private val busyState = mutableStateOf(false)
    private val useBackupHeaderState = mutableStateOf(false)
    private val readOnlyOpenState = mutableStateOf(false)
    private val trueCryptModeState = mutableStateOf(false)
    private val protectHiddenState = mutableStateOf(false)
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
    private var lastUnlockPassword = ""
    private var lastUnlockPim = "0"
    private var pendingContainerPfd: ParcelFileDescriptor? = null
    private val liveContainerPfds = mutableMapOf<Long, ParcelFileDescriptor>()
    /** File pickers stop this activity. Do not wipe session fields in that gap. */
    private fun holdLockForPicker() {
        suppressLock = true
    }

    private fun unlockPassword(form: String, useText: Boolean): String {
        if (!useText) return ""
        return form.ifEmpty { lastUnlockPassword }
    }

    private fun unlockPimText(formPassword: String, formPim: String): String {
        return if (formPassword.isEmpty() && lastUnlockPassword.isNotEmpty()) lastUnlockPim else formPim
    }

    private fun rememberUnlock(password: String, pimText: String) {
        lastUnlockPassword = password
        lastUnlockPim = pimText.ifBlank { "0" }
    }

    private fun forgetUnlock() {
        lastUnlockPassword = ""
        lastUnlockPim = "0"
    }

    /** Clear Volume-tab unlock fields after a successful mount. Tools still uses [lastUnlockPassword]. */
    private fun wipeUnlockForm() {
        passwordState.value = ""
        pimState.value = "0"
        hiddenProtectPasswordState.value = ""
        hiddenProtectPimState.value = "0"
        useBackupHeaderState.value = false
        readOnlyOpenState.value = false
        trueCryptModeState.value = false
        protectHiddenState.value = false
    }

    /**
     * Instrumented tests skip SAF CreateDocument / OpenDocument sheets so a
     * session can finish Create → save → Open on the emulator. Production
     * still uses the system pickers.
     */
    @androidx.annotation.VisibleForTesting
    var testingSkipSystemPickers = false

    /** Instrumented tests add basket files without the system picker. */
    @androidx.annotation.VisibleForTesting
    fun testingCreatePassword(): String = createPasswordState.value

    @androidx.annotation.VisibleForTesting
    fun testingCreateHiddenPassword(): String = createHiddenPasswordState.value

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

    @androidx.annotation.VisibleForTesting
    fun testingFinishCreateSave(dest: File): Boolean {
        val src = File(pathState.value)
        if (!src.isFile) return false
        dest.parentFile?.mkdirs()
        src.inputStream().use { input ->
            dest.outputStream().use { out ->
                input.copyTo(out)
                out.flush()
                out.fd.sync()
            }
        }
        if (!dest.isFile || dest.length() != src.length()) return false
        runOnUiThread {
            incomingState.value = null
            containerUriState.value = Uri.fromFile(dest)
            containerLabelState.value = dest.name
            wipeCreateSecrets()
            statusState.value =
                "Saved ${dest.name}. Type the volume password and Open volume. Create secrets were wiped."
            tabState.intValue = 0
        }
        return true
    }

    @androidx.annotation.VisibleForTesting
    fun testingSelectContainer(file: File) {
        runOnUiThread {
            pathState.value = file.absolutePath
            containerUriState.value = Uri.fromFile(file)
            containerLabelState.value = file.name
            incomingState.value = null
            statusState.value = "Selected ${file.name}. Open volume to browse folders here."
            tabState.intValue = 0
        }
    }

    @androidx.annotation.VisibleForTesting
    fun testingClearKeyfiles() {
        val done = CountDownLatch(1)
        runOnUiThread {
            try {
                keyfileUrisState.value = emptyList()
                hiddenKeyfileUrisState.value = emptyList()
            } finally {
                done.countDown()
            }
        }
        done.await(5, TimeUnit.SECONDS)
    }

    @androidx.annotation.VisibleForTesting
    fun testingAddKeyfiles(files: List<File>) {
        val uris = files.map { Uri.fromFile(it) }
        val done = CountDownLatch(1)
        runOnUiThread {
            try {
                keyfileUrisState.value = keyfileUrisState.value + uris
            } finally {
                done.countDown()
            }
        }
        done.await(5, TimeUnit.SECONDS)
    }

    @androidx.annotation.VisibleForTesting
    fun testingSnapshotKeyfiles(into: File): List<File> {
        into.mkdirs()
        val out = mutableListOf<File>()
        for (uri in keyfileUrisState.value) {
            val src = uri.path?.let { File(it) } ?: continue
            if (!src.isFile) continue
            val dest = File(into, src.name)
            src.copyTo(dest, overwrite = true)
            out += dest
        }
        return out
    }

    @androidx.annotation.VisibleForTesting
    fun testingImportFiles(files: List<File>) {
        val handle = handleState.value
        val dir = dirPathState.value
        val destDir = if (dir.isEmpty()) "/" else dir
        if (!NativeBridge.isOpen(handle)) return
        beginWork("Copying from device…")
        Thread {
            for (file in files) {
                NativeBridge.importFile(
                    handle,
                    destDir,
                    file.absolutePath,
                    ShareHelper.safeName(file.name)
                )
            }
            runOnUiThread {
                endWork()
                loadDir(handle, dir, { entriesState.value = it }, { statusState.value = it })
            }
        }.start()
    }

    @androidx.annotation.VisibleForTesting
    fun testingSelectNames(names: Set<String>) {
        runOnUiThread { selectedNamesState.value = names }
    }

    @androidx.annotation.VisibleForTesting
    fun testingStatus(): String = statusState.value

    @androidx.annotation.VisibleForTesting
    fun testingTransferNamed(names: Set<String>, destLabel: String, move: Boolean): Boolean {
        val started = booleanArrayOf(false)
        val done = CountDownLatch(1)
        runOnUiThread {
            try {
                val mounts = mountedVolumesState.value
                val dest = mounts.firstOrNull { vol ->
                    File(vol.path).name.equals(destLabel, ignoreCase = true) ||
                        vol.label.equals(destLabel, ignoreCase = true)
                }
                val src = dest?.let { d ->
                    mounts.firstOrNull { vol ->
                        vol.handle != d.handle && vol.entries.any { it.name in names && !it.isDir }
                    } ?: mounts.firstOrNull { it.handle != d.handle }
                }
                if (dest == null || src == null || src.handle == dest.handle) return@runOnUiThread
                val files = src.entries.filter { it.name in names && !it.isDir }.ifEmpty {
                    entriesState.value.filter { it.name in names && !it.isDir }
                }
                if (files.isEmpty()) return@runOnUiThread
                val srcIndex = mounts.indexOfFirst { it.handle == src.handle }
                if (srcIndex >= 0) {
                    persistActiveMount(dirPathState.value, entriesState.value, listTruncatedState.value)
                    activeMountIndexState.intValue = srcIndex
                    handleState.value = src.handle
                    dirPathState.value = src.dirPath
                    entriesState.value = src.entries
                    listTruncatedState.value = src.truncated
                }
                selectedNamesState.value = names
                started[0] = true
                transferBetweenVolumes(
                    srcHandle = src.handle,
                    srcDir = src.dirPath,
                    files = files,
                    dest = dest,
                    move = move,
                    onSrcEntries = { entriesState.value = it },
                    onStatus = { statusState.value = it }
                )
            } finally {
                done.countDown()
            }
        }
        done.await(5, TimeUnit.SECONDS)
        return started[0]
    }

    @androidx.annotation.VisibleForTesting
    fun testingExportNamed(name: String, dest: File): Boolean {
        val handle = handleState.value
        if (!NativeBridge.isOpen(handle)) return false
        val dir = dirPathState.value
        val rel = if (dir.isEmpty()) name else "$dir/$name"
        dest.parentFile?.mkdirs()
        return NativeBridge.exportFile(handle, rel, dest.absolutePath) == 0
    }

    @androidx.annotation.VisibleForTesting
    fun testingEntryNames(): List<String> = entriesState.value.map { it.name }

    @androidx.annotation.VisibleForTesting
    fun testingOpenDir(name: String) {
        runOnUiThread {
            val next = joinDir(dirPathState.value, name)
            dirPathState.value = next
            loadDir(handleState.value, next, { entriesState.value = it }, { statusState.value = it })
        }
    }

    @androidx.annotation.VisibleForTesting
    fun testingGoParent() {
        runOnUiThread {
            val next = parentDir(dirPathState.value)
            dirPathState.value = next
            loadDir(handleState.value, next, { entriesState.value = it }, { statusState.value = it })
        }
    }

    @androidx.annotation.VisibleForTesting
    fun testingCopyHeaderBackup(dest: File): Boolean {
        val src = File(cacheDir, "volume-header.bak")
        if (!src.isFile) return false
        dest.parentFile?.mkdirs()
        src.copyTo(dest, overwrite = true)
        return dest.isFile && dest.length() == src.length()
    }

    @androidx.annotation.VisibleForTesting
    fun testingRestoreHeader(bak: File) {
        if (!bak.isFile) return
        val volumePath = pathState.value
        val text = passwordState.value
        val pim = pimState.value.toIntOrNull() ?: 0
        val keyfileUris = keyfileUrisState.value
        closeMountedVolume()
        beginWork("Restoring volume header…")
        Thread {
            val temps = mutableListOf<File>()
            try {
                val (copied, err) = copyUnlockKeyfiles(keyfileUris)
                if (err != null) {
                    runOnUiThread {
                        endWork()
                        statusState.value = err
                    }
                    return@Thread
                }
                temps.addAll(copied)
                val rc = NativeBridge.restoreHeaders(
                    volumePath,
                    bak.absolutePath,
                    text,
                    pim,
                    temps.map { it.absolutePath }.toTypedArray()
                )
                runOnUiThread {
                    endWork()
                    handleState.value = 0L
                    entriesState.value = emptyList()
                    statusState.value = if (rc == 0) {
                        "Restored volume header. Open with the password that was current when the backup was made."
                    } else {
                        headerErrorMessage(rc)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    endWork()
                    statusState.value = "Restore volume header failed."
                }
            } finally {
                temps.forEach { KeyfileIo.wipe(it) }
            }
        }.start()
    }

    @androidx.annotation.VisibleForTesting
    fun testingVolumeInfo(): String? {
        val handle = handleState.value
        if (!NativeBridge.isOpen(handle)) return null
        return NativeBridge.volumeInfo(handle)
    }

    private fun lookPrefs() = getSharedPreferences("vc_port_look", MODE_PRIVATE)

    private fun loadSkin(): VcSkin {
        val name = lookPrefs().getString("skin", VcSkin.Desktop.name) ?: VcSkin.Desktop.name
        return when (name) {
            VcSkin.Signal.name, "DarkMode" -> VcSkin.Signal
            else -> VcSkin.Desktop
        }
    }

    private fun saveSkin(skin: VcSkin) {
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
                var mountedVolumes by mountedVolumesState
                var activeMountIndex by activeMountIndexState
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
                var useBackupHeader by useBackupHeaderState
                var readOnlyOpen by readOnlyOpenState
                var trueCryptMode by trueCryptModeState
                var protectHidden by protectHiddenState
                var hiddenProtectPassword by hiddenProtectPasswordState
                var hiddenProtectPim by hiddenProtectPimState
                var namePrompt by remember { mutableStateOf<String?>(null) }
                var namePromptValue by remember { mutableStateOf("") }
                var pendingExportFile by remember { mutableStateOf<File?>(null) }
                var pendingSaveName by remember { mutableStateOf("") }
                var pendingSaveMove by remember { mutableStateOf(false) }
                var pendingExportNames by remember { mutableStateOf(listOf<String>()) }
                var pendingFromDeviceMove by remember { mutableStateOf(false) }
                var showOpenAnother by remember { mutableStateOf(false) }
                var transferMove by remember { mutableStateOf<Boolean?>(null) }
                var selectedNames by selectedNamesState
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
                        if (copyFileToUri(File(path), uri)) {
                            incoming = null
                            containerUri = uri
                            containerLabel = ShareHelper.displayName(this@MainActivity, uri)
                                ?: File(path).name
                            wipeCreateSecrets()
                            status = "Saved $containerLabel. Type the volume password and Open volume. Create secrets were wiped."
                        } else {
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
                            keyfileUris = keyfileUris,
                            onHandle = { handle = it },
                            onEntries = { entries = it },
                            onStatus = { status = it }
                        )
                    }
                }
                val copyFromDevicePicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenMultipleDocuments()
                ) { uris: List<Uri> ->
                    if (uris.isNotEmpty()) {
                        importFromDevice(
                            handle = handle,
                            dirPath = dirPath,
                            uris = uris,
                            existingNames = entries.filter { !it.isDir }.map { it.name }.toSet(),
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
                val saveToFolderPicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocumentTree()
                ) { tree: Uri? ->
                    val names = pendingExportNames
                    if (tree != null && names.isNotEmpty()) {
                        exportManyToDevice(
                            handle = handle,
                            dirPath = dirPath,
                            files = entries.filter { it.name in names && !it.isDir },
                            treeUri = tree,
                            move = pendingSaveMove,
                            onEntries = { entries = it },
                            onStatus = { status = it }
                        )
                    }
                }

                fun runPanic() {
                    panicWipe()
                    password = ""
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
                                            "VC Port",
                                            style = MaterialTheme.typography.titleLarge
                                        )
                                        Text(
                                            when {
                                                mountedVolumes.size > 1 ->
                                                    "${mountedVolumes.size} volumes mounted"
                                                NativeBridge.isOpen(handle) ->
                                                    "Mounted in this app"
                                                else ->
                                                    "Stay offline. This build has no network."
                                            },
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
                            cipherName != null ->
                                "$cipherName — encrypted file in front"
                            else ->
                                "Nothing in front. Select a container or file, then share from here."
                        }
                        val canDecrypted = selectedFiles.isNotEmpty() ||
                            lastPlainFiles.any { it.exists() }
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
                                    lastPlain = lastPlainFiles
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
                            }
                        }
                            ScrollableTabRow(
                                selectedTabIndex = tab.coerceIn(0, 3),
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
                                Tab(selected = tab == 3, onClick = { tab = 3 }, modifier = Modifier.testTag("tab_mounted"), text = { Text("Mounted") })
                            }
                            if (tab == 3) {
                            VaultPane(
                                modifier = Modifier.weight(1f),
                                dirPath = dirPath,
                                entries = entries,
                                selectedNames = selectedNames,
                                truncated = listTruncated,
                                busy = busy,
                                mounts = mountedVolumes,
                                activeMount = activeMountIndex,
                                onSelectMount = { index ->
                                    persistActiveMount(dirPath, entries, listTruncated)
                                    activeMountIndex = index
                                    val v = mountedVolumes[index]
                                    handle = v.handle
                                    dirPath = v.dirPath
                                    entries = v.entries
                                    listTruncated = v.truncated
                                },
                                onDismountMount = { dismountMountedAt(it) },
                                onOpenAnother = { showOpenAnother = true },
                                canTransfer = mountedVolumes.size > 1,
                                onCopyToVolume = { transferMove = false },
                                onMoveToVolume = { transferMove = true },
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
                                onShare = { files ->
                                    shareVaultFiles(handle, dirPath, files) { status = it }
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
                                    val files = entries.filter { it.name in selectedNames && !it.isDir }
                                    if (files.isEmpty()) {
                                        status = "Tap one or more files in the volume, then Copy to device."
                                    } else {
                                        beginShare()
                                        pendingSaveMove = false
                                        if (files.size == 1) {
                                            pendingSaveName = files[0].name
                                            saveToDevicePicker.launch(files[0].name)
                                        } else {
                                            pendingExportNames = files.map { it.name }
                                            saveToFolderPicker.launch(null)
                                        }
                                    }
                                },
                                onMoveToDevice = {
                                    val files = entries.filter { it.name in selectedNames && !it.isDir }
                                    if (files.isEmpty()) {
                                        status = "Tap one or more files in the volume, then Move to device."
                                    } else {
                                        beginShare()
                                        pendingSaveMove = true
                                        if (files.size == 1) {
                                            pendingSaveName = files[0].name
                                            saveToDevicePicker.launch(files[0].name)
                                        } else {
                                            pendingExportNames = files.map { it.name }
                                            saveToFolderPicker.launch(null)
                                        }
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
                                onSelectAll = {
                                    val files = entries.filter { !it.isDir }.map { it.name }.toSet()
                                    selectedNames = if (files.isNotEmpty() && selectedNames.containsAll(files)) {
                                        emptySet()
                                    } else {
                                        files
                                    }
                                },
                                onMore = {
                                    loadDir(handle, dirPath, { entries = it }, { status = it }, append = true)
                                }
                            )
                            } else {
                            Column(
                                Modifier
                                    .weight(1f)
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
                                                    } else if (testingSkipSystemPickers) {
                                                        status = "Generated and added ${files.first().name}. Save a copy."
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
                                                        } else if (testingSkipSystemPickers) {
                                                            status = "Generated nested keyfile ${files.first().name}. Save a copy."
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
                                                                    createSaver.launch(ShareHelper.sanitizeDisguiseName(createFileName))
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
                                    2 -> {
                                        VcCard {
                                            Text("Appearance", style = MaterialTheme.typography.titleMedium)
                                            VcHint("Original is the VeraCrypt-like look. Dark mode is a dark theme. Pick is stored on this phone only.")
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
                                                    ) { Text(if (selected) "●  ${option.picker}" else option.picker) }
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
                                                        onHandle = { handle = it },
                                                        onEntries = { entries = it },
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
                                                { headerKdf = it },
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
                                                            onHandle = { handle = it },
                                                            onEntries = { entries = it },
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
                                                        onHandle = { handle = it },
                                                        onEntries = { entries = it },
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
                                                        onHandle = { handle = it },
                                                        onEntries = { entries = it },
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
                                                        onHandle = { handle = it },
                                                        onEntries = { entries = it },
                                                        onStatus = { status = it },
                                                        onSaved = { file ->
                                                            pendingExportFile = file
                                                            if (!testingSkipSystemPickers) {
                                                                holdLockForPicker()
                                                                window.decorView.post {
                                                                    holdLockForPicker()
                                                                    toolSaver.launch("volume-header.bak")
                                                                }
                                                            }
                                                        }
                                                    )
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth().testTag("tools_backup_header")
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
                                                        onHandle = { handle = it },
                                                        onEntries = { entries = it },
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
                                                        status = "Keyfile generator failed."
                                                    } else if (testingSkipSystemPickers) {
                                                        status = "Generated and added ${files.first().name}. Save a copy."
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
                                        }
                                        VcCard {
                                            Text("Wipe cached passwords", style = MaterialTheme.typography.titleMedium)
                                            OutlinedButton(
                                                onClick = {
                                                    lockSession()
                                                    password = ""
                                                    handle = 0
                                                    entries = emptyList()
                                                    status = "Wipe cached passwords complete. Volume closed."
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Wipe cached passwords") }
                                        }
                                        VcCard {
                                            Text("Not on this phone", style = MaterialTheme.typography.titleMedium)
                                            VcHint("Root / jailbreak: this app will not ask for superuser.")
                                            VcHint("Device encryption: this app encrypts VeraCrypt container files (any file name). It cannot encrypt the phone's operating system. Android already encrypts the device with your screen lock.")
                                            VcHint("Security tokens: PKCS#11 smart cards are not available here. Export a keyfile on a computer, then Add keyfiles.")
                                        }
                                        VcCard {
                                            Text("About / licenses", style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "“We must defend our own privacy if we expect to have any.” — Eric Hughes, A Cypherpunk’s Manifesto (1993)",
                                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                                color = colors.onSurfaceVariant
                                            )
                                            Text(
                                                "“Cypherpunks write code.” — Eric Hughes, A Cypherpunk’s Manifesto (1993)",
                                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                                                color = colors.onSurfaceVariant
                                            )
                                            VcHint("Portions of this product are based in part on TrueCrypt, freely available at http://www.truecrypt.org/")
                                            VcHint("Apache-2.0 / TrueCrypt License 3.0. Not named VeraCrypt. Not unbreakable.")
                                            VcHint("https://github.com/ShivamPingaleDev/Veracrypt_port")
                                            VcHint("Shivam Mangesh Pingale — shivampingaledev@proton.me · shivampingaledev@gmail.com")
                                            VcHint(stringResource(R.string.about_support))
                                            TextButton(
                                                onClick = { openUrl(SupportLinks.GITHUB_SPONSORS) },
                                                modifier = Modifier.testTag("about_support_github")
                                            ) { Text("GitHub Sponsors") }
                                            TextButton(
                                                onClick = { openUrl(SupportLinks.BUY_ME_A_COFFEE) },
                                                modifier = Modifier.testTag("about_support_bmac")
                                            ) { Text("Buy Me a Coffee") }
                                            TextButton(
                                                onClick = { openUrl(SupportLinks.KO_FI) },
                                                modifier = Modifier.testTag("about_support_kofi")
                                            ) { Text("Ko-fi") }
                                            TextButton(
                                                onClick = { openUrl(SupportLinks.LIBERAPAY) },
                                                modifier = Modifier.testTag("about_support_liberapay")
                                            ) { Text("Liberapay") }
                                            VcHint("The app does not install itself.")
                                            VcHint(SourcePin.describeBuild())
                                        }
                                    }
                                    else -> {
                                        VcCard {
                                            Text("VeraCrypt-compatible. This build has no network.")
                                            VcHint("Stay offline. A compelled password still wins. Not unbreakable.")
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
                                                    modifier = Modifier.testTag("volume_password"),
                                                    enabled = !busy
                                                )
                                            }
                                            OutlinedTextField(
                                                pim,
                                                { pim = it },
                                                label = { Text("PIM (0 = default)") },
                                                modifier = Modifier.fillMaxWidth().testTag("volume_pim"),
                                                enabled = !busy,
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                            )
                                            Text("Keyfiles", style = MaterialTheme.typography.titleSmall)
                                            VcHint("Same as VeraCrypt on a computer: pick several in Files (long-press). Any extension. First 1 MiB of each.")
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
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("use_backup_header")
                                                    .toggleable(
                                                        value = useBackupHeader,
                                                        enabled = !busy,
                                                        role = Role.Checkbox,
                                                        onValueChange = { useBackupHeader = it }
                                                    )
                                            ) {
                                                Checkbox(useBackupHeader, onCheckedChange = null, enabled = !busy)
                                                Text("Use backup header")
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("read_only")
                                                    .toggleable(
                                                        value = readOnlyOpen,
                                                        enabled = !busy,
                                                        role = Role.Checkbox,
                                                        onValueChange = { readOnlyOpen = it }
                                                    )
                                            ) {
                                                Checkbox(readOnlyOpen, onCheckedChange = null, enabled = !busy)
                                                Text("Read-only")
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("truecrypt_mode")
                                                    .toggleable(
                                                        value = trueCryptMode,
                                                        enabled = !busy,
                                                        role = Role.Checkbox,
                                                        onValueChange = { trueCryptMode = it }
                                                    )
                                            ) {
                                                Checkbox(trueCryptMode, onCheckedChange = null, enabled = !busy)
                                                Text("TrueCrypt Mode")
                                            }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("protect_hidden")
                                                    .toggleable(
                                                        value = protectHidden,
                                                        enabled = !busy,
                                                        role = Role.Checkbox,
                                                        onValueChange = { protectHidden = it }
                                                    )
                                            ) {
                                                Checkbox(protectHidden, onCheckedChange = null, enabled = !busy)
                                                Text("Protect hidden volume against damage caused by writing to outer volume")
                                            }
                                            if (protectHidden) {
                                                SecretField(
                                                    hiddenProtectPassword,
                                                    { hiddenProtectPassword = it },
                                                    "Password to hidden volume",
                                                    modifier = Modifier.testTag("hidden_protect_password"),
                                                    enabled = !busy
                                                )
                                                OutlinedTextField(
                                                    hiddenProtectPim,
                                                    { hiddenProtectPim = it },
                                                    label = { Text("Hidden volume PIM (0 = default)") },
                                                    modifier = Modifier.fillMaxWidth().testTag("hidden_protect_pim"),
                                                    enabled = !busy,
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                                )
                                            }
                                            Button(
                                                onClick = {
                                                    persistActiveMount(dirPath, entries, listTruncated)
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
                                                        onHandle = { handle = it },
                                                        onEntries = { entries = it },
                                                        onStatus = { status = it }
                                                    )
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth().testTag("open_volume")
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
                            modifier = Modifier.testTag("name_prompt"),
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
                            },
                            modifier = Modifier.testTag("name_prompt_ok")
                        ) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { namePrompt = null }) { Text("Cancel") }
                    }
                )
            }
            if (showOpenAnother) {
                AlertDialog(
                    onDismissRequest = { showOpenAnother = false },
                    title = { Text("Open another container") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("This session can keep several volumes mounted. Copy to volume / Move to volume sends selected files into the folder last opened on the other volume.")
                            if (containerLabel.isNotEmpty()) {
                                Text("Selected: $containerLabel")
                            }
                            OutlinedButton(
                                onClick = {
                                    holdLockForPicker()
                                    picker.launch(arrayOf("*/*"))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Choose container") }
                            if (useTextPassword) {
                                SecretField(
                                    password,
                                    { password = it },
                                    "Password",
                                    modifier = Modifier.testTag("open_another_password"),
                                    enabled = !busy
                                )
                            }
                            OutlinedTextField(
                                pim,
                                { pim = it },
                                label = { Text("PIM (0 = default)") },
                                modifier = Modifier.fillMaxWidth().testTag("open_another_pim"),
                                enabled = !busy,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showOpenAnother = false
                                persistActiveMount(dirPath, entries, listTruncated)
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
                                    onHandle = { handle = it },
                                    onEntries = { entries = it },
                                    onStatus = { status = it }
                                )
                            }
                        ) { Text("Open") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showOpenAnother = false }) { Text("Cancel") }
                    }
                )
            }
            if (transferMove != null) {
                val others = mountedVolumes.filterIndexed { i, _ -> i != activeMountIndex }
                AlertDialog(
                    onDismissRequest = { transferMove = null },
                    title = { Text(if (transferMove == true) "Move to volume" else "Copy to volume") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Selected files land in the folder last opened on that volume.")
                            others.forEach { dest ->
                                OutlinedButton(
                                    onClick = {
                                        val move = transferMove == true
                                        transferMove = null
                                        val files = entries.filter { it.name in selectedNames && !it.isDir }
                                        if (files.isEmpty()) {
                                            status = "Tap one or more files, then Copy to volume or Move to volume."
                                        } else {
                                            transferBetweenVolumes(
                                                srcHandle = handle,
                                                srcDir = dirPath,
                                                files = files,
                                                dest = dest,
                                                move = move,
                                                onSrcEntries = { entries = it },
                                                onStatus = { status = it }
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("transfer_dest")
                                ) { Text(dest.label) }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { transferMove = null }) { Text("Cancel") }
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
        releaseAllContainerPfds()
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

    private fun persistActiveMount(dirPath: String, entries: List<VaultEntry>, truncated: Boolean) {
        val list = mountedVolumesState.value.toMutableList()
        val i = activeMountIndexState.intValue
        if (i in list.indices) {
            list[i] = list[i].copy(dirPath = dirPath, entries = entries, truncated = truncated)
            mountedVolumesState.value = list
        }
    }

    private fun dismountMountedAt(index: Int) {
        persistActiveMount(dirPathState.value, entriesState.value, listTruncatedState.value)
        val list = mountedVolumesState.value.toMutableList()
        if (index !in list.indices) return
        val victim = list.removeAt(index)
        if (NativeBridge.isOpen(victim.handle)) NativeBridge.closeVolume(victim.handle)
        liveContainerPfds.remove(victim.handle)?.let {
            try {
                it.close()
            } catch (_: Exception) {
            }
        }
        mountedVolumesState.value = list
        if (list.isEmpty()) {
            activeMountIndexState.intValue = 0
            handleState.value = 0L
            entriesState.value = emptyList()
            dirPathState.value = ""
            listTruncatedState.value = false
            statusState.value = "Dismounted ${victim.label}."
            return
        }
        val active = activeMountIndexState.intValue
        val next = when {
            index < active -> (active - 1).coerceIn(0, list.lastIndex)
            index == active -> active.coerceIn(0, list.lastIndex)
            else -> active
        }
        activeMountIndexState.intValue = next
        val v = list[next]
        handleState.value = v.handle
        dirPathState.value = v.dirPath
        entriesState.value = v.entries
        listTruncatedState.value = v.truncated
        statusState.value = "Dismounted ${victim.label}. ${list.size} still mounted."
    }

    private fun closeMountedVolume() {
        for (vol in mountedVolumesState.value) {
            if (NativeBridge.isOpen(vol.handle)) NativeBridge.closeVolume(vol.handle)
        }
        liveContainerPfds.values.forEach {
            try {
                it.close()
            } catch (_: Exception) {
            }
        }
        liveContainerPfds.clear()
        mountedVolumesState.value = emptyList()
        activeMountIndexState.intValue = 0
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
        newPasswordState.value = ""
        pimState.value = "0"
        hiddenProtectPimState.value = "0"
        newPimState.value = "0"
        forgetUnlock()
        if (wasOpen && !statusState.value.startsWith("Panic")) {
            statusState.value =
                "Dismounted. Create form kept. Dismount or Panic wipe also clears the generated password."
        }
    }

    /**
     * After a successful CreateDocument save: forget create/open secrets so they
     * are not sitting in RAM. Keep the selected cache path and wizard size/cipher
     * so Open volume still works after they re-type the password.
     * Cancelling the save picker must not call this.
     */
    private fun wipeCreateSecrets() {
        createPasswordState.value = ""
        createHiddenPasswordState.value = ""
        hiddenProtectPasswordState.value = ""
        passwordState.value = ""
        createPimState.value = "0"
        createHiddenPimState.value = "0"
        hiddenProtectPimState.value = "0"
        pimState.value = "0"
        newPimState.value = "0"
        newPasswordState.value = ""
        val keys = keyfileUrisState.value + hiddenKeyfileUrisState.value
        keyfileUrisState.value = emptyList()
        headerKeyfileUrisState.value = emptyList()
        hiddenKeyfileUrisState.value = emptyList()
        keys.forEach { uri ->
            if (uri.scheme == "file") {
                uri.path?.let { Hardening.wipeFile(File(it)) }
            }
        }
        Hardening.wipeDir(File(cacheDir, "keyfiles"))
        forgetUnlock()
    }

    private fun wipeRamSecrets() {
        createPasswordState.value = ""
        createHiddenPasswordState.value = ""
        hiddenProtectPasswordState.value = ""
        newPasswordState.value = ""
        passwordState.value = ""
        pimState.value = "0"
        createPimState.value = "0"
        createHiddenPimState.value = "0"
        newPimState.value = "0"
        hiddenProtectPimState.value = "0"
        forgetUnlock()
        keyfileUrisState.value = emptyList()
        headerKeyfileUrisState.value = emptyList()
        hiddenKeyfileUrisState.value = emptyList()
        useBackupHeaderState.value = false
        readOnlyOpenState.value = false
        trueCryptModeState.value = false
        protectHiddenState.value = false
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
        releasePendingPfd()
        wipeRamSecrets()
        endWork()
        Hardening.wipeSessionFiles(this)
        if (!statusState.value.startsWith("Panic")) {
            statusState.value =
                "Dismounted. Passwords, keyfiles in memory, and decrypted copies wiped. Ciphertext stays."
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            statusState.value = "No browser found: $url"
        }
    }

    private fun panicWipe() {
        closeMountedVolume()
        releasePendingPfd()
        wipeRamSecrets()
        Hardening.panic(this)
        basketUrisState.value = emptyList()
    }

    private fun beginWork(title: String = "", updateStatus: Boolean = true) {
        NativeBridge.resetProgress()
        if (updateStatus && title.isNotEmpty()) statusState.value = title
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
            val outFile = copyUriForNativeImport(uri, name, "Copying $name into volume")
                ?: return "Could not read $display. Pick it again from Files."
            cache = outFile
            val rc = NativeBridge.importFile(handle, "/", outFile.absolutePath, name)
            if (rc == 0) null else importErrorMessage(name, rc, handle)
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

    /** Native File::Open cannot reopen a SAF `/proc/self/fd/N` path. Copy first. */
    private fun copyUriForNativeImport(uri: Uri, name: String, phase: String): File? {
        val input = KeyfileIo.openReadable(this, uri) ?: return null
        val outFile = File(cacheDir, "from-device-${System.nanoTime()}-${ShareHelper.safeName(name)}")
        return try {
            input.use { src ->
                outFile.outputStream().use { dest ->
                    copyStreamProgress(src, dest, uriLength(uri), phase)
                }
            }
            if (outFile.isFile) outFile else {
                outFile.delete()
                null
            }
        } catch (_: Exception) {
            outFile.delete()
            null
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
        newPassword: String,
        newPimText: String,
        newKdf: String,
        keepKeyfiles: Boolean,
        onHandle: (Long) -> Unit,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit,
        successMessage: String,
        applySessionKeyfiles: Boolean = false
    ) {
        val resolved = ensureContainerPath(path, containerUriState.value)
        if (resolved.isEmpty()) {
            onStatus(
                if (path.isEmpty() && containerUriState.value == null)
                    "Choose a container first."
                else
                    "Could not read the container file. Choose it again from Files."
            )
            return
        }
        if (resolved != path) pathState.value = resolved
        val text = unlockPassword(password, useTextPassword)
        val unlockUris = headerKeyfileUrisState.value.ifEmpty { keyfileUris }
        if (text.isEmpty() && unlockUris.isEmpty()) {
            onStatus("Enter the current password or keyfiles on the Volume tab.")
            return
        }
        val nextPassword = newPassword.ifEmpty { text }
        if (!keepKeyfiles && nextPassword.isEmpty()) {
            onStatus("Removing all keyfiles needs a text password, or the volume cannot be opened.")
            return
        }
        val pimUsed = unlockPimText(password, pimText)
        val nextPim = if (newPassword.isEmpty() && (newPimText.isBlank() || newPimText == "0")) {
            pimUsed.toIntOrNull() ?: 0
        } else {
            newPimText.toIntOrNull() ?: 0
        }
        closeMountedVolume()
        beginWork("Rewriting volume header…")
        Thread {
            val temps = mutableListOf<File>()
            val sessionTemps = mutableListOf<File>()
            try {
                val (copied, err) = copyUnlockKeyfiles(unlockUris)
                if (err != null) {
                    runOnUiThread {
                        endWork()
                        onStatus(err)
                    }
                    return@Thread
                }
                temps.addAll(copied)
                val newKeys = if (!keepKeyfiles) {
                    emptyArray()
                } else if (applySessionKeyfiles) {
                    val (sessionCopied, sessionErr) = copyUnlockKeyfiles(keyfileUris)
                    if (sessionErr != null) {
                        runOnUiThread {
                            endWork()
                            onStatus(sessionErr)
                        }
                        return@Thread
                    }
                    sessionTemps.addAll(sessionCopied)
                    sessionCopied.map { it.absolutePath }.toTypedArray()
                } else {
                    copied.map { it.absolutePath }.toTypedArray()
                }
                val rc = NativeBridge.changeHeader(
                    resolved,
                    text,
                    pimUsed.toIntOrNull() ?: 0,
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
                    if (rc == 0) {
                        headerKeyfileUrisState.value = when {
                            !keepKeyfiles -> emptyList()
                            applySessionKeyfiles -> keyfileUris
                            else -> unlockUris
                        }
                        rememberUnlock(nextPassword, nextPim.toString())
                        wipeUnlockForm()
                    }
                    onStatus(if (rc == 0) successMessage else headerErrorMessage(rc))
                }
            } catch (_: Exception) {
                runOnUiThread {
                    endWork()
                    onStatus("Could not rewrite the volume header.")
                }
            } finally {
                temps.forEach { KeyfileIo.wipe(it) }
                sessionTemps.forEach { KeyfileIo.wipe(it) }
            }
        }.start()
    }

    private fun backupVolumeHeader(
        volumePath: String,
        password: String,
        pimText: String,
        useTextPassword: Boolean,
        keyfileUris: List<Uri>,
        onHandle: (Long) -> Unit,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit,
        onSaved: (File) -> Unit
    ) {
        val resolved = ensureContainerPath(volumePath, containerUriState.value)
        if (resolved.isEmpty()) {
            onStatus(
                if (volumePath.isEmpty() && containerUriState.value == null)
                    "Choose a container first."
                else
                    "Could not read the container file. Choose it again from Files."
            )
            return
        }
        if (resolved != volumePath) pathState.value = resolved
        val text = unlockPassword(password, useTextPassword)
        val pimUsed = unlockPimText(password, pimText)
        closeMountedVolume()
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
                val dest = File(cacheDir, "volume-header.bak")
                val rc = NativeBridge.backupHeaders(
                    resolved,
                    dest.absolutePath,
                    text,
                    pimUsed.toIntOrNull() ?: 0,
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
        onHandle: (Long) -> Unit,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit
    ) {
        val resolved = ensureContainerPath(volumePath, containerUriState.value)
        if (resolved.isEmpty()) {
            onStatus(
                if (volumePath.isEmpty() && containerUriState.value == null)
                    "Choose a container first."
                else
                    "Could not read the container file. Choose it again from Files."
            )
            return
        }
        if (resolved != volumePath) pathState.value = resolved
        val text = unlockPassword(password, useTextPassword)
        val pimUsed = unlockPimText(password, pimText)
        closeMountedVolume()
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
                val rc = NativeBridge.restoreHeaders(
                    resolved,
                    backup.absolutePath,
                    text,
                    pimUsed.toIntOrNull() ?: 0,
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

    private fun releasePendingPfd() {
        try {
            pendingContainerPfd?.close()
        } catch (_: Exception) {
        }
        pendingContainerPfd = null
    }

    private fun releaseAllContainerPfds() {
        releasePendingPfd()
        liveContainerPfds.values.forEach {
            try {
                it.close()
            } catch (_: Exception) {
            }
        }
        liveContainerPfds.clear()
    }

    private fun nativePathIsInternal(nativePath: String): Boolean {
        return nativePath.startsWith("/proc/self/fd/") ||
            nativePath.startsWith(cacheDir.absolutePath) ||
            nativePath.startsWith(filesDir.absolutePath)
    }

    private fun containerPathUsable(path: String): Boolean {
        if (path.isEmpty() || path.startsWith("/proc/self/fd/")) return false
        val file = File(path)
        return file.isFile && file.canRead() && file.length() > 0L
    }

    /**
     * Home / Dismount closes SAF descriptors. `/proc/self/fd/N` is then a dead
     * path, and native open reports "Could not read the container file."
     * Copy into cache so Open still has a real file. Never pass a proc fd path
     * to VeraCrypt File::Open.
     */
    private fun ensureContainerPath(path: String, uri: Uri?): String {
        if (containerPathUsable(path)) return path
        if (uri != null) {
            val bound = bindContainer(uri)
            if (containerPathUsable(bound)) return bound
        }
        return ""
    }

    private fun bindContainer(uri: Uri): String {
        releasePendingPfd()
        if (uri.scheme == "file") {
            val p = uri.path
            if (!p.isNullOrEmpty() && containerPathUsable(p)) return p
        }
        return copyToCache(uri)
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
        onHandle: (Long) -> Unit,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit
    ) {
        val resolved = ensureContainerPath(path, containerUriState.value)
        if (resolved.isEmpty()) {
            onStatus(
                if (path.isEmpty() && containerUriState.value == null)
                    "Choose a container first."
                else
                    "Could not read the container file. Choose it again from Files."
            )
            return
        }
        if (resolved != path) pathState.value = resolved
        val already = mountedVolumesState.value
        if (already.size >= MOUNT_SLOTS) {
            onStatus("This session already has 8 volumes mounted. Dismount one first.")
            return
        }
        val key = containerUriState.value?.toString() ?: resolved
        if (already.any { it.uriKey == key || it.path == resolved }) {
            onStatus("That container is already mounted. Switch to it on the Mounted tab.")
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
                val result = NativeBridge.openVolume(
                    resolved,
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
                        onStatus(listErrorMessage(parsed[0].size.toInt()))
                    } else {
                        pendingContainerPfd?.let { pfd ->
                            liveContainerPfds[result] = pfd
                            pendingContainerPfd = null
                        }
                        persistActiveMount(dirPathState.value, entriesState.value, listTruncatedState.value)
                        val label = containerLabelState.value.ifEmpty { File(resolved).name }
                        val uriKey = containerUriState.value?.toString() ?: resolved
                        val next = mountedVolumesState.value + MountedVolume(
                            handle = result,
                            label = label,
                            path = resolved,
                            uriKey = uriKey,
                            dirPath = "",
                            entries = files,
                            truncated = truncated
                        )
                        mountedVolumesState.value = next
                        activeMountIndexState.intValue = next.lastIndex
                        headerKeyfileUrisState.value = keyfileUris
                        rememberUnlock(text, pimText)
                        wipeUnlockForm()
                        onHandle(result)
                        onEntries(files)
                        dirPathState.value = ""
                        listTruncatedState.value = truncated
                        tabState.intValue = 3
                        var msg = "Mounted in this app. Size $volumeBytes bytes. Slots are on the Mounted tab. Tap a folder to open it, or a file to select it. Copy to volume moves selected files into another mounted container."
                        if (next.size > 1) msg = "${next.size} volumes mounted. $msg"
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
        tabState.intValue = 0
        pathState.value = copyIncomingAsContainer(first)
        containerLabelState.value = first.name
        statusState.value = "Received ${first.name}. Any extension can be a volume. Open with the correct password, PIM, and keyfiles, or share as-is."
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
            else -> onStatus("Tap files in an open volume, then Share decrypted.")
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
        append: Boolean = false,
        quiet: Boolean = false
    ) {
        if (!NativeBridge.isOpen(handle)) return
        beginWork(if (append) "Loading more…" else "Reading folder…", updateStatus = !quiet)
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
                    val shown = if (append) entriesState.value + files else files
                    onEntries(shown)
                    listTruncatedState.value = truncated
                    persistActiveMount(path, shown, truncated)
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
        uris: List<Uri>,
        existingNames: Set<String>,
        move: Boolean,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit
    ) {
        if (!NativeBridge.isOpen(handle)) {
            onStatus("Open a volume first.")
            return
        }
        if (uris.isEmpty()) return
        val verb = if (move) "Moving" else "Copying"
        beginWork(
            if (uris.size == 1) "$verb from device…"
            else "$verb ${uris.size} files from device…"
        )
        Thread {
            val used = existingNames.toMutableSet()
            var copied = 0
            var moved = 0
            var lastError: String? = null
            val destDir = if (dirPath.isEmpty()) "/" else dirPath
            for (uri in uris) {
                var cache: File? = null
                try {
                    val display = ShareHelper.displayName(this, uri) ?: "file"
                    val name = uniqueDestName(display, used)
                    val outFile = copyUriForNativeImport(uri, name, "Reading $name")
                    if (outFile == null) {
                        lastError = "Could not read $display. Pick it again from Files."
                        continue
                    }
                    cache = outFile
                    val rc = NativeBridge.importFile(handle, destDir, outFile.absolutePath, name)
                    if (rc != 0) {
                        lastError = importErrorMessage(name, rc, handle)
                        continue
                    }
                    copied++
                    if (move && tryDeleteDocument(uri)) moved++
                } catch (_: Exception) {
                    lastError = "Could not copy that file into the volume."
                } finally {
                    cache?.delete()
                }
            }
            runOnUiThread {
                endWork()
                onStatus(
                    when {
                        lastError != null && copied == 0 -> lastError
                        move && moved < copied ->
                            "Copied $copied file(s) into the volume. Could not delete the original; remove them in Files if you meant a move."
                        move && copied == uris.size ->
                            "Moved $copied file(s) into the volume."
                        copied == uris.size ->
                            "Copied $copied file(s) from the device into this folder."
                        else ->
                            "Copied $copied of ${uris.size} file(s) into the volume. $lastError"
                    }
                )
                if (copied > 0) loadDir(handle, dirPath, onEntries, onStatus, quiet = true)
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
                            loadDir(handle, dirPath, onEntries, onStatus, quiet = true)
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

    private fun createDocumentInTree(treeUri: Uri, name: String): Uri? {
        return try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            DocumentsContract.createDocument(
                contentResolver,
                parent,
                "application/octet-stream",
                name
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun exportManyToDevice(
        handle: Long,
        dirPath: String,
        files: List<VaultEntry>,
        treeUri: Uri,
        move: Boolean,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit
    ) {
        if (!NativeBridge.isOpen(handle)) {
            onStatus("Open a volume first.")
            return
        }
        val toCopy = files.filter { !it.isDir }
        if (toCopy.isEmpty()) {
            onStatus("Tap one or more files in the volume, then Copy to device.")
            return
        }
        val verb = if (move) "Moving" else "Copying"
        beginWork("$verb ${toCopy.size} files to device…")
        Thread {
            var copied = 0
            var moved = 0
            var lastError: String? = null
            for (entry in toCopy) {
                val dest = File(cacheDir, "to-device-${System.nanoTime()}-${ShareHelper.safeName(entry.name)}")
                try {
                    val volumePath = joinDir(dirPath, entry.name)
                    val rc = NativeBridge.exportFile(handle, volumePath, dest.absolutePath)
                    if (rc != 0 || !dest.exists()) {
                        lastError = extractErrorMessage(entry.name, rc)
                        continue
                    }
                    val outUri = createDocumentInTree(treeUri, entry.name)
                    if (outUri == null) {
                        lastError = "Could not save ${entry.name} in that folder."
                        continue
                    }
                    val wrote = contentResolver.openOutputStream(outUri)?.use { out ->
                        dest.inputStream().use { input ->
                            copyStreamProgress(input, out, dest.length(), "Saving ${entry.name}")
                        }
                        true
                    } ?: false
                    if (!wrote) {
                        lastError = "Could not save ${entry.name} on the device."
                        continue
                    }
                    copied++
                    if (move && NativeBridge.deleteFile(handle, volumePath) == 0) moved++
                } catch (_: Exception) {
                    lastError = "Could not copy ${entry.name} to the device."
                } finally {
                    dest.delete()
                }
            }
            runOnUiThread {
                endWork()
                onStatus(
                    when {
                        lastError != null && copied == 0 -> lastError
                        move && moved < copied ->
                            "Copied $copied file(s) to the device, but could not remove ${copied - moved} from the volume."
                        move && copied == toCopy.size ->
                            "Moved $copied file(s) to the device."
                        copied == toCopy.size ->
                            "Copied $copied file(s) to the device."
                        else ->
                            "Copied $copied of ${toCopy.size} file(s) to the device. $lastError"
                    }
                )
                if (move && moved > 0) loadDir(handle, dirPath, onEntries, onStatus, quiet = true)
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
                if (rc != 0) onStatus(importErrorMessage(name, rc, handle))
                else {
                    onStatus("Created folder $name.")
                    loadDir(handle, dirPath, onEntries, onStatus, quiet = true)
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
                if (rc != 0) onStatus(importErrorMessage(entry.name, rc, handle))
                else {
                    onStatus("Renamed ${entry.name} to $newName.")
                    loadDir(handle, dirPath, onEntries, onStatus, quiet = true)
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
                        else importErrorMessage(entry.name, rc, handle)
                    )
                } else {
                    onStatus("Deleted ${entry.name}.")
                    loadDir(handle, dirPath, onEntries, onStatus, quiet = true)
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
                if (rc != 0) {
                    onStatus(
                        if (NativeBridge.protectionTriggered(handle))
                            "Hidden volume protection triggered. The outer volume is now write-protected until you dismount."
                        else
                            "Could not wipe free space (code $rc). Read-only volumes refuse this."
                    )
                } else {
                    onStatus("Wiped unused FAT clusters. Deleted file contents in free space are overwritten.")
                    loadDir(handle, dirPath, onEntries, onStatus, quiet = true)
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
        onHandle: (Long) -> Unit,
        onEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit
    ) {
        val resolved = ensureContainerPath(volumePath, containerUriState.value)
        if (resolved.isEmpty()) {
            onStatus(
                if (volumePath.isEmpty() && containerUriState.value == null)
                    "Choose a container first."
                else
                    "Could not read the container file. Choose it again from Files."
            )
            return
        }
        if (resolved != volumePath) pathState.value = resolved
        val text = unlockPassword(password, useTextPassword)
        val pimUsed = unlockPimText(password, pimText)
        closeMountedVolume()
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
                val rc = NativeBridge.restoreHeaders(
                    resolved,
                    "",
                    text,
                    pimUsed.toIntOrNull() ?: 0,
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
        val outFile = KeyfileIo.uniqueNamed(File(cacheDir, "containers").apply { mkdirs() }, name)
        if (file.length() > 0 && cacheDir.usableSpace < file.length() + (32L shl 20)) {
            return ""
        }
        file.copyTo(outFile, overwrite = false)
        return outFile.absolutePath
    }

    private fun transferBetweenVolumes(
        srcHandle: Long,
        srcDir: String,
        files: List<VaultEntry>,
        dest: MountedVolume,
        move: Boolean,
        onSrcEntries: (List<VaultEntry>) -> Unit,
        onStatus: (String) -> Unit
    ) {
        if (!NativeBridge.isOpen(srcHandle) || !NativeBridge.isOpen(dest.handle)) {
            onStatus("Open both volumes first.")
            return
        }
        val toCopy = files.filter { !it.isDir }
        if (toCopy.isEmpty()) {
            onStatus("Tap one or more files, then Copy to volume or Move to volume.")
            return
        }
        if (srcHandle == dest.handle) {
            onStatus("Pick a different volume.")
            return
        }
        val label = dest.label
        val verb = if (move) "Moving" else "Copying"
        beginWork(
            if (toCopy.size == 1) "$verb ${toCopy[0].name} to $label…"
            else "$verb ${toCopy.size} files to $label…"
        )
        Thread {
            var copied = 0
            var moved = 0
            var lastError: String? = null
            for (entry in toCopy) {
                val temp = File(cacheDir, "xfer-${System.nanoTime()}-${ShareHelper.safeName(entry.name)}")
                try {
                    val srcPath = joinDir(srcDir, entry.name)
                    val rcExport = NativeBridge.exportFile(srcHandle, srcPath, temp.absolutePath)
                    if (rcExport != 0 || !temp.exists()) {
                        lastError = extractErrorMessage(entry.name, rcExport)
                        continue
                    }
                    val destDir = dest.dirPath.ifEmpty { "/" }
                    val rcImport = NativeBridge.importFile(dest.handle, destDir, temp.absolutePath, entry.name)
                    if (rcImport != 0) {
                        lastError = importErrorMessage(entry.name, rcImport, dest.handle)
                        continue
                    }
                    copied++
                    if (move) {
                        if (NativeBridge.deleteFile(srcHandle, srcPath) == 0) moved++
                    }
                } catch (_: Exception) {
                    lastError = "Could not copy ${entry.name} into the other volume."
                } finally {
                    Hardening.wipeFile(temp)
                }
            }
            runOnUiThread {
                endWork()
                onStatus(
                    when {
                        lastError != null && copied == 0 -> lastError
                        move && moved < copied ->
                            "Copied $copied file(s) into $label. Could not delete ${copied - moved} from the source volume."
                        move && copied == toCopy.size ->
                            "Moved $copied file(s) into $label."
                        copied == toCopy.size ->
                            "Copied $copied file(s) into $label."
                        else ->
                            "Copied $copied of ${toCopy.size} file(s) into $label. $lastError"
                    }
                )
                loadDir(srcHandle, srcDir, { listed ->
                    onSrcEntries(listed)
                    persistActiveMount(srcDir, listed, listTruncatedState.value)
                }, onStatus, quiet = true)
                refreshMountedListing(dest.handle, dest.dirPath)
            }
        }.start()
    }

    private fun refreshMountedListing(handle: Long, dirPath: String) {
        if (!NativeBridge.isOpen(handle)) return
        Thread {
            try {
                val listed = NativeBridge.listDir(handle, if (dirPath.isEmpty()) "/" else dirPath)
                val parsed = listed.mapNotNull { parseEntry(it) }
                val truncated = parsed.any { it.name == "!truncated!" }
                val files = parsed.filter { it.name != "!error!" && it.name != "!truncated!" }
                runOnUiThread {
                    val list = mountedVolumesState.value.toMutableList()
                    val i = list.indexOfFirst { it.handle == handle }
                    if (i >= 0) {
                        list[i] = list[i].copy(entries = files, truncated = truncated)
                        mountedVolumesState.value = list
                    }
                }
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun copyFileToUri(src: File, uri: Uri): Boolean {
        try {
            contentResolver.openFileDescriptor(uri, "w")?.use { pfd ->
                FileOutputStream(pfd.fileDescriptor).use { out ->
                    src.inputStream().use { input -> input.copyTo(out) }
                    out.flush()
                    pfd.fileDescriptor.sync()
                }
            } ?: return false
            return true
        } catch (_: Exception) {
            return try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    src.inputStream().use { input -> input.copyTo(out) }
                    out.flush()
                } != null
            } catch (_: Exception) {
                false
            }
        }
    }

    private fun copyToCache(uri: Uri): String {
        val display = ShareHelper.displayName(this, uri) ?: "volume.hc"
        val name = ShareHelper.sanitizeDisguiseName(display)
        val len = uriLength(uri)
        if (len > 0 && cacheDir.usableSpace < len + (32L shl 20)) {
            return ""
        }
        val input = KeyfileIo.openReadable(this, uri) ?: return ""
        val outFile = KeyfileIo.uniqueNamed(File(cacheDir, "containers").apply { mkdirs() }, name)
        try {
            outFile.outputStream().use { output ->
                copyStreamProgress(input, output, len, "Copying container")
            }
        } finally {
            input.close()
        }
        if (!outFile.exists() || outFile.length() == 0L) {
            outFile.delete()
            return ""
        }
        if (len > 0 && outFile.length() < len) {
            outFile.delete()
            return ""
        }
        return outFile.absolutePath
    }
}

@Composable
private fun VaultPane(
    modifier: Modifier = Modifier,
    dirPath: String,
    entries: List<VaultEntry>,
    selectedNames: Set<String>,
    truncated: Boolean,
    busy: Boolean,
    mounts: List<MountedVolume>,
    activeMount: Int,
    onSelectMount: (Int) -> Unit,
    onDismountMount: (Int) -> Unit,
    onOpenAnother: () -> Unit,
    canTransfer: Boolean,
    onCopyToVolume: () -> Unit,
    onMoveToVolume: () -> Unit,
    onUp: () -> Unit,
    onGoToPath: (String) -> Unit,
    onOpen: (VaultEntry) -> Unit,
    onShare: (List<VaultEntry>) -> Unit,
    onCopyFromDevice: () -> Unit,
    onMoveFromDevice: () -> Unit,
    onCopyToDevice: () -> Unit,
    onMoveToDevice: () -> Unit,
    onNewFolder: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
    onWipeFreeSpace: () -> Unit,
    onSelectAll: () -> Unit,
    onMore: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val fileCount = entries.count { !it.isDir }
    val allFilesSelected = fileCount > 0 && entries.filter { !it.isDir }.all { it.name in selectedNames }
    val live = mounts.isNotEmpty()
    var folderMenu by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        Text(
            if (mounts.size > 1) "${mounts.size} volumes mounted" else "Mounted in this app",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
        )
        Text(
            "Slots are this session only. Not a system drive. Select files, then Copy to volume / Copy to device, or Copy from device.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onOpenAnother, enabled = !busy) { Text("Open another container") }
            TextButton(onClick = onSelectAll, enabled = !busy && live && fileCount > 0) {
                Text(if (allFilesSelected) "Clear selection" else "Select files")
            }
            if (canTransfer) {
                TextButton(
                    onClick = onCopyToVolume,
                    enabled = !busy && live,
                    modifier = Modifier.testTag("copy_to_volume")
                ) { Text("Copy to volume") }
                TextButton(
                    onClick = onMoveToVolume,
                    enabled = !busy && live,
                    modifier = Modifier.testTag("move_to_volume")
                ) { Text("Move to volume") }
            }
            TextButton(
                onClick = onNewFolder,
                enabled = !busy && live,
                modifier = Modifier.testTag("new_folder")
            ) { Text("New folder") }
            TextButton(
                onClick = onWipeFreeSpace,
                enabled = !busy && live,
                modifier = Modifier.testTag("wipe_free_space")
            ) { Text("Wipe free space") }
            Box {
                TextButton(onClick = { folderMenu = true }, enabled = !busy && live) { Text("Folder") }
                DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Copy from device") },
                        onClick = { folderMenu = false; onCopyFromDevice() }
                    )
                    DropdownMenuItem(
                        text = { Text("Move from device") },
                        onClick = { folderMenu = false; onMoveFromDevice() }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy to device") },
                        onClick = { folderMenu = false; onCopyToDevice() }
                    )
                    DropdownMenuItem(
                        text = { Text("Move to device") },
                        onClick = { folderMenu = false; onMoveToDevice() }
                    )
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        onClick = { folderMenu = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { folderMenu = false; onDelete() }
                    )
                    DropdownMenuItem(
                        text = { Text("Properties") },
                        onClick = { folderMenu = false; onProperties() }
                    )
                    DropdownMenuItem(
                        text = { Text("Share decrypted") },
                        onClick = {
                            folderMenu = false
                            onShare(entries.filter { it.name in selectedNames && !it.isDir })
                        }
                    )
                }
            }
        }
        HorizontalDivider(color = colors.outline.copy(alpha = 0.25f))
        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                Modifier
                    .width(120.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("No.", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(22.dp))
                    Text("Volume", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                for (slot in 0 until MOUNT_SLOTS) {
                    val vol = mounts.getOrNull(slot)
                    val selected = vol != null && slot == activeMount
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (selected) colors.primaryContainer else Color.Transparent)
                            .clickable(enabled = !busy) {
                                if (vol != null) onSelectMount(slot) else onOpenAnother()
                            }
                            .padding(horizontal = 8.dp, vertical = 7.dp)
                            .testTag("mount_slot_$slot"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${slot + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.width(22.dp)
                        )
                        Text(
                            vol?.label ?: "Empty",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (vol == null) colors.onSurfaceVariant else colors.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (vol != null) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Dismount ${vol.label}",
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable(enabled = !busy) { onDismountMount(slot) }
                            )
                        }
                    }
                }
            }
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(colors.outline.copy(alpha = 0.4f))
            )
            Column(Modifier.weight(1f).fillMaxHeight()) {
        Row(
            Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (dirPath.isNotEmpty()) {
                OutlinedButton(onClick = onUp, enabled = !busy, modifier = Modifier.testTag("vault_up")) { Text("Up") }
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
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("Name", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("Size", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        if (entries.isEmpty() && !busy) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    if (!live) "No volume in this slot." else "This folder is empty.",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    if (!live) {
                        "Open volume on the Volume tab, or tap an empty slot. This is not a system drive."
                    } else {
                        "Tap a folder to open it, or Copy from device to add files. Copy to device writes files Files can open."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("vault_list"),
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
                        Text(entry.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (entry.isDir) {
                            Text(
                                "Folder — tap to open",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant
                            )
                        }
                    }
                    if (!entry.isDir) {
                        Text(
                            formatSize(entry.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
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
    }
}

private fun formatSize(size: Long): String {
    if (size < 1024) return "$size B"
    if (size < 1024 * 1024) return "${size / 1024} KB"
    if (size < 1024L * 1024 * 1024) return "${size / (1024 * 1024)} MB"
    return "${size / (1024L * 1024 * 1024)} GB"
}
