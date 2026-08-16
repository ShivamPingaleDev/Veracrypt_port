#!/bin/sh
# Exit 1 if CMake lists a VeraCrypt file that is gone.
# Exit 2 if Crypto/Volume has a file that is neither compiled nor in mobile-skip.txt
# (that is how new VeraCrypt ciphers show up after a merge).
#
#   scripts/check-upstream-layout.sh

set -eu

ROOT=$(CDPATH= git rev-parse --show-toplevel)
cd "$ROOT"

CMAKE=ports/shared/upstream-sources.cmake
SKIP=ports/overlay/mobile-skip.txt
VC_SRC=${VC_SRC:-src}

if [ ! -f "$CMAKE" ]; then
	echo "Missing $CMAKE" >&2
	exit 1
fi
if [ ! -f "$SKIP" ]; then
	echo "Missing $SKIP (run scripts/refresh-overlay.sh)" >&2
	exit 1
fi

listed=$(mktemp)
found=$(mktemp)
skip=$(mktemp)
trap 'rm -f "$listed" "$found" "$skip"' EXIT

grep -E '\$\{VC_SRC\}/' "$CMAKE" | sed 's/.*\${VC_SRC}\///; s/).*//; s/[[:space:]]*$//' | sort -u > "$listed"
grep -v '^#' "$SKIP" | grep -v '^$' | sort -u > "$skip"

missing=0
while IFS= read -r rel; do
	[ -z "$rel" ] && continue
	if [ ! -f "$VC_SRC/$rel" ]; then
		echo "MISSING (listed in cmake, not on disk): $VC_SRC/$rel"
		missing=1
	fi
done < "$listed"

: > "$found"
for dir in Crypto Volume; do
	[ -d "$VC_SRC/$dir" ] || continue
	find "$VC_SRC/$dir" -type f \( -name '*.c' -o -name '*.cpp' \) ! -path '*/Argon2/src/test*' \
		| sed "s|^$VC_SRC/||" >> "$found"
done
sort -u "$found" -o "$found"

extra=0
while IFS= read -r rel; do
	[ -z "$rel" ] && continue
	grep -Fxq "$rel" "$listed" && continue
	grep -Fxq "$rel" "$skip" && continue
	echo "NEW upstream source not in cmake or mobile-skip.txt: $rel"
	extra=1
done < "$found"

if [ "$missing" = 1 ]; then
	echo "CMake lists files that VeraCrypt no longer ships. Update $CMAKE."
	exit 1
fi
if [ "$extra" = 1 ]; then
	echo "VeraCrypt added Crypto/Volume units. Add them to $CMAKE or to $SKIP via refresh-overlay.sh after review."
	exit 2
fi
echo "Upstream layout matches $CMAKE (pin $(tr -d '[:space:]' < ports/UPSTREAM_COMMIT))."
