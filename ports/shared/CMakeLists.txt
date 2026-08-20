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
	-DTC_PORT_NO_TOKEN
)
if(ANDROID)
	add_definitions(-DTC_LINUX -DTC_ANDROID)
	# NDK never assembles Aes_hw_cpu.asm (CRYPTOPP_DISABLE_X86ASM). Without
	# these, Cipher.cpp and cpu.c still demand AES-NI / SHA-NI objects on
	# x86 and x86_64 emulator slices.
	if(ANDROID_ABI MATCHES "^x86")
		add_definitions(-DCRYPTOPP_DISABLE_AESNI -DCRYPTOPP_DISABLE_SHANI)
	endif()
	# C11 inline in cpu.c does not emit CPU_QueryAES/SHA2 at -O0 (Android
	# Studio / connectedDebug). GNU89 inline keeps ARM crypto detection.
	# arm64 also needs +crypto so DetectArmFeatures / Aes_hw_armv8 agree.
	if(ANDROID_ABI STREQUAL "arm64-v8a")
		set_source_files_properties("${VC_SRC}/Crypto/cpu.c"
			PROPERTIES COMPILE_FLAGS "-fgnu89-inline -march=armv8-a+crypto")
	else()
		set_source_files_properties("${VC_SRC}/Crypto/cpu.c"
			PROPERTIES COMPILE_FLAGS "-fgnu89-inline")
	endif()
elseif(CMAKE_SYSTEM_NAME STREQUAL "iOS")
	add_definitions(-DTC_MACOSX -DTC_IOS)
elseif(APPLE)
	add_definitions(-DTC_MACOSX)
else()
	add_definitions(-DTC_LINUX)
	# Host Linux CI is x86_64. cpu.c wants TrySHA256 from Sha2Intel.c unless SHANI is off.
	if(CMAKE_SYSTEM_PROCESSOR MATCHES "x86_64|amd64|AMD64|i.86")
		add_definitions(-DCRYPTOPP_DISABLE_AESNI -DCRYPTOPP_DISABLE_SHANI)
	endif()
endif()

include_directories(
	${CMAKE_CURRENT_SOURCE_DIR}
	${VC_SRC}
	${VC_SRC}/Crypto
	${VC_SRC}/Crypto/Argon2/include
	${VC_SRC}/PKCS11
)

include("${CMAKE_CURRENT_SOURCE_DIR}/upstream-sources.cmake")
include("${CMAKE_CURRENT_SOURCE_DIR}/overlay.cmake")

add_library(vc_mobile STATIC
	vc_mobile.cpp
	vc_exfat.cpp
	vc_wrap.cpp
	vc_progress.cpp
	vc_otg_dev.cpp
	${VC_CRYPTO}
	${VC_VOLUME}
	${VC_PLATFORM}
	${VC_COMMON}
)

target_compile_options(vc_mobile PRIVATE -fPIC -fno-strict-aliasing -fstack-protector-strong -fno-common)
# FORTIFY needs -O1+. Host Debug stays -O0; Android arm Debug already forces -O2.
if(CMAKE_BUILD_TYPE MATCHES "Release|RelWithDebInfo|MinSizeRel")
	target_compile_definitions(vc_mobile PRIVATE _FORTIFY_SOURCE=2)
endif()
if(ANDROID AND ANDROID_ABI MATCHES "arm")
	target_compile_definitions(vc_mobile PRIVATE _FORTIFY_SOURCE=2)
endif()

# ARM: ARMv8 AES/SHA (+crypto) and NEON. Debug NDK is otherwise -O0, which
# leaves CPU_QueryAES uninlined and skips auto-vectorized Twofish/Serpent/SHA-512.
# HMAC-SHA-512 stays sequential; the pool parallelizes XTS and auto-detect KDF.
# PAC/BTI stays on Aes_hw_armv8 / sha256_armv8 only — whole-library
# -mbranch-protection aborted host volume tests (C++ PasswordIncorrect).
if(ANDROID AND ANDROID_ABI STREQUAL "arm64-v8a")
	target_compile_options(vc_mobile PRIVATE -ftree-vectorize -march=armv8-a+crypto)
	if(NOT CMAKE_BUILD_TYPE OR CMAKE_BUILD_TYPE STREQUAL "Debug")
		target_compile_options(vc_mobile PRIVATE -O2)
	endif()
	set_source_files_properties(
		${VC_SRC}/Crypto/Aescrypt.c
		${VC_SRC}/Crypto/Aeskey.c
		${VC_SRC}/Crypto/Aestab.c
		PROPERTIES COMPILE_FLAGS "-O3 -ftree-vectorize")
elseif(ANDROID AND ANDROID_ABI STREQUAL "armeabi-v7a")
	target_compile_options(vc_mobile PRIVATE -ftree-vectorize -mfpu=neon)
	if(NOT CMAKE_BUILD_TYPE OR CMAKE_BUILD_TYPE STREQUAL "Debug")
		target_compile_options(vc_mobile PRIVATE -O2)
	endif()
	# No AES crypto-extension on ARMv7. Table AES + NEON vectorize/unroll.
	set_source_files_properties(
		${VC_SRC}/Crypto/Aescrypt.c
		${VC_SRC}/Crypto/Aeskey.c
		${VC_SRC}/Crypto/Aestab.c
		PROPERTIES COMPILE_FLAGS "-O3 -mfpu=neon -ftree-vectorize -funroll-loops")
elseif(CMAKE_SYSTEM_NAME STREQUAL "iOS")
	if(CMAKE_OSX_ARCHITECTURES MATCHES "arm64" OR CMAKE_SYSTEM_PROCESSOR MATCHES "arm64|aarch64")
		target_compile_options(vc_mobile PRIVATE -O2 -ftree-vectorize -march=armv8-a+crypto)
		set_source_files_properties(
			${VC_SRC}/Crypto/Aescrypt.c
			${VC_SRC}/Crypto/Aeskey.c
			${VC_SRC}/Crypto/Aestab.c
			PROPERTIES COMPILE_FLAGS "-O3 -ftree-vectorize")
	endif()
elseif(APPLE AND CMAKE_SYSTEM_PROCESSOR MATCHES "arm64|aarch64")
	target_compile_options(vc_mobile PRIVATE -ftree-vectorize -march=armv8-a+crypto)
	set_source_files_properties(
		${VC_SRC}/Crypto/Aescrypt.c
		${VC_SRC}/Crypto/Aeskey.c
		${VC_SRC}/Crypto/Aestab.c
		PROPERTIES COMPILE_FLAGS "-O3 -ftree-vectorize")
endif()

if(NOT APPLE)
	target_link_libraries(vc_mobile PRIVATE dl)
endif()

if(ANDROID)
	add_library(vcport SHARED android_jni.cpp)
	target_link_libraries(vcport PRIVATE vc_mobile log)
	target_compile_options(vcport PRIVATE -fstack-protector-strong -fno-common)
	target_link_options(vcport PRIVATE -Wl,-z,relro -Wl,-z,now -Wl,-z,noexecstack)
	if(ANDROID_ABI MATCHES "arm")
		target_compile_definitions(vcport PRIVATE _FORTIFY_SOURCE=2)
	endif()
	if(ANDROID_ABI STREQUAL "arm64-v8a")
		target_compile_options(vcport PRIVATE -ftree-vectorize -march=armv8-a+crypto)
		if(NOT CMAKE_BUILD_TYPE OR CMAKE_BUILD_TYPE STREQUAL "Debug")
			target_compile_options(vcport PRIVATE -O2)
		endif()
	elseif(ANDROID_ABI STREQUAL "armeabi-v7a")
		target_compile_options(vcport PRIVATE -ftree-vectorize -mfpu=neon)
		if(NOT CMAKE_BUILD_TYPE OR CMAKE_BUILD_TYPE STREQUAL "Debug")
			target_compile_options(vcport PRIVATE -O2)
		endif()
	endif()
elseif(NOT CMAKE_SYSTEM_NAME STREQUAL "iOS")
	find_package(Threads REQUIRED)
	target_link_libraries(vc_mobile PRIVATE Threads::Threads)
	add_executable(vc_volume_test test_volume_main.cpp)
	target_link_libraries(vc_volume_test PRIVATE vc_mobile Threads::Threads)
	add_executable(vc_lifecycle_test test_lifecycle_main.cpp)
	target_link_libraries(vc_lifecycle_test PRIVATE vc_mobile Threads::Threads)
	add_executable(vc_crypto_safety_test test_crypto_safety_main.cpp mobile_mocks.cpp)
	target_link_libraries(vc_crypto_safety_test PRIVATE vc_mobile Threads::Threads)
	option(VC_ENABLE_ASAN "ASan+UBSan on vc_crypto_safety_test (do not mix with Valgrind)" OFF)
	if(VC_ENABLE_ASAN)
		target_compile_options(vc_crypto_safety_test PRIVATE -fsanitize=address,undefined -fno-omit-frame-pointer)
		target_link_options(vc_crypto_safety_test PRIVATE -fsanitize=address,undefined)
	endif()
endif()
