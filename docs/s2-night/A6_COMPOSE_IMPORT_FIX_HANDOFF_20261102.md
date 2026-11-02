# FARO V5 S2 — A6 Android Compose compile fix · 2026-11-02

Scope: isolated branch `work/s2-chat-finish-night-20261102`, no main/V1/V2 activation. S2.A6 OPEN 6/8; F1 (#4) and T4 (#8) pending.

## Verified failure and code correction

GitHub Actions [#41402146260](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41402146260) finished FAILURE. The JVM job SUCCESS includes 9/9 A5 consent XML; Android job failed at `:app:compileDebugAndroidTestKotlin` before instrumentation with `UNRESOLVED_IMPORT androidx.compose.ui.test.onAllNodes` at line 10 of `FaroV5F1IsolatedEntryInstrumentationTest.kt`. Removed only the unresolved top-level import; `compose.onAllNodes(...)` is provided by `ComposeTestRule` and remains unchanged. This is a targeted test compilation fix, not a physical PASS.

## Next run

This commit deliberately does **not** use [skip ci], to trigger one isolated directed CI on the latest A4 provider-UID fix and A6 test import fix. Previous branch runs completed; no overlapping CI was started manually. Confirm latest run ID and exact XML for 9/9 A5 JVM, 4/4 F1 JVM, and 5/5 Android; if red inspect failure. If all green, verify one debug APK identity, signing and SHA256 and write a minimal T4 physical checklist. Do not claim S2 8/8 without Moto G15 evidence.

Writer NONE / HANDOFF CLEAN on commit; V2 OFF; S3/S4 unopened. No Groq call or device access.
