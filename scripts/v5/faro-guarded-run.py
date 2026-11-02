#!/usr/bin/env python3
"""FARO Auto/Noche shared guarded runner. Fail closed. Never starts on import."""
import argparse
import os
import pathlib
import secrets
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
WRITER = ROOT / "scripts/v5/faro-writer-cas.py"
PREFLIGHT = ROOT / "scripts/v5/faro-auto-v2-preflight"
ALLOWED = {"auto", "night-a", "night-b", "manual"}


def run(cmd, env=None):
    return subprocess.run(cmd, cwd=ROOT, env=env, check=True)


def main():
    p = argparse.ArgumentParser(description="FARO guarded execution; no implicit permissions")
    p.add_argument("--mode", required=True, choices=sorted(ALLOWED))
    p.add_argument("--owner", required=True)
    p.add_argument("--target-head", required=True)
    p.add_argument("--approved", action="store_true")
    p.add_argument("command", nargs=argparse.REMAINDER)
    a = p.parse_args()
    if not a.approved or os.environ.get("FARO_V5_EXECUTION_AUTHORIZED") != "YES":
        raise RuntimeError("human scope authorization missing")
    if not a.command or a.command[0] != "--" or len(a.command) < 2:
        raise RuntimeError("explicit command required after --")
    if a.mode != "manual" and os.environ.get("FARO_V5_AUTOMATION_AUTHORIZED") != "YES":
        raise RuntimeError("automation not authorized")
    if os.environ.get("FARO_V5_WRITER_COORDINATION_READY") != "YES":
        raise RuntimeError("shared writer coordination not certified")
    run(["bash", str(PREFLIGHT)])
    run([sys.executable, str(ROOT / "scripts/v5/faro-auto-v2-resource.py")])
    # All modes use the same remote writer ref. Never auto-takeover an occupied lock.
    token = secrets.token_hex(32)
    env = {**os.environ, "FARO_WRITER_TOKEN": token}
    cmd = [sys.executable, str(WRITER)]
    base = ["--owner", a.owner, "--target-head", a.target_head]
    acquired = False
    try:
        run(cmd + ["acquire"] + base, env=env)
        acquired = True
        run(cmd + ["verify"] + base, env=env)
        # Command must be separately reviewed. This guard does not sandbox it.
        run(a.command[1:], env={k:v for k,v in os.environ.items() if k != "FARO_WRITER_TOKEN"})
    finally:
        if acquired:
            # A failed release must be visible, not silently ignored.
            run(cmd + ["release"] + base, env=env)


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError, OSError) as exc:
        print("STOP: " + str(exc), file=sys.stderr)
        sys.exit(20)
