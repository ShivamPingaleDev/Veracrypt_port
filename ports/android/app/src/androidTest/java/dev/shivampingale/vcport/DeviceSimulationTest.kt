package dev.shivampingale.vcport

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Emulator / device: the same NativeBridge path as the UI. Offline only —
 * never opens an update-check HTTPS window.
 */
@RunWith(AndroidJUnit4::class)
class DeviceSimulationTest {
    @Test
    fun createStoreEncryptDecryptReopen() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.cacheDir, "device-sim").apply {
            deleteRecursively()
            mkdirs()
        }

        NativeBridge.resetEntropy()
        val sample = ByteArray(32) { 0x5A }
        repeat(320) { NativeBridge.addEntropy(sample) }
        assertEquals(100, NativeBridge.entropyPercent())
        assertEquals(0, NativeBridge.testVectors())

        NativeBridge.resetProgress()
        NativeBridge.setProgress(5, "Create volume")
        assertEquals(5, NativeBridge.progressPercent())
        assertTrue(NativeBridge.progressPhase().contains("Create"))

        val password = NativeBridge.generatePassword(64)
        assertNotNull(password)
        assertEquals(64, password!!.length)
        val volumePassword = "vcport-emulator-volume"

        val bio = File(dir, "bio.bin")
        val extra = File(dir, "extra.bin")
        assertEquals(0, NativeBridge.generateKeyfile(bio.absolutePath, 64))
        assertEquals(0, NativeBridge.generateKeyfile(extra.absolutePath, 128))
        val keyfiles = arrayOf(bio.absolutePath, extra.absolutePath)

        val plain = File(dir, "plain.txt").apply { writeText(PAYLOAD) }
        val wrap = File(dir, "plain.vcpw")
        assertEquals(0, NativeBridge.wrapFile(plain.absolutePath, wrap.absolutePath, password, "NOTE.TXT"))
        assertTrue(NativeBridge.isWrap(wrap.absolutePath))
        val unwrapDir = File(dir, "unwrapped").apply { mkdirs() }
        val unwrapped = NativeBridge.unwrapFile(wrap.absolutePath, unwrapDir.absolutePath, password)
        assertNotNull(unwrapped)
        assertEquals(PAYLOAD, File(unwrapped!!).readText())

        val volume = File(dir, "vault.hc")
        assertEquals(
            0,
            NativeBridge.createVolume(
                volume.absolutePath,
                volumePassword,
                1,
                2L * 1024L * 1024L,
                NativeBridge.DEFAULT_CIPHER,
                NativeBridge.DEFAULT_KDF,
                keyfiles,
                "",
                0,
                0L,
                emptyArray()
            )
        )

        assertTrue("volume too small: ${volume.length()}", volume.exists() && volume.length() >= 2L * 1024L * 1024L)

        val wrong = NativeBridge.openVolume(
            volume.absolutePath, "wrong-password", 1, false, keyfiles, false
        )
        assertTrue("wrong password returned $wrong", !NativeBridge.isOpen(wrong))

        val handle = NativeBridge.openVolume(
            volume.absolutePath, volumePassword, 1, false, keyfiles, false
        )
        assertTrue("openVolume failed with $handle", NativeBridge.isOpen(handle))
        assertTrue(NativeBridge.volumeSize(handle) > 0L)
        val info = NativeBridge.volumeInfo(handle)
        assertNotNull(info)
        assertTrue(info!!.contains("AES(Twofish(Serpent))"))
        assertTrue(info.contains("HMAC-SHA-512"))

        assertEquals(0, NativeBridge.mkdir(handle, "/", "VAULT"))
        assertEquals(0, NativeBridge.importFile(handle, "/", plain.absolutePath, "HELLO.TXT"))
        assertEquals(0, NativeBridge.importFile(handle, "VAULT", plain.absolutePath, "NOTE.TXT"))
        assertTrue(NativeBridge.listRoot(handle).any { it.startsWith("VAULT\t") })
        assertTrue(NativeBridge.listDir(handle, "VAULT").any { it.startsWith("NOTE.TXT\t") })

        val exported = File(dir, "out.txt")
        assertEquals(0, NativeBridge.exportFile(handle, "VAULT/NOTE.TXT", exported.absolutePath))
        assertEquals(PAYLOAD, exported.readText())
        assertEquals(0, NativeBridge.renameFile(handle, "VAULT/NOTE.TXT", "MEMO.TXT"))
        assertEquals(0, NativeBridge.deleteFile(handle, "VAULT/MEMO.TXT"))
        assertEquals(0, NativeBridge.importFile(handle, "VAULT", plain.absolutePath, "NOTE.TXT"))
        assertEquals(0, NativeBridge.wipeFreeSpace(handle))
        NativeBridge.closeVolume(handle)

        val again = NativeBridge.openVolume(
            volume.absolutePath, volumePassword, 1, false, keyfiles, false
        )
        assertTrue("reopen failed with $again", NativeBridge.isOpen(again))
        assertEquals(0, NativeBridge.exportFile(again, "VAULT/NOTE.TXT", exported.absolutePath))
        assertEquals(PAYLOAD, exported.readText())
        assertEquals(0, NativeBridge.deleteFile(again, "VAULT/NOTE.TXT"))
        assertEquals(0, NativeBridge.rmdir(again, "VAULT"))
        NativeBridge.closeVolume(again)

        val bak = File(dir, "headers.bak")
        assertEquals(0, NativeBridge.backupHeaders(volume.absolutePath, bak.absolutePath, volumePassword, 1, keyfiles))
        assertEquals(0, NativeBridge.restoreHeaders(volume.absolutePath, bak.absolutePath, volumePassword, 1, keyfiles))

        val changed = "vcport-changed-password-ok"
        assertEquals(
            0,
            NativeBridge.changeHeader(
                volume.absolutePath, volumePassword, 1, keyfiles, false,
                changed, 1, "", keyfiles
            )
        )
        assertTrue(
            !NativeBridge.isOpen(
                NativeBridge.openVolume(volume.absolutePath, volumePassword, 1, false, keyfiles, false)
            )
        )
        val withNew = NativeBridge.openVolume(volume.absolutePath, changed, 1, false, keyfiles, false)
        assertTrue(NativeBridge.isOpen(withNew))
        NativeBridge.closeVolume(withNew)

        val bench = NativeBridge.benchmark()
        assertNotNull(bench)
        assertTrue(bench!!.contains("MiB/s"))

        Hardening.wipeDir(dir)
    }

    companion object {
        private const val PAYLOAD = "from-device-encrypted\n"
    }
}
