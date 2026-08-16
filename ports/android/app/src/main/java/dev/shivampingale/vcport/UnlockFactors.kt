package dev.shivampingale.vcport

import android.content.Context
import android.net.Uri
import android.util.Base64
import java.io.File
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

    fun copyUri(context: Context, uri: Uri): File? {
        val dest = File.createTempFile("vckf", ".bin", keyfileDir(context))
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output ->
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > MAX_KEYFILE) {
                        dest.delete()
                        return null
                    }
                    output.write(buf, 0, n)
                }
            }
        } ?: run {
            dest.delete()
            return null
        }
        return dest
    }

    fun writeSecret(context: Context, secret: ByteArray): File {
        val dest = File.createTempFile("vcbio", ".key", keyfileDir(context))
        dest.writeBytes(secret)
        return dest
    }

    fun wipe(file: File) {
        if (!file.exists()) return
        val len = file.length().toInt().coerceAtLeast(0)
        if (len > 0) file.writeBytes(ByteArray(len))
        file.delete()
    }

    fun readLimited(context: Context, uri: Uri): ByteArray? {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                total += n
                if (total > MAX_KEYFILE) return null
                out.write(buf, 0, n)
            }
            return out.toByteArray()
        }
        return null
    }
}
