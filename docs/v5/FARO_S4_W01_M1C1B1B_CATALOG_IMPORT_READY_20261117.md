# FARO V5 W01 M1C1B1B — READY test-only for missing BuiltInContractCatalog import
Fecha: 2026-11-17 Argentina. HEAD auditado `3feb4e41a24bccdff1f74f1bb44a4ba3f3555073`. Guardia documental `44061265226` SUCCESS SHA exacto. Unidad `S4_W01_M1C1B1B_FINANCE9_ANDROID_STAGE_GATE`.

## B1
CI `44060376069` FAILURE por unresolved reference `BuiltInContractCatalog` en único archivo test; regresión S3 Home `44060376148` mismo fallo de compilación, sin XML. Código fallido `b3f4086d1cf62b06a8ca50028c8eff5cda76c826`. RED_FIX B7 registrado en `docs/v5/FARO_S4_W01_M1C1B1B_CATALOG_IMPORT_RED_FIX_B7_20261117.md` con prueba exacta.
## B2
El test compilado `S4W01B2C0FinanceDevApplyHostTest.kt` usa `ar.com.faro.v5.kernel.runtime.*` y `BuiltInContractCatalog.create()`. En el actual test M1C1B1B, el import explícito del mismo package sólo incluye `PreparedPlan`; no aparece `BuiltInContractCatalog`. La comparación confirma falta de import, no de API.
## B3
Se revisaron los imports de test: `contracts.*`, `runtime.PreparedPlan`, `maintenance.*`, `finance.api.*`, `reality.*`, Path, JUnit. La alternativa de mínimo riesgo es `import ar.com.faro.v5.kernel.runtime.BuiltInContractCatalog`. No añadir dependencia ni migrar código, evitar wildcard y saltos de test.
## B4
READY solo para cambiar la ruta `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01M1C1B1BFinance9StageGateTest.kt` con UNA línea de import del catálogo. Ningún código productivo, workflow, Gradle, SHA previo, manifest, SQLite o UI. Ejecutar preflight exacto antes de escribir, writer CAS, FARO-UNIT en commit.
## B5
- Verificar diff único de import; compilar el test y exigir nueva CI dirigida exacta con XML 5 Reality9 + 6 app + 8 legacy = 19/19 y regresión S3 Home afectada. Inspeccionar otra vez la suite si error nuevo, no cazar imports sin análisis.
- Sin autenticación física real, ni Android shipping, ni W1 B/C/D/E/F, ni W2; NO certificar product Apply o G15.
- Writer liberado antes de CI, guard exact SHA, B7 independiente tras pruebas. Si falla preflight, STOP sin mutación.

## B6
NOT_RUN al publicar READY. Código autorizado sólo después de guard y preflight.
## B7
PENDING. READY para corrección de test, no es PASS.
