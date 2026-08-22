#!/bin/sh
# Full NativeBridge + Compose UI on a connected emulator or phone.
# Boots AVD vcport-api35 when adb is empty and that AVD exists.
# Host equivalent (no emulator): ports/shared/run_lifecycle_test.sh
# Does not call UpdateChecker.check().
set -e
ANDROID="$(cd "$(dirname "$0")" && pwd)"
# shellcheck disable=SC1091
. "$ANDROID/../scripts/android-dev.sh"

vcport_resolve_java || exit 1
if ! vcport_ensure_emulator; then
	echo "FAIL  no emulator or device"
	exit 1
fi

ADB="$(vcport_adb)"
echo "device ABI: $("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')"
echo "device nproc: $("$ADB" shell nproc 2>/dev/null | tr -d '\r' || echo '?')"
cd "$ANDROID"
./gradlew :app:connectedFossDebugAndroidTest --no-daemon

# Compose UI shots (FLAG_SECURE still on; not adb screencap).
SHOTS="$(cd "$ANDROID/../docs/screenshots" && pwd)"
mkdir -p "$SHOTS"
DL_SHOTS=/storage/emulated/0/Download/vcport-github-shots
if "$ADB" shell ls "$DL_SHOTS/01-volume.png" >/dev/null 2>&1; then
	echo "Pulling GitHub UI shots into $SHOTS"
	for name in 01-volume.png 03-create.png 04-tools.png \
		05-mounted.png 08-skin-signal.png; do
		if "$ADB" shell ls "$DL_SHOTS/$name" >/dev/null 2>&1; then
			"$ADB" pull "$DL_SHOTS/$name" "$SHOTS/$name"
			ls -l "$SHOTS/$name"
		fi
	done
else
	echo "NOTE  no github-shots on device (UI test did not write PNGs)"
fi
