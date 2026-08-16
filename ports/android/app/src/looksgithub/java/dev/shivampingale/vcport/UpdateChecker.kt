package dev.shivampingale.vcport

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Looks GitHub flavor. User tap opens a ≤20s HTTPS window to three allowlisted
 * URLs, then disconnects. No listeners, no redirects, no APK/src fetch.
 * Skins stay on. Same applicationId as production.
 */
object UpdateChecker {
    val LOCAL_VERSION: String get() = SourcePin.localVersion
    private val SHA256 = Regex("^[0-9a-fA-F]{64}$")

    fun check(): SourcePin.CheckResult {
        val deadline = System.nanoTime() + TrustedNet.WINDOW_MS * 1_000_000L
        val warnings = mutableListOf<String>()
        githubStatus(deadline)?.let { warnings.add(it) }
        val body = get(SourcePin.manifest, deadline)
        val json = JSONObject(body)
        val remote = json.optString("port_version")
        if (remote.isEmpty()) error("bad manifest")
        val sha = json.optString("android_apk_sha256")
        if (sha.isNotEmpty() && !SHA256.matches(sha)) error("bad manifest")
        val url = json.optString("android_url").ifEmpty { json.optString("download_url") }
        if (url.isNotEmpty() && !url.startsWith("https://")) error("bad manifest")
        val remoteCommit = json.optString("upstream_commit")
        val newer = SourcePin.compare(remote, LOCAL_VERSION) > 0
        val (officialNewer, officialVersion, officialWarn) = officialRelease(deadline)
        if (officialWarn.isNotEmpty()) warnings.add(officialWarn)
        return SourcePin.CheckResult(
            newer = newer,
            remoteVersion = remote,
            notes = json.optString("notes"),
            downloadUrl = url,
            apkSha256 = sha,
            remoteUpstreamCommit = remoteCommit,
            sourceMoved = !newer && remoteCommit.isNotEmpty() && remoteCommit != SourcePin.upstreamCommit,
            officialNewer = officialNewer,
            officialVersion = officialVersion,
            sourceDegraded = warnings.isNotEmpty(),
            sourceWarning = warnings.joinToString(" ")
        )
    }

    private fun remaining(deadline: Long): Int {
        val ms = ((deadline - System.nanoTime()) / 1_000_000L).toInt()
        if (ms <= 0) error("update window closed")
        return minOf(ms, TrustedNet.CONNECT_MS)
    }

    private fun get(raw: String, deadline: Long): String {
        if (!TrustedNet.allow(raw)) error("host not on the allowlist")
        val timeout = remaining(deadline)
        val connection = (URL(raw).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = timeout
            readTimeout = minOf(remaining(deadline), TrustedNet.READ_MS)
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "VCPort-OfflineUpdate/$LOCAL_VERSION")
            setRequestProperty("Connection", "close")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            if (code in 300..399) error("redirect refused")
            if (code != 200) error("HTTP $code")
            connection.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(4096)
                var total = 0
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > TrustedNet.MAX_BODY) error("response too large")
                    out.write(buf, 0, n)
                }
                return String(out.toByteArray(), Charsets.UTF_8)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun githubStatus(deadline: Long): String? {
        return try {
            val json = JSONObject(get(TrustedNet.GITHUB_STATUS, deadline))
            val indicator = json.optJSONObject("status")?.optString("indicator").orEmpty()
            val description = json.optJSONObject("status")?.optString("description").orEmpty()
            if (indicator == "major" || indicator == "critical") {
                "GitHub status is $indicator ($description). Treat this check as unverified."
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun officialRelease(deadline: Long): Triple<Boolean, String, String> {
        return try {
            val json = JSONObject(get(SourcePin.upstreamReleases, deadline))
            val ver = SourcePin.versionFromVeraCryptTag(json.optString("tag_name"))
            if (ver.isEmpty()) Triple(false, "", "Official VeraCrypt tag was empty or unexpected.")
            else Triple(SourcePin.compare(ver, SourcePin.upstreamVersion) > 0, ver, "")
        } catch (_: Exception) {
            Triple(false, "", "Official VeraCrypt GitHub was unreachable. Do not trust a missing pin.")
        }
    }
}
