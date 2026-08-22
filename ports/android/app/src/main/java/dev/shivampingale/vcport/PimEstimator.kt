package dev.shivampingale.vcport

/**
 * Honest PIM helper. Not Benchmark. Not a time-to-crack estimate.
 * Matches VeraCrypt non-system volumes: HMAC KDFs use 500,000 iterations
 * at PIM 0, or PIM × 1,000. Argon2id uses a different cost; we do not
 * pretend it is the same formula.
 */
object PimEstimator {
    fun hmacIterations(pim: Int): Int = if (pim <= 0) 500_000 else pim * 1_000

    fun describe(kdf: String, pimText: String): String {
        val pim = pimText.filter { it.isDigit() }.toIntOrNull() ?: 0
        val argon = kdf.contains("Argon2", ignoreCase = true)
        return if (argon) {
            "Argon2id. PIM changes Argon2 time cost. This is not seconds-to-open and not a crack-time estimate."
        } else {
            val n = hmacIterations(pim)
            val pimBit = if (pim <= 0) "PIM 0 (VeraCrypt default)" else "PIM $pim"
            "$kdf: about ${"%,d".format(n)} header iterations ($pimBit). Not a crack-time estimate. Benchmark measures cipher speed, not this."
        }
    }
}
