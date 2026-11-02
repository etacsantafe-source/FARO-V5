# FARO V5 S3 — NOCHE_A 20:00 · A1 owner discovery and safe seam

## Verificación read-only
Branch checkpoint before changes: `d2296680301dfecba448e961a226fb05426ed3e9`. N00 PASS A/B, P00 PASS_SPEC_ONLY, A1 PARTIAL_CI_VERIFIED. Reused green CI #41538658363 (31/31) and #41539252466 (34/34), not rerun.

Inspected actual composition: `FaroV5BootstrapActivity.showShellSurface()` calls `FaroV5SurfaceHost.install()` with Workspace, Timeline, Composer, Review, Selection, Apply, Assistant; **does not pass a Home owner**. `FaroV5CompositionRoot` exports active Workspace/Timeline, but no N-022 home.read, N-031 attention.read, N-032 notice.read or auxiliary Guardados count owner. Catalog names are contracts, not proof of runtime bindings. V4.4 Home materiality and Radar refresh cannot be treated as canonical V5 Home data. A real 4-count binding remains **BLOCKED_BY_MISSING_CANONICAL_OWNERS**, not fabricated.

## Bounded code
Added `FaroV5HomeReadCoordinator` in surface, a read-only composition seam accepting independently authorized owner read ports. Each unavailable, failed or revoked source remains UNKNOWN, not zero; a valid real zero is preserved. Cancellation propagates. Tests cover zero vs unavailable, partial owner failure, cancellation and empty owners. This does **not** activate app V2, introduce OperationIds, claim canonical sources, or wire a fake owner into bootstrap.

## Next
Verify directed CI and XML for this exact SHA. A1 remains PARTIAL until canonical owners and owner-generation/ACL semantics are implemented and bound; do not mark PASS based on seam tests. Only then A2/A3/A4 according to dependencies. A3 +8 candidate remains BLOCKED_FOR_BOUNDED_REOPEN. X01-X12 and physical campaign NOT_RUN.
