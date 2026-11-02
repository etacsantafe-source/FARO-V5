#!/usr/bin/env python3
"""Fail-closed same-host queue. Global GitHub writer is separately required."""
import argparse
from contextlib import contextmanager
import fcntl
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import time
import uuid

ROOT = Path(__file__).resolve().parents[2]
MODES = {"auto", "night-a", "night-b", "manual"}
ALLOWED = {"scripts/v5/faro-auto-v2-readiness.py", "scripts/v5/faro-s3-verify.py"}


def root_path():
    raw = os.environ.get("FARO_V5_QUEUE_ROOT", "")
    if not raw or not os.path.isabs(raw):
        raise RuntimeError("FARO_V5_QUEUE_ROOT must be an absolute durable path")
    p = Path(raw)
    if p.is_symlink():
        raise RuntimeError("queue root symlink rejected")
    p = p.resolve()
    if p == ROOT.resolve() or ROOT.resolve() in p.parents:
        raise RuntimeError("queue root must be outside Git worktree")
    if p.exists() and (not p.is_dir() or stat.S_IMODE(p.stat().st_mode) & 0o077):
        raise RuntimeError("queue root must be a private directory (0700)")
    return p


def read(path, default):
    return json.loads(path.read_text()) if path.exists() else default


def atomic(path, obj):
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    tmp = path.with_name(path.name + "." + uuid.uuid4().hex + ".tmp")
    try:
        with tmp.open("x") as f:
            os.chmod(tmp, 0o600)
            json.dump(obj, f, sort_keys=True, indent=2)
            f.write("\n")
            f.flush()
            os.fsync(f.fileno())
        os.replace(tmp, path)
        fd = os.open(str(path.parent), os.O_RDONLY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)
    finally:
        tmp.unlink(missing_ok=True)


@contextmanager
def exclusive(root):
    root.mkdir(mode=0o700, parents=True, exist_ok=True)
    if stat.S_IMODE(root.stat().st_mode) & 0o077:
        raise RuntimeError("queue root is not private")
    lock = root / ".queue.lock"
    if lock.is_symlink():
        raise RuntimeError("invalid queue lock")
    with lock.open("a+") as f:
        os.chmod(lock, 0o600)
        fcntl.flock(f, fcntl.LOCK_EX | fcntl.LOCK_NB)
        try:
            yield
        finally:
            fcntl.flock(f, fcntl.LOCK_UN)


def command_ok(command):
    if (len(command or []) != 2 or command[0] not in (sys.executable, "python3")
            or command[1] not in ALLOWED):
        raise RuntimeError("unapproved queue command")
    return command


def main():
    p = argparse.ArgumentParser()
    p.add_argument("action", choices=["status", "enqueue", "once"])
    p.add_argument("--mode", choices=sorted(MODES), default="auto")
    p.add_argument("--task-id")
    p.add_argument("--target-head")
    p.add_argument("--target-branch")
    p.add_argument("--owner")
    p.add_argument("--command", nargs="+")
    a = p.parse_args()
    root = root_path()
    if a.action == "status":
        print(json.dumps(read(root / "state.json", {"status": "IDLE"}), sort_keys=True))
        return
    if a.action == "enqueue":
        if os.environ.get("FARO_V5_QUEUE_WRITE_AUTHORIZED") != "YES":
            raise RuntimeError("enqueue not authorized")
        if not (a.task_id and a.target_head and a.target_branch):
            raise RuntimeError("missing queue task identity")
        if not re.fullmatch(r"[A-Za-z0-9_.-]{1,80}", a.task_id) or a.task_id in (".", ".."):
            raise RuntimeError("invalid task id")
        if not re.fullmatch(r"[0-9a-f]{40}", a.target_head):
            raise RuntimeError("invalid SHA")
        if not re.fullmatch(r"work/[A-Za-z0-9_./-]{6,150}", a.target_branch) or ".." in a.target_branch:
            raise RuntimeError("invalid branch")
        command_ok(a.command)
        with exclusive(root):
            if read(root / "state.json", {}).get("status") in ("RUNNING", "BLOCKED"):
                raise RuntimeError("unresolved task; manual recovery required")
            target = root / "pending" / (a.task_id + ".json")
            if target.exists() or (root / "done" / target.name).exists():
                raise RuntimeError("duplicate task")
            atomic(target, {"id": a.task_id, "mode": a.mode, "target_head": a.target_head,
                            "target_branch": a.target_branch, "command": a.command,
                            "created_at": int(time.time())})
            print("QUEUED", a.task_id)
        return
    if os.environ.get("FARO_V5_EXECUTION_AUTHORIZED") != "YES" or os.environ.get("FARO_V5_AUTOMATION_AUTHORIZED") != "YES":
        raise RuntimeError("execution not authorized")
    if os.environ.get("FARO_V5_WRITER_COORDINATION_READY") != "YES":
        raise RuntimeError("writer coordination not certified")
    if os.environ.get("FARO_V5_SINGLE_HOST_QUEUE_ACK") != "YES":
        raise RuntimeError("single-host sharing not acknowledged")
    with exclusive(root):
        state = read(root / "state.json", {"status": "IDLE"})
        if state.get("status") in ("RUNNING", "BLOCKED"):
            raise RuntimeError("previous task uncertain; manual recovery required")
        tasks = sorted((root / "pending").glob("*.json")) if (root / "pending").exists() else []
        tasks = [t for t in tasks if read(t, {}).get("mode") == a.mode]
        if not tasks:
            print("IDLE")
            return
        if not a.owner:
            raise RuntimeError("owner required")
        task = read(tasks[0], {})
        command_ok(task["command"])
        cmd = [sys.executable, str(ROOT / "scripts/v5/faro-guarded-run.py"),
               "--mode", a.mode, "--owner", a.owner, "--target-head", task["target_head"],
               "--target-branch", task["target_branch"], "--approved", "--", *task["command"]]
        atomic(root / "state.json", {"status": "RUNNING", "task": task["id"]})
        try:
            subprocess.run(cmd, cwd=ROOT, check=True, timeout=1200)
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
            atomic(root / "state.json", {"status": "BLOCKED", "task": task["id"],
                                         "reason": "effect uncertain; no automatic replay"})
            raise
        done = root / "done" / tasks[0].name
        done.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        os.replace(tasks[0], done)
        atomic(root / "state.json", {"status": "IDLE", "last_completed": task["id"]})
        print("DONE", task["id"])


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError, KeyError, BlockingIOError,
            subprocess.CalledProcessError, subprocess.TimeoutExpired) as exc:
        print("STOP:", exc, file=sys.stderr)
        sys.exit(20)
