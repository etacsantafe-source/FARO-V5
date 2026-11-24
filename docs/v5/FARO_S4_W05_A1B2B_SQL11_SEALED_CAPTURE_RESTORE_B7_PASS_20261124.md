# FARO V5 · W05-A1B2B · B7 PASS scoped · Captura/restauración SQL11 STAGED

Fecha: 2026-11-24 (Argentina). Unidad `S4_W05_A1B2B_SQL11_SEALED_CAPTURE_RESTORE_DEV`. Rama `work/s3-night-20261102`.

## B1–B5 — Preauditoría y permiso exacto

Preauditoría `docs/v5/FARO_S4_W05_A1B2B_SQL11_SEALED_CAPTURE_RESTORE_B1_B5_20261124.md` (blob `f18b20bf686bfbd45e8c10f8bea5558ee215c6c9`), commit `eb56e51e9226c02e6364f5dfc2223ed2d50a96db`. Guardia preauditoría `45432271330` COMPLETED SUCCESS.
READY exact4 en commit `16af988e5e7d70daa2f1110fe6e73831eafdfd26`, guardia `45432483570` COMPLETED SUCCESS.
Checkpoint inicial `d781ed7413a0c6984f84123e4d5ee587fec03531`, writer RELEASED época 1822 y A1B2A B7 10/10 en CI `45430216765`. No se repiten A0/A1A/A1B0/A1B1/A1B2A.

## B6 — Código, guardia, CI y XML real

Código `d25c369a17ce58b46e65dc4defb78755b8fc6467` con cuatro rutas exactas:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold11ReceiptRowsPort.kt`
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11ReceiptSql11Port.kt`
3. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11ReceiptSql11PortTest.kt`
4. `.github/workflows/faro-v5-s4-w05-a1b2b-sql11-capture-restore.yml`

Guardia de código `45433033648`: COMPLETED SUCCESS. CI dirigida `45433033990`, job `138583238568`, SHA exacto de B6: **COMPLETED SUCCESS**. `BUILD SUCCESSFUL in 3m 34s`; preflight exact4 sobre READY parent pasó cuatro rutas. Marcador `FARO_W05_A1B2B_SQL11_STAGED_XML_PASS_10_10`.
Artefacto `14939169622` `w05-a1b2b-sql11-staged-xml`, ZIP 853 bytes, digest informado GitHub `sha256:bed0c524005dc6352fc142555d4e2455b41fa20388a4e0a6a9ec60d213fbd890`. XML descargado e inspeccionado desde el ZIP original:
`TEST-ar.com.faro.v5.generation.FaroV5Household11ReceiptSql11PortTest.xml`, `tests=10,failures=0,errors=0,skipped=0`, diez `testcase` reales, 0 elementos `failure` y `error`. La inspección del XML es independiente del marcador impreso por el workflow.

Regresiones en el mismo SHA: CI Reality G4 `45433033828` COMPLETED SUCCESS; CI S3 Reality receipt history `45433033793` COMPLETED SUCCESS. La V2 core `45433033710` permanecía IN_PROGRESS al revisar; no se hereda PASS ni se declara que toda Action haya acabado.

## B7 — Auditoría adversarial de alcance

**PASS_SCOPED_W05_A1B2B_SQL11_STAGED_CAPTURE_RESTORE_XML_10_10**.

Certificado DEV JVM, con una generación de cinco stores en escenario aislado y Reality11 sellada:
- Snapshot SQL11 de `household_stock_receipt_state` y `household_stock_receipt`: revision global, rows exactas, orden, ID e intent únicos, cantidad/fecha/unidad/revisión válidas, 8192 filas y 4MiB, conversión canónica al H11R certificado en A1B1.
- Lectura SQLite solo `mode=ro&immutable=1` bajo fuente STAGED sellada; rechazo de version !=11, SQL STRICT/columnas incorrectas, triggers de recibos, inbound FKs, alias/hardlink y sidecars; `integrity_check` más `foreign_key_check`.
- Restore exclusivamente de las dos tablas de recibos en STAGED SQL11, `BEGIN IMMEDIATE` / `COMMIT` / rollback, idempotente `RESTORED` o `ALREADY_IDENTICAL`, sin overwrite de destino revisado, readback dentro del txn y tras close y checkpoint. Prueba de fallo antes del COMMIT y reintento sin duplicados.
- Puente de generación con puntero exacto ID+sequence, descriptor real, cinco archivos sellados, host DEV exclusivo y quiescence; rechazos de host revocado, cambio de secuencia ABA y destino ACTIVE. No restaura Household10 ni reescribe Finance/Evidence/Workspace/Auxiliary.
- Ninguna invocación externa, pago, red, inventario/corte real ni interfaz Android.

**Fuera de certificación, expresamente pendiente:** no existe todavía el coordinador de restore completo del archivo autenticado FBS2v8 junto con todos los owners ni identidad/cutover/reopen/recovery de generación tras caída. La quiescence del DEV aislado no demuestra exclusividad multiplataforma/Android. No hubo restauración física de G15 ni actividad real de stock, ni Review/Apply/Undo/cantidades consumidas. Un `PASS` del puerto SQL11 no autoriza activar stock.

Siguiente unidad **W05-A1B3**: integrar el archivo autenticado FBS2v8 con capture/restore de etapa completo, identidad y cutover/reopen/recovery, con su propia preauditoría, guardia y READY; no reutilizar permiso A1B2B. La aceptación de stock Apply A1C es todavía posterior.
Consumir permiso a BLOCKED y liberar writer tras checkpoint. V2 productiva, pagos y Auto/Noche siguen OFF.
