package dev.shivampingale.vcport

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.view.Window
import android.view.WindowManager

object SensitiveClipboard {
    private const val CLEAR_MS = 30_000L
    private val handler = Handler(Looper.getMainLooper())
    private var clearToken: Any? = null

    fun copyOnce(context: Context, secret: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("", secret)
        val extras = PersistableBundle()
        extras.putBoolean("android.content.extra.IS_SENSITIVE", true)
        clip.description.extras = extras
        clipboard.setPrimaryClip(clip)
        scheduleClear(clipboard)
    }

    fun forget(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clearNow(clipboard)
    }

    fun setScreenshotBlocked(window: Window, blocked: Boolean) {
        if (blocked) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        // Never clear FLAG_SECURE. Recents / screenshots are a seizure risk.
    }

    private fun scheduleClear(clipboard: ClipboardManager) {
        clearToken?.let { handler.removeCallbacksAndMessages(it) }
        val token = Any()
        clearToken = token
        handler.postAtTime({
            if (clearToken === token) {
                clearNow(clipboard)
            }
        }, token, android.os.SystemClock.uptimeMillis() + CLEAR_MS)
    }

    private fun clearNow(clipboard: ClipboardManager) {
        clearToken = null
        if (Build.VERSION.SDK_INT >= 28) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
