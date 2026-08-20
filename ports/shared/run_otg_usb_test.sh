#!/bin/sh
# 12-phase host simulation of a whole encrypted USB disk (no real OTG stick).
set -e
SHARED="$(cd "$(dirname "$0")" && pwd)"
if [ -d "$SHARED/../../src/Volume" ]; then
	SRC="$(cd "$SHARED/../../src" && pwd)"
elif [ -n "$VC_SRC" ]; then
	SRC="$VC_SRC"
else
	echo "Clone ShivamPingaleDev/Veracrypt_port, or set VC_SRC." >&2
	exit 1
fi
BUILD="${VC_VOL_BUILD:-${TMPDIR:-/tmp}/vcport-vol-build}"
cmake -S "$SHARED" -B "$BUILD" -DVC_SRC="$SRC" -DCMAKE_BUILD_TYPE=Release
cmake --build "$BUILD" --target vc_otg_usb_test
"$BUILD/vc_otg_usb_test"
