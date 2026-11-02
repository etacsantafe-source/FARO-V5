#!/usr/bin/env python3
"""FARO V5 Auto/Noche V2 queue. Read-only status by default; no implicit worker."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
QUEUE = ROOT / "scripts/v5/automation-v2"
VALID = {"auto", "night-a", "night-b", "manual"}


def read(path, default):
    return json.loads(path.read_text()) if path.exists() else default


def atomic(path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + "." + uuid.uuid4().hex + ".tmp")
    try:
        with tmp.open("x") as f:
            json.dump(obj, f, sort_keys=True, indent=2)
            f.write("\n")
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, path)
    finally:
        tmp.unlink(missing_ok=True)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("action", choices=["status", "enqueue", "once"])
    p.add_argument("--mode", choices=sorted(VALID), default="auto")
    p.add_argument("--task-id")
    p.add_argument("--target-head")
    p.add_argument("--owner")
    p.add_argument("--command", nargs="+")
    args = p.parse_args()
    if args.action == "status":
        print(json.dumps(read(QUEUE / "state.json", {"status": "IDLE"}),
                         sort_keys=True))
        return
    if args.action == "enqueue":
        if os.environ.get("FARO_V5_QUEUE_WRITE_AUTHORIZED") != "YES":
            raise RuntimeError("queue enqueue not authorized")
        if not args.task_id or not args.target_head or not args.command:
            raise RuntimeError("task-id, target-head and command required")
        if not all(c.isalnum() or c in "-_." for c in args.task_id):
            raise RuntimeError("invalid task-id")
        target = QUEUE / "pending" / (args.task_id + ".json")
        if target.exists() or (QUEUE / "done" / (args.task_id + ".json")).exists():
            raise RuntimeError("duplicate task")
        atomic(target, {"id": args.task_id, "mode": args.mode,
                        "target_head": args.target_head, "command": args.command,
                        "created_at": int(time.time())})
        print("QUEUED", args.task_id)
        return
    if os.environ.get("FARO_V5_EXECUTION_AUTHORIZED") != "YES" or os.environ.get("FARO_V5_AUTOMATION_AUTHORIZED") != "YES":
        raise RuntimeError("automation disabled")
    if os.environ.get("FARO_V5_WRITER_COORDINATION_READY") != "YES":
        raise RuntimeError("shared writer not certified")
    tasks = sorted((QUEUE / "pending").glob("*.json")) if (QUEUE / "pending").exists() else []
    tasks = [t for t in tasks if read(t, {}).get("mode") == args.mode]
    if not tasks:
        print("IDLE")
        return
    task = read(tasks[0], {})
    if not args.owner:
        raise RuntimeError("owner required")
    cmd = [sys.executable, str(ROOT / "scripts/v5/faro-guarded-run.py"),
           "--mode", args.mode, "--owner", args.owner,
           "--target-head", task["target_head"], "--approved", "--", *task["command"]]
    atomic(QUEUE / "state.json", {"status": "RUNNING", "task": task["id"]})
    try:
        subprocess.run(cmd, cwd=ROOT, check=True, timeout=1200)
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
        atomic(QUEUE / "state.json", {"status": "BLOCKED", "task": task["id"]})
        raise
    done = QUEUE / "done" / tasks[0].name
    done.parent.mkdir(parents=True, exist_ok=True)
    os.replace(tasks[0], done)
    atomic(QUEUE / "state.json", {"status": "IDLE", "last_completed": task["id"]})
    print("DONE", task["id"])


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, subprocess.CalledProcessError,
            subprocess.TimeoutExpired, ValueError, KeyError) as e:
        print("STOP:", e, file=sys.stderr)
        sys.exit(20)
