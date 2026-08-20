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
 * 12-phase whole encrypted USB simulation on the emulator.
 * No real OTG stick: a disk image is bound as /vcport-otg-dev/N.
 */
@RunWith(AndroidJUnit4::class)
class WholeUsbSimTest {
    @Test
    fun twelvePhasesWholeEncryptedUsb() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.cacheDir, "usb-sim").apply {
            deleteRecursively()
            mkdirs()
        }
        fillEntropy()
        val pw = "vcport-otg-usb-sim-ok"
        val pim = 1
        val part = File(dir, "part-a.bin")
        assertEquals(
            0,
            NativeBridge.createVolume(
                part.absolutePath, pw, pim, 2L shl 20, "AES", "HMAC-SHA-512",
                emptyArray(), "", 0, 0L, emptyArray(), "FAT"
            )
        )

        val start = 2048L * 512L
        val disk = File(dir, "usb-a.bin")
        RandomAccessFile(disk, "rw").use { raf ->
            raf.setLength(start + part.length() + 512)
            val mbr = ByteArray(512)
            mbr[510] = 0x55.toByte()
            mbr[511] = 0xAA.toByte()
            val lba = 2048
            val secs = (part.length() / 512).toInt()
            mbr[446 + 4] = 0x83.toByte()
            mbr[446 + 8] = lba.toByte()
            mbr[446 + 9] = (lba shr 8).toByte()
            mbr[446 + 10] = (lba shr 16).toByte()
            mbr[446 + 11] = (lba shr 24).toByte()
            mbr[446 + 12] = secs.toByte()
            mbr[446 + 13] = (secs shr 8).toByte()
            mbr[446 + 14] = (secs shr 16).toByte()
            mbr[446 + 15] = (secs shr 24).toByte()
            raf.seek(0)
            raf.write(mbr)
            raf.seek(start)
            raf.write(part.readBytes())
        }
        assertFalse(OtgBlockStore.isPath("/proc/self/fd/3"))
        val path = OtgBlockStore.bindFile(disk, start, part.length(), "MBR partition 1")
        assertTrue(OtgBlockStore.isPath(path))
        assertTrue(OtgBlockStore.isReady(path))

        val handle = NativeBridge.openVolume(path, pw, pim, false, emptyArray(), false)
        assertTrue(NativeBridge.isOpen(handle))
        val bad = NativeBridge.openVolume(path, "wrong-usb-password", pim, false, emptyArray(), false)
        assertFalse(NativeBridge.isOpen(bad))

        assertEquals(0, NativeBridge.mkdir(handle, "/", "PHOTOS"))
        val note = File(dir, "note.txt").apply { writeText("usb-nested-ok\n") }
        assertEquals(0, NativeBridge.importFile(handle, "PHOTOS", note.absolutePath, "NOTE.TXT"))
        val out = File(dir, "out.txt")
        assertEquals(0, NativeBridge.exportFile(handle, "PHOTOS/NOTE.TXT", out.absolutePath))
        assertEquals("usb-nested-ok\n", out.readText())

        NativeBridge.closeVolume(handle)
        val again = NativeBridge.openVolume(path, pw, pim, false, emptyArray(), false)
        assertTrue(NativeBridge.isOpen(again))
        val out2 = File(dir, "out2.txt")
        assertEquals(0, NativeBridge.exportFile(again, "PHOTOS/NOTE.TXT", out2.absolutePath))
        assertEquals("usb-nested-ok\n", out2.readText())
        NativeBridge.closeVolume(again)
        OtgBlockStore.release(path)
        val dead = NativeBridge.openVolume(path, pw, pim, false, emptyArray(), false)
        assertFalse("released USB slot must not auto-mount", NativeBridge.isOpen(dead))
    }

    private fun fillEntropy() {
        NativeBridge.resetEntropy()
        NativeBridge.addEntropy(ByteArray(256) { it.toByte() })
    }
}
