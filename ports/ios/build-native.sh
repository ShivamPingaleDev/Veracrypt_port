#!/bin/sh
# Build libvc_mobile for the current iOS SDK/arch, and keep per-slice copies.
# Xcode sets PLATFORM_NAME and ARCHS. From a shell:
#   IOS_SDK=iphoneos IOS_ARCH=arm64 ports/ios/build-native.sh
#   IOS_SDK=iphonesimulator IOS_ARCH=arm64 ports/ios/build-native.sh
#   ./build-native.sh --all
set -euo pipefail
ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
SELF="${ROOT}/ios/build-native.sh"
OUT="${ROOT}/ios/build/native"

build_one() {
	SDK="$1"
	ARCH="$2"
	SRC="$3"
	SLICE="${OUT}/${SDK}-${ARCH}"
	mkdir -p "$SLICE"
	cmake -S "${ROOT}/shared" -B "${SLICE}/cmake" \
		-DCMAKE_SYSTEM_NAME=iOS \
		-DCMAKE_OSX_ARCHITECTURES="$ARCH" \
		-DCMAKE_OSX_DEPLOYMENT_TARGET=16.0 \
		-DCMAKE_OSX_SYSROOT="$SDK" \
		-DCMAKE_BUILD_TYPE=Release \
		-DVC_SRC="$SRC" \
		-DVC_PORT_OTG=OFF
	cmake --build "${SLICE}/cmake" --target vc_mobile
	cp "${SLICE}/cmake/libvc_mobile.a" "${SLICE}/libvc_mobile.a"
	# Xcode pre-build script still links ${OUT}/libvc_mobile.a for the active SDK.
	cp "${SLICE}/libvc_mobile.a" "${OUT}/libvc_mobile.a"
	echo "Wrote ${SLICE}/libvc_mobile.a and ${OUT}/libvc_mobile.a ($SDK $ARCH)"
}

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

if [ "${1:-}" = "--all" ]; then
	mkdir -p "$OUT"
	build_one iphoneos arm64 "$SRC"
	HOST_ARCH="$(uname -m)"
	case "$HOST_ARCH" in
		arm64|x86_64) build_one iphonesimulator "$HOST_ARCH" "$SRC" ;;
		*) echo "Skipping simulator slice on $HOST_ARCH" ;;
	esac
	exit 0
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

case "$SDK:$ARCH" in
	iphoneos:arm64) ;;
	iphonesimulator:arm64|iphonesimulator:x86_64) ;;
	*)
		echo "Unsupported iOS slice $SDK $ARCH (device=arm64, sim=arm64|x86_64)." >&2
		exit 1
		;;
esac

mkdir -p "$OUT"
build_one "$SDK" "$ARCH" "$SRC"
