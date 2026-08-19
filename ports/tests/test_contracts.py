#!/usr/bin/env python3
"""Device-version and high-threat contract tests.

Run without Android SDK or Xcode. These assert that every VC Port
surface (Android FOSS, Android GitHub, iOS, shared native)
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
        plist = read("ports/ios/VCPort/Info.plist")
        pin = read("ports/android/app/src/main/java/dev/shivampingale/vcport/SourcePin.kt")
        self.assertIn(self.port, plist)
        self.assertIn(self.commit, plist)
        self.assertIn("https://github.com/veracrypt/VeraCrypt.git", plist)
        self.assertIn("ShivamPingaleDev/Veracrypt_port", plist)
        self.assertIn("ports/version.json", plist)
        self.assertIn("BuildConfig.PORT_VERSION", pin)
        if FULL_TREE:
            self.assertFalse(resolve("src/Main/PortVersion.h").exists())

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
        self.assertNotIn("ENABLE_SKINS", gradle)
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
        checker = read("ports/android/app/src/main/java/dev/shivampingale/vcport/UpdateChecker.kt")
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        self.assertIn("BuildConfig.PORT_VERSION", pin)
        self.assertIn("BuildConfig.SOURCE_MANIFEST", pin)
        self.assertIn("never downloads or installs", pin)
        self.assertIn("SourcePin.localVersion", checker)
        self.assertIn('error("This build has no network")', checker)
        self.assertNotIn("HttpURLConnection", checker)
        self.assertNotIn("TrustedNet", checker)
        self.assertNotIn("Check for updates", main)
        self.assertIn("does not install itself", main)
        self.assertIn("SourcePin.describeBuild", main)
        self.assertIn("sync-upstream.sh", read("ports/UPSTREAM.md"))
        self.assertFalse(
            resolve("ports/android/app/src/main/java/dev/shivampingale/vcport/TrustedNet.kt").exists()
        )
        self.assertFalse(
            resolve(
                "ports/android/app/src/github/java/dev/shivampingale/vcport/UpdateChecker.kt"
            ).exists()
        )
        self.assertFalse(
            resolve(
                "ports/android/app/src/looksgithub/java/dev/shivampingale/vcport/UpdateChecker.kt"
            ).exists()
        )
        self.assertNotIn("ServerSocket", main)
        self.assertNotIn("ServerSocket", checker)

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
        self.assertIn("TARGETED_DEVICE_FAMILY: \"1,2\"", yml)
        self.assertIn("CODE_SIGN_IDENTITY: Apple Development", yml)
        self.assertIn("Signing.xcconfig", yml)
        self.assertIn("<key>UILaunchScreen</key>", plist)
        self.assertIn("<key>CFBundleExecutable</key>", plist)
        self.assertIn("UISupportedInterfaceOrientations~ipad", plist)
        self.assertIn("ios/Signing.local.xcconfig", read("ports/.gitignore"))

    def test_ios_update_checker(self) -> None:
        swift = read("ports/ios/VCPort/UpdateChecker.swift")
        pin = read("ports/ios/VCPort/SourcePin.swift")
        view = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("SourcePin.localVersion", swift)
        self.assertIn("This build has no network", swift)
        self.assertNotIn("URLSession", swift)
        self.assertNotIn("TrustedNet", pin)
        self.assertNotIn("Check for updates", view)
        self.assertIn("never downloads or installs", pin)
        self.assertIn("SourcePin.describeBuild", view)
        self.assertIn("does not install itself", view)
        self.assertIn("sync-upstream.sh", read("ports/UPSTREAM.md"))

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

    def test_foss_flavor_is_production(self) -> None:
        gradle = read("ports/android/app/build.gradle")
        self.assertIn("foss {", gradle)
        self.assertNotIn("fdroid {", gradle)
        self.assertIn(":app:assembleFossRelease", read("ports/android/README.md"))

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
        self.assertFalse(resolve("ports/android/app/src/styled").exists())
        self.assertFalse(resolve("ports/android/app/src/looksgithub").exists())

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
        self.assertIn("build-phones.sh", foss)
        self.assertIn("VC_PORT_IOS_TEAM", foss)
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
        self.assertIn("docs/screenshots/05-mounted.png", readme)
        self.assertIn("docs/screenshots/08-skin-signal.png", readme)
        self.assertIn("docs/screenshots/ios-01-volume.png", readme)
        self.assertIn("docs/screenshots/ios-05-mounted.png", readme)
        self.assertNotIn("docs/screenshots/05-skin-cyberpunk.png", readme)
        self.assertNotIn("wrap a file", readme.lower())
        self.assertIn("TrueCrypt License 3.0", readme)
        self.assertIn("OSI", readme)
        self.assertIn("debug-signed", readme.lower())
        self.assertIn("inherit", readme.lower())
        self.assertNotIn("apple silicon extras", readme.lower())
        if FULL_TREE:
            root = read("README.md")
            self.assertNotIn("wrap a file", root.lower())
            self.assertIn("TrueCrypt License 3.0", root)
            self.assertIn("OSI", root)
            self.assertIn("debug-signed", root.lower())
            self.assertIn("inherit", root.lower())
            self.assertNotIn("apple silicon extras", root.lower())

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
            "ports/FOSS.md",
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
        self.assertNotIn("excludeFromRecents", manifest)
        self.assertNotIn("finishAndRemoveTask", read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt"))
        self.assertIn("FLAG_SECURE", read("ports/android/app/src/main/java/dev/shivampingale/vcport/Hardening.kt"))
        self.assertIn("fun fit(", read("ports/android/app/src/main/java/dev/shivampingale/vcport/SizeUnits.kt"))
        self.assertIn("SizeUnitPicker", read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt"))
        self.assertIn('android:launchMode="singleTask"', manifest)
        self.assertIn('android:exported="false"', manifest)
        self.assertIn("${applicationId}.share", manifest)
        self.assertNotIn("VolumeDocumentsProvider", manifest)
        self.assertNotIn("MANAGE_EXTERNAL_STORAGE", manifest)
        self.assertIn("network_security_config", manifest)
        self.assertIn("backup_rules", manifest)
        self.assertIn("data_extraction_rules", manifest)

    def test_foss_removes_internet(self) -> None:
        foss = read("ports/android/app/src/foss/AndroidManifest.xml")
        self.assertIn("android.permission.INTERNET", foss)
        self.assertIn('tools:node="remove"', foss)

    def test_looks_flavors_are_gone(self) -> None:
        gradle = read("ports/android/app/build.gradle")
        self.assertNotIn("styled {", gradle)
        self.assertNotIn("looksgithub {", gradle)
        self.assertNotIn("ENABLE_SKINS", gradle)
        self.assertFalse(resolve("ports/android/app/src/styled").exists())
        self.assertFalse(resolve("ports/android/app/src/looksgithub").exists())

    def test_github_flavor_has_no_internet(self) -> None:
        gradle = read("ports/android/app/build.gradle")
        self.assertNotIn("buildConfigField 'boolean', 'ENABLE_UPDATE_CHECK', 'true'", gradle)
        self.assertIn("buildConfigField 'boolean', 'ENABLE_UPDATE_CHECK', 'false'", gradle)
        github = read("ports/android/app/src/github/AndroidManifest.xml")
        self.assertIn("android.permission.INTERNET", github)
        self.assertIn('tools:node="remove"', github)

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
        share = read("ports/android/app/src/main/java/dev/shivampingale/vcport/ShareHelper.kt")
        self.assertIn("stageInShareDir", share)
        self.assertIn("sanitizeKeyfileName", share)

    def test_keyfiles_are_multiple_and_desktop_mount_options(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        for blob in (main, view):
            self.assertIn("Generate keyfile and add", blob)
            self.assertIn("Add keyfiles", blob)
            self.assertIn("Use backup header", blob)
            self.assertIn("TrueCrypt Mode", blob)
            self.assertIn("PIM (0 = default)", blob)
            self.assertNotIn("Save extra keyfile for a computer", blob)
            self.assertNotIn("How it works:", blob)
            self.assertNotIn("Create phone-unlock keyfile", blob)
            self.assertNotIn("Unlock with fingerprint", blob)
            self.assertNotIn("Unlock with Face ID", blob)
        self.assertIn("copyOwned", read("ports/android/app/src/main/java/dev/shivampingale/vcport/UnlockFactors.kt"))
        self.assertFalse(
            (PORTS / "android/app/src/main/java/dev/shivampingale/vcport/BiometricVault.kt").exists()
        )
        self.assertFalse((PORTS / "ios/VCPort/BiometricStore.swift").exists())

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
        self.assertIn("vc-in-", hard)
        self.assertIn("fun panic", hard)
        self.assertIn("vc_port_volume_key", hard)
        self.assertNotIn("BiometricVault", hard)
        self.assertNotIn("takePersistableUriPermission", hard)

    def test_pickers_keep_session_across_file_picker(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("holdLockForPicker()", main)
        self.assertNotIn("wrapHold", main)
        self.assertIn("override fun onResume()", main)
        self.assertIn("holdingForPicker", view)
        self.assertNotIn("wrapHold", view)
        self.assertIn("holdLock", view)

    def test_tools_pim_is_wiped_and_not_copied_from_volume(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        self.assertNotIn("newPim = pim", main)
        create_wipe = main.split("private fun wipeCreateSecrets()")[1].split("private fun wipeRamSecrets()")[0]
        self.assertIn('newPimState.value = "0"', create_wipe)
        leave = main.split("private fun dismountOnLeave()")[1].split("private fun wipeCreateSecrets()")[0]
        self.assertIn('newPimState.value = "0"', leave)
        ios_wipe = view.split("private func wipeCreateSecrets()")[1].split("private func clearMountOptions()")[0]
        self.assertIn('newPim = "0"', ios_wipe)
        ios_leave = view.split("private func dismountOnLeave()")[1].split("private func isTemporaryContainer")[0]
        self.assertIn('newPim = "0"', ios_leave)

    def test_volume_unlock_form_wiped_after_successful_open(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("lastUnlockPassword", main)
        self.assertIn("lastUnlockPassword", view)
        self.assertIn("private fun wipeUnlockForm()", main)
        self.assertIn("private func wipeUnlockForm()", view)
        self.assertIn("rememberUnlock(text, pimText)", main)
        self.assertIn("rememberUnlock(text, pimText)", view)
        android_open = main.split("private fun openVolumeWithFactors")[1].split("private fun handleIncoming")[0]
        self.assertNotIn("unlockPassword(", android_open)
        self.assertIn("wipeUnlockForm()", android_open)
        ios_open = view.split("private func startOpenVolume()")[1].split("private func wipeFile")[0]
        self.assertNotIn("unlockPassword()", ios_open)
        self.assertIn("wipeUnlockForm()", ios_open)
        self.assertIn("unlockPassword(password, useTextPassword)", main)
        self.assertIn("unlock.pim", view)
        leave = main.split("private fun dismountOnLeave()")[1].split("private fun wipeCreateSecrets()")[0]
        self.assertIn("forgetUnlock()", leave)
        ram = main.split("private fun wipeRamSecrets()")[1].split("private fun resetCreateWizard()")[0]
        self.assertIn("forgetUnlock()", ram)
        ios_leave = view.split("private func dismountOnLeave()")[1].split("private func isTemporaryContainer")[0]
        self.assertIn("forgetUnlock()", ios_leave)
        ios_lock = view.split("private func lockSession()")[1].split("private func panicWipe()")[0]
        self.assertIn("forgetUnlock()", ios_lock)
        form = main.split("private fun wipeUnlockForm()")[1].split("/**")[0]
        self.assertNotIn("keyfileUrisState", form)
        ios_form = view.split("private func wipeUnlockForm()")[1].split("private func currentUnlockPaths()")[0]
        self.assertNotIn("keyfileURLs", ios_form)

    def test_choose_container_keeps_session_and_shows_name(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("Selected: $containerLabel", main)
        self.assertNotIn('label = { Text("Container path") }', main)
        self.assertNotIn("bindContainerFd", main)
        self.assertIn("ensureContainerPath", main)
        self.assertIn("containerPathUsable", main)
        bind = main.split("private fun bindContainer")[1].split("private fun openVolumeWithFactors")[0]
        self.assertIn("copyToCache(uri)", bind)
        self.assertNotIn("/proc/self/fd", bind)
        self.assertIn("Selected: \\(url.lastPathComponent)", view)
        self.assertIn("ingestPickedContainer", view)
        self.assertIn("ensureContainerURL", view)

    def test_wipe_create_secrets_after_save_keeps_cache_volume(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        saver = main.split("val createSaver")[1].split("val toolSaver")[0]
        self.assertIn("wipeCreateSecrets()", saver)
        self.assertIn("copyFileToUri", saver)
        self.assertNotIn("copyContainerAsync", saver)
        self.assertIn("Create secrets were wiped", saver)
        self.assertIn("Type the volume password", saver)
        self.assertIn('File(cacheDir, "containers")', main)
        self.assertIn("KeyfileIo.uniqueNamed", main)
        copy = main.split("private fun copyToCache")[1].split("\n}")[0]
        self.assertIn("uniqueNamed", copy)
        self.assertNotIn("File(cacheDir, name)", copy)
        ios_save = view.split("SystemFiles.exportCopy(url: dest)")[1].split("private func openVolume()")[0]
        self.assertIn("wipeCreateSecrets()", ios_save)
        self.assertIn("containerURL = dest", ios_save)
        self.assertNotIn("containerURL = saved", ios_save)
        self.assertIn("Create secrets were wiped", ios_save)
        self.assertIn("Create form kept", main)
        self.assertIn("Create form kept", view)

    def test_mounted_tab_slot_column_on_android_and_ios(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn('testTag("tab_mounted")', main)
        self.assertIn("Text(\"Mounted\")", main)
        self.assertIn("MOUNT_SLOTS = 8", main)
        self.assertIn("mount_slot_", main)
        self.assertIn("Select files", main)
        self.assertIn("Tap one or more files", main)
        self.assertIn("Switch to it on the Mounted tab.", main)
        self.assertIn("tabState.intValue = 3", main)
        self.assertIn("Label(\"Mounted\"", view)
        self.assertIn("mountSlots = 8", view)
        self.assertIn("Select files", view)
        self.assertIn("Tap one or more files", view)
        self.assertIn("Switch to it on the Mounted tab.", view)
        self.assertIn("selectedTab = 3", view)
        self.assertIn("slots are this session only", main.lower())
        self.assertIn("slots are this session only", view.lower())

    def test_master_has_no_phone_biometrics(self) -> None:
        vault = PORTS / "android/app/src/main/java/dev/shivampingale/vcport/BiometricVault.kt"
        store = PORTS / "ios/VCPort/BiometricStore.swift"
        self.assertFalse(vault.exists())
        self.assertFalse(store.exists())
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        gradle = read("ports/android/app/build.gradle")
        manifest = read("ports/android/app/src/main/AndroidManifest.xml")
        plist = read("ports/ios/VCPort/Info.plist")
        self.assertNotIn("androidx.biometric", gradle)
        self.assertNotIn("USE_BIOMETRIC", manifest)
        self.assertNotIn("USE_FINGERPRINT", manifest)
        self.assertNotIn("NSFaceIDUsageDescription", plist)
        self.assertNotIn("BiometricPrompt", main)
        self.assertNotIn("BiometricStore", view)
        self.assertNotIn("Type REMEMBER", main)
        self.assertNotIn("Type REMEMBER", view)

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
        threat = read("ports/THREAT-MODEL.md")
        self.assertIn("no key escrow", threat.lower())
        self.assertIn("They still win", threat)

    def test_about_has_cypherpunk_quote(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        for blob in (main, view):
            self.assertIn("We must defend our own privacy if we expect to have any", blob)
            self.assertIn("Cypherpunks write code", blob)
            self.assertIn("Eric Hughes", blob)
            self.assertIn("https://github.com/ShivamPingaleDev/Veracrypt_port", blob)
            self.assertIn("shivampingaledev@proton.me", blob)
            self.assertNotIn("programming noob", blob.lower())
            self.assertNotIn("internship", blob.lower())

    def test_never_save_history_is_default(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        theme = read("ports/android/app/src/main/java/dev/shivampingale/vcport/VcPortTheme.kt")
        hardening = read("ports/android/app/src/main/java/dev/shivampingale/vcport/Hardening.kt")
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
        self.assertIn("copyOwned", read("ports/android/app/src/main/java/dev/shivampingale/vcport/UnlockFactors.kt"))
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
        self.assertIn("Panic", view)
        threat = read("ports/THREAT-MODEL.md")
        self.assertIn("no key escrow", threat.lower())
        self.assertIn("They still win", threat)

    def test_never_save_history_is_default(self) -> None:
        view = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("neverSaveHistory()", view)
        self.assertGreaterEqual(view.count("SecureField("), 5)
        self.assertGreaterEqual(view.count(".neverSaveHistory()"), 5)
        self.assertIn("textContentType(.oneTimeCode)", view)


class MobileSrcOverlayTests(unittest.TestCase):
    def test_phone_src_hunks_stay(self) -> None:
        if not FULL_TREE:
            self.skipTest("src/ lives in Veracrypt_port")
        official = read("src/Platform/Unix/File.cpp")
        overlay = read("ports/overlay/src/Platform/Unix/File.cpp")
        self.assertNotIn("TC_IOS", official)
        self.assertIn("TC_IOS", overlay)
        keyfile = read("src/Volume/Keyfile.cpp")
        self.assertIn("Common/SecurityToken.h", keyfile)
        self.assertNotIn("TC_ANDROID", keyfile)
        self.assertIn("TC_PORT_NO_TOKEN", read("ports/overlay/src/Common/SecurityToken.h"))
        self.assertIn("TC_PORT_NO_TOKEN", read("ports/overlay/src/Common/EMVToken.h"))

    def test_no_desktop_fork_extras(self) -> None:
        if not FULL_TREE:
            self.skipTest("src/ lives in Veracrypt_port")
        self.assertFalse(resolve("archive/desktop").exists())
        self.assertFalse(resolve("src/Main/OfflineUpdate.cpp").exists())
        self.assertFalse(resolve("src/Main/PortFileWrap.cpp").exists())
        self.assertFalse(resolve("src/Main/MacOSXBiometric.h").exists())
        self.assertNotIn("PortFileWrap", read("src/Main/Forms/MainFrame.cpp"))
        self.assertNotIn("StayOffline", read("src/Main/UserPreferences.h"))


class CrossPortGuiParityTests(unittest.TestCase):
    """Same discussed features must appear in every device GUI."""

    def test_wrap_panic_share_stay_offline_on_android(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        self.assertIn("Panic wipe", main)
        self.assertNotIn("Decrypt wrap", main)
        self.assertNotIn("Leftover wrap", main)
        self.assertIn("Share encrypted", main)
        self.assertIn("Stay offline", main)
        self.assertIn("compelled", main.lower())
        self.assertIn("Copy once", main)
        self.assertIn("generatePassword(64)", main)
        self.assertIn("64-character password", main)
        lock = main.split("private fun lockSession()")[1].split("private fun panicWipe()")[0]
        self.assertNotIn("SensitiveClipboard.forget", lock)
        onstop = main.split("override fun onStop()")[1].split("private fun closeMountedVolume()")[0]
        self.assertIn("dismountOnLeave()", onstop)
        self.assertNotIn("lockSession()", onstop)
        self.assertIn("Create form kept", main)
        self.assertIn('testTag("copy_once")', main)
        self.assertIn('testTag("create_password")', main)

    def test_wrap_panic_share_stay_offline_on_ios(self) -> None:
        view = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("Panic wipe", view)
        self.assertNotIn("Decrypt wrap", view)
        self.assertNotIn("Leftover wrap", view)
        self.assertIn("Share encrypted file", view)
        self.assertIn("Stay offline", view)
        self.assertIn("compelled", view.lower())
        self.assertIn("Copy once", view)
        self.assertIn("64-character password", view)
        lock = view.split("private func lockSession()")[1].split("private func panicWipe()")[0]
        self.assertNotIn("SensitivePaste.forget()", lock)
        self.assertIn("dismountOnLeave()", view)
        self.assertIn("Create form kept", view)
        self.assertIn("generatePassword(length: Int32 = 64)", read("ports/ios/VCPort/VcMobileBridge.swift"))
        self.assertIn("VC_ENTROPY_NEED = 8192", read("ports/shared/vc_mobile.cpp"))
        self.assertIn('portTag("copy_once")', view)
        self.assertIn('portTag("create_password")', view)

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
        self.assertIn("nextPim", main)
        self.assertIn("nextPim", view)
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
            self.assertIn("Copy to volume", blob)
            self.assertIn("Move to volume", blob)
            self.assertIn("Could not delete the original", blob)
        self.assertIn("OpenMultipleDocuments", main)
        self.assertIn("OpenDocumentTree", main)
        self.assertIn("exportManyToDevice", main)
        self.assertIn("copyUriForNativeImport", main)
        self.assertIn("allowsMultipleSelection: true", view.split("isPresented: $copyFromDevicePresented")[1].split(".alert")[0])
        self.assertIn("exportCopy(urls:", view)
        self.assertIn("Tap one or more files in the volume, then Copy to device.", main)
        self.assertIn("Tap one or more files in the volume, then Copy to device.", view)
        imp = main.split("private fun importFromDevice")[1].split("private fun exportToDevice")[0]
        basket = main.split("private fun importUriIntoVolume")[1].split("private fun copyStreamProgress")[0]
        self.assertNotIn("/proc/self/fd", imp)
        self.assertNotIn("/proc/self/fd", basket)
        self.assertIn("importFile", native)
        self.assertIn("deleteFile", native)
        self.assertIn("vc_import_file", header)
        self.assertIn("vc_delete_file", header)

    def test_create_basket_on_android_and_ios(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        for blob in (main, view):
            self.assertIn("Add files to basket", blob)
            self.assertIn("Empty basket", blob)
            self.assertIn("from the basket into the volume", blob)
            self.assertIn("BASKET.sha256", blob)
            self.assertIn("Inside the volume", blob)
            self.assertIn("exFAT", blob)
            self.assertIn("Volume password", blob)
            self.assertIn("will not ask for superuser", blob)

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
            self.assertIn("Not on this phone", blob)
            self.assertNotIn("Desktop leftovers", blob)
        self.assertIn("mkdir", native)
        self.assertIn("wipeFreeSpace", native)
        self.assertIn("vc_mkdir", header)
        self.assertIn("vc_rmdir", header)
        self.assertIn("vc_rename", header)
        self.assertIn("vc_wipe_free_space", header)
        self.assertIn("read_only", header)
        self.assertIn("protect_hidden", header)
        self.assertIn("vc_protection_triggered", header)
        wipe_android = main[main.find("fun wipeFreeSpace") : main.find("fun restoreEmbeddedHeader")]
        self.assertIn("protectionTriggered", wipe_android)
        wipe_ios = view[view.find("func wipeFreeSpace") : view.find("func formatFatStamp")]
        self.assertIn("protectionTriggered", wipe_ios)

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
        for blob in (main, helper, view):
            self.assertIn("photo.jpg", blob)
            self.assertIn("model.safetensors", blob)
            self.assertIn("adapter.lora", blob)
        self.assertIn("Opening ignores the extension", main)
        self.assertIn("Opening ignores the extension", view)
        self.assertIn("File name (any extension)", main)
        self.assertIn("The name is only a disguise", main)
        self.assertIn("The name is only a disguise", view)
        self.assertIn("pimState", main)
        self.assertIn('pimState.value = "0"', main)
        self.assertIn("sanitizeDisguiseName", helper)

    def test_cmake_cpu_slices_are_explicit(self) -> None:
        cmake = read("ports/shared/upstream-sources.cmake")
        self.assertIn("ANDROID_ABI", cmake)
        self.assertIn("opt_avx2.c", cmake)
        self.assertIn("opt_sse2.c", cmake)
        self.assertIn("Aes_hw_armv8.c", cmake)
        self.assertIn("-O3 -march=armv8-a+crypto", cmake)
        self.assertIn("-mbranch-protection=standard", cmake)
        lists = read("ports/shared/CMakeLists.txt")
        self.assertIn("CRYPTOPP_DISABLE_AESNI", lists)
        self.assertIn("CRYPTOPP_DISABLE_SHANI", lists)
        self.assertIn("TC_IOS", lists)
        self.assertIn('CMAKE_SYSTEM_NAME STREQUAL "iOS"', lists)
        self.assertIn("vc_progress.cpp", lists)
        self.assertIn("vc_exfat.cpp", lists)
        self.assertIn("vc_crypto_safety_test", lists)
        self.assertIn("VC_ENABLE_ASAN", lists)
        self.assertIn("armv8-a+crypto", lists)
        self.assertIn("mfpu=neon", lists)
        self.assertIn("-ftree-vectorize", lists)
        self.assertIn("DetectArmFeatures", read("ports/shared/vc_mobile.cpp"))
        self.assertIn("Aescrypt.c", lists)
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
        self.assertIn("overlay.cmake", lists)
        self.assertIn("Common/Token.cpp", cmake)
        self.assertNotIn("token_stubs.cpp", lists)
        keyfile = read("src/Volume/Keyfile.cpp")
        self.assertNotIn("TC_PORT_NO_TOKEN", keyfile)
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
        replace = set(self._list("ports/overlay/replace.txt"))
        self.assertTrue(owned)
        self.assertFalse(patched)
        self.assertIn("Platform/Unix/File.cpp", replace)
        self.assertIn("Common/Token.cpp", replace)
        self.assertIn("Common/SecurityToken.h", replace)
        self.assertIn("Common/EMVToken.h", replace)
        self.assertFalse(owned & patched)

    def test_replace_files_exist(self) -> None:
        if not FULL_TREE:
            self.skipTest("overlay src/ lives in Veracrypt_port")
        for rel in self._list("ports/overlay/replace.txt"):
            self.assertTrue((ROOT / "ports/overlay/src" / rel).is_file(), rel)
            self.assertTrue((ROOT / "src" / rel).is_file(), rel)

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
