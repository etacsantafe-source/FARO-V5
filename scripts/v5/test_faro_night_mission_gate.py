#!/usr/bin/env python3
"""Non-network deterministic tests; no Git write, no Actions, no automation."""
import importlib.util
from pathlib import Path
import unittest

SRC = Path(__file__).with_name("faro-night-mission-gate.py")
spec = importlib.util.spec_from_file_location("night_gate", SRC)
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)

SHA = "a" * 40
SCHEDULE = "2026-11-02T23:30:00-03:00"
NOW = "2026-11-02T23:30:20-03:00"


def seed():
    return ({
        "branch": gate.BRANCH, "mode": "ENABLED", "activation_authorized": True,
        "starts_at": "2026-11-02T23:00:00-03:00",
        "hard_stop_at": "2026-11-03T07:00:00-03:00", "inflight": None,
    }, {"status": "RELEASED"}, {
        "branch": gate.BRANCH, "protocol": "V2.2-R1", "opportunities": [{
            "opportunity_id": "OPP-A1-ADV", "source_head": "b" * 40, "status": "RESEARCH_ONLY",
            "hypothesis": "stale count", "acceptance": "new regression test"
        }],
    })


def decide(q=None, w=None, b=None, scheduled=SCHEDULE, now=NOW):
    x, y, z = seed()
    return gate.select_mission(q or x, w or y, b or z, scheduled, now, "NOCHE_B", SHA)


class NightMissionGateTests(unittest.TestCase):
    def test_cancelled_queue_refuses_execution(self):
        q, _, _ = seed()
        q.update(mode="STAGED", activation_authorized=False)
        self.assertEqual("STOP", decide(q=q)["decision"])

    def test_cancellation_refuses_even_if_prior_time_valid(self):
        q, _, _ = seed()
        q["activation_authorized"] = False
        self.assertEqual("STOP", decide(q=q)["decision"])

    def test_before_slot_stops(self):
        self.assertEqual("STOP", decide(now="2026-11-02T23:29:59-03:00")["decision"])

    def test_after_productive_window_closes(self):
        self.assertEqual("CLOSE_ONLY", decide(now="2026-11-02T23:50:00-03:00")["decision"])

    def test_cutoff_is_absolute(self):
        self.assertEqual("STOP", decide(scheduled="2026-11-03T06:30:00-03:00",
                                        now="2026-11-03T07:00:00-03:00")["decision"])

    def test_writer_held_prevents_code(self):
        self.assertEqual("READ_ONLY", decide(w={"status": "HELD"})["decision"])

    def test_unknown_writer_stops(self):
        self.assertEqual("STOP", decide(w={"status": "UNKNOWN"})["decision"])

    def test_unresolved_inflight_stops(self):
        q, _, _ = seed()
        q["inflight"] = {"task": "S3-A1"}
        self.assertEqual("STOP", decide(q=q)["decision"])

    def test_preselects_real_test_instead_of_duplicate_research(self):
        actual = decide()
        self.assertEqual("TEST_FIRST", actual["decision"])
        self.assertEqual("OPP-A1-ADV", actual["opportunity_id"])
        self.assertIn("MUST_ACQUIRE", actual["write_authority"])
        self.assertIn("FaroV5HomeIndicators.kt", actual["production_file"])

    def test_completed_hypothesis_is_not_reopened(self):
        _, _, b = seed()
        b["opportunities"][0]["status"] = "DONE_VERIFIED"
        self.assertEqual("CLOSE_ONLY", decide(b=b)["decision"])

    def test_missing_branch_head_stops(self):
        q, w, b = seed()
        self.assertEqual("STOP", gate.select_mission(q,w,b,SCHEDULE,NOW,"NOCHE_B","oops")["decision"])

    def test_report_only_is_never_pass(self):
        self.assertEqual("NO_PASS", gate.verify_result({"kind": "REPORT", "text": "all green"})["decision"])

    def test_pass_requires_real_ci_xml(self):
        x = {"kind": "CODE_TEST", "started_from": "b" * 40, "commit_sha": SHA,
             "tests_executed": True, "xml_verified": False, "xml_passed": 10,
             "run_id": 123, "run_sha": SHA}
        self.assertEqual("NO_PASS", gate.verify_result(x)["decision"])
        x["xml_verified"] = True
        self.assertEqual("EVIDENCE_FORMAT_VALID_REMOTE_UNVERIFIED", gate.verify_result(x)["decision"])

    def test_ci_other_sha_cannot_certify(self):
        x = {"kind": "CODE_TEST", "started_from": "b" * 40, "commit_sha": SHA,
             "tests_executed": True, "xml_verified": True, "xml_passed": 10,
             "run_id": 123, "run_sha": "c" * 40}
        self.assertEqual("NO_PASS", gate.verify_result(x)["decision"])

    def test_writer_held_after_cutoff_stops_not_wait(self):
        self.assertEqual("STOP", decide(w={"status": "HELD"},
                                         scheduled="2026-11-03T06:30:00-03:00",
                                         now="2026-11-03T07:00:00-03:00")["decision"])

    def test_boolean_xml_count_is_rejected(self):
        x = {"kind": "CODE_TEST", "started_from": "b" * 40, "commit_sha": SHA,
             "tests_executed": True, "xml_verified": True, "xml_passed": True,
             "run_id": 123, "run_sha": SHA}
        self.assertEqual("NO_PASS", gate.verify_result(x)["decision"])

    def test_blocker_is_not_code_pass(self):
        x = {"kind":"BLOCKER","opportunity_id":"OPP-A1-ADV",
             "source_file":"FaroV5HomeIndicators.kt", "falsifiable_issue":"No Compose test environment",
             "next_experiment":"Add instrumented test harness"}
        self.assertEqual("BLOCKED_NEW_EVIDENCE", gate.verify_result(x)["decision"])


if __name__ == "__main__":
    unittest.main()
