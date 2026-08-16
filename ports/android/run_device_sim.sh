#!/bin/sh
# Full NativeBridge + Compose UI on a connected emulator or phone.
# Boots AVD vcport-api35 when adb is empty and that AVD exists.
# Host equivalent (no emulator): ports/shared/run_lifecycle_test.sh
# Does not call UpdateChecker.check().
set -e
ANDROID="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
ADB="$(command -v adb 2>/dev/null || true)"
if [ -z "$ADB" ] && [ -x "$SDK/platform-tools/adb" ]; then
	ADB="$SDK/platform-tools/adb"
	export PATH="$SDK/platform-tools:$PATH"
fi
if [ -z "$ADB" ]; then
	echo "SKIP  adb not on PATH (install Android SDK platform-tools)"
	exit 0
fi

have_device() {
	"$ADB" devices | awk 'NR>1 && $2=="device" {found=1} END {exit found?0:1}'
}

boot_avd() {
	EMU="$SDK/emulator/emulator"
	AVD="${VC_PORT_AVD:-vcport-api35}"
	if [ ! -x "$EMU" ]; then
		return 1
	fi
	if ! "$EMU" -list-avds 2>/dev/null | grep -qx "$AVD"; then
		echo "SKIP  AVD $AVD not found (adb devices is empty)"
		return 1
	fi
	echo "Starting AVD $AVD for VC Port emulator tests..."
	"$EMU" -avd "$AVD" -no-snapshot-save -no-boot-anim >/tmp/vcport-emu.log 2>&1 &
	i=0
	while [ "$i" -lt 48 ]; do
		if have_device; then
			booted=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
			if [ "$booted" = "1" ]; then
				return 0
			fi
		fi
		i=$((i + 1))
		sleep 5
	done
	echo "SKIP  emulator did not boot in 240s"
	return 1
}

if ! have_device; then
	boot_avd || true
fi
if ! have_device; then
	echo "SKIP  no emulator or device (adb devices is empty)"
	exit 0
fi

echo "device ABI: $("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')"
echo "device nproc: $("$ADB" shell nproc 2>/dev/null | tr -d '\r' || echo '?')"
cd "$ANDROID"
./gradlew :app:connectedFdroidDebugAndroidTest --no-daemon
./gradlew :app:connectedStyledDebugAndroidTest --no-daemon \
	-Pandroid.testInstrumentationRunnerArguments.class=dev.shivampingale.vcport.MainActivityUiTest

# Compose UI shots (FLAG_SECURE still on; not adb screencap).
SHOTS="$(cd "$ANDROID/../docs/screenshots" && pwd)"
mkdir -p "$SHOTS"
DL_SHOTS=/storage/emulated/0/Download/vcport-github-shots
if "$ADB" shell ls "$DL_SHOTS/01-volume.png" >/dev/null 2>&1; then
	echo "Pulling GitHub UI shots into $SHOTS"
	for name in 01-volume.png 02-wrap.png 03-create.png 04-tools.png \
		05-skin-cyberpunk.png 06-skin-matrix.png 07-skin-eva.png 08-skin-signal.png; do
		if "$ADB" shell ls "$DL_SHOTS/$name" >/dev/null 2>&1; then
			"$ADB" pull "$DL_SHOTS/$name" "$SHOTS/$name"
			ls -l "$SHOTS/$name"
		fi
	done
else
	echo "NOTE  no github-shots on device (UI test did not write PNGs)"
fi

THEME_DL=/storage/emulated/0/Download/vcport-theme-shots
THEME_SHOTS="$(cd "$ANDROID/../../experimental/pure-c" && pwd)/shots"
if "$ADB" shell ls "$THEME_DL/skin-cyberpunk.png" >/dev/null 2>&1; then
	mkdir -p "$THEME_SHOTS"
	echo "Pulling experimental computer-skin shots into $THEME_SHOTS"
	for name in skin-cyberpunk.png skin-matrix.png skin-eva.png skin-signal.png; do
		"$ADB" pull "$THEME_DL/$name" "$THEME_SHOTS/$name"
		ls -l "$THEME_SHOTS/$name"
	done
fi
