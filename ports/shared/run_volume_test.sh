#!/bin/sh
# Host test for vc_open / FAT list / export. Builds libvc_mobile via CMake.
set -e
SHARED="$(cd "$(dirname "$0")" && pwd)"
if [ -d "$SHARED/../../src/Volume" ]; then
	SRC="$(cd "$SHARED/../../src" && pwd)"
elif [ -d "$SHARED/../veracrypt/src/Volume" ]; then
	SRC="$(cd "$SHARED/../veracrypt/src" && pwd)"
elif [ -n "$VC_SRC" ]; then
	SRC="$VC_SRC"
else
	echo "Clone ShivamPingaleDev/Veracrypt_port, or set VC_SRC." >&2
	exit 1
fi

BUILD="$(mktemp -d "${TMPDIR:-/tmp}/vcport-vol-build.XXXXXX")"
trap 'rm -rf "$BUILD"' EXIT
cmake -S "$SHARED" -B "$BUILD" -DVC_SRC="$SRC" -DCMAKE_BUILD_TYPE=Release
cmake --build "$BUILD" --target vc_volume_test
"$BUILD/vc_volume_test"
