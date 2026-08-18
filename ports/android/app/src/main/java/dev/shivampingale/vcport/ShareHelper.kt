package dev.shivampingale.vcport

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import java.io.File

object ShareHelper {
    fun authority(context: Context): String = "${context.packageName}.share"

    fun shareDir(context: Context): File {
        return File(context.cacheDir, "share").apply { mkdirs() }
    }

    fun inboxDir(context: Context): File {
        return File(context.cacheDir, "inbox").apply { mkdirs() }
    }

    fun safeName(name: String): String {
        return name.replace(Regex("[\\\\/\\x00]+"), "_").ifEmpty { "file" }
    }

    fun mimeFor(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (ext) {
                "hc", "tc", "vera" -> "application/octet-stream"
                else -> "application/octet-stream"
            }
    }

    fun uriFor(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    fun shareFiles(context: Context, files: List<File>, title: String = "Share") {
        if (files.isEmpty()) return
        val staged = files.mapNotNull { stageInShareDir(context, it) }
        if (staged.isEmpty()) return
        shareUris(context, staged.map { uriFor(context, it) }, title, staged.firstOrNull()?.let { mimeFor(it.name) })
    }

    /** FileProvider only exposes cache/share/. Copy here before any share Intent. */
    fun stageInShareDir(context: Context, file: File): File? {
        if (!file.isFile) return null
        val dir = shareDir(context)
        var dest = File(dir, safeName(file.name).ifEmpty { "file.bin" })
        if (dest.exists() && dest.canonicalPath != file.canonicalPath) {
            dest = File(dir, "${System.nanoTime()}-${safeName(file.name)}")
        }
        if (dest.canonicalPath == file.canonicalPath) return file
        return try {
            file.inputStream().use { input -> dest.outputStream().use { input.copyTo(it) } }
            dest
        } catch (_: Exception) {
            dest.delete()
            null
        }
    }

    fun shareUris(
        context: Context,
        uris: List<Uri>,
        title: String = "Share encrypted file",
        mime: String? = null
    ) {
        if (uris.isEmpty()) return
        val type = mime ?: "application/octet-stream"
        val builder = ShareCompat.IntentBuilder(context)
            .setType(type)
            .setChooserTitle(title)
        uris.forEach { builder.addStream(it) }
        val intent = builder.createChooserIntent()
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun sanitizeKeyfileName(raw: String): String {
        var name = safeName(raw.trim()).substringAfterLast('/').substringAfterLast('\\')
        if (name.isEmpty() || name == "." || name == "..") return "keyfile.bin"
        return name.take(120)
    }

    fun displayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        } ?: uri.lastPathSegment
    }

    fun persistRead(context: Context, uri: Uri) {
        // High-threat: do not persist URI grants across reboots (SAF leftover on seizure).
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
    }

    fun copyIncoming(context: Context, uri: Uri, nameHint: String?): File? {
        val resolver = context.contentResolver
        val queried = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) cursor.getString(idx) else null
            } else null
        }
        val name = safeName(nameHint ?: queried ?: uri.lastPathSegment ?: "incoming.bin")
        val dest = File(inboxDir(context), name)
        resolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        return dest
    }

    fun sanitizeDisguiseName(raw: String): String {
        var name = safeName(raw.trim()).substringAfterLast('/').substringAfterLast('\\')
        if (name.isEmpty() || name == "." || name == "..") return "volume.hc"
        if (name.lowercase().endsWith(".vcpw")) name = name.dropLast(5)
        if (name.isEmpty()) return "volume.hc"
        return name.take(120)
    }

    val DISGUISE_NAMES = listOf(
        "volume.hc",
        "photo.jpg",
        "image.png",
        "clip.mp4",
        "notes.pdf",
        "model.safetensors",
        "adapter.lora",
        "weights.bin"
    )

    fun looksLikeContainer(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".hc") || lower.endsWith(".tc") || lower.endsWith(".vera")
    }
}
