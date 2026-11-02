#!/usr/bin/env python3
"""Non-mutating FARO Auto/Noche safety readiness check."""
import json
import pathlib
import subprocess
import sys

root = pathlib.Path(__file__).resolve().parents[2]
required = [
    "scripts/v5/faro-writer-cas.py",
    "scripts/v5/faro-guarded-run.py",
    "scripts/v5/faro-auto-v2-preflight",
    "docs/s2-night/RUN_STATE.json",
    "docs/v5/FARO_CONTINUITY_POINTER.json",
]
missing = [x for x in required if not (root / x).is_file()]
if missing:
    print("STOP missing:", missing)
    sys.exit(20)
s = json.loads((root / "docs/s2-night/RUN_STATE.json").read_text())
p = json.loads((root / "docs/v5/FARO_CONTINUITY_POINTER.json").read_text())
if not (s["phase"] == "S2_CLOSED" and s["certified"] == s["total"] == 8
        and not s["s3_open"] and not s["s4_open"] and not s["v2_active_enabled"]
        and not p["automation"]["activation_authorized"]):
    print("STOP phase or authorization mismatch")
    sys.exit(20)
print("PASS: S2 closed, app V2 off, S3/S4 off, automation unauthorized")
print("PASS: shared writer and runner files present")
print("BLOCKED: daemon and queue not ported; live CAS contention not tested")
print("BLOCKED: Noche schedules not wired to guarded runner")
print("BLOCKED: writer must be reverified for each mutating step")
print("READINESS: NOT READY FOR AUTONOMOUS EXECUTION")
