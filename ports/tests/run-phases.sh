#!/bin/sh
# Host test pass: honesty freeze, wrap + FAT volume, Python contracts, overlay,
# then public GitHub visibility.
#
#   ports/tests/run-phases.sh

set -eu

ROOT=$(CDPATH= git rev-parse --show-toplevel)
if [ -d "$ROOT/ports/android" ]; then
	PORTS="$ROOT/ports"
else
	PORTS="$ROOT"
fi
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

run "phase 1 honesty freeze" sh -c "cd \"$PORTS/tests\" && python3 -m unittest test_phases.Phase1HonestyFreezeTests -v"
run "phase 2 wrap/crypto" "$PORTS/shared/run_wrap_test.sh"
run "phase 2 crypto safety ASan" "$PORTS/shared/run_crypto_safety_test.sh"
run "phase 2 volume FAT fixture" "$PORTS/shared/run_volume_test.sh"
run "phase 2 volume lifecycle" "$PORTS/shared/run_lifecycle_test.sh"
run "phases 3-8 python" sh -c "cd \"$PORTS/tests\" && python3 -m unittest \
	test_phases.Phase3FatFolderTests \
	test_phases.Phase4AndroidTests \
	test_phases.Phase5IosTests \
	test_phases.Phase6ArchiveTests \
	test_phases.Phase7ManifestTests \
	test_phases.Phase8CiTests -v"

PIN=$(tr -d '[:space:]' < "$PORTS/UPSTREAM_COMMIT")
if git cat-file -e "$PIN^{commit}" 2>/dev/null; then
	run "phase 8 overlay inventories --check" "$ROOT/scripts/refresh-overlay.sh" --check
else
	echo "SKIP  phase 8 overlay inventories (pin $PIN not in this clone; use fetch-depth: 0)"
	echo "SKIP  phase 8 overlay inventories --check" >> "$SUMMARY"
fi

if [ -x "$ROOT/scripts/check-upstream-layout.sh" ]; then
	run "phase 8 upstream Crypto/Volume layout" "$ROOT/scripts/check-upstream-layout.sh"
fi

run "phase 9 python" sh -c "cd \"$PORTS/tests\" && python3 -m unittest \
	test_phases.Phase9LegalVersionTests \
	test_contracts test_factors test_wipe test_quality -v"
run "phase 9 official VeraCrypt pin" python3 "$PORTS/scripts/check_veracrypt_release.py" --pin-only
run "phase 9 emulator NativeBridge+UI" "$PORTS/android/run_device_sim.sh"
run "phase 9 iPad Simulator" "$PORTS/ios/run_ipad_sim.sh"
run "phase 9 iOS app-interface session" "$PORTS/ios/run_ios_session_test.sh"
run "phase 10 relaunch contracts" sh -c "cd \"$PORTS/tests\" && python3 -m unittest test_phases.Phase10RelaunchTests -v"

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
	ver=$(python3 -c "import json; print(json.load(open(\"$PORTS/version.json\"))[\"port_version\"])")
	remote_tag=$(gh api "repos/ShivamPingaleDev/Veracrypt_port/git/refs/tags/v${ver}" --jq .object.sha)
	echo "remote tag v${ver} -> $remote_tag"
	[ -n "$remote_tag" ]
}

run "phase 10 public GitHub + current version tag" phase10_github

echo ""
echo "======== 10-phase summary ========"
cat "$SUMMARY"
if [ "$FAIL" -ne 0 ]; then
	echo "10-PHASE TESTS FAILED"
	exit 1
fi
echo "10-PHASE TESTS PASSED"
exit 0
