# S3.A3 M01 — B7 RED_FIX (2026-11-11)

## B7
Result RED_FIX for code 6b600a608a119c3e58f144428d48d4fad0839a1f. Native read-host regression run43014778557 job130502996394 failed compileDebugKotlin at FaroV5BookmarkMutationHostBinding.kt:51, UNSAFE_CALL: nullable authority captured by local function. Exact scope unchanged: bind authority to a non-null local val after fail-closed admission, capture that local. No contract/owner/schema/path expansion.

Coverage delta: add deterministic post-COMMIT revocation via test-only MaterialWriteGate wrapper, assert UNKNOWN_RECONCILE, one durable receipt, and authorized replay without duplication. Existing tests remain. Next functional commit limited to same M01 permission and affected host/test/workflow paths. Guard on first code43014778404 SUCCESS; Auxiliary CAS8/8, authority5/5, schema and portability regressions PASS. M01 not certified; M02 not permitted.
