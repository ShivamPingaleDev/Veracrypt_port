# VeraCrypt src files compiled into libvc_mobile.
# When syncing a new VeraCrypt release, run scripts/check-upstream-layout.sh
# and add any new cipher/volume/platform units here (or explicitly skip them).

set(VC_CRYPTO
	${VC_SRC}/Crypto/Aescrypt.c
	${VC_SRC}/Crypto/Aeskey.c
	${VC_SRC}/Crypto/Aestab.c
	${VC_SRC}/Crypto/blake2s.c
	${VC_SRC}/Crypto/SerpentFast.c
	${VC_SRC}/Crypto/Sha2.c
	${VC_SRC}/Crypto/Twofish.c
	${VC_SRC}/Crypto/Whirlpool.c
	${VC_SRC}/Crypto/Camellia.c
	${VC_SRC}/Crypto/Streebog.c
	${VC_SRC}/Crypto/kuznyechik.c
	${VC_SRC}/Crypto/cpu.c
	${VC_SRC}/Crypto/jitterentropy-base.c
	${VC_SRC}/Crypto/Argon2/src/blake2/blake2b.c
	${VC_SRC}/Crypto/Argon2/src/argon2.c
	${VC_SRC}/Crypto/Argon2/src/core.c
	${VC_SRC}/Crypto/Argon2/src/ref.c
	${VC_SRC}/Crypto/Argon2/src/selftest.c
)

# Pick extra crypto by ABI, not by the host CPU. Android Gradle sets
# ANDROID_ABI per slice. iOS may set CMAKE_OSX_ARCHITECTURES.
set(_vc_cpu "${CMAKE_SYSTEM_PROCESSOR}")
if(DEFINED ANDROID_ABI AND ANDROID_ABI)
	set(_vc_cpu "${ANDROID_ABI}")
elseif(CMAKE_OSX_ARCHITECTURES)
	set(_vc_cpu "${CMAKE_OSX_ARCHITECTURES}")
endif()

set(_vc_arm64 FALSE)
set(_vc_x64 FALSE)
set(_vc_x86 FALSE)
if(_vc_cpu MATCHES "arm64-v8a" OR _vc_cpu MATCHES "^(aarch64|arm64)$")
	set(_vc_arm64 TRUE)
endif()
if(_vc_cpu MATCHES "x86_64" OR _vc_cpu MATCHES "amd64" OR _vc_cpu MATCHES "AMD64")
	set(_vc_x64 TRUE)
endif()
if(_vc_cpu MATCHES "(^|;)x86(;|$)" OR _vc_cpu MATCHES "i.86")
	set(_vc_x86 TRUE)
endif()
# Mixed slices (e.g. sim arm64+x86_64) stay on portable C.
if(_vc_arm64 AND (_vc_x64 OR _vc_x86))
	set(_vc_arm64 FALSE)
	set(_vc_x64 FALSE)
	set(_vc_x86 FALSE)
endif()

if(_vc_arm64)
	list(APPEND VC_CRYPTO
		${VC_SRC}/Crypto/Aes_hw_armv8.c
		${VC_SRC}/Crypto/sha256_armv8.c
	)
	set_source_files_properties(
		${VC_SRC}/Crypto/Aes_hw_armv8.c
		${VC_SRC}/Crypto/sha256_armv8.c
		PROPERTIES COMPILE_FLAGS "-march=armv8-a+crypto"
	)
elseif(_vc_x64)
	list(APPEND VC_CRYPTO
		${VC_SRC}/Crypto/Argon2/src/opt_sse2.c
		${VC_SRC}/Crypto/Argon2/src/opt_avx2.c
	)
elseif(_vc_x86)
	list(APPEND VC_CRYPTO
		${VC_SRC}/Crypto/Argon2/src/opt_sse2.c
	)
endif()

set(VC_VOLUME
	${VC_SRC}/Volume/Cipher.cpp
	${VC_SRC}/Volume/EncryptionAlgorithm.cpp
	${VC_SRC}/Volume/EncryptionMode.cpp
	${VC_SRC}/Volume/EncryptionModeXTS.cpp
	${VC_SRC}/Volume/EncryptionTest.cpp
	${VC_SRC}/Volume/EncryptionThreadPool.cpp
	${VC_SRC}/Volume/Hash.cpp
	${VC_SRC}/Volume/Keyfile.cpp
	${VC_SRC}/Volume/Pkcs5Kdf.cpp
	${VC_SRC}/Volume/Volume.cpp
	${VC_SRC}/Volume/VolumeException.cpp
	${VC_SRC}/Volume/VolumeHeader.cpp
	${VC_SRC}/Volume/VolumeInfo.cpp
	${VC_SRC}/Volume/VolumeLayout.cpp
	${VC_SRC}/Volume/VolumePassword.cpp
	${VC_SRC}/Volume/VolumePasswordCache.cpp
)

set(VC_PLATFORM
	${VC_SRC}/Platform/Buffer.cpp
	${VC_SRC}/Platform/Exception.cpp
	${VC_SRC}/Platform/Event.cpp
	${VC_SRC}/Platform/FileCommon.cpp
	${VC_SRC}/Platform/MemoryStream.cpp
	${VC_SRC}/Platform/Memory.cpp
	${VC_SRC}/Platform/Serializable.cpp
	${VC_SRC}/Platform/Serializer.cpp
	${VC_SRC}/Platform/SerializerFactory.cpp
	${VC_SRC}/Platform/StringConverter.cpp
	${VC_SRC}/Platform/TextReader.cpp
	${VC_SRC}/Platform/Unix/Directory.cpp
	${VC_SRC}/Platform/Unix/File.cpp
	${VC_SRC}/Platform/Unix/FilesystemPath.cpp
	${VC_SRC}/Platform/Unix/Mutex.cpp
	${VC_SRC}/Platform/Unix/SyncEvent.cpp
	${VC_SRC}/Platform/Unix/SystemException.cpp
	${VC_SRC}/Platform/Unix/SystemInfo.cpp
	${VC_SRC}/Platform/Unix/SystemLog.cpp
	${VC_SRC}/Platform/Unix/Thread.cpp
	${VC_SRC}/Platform/Unix/Time.cpp
)

set(VC_COMMON
	${VC_SRC}/Common/Pkcs5.c
	${VC_SRC}/Common/Crc.c
	${VC_SRC}/Common/Endian.c
	${VC_SRC}/Common/GfMul.c
)
