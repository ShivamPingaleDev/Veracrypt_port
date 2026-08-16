package dev.shivampingale.vcport

object NativeBridge {
    const val LIST_UI_MAX = 1024
    init {
        System.loadLibrary("vcport")
        startRuntime()
    }
    private external fun startRuntime()

    external fun openVolume(
        path: String,
        password: String,
        pim: Int,
        backup: Boolean,
        keyfiles: Array<String>,
        readOnly: Boolean,
        protectHidden: Boolean = false,
        hiddenPassword: String = "",
        hiddenPim: Int = 0
    ): Long
    external fun closeVolume(handle: Long)
    external fun volumeSize(handle: Long): Long
    external fun listRoot(handle: Long): Array<String>
    external fun listDir(handle: Long, path: String, offset: Int): Array<String>
    fun listDir(handle: Long, path: String): Array<String> = listDir(handle, path, 0)
    external fun exportFile(handle: Long, name: String, destPath: String): Int
    external fun importFile(handle: Long, destDir: String, srcPath: String, destName: String): Int
    external fun deleteFile(handle: Long, path: String): Int
    external fun mkdir(handle: Long, parentDir: String, name: String): Int
    external fun rmdir(handle: Long, path: String): Int
    external fun renameFile(handle: Long, path: String, newName: String): Int
    external fun wipeFreeSpace(handle: Long): Int
    external fun wrapFile(srcPath: String, destPath: String, password: String, originalName: String): Int
    external fun unwrapFile(srcPath: String, destDir: String, password: String): String?
    external fun isWrap(path: String): Boolean
    external fun generatePassword(length: Int): String?
    external fun createVolume(
        path: String,
        password: String,
        pim: Int,
        sizeBytes: Long,
        cipher: String,
        kdf: String,
        keyfiles: Array<String>,
        hiddenPassword: String,
        hiddenPim: Int,
        hiddenSizeBytes: Long,
        hiddenKeyfiles: Array<String>
    ): Int
    external fun addEntropy(samples: ByteArray)
    external fun entropyPercent(): Int
    external fun resetEntropy()
    external fun changeHeader(
        path: String,
        password: String,
        pim: Int,
        keyfiles: Array<String>,
        backup: Boolean,
        newPassword: String,
        newPim: Int,
        newKdf: String,
        newKeyfiles: Array<String>
    ): Int
    external fun backupHeaders(
        volumePath: String,
        backupPath: String,
        password: String,
        pim: Int,
        keyfiles: Array<String>
    ): Int
    external fun restoreHeaders(
        volumePath: String,
        backupPath: String,
        password: String,
        pim: Int,
        keyfiles: Array<String>
    ): Int
    external fun generateKeyfile(path: String, size: Int): Int
    external fun volumeInfo(handle: Long): String?
    external fun protectionTriggered(handle: Long): Boolean
    external fun benchmark(): String?
    external fun testVectors(): Int
    external fun resetProgress()
    external fun setProgress(percent: Int, phase: String)
    external fun progressPercent(): Int
    external fun progressPhase(): String

    /** Live volume pointer. Error codes from openVolume are 0 and -1..-6. */
    fun isOpen(handle: Long): Boolean = handle < -6L || handle > 0L

    val CIPHERS = listOf(
        "AES",
        "Serpent",
        "Twofish",
        "Camellia",
        "Kuznyechik",
        "AES(Twofish)",
        "AES(Twofish(Serpent))",
        "Camellia(Kuznyechik)",
        "Camellia(Serpent)",
        "Kuznyechik(AES)",
        "Kuznyechik(Serpent(Camellia))",
        "Kuznyechik(Twofish)",
        "Serpent(AES)",
        "Serpent(Twofish(AES))",
        "Twofish(Serpent)"
    )
    val KDFS = listOf(
        "HMAC-SHA-512",
        "HMAC-SHA-256",
        "HMAC-BLAKE2s-256",
        "HMAC-Whirlpool",
        "HMAC-Streebog",
        "Argon2"
    )
    const val DEFAULT_CIPHER = "AES(Twofish(Serpent))"
    const val DEFAULT_KDF = "HMAC-SHA-512"
}
