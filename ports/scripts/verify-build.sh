#!/bin/sh
# Reproducible check: this git commit/tag → SHA-256 of the FOSS APK and unsigned IPA.
#
# GitHub Actions APKs are debug-signed previews. Do not write those hashes into
# version.json (hash_release.py refuses CN=Android Debug). Freeze trust is a
# reviewer rebuilding FOSS locally and comparing the printed SHA-256.
#
# Usage:
#   ports/scripts/verify-build.sh                 # hash last build-phones outputs
#   ports/scripts/verify-build.sh --rebuild       # build-phones.sh, then hash
#   ports/scripts/verify-build.sh --hash-only APK [IPA]
set -eu
PORTS="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
ROOT="$(CDPATH= cd -- "$PORTS/.." && pwd)"

sha256_of() {
	if command -v sha256sum >/dev/null 2>&1; then
		sha256sum "$1" | awk '{print $1}'
	else
		shasum -a 256 "$1" | awk '{print $1}'
	fi
}

print_git() {
	cd "$ROOT"
	COMMIT="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
	TAG="$(git describe --tags --exact-match 2>/dev/null || true)"
	DIRTY=""
	if ! git diff --quiet 2>/dev/null || ! git diff --cached --quiet 2>/dev/null; then
		DIRTY=" dirty-worktree"
	fi
	echo "git:    $COMMIT$DIRTY"
	if [ -n "$TAG" ]; then
		echo "tag:    $TAG"
	else
		echo "tag:    (none at HEAD)"
	fi
	if [ -f "$PORTS/version.json" ]; then
		echo "port:   $(python3 -c 'import json,sys; print(json.load(open(sys.argv[1])).get("port_version",""))' "$PORTS/version.json")"
	fi
}

hash_one() {
	KIND="$1"
	FILE="$2"
	if [ -z "$FILE" ] || [ ! -f "$FILE" ]; then
		echo "$KIND:    (missing)"
		return 1
	fi
	SUM="$(sha256_of "$FILE")"
	NOTE=""
	case "$FILE" in
		*debug*) NOTE="  [debug-signed preview — not for version.json]" ;;
	esac
	echo "$KIND:    $SUM  $FILE$NOTE"
}

APK=""
IPA=""
REBUILD=0
if [ "${1:-}" = "--rebuild" ]; then
	REBUILD=1
	shift
elif [ "${1:-}" = "--hash-only" ]; then
	shift
	APK="${1:-}"
	IPA="${2:-}"
	if [ -z "$APK" ]; then
		echo "usage: ports/scripts/verify-build.sh --hash-only APK [IPA]" >&2
		exit 2
	fi
fi

print_git
echo "note:   GitHub APKs stay debug-signed previews. Rebuild FOSS here and compare."
echo "note:   Do not write debug hashes into version.json."

if [ "$REBUILD" -eq 1 ]; then
	"$PORTS/scripts/build-phones.sh"
fi

if [ -z "$APK" ]; then
	APK="$(find "$PORTS/android/app/build/outputs/apk/foss/release" -name '*.apk' 2>/dev/null | head -n 1 || true)"
fi
if [ -z "$IPA" ]; then
	IPA="$(find "$PORTS/ios/build" -name 'VCPort-*-unsigned-preview.ipa' 2>/dev/null | head -n 1 || true)"
fi

FAIL=0
hash_one "apk" "$APK" || FAIL=1
hash_one "ipa" "$IPA" || FAIL=1
if [ "$FAIL" -ne 0 ]; then
	echo "rebuild: $PORTS/scripts/build-phones.sh"
	echo "then:    $PORTS/scripts/verify-build.sh"
	exit 1
fi
echo "PASS  hashes printed. Compare to a self-built copy of this tag."
