#!/usr/bin/env python3
"""Integration test: real Git push CAS against temporary LOCAL bare remote. No GitHub writes."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


def git(cwd, *args, check=True):
    return subprocess.run(["git", "-C", str(cwd), *args], check=check,
                          capture_output=True, text=True)


class RealGitCASTests(unittest.TestCase):
    def test_two_contenders_one_wins_and_release_epoch(self):
        with tempfile.TemporaryDirectory() as d:
            d = Path(d)
            remote = d / "remote.git"
            git(d, "init", "--bare", str(remote))
            source = d / "source"
            git(d, "init", str(source))
            git(source, "config", "user.email", "test@example.invalid")
            git(source, "config", "user.name", "FARO CAS test")
            (source / "file").write_text("base")
            git(source, "add", ".")
            git(source, "commit", "-m", "base")
            git(source, "remote", "add", "origin", str(remote))
            git(source, "push", "origin", "HEAD:refs/heads/main")
            base = git(source, "rev-parse", "HEAD").stdout.strip()
            ref = "refs/heads/faro-v5-writer-lock"
            def commit(message):
                git(source, "commit", "--allow-empty", "-m", message)
                return git(source, "rev-parse", "HEAD").stdout.strip()
            a = commit("writer A acquire")
            b = commit("writer B acquire")
            first = git(source, "push", "origin",
                        f"--force-with-lease={ref}:{'0'*40}", f"{a}:{ref}", check=False)
            second = git(source, "push", "origin",
                         f"--force-with-lease={ref}:{'0'*40}", f"{b}:{ref}", check=False)
            self.assertEqual(first.returncode, 0)
            self.assertNotEqual(second.returncode, 0)
            self.assertEqual(git(source, "ls-remote", "origin", ref).stdout.split()[0], a)
            release = commit("writer A release")
            good = git(source, "push", "origin", f"--force-with-lease={ref}:{a}",
                       f"{release}:{ref}", check=False)
            self.assertEqual(good.returncode, 0)
            self.assertEqual(git(source, "ls-remote", "origin", ref).stdout.split()[0], release)
            stale = git(source, "push", "origin", f"--force-with-lease={ref}:{a}",
                        f"{b}:{ref}", check=False)
            self.assertNotEqual(stale.returncode, 0)


if __name__ == "__main__":
    unittest.main()
