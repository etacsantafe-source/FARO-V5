# S3.A2 DEV Search — ABA RED_FIX B1–B5 (04/11/2026)

**Same bounded unit:** `S3_A2_DEV_SEARCH_SESSION_OWNER`; **HEAD reviewed:** `83575bc551aab4f0a3df7b71d536ff0e401f57c0`. Shipping V2 OFF. The immediately previous code `8c01f4c4` has B7=RED_FIX, not PASS.

## B1 EVIDENCIA
- Full CI for `8c01f4c4` green: guard #41932138079, V2 DEV core #41932138084 (XML 9/9, T4 7/7, isolation 4/4), APK #41932138014 (signature v2, no permissions, SHA256 `0b05dacd...`), Home #41932137991 (25/25). Preserve prior findings.
- Static trace of `FaroV5V2CoreDevAuthority.ready`: checks target generation ID, final journal, replay fence and `alive` but NOT `FileActiveGenerationPointer.sequence`. `FaroV5V2CoreDevSearchReadAuthority.invalidate()` increments a flow version; no invalidations collector means old `publication` remains in adapter. B7 defect: after A→B→A, original `aclEpoch` and owner revision may match.
- Actual `FileActiveGenerationPointer.compareAndSet` persists strictly increasing sequence before notifying, so **use existing persistent fact**, no new store/schema required.

## B2 COMPARACIÓN
- FARO V4.4 search validates current domain/owner but has no Workspace V2 durable active-generation sequence; do not copy legacy implementation.
- FARO V5 already uses fresh canonical authority stamp and selection check. The missing extra component is **pinning the admission to the full durable pointer state**, not redoing search/ACL/Compose or migrating FBS2. Existing 9/9 tests must remain preserved.

## B3 INVESTIGACIÓN EXTERNA
- Previously documented external real-app and industry comparison: `docs/s3-night/S3_A2_G1_B3_EXTERNAL_REAL_WORLD_RESEARCH_20261104.md`.
- OWASP Authorization Cheat Sheet (https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html): revalidate authorization for every request and deny by default; reinforces pointer+permission recheck.
- Element X Android #7877 (https://github.com/element-hq/element-x-android/pull/7877): incorrect sign-out of another account; analogous negative: session must not regain identity because a reusable ID reappears. Element X issue #7988 (https://github.com/element-hq/element-x-android/issues/7988): result navigation stale; old hit must be rejected on new authority.
- Alternatives: **ADAPT** durable monotonic sequence already in FileActiveGenerationPointer (no dependency) vs **REJECT** UI listener as only revocation, UUID rotated by UI, generation ID reused as sole epoch, new persisted ACL table or Room invalidation as permission.
- No external code imported; license/cost/dependency delta ZERO.

## B4 UNIFICACIÓN
- In `FaroV5V2CoreDevAuthority.kt`, read `FileActiveGenerationPointer` **before** opening active graph; pin `sequence`, verify `generationId==targetId`; after open, `ready` checks the exact same pinned `sequence` in addition to existing journal/fence/ready guards. If A→B→A happens before/during open, fresh grant is refused; after open, old session remains denied indefinitely until new verified host opens.
- Add a new single test in existing `FaroV5V2CoreDevAuthorityTest.kt`: publish hit, **without subscribing to invalidations**, CAS A→B→A, assert search UNAVAILABLE and stale hit cannot select. Include new pointer sequence > pinned. Reuse existing test fixture root, no actual device.
- Update exact XML oracle in `.github/workflows/faro-v5-v2-core-directed.yml` from 9/9 to 10/10. Other 9 tests remain. Exact paths only those 3. No Android permissions/manifest, APK signature or V2 flags affected.

## B5 AUDITORÍA PREVIA ADVERSARIAL
- Failure injection: A→B→A should be rejected irrespective of listener delivery; A→B alone already covered. Sequence strictly monotonic from durable pointer implementation; no ABA if sequence changes and graph remains open. New host after restart intentionally mints a distinct UUID while rechecking current pointer/journal.
- Do not widen grant, ignore recovery/fence status, create a second owner, backport to shipping, change SQLite schema/FBS2, edit manifest, publish APK to device or touch user data. No extra network dependency.
- B6 authorized **only** with distinct READY permit, CAS writer, preflight; new exact CI and 10/10 before B7. Any green original CI is historical B7=RED_FIX and cannot certify corrected code.
- **B5 READY FOR SAME-UNIT RED_FIX ONLY**. G1 shipping OPEN; no formal stage closure implied.
