# FARO V5 — S4 W00-C RED_FIX · Visibilidad Kotlin entre módulos
**Fecha:** 2026-11-15 AR; unidad única `S4_W00_C_COMPOSITE_HOST_FAIL_CLOSED_TEST`. BASE `dbeb93134fa4dd6c9aee4d79423d8d304cef6796`, B7 anterior RED (no PASS).
**Writer:** global CAS epoch 921 DOC_ONLY; permiso W00-C previo READY para test+workflow, no W01-W11, no runtime ni owner Household. RED_FIX no amplía alcance.

## B1 — Error reproducido en CI exacta
Run W00-C `43767904800`, job `132977031650`, SHA `dbeb93134fa4dd6c9aee4d79423d8d304cef6796`, conclusion **FAILURE**. Guardia de protocolo `43767904697` SUCCESS pero es independiente del build.
Falla única de compilación `:app:compileDebugUnitTestKotlin`: `INVISIBLE_REFERENCE`; línea 163 `reality.integrityCheck()` en `S4W00CCompositeHostDenialTest.kt`. `integrityCheck()` es **internal** de `:store:reality`, no visible desde `:app`. No hay XML JUnit de W00-C; pruebas `NOT_RUN`, no 2/2.
B1–B5 original `docs/v5/FARO_S4_W00_C_COMPOSITE_HOST_FAIL_CLOSED_B1_B5_20261115.md`, blob `6e704577ec2debf9b8e3d06db26cd71d604f5e6a`, conserva todos los contratos y amenazas iniciales.
## B2 — Reuso y corrección mínima
No copiar ni exponer método `internal`, ni modificar RealityStore. Las aserciones ya existentes sobre `planningBackup.snapshot()` de **dos owners**, ausencia de `applyAttemptLookup` y `executionReceiptLookup`, y Workspace Review APPROVED comprueban los efectos de interés. El test previamente usado en `:store:reality` podía llamar `integrityCheck` por ser módulo propio; `:app` no.
## B3 — Alternativas y evaluación externa
A) **elegida** eliminar únicamente aserción `integrityCheck` de test de host en `:app`, conservar verificaciones públicas independientes de cero cambios. B) exponer `internal` como `public` o añadir API solo para test: RECHAZADO porque cambia production/runtime. C) mover test de host a `:store:reality`: RECHAZADO porque perdería acceso a host `:app`. Se conservan fuentes SQLite https://www.sqlite.org/lang_transaction.html y AndroidX https://developer.android.com/reference/androidx/sqlite/db/SupportSQLiteDatabase; error es de control de visibilidad Kotlin, no una debilidad de SQLite.
## B4 — Plan exacto RED_FIX y prueba
**ÚNICA ruta funcional** `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W00CCompositeHostDenialTest.kt`; eliminar UNA línea exacta `assertEquals("ok", reality.integrityCheck())`. Mantener los demás 2 escenarios, catálogo test local, workbook/Workspace, REAL Reality8, recibo e intentos. No tocar YAML, prod, owners, Gradle, catálogos ni modelos.
Reejecutar workflow existente `faro-v5-s4-w00-c-host-denial-directed.yml` por push del delta; aceptación exige compile Gradle SUCCESS, JUnit exacto `2,0,0,0`, nombres exactos, guard `seven-block-evidence` del mismo SHA y B7 independiente. Si RED otra vez, documentar nueva causa y STOP en esta unidad.
## B5 — Auditoría adversarial
No modificar assertions para simular PASS; solo quitar llamada inaccesible `internal`. Conservar denegación de dos nodos aprobados, cero PREPARED, cero receipt, cero mutación Owner Reminder/Monthly. Un PASS certifica solo fail-closed de este host, NO permisología de Composite Finance+Household, no W06/W07, no Android físico. W00 global OPEN.
## B6 — Plan de publicación
Publicar ESTE documento y permiso acotado de RED_FIX en el MISMO commit DOC_ONLY bajo writer 921. Luego liberar writer; para B6 funcional nuevo adquirir writer/confirmar HEAD, verificar preflight exacto y FARO-UNIT en commit separado. No CI declarada PASS antes de ejecutarse.
## B7 — Disposición del SHA fallido
**RED_FIX** (no PASS) para `dbeb93134fa4dd6c9aee4d79423d8d304cef6796`: `:app:compileDebugUnitTestKotlin` ERROR de visibilidad, 0 pruebas ejecutadas, XML no generado. Guardia protocolo PASS pero W00-C no certificada. Se permite exclusivamente corregir **la misma unidad** en la ruta exacta documentada; el nuevo SHA debe pasar CI y auditoría antes de continuar.
