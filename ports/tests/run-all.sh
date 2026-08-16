#!/bin/sh
# Host test runner for every VC Port surface that does not need a phone,
# an iOS simulator, or a FUSE-T mount. Safe to run on a remote Mac/Linux box
# and in GitHub Actions. Delegates to the 10-phase runner.
#
#   ports/tests/run-all.sh

set -eu

ROOT=$(CDPATH= git rev-parse --show-toplevel)
exec "$ROOT/ports/tests/run-phases.sh"
