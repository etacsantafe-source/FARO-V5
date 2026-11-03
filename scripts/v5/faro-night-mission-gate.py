#!/usr/bin/env python3
"""Read-only FARO Noche mission selector and evidence gate (standard library only).

Does NOT acquire writer, write to GitHub, launch Actions, or authorize Night.
The caller must independently establish remote CAS ownership before any mutation.
"""
import argparse
from datetime import datetime, timedelta
import json
from pathlib import Path
import re

REPO = "etacsantafe-source/FARO-V5"
BRANCH = "work/s3-night-20261102"
SHA = re.compile(r"^[0-9a-f]{40}$")


def stamp(value):
    try:
        parsed = datetime.fromisoformat(value)
    except (TypeError, ValueError) as e:
        raise ValueError("invalid ISO timestamp") from e
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        raise ValueError("timestamp must have timezone offset")
    return parsed


def verdict(code, why, **kw):
    return {"decision": code, "reason": why, **kw}


def select_mission(queue, writer, bank, scheduled_at, now, role, branch_head):
    # Any uncertain authority is an absolute stop; this program only reads state.
    if queue.get("branch") != BRANCH or bank.get("branch") != BRANCH:
        return verdict("STOP", "branch mismatch")
    if queue.get("mode") != "ENABLED" or queue.get("activation_authorized") is not True:
        return verdict("STOP", "Noche is not authorized; do not launch work")
    if not SHA.fullmatch(branch_head or ""):
        return verdict("STOP", "unverified live branch HEAD")
    if queue.get("inflight") is not None:
        return verdict("STOP", "unresolved inflight task")
    if role not in {"NOCHE_A", "NOCHE_B"}:
        return verdict("STOP", "invalid turn identity")
    if writer.get("status") not in {"HELD", "RELEASED"}:
        return verdict("STOP", "unknown writer state")
    if writer.get("status") == "HELD":
        return verdict("READ_ONLY", "writer HELD: no CAS takeover or Actions", next="OPP-N031")

    try:
        started = stamp(queue["starts_at"])
        cutoff = stamp(queue["hard_stop_at"])
        scheduled = stamp(scheduled_at)
        current = stamp(now)
    except (ValueError, KeyError):
        return verdict("STOP", "authorization timestamps missing/invalid")
    if current < started or scheduled < started or scheduled >= cutoff or current >= cutoff:
        return verdict("STOP", "outside explicit authorization")
    if current < scheduled:
        return verdict("STOP", "scheduled turn has not started")
    if scheduled + timedelta(minutes=20) <= current:
        return verdict("CLOSE_ONLY", "productive window elapsed; no new unit")
    if scheduled + timedelta(minutes=20) > cutoff:
        return verdict("CLOSE_ONLY", "insufficient room before hard stop")
    if bank.get("protocol") != "V2.2-R1":
        return verdict("STOP", "unrecognized bank protocol")

    opps = {x.get("opportunity_id"): x for x in bank.get("opportunities", [])}
    target = opps.get("OPP-A1-ADV")
    if target and target.get("status") not in {"DONE_VERIFIED", "WAIT_HUMAN"}:
        # Existing investigation is deduplicated. NEXT action is a real test;
        # it is never counted as a new research discovery or as a PASS.
        return verdict(
            "TEST_FIRST", "existing OPP-A1-ADV requires a reproducible test, not another report",
            task_id="S3-A1", opportunity_id="OPP-A1-ADV",
            source_head=target.get("source_head"), expected_live_head=branch_head,
            production_file="v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5HomeIndicators.kt",
            test_file="v5/surface/src/test/kotlin/ar/com/faro/v5/surface/FaroV5HomeIndicatorsContractTest.kt",
            acceptance="revoke authority after visible Avisos=3 while readPort object stays identical; screen must show No disponible",
            required_next_step="Implement new targeted regression test; verify red/green or record concrete blocking API",
            write_authority="MUST_ACQUIRE_AND_VERIFY_SEPARATE_GLOBAL_WRITER_CAS",
            claims_not_allowed=["PASS_WITHOUT_XML", "NEW_DISCOVERY_FOR_EXISTING_HYPOTHESIS", "PHYSICAL_TEST_DONE"],
        )
    for key in ("OPP-N031", "OPP-N015", "OPP-GUARDADOS"):
        opp = opps.get(key)
        if opp and opp.get("status") not in {"DONE_VERIFIED", "WAIT_HUMAN"}:
            return verdict("RESEARCH_ONLY", "no safely pre-approved implementation unit", opportunity_id=key,
                           hypothesis=opp.get("hypothesis"), acceptance=opp.get("acceptance"),
                           rule="no write to GitHub without an independently verified writer CAS")
    return verdict("CLOSE_ONLY", "no safe nonduplicated opportunity")


def verify_result(result):
    if result.get("kind") == "CODE_TEST":
        sha = result.get("commit_sha")
        if not isinstance(sha, str) or not SHA.fullmatch(sha):
            return verdict("NO_PASS", "missing exact code commit SHA")
        if result.get("started_from") == sha:
            return verdict("NO_PASS", "no new code/test commit")
        if result.get("tests_executed") is not True:
            return verdict("NO_PASS", "tests not executed")
        if result.get("xml_verified") is not True or not isinstance(result.get("xml_passed"), int) or result["xml_passed"] < 1:
            return verdict("NO_PASS", "JUnit XML proof missing")
        if not isinstance(result.get("run_id"), int) or result["run_id"] <= 0:
            return verdict("NO_PASS", "exact CI run ID missing")
        if result.get("run_sha") != sha:
            return verdict("NO_PASS", "CI did not run on exact code SHA")
        return verdict("EVIDENCE_FORMAT_VALID_REMOTE_UNVERIFIED", "reported code/test IDs are well-formed; GitHub run, SHA and XML require independent fetch", commit_sha=sha,
                       run_id=result["run_id"], xml_passed=result["xml_passed"])
    if result.get("kind") == "BLOCKER":
        if all(isinstance(result.get(x), str) and result[x].strip() for x in
               ("opportunity_id", "source_file", "falsifiable_issue", "next_experiment")):
            return verdict("BLOCKED_NEW_EVIDENCE", "reproducible blocker recorded, not counted as code PASS")
    return verdict("NO_PASS", "narrative-only outcome or incomplete evidence")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("operation", choices=("plan", "verify"))
    parser.add_argument("--queue", type=Path)
    parser.add_argument("--writer", type=Path)
    parser.add_argument("--bank", type=Path)
    parser.add_argument("--result", type=Path)
    parser.add_argument("--scheduled-at")
    parser.add_argument("--now")
    parser.add_argument("--role")
    parser.add_argument("--branch-head")
    args = parser.parse_args()
    try:
        if args.operation == "verify":
            output = verify_result(json.loads(args.result.read_text(encoding="utf-8")))
        else:
            output = select_mission(
                json.loads(args.queue.read_text(encoding="utf-8")),
                json.loads(args.writer.read_text(encoding="utf-8")),
                json.loads(args.bank.read_text(encoding="utf-8")),
                args.scheduled_at, args.now, args.role, args.branch_head,
            )
        print(json.dumps(output, ensure_ascii=False, indent=2))
        if output["decision"] in {"STOP", "NO_PASS"}:
            raise SystemExit(20)
    except (OSError, TypeError, AttributeError, KeyError, ValueError) as exc:
        print(json.dumps(verdict("STOP", "missing or invalid input: " + str(exc))))
        raise SystemExit(20)


if __name__ == "__main__":
    main()
