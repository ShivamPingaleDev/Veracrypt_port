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
set -eu

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

cd "$PORTS/tests"
python3 -m unittest test_agile test_contrib -v

echo ""
echo "======== host crypto-safety ========"
"$PORTS/shared/run_crypto_safety_test.sh"
echo ""
echo "======== host volume ========"
"$PORTS/shared/run_volume_test.sh"

if [ "${VC_PORT_CONTRIB:-full}" = "host" ]; then
	echo ""
	echo "HOST  catalog and native proofs passed."
	echo "UI walk was not run. A phone change still needs: ports/scripts/run-ui-walk.sh"
	exit 0
fi

echo ""
echo "======== UI walk ========"
exec "$PORTS/scripts/run-ui-walk.sh"
