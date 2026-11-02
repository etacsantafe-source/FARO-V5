#!/usr/bin/env python3
"""FARO V2 resource admission: fail closed when resources cannot be measured."""
import os
from pathlib import Path
import sys


def mem_available_mib(path="/proc/meminfo"):
    for line in Path(path).read_text().splitlines():
        if line.startswith("MemAvailable:"):
            return int(line.split()[1]) // 1024
    raise RuntimeError("MemAvailable unavailable")


def main():
    available = mem_available_mib()
    min_mib = int(os.environ.get("FARO_V5_MIN_AVAILABLE_MIB", "885"))
    if available < min_mib:
        raise RuntimeError(f"resource STOP: MemAvailable {available} MiB < {min_mib} MiB")
    print(f"RESOURCE_PASS: MemAvailable={available}MiB")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError) as exc:
        print("STOP:", exc, file=sys.stderr)
        sys.exit(20)
