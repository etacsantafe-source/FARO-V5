# FARO V5 — S3.A3 Guardados native read host — B7 final PASS (scoped)

Unit: `S3_A3_BOOKMARKS_READ_HOST_NATIVE`.
Date: 2026-11-11 UTC / 2026-11-10 Argentina.
Final functional SHA: `a6f9a6b7b52117de2ce0d668f155ca4cac4a6541`.
Parent implementation: `916e8a82705a666875d460ff5084797b02246f89`.
Preaudit: `docs/s3-night/S3_A3_BOOKMARKS_READ_HOST_B1_B5_20261111.md`; B1–B5 proof blobs/markers verified against permit; authorized path set exact.
RED_FIX record: `docs/s3-night/S3_A3_BOOKMARKS_READ_HOST_B7_RED_FIX_20261111.md`.

## B7 — Independently verified final disposition: PASS for isolated DEV READ ONLY

1. **Changes and limits:** first implementation `916e8a8` affected exactly nine preaudited paths. It mounts a native DEV-only Guardados button and metadata-only presentation; canonical source validity is checked by the real Workspace owner, while Auxiliary supplies saved-item metadata on its own existing SQLite connection and lock. Separate local-install read authorities for BOOKMARK_METADATA and WORKSPACE_MESSAGE, whole-generation pointer and host epochs, foreground grant only on explicit action, and onPause/close revocation. The read closure never exposes a mutation port or creates an implicit writer. No shipping CompositionRoot, productive V2, catalog, Reality, Apply, main, V4.4, network, scheduled Auto/Noche or G15 physical changes.
2. **B1–B5:** use `S3_A3_BOOKMARKS_READ_HOST_B1_B5_20261111.md` for evidence, V4.4 comparison, actual external Android/SQLite/Chromium sources, unified scoped design and adversarial negatives. Same SHA read-authority tests cover absent grants, default-denied mutation, revocation/regrant, pointer ABA, stale owner, invalid target and mid-read revocation. No code copied from external libraries.
3. **Red fix without relaxing assertions:** original run `42929324277` failed D8 heap on Android dex merge, including rerun. Recorded `RED_FIX` and amended the permitted native read-host workflow ONLY in `a6f9a6b7`: `-Xmx2g`, metaspace 512 MiB, max-workers 1 in APK step. App Kotlin and tests unchanged.
4. **Exact final CI** on `a6f9a6b7b52117de2ce0d668f155ca4cac4a6541`:
   - Directed run `42935422794` conclusion `success`; job `bookmark-read-host` SUCCESS.
   - Real two-owner read authority tests: `FaroV5BookmarkReadHostBindingT2Test 6 PASS`.
   - Native metadata projection: `FaroV5V2CoreDevBookmarksPresentationT2Test 3 PASS`.
   - Local installation/read authority: `FaroV5LocalInstallReadAuthorityT2Test 10 PASS`.
   - Exact JUnit XML verification step succeeded with 19 tests, zero failures/errors/skips, log marker `S3_A3_BOOKMARK_READ_HOST_NATIVE_PASS 19/19; nine new; DEV APK built`.
   - Android DEV APK assemble step SUCCESS; logs show `BUILD SUCCESSFUL`.
   - Protocol guard run `42935422815` conclusion `success` on the identical SHA.
   - Independent isolated DEV APK workflow for original implementation `42929324244` also SUCCESS; not used to substitute required native directed gate.
5. **Negative claims prohibited:** This PASS is **read-only native DEV unit scope**, not full Guardados/A3 completion. Mutation UI/Chat Review, source navigation/focus and complete X01–X12 integrated acceptance remain open; no genuine physical G15 gesture/lifecycle campaign was run. No production V2 authorization or globally complete bookmark count.
6. **Next independent unit:** B1–B5 for explicit Guardados mutation+receipt/Review and exact source navigation, reusing Auxiliary CAS/idempotent receipts and Workspace source authority; a separate bounded permit and exact CI before new functional changes. Keep one writer and physical tests deferred to final campaign.

Final disposition: **B7 PASS_SCOPED**, checkpoint allowed; broader S3.A3 and S3.A5 still OPEN.
