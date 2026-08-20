#!/bin/sh
# One slow UI walk on Android and iOS at the same time.
# 9-step session on both phones; this branch also runs fake USB (Android)
# and no-whole-disk + View in app (iOS). Does not tap Panic wipe or
# Check for updates. Does not run on GitHub Actions (no emulator there).
# SLOW=1 also runs Android SlowHumanSessionTest (entropy scribble on screen).
set -eu
PORTS="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
LOG="${TMPDIR:-/tmp}/vcport-ui-walk"
mkdir -p "$LOG"

if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
	_jh="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
	if [ -n "$_jh" ]; then
		export JAVA_HOME="$_jh"
	fi
fi
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}${ANDROID_HOME:+$ANDROID_HOME/platform-tools:}$PATH"

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
	./gradlew :app:connectedFossDebugAndroidTest --no-daemon \
		"-Pandroid.testInstrumentationRunnerArguments.class=${android_classes}"
}

ios_walk() {
	UDID="$(pick_ios_udid | awk 'NF{print; exit}')"
	if [ -z "$UDID" ]; then
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
