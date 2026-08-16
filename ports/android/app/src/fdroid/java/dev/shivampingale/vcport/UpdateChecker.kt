package dev.shivampingale.vcport

object UpdateChecker {
    const val LOCAL_VERSION = "0.3.0"

    data class Result(
        val newer: Boolean,
        val remoteVersion: String,
        val notes: String,
        val downloadUrl: String
    )

    fun check(): Result {
        error("F-Droid build has no network")
    }
}
