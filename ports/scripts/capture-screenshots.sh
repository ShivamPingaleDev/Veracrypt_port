#!/bin/sh
# Capture GitHub README UI shots from emulators (FLAG_SECURE stays on Android).
# Android: Compose captureToImage → Download/vcport-github-shots
# iOS: testPublishTabScreenshots → app Documents/github-shots
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)"
PORTS="$ROOT/ports"
ANDROID="$PORTS/android"
IOS="$PORTS/ios"
SHOTS="$PORTS/docs/screenshots"
# shellcheck disable=SC1091
. "$PORTS/scripts/android-dev.sh"

mkdir -p "$SHOTS"

android_shots() {
	vcport_resolve_java || return 1
	if ! vcport_ensure_emulator; then
		echo "FAIL  no Android emulator"
		return 1
	fi
	vcport_keep_awake
	cd "$ANDROID"
	./gradlew :app:connectedFossDebugAndroidTest --no-daemon \
		"-Pandroid.testInstrumentationRunnerArguments.class=dev.shivampingale.vcport.MainActivityUiTest"
	ADB="$(vcport_adb)"
	DL=/storage/emulated/0/Download/vcport-github-shots
	for name in 01-volume.png 03-create.png 04-tools.png 05-mounted.png 08-skin-signal.png; do
		if "$ADB" shell ls "$DL/$name" >/dev/null 2>&1; then
			"$ADB" pull "$DL/$name" "$SHOTS/$name"
			echo "android $name"
		else
			echo "WARN  missing $name on device"
		fi
	done
}

ios_shots() {
	if ! command -v xcodebuild >/dev/null 2>&1 || ! command -v xcodegen >/dev/null 2>&1; then
		echo "SKIP  Xcode / xcodegen missing"
		return 0
	fi
	UDID="$(xcrun simctl list devices available 2>/dev/null | awk '
		/iPhone/ && /Booted/ {
			n = split($0, a, /[()]/)
			for (i = 1; i <= n; i++) {
				gsub(/ /, "", a[i])
				if (a[i] ~ /^[0-9A-Fa-f-]{36}$/) { print a[i]; exit }
			}
		}
	')"
	if [ -z "$UDID" ]; then
		UDID="$(xcrun simctl list devices available 2>/dev/null | awk '
			/iPhone/ && /Shutdown/ {
				n = split($0, a, /[()]/)
				for (i = 1; i <= n; i++) {
					gsub(/ /, "", a[i])
					if (a[i] ~ /^[0-9A-Fa-f-]{36}$/) { print a[i]; exit }
				}
			}
		')"
	fi
	if [ -z "$UDID" ]; then
		echo "SKIP  no iPhone Simulator"
		return 0
	fi
	xcrun simctl boot "$UDID" >/dev/null 2>&1 || true
	open -a Simulator --args -CurrentDeviceUDID "$UDID" >/dev/null 2>&1 || true
	cd "$IOS"
	IOS_SDK=iphonesimulator IOS_ARCH="$(uname -m)" ./build-native.sh
	xcodegen generate
	DERIVED="$IOS/build/DerivedData-screens"
	xcodebuild -project VCPort.xcodeproj -scheme VCPort -configuration Debug \
		-destination "id=$UDID" \
		-derivedDataPath "$DERIVED" \
		CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO build-for-testing
	xcodebuild -project VCPort.xcodeproj -scheme VCPort -configuration Debug \
		-destination "id=$UDID" \
		-derivedDataPath "$DERIVED" \
		CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO test-without-building \
		-only-testing:VCPortTests/AppInterfaceSessionTests/testPublishTabScreenshots
	CONTAINER="$(xcrun simctl get_app_container "$UDID" dev.shivampingale.vcport data 2>/dev/null || true)"
	if [ -z "$CONTAINER" ] || [ ! -d "$CONTAINER/Documents/github-shots" ]; then
		echo "WARN  ios github-shots not found in simulator"
		return 1
	fi
	for name in ios-01-volume.png ios-03-create.png ios-04-tools.png ios-05-mounted.png; do
		if [ -f "$CONTAINER/Documents/github-shots/$name" ]; then
			cp "$CONTAINER/Documents/github-shots/$name" "$SHOTS/$name"
			echo "ios $name"
		fi
	done
}

make_thumbs() {
	if ! command -v sips >/dev/null 2>&1; then
		return 0
	fi
	mkdir -p "$SHOTS/thumbs"
	for f in "$SHOTS"/*.png; do
		[ -f "$f" ] || continue
		case "$f" in */thumbs/*) continue ;; esac
		base="$(basename "$f")"
		# 480px wide = 2× the README display width (240) for sharp previews on HiDPI screens.
		sips --resampleWidth 480 "$f" --out "$SHOTS/thumbs/$base" >/dev/null 2>&1 || true
	done
	echo "thumbs in $SHOTS/thumbs"
}

polish_shots() {
	if command -v python3 >/dev/null 2>&1; then
		python3 "$PORTS/scripts/polish-screenshots.py" || true
	fi
}

echo "== Android screenshots =="
android_shots || true
echo "== iOS screenshots =="
ios_shots || true
polish_shots
make_thumbs
echo "Done → $SHOTS"
