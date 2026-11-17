# FARO V5 — W01 M1C1B1B · RED_FIX B7 por import faltante del catálogo
Fecha: 2026-11-17 (Argentina). Rama work/s3-night-20261102. HEAD auditado `174c4708fdbb3eb50d8b5a35388e9b16b89e1cd8`.
Unidad: `S4_W01_M1C1B1B_FINANCE9_ANDROID_STAGE_GATE`. Estado RED_FIX, NO PASS.

## B1 — Evidencia exacta de la segunda falla
- Código original B6 `bcd29ccabdf1e4f6144aadaadee2bef578f49616` no compilaba por `PreparedPlan` no importado; red fix documental previo `docs/v5/FARO_S4_W01_M1C1B1B_PREPARED_PLAN_IMPORT_RED_FIX_B7_20261117.md`.
- Código corregido `b3f4086d1cf62b06a8ca50028c8eff5cda76c826` cambia EXCLUSIVAMENTE una línea import de test `S4W01M1C1B1BFinance9StageGateTest.kt` (`ar.com.faro.v5.kernel.runtime.PreparedPlan`). Ningún código de producción se tocó en ese cambio. Guardia `44060376145` SUCCESS.
- CI dirigida `44060376069` COMPLETED/FAILURE sobre `b3f4086d...`: primera fase Reality9 bridge + legacy 8 pasó, segunda fase `:app:compileDebugUnitTestKotlin` falló `Unresolved reference 'BuiltInContractCatalog'` en línea 31 del **mismo archivo test**. Sin XML final 19/19, B7 NO PASS.
- Regresión S3 Home notice `44060376148` COMPLETED/FAILURE sobre la misma SHA, misma única causa de compilación compartida en el test; los pasos de XML/manifest no se ejecutaron. No afirmar regresión S3 verde ni atribuir falla de lógica de S3.

## B2 — Comparación real de fuentes y localización
- Test previo B2C0 PASS usa `import ar.com.faro.v5.kernel.runtime.*` y `BuiltInContractCatalog.create()`, demostrando que esta referencia pertenece al módulo kernel.runtime, NO kernel.contracts. El test actual importa `ar.com.faro.v5.kernel.contracts.*` y `PreparedPlan` aislado de runtime, por lo que el símbolo BuiltInContractCatalog queda fuera de alcance.
- Comprobados imports actuales: contracts.*, runtime.PreparedPlan, maintenance.*, finance.api.*, reality.*, JUnit, Path. No hay evidencia de cambio de API ni de dependencia; evitar modificar producción, Gradle o catálogo.

## B3 — Investigación y causa raíz revisadas tras dos fallas consecutivas
- Kotlin imports son explícitos por paquete; `import ar.com.faro.v5.kernel.runtime.PreparedPlan` no importa otros nombres del mismo paquete. El test B2C0 usa `runtime.*` y compila en CI histórica.
- Alternativa USAR: una sola línea `import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog` en el test. Alternativa DESCARTAR: wildcard indiscriminado, mover clase, modificar Gradle, duplicar catálogo o eliminar asserts.
- Unresolved references consecutivas obligan a detener ensayos genéricos y revisar integralmente B2/B3/B5: esta auditoría documenta la familia de imports explícitos del archivo y su comparación con tests certificados.

## B4 — Delta único
Ruta funcional exacta única ya existente:
`v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01M1C1B1BFinance9StageGateTest.kt`.
Añadir import de `BuiltInContractCatalog` inmediatamente después del de `PreparedPlan`. NO modificar la línea de `PreparedPlan`; no tocar Kotlin productivo, workflows, manifest, build logic, APIs, Android, SQL, UI ni otras pruebas.
Preflight futuro de esa ruta solamente y commit funcional distinto con `FARO-UNIT:S4_W01_M1C1B1B_FINANCE9_ANDROID_STAGE_GATE`.

## B5 — Auditoría adversarial de la corrección
- Verificar que la diferencia del nuevo código sea una única línea en el test y que la compilación de app complete, sin introducir ignorados/skip.
- Ejecutar CI dirigida exacta que cuenta XML Reality9 nuevos 5/5 + host DEV 6/6 + legacy grant receipt 8/8 = 19/19 y regresión S3 Home notice afectada por compilación, con SHA exacta.
- Si vuelve a fallar, inspeccionar causa completa antes de otra mutación. No marcar B7 PASS por el éxito parcial Reality.
- Los 19 tests JAMÁS certifican BiometricPrompt físico G15, identidad humana productiva, grant issuance auténtico ni app de producción; B7, si pasa, será sólo DEV-STAGED9. W1 B/C/D/E/F OPEN; W2 STOP.
- Anterior permiso exact1 consumido BLOCKED; para corregir será necesario emitir READY independiente **posterior** a guard documental, con la ruta única y B1-B5 probados. Writer CAS y liberación durante CI.

## B6
PENDIENTE, import aún NO aplicado a la fecha de este documento.

## B7
**RED_FIX** en `b3f4086d1cf62b06a8ca50028c8eff5cda76c826` por import faltante `BuiltInContractCatalog`; no declarar PASS. Esta disposición habilita únicamente una corrección de test de la misma unidad después de READY independiente. El resultado de CI siguiente decidirá PASS o nueva auditoría.
