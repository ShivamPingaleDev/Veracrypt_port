package dev.shivampingale.vcport

import android.app.Activity
import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowManager
import java.io.File
import java.security.KeyStore

/**
 * High-threat defaults (MASVS storage/privacy, journalist/whistleblower profile).
 * Nothing here makes the app "unbreakable": a rooted implant, a compelled
 * password, or a hidden-volume tell still wins. These controls raise the cost
 * of a casual seizure and of forensic leftovers.
 *
 * No backdoor: this object never opens a socket, never listens, and never
 * phones home. Network exists only in the GitHub flavor UpdateChecker, on a
 * user tap, for ≤20s, to hardcoded hosts.
 */
object Hardening {
    fun protectWindow(activity: Activity) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        activity.window.decorView.filterTouchesWhenObscured = true
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                activity.window.setHideOverlayWindows(true)
            } catch (_: SecurityException) {
                // API 31+ requires HIDE_OVERLAY_WINDOWS; never crash the vault UI.
            }
        }
        activity.window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
    }

    fun wipeFile(file: File) {
        if (!file.exists()) return
        if (file.isDirectory) {
            file.listFiles()?.forEach { wipeFile(it) }
            file.delete()
            return
        }
        val len = file.length().coerceAtLeast(0L)
        if (len > 0L && len <= 64L * 1024L * 1024L) {
            file.writeBytes(ByteArray(len.toInt()))
        }
        file.delete()
    }

    fun wipeDir(dir: File) {
        if (dir.exists()) wipeFile(dir)
    }

    /** Drop plaintext leftovers. Ciphertext copies in cache are wiped on panic. */
    fun wipeSessionFiles(context: Context) {
        wipeDir(File(context.cacheDir, "keyfiles"))
        wipeDir(File(context.cacheDir, "unwrapped"))
        wipeDir(File(context.cacheDir, "share"))
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && (file.name.startsWith("wrap-in-") || file.name.startsWith("vcbio"))) {
                wipeFile(file)
            }
        }
    }

    fun panic(context: Context) {
        wipeSessionFiles(context)
        wipeDir(File(context.cacheDir, "inbox"))
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.isFile) wipeFile(file)
        }
        context.getSharedPreferences("vc_port_bio", Context.MODE_PRIVATE).edit().clear().apply()
        try {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            for (alias in listOf(BiometricVault.KEY_ALIAS, BiometricVault.LEGACY_KEY_ALIAS)) {
                if (store.containsAlias(alias)) {
                    store.deleteEntry(alias)
                }
            }
        } catch (_: Exception) {
        }
        SensitiveClipboard.forget(context)
    }
}
