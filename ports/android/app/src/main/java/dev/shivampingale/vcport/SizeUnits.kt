package dev.shivampingale.vcport

enum class SizeUnit(val label: String, val factor: Long) {
    KiB("KiB", 1024L),
    MiB("MiB", 1024L * 1024L),
    GiB("GiB", 1024L * 1024L * 1024L);

    companion object {
        val labels: List<String> = entries.map { it.label }
        fun fromLabel(label: String): SizeUnit = entries.find { it.label == label } ?: MiB
    }
}

object SizeUnits {
    const val MIN_VOLUME = 2L * 1024L * 1024L
    const val MAX_VOLUME = 64L * 1024L * 1024L * 1024L
    const val FAT_MAX_FILE = 0xFFFFFFFFL

    fun toBytes(amount: Long, unit: SizeUnit): Long {
        if (amount <= 0L) return 0L
        val factor = unit.factor
        if (amount > Long.MAX_VALUE / factor) return Long.MAX_VALUE
        return amount * factor
    }

    fun formatBytes(bytes: Long): String {
        if (bytes >= 1024L * 1024L * 1024L) {
            val g = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            return "${"%.2f".format(g)} GiB"
        }
        if (bytes >= 1024L * 1024L) {
            val m = (bytes + (1L shl 20) - 1) / (1L shl 20)
            return "$m MiB"
        }
        val k = (bytes + 1023) / 1024
        return "$k KiB"
    }
}
