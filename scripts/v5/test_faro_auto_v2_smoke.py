#!/usr/bin/env python3
"""Disposable integration smoke: exercises real queue and runner with isolated stubs.
No FARO application mutation, no GitHub writes, no scheduled automation.
"""
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from unittest.mock import patch


def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


root = Path(__file__).resolve().parents[2]
queue_mod = load("queue_mod", root / "scripts/v5/faro-auto-v2-queue.py")
guard_mod = load("guard_mod", root / "scripts/v5/faro-guarded-run.py")

with tempfile.TemporaryDirectory(prefix="faro-v2-smoke-") as tmp:
    tmp = Path(tmp)
    queue = tmp / "queue"
    queue_mod.QUEUE = queue
    head = "a" * 40
    task = {"id": "smoke-001", "mode": "auto", "target_head": head,
            "command": [sys.executable, "scripts/v5/faro-auto-v2-readiness.py"]}
    queue_mod.atomic(queue / "pending/smoke-001.json", task)
    env = {"FARO_V5_EXECUTION_AUTHORIZED": "YES",
           "FARO_V5_AUTOMATION_AUTHORIZED": "YES",
           "FARO_V5_WRITER_COORDINATION_READY": "YES"}
    writer_state = {"owner": None, "acquires": 0, "releases": 0, "tasks": 0}

    def mocked_guard_run(cmd, env=None):
        if "faro-writer-cas.py" in " ".join(cmd):
            if "acquire" in cmd:
                if writer_state["owner"] is not None:
                    raise subprocess.CalledProcessError(20, cmd)
                writer_state["owner"] = "auto"
                writer_state["acquires"] += 1
            elif "verify" in cmd:
                assert writer_state["owner"] == "auto"
            elif "release" in cmd:
                assert writer_state["owner"] == "auto"
                writer_state["owner"] = None
                writer_state["releases"] += 1
        elif cmd[0] == sys.executable and "faro-auto-v2-readiness.py" in cmd:
            writer_state["tasks"] += 1

    def execute_guard(cmd, **kwargs):
        with patch.object(sys, "argv", ["guard", *cmd[2:]]), \
             patch.dict(os.environ, env, clear=True), \
             patch.object(guard_mod, "run", side_effect=mocked_guard_run):
            guard_mod.main()
        return subprocess.CompletedProcess(cmd, 0)

    with patch.dict(os.environ, env, clear=True), \
         patch.object(sys, "argv", ["queue", "once", "--mode", "auto", "--owner", "smoke-auto"]), \
         patch.object(queue_mod.subprocess, "run", side_effect=execute_guard):
        queue_mod.main()
    assert writer_state == {"owner": None, "acquires": 1, "releases": 1, "tasks": 1}, writer_state
    assert not list((queue / "pending").glob("*.json"))
    assert (queue / "done/smoke-001.json").is_file()
    assert queue_mod.read(queue / "state.json", {})["status"] == "IDLE"
    print("SMOKE PASS: queued -> acquired -> verified -> executed -> released -> done")
    print("ISOLATED: writer Git calls stubbed; no production writes")
