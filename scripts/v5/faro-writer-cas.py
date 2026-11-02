#!/usr/bin/env python3
"""FARO writer v2. Exclusive GitHub ref CAS. Never auto-steal an existing lock."""
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time

REF = "refs/heads/faro-v5-writer-lock"
ZERO = "0" * 40
REMOTE = "origin"
EXPECTED = "etacsantafe-source/FARO-V5"


class Stop(Exception):
    pass


def git(*args, data=None):
    p = subprocess.run(["git", *args], input=data, text=True,
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                       env={**os.environ, "GIT_TERMINAL_PROMPT": "0"}, timeout=45)
    if p.returncode:
        raise Stop("git operation failed: " + " ".join(args[:2]) + ": " + p.stderr[-350:])
    return p.stdout.strip()


def remote_ok():
    u = git("remote", "get-url", REMOTE)
    if u not in ("git@github.com:" + EXPECTED + ".git",
                 "https://github.com/" + EXPECTED,
                 "https://github.com/" + EXPECTED + ".git"):
        raise Stop("wrong origin")


def head():
    return git("rev-parse", "HEAD")


def ref():
    out = git("ls-remote", REMOTE, REF)
    return out.split()[0] if out else ZERO


def record(sha):
    if sha == ZERO:
        return None
    git("fetch", "--no-tags", REMOTE, REF)
    if git("rev-parse", "FETCH_HEAD") != sha:
        raise Stop("writer changed during fetch")
    raw = git("show", sha + ":writer.json")
    return json.loads(raw)


def publish(old, value):
    blob = git("hash-object", "-w", "--stdin", data=json.dumps(value, sort_keys=True) + "\n")
    tree = git("mktree", data="100644 blob " + blob + "\twriter.json\n")
    args = ["commit-tree", tree]
    if old != ZERO:
        args += ["-p", old]
    commit = git(*args, data="FARO writer CAS state\n")
    # Explicit expected old SHA; concurrent writers cannot both win.
    git("push", "--porcelain", REMOTE,
        "--force-with-lease=" + REF + ":" + old,
        commit + ":" + REF)
    if ref() != commit:
        raise Stop("remote writer ref changed after CAS")
    return commit


def main():
    p = argparse.ArgumentParser()
    p.add_argument("action", choices=["status", "acquire", "verify", "release"])
    p.add_argument("--owner", default="")
    p.add_argument("--token", default="")
    p.add_argument("--target-head", default="")
    args = p.parse_args()
    if args.token:
        raise Stop("token CLI argument disabled; use FARO_WRITER_TOKEN environment variable")
    args.token = os.environ.get("FARO_WRITER_TOKEN", "")
    remote_ok()
    old = ref()
    state = record(old)
    if args.action == "status":
        print(json.dumps({"ref": old, "state": state}, sort_keys=True))
        return
    if not re.fullmatch(r"[A-Za-z0-9_.:-]{8,100}", args.owner):
        raise Stop("invalid owner")
    if not re.fullmatch(r"[A-Za-z0-9_.:-]{24,160}", args.token):
        raise Stop("invalid session token; use a unique random token")
    if args.action == "acquire":
        if state is not None and state.get("status") != "RELEASED":
            raise Stop("writer occupied; no automatic lease takeover")
        if args.target_head != head():
            raise Stop("target HEAD mismatch")
        if git("status", "--porcelain"):
            raise Stop("dirty worktree")
        value = {"schema": 1, "status": "HELD", "owner": args.owner,
                 "token_hash": hashlib.sha256(args.token.encode()).hexdigest(), "target_head": args.target_head,
                 "epoch": (state or {}).get("epoch", 0) + 1,
                 "timestamp": int(time.time())}
        publish(old, value)
        print("ACQUIRED epoch=" + str(value["epoch"]))
        return
    if state is None or state.get("status") != "HELD":
        raise Stop("writer not held")
    if state.get("owner") != args.owner or state.get("token_hash") != hashlib.sha256(args.token.encode()).hexdigest():
        raise Stop("writer identity mismatch")
    if args.action == "verify":
        if args.target_head != state.get("target_head") or head() != args.target_head:
            raise Stop("target HEAD mismatch")
        if ref() != old:
            raise Stop("writer changed during verification")
        print("VERIFIED epoch=" + str(state["epoch"]))
        return
    if args.action == "release":
        publish(old, {**state, "status": "RELEASED", "timestamp": int(time.time())})
        print("RELEASED")


if __name__ == "__main__":
    try:
        main()
    except (Stop, subprocess.TimeoutExpired, ValueError, KeyError) as e:
        print("STOP: " + str(e), file=sys.stderr)
        sys.exit(20)
