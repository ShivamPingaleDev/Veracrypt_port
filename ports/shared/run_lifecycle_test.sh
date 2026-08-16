#!/bin/sh
# Host simulation: create / open / store / close / reopen a FAT volume with
# password, PIM, and a biometric keyfile. Builds libvc_mobile via CMake.
# Reuses $VC_VOL_BUILD (default /tmp) so cmake is incremental after the first run.
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

BUILD="${VC_VOL_BUILD:-${TMPDIR:-/tmp}/vcport-vol-build}"
cmake -S "$SHARED" -B "$BUILD" -DVC_SRC="$SRC" -DCMAKE_BUILD_TYPE=Release
cmake --build "$BUILD" --target vc_lifecycle_test
"$BUILD/vc_lifecycle_test"
