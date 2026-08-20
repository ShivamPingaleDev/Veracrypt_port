package dev.shivampingale.vcport

import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * Sprint 10: create a random VeraCrypt file container on this phone, then
 * open a container the other phone made. A host script copies the .hc files
 * between the emulator and the iOS Simulator.
 */
@RunWith(AndroidJUnit4::class)
class CrossPhoneVolumeTest {
    @Test
    fun createRandomVolumeOnAndroid() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        fillEntropy()
        val cross = crossDir(ctx)
        val work = File(ctx.cacheDir, "cross-create").apply {
            deleteRecursively()
            mkdirs()
        }
        val password = NativeBridge.generatePassword(64)
        assertNotNull(password)
        assertEquals(64, password!!.length)

        val memo = "android-memo-ok\n"
        val photo = ByteArray(32 * 1024) { it.toByte() }
        val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)
        val memoFile = File(work, "MEMO.TXT").apply { writeText(memo) }
        val photoFile = File(work, "PHOTO.JPG").apply { writeBytes(photo) }
        val zipFile = File(work, "NOTES.ZIP").apply { writeBytes(zip) }

        val volume = File(cross, "android-made.hc")
        volume.delete()
        assertEquals(
            0,
            NativeBridge.createVolume(
                volume.absolutePath,
                password,
                1,
                2L * 1024L * 1024L,
                NativeBridge.DEFAULT_CIPHER,
                NativeBridge.DEFAULT_KDF,
                emptyArray(),
                "",
                0,
                0L,
                emptyArray(),
                "FAT"
            )
        )
        val handle = NativeBridge.openVolume(
            volume.absolutePath, password, 1, false, emptyArray(), false
        )
        assertTrue("android create must open: $handle", NativeBridge.isOpen(handle))
        assertEquals(0, NativeBridge.importFile(handle, "/", memoFile.absolutePath, "MEMO.TXT"))
        assertEquals(0, NativeBridge.importFile(handle, "/", photoFile.absolutePath, "PHOTO.JPG"))
        assertEquals(0, NativeBridge.importFile(handle, "/", zipFile.absolutePath, "NOTES.ZIP"))
        NativeBridge.closeVolume(handle)

        val meta = JSONObject()
        meta.put("password", password)
        meta.put("pim", 1)
        meta.put("cipher", NativeBridge.DEFAULT_CIPHER)
        meta.put("kdf", NativeBridge.DEFAULT_KDF)
        val files = JSONObject()
        files.put("MEMO.TXT", sha256(memo.toByteArray()))
        files.put("PHOTO.JPG", sha256(photo))
        files.put("NOTES.ZIP", sha256(zip))
        meta.put("files", files)
        File(cross, "android-made.json").writeText(meta.toString())
        publishToDownload(volume, "android-made.hc")
        publishToDownload(File(cross, "android-made.json"), "android-made.json")
        assertTrue(volume.length() >= 2L * 1024L * 1024L)
        Hardening.wipeDir(work)
    }

    @Test
    fun openVolumeMadeOnIos() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val cross = crossDir(ctx)
        val volume = File(cross, "ios-made.hc")
        val metaFile = File(cross, "ios-made.json")
        if (!volume.isFile || !metaFile.isFile) {
            copyViaShell("/sdcard/Download/vcport-cross/ios-made.hc", volume)
            copyViaShell("/sdcard/Download/vcport-cross/ios-made.json", metaFile)
        }
        if (!volume.isFile || !metaFile.isFile) {
            copyViaShell("/data/local/tmp/vcport-cross/ios-made.hc", volume)
            copyViaShell("/data/local/tmp/vcport-cross/ios-made.json", metaFile)
        }
        Assume.assumeTrue("iOS-made volume not copied onto this emulator yet", volume.isFile && metaFile.isFile)
        val meta = JSONObject(metaFile.readText())
        val password = meta.getString("password")
        val pim = meta.getInt("pim")
        val files = meta.getJSONObject("files")
        val handle = NativeBridge.openVolume(
            volume.absolutePath, password, pim, false, emptyArray(), true
        )
        assertTrue("iOS volume must open on Android: $handle", NativeBridge.isOpen(handle))
        val info = NativeBridge.volumeInfo(handle)
        assertNotNull(info)
        assertTrue(info!!.contains(meta.optString("cipher", NativeBridge.DEFAULT_CIPHER)))
        val listed = NativeBridge.listRoot(handle)
        val outDir = File(ctx.cacheDir, "cross-open").apply {
            deleteRecursively()
            mkdirs()
        }
        val keys = files.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            assertTrue("$name missing in iOS volume: ${listed.joinToString()}", listed.any { it.startsWith("$name\t") })
            val dest = File(outDir, name)
            assertEquals(0, NativeBridge.exportFile(handle, name, dest.absolutePath))
            assertEquals(files.getString(name), sha256(dest.readBytes()))
        }
        NativeBridge.closeVolume(handle)
        Hardening.wipeDir(outDir)
    }

    companion object {
        fun crossDir(ctx: android.content.Context): File {
            val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            return File(base, "cross").apply { mkdirs() }
        }

        fun publicCrossDir(): File {
            return File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "vcport-cross"
            ).apply { mkdirs() }
        }

        private fun publishToDownload(src: File, name: String) {
            try {
                val dest = File(publicCrossDir(), name)
                src.copyTo(dest, overwrite = true)
                if (dest.isFile && dest.length() == src.length()) {
                    return
                }
            } catch (_: Exception) {
            }
            val ctx = InstrumentationRegistry.getInstrumentation().targetContext
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/vcport-cross")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            ctx.contentResolver.update(uri, values, null, null)
        }

        private fun copyViaShell(remote: String, dest: File) {
            dest.parentFile?.mkdirs()
            try {
                InstrumentationRegistry.getInstrumentation().uiAutomation
                    .executeShellCommand("cat $remote")
                    .use { pfd ->
                        java.io.FileInputStream(pfd.fileDescriptor).use { input ->
                            dest.outputStream().use { input.copyTo(it) }
                        }
                    }
            } catch (_: Exception) {
                dest.delete()
            }
            if (dest.isFile && dest.length() == 0L) {
                dest.delete()
            }
        }

        private fun fillEntropy() {
            NativeBridge.resetEntropy()
            val sample = ByteArray(32) { 0x5A }
            repeat(320) { NativeBridge.addEntropy(sample) }
            assertEquals(100, NativeBridge.entropyPercent())
        }

        private fun sha256(bytes: ByteArray): String {
            val d = MessageDigest.getInstance("SHA-256").digest(bytes)
            return d.joinToString("") { "%02x".format(it) }
        }
    }
}
