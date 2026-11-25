# FARO V5 · W05-A1C2E · SQL13 ACTIVE read-only trusted DEV admission

**Alcance:** exclusivamente perfil de lectura SQL13 ACTIVE en raíz DEV cerrada, ya seleccionada por una autoridad posterior; NO cutover, escritura de stock, permisos Apply, Android ni uso productivo. Checkpoint anterior `3ab3fd9dd6f20845b8adfd89a5e710e2a47b6129`: A1C2D B7 PASS scoped, 24/24, guardia 45618068661 SUCCESS. Writer global CAS y ramo trabajo aislado.

## B1 — Evidencia viva
- `FaroV5Household13V10StagedReplay` autenticado v10, SQL13 compensation y readback de cinco almacenes B7; `RealityHousehold13CompensationRowsPort.captureConnection` valida rev/Undo/filas.
- `FaroV5Household12ActiveDevReadHost` y `FaroV5GenerationStoreGraph.openActiveHousehold12ForHeldDevHost` ya abren SQL12 ACTIVE mediante guardia de owner+puntero, WAL aware, sin conceder Apply sin plan verifier.
- `RealityStore` tiene staged SQL13 read-only pero no ACTIVE SQL13; `requireStrictV2StoreSchemas` y la apertura de `commercialEvidence2ActiveDev` no admiten su modo activo.

## B2 — Reutilización y separación
- Recuperar host DEV SQL12 y preflight `ActiveSqlitePreflight`, nunca `immutable=1` para ACTIVE WAL. Reusar `FaroV5Household13CompensationRowsPort.captureConnection` y datos completos de `RealityHousehold13CompensationRows`, sin otra fuente de stock.
- Respetar principio de S4 P02/P03: conservar journaling/AtomicApply y patrones V4.4 solo como referencia, sin copiar ni reescribir base histórica. No depender de Work, APIs pagas o nueva librería.

## B3 — Decisión acotada
- Incorporar bandera explícita `householdActive13Dev` SOLO en `FaroV5GenerationStoreGraph` bajo `activeGeneration=true`, `checkedPlanning8Active=true`, Evidence2 ACTIVE y gate de material writes. Preflight exacto SQL13 + cinco stores + enlace de compensación.
- `RealityStore` obtiene conexión `SQLITE_OPEN_READONLY|SQLITE_OPEN_NOFOLLOW` normal, exige fence antes/después, valida esquema13 y devuelve snapshot tipado de compensaciones. Modos STAGED13, ACTIVE12 y ACTIVE10 preservados, mutuamente exclusivos.
- Host nuevo exige lease exclusivo `finance9-dev-root.lock`, puntero completo igual y `ownerAdmission` vivo; `openActiveHousehold13ForHeldDevHost` es INTERNAL, no invocable desde Activity o Chat. Solo leer, sin binding N-016 ni operación Apply.
- Este bloque no certifica journal de selección: el futuro cutover SQL13 tendrá que verificar ticket COMPLETED, recuperación y pointer. Un test de selección DEV sintética no equivale a cutover autorizado.

## B4 — Cinco rutas exactas
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household13ActiveDevReadHost.kt` (nuevo)
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
3. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
4. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household13ActiveDevReadHostTest.kt` (nuevo)
5. `.github/workflows/faro-v5-s4-w05-a1c2e-sql13-active-dev-read.yml` (nuevo)

## B5 — Auditoría adversarial antes de implementar
- T01: SQL13 con compra10, recibos7+3+7, compensación7; snapshot ACTIVE devuelve histórico bruto17/neto10, dueño válido y sin cambiar SQLite.
- T02: pointer no seleccionado/ABA y modo incorrecto SQL12 STOP sin abrir grafo ACTIVE13.
- T03: revocación de `ownerAdmission` devuelve nulo y no lee datos.
- T04: root lease ya adquirido bloquea segundo host, cierre libera lease; sin credencial de Apply.
- T05: corrupted/invalid SQL13 o Evidence1 no satisface perfil Evidence2 ACTIVE; rollback del host sin exponer stream.
- T06: WAL activo debe leerse por modo normal (no immutable); doble reapertura no crea stock ni modifica original.
- JUnit JVM `:store:generation:test` selectivo, XML por SHA (cero failures/errors/skipped), comparar regresión v10 y v9, guardia por código. B7 solo al verificar run/artefacto. Mantener G15 NOT_RUN, V2 productiva/pagos/stock real/Auto/Noche OFF.

**No es READY ni PASS B7 por sí solo.** Permiso READY deberá emitirse por separado tras guardia documental y revisión de HEAD/rutas exactas.
