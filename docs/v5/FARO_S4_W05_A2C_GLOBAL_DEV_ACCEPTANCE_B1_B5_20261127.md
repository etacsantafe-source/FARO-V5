# FARO V5 — W05-A2C GLOBAL · B1–B5 auditoría DEV de cierre (27/11/2026)

**Unidad independiente:** `S4_W05_A2C_GLOBAL_DEV_ACCEPTANCE`. **Base:** `de30d1c2b65618e27bdc03fa3d391e8ea48c47c9`, rama `work/s3-night-20261102`.
**Estado:** B1–B5 documentados para guardia; B6 NOT_RUN; B7 NOT_RUN; permiso anterior A2C1 GLOBAL CONSUMED/BLOCKED. Documento de planificación: por sí solo NO habilita código ni cierra A2C.

## B1 — Evidencia, frontera y brecha concreta

- Fuente de alcance `docs/v5/FARO_S4_W05_A2_STOCK_RESERVATIONS_CONSUMPTION_ROADMAP_20261126.md`: A2C consta de **A2C0 persistencia SQL15/backup** y **A2C1 stock.consume/Undo DEV**. **No hay una unidad A2C2 establecida**. A2D UX y A2E aceptación global A2 son trabajo **posterior**, fuera del cierre A2C.
- A2C0: ocho unidades con B7 scoped DEV (A, B1–B3, C0, C1A, C1B y C2). Cierre A2C0C2 en `docs/v5/FARO_S4_W05_A2C0C2_B7_PASS_SQL15_JOURNALED_FIVESTORE_DEV_20261126.md`, código `7234e551146e89b658f3d56c3bf76e3fcee86fe1`, guard `45917344583` SUCCESS, CI `45917344600` SUCCESS, XML original 35/35: cutover SQL15 (9), cutover SQL14 (6), SQL15 FBS2v12 replay (15), SQL15 ACTIVE read (5).
- A2C1 A–F ya B7 PASS. Aceptación DEV global A2C1 `docs/v5/FARO_S4_W05_A2C1_GLOBAL_DEV_ACCEPTANCE_B7_PASS_XML56_APK_20261127.md`, código `340e08983f0b69a6a5d37fa7369074f4715bbe5e`, guard `45982428504` SUCCESS, CI `45982428740` SUCCESS, XML original 56/56 + APK DEV aislada. Guardia final documental en HEAD `de30d1c2b65618e27bdc03fa3d391e8ea48c47c9`, run `45984050495` SUCCESS.
- Permiso actual `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json`: BLOCKED, unidad A2C1 global y B7 PASS; writer `faro-v5-writer-lock:writer.json` se comprueba por CAS aparte. La app V2 productiva, stock real, pagos y Auto/Noche siguen OFF.
- **Brecha**: las 35 pruebas de persistencia y las 56 de consumo proceden de SHAs/CI diferentes. Antes de cerrar A2C DEV, ejecutar **una sola matriz fresca de 91 tests, once XML originales, Android DEV APK, en el mismo HEAD de código**, sin afirmar migración productiva ni G15 físico.

## B2 — Comparación y reutilización sin repetir

- Matriz de comparación V4.4→V5: `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`. Reutilizar distinción entre compra/presupuesto, stock físico recibido, reserva, consumo y Undo compensatorio; ninguna evidencia comercial constituye automáticamente stock ni dinero.
- A2B SQL14 reserva/Release certificado y A2C0/A2C1 XML previos permanecen **congelados**. No reescribir `RealityStore`, port, owners, FBS2v12, journal, UI ni tests previamente aprobados.
- Reutilizar jobs Gradle `:store:generation:test` (cuatro suites, 35), `:owner:household:test` (A/D/Undo previo 27), `:store:reality:test` (B/E 16), `:app:testV2CoreDevUnitTest` (C/F 13), `:app:assembleV2CoreDev`. Total 35+27+16+13 = **91**.

## B3 — Alternativas externas y selección razonada

Fuentes técnicas públicas que guían los fallos adversariales:
- SQLite https://www.sqlite.org/lang_transaction.html — BEGIN IMMEDIATE, atomicidad, rollback y fallos de lock.
- SQLite https://www.sqlite.org/isolation.html — consistencia transaccional y observación WAL, sin inferir exactly-once a partir del lector.
- Android https://developer.android.com/topic/architecture/data-layer/offline-first — owner local y separación de lectura/escritura; no activar conectividad.
- GitHub Actions https://docs.github.com/en/actions/tutorials/store-and-share-data — artifact para conservar XML originales y APK verificable.

Alternativas: (A) cerrar por dos CI históricas verdes (DESCARTAR: SHA y estado integrado distintos); (B) reimplementar suite/journals (DESCARTAR: duplicación); (C) **usar suites certificadas, ejecutarlas juntas en workflow nuevo con validación exacta de once XML y APK** (ELEGIR); (D) probar G15 / activar stock real (DESCARTAR: fuera de autorización). No nuevas bibliotecas ni APIs pagas.

## B4 — Diseño mínimo y ruta candidata

**Solo** ruta candidata `.github/workflows/faro-v5-s4-w05-a2c-global-dev-acceptance.yml` nueva. Primero cerrar B1–B5 con guard documental SUCCESS; en commit separado emitir READY exact1 para unidad `S4_W05_A2C_GLOBAL_DEV_ACCEPTANCE`; guard de permiso SUCCESS; adquirir writer CAS; verificar preflight exacto; recién entonces B6 de un único workflow.

Workflow: Ubuntu/Java21 Android SDK37.0; usar suites existentes cuatro generation + siete owner/reality/app, y APK DEV aislada. Comprobar JUnit XML original `tests/failures/errors/skipped`, conteos 9+6+15+5+8+9+10+8+8+5+8=**91**; once XML únicos, cero fallos/errores/omitidos, ningún XML vacío/stale. Verificar N-035 `stock.consume` household ≠ N-016 `undo.execute` undo-coordinator. Verificar APK no vacía y SHA256. Subir once XML originales y APK por artifact. Scope restringido a esa ruta, `FARO-UNIT:S4_W05_A2C_GLOBAL_DEV_ACCEPTANCE`.

## B5 — Auditoría adversarial previa y criterios binarios

1. HEAD/puntero, writer CAS y guard exactos antes de cada mutación; abortar si otro HELD/UNKNOWN.
2. B1–B5 documental y guard VERIFIED antes de READY; READY por ruta exacta y guard independiente antes de B6.
3. Workflow no cambia código productivo, certificaciones pasadas, `main`, V4.4 ni SDK de producción.
4. Evidencia de 91/91 del mismo SHA exacto en once XML independientes (35 SQL15 journaled + 56 consume/Undo); no confundir compilación con PASS o aceptación física.
5. SQL15 origin SQL14/backup/cutover/replay, pointer ABA, WAL, crash, idempotencia, compensación y grants humanos cubiertos por suites existentes; no extrapolar a invariantes que esos tests no prueban.
6. `stock.consume` jamás se deriva de preview de receta/pago, ni el Undo hereda grant de consumo. Sin stock real, pagos, red, Auto/Noche ni V2 productiva.
7. La CI/guard roja implica B7 RED_FIX, no PASS. Artefactos deben preservar XML originales y APK compilada.
8. Tras CI verde, B7 documental independiente, checkpoint exacto, permiso consumido/BLOCKED, writer RELEASED, guard final SUCCESS. Solo entonces **A2C DEV CLOSED** (NO W05-A2 CLOSED; A2D y A2E pendientes y G15 NOT_RUN).

**Resultado de esta preauditoría:** propuesta viable, pendiente guardia y permiso; **NO READY / NO B6**.
