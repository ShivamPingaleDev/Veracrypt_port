package dev.shivampingale.vcport

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.withLock
import java.util.concurrent.locks.ReentrantLock

/**
 * USB Mass Storage Bulk-Only + SCSI READ(10)/WRITE(10).
 * Independent of OTG Master; same userspace USB idea. Apache-2.0.
 */
class OtgScsiDevice private constructor(
    private val connection: UsbDeviceConnection,
    private val iface: UsbInterface,
    private val bulkIn: UsbEndpoint,
    private val bulkOut: UsbEndpoint
) {
    val blockSize: Int
    val blockCount: Long
    val sizeBytes: Long
        get() = blockCount * blockSize
    private val lock = ReentrantLock()
    private var tag = 1

    init {
        val cap = scsiIn(byteArrayOf(0x25, 0, 0, 0, 0, 0, 0, 0, 0, 0), 8)
        val buf = ByteBuffer.wrap(cap).order(ByteOrder.BIG_ENDIAN)
        val lastLba = buf.int.toLong() and 0xffffffffL
        val size = buf.int
        if (size <= 0) throw IOException("USB READ CAPACITY block size")
        blockSize = size
        blockCount = lastLba + 1
    }

    fun read(offset: Long, dst: ByteArray, dstOff: Int = 0, length: Int = dst.size - dstOff): Int {
        if (length <= 0) return 0
        lock.withLock {
            var done = 0
            while (done < length) {
                val abs = offset + done
                val lba = abs / blockSize
                val skip = (abs % blockSize).toInt()
                val chunk = minOf(length - done, blockSize - skip)
                val block = ByteArray(blockSize)
                scsiRead10(lba, 1, block)
                System.arraycopy(block, skip, dst, dstOff + done, chunk)
                done += chunk
            }
            return done
        }
    }

    fun write(offset: Long, src: ByteArray, srcOff: Int = 0, length: Int = src.size - srcOff): Int {
        if (length <= 0) return 0
        lock.withLock {
            var done = 0
            while (done < length) {
                val abs = offset + done
                val lba = abs / blockSize
                val skip = (abs % blockSize).toInt()
                val chunk = minOf(length - done, blockSize - skip)
                val block = ByteArray(blockSize)
                if (skip != 0 || chunk != blockSize) {
                    scsiRead10(lba, 1, block)
                }
                System.arraycopy(src, srcOff + done, block, skip, chunk)
                scsiWrite10(lba, 1, block)
                done += chunk
            }
            return done
        }
    }

    fun close() {
        try {
            connection.releaseInterface(iface)
        } catch (_: Exception) {
        }
        try {
            connection.close()
        } catch (_: Exception) {
        }
    }

    private fun scsiRead10(lba: Long, blocks: Int, dst: ByteArray) {
        val cdb = ByteArray(10)
        cdb[0] = 0x28
        putLba(cdb, lba)
        cdb[8] = blocks.toByte()
        val data = scsiIn(cdb, blocks * blockSize)
        System.arraycopy(data, 0, dst, 0, minOf(dst.size, data.size))
    }

    private fun scsiWrite10(lba: Long, blocks: Int, src: ByteArray) {
        val cdb = ByteArray(10)
        cdb[0] = 0x2a
        putLba(cdb, lba)
        cdb[8] = blocks.toByte()
        scsiOut(cdb, src.copyOf(blocks * blockSize))
    }

    private fun putLba(cdb: ByteArray, lba: Long) {
        cdb[2] = ((lba ushr 24) and 0xff).toByte()
        cdb[3] = ((lba ushr 16) and 0xff).toByte()
        cdb[4] = ((lba ushr 8) and 0xff).toByte()
        cdb[5] = (lba and 0xff).toByte()
    }

    private fun nextTag(): Int {
        tag += 1
        if (tag == 0) tag = 1
        return tag
    }

    private fun scsiIn(cdb: ByteArray, dataLen: Int): ByteArray {
        val tag = nextTag()
        sendCbw(tag, dataLen, directionIn = true, cdb = cdb)
        val data = ByteArray(dataLen)
        if (dataLen > 0) {
            val n = connection.bulkTransfer(bulkIn, data, dataLen, TIMEOUT_MS)
            if (n < dataLen) throw IOException("USB SCSI data in ($n < $dataLen)")
        }
        readCsw(tag)
        return data
    }

    private fun scsiOut(cdb: ByteArray, data: ByteArray) {
        val tag = nextTag()
        sendCbw(tag, data.size, directionIn = false, cdb = cdb)
        if (data.isNotEmpty()) {
            val n = connection.bulkTransfer(bulkOut, data, data.size, TIMEOUT_MS)
            if (n < data.size) throw IOException("USB SCSI data out")
        }
        readCsw(tag)
    }

    private fun sendCbw(tag: Int, dataLen: Int, directionIn: Boolean, cdb: ByteArray) {
        val cbw = ByteArray(31)
        val buf = ByteBuffer.wrap(cbw).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(0x43425355)
        buf.putInt(tag)
        buf.putInt(dataLen)
        buf.put(if (directionIn) 0x80.toByte() else 0x00)
        buf.put(0)
        buf.put(cdb.size.toByte())
        for (i in cdb.indices) cbw[15 + i] = cdb[i]
        val n = connection.bulkTransfer(bulkOut, cbw, 31, TIMEOUT_MS)
        if (n != 31) throw IOException("USB CBW")
    }

    private fun readCsw(tag: Int) {
        val csw = ByteArray(13)
        val n = connection.bulkTransfer(bulkIn, csw, 13, TIMEOUT_MS)
        if (n != 13) throw IOException("USB CSW")
        val buf = ByteBuffer.wrap(csw).order(ByteOrder.LITTLE_ENDIAN)
        if (buf.int != 0x53425355) throw IOException("USB CSW signature")
        if (buf.int != tag) throw IOException("USB CSW tag")
        buf.int
        val status = csw[12].toInt() and 0xff
        if (status != 0) throw IOException("USB SCSI status $status")
    }

    companion object {
        private const val TIMEOUT_MS = 8000

        fun open(manager: UsbManager, device: UsbDevice): OtgScsiDevice {
            val iface = massStorageInterface(device)
                ?: throw IOException("Not a USB mass-storage device")
            val connection = manager.openDevice(device)
                ?: throw IOException("Could not open USB device")
            if (!connection.claimInterface(iface, true)) {
                connection.close()
                throw IOException("Could not claim USB interface")
            }
            var bulkIn: UsbEndpoint? = null
            var bulkOut: UsbEndpoint? = null
            for (i in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(i)
                if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                if (ep.direction == UsbConstants.USB_DIR_IN) bulkIn = ep
                else bulkOut = ep
            }
            if (bulkIn == null || bulkOut == null) {
                connection.releaseInterface(iface)
                connection.close()
                throw IOException("USB mass-storage endpoints missing")
            }
            return OtgScsiDevice(connection, iface, bulkIn, bulkOut)
        }

        fun massStorageInterface(device: UsbDevice): UsbInterface? {
            for (i in 0 until device.interfaceCount) {
                val iface = device.getInterface(i)
                if (iface.interfaceClass == UsbConstants.USB_CLASS_MASS_STORAGE &&
                    iface.interfaceSubclass == 6 &&
                    iface.interfaceProtocol == 80
                ) {
                    return iface
                }
            }
            return null
        }

        fun isMassStorage(device: UsbDevice): Boolean = massStorageInterface(device) != null
    }
}
