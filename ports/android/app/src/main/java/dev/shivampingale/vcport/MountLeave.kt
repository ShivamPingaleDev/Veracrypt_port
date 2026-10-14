package dev.shivampingale.vcport

/** Words for a person who already left. Panic does not use this. */
object MountLeave {
    const val KEPT = "Dismounted. Create form kept."

    fun status(wasOpen: Boolean, failedLabels: List<String>, statusAlreadyPanic: Boolean): String? {
        if (!wasOpen || statusAlreadyPanic) return null
        val fail = MountReport.saveFailure(failedLabels) ?: return KEPT
        return "$KEPT $fail"
    }
}
