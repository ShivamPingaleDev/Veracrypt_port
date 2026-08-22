package dev.shivampingale.vcport

import android.content.Intent
import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * Person session on the emulator through the app UI (10 phases): basket + nested volume,
 * nested folders, save wipes secrets, fill files, leave and reopen, decrypt,
 * mount several, Copy to volume / Move to volume, hidden volume files, header backup/restore, KDF
 * change, add/remove password and keyfiles, then idle / read-only banner / in-volume hash / PIM estimate.
 * Does not tap Panic wipe. Does not start Check for updates.
 */
@RunWith(AndroidJUnit4::class)
class AppInterfaceSessionTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun createSaveWipeReopenMountTransferAndSecurity() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        rule.activity.testingSkipSystemPickers = true
        NativeBridge.resetEntropy()
        assertSecure()

        val work = File(ctx.filesDir, "ui-session").apply {
            deleteRecursively()
            mkdirs()
        }
        val basketDir = File(work, "basket").apply { mkdirs() }
        val photoBytes = ByteArray(64 * 1024) { it.toByte() }
        val memoText = "basket-memo-ok\n"
        val loraBytes = ByteArray(4096) { 0xA5.toByte() }
        val zipBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)
        val photo = File(basketDir, "PHOTO.JPG").apply { writeBytes(photoBytes) }
        val memo = File(basketDir, "MEMO.TXT").apply { writeText(memoText) }
        val lora = File(basketDir, "ADAPTER.BIN").apply { writeBytes(loraBytes) }
        val zip = File(basketDir, "NOTES.ZIP").apply { writeBytes(zipBytes) }
        val extraFill = File(work, "FILL.TXT").apply { writeText("copied-in-after-open\n") }
        val extraFill2 = File(work, "MORE.BIN").apply { writeBytes(ByteArray(512) { 7 }) }
        val nestedNote = File(work, "NEST.TXT").apply { writeText("nested-folder-ok\n") }
        val hiddenNote = File(work, "HIDDEN.TXT").apply { writeText("hidden-volume-ok\n") }
        val changedPassword = "basket-password-changed-ok"

        rule.onNodeWithTag("tab_create").performClick()
        rule.waitForIdle()
        scribbleUntilFull()

        rule.activity.testingAddBasketFiles(listOf(photo, memo, lora, zip))
        rule.waitForIdle()
        rule.waitUntil(12_000) {
            rule.onAllNodesWithText("Volume will be at least", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("PHOTO.JPG").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("MEMO.TXT").performScrollTo().assertIsDisplayed()

        rule.onNodeWithTag("create_cipher").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("AES(Twofish(Serpent))").onLast().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("create_kdf").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("HMAC-SHA-512").onLast().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("create_pim").performScrollTo().performTextReplacement("1")
        rule.waitForIdle()
        rule.onNodeWithTag("create_filename").performScrollTo()
            .performTextReplacement("basket.jpg")
        rule.waitForIdle()

        rule.onAllNodesWithText("Generate strong password").onFirst().performScrollTo().performClick()
        rule.waitForIdle()
        val basketPassword = passwordAfterCopyOnce(hidden = false)
        File(work, "basket-password.txt").writeText(basketPassword)

        rule.onNodeWithTag("create_generate_keyfile").performScrollTo().performClick()
        rule.waitForIdle()
        val keyfiles = rule.activity.testingSnapshotKeyfiles(File(work, "keys"))
        assertTrue("Generate keyfile and add must produce a file", keyfiles.isNotEmpty())

        clickCreateVolume()
        waitStatus("from the basket into the volume", 180_000)
        val basketDest = File(work, "basket.jpg")
        assertTrue(rule.activity.testingFinishCreateSave(basketDest))
        rule.waitForIdle()

        rule.onNodeWithText("Create secrets were wiped", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("volume_password").assert(hasText(""))
        rule.onNodeWithTag("volume_pim").assert(hasText("0"))
        rule.onNodeWithTag("tab_create").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("create_password").performScrollTo().assert(hasText(""))
        rule.onNodeWithTag("create_pim").performScrollTo().assert(hasText("0"))
        rule.onNodeWithText("No files in the basket.").performScrollTo().assertIsDisplayed()
        assertSecure()

        scribbleUntilFull()
        rule.onAllNodesWithText("Generate strong password").onFirst().performScrollTo().performClick()
        rule.waitForIdle()
        val outerPassword = passwordAfterCopyOnce(hidden = false)
        assertTrue(outerPassword != basketPassword)

        rule.onNodeWithTag("create_hidden").performScrollTo().performClick()
        rule.waitForIdle()
        rule.waitUntil(8_000) {
            rule.onAllNodesWithText("Generate nested password").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Generate nested password").performScrollTo().performClick()
        rule.waitForIdle()
        val nestedPassword = passwordAfterCopyOnce(hidden = true)
        assertTrue(nestedPassword != outerPassword)
        File(work, "nested-passwords.txt").writeText("$outerPassword\n$nestedPassword")

        rule.onNodeWithTag("create_cipher").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("AES(Twofish(Serpent))").onLast().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("create_kdf").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("HMAC-SHA-512").onLast().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("create_pim").performScrollTo().performTextReplacement("1")
        rule.onNodeWithTag("create_hidden_pim").performScrollTo().performTextReplacement("1")
        rule.onNodeWithTag("create_hidden_size").performScrollTo().performTextReplacement("4")
        rule.onNodeWithTag("create_hidden_size").performScrollTo().performTextReplacement("2")
        rule.onNodeWithTag("create_size").performScrollTo().performTextReplacement("8")
        rule.onNodeWithTag("create_filename").performScrollTo()
            .performTextReplacement("photos.jpg")
        rule.waitForIdle()

        scribbleUntilFull()
        clickCreateVolume()
        waitStatus("Nested volume is inside", 240_000)
        val nestedDest = File(work, "photos.jpg")
        assertTrue(rule.activity.testingFinishCreateSave(nestedDest))
        rule.waitForIdle()
        rule.onNodeWithText("Create secrets were wiped", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("volume_password").assert(hasText(""))
        rule.onNodeWithTag("tab_create").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("create_password").performScrollTo().assert(hasText(""))
        rule.onNodeWithTag("create_hidden_password").performScrollTo().assert(hasText(""))

        homeAndReturn(ctx)

        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        rule.activity.testingSkipSystemPickers = true
        assertSecure()
        rule.onNodeWithText("Stay offline. This build has no network.").assertIsDisplayed()
        rule.onNodeWithText("Check for updates").assertDoesNotExist()

        rule.onNodeWithTag("tab_volume").performClick()
        rule.waitForIdle()
        rule.activity.testingSelectContainer(basketDest)
        rule.waitForIdle()
        rule.onNodeWithTag("volume_password").performScrollTo()
            .performTextReplacement(basketPassword)
        rule.onNodeWithTag("volume_pim").performScrollTo().performTextReplacement("1")
        rule.activity.testingAddKeyfiles(keyfiles)
        rule.waitForIdle()
        rule.onNodeWithText("TrueCrypt Mode").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("open_volume").performScrollTo().performClick()
        waitStatus("Mounted in this app", 180_000)
        rule.onNodeWithTag("tab_volume").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("volume_password").assert(hasText(""))
        rule.onNodeWithTag("volume_pim").assert(hasText("0"))

        rule.onNodeWithTag("tab_tools").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tools_volume_properties").performScrollTo().performClick()
        rule.waitUntil(15_000) {
            val info = rule.activity.testingVolumeInfo() ?: rule.activity.testingStatus()
            info.contains("AES(Twofish(Serpent))") && info.contains("HMAC-SHA-512")
        }

        rule.onNodeWithTag("tab_mounted").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("Mounted in this app").onFirst().assertIsDisplayed()
        assertTrue(rule.activity.testingEntryNames().contains("BASKET.sha256"))
        assertTrue(rule.activity.testingEntryNames().any { it.contains("MEMO", ignoreCase = true) })
        assertTrue(rule.activity.testingEntryNames().any { it.contains("PHOTO", ignoreCase = true) })

        val memoOut = File(work, "out-memo.txt")
        val memoName = rule.activity.testingEntryNames().first { it.contains("MEMO", ignoreCase = true) }
        assertTrue(rule.activity.testingExportNamed(memoName, memoOut))
        assertEquals(memoText, memoOut.readText())
        val photoName = rule.activity.testingEntryNames().first { it.contains("PHOTO", ignoreCase = true) }
        val photoOut = File(work, "out-photo.jpg")
        assertTrue(rule.activity.testingExportNamed(photoName, photoOut))
        assertEquals(sha256(photoBytes), sha256(photoOut.readBytes()))

        rule.activity.testingImportFiles(listOf(extraFill, extraFill2))
        rule.waitUntil(30_000) {
            val names = rule.activity.testingEntryNames()
            names.any { it.contains("FILL", ignoreCase = true) } &&
                names.any { it.contains("MORE", ignoreCase = true) }
        }

        rule.onNodeWithTag("new_folder").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("name_prompt").performTextReplacement("INBOX")
        rule.onNodeWithTag("name_prompt_ok").performClick()
        waitStatus("Created folder INBOX", 30_000)
        rule.activity.testingOpenDir("INBOX")
        rule.waitUntil(15_000) {
            !rule.activity.testingEntryNames().any { it.contains("FILL", ignoreCase = true) }
        }
        rule.activity.testingImportFiles(listOf(nestedNote))
        rule.waitUntil(30_000) {
            rule.activity.testingEntryNames().any { it.contains("NEST", ignoreCase = true) }
        }
        val nestOut = File(work, "from-inbox-nest.txt")
        val nestName = rule.activity.testingEntryNames().first { it.contains("NEST", ignoreCase = true) }
        assertTrue(rule.activity.testingExportNamed(nestName, nestOut))
        assertEquals("nested-folder-ok\n", nestOut.readText())
        rule.activity.testingGoParent()
        rule.waitUntil(15_000) {
            rule.activity.testingEntryNames().any { it.contains("INBOX", ignoreCase = true) }
        }

        rule.onNodeWithTag("mount_slot_1").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("open_volume_form").assertIsDisplayed()
        rule.onNodeWithTag("use_backup_header").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("read_only").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("protect_hidden").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("add_keyfiles").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("TrueCrypt Mode").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("mounted_open_cancel").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("new_folder").assertIsDisplayed()

        rule.onNodeWithTag("tab_volume").performClick()
        rule.waitForIdle()
        rule.activity.testingClearKeyfiles()
        rule.activity.testingSelectContainer(nestedDest)
        rule.waitForIdle()
        rule.onNodeWithTag("volume_password").performScrollTo()
            .performTextReplacement(outerPassword)
        rule.onNodeWithTag("volume_pim").performScrollTo().performTextReplacement("1")
        rule.onNodeWithTag("protect_hidden").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("hidden_protect_password").performScrollTo()
            .performTextReplacement(nestedPassword)
        rule.onNodeWithTag("hidden_protect_pim").performScrollTo().performTextReplacement("1")
        rule.onNodeWithTag("open_volume").performScrollTo().performClick()
        waitStatus("volumes mounted", 180_000)

        rule.onNodeWithTag("tab_mounted").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("2 volumes mounted").onFirst().assertIsDisplayed()
        rule.onNodeWithTag("mount_slot_0").performScrollTo().performClick()
        rule.waitForIdle()
        val names = rule.activity.testingEntryNames()
        val fillIdx = names.indexOfFirst { it.contains("FILL", ignoreCase = true) }
        assertTrue("FILL was imported into the basket volume", fillIdx >= 0)
        val moveName = names[fillIdx]
        assertTrue(
            "copy FILL into photos.jpg: ${rule.activity.testingStatus()}",
            rule.activity.testingTransferNamed(setOf(moveName), nestedDest.name, false)
        )
        waitStatus("Copied 1 file(s) into", 60_000)

        val moreName = rule.activity.testingEntryNames().first { it.contains("MORE", ignoreCase = true) }
        assertTrue(
            "move MORE into photos.jpg: ${rule.activity.testingStatus()}",
            rule.activity.testingTransferNamed(setOf(moreName), nestedDest.name, true)
        )
        waitStatus("Moved 1 file(s) into", 60_000)
        assertFalse(rule.activity.testingEntryNames().any { it.equals(moreName, ignoreCase = true) })

        rule.onNodeWithTag("mount_slot_1").performScrollTo().performClick()
        rule.waitForIdle()
        rule.waitUntil(15_000) {
            val listed = rule.activity.testingEntryNames()
            listed.any { it.contains("FILL", ignoreCase = true) } &&
                listed.any { it.contains("MORE", ignoreCase = true) }
        }
        val fillOut = File(work, "from-nested-fill.txt")
        val fillName = rule.activity.testingEntryNames().first { it.contains("FILL", ignoreCase = true) }
        assertTrue(rule.activity.testingExportNamed(fillName, fillOut))
        assertEquals("copied-in-after-open\n", fillOut.readText())

        val moreOut = File(work, "from-nested-more.bin")
        val moreOnPhotos = rule.activity.testingEntryNames().first { it.contains("MORE", ignoreCase = true) }
        assertTrue(rule.activity.testingExportNamed(moreOnPhotos, moreOut))
        assertEquals(512, moreOut.length())

        assertTrue(
            "copy MORE back into basket.jpg: ${rule.activity.testingStatus()}",
            rule.activity.testingTransferNamed(setOf(moreOnPhotos), basketDest.name, false)
        )
        waitStatus("Copied 1 file(s) into", 60_000)

        rule.onNodeWithTag("tab_tools").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Dismount").performClick()
        waitStatus("Dismounted", 30_000)

        rule.activity.testingClearKeyfiles()
        rule.activity.testingSelectContainer(nestedDest)
        rule.waitForIdle()
        rule.onNodeWithTag("tab_volume").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("volume_password").performScrollTo()
            .performTextReplacement(nestedPassword)
        rule.onNodeWithTag("volume_pim").performScrollTo().performTextReplacement("1")
        rule.onNodeWithTag("open_volume").performScrollTo().performClick()
        waitStatus("Mounted in this app", 180_000)

        rule.onNodeWithTag("tab_mounted").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("new_folder").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("name_prompt").performTextReplacement("SECRET")
        rule.onNodeWithTag("name_prompt_ok").performClick()
        waitStatus("Created folder SECRET", 30_000)
        rule.activity.testingOpenDir("SECRET")
        rule.waitUntil(15_000) {
            !rule.activity.testingEntryNames().any { it.contains("SECRET", ignoreCase = true) }
        }
        rule.activity.testingImportFiles(listOf(hiddenNote))
        rule.waitUntil(30_000) {
            rule.activity.testingEntryNames().any { it.contains("HIDDEN", ignoreCase = true) }
        }
        val hiddenOut = File(work, "from-hidden.txt")
        val hiddenName = rule.activity.testingEntryNames().first { it.contains("HIDDEN", ignoreCase = true) }
        assertTrue(rule.activity.testingExportNamed(hiddenName, hiddenOut))
        assertEquals("hidden-volume-ok\n", hiddenOut.readText())

        rule.onNodeWithTag("tab_tools").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Dismount").performClick()
        waitStatus("Dismounted", 30_000)

        rule.activity.testingSelectContainer(basketDest)
        rule.waitForIdle()
        rule.onNodeWithTag("tab_volume").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("volume_password").performScrollTo()
            .performTextReplacement(basketPassword)
        rule.onNodeWithTag("volume_pim").performScrollTo().performTextReplacement("1")
        rule.activity.testingAddKeyfiles(keyfiles)
        rule.waitForIdle()
        rule.onNodeWithTag("tab_tools").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tools_backup_header").performScrollTo().performClick()
        waitStatus("Header backup ready", 60_000)
        val headerBak = File(work, "basket-header.bak")
        assertTrue(rule.activity.testingCopyHeaderBackup(headerBak))

        rule.onNodeWithTag("tools_new_password").performScrollTo()
            .performTextReplacement(changedPassword)
        rule.onNodeWithTag("tools_new_pim").performScrollTo().performTextReplacement("1")
        rule.onNodeWithTag("tools_change_password").performScrollTo().performClick()
        waitStatus("Changed volume password", 60_000)

        rule.onNodeWithTag("tab_volume").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("volume_password").performScrollTo()
            .performTextReplacement(basketPassword)
        rule.onNodeWithTag("open_volume").performScrollTo().performClick()
        waitStatus("Wrong password", 60_000)
        rule.onNodeWithTag("volume_password").performScrollTo()
            .performTextReplacement(changedPassword)
        rule.onNodeWithTag("volume_pim").performScrollTo().performTextReplacement("1")
        rule.onNodeWithTag("open_volume").performScrollTo().performClick()
        waitStatus("Mounted in this app", 180_000)
        val memoAfterChange = File(work, "memo-after-password.txt")
        val memoAfterName = rule.activity.testingEntryNames().first { it.contains("MEMO", ignoreCase = true) }
        assertTrue(rule.activity.testingExportNamed(memoAfterName, memoAfterChange))
        assertEquals(memoText, memoAfterChange.readText())
        assertTrue(rule.activity.testingEntryNames().any { it.contains("INBOX", ignoreCase = true) })

        rule.onNodeWithTag("tab_tools").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Dismount").performClick()
        waitStatus("Dismounted", 30_000)

        rule.onNodeWithTag("tab_volume").performClick()
        rule.waitForIdle()
        rule.activity.testingSelectContainer(basketDest)
        rule.onNodeWithTag("volume_password").performScrollTo()
            .performTextReplacement(changedPassword)
        rule.onNodeWithTag("volume_pim").performScrollTo().performTextReplacement("1")
        rule.activity.testingAddKeyfiles(keyfiles)
        rule.waitForIdle()
        rule.onNodeWithTag("tab_tools").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tools_header_kdf").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithText("HMAC-SHA-256").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tools_set_kdf").performScrollTo().performClick()
        waitStatus("Set header key derivation algorithm to HMAC-SHA-256", 60_000)

        rule.onNodeWithTag("tab_volume").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("volume_password").performScrollTo()
            .performTextReplacement(changedPassword)
        rule.onNodeWithTag("volume_pim").performScrollTo().performTextReplacement("1")
        rule.onNodeWithTag("open_volume").performScrollTo().performClick()
        waitStatus("Mounted in this app", 180_000)
        rule.onNodeWithTag("tab_tools").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tools_volume_properties").performScrollTo().performClick()
        rule.waitUntil(15_000) {
            val info = rule.activity.testingVolumeInfo() ?: rule.activity.testingStatus()
            info.contains("HMAC-SHA-256")
        }

        rule.onNodeWithTag("tools_keyfile_name").performScrollTo()
            .performTextReplacement("extra.bin")
        rule.onNodeWithTag("tools_generate_keyfile").performScrollTo().performClick()
        waitStatus("Generated and added", 15_000)
        val extraKeys = rule.activity.testingSnapshotKeyfiles(File(work, "keys-plus"))
        assertTrue(extraKeys.size >= 2)
        rule.onNodeWithTag("tools_apply_keyfiles").performScrollTo().performClick()
        waitStatus("Applied the current keyfile list", 60_000)

        rule.onNodeWithTag("tab_volume").performClick()
        rule.waitForIdle()
        rule.activity.testingClearKeyfiles()
        rule.activity.testingAddKeyfiles(keyfiles)
        rule.onNodeWithTag("volume_password").performScrollTo()
            .performTextReplacement(changedPassword)
        rule.onNodeWithTag("volume_pim").performScrollTo().performTextReplacement("1")
        rule.onNodeWithTag("open_volume").performScrollTo().performClick()
        waitStatus("Wrong password", 60_000)
        rule.activity.testingClearKeyfiles()
        rule.activity.testingAddKeyfiles(extraKeys)
        rule.waitForIdle()
        rule.onNodeWithTag("open_volume").performScrollTo().performClick()
        waitStatus("Mounted in this app", 180_000)

        rule.onNodeWithTag("tab_tools").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Dismount").performClick()
        waitStatus("Dismounted", 30_000)

        rule.onNodeWithTag("tab_volume").performClick()
        rule.waitForIdle()
        rule.activity.testingSelectContainer(basketDest)
        rule.onNodeWithTag("volume_password").performScrollTo()
            .performTextReplacement(changedPassword)
        rule.onNodeWithTag("volume_pim").performScrollTo().performTextReplacement("1")
        rule.activity.testingAddKeyfiles(extraKeys)
        rule.waitForIdle()
        rule.onNodeWithTag("tab_tools").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tools_remove_all_keyfiles").performScrollTo().performClick()
        waitStatus("Removed all keyfiles from volume", 60_000)

        rule.onNodeWithTag("tab_volume").performClick()
        rule.waitForIdle()
        rule.activity.testingClearKeyfiles()
        rule.waitForIdle()
        rule.onNodeWithTag("volume_password").performScrollTo()
            .performTextReplacement(changedPassword)
        rule.onNodeWithTag("volume_pim").performScrollTo().performTextReplacement("1")
        rule.onNodeWithTag("open_volume").performScrollTo().performClick()
        waitStatus("Mounted in this app", 180_000)

        rule.onNodeWithTag("tab_tools").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Dismount").performClick()
        waitStatus("Dismounted", 30_000)

        rule.onNodeWithTag("tab_volume").performClick()
        rule.waitForIdle()
        rule.activity.testingSelectContainer(basketDest)
        rule.onNodeWithTag("volume_password").performScrollTo()
            .performTextReplacement(basketPassword)
        rule.onNodeWithTag("volume_pim").performScrollTo().performTextReplacement("1")
        rule.activity.testingAddKeyfiles(keyfiles)
        rule.waitForIdle()
        rule.activity.testingRestoreHeader(headerBak)
        waitStatus("Restored volume header", 60_000)

        rule.onNodeWithTag("volume_password").performScrollTo()
            .performTextReplacement(changedPassword)
        rule.onNodeWithTag("open_volume").performScrollTo().performClick()
        waitStatus("Wrong password", 60_000)
        rule.onNodeWithTag("volume_password").performScrollTo()
            .performTextReplacement(basketPassword)
        rule.onNodeWithTag("volume_pim").performScrollTo().performTextReplacement("1")
        rule.onNodeWithTag("open_volume").performScrollTo().performClick()
        waitStatus("Mounted in this app", 180_000)
        rule.onNodeWithTag("tab_tools").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tools_volume_properties").performScrollTo().performClick()
        rule.waitUntil(15_000) {
            val info = rule.activity.testingVolumeInfo() ?: rule.activity.testingStatus()
            info.contains("AES(Twofish(Serpent))") && info.contains("HMAC-SHA-512")
        }
        val memoRestored = File(work, "memo-after-restore.txt")
        val memoRestoredName = rule.activity.testingEntryNames().first { it.contains("MEMO", ignoreCase = true) }
        assertTrue(rule.activity.testingExportNamed(memoRestoredName, memoRestored))
        assertEquals(memoText, memoRestored.readText())
        rule.activity.testingOpenDir("INBOX")
        rule.waitUntil(15_000) {
            rule.activity.testingEntryNames().any { it.contains("NEST", ignoreCase = true) }
        }

        rule.onNodeWithTag("tab_tools").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Dismount").performClick()
        waitStatus("Dismounted", 30_000)

        rule.onNodeWithTag("tab_volume").performClick()
        rule.waitForIdle()
        rule.activity.testingSelectContainer(basketDest)
        rule.onNodeWithTag("volume_password").performScrollTo()
            .performTextReplacement(basketPassword)
        rule.onNodeWithTag("volume_pim").performScrollTo().performTextReplacement("1")
        rule.activity.testingAddKeyfiles(keyfiles)
        rule.waitForIdle()
        rule.onNodeWithTag("tab_tools").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tools_restore_embedded").performScrollTo().performClick()
        waitStatus("Restored from embedded backup header", 60_000)

        rule.onNodeWithTag("tab_volume").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("volume_password").performScrollTo()
            .performTextReplacement(basketPassword)
        rule.onNodeWithTag("volume_pim").performScrollTo().performTextReplacement("1")
        rule.onNodeWithTag("use_backup_header").performScrollTo().performClick()
        rule.onNodeWithTag("read_only").performScrollTo().performClick()
        rule.onNodeWithTag("open_volume").performScrollTo().performClick()
        waitStatus("Mounted in this app", 180_000)

        rule.onNodeWithTag("tab_mounted").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("wipe_free_space").performScrollTo().performClick()
        waitStatus("Read-only volumes refuse", 30_000)
        rule.onNodeWithTag("read_only_banner").assertIsDisplayed()

        val memoHashName = rule.activity.testingEntryNames().first { it.contains("MEMO", ignoreCase = true) }
        rule.activity.testingSelectNames(setOf(memoHashName))
        rule.waitForIdle()
        rule.activity.testingHashSelected(memoHashName)
        waitStatus("SHA-256 in volume", 60_000)

        rule.onNodeWithTag("tab_tools").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("tools_pim_estimate").performScrollTo().performClick()
        waitStatus("header iterations", 8_000)
        rule.onNodeWithTag("pim_estimate_result").assertIsDisplayed()
        assertTrue(rule.activity.testingPimEstimate().contains("500,000") || rule.activity.testingPimEstimate().contains("1,000"))

        rule.onNodeWithTag("tab_volume").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("idle_amount").performScrollTo().performTextReplacement("1")
        rule.waitForIdle()
        rule.activity.testingFireIdleTimeout()
        waitStatus("Idle timeout", 15_000)

        assertSecure()
        hideIme()
        rule.onNodeWithText("Check for updates").assertDoesNotExist()
        rule.waitUntil(8_000) {
            rule.onAllNodesWithTag("panic_wipe").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("panic_wipe").assertExists()
        Hardening.wipeDir(work)
    }

    private fun passwordAfterCopyOnce(hidden: Boolean): String {
        rule.waitUntil(12_000) {
            val pw = if (hidden) {
                rule.activity.testingCreateHiddenPassword()
            } else {
                rule.activity.testingCreatePassword()
            }
            pw.length == 64
        }
        val tag = if (hidden) "copy_nested_once" else "copy_once"
        // Generate copy says "Copy once"; wait for the Copied-once status, not that substring.
        val copied = if (hidden) "Copied nested" else "Copied once"
        repeat(3) {
            rule.onNodeWithTag(tag).performScrollTo().performClick()
            rule.waitForIdle()
            if (rule.activity.testingStatus().contains(copied, ignoreCase = true)) {
                return@repeat
            }
            Thread.sleep(400)
        }
        waitStatus(copied, 8_000)
        val pw = if (hidden) {
            rule.activity.testingCreateHiddenPassword()
        } else {
            rule.activity.testingCreatePassword()
        }
        assertEquals("Generate must leave a 64-character password in the Create field", 64, pw.length)
        return pw
    }

    private fun hideIme() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 111")
            .close()
        rule.waitForIdle()
    }

    private fun clickCreateVolume() {
        hideIme()
        rule.onNodeWithTag("create_volume").performScrollTo().assertIsEnabled().performClick()
        rule.waitForIdle()
        // Create can finish before the overlay is sampled; success copy also contains "Created".
        try {
            rule.waitUntil(12_000) {
                val s = rule.activity.testingStatus()
                s.contains("Creating", ignoreCase = true) ||
                    s.contains("Created", ignoreCase = true) ||
                    s.contains("from the basket", ignoreCase = true)
            }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError(
                "Timed out waiting for create to start or finish. Last status: '${rule.activity.testingStatus()}'",
                e
            )
        }
        rule.waitForIdle()
    }

    private fun waitStatus(substring: String, timeoutMs: Long) {
        try {
            rule.waitUntil(timeoutMs) {
                rule.activity.testingStatus().contains(substring, ignoreCase = true)
            }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            throw AssertionError(
                "Timed out waiting for status containing '$substring'. Last status: '${rule.activity.testingStatus()}'",
                e
            )
        }
        rule.waitForIdle()
    }

    private fun homeAndReturn(ctx: android.content.Context) {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 3")
            .close()
        Thread.sleep(5_000)
        val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        assertNotNull(intent)
        intent!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        ctx.startActivity(intent)
        rule.waitForIdle()
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

    private fun assertSecure() {
        assertTrue(
            "FLAG_SECURE must stay on",
            rule.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        )
    }

    private fun sha256(bytes: ByteArray): String {
        val d = MessageDigest.getInstance("SHA-256").digest(bytes)
        return d.joinToString("") { "%02x".format(it) }
    }
}
