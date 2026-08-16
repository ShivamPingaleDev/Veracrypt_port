#!/usr/bin/env python3
"""Device-version and high-threat contract tests.

Run without Android SDK, Xcode, or FUSE-T. These assert that every VC Port
surface (Android F-Droid, Android GitHub, iOS, macOS overlay, shared native)
stays on the same version pin and keeps the FOSS / high-threat defaults.
"""

from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PORTS = ROOT / "ports"


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def load_version() -> dict:
    return json.loads(read("ports/version.json"))


def gradle_field(name: str) -> str:
    text = read("ports/android/app/build.gradle")
    m = re.search(rf"{name}\s+['\"]?([^'\"\s]+)['\"]?", text)
    if not m:
        raise AssertionError(f"missing {name} in build.gradle")
    return m.group(1)


class VersionMatrixTests(unittest.TestCase):
    """Every device build must advertise the same VC Port version."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.v = load_version()
        cls.port = cls.v["port_version"]
        cls.upstream = cls.v["upstream_version"]
        cls.commit = cls.v["upstream_commit"]
        cls.code = gradle_field("versionCode")

    def test_version_json_shape(self) -> None:
        for key in (
            "port_version",
            "upstream_name",
            "upstream_version",
            "upstream_commit",
            "notes",
            "download_url",
            "android_url",
        ):
            self.assertIn(key, self.v)
        self.assertEqual(self.v["upstream_name"], "VeraCrypt")
        self.assertRegex(self.port, r"^\d+\.\d+\.\d+$")
        self.assertEqual(len(self.commit), 40)

    def test_port_version_h(self) -> None:
        h = read("src/Main/PortVersion.h")
        self.assertRegex(h, rf'#define VC_PORT_VERSION\s+"{re.escape(self.port)}"')
        self.assertRegex(h, rf'#define VC_PORT_UPSTREAM_VERSION\s+"{re.escape(self.upstream)}"')
        self.assertRegex(h, rf'#define VC_PORT_UPSTREAM_COMMIT\s+"{re.escape(self.commit)}"')
        self.assertIn("ShivamPingaleDev/Veracrypt_port", h)
        self.assertIn("ports/version.json", h)

    def test_upstream_commit_file(self) -> None:
        pin = read("ports/UPSTREAM_COMMIT").strip()
        self.assertEqual(pin, self.commit)

    def test_android_gradle(self) -> None:
        self.assertEqual(gradle_field("versionName"), self.port)
        self.assertEqual(gradle_field("applicationId"), "dev.shivampingale.vcport")
        self.assertEqual(gradle_field("minSdk"), "28")
        self.assertEqual(gradle_field("targetSdk"), "35")
        gradle = read("ports/android/app/build.gradle")
        self.assertIn("armeabi-v7a", gradle)
        self.assertIn("arm64-v8a", gradle)
        self.assertIn("'x86'", gradle)
        self.assertIn("x86_64", gradle)
        self.assertIn("ENABLE_UPDATE_CHECK", gradle)
        self.assertIn("minifyEnabled false", gradle)
        self.assertNotIn("play-services", gradle)
        self.assertNotIn("firebase", gradle.lower())

    def test_android_update_checkers(self) -> None:
        fdroid = read("ports/android/app/src/fdroid/java/dev/shivampingale/vcport/UpdateChecker.kt")
        github = read("ports/android/app/src/github/java/dev/shivampingale/vcport/UpdateChecker.kt")
        self.assertIn(f'LOCAL_VERSION = "{self.port}"', fdroid)
        self.assertIn(f'LOCAL_VERSION = "{self.port}"', github)
        self.assertIn('error("F-Droid build has no network")', fdroid)
        self.assertIn("raw.githubusercontent.com/ShivamPingaleDev/Veracrypt_port", github)
        self.assertNotIn("INTERNET", fdroid)

    def test_ios_plist_and_xcodegen(self) -> None:
        plist = read("ports/ios/VCPort/Info.plist")
        yml = read("ports/ios/project.yml")
        self.assertIn(f"<string>{self.port}</string>", plist)
        self.assertIn(f"<string>{self.code}</string>", plist)
        self.assertIn("CFBundleDisplayName", plist)
        self.assertIn("<string>VC Port</string>", plist)
        self.assertIn("<string>dev.shivampingale.vcport</string>", plist)
        self.assertIn("<key>UIFileSharingEnabled</key>", plist)
        self.assertIn("<false/>", plist)
        self.assertIn("<key>VCPortEnableUpdateCheck</key>", plist)
        self.assertIn("MARKETING_VERSION: " + self.port, yml)
        self.assertIn(f"CURRENT_PROJECT_VERSION: {self.code}", yml)
        self.assertIn("PRODUCT_BUNDLE_IDENTIFIER: dev.shivampingale.vcport", yml)
        self.assertIn('iOS: "16.0"', yml)

    def test_ios_update_checker(self) -> None:
        swift = read("ports/ios/VCPort/UpdateChecker.swift")
        self.assertIn(f'static let localVersion = "{self.port}"', swift)
        self.assertIn("ShivamPingaleDev/Veracrypt_port", swift)

    def test_fdroiddata_current(self) -> None:
        yml = read("ports/fdroiddata/metadata/dev.shivampingale.vcport.yml")
        self.assertIn(f"CurrentVersion: {self.port}", yml)
        self.assertIn(f"CurrentVersionCode: {self.code}", yml)
        self.assertIn(f"versionName: {self.port}", yml)
        self.assertIn(f"versionCode: {int(self.code)}", yml)
        self.assertIn("Name: VC Port", yml)
        self.assertIn("truecrypt.org", yml.lower())
        self.assertNotIn("Name: VeraCrypt", yml)

    def test_changelog_mentions_current(self) -> None:
        log = read("ports/CHANGELOG.md")
        self.assertIn(f"## {self.port}", log)


class NamingAndAttributionTests(unittest.TestCase):
    def test_android_label_is_vc_port(self) -> None:
        strings = read("ports/android/app/src/main/res/values/strings.xml")
        self.assertIn('<string name="app_name">VC Port</string>', strings)
        self.assertIn("truecrypt.org", strings)
        self.assertIn("This app is not named VeraCrypt", strings)

    def test_notice(self) -> None:
        notice = read("ports/NOTICE")
        self.assertIn("TrueCrypt", notice)
        self.assertIn("not named VeraCrypt", notice)
        self.assertIn("Apache-2.0", notice)

    def test_mobile_sources_do_not_brand_as_veracrypt(self) -> None:
        for rel in (
            "ports/android/app/src/main/res/values/strings.xml",
            "ports/ios/VCPort/Info.plist",
            "ports/fdroiddata/metadata/dev.shivampingale.vcport.yml",
        ):
            text = read(rel)
            self.assertNotRegex(
                text,
                r'name="app_name">VeraCrypt<',
                msg=rel,
            )
            self.assertNotIn("<string>VeraCrypt</string>", text)


class AndroidHighThreatTests(unittest.TestCase):
    def test_main_manifest_offline_and_hardened(self) -> None:
        manifest = read("ports/android/app/src/main/AndroidManifest.xml")
        self.assertNotIn("android.permission.INTERNET", manifest)
        self.assertIn('android:allowBackup="false"', manifest)
        self.assertIn('android:usesCleartextTraffic="false"', manifest)
        self.assertIn('android:excludeFromRecents="true"', manifest)
        self.assertIn('android:launchMode="singleTask"', manifest)
        self.assertIn('android:exported="false"', manifest)
        self.assertIn("dev.shivampingale.vcport.share", manifest)
        self.assertNotIn("VolumeDocumentsProvider", manifest)
        self.assertNotIn("MANAGE_EXTERNAL_STORAGE", manifest)
        self.assertIn("network_security_config", manifest)
        self.assertIn("backup_rules", manifest)
        self.assertIn("data_extraction_rules", manifest)

    def test_fdroid_removes_internet(self) -> None:
        fdroid = read("ports/android/app/src/fdroid/AndroidManifest.xml")
        self.assertIn("android.permission.INTERNET", fdroid)
        self.assertIn('tools:node="remove"', fdroid)

    def test_github_flavor_has_opt_in_internet_only(self) -> None:
        github = read("ports/android/app/src/github/AndroidManifest.xml")
        self.assertIn("android.permission.INTERNET", github)
        self.assertIn("user-tapped update check", github.lower())

    def test_network_security_system_cas_only(self) -> None:
        for rel in (
            "ports/android/app/src/main/res/xml/network_security_config.xml",
            "ports/android/app/src/github/res/xml/network_security_config.xml",
        ):
            xml = read(rel)
            self.assertIn('cleartextTrafficPermitted="false"', xml)
            self.assertIn('<certificates src="system"', xml)
            self.assertNotIn('src="user"', xml)

    def test_file_provider_share_only(self) -> None:
        paths = read("ports/android/app/src/main/res/xml/file_paths.xml")
        self.assertIn('path="share/"', paths)
        self.assertNotIn('path="."', paths)
        self.assertNotIn("external-path", paths)

    def test_backup_excludes_everything(self) -> None:
        backup = read("ports/android/app/src/main/res/xml/backup_rules.xml")
        extract = read("ports/android/app/src/main/res/xml/data_extraction_rules.xml")
        for domain in ("sharedpref", "file", "database"):
            self.assertIn(domain, backup)
            self.assertIn(domain, extract)
        self.assertIn("device-transfer", extract)

    def test_hardening_source_contracts(self) -> None:
        hard = read("ports/android/app/src/main/java/dev/shivampingale/vcport/Hardening.kt")
        self.assertIn("FLAG_SECURE", hard)
        self.assertIn("wipeSessionFiles", hard)
        self.assertIn("fun panic", hard)
        self.assertIn("vc_port_volume_key", hard)
        self.assertNotIn("takePersistableUriPermission", hard)

    def test_biometric_strong_only(self) -> None:
        vault = read("ports/android/app/src/main/java/dev/shivampingale/vcport/BiometricVault.kt")
        self.assertIn("BIOMETRIC_STRONG", vault)
        self.assertNotIn("DEVICE_CREDENTIAL", vault)
        self.assertIn("AES/GCM/NoPadding", vault)
        self.assertIn("setIsStrongBoxBacked", vault)

    def test_no_gms_firebase_play_integrity(self) -> None:
        blob = ""
        for path in (PORTS / "android").rglob("*"):
            if path.suffix.lower() in {".gradle", ".kts", ".properties"}:
                blob += path.read_text(encoding="utf-8", errors="ignore")
        for needle in (
            "com.google.firebase",
            "play-services",
            "PlayIntegrity",
            "SafetyNet",
            "crashlytics",
        ):
            self.assertNotIn(needle, blob)

    def test_compelled_biometrics_copy(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        self.assertIn("compelled", main.lower())
        self.assertIn("not unbreakable", main.lower())


class IosHighThreatTests(unittest.TestCase):
    def test_privacy_manifest_no_tracking(self) -> None:
        privacy = read("ports/ios/VCPort/PrivacyInfo.xcprivacy")
        self.assertIn("<key>NSPrivacyTracking</key>", privacy)
        self.assertIn("<false/>", privacy)
        self.assertIn("<key>NSPrivacyCollectedDataTypes</key>", privacy)
        self.assertIn("<array/>", privacy)

    def test_entitlements_keychain_only(self) -> None:
        ent = read("ports/ios/VCPort/VCPort.entitlements")
        self.assertIn("keychain-access-groups", ent)
        self.assertIn("dev.shivampingale.vcport", ent)
        self.assertNotIn("com.apple.developer.networking.networkextension", ent)
        self.assertNotIn("icloud", ent.lower())

    def test_foss_update_check_defaults_off(self) -> None:
        foss = read("ports/ios/VCPort/FossConfig.swift")
        self.assertIn("?? false", foss)

    def test_compelled_biometrics_copy(self) -> None:
        view = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("compelled", view.lower())
        self.assertIn("not unbreakable", view.lower())
        self.assertIn("Panic", view)


class MacosDesktopTests(unittest.TestCase):
    def test_stay_offline_default(self) -> None:
        prefs = read("src/Main/UserPreferences.h")
        self.assertIn("StayOffline (true)", prefs)

    def test_fuse_t_does_not_force_smb(self) -> None:
        fuse = read("src/Driver/Fuse/FuseService.cpp")
        self.assertIn("fuseTHasBackend", fuse)
        self.assertIn("backend=smb", fuse)
        self.assertIn("go-smb2", fuse)
        self.assertIn("leave FUSE-T on its default NFS backend", fuse)
        self.assertIn("Forcing SMB when the backend is missing hangs the mount", fuse)

    def test_port_version_header_exists_for_desktop(self) -> None:
        self.assertTrue((ROOT / "src/Main/PortVersion.h").is_file())

    def test_desktop_gui_has_wrap_panic_share(self) -> None:
        lang = read("src/Common/Language.xml")
        for key in (
            "IDM_WRAP_FILE",
            "IDM_UNWRAP_FILE",
            "IDM_SHARE_ENCRYPTED",
            "IDM_PANIC_WIPE",
            "MACOSX_BIOMETRIC_COMPELLED",
        ):
            self.assertIn(f'key="{key}"', lang)
        make = read("src/Main/Main.make")
        self.assertIn("PortFileWrap.o", make)
        self.assertIn("VcWrap.o", make)
        self.assertIn("MacOSXShare.o", make)
        frame = read("src/Main/Forms/MainFrame.cpp")
        self.assertIn("OnPanicWipeMenuItemSelected", frame)
        self.assertIn("PortFileWrap::WrapFile", frame)
        bio = read("src/Main/MacOSXBiometric.h")
        self.assertIn("DeleteAllStoredPasswords", bio)


class CrossPortGuiParityTests(unittest.TestCase):
    """Same discussed features must appear in every device GUI."""

    def test_wrap_panic_share_stay_offline_on_android(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        self.assertIn("Panic wipe", main)
        self.assertIn("Encrypt file", main)
        self.assertIn("Decrypt wrap", main)
        self.assertIn("Share encrypted", main)
        self.assertIn("Stay offline", main)
        self.assertIn("compelled", main.lower())

    def test_wrap_panic_share_stay_offline_on_ios(self) -> None:
        view = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("Panic wipe", view)
        self.assertIn("Encrypt file", view)
        self.assertIn("Decrypt wrap", view)
        self.assertIn("Share encrypted file", view)
        self.assertIn("Stay offline", view)
        self.assertIn("compelled", view.lower())

    def test_desktop_tools_menu_wired(self) -> None:
        frame = read("src/Main/Forms/MainFrame.cpp")
        self.assertIn('LangString["IDM_WRAP_FILE"]', frame)
        self.assertIn('LangString["IDM_UNWRAP_FILE"]', frame)
        self.assertIn('LangString["IDM_SHARE_ENCRYPTED"]', frame)
        self.assertIn('LangString["IDM_PANIC_WIPE"]', frame)


    def test_cmake_cpu_slices_are_explicit(self) -> None:
        cmake = read("ports/shared/upstream-sources.cmake")
        self.assertIn("ANDROID_ABI", cmake)
        self.assertIn("opt_avx2.c", cmake)
        self.assertIn("opt_sse2.c", cmake)
        self.assertIn("Aes_hw_armv8.c", cmake)
        wrap = read("ports/shared/run_wrap_test.sh")
        self.assertIn("arm64|aarch64", wrap)
        self.assertIn("x86_64|amd64", wrap)
        self.assertIn("i686|i386", wrap)
        ios = read("ports/ios/build-native.sh")
        self.assertIn("iphonesimulator", ios)
        self.assertIn("x86_64", ios)
        yml = read("ports/ios/project.yml")
        self.assertIn("IOS_SDK", yml)
        self.assertIn("TARGETED_DEVICE_FAMILY: \"1,2\"", yml)


class SharedNativeContractsTests(unittest.TestCase):
    def test_wrap_kdf_is_32mib_argon2id(self) -> None:
        wrap = read("ports/shared/vc_wrap.cpp")
        self.assertIn("const uint32_t kMemKib = 32768", wrap)
        self.assertIn("fopen_private_write", wrap)
        self.assertIn("O_CREAT | O_TRUNC, 0600", wrap)
        self.assertIn("mlock", wrap)
        self.assertIn("sanitize_name", wrap)

    def test_cmake_does_not_compile_windows_blake2s_ref(self) -> None:
        cmake = read("ports/shared/upstream-sources.cmake")
        self.assertNotIn("blake2s-ref.c", cmake)
        self.assertIn("blake2s.c", cmake)
        self.assertIn("opt_sse2.c", cmake)
        self.assertIn("opt_avx2.c", cmake)

    def test_cmake_listed_files_exist(self) -> None:
        cmake = read("ports/shared/upstream-sources.cmake")
        missing = []
        for rel in re.findall(r"\$\{VC_SRC\}/([A-Za-z0-9_./-]+\.(?:cpp|c))\b", cmake):
            path = ROOT / "src" / rel
            if not path.is_file():
                missing.append(rel)
        self.assertEqual(missing, [])

    def test_jni_does_not_log_passwords(self) -> None:
        jni = read("ports/shared/android_jni.cpp")
        self.assertNotIn("__android_log_print", jni)
        self.assertIn("vc_secure_wipe", jni)


class OverlayInventoryTests(unittest.TestCase):
    def _list(self, rel: str) -> list[str]:
        lines = []
        for line in read(rel).splitlines():
            line = line.strip()
            if line and not line.startswith("#"):
                lines.append(line)
        return lines

    def test_owned_and_patched_disjoint(self) -> None:
        owned = set(self._list("ports/overlay/owned.txt"))
        patched = set(self._list("ports/overlay/patched.txt"))
        self.assertTrue(owned)
        self.assertTrue(patched)
        self.assertFalse(owned & patched)

    def test_patched_files_exist(self) -> None:
        for rel in self._list("ports/overlay/patched.txt"):
            self.assertTrue((ROOT / rel).is_file(), rel)

    def test_owned_port_files_exist(self) -> None:
        for rel in self._list("ports/overlay/owned.txt"):
            self.assertTrue((ROOT / rel).is_file(), rel)


if __name__ == "__main__":
    unittest.main(verbosity=2)
