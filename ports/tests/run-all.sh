#!/bin/sh
# Host test runner for every VC Port surface that does not need a phone,
# an iOS simulator, or a FUSE-T mount. Safe to run on a remote Mac/Linux box
# and in GitHub Actions.
#
#   ports/tests/run-all.sh

set -eu

ROOT=$(CDPATH= git rev-parse --show-toplevel)
cd "$ROOT"

FAIL=0
SUMMARY="$(mktemp)"
trap 'rm -f "$SUMMARY"' EXIT

run() {
	name=$1
	shift
	echo ""
	echo "======== $name ========"
	if "$@"; then
		echo "PASS  $name" | tee -a "$SUMMARY"
	else
		echo "FAIL  $name" | tee -a "$SUMMARY"
		FAIL=1
	fi
}

echo "VC Port host tests"
echo "root  $ROOT"
echo "host  $(uname -s) $(uname -m)"
python3 --version

run "wrap/crypto (Argon2id + AES-CTR)" ports/shared/run_wrap_test.sh
run "volume open/list/export (FAT fixture)" ports/shared/run_volume_test.sh
run "version + device contracts" python3 ports/tests/test_contracts.py
run "VCF2 factor codec + semver" python3 ports/tests/test_factors.py
run "panic wipe semantics" python3 ports/tests/test_wipe.py

if [ -x scripts/check-upstream-layout.sh ]; then
	run "upstream Crypto/Volume layout" scripts/check-upstream-layout.sh
fi

PIN=$(tr -d '[:space:]' < ports/UPSTREAM_COMMIT)
if git cat-file -e "$PIN^{commit}" 2>/dev/null; then
	run "overlay inventories --check" scripts/refresh-overlay.sh --check
else
	echo "SKIP  overlay inventories (pin $PIN not in this clone; use fetch-depth: 0)"
	echo "SKIP  overlay inventories --check" >> "$SUMMARY"
fi

if [ "${VCPORT_TEST_EXPERIMENTAL:-}" = "1" ] && [ -f experimental/pure-c/Makefile ]; then
	run "experimental C lab (local only)" make -C experimental/pure-c test
fi

echo ""
echo "======== summary ========"
cat "$SUMMARY"
if [ "$FAIL" -ne 0 ]; then
	echo "HOST TESTS FAILED"
	exit 1
fi
echo "HOST TESTS PASSED"
exit 0
