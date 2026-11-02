#!/usr/bin/env python3
"""Disposable queue+runner smoke; uses mock writer and no remote writes."""
import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from unittest.mock import patch

def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m

root = Path(__file__).resolve().parents[2]
qmod = load("queue_mod", root / "scripts/v5/faro-auto-v2-queue.py")
gmod = load("guard_mod", root / "scripts/v5/faro-guarded-run.py")
with tempfile.TemporaryDirectory() as tmp:
    q = Path(tmp) / "queue"
    q.mkdir(mode=0o700)
    task = {"id": "smoke-001", "mode": "auto", "target_head": "a" * 40,
            "target_branch": "work/faro-auto-v2-integration-20261102",
            "command": [sys.executable, "scripts/v5/faro-auto-v2-readiness.py"]}
    qmod.atomic(q / "pending/smoke-001.json", task)
    env = {"FARO_V5_QUEUE_ROOT": str(q), "FARO_V5_EXECUTION_AUTHORIZED": "YES",
           "FARO_V5_AUTOMATION_AUTHORIZED": "YES",
           "FARO_V5_WRITER_COORDINATION_READY": "YES",
           "FARO_V5_SINGLE_HOST_QUEUE_ACK": "YES"}
    state = {"owner": None, "acquires": 0, "releases": 0, "tasks": 0}
    def mocked(cmd, env=None):
        if "faro-writer-cas.py" in " ".join(cmd):
            if "acquire" in cmd:
                assert state["owner"] is None
                state["owner"] = "auto"
                state["acquires"] += 1
            elif "verify" in cmd:
                assert state["owner"] == "auto"
            elif "release" in cmd:
                assert state["owner"] == "auto"
                state["owner"] = None
                state["releases"] += 1
        elif any(str(x).endswith("faro-auto-v2-readiness.py") for x in cmd):
            state["tasks"] += 1
    def dispatch(cmd, **kwargs):
        with patch.object(sys, "argv", ["guard", *cmd[2:]]), \
             patch.dict(os.environ, env, clear=True), \
             patch.object(gmod, "run", side_effect=mocked):
            gmod.main()
        return subprocess.CompletedProcess(cmd, 0)
    with patch.dict(os.environ, env, clear=True), \
         patch.object(sys, "argv", ["queue", "once", "--mode", "auto", "--owner", "smoke-auto"]), \
         patch.object(qmod.subprocess, "run", side_effect=dispatch):
        qmod.main()
    assert state == {"owner": None, "acquires": 1, "releases": 1, "tasks": 1}
    assert (q / "done/smoke-001.json").exists()
    assert qmod.read(q / "state.json", {})["status"] == "IDLE"
    qmod.atomic(q / "state.json", {"status": "BLOCKED", "task": "uncertain"})
    with patch.dict(os.environ, env, clear=True), \
         patch.object(sys, "argv", ["queue", "once", "--mode", "auto", "--owner", "smoke-auto"]):
        try:
            qmod.main()
        except RuntimeError as err:
            assert "manual recovery" in str(err)
        else:
            raise AssertionError("blocked queue resumed")
    print("SMOKE PASS: durable external queue, one execution, uncertain replay STOP")
