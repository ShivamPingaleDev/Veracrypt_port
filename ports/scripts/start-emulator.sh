#!/bin/sh
# Start Android emulator with a visible window. Keeps running after this script exits.
# Usage: ports/scripts/start-emulator.sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
# shellcheck disable=SC1091
. "$ROOT/scripts/android-dev.sh"
export VC_PORT_EMU_WINDOW=1
export VC_PORT_EMU_GPU="${VC_PORT_EMU_GPU:-angle_indirect}"
vcport_resolve_java || exit 1
if vcport_have_device && vcport_wait_boot 15; then
	echo "Emulator already running: $(vcport_adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
	exit 0
fi
vcport_ensure_emulator || exit 1
vcport_keep_awake
echo "Emulator window should stay open. Log: ${TMPDIR:-/tmp}/vcport-emu.log"
echo "Stop: adb emu kill"
