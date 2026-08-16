package dev.shivampingale.vcport

object NativeBridge {
    init {
        System.loadLibrary("vcport")
    }

    external fun openVolume(
        path: String,
        password: String,
        pim: Int,
        backup: Boolean,
        keyfiles: Array<String>
    ): Long
    external fun closeVolume(handle: Long)
    external fun volumeSize(handle: Long): Long
    external fun listRoot(handle: Long): Array<String>
    external fun listDir(handle: Long, path: String): Array<String>
    external fun exportFile(handle: Long, name: String, destPath: String): Int
    external fun wrapFile(srcPath: String, destPath: String, password: String, originalName: String): Int
    external fun unwrapFile(srcPath: String, destDir: String, password: String): String?
    external fun isWrap(path: String): Boolean
    external fun generatePassword(length: Int): String?
}
