#!/bin/sh
# Build libvc_mobile for the current iOS SDK/arch.
# Xcode sets PLATFORM_NAME and ARCHS. From a shell:
#   IOS_SDK=iphoneos IOS_ARCH=arm64 ports/ios/build-native.sh
#   IOS_SDK=iphonesimulator IOS_ARCH=arm64 ports/ios/build-native.sh
#   IOS_SDK=iphonesimulator IOS_ARCH=x86_64 ports/ios/build-native.sh
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
OUT="${ROOT}/ios/build/native"
SRC="${VC_SRC:-}"
if [ -z "$SRC" ]; then
	for candidate in \
		"${ROOT}/../src" \
		"${ROOT}/veracrypt/src" \
		"${ROOT}/veracrypt-local/src"
	do
		if [ -f "${candidate}/Volume/Volume.h" ]; then
			SRC="$candidate"
			break
		fi
	done
fi
if [ -z "$SRC" ] || [ ! -f "${SRC}/Volume/Volume.h" ]; then
	echo "Set VC_SRC to the VeraCrypt src tree (clone ShivamPingaleDev/Veracrypt_port)." >&2
	exit 1
fi

SDK="${IOS_SDK:-}"
if [ -z "$SDK" ]; then
	case "${PLATFORM_NAME:-iphoneos}" in
		iphonesimulator) SDK=iphonesimulator ;;
		*) SDK=iphoneos ;;
	esac
fi

ARCH="${IOS_ARCH:-}"
if [ -z "$ARCH" ]; then
	if [ -n "${ARCHS:-}" ]; then
		ARCH="$(printf '%s' "$ARCHS" | awk '{print $1}')"
	elif [ "$SDK" = "iphonesimulator" ]; then
		ARCH="$(uname -m)"
	else
		ARCH=arm64
	fi
fi

# Device iOS is arm64-only. Simulator follows the Mac (arm64 or x86_64).
case "$SDK:$ARCH" in
	iphoneos:arm64) ;;
	iphonesimulator:arm64|iphonesimulator:x86_64) ;;
	*)
		echo "Unsupported iOS slice $SDK $ARCH (device=arm64, sim=arm64|x86_64)." >&2
		exit 1
		;;
esac

mkdir -p "$OUT"
cmake -S "${ROOT}/shared" -B "${OUT}/cmake-${SDK}-${ARCH}" \
	-DCMAKE_SYSTEM_NAME=iOS \
	-DCMAKE_OSX_ARCHITECTURES="$ARCH" \
	-DCMAKE_OSX_DEPLOYMENT_TARGET=16.0 \
	-DCMAKE_OSX_SYSROOT="$SDK" \
	-DCMAKE_BUILD_TYPE=Release \
	-DVC_SRC="$SRC"
cmake --build "${OUT}/cmake-${SDK}-${ARCH}" --target vc_mobile
cp "${OUT}/cmake-${SDK}-${ARCH}/libvc_mobile.a" "${OUT}/libvc_mobile.a"
echo "Wrote ${OUT}/libvc_mobile.a ($SDK $ARCH)"
