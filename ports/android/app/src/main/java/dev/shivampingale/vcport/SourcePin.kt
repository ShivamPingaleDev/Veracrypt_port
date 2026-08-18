package dev.shivampingale.vcport

/**
 * Baked-in public source pin from ports/version.json (Gradle BuildConfig).
 *
 * A newer app is a new build from git. This APK never downloads or installs
 * VeraCrypt source, never patches itself, and never installs an update.
 *
 * Official VeraCrypt (hardcoded, not fetched by this APK):
 *   BuildConfig.UPSTREAM_GIT
 *   BuildConfig.UPSTREAM_RELEASES
 *
 * See ports/UPSTREAM.md. Live Check for updates is on experimental-biometrics.
 */
object SourcePin {
    val repo: String get() = BuildConfig.SOURCE_REPO
    val manifest: String get() = BuildConfig.SOURCE_MANIFEST
    val localVersion: String get() = BuildConfig.PORT_VERSION
    val upstreamVersion: String get() = BuildConfig.UPSTREAM_VERSION
    val upstreamCommit: String get() = BuildConfig.UPSTREAM_COMMIT
    val upstreamGit: String get() = BuildConfig.UPSTREAM_GIT
    val upstreamReleases: String get() = BuildConfig.UPSTREAM_RELEASES
    val upstreamTag: String get() = BuildConfig.UPSTREAM_TAG

    fun compare(a: String, b: String): Int {
        val pa = a.split('.', '-').mapNotNull { it.toIntOrNull() }
        val pb = b.split('.', '-').mapNotNull { it.toIntOrNull() }
        val n = maxOf(pa.size, pb.size)
        for (i in 0 until n) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /** VeraCrypt_1.26.29 → 1.26.29 */
    fun versionFromVeraCryptTag(tag: String): String {
        var t = tag.trim()
        for (prefix in listOf("VeraCrypt_", "VeraCrypt-", "VeraCrypt ")) {
            if (t.startsWith(prefix)) {
                t = t.removePrefix(prefix).trim()
                break
            }
        }
        return t.split(Regex("\\s+")).firstOrNull().orEmpty()
    }

    fun describeBuild(): String {
        val shortCommit = if (upstreamCommit.length >= 12) upstreamCommit.substring(0, 12) else upstreamCommit
        return "This build is VC Port $localVersion, VeraCrypt $upstreamVersion ($shortCommit, $upstreamTag). Source: $repo Official: $upstreamGit"
    }
}
