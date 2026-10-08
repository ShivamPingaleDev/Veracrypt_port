#!/usr/bin/env python3
"""Agile gate for the phone UI walk.

Regressive cases are the old 10-phase session. They must stay in both
phone tests and in the walk. Progressive cases are behavior added after
that session (USB select, scribble reset, shared password rows). The walk
runs both sets, and GitHub Actions runs the same walk on each phone.
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from repo_paths import read  # noqa: E402


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


class ProgressiveWalkTests(unittest.TestCase):
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

    def test_github_actions_runs_the_same_walk(self) -> None:
        wf = read(".github/workflows/vcport.yml")
        self.assertIn("ui-walk-android:", wf)
        self.assertIn("ui-walk-ios:", wf)
        self.assertIn("VC_PORT_WALK=android", wf)
        self.assertIn("VC_PORT_WALK=ios", wf)
        self.assertIn("VC_PORT_CI=1", wf)
        self.assertIn("VC_PORT_CREATE_AVD=1", wf)
        self.assertIn("ports/scripts/run-ui-walk.sh", wf)
        self.assertIn("test_agile", wf)
        self.assertNotIn("needs: android", wf)
        self.assertNotIn("needs: ios", wf)
        self.assertNotIn("needs: ui-walk-android", wf)
        self.assertNotIn("needs: ui-walk-ios", wf)
        self.assertNotIn("SLOW=1", wf)
