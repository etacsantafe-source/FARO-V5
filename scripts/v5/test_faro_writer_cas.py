#!/usr/bin/env python3
"""Non-network unit checks for FARO writer v2. Does not write GitHub."""
import importlib.util
import io
import json
import os
import pathlib
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch

PATH = pathlib.Path(__file__).with_name("faro-writer-cas.py")
spec = importlib.util.spec_from_file_location("writer", PATH)
writer = importlib.util.module_from_spec(spec)
spec.loader.exec_module(writer)


class WriterSafety(unittest.TestCase):
    def run_action(self, action, *, owner="auto-owner", token="a" * 32, target="a" * 40,
                   remote_sha=writer.ZERO, state=None, head=None, dirty=""):
        args = ["writer", action, "--owner", owner, "--target-head", target,
                "--target-branch", "work/faro-auto-v2-integration-20261102"]
        with patch("sys.argv", args), patch.dict(os.environ, {"FARO_WRITER_TOKEN": token}), \
             patch.object(writer, "remote_ok"), patch.object(writer, "verify_target"), \
             patch.object(writer, "ref", return_value=remote_sha), \
             patch.object(writer, "record", return_value=state), \
             patch.object(writer, "head", return_value=head or target), \
             patch.object(writer, "git", return_value=dirty), \
             patch.object(writer, "publish") as publish, redirect_stdout(io.StringIO()):
            writer.main()
            return publish.call_args

    def test_occupied_lock_blocks_new_owner(self):
        with self.assertRaisesRegex(writer.Stop, "occupied"):
            self.run_action("acquire", remote_sha="b" * 40, state={"status": "HELD"})

    def test_stale_held_lock_still_blocks(self):
        with self.assertRaisesRegex(writer.Stop, "occupied"):
            self.run_action("acquire", remote_sha="b" * 40,
                            state={"status": "HELD", "timestamp": 1})

    def test_acquire_requires_head_match(self):
        with self.assertRaisesRegex(writer.Stop, "HEAD mismatch"):
            self.run_action("acquire", head="b" * 40)

    def test_acquire_requires_clean_worktree(self):
        with self.assertRaisesRegex(writer.Stop, "dirty worktree"):
            self.run_action("acquire", dirty=" M source.txt")

    def test_new_lock_uses_hashed_token(self):
        call = self.run_action("acquire")
        value = call.args[1]
        self.assertNotIn("token", value)
        self.assertEqual(len(value["token_hash"]), 64)
        self.assertEqual(value["status"], "HELD")

    def test_wrong_owner_cannot_release(self):
        with self.assertRaisesRegex(writer.Stop, "identity mismatch"):
            self.run_action("release", state={"status": "HELD", "owner": "other",
                             "token_hash": "x" * 64}, remote_sha="b" * 40)

    def test_wrong_token_cannot_release(self):
        with self.assertRaisesRegex(writer.Stop, "identity mismatch"):
            self.run_action("release", state={"status": "HELD", "owner": "auto-owner",
                             "token_hash": "x" * 64}, remote_sha="b" * 40)

    def test_release_only_for_owner(self):
        import hashlib
        state = {"status": "HELD", "owner": "auto-owner", "target_branch": "work/faro-auto-v2-integration-20261102", "token_hash":
                 hashlib.sha256(("a" * 32).encode()).hexdigest(), "epoch": 2}
        call = self.run_action("release", state=state, remote_sha="b" * 40)
        self.assertEqual(call.args[1]["status"], "RELEASED")

    def test_cli_token_rejected(self):
        with patch("sys.argv", ["writer", "status", "--token", "a" * 32]):
            with self.assertRaisesRegex(writer.Stop, "CLI argument disabled"):
                writer.main()


if __name__ == "__main__":
    unittest.main()
