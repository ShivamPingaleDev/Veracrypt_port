package dev.shivampingale.vcport

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        fillEntropy()
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
        val wrapWrong = NativeBridge.unwrapFile(wrap.absolutePath, unwrapDir.absolutePath, "wrong-wrap-password")
        assertTrue("wrong wrap password must not yield a path", wrapWrong.isNullOrEmpty())
        val flipped = wrap.readBytes()
        flipped[flipped.size - 8] = (flipped[flipped.size - 8].toInt() xor 0xFF).toByte()
        val tampered = File(dir, "tampered.vcpw").apply { writeBytes(flipped) }
        assertTrue(NativeBridge.isWrap(tampered.absolutePath))
        val wrapTamper = NativeBridge.unwrapFile(tampered.absolutePath, unwrapDir.absolutePath, password)
        assertTrue("tampered wrap must not unwrap", wrapTamper.isNullOrEmpty())

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
                emptyArray(),
                "FAT"
            )
        )

        assertTrue("volume too small: ${volume.length()}", volume.exists() && volume.length() >= 2L * 1024L * 1024L)

        val wrong = NativeBridge.openVolume(
            volume.absolutePath, "wrong-password", 1, false, keyfiles, false
        )
        assertTrue("wrong password returned $wrong", !NativeBridge.isOpen(wrong))

        val pimZero = NativeBridge.openVolume(
            volume.absolutePath, volumePassword, 0, false, keyfiles, false
        )
        assertTrue("TrueCrypt Mode PIM 0 must not open a PIM 1 volume: $pimZero", !NativeBridge.isOpen(pimZero))

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
        val page0 = NativeBridge.listDir(handle, "/", 0)
        val page1 = NativeBridge.listDir(handle, "/", 1)
        assertTrue(page0.isNotEmpty())
        assertTrue(page1.size <= page0.size)

        val exported = File(dir, "out.txt")
        assertEquals(0, NativeBridge.exportFile(handle, "VAULT/NOTE.TXT", exported.absolutePath))
        assertEquals(PAYLOAD, exported.readText())
        assertEquals(0, NativeBridge.renameFile(handle, "VAULT/NOTE.TXT", "MEMO.TXT"))
        assertEquals(0, NativeBridge.deleteFile(handle, "VAULT/MEMO.TXT"))
        assertEquals(0, NativeBridge.importFile(handle, "VAULT", plain.absolutePath, "NOTE.TXT"))
        assertEquals(0, NativeBridge.mkdir(handle, "/", "COPY"))
        val copied = File(dir, "hello-copy.txt")
        assertEquals(0, NativeBridge.exportFile(handle, "HELLO.TXT", copied.absolutePath))
        assertEquals(0, NativeBridge.importFile(handle, "COPY", copied.absolutePath, "HELLO.TXT"))
        assertTrue(NativeBridge.listDir(handle, "COPY").any { it.startsWith("HELLO.TXT\t") })
        assertEquals(PAYLOAD, copied.readText())
        assertEquals(0, NativeBridge.wipeFreeSpace(handle))
        NativeBridge.closeVolume(handle)

        val readOnly = NativeBridge.openVolume(
            volume.absolutePath, volumePassword, 1, false, keyfiles, readOnly = true
        )
        assertTrue("read-only open failed with $readOnly", NativeBridge.isOpen(readOnly))
        assertTrue(NativeBridge.mkdir(readOnly, "/", "DENIED") != 0)
        assertTrue(NativeBridge.importFile(readOnly, "/", plain.absolutePath, "NOPE.TXT") != 0)
        assertTrue(NativeBridge.deleteFile(readOnly, "HELLO.TXT") != 0)
        NativeBridge.closeVolume(readOnly)

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
        val fromBackup = NativeBridge.openVolume(
            volume.absolutePath, volumePassword, 1, true, keyfiles, false
        )
        assertTrue("use backup header failed with $fromBackup", NativeBridge.isOpen(fromBackup))
        NativeBridge.closeVolume(fromBackup)
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

    /**
     * Person session on the emulator: several volumes, basket + BASKET.sha256,
     * corrupt primary header then restore from .bak and from the embedded backup,
     * extra 64-byte phone-unlock keyfile, header KDF change, add/remove keyfiles,
     * change password, Copy once clipboard (not wiped by lock).
     */
    @Test
    fun phoneSessionFlows() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.cacheDir, "device-sim-session").apply {
            deleteRecursively()
            mkdirs()
        }
        fillEntropy()

        val pwA = "vcport-vol-a-password"
        val pwB = "vcport-vol-b-password"
        val pwBasket = "vcport-basket-password"
        val pwBio = "vcport-bio-volume-pw"
        val pim = 1

        val random1 = File(dir, "rand1.bin").apply { writeBytes(ByteArray(4096) { it.toByte() }) }
        val random2 = File(dir, "rand2.bin").apply { writeBytes(ByteArray(8192) { (it * 3).toByte() }) }
        val note = File(dir, "note.txt").apply { writeText("basket-note-ok\n") }
        val hash1 = sha256(random1)
        val hash2 = sha256(random2)
        val hashNote = sha256(note)

        val volA = File(dir, "photos.mp4")
        val volB = File(dir, "backup.zip")
        assertEquals(0, makeVolume(volA, pwA, pim, 2L shl 20, "AES", "HMAC-SHA-256", emptyArray(), "FAT"))
        assertEquals(0, makeVolume(volB, pwB, pim, 3L shl 20, "AES", "HMAC-SHA-256", emptyArray(), "exFAT"))
        assertTrue(volA.length() >= 2L shl 20)
        assertTrue(volB.length() >= 3L shl 20)

        var handleA = NativeBridge.openVolume(volA.absolutePath, pwA, pim, false, emptyArray(), false)
        assertTrue("open A failed $handleA", NativeBridge.isOpen(handleA))
        assertEquals(0, NativeBridge.mkdir(handleA, "/", "DATA"))
        assertEquals(0, NativeBridge.importFile(handleA, "DATA", random1.absolutePath, "RAND1.BIN"))
        assertEquals(0, NativeBridge.importFile(handleA, "/", random2.absolutePath, "RAND2.BIN"))
        NativeBridge.closeVolume(handleA)

        var handleB = NativeBridge.openVolume(volB.absolutePath, pwB, pim, false, emptyArray(), false)
        assertTrue("open B failed $handleB", NativeBridge.isOpen(handleB))
        val infoB = NativeBridge.volumeInfo(handleB)
        assertNotNull(infoB)
        assertTrue(infoB!!.contains("AES"))
        assertTrue(infoB.contains("HMAC-SHA-256"))
        assertEquals(0, NativeBridge.importFile(handleB, "/", note.absolutePath, "NOTE.TXT"))
        NativeBridge.closeVolume(handleB)

        handleA = NativeBridge.openVolume(volA.absolutePath, pwA, pim, false, emptyArray(), false)
        assertTrue(NativeBridge.isOpen(handleA))
        val outA1 = File(dir, "out-a1.bin")
        val outA2 = File(dir, "out-a2.bin")
        assertEquals(0, NativeBridge.exportFile(handleA, "DATA/RAND1.BIN", outA1.absolutePath))
        assertEquals(0, NativeBridge.exportFile(handleA, "RAND2.BIN", outA2.absolutePath))
        assertEquals(hash1, sha256(outA1))
        assertEquals(hash2, sha256(outA2))
        assertFalse(NativeBridge.listRoot(handleA).any { it.startsWith("NOTE.TXT\t") })
        NativeBridge.closeVolume(handleA)

        handleB = NativeBridge.openVolume(volB.absolutePath, pwB, pim, false, emptyArray(), false)
        assertTrue(NativeBridge.isOpen(handleB))
        val outB = File(dir, "out-b.txt")
        assertEquals(0, NativeBridge.exportFile(handleB, "NOTE.TXT", outB.absolutePath))
        assertEquals(hashNote, sha256(outB))
        NativeBridge.closeVolume(handleB)

        val basketVol = File(dir, "basket.hc")
        val basketBytes = SizeUnits.MIN_VOLUME + (5L shl 20)
        assertEquals(
            0,
            makeVolume(basketVol, pwBasket, pim, basketBytes, "AES", "HMAC-SHA-256", emptyArray(), "FAT")
        )
        val basketHandle = NativeBridge.openVolume(
            basketVol.absolutePath, pwBasket, pim, false, emptyArray(), false
        )
        assertTrue("basket open failed $basketHandle", NativeBridge.isOpen(basketHandle))
        assertEquals(0, NativeBridge.importFile(basketHandle, "/", random1.absolutePath, "RAND1.BIN"))
        assertEquals(0, NativeBridge.importFile(basketHandle, "/", random2.absolutePath, "RAND2.BIN"))
        assertEquals(0, NativeBridge.importFile(basketHandle, "/", note.absolutePath, "NOTE.TXT"))
        val proof = File(dir, "BASKET.sha256")
        proof.writeText("$hash1  RAND1.BIN\n$hash2  RAND2.BIN\n$hashNote  NOTE.TXT\n")
        assertEquals(0, NativeBridge.importFile(basketHandle, "/", proof.absolutePath, "BASKET.sha256"))
        NativeBridge.closeVolume(basketHandle)

        val basketAgain = NativeBridge.openVolume(
            basketVol.absolutePath, pwBasket, pim, false, emptyArray(), false
        )
        assertTrue(NativeBridge.isOpen(basketAgain))
        val names = NativeBridge.listRoot(basketAgain).map { it.substringBefore('\t') }
        assertTrue(names.contains("RAND1.BIN"))
        assertTrue(names.contains("RAND2.BIN"))
        assertTrue(names.contains("NOTE.TXT"))
        assertTrue(names.contains("BASKET.sha256"))
        val proofOut = File(dir, "proof-out.txt")
        val r1Out = File(dir, "basket-r1.bin")
        assertEquals(0, NativeBridge.exportFile(basketAgain, "BASKET.sha256", proofOut.absolutePath))
        assertEquals(0, NativeBridge.exportFile(basketAgain, "RAND1.BIN", r1Out.absolutePath))
        assertEquals(hash1, sha256(r1Out))
        val proofText = proofOut.readText()
        assertTrue(proofText.contains(hash1))
        assertTrue(proofText.contains("RAND1.BIN"))
        NativeBridge.closeVolume(basketAgain)

        val bak = File(dir, "photos.bak")
        assertEquals(0, NativeBridge.backupHeaders(volA.absolutePath, bak.absolutePath, pwA, pim, emptyArray()))
        assertTrue("backup header too small: ${bak.length()}", bak.length() >= 64L * 1024L)

        corruptPrimaryHeader(volA)
        assertTrue(
            "primary header still opened after corruption",
            !NativeBridge.isOpen(
                NativeBridge.openVolume(volA.absolutePath, pwA, pim, false, emptyArray(), false)
            )
        )
        val fromBak = NativeBridge.openVolume(volA.absolutePath, pwA, pim, true, emptyArray(), false)
        assertTrue("open with backup header failed $fromBak", NativeBridge.isOpen(fromBak))
        val restoredA = File(dir, "restored-a.bin")
        assertEquals(0, NativeBridge.exportFile(fromBak, "DATA/RAND1.BIN", restoredA.absolutePath))
        assertEquals(hash1, sha256(restoredA))
        NativeBridge.closeVolume(fromBak)

        assertEquals(0, NativeBridge.restoreHeaders(volA.absolutePath, bak.absolutePath, pwA, pim, emptyArray()))
        val afterBak = NativeBridge.openVolume(volA.absolutePath, pwA, pim, false, emptyArray(), false)
        assertTrue("open after .bak restore failed $afterBak", NativeBridge.isOpen(afterBak))
        NativeBridge.closeVolume(afterBak)

        corruptPrimaryHeader(volA)
        assertEquals(0, NativeBridge.restoreHeaders(volA.absolutePath, "", pwA, pim, emptyArray()))
        val afterEmbedded = NativeBridge.openVolume(volA.absolutePath, pwA, pim, false, emptyArray(), false)
        assertTrue("open after embedded restore failed $afterEmbedded", NativeBridge.isOpen(afterEmbedded))
        val restoredEmbedded = File(dir, "embedded-a.bin")
        assertEquals(0, NativeBridge.exportFile(afterEmbedded, "RAND2.BIN", restoredEmbedded.absolutePath))
        assertEquals(hash2, sha256(restoredEmbedded))
        NativeBridge.closeVolume(afterEmbedded)

        val bioKey = FactorCodec.randomBiometricKey()
        assertEquals(64, bioKey.size)
        val bioFile = KeyfileIo.writeSecret(ctx, bioKey)
        val bioVol = File(dir, "bio.hc")
        assertEquals(
            0,
            makeVolume(bioVol, pwBio, pim, 2L shl 20, "AES", "HMAC-SHA-256", arrayOf(bioFile.absolutePath), "FAT")
        )
        assertTrue(
            "bio volume must not open without the extra keyfile",
            !NativeBridge.isOpen(
                NativeBridge.openVolume(bioVol.absolutePath, pwBio, pim, false, emptyArray(), false)
            )
        )
        val bioHandle = NativeBridge.openVolume(
            bioVol.absolutePath, pwBio, pim, false, arrayOf(bioFile.absolutePath), false
        )
        assertTrue("bio volume open failed $bioHandle", NativeBridge.isOpen(bioHandle))
        assertEquals(0, NativeBridge.importFile(bioHandle, "/", note.absolutePath, "NOTE.TXT"))
        NativeBridge.closeVolume(bioHandle)
        val vault = BiometricVault(ctx)
        vault.isAvailable()

        val kf = File(dir, "extra.key")
        assertEquals(0, NativeBridge.generateKeyfile(kf.absolutePath, 128))
        assertEquals(
            0,
            NativeBridge.changeHeader(
                volB.absolutePath, pwB, pim, emptyArray(), false,
                "", pim, "HMAC-SHA-512", emptyArray()
            )
        )
        val kdfHandle = NativeBridge.openVolume(volB.absolutePath, pwB, pim, false, emptyArray(), false)
        assertTrue("open after KDF change failed $kdfHandle", NativeBridge.isOpen(kdfHandle))
        val kdfInfo = NativeBridge.volumeInfo(kdfHandle)
        assertNotNull(kdfInfo)
        assertTrue("expected HMAC-SHA-512, got $kdfInfo", kdfInfo!!.contains("HMAC-SHA-512"))
        NativeBridge.closeVolume(kdfHandle)

        assertEquals(
            0,
            NativeBridge.changeHeader(
                volB.absolutePath, pwB, pim, emptyArray(), false,
                "", pim, "", arrayOf(kf.absolutePath)
            )
        )
        assertTrue(
            "must not open after adding a keyfile without it",
            !NativeBridge.isOpen(
                NativeBridge.openVolume(volB.absolutePath, pwB, pim, false, emptyArray(), false)
            )
        )
        val withKf = NativeBridge.openVolume(
            volB.absolutePath, pwB, pim, false, arrayOf(kf.absolutePath), false
        )
        assertTrue(NativeBridge.isOpen(withKf))
        val afterKf = File(dir, "after-kf.txt")
        assertEquals(0, NativeBridge.exportFile(withKf, "NOTE.TXT", afterKf.absolutePath))
        assertEquals(hashNote, sha256(afterKf))
        NativeBridge.closeVolume(withKf)

        assertEquals(
            0,
            NativeBridge.changeHeader(
                volB.absolutePath, pwB, pim, arrayOf(kf.absolutePath), false,
                "", pim, "", emptyArray()
            )
        )
        val noKf = NativeBridge.openVolume(volB.absolutePath, pwB, pim, false, emptyArray(), false)
        assertTrue("open after removing all keyfiles failed $noKf", NativeBridge.isOpen(noKf))
        NativeBridge.closeVolume(noKf)
        assertTrue(
            "old keyfile must not open after remove-all",
            !NativeBridge.isOpen(
                NativeBridge.openVolume(volB.absolutePath, pwB, pim, false, arrayOf(kf.absolutePath), false)
            )
        )

        val changed = "vcport-vol-b-changed"
        assertEquals(
            0,
            NativeBridge.changeHeader(
                volB.absolutePath, pwB, pim, emptyArray(), false,
                changed, pim, "", emptyArray()
            )
        )
        assertTrue(
            !NativeBridge.isOpen(
                NativeBridge.openVolume(volB.absolutePath, pwB, pim, false, emptyArray(), false)
            )
        )
        val changedHandle = NativeBridge.openVolume(volB.absolutePath, changed, pim, false, emptyArray(), false)
        assertTrue(NativeBridge.isOpen(changedHandle))
        NativeBridge.closeVolume(changedHandle)

        val secret = NativeBridge.generatePassword(64)
        assertNotNull(secret)
        assertEquals(64, secret!!.length)
        val fromClip = arrayOfNulls<String>(1)
        val latch = java.util.concurrent.CountDownLatch(1)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            SensitiveClipboard.copyOnce(ctx, secret)
            val clip = ctx.getSystemService(android.content.ClipboardManager::class.java)
            fromClip[0] = clip.primaryClip?.getItemAt(0)?.text?.toString()
            latch.countDown()
        }
        assertTrue(latch.await(5, java.util.concurrent.TimeUnit.SECONDS))
        val notes = File(dir, "notes-paste.txt").apply { writeText(secret) }
        assertEquals(secret, notes.readText())
        fromClip[0]?.let { clipped ->
            assertEquals("Copy once must leave the generated password on the clipboard", secret, clipped)
        }

        KeyfileIo.wipe(bioFile)
        Hardening.wipeDir(dir)
    }

    @Test
    fun hiddenVolumeWriteProtection() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.cacheDir, "device-sim-hidden").apply {
            deleteRecursively()
            mkdirs()
        }
        fillEntropy()
        val volume = File(dir, "nested.hc")
        val outer = "vcport-outer-password"
        val hidden = "vcport-hidden-password"
        assertEquals(
            0,
            NativeBridge.createVolume(
                volume.absolutePath,
                outer,
                1,
                6L * 1024L * 1024L,
                NativeBridge.DEFAULT_CIPHER,
                NativeBridge.DEFAULT_KDF,
                emptyArray(),
                hidden,
                1,
                2L * 1024L * 1024L,
                emptyArray(),
                "FAT"
            )
        )

        val denied = NativeBridge.openVolume(
            volume.absolutePath, outer, 1, false, emptyArray(), false,
            protectHidden = true, hiddenPassword = "wrong-hidden", hiddenPim = 1
        )
        assertTrue("wrong nested password returned $denied", !NativeBridge.isOpen(denied))

        val handle = NativeBridge.openVolume(
            volume.absolutePath, outer, 1, false, emptyArray(), false,
            protectHidden = true, hiddenPassword = hidden, hiddenPim = 1
        )
        assertTrue("protect-hidden open failed with $handle", NativeBridge.isOpen(handle))
        val info = NativeBridge.volumeInfo(handle)
        assertNotNull(info)
        assertTrue(info!!.contains("Volume type: Outer"))
        assertTrue(info.contains("Hidden Volume Protected: Yes"))
        assertFalse(NativeBridge.protectionTriggered(handle))
        NativeBridge.wipeFreeSpace(handle)
        if (!NativeBridge.protectionTriggered(handle)) {
            val fill = File(dir, "fill.bin").apply {
                writeBytes(ByteArray(3 * 1024 * 1024) { 0xAA.toByte() })
            }
            NativeBridge.importFile(handle, "/", fill.absolutePath, "FILL.BIN")
        }
        assertTrue(
            "writing outer free space must trip hidden-volume protection",
            NativeBridge.protectionTriggered(handle)
        )
        NativeBridge.closeVolume(handle)
        Hardening.wipeDir(dir)
    }

    companion object {
        private const val PAYLOAD = "from-device-encrypted\n"

        private fun fillEntropy() {
            NativeBridge.resetEntropy()
            val sample = ByteArray(32) { 0x5A }
            repeat(320) { NativeBridge.addEntropy(sample) }
            assertEquals(100, NativeBridge.entropyPercent())
        }

        private fun makeVolume(
            file: File,
            password: String,
            pim: Int,
            size: Long,
            cipher: String,
            kdf: String,
            keyfiles: Array<String>,
            filesystem: String
        ): Int {
            return NativeBridge.createVolume(
                file.absolutePath,
                password,
                pim,
                size,
                cipher,
                kdf,
                keyfiles,
                "",
                0,
                0L,
                emptyArray(),
                filesystem
            )
        }

        private fun sha256(file: File): String {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    md.update(buf, 0, n)
                }
            }
            return md.digest().joinToString("") { b -> "%02x".format(b) }
        }

        private fun corruptPrimaryHeader(volume: File) {
            java.io.RandomAccessFile(volume, "rw").use { raf ->
                raf.seek(0)
                raf.write(ByteArray(64 * 1024) { 0xFF.toByte() })
            }
        }
    }
}
