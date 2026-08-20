#!/bin/sh
# Sprint 11: official desktop VeraCrypt 1.26.29 volumes (password, PIM, keyfile,
# cascade, hash, hidden) must open on the phones, and phone/engine FAT volumes
# must open on desktop. Needs VeraCrypt CLI for the desktop side.
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/../.." && pwd)"
ANDROID="$ROOT/ports/android"
IOS="$ROOT/ports/ios"
SHARED="$ROOT/ports/shared"
STAGE="${VCPORT_DESKTOP_DIR:-/tmp/vcport-desktop}"
PHONE_STAGE="${VCPORT_CROSS_DIR:-/tmp/vcport-cross}"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
ADB="$(command -v adb 2>/dev/null || true)"
if [ -z "$ADB" ] && [ -x "$SDK/platform-tools/adb" ]; then
	ADB="$SDK/platform-tools/adb"
fi
VC="$(command -v veracrypt 2>/dev/null || true)"
if [ -z "$VC" ] && [ -x /usr/local/bin/veracrypt ]; then
	VC=/usr/local/bin/veracrypt
fi
PKG=dev.shivampingale.vcport
ANDROID_DESKTOP="/sdcard/Download/vcport-desktop"
JAVA_HOME="${JAVA_HOME:-/Users/smp/.jdks/jdk-17.0.20+8/Contents/Home}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:${PATH:-}"
mkdir -p "$STAGE"

if [ -d "$ROOT/src/Volume" ]; then
	SRC="$ROOT/src"
elif [ -n "${VC_SRC:-}" ]; then
	SRC="$VC_SRC"
else
	echo "Clone ShivamPingaleDev/Veracrypt_port, or set VC_SRC." >&2
	exit 1
fi
BUILD="${VC_VOL_BUILD:-${TMPDIR:-/tmp}/vcport-vol-build}"

have_android() {
	[ -n "$ADB" ] && "$ADB" devices | awk 'NR>1 && $2=="device" {found=1} END {exit found?0:1}'
}

first_ipad() {
	want="$1"
	xcrun simctl list devices available 2>/dev/null | awk -v want="$want" '
		/iPad/ && $0 ~ want {
			n = split($0, a, /[()]/)
			for (i = 1; i <= n; i++) {
				gsub(/ /, "", a[i])
				if (a[i] ~ /^[0-9A-Fa-f-]{36}$/) { print a[i]; exit }
			}
		}
	'
}

android_connected() {
	export ANDROID_HOME="${ANDROID_HOME:-$SDK}"
	cd "$ANDROID"
	./gradlew :app:connectedFossDebugAndroidTest --no-daemon \
		-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true \
		-Pandroid.testInstrumentationRunnerArguments.class="dev.shivampingale.vcport.$1"
}

ios_only() {
	UDID="$1"
	test="$2"
	cd "$IOS"
	IOS_SDK=iphonesimulator IOS_ARCH="$(uname -m)" ./build-native.sh
	xcodegen generate
	xcodebuild -project VCPort.xcodeproj -scheme VCPort -configuration Debug \
		-destination "id=$UDID" \
		-derivedDataPath "$IOS/build/DerivedData" \
		CODE_SIGNING_ALLOWED=NO \
		CODE_SIGNING_REQUIRED=NO \
		-maximum-test-execution-time-allowance 600 \
		-only-testing:"VCPortTests/$test" \
		test
}

ios_docs() {
	UDID="$1"
	sub="$2"
	data="$(xcrun simctl get_app_container "$UDID" "$PKG" data 2>/dev/null || true)"
	if [ -z "$data" ]; then
		echo "no iOS app data container yet" >&2
		return 1
	fi
	mkdir -p "$data/Documents/$sub"
	printf '%s\n' "$data/Documents/$sub"
}

echo "== Host FAT12 / 32KiB regression =="
cmake -S "$SHARED" -B "$BUILD" -DVC_SRC="$SRC" -DCMAKE_BUILD_TYPE=Release
cmake --build "$BUILD" --target vc_volume_test
"$BUILD/vc_volume_test"
"$BUILD/vc_volume_test" --write-compat "$STAGE/engine-made.hc"

python3 - "$STAGE" "$VC" <<'PY'
import hashlib, json, os, shutil, subprocess, sys, time
from pathlib import Path

stage = Path(sys.argv[1])
vc = sys.argv[2]
stage.mkdir(parents=True, exist_ok=True)
photo = bytes(i & 0xFF for i in range(32 * 1024))
memo_engine = b"engine-memo-ok\n"
memo_desk = b"desktop-memo-ok\n"
decoy = b"outer-decoy-ok\n"
secret = b"hidden-secret-ok\n"

def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()

def write_json(name: str, payload: dict) -> None:
    (stage / name).write_text(json.dumps(payload, indent=2) + "\n")

write_json("engine-made.json", {
    "volume": "engine-made.hc",
    "password": "EngineCompat-password-one-OK",
    "pim": 1,
    "cipher": "AES(Twofish(Serpent))",
    "kdf": "HMAC-SHA-512",
    "keyfiles": [],
    "files": {"MEMO.TXT": sha(memo_engine), "PHOTO.JPG": sha(photo)},
})

if not vc:
    print("SKIP  official VeraCrypt CLI not installed")
    sys.exit(0)

def run(args, **kw):
    print("+", " ".join(args[:6]), "...")
    return subprocess.run(args, check=False, **kw)

def unmount(path: str) -> None:
    run([vc, "-t", "--non-interactive", "-u", path, "-f"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

listed = run([vc, "-t", "--non-interactive", "-l"], capture_output=True, text=True)
for line in (listed.stdout or "").splitlines():
    if "vcport-desktop" in line or "vcport-cross" in line or "mnt-phone" in line:
        parts = line.split()
        for p in parts:
            if p.endswith(".hc"):
                unmount(p)

for leftover in ("engine-made.hc", "aes-sha512-pim-kf.hc", "cascade-sha512.hc",
                 "aes-sha256.hc", "hidden.hc"):
    unmount(str(stage / leftover))

mnt = stage / "mnt"
if mnt.exists():
    try:
        mnt.rmdir()
    except OSError:
        pass
mnt.mkdir(parents=True, exist_ok=True)

def mount(vol: Path, password: str, keyfiles: str = "") -> Path:
    dest = mnt
    if dest.exists():
        for child in dest.iterdir():
            if child.is_file() or child.is_symlink():
                child.unlink()
    args = [vc, "-t", "--non-interactive", "--mount", str(vol), str(dest),
            "--password=" + password, "--pim=1", "--protect-hidden=no",
            "--keyfiles=" + keyfiles, "-f"]
    rc = run(args)
    if rc.returncode != 0:
        raise SystemExit(f"mount failed {vol} rc={rc.returncode}")
    return dest

def check_photo(dest: Path, extra: dict[str, bytes]) -> None:
    names = {p.name.upper(): p for p in dest.iterdir() if p.is_file()}
    photo_path = names.get("PHOTO.JPG")
    if photo_path is None:
        raise SystemExit(f"PHOTO.JPG missing in {sorted(names)} size dump {[ (n, p.stat().st_size) for n,p in names.items() ]}")
    got = photo_path.read_bytes()
    if len(got) != len(photo) or got != photo:
        raise SystemExit(f"PHOTO.JPG desktop size {len(got)} expected {len(photo)}")
    for name, body in extra.items():
        hit = names.get(name.upper())
        if hit is None:
            raise SystemExit(f"{name} missing")
        if hit.read_bytes() != body:
            raise SystemExit(f"{name} contents mismatch ({hit.stat().st_size} bytes)")

print("== Desktop opens engine-made volume ==")
root = mount(stage / "engine-made.hc", "EngineCompat-password-one-OK")
check_photo(root, {"MEMO.TXT": memo_engine})
unmount(str(stage / "engine-made.hc"))
print("OK  engine-made PHOTO.JPG is 32768 bytes on desktop")

key = stage / "key.bin"
if not key.is_file():
    key.write_bytes(os.urandom(1024))

def create_normal(path: Path, password: str, encryption: str, digest: str, keyfiles: str) -> None:
    unmount(str(path))
    if path.exists():
        path.unlink()
    args = [
        vc, "-t", "--non-interactive", "--create", str(path),
        "--size=2097152", "--password=" + password, "--pim=1",
        "--encryption=" + encryption, "--hash=" + digest,
        "--filesystem=FAT", "--volume-type=normal",
        "--keyfiles=" + keyfiles, "--random-source=/dev/urandom",
        "--quick", "-f",
    ]
    rc = run(args)
    if rc.returncode != 0:
        raise SystemExit(f"create failed {path} rc={rc.returncode}")

def fill(path: Path, password: str, keyfiles: str, files: dict[str, bytes]) -> None:
    root = mount(path, password, keyfiles)
    for name, body in files.items():
        (root / name).write_bytes(body)
        os.sync()
    time.sleep(0.2)
    unmount(str(path))

print("== Create desktop volumes ==")
files_common = {"MEMO.TXT": memo_desk, "PHOTO.JPG": photo}
create_normal(stage / "aes-sha512-pim-kf.hc", "DesktopCompat-password-one-OK",
              "AES", "sha-512", str(key))
fill(stage / "aes-sha512-pim-kf.hc", "DesktopCompat-password-one-OK", str(key), files_common)
write_json("aes-sha512-pim-kf.json", {
    "volume": "aes-sha512-pim-kf.hc",
    "password": "DesktopCompat-password-one-OK",
    "pim": 1,
    "cipher": "AES",
    "kdf": "HMAC-SHA-512",
    "keyfiles": ["key.bin"],
    "files": {"MEMO.TXT": sha(memo_desk), "PHOTO.JPG": sha(photo)},
})

create_normal(stage / "cascade-sha512.hc", "DesktopCompat-password-two-OK",
              "AES-Twofish-Serpent", "sha-512", "")
fill(stage / "cascade-sha512.hc", "DesktopCompat-password-two-OK", "", files_common)
write_json("cascade-sha512.json", {
    "volume": "cascade-sha512.hc",
    "password": "DesktopCompat-password-two-OK",
    "pim": 1,
    "cipher": "AES(Twofish(Serpent))",
    "kdf": "HMAC-SHA-512",
    "keyfiles": [],
    "files": {"MEMO.TXT": sha(memo_desk), "PHOTO.JPG": sha(photo)},
})

create_normal(stage / "aes-sha256.hc", "DesktopCompat-password-one-OK",
              "AES", "sha-256", "")
fill(stage / "aes-sha256.hc", "DesktopCompat-password-one-OK", "", files_common)
write_json("aes-sha256.json", {
    "volume": "aes-sha256.hc",
    "password": "DesktopCompat-password-one-OK",
    "pim": 1,
    "cipher": "AES",
    "kdf": "HMAC-SHA-256",
    "keyfiles": [],
    "files": {"MEMO.TXT": sha(memo_desk), "PHOTO.JPG": sha(photo)},
})

hidden = stage / "hidden.hc"
unmount(str(hidden))
if hidden.exists():
    hidden.unlink()
rc = run([
    vc, "-t", "--non-interactive", "--create", str(hidden),
    "--size=8388608", "--password=DesktopCompat-hidden-outer-OK", "--pim=1",
    "--encryption=AES", "--hash=sha-512", "--filesystem=FAT",
    "--volume-type=normal", "--keyfiles=", "--random-source=/dev/urandom",
    "-f",
])
hidden_ok = rc.returncode == 0
if hidden_ok:
    fill(hidden, "DesktopCompat-hidden-outer-OK", "", {"DECOY.TXT": decoy})
    rc = run([
        vc, "-t", "--non-interactive", "--create", str(hidden),
        "--size=2097152", "--password=DesktopCompat-hidden-inner-OK", "--pim=1",
        "--encryption=AES", "--hash=sha-512", "--filesystem=FAT",
        "--volume-type=hidden", "--keyfiles=", "--random-source=/dev/urandom",
        "--quick", "-f",
    ])
    hidden_ok = rc.returncode == 0
if hidden_ok:
    root = mount(hidden, "DesktopCompat-hidden-inner-OK")
    (root / "SECRET.TXT").write_bytes(secret)
    os.sync()
    time.sleep(0.2)
    unmount(str(hidden))
    write_json("hidden.json", {
        "volume": "hidden.hc",
        "password": "DesktopCompat-hidden-outer-OK",
        "pim": 1,
        "cipher": "AES",
        "kdf": "HMAC-SHA-512",
        "keyfiles": [],
        "files": {"DECOY.TXT": sha(decoy)},
        "hidden_password": "DesktopCompat-hidden-inner-OK",
        "hidden_pim": 1,
        "hidden_files": {"SECRET.TXT": sha(secret)},
    })
    print("OK  hidden volume")
else:
    print("SKIP  hidden volume create")
    hj = stage / "hidden.json"
    if hj.exists():
        hj.unlink()

print("OK  desktop volumes in", stage)
PY

echo "== Host engine volume is in $STAGE/engine-made.hc =="

push_android() {
	"$ADB" shell mkdir -p "$ANDROID_DESKTOP" /data/local/tmp/vcport-desktop
	for f in "$@"; do
		if [ -f "$STAGE/$f" ]; then
			"$ADB" push "$STAGE/$f" "$ANDROID_DESKTOP/$f"
			"$ADB" push "$STAGE/$f" /data/local/tmp/vcport-desktop/"$f"
		fi
	done
}

if have_android; then
	echo "== Android opens desktop volumes =="
	push_android key.bin aes-sha512-pim-kf.hc aes-sha512-pim-kf.json \
		cascade-sha512.hc cascade-sha512.json aes-sha256.hc aes-sha256.json \
		engine-made.hc engine-made.json hidden.hc hidden.json
	android_connected "DesktopCompatVolumeTest#openDesktopCreatedVolumes"
else
	echo "SKIP  no Android emulator"
fi

UDID="$(first_ipad Booted)"
if [ -z "$UDID" ]; then
	UDID="$(first_ipad Shutdown)"
fi
if [ -n "$UDID" ] && command -v xcodebuild >/dev/null 2>&1 && command -v xcodegen >/dev/null 2>&1; then
	xcrun simctl boot "$UDID" >/dev/null 2>&1 || true
	echo "iPad Simulator $UDID"
	if ios_only "$UDID" "DesktopCompatVolumeTests/testOpenDesktopCreatedVolumes" >/tmp/vcport-ios-desktop-probe.log 2>&1; then
		:
	fi
	DOCS="$(ios_docs "$UDID" desktop || true)"
	if [ -n "$DOCS" ]; then
		for f in key.bin aes-sha512-pim-kf.hc aes-sha512-pim-kf.json \
			cascade-sha512.hc cascade-sha512.json aes-sha256.hc aes-sha256.json \
			engine-made.hc engine-made.json hidden.hc hidden.json; do
			if [ -f "$STAGE/$f" ]; then
				cp "$STAGE/$f" "$DOCS/$f"
			fi
		done
		echo "== iOS opens desktop volumes =="
		ios_only "$UDID" "DesktopCompatVolumeTests/testOpenDesktopCreatedVolumes"
	else
		echo "SKIP  iOS data container not ready"
	fi
else
	echo "SKIP  no iPad Simulator / xcodebuild"
fi

desktop_open_json() {
	vol="$1"
	json="$2"
	if [ ! -f "$vol" ] || [ ! -f "$json" ] || [ -z "$VC" ]; then
		return 0
	fi
	python3 - "$VC" "$vol" "$json" "$STAGE" <<'PY'
import json, os, subprocess, sys, time
from pathlib import Path
vc, vol, meta_path, stage = sys.argv[1], sys.argv[2], sys.argv[3], Path(sys.argv[4])
meta = json.loads(Path(meta_path).read_text())
mnt = stage / "mnt-phone"
mnt.mkdir(parents=True, exist_ok=True)
subprocess.run([vc, "-t", "--non-interactive", "-u", vol, "-f"],
               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
kfs = meta.get("keyfiles") or []
kf = str(Path(vol).parent / kfs[0]) if kfs else ""
args = [vc, "-t", "--non-interactive", "--mount", vol, str(mnt),
        "--password=" + meta["password"], "--pim=" + str(meta.get("pim", 1)),
        "--protect-hidden=no", "--keyfiles=" + kf, "-f"]
rc = subprocess.run(args)
if rc.returncode != 0:
    raise SystemExit(f"desktop mount failed {vol}")
want = meta["files"]
names = {p.name.upper(): p for p in mnt.iterdir() if p.is_file()}
for name, digest in want.items():
    hit = names.get(name.upper())
    if hit is None:
        raise SystemExit(f"{name} missing on desktop from {vol}")
    import hashlib
    got = hashlib.sha256(hit.read_bytes()).hexdigest()
    if got != digest:
        raise SystemExit(f"{name} hash {got} != {digest} size={hit.stat().st_size}")
    if name.upper() == "PHOTO.JPG" and hit.stat().st_size != 32768:
        raise SystemExit(f"PHOTO.JPG still truncated to {hit.stat().st_size}")
subprocess.run([vc, "-t", "--non-interactive", "-u", vol, "-f"],
               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
print("OK  desktop opened", vol)
PY
}

if have_android && [ -n "$VC" ]; then
	echo "== Android create, then desktop opens that volume =="
	android_connected "CrossPhoneVolumeTest#createRandomVolumeOnAndroid"
	"$ADB" shell mkdir -p /sdcard/Download/vcport-cross
	if ! "$ADB" pull /sdcard/Download/vcport-cross/android-made.hc "$PHONE_STAGE/android-made.hc" 2>/dev/null; then
		"$ADB" pull "/sdcard/Android/data/$PKG/files/cross/android-made.hc" "$PHONE_STAGE/android-made.hc"
		"$ADB" pull "/sdcard/Android/data/$PKG/files/cross/android-made.json" "$PHONE_STAGE/android-made.json"
	else
		"$ADB" pull /sdcard/Download/vcport-cross/android-made.json "$PHONE_STAGE/android-made.json"
	fi
	mkdir -p "$PHONE_STAGE"
	desktop_open_json "$PHONE_STAGE/android-made.hc" "$PHONE_STAGE/android-made.json"
fi

if [ -n "$UDID" ] && [ -n "$VC" ] && command -v xcodebuild >/dev/null 2>&1; then
	echo "== iOS create, then desktop opens that volume =="
	ios_only "$UDID" "CrossPhoneVolumeTests/testCreateRandomVolumeOnIos"
	DOCS="$(ios_docs "$UDID" cross || true)"
	if [ -n "$DOCS" ] && [ -f "$DOCS/ios-made.hc" ]; then
		mkdir -p "$PHONE_STAGE"
		cp "$DOCS/ios-made.hc" "$PHONE_STAGE/ios-made.hc"
		cp "$DOCS/ios-made.json" "$PHONE_STAGE/ios-made.json"
		desktop_open_json "$PHONE_STAGE/ios-made.hc" "$PHONE_STAGE/ios-made.json"
	fi
fi

echo "OK  sprint 11 desktop/phone volumes in $STAGE"
ls -l "$STAGE"
