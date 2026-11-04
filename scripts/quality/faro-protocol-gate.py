#!/usr/bin/env python3
"""FARO V5: fail-closed seven-block protocol gate, stdlib only.

Checks every first-parent commit after an immutable baseline; a docs-only
commit cannot hide a prior unreviewed functional change. An approved per-unit
permit MUST exist in the parent of each code commit (not minted with code).
A subsequent code commit requires a documented B7 disposition for the last.
CI is a detectable check, NOT a GitHub branch-protection rule on its own.
"""
from __future__ import annotations

import argparse
import fnmatch
import json
import pathlib
import subprocess
import sys

BASELINE = "197e68bc1d9c52917a9066a27e6a0fa235a28217"
PERMIT = "docs/v5/FARO_PROTOCOL_WORK_PERMIT.json"
STAGES = ("B1", "B2", "B3", "B4", "B5")
CONTROL_FILES = {
    PERMIT,
    "docs/v5/FARO_CONTINUITY_POINTER.json",
    "docs/v5/FARO_V2_PROTOCOL_RETRO_AUDIT_20261104.json",
    "scripts/quality/faro-protocol-gate.py",
    "scripts/quality/test_faro_protocol_gate.py",
    ".github/workflows/faro-v5-protocol-guard.yml",
}


class ProtocolError(RuntimeError):
    pass


def git(repo: pathlib.Path, *arguments: str) -> str:
    result = subprocess.run(
        ["git", "-C", str(repo), *arguments],
        text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode:
        raise ProtocolError("git " + " ".join(arguments) + ": " + result.stderr.strip()[:250])
    return result.stdout.strip()


def files_in_commit(repo: pathlib.Path, commit: str) -> list[str]:
    parent = git(repo, "rev-parse", commit + "^")
    # --name-only includes rename destinations; --no-renames also exposes deletion.
    result = git(repo, "diff", "--name-only", "--no-renames", parent, commit)
    return [p for p in result.splitlines() if p]


def is_functional(path: str) -> bool:
    return path.startswith("v5/") or path.startswith("scripts/v5/") or (
        path.startswith(".github/workflows/") and path !=
        ".github/workflows/faro-v5-protocol-guard.yml"
    )


def is_control(path: str) -> bool:
    return path in CONTROL_FILES


def read_file(repo: pathlib.Path, commit: str, path: str) -> str:
    return git(repo, "show", commit + ":" + path)


def read_permit(repo: pathlib.Path, commit: str) -> dict:
    try:
        data = json.loads(read_file(repo, commit, PERMIT))
    except (ProtocolError, ValueError) as error:
        raise ProtocolError("missing/invalid protocol permit in PARENT " + commit[:12]) from error
    if not isinstance(data, dict) or data.get("schema") != 1:
        raise ProtocolError("protocol permit has invalid schema")
    return data


def evidence(repo: pathlib.Path, commit: str, stage: str, record: dict) -> None:
    proof = record.get("evidence")
    if record.get("status") != "VERIFIED" or not isinstance(proof, list) or not proof:
        raise ProtocolError(stage + ": no VERIFIED evidence")
    for ref in proof:
        if not isinstance(ref, dict):
            raise ProtocolError(stage + ": malformed evidence")
        path, sha, marker = ref.get("path"), ref.get("blob_sha"), ref.get("marker")
        if not isinstance(path, str) or not path.startswith("docs/") or ".." in path.split("/"):
            raise ProtocolError(stage + ": evidence must be an existing docs path")
        if not isinstance(sha, str) or len(sha) != 40:
            raise ProtocolError(stage + ": missing exact 40-character proof blob")
        if not isinstance(marker, str) or not marker.strip():
            raise ProtocolError(stage + ": missing proof section marker")
        actual = git(repo, "rev-parse", commit + ":" + path)
        if actual != sha:
            raise ProtocolError(stage + ": evidence blob mismatch for " + path)
        if marker not in read_file(repo, commit, path):
            raise ProtocolError(stage + ": evidence section missing in " + path)


def verify_permit(
    repo: pathlib.Path, parent: str, paths: list[str],
    latest_functional: str | None,
) -> str:
    data = read_permit(repo, parent)
    if data.get("status") != "READY":
        raise ProtocolError("STOP: protocol not READY in parent (B1-B5 required)")
    unit = data.get("unit_id")
    if not isinstance(unit, str) or not unit or len(unit) > 80:
        raise ProtocolError("STOP: missing bounded unit_id")
    review = data.get("reviewed_head")
    if not isinstance(review, str) or len(review) != 40:
        raise ProtocolError("STOP: no exact reviewed_head")
    git(repo, "merge-base", "--is-ancestor", review, parent)
    scopes = data.get("paths")
    if not isinstance(scopes, list) or not scopes or len(scopes) > 20 or any(
        not isinstance(p, str) or not p or p in ("*", "**", "v5/**", "scripts/**")
        for p in scopes
    ):
        raise ProtocolError("STOP: permit must be path-bounded, not global")
    for path in paths:
        if not any(fnmatch.fnmatchcase(path, pattern) for pattern in scopes):
            raise ProtocolError("STOP: file outside preaudited unit: " + path)
    stages = data.get("stages")
    if not isinstance(stages, dict):
        raise ProtocolError("STOP: seven-block stages missing")
    for stage in STAGES:
        record = stages.get(stage)
        if not isinstance(record, dict):
            raise ProtocolError(stage + ": not recorded")
        evidence(repo, parent, stage, record)
    if stages.get("B6") != "READY_TO_IMPLEMENT":
        raise ProtocolError("STOP: B6 not prepared")
    if stages.get("B7") != "PENDING_FINAL_AUDIT":
        raise ProtocolError("STOP: B7 final-audit obligation missing")
    last = data.get("last_code_review")
    if latest_functional:
        if not isinstance(last, dict) or last.get("sha") != latest_functional:
            raise ProtocolError("STOP: previous code change has no B7 disposition")
        if last.get("result") not in ("PASS", "RED_FIX"):
            raise ProtocolError("STOP: previous code change missing PASS/RED_FIX audit")
        evidence(repo, parent, "B7", {
            "status": "VERIFIED", "evidence": last.get("evidence"),
        })
        if last["result"] == "RED_FIX" and last.get("unit_id") != unit:
            raise ProtocolError("STOP: RED_FIX cannot open another unit")
    return unit


def audit_range(repo: pathlib.Path, baseline: str, head: str) -> list[str]:
    git(repo, "merge-base", "--is-ancestor", baseline, head)
    commits = git(repo, "rev-list", "--first-parent", "--reverse", baseline + ".." + head)
    latest: str | None = None
    approved: list[str] = []
    for commit in commits.splitlines():
        paths = files_in_commit(repo, commit)
        functional = [p for p in paths if is_functional(p)]
        if not functional:
            continue
        if any(is_control(p) for p in paths):
            raise ProtocolError("STOP: control-state and code mixed in commit " + commit[:12])
        parent = git(repo, "rev-parse", commit + "^")
        unit = verify_permit(repo, parent, functional, latest)
        subject = git(repo, "show", "-s", "--format=%B", commit)
        if ("FARO-UNIT:" + unit) not in subject.splitlines():
            raise ProtocolError("STOP: missing FARO-UNIT:" + unit + " in " + commit[:12])
        latest = commit
        approved.append(unit + "@" + commit[:12])
    return approved


def preflight(repo: pathlib.Path, head: str, unit: str, paths: list[str]) -> str:
    if not paths or any(not is_functional(p) for p in paths):
        raise ProtocolError("STOP: preflight requires explicit functional file paths")
    if not unit:
        raise ProtocolError("STOP: unit required")
    # Determine latest code commit since baseline; cannot skip B7.
    git(repo, "merge-base", "--is-ancestor", BASELINE, head)
    commits = git(repo, "rev-list", "--first-parent", "--reverse", BASELINE + ".." + head)
    latest = None
    for commit in commits.splitlines():
        if any(is_functional(p) for p in files_in_commit(repo, commit)):
            latest = commit
    authorized = verify_permit(repo, head, paths, latest)
    if authorized != unit:
        raise ProtocolError("STOP: requested unit differs from audited permit")
    return authorized


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo", default=".")
    parser.add_argument("--baseline", default=BASELINE)
    parser.add_argument("--head", default="HEAD")
    parser.add_argument("--mode", required=True, choices=("ci", "preflight"))
    parser.add_argument("--unit")
    parser.add_argument("--path", action="append", default=[])
    args = parser.parse_args()
    repo = pathlib.Path(args.repo).resolve()
    try:
        head = git(repo, "rev-parse", args.head)
        if args.mode == "ci":
            approved = audit_range(repo, args.baseline, head)
            print("FARO_PROTOCOL_GATE_PASS", len(approved), "functional units validated",
                  ",".join(approved) if approved else "docs/tool-only checkpoint")
        else:
            unit = preflight(repo, head, args.unit or "", args.path)
            print("FARO_PROTOCOL_PREFLIGHT_PASS", unit, head)
        return 0
    except ProtocolError as error:
        print("FARO_PROTOCOL_GATE_STOP:", str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
