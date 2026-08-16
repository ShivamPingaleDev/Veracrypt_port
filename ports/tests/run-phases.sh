#!/bin/sh
# Ten-phase test pass for the pre-public hardening cycle.
#
#   ports/tests/run-phases.sh
#
# Phases 1, 3–10 are contracts. Phase 2 is wrap + known-password FAT volume.
# Overlay check is phase 8. Live GitHub visibility is phase 10.

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

echo "VC Port 10-phase testing"
echo "root  $ROOT"
echo "host  $(uname -s) $(uname -m)"
python3 --version

run "phase 1 honesty freeze" sh -c 'cd ports/tests && python3 -m unittest test_phases.Phase1HonestyFreezeTests -v'
run "phase 2 wrap/crypto" ports/shared/run_wrap_test.sh
run "phase 2 volume FAT fixture" ports/shared/run_volume_test.sh
run "phase 3 FAT folders" sh -c 'cd ports/tests && python3 -m unittest test_phases.Phase3FatFolderTests -v'
run "phase 4 Android" sh -c 'cd ports/tests && python3 -m unittest test_phases.Phase4AndroidTests -v'
run "phase 5 iOS" sh -c 'cd ports/tests && python3 -m unittest test_phases.Phase5IosTests -v'
run "phase 6 desktop" sh -c 'cd ports/tests && python3 -m unittest test_phases.Phase6DesktopTests -v'
run "phase 7 manifest integrity" sh -c 'cd ports/tests && python3 -m unittest test_phases.Phase7ManifestTests -v'
run "phase 8 CI contracts" sh -c 'cd ports/tests && python3 -m unittest test_phases.Phase8CiTests -v'

PIN=$(tr -d '[:space:]' < ports/UPSTREAM_COMMIT)
if git cat-file -e "$PIN^{commit}" 2>/dev/null; then
	run "phase 8 overlay inventories --check" scripts/refresh-overlay.sh --check
else
	echo "SKIP  phase 8 overlay inventories (pin $PIN not in this clone; use fetch-depth: 0)"
	echo "SKIP  phase 8 overlay inventories --check" >> "$SUMMARY"
fi

if [ -x scripts/check-upstream-layout.sh ]; then
	run "phase 8 upstream Crypto/Volume layout" scripts/check-upstream-layout.sh
fi

run "phase 9 legal/version" sh -c 'cd ports/tests && python3 -m unittest test_phases.Phase9LegalVersionTests -v'
run "phase 9 version + device contracts" python3 ports/tests/test_contracts.py
run "phase 9 VCF2 factor codec" python3 ports/tests/test_factors.py
run "phase 9 panic wipe semantics" python3 ports/tests/test_wipe.py
run "phase 10 relaunch contracts" sh -c 'cd ports/tests && python3 -m unittest test_phases.Phase10RelaunchTests -v'

phase10_github() {
	if ! command -v gh >/dev/null 2>&1; then
		echo "SKIP  gh not installed"
		return 0
	fi
	if ! gh auth status >/dev/null 2>&1; then
		echo "SKIP  gh not authenticated"
		return 0
	fi
	priv=$(gh repo view ShivamPingaleDev/Veracrypt_port --json isPrivate --jq .isPrivate)
	echo "Veracrypt_port isPrivate=$priv"
	if [ "$priv" != "false" ]; then
		echo "FAIL  repository must be public for TrueCrypt 3.0 source-available"
		return 1
	fi
	remote_tag=$(gh api repos/ShivamPingaleDev/Veracrypt_port/git/refs/tags/v0.3.0 --jq .object.sha)
	echo "remote tag v0.3.0 -> $remote_tag"
	[ -n "$remote_tag" ]
}

run "phase 10 public GitHub + tag v0.3.0" phase10_github

echo ""
echo "======== 10-phase summary ========"
cat "$SUMMARY"
if [ "$FAIL" -ne 0 ]; then
	echo "10-PHASE TESTS FAILED"
	exit 1
fi
echo "10-PHASE TESTS PASSED"
exit 0
