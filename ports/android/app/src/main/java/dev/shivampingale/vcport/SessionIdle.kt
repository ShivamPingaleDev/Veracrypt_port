package dev.shivampingale.vcport

/** Idle timeout. 0 = off. Home / screen lock still dismount. */
object SessionIdle {
    const val MAX_MINUTES = 24 * 60

    enum class Unit(val label: String, val minutesPer: Int) {
        Minutes("min", 1),
        Hours("h", 60)
    }

    fun toMinutes(amount: Int, unit: Unit): Int {
        if (amount <= 0) return 0
        val raw = amount.toLong() * unit.minutesPer
        return raw.coerceIn(0L, MAX_MINUTES.toLong()).toInt()
    }

    fun split(minutes: Int): Pair<Int, Unit> {
        val n = minutes.coerceIn(0, MAX_MINUTES)
        if (n >= 60 && n % 60 == 0) return (n / 60) to Unit.Hours
        return n to Unit.Minutes
    }

    fun label(minutes: Int): String = when (val n = minutes.coerceIn(0, MAX_MINUTES)) {
        0 -> "Off"
        1 -> "1 minute"
        else -> if (n % 60 == 0) {
            val h = n / 60
            if (h == 1) "1 hour" else "$h hours"
        } else {
            "$n minutes"
        }
    }
}
