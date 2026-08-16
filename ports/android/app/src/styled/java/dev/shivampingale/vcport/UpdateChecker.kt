package dev.shivampingale.vcport

/**
 * Looks APK: same applicationId as production, no network. Not a separate package.
 * Updates are a new APK, not a download inside the app.
 */
object UpdateChecker {
    val LOCAL_VERSION: String get() = SourcePin.localVersion

    fun check(): SourcePin.CheckResult {
        error("Looks build has no network")
    }
}
