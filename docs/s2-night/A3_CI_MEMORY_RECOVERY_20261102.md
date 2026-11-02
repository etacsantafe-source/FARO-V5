# FARO S2 — A3: CI memory recovery (02/11/2026)

Branch: `work/s2-chat-finish-night-20261102`. Base CAS: `340b0fe11cd1f02ea41014fd76f9a9e0ccea0043`. Writer NONE / HANDOFF CLEAN. S2.A6 OPEN 6/8; V2 OFF; S3/S4 not opened.

## Actual failure (not a successful compile)

[Actions #41390549337](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41390549337) reached `:app:compileDebugJavaWithJavac` and logged `java.lang.OutOfMemoryError: Java heap space` at 2026-11-02T05:10:42Z, then was cancelled at 05:50:17Z. This is the first actionable build blocker; older runs also cancelled. Do not label any of them PASS.

## Implemented recovery

Changed only isolated S2 workflow: compile/JVM Gradle daemon heap 3 GiB, Kotlin daemon cap 1.5 GiB, one worker, serial tasks; Android emulator remains at 1.5 GiB Gradle heap and one worker. A single push of this workflow triggers the first CI attempt against the fully integrated A5 consent and Android test code at HEAD. No production Gradle configuration or source modified.

## Next work

Wait for the new run; check exact job logs and XML: A5 consent 9/9 JVM, F1 JVM 2+2, Android F1+ A5 5/5. If OOM persists, examine actual runner memory and Java compiler fork/daemon settings, then correct only affected workflow. Once green, verify debug APK identity/signature/SHA and perform remaining integrated physical T4 only with the user. Do not repeat already green SAF, do not use real Groq/phone, and do not certify 8/8 prematurely.
