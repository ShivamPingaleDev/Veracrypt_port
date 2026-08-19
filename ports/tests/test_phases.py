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

import sys

sys.path.insert(0, str(Path(__file__).resolve().parent))
from repo_paths import FULL_TREE, ROOT, read, resolve  # noqa: E402


def load_version() -> dict:
    return json.loads(read("ports/version.json"))


class Phase1HonestyFreezeTests(unittest.TestCase):
    def test_security_md_exists_with_contacts(self) -> None:
        sec = read("SECURITY.md")
        self.assertIn("shivampingaledev@proton.me", sec)
        self.assertIn("shivampingaledev@gmail.com", sec)
        self.assertIn("TrueCrypt License 3.0", sec)
        self.assertIn("not unbreakable", sec.lower())
        self.assertIn("no key escrow", sec.lower())
        self.assertIn("Do not make the tree private again", sec)
        self.assertIn("debug-signed previews", sec)
        cite = read("CITATION.cff")
        self.assertIn("email: shivampingaledev@proton.me", cite)
        self.assertIn("email: shivampingaledev@gmail.com", cite)
        foss = read("ports/FOSS.md")
        self.assertIn("Shivam Mangesh Pingale", foss)
        self.assertIn("shivampingaledev@proton.me", foss)
        self.assertIn("shivampingaledev@gmail.com", foss)

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
        self.assertIn("create exFAT volume", test)
        self.assertIn("invalid exFAT boot is not listed as FAT", test)
        self.assertIn("DOCS", test)
        self.assertIn("vc_list_dir_from", test)
        self.assertIn("negative skip", test)
        self.assertIn("change volume password", test)
        self.assertIn("backup volume header", test)
        self.assertIn("restore volume header", test)
        self.assertIn("restore from embedded backup header", test)
        self.assertIn("open with backup header after corruption", test)
        self.assertIn("set header key derivation algorithm", test)
        self.assertIn("remove all keyfiles from volume", test)
        self.assertIn("keyfile generator", test)
        self.assertIn("import FROMDEV.TXT", test)
        self.assertIn("delete FROMDEV.TXT", test)
        self.assertIn("mkdir INBOX", test)
        self.assertIn("wipe free space", test)

    def test_lifecycle_simulation_covers_roundtrip(self) -> None:
        test = read("ports/shared/test_lifecycle_main.cpp")
        self.assertIn("create AES(Twofish(Serpent))/HMAC-SHA-512", test)
        self.assertIn("create biometric password", test)
        self.assertIn("store VCF2 remember bundle", test)
        self.assertIn("load VCF2 remember bundle", test)
        self.assertIn("open with password PIM and biometric", test)
        self.assertIn("mkdir VAULT", test)
        self.assertIn("import NOTE.TXT", test)
        self.assertIn("close volume", test)
        self.assertIn("reopen with stored factors", test)
        self.assertIn("payload still matches", test)
        self.assertIn("wrong password is rejected", test)
        self.assertIn("wrong PIM is rejected", test)
        self.assertIn("missing biometric is rejected", test)
        self.assertIn("password-only PIM", test)
        self.assertIn("bio-only PIM", test)
        self.assertIn("generated 64", test)
        self.assertIn("PIM 0", test)
        self.assertIn("PIM 1", test)
        self.assertIn("PIM 5", test)
        self.assertIn("PIM 12", test)
        self.assertIn("98", test)
        self.assertIn("485", test)
        self.assertIn("phone session", test)
        self.assertIn("wrapFile encrypt", test)
        self.assertIn("unwrapFile decrypt", test)
        self.assertIn("changeHeader", test)

    def test_android_and_ios_browse_folders(self) -> None:
        android = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        ios = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("listDir", android)
        self.assertIn("Tap a folder", android)
        self.assertIn("Mounted in this app", android)
        self.assertIn("Mounted in this app", ios)
        self.assertIn("Dismount", android)
        self.assertIn("Dismount", ios)
        self.assertIn("lockSession()", android)
        self.assertIn('Button("Dismount") { lockSession() }', ios)
        self.assertNotIn('Button("Dismount") { closeVolume() }', ios)
        self.assertIn("wipeSessionFiles()", ios)
        self.assertIn("createPasswordState.value = \"\"", android)
        self.assertNotIn("BiometricStore.deleteAll()", ios)
        self.assertIn("!truncated!", android)
        self.assertIn("Load more", android)
        self.assertIn("listDir", ios)
        self.assertIn("!truncated!", ios)
        self.assertIn("Load more", ios)
        self.assertIn("FAT and exFAT folders are browsable", ios)
        self.assertIn("Open another container", android)
        self.assertIn("Open another container", ios)
        self.assertIn("Copy to volume", android)
        self.assertIn("Copy to volume", ios)
        self.assertIn("Move to volume", android)
        self.assertIn("Move to volume", ios)
        self.assertIn("This session already has 8 volumes mounted", android)
        self.assertIn("This session already has 8 volumes mounted", ios)
        self.assertIn("already mounted", android)
        self.assertIn("already mounted", ios)
        self.assertIn("tab_mounted", android)
        self.assertIn("MOUNT_SLOTS = 8", android)
        self.assertIn("mountSlots = 8", ios)
        self.assertIn("Label(\"Mounted\"", ios)
        self.assertIn("Select files", android)
        self.assertIn("Select files", ios)
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
        self.assertIn("FAT and exFAT", main)
        self.assertIn("Wrong password", main)
        self.assertIn("Could not extract", main)
        self.assertIn("NativeBridge.listDir", main)
        self.assertIn("tab_volume", main)
        self.assertIn("testTag", main)
        self.assertIn("NativeBridge.mkdir", main)
        mkdir = main[main.find("fun mkdirInVolume") : main.find("fun renameVaultEntry")]
        self.assertIn("Thread", mkdir)
        self.assertIn("NativeBridge.mkdir", mkdir)
        self.assertIn("Not enough memory to open the volume.", main)
        self.assertIn("Missing path or password argument.", main)
        self.assertIn("does not install itself", main)
        self.assertIn("sync-upstream.sh", read("ports/UPSTREAM.md"))


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
        self.assertIn("does not install itself", view)
        self.assertIn("sync-upstream.sh", read("ports/UPSTREAM.md"))

    def test_ipad_simulator_and_sideload_sign(self) -> None:
        sim = read("ports/ios/run_ipad_sim.sh")
        sign = read("ports/ios/sideload-sign.sh")
        yml = read("ports/ios/project.yml")
        self.assertIn("iPad", sim)
        self.assertIn("CODE_SIGNING_ALLOWED=NO", sim)
        self.assertIn("dev.shivampingale.vcport", sim)
        self.assertIn("Apple Development", sign)
        self.assertIn("DEVELOPMENT_TEAM", sign)
        self.assertIn("generic/platform=iOS", sign)
        self.assertIn("Signing.local.xcconfig", sign)
        self.assertIn("TARGETED_DEVICE_FAMILY: \"1,2\"", yml)
        self.assertIn("run_ipad_sim.sh", read("ports/tests/run-phases.sh"))
        self.assertIn("run_ios_session_test.sh", read("ports/tests/run-phases.sh"))
        self.assertIn("VCPortTests", yml)
        self.assertIn("AppInterfaceSessionTests", read("ports/ios/run_ios_session_test.sh"))
        self.assertIn("generic/platform=iOS", read("ports/ios/build-unsigned-ipa.sh"))
        phones = read("ports/scripts/build-phones.sh")
        self.assertIn("assembleFossRelease", phones)
        self.assertIn("build-unsigned-ipa.sh", phones)
        self.assertIn("APID", phones)
        self.assertIn("IPID", phones)
        self.assertIn("VC_PORT_IOS_TEAM", phones)


class Phase6ArchiveTests(unittest.TestCase):
    def test_no_desktop_fork_extras(self) -> None:
        if not FULL_TREE:
            self.skipTest("src/ lives in Veracrypt_port")
        self.assertFalse(resolve("archive/desktop").exists())
        self.assertFalse(resolve("src/Main/OfflineUpdate.cpp").exists())
        self.assertFalse(resolve("src/Main/PortFileWrap.cpp").exists())
        self.assertNotIn("PortFileWrap", read("src/Main/Forms/MainFrame.cpp"))


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
        checker = read("ports/android/app/src/main/java/dev/shivampingale/vcport/UpdateChecker.kt")
        self.assertIn("has no network", checker)
        self.assertNotIn("HttpURLConnection", checker)
        ios = read("ports/ios/VCPort/UpdateChecker.swift")
        self.assertIn("has no network", ios)
        self.assertNotIn("URLSession", ios)
        self.assertIn("android_apk_sha256", read("ports/version.json"))
        self.assertIn("tag_name", read("ports/scripts/check_veracrypt_release.py"))


class Phase8CiTests(unittest.TestCase):
    def test_ci_runs_host_tests_on_linux_and_macos(self) -> None:
        wf = read(".github/workflows/vcport.yml")
        self.assertIn("wrap-test:", wf)
        self.assertIn("host-macos:", wf)
        self.assertIn("macos-latest", wf)
        self.assertIn("ports/tests/run-all.sh", wf)
        self.assertIn("apt-get install -y g++ python3 cmake", wf)
        self.assertIn("assembleFossRelease", wf)
        self.assertIn("assembleGithubRelease", wf)
        self.assertNotIn("assembleStyledRelease", wf)
        self.assertNotIn("assembleLooksgithubRelease", wf)
        self.assertNotIn("vcport-looks-apk", wf)
        self.assertIn("ios:", wf)
        self.assertIn("build-unsigned-ipa.sh", wf)
        self.assertIn("iphoneos", read("ports/ios/build-unsigned-ipa.sh"))
        self.assertNotIn("ios-native:", wf)
        self.assertNotIn("needs: android", wf)
        self.assertNotIn("needs: ios", wf)
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
    def test_current_version_is_0_3_7(self) -> None:
        v = load_version()
        self.assertEqual(v["port_version"], "0.3.7")
        self.assertEqual(v["upstream_version"], "1.26.29")
        self.assertEqual(v["upstream_commit"], "b48e31f5b47da7d41025e3f0e02751675e15005a")
        self.assertEqual(v["upstream_git"], "https://github.com/veracrypt/VeraCrypt.git")
        self.assertEqual(v["upstream_tag"], "VeraCrypt_1.26.29")
        plist = read("ports/ios/VCPort/Info.plist")
        self.assertIn("0.3.7", plist)
        gradle = read("ports/android/app/build.gradle")
        self.assertIn("versionJson.port_version", gradle)
        self.assertIn("android_version_code", gradle)
        self.assertEqual(v["android_version_code"], 12)
        notes = resolve("ports/android/fastlane/metadata/android/en-US/changelogs/12.txt")
        self.assertTrue(notes.is_file(), "missing Fastlane changelog for versionCode 12")
        self.assertIn("session tests", notes.read_text(encoding="utf-8").lower())
        self.assertIn("not unbreakable", notes.read_text(encoding="utf-8").lower())
        self.assertIn("stable alpha", notes.read_text(encoding="utf-8").lower())
        self.assertIn("stable alpha", read("ports/CHANGELOG.md").lower())
        self.assertIn("stable alpha", v["notes"].lower())

    def test_about_and_contact_on_every_surface(self) -> None:
        android = read("ports/android/app/src/main/res/values/strings.xml")
        ios = read("ports/ios/VCPort/ContentView.swift")
        for blob in (android, ios):
            self.assertIn("shivampingaledev@proton.me", blob)
            self.assertIn("shivampingaledev@gmail.com", blob)
        self.assertIn("Shivam Mangesh Pingale", android)
        self.assertIn("https://github.com/ShivamPingaleDev/Veracrypt_port", android)
        footnote = "programming noob still doing a five-year IT engineering degree"
        blobs = [
            read("ports/README.md"),
            read("ports/NOTICE"),
            read("ports/CONTRIBUTING.md"),
            read("ports/FOSS.md"),
            read("ports/android/fastlane/metadata/android/en-US/full_description.txt"),
            read("ports/PUBLIC.md"),
        ]
        if FULL_TREE:
            blobs.extend(
                [
                    read("README.md"),
                    read("SECURITY.md"),
                    read("PORTING.md"),
                    read("NOTICE"),
                ]
            )
        for blob in blobs:
            self.assertIn(footnote, blob)
            self.assertIn("Open to suggestions and advice", blob)

    def test_no_fake_store_screenshots(self) -> None:
        shots = resolve("ports/android/fastlane/metadata/android/en-US/images/phoneScreenshots")
        if shots.is_dir():
            files = [p for p in shots.iterdir() if p.suffix.lower() in {".png", ".jpg", ".jpeg", ".webp"}]
            self.assertEqual(files, [])

    def test_github_emulator_screenshots_are_png(self) -> None:
        shots = resolve("ports/docs/screenshots")
        self.assertTrue(shots.is_dir(), "missing ports/docs/screenshots")
        names = [
            "01-volume.png",
            "02-wrap.png",
            "03-create.png",
            "04-tools.png",
            "05-mounted.png",
            "08-skin-signal.png",
        ]
        for name in names:
            path = shots / name
            self.assertTrue(path.is_file(), f"missing {path}")
            data = path.read_bytes()
            self.assertTrue(data.startswith(b"\x89PNG\r\n\x1a\n"), f"{name} is not a PNG")
            self.assertGreater(len(data), 20_000, f"{name} looks empty/fake ({len(data)} bytes)")
        note = (shots / "README.md").read_text(encoding="utf-8")
        self.assertIn("FLAG_SECURE", note)
        self.assertIn("emulator", note.lower())
        self.assertIn("not unbreakable", note.lower())


class Phase10RelaunchTests(unittest.TestCase):
    def test_foss_says_public(self) -> None:
        foss = read("ports/FOSS.md")
        self.assertIn("https://github.com/ShivamPingaleDev/Veracrypt_port", foss)
        self.assertIn("https://github.com/ShivamPingaleDev/VCPort", foss)
        self.assertIn("assembleFossRelease", foss)
        self.assertIn("build-phones.sh", foss)
        self.assertIn("VC_PORT_IOS_TEAM", foss)
        self.assertNotIn("fdroiddata", foss)
        self.assertNotIn("may still be private", foss)
        self.assertNotIn("this repository is currently private", foss.lower())

    def test_hash_release_refuses_debug_apk_name(self) -> None:
        import subprocess
        import tempfile
        from pathlib import Path

        script = resolve("ports/scripts/hash_release.py")
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
        from pathlib import Path

        script = str(resolve("ports/scripts/hash_release.py"))
        src = subprocess.run(
            ["python3", script, "--source"],
            cwd=ROOT,
            capture_output=True,
            text=True,
        )
        self.assertEqual(src.returncode, 0, src.stderr)
        self.assertRegex(src.stdout.strip(), r"^[0-9a-f]{64}$")
        marker = ROOT / "ports/.hash-write-dirty-check"
        marker.write_text("dirty\n", encoding="utf-8")
        try:
            write = subprocess.run(
                ["python3", script, "--source", "--write"],
                cwd=ROOT,
                capture_output=True,
                text=True,
            )
            self.assertNotEqual(write.returncode, 0)
            self.assertIn("dirty", (write.stderr + write.stdout).lower())
        finally:
            marker.unlink(missing_ok=True)

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
