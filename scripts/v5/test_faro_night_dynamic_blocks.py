#!/usr/bin/env python3
"""Read-only S3 dynamic blocks tests; no write to GitHub."""
import importlib.util
from pathlib import Path
import unittest

SRC = Path(__file__).with_name("faro-night-mission-gate.py")
spec = importlib.util.spec_from_file_location("night_gate_dynamic", SRC)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)

SHA = "a" * 40
OTHER_SHA = "b" * 40
A = "2026-11-03T20:00:00-03:00"
B = "2026-11-03T20:30:00-03:00"
NOW = "2026-11-03T20:30:05-03:00"


def seed():
    q = {
        "branch": gate.BRANCH, "protocol": gate.DYNAMIC_PROTOCOL,
        "mode": "ENABLED", "activation_authorized": True,
        "starts_at": A, "hard_stop_at": "2026-11-03T22:00:00-03:00",
        "inflight": None,
        "tasks": [
            {"task_id": "S3-N00", "status": "PASS", "depends_on": []},
            {"task_id": "S3-P00", "status": "PASS_SPEC_ONLY", "depends_on": ["S3-N00"]},
            {"task_id": "S3-A1", "status": "IN_PROGRESS", "depends_on": ["S3-P00"], "acceptance": "A1"},
            {"task_id": "S3-A2", "status": "PENDING", "depends_on": ["S3-A1"], "acceptance": "A2"},
        ],
        "block_checkpoints": {
            "S3-A1": {"unit_id": "S3-A1", "block_status": "IN_PROGRESS",
                      "expected_head": SHA, "verified_blocks": {}}
        }
    }
    return q, {"status": "RELEASED"}


def decide(q=None, w=None, scheduled=B, now=NOW, role="NOCHE_B", head=SHA):
    if q is None or w is None:
        sq, sw = seed()
        q = sq if q is None else q
        w = sw if w is None else w
    return gate.select_dynamic_blocks(q, w, scheduled, now, role, head)


class DynamicBlockTests(unittest.TestCase):
    def test_resume_first_block(self):
        r = decide()
        self.assertEqual(("WORK_BLOCK", "S3-A1", 1), (r["decision"], r["unit_id"], r["block_id"]))

    def test_skip_only_exact_sha_and_acceptance(self):
        q, _ = seed()
        q["block_checkpoints"]["S3-A1"]["verified_blocks"] = {
            "1": {"sha": SHA, "acceptance": "A1", "evidence": ["test:1"]},
            "2": {"sha": SHA, "acceptance": "A1", "evidence": ["review:2"]}}
        self.assertEqual(3, decide(q=q)["block_id"])
        q["block_checkpoints"]["S3-A1"]["verified_blocks"]["2"]["sha"] = OTHER_SHA
        self.assertEqual(2, decide(q=q)["block_id"])

    def test_legacy_inprogress_does_not_repeat(self):
        q, _ = seed()
        q["block_checkpoints"] = {}
        self.assertEqual("RECONCILE_CHECKPOINT", decide(q=q)["decision"])

    def test_stale_sha_only_revalidates(self):
        q, _ = seed()
        q["block_checkpoints"]["S3-A1"]["expected_head"] = OTHER_SHA
        self.assertEqual("REVALIDATE_SHA", decide(q=q)["decision"])

    def test_wait_ci_is_read_only(self):
        q, _ = seed()
        q["block_checkpoints"]["S3-A1"]["block_status"] = "WAIT_CI"
        self.assertEqual("READ_ONLY", decide(q=q)["decision"])

    def test_writer_held_is_read_only(self):
        self.assertEqual("READ_ONLY", decide(w={"status": "HELD"})["decision"])

    def test_unknown_writer_stops(self):
        self.assertEqual("STOP", decide(w={"status": "UNKNOWN"})["decision"])

    def test_queue_disabled_stops(self):
        q, _ = seed()
        q["activation_authorized"] = False
        self.assertEqual("STOP", decide(q=q)["decision"])

    def test_old_protocol_stops(self):
        q, _ = seed()
        q["protocol"] = "V2.2-R1"
        self.assertEqual("STOP", decide(q=q)["decision"])

    def test_wrong_ab_slot_stops(self):
        self.assertEqual("STOP", decide(scheduled=B, role="NOCHE_A")["decision"])

    def test_closing_phase_no_new_work(self):
        self.assertEqual("CLOSE_ONLY", decide(now="2026-11-03T20:51:00-03:00")["decision"])

    def test_margin_read_only(self):
        self.assertEqual("READ_ONLY", decide(now="2026-11-03T20:58:00-03:00")["decision"])

    def test_after_slot_stops(self):
        self.assertEqual("STOP", decide(now="2026-11-03T21:00:00-03:00")["decision"])

    def test_absolute_cutoff_stops(self):
        self.assertEqual("STOP", decide(now="2026-11-03T22:00:00-03:00")["decision"])

    def test_inflight_stops(self):
        q, _ = seed()
        q["inflight"] = {"task_id": "S3-A1"}
        self.assertEqual("STOP", decide(q=q)["decision"])

    def test_dependencies_gate_next_unit(self):
        q, _ = seed()
        q["tasks"][2]["status"] = "DONE_VERIFIED"
        q["block_checkpoints"].pop("S3-A1")
        r = decide(q=q)
        self.assertEqual(("S3-A2", 1), (r["unit_id"], r["block_id"]))

    def test_wait_human_skips_to_independent_unit(self):
        q, _ = seed()
        q["block_checkpoints"]["S3-A1"]["block_status"] = "WAIT_HUMAN"
        q["tasks"].append({"task_id": "S3-A4", "status": "READY", "depends_on": ["S3-P00"], "acceptance": "A4"})
        r = decide(q=q)
        self.assertEqual("S3-A4", r["unit_id"])

    def test_checkpoint_identity_mismatch_stops(self):
        q, _ = seed()
        q["block_checkpoints"]["S3-A1"]["unit_id"] = "S3-A2"
        self.assertEqual("STOP", decide(q=q)["decision"])

    def test_all_blocks_never_auto_close(self):
        q, _ = seed()
        q["block_checkpoints"]["S3-A1"]["verified_blocks"] = {
            str(i): {"sha": SHA, "acceptance": "A1", "evidence": ["test"]} for i in range(1, 8)}
        self.assertEqual("REVIEW_COMPLETION", decide(q=q)["decision"])


if __name__ == "__main__":
    unittest.main()
