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

        rule.onNodeWithTag("tab_wrap").performClick()
        rule.onNodeWithText("Encrypt file").assertIsDisplayed()
        rule.onNodeWithText("Decrypt wrap").assertIsDisplayed()
        captureShot("02-wrap.png")

        rule.onNodeWithTag("tab_create").performClick()
        rule.onNodeWithText("Encryption Algorithm").assertIsDisplayed()
        captureShot("03-create.png")

        rule.onNodeWithTag("tab_tools").performClick()
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

        rule.onNodeWithTag("tab_wrap").performClick()
        rule.onNodeWithText("Generate strong password").assertIsEnabled()
        rule.onAllNodesWithText("Generate strong password").onFirst().performClick()
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

    private fun captureShot(name: String) {
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
        publishShot(out, name)
    }

    /** Survive Gradle uninstall-after-test so the host can adb pull the PNGs. */
    private fun publishShot(file: File, name: String) {
        assertTrue("MediaStore shots need API 29+", Build.VERSION.SDK_INT >= 29)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = Environment.DIRECTORY_DOWNLOADS + "/vcport-github-shots/"
        ctx.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
            arrayOf(folder, name.replace(".png", "%")),
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
                Environment.DIRECTORY_DOWNLOADS + "/vcport-github-shots"
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
