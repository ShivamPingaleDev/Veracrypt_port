package dev.shivampingale.vcport

/**
 * F-Droid flavor: no network permission. VeraCrypt source updates arrive as a
 * new F-Droid build of this git tree, not as a download inside the app.
 */
object UpdateChecker {
    val LOCAL_VERSION: String get() = SourcePin.localVersion

    fun check(): SourcePin.CheckResult {
        error("F-Droid build has no network")
    }
}
