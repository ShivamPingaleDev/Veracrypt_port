#!/bin/sh
# Unsigned iPhone IPA for AltStore / SideStore to sign with the user's Apple ID.
# GitHub CI and ports/scripts/build-phones.sh both call this. No Apple cert here.
set -euo pipefail
IOS="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
PORTS="$(CDPATH= cd -- "$IOS/.." && pwd)"
VER="$(python3 -c "import json; print(json.load(open('$PORTS/version.json'))['port_version'])")"
DERIVED="${IOS}/build/DerivedDataRelease"
OUT="${IOS}/build/VCPort-${VER}-unsigned-preview.ipa"

if ! command -v xcodegen >/dev/null 2>&1; then
	echo "install xcodegen (brew install xcodegen)" >&2
	exit 1
fi
if ! command -v xcodebuild >/dev/null 2>&1; then
	echo "xcodebuild not on PATH" >&2
	exit 1
fi

cd "$IOS"
IOS_SDK=iphoneos IOS_ARCH=arm64 ./build-native.sh
xcodegen generate

xcodebuild -project VCPort.xcodeproj -scheme VCPort -configuration Release \
	-destination 'generic/platform=iOS' \
	-derivedDataPath "$DERIVED" \
	CODE_SIGNING_ALLOWED=NO \
	CODE_SIGNING_REQUIRED=NO \
	build

APP=$(find "$DERIVED" -name 'VCPort.app' -path '*/Release-iphoneos/*' | head -n 1)
if [ -z "$APP" ] || [ ! -d "$APP" ]; then
	echo "FAIL  VCPort.app not in $DERIVED" >&2
	exit 1
fi

STAGE="${IOS}/build/ipa-unsigned"
rm -rf "$STAGE"
mkdir -p "$STAGE/Payload"
cp -R "$APP" "$STAGE/Payload/"
rm -rf "$STAGE/Payload/VCPort.app/_CodeSignature"
(cd "$STAGE" && zip -qr "$OUT" Payload)
echo "Wrote $OUT"
ls -lh "$OUT"
