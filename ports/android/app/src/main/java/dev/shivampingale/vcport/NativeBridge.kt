package dev.shivampingale.vcport

object NativeBridge {
    init {
        System.loadLibrary("vcport")
    }

    external fun openVolume(path: String, password: String, pim: Int, backup: Boolean): Long
    external fun closeVolume(handle: Long)
    external fun volumeSize(handle: Long): Long
    external fun listRoot(handle: Long): Array<String>
}
