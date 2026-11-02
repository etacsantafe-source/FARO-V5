#!/usr/bin/env python3
"""Safety tests for guarded Auto/Noche runner; no network or execution."""
import importlib.util
import os
import pathlib
import sys
import unittest
from unittest.mock import patch

p = pathlib.Path(__file__).with_name("faro-guarded-run.py")
spec = importlib.util.spec_from_file_location("guard", p)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)


class GuardTests(unittest.TestCase):
    def invoke(self, mode, flags=None, env=None):
        args = ["guard", "--mode", mode, "--owner", "night-a-001",
                "--target-head", "a" * 40] + (flags or []) + ["--", sys.executable, "scripts/v5/faro-auto-v2-readiness.py"]
        with patch.object(sys, "argv", args), patch.dict(os.environ, env or {}, clear=True):
            mod.main()

    def test_no_approval_blocks(self):
        with patch.object(mod, "run") as run:
            with self.assertRaisesRegex(RuntimeError, "human scope"):
                self.invoke("auto")
            run.assert_not_called()

    def test_no_env_permission_blocks(self):
        with patch.object(mod, "run") as run:
            with self.assertRaisesRegex(RuntimeError, "human scope"):
                self.invoke("night-a", ["--approved"])
            run.assert_not_called()

    def test_automation_needs_separate_authorization(self):
        with patch.object(mod, "run") as run:
            with self.assertRaisesRegex(RuntimeError, "automation not authorized"):
                self.invoke("night-b", ["--approved"],
                            {"FARO_V5_EXECUTION_AUTHORIZED": "YES"})
            run.assert_not_called()

    def test_uncertified_writer_blocks(self):
        with patch.object(mod, "run") as run:
            with self.assertRaisesRegex(RuntimeError, "not certified"):
                self.invoke("auto", ["--approved"],
                            {"FARO_V5_EXECUTION_AUTHORIZED": "YES",
                             "FARO_V5_AUTOMATION_AUTHORIZED": "YES"})
            run.assert_not_called()

    def test_acquire_failure_never_runs_task(self):
        from subprocess import CalledProcessError
        with patch.object(mod, "run", side_effect=[None, None, CalledProcessError(20, "acquire")]) as run:
            with self.assertRaises(CalledProcessError):
                self.invoke("night-a", ["--approved"],
                            {"FARO_V5_EXECUTION_AUTHORIZED": "YES",
                             "FARO_V5_AUTOMATION_AUTHORIZED": "YES",
                             "FARO_V5_WRITER_COORDINATION_READY": "YES"})
            self.assertEqual(run.call_count, 3)

    def test_verified_writer_executes_then_releases(self):
        with patch.object(mod, "run") as run:
            self.invoke("manual", ["--approved"],
                        {"FARO_V5_EXECUTION_AUTHORIZED": "YES",
                         "FARO_V5_WRITER_COORDINATION_READY": "YES"})
            self.assertEqual(run.call_count, 6)
            self.assertIn("acquire", run.call_args_list[2].args[0])
            self.assertIn("verify", run.call_args_list[3].args[0])
            self.assertIn("release", run.call_args_list[5].args[0])


if __name__ == "__main__":
    unittest.main()
