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
    if writer.get("status") == "HELD":
        return verdict("READ_ONLY", "writer HELD: no CAS takeover or Actions", next="OPP-N031")

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


# FARO Noche S3 blocks V1. Pure selector: no GitHub writes, locks or schedules.
DYNAMIC_PROTOCOL = "docs/s3-night/FARO_S3_NOCHE_BLOQUES_DINAMICOS_V1_20261103.md"
BLOCKS = ("EVIDENCIA", "COMPARACION", "INVESTIGACION", "UNIFICACION",
          "AUDITORIA_PREVIA", "IMPLEMENTAR_Y_PROBAR", "AUDITORIA_FINAL")
DONE_STATES = {"PASS", "DONE_VERIFIED", "READY_FOR_SINGLE_PHYSICAL_CAMPAIGN"}
BLOCK_STATES = {"READY", "IN_PROGRESS", "WAIT_CI", "WAIT_WRITER", "WAIT_HUMAN",
                "BLOCKED", "DONE_VERIFIED", "READY_FOR_SINGLE_PHYSICAL_CAMPAIGN"}


def select_dynamic_blocks(queue, writer, scheduled_at, now, role, branch_head):
    """Choose one safe S3 unit/block from the existing shared CLOUD queue.

    Pure read-only decision. Caller must independently acquire/verify own writer
    via official CAS before changing code/queue. Never grant execution authority.
    """
    if not isinstance(queue, dict) or not isinstance(writer, dict):
        return verdict("STOP", "invalid queue/writer")
    if queue.get("branch") != BRANCH or queue.get("protocol") != DYNAMIC_PROTOCOL:
        return verdict("STOP", "branch/protocol mismatch")
    if queue.get("mode") != "ENABLED" or queue.get("activation_authorized") is not True:
        return verdict("STOP", "Noche not authorized")
    if not isinstance(branch_head, str) or not SHA.fullmatch(branch_head):
        return verdict("STOP", "invalid branch HEAD")
    if role not in ("NOCHE_A", "NOCHE_B"):
        return verdict("STOP", "invalid role")
    if queue.get("inflight") is not None:
        return verdict("STOP", "unresolved in-flight operation")
    if writer.get("status") not in ("RELEASED", "HELD"):
        return verdict("STOP", "writer state unknown")
    try:
        start = stamp(queue["starts_at"])
        cutoff = stamp(queue["hard_stop_at"])
        slot = stamp(scheduled_at)
        current = stamp(now)
    except (ValueError, TypeError, KeyError):
        return verdict("STOP", "invalid authorization timestamps")
    if cutoff <= start or slot < start or slot >= cutoff or current >= cutoff:
        return verdict("STOP", "authorization expired or invalid")
    offset = int((slot - start).total_seconds())
    expected_offset = 0 if role == "NOCHE_A" else 1800
    regular_slot = offset >= 0 and offset % 3600 == expected_offset
    # Explicit user-authorized early B handoff after A completed; never
    # infer extra schedule authority from the agent stopping early.
    handoff = queue.get("early_handoff")
    early_slot = False
    if role == "NOCHE_B" and isinstance(handoff, dict):
        try:
            completed = stamp(handoff.get("prior_completed_at"))
            early_slot = (
                handoff.get("authorized") is True
                and handoff.get("role") == "NOCHE_B"
                and handoff.get("prior_role") == "NOCHE_A"
                and isinstance(handoff.get("prior_run_id"), str)
                and bool(handoff["prior_run_id"].strip())
                and handoff.get("starts_at") == queue.get("starts_at")
                and handoff.get("slot_at") == scheduled_at
                and start <= completed < slot
                and start < slot < start + timedelta(minutes=30)
                and slot + timedelta(minutes=30) <= cutoff
            )
        except (ValueError, TypeError):
            early_slot = False
    if not (regular_slot or early_slot):
        return verdict("STOP", "A/B slot mismatch")
    if current < slot or current >= slot + timedelta(minutes=30):
        return verdict("STOP", "outside scheduled slot")
    age = (current - slot).total_seconds()
    if age >= 27 * 60:
        return verdict("READ_ONLY", "margin phase: no writes")
    if age >= 20 * 60 or slot + timedelta(minutes=20) > cutoff:
        return verdict("CLOSE_ONLY", "closing phase: checkpoint/release own writer only")
    if writer["status"] == "HELD":
        return verdict("READ_ONLY", "writer held; never take over")

    tasks = queue.get("tasks")
    if not isinstance(tasks, list) or not tasks:
        return verdict("STOP", "missing shared task queue")
    by_id = {}
    for task in tasks:
        if not isinstance(task, dict) or not isinstance(task.get("task_id"), str):
            return verdict("STOP", "invalid queue task")
        if task["task_id"] in by_id:
            return verdict("STOP", "duplicate task id")
        by_id[task["task_id"]] = task
    checkpoints = queue.get("block_checkpoints", {})
    if not isinstance(checkpoints, dict):
        return verdict("STOP", "invalid block checkpoints")

    def dependencies_done(task):
        dependencies = task.get("depends_on", [])
        if not isinstance(dependencies, list):
            return False
        for dep in dependencies:
            upstream = by_id.get(dep)
            if upstream is None:
                return False
            if upstream.get("status") in DONE_STATES:
                continue
            if dep == "S3-P00" and upstream.get("status") == "PASS_SPEC_ONLY":
                continue
            return False
        return True

    # Resume in-progress work before opening another unit; WAIT_CI blocks
    # independent writes only for the same unit, not a global CI lock.
    candidates = [t for t in tasks if t.get("status") in ("IN_PROGRESS", "WAIT_CI", "WAIT_WRITER")]
    candidates += [t for t in tasks if t.get("status") in ("READY", "PENDING")]
    for task in candidates:
        tid = task["task_id"]
        if not tid.startswith("S3-") or not dependencies_done(task):
            continue
        status = task.get("status")
        if status == "WAIT_CI":
            return verdict("READ_ONLY", "CI pending; verify exact run and SHA", unit_id=tid,
                           run_ids=task.get("action_run_ids", []))
        if status == "WAIT_WRITER":
            return verdict("READ_ONLY", "unit waiting for writer", unit_id=tid)
        cp = checkpoints.get(tid)
        if cp is None:
            if status == "IN_PROGRESS":
                return verdict("RECONCILE_CHECKPOINT", "legacy unit needs evidence mapping; do not repeat", unit_id=tid)
            return verdict("WORK_BLOCK", "new unit starts from evidence", unit_id=tid,
                           block_id=1, block_name=BLOCKS[0], expected_head=branch_head,
                           requires_writer_cas=True)
        if not isinstance(cp, dict) or cp.get("unit_id") != tid:
            return verdict("STOP", "checkpoint identity mismatch", unit_id=tid)
        if cp.get("block_status") not in BLOCK_STATES:
            return verdict("STOP", "unknown checkpoint status", unit_id=tid)
        if cp.get("block_status") == "WAIT_CI":
            return verdict("READ_ONLY", "checkpoint WAIT_CI; verify remote CI", unit_id=tid,
                           evidence=cp.get("evidence", []))
        if cp.get("block_status") in ("WAIT_HUMAN", "BLOCKED"):
            continue
        if cp.get("block_status") == "WAIT_WRITER":
            return verdict("READ_ONLY", "checkpoint WAIT_WRITER", unit_id=tid)
        if cp.get("expected_head") != branch_head:
            return verdict("REVALIDATE_SHA", "checkpoint SHA differs; revalidate affected blocks only",
                           unit_id=tid, expected_head=cp.get("expected_head"), live_head=branch_head)
        acceptance = task.get("acceptance")
        verified = cp.get("verified_blocks", {})
        if not isinstance(verified, dict):
            return verdict("STOP", "invalid verified blocks", unit_id=tid)
        block = None
        for i in range(1, 8):
            proof = verified.get(str(i))
            if isinstance(proof, dict) and proof.get("sha") == branch_head and proof.get("acceptance") == acceptance and proof.get("evidence"):
                continue
            block = i
            break
        if block is None:
            return verdict("REVIEW_COMPLETION", "all blocks evidenced; require acceptance audit before DONE", unit_id=tid)
        return verdict("WORK_BLOCK", "resume first nonverified block", unit_id=tid,
                       block_id=block, block_name=BLOCKS[block - 1], expected_head=branch_head,
                       requires_writer_cas=True)
    return verdict("CLOSE_ONLY", "no safely eligible S3 unit; leave human/physical for later")


def verify_result(result):
    if result.get("kind") == "CODE_TEST":
        sha = result.get("commit_sha")
        if not isinstance(sha, str) or not SHA.fullmatch(sha):
            return verdict("NO_PASS", "missing exact code commit SHA")
        if result.get("started_from") == sha:
            return verdict("NO_PASS", "no new code/test commit")
        if result.get("tests_executed") is not True:
            return verdict("NO_PASS", "tests not executed")
        if result.get("xml_verified") is not True or type(result.get("xml_passed")) is not int or result["xml_passed"] < 1:
            return verdict("NO_PASS", "JUnit XML proof missing")
        if type(result.get("run_id")) is not int or result["run_id"] <= 0:
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
    parser.add_argument("--dynamic", action="store_true")
    args = parser.parse_args()
    try:
        if args.operation == "verify":
            output = verify_result(json.loads(args.result.read_text(encoding="utf-8")))
        elif args.dynamic:
            output = select_dynamic_blocks(
                json.loads(args.queue.read_text(encoding="utf-8")),
                json.loads(args.writer.read_text(encoding="utf-8")),
                args.scheduled_at, args.now, args.role, args.branch_head,
            )
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
