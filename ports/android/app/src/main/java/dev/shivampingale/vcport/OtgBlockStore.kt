package dev.shivampingale.vcport

/**
 * JNI target for overlay File.cpp USB slots. Paths are /vcport-otg-dev/N,
 * never /proc/self/fd.
 */
object OtgBlockStore {
    data class Slot(
        val scsi: OtgScsiDevice?,
        val file: java.io.RandomAccessFile? = null,
        val byteOffset: Long,
        val byteLength: Long,
        val label: String
    )

    private val slots = arrayOfNulls<Slot>(8)

    @Synchronized
    fun bindFile(file: java.io.File, byteOffset: Long, byteLength: Long, label: String): String {
        val index = slots.indexOfFirst { it == null }
        if (index < 0) throw IllegalStateException("No USB slot left")
        val raf = java.io.RandomAccessFile(file, "rw")
        slots[index] = Slot(null, raf, byteOffset, byteLength, label)
        return pathFor(index)
    }

    @Synchronized
    fun bind(scsi: OtgScsiDevice, candidate: OtgCandidate): String {
        val index = slots.indexOfFirst { it == null }
        if (index < 0) {
            throw IllegalStateException("No USB slot left")
        }
        slots[index] = Slot(scsi, null, candidate.byteOffset, candidate.byteLength, candidate.label)
        return pathFor(index)
    }

    fun pathFor(slot: Int): String = "/vcport-otg-dev/$slot"

    fun isPath(path: String): Boolean = path.startsWith("/vcport-otg-dev/")

    @Synchronized
    fun isReady(path: String): Boolean {
        val slot = slotOf(path) ?: return false
        return slots[slot] != null
    }

    @Synchronized
    fun label(path: String): String? = slotOf(path)?.let { slots[it]?.label }

    @Synchronized
    fun release(path: String) {
        val slot = slotOf(path) ?: return
        slots[slot]?.scsi?.close()
        try {
            slots[slot]?.file?.close()
        } catch (_: Exception) {
        }
        slots[slot] = null
    }

    @Synchronized
    fun releaseAll() {
        for (i in slots.indices) {
            slots[i]?.scsi?.close()
            try {
                slots[i]?.file?.close()
            } catch (_: Exception) {
            }
            slots[i] = null
        }
    }

    private fun slotOf(path: String): Int? {
        if (!isPath(path) || path.length != "/vcport-otg-dev/0".length) return null
        val n = path.last() - '0'
        return n.takeIf { it in 0..7 }
    }

    @JvmStatic
    fun nativeReady(slot: Int): Boolean = synchronized(this) { slot in slots.indices && slots[slot] != null }

    @JvmStatic
    fun nativeSize(slot: Int): Long = synchronized(this) { slots.getOrNull(slot)?.byteLength ?: -1L }

    @JvmStatic
    fun nativeSectorSize(slot: Int): Int = synchronized(this) {
        slots.getOrNull(slot)?.scsi?.blockSize ?: 512
    }

    @JvmStatic
    fun nativeRead(slot: Int, offset: Long, buf: ByteArray): Int {
        val bound = synchronized(this) { slots.getOrNull(slot) } ?: return -1
        if (offset < 0 || offset >= bound.byteLength) return -1
        val n = minOf(buf.size.toLong(), bound.byteLength - offset).toInt()
        return try {
            bound.scsi?.read(bound.byteOffset + offset, buf, 0, n)
                ?: bound.file?.let { raf ->
                    synchronized(raf) {
                        raf.seek(bound.byteOffset + offset)
                        raf.read(buf, 0, n)
                    }
                } ?: -1
        } catch (_: Exception) {
            -1
        }
    }

    @JvmStatic
    fun nativeWrite(slot: Int, offset: Long, buf: ByteArray): Int {
        val bound = synchronized(this) { slots.getOrNull(slot) } ?: return -1
        if (offset < 0 || offset >= bound.byteLength) return -1
        val n = minOf(buf.size.toLong(), bound.byteLength - offset).toInt()
        return try {
            bound.scsi?.write(bound.byteOffset + offset, buf, 0, n)
                ?: bound.file?.let { raf ->
                    synchronized(raf) {
                        raf.seek(bound.byteOffset + offset)
                        raf.write(buf, 0, n)
                        n
                    }
                } ?: -1
        } catch (_: Exception) {
            -1
        }
    }
}
