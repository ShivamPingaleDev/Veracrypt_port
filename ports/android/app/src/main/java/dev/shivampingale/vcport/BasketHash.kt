package dev.shivampingale.vcport

import android.content.Context
import android.net.Uri
import java.security.MessageDigest

/** Session-only SHA-256 of basket / import files. Never written to app cache. */
object BasketHash {
    fun sha256(context: Context, uri: Uri): String? {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val input = KeyfileIo.openReadable(context, uri) ?: return null
            input.use { stream ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = stream.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            md.digest().joinToString("") { b -> "%02x".format(b) }
        } catch (_: Exception) {
            null
        }
    }

    fun shortHex(hex: String): String {
        if (hex.length < 12) return hex
        return hex.take(8) + "…" + hex.takeLast(4)
    }
}
