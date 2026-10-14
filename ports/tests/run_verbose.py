#!/usr/bin/env python3
"""Run the host unittest modules and print a local allocation trace.

The trace lists where this process allocated memory. It stays on this
machine. It is not a phone leak scan and it is not uploaded.
"""

from __future__ import annotations

import sys
import tracemalloc
import unittest


def main(argv: list[str]) -> int:
    tracemalloc.start(25)
    program = unittest.main(module=None, argv=argv, exit=False, verbosity=2)
    snapshot = tracemalloc.take_snapshot()
    print("")
    print("======== python allocation trace (top 20, local, not uploaded) ========")
    for stat in snapshot.statistics("lineno")[:20]:
        print(stat)
    print("======== end allocation trace ========")
    return 0 if program.result.wasSuccessful() else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv))
