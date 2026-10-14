#!/usr/bin/env python3
"""Extensive host cases for every VC Port feature block.

The pure helpers (entropy, PIM, size, idle, queue, basket, names) are
executed here so Android and iOS cannot drift. Each screen block then
locks the same buttons, refusals, and session rules on both phones.
No emulator. No network.
"""

from __future__ import annotations

import math
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from repo_paths import read, read_android_ui, read_ios_ui  # noqa: E402

MIN_VOLUME = 2 * 1024 * 1024
MAX_VOLUME = 64 * 1024 * 1024 * 1024
HEADROOM = 32 << 20
KIB = 1024
MIB = 1024 * 1024
GIB = 1024 * 1024 * 1024
LONG_MAX = (1 << 63) - 1
ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%^&*-_=+"


def pool_size(password: str) -> int:
    if password and all(ch in ALPHABET for ch in password):
        return len(ALPHABET)
    pool = 0
    if any("A" <= ch <= "Z" for ch in password):
        pool += 26
    if any("a" <= ch <= "z" for ch in password):
        pool += 26
    if any(ch.isdigit() for ch in password):
        pool += 10
    if any(not ch.isalnum() for ch in password):
        pool += 32
    return max(pool, len(set(password)))


def entropy_bits(password: str) -> int:
    if not password:
        return 0
    return int(len(password) * math.log2(max(pool_size(password), 1)))


def entropy_label(password: str) -> str:
    bits = entropy_bits(password)
    if not password:
        return "Entropy: none."
    if len(password) < 16:
        return f"Entropy: about {bits} bits — too short."
    if bits >= 80:
        return f"Entropy: about {bits} bits."
    return f"Entropy: about {bits} bits — weak."


def hmac_iterations(pim: int) -> int:
    return 500_000 if pim <= 0 else pim * 1_000


def pim_from_text(text: str) -> int:
    digits = "".join(ch for ch in text if ch.isdigit())
    return int(digits) if digits else 0


def grouped(n: int) -> str:
    return f"{n:,}"


def pim_describe(kdf: str, pim_text: str) -> str:
    pim = pim_from_text(pim_text)
    if "argon2" in kdf.lower():
        return (
            "Argon2id. PIM changes Argon2 time cost. "
            "This is not seconds-to-open and not a crack-time estimate."
        )
    n = hmac_iterations(pim)
    pim_bit = "PIM 0 (VeraCrypt default)" if pim <= 0 else f"PIM {pim}"
    return (
        f"{kdf}: about {grouped(n)} header iterations ({pim_bit}). "
        "Not a crack-time estimate. Benchmark measures cipher speed, not this."
    )


def to_bytes(amount: int, factor: int) -> int:
    if amount <= 0:
        return 0
    if amount > LONG_MAX // factor:
        return LONG_MAX
    return amount * factor


def format_bytes(n: int) -> str:
    if n >= GIB:
        return f"{n / GIB:.2f} GiB"
    if n >= MIB:
        return f"{(n + MIB - 1) // MIB} MiB"
    return f"{(n + 1023) // KIB} KiB"


def fit(n: int) -> tuple[int, str]:
    n = min(max(n, MIN_VOLUME), MAX_VOLUME)
    if n >= GIB and n % GIB == 0:
        return n // GIB, "GiB"
    if n >= MIB:
        m = (n + MIB - 1) // MIB
        if m >= 1024 and m % 1024 == 0:
            return m // 1024, "GiB"
        return m, "MiB"
    return (n + 1023) // KIB, "KiB"


def app_storage_need(payload: int) -> int:
    return payload + HEADROOM


def shortage(payload: int, free: int) -> str | None:
    if payload <= 0:
        return None
    if free < app_storage_need(payload):
        need = app_storage_need(payload)
        return (
            "Not enough free space in app storage. "
            f"Needs {format_bytes(need)}; this phone has {format_bytes(free)}."
        )
    return None


MAX_IDLE = 24 * 60


def idle_minutes(amount: int, per: int) -> int:
    if amount <= 0:
        return 0
    return min(max(amount * per, 0), MAX_IDLE)


def idle_split(minutes: int) -> tuple[int, str]:
    n = min(max(minutes, 0), MAX_IDLE)
    if n >= 60 and n % 60 == 0:
        return n // 60, "h"
    return n, "min"


def idle_label(minutes: int) -> str:
    n = min(max(minutes, 0), MAX_IDLE)
    if n == 0:
        return "Off"
    if n == 1:
        return "1 minute"
    if n % 60 == 0:
        h = n // 60
        return "1 hour" if h == 1 else f"{h} hours"
    return f"{n} minutes"


WAITING, RUNNING, DONE, FAILED, CANCELLED = (
    "Waiting",
    "Running",
    "Done",
    "Failed",
    "Cancelled",
)


class Queue:
    def __init__(self, clock=None) -> None:
        self.clock = clock or (lambda: 0.0)
        self.jobs: list[dict] = []
        self.cancel_requested = False
        self.generation = 0
        self.started = 0.0

    def begin(self, items: list[tuple[str, int]]) -> None:
        self.generation += 1
        self.cancel_requested = False
        self.started = self.clock()
        self.jobs = [
            {"name": name, "bytes": max(size, 0), "state": WAITING, "detail": ""}
            for name, size in items
        ]

    def claim(self, index: int) -> bool:
        if self.cancel_requested:
            self._cancel_from(index)
            return False
        if 0 <= index < len(self.jobs):
            self.jobs[index]["state"] = RUNNING
        return True

    def finish(self, index: int, failed: str | None) -> None:
        if index < 0 or index >= len(self.jobs):
            return
        if failed is None:
            self.jobs[index]["state"] = DONE
            self.jobs[index]["detail"] = ""
        else:
            self.jobs[index]["state"] = FAILED
            self.jobs[index]["detail"] = failed

    def request_cancel(self) -> None:
        self.cancel_requested = True

    def reset(self) -> None:
        self.generation += 1
        self.cancel_requested = True
        self.jobs = []
        self.started = 0.0

    def eta(self) -> str:
        total = sum(job["bytes"] for job in self.jobs)
        done = sum(
            job["bytes"] for job in self.jobs if job["state"] in (DONE, FAILED)
        )
        if total <= 0 or done <= 0 or done >= total:
            return ""
        elapsed = self.clock() - self.started
        if elapsed < 0.5:
            return ""
        seconds = max(int(elapsed * (total - done) / done), 1)
        return f"about {seconds}s left"

    def _cancel_from(self, index: int) -> None:
        for i in range(max(index, 0), len(self.jobs)):
            if self.jobs[i]["state"] in (WAITING, RUNNING):
                self.jobs[i]["state"] = CANCELLED
                self.jobs[i]["detail"] = "Queue cancelled"


def volume_bytes(asked: int, payloads: list[int], hidden: int) -> int:
    payload = sum(n if n > 0 else MIB for n in payloads)
    overhead = 0 if not payloads else 5 * MIB
    need = payload + overhead + hidden
    size = max(asked, need, MIN_VOLUME)
    if hidden > 0:
        size = max(size, hidden * 2 + MIN_VOLUME)
    return size


def nested_fits(outer: int, nested: int) -> bool:
    return nested >= MIN_VOLUME and outer >= 8 * MIB and nested * 2 < outer


def create_refusal(
    password: str,
    keyfiles: int,
    entropy: int,
    size: int,
    basket: list[int],
    hidden: bool,
    hidden_password: str,
    hidden_keyfiles: int,
    hidden_size: int,
    free: int,
) -> str | None:
    if not password and keyfiles == 0:
        return "Type a volume password, or add a keyfile."
    if password and len(password) < 16 and keyfiles == 0:
        return "Use Generate strong password, or type at least 16 characters. Nothing is saved."
    if entropy < 100:
        return "Move your finger in the blank area until the randomness bar is full."
    nested = 0
    if hidden:
        if len(hidden_password) < 16 and hidden_keyfiles == 0:
            return (
                "Nested volume password must be at least 16 characters, "
                "and different from the outer password."
            )
        if hidden_password and hidden_password == password:
            return "Use a different password for the nested volume."
        nested = hidden_size
        if nested < MIN_VOLUME:
            return (
                "Nested size must be at least 2 MiB and less than half the outer size, "
                "so the outer volume has room."
            )
    if not basket and (size < MIN_VOLUME or size > MAX_VOLUME):
        return "Size must be at least 2 MiB and at most 64 GiB on this phone."
    size = volume_bytes(size, basket, nested)
    if size > MAX_VOLUME:
        return "Basket is too large for a 64 GiB phone volume. Remove files."
    short = shortage(size, free)
    if short:
        return short
    if hidden:
        if size < 8 * MIB or nested * 2 >= size:
            return (
                "Nested size must be at least 2 MiB and less than half the outer size, "
                "so the outer volume has room."
            )
    return None


def safe_name(name: str) -> str:
    out = []
    for ch in name:
        out.append("_" if ch in "\\/\x00" else ch)
    return "".join(out) or "file"


def _strip_vcpw(name: str) -> str:
    if name.lower().endswith(".vcpw"):
        name = name[:-5]
    if not name:
        return "volume.hc"
    return name[:120]


def sanitize_android(raw: str) -> str:
    trimmed = raw.strip()
    if trimmed in ("", ".", ".."):
        return "volume.hc"
    return _strip_vcpw(safe_name(trimmed))


def sanitize_ios(raw: str) -> str:
    name = raw.strip().replace("/", "_").replace("\\", "_")
    if name in ("", ".", ".."):
        return "volume.hc"
    return _strip_vcpw(name)


def unique_dest(raw: str, used: set[str]) -> str:
    name = safe_name(raw) or "file"
    if name not in used:
        used.add(name)
        return name
    dot = name.rfind(".")
    if dot > 0:
        stem, ext = name[:dot], name[dot:]
    else:
        stem, ext = name, ""
    n = 2
    while True:
        cand = f"{stem}-{n}{ext}"
        if cand not in used:
            used.add(cand)
            return cand
        n += 1


def short_hex(hex_text: str) -> str:
    if len(hex_text) < 12:
        return hex_text
    return f"{hex_text[:8]}…{hex_text[-4:]}"


KEPT = "Dismounted. Create form kept."


def write_accepted(copied: int, expected: int) -> bool:
    """A returned stream is not a finished file. Zero bytes is not a volume."""
    if expected <= 0:
        return False
    return copied == expected


def write_back_allowed(read_only: bool, cache_copy: bool, distinct: bool) -> bool:
    if read_only:
        return False
    if not cache_copy:
        return False
    return distinct


def save_failure(failed: list[str]) -> str | None:
    if not failed:
        return None
    which = failed[0] if len(failed) == 1 else str(len(failed))
    return f"Not saved: {which}. Original unchanged."


def dismount_status(label: str, remaining: int, had_source: bool, saved: bool) -> str:
    if had_source and not saved:
        return f"Dismounted {label}. Not saved."
    if had_source and saved:
        if remaining == 0:
            return f"Saved. Dismounted {label}."
        return f"Saved. Dismounted {label}. {remaining} still open."
    if remaining == 0:
        return f"Dismounted {label}."
    return f"Dismounted {label}. {remaining} still open."


def lock_status(finish: str, failed: list[str]) -> str:
    fail = save_failure(failed)
    if fail is None:
        return finish
    return f"{finish} {fail}"


def path_rejected(path: str) -> bool:
    return path == "" or path.startswith("/proc/self/fd/")


def leave_status(was_open: bool, failed: list[str], panic: bool) -> str | None:
    """The person has left. The phone may not have finished the write."""
    if not was_open or panic:
        return None
    fail = save_failure(failed)
    if fail is None:
        return KEPT
    return f"{KEPT} {fail}"


def function_body(text: str, name: str) -> str:
    start = -1
    for marker in (f"fun {name}(", f"func {name}("):
        start = text.find(marker)
        if start >= 0:
            break
    if start < 0:
        raise AssertionError(f"missing {name}")
    lines = text[start:].splitlines(True)
    body = [lines[0]]
    for line in lines[1:]:
        stripped = line.lstrip()
        if line.startswith("    ") and not line.startswith("        ") and (
            stripped.startswith("fun ")
            or stripped.startswith("func ")
            or stripped.startswith("private fun ")
            or stripped.startswith("private func ")
        ):
            break
        body.append(line)
    return "".join(body)


class PasswordEntropyBlockTests(unittest.TestCase):
    def test_alphabet_excludes_ambiguous_characters(self) -> None:
        self.assertEqual(len(ALPHABET), 69)
        for banned in "0O1lI":
            self.assertNotIn(banned, ALPHABET)
        self.assertEqual(len(set(ALPHABET)), 69)

    def test_bits_and_labels(self) -> None:
        generated = ALPHABET[:16]
        long_pw = ALPHABET[0] * 64
        rows = (
            ("", 0, "Entropy: none."),
            ("abc", entropy_bits("abc"), "too short"),
            ("password", entropy_bits("password"), "too short"),
            ("a" * 15, entropy_bits("a" * 15), "too short"),
            (generated, entropy_bits(generated), "Entropy: about"),
            (long_pw, entropy_bits(long_pw), "Entropy: about"),
            ("lowercase-only!!", entropy_bits("lowercase-only!!"), "Entropy: about"),
        )
        for password, bits, piece in rows:
            with self.subTest(password=password[:12], bits=bits):
                self.assertEqual(entropy_bits(password), bits)
                label = entropy_label(password)
                self.assertIn(piece, label)
                if password and len(password) < 16:
                    self.assertIn("too short", label)
                if len(password) >= 16 and bits >= 80:
                    self.assertNotIn("weak", label)
                    self.assertNotIn("too short", label)
        self.assertGreaterEqual(entropy_bits(generated), 80)
        self.assertGreaterEqual(entropy_bits(long_pw), 256)
        self.assertLess(entropy_bits("a" * 15), entropy_bits("a" * 16))

    def test_symbol_outside_the_generator_uses_a_smaller_pool(self) -> None:
        inside = "A" * 20
        outside = "A" * 19 + " "
        self.assertEqual(pool_size(inside), len(ALPHABET))
        self.assertLess(pool_size(outside), pool_size(inside))
        self.assertLess(entropy_bits(outside), entropy_bits(inside))

    def test_both_phones_share_the_alphabet_and_bands(self) -> None:
        kt = read("ports/android/app/src/main/java/dev/shivampingale/vcport/PasswordEntropy.kt")
        sw = read("ports/ios/VCPort/PasswordEntropy.swift")
        for blob in (kt, sw):
            self.assertIn(ALPHABET, blob)
            self.assertIn("Entropy: none.", blob)
            self.assertIn("too short.", blob)
            self.assertIn("weak.", blob)
            self.assertIn("password.length < 16" if "fun bits" in blob else "password.count < 16", blob)


class PimEstimatorBlockTests(unittest.TestCase):
    def test_hmac_rows(self) -> None:
        rows = (
            (-5, 500_000),
            (0, 500_000),
            (1, 1_000),
            (12, 12_000),
            (485, 485_000),
            (1000, 1_000_000),
        )
        for pim, want in rows:
            with self.subTest(pim=pim):
                self.assertEqual(hmac_iterations(pim), want)

    def test_text_keeps_digits_only(self) -> None:
        self.assertEqual(pim_from_text(""), 0)
        self.assertEqual(pim_from_text("0"), 0)
        self.assertEqual(pim_from_text("000"), 0)
        self.assertEqual(pim_from_text("-1"), 1)
        self.assertEqual(pim_from_text("12abc"), 12)
        self.assertEqual(pim_from_text("PIM 485"), 485)

    def test_describe_does_not_claim_crack_time(self) -> None:
        hmac = pim_describe("HMAC-SHA-512", "0")
        self.assertIn("500,000", hmac)
        self.assertIn("PIM 0 (VeraCrypt default)", hmac)
        self.assertIn("Not a crack-time estimate", hmac)
        self.assertNotIn("seconds", hmac)
        custom = pim_describe("HMAC-SHA-256", "485")
        self.assertIn("485,000", custom)
        self.assertIn("PIM 485", custom)
        argon = pim_describe("Argon2id", "485")
        self.assertNotIn("485", argon)
        self.assertIn("not a crack-time estimate", argon)
        self.assertEqual(pim_describe("argon2id", "1"), argon)

    def test_both_phones_use_the_same_formula(self) -> None:
        kt = read("ports/android/app/src/main/java/dev/shivampingale/vcport/PimEstimator.kt")
        sw = read("ports/ios/VCPort/PimEstimator.swift")
        for blob in (kt, sw):
            self.assertIn("500_000", blob)
            self.assertIn("1_000", blob)
            self.assertIn("Argon2", blob)
            self.assertIn("Not a crack-time estimate", blob)
            self.assertIn("Benchmark measures cipher speed", blob)


class SizeAndStorageBlockTests(unittest.TestCase):
    def test_to_bytes_boundaries(self) -> None:
        self.assertEqual(to_bytes(0, MIB), 0)
        self.assertEqual(to_bytes(-3, MIB), 0)
        self.assertEqual(to_bytes(2, MIB), MIN_VOLUME)
        self.assertEqual(to_bytes(64, GIB), MAX_VOLUME)
        self.assertEqual(to_bytes(LONG_MAX, KIB), LONG_MAX)

    def test_format_bytes_rounds_up_below_a_gibibyte(self) -> None:
        rows = (
            (0, "0 KiB"),
            (1, "1 KiB"),
            (KIB, "1 KiB"),
            (KIB + 1, "2 KiB"),
            (MIB, "1 MiB"),
            (MIB + 1, "2 MiB"),
            (GIB, "1.00 GiB"),
            (3 * GIB // 2, "1.50 GiB"),
            (MAX_VOLUME, "64.00 GiB"),
        )
        for n, want in rows:
            with self.subTest(n=n):
                self.assertEqual(format_bytes(n), want)

    def test_fit_clamps_and_picks_a_unit(self) -> None:
        rows = (
            (0, (2, "MiB")),
            (MIN_VOLUME, (2, "MiB")),
            (3 * MIB, (3, "MiB")),
            (GIB, (1, "GiB")),
            (GIB + 1, (1025, "MiB")),
            (MAX_VOLUME, (64, "GiB")),
            (MAX_VOLUME + GIB, (64, "GiB")),
        )
        for n, want in rows:
            with self.subTest(n=n):
                self.assertEqual(fit(n), want)

    def test_app_storage_names_need_and_free(self) -> None:
        self.assertIsNone(shortage(0, 0))
        self.assertIsNone(shortage(MIB, app_storage_need(MIB)))
        self.assertIsNone(shortage(MIB, app_storage_need(MIB) + 1))
        text = shortage(MIB, app_storage_need(MIB) - 1)
        self.assertIsNotNone(text)
        assert text is not None
        self.assertIn("Needs", text)
        self.assertIn("this phone has", text)
        self.assertIn(format_bytes(app_storage_need(MIB)), text)

    def test_both_phones_share_limits_and_the_shortage_sentence(self) -> None:
        kt = read("ports/android/app/src/main/java/dev/shivampingale/vcport/SizeUnits.kt")
        sw = read("ports/ios/VCPort/PasswordEntropy.swift")
        space = read("ports/ios/VCPort/AppStorageSpace.swift")
        self.assertIn("2L * 1024L * 1024L", kt)
        self.assertIn("64L * 1024L * 1024L * 1024L", kt)
        self.assertIn("2 * 1024 * 1024", sw)
        self.assertIn("64 * 1024 * 1024 * 1024", sw)
        self.assertIn("%.2f", kt)
        self.assertIn("%.2f", sw)
        self.assertIn("32L shl 20", kt)
        self.assertIn("32 * 1024 * 1024", space)
        self.assertIn("Not enough free space in app storage.", kt)
        self.assertIn("Not enough free space in app storage.", space)
        self.assertIn("Could not copy the container into app storage.", kt)
        self.assertIn("Could not copy the container into app storage.", space)


class SessionIdleBlockTests(unittest.TestCase):
    def test_zero_is_off_and_the_day_is_the_cap(self) -> None:
        rows = (
            (0, 1, 0, "Off"),
            (-4, 60, 0, "Off"),
            (1, 1, 1, "1 minute"),
            (59, 1, 59, "59 minutes"),
            (1, 60, 60, "1 hour"),
            (2, 60, 120, "2 hours"),
            (90, 1, 90, "90 minutes"),
            (24, 60, MAX_IDLE, "24 hours"),
            (100, 60, MAX_IDLE, "24 hours"),
            (MAX_IDLE + 5, 1, MAX_IDLE, "24 hours"),
        )
        for amount, per, minutes, label in rows:
            with self.subTest(amount=amount, per=per):
                self.assertEqual(idle_minutes(amount, per), minutes)
                self.assertEqual(idle_label(minutes), label)

    def test_split_uses_hours_only_on_an_exact_hour(self) -> None:
        self.assertEqual(idle_split(0), (0, "min"))
        self.assertEqual(idle_split(59), (59, "min"))
        self.assertEqual(idle_split(60), (1, "h"))
        self.assertEqual(idle_split(90), (90, "min"))
        self.assertEqual(idle_split(120), (2, "h"))
        self.assertEqual(idle_split(10_000), (24, "h"))

    def test_both_phones_cap_a_day(self) -> None:
        kt = read("ports/android/app/src/main/java/dev/shivampingale/vcport/SessionIdle.kt")
        sw = read("ports/ios/VCPort/SessionIdle.swift")
        for blob in (kt, sw):
            self.assertIn("24 * 60", blob)
            self.assertIn('"Off"' if "Off" in blob else "Off", blob)
            self.assertIn("1 minute", blob)
            self.assertIn("1 hour", blob)


class TransferQueueBlockTests(unittest.TestCase):
    def test_begin_claim_finish_and_a_second_batch(self) -> None:
        q = Queue()
        q.begin([("a.txt", 10), ("b.txt", -4)])
        self.assertEqual(q.generation, 1)
        self.assertEqual([job["state"] for job in q.jobs], [WAITING, WAITING])
        self.assertEqual(q.jobs[1]["bytes"], 0)
        self.assertTrue(q.claim(0))
        self.assertEqual(q.jobs[0]["state"], RUNNING)
        q.finish(0, None)
        q.finish(1, "disk full")
        q.finish(9, "ignored")
        self.assertEqual(q.jobs[0]["state"], DONE)
        self.assertEqual(q.jobs[1]["state"], FAILED)
        self.assertEqual(q.jobs[1]["detail"], "disk full")
        q.begin([("c.txt", 1)])
        self.assertEqual(q.generation, 2)
        self.assertFalse(q.cancel_requested)
        self.assertEqual(len(q.jobs), 1)

    def test_cancel_stops_at_the_next_file_and_keeps_finished_rows(self) -> None:
        q = Queue()
        q.begin([("a", 1), ("b", 1), ("c", 1)])
        self.assertTrue(q.claim(0))
        q.finish(0, None)
        q.request_cancel()
        self.assertFalse(q.claim(1))
        self.assertEqual(q.jobs[0]["state"], DONE)
        self.assertEqual(q.jobs[1]["state"], CANCELLED)
        self.assertEqual(q.jobs[2]["state"], CANCELLED)
        self.assertEqual(q.jobs[1]["detail"], "Queue cancelled")

    def test_reset_drops_the_batch(self) -> None:
        q = Queue()
        q.begin([("a", 1)])
        q.reset()
        self.assertEqual(q.jobs, [])
        self.assertTrue(q.cancel_requested)
        self.assertEqual(q.generation, 2)

    def test_eta_waits_for_finished_bytes_and_half_a_second(self) -> None:
        now = {"t": 0.0}
        q = Queue(clock=lambda: now["t"])
        q.begin([("a", 0), ("b", 0)])
        q.finish(0, None)
        self.assertEqual(q.eta(), "")
        q.begin([("a", 100), ("b", 300)])
        self.assertEqual(q.eta(), "")
        q.finish(0, None)
        self.assertEqual(q.eta(), "")
        now["t"] = 0.5
        self.assertEqual(q.eta(), "about 1s left")
        now["t"] = 2.0
        self.assertEqual(q.eta(), "about 6s left")
        q.finish(1, None)
        self.assertEqual(q.eta(), "")

    def test_both_phones_keep_one_in_memory_queue(self) -> None:
        kt = read("ports/android/app/src/main/java/dev/shivampingale/vcport/TransferQueue.kt")
        sw = read("ports/ios/VCPort/TransferQueue.swift")
        for blob in (kt, sw):
            for word in (WAITING, RUNNING, DONE, FAILED, CANCELLED, "Queue cancelled", "about "):
                self.assertIn(word, blob)
            self.assertTrue("0.5" in blob or "500_000_000L" in blob, blob[:80])
            self.assertIn("requestCancel", blob)
        ui_a = read("ports/android/app/src/main/java/dev/shivampingale/vcport/VcPortTheme.kt")
        ui_i = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("transfer_queue", ui_a)
        self.assertIn("Cancel queue", ui_a)
        self.assertIn("transferQueue", ui_i)


class BasketAndNestedBlockTests(unittest.TestCase):
    def test_volume_grows_for_files_and_the_nested_half(self) -> None:
        rows = (
            (MIN_VOLUME, [], 0, MIN_VOLUME),
            (MIB, [], 0, MIN_VOLUME),
            (MIN_VOLUME, [3 * MIB], 0, 8 * MIB),
            (MIN_VOLUME, [0], 0, 6 * MIB),
            (MIN_VOLUME, [MIB, MIB], 0, 7 * MIB),
            (MIN_VOLUME, [], 4 * MIB, 10 * MIB),
            (20 * MIB, [], 4 * MIB, 20 * MIB),
        )
        for asked, payloads, hidden, want in rows:
            with self.subTest(asked=asked, payloads=payloads, hidden=hidden):
                self.assertEqual(volume_bytes(asked, payloads, hidden), want)

    def test_nested_must_be_two_mib_and_under_half(self) -> None:
        self.assertFalse(nested_fits(8 * MIB, MIB))
        self.assertFalse(nested_fits(8 * MIB, 4 * MIB))
        self.assertTrue(nested_fits(8 * MIB, 2 * MIB))
        self.assertTrue(nested_fits(10 * MIB, 4 * MIB))
        self.assertFalse(nested_fits(7 * MIB, 2 * MIB))
        self.assertFalse(nested_fits(64 * GIB, 32 * GIB))
        self.assertTrue(nested_fits(64 * GIB, 32 * GIB - 1))

    def test_create_refusals(self) -> None:
        ok_pw = "correct horse battery staple"
        nested_pw = "different horse battery staple"
        plenty = MAX_VOLUME
        base = dict(
            password=ok_pw,
            keyfiles=0,
            entropy=100,
            size=16 * MIB,
            basket=[],
            hidden=False,
            hidden_password="",
            hidden_keyfiles=0,
            hidden_size=0,
            free=plenty,
        )
        cases = (
            ("empty", {**base, "password": ""}, "Type a volume password"),
            ("short", {**base, "password": "short-secret"}, "at least 16 characters"),
            ("keyfile instead of password", {**base, "password": "", "keyfiles": 1}, None),
            ("scribble", {**base, "entropy": 99}, "randomness bar is full"),
            ("tiny", {**base, "size": MIB}, "at least 2 MiB"),
            ("huge", {**base, "size": MAX_VOLUME + 1}, "at most 64 GiB"),
            ("same nested password", {**base, "hidden": True, "hidden_password": ok_pw, "hidden_size": 4 * MIB}, "different password"),
            ("short nested", {**base, "hidden": True, "hidden_password": "short", "hidden_size": 4 * MIB}, "at least 16 characters"),
            ("nested keyfile", {**base, "hidden": True, "hidden_password": "", "hidden_keyfiles": 1, "hidden_size": 4 * MIB, "size": 16 * MIB}, None),
            ("nested too small", {**base, "hidden": True, "hidden_password": nested_pw, "hidden_size": MIB}, "less than half"),
            ("nested leaves the outer under 8 MiB", {**base, "hidden": True, "hidden_password": nested_pw, "hidden_size": 2 * MIB, "size": MIN_VOLUME}, "less than half"),
            ("nested ok", {**base, "hidden": True, "hidden_password": nested_pw, "hidden_size": 4 * MIB, "size": 16 * MIB}, None),
            ("no space", {**base, "free": 0}, "Not enough free space"),
            ("basket too big", {**base, "basket": [MAX_VOLUME], "size": MIN_VOLUME}, "Basket is too large"),
        )
        for name, kwargs, piece in cases:
            with self.subTest(name=name):
                got = create_refusal(**kwargs)
                if piece is None:
                    self.assertIsNone(got)
                else:
                    self.assertIsNotNone(got)
                    assert got is not None
                    self.assertIn(piece, got)

    def test_both_phones_grow_the_volume_the_same_way(self) -> None:
        android = read_android_ui()
        ios = read_ios_ui()
        for blob in (android, ios):
            self.assertIn("volumeBytesForBasket", blob)
            self.assertIn("1L shl 20" if "fun volumeBytesForBasket" in blob else "1 << 20", blob)
            self.assertIn("5L shl 20" if "fun volumeBytesForBasket" in blob else "5 << 20", blob)
            self.assertIn("hiddenBytes * 2", blob)
            self.assertIn("less than half the outer size", blob)
            self.assertIn("Type a volume password, or add a keyfile.", blob)
            self.assertIn("randomness bar is full", blob)
            self.assertIn("Use a different password for the nested volume.", blob)
            self.assertIn("Basket is too large", blob)
            self.assertIn("there is no open-time hidden checkbox", blob)


class NamesAndHashBlockTests(unittest.TestCase):
    def test_disguise_names(self) -> None:
        shared = (
            (".", "volume.hc"),
            ("..", "volume.hc"),
            ("notes.vcpw", "notes"),
            ("NOTES.VCPW", "NOTES"),
            (".vcpw", "volume.hc"),
            ("photo.jpg", "photo.jpg"),
            ("a/b.jpg", "a_b.jpg"),
            ("a\\b.jpg", "a_b.jpg"),
            ("n" * 200 + ".jpg", ("n" * 200 + ".jpg")[:120]),
        )
        for raw, want in shared:
            with self.subTest(raw=raw[:20]):
                self.assertEqual(sanitize_android(raw), want)
                self.assertEqual(sanitize_ios(raw), want)
        self.assertEqual(sanitize_android(""), "volume.hc")
        self.assertEqual(sanitize_android("   "), "volume.hc")
        self.assertEqual(sanitize_ios(""), "volume.hc")
        self.assertEqual(sanitize_ios("   "), "volume.hc")
        self.assertEqual(sanitize_android("a\x00b.hc"), "a_b.hc")
        self.assertEqual(sanitize_ios("a\x00b.hc"), "a\x00b.hc")

    def test_unique_destination_adds_a_number(self) -> None:
        used: set[str] = set()
        self.assertEqual(unique_dest("notes.txt", used), "notes.txt")
        self.assertEqual(unique_dest("notes.txt", used), "notes-2.txt")
        self.assertEqual(unique_dest("notes.txt", used), "notes-3.txt")
        self.assertEqual(unique_dest("", used), "file")
        self.assertEqual(unique_dest("", used), "file-2")
        self.assertEqual(unique_dest("archive.tar.gz", used), "archive.tar.gz")
        self.assertEqual(unique_dest("archive.tar.gz", used), "archive.tar-2.gz")
        self.assertEqual(unique_dest(".bashrc", used), ".bashrc")
        self.assertEqual(unique_dest(".bashrc", used), ".bashrc-2")

    def test_short_hex_keeps_short_digests(self) -> None:
        self.assertEqual(short_hex(""), "")
        self.assertEqual(short_hex("abc"), "abc")
        self.assertEqual(short_hex("a" * 11), "a" * 11)
        self.assertEqual(short_hex("a" * 12), "aaaaaaaa…aaaa")
        digest = "0123456789abcdef" * 4
        self.assertEqual(short_hex(digest), "01234567…cdef")

    def test_both_phones_sanitize_and_shorten(self) -> None:
        helper = read("ports/android/app/src/main/java/dev/shivampingale/vcport/ShareHelper.kt")
        ios = read("ports/ios/VCPort/ContentView.swift")
        disguise = function_body(helper, "sanitizeDisguiseName")
        self.assertLess(disguise.index("volume.hc"), disguise.index("safeName"))
        self.assertIn("volume.hc", helper)
        self.assertIn(".vcpw", helper)
        self.assertIn("take(120)", helper)
        self.assertIn("volume.hc", ios)
        self.assertIn(".vcpw", ios)
        self.assertIn("prefix(120)", ios)
        self.assertIn('"$stem-$n$ext"', read_android_ui())
        self.assertIn('"\\(stem)-\\(n).\\(ext)"', ios)
        self.assertIn("hex.take(8)", read("ports/android/app/src/main/java/dev/shivampingale/vcport/BasketHash.kt"))
        self.assertIn("hex.prefix(8)", ios)
        for name in (
            "volume.hc",
            "photo.jpg",
            "image.png",
            "model.safetensors",
            "adapter.lora",
        ):
            self.assertIn(name, helper)


class OpenVolumeBlockTests(unittest.TestCase):
    def test_one_form_on_both_phones(self) -> None:
        android = read_android_ui()
        ios = read_ios_ui()
        for blob in (android, ios):
            for needle in (
                "Choose container",
                "Open volume",
                "PIM (0 = default)",
                "keyfile",
                "Read-only",
                "read_only",
                "idle_amount",
                "Share this encrypted file",
            ):
                self.assertIn(needle, blob, needle)
        self.assertIn("open_volume_form", android)
        self.assertIn("Hidden volume PIM (0 = default)", android)
        self.assertIn("Whole-disk Open is not on iPhone.", ios)
        self.assertIn('path.startsWith("/proc/self/fd/")', android)
        self.assertIn("return false", android)
        self.assertNotIn("/proc/self/fd/${", android)
        self.assertNotIn("/proc/self/fd", ios)


class CreateVolumeBlockTests(unittest.TestCase):
    def test_create_controls_and_defaults(self) -> None:
        android = read_android_ui()
        ios = read_ios_ui()
        for blob in (android, ios):
            for needle in (
                "Generate strong password",
                "Copy once",
                "Forget password",
                "create_cipher",
                "create_kdf",
                "create_filesystem",
                "create_full_format",
                "create_filename",
                "create_size",
                "create_hidden",
                "create_hidden_size",
                "Generate nested password",
                "Add files to basket",
                "Empty basket",
                "Quick format",
                "Full format",
            ):
                self.assertIn(needle, blob, needle)
        self.assertIn("exFAT if a file is over 4 GiB.", ios)
        self.assertIn("FAT", android)
        self.assertIn("exFAT", android)


class MountedVaultBlockTests(unittest.TestCase):
    def test_browser_and_file_actions(self) -> None:
        android = read_android_ui()
        ios = read_ios_ui()
        for blob in (android, ios):
            for needle in (
                "Open another container",
                "View in app",
                "Copy to volume",
                "Move to volume",
                "New folder",
                "Wipe free space",
                "SHA-256 in volume",
                "Copy from device",
                "Move from device",
                "Copy to device",
                "Move to device",
                "Rename",
                "Delete",
                "Properties",
                "Share decrypted",
                "Load more",
                "read_only_banner",
                "vault_path",
                "hash_result",
            ):
                self.assertIn(needle, blob, needle)
        self.assertIn("mount_slot_", android)
        self.assertIn("volumes mounted", ios)
        self.assertIn("Read-only. This slot refuses writes", ios)


class ToolsBlockTests(unittest.TestCase):
    def test_header_tools_stay_off_the_open_form(self) -> None:
        android = read("ports/android/app/src/main/java/dev/shivampingale/vcport/ToolsPane.kt")
        ios = read("ports/ios/VCPort/ContentView+Tools.swift")
        for blob in (android, ios):
            for needle in (
                "Change volume password",
                "Set header key derivation algorithm",
                "Add/Remove keyfiles to/from volume",
                "Remove all keyfiles from volume",
                "Backup volume header",
                "Restore volume header",
                "Restore from embedded backup header",
                "Volume properties",
                "Keyfile generator",
                "Benchmark",
                "Test vectors",
                "PIM iteration estimate",
                "Wipe cached passwords",
            ):
                self.assertIn(needle, blob, needle)
        self.assertIn("tools_pim_estimate", android)
        self.assertIn("tools_test_vectors", android)
        self.assertIn(
            "Not a crack-time estimate",
            read("ports/android/app/src/main/java/dev/shivampingale/vcport/PimEstimator.kt"),
        )
        self.assertIn(
            "Not a crack-time estimate",
            read("ports/ios/VCPort/PimEstimator.swift"),
        )


class UsbAndFilesBlockTests(unittest.TestCase):
    def test_usb_asks_before_it_lists_a_disk(self) -> None:
        panel = read("ports/android/app/src/main/java/dev/shivampingale/vcport/OtgVolumePanel.kt")
        ios = read("ports/ios/VCPort/ContentView+Open.swift")
        for needle in (
            "Scan USB disks",
            "Select USB device",
            "usb_select_prompt",
            "usb_device",
            "Nothing auto-mounts.",
            "Needs permission",
        ):
            self.assertIn(needle, panel, needle)
        self.assertIn("Whole-disk Open is not on iPhone.", ios)
        self.assertNotIn("usb_select_prompt", ios)

    def test_files_app_is_opt_in_and_android_only(self) -> None:
        panel = read("ports/android/app/src/main/java/dev/shivampingale/vcport/OtgVolumePanel.kt")
        provider = read(
            "ports/android/app/src/main/java/dev/shivampingale/vcport/VolumeDocumentsProvider.kt"
        )
        ios = read("ports/ios/VCPort/ContentView.swift")
        self.assertIn("Show unlocked volumes in the Files app", panel)
        self.assertIn("files_app_share", panel)
        self.assertIn("shareWithFiles", provider)
        self.assertIn("false", provider)
        self.assertIn("This app has none.", ios)


class PreviewPanicAndSaveBlockTests(unittest.TestCase):
    def test_preview_stays_inside_the_app(self) -> None:
        android = read("ports/android/app/src/main/java/dev/shivampingale/vcport/InAppPreview.kt")
        ios = read("ports/ios/VCPort/InAppPreview.swift")
        self.assertIn("in_app_preview", android)
        self.assertIn("Could not decode this image in-app.", android)
        self.assertIn("Could not decode this PDF in-app.", android)
        self.assertIn("Not VLC, Files, or another app.", ios)
        self.assertIn("Office and unknown binaries stay in the volume", ios)

    def test_panic_closes_without_write_back(self) -> None:
        android = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        ios = read("ports/ios/VCPort/ContentView.swift")
        for blob, name in ((android, "panicWipe"), (ios, "panicWipe")):
            body = function_body(blob, name)
            self.assertIn("requestCancel", body)
            self.assertNotIn("writeBack", body)
            self.assertNotIn("autoSave", body)
            self.assertNotIn("saveAllMounted", body)
            self.assertNotIn("saveAndCloseMounted", body)
        self.assertIn("closeMountedVolume()", function_body(android, "panicWipe"))
        self.assertIn("closeVolume()", function_body(ios, "panicWipe"))

    def test_leave_saves_before_it_clears(self) -> None:
        android = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        ios = read("ports/ios/VCPort/ContentView.swift")
        leave_a = function_body(android, "dismountOnLeave")
        leave_i = function_body(ios, "dismountOnLeave")
        self.assertIn("saveAllMountedSync()", leave_a)
        self.assertIn("saveAndCloseMounted", leave_i)
        self.assertIn("MountLeave.status", leave_a)
        self.assertIn("MountLeave.status", leave_i)
        self.assertIn("hadSource && !it.saved", leave_a)
        self.assertIn("!outcome.saved", leave_i)
        self.assertIn("flushVolume", android)
        self.assertIn("autoSaveSaveWarning", android)
        self.assertIn("autoSaveSaveWarning", ios)

    def test_self_test_and_offline_stay_in_front(self) -> None:
        header = read("ports/shared/vc_mobile.h")
        android = read_android_ui()
        ios = read_ios_ui()
        self.assertIn("VC_ERR_SELFTEST", header)
        self.assertIn("This build has no network.", android)
        self.assertIn("Stay offline.", ios)
        self.assertIn("Nothing runs out of sight.", ios)
        self.assertIn("WorkOverlay", android)


class HumanAndMachineFailureTests(unittest.TestCase):
    def test_leave_does_not_call_a_failed_write_a_clean_dismount(self) -> None:
        self.assertIsNone(leave_status(False, [], False))
        self.assertIsNone(leave_status(True, ["notes.hc"], True))
        calm = leave_status(True, [], False)
        self.assertEqual(calm, KEPT)
        one = leave_status(True, ["notes.hc"], False)
        assert one is not None
        self.assertIn("Not saved: notes.hc. Original unchanged.", one)
        self.assertNotEqual(one, KEPT)
        many = leave_status(True, ["a.hc", "b.hc"], False)
        assert many is not None
        self.assertIn("Not saved: 2. Original unchanged.", many)
        self.assertNotIn("Saved.", many)

    def test_both_phones_keep_the_failure_in_the_status(self) -> None:
        android = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MountLeave.kt")
        ios = read("ports/ios/VCPort/MountLeave.swift")
        for blob in (android, ios):
            self.assertIn("Create form kept", blob)
            self.assertNotIn("generated password", blob)
            self.assertIn("statusAlreadyPanic", blob)
        self.assertNotIn("MountLeave.status", function_body(
            read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt"),
            "panicWipe",
        ))
        self.assertNotIn("MountLeave.status", function_body(
            read("ports/ios/VCPort/ContentView.swift"),
            "panicWipe",
        ))

    def test_a_short_write_is_not_success_and_the_original_is_replaced_last(self) -> None:
        copy = function_body(
            read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt"),
            "copyFileToUri",
        )
        self.assertIn("expected <= 0L || copied != expected", copy)
        self.assertLess(copy.index("input.copyTo"), copy.index("copied != expected"))
        wipe = function_body(
            read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt"),
            "saveMountedContainer",
        )
        self.assertLess(wipe.index("writeBackMountedCache"), wipe.index("wipeFile"))
        ios = function_body(read("ports/ios/VCPort/ContentView.swift"), "writeBackMountedCache")
        self.assertLess(ios.index("copyItem"), ios.index("staged != expected"))
        self.assertLess(ios.index("staged != expected"), ios.index("replaceItemAt"))
        self.assertNotIn("removeItem(at: source)", ios)
        self.assertIn(".vcport-new", ios)

    def test_a_write_is_checked_before_it_is_treated_as_finished(self) -> None:
        self.assertFalse(write_accepted(0, 0))
        self.assertFalse(write_accepted(99, 100))
        self.assertFalse(write_accepted(101, 100))
        self.assertFalse(write_accepted(-1, 100))
        self.assertTrue(write_accepted(100, 100))
        self.assertFalse(write_back_allowed(True, True, True))
        self.assertFalse(write_back_allowed(False, False, True))
        self.assertFalse(write_back_allowed(False, True, False))
        self.assertTrue(write_back_allowed(False, True, True))
        self.assertTrue(path_rejected(""))
        self.assertTrue(path_rejected("/proc/self/fd/4"))
        self.assertFalse(path_rejected("/cache/volume.hc"))
        calm = lock_status("Dismounted. Session cleared.", [])
        self.assertEqual(calm, "Dismounted. Session cleared.")
        failed = lock_status("Dismounted. Session cleared.", ["notes.hc"])
        self.assertIn("Not saved: notes.hc. Original unchanged.", failed)
        self.assertNotEqual(failed, calm)
        self.assertIn("Not saved: 2. Original unchanged.", lock_status("Idle.", ["a.hc", "b.hc"]))
        self.assertEqual(
            dismount_status("notes.hc", 0, True, False),
            "Dismounted notes.hc. Not saved.",
        )
        self.assertEqual(
            dismount_status("notes.hc", 1, True, True),
            "Saved. Dismounted notes.hc. 1 still open.",
        )
        self.assertEqual(dismount_status("notes.hc", 0, False, False), "Dismounted notes.hc.")
        android = read("ports/android/app/src/main/java/dev/shivampingale/vcport/MainActivity.kt")
        ios = read("ports/ios/VCPort/ContentView.swift")
        for blob, name in ((android, "lockSession"), (ios, "lockSession")):
            body = function_body(blob, name)
            self.assertIn("MountReport.lockStatus", body)
            self.assertLess(body.index("save"), body.index("MountReport.lockStatus"))
            self.assertLess(body.index("MountReport.lockStatus"), body.index("completeSessionReset"))
        for blob, name in ((android, "panicWipe"), (ios, "panicWipe")):
            self.assertNotIn("MountReport", function_body(blob, name))
        back = function_body(android, "shouldWriteBack")
        self.assertIn("vol.readOnly", back)
        self.assertIn("!nativePathIsInternal", back)
        self.assertIn("uri.scheme == \"content\"", back)
        self.assertNotIn("WriteBackGate", back)
        ios_back = function_body(ios, "shouldWriteBackMounted")
        self.assertIn("vol.readOnly", ios_back)
        self.assertIn("mountedCachePath", ios_back)
        self.assertIn("sourceURL != vol.url", ios_back)
        usable = function_body(android, "containerPathUsable")
        self.assertIn('path.isEmpty() || path.startsWith("/proc/self/fd/")', usable)
        self.assertIn("Not saved:", read("ports/android/app/src/main/java/dev/shivampingale/vcport/MountReport.kt"))
        self.assertIn("Not saved:", read("ports/ios/VCPort/MountReport.swift"))
        self.assertNotIn("WriteAccept", android)
        self.assertNotIn("WriteAccept", ios)


if __name__ == "__main__":
    unittest.main()
