#!/bin/sh
# Run one test binary and keep a local leak report.
#
# Linux: valgrind --leak-check=full (not combined with AddressSanitizer).
# macOS: leaks(1) with malloc stack logs. Valgrind does not run on this OS.
# AddressSanitizer builds: verbose ASan stats. ASan and valgrind/leaks do not mix.
#
# The report stays on this machine. This script does not upload it.
#
#   ports/shared/leak_report.sh /path/to/test [args...]
#   VC_LEAK=0 ports/shared/leak_report.sh /path/to/test
set -u

if [ "$#" -lt 1 ] || [ ! -x "$1" ]; then
	echo "usage: leak_report.sh binary [args...]" >&2
	exit 2
fi

BIN=$1
shift
LOGDIR="${VC_LEAK_DIR:-${TMPDIR:-/tmp}/vcport-leak-logs}"
mkdir -p "$LOGDIR"
chmod 700 "$LOGDIR" 2>/dev/null || true
NAME=$(basename "$BIN")
REPORT="$LOGDIR/${NAME}.leak.txt"
umask 077

asan_binary() {
	if [ "${VC_ASAN:-}" = "1" ]; then
		return 0
	fi
	if command -v otool >/dev/null 2>&1 && otool -L "$BIN" 2>/dev/null | grep -q 'asan'; then
		return 0
	fi
	if command -v ldd >/dev/null 2>&1 && ldd "$BIN" 2>/dev/null | grep -q 'asan'; then
		return 0
	fi
	return 1
}

echo ""
echo "======== leak report: $NAME ========"
echo "binary  $BIN"
echo "report  $REPORT"
echo "This report is local. It is not uploaded."

if [ "${VC_LEAK:-1}" = "0" ]; then
	echo "tool    off (VC_LEAK=0)"
	set +e
	"$BIN" "$@"
	rc=$?
	set -e
	exit "$rc"
fi

if asan_binary; then
	# Apple's ASan has no LeakSanitizer. detect_leaks=1 aborts before the test runs.
	if [ "$(uname -s)" = "Darwin" ]; then
		echo "tool    AddressSanitizer verbose stats (detect_leaks=0 on macOS; not valgrind, not leaks)"
		DEFAULT_ASAN="halt_on_error=1:abort_on_error=1:detect_leaks=0:print_stats=1:verbosity=1"
	else
		echo "tool    AddressSanitizer verbose stats (detect_leaks=1; not valgrind, not leaks)"
		DEFAULT_ASAN="halt_on_error=1:abort_on_error=1:detect_leaks=1:print_stats=1:verbosity=1"
	fi
	export ASAN_OPTIONS="${ASAN_OPTIONS:-$DEFAULT_ASAN}"
	export UBSAN_OPTIONS="${UBSAN_OPTIONS:-halt_on_error=1:print_stacktrace=1}"
	set +e
	"$BIN" "$@" >"$REPORT" 2>&1
	rc=$?
	set -e
	cat "$REPORT"
	echo "LEAK  asan exit $rc"
	exit "$rc"
fi

if command -v valgrind >/dev/null 2>&1; then
	echo "tool    valgrind --leak-check=full --show-leak-kinds=all --track-origins=yes"
	set +e
	valgrind \
		--error-exitcode=99 \
		--leak-check=full \
		--show-leak-kinds=all \
		--track-origins=yes \
		--verbose \
		--log-file="$REPORT" \
		"$BIN" "$@"
	rc=$?
	set -e
	echo "----- valgrind log -----"
	cat "$REPORT"
	echo "LEAK  valgrind exit $rc"
	exit "$rc"
fi

if command -v leaks >/dev/null 2>&1; then
	echo "tool    leaks --atExit --fullStacks --hex (MallocStackLogging)"
	RUNBIN=$BIN
	if command -v codesign >/dev/null 2>&1; then
		RUNBIN="$LOGDIR/$NAME.signed"
		cp "$BIN" "$RUNBIN"
		ENT="$LOGDIR/get-task-allow.plist"
		if [ ! -f "$ENT" ]; then
			cat >"$ENT" <<'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
	<key>com.apple.security.get-task-allow</key><true/>
</dict></plist>
EOF
		fi
		codesign -s - -f --entitlements "$ENT" "$RUNBIN" >/dev/null 2>&1 || RUNBIN=$BIN
	fi
	set +e
	env MallocStackLogging=1 MallocStackLoggingNoCompact=1 \
		leaks --atExit --fullStacks --hex --list -- "$RUNBIN" "$@" >"$REPORT" 2>&1
	rc=$?
	set -e
	cat "$REPORT"
	echo "LEAK  leaks exit $rc"
	exit "$rc"
fi

echo "tool    none (install valgrind on Linux; macOS already has leaks)"
set +e
"$BIN" "$@"
rc=$?
set -e
exit "$rc"
