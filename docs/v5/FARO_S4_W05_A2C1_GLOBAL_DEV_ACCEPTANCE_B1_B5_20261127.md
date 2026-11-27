# FARO V5 · W05-A2C1 GLOBAL DEV ACCEPTANCE · B1–B5

**Unidad aislada NUEVA:** `S4_W05_A2C1_GLOBAL_DEV_ACCEPTANCE`.
**Base exacta:** `4a124fa50febe0406761451c8a26864ca78b7212` en `work/s3-night-20261102`, 27/11/2026 (Argentina).
**Estado:** B1–B5 EVIDENCIA DOCUMENTADA PARA GUARDIA, B6 NOT_RUN y B7 NOT_RUN. El permiso A2C1F está CONSUMIDO/BLOCKED; este documento **no** habilita código ni supone cierre global.
**Frontera:** solo desarrollo SQL15 aislado, ninguna compra/stock/pago real, ningún teléfono G15, UI productiva, V2 productiva, Auto/Noche o main.

## B1 — evidencia real, certificaciones previas y brecha global

- HEAD remoto `4a124fa50febe0406761451c8a26864ca78b7212`; `docs/v5/FARO_CONTINUITY_POINTER.json` fase `S4_W05_A2C1F_B7_PASS_DEV_XML13_13_A2C1_GLOBAL_ACCEPTANCE_NEXT_PREAUDIT`, writer global RELEASED época 2179 antes de este documento. Último permiso `S4_W05_A2C1F_CONSUMPTION_UNDO_HOST_DEV` BLOCKED, B7 `PASS_SCOPED_13_OF_13`.
- **A · Review N-035:** B7 `docs/v5/FARO_S4_W05_A2C1A_B7_PASS_CONSUME_REVIEW_DEV_20261126.md` blob `2464bfc1bd8d711536cf4717f5da9225c4f76d71`; code `3885084a6447febde9586da2c5a02414e3c4aa40`; CI `45925067636` SUCCESS 8/8, guard `45925067509` SUCCESS.
- **B · escritura CONSUME SQL15:** B7 `docs/v5/FARO_S4_W05_A2C1B_B7_PASS_CONSUME_ATOMIC_SQL15_DEV_20261126.md` blob `fa1ba2664ad0142a1ad30bef2dc3366c33f77cb5`; code `09a7f72445b3a8a9c691d1a79096e6d468afb929`; CI `45928809060` SUCCESS 8/8, guard `45928809081` SUCCESS.
- **C · consumo Review→Confirm→Apply:** B7 `docs/v5/FARO_S4_W05_A2C1C_B7_PASS_CONSUMPTION_REVIEW_APPLY_SQL15_DEV_20261127.md` blob `167dd97e8d2152e7dbe2a5e87ef62a1b297f4983`; code `b322e5d166876c106f71eac9a734c62d31b55eda`; CI `45939930692` SUCCESS 5/5, guard `45939930719` SUCCESS.
- **D · Review Undo N-016 y origen confiable:** B7 `docs/v5/FARO_S4_W05_A2C1D_B7_PASS_CONSUMPTION_UNDO_ADMISSION_20261127.md` blob `3f663508bdcb1c400641a88aab80b62bf94d44a6`; code `97e3155e9dd619fdbe31dd2e2c7a88d2828c9ba4`; CI `45954154676` SUCCESS 9 nuevas + 10 regresión 19/19, guard `45954154596` SUCCESS.
- **E · Undo CONSUME_UNDO SQL15 FULL:** B7 `docs/v5/FARO_S4_W05_A2C1E_B7_PASS_SQL15_CONSUMPTION_UNDO_ATOMIC_DEV_20261127.md` blob `e44fc7ea715efdde49ac2443e7f154ce449fac20`; code `7a9898fc4cd8767278bbcb2ee5ab600c40067350`; CI `45956198815` SUCCESS 8 nuevas + 8 regresión 16/16, guard `45956198842` SUCCESS.
- **F · host Undo autenticado:** B7 `docs/v5/FARO_S4_W05_A2C1F_B7_PASS_AUTHENTICATED_CONSUMPTION_UNDO_HOST_DEV_20261127.md` blob `2c826d1e56426773d21e2dd15fc8a29985e47c62`; code RED_FIX final `15a47470655c06ebaab4422fdb2c5811da067105`; CI `45959680590` SUCCESS 8 nuevas + 5 regresión 13/13, guard `45959680644` SUCCESS. La CI inicial F 12/13 FALLÓ en aserción ABA y fue reparada; no ocultar.
- **Brecha:** los seis B7 individuales validan códigos/fixtures distintos y SHAs distintos. **No existe** aceptación DEV global independiente de A–F ejecutada conjuntamente en el HEAD final con pruebas XML originales y APK V2CoreDev aislada. La aceptación debe probar además que `undo.execute` no hereda autorización `stock.consume`, ningún COMMITTED se inventa, stock físico/reservado no se desincroniza, Undo FULL una sola vez y recuperación sin segundo Apply.

## B2 — reutilización V4.4 y V5 sin repetir código certificado

- Comparación V4.4: `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md` (blob `4b9789004459eed6e95bc4d4f8032ee3b4c9f340`); mantener contabilidad compra / recepción / consumo separada, revisión humana y recibo por mutation; no sustituir stock por evidencia comercial, ni enviar movimientos a FINANZAS/pagos.
- Reusar **sin reimplementar**: A `HouseholdStockConsumptionDevAdmissionTest`, B `RealityHousehold15ConsumptionApplyPortTest`, C `S4W05StockConsumptionDevApplyHostTest`, D `HouseholdStockConsumptionUndoDevAdmissionTest` + `HouseholdStockUndoDevAdmissionTest` regresión N-016, E `RealityHousehold15ConsumptionUndoApplyPortTest`, F `S4W05StockConsumptionUndoDevApplyHostTest`.
- Reusar compilar `:app:assembleV2CoreDev` por el pipeline existente A2B1C. **Sin modificaciones** de fuentes `v5/app/src/main`, `v5/store`, `v5/owner`, esquema SQL15 o suites B7 anteriores; esta unidad es auditora, no una nueva funcionalidad.
- `AGENTS.md` y `FARO_PROTOCOL_WORK_PERMIT.json` requieren nueva B1–B5 propia, exact1, guardia, writer único CAS, B7 tras resultados del mismo SHA.

## B3 — alternativas externas verificables y elección

Fuentes públicas revisadas el 27/11/2026:
- SQLite, https://www.sqlite.org/lang_transaction.html : `BEGIN IMMEDIATE` puede rechazar escritura por `SQLITE_BUSY`; `ROLLBACK` evita publicar compensación parcial. Usar la cobertura existente en B/E/F y no simular atomicidad a partir de un test de presentación.
- SQLite, https://www.sqlite.org/isolation.html : WAL y orden observable de commits; no inferir exactamente-once sin idempotencia/recibo persistente.
- Android, https://developer.android.com/topic/architecture/data-layer/offline-first : realidad local como fuente de verdad. Mantener DEV aislado y no activar red/WorkManager.
- GitHub, https://docs.github.com/en/actions/tutorials/store-and-share-data : usar `actions/upload-artifact@v4` para preservar XML **originales** y APK aislada de la misma ejecución.
Alternativas: (A) solo unir green checks de seis CI anteriores (DESCARTAR: distintos SHAs y contexto); (B) reescribir las suites certificadas A–F en un nuevo test fixture (DESCARTAR: repetición y riesgo de ensayar lo mismo); (C) orquestar **en HEAD actual** suites existentes A–F, controles de descriptor, comprobación exacta de JUnit XML y build V2CoreDev (ELEGIR, mínimo reproducible); (D) instalar/mutar Moto G15 o cambiar `main` (DESCARTAR: falta aceptación física/autorización nueva).

## B4 — alcance de B6 mínimo, una sola ruta exacta

Candidato de permiso `READY` posterior a guardia (**aún NO READY**):

1. `.github/workflows/faro-v5-s4-w05-a2c1-global-dev-acceptance.yml` (NUEVA, única ruta). Push por esta ruta y branch de trabajo, `permissions: contents: read`. Preflight padre `S4_W05_A2C1_GLOBAL_DEV_ACCEPTANCE` exact1 `FARO-UNIT`. Java21, Android SDK 37.0 alias37, Gradle secuencial de siete clases agrupadas en tres tareas independientes:
   - `:owner:household:test` con A 8, D 9 y Undo legado 10.
   - `:store:reality:test` con B 8 y E 8.
   - `:app:testV2CoreDevUnitTest` con C 5 y F 8, y `:app:assembleV2CoreDev`.
   - **Total esperado 56 casos JUnit originales**, 0 failures/errors/skips, siete XML individuales con nombres únicos, sin `tests=0`. No aceptar silencio, archivos stale ni artefacto ausente.
   - Auditoría del catálogo tipado `N-035 stock.consume household`, `N-016 undo.execute undo-coordinator` sin operaciones artificiales; checksum/bytes de la APK DEV y XML en artefacto. Reportar pasadas por clase, SHA, run y digest; no reclamar G15 físico.
   - Una sola job `global-sql15-consumption-dev` a lo sumo 75 minutos, sin runner privilegiado, sin pagos/Internet de aplicación. Guard de 7 bloques en paralelo por push.
No añadir nuevos tests a archivos A–F. Si gradle falla, RED_FIX documentado de esta unidad y solo workflow, con guardia antes de corregir.

## B5 — auditoría adversarial previa: matriz de puertas binarias

- **G01** pruebas A–F + Undo legado existen en la misma rama y corren en **una sola** ejecución CI nueva del HEAD; no atribuir resultados anteriores.
- **G02** cada uno de siete XML tiene `tests>0`, los conteos esperados **8/9/10/8/8/5/8**, `failures=errors=skipped=0`, casos únicos y archivo exactamente uno. Total **56/56**. Si falta un XML, STOP.
- **G03** Gradle Unit Test `owner`, `reality` y `app V2CoreDev` secuenciales terminan SUCCESS; no compilar sin correr.
- **G04** `:app:assembleV2CoreDev` entrega APK DEV concreta, `*.apk` tamaño positivo, sin instalarla ni confundir build con campaña física.
- **G05** descriptor N-035 y N-016 del catálogo corresponden a ejecutores distintos; no crear función de auto-Undo ni conceder permiso producto.
- **G06** root lease, journal COMPLETED SQL15, cambio de epoch, pointer ABA, doble Undo, origen forjado, stock reservado y crash recovery se observan en tests de F/C/E ya existentes; no inferir cobertura de otras rutas no probadas.
- **G07** no modificación de `RealityStore`, SQL15, host, grants, finanzas ni V4.4. Nuevo workflow único, padre permit exact1 y guard SUCCESS.
- **G08** si cualquier CI, XML, APK, preflight o guardia es roja, B7 RED_FIX/PENDING, sin cerrar A2C1.
- **G09** B7 PASS únicamente después de verificar run workflow/guardia HEAD exacto, XML original y artifact, scope, y cierre documental con permiso consumido, writer RELEASED.
- **G10** A2C1 integral DEV PASS no implica A2C2, W05-A2C global, instalación en G15, API cloud, stock real, pagos ni V2 productiva; esos requieren aceptación específica posterior.

**Puertas:** publicar SOLO documento B1–B5 con writer CAS → liberar → guardia documental SUCCESS → adquirir nuevo writer y publicar permiso `READY` exact1 en commit solo documental → liberar → guardia permiso SUCCESS → nuevo B6 único workflow + CI → B7/artefactos originales → consumir permiso y checkpoint, writer RELEASED. Cualquier scope distinto se reaudita antes de escribir.
