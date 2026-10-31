# FARO V5 — Noche A V1.3 · turno 3 · 31/10/2026 00:43 Argentina

- Writer: NOCHE_A_V13_TURN3_20261031_0043, epoch 22, acquired by CAS from 4b541779699218352320f817f02c6e8f29ad3b26, release requested by this checkpoint CAS. Verify branch ref and RUN_STATE writer NONE before asserting release.
- Observed duration: 03:43:46–03:45:51 UTC, ~2.08 min; no simulated waiting. Code HEAD bc1a80beb441258f87926fa53c1cffbf32b101db.
- CI #41163095797 (074b975) FAILURE: SQLite directed XML 2/2 PASS; JVM directed XML 2/2 PASS; Android 3 cases, 2 FAILED (Composer/Review timeout). Android job 124424745434, ~14.37m wall.
- CI #41163252426 (b96d86a) FAILURE: SQLite 2/2 PASS; JVM 2/2 PASS; Android 3 cases, 2 FAILED (Composer timeout in two tests). Android job 124428013541, ~22.85m wall.
- Diagnosis from actual Android semantics tree: drawer contains "Nuevo chat" while main Chat displays "No hay una conversación activa"; canonical Workspace port snapshot active id is correct. Hypothesis: retained Navigation3 NavEntry captured initial null selection.
- Commit bc1a80beb441258f87926fa53c1cffbf32b101db: read rememberUpdatedState(workspaceProjection?.activeConversationId) inside NavEntry, preserve canonical selection and UI ownership; add two projector assertions in Android instrumentation. V2 remains OFF.
- New Actions #41165731391 SHA bc1a80beb441258f87926fa53c1cffbf32b101db: IN_PROGRESS / WAIT_CI, do not cancel, no PASS assumed. Auto-triggered by one meaningful functional batch.
- Criteria 1–3 CERTIFIED; 4–7 OPEN; 8 T4 NOT_RUN; S2.A6 3/8. F1 still requires Android UI XML, consented provider success and real OS Delivery. No S3/S4/cutover, no main/history/V4.4/FARO Auto edits.
- Next: verify exact terminal run #41165731391, jobs and XML; if failed inspect new semantics and change hypothesis. Then finish F1, F2, F3 option A, F4. No physical T4 simulation.
