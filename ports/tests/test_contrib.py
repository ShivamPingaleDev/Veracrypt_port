#!/usr/bin/env python3
"""Contribution catalog: regressive session, progressive walk, and nuances.

This is the list a contributor keeps. `ports/tests/contrib.sh` runs it,
then the host volume and crypto-safety proofs, then the UI walk.
GitHub Actions runs this file. It does not boot an emulator.
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from repo_paths import read  # noqa: E402
from test_agile import REGRESSIVE, WALK_FEATURES  # noqa: E402

# Host proofs that are not a separate walk row. The walk still has to
# keep the regressive status strings and the WALK_FEATURES rows.
# kind, feature, path, proof that must remain in that file.
NUANCES = (
    (
        "regressive",
        "Screenshots stay blocked",
        "ports/android/app/src/main/java/dev/shivampingale/vcport/Hardening.kt",
        "FLAG_SECURE",
    ),
    (
        "regressive",
        "The build stays offline",
        "ports/android/app/src/foss/AndroidManifest.xml",
        'android.permission.INTERNET" tools:node="remove"',
    ),
    (
        "regressive",
        "Home and Recents save then dismount",
        "ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt",
        "private fun dismountOnLeave()",
    ),
    (
        "regressive",
        "Home and Recents save then dismount on iPhone",
        "ports/ios/VCPort/ContentView.swift",
        "func dismountOnLeave()",
    ),
    (
        "regressive",
        "Tools can still change the header after the password field clears",
        "ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt",
        "lastUnlockPassword",
    ),
    (
        "regressive",
        "Tools can still change the header after the password field clears on iPhone",
        "ports/ios/VCPort/ContentView.swift",
        "lastUnlockPassword",
    ),
    (
        "regressive",
        "Quick format stays the default",
        "ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt",
        'createFullFormatState = mutableStateOf(false)',
    ),
    (
        "regressive",
        "Files app sharing stays off until the user ticks it",
        "ports/android/app/src/main/java/dev/shivampingale/vcport/VolumeDocumentsProvider.kt",
        "var shareWithFiles: Boolean = false",
    ),
    (
        "regressive",
        "A failed VeraCrypt self-test refuses to open",
        "ports/shared/vc_mobile.h",
        "VC_ERR_SELFTEST",
    ),
    (
        "regressive",
        "iPhone has no Files provider",
        "ports/ios/VCPortTests/AppInterfaceSessionTests.swift",
        "Mounted volume does not appear in Files.app",
    ),
    (
        "regressive",
        "Whole-disk USB is Android-only",
        "ports/android/app/build.gradle",
        "ENABLE_OTG_DISK",
    ),
    (
        "progressive",
        "Full format fills the data area before the filesystem",
        "ports/shared/vc_mobile.cpp",
        "format_fill_data_area",
    ),
    (
        "progressive",
        "Full format covers the nested free space",
        "ports/shared/vc_mobile.cpp",
        "outerFull, 0, 0",
    ),
    (
        "progressive",
        "Host check of nested full format",
        "ports/shared/test_volume_main.cpp",
        "full format filled the nested free space",
    ),
    (
        "progressive",
        "Host check of quick format",
        "ports/shared/test_volume_main.cpp",
        "quick format left the unused part of the volume empty",
    ),
    (
        "progressive",
        "Tools runs the official CRC-32 self-test",
        "ports/shared/vc_mobile.cpp",
        "crc32_selftests",
    ),
    (
        "progressive",
        "Tools runs the official Argon2id self-test",
        "ports/shared/vc_mobile.cpp",
        "argon2id_selftest",
    ),
    (
        "progressive",
        "Open flushes before the container is written back",
        "ports/android/app/src/main/java/dev/shivampingale/vcport/NativeBridge.kt",
        "flushVolume",
    ),
    (
        "progressive",
        "The Files app checkbox is on the Mounted tab",
        "ports/android/app/src/main/java/dev/shivampingale/vcport/VaultPane.kt",
        "files_app_share",
    ),
    (
        "progressive",
        "Hidden-volume protection still refuses a wipe that would damage it",
        "ports/shared/test_volume_main.cpp",
        "wipe outer free space is refused before it can overwrite the hidden volume",
    ),
)


class ContribCatalogTests(unittest.TestCase):
    def test_regressive_session_is_the_old_walk(self) -> None:
        self.assertGreaterEqual(len(REGRESSIVE), 12)
        doc = read("ports/tests/UI-WALK.md")
        self.assertIn("## Regressive session", doc)
        for needle in REGRESSIVE:
            self.assertIn(needle, doc, needle)

    def test_progressive_rows_are_the_walk_features(self) -> None:
        self.assertGreaterEqual(len(WALK_FEATURES), 9)
        doc = read("ports/tests/UI-WALK.md")
        self.assertIn("## Progressive cases", doc)
        for name, _android, _ios in WALK_FEATURES:
            self.assertIn(name, doc, name)

    def test_every_nuance_proof_is_still_in_the_tree(self) -> None:
        kinds = set()
        for kind, feature, path, proof in NUANCES:
            kinds.add(kind)
            self.assertIn(kind, ("regressive", "progressive"), feature)
            self.assertIn(proof, read(path), feature)
        self.assertEqual(kinds, {"regressive", "progressive"})

    def test_panic_closes_without_writing_the_cache_copy_back(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        body = main.split("private fun panicWipe()")[1].split("private fun publishTransferQueue")[0]
        self.assertIn("closeMountedVolume()", body)
        self.assertNotIn("saveMountedContainer", body)
        view = read("ports/ios/VCPort/ContentView.swift")
        ios = view.split("func panicWipe()")[1].split("func publishTransferQueue")[0]
        self.assertNotIn("saveMounted", ios)

    def test_import_never_uses_a_proc_self_fd_path(self) -> None:
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        self.assertNotIn("/proc/self/fd/${", main)
        native = read("ports/shared/vc_mobile.cpp")
        self.assertNotIn("/proc/self/fd/", native)

    def test_contributor_command_runs_this_catalog_and_the_walk(self) -> None:
        script = read("ports/tests/contrib.sh")
        self.assertIn("test_agile", script)
        self.assertIn("test_contrib", script)
        self.assertIn("run_crypto_safety_test.sh", script)
        self.assertIn("run_volume_test.sh", script)
        self.assertIn("run-ui-walk.sh", script)
        self.assertIn("VC_PORT_CONTRIB", script)
        self.assertIn("FAIL  contribution suite", script)
        self.assertIn("Stopped before the UI walk.", script)
        wf = read(".github/workflows/vcport.yml")
        self.assertNotIn("contrib.sh", wf)
        self.assertNotIn("run-ui-walk.sh", wf)
        phases = read("ports/tests/run-phases.sh")
        self.assertIn("test_contrib", phases)
        guide = read("ports/CONTRIBUTING.md")
        self.assertIn("ports/tests/contrib.sh", guide)

    def test_app_and_suite_fail_with_the_same_codes(self) -> None:
        header = read("ports/shared/vc_mobile.h")
        android = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        ios = read("ports/ios/VCPort/ContentView.swift")
        script = read("ports/tests/contrib.sh")
        for phrase in (
            "-1  IO. Stay on the current screen. Nothing new is mounted.",
            "-2  PASSWORD. Stay. The password field is kept for another try.",
            "-3  FORMAT. Stay unmounted. A partial container is discarded.",
            "-4  ARGUMENT. Stay. No file is written.",
            "-5  MEMORY. Stay. The operation stops.",
            "-6  UNSUPPORTED. Stay. That cipher or filesystem is refused.",
            "-7  SELFTEST. Open and create refuse. The rest of the app stays usable.",
            "-8  Open aborted after unlock. The volume is closed.",
            "-9  Create aborted. The incomplete file is discarded.",
            "-10 Hidden protection. The outer volume stays mounted and writes stop.",
        ):
            self.assertIn(phrase, header, phrase)
        self.assertIn('"$message (code $code)"', android)
        self.assertIn(r'"\(message) (code \(code))"', ios)
        for sentence in (
            "Could not read the container file.",
            "Wrong password, PIM, or keyfile mix.",
            "Not a VeraCrypt-compatible volume, or the header is damaged.",
            "Missing path or password argument.",
            "Not enough memory to open the volume.",
            "This container uses NTFS, ext, or another filesystem VC Port does not open. FAT and exFAT are supported.",
            "VeraCrypt self-test failed. This build will not open a volume.",
            "VeraCrypt self-test failed. This build will not create a volume.",
        ):
            self.assertIn(f'coded(rc, "{sentence}")', android, sentence)
            self.assertIn(f'coded(code, "{sentence}")', ios, sentence)
        for sentence in (
            "Open failed. The volume was not left mounted. (code -8)",
            "The incomplete file was discarded. (code -9)",
            "write-protected until you dismount. (code -10)",
        ):
            self.assertIn(sentence, android, sentence)
            self.assertIn(sentence, ios, sentence)
        password_fail = android.split("if (!NativeBridge.isOpen(result))")[1].split("return@Thread")[0]
        self.assertIn("openErrorMessage", password_fail)
        self.assertNotIn("wipeUnlockForm", password_fail)
        ios_fail = ios.split("guard let handle else")[1].split("return")[0]
        self.assertIn("openErrorMessage", ios_fail)
        self.assertNotIn("wipeUnlockForm", ios_fail)
        for code in (11, 12, 13, 14):
            self.assertIn(f"suite code {code}", script)
            self.assertIn(f"exit {code}", script)


if __name__ == "__main__":
    unittest.main()
