package dev.shivampingale.vcport

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class OtgCandidate(
    val label: String,
    val byteOffset: Long,
    val byteLength: Long
)

/** MBR / GPT start offsets. Does not auto-mount. */
object OtgPartitions {
    fun probe(scsi: OtgScsiDevice): List<OtgCandidate> {
        val out = mutableListOf<OtgCandidate>()
        out.add(OtgCandidate("Whole disk", 0L, scsi.sizeBytes))
        val sector0 = ByteArray(512)
        if (scsi.read(0, sector0) < 512) return out
        if (sector0[510] != 0x55.toByte() || sector0[511] != 0xAA.toByte()) return out
        val type1 = sector0[450].toInt() and 0xff
        if (type1 == 0xEE) {
            parseGpt(scsi, out)
        } else {
            parseMbr(sector0, scsi.blockSize.toLong().coerceAtLeast(512L), scsi.sizeBytes, out)
        }
        return out.distinctBy { it.byteOffset }
    }

    private fun parseMbr(sector0: ByteArray, sector: Long, diskSize: Long, out: MutableList<OtgCandidate>) {
        for (i in 0 until 4) {
            val off = 446 + i * 16
            val type = sector0[off + 4].toInt() and 0xff
            if (type == 0) continue
            val lba = u32le(sector0, off + 8)
            val sectors = u32le(sector0, off + 12)
            if (lba == 0L || sectors == 0L) continue
            val start = lba * sector
            val len = minOf(sectors * sector, (diskSize - start).coerceAtLeast(0L))
            if (start >= diskSize || len < 64L * 1024L) continue
            out.add(OtgCandidate("MBR partition ${i + 1}", start, len))
        }
    }

    private fun parseGpt(scsi: OtgScsiDevice, out: MutableList<OtgCandidate>) {
        val hdr = ByteArray(scsi.blockSize.coerceAtLeast(92))
        if (scsi.read(scsi.blockSize.toLong(), hdr) < 92) return
        if (String(hdr, 0, 8, Charsets.US_ASCII) != "EFI PART") return
        val buf = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN)
        val partLba = buf.getLong(72)
        val partCount = buf.getInt(80)
        val partSize = buf.getInt(84)
        if (partLba <= 0L || partCount <= 0 || partSize < 128 || partCount > 128) return
        val table = ByteArray(partCount * partSize)
        if (scsi.read(partLba * scsi.blockSize, table) < table.size) return
        for (i in 0 until partCount) {
            val e = i * partSize
            if (table.copyOfRange(e, e + 16).all { it == 0.toByte() }) continue
            val first = ByteBuffer.wrap(table, e + 32, 8).order(ByteOrder.LITTLE_ENDIAN).long
            val last = ByteBuffer.wrap(table, e + 40, 8).order(ByteOrder.LITTLE_ENDIAN).long
            if (last < first) continue
            val start = first * scsi.blockSize
            val len = (last - first + 1) * scsi.blockSize
            if (len < 64L * 1024L) continue
            out.add(OtgCandidate("GPT partition ${i + 1}", start, len))
        }
    }

    private fun u32le(b: ByteArray, off: Int): Long {
        return (b[off].toLong() and 0xff) or
            ((b[off + 1].toLong() and 0xff) shl 8) or
            ((b[off + 2].toLong() and 0xff) shl 16) or
            ((b[off + 3].toLong() and 0xff) shl 24)
    }
}
