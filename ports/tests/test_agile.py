#!/usr/bin/env python3
"""Agile gate for the phone UI walk.

Regressive cases are the old 10-phase session. They must stay in both
phone tests and in the walk. Progressive cases are behavior added after
that session. Each one is a WALK_FEATURES row whose proof the walk runs
on both phones. A new UI test file that is missing from the walk fails
here. The walk runs on a local Mac. GitHub Actions does not boot an emulator.
"""

from __future__ import annotations

import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from repo_paths import read  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]

# Proof strings that the local UI walk must execute on both phones.
# A new user-facing feature adds one row and the same proof in the
# Android and iOS tests the walk already runs, plus UI-WALK.md.
WALK_FEATURES = (
    (
        "Transfer queue finished the copy",
        "Transfer queue finished the copy",
        "Transfer queue finished the copy",
    ),
    (
        "Create resets the scribble pad before the file is saved",
        "Create resets the scribble pad before the file is saved",
        "Create resets the scribble pad before the file is saved",
    ),
    ("USB select", "testingPreviewUsbSelect", "enableOtgDisk"),
    ("In-app preview", "preview-in-app-ok", "preview-in-app-ok"),
    (
        "In-app file browser lists the mounted volume",
        "In-app file browser lists the mounted volume",
        "In-app file browser lists the mounted volume",
    ),
    (
        "Files app",
        "Files app lists the mounted volume only after the user allows it",
        "Mounted volume does not appear in Files.app",
    ),
    (
        "Test vectors passed",
        "Test vectors passed",
        "Test vectors passed",
    ),
    (
        "Quick format left the unused part of the volume empty",
        "Quick format left the unused part of the volume empty",
        "Quick format left the unused part of the volume empty",
    ),
    (
        "Full format filled the volume",
        "Full format filled the volume",
        "Full format filled the volume",
    ),
)

# Native or compat checks. They are not a screen walk. A new UI test
# must not be added here; it goes in UiWalkSuite and run-ui-walk.sh.
WALK_EXEMPT = frozenset(
    {
        "DeviceSimulationTest",
        "DesktopCompatVolumeTest",
        "DesktopCompatVolumeTests",
        "CrossPhoneVolumeTest",
        "CrossPhoneVolumeTests",
        "WholeUsbSimTest",
        "MainActivityUiTest",
    }
)


REGRESSIVE = (
    "from the basket into the volume",
    "Session cleared",
    "Nested volume is inside",
    "Mounted in this app",
    "Created folder INBOX",
    "volumes mounted",
    "Dismounted",
    "Wrong password",
    "Restored from embedded backup header",
    "Read-only volumes refuse",
    "SHA-256 in volume",
    "Idle timeout",
)


class RegressiveWalkTests(unittest.TestCase):
    def test_old_session_stays_on_both_phones(self) -> None:
        android = read(
            "ports/android/app/src/androidTest/java/dev/shivampingale/vcport/AppInterfaceSessionTest.kt"
        )
        ios = read("ports/ios/VCPortTests/AppInterfaceSessionTests.swift")
        doc = read("ports/tests/UI-WALK.md")
        for needle in REGRESSIVE:
            self.assertIn(needle, android, needle)
            self.assertIn(needle, ios, needle)
            self.assertIn(needle, doc, needle)
        self.assertIn('onNodeWithTag("panic_wipe").assertExists()', android)
        self.assertNotIn('onNodeWithTag("panic_wipe").performClick()', android)
        self.assertNotIn("panicWipe()", ios)
        self.assertIn("Does not tap Panic wipe", android)
        self.assertIn("Does not tap Panic wipe", ios)
        self.assertIn("Check for updates", android)
        self.assertIn("Check for updates", ios)

    def test_walk_still_runs_the_old_session(self) -> None:
        walk = read("ports/scripts/run-ui-walk.sh")
        suite = read(
            "ports/android/app/src/androidTest/java/dev/shivampingale/vcport/UiWalkSuite.kt"
        )
        self.assertIn("AppInterfaceSessionTest::class", suite)
        self.assertIn("UiWalkSuite", walk)
        self.assertIn("AppInterfaceSessionTests", walk)
        self.assertIn("VC_PORT_WALK:-both", walk)
        phases = read("ports/tests/run-phases.sh")
        self.assertIn("test_agile", phases)


def _walk_android_text() -> str:
    suite = read(
        "ports/android/app/src/androidTest/java/dev/shivampingale/vcport/UiWalkSuite.kt"
    )
    names = [
        name
        for name in re.findall(r"(\w+)::class", suite)
        if name.endswith("Test")
    ]
    parts = [suite, read("ports/scripts/run-ui-walk.sh")]
    base = ROOT / "ports/android/app/src/androidTest/java/dev/shivampingale/vcport"
    for name in names:
        parts.append((base / f"{name}.kt").read_text(encoding="utf-8"))
    return "\n".join(parts)


def _walk_ios_text() -> str:
    walk = read("ports/scripts/run-ui-walk.sh")
    names = re.findall(r"-only-testing:VCPortTests/(\w+)", walk)
    base = ROOT / "ports/ios/VCPortTests"
    parts = [walk]
    for name in names:
        parts.append((base / f"{name}.swift").read_text(encoding="utf-8"))
    return "\n".join(parts)


class ProgressiveWalkTests(unittest.TestCase):
    def test_every_ui_test_is_on_the_walk(self) -> None:
        suite = read(
            "ports/android/app/src/androidTest/java/dev/shivampingale/vcport/UiWalkSuite.kt"
        )
        walk = read("ports/scripts/run-ui-walk.sh")
        android_dir = ROOT / "ports/android/app/src/androidTest/java/dev/shivampingale/vcport"
        for path in android_dir.glob("*Test.kt"):
            name = path.stem
            if name in WALK_EXEMPT:
                continue
            self.assertTrue(
                name in suite or name in walk,
                f"{name} is not in the UI walk. Add it to UiWalkSuite and run-ui-walk.sh.",
            )
        ios_dir = ROOT / "ports/ios/VCPortTests"
        for path in ios_dir.glob("*Tests.swift"):
            name = path.stem
            if name in WALK_EXEMPT:
                continue
            self.assertIn(
                name,
                walk,
                f"{name} is not in the UI walk. Add -only-testing:VCPortTests/{name} to run-ui-walk.sh.",
            )

    def test_listed_features_run_on_both_phone_walks(self) -> None:
        android = _walk_android_text()
        ios = _walk_ios_text()
        doc = read("ports/tests/UI-WALK.md")
        for name, android_proof, ios_proof in WALK_FEATURES:
            self.assertIn(name, doc, name)
            self.assertIn(android_proof, android, name)
            self.assertIn(ios_proof, ios, name)

    def test_new_cases_are_in_the_walk(self) -> None:
        suite = read(
            "ports/android/app/src/androidTest/java/dev/shivampingale/vcport/UiWalkSuite.kt"
        )
        walk = read("ports/scripts/run-ui-walk.sh")
        self.assertIn("FakeUsbUiTest::class", suite)
        self.assertIn("InAppPreviewTest::class", suite)
        self.assertIn("OtgAbsentAndPreviewTests", walk)
        self.assertIn("InAppPreviewTests", walk)
        fake = read(
            "ports/android/app/src/androidTest/java/dev/shivampingale/vcport/FakeUsbUiTest.kt"
        )
        self.assertIn("testingPreviewUsbSelect", fake)
        self.assertIn("testingFinishUsbPermission", fake)
        self.assertIn("usb_select_prompt", fake)
        self.assertIn("usb_device", fake)

    def test_create_resets_the_scribble_pad_before_save(self) -> None:
        android = read(
            "ports/android/app/src/androidTest/java/dev/shivampingale/vcport/AppInterfaceSessionTest.kt"
        )
        ios = read("ports/ios/VCPortTests/AppInterfaceSessionTests.swift")
        form = read("ports/android/app/src/main/java/dev/shivampingale/vcport/CreateVolumeForm.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        pad = read("ports/android/app/src/main/java/dev/shivampingale/vcport/VcPortTheme.kt")
        self.assertEqual(android.count("Create resets the scribble pad before the file is saved"), 2)
        self.assertEqual(ios.count("Create resets the scribble pad before the file is saved"), 2)
        saved = form.split("onSaved = {", 1)[1].split("if (!testingSkipSystemPickers)", 1)[0]
        self.assertIn("NativeBridge.resetEntropy()", saved)
        self.assertIn("entropyPercent = 0", saved)
        created = view.split("if rc != 0 {", 1)[1].split("pendingCreateURL = dest", 1)[0]
        self.assertIn("VcMobileBridge.resetEntropy()", created)
        self.assertIn("entropyMarks = []", created)
        self.assertIn("if (percent == 0) marks.clear()", pad)

    def test_shared_factor_rows_keep_the_same_tags(self) -> None:
        fields = read("ports/android/app/src/main/java/dev/shivampingale/vcport/FactorFields.kt")
        ios = read("ports/ios/VCPort/FactorFields.swift")
        self.assertIn("fun PasswordAndPim(", fields)
        self.assertIn("fun PimField(", fields)
        self.assertIn("fun KeyfileList(", fields)
        self.assertIn("func passwordAndPim(", ios)
        open_form = read("ports/android/app/src/main/java/dev/shivampingale/vcport/OpenVolumeForm.kt")
        create = read("ports/android/app/src/main/java/dev/shivampingale/vcport/CreateVolumeForm.kt")
        tools = read("ports/android/app/src/main/java/dev/shivampingale/vcport/ToolsPane.kt")
        self.assertIn('passwordTag = "volume_password"', open_form)
        self.assertIn('passwordTag = "create_password"', create)
        self.assertIn('passwordTag = "tools_new_password"', tools)
        self.assertIn('passwordTag: "volume_password"', read("ports/ios/VCPort/ContentView+Open.swift"))
        self.assertIn('passwordTag: "create_password"', read("ports/ios/VCPort/ContentView+Create.swift"))
        self.assertIn('passwordTag: "tools_new_password"', read("ports/ios/VCPort/ContentView+Tools.swift"))

    def test_skipped_save_sheet_does_not_delete_the_new_volume(self) -> None:
        sheet = read("ports/ios/VCPort/ShareSheet.swift")
        export = sheet.split("static func exportCopy(urls:", 1)[1]
        skipped = export.split("if VcPortTesting.shared.skipSystemPickers", 1)[1].split("let controller", 1)[0]
        self.assertNotIn("onFinish(nil)", skipped)
        finish = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("pendingCreateURL ?? containerURL", finish)
        pim = read("ports/ios/VCPort/PimEstimator.swift")
        self.assertIn('Locale(identifier: "en_US")', pim)
        self.assertIn("500_000", pim)

    def test_transfer_queue_is_one_worker_and_can_cancel(self) -> None:
        android = read("ports/android/app/src/main/java/dev/shivampingale/vcport/TransferQueue.kt")
        ios = read("ports/ios/VCPort/TransferQueue.swift")
        theme = read("ports/android/app/src/main/java/dev/shivampingale/vcport/VcPortTheme.kt")
        view = read("ports/ios/VCPort/ContentView.swift")
        main = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        for src in (android, ios):
            self.assertIn("fun claim" if src is android else "func claim", src)
            self.assertIn("requestCancel", src)
            self.assertIn("about ", src)
            self.assertIn("Queue cancelled", src)
        self.assertIn("transfer_queue", theme)
        self.assertIn("transfer_queue_cancel", theme)
        self.assertIn("Cancel queue", theme)
        self.assertIn("transfer_queue", view)
        self.assertIn("Cancel queue", view)
        self.assertIn("transferQueue.requestCancel()", main)
        self.assertIn("transferQueue.requestCancel()", view)
        self.assertIn("transferQueue.begin", main.split("private fun transferBetweenVolumes")[1].split("private fun refreshMountedListing")[0])
        self.assertIn("transferQueue.begin", view.split("func transferBetweenVolumes")[1].split("func refreshMountedListing")[0])
        self.assertNotIn("WorkManager", main)
        self.assertNotIn("BGTaskScheduler", view)
        session_android = read(
            "ports/android/app/src/androidTest/java/dev/shivampingale/vcport/AppInterfaceSessionTest.kt"
        )
        session_ios = read("ports/ios/VCPortTests/AppInterfaceSessionTests.swift")
        walk = read("ports/scripts/run-ui-walk.sh")
        doc = read("ports/tests/UI-WALK.md")
        for src in (session_android, session_ios, doc):
            self.assertIn("Transfer queue finished the copy", src)
        self.assertIn("testingLastTransferReport", session_android)
        self.assertIn("lastTransferReport", session_ios)
        self.assertIn("transfer queue", walk)
        self.assertIn("AppInterfaceSessionTest::class", read(
            "ports/android/app/src/androidTest/java/dev/shivampingale/vcport/UiWalkSuite.kt"
        ))

    def test_ui_walk_stays_local(self) -> None:
        wf = read(".github/workflows/vcport.yml")
        walk = read("ports/scripts/run-ui-walk.sh")
        self.assertNotIn("ui-walk-android:", wf)
        self.assertNotIn("ui-walk-ios:", wf)
        self.assertNotIn("VC_PORT_WALK=", wf)
        self.assertNotIn("run-ui-walk.sh", wf)
        self.assertIn("test_agile", wf)
        self.assertIn("VC_PORT_WALK:-both", walk)
        self.assertIn("UiWalkSuite", walk)
        self.assertNotIn("needs: android", wf)
        self.assertNotIn("needs: ios", wf)
