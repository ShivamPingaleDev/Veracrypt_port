package dev.shivampingale.vcport

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.File

/**
 * Experimental DocumentsProvider so Files / Gallery can browse an unlocked
 * volume. This is a seizure leak compared to master. Opt-in per session.
 *
 * Feature idea from OTG Master by moylali
 * (https://github.com/moylali/OTGMaster, GPL-2.0-or-later). This file is
 * new Apache-2.0 code in VC Port, not a copy of that tree.
 */
class VolumeDocumentsProvider : DocumentsProvider() {
    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): android.database.Cursor {
        val cols = projection ?: arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_MIME_TYPES
        )
        val cursor = android.database.MatrixCursor(cols)
        if (!OtgMountShare.shareWithFiles) return cursor
        for (root in OtgMountShare.roots()) {
            val row = cursor.newRow()
            for (col in cols) {
                when (col) {
                    DocumentsContract.Root.COLUMN_ROOT_ID -> row.add(root.id)
                    DocumentsContract.Root.COLUMN_DOCUMENT_ID -> row.add(docId(root.handle, ""))
                    DocumentsContract.Root.COLUMN_TITLE -> row.add("VC Port · ${root.label}")
                    DocumentsContract.Root.COLUMN_FLAGS -> row.add(
                        DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                            DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
                    )
                    DocumentsContract.Root.COLUMN_MIME_TYPES -> row.add("*/*")
                    else -> row.add(null)
                }
            }
        }
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): android.database.Cursor {
        val cols = docCols(projection)
        val cursor = android.database.MatrixCursor(cols)
        val (handle, path) = parse(documentId)
        if (path.isEmpty()) {
            addDoc(cursor, cols, documentId, "/", true, 0L)
            return cursor
        }
        val parent = parentOf(path)
        val name = path.substringAfterLast('/')
        val entries = NativeBridge.listDir(handle, parent).mapNotNull { parseLine(it) }
        val hit = entries.firstOrNull { it.first == name }
        if (hit != null) addDoc(cursor, cols, documentId, hit.first, hit.second, hit.third)
        return cursor
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): android.database.Cursor {
        val cols = docCols(projection)
        val cursor = android.database.MatrixCursor(cols)
        val (handle, path) = parse(parentDocumentId)
        val listed = NativeBridge.listDir(handle, path.ifEmpty { "/" })
        for (line in listed) {
            val e = parseLine(line) ?: continue
            addDoc(cursor, cols, docId(handle, join(path, e.first)), e.first, e.second, e.third)
        }
        return cursor
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val (handle, path) = parse(documentId)
        if (path.isEmpty()) throw IllegalArgumentException("folder")
        val ctx = context ?: throw IllegalStateException("provider")
        val dest = File(ctx.cacheDir, "share").apply { mkdirs() }
        val out = File(dest, ShareHelper.sanitizeDisguiseName(path.substringAfterLast('/')))
        val rc = NativeBridge.exportFile(handle, path, out.absolutePath)
        if (rc != 0 || !out.isFile) throw IllegalStateException("export $rc")
        return ParcelFileDescriptor.open(out, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        return documentId.startsWith(parentDocumentId)
    }

    private fun docCols(projection: Array<out String>?) = projection ?: arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_FLAGS,
        DocumentsContract.Document.COLUMN_SIZE
    )

    private fun addDoc(
        cursor: android.database.MatrixCursor,
        cols: Array<out String>,
        id: String,
        name: String,
        dir: Boolean,
        size: Long
    ) {
        val row = cursor.newRow()
        for (col in cols) {
            when (col) {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID -> row.add(id)
                DocumentsContract.Document.COLUMN_DISPLAY_NAME -> row.add(name)
                DocumentsContract.Document.COLUMN_MIME_TYPE -> row.add(
                    if (dir) DocumentsContract.Document.MIME_TYPE_DIR else "application/octet-stream"
                )
                DocumentsContract.Document.COLUMN_FLAGS -> row.add(
                    if (dir) DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE else 0
                )
                DocumentsContract.Document.COLUMN_SIZE -> row.add(if (dir) null else size)
                else -> row.add(null)
            }
        }
    }

    private fun parse(documentId: String): Pair<Long, String> {
        val parts = documentId.split(":", limit = 2)
        val handle = parts[0].toLongOrNull() ?: 0L
        val path = parts.getOrNull(1) ?: ""
        return handle to path
    }

    private fun docId(handle: Long, path: String) = if (path.isEmpty()) "$handle:" else "$handle:$path"

    private fun join(dir: String, name: String) = if (dir.isEmpty()) name else "$dir/$name"

    private fun parentOf(path: String): String {
        val slash = path.lastIndexOf('/')
        return if (slash <= 0) "/" else path.substring(0, slash)
    }

    private fun parseLine(line: String): Triple<String, Boolean, Long>? {
        val parts = line.split('\t')
        if (parts.isEmpty() || parts[0].isEmpty()) return null
        if (parts[0] == "!error!" || parts[0] == "!truncated!") return null
        return Triple(parts[0], parts.getOrNull(1) == "1", parts.getOrNull(2)?.toLongOrNull() ?: 0L)
    }
}

object OtgMountShare {
    data class Root(val id: String, val handle: Long, val label: String)

    @Volatile
    var shareWithFiles: Boolean = false

    private val live = mutableListOf<Root>()

    @Synchronized
    fun publish(roots: List<Root>) {
        live.clear()
        live.addAll(roots)
    }

    @Synchronized
    fun roots(): List<Root> = live.toList()

    fun notify(context: android.content.Context) {
        val uri = DocumentsContract.buildRootsUri("${context.packageName}.documents")
        context.contentResolver.notifyChange(uri, null)
    }
}
