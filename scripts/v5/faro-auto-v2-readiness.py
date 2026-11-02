#!/usr/bin/env python3
"""Non-mutating operational readiness. A PASS of this file is NEVER authorization."""
import json
import pathlib
import sys

root = pathlib.Path(__file__).resolve().parents[2]
required = [
    "scripts/v5/faro-writer-cas.py",
    "scripts/v5/faro-guarded-run.py",
    "scripts/v5/faro-auto-v2-preflight",
    "scripts/v5/faro-auto-v2-queue.py",
    "scripts/v5/faro-auto-v2-once",
    "scripts/v5/faro-s3-verify.py",
    "docs/s2-night/RUN_STATE.json",
    "docs/v5/FARO_CONTINUITY_POINTER.json",
]
missing = [name for name in required if not (root / name).is_file()]
if missing:
    print("STOP missing:", missing)
    sys.exit(20)
s = json.loads((root / "docs/s2-night/RUN_STATE.json").read_text())
p = json.loads((root / "docs/v5/FARO_CONTINUITY_POINTER.json").read_text())
if not (s["phase"] == "S2_CLOSED" and s["certified"] == s["total"] == 8
        and not s["v2_active_enabled"] and not s["s4_open"]
        and not p["app_v2_enabled"] and not p["s4_open"]):
    print("STOP: S2 closure or app/S4 invariant mismatch")
    sys.exit(20)
active = bool(p["s3_open"] and p["automation"]["activation_authorized"])
print("PASS: S2 8/8 and app V2 OFF; readiness scripts present")
print("S3/NOCHE DURABLE AUTHORIZATION:", "YES" if active else "NO")
print("NOTE: local same-host queue and GitHub cross-host queue have DIFFERENT transports")
print("NOTE: Python runner is restricted to read-only checked commands")
print("NOTE: ChatGPT Noche must use GitHub writer CAS and durable checkpoint per turn")
print("READINESS: NO ACTIVATION IMPLIED")
