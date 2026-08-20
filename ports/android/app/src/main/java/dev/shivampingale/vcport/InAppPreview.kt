package dev.shivampingale.vcport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.os.ParcelFileDescriptor
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import java.io.File

/**
 * In-app preview of a decrypted volume file. Stays inside this process:
 * platform codecs (Bitmap / PdfRenderer / MediaPlayer), not VLC, Files,
 * or Gallery. Word/Excel are not decoded here.
 */
enum class InAppPreviewKind {
    IMAGE, TEXT, PDF, AUDIO, VIDEO, UNSUPPORTED
}

object InAppPreview {
    const val DIR = "preview"
    const val MAX_BYTES = 64L * 1024L * 1024L
    const val TEXT_CHARS = 256 * 1024

    fun kindOf(name: String): InAppPreviewKind {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif" -> InAppPreviewKind.IMAGE
            "txt", "md", "json", "xml", "csv", "log", "html", "htm",
            "c", "h", "cc", "cpp", "py", "kt", "swift", "sh", "ini", "cfg" -> InAppPreviewKind.TEXT
            "pdf" -> InAppPreviewKind.PDF
            "mp3", "m4a", "aac", "wav", "ogg", "flac", "oga" -> InAppPreviewKind.AUDIO
            "mp4", "mkv", "webm", "3gp", "mov", "m4v" -> InAppPreviewKind.VIDEO
            else -> InAppPreviewKind.UNSUPPORTED
        }
    }

    fun dir(context: Context): File = File(context.cacheDir, DIR).apply { mkdirs() }

    fun wipe(context: Context) {
        Hardening.wipeDir(File(context.cacheDir, DIR))
    }

    fun materialize(context: Context, handle: Long, volumePath: String, name: String): File? {
        if (!NativeBridge.isOpen(handle)) return null
        wipe(context)
        val dest = File(dir(context), ShareHelper.safeName(name).ifEmpty { "file" })
        val rc = NativeBridge.exportFile(handle, volumePath, dest.absolutePath)
        if (rc != 0 || !dest.isFile || dest.length() <= 0L) {
            dest.delete()
            return null
        }
        if (dest.length() > MAX_BYTES) {
            dest.delete()
            return null
        }
        return dest
    }

    fun readText(file: File): String {
        val raw = file.readText()
        return if (raw.length > TEXT_CHARS) raw.take(TEXT_CHARS) + "\n… truncated …" else raw
    }

    fun hexHead(file: File, n: Int = 256): String {
        val buf = ByteArray(n)
        val got = file.inputStream().use { it.read(buf) }
        if (got <= 0) return ""
        return buf.copyOf(got).joinToString(" ") { b -> "%02x".format(b.toInt() and 0xFF) }
    }
}

@Composable
fun InAppPreviewDialog(file: File, name: String, onClose: () -> Unit) {
    val kind = InAppPreview.kindOf(name)
    Dialog(onDismissRequest = onClose) {
        androidx.compose.material3.Surface(shape = MaterialTheme.shapes.medium) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("in_app_preview")
            ) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Inside VC Port. Not VLC, Files, or another app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                when (kind) {
                    InAppPreviewKind.IMAGE -> ImagePages(file)
                    InAppPreviewKind.TEXT -> Text(
                        InAppPreview.readText(file),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("in_app_preview_text")
                    )
                    InAppPreviewKind.PDF -> PdfPages(file)
                    InAppPreviewKind.AUDIO -> AudioPreview(file)
                    InAppPreviewKind.VIDEO -> VideoPreview(file)
                    InAppPreviewKind.UNSUPPORTED -> {
                        Text(
                            "This type is not decoded in-app (Office and unknown binaries stay in the volume). Copy to device if you need another tool — that leaves plaintext on the phone.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            InAppPreview.hexHead(file),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                Button(
                    onClick = onClose,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .testTag("in_app_preview_close")
                ) { Text("Close") }
            }
        }
    }
}

@Composable
private fun ImagePages(file: File) {
    val bmp = remember(file) {
        BitmapFactory.decodeFile(file.absolutePath)
    }
    if (bmp == null) {
        Text("Could not decode this image in-app.")
        return
    }
    Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = file.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PdfPages(file: File) {
    val pages = remember(file) { renderPdf(file) }
    if (pages.isEmpty()) {
        Text("Could not decode this PDF in-app.")
        return
    }
    pages.forEach { bmp ->
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "PDF page",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
    }
}

private fun renderPdf(file: File): List<Bitmap> {
    val out = mutableListOf<Bitmap>()
    try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val n = minOf(renderer.pageCount, 8)
                for (i in 0 until n) {
                    renderer.openPage(i).use { page ->
                        val bmp = Bitmap.createBitmap(
                            page.width.coerceAtLeast(1),
                            page.height.coerceAtLeast(1),
                            Bitmap.Config.ARGB_8888
                        )
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        out.add(bmp)
                    }
                }
            }
        }
    } catch (_: Exception) {
    }
    return out
}

@Composable
private fun AudioPreview(file: File) {
    val player = remember { MediaPlayer() }
    DisposableEffect(file) {
        try {
            player.setDataSource(file.absolutePath)
            player.prepare()
        } catch (_: Exception) {
        }
        onDispose {
            try {
                player.release()
            } catch (_: Exception) {
            }
        }
    }
    Button(onClick = {
        try {
            if (player.isPlaying) player.pause() else player.start()
        } catch (_: Exception) {
        }
    }) { Text("Play / Pause") }
}

@Composable
private fun VideoPreview(file: File) {
    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoPath(file.absolutePath)
                setOnPreparedListener { it.isLooping = true; start() }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp)
    )
}
