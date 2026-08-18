package dev.shivampingale.vcport

/**
 * Master builds stay offline. Live Check for updates is on experimental-biometrics.
 * A newer app is a new APK from git or F-Droid, not a download inside this process.
 */
object UpdateChecker {
    val LOCAL_VERSION: String get() = SourcePin.localVersion

    fun check(): Nothing {
        error("This build has no network")
    }
}
