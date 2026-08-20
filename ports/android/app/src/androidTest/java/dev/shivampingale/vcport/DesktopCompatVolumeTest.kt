package dev.shivampingale.vcport

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
 * Sprint 11: open official desktop VeraCrypt volumes (password, PIM, keyfiles,
 * cascade, hash) and a host-engine volume on this phone.
 */
@RunWith(AndroidJUnit4::class)
class DesktopCompatVolumeTest {
    @Test
    fun openDesktopCreatedVolumes() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = desktopDir(ctx)
        val names = listOf(
            "aes-sha512-pim-kf.json",
            "cascade-sha512.json",
            "aes-sha256.json",
            "engine-made.json",
            "hidden.json"
        )
        var opened = 0
        for (name in names) {
            val metaFile = File(dir, name)
            if (!metaFile.isFile) {
                copySidecar(name, dir)
            }
            if (!metaFile.isFile) {
                continue
            }
            openOne(ctx, dir, JSONObject(metaFile.readText()))
            opened += 1
        }
        Assume.assumeTrue("desktop volumes not copied onto this emulator yet", opened > 0)
        assertTrue("expected AES+keyfile, cascade, sha256, engine", opened >= 4)
    }

    private fun openOne(ctx: android.content.Context, dir: File, meta: JSONObject) {
        val volumeName = meta.optString("volume", "")
        val volume = File(dir, volumeName)
        if (!volume.isFile) {
            copyViaShell("/sdcard/Download/vcport-desktop/$volumeName", volume)
            copyViaShell("/data/local/tmp/vcport-desktop/$volumeName", volume)
        }
        assertTrue("$volumeName missing", volume.isFile)
        val password = meta.getString("password")
        val pim = meta.optInt("pim", 1)
        val keyfiles = jsonStringArray(meta.optJSONArray("keyfiles")).map { name ->
            val kf = File(dir, name)
            if (!kf.isFile) {
                copyViaShell("/sdcard/Download/vcport-desktop/$name", kf)
                copyViaShell("/data/local/tmp/vcport-desktop/$name", kf)
            }
            assertTrue("keyfile $name missing", kf.isFile)
            kf.absolutePath
        }.toTypedArray()
        val handle = NativeBridge.openVolume(
            volume.absolutePath, password, pim, false, keyfiles, true
        )
        assertTrue("$volumeName must open: $handle", NativeBridge.isOpen(handle))
        val info = NativeBridge.volumeInfo(handle)
        assertNotNull(info)
        val cipher = meta.optString("cipher", "")
        if (cipher.isNotEmpty()) {
            assertTrue("$volumeName cipher $info", info!!.contains(cipher))
        }
        val kdf = meta.optString("kdf", "")
        if (kdf.isNotEmpty()) {
            assertTrue("$volumeName kdf $info", info!!.contains(kdf))
        }
        checkFiles(ctx, handle, meta.getJSONObject("files"), "$volumeName outer")
        NativeBridge.closeVolume(handle)

        val hiddenPassword = meta.optString("hidden_password", "")
        if (hiddenPassword.isNotEmpty()) {
            val hidden = NativeBridge.openVolume(
                volume.absolutePath, hiddenPassword, meta.optInt("hidden_pim", pim),
                false, keyfiles, true
            )
            assertTrue("$volumeName hidden must open: $hidden", NativeBridge.isOpen(hidden))
            checkFiles(ctx, hidden, meta.getJSONObject("hidden_files"), "$volumeName hidden")
            NativeBridge.closeVolume(hidden)
        }
    }

    private fun checkFiles(ctx: android.content.Context, handle: Long, files: JSONObject, label: String) {
        val listed = NativeBridge.listRoot(handle)
        val outDir = File(ctx.cacheDir, "desktop-open").apply {
            deleteRecursively()
            mkdirs()
        }
        val keys = files.keys()
        while (keys.hasNext()) {
            val name = keys.next()
            assertTrue("$label $name missing: ${listed.joinToString()}", listed.any { it.startsWith("$name\t") })
            val dest = File(outDir, name)
            assertEquals(0, NativeBridge.exportFile(handle, name, dest.absolutePath))
            assertEquals("$label $name hash", files.getString(name), sha256(dest.readBytes()))
            if (name.equals("PHOTO.JPG", ignoreCase = true)) {
                assertEquals("$label PHOTO.JPG size", 32 * 1024, dest.length().toInt())
            }
        }
        Hardening.wipeDir(outDir)
    }

    private fun copySidecar(name: String, dir: File) {
        copyViaShell("/sdcard/Download/vcport-desktop/$name", File(dir, name))
        if (!File(dir, name).isFile) {
            copyViaShell("/data/local/tmp/vcport-desktop/$name", File(dir, name))
        }
    }

    companion object {
        fun desktopDir(ctx: android.content.Context): File {
            val base = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            return File(base, "desktop").apply { mkdirs() }
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

        private fun jsonStringArray(arr: org.json.JSONArray?): List<String> {
            if (arr == null) {
                return emptyList()
            }
            return (0 until arr.length()).map { arr.getString(it) }
        }

        private fun sha256(bytes: ByteArray): String {
            val d = MessageDigest.getInstance("SHA-256").digest(bytes)
            return d.joinToString("") { "%02x".format(it) }
        }
    }
}
