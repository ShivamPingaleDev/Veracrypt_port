#!/bin/sh
# Host test runner for every VC Port surface that does not need a phone
# or an iOS simulator. Safe to run on a remote Mac/Linux box
# and in GitHub Actions. Delegates to the 10-phase runner.
#
#   ports/tests/run-all.sh   (Veracrypt_port)
#   tests/run-all.sh         (VCPort)

set -eu

ROOT=$(CDPATH= git rev-parse --show-toplevel)
if [ -d "$ROOT/ports/tests" ]; then
	exec "$ROOT/ports/tests/run-phases.sh"
fi
exec "$ROOT/tests/run-phases.sh"
