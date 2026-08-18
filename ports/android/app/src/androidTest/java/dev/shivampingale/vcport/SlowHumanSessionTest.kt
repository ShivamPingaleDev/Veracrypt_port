package dev.shivampingale.vcport

import android.content.Intent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
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
 * Slow person on the emulator: scribble entropy, generate, Copy once, Home,
 * paste into a notes file, come back through Recents, keep Create, watch
 * basket size grow, set cipher/KDF/PIM/disguise name. Does not tap Panic wipe
 * or Check for updates. Does not finish Create (system Save-as picker).
 */
@RunWith(AndroidJUnit4::class)
class SlowHumanSessionTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun generateCopyMinimizePasteContinueAndBasketSize() {
        if (BuildConfig.ENABLE_SKINS) return
        NativeBridge.resetEntropy()

        slow()
        rule.onNodeWithTag("tab_create").performClick()
        rule.waitForIdle()
        slow()

        rule.onNodeWithText("Create volume").performScrollTo().assertIsNotEnabled()
        scribbleUntilFull()
        rule.onNodeWithText("Create volume").performScrollTo().assertIsEnabled()
        slow()

        rule.onNodeWithText("Generate strong password").performScrollTo().assertIsEnabled()
        slow()
        rule.onAllNodesWithText("Generate strong password").onFirst().performClick()
        rule.waitForIdle()
        slow()
        rule.onNodeWithText("It is not saved", substring = true).assertIsDisplayed()

        rule.onNodeWithTag("copy_once").performScrollTo().performClick()
        rule.waitForIdle()
        slow()

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val clip = ctx.getSystemService(android.content.ClipboardManager::class.java)
        val secret = clip.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
        assertNotNull("Copy once must put the generated password on the clipboard", secret)
        assertEquals(64, secret!!.length)

        Thread.sleep(1_400)
        val notes = File(ctx.filesDir, "slow-human-notes.txt")
        notes.writeText(secret)
        assertEquals("paste into Notes", secret, notes.readText())
        slow()

        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 3")
            .close()
        Thread.sleep(8_000)

        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        assertNotNull(intent)
        intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        ctx.startActivity(intent)
        rule.waitForIdle()
        slow()

        rule.onNodeWithTag("tab_create").performClick()
        rule.waitForIdle()
        slow()
        rule.onNodeWithText(
            "Dismounted. Passwords, keyfiles in memory",
            substring = true
        ).assertDoesNotExist()
        rule.onNodeWithText("Generate strong password").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("copy_once").performScrollTo().performClick()
        rule.waitForIdle()
        val again = clip.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
        assertEquals("Create password must survive Home so the wizard can continue", secret, again)
        assertEquals(secret, notes.readText())
        slow()

        rule.onNodeWithTag("create_cipher").performScrollTo().performClick()
        rule.waitForIdle()
        slow()
        rule.onNodeWithText("AES").performClick()
        rule.waitForIdle()
        slow()

        rule.onNodeWithTag("create_kdf").performScrollTo().performClick()
        rule.waitForIdle()
        slow()
        rule.onNodeWithText("HMAC-SHA-256").performClick()
        rule.waitForIdle()
        slow()

        rule.onNodeWithTag("create_pim").performScrollTo().performTextReplacement("1")
        rule.waitForIdle()
        slow()
        rule.onNodeWithTag("create_filename").performScrollTo()
            .performTextReplacement("vacation.jpg")
        rule.waitForIdle()
        slow()

        val dir = File(ctx.cacheDir, "slow-basket").apply {
            deleteRecursively()
            mkdirs()
        }
        val photo = File(dir, "photo.jpg").apply { writeBytes(ByteArray(2 * 1024 * 1024) { it.toByte() }) }
        val note = File(dir, "memo.txt").apply { writeText("slow-human-note\n") }
        val lora = File(dir, "adapter.lora").apply { writeBytes(ByteArray(4096) { 0xA5.toByte() }) }

        rule.activity.testingAddBasketFiles(listOf(photo))
        rule.waitForIdle()
        rule.waitUntil(8_000) {
            rule.onAllNodesWithText("Volume will be at least 7 MiB", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("create_size").assert(hasText("7"))
        slow()

        rule.activity.testingAddBasketFiles(listOf(note, lora))
        rule.waitForIdle()
        rule.waitUntil(8_000) {
            rule.onAllNodesWithText("Volume will be at least 8 MiB", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Volume will be at least 8 MiB", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        rule.onNodeWithTag("create_size").performScrollTo().assert(hasText("8"))
        slow()

        rule.onNodeWithTag("tab_tools").performClick()
        rule.waitForIdle()
        slow()
        rule.onNodeWithText("Change volume password").performScrollTo().assertIsDisplayed()
        slow()
        rule.onNodeWithText("Set header key derivation algorithm").performScrollTo().assertIsDisplayed()
        slow()
        rule.onNodeWithText("Add/Remove keyfiles to/from volume").performScrollTo().assertIsDisplayed()
        slow()
        rule.onNodeWithText("Remove all keyfiles from volume").performScrollTo().assertIsDisplayed()
        slow()
        rule.onNodeWithText("Backup volume header").performScrollTo().assertIsDisplayed()
        slow()
        rule.onNodeWithText("Restore volume header").performScrollTo().assertIsDisplayed()
        slow()
        rule.onNodeWithText("Check for updates").assertDoesNotExist()

        rule.onNodeWithTag("tab_volume").performClick()
        rule.waitForIdle()
        slow()
        rule.onNodeWithText("Use backup header").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("compelled", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Panic wipe").assertIsDisplayed()
        rule.onNodeWithText("Create volume").assertDoesNotExist()

        rule.onNodeWithTag("tab_create").performClick()
        rule.waitForIdle()
        slow()
        rule.onNodeWithTag("create_filename").performScrollTo().assert(hasText("vacation.jpg"))
        rule.onNodeWithTag("create_size").performScrollTo().assert(hasText("8"))
        rule.onNodeWithText("Create volume").performScrollTo().assertIsEnabled()
        assertEquals(secret, notes.readText())
        Hardening.wipeDir(dir)
    }

    @Test
    fun nestedCreateMinimizeKeepsWizard() {
        if (BuildConfig.ENABLE_SKINS) return
        NativeBridge.resetEntropy()

        slow()
        rule.onNodeWithTag("tab_create").performClick()
        rule.waitForIdle()
        slow()
        scribbleUntilFull()
        slow()

        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.cacheDir, "slow-nested").apply {
            deleteRecursively()
            mkdirs()
        }
        val photo = File(dir, "photo.jpg").apply { writeBytes(ByteArray(2 * 1024 * 1024) { it.toByte() }) }
        rule.activity.testingAddBasketFiles(listOf(photo))
        rule.waitForIdle()
        rule.waitUntil(8_000) {
            rule.onAllNodesWithText("Volume will be at least 7 MiB", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        slow()

        rule.onAllNodesWithText("Generate strong password").onFirst().performScrollTo().performClick()
        rule.waitForIdle()
        slow()
        rule.onNodeWithTag("copy_once").performScrollTo().performClick()
        rule.waitForIdle()
        val clip = ctx.getSystemService(android.content.ClipboardManager::class.java)
        val outer = clip.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
        assertNotNull("outer Copy once", outer)
        assertEquals(64, outer!!.length)

        rule.onNodeWithText("Nested volume (VeraCrypt hidden volume)")
            .performScrollTo()
            .performClick()
        rule.waitForIdle()
        slow()
        rule.waitUntil(8_000) {
            rule.onAllNodesWithText("Generate nested password")
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.waitUntil(8_000) {
            rule.onAllNodesWithText("Volume will be at least 11 MiB", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("create_size").performScrollTo().assert(hasText("11"))
        rule.onNodeWithText("Generate nested password").performScrollTo().performClick()
        rule.waitForIdle()
        slow()
        rule.onNodeWithTag("copy_nested_once").performScrollTo().performClick()
        rule.waitForIdle()
        val nested = clip.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
        assertNotNull("nested Copy once", nested)
        assertEquals(64, nested!!.length)
        assertTrue("outer and nested passwords must differ", outer != nested)

        val notes = File(ctx.filesDir, "slow-human-nested-notes.txt")
        homePasteNotesAndReturn(ctx, outer + "\n" + nested, notes, lingerMs = 12_000)

        rule.onNodeWithTag("tab_create").performClick()
        rule.waitForIdle()
        slow()
        rule.onNodeWithText(
            "Dismounted. Passwords, keyfiles in memory",
            substring = true
        ).assertDoesNotExist()
        rule.onNodeWithText("Nested volume (VeraCrypt hidden volume)").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Generate nested password").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("copy_once").performScrollTo().performClick()
        rule.waitForIdle()
        val outerAgain = clip.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
        assertEquals("outer password must survive Home", outer, outerAgain)
        slow()
        rule.onNodeWithTag("copy_nested_once").performScrollTo().performClick()
        rule.waitForIdle()
        val nestedAgain = clip.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()
        assertEquals("nested password must survive Home", nested, nestedAgain)
        assertEquals(outer + "\n" + nested, notes.readText())
        rule.onNodeWithText("Volume will be at least 11 MiB", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        slow()

        rule.onNodeWithTag("create_cipher").performScrollTo().performClick()
        rule.waitForIdle()
        slow()
        rule.onNodeWithText("AES").performClick()
        rule.waitForIdle()
        slow()
        rule.onNodeWithTag("create_kdf").performScrollTo().performClick()
        rule.waitForIdle()
        slow()
        rule.onNodeWithText("HMAC-SHA-256").performClick()
        rule.waitForIdle()
        slow()
        rule.onNodeWithTag("create_pim").performScrollTo().performTextReplacement("1")
        rule.waitForIdle()
        slow()
        rule.onNodeWithTag("create_hidden_pim").performScrollTo().performTextReplacement("1")
        rule.waitForIdle()
        slow()
        rule.onNodeWithTag("create_hidden_size").performScrollTo().performTextClearance()
        rule.onNodeWithTag("create_hidden_size").performTextInput("2")
        rule.waitForIdle()
        slow()
        rule.onNodeWithTag("create_filename").performScrollTo().performTextReplacement("photos.jpg")
        rule.waitForIdle()
        slow()

        homePasteNotesAndReturn(ctx, notes.readText(), notes, lingerMs = 10_000)

        rule.onNodeWithTag("tab_create").performClick()
        rule.waitForIdle()
        slow()
        rule.onNodeWithText("Generate nested password").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("create_filename").performScrollTo().assert(hasText("photos.jpg"))
        rule.onNodeWithTag("create_pim").performScrollTo().assert(hasText("1"))
        rule.onNodeWithTag("create_hidden_pim").performScrollTo().assert(hasText("1"))
        rule.onNodeWithTag("create_hidden_size").performScrollTo().assert(hasText("2"))
        rule.onNodeWithText("HMAC-SHA-256").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Volume will be at least", substring = true).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("copy_once").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(outer, clip.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString())
        rule.onNodeWithTag("copy_nested_once").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(nested, clip.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString())
        rule.onNodeWithText("Create volume").performScrollTo().assertIsEnabled()
        assertEquals(outer + "\n" + nested, notes.readText())
        Hardening.wipeDir(dir)
    }

    private fun homePasteNotesAndReturn(
        ctx: android.content.Context,
        secret: String,
        notes: File,
        lingerMs: Long
    ) {
        Thread.sleep(1_200)
        notes.writeText(secret)
        assertEquals(secret, notes.readText())
        slow()
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 3")
            .close()
        Thread.sleep(lingerMs)
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        assertNotNull(intent)
        intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        ctx.startActivity(intent)
        rule.waitForIdle()
        slow()
    }

    private fun scribbleUntilFull() {
        rule.onNodeWithTag("entropy_pad").performScrollTo()
        repeat(70) { i ->
            rule.onNodeWithTag("entropy_pad").performTouchInput {
                swipe(
                    start = Offset(16f, 16f + (i % 10) * 6f),
                    end = Offset(width - 16f, height - 16f - (i % 7) * 8f),
                    durationMillis = 140
                )
            }
            Thread.sleep(90)
        }
        rule.waitUntil(20_000) {
            rule.onAllNodesWithText("100%").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun slow() {
        Thread.sleep(650)
        rule.waitForIdle()
    }
}
