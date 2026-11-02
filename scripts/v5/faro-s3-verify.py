#!/usr/bin/env python3
"""Bounded verification only. Never deploy, mutate sources, or run shell commands."""
import json
from pathlib import Path
import subprocess
import sys

root = Path(__file__).resolve().parents[2]
state = json.loads((root / "docs/s2-night/RUN_STATE.json").read_text())
if state.get("phase") != "S2_CLOSED" or state.get("certified") != 8:
    raise SystemExit("STOP S2 closure not established")
if state.get("v2_active_enabled") or state.get("s4_open"):
    raise SystemExit("STOP V2/S4 invariant")
for script in ("test_faro_writer_cas.py", "test_faro_guarded_run.py",
               "test_faro_writer_git_integration.py"):
    subprocess.run([sys.executable, "scripts/v5/" + script], cwd=root,
                   check=True, timeout=180)
print("S3 VERIFY PASS: bounded non-mutating tooling tests")
