#!/usr/bin/env python3
"""Unit-level fail-closed checks for queue path, writer remote target and task ids."""
import importlib.util
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]

def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

queue = load("queue", ROOT / "scripts/v5/faro-auto-v2-queue.py")
writer = load("writer", ROOT / "scripts/v5/faro-writer-cas.py")

class Hardening(unittest.TestCase):
    def test_queue_in_repo_refused(self):
        with patch.dict(os.environ, {"FARO_V5_QUEUE_ROOT": str(ROOT / "scripts/v5/automation-v2")}):
            with self.assertRaisesRegex(RuntimeError, "outside Git"):
                queue.root_path()

    def test_queue_requires_explicit_path(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaisesRegex(RuntimeError, "absolute durable"):
                queue.root_path()

    def test_enqueue_without_approval_rejected(self):
        with tempfile.TemporaryDirectory() as tmp, \
             patch.dict(os.environ, {"FARO_V5_QUEUE_ROOT": str(Path(tmp) / "private")}, clear=True), \
             patch.object(sys, "argv", ["queue", "enqueue", "--task-id", "task01"]):
            with self.assertRaisesRegex(RuntimeError, "enqueue not authorized"):
                queue.main()

    def test_unreviewed_command_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "unapproved queue command"):
            queue.command_ok(["python3", "../delete-all.py"])

    def test_remote_target_changed_rejected(self):
        with patch.object(writer, "git", return_value="b" * 40 + "\trefs/heads/work/test"):
            with self.assertRaisesRegex(writer.Stop, "remote target HEAD mismatch"):
                writer.verify_target("work/test-branch", "a" * 40)

    def test_remote_target_match_accepted(self):
        with patch.object(writer, "git", return_value="a" * 40 + "\trefs/heads/work/test"):
            writer.verify_target("work/test-branch", "a" * 40)

    def test_invalid_target_branch_rejected(self):
        with self.assertRaisesRegex(writer.Stop, "invalid isolated target"):
            writer.verify_target("../main", "a" * 40)

if __name__ == "__main__":
    unittest.main()
