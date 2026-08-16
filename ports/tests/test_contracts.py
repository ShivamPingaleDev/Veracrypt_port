#!/usr/bin/env python3
"""Device-version and high-threat contract tests.

Run without Android SDK, Xcode, or FUSE-T. These assert that every VC Port
surface (Android F-Droid, Android GitHub, iOS, macOS overlay, shared native)
stays on the same version pin and keeps the FOSS / high-threat defaults.
"""

from __future__ import annotations

import json
import re
import subprocess
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from repo_paths import ROOT, PORTS, FULL_TREE, read, resolve  # noqa: E402


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
        cls.code = str(cls.v["android_version_code"])
        cls.repo = cls.v["source_repo"]
        cls.manifest = cls.v["update_manifest"]

    def test_version_json_shape(self) -> None:
        for key in (
            "port_version",
            "android_version_code",
            "upstream_name",
            "upstream_git",
            "upstream_releases",
            "upstream_tag",
            "upstream_version",
            "upstream_commit",
            "source_repo",
            "update_manifest",
            "notes",
            "download_url",
            "android_url",
            "android_apk_sha256",
            "source_sha256",
        ):
            self.assertIn(key, self.v)
        self.assertEqual(self.v["upstream_name"], "VeraCrypt")
        self.assertEqual(self.v["upstream_git"], "https://github.com/veracrypt/VeraCrypt.git")
        self.assertEqual(
            self.v["upstream_releases"],
            "https://api.github.com/repos/veracrypt/VeraCrypt/releases/latest",
        )
        self.assertTrue(str(self.v["upstream_tag"]).startswith("VeraCrypt_"))
        self.assertRegex(self.port, r"^\d+\.\d+\.\d+$")
        self.assertEqual(len(self.commit), 40)
        self.assertEqual(int(self.v["android_version_code"]), int(self.code))
        self.assertTrue(self.repo.startswith("https://"))
        self.assertTrue(self.manifest.startswith("https://"))
        self.assertIn("Veracrypt_port", self.repo)
        self.assertIn("ports/version.json", self.manifest)

    def test_port_version_h(self) -> None:
        h = read("src/Main/PortVersion.h")
        self.assertRegex(h, rf'#define VC_PORT_VERSION\s+"{re.escape(self.port)}"')
        self.assertRegex(h, rf'#define VC_PORT_UPSTREAM_VERSION\s+"{re.escape(self.upstream)}"')
        self.assertRegex(h, rf'#define VC_PORT_UPSTREAM_COMMIT\s+"{re.escape(self.commit)}"')
        self.assertIn("VC_PORT_UPSTREAM_GIT", h)
        self.assertIn("https://github.com/veracrypt/VeraCrypt.git", h)
        self.assertIn("VC_PORT_UPSTREAM_RELEASES", h)
        self.assertRegex(h, rf'#define VC_PORT_SOURCE_REPO\s+"{re.escape(self.repo)}"')
        self.assertRegex(h, rf'#define VC_PORT_UPDATE_MANIFEST_URL\s+"{re.escape(self.manifest)}"')
        self.assertIn("ShivamPingaleDev/Veracrypt_port", h)
        self.assertIn("ports/version.json", h)

    def test_upstream_commit_file(self) -> None:
        pin = read("ports/UPSTREAM_COMMIT").strip()
        self.assertEqual(pin, self.commit)

    def test_android_gradle(self) -> None:
        self.assertEqual(gradle_field("applicationId"), "dev.shivampingale.vcport")
        self.assertEqual(gradle_field("minSdk"), "28")
        self.assertEqual(gradle_field("targetSdk"), "35")
        gradle = read("ports/android/app/build.gradle")
        self.assertIn("versionJson.port_version", gradle)
        self.assertIn("android_version_code", gradle)
        self.assertIn("SOURCE_MANIFEST", gradle)
        self.assertIn("version.json", gradle)
        self.assertIn("armeabi-v7a", gradle)
        self.assertIn("arm64-v8a", gradle)
        self.assertIn("'x86'", gradle)
        self.assertIn("x86_64", gradle)
        self.assertIn("ENABLE_UPDATE_CHECK", gradle)
        self.assertIn("ENABLE_SKINS", gradle)
        self.assertNotIn("applicationIdSuffix", gradle)
        self.assertIn("UPSTREAM_GIT", gradle)
        self.assertIn("UPSTREAM_RELEASES", gradle)
        self.assertIn("minifyEnabled false", gradle)
        self.assertIn("abortOnError true", gradle)
        self.assertIn("VC_PORT_RELEASE_STORE_FILE", gradle)
        self.assertNotIn("play-services", gradle)
        self.assertNotIn("firebase", gradle.lower())

    def test_android_update_checkers(self) -> None:
        pin = read("ports/android/app/src/main/java/dev/shivampingale/vcport/SourcePin.kt")
        fdroid = read("ports/android/app/src/fdroid/java/dev/shivampingale/vcport/UpdateChecker.kt")
        github = read("ports/android/app/src/github/java/dev/shivampingale/vcport/UpdateChecker.kt")
        styled = read("ports/android/app/src/styled/java/dev/shivampingale/vcport/UpdateChecker.kt")
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        self.assertIn("BuildConfig.PORT_VERSION", pin)
        self.assertIn("BuildConfig.SOURCE_MANIFEST", pin)
        self.assertIn("never downloads or installs", pin)
        self.assertIn("SourcePin.localVersion", fdroid)
        self.assertIn("SourcePin.localVersion", github)
        self.assertIn("SourcePin.manifest", github)
        self.assertIn('error("F-Droid build has no network")', fdroid)
        self.assertIn('error("Looks build has no network")', styled)
        self.assertNotIn("INTERNET", styled)
        self.assertIn("android_apk_sha256", github)
        self.assertIn("upstream_commit", github)
        self.assertIn("upstreamReleases", github)
        self.assertIn("officialNewer", github)
        self.assertIn("sourceMoved", github)
        self.assertIn("sourceDegraded", github)
        self.assertIn("instanceFollowRedirects = false", github)
        self.assertIn("TrustedNet.allow", github)
        self.assertIn("WINDOW_MS", github)
        self.assertIn("does not install itself", main)
        self.assertIn("sync-upstream.sh", main)
        self.assertIn("SourcePin.describeBuild", main)
        self.assertNotIn("INTERNET", fdroid)
        net = read("ports/android/app/src/main/java/dev/shivampingale/vcport/TrustedNet.kt")
        self.assertIn("www.githubstatus.com", net)
        self.assertIn("api.github.com", net)
        self.assertIn("raw.githubusercontent.com", net)
        self.assertNotIn("ServerSocket", main)
        self.assertNotIn("ServerSocket", github)

    def test_ios_plist_and_xcodegen(self) -> None:
        plist = read("ports/ios/VCPort/Info.plist")
        yml = read("ports/ios/project.yml")
        self.assertIn(f"<string>{self.port}</string>", plist)
        self.assertIn(f"<string>{self.code}</string>", plist)
        self.assertIn("CFBundleDisplayName", plist)
        self.assertIn("<string>VC Port</string>", plist)
        self.assertIn("<string>dev.shivampingale.vcport</string>", plist)
        self.assertIn("<key>NSAppTransportSecurity</key>", plist)
        self.assertIn("<key>NSAllowsArbitraryLoads</key>", plist)
        self.assertIn("<false/>", plist)
        self.assertIn("<key>VCPortEnableUpdateCheck</key>", plist)
        self.assertIn("<key>VCPortSourceRepo</key>", plist)
        self.assertIn("<key>VCPortUpdateManifest</key>", plist)
        self.assertIn(f"<string>{self.repo}</string>", plist)
        self.assertIn(f"<string>{self.manifest}</string>", plist)
        self.assertIn(f"<string>{self.upstream}</string>", plist)
        self.assertIn(f"<string>{self.commit}</string>", plist)
        self.assertIn("VCPortUpstreamGit", plist)
        self.assertIn("https://github.com/veracrypt/VeraCrypt.git", plist)
        self.assertIn("VCPortUpstreamReleases", plist)
        self.assertIn("MARKETING_VERSION: " + self.port, yml)
        self.assertIn(f"CURRENT_PROJECT_VERSION: {self.code}", yml)
        self.assertIn("PRODUCT_BUNDLE_IDENTIFIER: dev.shivampingale.vcport", yml)
        self.assertIn('iOS: "16.0"', yml)

    def test_ios_update_checker(self) -> None:
        swift = read("ports/ios/VCPort/UpdateChecker.swift")
        pin = read("ports/ios/VCPort/SourcePin.swift")
        view = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("SourcePin.localVersion", swift)
        self.assertIn("SourcePin.manifestURL", swift)
        self.assertIn("sourceMoved", swift)
        self.assertIn("officialNewer", swift)
        self.assertIn("sourceDegraded", swift)
        self.assertIn("TrustedNet", pin)
        self.assertIn("NoRedirect", swift)
        self.assertIn("completionHandler(nil)", swift)
        self.assertIn("upstreamReleases", swift)
        self.assertIn("never downloads or installs", pin)
        self.assertIn("SourcePin.describeBuild", view)
        self.assertIn("does not install itself", view)
        self.assertIn("sync-upstream.sh", view)

    def test_official_veracrypt_pin_script(self) -> None:
        import subprocess

        rc = subprocess.run(
            [sys.executable, str(resolve("ports/scripts/check_veracrypt_release.py")), "--pin-only"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(rc.returncode, 0, rc.stderr)
        self.assertIn("VeraCrypt_1.26.29", rc.stdout)

    def test_source_pin_script_matches(self) -> None:
        import subprocess

        rc = subprocess.run(
            [sys.executable, str(resolve("ports/scripts/sync_source_pin.py")), "--check"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(rc.returncode, 0, rc.stderr)

    def test_fdroiddata_current(self) -> None:
        yml = read("ports/fdroiddata/metadata/dev.shivampingale.vcport.yml")
        self.assertIn(f"CurrentVersion: {self.port}", yml)
        self.assertIn(f"CurrentVersionCode: {self.code}", yml)
        self.assertIn(f"versionName: {self.port}", yml)
        self.assertIn(f"versionCode: {int(self.code)}", yml)
        self.assertIn("Name: VC Port", yml)
        self.assertIn("truecrypt.org", yml.lower())
        self.assertIn("TrueCrypt License 3.0", yml)
        self.assertIn("not OSI", yml)
        self.assertNotIn("Name: VeraCrypt", yml)
        self.assertIn("Veracrypt_port.git", yml)
        self.assertIn("subdir: ports/android", yml)
        self.assertNotIn("VCPort.git", yml)
        self.assertNotIn("styled", yml)
        self.assertNotIn(".looks", yml)

    def test_changelog_mentions_current(self) -> None:
        log = read("ports/CHANGELOG.md")
        self.assertIn(f"## {self.port}", log)
        notes = resolve(f"ports/android/fastlane/metadata/android/en-US/changelogs/{self.code}.txt")
        self.assertTrue(notes.is_file(), notes)


class NamingAndAttributionTests(unittest.TestCase):
    def test_android_label_is_vc_port(self) -> None:
        strings = read("ports/android/app/src/main/res/values/strings.xml")
        self.assertIn('<string name="app_name">VC Port</string>', strings)
        self.assertIn("truecrypt.org", strings)
        self.assertIn("This app is not named VeraCrypt", strings)
        self.assertIn("shivampingaledev@proton.me", strings)
        self.assertIn("shivampingaledev@gmail.com", strings)
        looks = read("ports/android/app/src/styled/res/values/strings.xml")
        self.assertIn('<string name="app_name">VC Port Looks</string>', looks)

    def test_notice(self) -> None:
        notice = read("ports/NOTICE")
        self.assertIn("TrueCrypt", notice)
        self.assertIn("not named VeraCrypt", notice)
        self.assertIn("Apache-2.0", notice)

    def test_security_md_contact_and_scope(self) -> None:
        sec = read("SECURITY.md")
        self.assertIn("shivampingaledev@proton.me", sec)
        self.assertIn("shivampingaledev@gmail.com", sec)
        self.assertIn("not unbreakable", sec.lower())
        self.assertIn("TrueCrypt License 3.0", sec)
        self.assertIn("no key escrow", sec.lower())
        self.assertIn("nation-state implant", sec.lower())

    def test_nation_state_apts_are_out_of_scope(self) -> None:
        threat = read("ports/THREAT-MODEL.md")
        self.assertIn("They still win", threat)
        self.assertIn("no key escrow", threat.lower())
        self.assertIn("Unit 8200", threat)
        self.assertIn("foolproof build against", threat.lower())
        self.assertIn("claiming that would be a lie", threat.lower())
        blob = (
            read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
            + read("ports/ios/VCPort/ContentView.swift")
            + read("ports/android/fastlane/metadata/android/en-US/full_description.txt")
        )
        for claim in (
            "blocks unit 8200",
            "stops lazarus",
            "foolproof against cia",
            "absolutely foolproof",
        ):
            self.assertNotIn(claim, blob.lower())

    def test_public_md_self_sign_and_honest_name(self) -> None:
        public = read("ports/PUBLIC.md")
        foss = read("ports/FOSS.md")
        ios = read("ports/ios/README.md")
        readme = read("ports/README.md")
        for blob in (public, foss, ios, readme):
            self.assertIn("sign", blob.lower())
            self.assertIn("Apple ID", blob)
        self.assertIn("not named VeraCrypt", public)
        self.assertIn("not unbreakable", public.lower())
        self.assertIn("unsigned", public.lower())
        self.assertIn("AltStore", public)
        self.assertIn("downloadURL", public)
        self.assertIn("docs/screenshots", public)
        self.assertNotIn("unbreakable encryption", public.lower())
        self.assertIn("GitHub topics", public)
        self.assertIn("no Google Analytics", public)
        self.assertIn("Do **not** title posts", public)
        self.assertIn("PUBLIC.md", readme)
        self.assertIn("docs/screenshots/01-volume.png", readme)
        self.assertIn("docs/screenshots/05-skin-cyberpunk.png", readme)
        self.assertIn("docs/screenshots/08-skin-signal.png", readme)
        for blob in (public, readme):
            self.assertIn("internship", blob.lower())
            self.assertIn("teach", blob.lower())
            self.assertIn("hire", blob.lower())
            self.assertIn("No pressure", blob)
        if FULL_TREE:
            root = read("README.md")
            self.assertIn("internship", root.lower())
            self.assertIn("No pressure", root)

    def test_android_readme_has_no_documents_provider(self) -> None:
        readme = read("ports/android/README.md")
        self.assertNotIn("DocumentsProvider stub", readme)
        self.assertIn("no DocumentsProvider", readme)

    def test_contributing_has_item_5(self) -> None:
        text = read("ports/CONTRIBUTING.md")
        self.assertIn("5. Report security issues", text)
        self.assertIn("SECURITY.md", text)
        self.assertIn("9. Do not open drive-by pull requests on official VeraCrypt", text)

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
        self.assertIn("${applicationId}.share", manifest)
        self.assertNotIn("VolumeDocumentsProvider", manifest)
        self.assertNotIn("MANAGE_EXTERNAL_STORAGE", manifest)
        self.assertIn("network_security_config", manifest)
        self.assertIn("backup_rules", manifest)
        self.assertIn("data_extraction_rules", manifest)

    def test_fdroid_removes_internet(self) -> None:
        fdroid = read("ports/android/app/src/fdroid/AndroidManifest.xml")
        self.assertIn("android.permission.INTERNET", fdroid)
        self.assertIn('tools:node="remove"', fdroid)

    def test_styled_looks_package_has_no_internet(self) -> None:
        styled = read("ports/android/app/src/styled/AndroidManifest.xml")
        self.assertIn("android.permission.INTERNET", styled)
        self.assertIn('tools:node="remove"', styled)
        gradle = read("ports/android/app/build.gradle")
        self.assertNotIn("applicationIdSuffix", gradle)
        self.assertIn("buildConfigField 'boolean', 'ENABLE_SKINS', 'true'", gradle)

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
        vault = read("ports/android/app/src/main/java/dev/shivampingale/vcport/BiometricVault.kt")
        self.assertIn("FLAG_SECURE", hard)
        self.assertIn("wipeSessionFiles", hard)
        self.assertIn("fun panic", hard)
        self.assertIn("vc_port_volume_key", vault)
        self.assertIn("BiometricVault.KEY_ALIAS", hard)
        self.assertNotIn("takePersistableUriPermission", hard)

    def test_biometric_strong_only(self) -> None:
        vault = read("ports/android/app/src/main/java/dev/shivampingale/vcport/BiometricVault.kt")
        ios = read("ports/ios/VCPort/BiometricStore.swift")
        self.assertIn("BIOMETRIC_STRONG", vault)
        self.assertNotIn("BIOMETRIC_WEAK", vault)
        self.assertIn("DEVICE_CREDENTIAL", vault)
        self.assertIn("AES/GCM/NoPadding", vault)
        self.assertIn("setIsStrongBoxBacked", vault)
        self.assertIn("userPresence", ios)
        self.assertIn("deviceOwnerAuthentication", ios)

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
        self.assertIn("no key escrow", main.lower())
        self.assertIn("nation-state implant still wins", main.lower())

    def test_about_has_cypherpunk_quote(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        for blob in (main, view):
            self.assertIn("We must defend our own privacy if we expect to have any", blob)
            self.assertIn("Eric Hughes", blob)
            self.assertIn("Cypherpunk", blob)

    def test_never_save_history_is_default(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        theme = read("ports/android/app/src/main/java/dev/shivampingale/vcport/VcPortTheme.kt")
        hardening = read("ports/android/app/src/main/java/dev/shivampingale/vcport/Hardening.kt")
        self.assertIn("var rememberBio by remember { mutableStateOf(false) }", main)
        self.assertIn("Type REMEMBER", main)
        self.assertIn("rememberConfirmOpen", main)
        self.assertIn("hasBio && rememberBio", main)
        self.assertIn("KeyboardType.Password", theme)
        self.assertIn("autoCorrect = false", theme)
        self.assertIn("IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS", hardening)

    def test_create_defaults_match_desktop_wizard(self) -> None:
        bridge = read("ports/android/app/src/main/java/dev/shivampingale/vcport/NativeBridge.kt")
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        ios = read("ports/ios/VCPort/VcMobileBridge.swift")
        view = read("ports/ios/VCPort/ContentView.swift")
        blob = bridge + main + ios + view
        self.assertIn("AES(Twofish(Serpent))", blob)
        self.assertIn("HMAC-SHA-512", blob)
        self.assertIn("HMAC-BLAKE2s-256", blob)
        self.assertIn("HMAC-Whirlpool", blob)
        self.assertIn("HMAC-Streebog", blob)
        self.assertIn("Argon2", blob)
        self.assertIn("Kuznyechik(Serpent(Camellia))", blob)
        self.assertIn("Serpent(Twofish(AES))", blob)
        self.assertIn("DEFAULT_CIPHER", bridge)
        self.assertIn("defaultCipher", ios)
        native = read("ports/shared/vc_mobile.cpp")
        self.assertIn("AES(Twofish(Serpent))", native)
        self.assertIn("HMAC-SHA-512", native)
        self.assertIn("standard VeraCrypt", main)
        self.assertIn("writeSecret", main)
        self.assertIn("standard VeraCrypt", view)
        self.assertIn("vc_entropy_add", native)
        self.assertIn("hidden_size_bytes", native)
        self.assertIn("Move your finger", main)
        self.assertIn("Nested volume", main)
        self.assertIn("Move your finger", view)
        self.assertIn("Nested volume", view)


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
        self.assertIn("no key escrow", view.lower())
        self.assertIn("nation-state implant still wins", view.lower())
        self.assertIn("Panic", view)

    def test_never_save_history_is_default(self) -> None:
        view = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("rememberBiometrics = false", view)
        self.assertIn("Type REMEMBER", view)
        self.assertIn("neverSaveHistory()", view)
        self.assertGreaterEqual(view.count("SecureField("), 5)
        self.assertGreaterEqual(view.count(".neverSaveHistory()"), 5)
        self.assertIn("textContentType(.oneTimeCode)", view)
        self.assertIn("hasBio && remember", view)


class MacosDesktopTests(unittest.TestCase):
    def test_stay_offline_default(self) -> None:
        prefs = read("src/Main/UserPreferences.h")
        self.assertIn("StayOffline (true)", prefs)
        self.assertIn("SaveHistory (false)", prefs)
        cpp = read("src/Main/UserPreferences.cpp")
        self.assertIn('formatter.AddEntry (L"SaveHistory", false)', cpp)
        self.assertIn("never restore volume-path history", cpp)
        history = read("src/Main/VolumeHistory.cpp")
        self.assertIn("WipeHistoryFile", history)
        self.assertIn("ConfirmEnable", history)
        self.assertNotIn("historyXml", history)
        frame = read("src/Main/Forms/MainFrame.cpp")
        self.assertIn("VolumeHistory::ConfirmEnable", frame)
        filecpp = read("src/Platform/Unix/File.cpp")
        self.assertIn("TC_IOS", filecpp)
        keyfile = read("src/Volume/Keyfile.cpp")
        self.assertIn("TC_IOS", keyfile)

    def test_fuse_t_does_not_force_smb(self) -> None:
        fuse = read("src/Driver/Fuse/FuseService.cpp")
        self.assertIn("fuseTHasBackend", fuse)
        self.assertIn("fuseTHasBackend", fuse)
        self.assertIn("backend=smb", fuse)
        self.assertIn("go-smb2", fuse)
        self.assertIn("leave FUSE-T on its default NFS backend", fuse)
        self.assertIn("Forcing SMB when the backend is missing hangs the mount", fuse)

    def test_port_version_header_exists_for_desktop(self) -> None:
        self.assertIn("VC_PORT_VERSION", read("src/Main/PortVersion.h"))

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
        self.assertIn("GetPreferences().StayOffline", frame)
        self.assertIn("PANIC_WIPE_DISMOUNT_FAILED", frame)
        update = read("src/Main/OfflineUpdate.cpp")
        self.assertIn("VCPort-OfflineUpdate/", update)
        self.assertIn("VC_PORT_VERSION", update)
        self.assertIn("android_apk_sha256", update)
        self.assertNotIn("VCPort-OfflineUpdate/0.1", update)
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
        self.assertIn("Copy once", main)
        self.assertIn("generatePassword(64)", main)
        self.assertIn("64-character password", main)

    def test_wrap_panic_share_stay_offline_on_ios(self) -> None:
        view = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("Panic wipe", view)
        self.assertIn("Encrypt file", view)
        self.assertIn("Decrypt wrap", view)
        self.assertIn("Share encrypted file", view)
        self.assertIn("Stay offline", view)
        self.assertIn("compelled", view.lower())
        self.assertIn("Copy once", view)
        self.assertIn("64-character password", view)
        self.assertIn("generatePassword(length: Int32 = 64)", read("ports/ios/VCPort/VcMobileBridge.swift"))
        self.assertIn("VC_ENTROPY_NEED = 8192", read("ports/shared/vc_mobile.cpp"))

    def test_volume_tools_on_android_and_ios(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        native = read("ports/android/app/src/main/java/dev/shivampingale/vcport/NativeBridge.kt")
        header = read("ports/shared/vc_mobile.h")
        for blob in (main, view):
            self.assertIn("Change volume password", blob)
            self.assertIn("Backup volume header", blob)
            self.assertIn("Restore volume header", blob)
            self.assertIn("Wipe cached passwords", blob)
            self.assertIn("USB/OTG", blob)
            self.assertIn("Device encryption", blob)
            self.assertIn("Security tokens", blob)
            self.assertIn("Keyfile generator", blob)
            self.assertIn("Volume properties", blob)
            self.assertIn("Set header key derivation algorithm", blob)
            self.assertIn("Remove all keyfiles from volume", blob)
        self.assertIn("cannot encrypt the phone", main.lower())
        self.assertIn("cannot encrypt the iphone", view.lower())
        self.assertIn("changeHeader", native)
        self.assertIn("vc_change_header", header)
        self.assertIn("vc_backup_headers", header)
        self.assertIn("vc_generate_keyfile", header)

    def test_copy_move_device_files_on_android_and_ios(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        native = read("ports/android/app/src/main/java/dev/shivampingale/vcport/NativeBridge.kt")
        header = read("ports/shared/vc_mobile.h")
        for blob in (main, view):
            self.assertIn("Copy from device", blob)
            self.assertIn("Copy to device", blob)
            self.assertIn("Move to device", blob)
            self.assertIn("Move from device", blob)
            self.assertIn("Could not delete the original", blob)
        self.assertIn("importFile", native)
        self.assertIn("deleteFile", native)
        self.assertIn("vc_import_file", header)
        self.assertIn("vc_delete_file", header)

    def test_desktop_file_ops_on_android_and_ios(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        native = read("ports/android/app/src/main/java/dev/shivampingale/vcport/NativeBridge.kt")
        header = read("ports/shared/vc_mobile.h")
        for blob in (main, view):
            self.assertIn("New folder", blob)
            self.assertIn("Rename", blob)
            self.assertIn("Wipe free space", blob)
            self.assertIn("Restore from embedded backup header", blob)
            self.assertIn("TrueCrypt Mode", blob)
            self.assertIn("Read-only", blob)
            self.assertIn("Protect hidden volume against damage", blob)
            self.assertIn("Desktop leftovers", blob)
            self.assertIn("volume expander", blob.lower())
            self.assertIn("traveler disk", blob.lower())
            self.assertIn("rescue disk", blob.lower())
        self.assertIn("mkdir", native)
        self.assertIn("wipeFreeSpace", native)
        self.assertIn("vc_mkdir", header)
        self.assertIn("vc_rmdir", header)
        self.assertIn("vc_rename", header)
        self.assertIn("vc_wipe_free_space", header)
        self.assertIn("read_only", header)
        self.assertIn("protect_hidden", header)
        self.assertIn("vc_protection_triggered", header)

    def test_work_is_visual_on_android_and_ios(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        theme = read("ports/android/app/src/main/java/dev/shivampingale/vcport/VcPortTheme.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        native = read("ports/android/app/src/main/java/dev/shivampingale/vcport/NativeBridge.kt")
        header = read("ports/shared/vc_mobile.h")
        self.assertIn("WorkOverlay", theme)
        self.assertIn("WorkOverlay", main)
        self.assertIn("WorkOverlay", view)
        self.assertIn("progressPercent", native)
        self.assertIn("vc_progress_percent", header)
        self.assertIn("beginWork", main)
        self.assertIn("beginWork", view)

    def test_disguise_filenames_on_android_and_ios(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        helper = read("ports/android/app/src/main/java/dev/shivampingale/vcport/ShareHelper.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        desktop = read("src/Main/GraphicUserInterface.cpp")
        for blob in (main, helper, view):
            self.assertIn("photo.jpg", blob)
            self.assertIn("model.safetensors", blob)
            self.assertIn("adapter.lora", blob)
        self.assertIn("Opening ignores the extension", main)
        self.assertIn("Opening ignores the extension", view)
        self.assertIn("sanitizeDisguiseName", helper)
        self.assertIn("safetensors", desktop)
        self.assertIn("jpg", desktop)

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
        self.assertIn("-march=armv8-a+crypto", cmake)
        self.assertIn("-mbranch-protection=standard", cmake)
        lists = read("ports/shared/CMakeLists.txt")
        self.assertIn("CRYPTOPP_DISABLE_AESNI", lists)
        self.assertIn("CRYPTOPP_DISABLE_SHANI", lists)
        self.assertIn("TC_IOS", lists)
        self.assertIn('CMAKE_SYSTEM_NAME STREQUAL "iOS"', lists)
        self.assertIn("vc_progress.cpp", lists)
        self.assertIn("armv8-a+crypto", lists)
        self.assertIn("mfpu=neon", lists)
        self.assertIn("-ftree-vectorize", lists)
        wrap = read("ports/shared/run_wrap_test.sh")
        self.assertIn("vc_progress.cpp", wrap)
        self.assertIn("arm64|aarch64", wrap)
        self.assertIn("x86_64|amd64", wrap)
        self.assertIn("i686|i386", wrap)
        self.assertIn("CRYPTOPP_DISABLE_SHANI", wrap)
        self.assertIn("CRYPTOPP_DISABLE_AESNI", wrap)
        self.assertIn("-fstack-protector-strong", wrap)
        self.assertIn("-fno-common", wrap)
        self.assertIn("-mbranch-protection=standard", wrap)
        keyfile = read("src/Volume/Keyfile.cpp")
        self.assertIn("TC_PORT_NO_TOKEN", keyfile)
        self.assertIn("TC_PORT_NO_TOKEN", lists)
        ios = read("ports/ios/build-native.sh")
        self.assertIn("iphonesimulator", ios)
        self.assertIn("x86_64", ios)
        self.assertIn("--all", ios)
        self.assertIn("${SDK}-${ARCH}", ios)
        yml = read("ports/ios/project.yml")
        self.assertIn("IOS_SDK", yml)
        self.assertIn("TARGETED_DEVICE_FAMILY: \"1,2\"", yml)


class SharedNativeContractsTests(unittest.TestCase):
    def test_wrap_kdf_is_32mib_argon2id(self) -> None:
        wrap = read("ports/shared/vc_wrap.cpp")
        self.assertIn("arc4random_buf", wrap)
        self.assertNotIn("sys/random.h", wrap)
        self.assertIn("const uint32_t kMemKib = 32768", wrap)
        header = read("ports/shared/vc_mobile.h")
        self.assertIn("vc_list_dir", header)
        self.assertIn("vc_list_dir_from", header)
        self.assertIn("VC_LIST_UI_MAX", header)
        self.assertIn("vc_change_header", header)
        self.assertIn("vc_backup_headers", header)
        self.assertIn("vc_restore_headers", header)
        mobile = read("ports/shared/vc_mobile.cpp")
        self.assertIn("fat_find_path", mobile)
        self.assertIn("EXFAT   ", mobile)
        self.assertIn("fopen_private_write", wrap)
        self.assertIn("O_CREAT | O_TRUNC, 0600", wrap)
        self.assertIn("mlock", wrap)
        self.assertIn("sanitize_name", wrap)

    def test_cmake_does_not_compile_windows_blake2s_ref(self) -> None:
        cmake = read("ports/shared/upstream-sources.cmake")
        self.assertNotIn("blake2s-ref.c", cmake)
        self.assertIn("blake2s.c", cmake)
        self.assertIn("jitterentropy-base.c", cmake)
        self.assertIn('COMPILE_FLAGS "-O0"', cmake)
        self.assertIn("opt_sse2.c", cmake)
        self.assertIn("opt_avx2.c", cmake)

    def test_cmake_listed_files_exist(self) -> None:
        if not FULL_TREE:
            self.skipTest("src/ lives in Veracrypt_port")
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
        self.assertIn("listDir", jni)
        self.assertIn("vc_list_dir_from", jni)
        self.assertIn("VC_LIST_UI_MAX", jni)
        self.assertIn("!truncated!", jni)


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
        if not FULL_TREE:
            self.skipTest("patched src/ files live in Veracrypt_port")
        for rel in self._list("ports/overlay/patched.txt"):
            self.assertTrue((ROOT / rel).is_file(), rel)

    def test_owned_port_files_exist(self) -> None:
        if not FULL_TREE:
            self.skipTest("owned overlay files live in Veracrypt_port")
        for rel in self._list("ports/overlay/owned.txt"):
            self.assertTrue((ROOT / rel).is_file(), rel)


if __name__ == "__main__":
    unittest.main(verbosity=2)
