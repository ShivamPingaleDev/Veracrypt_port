package dev.shivampingale.vcport

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile

/**
 * Visible Compose UI: Scan USB (empty on emulator), then a fake MBR disk
 * image is injected so the partition button and Open run on screen.
 * Not a physical OTG stick. Not /proc/self/fd.
 */
@RunWith(AndroidJUnit4::class)
class FakeUsbUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun scanThenFakePartitionOpenShowsMounted() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        rule.activity.testingSkipSystemPickers = true
        NativeBridge.resetEntropy()
        NativeBridge.addEntropy(ByteArray(256) { it.toByte() })

        val work = File(ctx.cacheDir, "fake-usb-ui").apply {
            deleteRecursively()
            mkdirs()
        }
        val pw = "vcport-otg-usb-sim-ok"
        val pim = 1
        val part = File(work, "part-a.bin")
        assertEquals(
            0,
            NativeBridge.createVolume(
                part.absolutePath, pw, pim, 2L shl 20, "AES", "HMAC-SHA-512",
                emptyArray(), "", 0, 0L, emptyArray(), "FAT"
            )
        )
        val seed = NativeBridge.openVolume(part.absolutePath, pw, pim, false, emptyArray(), false)
        assertTrue(NativeBridge.isOpen(seed))
        assertEquals(0, NativeBridge.mkdir(seed, "/", "PHOTOS"))
        val note = File(work, "NOTE.TXT").apply { writeText("usb-ui-ok\n") }
        assertEquals(0, NativeBridge.importFile(seed, "PHOTOS", note.absolutePath, "NOTE.TXT"))
        NativeBridge.closeVolume(seed)

        val start = 2048L * 512L
        val disk = File(work, "usb-a.bin")
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

        rule.onNodeWithTag("tab_volume").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Scan USB disks").performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithText("See OTG Master", substring = true).onFirst().assertIsDisplayed()
        holdUi(2_000)

        rule.onNodeWithTag("scan_usb").performScrollTo().performClick()
        rule.waitForIdle()
        waitStatus("No USB mass-storage device", 15_000)
        rule.onNodeWithText("No USB mass-storage device", substring = true).assertIsDisplayed()
        holdUi(2_000)

        rule.activity.testingInjectFakeUsb(disk, start, part.length(), "MBR partition 1")
        rule.waitForIdle()
        waitStatus("Simulated USB disk", 8_000)
        rule.onNodeWithTag("otg_partition").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("MBR partition 1", substring = true).assertIsDisplayed()
        holdUi(2_000)

        rule.onNodeWithTag("otg_partition").performClick()
        rule.waitForIdle()
        waitStatus("Selected MBR partition 1", 8_000)
        val slotReady = (0..7).any { OtgBlockStore.isReady("/vcport-otg-dev/$it") }
        assertTrue("partition bind must be /vcport-otg-dev/N", slotReady)
        assertFalse(OtgBlockStore.isPath("/proc/self/fd/3"))

        rule.onNodeWithTag("volume_password").performScrollTo().performTextReplacement(pw)
        rule.onNodeWithTag("volume_pim").performScrollTo().performTextReplacement("1")
        rule.onNodeWithTag("open_volume").performScrollTo().performClick()
        rule.waitUntil(90_000) {
            rule.onAllNodesWithText("PHOTOS").fetchSemanticsNodes().isNotEmpty() ||
                rule.activity.testingEntryNames().any { it.equals("PHOTOS", ignoreCase = true) }
        }
        rule.onNodeWithTag("tab_mounted").performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText("PHOTOS").onFirst().assertIsDisplayed()
        holdUi(2_000)
        rule.onAllNodesWithText("PHOTOS").onFirst().performClick()
        rule.waitUntil(12_000) {
            rule.onAllNodesWithText("NOTE.TXT").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithText("NOTE.TXT").onFirst().assertIsDisplayed()
        holdUi(2_000)
        rule.onAllNodesWithText("NOTE.TXT").onFirst().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("view_in_app").performClick()
        rule.waitUntil(30_000) {
            rule.onAllNodesWithText("usb-ui-ok", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("in_app_preview").assertIsDisplayed()
        rule.onNodeWithText("usb-ui-ok", substring = true).assertIsDisplayed()
        holdUi(3_000)
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

    private fun holdUi(ms: Long) {
        Thread.sleep(ms)
        rule.waitForIdle()
    }
}
