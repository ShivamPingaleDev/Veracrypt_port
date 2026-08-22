package dev.shivampingale.vcport

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

/**
 * In-app preview on a file container and a simulated whole-disk USB.
 * Does not send ACTION_VIEW / VLC.
 */
@RunWith(AndroidJUnit4::class)
class InAppPreviewTest {
    @Test
    fun kindsStayInsideApp() {
        assertEquals(InAppPreviewKind.TEXT, InAppPreview.kindOf("NOTE.TXT"))
        assertEquals(InAppPreviewKind.IMAGE, InAppPreview.kindOf("photo.PNG"))
        assertEquals(InAppPreviewKind.PDF, InAppPreview.kindOf("doc.pdf"))
        assertEquals(InAppPreviewKind.AUDIO, InAppPreview.kindOf("clip.mp3"))
        assertEquals(InAppPreviewKind.VIDEO, InAppPreview.kindOf("clip.mp4"))
        assertEquals(InAppPreviewKind.UNSUPPORTED, InAppPreview.kindOf("report.docx"))
    }

    @Test
    fun previewTextFromVolumeAndUsbSlot() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        NativeBridge.resetEntropy()
        NativeBridge.addEntropy(ByteArray(256) { it.toByte() })
        val dir = File(ctx.cacheDir, "preview-sim").apply {
            deleteRecursively()
            mkdirs()
        }
        val pw = "vcport-preview-ok"
        val part = File(dir, "part.bin")
        assertEquals(
            0,
            NativeBridge.createVolume(
                part.absolutePath, pw, 1, 2L shl 20, "AES", "HMAC-SHA-512",
                emptyArray(), "", 0, 0L, emptyArray(), "FAT"
            )
        )
        val handle = NativeBridge.openVolume(part.absolutePath, pw, 1, false, emptyArray(), false)
        assertTrue(NativeBridge.isOpen(handle))
        val note = File(dir, "NOTE.TXT").apply { writeText("preview-in-app-ok\n") }
        val png = File(dir, "dot.png").apply { writeBytes(TINY_PNG) }
        assertEquals(0, NativeBridge.importFile(handle, "/", note.absolutePath, "NOTE.TXT"))
        assertEquals(0, NativeBridge.importFile(handle, "/", png.absolutePath, "DOT.PNG"))

        val textFile = InAppPreview.materialize(ctx, handle, "NOTE.TXT", "NOTE.TXT")
        assertTrue(textFile != null && textFile.isFile)
        assertEquals("preview-in-app-ok\n", InAppPreview.readText(textFile!!))
        assertEquals(InAppPreviewKind.TEXT, InAppPreview.kindOf("NOTE.TXT"))

        val img = InAppPreview.materialize(ctx, handle, "DOT.PNG", "DOT.PNG")
        assertTrue(img != null && img.length() == TINY_PNG.size.toLong())
        assertEquals(InAppPreviewKind.IMAGE, InAppPreview.kindOf("DOT.PNG"))
        NativeBridge.closeVolume(handle)

        val start = 2048L * 512L
        val disk = File(dir, "usb.bin")
        RandomAccessFile(disk, "rw").use { raf ->
            raf.setLength(start + part.length() + 512)
            val mbr = ByteArray(512)
            mbr[510] = 0x55.toByte()
            mbr[511] = 0xAA.toByte()
            raf.write(mbr)
            raf.seek(start)
            raf.write(part.readBytes())
        }
        val path = OtgBlockStore.bindFile(disk, start, part.length(), "preview USB")
        val usb = NativeBridge.openVolume(path, pw, 1, false, emptyArray(), false)
        assertTrue(NativeBridge.isOpen(usb))
        val fromUsb = InAppPreview.materialize(ctx, usb, "NOTE.TXT", "NOTE.TXT")
        assertEquals("preview-in-app-ok\n", InAppPreview.readText(fromUsb!!))
        NativeBridge.closeVolume(usb)
        OtgBlockStore.release(path)
        InAppPreview.wipe(ctx)
        assertFalse(File(ctx.cacheDir, InAppPreview.DIR).exists())
    }

    companion object {
        private val TINY_PNG = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D,
            0x49, 0x48, 0x44, 0x52, 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x02,
            0x00, 0x00, 0x00, 0x90.toByte(), 0x77, 0x53, 0xDE.toByte(), 0x00, 0x00, 0x00, 0x0C,
            0x49, 0x44, 0x41, 0x54, 0x08, 0xD7.toByte(), 0x63, 0xF8.toByte(), 0xCF.toByte(),
            0xC0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x00, 0x01, 0x18, 0xDD.toByte(), 0x8D.toByte(),
            0xB0.toByte(), 0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, 0xAE.toByte(), 0x42, 0x60, 0x82.toByte()
        )
    }
}
