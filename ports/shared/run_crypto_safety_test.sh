#!/bin/sh
# ASan/UBSan crypto-safety tests + JNI host syntax check.
# Does not run libFuzzer (see Makefile.crypto-safety fuzz).
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
OBJ="${VC_CRYPTO_OBJ:-${TMPDIR:-/tmp}/vcport-crypto-safety-obj}"
mkdir -p "$OBJ"
OUT="$OBJ/vcport-crypto-safety-test"

UNAME_S="$(uname -s)"
UNAME_M="$(uname -m)"
SAN="${VC_SANITIZE:--fsanitize=address,undefined -fno-omit-frame-pointer}"
CFLAGS="-O1 -g -fno-strict-aliasing -fstack-protector-strong -fno-common -DTC_UNIX -DTC_PORT_NO_TOKEN -DARGON2_NO_THREADS -DCRYPTOPP_DISABLE_X86ASM -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE $SAN"
case "$UNAME_S" in
	Darwin) CFLAGS="$CFLAGS -DTC_MACOSX" ;;
	*) CFLAGS="$CFLAGS -DTC_LINUX" ;;
esac
case "$UNAME_M" in
	x86_64|amd64|i686|i386)
		CFLAGS="$CFLAGS -DCRYPTOPP_DISABLE_AESNI -DCRYPTOPP_DISABLE_SHANI"
		;;
esac
INCLUDES="-I$SHARED -I$SRC -I$SRC/Crypto -I$SRC/Crypto/Argon2/include"

printf '%s\n' "$CC $CXX $CFLAGS $UNAME_M $SRC" > "$OBJ/flags.new"
if [ ! -f "$OBJ/flags" ] || ! cmp -s "$OBJ/flags" "$OBJ/flags.new"; then
	rm -f "$OBJ"/*.o "$OUT"
	mv "$OBJ/flags.new" "$OBJ/flags"
else
	rm -f "$OBJ/flags.new"
fi

compile_c() {
	dest=$1
	src=$2
	shift 2
	if [ ! -f "$dest" ] || [ "$src" -nt "$dest" ]; then
		# shellcheck disable=SC2086
		$CC -c $CFLAGS $INCLUDES "$@" -o "$dest" "$src"
	fi
}

compile_cxx() {
	dest=$1
	src=$2
	shift 2
	if [ ! -f "$dest" ] || [ "$src" -nt "$dest" ]; then
		# shellcheck disable=SC2086
		$CXX -std=c++14 -c $CFLAGS $INCLUDES "$@" -o "$dest" "$src"
	fi
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
EXTRA_OBJS=""
case "$UNAME_M" in
	arm64|aarch64)
		compile_c "$OBJ/Aes_hw.o" "$SRC/Crypto/Aes_hw_armv8.c" -march=armv8-a+crypto -mbranch-protection=standard
		compile_c "$OBJ/sha256_armv8.o" "$SRC/Crypto/sha256_armv8.c" -march=armv8-a+crypto -mbranch-protection=standard
		AES_HW="$OBJ/Aes_hw.o $OBJ/sha256_armv8.o"
		;;
	x86_64|amd64)
		compile_c "$OBJ/cpu.o" "$SRC/Crypto/cpu.c"
		compile_c "$OBJ/opt_sse2.o" "$SRC/Crypto/Argon2/src/opt_sse2.c"
		compile_c "$OBJ/opt_avx2.o" "$SRC/Crypto/Argon2/src/opt_avx2.c"
		EXTRA_OBJS="$OBJ/cpu.o $OBJ/opt_sse2.o $OBJ/opt_avx2.o"
		;;
	i686|i386)
		compile_c "$OBJ/cpu.o" "$SRC/Crypto/cpu.c"
		compile_c "$OBJ/opt_sse2.o" "$SRC/Crypto/Argon2/src/opt_sse2.c"
		EXTRA_OBJS="$OBJ/cpu.o $OBJ/opt_sse2.o"
		;;
esac

compile_cxx "$OBJ/vc_wrap.o" "$SHARED/vc_wrap.cpp"
compile_cxx "$OBJ/vc_progress.o" "$SHARED/vc_progress.cpp"
compile_cxx "$OBJ/mobile_mocks.o" "$SHARED/mobile_mocks.cpp"
compile_cxx "$OBJ/test_crypto_safety.o" "$SHARED/test_crypto_safety_main.cpp"
compile_cxx "$OBJ/android_jni.o" "$SHARED/android_jni.cpp" -DVC_HOST_JNI

need_link=0
if [ ! -x "$OUT" ]; then
	need_link=1
else
	for o in "$OBJ/Aescrypt.o" "$OBJ/Aeskey.o" "$OBJ/Aestab.o" "$OBJ/Sha2.o" \
		"$OBJ/blake2b.o" "$OBJ/argon2.o" "$OBJ/argon2core.o" "$OBJ/argon2ref.o" \
		"$OBJ/vc_wrap.o" "$OBJ/vc_progress.o" "$OBJ/mobile_mocks.o" "$OBJ/test_crypto_safety.o"; do
		if [ -f "$o" ] && [ "$o" -nt "$OUT" ]; then
			need_link=1
			break
		fi
	done
fi
if [ "$need_link" -eq 1 ]; then
	# shellcheck disable=SC2086
	$CXX $SAN -o "$OUT" "$OBJ/test_crypto_safety.o" "$OBJ/mobile_mocks.o" "$OBJ/vc_wrap.o" "$OBJ/vc_progress.o" \
		"$OBJ/Aescrypt.o" "$OBJ/Aeskey.o" "$OBJ/Aestab.o" "$OBJ/Sha2.o" $AES_HW $EXTRA_OBJS \
		"$OBJ/blake2b.o" "$OBJ/argon2.o" "$OBJ/argon2core.o" "$OBJ/argon2ref.o"
fi
echo "Running $OUT (ASan/UBSan)"
"$OUT"
echo "JNI host syntax object: $OBJ/android_jni.o"
