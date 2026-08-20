package dev.shivampingale.vcport

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * One Android UI walk: 9-step session, fake USB Open + View in app, in-app
 * preview. Does not tap Panic wipe or Check for updates. Not a physical
 * OTG stick. Run both phones with ports/scripts/run-ui-walk.sh.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    AppInterfaceSessionTest::class,
    FakeUsbUiTest::class,
    InAppPreviewTest::class,
)
class UiWalkSuite
