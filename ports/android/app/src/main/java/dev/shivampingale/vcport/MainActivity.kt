package dev.shivampingale.vcport

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val truncated: Boolean = false,
    val readOnly: Boolean = false
)

/** Session slot list. This session only; not a system drive letter. */
internal const val MOUNT_SLOTS = 8

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : AppCompatActivity() {
    internal val pathState = mutableStateOf("")
    internal val containerUriState = mutableStateOf<Uri?>(null)
    internal val statusState = mutableStateOf("Stay offline. Select a VeraCrypt container, or share an encrypted file as-is.")
    internal val otgDevicesState = mutableStateOf<List<UsbDevice>>(emptyList())
    internal val otgCandidatesState = mutableStateOf<List<OtgCandidate>>(emptyList())
    private var pendingOtgScsi: OtgScsiDevice? = null
    private var pendingOtgFile: File? = null
    private var usbPermissionReceiver: BroadcastReceiver? = null
    internal val incomingState = mutableStateOf<File?>(null)
    internal val passwordState = mutableStateOf("")
    internal val pimState = mutableStateOf("0")
    internal val createPimState = mutableStateOf("0")
    internal val createHiddenPimState = mutableStateOf("0")
    internal val newPimState = mutableStateOf("0")
    internal val hiddenProtectPimState = mutableStateOf("0")
    internal val keyfileUrisState = mutableStateOf(listOf<Uri>())
    internal val headerKeyfileUrisState = mutableStateOf(listOf<Uri>())
    internal val basketUrisState = mutableStateOf(listOf<Uri>())
    internal val basketHashesState = mutableStateOf(mapOf<String, String>())
    internal val hiddenKeyfileUrisState = mutableStateOf(listOf<Uri>())
    internal val keyfileGenNameState = mutableStateOf("keyfile.bin")
    internal val keyfileGenCountState = mutableStateOf("1")
    internal val containerLabelState = mutableStateOf("")
    internal val handleState = mutableStateOf(0L)
    internal val mountedVolumesState = mutableStateOf(listOf<MountedVolume>())
    internal val activeMountIndexState = mutableIntStateOf(0)
    internal val entriesState = mutableStateOf(listOf<VaultEntry>())
    internal val selectedNamesState = mutableStateOf(setOf<String>())
    internal val dirPathState = mutableStateOf("")
    internal val listTruncatedState = mutableStateOf(false)
    internal val busyState = mutableStateOf(false)
    internal val hashResultState = mutableStateOf("")
    internal val pimEstimateResultState = mutableStateOf("")
    internal val useBackupHeaderState = mutableStateOf(false)
    internal val readOnlyOpenState = mutableStateOf(false)
    internal val idleMinutesState = mutableIntStateOf(0)
    internal val trueCryptModeState = mutableStateOf(false)
    internal val protectHiddenState = mutableStateOf(false)
    internal val tabState = mutableIntStateOf(0)
    internal val lastPlainFilesState = mutableStateOf(listOf<File>())
    internal val previewFileState = mutableStateOf<File?>(null)
    internal val previewNameState = mutableStateOf("")
    internal val createPasswordState = mutableStateOf("")
    internal val createHiddenPasswordState = mutableStateOf("")
    internal val createCipherState = mutableStateOf(NativeBridge.DEFAULT_CIPHER)
    internal val createKdfState = mutableStateOf(NativeBridge.DEFAULT_KDF)
    internal val createFilesystemState = mutableStateOf("FAT")
    internal val createFileNameState = mutableStateOf("volume.hc")
    internal val createSizeAmountState = mutableStateOf("16")
    internal val createSizeUnitState = mutableStateOf(SizeUnit.MiB)
    internal val createHiddenState = mutableStateOf(false)
    internal val createHiddenSizeAmountState = mutableStateOf("4")
    internal val createHiddenSizeUnitState = mutableStateOf(SizeUnit.MiB)
    internal val entropyPercentState = mutableIntStateOf(0)
    internal val newPasswordState = mutableStateOf("")
    internal val hiddenProtectPasswordState = mutableStateOf("")
    private var suppressLock = false
    private val idleHandler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable {
        if (!suppressLock && NativeBridge.isOpen(handleState.value)) {
            closeOpenVolumes("Idle timeout. Volume closed.")
        }
    }
    private var screenOffReceiver: BroadcastReceiver? = null
    private var lastUnlockPassword = ""
    private var lastUnlockPim = "0"
    private var pendingContainerPfd: ParcelFileDescriptor? = null
    private val liveContainerPfds = mutableMapOf<Long, ParcelFileDescriptor>()
    /** Last SAF/cache copy failure (space vs unreadable). Read on the UI thread after bind. */
    private var lastContainerCopyError = ""
    /** File pickers stop this activity. Do not wipe session fields in that gap. */
    internal fun holdLockForPicker() {
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
            wipeCreateSecrets()
            statusState.value =
                "Saved ${dest.name}. Choose the volume you want, then Open. Create secrets were wiped."
            tabState.intValue = 0
        }
        return true
    }

    /**
     * Emulator has no USB Host. Instrumented UI tests inject a disk image so
     * the Volume tab can show a partition button and Open `/vcport-otg-dev/N`.
     */
    @androidx.annotation.VisibleForTesting
    fun testingInjectFakeUsb(disk: File, byteOffset: Long, byteLength: Long, label: String) {
        val done = CountDownLatch(1)
        runOnUiThread {
            try {
                pendingOtgScsi?.close()
                pendingOtgScsi = null
                pendingOtgFile = disk
                otgCandidatesState.value = listOf(OtgCandidate(label, byteOffset, byteLength))
                pathState.value = ""
                containerUriState.value = null
                containerLabelState.value = ""
                statusState.value =
                    "Simulated USB disk. Pick a partition, then type the password and Open volume. Nothing auto-mounted."
                tabState.intValue = 0
            } finally {
                done.countDown()
            }
        }
        done.await(5, TimeUnit.SECONDS)
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
    fun testingFireIdleTimeout() {
        runOnUiThread {
            idleHandler.removeCallbacks(idleRunnable)
            closeOpenVolumes("Idle timeout. Volume closed.")
        }
    }

    @androidx.annotation.VisibleForTesting
    fun testingHashSelected(name: String) {
        runOnUiThread {
            val entry = entriesState.value.firstOrNull { it.name == name } ?: return@runOnUiThread
            hashVaultFiles(handleState.value, dirPathState.value, listOf(entry)) { statusState.value = it }
        }
    }

    @androidx.annotation.VisibleForTesting
    fun testingPimEstimate(): String = PimEstimator.describe(createKdfState.value, pimState.value)

    @androidx.annotation.VisibleForTesting
    fun testingVolumeInfo(): String? {
        val handle = handleState.value
        if (!NativeBridge.isOpen(handle)) return null
        return NativeBridge.volumeInfo(handle)
    }

    private fun lookPrefs() = getSharedPreferences("vc_port_look", MODE_PRIVATE)

    private fun sessionPrefs() = getSharedPreferences("vc_port_session", MODE_PRIVATE)

    private fun loadIdleMinutes(): Int {
        val n = sessionPrefs().getInt("idle_minutes", 0)
        return n.coerceIn(0, SessionIdle.MAX_MINUTES)
    }

    private fun saveIdleMinutes(minutes: Int) {
        sessionPrefs().edit().putInt("idle_minutes", minutes).apply()
        idleMinutesState.intValue = minutes
        armIdleTimer()
    }

    private fun armIdleTimer() {
        idleHandler.removeCallbacks(idleRunnable)
        val minutes = idleMinutesState.intValue
        if (minutes <= 0 || !NativeBridge.isOpen(handleState.value)) return
        idleHandler.postDelayed(idleRunnable, minutes * 60_000L)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN || ev.action == MotionEvent.ACTION_MOVE) {
            armIdleTimer()
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun loadSkin(): VcSkin {
        val name = lookPrefs().getString("skin", VcSkin.Desktop.name) ?: VcSkin.Desktop.name
        return when (name) {
            VcSkin.Signal.name, "DarkMode" -> VcSkin.Signal
            else -> VcSkin.Desktop
        }
    }

    internal fun saveSkin(skin: VcSkin) {
        lookPrefs().edit().putString("skin", skin.name).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Hardening.protectWindow(this)
        idleMinutesState.intValue = loadIdleMinutes()
        handleIncoming(intent)
        screenOffReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF && NativeBridge.isOpen(handleState.value)) {
                    closeOpenVolumes("Screen locked. Volume closed.")
                }
            }
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }
        usbPermissionReceiver = OtgUsb.registerPermissionReceiver(this) { device ->
            openUsbMassStorage(device)
        }
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
                var useBiometric by remember { mutableStateOf(false) }
                var shareWithFiles by remember { mutableStateOf(OtgMountShare.shareWithFiles) }
                var otgDevices by otgDevicesState
                var otgCandidates by otgCandidatesState
                var status by statusState
                var entries by entriesState
                var handle by handleState
                var mountedVolumes by mountedVolumesState
                var activeMountIndex by activeMountIndexState
                var dirPath by dirPathState
                var listTruncated by listTruncatedState
                var busy by busyState
                var hashResult by hashResultState
                var pimEstimateResult by pimEstimateResultState
                var tab by tabState
                var previewFile by previewFileState
                var previewName by previewNameState
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
                val idleSplit = SessionIdle.split(idleMinutesState.intValue)
                var idleAmount by remember { mutableStateOf(idleSplit.first.toString()) }
                var idleUnit by remember { mutableStateOf(idleSplit.second) }
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
                                lastContainerCopyError.ifEmpty { SizeUnits.APP_STORAGE_UNREADABLE }
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
                    val skipped = mutableListOf<String>()
                    for (uri in uris) {
                        ShareHelper.persistRead(this@MainActivity, uri)
                        val copied = KeyfileIo.copyOwned(this@MainActivity, uri)
                        if (copied == null) {
                            failed = ShareHelper.displayName(this@MainActivity, uri) ?: "keyfile"
                        } else {
                            val owned = Uri.fromFile(copied)
                            if (sessionHasKeyfile(kept, copied)) {
                                skipped += copied.name
                            } else {
                                kept += owned
                            }
                        }
                    }
                    keyfileUris = kept
                    status = keyfileAddStatus(failed, skipped, nested = false)
                }
                val hiddenKeyfilePicker = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenMultipleDocuments()
                ) { uris: List<Uri> ->
                    val kept = hiddenKeyfileUris.toMutableList()
                    var failed: String? = null
                    val skipped = mutableListOf<String>()
                    for (uri in uris) {
                        ShareHelper.persistRead(this@MainActivity, uri)
                        val copied = KeyfileIo.copyOwned(this@MainActivity, uri)
                        if (copied == null) {
                            failed = ShareHelper.displayName(this@MainActivity, uri) ?: "keyfile"
                        } else {
                            val owned = Uri.fromFile(copied)
                            if (sessionHasKeyfile(kept, copied)) {
                                skipped += copied.name
                            } else {
                                kept += owned
                            }
                        }
                    }
                    hiddenKeyfileUris = kept
                    status = keyfileAddStatus(failed, skipped, nested = true)
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
                        val savedName = ShareHelper.displayName(this@MainActivity, uri)
                            ?: File(path).name
                        ShareHelper.persistRead(this@MainActivity, uri)
                        if (copyFileToUri(File(path), uri)) {
                            incoming = null
                            wipeCreateSecrets()
                            status = "Saved $savedName. Choose the volume you want, then Open. Create secrets were wiped."
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
                                IconButton(
                                    onClick = { runPanic() },
                                    modifier = Modifier.testTag("panic_wipe")
                                ) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = "Panic wipe",
                                        tint = colors.error
                                    )
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
                                        status = if (path.isEmpty())
                                            lastContainerCopyError.ifEmpty { SizeUnits.APP_STORAGE_UNREADABLE }
                                        else
                                            "Selected ${file.name}. Open volume to browse folders here."
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
                                Tab(selected = tab == 2, onClick = { tab = 2 }, modifier = Modifier.testTag("tab_mounted"), text = { Text("Mounted") })
                                Tab(selected = tab == 3, onClick = { tab = 3 }, modifier = Modifier.testTag("tab_tools"), text = { Text("Tools") })
                            }
                            @Composable
                            fun BindOpenVolumeForm(mountedSlot: Boolean, onCancel: (() -> Unit)?) {
                                OpenVolumeForm(
                                    busy = busy,
                                    mountedSlot = mountedSlot,
                                    containerLabel = containerLabel,
                                    shownPath = path.takeIf { it.isNotEmpty() && !nativePathIsInternal(it) },
                                    onChooseContainer = {
                                        holdLockForPicker()
                                        picker.launch(arrayOf("*/*"))
                                    },
                                    onShareEncryptedPick = {
                                        holdLockForPicker()
                                        shareEncPicker.launch(arrayOf("*/*"))
                                    },
                                    onShareThis = if (containerUri != null || path.isNotEmpty()) {
                                        {
                                            shareEncryptedVolume(containerUri, path) { status = it }
                                        }
                                    } else {
                                        null
                                    },
                                    otgSlot = {
                                        if (BuildConfig.ENABLE_OTG_DISK) {
                                            OtgVolumePanel(
                                                busy = busy,
                                                devices = otgDevices,
                                                candidates = otgCandidates,
                                                shareWithFiles = shareWithFiles,
                                                onShareWithFiles = {
                                                    shareWithFiles = it
                                                    OtgMountShare.shareWithFiles = it
                                                    refreshDocumentRoots()
                                                },
                                                onScan = {
                                                    pendingOtgFile = null
                                                    otgDevices = OtgUsb.massStorageDevices(this@MainActivity)
                                                    otgCandidates = emptyList()
                                                    status = if (otgDevices.isEmpty()) {
                                                        "No USB mass-storage device. Plug a stick, then Scan USB disks. This never auto-mounts."
                                                    } else {
                                                        "Found ${otgDevices.size} USB disk(s). Tap one. Grant permission. Then pick a partition and Open volume."
                                                    }
                                                },
                                                onPickDevice = { device ->
                                                    if (OtgUsb.hasPermission(this@MainActivity, device)) {
                                                        openUsbMassStorage(device)
                                                    } else {
                                                        OtgUsb.requestPermission(this@MainActivity, device)
                                                        status = "Grant USB permission, then the partition list appears. Still no auto-mount."
                                                    }
                                                },
                                                onPickPartition = { cand ->
                                                    val scsi = pendingOtgScsi
                                                    val fake = pendingOtgFile
                                                    when {
                                                        scsi != null -> {
                                                            try {
                                                                pendingOtgScsi = null
                                                                val otgPath = OtgBlockStore.bind(scsi, cand)
                                                                path = otgPath
                                                                containerUri = null
                                                                containerLabel = cand.label
                                                                status = "Selected ${cand.label}. Type the volume password and Open volume. Files app stays closed until you tick Allow Files to browse."
                                                            } catch (_: Exception) {
                                                                status = "Could not bind USB partition."
                                                                scsi.close()
                                                            }
                                                        }
                                                        fake != null -> {
                                                            try {
                                                                pendingOtgFile = null
                                                                val otgPath = OtgBlockStore.bindFile(
                                                                    fake,
                                                                    cand.byteOffset,
                                                                    cand.byteLength,
                                                                    cand.label
                                                                )
                                                                path = otgPath
                                                                containerUri = null
                                                                containerLabel = cand.label
                                                                status = "Selected ${cand.label}. Type the volume password and Open volume. Files app stays closed until you tick Allow Files to browse."
                                                            } catch (_: Exception) {
                                                                status = "Could not bind USB partition."
                                                            }
                                                        }
                                                        else -> status = "Scan USB disks again."
                                                    }
                                                }
                                            )
                                        }
                                    },
                                    useTextPassword = useTextPassword,
                                    onUseTextPassword = { useTextPassword = it },
                                    password = password,
                                    onPassword = { password = it },
                                    pim = pim,
                                    onPim = { pim = it },
                                    keyfileLabels = keyfileUris.map {
                                        ShareHelper.displayName(this@MainActivity, it) ?: it.toString()
                                    },
                                    onRemoveKeyfile = { index ->
                                        keyfileUris = keyfileUris.filterIndexed { i, _ -> i != index }
                                    },
                                    onAddKeyfiles = {
                                        holdLockForPicker()
                                        keyfilePicker.launch(arrayOf("*/*"))
                                    },
                                    biometricSlot = {
                                        if (BuildConfig.ENABLE_BIOMETRIC) {
                                            val vault = BiometricVault(this@MainActivity)
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(useBiometric, { useBiometric = it }, enabled = !busy)
                                                Text(if (useBiometric) "On — extra keyfile mixed when opening" else "Fingerprint / face extra (off)")
                                            }
                                            VcHint("A compelled fingerprint still wins. GitHub flavor only. foss has no biometrics.")
                                            OutlinedButton(
                                                onClick = {
                                                    if (path.isEmpty()) {
                                                        status = "Choose a container or USB partition first."
                                                        return@OutlinedButton
                                                    }
                                                    vault.load(this@MainActivity, path) { bundle ->
                                                        if (bundle == null) {
                                                            status = "Fingerprint unlock cancelled."
                                                            return@load
                                                        }
                                                        password = bundle.password
                                                        pim = bundle.pim.toString()
                                                        useBiometric = bundle.hasBiometric()
                                                        status = "Filled from fingerprint. Then tap Open volume."
                                                    }
                                                },
                                                enabled = !busy,
                                                modifier = Modifier.fillMaxWidth()
                                            ) { Text("Unlock with fingerprint / face") }
                                        }
                                    },
                                    useBackupHeader = useBackupHeader,
                                    onBackupHeader = { useBackupHeader = it },
                                    readOnly = readOnlyOpen,
                                    onReadOnly = { readOnlyOpen = it },
                                    trueCryptMode = trueCryptMode,
                                    onTrueCryptMode = { trueCryptMode = it },
                                    protectHidden = protectHidden,
                                    onProtectHidden = { protectHidden = it },
                                    hiddenPassword = hiddenProtectPassword,
                                    onHiddenPassword = { hiddenProtectPassword = it },
                                    hiddenPim = hiddenProtectPim,
                                    onHiddenPim = { hiddenProtectPim = it },
                                    idleAmount = idleAmount,
                                    onIdleAmount = {
                                        idleAmount = it
                                        saveIdleMinutes(SessionIdle.toMinutes(it.toIntOrNull() ?: 0, idleUnit))
                                    },
                                    idleUnit = idleUnit,
                                    onIdleUnit = {
                                        idleUnit = it
                                        saveIdleMinutes(SessionIdle.toMinutes(idleAmount.toIntOrNull() ?: 0, it))
                                    },
                                    onOpen = {
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
                                    },
                                    onCancel = onCancel
                                )
                            }
                            if (tab == 2) {
                            VaultPane(
                                modifier = Modifier.weight(1f),
                                dirPath = dirPath,
                                entries = entries,
                                selectedNames = selectedNames,
                                truncated = listTruncated,
                                busy = busy,
                                mounts = mountedVolumes,
                                activeMount = activeMountIndex,
                                readOnly = mountedVolumes.getOrNull(activeMountIndex)?.readOnly == true,
                                onSelectMount = { index ->
                                    showOpenAnother = false
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
                                hashResult = hashResult,
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
                                onPreview = {
                                    val files = entries.filter { it.name in selectedNames && !it.isDir }
                                    if (!BuildConfig.ENABLE_IN_APP_PREVIEW) {
                                        status = "In-app preview is off in this build."
                                    } else if (files.size != 1) {
                                        status = "Tap one file, then View in app. Preview stays inside VC Port (not VLC or Files)."
                                    } else {
                                        startInAppPreview(handle, dirPath, files[0]) { status = it }
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
                                onHashSelected = {
                                    val files = entries.filter { it.name in selectedNames && !it.isDir }
                                    if (files.isEmpty()) {
                                        status = "Tap a file, then SHA-256 in volume."
                                    } else {
                                        hashVaultFiles(handle, dirPath, files) { status = it }
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
                            if (showOpenAnother) {
                                Dialog(
                                    onDismissRequest = { showOpenAnother = false },
                                    properties = DialogProperties(usePlatformDefaultWidth = false)
                                ) {
                                    androidx.compose.material3.Surface(
                                        modifier = Modifier
                                            .fillMaxWidth(0.96f)
                                            .fillMaxHeight(0.92f)
                                            .testTag("mounted_open_dialog"),
                                        shape = MaterialTheme.shapes.large,
                                        tonalElevation = 6.dp
                                    ) {
                                        Column(
                                            Modifier
                                                .fillMaxSize()
                                                .padding(12.dp)
                                        ) {
                                            Text("Open volume", style = MaterialTheme.typography.titleMedium)
                                            Column(
                                                Modifier
                                                    .weight(1f)
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                BindOpenVolumeForm(
                                                    mountedSlot = true,
                                                    onCancel = { showOpenAnother = false }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
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
                                        CreateVolumePane(
                                            busy = busy,
                                            onPickBasket = {
                                                holdLockForPicker()
                                                basketPicker.launch(arrayOf("*/*"))
                                            },
                                            onPickKeyfiles = {
                                                holdLockForPicker()
                                                keyfilePicker.launch(arrayOf("*/*"))
                                            },
                                            onPickHiddenKeyfiles = {
                                                holdLockForPicker()
                                                hiddenKeyfilePicker.launch(arrayOf("*/*"))
                                            },
                                            onSaveGeneratedKeyfile = { file, name ->
                                                pendingExportFile = file
                                                holdLockForPicker()
                                                window.decorView.post {
                                                    holdLockForPicker()
                                                    toolSaver.launch(name)
                                                }
                                            },
                                            onSaveCreatedVolume = { name ->
                                                holdLockForPicker()
                                                window.decorView.post {
                                                    holdLockForPicker()
                                                    createSaver.launch(name)
                                                }
                                            }
                                        )
                                    }
                                    3 -> {
                                        ToolsPane(
                                            busy = busy,
                                            path = path,
                                            password = password,
                                            pim = pim,
                                            useTextPassword = useTextPassword,
                                            keyfileUris = keyfileUris,
                                            useBackupHeader = useBackupHeader,
                                            headerKdf = headerKdf,
                                            onHeaderKdf = { headerKdf = it },
                                            handle = handle,
                                            onHandle = { handle = it },
                                            onEntries = { entries = it },
                                            skin = skin,
                                            onSkin = { skin = it },
                                            onPickRestoreHeader = {
                                                holdLockForPicker()
                                                restoreHeaderPicker.launch(arrayOf("*/*"))
                                            },
                                            onPickKeyfiles = {
                                                holdLockForPicker()
                                                keyfilePicker.launch(arrayOf("*/*"))
                                            },
                                            onSaveGeneratedKeyfile = { file, name ->
                                                pendingExportFile = file
                                                holdLockForPicker()
                                                window.decorView.post {
                                                    holdLockForPicker()
                                                    toolSaver.launch(name)
                                                }
                                            }
                                        )
                                    }
                                    else -> {
                                        BindOpenVolumeForm(mountedSlot = false, onCancel = null)
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
            previewFile?.let { file ->
                InAppPreviewDialog(
                    file = file,
                    name = previewName,
                    onClose = {
                        InAppPreview.wipe(this@MainActivity)
                        previewFile = null
                        previewName = ""
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
        usbPermissionReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        usbPermissionReceiver = null
        screenOffReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        screenOffReceiver = null
        idleHandler.removeCallbacks(idleRunnable)
        pendingOtgScsi?.close()
        pendingOtgScsi = null
        pendingOtgFile = null
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
        if (OtgBlockStore.isPath(victim.path)) OtgBlockStore.release(victim.path)
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
            refreshDocumentRoots()
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
        refreshDocumentRoots()
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
        mountedVolumesState.value.forEach { vol ->
            if (OtgBlockStore.isPath(vol.path)) OtgBlockStore.release(vol.path)
        }
        pendingOtgScsi?.close()
        pendingOtgScsi = null
        pendingOtgFile = null
        mountedVolumesState.value = emptyList()
        activeMountIndexState.intValue = 0
        handleState.value = 0L
        entriesState.value = emptyList()
        dirPathState.value = ""
        listTruncatedState.value = false
        previewFileState.value = null
        previewNameState.value = ""
        InAppPreview.wipe(this)
        refreshDocumentRoots()
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
     * After a successful CreateDocument save: forget create/open secrets, drop
     * the selected container, and wipe the app-cache copy. The user picks the
     * saved file with Choose container. Cancelling the save picker must not
     * call this.
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
        val cachePath = pathState.value
        if (cachePath.isNotEmpty() && nativePathIsInternal(cachePath)) {
            Hardening.wipeFile(File(cachePath))
        }
        pathState.value = ""
        containerUriState.value = null
        containerLabelState.value = ""
        incomingState.value = null
        resetCreateWizard()
        tabState.intValue = 0
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

    /**
     * Full closer: idle, screen-lock, Tools wipe-cache, Dismount.
     * Home / Recents uses [dismountOnLeave] so Create can continue. Panic adds
     * [Hardening.panic] after lock. Do not grow a fifth path.
     */
    internal fun closeOpenVolumes(reason: String) {
        if (NativeBridge.isOpen(handleState.value) || mountedVolumesState.value.isNotEmpty()) {
            beginWork(reason)
            NativeBridge.setProgress(100, reason)
        }
        lockSession()
        if (!statusState.value.startsWith("Panic")) {
            statusState.value = reason
        }
    }

    private fun lockSession() {
        closeMountedVolume()
        releasePendingPfd()
        wipeRamSecrets()
        hashResultState.value = ""
        endWork()
        Hardening.wipeSessionFiles(this)
        if (!statusState.value.startsWith("Panic")) {
            statusState.value =
                "Dismounted. Passwords, keyfiles in memory, and decrypted copies wiped. Ciphertext stays."
        }
    }

    private fun panicWipe() {
        closeMountedVolume()
        OtgBlockStore.releaseAll()
        releasePendingPfd()
        wipeRamSecrets()
        Hardening.panic(this)
        basketUrisState.value = emptyList()
    }

    internal fun beginWork(title: String = "", updateStatus: Boolean = true) {
        NativeBridge.resetProgress()
        if (updateStatus && title.isNotEmpty()) statusState.value = title
        busyState.value = true
    }

    internal fun endWork() {
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

    internal fun volumeBytesForBasket(askedBytes: Long, uris: List<Uri>, hiddenBytes: Long): Long {
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

    internal fun basketSummary(uris: List<Uri>, hiddenBytes: Long = 0L): String {
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

    internal fun createContainer(
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
        SizeUnits.shortageInAppStorage(bytes, cacheDir.usableSpace)?.let {
            onStatus(it)
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

    private fun sessionHasKeyfile(uris: List<Uri>, file: File): Boolean {
        val path = file.absolutePath
        return uris.any { it.scheme == "file" && it.path == path }
    }

    private fun keyfileAddStatus(failed: String?, skipped: List<String>, nested: Boolean): String {
        if (failed != null) {
            return if (nested) {
                "Could not read $failed as a nested keyfile."
            } else {
                "Could not read $failed. Pick it again, or open it from the Files app with VC Port. Any file can be a keyfile; VeraCrypt uses the first 1 MiB."
            }
        }
        if (skipped.isNotEmpty()) {
            return "Already in this session: ${skipped.joinToString()}. Remove it first, or change the name to generate another. VeraCrypt mixes every listed keyfile — the same file twice is a different mix."
        }
        return if (nested) {
            "Nested keyfile(s) added. Same rule as outer: first 1 MiB."
        } else {
            "Keyfile(s) added. Any file works; only the first 1 MiB is mixed, same as VeraCrypt on a computer."
        }
    }

    internal fun generateSessionKeyfiles(countText: String, pattern: String, nested: Boolean): List<File> {
        val n = (countText.toIntOrNull() ?: 1).coerceIn(1, 8)
        val name = ShareHelper.sanitizeKeyfileName(pattern)
        val dir = KeyfileIo.keyfileDir(this)
        val current = if (nested) hiddenKeyfileUrisState.value else keyfileUrisState.value
        val files = mutableListOf<File>()
        val skipped = mutableListOf<String>()
        for (i in 1..n) {
            val dest = File(dir, KeyfileIo.numberedName(name, i, n))
            if (sessionHasKeyfile(current, dest) || sessionHasKeyfile(files.map { Uri.fromFile(it) }, dest)) {
                skipped += dest.name
                continue
            }
            if (NativeBridge.generateKeyfile(dest.absolutePath, 128) != 0) {
                files.forEach { KeyfileIo.wipe(it) }
                return emptyList()
            }
            files += dest
        }
        if (skipped.isNotEmpty() && files.isEmpty()) {
            statusState.value =
                "Already in this session: ${skipped.joinToString()}. Remove it first, or change the name to generate another. VeraCrypt mixes every listed keyfile — the same file twice is a different mix."
            return emptyList()
        }
        val uris = files.map { Uri.fromFile(it) }
        if (nested) {
            hiddenKeyfileUrisState.value = hiddenKeyfileUrisState.value + uris
        } else {
            keyfileUrisState.value = keyfileUrisState.value + uris
        }
        return files
    }

    internal fun offerGeneratedKeyfileCopies(
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

    internal fun runChangeHeader(
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

    internal fun backupVolumeHeader(
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
            OtgBlockStore.isPath(nativePath) ||
            nativePath.startsWith(cacheDir.absolutePath) ||
            nativePath.startsWith(filesDir.absolutePath)
    }

    private fun containerPathUsable(path: String): Boolean {
        if (path.isEmpty() || path.startsWith("/proc/self/fd/")) return false
        if (OtgBlockStore.isPath(path)) return OtgBlockStore.isReady(path)
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

    private fun openUsbMassStorage(device: UsbDevice) {
        beginWork("Reading USB disk…")
        Thread {
            try {
                pendingOtgScsi?.close()
                pendingOtgScsi = null
                val scsi = OtgScsiDevice.open(OtgUsb.manager(this), device)
                val cands = OtgPartitions.probe(scsi)
                runOnUiThread {
                    pendingOtgScsi = scsi
                    otgCandidatesState.value = cands
                    endWork()
                    statusState.value =
                        "Pick a partition, then type the password and Open volume. Nothing auto-mounted."
                }
            } catch (_: Exception) {
                runOnUiThread {
                    endWork()
                    statusState.value = "Could not read that USB disk. It is not auto-mounted."
                }
            }
        }.start()
    }

    private fun refreshDocumentRoots() {
        val roots = if (OtgMountShare.shareWithFiles) {
            mountedVolumesState.value.map { vol ->
                OtgMountShare.Root(vol.path.ifEmpty { vol.label }, vol.handle, vol.label)
            }
        } else {
            emptyList()
        }
        OtgMountShare.publish(roots)
        OtgMountShare.notify(this)
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
                            truncated = truncated,
                            readOnly = readOnly
                        )
                        mountedVolumesState.value = next
                        refreshDocumentRoots()
                        activeMountIndexState.intValue = next.lastIndex
                        headerKeyfileUrisState.value = keyfileUris
                        rememberUnlock(text, pimText)
                        wipeUnlockForm()
                        onHandle(result)
                        onEntries(files)
                        dirPathState.value = ""
                        listTruncatedState.value = truncated
                        tabState.intValue = 2
                        armIdleTimer()
                        var msg = "Mounted in this app. Size $volumeBytes bytes. Slots are on the Mounted tab. Tap a folder to open it, or a file to select it. Copy to volume moves selected files into another mounted container."
                        if (readOnly) msg = "Read-only. Writes are refused. $msg"
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
        if (intent.action == PanicIntents.ACTION || intent.getBooleanExtra(PanicIntents.EXTRA, false)) {
            panicWipe()
            statusState.value = "Panic wipe complete. Cache, clipboard, and leftovers are gone."
            return
        }
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
        statusState.value = if (pathState.value.isEmpty())
            lastContainerCopyError.ifEmpty { SizeUnits.APP_STORAGE_UNREADABLE }
        else
            "Received ${first.name}. Any extension can be a volume. Open with the correct password, PIM, and keyfiles, or share as-is."
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

    private fun startInAppPreview(
        handle: Long,
        dirPath: String,
        entry: VaultEntry,
        onStatus: (String) -> Unit
    ) {
        if (!NativeBridge.isOpen(handle) || entry.isDir) {
            onStatus("Tap one file, then View in app.")
            return
        }
        beginWork("Opening ${entry.name} in this app…")
        Thread {
            val dest = InAppPreview.materialize(this, handle, joinDir(dirPath, entry.name), entry.name)
            runOnUiThread {
                endWork()
                if (dest == null) {
                    onStatus("Could not preview ${entry.name} in-app. File may be over 64 MiB, or export failed.")
                    return@runOnUiThread
                }
                previewNameState.value = entry.name
                previewFileState.value = dest
                onStatus("Viewing ${entry.name} in this app. Not VLC or Files.")
            }
        }.start()
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

    private fun hashVaultFiles(
        handle: Long,
        dirPath: String,
        files: List<VaultEntry>,
        onStatus: (String) -> Unit
    ) {
        if (!NativeBridge.isOpen(handle) || files.isEmpty()) {
            onStatus("Tap a file, then SHA-256 in volume.")
            return
        }
        beginWork("Hashing ${files.size} file(s) inside the volume…")
        Thread {
            val lines = mutableListOf<String>()
            try {
                files.forEachIndexed { index, entry ->
                    val pct = (index * 100) / files.size
                    NativeBridge.setProgress(pct, "Hashing ${index + 1} of ${files.size}: ${entry.name}")
                    val dest = File(cacheDir, "hash-${entry.name}")
                    val rc = NativeBridge.exportFile(handle, joinDir(dirPath, entry.name), dest.absolutePath)
                    if (rc != 0 || !dest.isFile) {
                        lines.add("${entry.name}: hash failed")
                        Hardening.wipeFile(dest)
                        NativeBridge.setProgress(
                            ((index + 1) * 100) / files.size,
                            "Hash failed: ${entry.name}"
                        )
                        return@forEachIndexed
                    }
                    val hex = dest.inputStream().use { stream ->
                        val md = java.security.MessageDigest.getInstance("SHA-256")
                        val buf = ByteArray(64 * 1024)
                        var hashed = 0L
                        val total = dest.length().coerceAtLeast(1L)
                        while (true) {
                            val n = stream.read(buf)
                            if (n <= 0) break
                            md.update(buf, 0, n)
                            hashed += n
                            val filePct = ((index * 100) + ((hashed * 100) / total).toInt()) / files.size
                            NativeBridge.setProgress(
                                filePct.coerceIn(0, 99),
                                "Hashing ${index + 1} of ${files.size}: ${entry.name}"
                            )
                        }
                        md.digest().joinToString("") { b -> "%02x".format(b) }
                    }
                    Hardening.wipeFile(dest)
                    lines.add("${entry.name}: $hex")
                    NativeBridge.setProgress(
                        ((index + 1) * 100) / files.size,
                        "Hashed ${entry.name}"
                    )
                }
                runOnUiThread {
                    val summary = "SHA-256 in volume (temp wiped): " + lines.joinToString(" · ")
                    hashResultState.value = summary
                    endWork()
                    onStatus(summary)
                }
            } catch (_: Exception) {
                runOnUiThread {
                    endWork()
                    onStatus("SHA-256 in volume failed.")
                }
            }
        }.start()
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

    internal fun restoreEmbeddedHeader(
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
        lastContainerCopyError = ""
        val name = ShareHelper.sanitizeDisguiseName(file.name)
        if (file.absolutePath.startsWith(cacheDir.absolutePath) ||
            file.absolutePath.startsWith(filesDir.absolutePath)
        ) {
            return file.absolutePath
        }
        val outFile = KeyfileIo.uniqueNamed(File(cacheDir, "containers").apply { mkdirs() }, name)
        SizeUnits.shortageInAppStorage(file.length(), cacheDir.usableSpace)?.let {
            lastContainerCopyError = it
            return ""
        }
        file.copyTo(outFile, overwrite = false)
        if (!outFile.exists() || outFile.length() == 0L) {
            outFile.delete()
            lastContainerCopyError = SizeUnits.APP_STORAGE_UNREADABLE
            return ""
        }
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
        lastContainerCopyError = ""
        val display = ShareHelper.displayName(this, uri) ?: "volume.hc"
        val name = ShareHelper.sanitizeDisguiseName(display)
        val len = uriLength(uri)
        SizeUnits.shortageInAppStorage(len, cacheDir.usableSpace)?.let {
            lastContainerCopyError = it
            return ""
        }
        val input = KeyfileIo.openReadable(this, uri)
        if (input == null) {
            lastContainerCopyError = SizeUnits.APP_STORAGE_UNREADABLE
            return ""
        }
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
            lastContainerCopyError = SizeUnits.APP_STORAGE_UNREADABLE
            return ""
        }
        if (len > 0 && outFile.length() < len) {
            outFile.delete()
            lastContainerCopyError = SizeUnits.APP_STORAGE_UNREADABLE
            return ""
        }
        return outFile.absolutePath
    }
}
