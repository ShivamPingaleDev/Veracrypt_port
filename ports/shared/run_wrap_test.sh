#!/bin/sh
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
OUT="/tmp/vcport-wrap-test"
CFLAGS="-O1 -fno-strict-aliasing -DTC_UNIX -DTC_MACOSX -DARGON2_NO_THREADS -DCRYPTOPP_DISABLE_X86ASM -D_FILE_OFFSET_BITS=64 -D_LARGEFILE_SOURCE"
INCLUDES="-I$SRC -I$SRC/Crypto -I$SRC/Crypto/Argon2/include -I$SHARED"

clang -c $CFLAGS $INCLUDES -o /tmp/Aescrypt.o "$SRC/Crypto/Aescrypt.c"
clang -c $CFLAGS $INCLUDES -o /tmp/Aeskey.o "$SRC/Crypto/Aeskey.c"
clang -c $CFLAGS $INCLUDES -o /tmp/Aestab.o "$SRC/Crypto/Aestab.c"
clang -c $CFLAGS $INCLUDES -march=armv8-a+crypto -o /tmp/Aes_hw.o "$SRC/Crypto/Aes_hw_armv8.c"
clang -c $CFLAGS $INCLUDES -o /tmp/Sha2.o "$SRC/Crypto/Sha2.c"
clang -c $CFLAGS $INCLUDES -march=armv8-a+crypto -o /tmp/sha256_armv8.o "$SRC/Crypto/sha256_armv8.c"
clang -c $CFLAGS $INCLUDES -o /tmp/blake2b.o "$SRC/Crypto/Argon2/src/blake2/blake2b.c"
clang -c $CFLAGS $INCLUDES -o /tmp/argon2.o "$SRC/Crypto/Argon2/src/argon2.c"
clang -c $CFLAGS $INCLUDES -o /tmp/argon2core.o "$SRC/Crypto/Argon2/src/core.c"
clang -c $CFLAGS $INCLUDES -o /tmp/argon2ref.o "$SRC/Crypto/Argon2/src/ref.c"
clang++ -std=c++14 -c $CFLAGS $INCLUDES -o /tmp/vc_wrap.o "$SHARED/vc_wrap.cpp"
clang++ -std=c++14 -c $CFLAGS $INCLUDES -o /tmp/test_wrap.o "$SHARED/test_wrap_main.cpp"

clang++ -o "$OUT" /tmp/test_wrap.o /tmp/vc_wrap.o /tmp/Aescrypt.o /tmp/Aeskey.o /tmp/Aestab.o /tmp/Aes_hw.o /tmp/Sha2.o /tmp/sha256_armv8.o /tmp/blake2b.o /tmp/argon2.o /tmp/argon2core.o /tmp/argon2ref.o
echo "Running $OUT"
"$OUT"
