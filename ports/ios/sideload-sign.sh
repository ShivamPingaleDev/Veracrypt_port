#!/bin/sh
# Sign a development IPA with your Apple ID (the name on the Apple Development cert).
# Sideload that IPA with Finder, Apple Configurator, or Xcode onto your iPad.
# Simulator (no Apple ID): ports/ios/run_ipad_sim.sh
set -eu
IOS="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
OUT="${IOS}/build/sideload"
ARCHIVE="${OUT}/VCPort.xcarchive"
EXPORT_PLIST="${OUT}/ExportOptions.plist"

TEAM="${VC_PORT_IOS_TEAM:-}"
if [ -z "$TEAM" ] && [ -f "${IOS}/Signing.local.xcconfig" ]; then
	TEAM="$(awk -F= '/^[[:space:]]*DEVELOPMENT_TEAM[[:space:]]*=/{gsub(/[[:space:]]/,"",$2); print $2; exit}' "${IOS}/Signing.local.xcconfig")"
fi

if [ -z "$TEAM" ]; then
	echo "No Team ID for device signing."
	echo "1. Xcode → Settings → Accounts → add your Apple ID."
	echo "2. Open ports/ios/VCPort.xcodeproj → Signing & Capabilities → Team → your name."
	echo "3. Or write a gitignored team file and re-run:"
	echo "     echo 'DEVELOPMENT_TEAM = YOUR10CHARID' > ports/ios/Signing.local.xcconfig"
	echo "   The 10-character Team ID is on your Apple Developer membership page."
	echo "Free Apple ID: 7-day cert, your iPad must be plugged in the first time."
	exit 1
fi

if ! command -v xcodegen >/dev/null 2>&1; then
	echo "install xcodegen (brew install xcodegen)" >&2
	exit 1
fi

mkdir -p "$OUT"
cd "$IOS"
IOS_SDK=iphoneos IOS_ARCH=arm64 ./build-native.sh
xcodegen generate

cat > "$EXPORT_PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>method</key>
	<string>development</string>
	<key>teamID</key>
	<string>${TEAM}</string>
	<key>signingStyle</key>
	<string>automatic</string>
	<key>compileBitcode</key>
	<false/>
	<key>destination</key>
	<string>export</string>
	<key>signingCertificate</key>
	<string>Apple Development</string>
</dict>
</plist>
EOF

xcodebuild -project VCPort.xcodeproj -scheme VCPort -configuration Release \
	-destination 'generic/platform=iOS' \
	-archivePath "$ARCHIVE" \
	-allowProvisioningUpdates \
	DEVELOPMENT_TEAM="$TEAM" \
	CODE_SIGN_STYLE=Automatic \
	CODE_SIGN_IDENTITY="Apple Development" \
	archive

xcodebuild -exportArchive \
	-archivePath "$ARCHIVE" \
	-exportPath "$OUT" \
	-exportOptionsPlist "$EXPORT_PLIST" \
	-allowProvisioningUpdates

echo "Signed IPA under $OUT (install on your iPad with Finder or Xcode)."
ls -l "$OUT"/*.ipa
