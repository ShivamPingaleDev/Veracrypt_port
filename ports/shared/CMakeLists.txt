cmake_minimum_required(VERSION 3.18)
project(vc_mobile C CXX)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_C_STANDARD 11)

if(NOT VC_SRC)
	foreach(_candidate
		"${CMAKE_CURRENT_SOURCE_DIR}/../../src"
		"${CMAKE_CURRENT_SOURCE_DIR}/../veracrypt/src"
		"${CMAKE_CURRENT_SOURCE_DIR}/veracrypt/src")
		if(EXISTS "${_candidate}/Volume/Volume.h")
			set(VC_SRC "${_candidate}")
			break()
		endif()
	endforeach()
endif()
if(NOT VC_SRC)
	message(FATAL_ERROR "Set VC_SRC to the VeraCrypt src tree (clone ShivamPingaleDev/Veracrypt_port).")
endif()

add_definitions(
	-DTC_UNIX
	-D_FILE_OFFSET_BITS=64
	-D_LARGEFILE_SOURCE
	-DARGON2_NO_THREADS
	-DCRYPTOPP_DISABLE_X86ASM
)
if(ANDROID)
	add_definitions(-DTC_LINUX -DTC_ANDROID)
elseif(APPLE)
	add_definitions(-DTC_MACOSX)
else()
	add_definitions(-DTC_LINUX)
endif()

include_directories(
	${VC_SRC}
	${VC_SRC}/Crypto
	${VC_SRC}/Crypto/Argon2/include
	${VC_SRC}/PKCS11
	${CMAKE_CURRENT_SOURCE_DIR}
)

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

if(CMAKE_SYSTEM_PROCESSOR MATCHES "aarch64|arm64")
	list(APPEND VC_CRYPTO
		${VC_SRC}/Crypto/Aes_hw_armv8.c
		${VC_SRC}/Crypto/sha256_armv8.c
	)
	set_source_files_properties(
		${VC_SRC}/Crypto/Aes_hw_armv8.c
		${VC_SRC}/Crypto/sha256_armv8.c
		PROPERTIES COMPILE_FLAGS "-march=armv8-a+crypto"
	)
elseif(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|amd64|AMD64|i.86")
	list(APPEND VC_CRYPTO
		${VC_SRC}/Crypto/Argon2/src/opt_sse2.c
		${VC_SRC}/Crypto/Argon2/src/opt_avx2.c
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

add_library(vc_mobile STATIC
	vc_mobile.cpp
	vc_wrap.cpp
	token_stubs.cpp
	${VC_CRYPTO}
	${VC_VOLUME}
	${VC_PLATFORM}
	${VC_COMMON}
)

target_compile_options(vc_mobile PRIVATE -fPIC -fno-strict-aliasing)
if(NOT APPLE)
	target_link_libraries(vc_mobile PRIVATE dl)
endif()

if(ANDROID)
	add_library(vcport SHARED android_jni.cpp)
	target_link_libraries(vcport PRIVATE vc_mobile log)
endif()
