#!/bin/sh
# Host test for wrap/unwrap. Works on macOS and Linux (CI).
set -e
SHARED="$(cd "$(dirname "$0")" && pwd)"
if [ -d "$SHARED/../../src/Volume" ]; then
	ROOT="$(cd "$SHARED/../.." && pwd)"
	SRC="$ROOT/src"
elif [ -d "$SHARED/../veracrypt/src/Volume" ]; then
	SRC="$(cd "$SHARED/../veracrypt/src" && pwd)"
elif [ -n "$VC_SRC" ]; then
	SRC="$VC_SRC"
else
	echo "Clone ShivamPingaleDev/Veracrypt_port next to this tree as veracrypt/, or set VC_SRC." >&2
	exit 1
fi

CC="${CC:-cc}"
CXX="${CXX:-c++}"
OBJ="$(mktemp -d "${TMPDIR:-/tmp}/vcport-wrap-obj.XXXXXX")"
OUT="${OBJ}/vcport-wrap-test"
trap 'rm -rf "$OBJ"' EXIT

UNAME_S="$(uname -s)"
UNAME_M="$(uname -m)"
CFLAGS="-O1 -fno-strict-aliasing -DTC_UNIX -DARGON2_NO_THREADS -DCRYPTOPP_DISABLE_X86ASM -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE"
case "$UNAME_S" in
	Darwin) CFLAGS="$CFLAGS -DTC_MACOSX" ;;
	*) CFLAGS="$CFLAGS -DTC_LINUX" ;;
esac
INCLUDES="-I$SRC -I$SRC/Crypto -I$SRC/Crypto/Argon2/include -I$SHARED"

compile_c() {
	$CC -c $CFLAGS $INCLUDES -o "$1" "$2"
}

compile_c "$OBJ/Aescrypt.o" "$SRC/Crypto/Aescrypt.c"
compile_c "$OBJ/Aeskey.o" "$SRC/Crypto/Aeskey.c"
compile_c "$OBJ/Aestab.o" "$SRC/Crypto/Aestab.c"
compile_c "$OBJ/Sha2.o" "$SRC/Crypto/Sha2.c"
compile_c "$OBJ/blake2b.o" "$SRC/Crypto/Argon2/src/blake2/blake2b.c"
compile_c "$OBJ/argon2.o" "$SRC/Crypto/Argon2/src/argon2.c"
compile_c "$OBJ/argon2core.o" "$SRC/Crypto/Argon2/src/core.c"
compile_c "$OBJ/argon2ref.o" "$SRC/Crypto/Argon2/src/ref.c"

AES_HW=""
case "$UNAME_M" in
	arm64|aarch64)
		$CC -c $CFLAGS $INCLUDES -march=armv8-a+crypto -o "$OBJ/Aes_hw.o" "$SRC/Crypto/Aes_hw_armv8.c"
		$CC -c $CFLAGS $INCLUDES -march=armv8-a+crypto -o "$OBJ/sha256_armv8.o" "$SRC/Crypto/sha256_armv8.c"
		AES_HW="$OBJ/Aes_hw.o $OBJ/sha256_armv8.o"
		;;
esac

$CXX -std=c++14 -c $CFLAGS $INCLUDES -o "$OBJ/vc_wrap.o" "$SHARED/vc_wrap.cpp"
$CXX -std=c++14 -c $CFLAGS $INCLUDES -o "$OBJ/test_wrap.o" "$SHARED/test_wrap_main.cpp"

# shellcheck disable=SC2086
$CXX -o "$OUT" "$OBJ/test_wrap.o" "$OBJ/vc_wrap.o" "$OBJ/Aescrypt.o" "$OBJ/Aeskey.o" "$OBJ/Aestab.o" \
	"$OBJ/Sha2.o" $AES_HW "$OBJ/blake2b.o" "$OBJ/argon2.o" "$OBJ/argon2core.o" "$OBJ/argon2ref.o"
echo "Running $OUT"
"$OUT"
