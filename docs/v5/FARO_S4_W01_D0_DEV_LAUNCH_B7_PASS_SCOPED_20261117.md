# FARO V5 — S4 W01 D0 launcher Finance DEV exact application ID — B7 PASS acotado
Fecha: 17/11/2026 (Argentina).
Unidad: `S4_W01_D0_ISOLATED_DEV_LAUNCH_PACKAGE_ALLOWLIST`.
**RESULTADO: B7 PASS_SCOPED_DEV_PACKAGE_ALLOWLIST_JVM8_ANDROID_COMPILATION**, no prueba de arranque físico G15 ni dos PID, no W1 cerrado.

## B1 — Evidencia y límite
- HEAD funcional inicial `1f05ed3a28db2f66a86fcb8c394c83dcfe035323`, autorizado por permiso `READY` exact3 en su padre `7c1cf1d2d81e862c99918a182b3bf7ebf28ae3af`, guard `44164343858` SUCCESS.
- Primer run dirigido `44164344121` FAILURE en task Gradle inexistente: `:app:testV2CoreDevUnitTest` sin opt-in de AGP9. El código de test no llegó a ejecutarse. B7 RED_FIX documentado antes de nuevo commit `docs/v5/FARO_S4_W01_D0_LAUNCH_B7_RED_FIX_20261117.md` blob `cf7cdda795d3c2a7c4377b9923178b4576934f47`.
- Fix mismo unit `014372cc071ff0eddf704b82066f93bb86ec480e`, parent `b03e708b3e3d864a7a45a488399aa79a7a17996a`, añade sólo `-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false` en workflow D0, basado en workflow V2 ya utilizado. No cambia Gradle/manifest/allowlist ni UI.

## B2 — Auditoría de diffs exactos
- `7c1cf1d2..1f05ed3a`: EXACT3 archivos: `FaroV5V2CoreDevActivity.kt` (+ constante package W01 DEV y igualdad estricta); `S4W01DevLaunchPackagePolicyTest.kt` (8 pruebas); workflow `faro-v5-s4-w01-d0-isolated-launch-allowlist.yml`.
- `b03e708b..014372cc`: EXACT1 archivo workflow, 1 línea agregada y 1 reemplazada. La política de aceptación mantiene exactamente los IDs previos v2coredev, g15isolated, s3integrateddev y añade sólo `ar.com.faro.alpha.w01financedev`.
- Ninguna alteración a V4.4/main, Reality9, grants, ledger, provider, FBS2, app shipping, permisos Internet, V2 productiva ni W2.

## B3 — Pruebas por SHA real
- Run dirigido GitHub Actions `44165550511`, job `134330497532`, SHA exacto `014372cc071ff0eddf704b82066f93bb86ec480e`: **COMPLETED SUCCESS**.
- El step de preflight padre (`b03e708b3e3d864a7a45a488399aa79a7a17996a`) emitió `FARO_PROTOCOL_PREFLIGHT_PASS S4_W01_D0_ISOLATED_DEV_LAUNCH_PACKAGE_ALLOWLIST`.
- `:app:testV2CoreDevUnitTest` con opt-in AGP9: `BUILD SUCCESSFUL`; validación explícita del XML JUnit `8 tests / 0 failures / 0 errors / 0 skipped` imprime `W01_D0_EXACT_PACKAGE_POLICY_8_OF_8_PASS`.
- Compilación `:app:compileW01FinanceDevKotlin` y `:app:compileV2CoreDevKotlin` con manifests: `BUILD SUCCESSFUL`, step de inspección verde `W01_D0_W01_PRIVATE_PROVIDER_V2CORE_ZERO_PROVIDER_DEV_COMPILES_PASS`.
- Guardia protocolo del código `44165550515` COMPLETED SUCCESS.
- No atribuir pruebas físicas, captura G15, sensor biométrico, SQL inter-PID, notificaciones ContentObserver ni permisos financieros a esta suite.

## B4 — Riesgos residuales
- El launch en un Moto G15 no fue ejecutado en este commit; sólo se validó aceptación de applicationId y compilación de ambas variantes. Activity puede fallar por otras causas no cubiertas.
- La notification IPC entre dos PID no está probada; D1A con B1–B5 preauditada `docs/v5/FARO_S4_W01_D1A_TWO_PID_INVALIDATION_B1_B5_20261117.md` requiere B5 independiente y READY individual.
- D1B de dueño real de lectura, reconciliación de lifecycle y consentimiento sigue pendiente. Ningún callback Android es una autorización de render.
- La guardia documental del commit `c5e532b379a985fb3a2923a372677d393b728139` run `44166383310` falló en su verificación de preflight porque el permiso seguía `READY` con `last_code_review.sha` apuntando al fix anterior `1f05...` mientras `014372...` aún no contaba con B7. No fue regresión funcional; al cerrar este B7, se consume permiso a BLOCKED y se revalida guardia de HEAD. No declararla verde hasta comprobar resultado.

## B5 — Prohibiciones preservadas
- V2 productiva OFF; W2 STOP; Auto/Noche OFF, sin API paga, identidad humana no autenticada, sin balances ni ledger y sin cambios a producción.
- El B7 previo de C y sus 16 JVM NO se repite. S2/S3 y aplicaciones G15 instaladas intactas.

## B6
IMPLEMENTED `1f05ed3a...`, RED_FIX mínimo `014372cc...`; exact8 JUnit, compiles de dos variantes y manifest paso; sin pruebas Android reales.

## B7
**PASS_SCOPED_DEV_EXACT_W01_PACKAGE_ALLOWLIST_JVM8_TWO_ANDROID_COMPILATIONS**; permiso D0 consumido a BLOCKED. Checkpoint documental y guardia posteriores obligatorios para cerrar turno; writer RELEASED remoto verificado al final. W1 permanece OPEN.
