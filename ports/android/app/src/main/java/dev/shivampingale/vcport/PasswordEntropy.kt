package dev.shivampingale.vcport

import kotlin.math.log2

/**
 * Bits of entropy for a secret typed or generated in this app.
 * Generator alphabet matches `vc_generate_password` (no 0/O/1/l/I).
 * NIST SP 800-63B treats 256-bit as very high; this is an estimate, not a proof.
 */
object PasswordEntropy {
    const val GENERATOR_ALPHABET =
        "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*-_=+"

    fun bits(password: String): Int {
        if (password.isEmpty()) return 0
        val pool = poolSize(password).coerceAtLeast(1)
        return (password.length * log2(pool.toDouble())).toInt()
    }

    fun label(password: String): String {
        val b = bits(password)
        return when {
            password.isEmpty() ->
                "Entropy: none."
            password.length < 16 ->
                "Entropy: about $b bits — too short."
            b >= 256 ->
                "Entropy: about $b bits."
            b >= 80 ->
                "Entropy: about $b bits."
            else ->
                "Entropy: about $b bits — weak."
        }
    }

    private fun poolSize(password: String): Int {
        if (password.all { it in GENERATOR_ALPHABET }) return GENERATOR_ALPHABET.length
        var pool = 0
        if (password.any { it in 'A'..'Z' }) pool += 26
        if (password.any { it in 'a'..'z' }) pool += 26
        if (password.any { it.isDigit() }) pool += 10
        if (password.any { !it.isLetterOrDigit() }) pool += 32
        return pool.coerceAtLeast(password.toSet().size)
    }
}
