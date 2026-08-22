package dev.shivampingale.vcport

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.SecureRandom

data class FactorBundle(
    val pim: Int = 0,
    val password: String = "",
    val biometricKey: ByteArray? = null,
    val keyfileUris: List<String> = emptyList()
) {
    fun hasBiometric(): Boolean = biometricKey != null && biometricKey.isNotEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FactorBundle) return false
        return pim == other.pim &&
            password == other.password &&
            keyfileUris == other.keyfileUris &&
            biometricKey.contentEquals(other.biometricKey)
    }

    override fun hashCode(): Int {
        var result = pim
        result = 31 * result + password.hashCode()
        result = 31 * result + (biometricKey?.contentHashCode() ?: 0)
        result = 31 * result + keyfileUris.hashCode()
        return result
    }
}

object FactorCodec {
    fun encode(bundle: FactorBundle): ByteArray {
        val pw = Base64.encodeToString(bundle.password.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val bio = bundle.biometricKey?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: ""
        return buildString {
            append("VCF2\n")
            append(bundle.pim).append('\n')
            append(pw).append('\n')
            append(bio).append('\n')
            bundle.keyfileUris.forEach { append(it).append('\n') }
        }.toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): FactorBundle {
        return try {
            val text = String(bytes, Charsets.UTF_8)
            if (!text.startsWith("VCF2\n")) {
                val parts = text.split("\n", limit = 2)
                val pim = parts.getOrNull(0)?.toIntOrNull() ?: 0
                return FactorBundle(pim = pim, password = parts.getOrNull(1) ?: "")
            }
            val lines = text.split('\n')
            val pim = lines.getOrNull(1)?.toIntOrNull() ?: 0
            val password = lines.getOrNull(2)?.let { encoded ->
                if (encoded.isEmpty()) "" else String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8)
            } ?: ""
            val bio = lines.getOrNull(3)?.let { encoded ->
                if (encoded.isEmpty()) null else Base64.decode(encoded, Base64.NO_WRAP)
            }
            val uris = if (lines.size > 4) lines.drop(4).filter { it.isNotEmpty() } else emptyList()
            FactorBundle(pim = pim, password = password, biometricKey = bio, keyfileUris = uris)
        } catch (_: Exception) {
            FactorBundle()
        }
    }

    fun randomBiometricKey(): ByteArray {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return bytes
    }
}

object KeyfileIo {
    private const val MAX_KEYFILE = 1024 * 1024

    fun keyfileDir(context: Context): File = File(context.cacheDir, "keyfiles").apply { mkdirs() }

    /** Any file the user picks. VeraCrypt only mixes the first 1 MiB. */
    fun openReadable(context: Context, uri: Uri): InputStream? {
        try {
            context.contentResolver.openInputStream(uri)?.let { return it }
        } catch (_: Exception) {
        }
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            return ParcelFileDescriptor.AutoCloseInputStream(pfd)
        } catch (_: Exception) {
        }
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            val file = File(path)
            if (file.canRead()) return FileInputStream(file)
        }
        return null
    }

    fun uniqueNamed(dir: File, name: String): File {
        val safe = ShareHelper.sanitizeKeyfileName(name)
        val first = File(dir, safe)
        if (!first.exists()) return first
        val dot = safe.lastIndexOf('.')
        val stem = if (dot > 0) safe.substring(0, dot) else safe
        val ext = if (dot > 0) safe.substring(dot) else ""
        var n = 2
        while (true) {
            val cand = File(dir, "$stem-$n$ext")
            if (!cand.exists()) return cand
            n++
        }
    }

    fun numberedName(base: String, index: Int, total: Int): String {
        val safe = ShareHelper.sanitizeKeyfileName(base)
        if (total <= 1) return safe
        val dot = safe.lastIndexOf('.')
        val stem = if (dot > 0) safe.substring(0, dot) else safe
        val ext = if (dot > 0) safe.substring(dot) else ""
        return "$stem-$index$ext"
    }

    fun copyOwned(context: Context, uri: Uri): File? {
        val raw = ShareHelper.displayName(context, uri) ?: "keyfile.bin"
        val dest = uniqueNamed(keyfileDir(context), raw)
        return copyInto(context, uri, dest)
    }

    fun copyUri(context: Context, uri: Uri): File? {
        val dest = File.createTempFile("vckf", ".bin", keyfileDir(context))
        return copyInto(context, uri, dest) ?: run {
            dest.delete()
            null
        }
    }

    private fun copyInto(context: Context, uri: Uri, dest: File): File? {
        val input = openReadable(context, uri) ?: run {
            dest.delete()
            return null
        }
        try {
            dest.outputStream().use { output ->
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    val room = MAX_KEYFILE - total
                    if (n > room) {
                        if (room > 0) output.write(buf, 0, room)
                        break
                    }
                    output.write(buf, 0, n)
                    total += n
                }
            }
        } catch (_: Exception) {
            dest.delete()
            return null
        } finally {
            try {
                input.close()
            } catch (_: Exception) {
            }
        }
        return dest
    }

    fun wipe(file: File) {
        if (!file.exists()) return
        val len = file.length().toInt().coerceAtLeast(0)
        if (len > 0) file.writeBytes(ByteArray(len))
        file.delete()
    }

    fun readLimited(context: Context, uri: Uri): ByteArray? {
        val input = openReadable(context, uri) ?: return null
        input.use { stream ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = stream.read(buf)
                if (n <= 0) break
                val room = MAX_KEYFILE - total
                if (n > room) {
                    if (room > 0) out.write(buf, 0, room)
                    break
                }
                out.write(buf, 0, n)
                total += n
            }
            return out.toByteArray()
        }
    }
}
