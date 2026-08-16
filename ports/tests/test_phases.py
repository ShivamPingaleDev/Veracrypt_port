#!/usr/bin/env python3
"""Contract tests for the 10-phase pre-public cycle.

Each class is one phase. Host wrap/volume/overlay live in run-phases.sh so this
file stays device-free.
"""

from __future__ import annotations

import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def load_version() -> dict:
    return json.loads(read("ports/version.json"))


class Phase1HonestyFreezeTests(unittest.TestCase):
    def test_security_md_exists_with_contacts(self) -> None:
        sec = read("SECURITY.md")
        self.assertIn("shivampingaledev@proton.me", sec)
        self.assertIn("shivampingaledev@gmail.com", sec)
        self.assertIn("TrueCrypt License 3.0", sec)
        self.assertIn("not unbreakable", sec.lower())
        self.assertIn("Do not make the tree private again", sec)
        self.assertIn("debug-signed previews", sec)

    def test_no_github_release_apk_job(self) -> None:
        wf = read(".github/workflows/vcport.yml")
        self.assertNotIn("release-apks:", wf)
        self.assertNotIn("softprops/action-gh-release", wf)
        self.assertIn("No GitHub Release APK attach", wf)

    def test_docs_do_not_claim_documents_provider(self) -> None:
        self.assertNotIn("DocumentsProvider stub", read("ports/android/README.md"))
        self.assertIn("no DocumentsProvider", read("ports/android/README.md"))
        contrib = read("ports/CONTRIBUTING.md")
        self.assertIn("5. Report security issues", contrib)
        self.assertIn("SECURITY.md", contrib)


class Phase3FatFolderTests(unittest.TestCase):
    def test_native_list_dir_and_path_safety(self) -> None:
        header = read("ports/shared/vc_mobile.h")
        mobile = read("ports/shared/vc_mobile.cpp")
        self.assertIn("vc_list_dir", header)
        self.assertIn("fat_find_path", mobile)
        self.assertIn('EXFAT   "', mobile)
        self.assertIn("VC_ERR_UNSUPPORTED", header)
        self.assertIn("VC_LIST_UI_MAX", header)
        self.assertIn("vc_list_dir_from", header)
        self.assertNotIn("VcDirEntry entries[128]", mobile)
        self.assertIn("32768", mobile)
        self.assertIn('strcmp (out, "..")', mobile)

    def test_volume_fixture_covers_folders(self) -> None:
        test = read("ports/shared/test_volume_main.cpp")
        self.assertIn("vc_list_dir", test)
        self.assertIn('reject ..', test)
        self.assertIn("exFAT unsupported", test)
        self.assertIn("DOCS", test)
        self.assertIn("vc_list_dir_from", test)
        self.assertIn("negative skip", test)
        self.assertIn("change volume password", test)
        self.assertIn("backup volume header", test)
        self.assertIn("restore volume header", test)
        self.assertIn("keyfile generator", test)
        self.assertIn("import FROMDEV.TXT", test)
        self.assertIn("delete FROMDEV.TXT", test)
        self.assertIn("mkdir INBOX", test)
        self.assertIn("wipe free space", test)

    def test_android_and_ios_browse_folders(self) -> None:
        android = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        ios = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("listDir", android)
        self.assertIn("Tap a folder", android)
        self.assertIn("!truncated!", android)
        self.assertIn("Load more", android)
        self.assertIn("listDir", ios)
        self.assertIn("!truncated!", ios)
        self.assertIn("Load more", ios)
        self.assertIn("exFAT is not", ios)
        self.assertNotIn("VolumeDocumentsProvider", read("ports/android/app/src/main/AndroidManifest.xml"))
        jni = read("ports/shared/android_jni.cpp")
        self.assertIn("VC_LIST_UI_MAX", jni)
        self.assertIn("vc_list_dir_from", jni)
        self.assertNotIn("entries[128]", jni)


class Phase4AndroidTests(unittest.TestCase):
    def test_lint_fail_closed_and_no_minify(self) -> None:
        gradle = read("ports/android/app/build.gradle")
        self.assertIn("abortOnError true", gradle)
        self.assertIn("minifyEnabled false", gradle)
        self.assertIn("VC_PORT_RELEASE_STORE_FILE", gradle)
        self.assertNotIn("play-services", gradle)

    def test_open_list_extract_errors_are_explicit(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        self.assertIn("FAT only", main)
        self.assertIn("Wrong password", main)
        self.assertIn("Could not extract", main)
        self.assertIn("NativeBridge.listDir", main)
        self.assertIn("Not enough memory to open the volume.", main)
        self.assertIn("Missing path or password argument.", main)
        self.assertIn("formatUpdateStatus", main)
        self.assertIn("SHA-256", main)
        self.assertIn("debug-signed previews", main)


class Phase5IosTests(unittest.TestCase):
    def test_native_build_writes_per_slice_archives(self) -> None:
        script = read("ports/ios/build-native.sh")
        self.assertIn("--all", script)
        self.assertIn("${SDK}-${ARCH}", script)
        self.assertIn("iphoneos", script)
        self.assertIn("iphonesimulator", script)
        yml = read("ports/ios/project.yml")
        self.assertIn("IOS_SDK", yml)

    def test_altstore_draft_has_empty_download(self) -> None:
        src = json.loads(read("ports/ios/altstore/source.json"))
        self.assertEqual(src["name"], "VC Port")
        app = src["apps"][0]
        self.assertEqual(app["bundleIdentifier"], "dev.shivampingale.vcport")
        self.assertEqual(app["developerName"], "Shivam Mangesh Pingale")
        ver = app["versions"][0]
        self.assertEqual(ver["version"], load_version()["port_version"])
        self.assertEqual(ver["downloadURL"], "")
        self.assertEqual(ver["size"], 0)

    def test_readme_does_not_claim_file_provider(self) -> None:
        readme = read("ports/ios/README.md")
        self.assertIn("There is **no** File Provider extension", readme)
        self.assertNotIn("The File Provider extension should", readme)
        view = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("Not enough memory to open the volume.", view)
        self.assertIn("Missing path or password argument.", view)
        self.assertIn("formatUpdateStatus", view)


class Phase6DesktopTests(unittest.TestCase):
    def test_stay_offline_gates_help_updates(self) -> None:
        frame = read("src/Main/Forms/MainFrame.cpp")
        self.assertIn("GetPreferences().StayOffline", frame)
        self.assertIn("OnCheckForUpdatesMenuItemSelected", frame)
        prefs = read("src/Main/UserPreferences.h")
        self.assertIn("StayOffline (true)", prefs)

    def test_panic_reports_dismount_failure(self) -> None:
        frame = read("src/Main/Forms/MainFrame.cpp")
        self.assertIn("PANIC_WIPE_DISMOUNT_FAILED", frame)
        lang = read("src/Common/Language.xml")
        self.assertIn('key="PANIC_WIPE_DISMOUNT_FAILED"', lang)

    def test_offline_update_user_agent_uses_port_version(self) -> None:
        update = read("src/Main/OfflineUpdate.cpp")
        self.assertIn("VCPort-OfflineUpdate/", update)
        self.assertIn("VC_PORT_VERSION", update)
        self.assertNotIn("VCPort-OfflineUpdate/0.1", update)
        self.assertIn("--max-redirs", update)
        self.assertIn("--max-filesize", update)
        self.assertIn("UrlAllowed", update)
        self.assertNotIn("-fsSL", update)


class Phase7ManifestTests(unittest.TestCase):
    def test_version_json_has_sha256_keys(self) -> None:
        v = load_version()
        self.assertIn("android_apk_sha256", v)
        self.assertIn("source_sha256", v)
        self.assertEqual(v["android_apk_sha256"], "")
        self.assertEqual(v["source_sha256"], "")
        for key in ("download_url", "android_url"):
            url = v[key]
            if url:
                self.assertTrue(url.startswith("https://"), key)

    def test_github_checker_rejects_bad_hex_and_http(self) -> None:
        github = read("ports/android/app/src/github/java/dev/shivampingale/vcport/UpdateChecker.kt")
        self.assertIn("SHA256", github)
        self.assertIn("bad manifest", github)
        self.assertIn('startsWith("https://")', github)
        ios = read("ports/ios/VCPort/UpdateChecker.swift")
        self.assertIn("android_apk_sha256", ios)
        desktop = read("src/Main/OfflineUpdate.cpp")
        self.assertIn("android_apk_sha256", desktop)
        self.assertIn("tag_name", desktop)
        self.assertIn("VersionFromVeraCryptTag", desktop)


class Phase8CiTests(unittest.TestCase):
    def test_ci_runs_host_tests_on_linux_and_macos(self) -> None:
        wf = read(".github/workflows/vcport.yml")
        self.assertIn("wrap-test:", wf)
        self.assertIn("host-macos:", wf)
        self.assertIn("macos-latest", wf)
        self.assertIn("ports/tests/run-all.sh", wf)
        self.assertIn("apt-get install -y g++ python3 cmake", wf)
        self.assertIn("assembleFdroidRelease", wf)
        self.assertIn("assembleGithubRelease", wf)
        self.assertIn("ios-native:", wf)
        self.assertIn("iphonesimulator", wf)
        self.assertNotIn("release-apks:", wf)
        self.assertIn("src/Main/**", wf)
        self.assertIn("src/Driver/**", wf)
        self.assertIn("SECURITY.md", wf)

    def test_ci_watches_official_veracrypt_releases(self) -> None:
        wf = read(".github/workflows/upstream-overlay.yml")
        self.assertIn("check_veracrypt_release.py", wf)
        self.assertIn("veracrypt/VeraCrypt.git", wf)

    def test_ci_does_not_attach_debug_apks_to_releases(self) -> None:
        wf = read(".github/workflows/vcport.yml")
        self.assertIn("upload-artifact@v4", wf)
        self.assertNotIn("action-gh-release", wf)


class Phase9LegalVersionTests(unittest.TestCase):
    def test_current_version_is_0_3_0(self) -> None:
        v = load_version()
        self.assertEqual(v["port_version"], "0.3.0")
        self.assertEqual(v["upstream_version"], "1.26.29")
        self.assertEqual(v["upstream_commit"], "b48e31f5b47da7d41025e3f0e02751675e15005a")
        self.assertEqual(v["upstream_git"], "https://github.com/veracrypt/VeraCrypt.git")
        self.assertEqual(v["upstream_tag"], "VeraCrypt_1.26.29")
        h = read("src/Main/PortVersion.h")
        self.assertIn('#define VC_PORT_VERSION\t\t\t"0.3.0"', h)
        gradle = read("ports/android/app/build.gradle")
        self.assertIn("versionJson.port_version", gradle)
        self.assertIn("android_version_code", gradle)
        self.assertEqual(v["android_version_code"], 5)
        notes = ROOT / "ports/android/fastlane/metadata/android/en-US/changelogs/5.txt"
        self.assertTrue(notes.is_file(), "missing Fastlane changelog for versionCode 5")
        self.assertIn("FAT folder", notes.read_text(encoding="utf-8"))
        self.assertIn("not unbreakable", notes.read_text(encoding="utf-8").lower())

    def test_about_and_contact_on_every_surface(self) -> None:
        android = read("ports/android/app/src/main/res/values/strings.xml")
        ios = read("ports/ios/VCPort/ContentView.swift")
        for blob in (android, ios):
            self.assertIn("shivampingaledev@proton.me", blob)
            self.assertIn("shivampingaledev@gmail.com", blob)
        self.assertIn("Shivam Mangesh Pingale", android)

    def test_no_fake_fdroid_screenshots(self) -> None:
        shots = ROOT / "ports/android/fastlane/metadata/android/en-US/images/phoneScreenshots"
        if shots.is_dir():
            files = [p for p in shots.iterdir() if p.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp"}]
            self.assertEqual(files, [])


class Phase10RelaunchTests(unittest.TestCase):
    def test_foss_says_public(self) -> None:
        foss = read("ports/FOSS.md")
        self.assertIn("https://github.com/ShivamPingaleDev/Veracrypt_port", foss)
        self.assertIn("v0.3.0", foss)
        self.assertIn("may still be private", foss)
        self.assertIn("subdir: ports/android", foss)
        self.assertNotIn("this repository is currently private", foss.lower())

    def test_hash_release_refuses_debug_apk_name(self) -> None:
        import subprocess
        import tempfile
        from pathlib import Path

        script = ROOT / "ports/scripts/hash_release.py"
        self.assertTrue(script.is_file())
        self.assertIn("Android Debug", script.read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as tmp:
            fake = Path(tmp) / "app-debug.apk"
            fake.write_bytes(b"not-an-apk")
            proc = subprocess.run(
                ["python3", str(script), str(fake)],
                capture_output=True,
                text=True,
            )
        self.assertNotEqual(proc.returncode, 0)
        self.assertIn("debug", (proc.stderr + proc.stdout).lower())

    def test_hash_source_and_refuse_write_on_dirty_tree(self) -> None:
        import subprocess

        script = str(ROOT / "ports/scripts/hash_release.py")
        src = subprocess.run(
            ["python3", script, "--source"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(src.returncode, 0, src.stderr)
        self.assertRegex(src.stdout.strip(), r"^[0-9a-f]{64}$")
        write = subprocess.run(
            ["python3", script, "--source", "--write"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertNotEqual(write.returncode, 0)
        self.assertIn("dirty", (write.stderr + write.stdout).lower())

    def test_changelog_covers_hardening_cycle(self) -> None:
        log = read("ports/CHANGELOG.md")
        self.assertIn("## 0.3.0", log)
        self.assertIn("public relaunch", log.lower())
        self.assertIn("debug-signed previews", log)

    def test_tag_matches_port_version(self) -> None:
        import subprocess

        ver = load_version()["port_version"]
        tag = f"v{ver}"
        tags = subprocess.check_output(
            ["git", "tag", "-l", tag],
            cwd=ROOT,
            text=True,
        ).strip()
        self.assertEqual(tags, tag)


if __name__ == "__main__":
    unittest.main(verbosity=2)
