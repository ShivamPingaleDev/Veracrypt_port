#!/bin/sh
# Sourced by phone scripts. Resolves Java 17 and boots AVD vcport-api35
# without treating the emulator launcher PID as the qemu process (that
# PID can exit while qemu is still starting, or qemu can outlive it).
# Default is headless SwiftShader: a windowed GPU boot dies when the
# parent Cursor/agent shell exits. VC_PORT_EMU_WINDOW=1 keeps a window.

vcport_resolve_java() {
	if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
		export PATH="$JAVA_HOME/bin:$PATH"
		return 0
	fi
	if [ -x /usr/libexec/java_home ]; then
		_jh="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
		if [ -n "$_jh" ] && [ -x "$_jh/bin/java" ]; then
			export JAVA_HOME="$_jh"
			export PATH="$JAVA_HOME/bin:$PATH"
			return 0
		fi
	fi
	for _cand in \
		/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
		/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
		"$HOME/.jdks/jdk-17.0.20+8/Contents/Home"
	do
		if [ -x "$_cand/bin/java" ]; then
			export JAVA_HOME="$_cand"
			export PATH="$JAVA_HOME/bin:$PATH"
			return 0
		fi
	done
	if command -v brew >/dev/null 2>&1; then
		_b="$(brew --prefix openjdk@17 2>/dev/null || true)"
		if [ -n "$_b" ] && [ -x "$_b/libexec/openjdk.jdk/Contents/Home/bin/java" ]; then
			export JAVA_HOME="$_b/libexec/openjdk.jdk/Contents/Home"
			export PATH="$JAVA_HOME/bin:$PATH"
			return 0
		fi
	fi
	echo "FAIL  no Java 17 (set JAVA_HOME, or brew install openjdk@17)" >&2
	return 1
}

vcport_android_sdk() {
	export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
	export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
	if [ -x "$ANDROID_HOME/platform-tools/adb" ]; then
		export PATH="$ANDROID_HOME/platform-tools:$PATH"
	fi
}

vcport_adb() {
	if [ -n "${VCPORT_ADB:-}" ] && [ -x "$VCPORT_ADB" ]; then
		echo "$VCPORT_ADB"
		return 0
	fi
	if command -v adb >/dev/null 2>&1; then
		command -v adb
		return 0
	fi
	if [ -x "${ANDROID_HOME:-}/platform-tools/adb" ]; then
		echo "$ANDROID_HOME/platform-tools/adb"
		return 0
	fi
	return 1
}

vcport_have_device() {
	_adb="$(vcport_adb)" || return 1
	"$_adb" devices 2>/dev/null | awk 'NR>1 && $2=="device" {found=1} END {exit found?0:1}'
}

vcport_wait_boot() {
	_adb="$(vcport_adb)" || return 1
	_i=0
	_limit="${1:-60}"
	while [ "$_i" -lt "$_limit" ]; do
		if vcport_have_device; then
			_boot="$("$_adb" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
			if [ "$_boot" = "1" ]; then
				"$_adb" shell input keyevent 82 >/dev/null 2>&1 || true
				return 0
			fi
		fi
		_i=$((_i + 1))
		sleep 4
	done
	return 1
}

vcport_emulator_running() {
	_avd="${1:-${VC_PORT_AVD:-vcport-api35}}"
	pgrep -f "qemu-system" >/dev/null 2>&1 || pgrep -f "[e]mulator.*${_avd}" >/dev/null 2>&1
}

vcport_adb_has_emulator() {
	_adb="$(vcport_adb)" || return 1
	"$_adb" devices 2>/dev/null | awk 'NR>1 && $1 ~ /^emulator-/ {found=1} END {exit found?0:1}'
}

# 0 = a device is ready. 1 = cannot boot (caller should fail, not skip).
# Waits on adb `device` + sys.boot_completed. Never treats the emulator
# launcher PID as qemu (that PID can exit while qemu is still starting).
# nohup so a Cursor/agent shell exit does not SIGHUP qemu.
vcport_ensure_emulator() {
	vcport_android_sdk
	_adb="$(vcport_adb)" || {
		echo "FAIL  adb not found under ANDROID_HOME=$ANDROID_HOME" >&2
		return 1
	}
	VCPORT_ADB="$_adb"
	"$_adb" start-server >/dev/null 2>&1 || true
	_avd="${VC_PORT_AVD:-vcport-api35}"
	_boot_tries="${VC_PORT_EMU_BOOT_TRIES:-90}"
	if vcport_have_device; then
		if vcport_wait_boot 20; then
			echo "Using existing Android device ($("$_adb" devices | awk 'NR>1 && $2=="device" {print $1; exit}'))"
			return 0
		fi
	fi
	# A second AVD on the same adb port is the usual "emulator never starts" failure.
	if vcport_adb_has_emulator || vcport_emulator_running "$_avd"; then
		echo "Waiting for existing AVD $_avd (do not start a second emulator)..."
		if vcport_wait_boot "$_boot_tries"; then
			echo "Android emulator ready"
			return 0
		fi
		echo "FAIL  existing emulator did not reach boot_completed (log ${TMPDIR:-/tmp}/vcport-emu.log)" >&2
		"$_adb" devices -l >&2 || true
		tail -20 "${TMPDIR:-/tmp}/vcport-emu.log" >&2 || true
		return 1
	fi
	_emu="$ANDROID_HOME/emulator/emulator"
	if [ ! -x "$_emu" ]; then
		echo "FAIL  emulator binary missing: $_emu" >&2
		return 1
	fi
	if ! "$_emu" -list-avds 2>/dev/null | grep -qx "$_avd"; then
		echo "FAIL  AVD $_avd not found. Create it, or set VC_PORT_AVD." >&2
		return 1
	fi
	_gpu="swiftshader_indirect"
	_win="-no-window"
	if [ "${VC_PORT_EMU_WINDOW:-0}" = "1" ]; then
		_gpu="host"
		_win=""
	fi
	: >"${TMPDIR:-/tmp}/vcport-emu.log"
	echo "Starting AVD $_avd (gpu=$_gpu ${_win:-windowed})..."
	# shellcheck disable=SC2086
	nohup "$_emu" -avd "$_avd" \
		-no-snapshot-load -no-snapshot-save -no-boot-anim -no-audio \
		-gpu "$_gpu" $_win \
		-netdelay none -netspeed full \
		</dev/null >>"${TMPDIR:-/tmp}/vcport-emu.log" 2>&1 &
	VCPORT_EMU_PID=$!
	if vcport_wait_boot "$_boot_tries"; then
		echo "Android emulator ready"
		return 0
	fi
	echo "FAIL  emulator did not reach boot_completed (pid ${VCPORT_EMU_PID:-?} log ${TMPDIR:-/tmp}/vcport-emu.log)" >&2
	"$_adb" devices -l >&2 || true
	tail -40 "${TMPDIR:-/tmp}/vcport-emu.log" >&2 || true
	return 1
}
