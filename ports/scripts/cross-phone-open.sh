#!/bin/sh
# Sprint 10: create a random volume on Android, open it on iOS, and the reverse.
# Needs a booted AVD (vcport-api35) and an iPad Simulator. Skips a side that is missing.
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)"
ANDROID="$ROOT/ports/android"
IOS="$ROOT/ports/ios"
STAGE="${VCPORT_CROSS_DIR:-/tmp/vcport-cross}"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
ADB="$(command -v adb 2>/dev/null || true)"
if [ -z "$ADB" ] && [ -x "$SDK/platform-tools/adb" ]; then
	ADB="$SDK/platform-tools/adb"
fi
PKG=dev.shivampingale.vcport
ANDROID_CROSS="/sdcard/Download/vcport-cross"
ANDROID_APP_CROSS="/sdcard/Android/data/$PKG/files/cross"
JAVA_HOME="${JAVA_HOME:-/Users/smp/.jdks/jdk-17.0.20+8/Contents/Home}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:${PATH:-}"

mkdir -p "$STAGE"
have_android() {
	[ -n "$ADB" ] && "$ADB" devices | awk 'NR>1 && $2=="device" {found=1} END {exit found?0:1}'
}

first_ipad() {
	want="$1"
	xcrun simctl list devices available 2>/dev/null | awk -v want="$want" '
		/iPad/ && $0 ~ want {
			n = split($0, a, /[()]/)
			for (i = 1; i <= n; i++) {
				gsub(/ /, "", a[i])
				if (a[i] ~ /^[0-9A-Fa-f-]{36}$/) { print a[i]; exit }
			}
		}
	'
}

android_connected() {
	export ANDROID_HOME="${ANDROID_HOME:-$SDK}"
	cd "$ANDROID"
	./gradlew :app:connectedFossDebugAndroidTest --no-daemon \
		-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
		-Pandroid.testInstrumentationRunnerArguments.class="dev.shivampingale.vcport.CrossPhoneVolumeTest#$1"
}

ios_only() {
	UDID="$1"
	test="$2"
	cd "$IOS"
	IOS_SDK=iphonesimulator IOS_ARCH="$(uname -m)" ./build-native.sh
	xcodegen generate
	xcodebuild -project VCPort.xcodeproj -scheme VCPort -configuration Debug \
		-destination "id=$UDID" \
		-derivedDataPath "$IOS/build/DerivedData" \
		CODE_SIGNING_ALLOWED=NO \
		CODE_SIGNING_REQUIRED=NO \
		-maximum-test-execution-time-allowance 600 \
		-only-testing:"VCPortTests/CrossPhoneVolumeTests/$test" \
		test
}

ios_docs() {
	UDID="$1"
	data="$(xcrun simctl get_app_container "$UDID" "$PKG" data 2>/dev/null || true)"
	if [ -z "$data" ]; then
		echo "no iOS app data container yet" >&2
		return 1
	fi
	mkdir -p "$data/Documents/cross"
	printf '%s\n' "$data/Documents/cross"
}

if have_android; then
	echo "== Android create =="
	android_connected createRandomVolumeOnAndroid
	"$ADB" shell mkdir -p "$ANDROID_CROSS"
	if ! "$ADB" pull "$ANDROID_CROSS/android-made.hc" "$STAGE/android-made.hc" 2>/dev/null; then
		"$ADB" pull "$ANDROID_APP_CROSS/android-made.hc" "$STAGE/android-made.hc"
		"$ADB" pull "$ANDROID_APP_CROSS/android-made.json" "$STAGE/android-made.json"
	else
		"$ADB" pull "$ANDROID_CROSS/android-made.json" "$STAGE/android-made.json"
	fi
	echo "pulled Android volume $(wc -c < "$STAGE/android-made.hc") bytes"
else
	echo "SKIP  no Android emulator (adb devices is empty)"
fi

UDID="$(first_ipad Booted)"
if [ -z "$UDID" ]; then
	UDID="$(first_ipad Shutdown)"
fi
if [ -n "$UDID" ] && command -v xcodebuild >/dev/null 2>&1 && command -v xcodegen >/dev/null 2>&1; then
	xcrun simctl boot "$UDID" >/dev/null 2>&1 || true
	echo "iPad Simulator $UDID"
	echo "== iOS create =="
	ios_only "$UDID" testCreateRandomVolumeOnIos
	DOCS="$(ios_docs "$UDID")"
	cp "$DOCS/ios-made.hc" "$STAGE/ios-made.hc"
	cp "$DOCS/ios-made.json" "$STAGE/ios-made.json"
	echo "copied iOS volume $(wc -c < "$STAGE/ios-made.hc") bytes"

	if [ -f "$STAGE/android-made.hc" ]; then
		echo "== iOS opens Android volume =="
		cp "$STAGE/android-made.hc" "$DOCS/android-made.hc"
		cp "$STAGE/android-made.json" "$DOCS/android-made.json"
		ios_only "$UDID" testOpenVolumeMadeOnAndroid
	fi
else
	echo "SKIP  no iPad Simulator / xcodebuild"
fi

if have_android && [ -f "$STAGE/ios-made.hc" ]; then
	echo "== Android opens iOS volume =="
	"$ADB" shell mkdir -p "$ANDROID_CROSS" /data/local/tmp/vcport-cross
	"$ADB" push "$STAGE/ios-made.hc" "$ANDROID_CROSS/ios-made.hc"
	"$ADB" push "$STAGE/ios-made.json" "$ANDROID_CROSS/ios-made.json"
	"$ADB" push "$STAGE/ios-made.hc" /data/local/tmp/vcport-cross/ios-made.hc
	"$ADB" push "$STAGE/ios-made.json" /data/local/tmp/vcport-cross/ios-made.json
	android_connected openVolumeMadeOnIos
fi

echo "OK  sprint 10 volumes in $STAGE"
ls -l "$STAGE"
