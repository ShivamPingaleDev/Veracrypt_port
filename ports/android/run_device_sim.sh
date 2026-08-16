#!/bin/sh
# Run the NativeBridge device simulation on a connected emulator or phone.
# Host equivalent (no emulator): ports/shared/run_lifecycle_test.sh
# Does not call UpdateChecker.check().
set -e
ANDROID="$(cd "$(dirname "$0")" && pwd)"
if ! command -v adb >/dev/null 2>&1; then
	echo "SKIP  adb not on PATH (install Android SDK platform-tools)"
	exit 0
fi
if ! adb devices | awk 'NR>1 && $2=="device" {found=1} END {exit found?0:1}'; then
	echo "SKIP  no emulator or device (adb devices is empty)"
	exit 0
fi
cd "$ANDROID"
./gradlew :app:connectedFdroidDebugAndroidTest --no-daemon
