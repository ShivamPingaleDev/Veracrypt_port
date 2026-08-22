package dev.shivampingale.vcport

/** Idle timeout choices. 0 = off. Home / screen lock still dismount. */
object SessionIdle {
    val MINUTES = listOf(0, 1, 5, 15)

    fun label(minutes: Int): String = when (minutes) {
        0 -> "Off"
        1 -> "1 minute"
        5 -> "5 minutes"
        15 -> "15 minutes"
        else -> "$minutes minutes"
    }
}
