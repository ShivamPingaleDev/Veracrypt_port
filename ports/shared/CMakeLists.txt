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
	# NDK never assembles Aes_hw_cpu.asm (CRYPTOPP_DISABLE_X86ASM). Without
	# these, Cipher.cpp and cpu.c still demand AES-NI / SHA-NI objects on
	# x86 and x86_64 emulator slices.
	if(ANDROID_ABI MATCHES "^x86")
		add_definitions(-DCRYPTOPP_DISABLE_AESNI -DCRYPTOPP_DISABLE_SHANI)
	endif()
	# C11 inline in cpu.c does not emit CPU_QueryAES/SHA2 at -O0 (Android
	# Studio / connectedDebug). GNU89 inline keeps ARM crypto detection.
	set_source_files_properties("${VC_SRC}/Crypto/cpu.c"
		PROPERTIES COMPILE_FLAGS "-fgnu89-inline")
elseif(CMAKE_SYSTEM_NAME STREQUAL "iOS")
	add_definitions(-DTC_MACOSX -DTC_IOS)
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

include("${CMAKE_CURRENT_SOURCE_DIR}/upstream-sources.cmake")

add_library(vc_mobile STATIC
	vc_mobile.cpp
	vc_wrap.cpp
	vc_progress.cpp
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
elseif(NOT CMAKE_SYSTEM_NAME STREQUAL "iOS")
	find_package(Threads REQUIRED)
	target_link_libraries(vc_mobile PRIVATE Threads::Threads)
	add_executable(vc_volume_test test_volume_main.cpp)
	target_link_libraries(vc_volume_test PRIVATE vc_mobile Threads::Threads)
	add_executable(vc_lifecycle_test test_lifecycle_main.cpp)
	target_link_libraries(vc_lifecycle_test PRIVATE vc_mobile Threads::Threads)
endif()
