package dev.shivampingale.vcport

/** The only sentences the person sees for a save. The checks stay at the call site. */
object MountReport {
    fun saveFailure(failedLabels: List<String>): String? {
        if (failedLabels.isEmpty()) return null
        val which = if (failedLabels.size == 1) failedLabels[0] else failedLabels.size.toString()
        return "Not saved: $which. Original unchanged."
    }

    fun dismount(label: String, remaining: Int, hadSource: Boolean, saved: Boolean): String {
        if (hadSource && !saved) return "Dismounted $label. Not saved."
        if (hadSource && saved) {
            return if (remaining == 0) "Saved. Dismounted $label."
            else "Saved. Dismounted $label. $remaining still open."
        }
        return if (remaining == 0) "Dismounted $label."
        else "Dismounted $label. $remaining still open."
    }

    fun lockStatus(finishStatus: String, failedLabels: List<String>): String {
        val fail = saveFailure(failedLabels) ?: return finishStatus
        return "$finishStatus $fail"
    }
}
