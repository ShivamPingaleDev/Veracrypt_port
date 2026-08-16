package dev.shivampingale.vcport

/**
 * Looks package: same crypto as F-Droid, no network. Not the store build.
 * Updates are a new APK, not a download inside the app.
 */
object UpdateChecker {
    val LOCAL_VERSION: String get() = SourcePin.localVersion

    fun check(): SourcePin.CheckResult {
        error("Looks build has no network")
    }
}
