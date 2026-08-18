package dev.shivampingale.vcport

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Compose UI on a real activity. FLAG_SECURE stays on. Does not tap Panic wipe
 * and does not start an update-check HTTPS window. Writes GitHub README shots
 * (Compose capture, not adb screencap) to Download/vcport-github-shots.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun chromeTabsAndContractCopy() {
        if (BuildConfig.ENABLE_SKINS) return
        val flags = rule.activity.window.attributes.flags
        assertTrue(
            "FLAG_SECURE must stay on during UI use",
            flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        )

        rule.onNodeWithText("VC Port").assertIsDisplayed()
        rule.onNodeWithText("Stay offline. F-Droid: no network.").assertIsDisplayed()
        rule.onNodeWithText("Panic wipe").assertIsDisplayed()
        rule.onNodeWithText("Share encrypted").assertIsDisplayed()
        rule.onNodeWithText("Working…").assertDoesNotExist()
        captureShot("01-volume.png")

        rule.onNodeWithTag("tab_create").performClick()
        rule.onNodeWithText("File basket").assertIsDisplayed()
        captureShot("02-wrap.png")

        rule.onNodeWithTag("tab_create").performClick()
        rule.onNodeWithText("Encryption Algorithm").assertIsDisplayed()
        captureShot("03-create.png")

        rule.onNodeWithTag("tab_tools").performClick()
        rule.onNodeWithText("Decrypt wrap").performScrollTo().assertIsDisplayed()
        captureShot("04-tools.png")

        rule.onNodeWithTag("tab_volume").performClick()
        rule.onNodeWithText("Open volume").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Use backup header").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Read-only").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(
            "Protect hidden volume against damage caused by writing to outer volume"
        ).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("not unbreakable", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("compelled", substring = true).performScrollTo().assertIsDisplayed()

        rule.onNodeWithTag("tab_create").performClick()
        rule.onNodeWithText("Generate strong password").performScrollTo().assertIsEnabled()
        rule.onAllNodesWithText("Generate strong password").onFirst().performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("It is not saved", substring = true).assertIsDisplayed()

        rule.onNodeWithTag("tab_create").performClick()
        rule.onNodeWithText("no open-time hidden checkbox", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Create volume").performScrollTo().assertIsDisplayed()

        rule.onNodeWithTag("tab_tools").performClick()
        rule.onNodeWithText("Change volume password").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("Keyfile generator").onFirst().performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Benchmark").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Test vectors").performScrollTo().assertIsDisplayed()

        rule.onNodeWithTag("tab_volume").performClick()
        rule.onNodeWithText("Open volume").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Working…").assertDoesNotExist()
        rule.onNodeWithText("Check for updates").assertDoesNotExist()
        assertTrue(
            "FLAG_SECURE must still be on after tab walk",
            rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        )
    }

    @Test
    fun experimentalComputerSkinShots() {
        if (!BuildConfig.ENABLE_SKINS) return
        rule.waitForIdle()
        rule.onNodeWithTag("tab_tools").performClick()
        rule.onNodeWithText("Looks (this phone)").performScrollTo().assertIsDisplayed()
        val shots = listOf(
            "skin_cyberpunk" to "skin-cyberpunk.png",
            "skin_matrix" to "skin-matrix.png",
            "skin_eva" to "skin-eva.png",
            "skin_signal" to "skin-signal.png"
        )
        val docs = listOf(
            "05-skin-cyberpunk.png",
            "06-skin-matrix.png",
            "07-skin-eva.png",
            "08-skin-signal.png"
        )
        for ((i, pair) in shots.withIndex()) {
            val (tag, file) = pair
            rule.onNodeWithTag(tag).performScrollTo().performClick()
            rule.waitForIdle()
            rule.onNodeWithTag("tab_volume").performClick()
            rule.waitForIdle()
            captureShot(file, folder = "vcport-theme-shots")
            captureShot(docs[i])
            rule.onNodeWithTag("tab_tools").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("skin_desktop").performScrollTo().performClick()
        assertTrue(
            "FLAG_SECURE stays on for experimental skins",
            rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        )
        rule.onNodeWithTag("tab_volume").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Check for updates").assertDoesNotExist()
    }

    @Test
    fun generateCopyBackgroundPasteContinue() {
        if (BuildConfig.ENABLE_SKINS) return
        rule.onNodeWithTag("tab_create").performClick()
        rule.onNodeWithText("Generate strong password").performScrollTo().assertIsEnabled()
        rule.onAllNodesWithText("Generate strong password").onFirst().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("It is not saved", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("copy_once").performScrollTo().performClick()
        rule.waitForIdle()

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val clip = ctx.getSystemService(android.content.ClipboardManager::class.java)
        val secret = clip.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
        assertNotNull("Copy once must put the generated password on the clipboard", secret)
        assertEquals(64, secret!!.length)
        val notes = File(ctx.filesDir, "notes-paste.txt")
        notes.writeText(secret)
        assertEquals("simulated Notes paste", secret, notes.readText())

        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 3")
            .close()
        Thread.sleep(2500)

        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        assertNotNull(intent)
        intent!!.addFlags(
            android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        )
        ctx.startActivity(intent)
        rule.waitForIdle()
        rule.onNodeWithTag("tab_create").performClick()
        rule.waitForIdle()
        rule.onNodeWithText(
            "Dismounted. Passwords, keyfiles in memory",
            substring = true
        ).assertDoesNotExist()
        rule.onNodeWithTag("copy_once").performScrollTo().performClick()
        rule.waitForIdle()
        val again = clip.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
        assertEquals(
            "Create password must survive background so the wizard can continue",
            secret,
            again
        )
        assertEquals(secret, notes.readText())
    }

    private fun captureShot(name: String, folder: String = "vcport-github-shots") {
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "github-shots"
        )
        if (!dir.exists()) {
            assertTrue("mkdir github-shots", dir.mkdirs())
        }
        val out = File(dir, name)
        out.outputStream().use { stream ->
            assertTrue("compress $name", bmp.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        assertTrue("$name too small: ${out.length()}", out.length() > 20_000L)
        publishShot(out, name, folder)
    }

    /** Survive Gradle uninstall-after-test so the host can adb pull the PNGs. */
    private fun publishShot(file: File, name: String, folderName: String = "vcport-github-shots") {
        assertTrue("MediaStore shots need API 29+", Build.VERSION.SDK_INT >= 29)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
            arrayOf(name),
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                ctx.contentResolver.delete(
                    android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id),
                    null,
                    null
                )
            }
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/$folderName"
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        assertNotNull("insert $name", uri)
        ctx.contentResolver.openOutputStream(uri!!).use { stream ->
            assertNotNull("open $name", stream)
            file.inputStream().use { input -> input.copyTo(stream!!) }
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        assertTrue(
            "publish $name",
            ctx.contentResolver.update(uri, values, null, null) > 0
        )
    }
}
