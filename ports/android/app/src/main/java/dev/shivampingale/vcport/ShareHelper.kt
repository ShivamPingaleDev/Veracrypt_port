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
    const val AUTHORITY = "dev.shivampingale.vcport.share"

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
        return FileProvider.getUriForFile(context, AUTHORITY, file)
    }

    fun shareFiles(context: Context, files: List<File>, title: String = "Share") {
        if (files.isEmpty()) return
        shareUris(context, files.map { uriFor(context, it) }, title, files.firstOrNull()?.let { mimeFor(it.name) })
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
        context.startActivity(intent)
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
        try {
            context.contentResolver.takePersistableUriPermission(
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

    fun looksLikeContainer(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".hc") || lower.endsWith(".tc") || lower.endsWith(".vera")
    }
}
