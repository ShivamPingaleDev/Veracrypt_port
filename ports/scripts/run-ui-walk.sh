#!/bin/sh
# One slow UI walk. Default runs Android and iOS together.
# VC_PORT_WALK=android or VC_PORT_WALK=ios runs one phone (GitHub Actions).
# VC_PORT_CI=1 fails if the iOS Simulator is missing instead of skipping.
# 10-phase session on both phones; this branch also runs fake USB (Android),
# the transfer queue on the session copy, and no-whole-disk + View in app (iOS).
# Does not tap Panic wipe or
# Check for updates.
# SLOW=1 also runs Android SlowHumanSessionTest (entropy scribble on screen).
# Android: boots AVD vcport-api35 headless if adb is empty. Needs Java 17
# (JAVA_HOME, java_home, or Homebrew openjdk@17).
set -eu
PORTS="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1091
. "$PORTS/scripts/android-dev.sh"
LOG="${TMPDIR:-/tmp}/vcport-ui-walk"
mkdir -p "$LOG"
SCOPE="${VC_PORT_WALK:-both}"

start_android() {
	vcport_resolve_java || return 1
	vcport_android_sdk
	export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}${ANDROID_HOME:+$ANDROID_HOME/platform-tools:}$PATH"
	if ! vcport_ensure_emulator; then
		echo "FAIL  android emulator did not start (see ${TMPDIR:-/tmp}/vcport-emu.log)"
		return 1
	fi
}

android_classes="dev.shivampingale.vcport.UiWalkSuite"
if [ "${SLOW:-0}" = "1" ]; then
	android_classes="${android_classes},dev.shivampingale.vcport.SlowHumanSessionTest"
fi

ios_only="-only-testing:VCPortTests/AppInterfaceSessionTests"
if [ -f "$PORTS/ios/VCPortTests/OtgAbsentAndPreviewTests.swift" ]; then
	ios_only="$ios_only -only-testing:VCPortTests/OtgAbsentAndPreviewTests"
fi
if [ -f "$PORTS/ios/VCPortTests/InAppPreviewTests.swift" ]; then
	ios_only="$ios_only -only-testing:VCPortTests/InAppPreviewTests"
fi

pick_ios_udid() {
	xcrun simctl list devices available 2>/dev/null | awk '
		/Booted/ {
			n = split($0, a, /[()]/)
			for (i = 1; i <= n; i++) {
				gsub(/ /, "", a[i])
				if (a[i] ~ /^[0-9A-Fa-f-]{36}$/) { print a[i]; exit }
			}
		}
	'
	xcrun simctl list devices available 2>/dev/null | awk '
		/iPhone/ && /Shutdown/ {
			n = split($0, a, /[()]/)
			for (i = 1; i <= n; i++) {
				gsub(/ /, "", a[i])
				if (a[i] ~ /^[0-9A-Fa-f-]{36}$/) { print a[i]; exit }
			}
		}
	'
	xcrun simctl list devices available 2>/dev/null | awk '
		/iPad/ && /Shutdown/ {
			n = split($0, a, /[()]/)
			for (i = 1; i <= n; i++) {
				gsub(/ /, "", a[i])
				if (a[i] ~ /^[0-9A-Fa-f-]{36}$/) { print a[i]; exit }
			}
		}
	'
}

android_walk() {
	cd "$PORTS/android"
	if ! vcport_have_device || ! vcport_wait_boot 15; then
		echo "Android device gone; restarting emulator..."
		vcport_ensure_emulator || return 1
	fi
	vcport_keep_awake
	set +e
	./gradlew :app:connectedFossDebugAndroidTest --no-daemon \
		"-Pandroid.testInstrumentationRunnerArguments.class=${android_classes}"
	_rc=$?
	set -e
	if [ "$_rc" -eq 0 ]; then
		return 0
	fi
	if vcport_have_device; then
		return "$_rc"
	fi
	echo "Gradle saw no device; restarting emulator and retrying once..."
	vcport_ensure_emulator || return 1
	vcport_keep_awake
	./gradlew :app:connectedFossDebugAndroidTest --no-daemon \
		"-Pandroid.testInstrumentationRunnerArguments.class=${android_classes}"
}

ios_walk() {
	UDID="$(pick_ios_udid | awk 'NF{print; exit}')"
	if [ -z "$UDID" ]; then
		if [ "${VC_PORT_CI:-0}" = "1" ]; then
			echo "FAIL  no iOS Simulator"
			return 1
		fi
		echo "SKIP  no iOS Simulator"
		return 0
	fi
	xcrun simctl boot "$UDID" >/dev/null 2>&1 || true
	open -a Simulator --args -CurrentDeviceUDID "$UDID" >/dev/null 2>&1 || true
	cd "$PORTS/ios"
	xcodegen generate
	# shellcheck disable=SC2086
	xcodebuild -project VCPort.xcodeproj -scheme VCPort -configuration Debug \
		-destination "id=$UDID" \
		-derivedDataPath "$PORTS/ios/build/DerivedData" \
		CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO \
		-maximum-test-execution-time-allowance 1800 \
		$ios_only \
		test
}

case "$SCOPE" in
	android)
		start_android
		android_walk
		echo "PASS  UI walk android (log follows on stdout)"
		;;
	ios)
		ios_walk
		echo "PASS  UI walk ios"
		;;
	both)
		start_android
		android_walk >"$LOG/android.log" 2>&1 &
		APID=$!
		ios_walk >"$LOG/ios.log" 2>&1 &
		IPID=$!
		A=0
		I=0
		wait "$APID" || A=$?
		wait "$IPID" || I=$?
		echo "==== android UI walk ===="
		cat "$LOG/android.log"
		echo "==== ios UI walk ===="
		cat "$LOG/ios.log"
		if [ "$A" -ne 0 ] || [ "$I" -ne 0 ]; then
			echo "FAIL  android=$A ios=$I"
			exit 1
		fi
		echo "PASS  UI walk android+ios (logs in $LOG)"
		;;
	*)
		echo "FAIL  VC_PORT_WALK=$SCOPE (use android, ios, or both)"
		exit 1
		;;
esac
