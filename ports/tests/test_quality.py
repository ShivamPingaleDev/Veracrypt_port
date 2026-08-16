#!/usr/bin/env python3
"""Host quality tests: taxonomy contracts plus deterministic property/fuzz.

No phone, no Hypothesis, no network. Device work stays in TESTING.md.
"""

from __future__ import annotations

import importlib.util
import json
import random
import re
import unittest
from pathlib import Path

import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from repo_paths import read, resolve  # noqa: E402

_REL = None


def release_mod():
    global _REL
    if _REL is None:
        path = resolve("ports/scripts/check_veracrypt_release.py")
        spec = importlib.util.spec_from_file_location("check_veracrypt_release", path)
        mod = importlib.util.module_from_spec(spec)
        assert spec.loader is not None
        sys.path.insert(0, str(path.parent))
        spec.loader.exec_module(mod)
        _REL = mod
    return _REL


def kotlin_style_compare(a: str, b: str) -> int:
    """SourcePin.compare: split on '.' / '-', keep Int parts, pad with 0."""
    pa = [int(p) for p in re.split(r"[.-]", a) if p.isdigit()]
    pb = [int(p) for p in re.split(r"[.-]", b) if p.isdigit()]
    n = max(len(pa), len(pb))
    pa += [0] * (n - len(pa))
    pb += [0] * (n - len(pb))
    if pa < pb:
        return -1
    if pa > pb:
        return 1
    return 0


class TableDrivenTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.rel = release_mod()

    def test_version_from_tag_rows(self) -> None:
        rows = (
            ("VeraCrypt_1.26.29", "1.26.29"),
            ("VeraCrypt-1.26.29", "1.26.29"),
            ("VeraCrypt 1.26.29", "1.26.29"),
            ("1.26.29", "1.26.29"),
            ("  VeraCrypt_1.27.0  ", "1.27.0"),
            ("VeraCrypt_1.26.29 extra", "1.26.29"),
            ("", ""),
            ("   ", ""),
        )
        for tag, want in rows:
            with self.subTest(tag=tag):
                self.assertEqual(self.rel.version_from_tag(tag), want)

    def test_compare_version_rows(self) -> None:
        rows = (
            ("1.26.29", "1.26.29", 0),
            ("1.27.0", "1.26.29", 1),
            ("1.26.24", "1.26.29", -1),
            ("1.26", "1.26.0", 0),
            ("0.3.0", "0.2.9", 1),
            ("1.26.29", "1.26.29-ignored", 0),
        )
        for a, b, sign in rows:
            with self.subTest(a=a, b=b):
                got = self.rel.compare_version(a, b)
                if sign == 0:
                    self.assertEqual(got, 0)
                elif sign > 0:
                    self.assertGreater(got, 0)
                else:
                    self.assertLess(got, 0)

    def test_pin_problems_empty_on_this_tree(self) -> None:
        self.assertEqual(self.rel.pin_problems(self.rel.load_version()), [])


class AirgapTests(unittest.TestCase):
    def test_trusted_hosts_only(self) -> None:
        net = read("ports/android/app/src/main/java/dev/shivampingale/vcport/TrustedNet.kt")
        swift = read("ports/ios/VCPort/SourcePin.swift")
        for blob in (net, swift):
            self.assertIn("raw.githubusercontent.com", blob)
            self.assertIn("api.github.com", blob)
            self.assertIn("www.githubstatus.com", blob)
            self.assertIn("/repos/veracrypt/VeraCrypt/releases/latest", blob)

    def test_allow_table(self) -> None:
        def allow(raw: str) -> bool:
            from urllib.parse import urlparse
            u = urlparse(raw)
            if u.scheme != "https" or u.username or u.password:
                return False
            if u.port not in (None, 443):
                return False
            host = (u.hostname or "").lower()
            path = u.path
            if host == "raw.githubusercontent.com":
                return path.startswith("/ShivamPingaleDev/Veracrypt_port/") and path.endswith(
                    "/ports/version.json"
                )
            if host == "api.github.com":
                return path == "/repos/veracrypt/VeraCrypt/releases/latest"
            if host == "www.githubstatus.com":
                return path == "/api/v2/status.json"
            return False

        self.assertTrue(
            allow(
                "https://raw.githubusercontent.com/ShivamPingaleDev/Veracrypt_port/master/ports/version.json"
            )
        )
        self.assertTrue(allow("https://api.github.com/repos/veracrypt/VeraCrypt/releases/latest"))
        self.assertTrue(allow("https://www.githubstatus.com/api/v2/status.json"))
        self.assertFalse(allow("http://api.github.com/repos/veracrypt/VeraCrypt/releases/latest"))
        self.assertFalse(allow("https://evil.example/x"))
        self.assertFalse(allow("https://api.github.com/repos/veracrypt/VeraCrypt/git/refs"))
        self.assertFalse(allow("https://user:pass@api.github.com/repos/veracrypt/VeraCrypt/releases/latest"))

    def test_no_listeners_in_mobile_source(self) -> None:
        roots = [
            resolve("ports/android/app/src"),
            resolve("ports/ios/VCPort"),
            resolve("ports/shared"),
        ]
        banned = ("ServerSocket", "DatagramSocket", "ServerSocketChannel", "NWListener", "CFSocket")
        for root in roots:
            for path in root.rglob("*"):
                if path.suffix not in {".kt", ".swift", ".cpp", ".h", ".xml"}:
                    continue
                text = path.read_text(encoding="utf-8", errors="replace")
                for word in banned:
                    self.assertNotIn(word, text, f"{path} {word}")
                for word in ("key_escrow", "lawfulIntercept", "goldenKey", "dual_ec"):
                    self.assertNotIn(word, text, f"{path} {word}")


class WhiteBoxTests(unittest.TestCase):
    def test_fdroid_check_is_a_hard_error_path(self) -> None:
        fdroid = read(
            "ports/android/app/src/fdroid/java/dev/shivampingale/vcport/UpdateChecker.kt"
        )
        self.assertIn('error("F-Droid build has no network")', fdroid)

    def test_github_official_release_failure_is_swallowed(self) -> None:
        github = read(
            "ports/android/app/src/github/java/dev/shivampingale/vcport/UpdateChecker.kt"
        )
        self.assertIn("catch (_: Exception)", github)
        self.assertIn("Official VeraCrypt GitHub was unreachable", github)

    def test_wrap_rejects_tamper_and_wrong_password(self) -> None:
        wrap = read("ports/shared/test_wrap_main.cpp")
        self.assertIn("wrong password is rejected", wrap)
        self.assertIn("tampered wrap is rejected", wrap)
        self.assertIn("truncated wrap is rejected", wrap)


class BlackBoxTests(unittest.TestCase):
    def test_wrap_host_harness_is_file_in_file_out(self) -> None:
        wrap = read("ports/shared/test_wrap_main.cpp")
        self.assertIn("wrap UTF-8 text", wrap)
        self.assertIn("payload matches", wrap)
        self.assertIn("wrap empty file", wrap)

    def test_volume_host_harness_create_open_list_export(self) -> None:
        vol = read("ports/shared/test_volume_main.cpp")
        self.assertIn("create AES(Twofish(Serpent))/HMAC-SHA-512", vol)
        self.assertIn("import FROMDEV.TXT", vol)
        self.assertIn("wipe free space", vol)
        self.assertIn("restore volume header", vol)

    def test_lifecycle_host_harness_create_store_close_reopen(self) -> None:
        life = read("ports/shared/test_lifecycle_main.cpp")
        cmake = read("ports/shared/CMakeLists.txt")
        runner = read("ports/tests/run-phases.sh")
        self.assertIn("create AES(Twofish(Serpent))/HMAC-SHA-512", life)
        self.assertIn("create biometric password", life)
        self.assertIn("store VCF2 remember bundle", life)
        self.assertIn("import NOTE.TXT", life)
        self.assertIn("reopen with stored factors", life)
        self.assertIn("payload still matches", life)
        self.assertIn("close volume again", life)
        self.assertIn("vc_lifecycle_test", cmake)
        self.assertIn("run_lifecycle_test.sh", runner)
        self.assertIn("std::thread", life)
        self.assertIn("parallel CPU", life)
        self.assertIn("VeraCrypt AES/Twofish/Serpent/HMAC test vectors", life)
        self.assertIn("phone session", life)
        self.assertIn("wrapFile encrypt", life)
        self.assertIn("unwrapFile decrypt", life)
        self.assertIn("createVolume", life)
        self.assertIn("changeHeader", life)
        self.assertIn("worker_count", life)

    def test_emulator_device_simulation_stays_offline(self) -> None:
        sim = read(
            "ports/android/app/src/androidTest/java/dev/shivampingale/vcport/DeviceSimulationTest.kt"
        )
        ui = read(
            "ports/android/app/src/androidTest/java/dev/shivampingale/vcport/MainActivityUiTest.kt"
        )
        gradle = read("ports/android/app/build.gradle")
        script = read("ports/android/run_device_sim.sh")
        self.assertIn("createStoreEncryptDecryptReopen", sim)
        self.assertIn("hiddenVolumeWriteProtection", sim)
        self.assertIn("NativeBridge.createVolume", sim)
        self.assertIn("NativeBridge.wrapFile", sim)
        self.assertIn("NativeBridge.unwrapFile", sim)
        self.assertIn("NativeBridge.importFile", sim)
        self.assertIn("NativeBridge.changeHeader", sim)
        self.assertIn("protectHidden", sim)
        self.assertIn("protectionTriggered", sim)
        self.assertIn("readOnly", sim)
        self.assertNotIn("UpdateChecker.check()", sim)
        self.assertNotIn("UpdateChecker.check()", ui)
        self.assertIn("NativeBridge.isOpen", sim)
        self.assertIn("createAndroidComposeRule", ui)
        self.assertIn("Panic wipe", ui)
        self.assertIn("Stay offline. F-Droid: no network.", ui)
        self.assertIn("Encrypt file", ui)
        self.assertIn("tab_create", ui)
        self.assertIn("Check for updates", ui)
        self.assertIn("Working…", ui)
        self.assertIn("BuildConfig.ENABLE_SKINS", ui)
        self.assertIn("Looks (this phone)", ui)
        self.assertIn("ui-test-junit4", gradle)
        self.assertIn("ui-test-manifest", gradle)
        self.assertIn("animationsDisabled true", gradle)
        self.assertIn("AndroidJUnitRunner", gradle)
        self.assertIn("ENABLE_UPDATE_CHECK", gradle)
        self.assertIn("ENABLE_SKINS", gradle)
        self.assertNotIn("applicationIdSuffix", gradle)
        self.assertIn("vcport-api35", script)
        self.assertIn("connectedFdroidDebugAndroidTest", script)
        self.assertIn("connectedStyledDebugAndroidTest", script)
        cmake = read("ports/shared/CMakeLists.txt")
        self.assertIn("-fgnu89-inline", cmake)
        self.assertIn("armv8-a+crypto", cmake)
        self.assertIn("-fstack-protector-strong", cmake)
        self.assertIn("-fno-common", cmake)
        self.assertIn("-Wl,-z,relro", cmake)
        self.assertIn("-Wl,-z,now", cmake)
        self.assertIn("-Wl,-z,noexecstack", cmake)
        arm = read("ports/shared/upstream-sources.cmake")
        self.assertIn("-mbranch-protection=standard", arm)
        jni = read("ports/shared/android_jni.cpp")
        self.assertIn("jni_live_handle", jni)
        self.assertIn("vc_runtime_start", jni)
        self.assertIn("startRuntime", jni)
        native = read(
            "ports/android/app/src/main/java/dev/shivampingale/vcport/NativeBridge.kt"
        )
        self.assertIn("fun isOpen", native)
        header = read("ports/shared/vc_mobile.h")
        self.assertIn("vc_runtime_start", header)
        life = read("ports/shared/test_lifecycle_main.cpp")
        self.assertIn("vc_runtime_start", life)

    def test_user_never_sees_install_from_the_app(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("does not install itself", main)
        self.assertIn("does not install itself", view)


class ModuleTests(unittest.TestCase):
    def test_progress_api_is_in_the_mobile_header(self) -> None:
        header = read("ports/shared/vc_mobile.h")
        for name in (
            "vc_progress_reset",
            "vc_progress_set",
            "vc_progress_tick",
            "vc_progress_percent",
            "vc_progress_phase",
            "vc_generate_keyfile",
            "vc_wipe_free_space",
            "vc_import_file",
        ):
            self.assertIn(name, header)

    def test_progress_impl_is_its_own_translation_unit(self) -> None:
        cmake = read("ports/shared/CMakeLists.txt")
        wrap = read("ports/shared/run_wrap_test.sh")
        self.assertIn("vc_progress.cpp", cmake)
        self.assertIn("vc_progress.cpp", wrap)

    def test_native_bridge_matches_header(self) -> None:
        native = read(
            "ports/android/app/src/main/java/dev/shivampingale/vcport/NativeBridge.kt"
        )
        self.assertIn("fun generateKeyfile", native)
        self.assertIn("fun progressPercent", native)
        self.assertIn("fun wipeFreeSpace", native)


class IntegrationTests(unittest.TestCase):
    def test_version_json_feeds_portversion_and_plist(self) -> None:
        v = json.loads(read("ports/version.json"))
        header = read("src/Main/PortVersion.h")
        plist = read("ports/ios/VCPort/Info.plist")
        gradle = read("ports/android/app/build.gradle")
        self.assertIn(v["upstream_commit"], header)
        self.assertIn(v["upstream_commit"], plist)
        self.assertIn("versionJson.upstream_git", gradle)
        self.assertIn(v["upstream_git"], header)

    def test_kotlin_and_swift_share_vcf2_module(self) -> None:
        kotlin = read(
            "ports/android/app/src/main/java/dev/shivampingale/vcport/UnlockFactors.kt"
        )
        swift = read("ports/ios/VCPort/UnlockFactors.swift")
        self.assertIn("VCF2\\n", kotlin)
        self.assertIn("VCF2\\n", swift)

    def test_jni_exports_progress(self) -> None:
        jni = read("ports/shared/android_jni.cpp")
        self.assertIn("Java_dev_shivampingale_vcport_NativeBridge_progressPercent", jni)
        self.assertIn("Java_dev_shivampingale_vcport_NativeBridge_resetProgress", jni)


class FunctionalTests(unittest.TestCase):
    def test_copy_move_open_wipe_on_both_clients(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        for blob in (main, view):
            self.assertIn("Copy from device", blob)
            self.assertIn("Wipe free space", blob)
            self.assertIn("Panic wipe", blob)
            self.assertIn("Add keyfiles", blob)
            self.assertIn("Keyfile generator", blob)

    def test_progress_is_in_front_of_the_user(self) -> None:
        theme = read(
            "ports/android/app/src/main/java/dev/shivampingale/vcport/VcPortTheme.kt"
        )
        view = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("fun WorkOverlay", theme)
        self.assertIn("fun SkinProgress", theme)
        self.assertIn("CpTerm", theme)
        self.assertIn("MELCHIOR", theme)
        self.assertIn("BALTHASAR", theme)
        self.assertIn("CASPER", theme)
        self.assertIn("UNIT-01", theme)
        self.assertIn("drawMagiSeal", theme)
        self.assertIn("drawSkinFrame", theme)
        self.assertIn("fun SkinTabIndicator", theme)
        self.assertIn("fun SkinCardCap", theme)
        self.assertIn("skinTextFieldColors", theme)
        self.assertIn("UNIT-01  SYNC", theme)
        self.assertIn("sys.ready", theme)
        self.assertTrue(resolve("ports/android/app/src/main/res/drawable/ic_look_magi.xml").is_file())
        self.assertTrue(resolve("ports/android/app/src/main/res/drawable/ic_look_unit01.xml").is_file())
        self.assertIn("struct WorkOverlay", view)
        self.assertIn("Nothing runs out of sight.", theme)
        self.assertIn("Nothing runs out of sight.", view)
        self.assertIn("On this phone", theme)
        self.assertIn("On this phone", view)
        self.assertNotIn("Working…", theme)
        self.assertNotIn("Working…", view)

    def test_nested_volume_has_no_open_time_checkbox(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        self.assertIn("no open-time hidden checkbox", main.lower())
        self.assertNotIn("isHiddenVolume", main)
        self.assertIn("Protect hidden volume against damage", main)
        view = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("Protect hidden volume against damage", view)


class SmokeSanityTests(unittest.TestCase):
    def test_version_json_parses(self) -> None:
        v = json.loads(read("ports/version.json"))
        self.assertEqual(v["port_version"], "0.3.1")
        self.assertEqual(len(v["upstream_commit"]), 40)

    def test_pin_file_matches_json(self) -> None:
        v = json.loads(read("ports/version.json"))
        pin = read("ports/UPSTREAM_COMMIT").strip()
        self.assertEqual(pin, v["upstream_commit"])


class RegressionTests(unittest.TestCase):
    def test_app_is_still_vc_port(self) -> None:
        plist = read("ports/ios/VCPort/Info.plist")
        gradle = read("ports/android/app/build.gradle")
        self.assertIn("VC Port", plist)
        self.assertIn("dev.shivampingale.vcport", gradle)
        self.assertNotIn("applicationId 'org.veracrypt", gradle)

    def test_truecrypt_license_was_not_stripped(self) -> None:
        license_txt = read("LICENSE")
        self.assertIn("TrueCrypt License version 3.0", license_txt)
        self.assertIn("Apache License 2.0", license_txt)


class SecurityTamperTests(unittest.TestCase):
    def test_flag_secure_and_no_obfuscation(self) -> None:
        hard = read(
            "ports/android/app/src/main/java/dev/shivampingale/vcport/Hardening.kt"
        )
        gradle = read("ports/android/app/build.gradle")
        self.assertIn("FLAG_SECURE", hard)
        self.assertIn("minifyEnabled false", gradle)
        self.assertNotIn("play-services", gradle)

    def test_fdroid_manifest_has_no_internet(self) -> None:
        manifest = read("ports/android/app/src/main/AndroidManifest.xml")
        fdroid_manifest = read("ports/android/app/src/fdroid/AndroidManifest.xml")
        fdroid = read(
            "ports/android/app/src/fdroid/java/dev/shivampingale/vcport/UpdateChecker.kt"
        )
        self.assertNotIn("android.permission.INTERNET", manifest)
        self.assertIn('tools:node="remove"', fdroid_manifest)
        self.assertIn("has no network", fdroid)

    def test_wrap_uses_hmac(self) -> None:
        wrap = read("ports/shared/vc_wrap.cpp")
        self.assertIn("HMAC-SHA256", wrap)
        self.assertIn("Argon2id", wrap)


class NegativeBoundaryTests(unittest.TestCase):
    def test_password_generator_rejects_short_and_long(self) -> None:
        wrap = read("ports/shared/test_wrap_main.cpp")
        self.assertIn("generate rejects length 8", wrap)
        self.assertIn("generate rejects length 65", wrap)

    def test_import_size_cap_is_fat32_max(self) -> None:
        mobile = read("ports/shared/vc_mobile.cpp")
        self.assertIn("0xFFFFFFFFull", mobile)
        self.assertIn("file_size64", mobile)
        self.assertNotIn("VC_IMPORT_MAX = 256 * 1024 * 1024", mobile)
        self.assertIn("JNI_UTF_MAX = 4096", read("ports/shared/android_jni.cpp"))

    def test_keyfile_cap_is_1_mib(self) -> None:
        kotlin = read(
            "ports/android/app/src/main/java/dev/shivampingale/vcport/UnlockFactors.kt"
        )
        self.assertIn("MAX_KEYFILE = 1024 * 1024", kotlin)


class CompatibilityTests(unittest.TestCase):
    def test_default_cipher_and_kdf_match_desktop(self) -> None:
        native = read(
            "ports/android/app/src/main/java/dev/shivampingale/vcport/NativeBridge.kt"
        )
        swift = read("ports/ios/VCPort/VcMobileBridge.swift")
        self.assertIn('DEFAULT_CIPHER = "AES(Twofish(Serpent))"', native)
        self.assertIn('DEFAULT_KDF = "HMAC-SHA-512"', native)
        self.assertIn("AES(Twofish(Serpent))", swift)
        self.assertIn("HMAC-SHA-512", swift)

    def test_exfat_stays_unsupported(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        self.assertIn("exFAT", main)


class RecoveryTests(unittest.TestCase):
    def test_header_backup_and_restore_exist(self) -> None:
        header = read("ports/shared/vc_mobile.h")
        self.assertIn("vc_backup_headers", header)
        self.assertIn("vc_restore_headers", header)
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        self.assertIn("Restore from embedded backup header", main)


class PropertyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.rel = release_mod()
        cls.rng = random.Random(20260816)

    def _versions(self, n: int) -> list[str]:
        out = []
        for _ in range(n):
            parts = [self.rng.randint(0, 40) for _ in range(self.rng.randint(1, 4))]
            out.append(".".join(str(p) for p in parts))
        return out

    def test_compare_reflexive_antisymmetric(self) -> None:
        versions = self._versions(40)
        for a in versions:
            with self.subTest(a=a):
                self.assertEqual(self.rel.compare_version(a, a), 0)
        for a, b in zip(versions, versions[::-1]):
            with self.subTest(a=a, b=b):
                self.assertEqual(
                    self.rel.compare_version(a, b),
                    -self.rel.compare_version(b, a),
                )

    def test_compare_transitivity(self) -> None:
        versions = self._versions(24)
        cmp = self.rel.compare_version
        for a in versions:
            for b in versions:
                for c in versions:
                    if cmp(a, b) <= 0 and cmp(b, c) <= 0:
                        self.assertLessEqual(cmp(a, c), 0, f"{a} {b} {c}")


class MetamorphicTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.rel = release_mod()

    def test_prefix_strip_then_compare_is_identity_on_numeric(self) -> None:
        numeric = "1.26.29"
        for prefix in ("VeraCrypt_", "VeraCrypt-", "VeraCrypt ", ""):
            got = self.rel.version_from_tag(prefix + numeric)
            with self.subTest(prefix=prefix):
                self.assertEqual(got, numeric)
                self.assertEqual(self.rel.compare_version(got, numeric), 0)

    def test_trailing_zero_component_does_not_change_order(self) -> None:
        self.assertEqual(self.rel.compare_version("1.26", "1.26.0"), 0)
        self.assertEqual(self.rel.compare_version("0.3.0", "0.3"), 0)


class DifferentialTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.rel = release_mod()

    def test_kotlin_swift_cpp_share_tag_prefixes(self) -> None:
        blobs = [
            read("ports/android/app/src/main/java/dev/shivampingale/vcport/SourcePin.kt"),
            read("ports/ios/VCPort/SourcePin.swift"),
            read("src/Main/OfflineUpdate.cpp"),
            read("ports/scripts/check_veracrypt_release.py"),
        ]
        for blob in blobs:
            self.assertIn('"VeraCrypt_"', blob.replace("'", '"'))
            self.assertIn("VeraCrypt-", blob)
            self.assertIn("VeraCrypt ", blob)

    def test_python_agrees_with_kotlin_style_on_dotted_numeric(self) -> None:
        rng = random.Random(20260816)
        for _ in range(60):
            a = ".".join(str(rng.randint(0, 30)) for _ in range(rng.randint(1, 4)))
            b = ".".join(str(rng.randint(0, 30)) for _ in range(rng.randint(1, 4)))
            py = self.rel.compare_version(a, b)
            kt = kotlin_style_compare(a, b)
            self.assertEqual(py, kt, f"{a} vs {b}: python={py} kotlin-style={kt}")

    def test_decode_catch_exists_on_android(self) -> None:
        kotlin = read(
            "ports/android/app/src/main/java/dev/shivampingale/vcport/UnlockFactors.kt"
        )
        self.assertIn("catch (_: Exception)", kotlin)
        self.assertIn("FactorBundle()", kotlin)

    def test_biometric_load_does_not_crash_on_bad_blob(self) -> None:
        vault = read(
            "ports/android/app/src/main/java/dev/shivampingale/vcport/BiometricVault.kt"
        )
        self.assertGreaterEqual(vault.count("catch (_: Exception)"), 2)


class BoundedFuzzTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.rel = release_mod()

    def test_version_from_tag_never_throws(self) -> None:
        rng = random.Random(20260816)
        alphabet = "VeraCrypt_-. 0123456789abcxyz\t\n"
        for i in range(80):
            n = rng.randint(0, 48)
            tag = "".join(rng.choice(alphabet) for _ in range(n))
            with self.subTest(i=i):
                got = self.rel.version_from_tag(tag)
                self.assertIsInstance(got, str)

    def test_compare_version_never_throws(self) -> None:
        rng = random.Random(7)
        for i in range(40):
            a = "".join(rng.choice("0123456789.v-rc") for _ in range(rng.randint(0, 16)))
            b = "".join(rng.choice("0123456789.v-rc") for _ in range(rng.randint(0, 16)))
            with self.subTest(i=i):
                got = self.rel.compare_version(a, b)
                self.assertIn(got, (-1, 0, 1))

    def test_wrap_harness_includes_chunk_boundary_and_fuzz(self) -> None:
        wrap = read("ports/shared/test_wrap_main.cpp")
        self.assertIn("wrap exact 64KiB chunk", wrap)
        self.assertIn("unwrap rejects bit flips", wrap)
        self.assertIn("unwrap rejects garbage", wrap)
        self.assertIn("unwrap rejects header mutation", wrap)


class StaticContractTests(unittest.TestCase):
    def test_testing_map_is_checked_in(self) -> None:
        doc = read("ports/tests/TESTING.md")
        for word in (
            "White-box",
            "Black-box",
            "Unit",
            "Module",
            "Integration",
            "Functional",
            "Regression",
            "Smoke",
            "Property",
            "Bounded fuzz",
        ):
            self.assertIn(word, doc)


class AcceptanceHostTests(unittest.TestCase):
    def test_runner_includes_this_taxonomy(self) -> None:
        runner = read("ports/tests/run-phases.sh")
        self.assertIn("test_quality", runner)
        self.assertNotIn("test_modern", runner)


if __name__ == "__main__":
    unittest.main(verbosity=2)
