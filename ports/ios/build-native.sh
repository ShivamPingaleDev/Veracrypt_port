#!/bin/sh
# Build the shared VeraCrypt-compatible static library for device (arm64).
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
mkdir -p "$OUT"
cmake -S "${ROOT}/shared" -B "${OUT}/cmake" \
	-DCMAKE_SYSTEM_NAME=iOS \
	-DCMAKE_OSX_ARCHITECTURES=arm64 \
	-DCMAKE_OSX_DEPLOYMENT_TARGET=16.0 \
	-DCMAKE_OSX_SYSROOT=iphoneos \
	-DCMAKE_BUILD_TYPE=Release \
	-DVC_SRC="$SRC"
cmake --build "${OUT}/cmake" --target vc_mobile
cp "${OUT}/cmake/libvc_mobile.a" "${OUT}/libvc_mobile.a"
echo "Wrote ${OUT}/libvc_mobile.a"
