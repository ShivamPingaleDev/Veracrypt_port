package dev.shivampingale.vcport

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI on a real activity. FLAG_SECURE stays on. Does not tap Panic wipe
 * and does not start an update-check HTTPS window.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityUiTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun chromeTabsAndContractCopy() {
        rule.onNodeWithText("VC Port").assertIsDisplayed()
        rule.onNodeWithText("Stay offline. F-Droid: no network.").assertIsDisplayed()
        rule.onNodeWithText("Panic wipe").assertIsDisplayed()
        rule.onNodeWithText("Share encrypted").assertIsDisplayed()
        rule.onNodeWithText("Working…").assertDoesNotExist()

        rule.onNodeWithText("Open volume").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Use backup header").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Read-only").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(
            "Protect hidden volume against damage caused by writing to outer volume"
        ).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("not unbreakable", substring = true).performScrollTo().assertIsDisplayed()

        rule.onNodeWithTag("tab_wrap").performClick()
        rule.onNodeWithText("Encrypt file").assertIsDisplayed()
        rule.onNodeWithText("Decrypt wrap").assertIsDisplayed()
        rule.onNodeWithText("Generate strong password").assertIsEnabled()

        rule.onNodeWithTag("tab_create").performClick()
        rule.onNodeWithText("Encryption Algorithm").assertIsDisplayed()
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
    }
}
