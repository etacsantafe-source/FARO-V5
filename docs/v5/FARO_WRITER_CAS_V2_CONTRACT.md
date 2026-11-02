# FARO Auto V2 — CAS writer operator contract

**NOT ACTIVATED.** This is an isolated implementation, not a verified production lock.

## Shared writer
All cooperating agents (Auto, Noche A, Noche B, manual) MUST use the same dedicated GitHub branch `faro-v5-writer-lock`, with explicit `--force-with-lease` expected SHA. An acquire fails while HELD, regardless of age. There is no automatic stale takeover. A lock is released only by the same owner/session token. A lost token requires a separate human-reviewed recovery protocol, not a force push.

A writer MUST also verify the target branch HEAD and writer ownership before EVERY mutating operation. This implementation supports only one immutable target HEAD per session; release and reacquire with a new target HEAD before additional commits. Remote ref CAS alone does not protect against noncooperating writers or direct GitHub API writes. Do not start an automated worker until every writer path has been wired to this gate.

The `--token` CLI argument is sensitive: command-line arguments can be visible to other local processes. Use a private runtime wrapper and never log tokens; token hash alone is stored in the public writer branch. Production secret-handling and race tests are pending.

## Safe preflight
`bash scripts/v5/faro-auto-v2-preflight` only checks repository and phase. It does not acquire a writer.

## Test matrix required before activation
- CAS race with two simultaneous owners: exactly one acquires.
- Non-owner release/verify denied; no stale-lock takeover.
- Network loss and remote SHA mismatch: fail closed.
- Target branch changed: verify denied.
- Release followed by new acquire increments epoch.
- No remote publication of raw token.
- Termux Resource Guard STOP blocks worker.
- Noche A/B and manual writers use identical gate.
- Recovery from abandoned HELD state needs explicit human approval.

No S3 work, V2 activation, automation scheduling, or main mutation authorized by this document.
