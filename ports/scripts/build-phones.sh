#!/bin/sh
# Build the Android FOSS APK and the unsigned iOS IPA at the same time.
# Sign the IPA on this Mac with your Apple Team ID:
#   VC_PORT_IOS_TEAM=YOUR10CHARID ports/ios/sideload-sign.sh
set -eu
PORTS="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
ROOT="$(CDPATH= cd -- "$PORTS/.." && pwd)"
LOG="${TMPDIR:-/tmp}/vcport-phone-build"
mkdir -p "$LOG"
if [ -z "${JAVA_HOME:-}" ] && [ -x /usr/libexec/java_home ]; then
	_jh="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
	if [ -n "$_jh" ]; then
		export JAVA_HOME="$_jh"
	fi
fi
# shellcheck disable=SC1091
. "$PORTS/scripts/android-dev.sh"
vcport_resolve_java
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"

android_build() {
	cd "$PORTS/android"
	./gradlew :app:assembleFossRelease --no-daemon
}

ios_build() {
	"$PORTS/ios/build-unsigned-ipa.sh"
}

android_build >"$LOG/android.log" 2>&1 &
APID=$!
ios_build >"$LOG/ios.log" 2>&1 &
IPID=$!

A=0
I=0
wait "$APID" || A=$?
wait "$IPID" || I=$?
echo "==== android log ===="
cat "$LOG/android.log"
echo "==== ios log ===="
cat "$LOG/ios.log"
if [ "$A" -ne 0 ] || [ "$I" -ne 0 ]; then
	echo "FAIL  android=$A ios=$I"
	exit 1
fi

APK=$(find "$PORTS/android/app/build/outputs/apk/foss/release" -name '*.apk' | head -n 1)
IPA=$(find "$PORTS/ios/build" -name 'VCPort-*-unsigned-preview.ipa' | head -n 1)
echo "PASS  Android $APK"
echo "PASS  iOS $IPA"
echo "Sign iOS: VC_PORT_IOS_TEAM=YOUR10CHARID $PORTS/ios/sideload-sign.sh"
echo "Bundle id stays dev.shivampingale.vcport"
