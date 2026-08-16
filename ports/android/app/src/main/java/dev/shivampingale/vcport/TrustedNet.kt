package dev.shivampingale.vcport

import java.net.URI

/**
 * Hardcoded network policy. The app never listens. It never talks until the
 * user taps Check for updates, then only these HTTPS URLs, then disconnects.
 *
 * This is not a 0-day detector and not a backdoor. Fetched bytes are JSON
 * version strings only — never executed, never installed.
 */
object TrustedNet {
    const val WINDOW_MS = 20_000
    const val MAX_BODY = 64 * 1024
    const val CONNECT_MS = 8_000
    const val READ_MS = 8_000
    const val GITHUB_STATUS = "https://www.githubstatus.com/api/v2/status.json"

    fun allow(raw: String): Boolean {
        val u = try {
            URI(raw)
        } catch (_: Exception) {
            return false
        }
        if (u.scheme != "https") return false
        if (!u.userInfo.isNullOrEmpty()) return false
        if (u.port != -1 && u.port != 443) return false
        val host = u.host?.lowercase() ?: return false
        val path = u.path ?: return false
        return when (host) {
            "raw.githubusercontent.com" ->
                path.startsWith("/ShivamPingaleDev/Veracrypt_port/") &&
                    path.endsWith("/ports/version.json")
            "api.github.com" ->
                path == "/repos/veracrypt/VeraCrypt/releases/latest"
            "www.githubstatus.com" ->
                path == "/api/v2/status.json"
            else -> false
        }
    }
}
