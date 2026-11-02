# FARO V5 — Noche A3 final: real CI blocker corrected

**Branch** `work/s2-chat-finish-night-20261102`. **Functional source** A5/Chat from `340b0fe11cd1f02ea41014fd76f9a9e0ccea0043`. **Workflow fix** `2e2413121f2a32c1cce6199870621424a084ba6c`. Writer NONE; handoff CLEAN. V2 OFF, main unchanged, S3/S4 unopened, S2.A6 OPEN 6/8.

- CI [#41390549337](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41390549337) was CANCELLED after a real `:app:compileDebugJavaWithJavac` `OutOfMemoryError: Java heap space`. Not PASS.
- A3 implemented bounded 3 GiB Gradle heap, Kotlin 1.5 GiB, one worker for compile/JVM; emulator remains 1.5 GiB/one worker. The first A3 workflow commit accidentally contained literal escaped newlines in comments, yielding no-job YAML failure [#41398800844](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41398800844). Corrected in `2e241312`, verified workflow recognized by GitHub.
- New single CI [#41398844415](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41398844415) triggered for current Chat+A5 code. Status at handoff: **in_progress**, conclusion **not yet known**. Do not assert PASS until logs and XML are verified.

**Next B3:** first inspect this run, not historical cancelled runs. If red, fix actual blocker and rerun only changed gates. If green, check F1 JVM 2+2, A5 JVM 9/9, Android 5/5 and the isolated APK package, signature and SHA. Then prepare one minimal Moto G15 test, no repeated SAF. No real Groq/Android consent while user sleeps.
