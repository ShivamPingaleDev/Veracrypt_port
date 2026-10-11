#!/bin/sh
# The contribution check. One command.
#
#   ports/tests/contrib.sh
#
# 1. Regressive session strings and progressive walk rows (test_agile).
# 2. Nuances that are not their own walk row (test_contrib).
# 3. Host crypto-safety and volume proofs, including quick and full format.
# 4. The UI walk on this Mac, Android and iOS together.
#
# VC_PORT_CONTRIB=host stops before the walk. That is a reviewer shortcut
# when this machine has no emulator. A phone change is not done until the
# walk itself has passed. GitHub Actions does not run this script.
# A failed stage prints its name and stops. Later stages, including the
# UI walk, do not start.
set -u

ROOT=$(CDPATH= git rev-parse --show-toplevel)
if [ -d "$ROOT/ports/android" ]; then
	PORTS="$ROOT/ports"
else
	PORTS="$ROOT"
fi

echo "VC Port contribution suite"
echo "regressive  old 10-phase session, must stay"
echo "progressive  walk features added on top of that session"
echo "nuances      host proofs for behavior the walk does not spell as its own row"
echo ""

stage() {
	name=$1
	shift
	echo ""
	echo "======== $name ========"
	if "$@"; then
		echo "PASS  $name"
		return 0
	fi
	echo "FAIL  $name"
	return 1
}

# Suite codes are process exits. They are not VC_ERR_* (-1..-7).
# 11 catalog. 12 crypto-safety. 13 volume fixture. 14 UI walk.

cd "$PORTS/tests"
if ! stage "catalog" python3 -m unittest test_agile test_contrib -v; then
	echo ""
	echo "FAIL  contribution suite"
	echo "suite code 11"
	echo "The catalog failed. Volume proofs and the UI walk were not started."
	exit 11
fi

if ! stage "host crypto-safety" "$PORTS/shared/run_crypto_safety_test.sh"; then
	echo ""
	echo "FAIL  contribution suite"
	echo "suite code 12"
	echo "Crypto-safety failed. The volume fixture and the UI walk were not started."
	exit 12
fi

if ! stage "host volume" "$PORTS/shared/run_volume_test.sh"; then
	echo ""
	echo "FAIL  contribution suite"
	echo "suite code 13"
	echo "The volume fixture failed. Stopped before the UI walk."
	exit 13
fi

if [ "${VC_PORT_CONTRIB:-full}" = "host" ]; then
	echo ""
	echo "HOST  catalog and native proofs passed."
	echo "UI walk was not run. A phone change still needs: ports/scripts/run-ui-walk.sh"
	exit 0
fi

if ! stage "UI walk" "$PORTS/scripts/run-ui-walk.sh"; then
	echo ""
	echo "FAIL  contribution suite"
	echo "suite code 14"
	echo "The UI walk failed. The catalog and host proofs had already passed."
	exit 14
fi

echo ""
echo "PASS  contribution suite"
