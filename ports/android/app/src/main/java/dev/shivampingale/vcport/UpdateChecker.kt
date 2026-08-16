package dev.shivampingale.vcport

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    const val LOCAL_VERSION = "0.1.0"
    private const val MANIFEST =
        "https://raw.githubusercontent.com/ShivamPingaleDev/Veracrypt_port/master/ports/version.json"

    data class Result(
        val newer: Boolean,
        val remoteVersion: String,
        val notes: String,
        val downloadUrl: String
    )

    fun check(): Result {
        val connection = (URL(MANIFEST).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "VCPort-OfflineUpdate/0.1")
            setRequestProperty("Connection", "close")
        }
        try {
            val body = connection.inputStream.bufferedReader().readText()
            val json = JSONObject(body)
            val remote = json.optString("port_version")
            return Result(
                newer = compare(remote, LOCAL_VERSION) > 0,
                remoteVersion = remote,
                notes = json.optString("notes"),
                downloadUrl = json.optString("android_url").ifEmpty { json.optString("download_url") }
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun compare(a: String, b: String): Int {
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
}
