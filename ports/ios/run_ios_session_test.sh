#!/bin/sh
# Full app-interface session on iPad Simulator (skip Files/share sheets).
# Same coverage as Android AppInterfaceSessionTest. Does not tap Panic wipe.
# Skips when Xcode Simulator runtimes are missing (CI, sandbox).
set -eu
IOS="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
DERIVED="${IOS}/build/DerivedData"

if ! command -v xcodebuild >/dev/null 2>&1; then
	echo "SKIP  xcodebuild not on PATH"
	exit 0
fi
if ! command -v xcodegen >/dev/null 2>&1; then
	echo "SKIP  xcodegen not on PATH (brew install xcodegen)"
	exit 0
fi

if ! xcrun simctl list devices available >/tmp/vcport-ipad-sims.txt 2>/tmp/vcport-ipad-sims.err; then
	echo "SKIP  iOS Simulator service not available"
	exit 0
fi

first_ipad() {
	want="$1"
	awk -v want="$want" '
		/iPad/ && $0 ~ want {
			n = split($0, a, /[()]/)
			for (i = 1; i <= n; i++) {
				gsub(/ /, "", a[i])
				if (a[i] ~ /^[0-9A-Fa-f-]{36}$/) { print a[i]; exit }
			}
		}
	' /tmp/vcport-ipad-sims.txt
}

UDID="$(first_ipad Booted)"
if [ -z "$UDID" ]; then
	UDID="$(first_ipad Shutdown)"
fi
if [ -z "$UDID" ]; then
	echo "SKIP  no iPad Simulator runtime (Xcode → Settings → Components)"
	exit 0
fi

echo "iPad Simulator $UDID"
xcrun simctl boot "$UDID" >/dev/null 2>&1 || true
open -a Simulator --args -CurrentDeviceUDID "$UDID" >/dev/null 2>&1 || true
i=0
while [ "$i" -lt 36 ]; do
	state=$(xcrun simctl list devices | awk -v id="$UDID" '$0 ~ id {print; exit}')
	case "$state" in
		*Booted*) break ;;
	esac
	i=$((i + 1))
	sleep 5
done

cd "$IOS"
IOS_SDK=iphonesimulator IOS_ARCH="$(uname -m)" ./build-native.sh
xcodegen generate

xcodebuild -project VCPort.xcodeproj -scheme VCPort -configuration Debug \
	-destination "id=$UDID" \
	-derivedDataPath "$DERIVED" \
	CODE_SIGNING_ALLOWED=NO \
	CODE_SIGNING_REQUIRED=NO \
	-maximum-test-execution-time-allowance 1800 \
	-only-testing:VCPortTests/AppInterfaceSessionTests/testCreateSaveWipeReopenMountTransferAndSecurity \
	test
