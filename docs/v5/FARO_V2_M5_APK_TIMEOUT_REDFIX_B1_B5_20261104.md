# M5 APK timeout RED_FIX — 2026-11-04

Unit M5_DEV_APK_PACKAGE_GATE. Exact functional SHA 9bd648415718070bdda0465ab26431e36eda95d4; previous durable HEAD f93986c83c33ca287058aec3c27c6806d653eb7a.

## B7 RED_FIX
CI 41899136921 CANCELLED after 30 minute job cap (started 19:40:03Z and completed 20:10:23Z). SDK PASS; assembleV2CoreDev cancelled, APK verification skipped, upload skipped; zero artifacts. Last logged completed task :app:mergeExtDexV2CoreDev 19:44:46Z and last task begun :app:compileV2CoreDevKotlin 19:45:40Z, then silence until cancellation. There is no evidence of compiler error, OOM, or specific daemon deadlock. Guard 41899136517 success. M5 B7 NOT PASS, APK NOT VERIFIED, V2_DEV_CORE_READY false.

## B1 EVIDENCIA
Workflow uses timeout-minutes 30 and Gradle --max-workers=2 without detailed diagnostics. Prior M4 direct CI 41896913438 did compileV2CoreDevKotlin and manifest tests successfully, so do not reopen M4. The APK artifact does not exist.

## B2 COMPARACIÓN
Reuse successful M4 Android DEV build graph and exact existing binary verifier, without modifying it. V4.4 is productive and has OS grants; do not copy its package, components, or permissions to isolated DEV. Earlier V4.4 read-only comparison docs/v5/FARO_V2_RETRO_B1_B5_M0_M3_20261104.md remains applicable.

## B3 INVESTIGACIÓN
ADAPT: Kotlin compiler in-process Gradle property as bounded experiment. Official https://kotlinlang.org/docs/compiler-execution-strategy.html . USAR: max-workers=1, Gradle JVM 3072m heap/768m metaspace, --info --stacktrace --console=plain, timeout 1200 seconds and diagnostics on failure. Official Gradle command-line https://docs.gradle.org/current/userguide/command_line_interface.html . DESCARTAR just raising overall timeout and silently wasting CI minutes. If it fails, read diagnostics, do not blindly retry.

## B4 UNIFICACIÓN
Edit ONLY .github/workflows/faro-v5-v2-core-apk-directed.yml, keep assembleV2CoreDev, strict AAPT2 package/launcher/zero permissions, apksigner, SHA256 and artifact conditioned on PASS. Restrict Gradle step to 20 min and leave job cap at 30 min. No app/manifest/production changes.

## B5 AUDITORÍA
Bounded workflow-only CI experiment. Fail closed: timeout exits nonzero; artifact cannot publish if build or inspection fails. Compiler in-process is a hypothesis, not proven cure; heap and one worker can reduce concurrency but may hurt speed. Preserve M4 6/6 JVM, T4 7/7, S3 25/25, writer CAS, S2 frozen 8/8, S3 approx 40% development and 0/5 closed, S4 OFF, Auto/Noche/V2 productive OFF. No Android physical or paid API. B6 code only after READY permit and verified preflight.

## B6 CORRECTION
PENDING new preflight and scoped workflow change.

## B7 FINAL
Previous code B7 RED_FIX, corrective code and CI not yet done.
