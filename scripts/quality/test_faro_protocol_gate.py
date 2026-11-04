#!/usr/bin/env python3
"""Synthetic Git-history tests: no FARO V5 product code, internet, or APK."""
import importlib.util
import json
import pathlib
import subprocess
import tempfile
import unittest

HERE = pathlib.Path(__file__).parent
spec = importlib.util.spec_from_file_location("faro_gate", HERE / "faro-protocol-gate.py")
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


def g(root, *args):
    p = subprocess.run(["git", "-C", str(root), *args], text=True,
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if p.returncode:
        raise RuntimeError(p.stderr)
    return p.stdout.strip()


class ProtocolHistoryGateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.r = pathlib.Path(self.tmp.name)
        g(self.r, "init", "-q", "-b", "main")
        g(self.r, "config", "user.name", "FARO Gate Fixture")
        g(self.r, "config", "user.email", "gate@example.invalid")
        self.audit = self.r / "docs/v5/audit.md"
        self.audit.parent.mkdir(parents=True)
        self.audit.write_text("# Evidence\n" +
                              "".join("## " + x + "\nVerified source and negative cases\n"
                                      for x in gate.STAGES) + "## B7\nFinal audit\n")
        self.permit = self.r / gate.PERMIT
        self.permit.write_text(json.dumps({"schema": 1, "status": "BLOCKED"}))
        self.base = self.commit("baseline checkpoint")

    def commit(self, message):
        g(self.r, "add", "-A")
        g(self.r, "commit", "-qm", message)
        return g(self.r, "rev-parse", "HEAD")

    def put(self, p, value="test"):
        dest = self.r / p
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_text(value)
        return dest

    def ready(self, unit="M2A", paths=None, statuses=None, last=None):
        proof = g(self.r, "rev-parse", "HEAD:docs/v5/audit.md")
        stages = {
            x: {"status": "VERIFIED", "evidence": [
                {"path": "docs/v5/audit.md", "blob_sha": proof, "marker": "## " + x}
            ]} for x in gate.STAGES
        }
        if statuses:
            for stage, value in statuses.items():
                stages[stage]["status"] = value
        stages.update({"B6": "READY_TO_IMPLEMENT", "B7": "PENDING_FINAL_AUDIT"})
        self.permit.write_text(json.dumps({
            "schema": 1, "status": "READY", "unit_id": unit,
            "reviewed_head": self.base, "paths": paths or ["v5/app/**"],
            "stages": stages, "last_code_review": last,
        }, indent=2))
        return self.commit("docs: verified B1-B5 permit for " + unit)

    def source(self, label="v5/app/example.kt", msg="feat: change\n\nFARO-UNIT:M2A"):
        self.put(label, "class Change {}\n")
        return self.commit(msg)

    def check(self, head=None):
        return gate.audit_range(self.r, self.base, head or g(self.r, "rev-parse", "HEAD"))

    def test_docs_only_checkpoint_passes(self):
        self.put("docs/v5/notes.md", "no code")
        self.commit("docs only")
        self.assertEqual([], self.check())

    def test_guard_bootstrap_exempt_from_functional_code(self):
        self.put("scripts/quality/faro-protocol-gate.py", "# revised guard")
        self.put(".github/workflows/faro-v5-protocol-guard.yml", "name: guard")
        self.commit("protocol guard bootstrap")
        self.assertEqual([], self.check())

    def test_code_without_permit_fails_closed(self):
        self.source()
        with self.assertRaisesRegex(gate.ProtocolError, "not READY"):
            self.check()

    def test_permit_minted_in_same_code_commit_cannot_authorize_itself(self):
        self.put("v5/app/a.kt", "class A")
        self.permit.write_text(json.dumps({"schema": 1, "status": "READY"}))
        self.commit("feat\n\nFARO-UNIT:M2A")
        with self.assertRaisesRegex(gate.ProtocolError, "not READY"):
            self.check()

    def test_complete_preaudit_and_bounded_scope_pass(self):
        self.ready()
        self.source()
        self.assertEqual(1, len(self.check()))

    def test_docs_only_commit_does_not_hide_earlier_unreviewed_code(self):
        self.source()
        self.put("docs/v5/later.md", "late note")
        self.commit("docs after bad code")
        with self.assertRaises(gate.ProtocolError):
            self.check()

    def test_missing_b5_fails(self):
        self.ready(statuses={"B5": "MISSING"})
        self.source()
        with self.assertRaisesRegex(gate.ProtocolError, "B5"):
            self.check()

    def test_scope_crossing_fails(self):
        self.ready(paths=["v5/app/**"])
        self.source("v5/store/reality/reality.kt")
        with self.assertRaisesRegex(gate.ProtocolError, "outside preaudited"):
            self.check()

    def test_unit_marker_required(self):
        self.ready()
        self.source(msg="feat: unscoped change")
        with self.assertRaisesRegex(gate.ProtocolError, "FARO-UNIT"):
            self.check()

    def test_old_evidence_sha_rejected(self):
        self.ready()
        self.audit.write_text(self.audit.read_text() + "changed after approval\n")
        self.commit("docs: changed proof")
        self.source()
        with self.assertRaisesRegex(gate.ProtocolError, "blob mismatch"):
            self.check()

    def test_next_functional_commit_requires_independent_b7_review(self):
        self.ready()
        self.source()
        self.put("v5/app/other.kt", "class Other")
        self.commit("fix\n\nFARO-UNIT:M2A")
        with self.assertRaisesRegex(gate.ProtocolError, "previous code change"):
            self.check()

    def test_b7_red_fix_allows_same_unit_only(self):
        self.ready()
        prior = self.source()
        sha = g(self.r, "rev-parse", "HEAD:docs/v5/audit.md")
        last = {
            "sha": prior, "result": "RED_FIX", "unit_id": "M2A",
            "evidence": [{"path": "docs/v5/audit.md", "blob_sha": sha, "marker": "## B7"}],
        }
        self.ready(last=last)
        self.put("v5/app/fix.kt", "class Fixed")
        self.commit("fix\n\nFARO-UNIT:M2A")
        self.assertEqual(2, len(self.check()))

    def test_b7_red_fix_cannot_open_other_unit(self):
        self.ready()
        prior = self.source()
        sha = g(self.r, "rev-parse", "HEAD:docs/v5/audit.md")
        last = {
            "sha": prior, "result": "RED_FIX", "unit_id": "M2A",
            "evidence": [{"path": "docs/v5/audit.md", "blob_sha": sha, "marker": "## B7"}],
        }
        self.ready(unit="M3", last=last)
        self.source(msg="feat\n\nFARO-UNIT:M3")
        with self.assertRaisesRegex(gate.ProtocolError, "RED_FIX cannot open"):
            self.check()

    def test_code_and_permit_cannot_change_in_same_commit(self):
        self.ready()
        self.put("v5/app/a.kt", "class A")
        self.permit.write_text(self.permit.read_text().replace("M2A", "M2X"))
        self.commit("feat\n\nFARO-UNIT:M2A")
        with self.assertRaisesRegex(gate.ProtocolError, "control-state and code mixed"):
            self.check()


if __name__ == "__main__":
    unittest.main()
