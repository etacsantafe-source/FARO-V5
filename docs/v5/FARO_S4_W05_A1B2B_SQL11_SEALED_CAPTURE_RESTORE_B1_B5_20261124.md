# FARO V5 · W05-A1B2B · Reality11 SQL capture/restore aislado · B1–B5

Fecha: 2026-11-24 (Argentina). HEAD revisado `d781ed7413a0c6984f84123e4d5ee587fec03531`; rama `work/s3-night-20261102`.
Unidad exacta: `S4_W05_A1B2B_SQL11_SEALED_CAPTURE_RESTORE_DEV`. V2 productiva/Auto/Noche/pagos/stock real OFF.

## B1 — Evidencia y contrato verificable

- **No repetir:** A0/A1A, A1B0 B7 8/8, A1B1 B7 12/12 y A1B2A authenticated FBS2v8 B7 PASS 10/10 CI `45430216765`, commit `c3019e043af9c12b16c21f02c02d931828f1e20e`; último checkpoint `d781ed7413a0c6984f84123e4d5ee587fec03531`, guard `45431186262` SUCCESS; writer RELEASED 1822, permiso BLOCKED.
- SQL11 DDL `RealityHousehold11ReceiptSchema.kt` blob `49b5ee37ce6951e01d282ed02940ad35b5e69b90`: estado singleton `household_stock_receipt_state(revision>=0)`; `household_stock_receipt`: receipt_id PK, intent_key UNIQUE, purchase_ref, stock_item_ref, quantity_thousandths 1..Int.MAX, unit tipada, received_epoch_day civil, recorded_at_ms>=0 y revision>0.
- `RealityHousehold11StagedFilePort.kt` blob `dbb32284960620296f94bad8fc7756658b9c2623` migra exclusivamente Reality10 STAGED a SQL11 sin cutover; no leer/escribir schemas ajenos.
- H11R codec `FaroV5Household11ReceiptBackupSectionCodec.kt` blob `4628d59935ce81c4e1e0bccb5a5a1e7df2d9cf9b` ya conserva 8192 recibos/4MiB con byte-UTF8 y orden, y FBS2v8 `FaroV5Household11Fbs2Codec.kt` certificado transporta esa sección únicamente dentro de un archivo FAROBAK2 autenticado.
- Gap: **no existe puerto verificado de captura SQL11 sellado ni restore-or-verify de recepciones v11**. Un archivo FBS2v8 autenticado sin relectura SQL11 puede omitir recepciones reales; no se puede activar stock hasta certificar la ruta posterior de restauración global/recovery.
- Criterio aceptación B7 acotado: snapshot SQL11 de estado/recepciones reales, orden/bytes/filas canónicos; restore atómico solo a STAGED SQL11 vacío o ALREADY_IDENTICAL; conflicto de fila/revisión, schema ajeno, puntero ABA, enlaces/sidecars y crash antes de commit fallan cerrado; ninguno habilita una generación activa.

## B2 — Comparación V4.4 / reuso probado

- V4.4 SmartCompra distingue necesidad, compra, recepción y consumo (comparación documentada en A1B1), pero no certificó arquitectura SQLite Reality11 ni FBS2v8. NO trasladar el runtime V4.4 ni deducir cantidades compradas desde necesidades.
- `RealityHousehold10SealedRowsPort.kt` blob `ab4ebc74b053e0f11ba802fddbc257b10b6be11d` aporta lectura read-only `immutable=1`, exact schema/columnas/STRICT, `integrity_check` + `foreign_key_check`, rechazo de triggers/foreign keys inbound, límites y orden.
- `RealityHousehold10StagedRowsRestorePort.kt` blob `bc6b2172f91d0013f06346207aa0e0c195f159df`: `BEGIN IMMEDIATE`, idempotencia por contenido, restaurar únicamente empty y snapshot readback antes y después de COMMIT, WAL checkpoint y rollback.
- `FaroV5Household11StagedMigrationTest.kt` ya tiene fixture de 5 stores aislado, Finance9/Aux4/Household10→11, puntero pinned y sidecars; reutilizar sin volver a ejecutar pruebas previas por inercia.

## B3 — Investigación externa con alternativas

Documentación primaria SQLite (consultada 2026-11-24):
- https://sqlite.org/pragma.html — `PRAGMA integrity_check` **no** sustituye `foreign_key_check`; `wal_checkpoint(TRUNCATE)` debe comprobar que no esté busy. `table_list` ofrece bandera STRICT.
- https://www.sqlite.org/uri.html — `mode=ro&immutable=1` **solo** para archivo realmente sellado/inmutable, porque ignora bloqueos y cambios concurrentes. No aplicar jamás a DB activa.
- https://www.sqlite.org/wal.html — en WAL es esencial un checkpoint antes de considerar la copia como un solo archivo consistente; rechazo de `-wal/-shm/-journal` residual.
- https://www.sqlite.org/stricttables.html — restricciones de tipo STRICT; se vuelven a verificar en la frontera de restore sin confiar solo en SQLite.

Alternativa A **ADAPTAR (elegida)**: puerto SQL11 específico (read-only + atomic restore a staged) con validadores semánticos y bridge de generación que exige stage canónico, exclusividad de host, pointer ID+sequence y pausa de writes. Ventajas: reusa esquemas/test/codec v11, no toca v6/v7/v8 ni owners históricos.
Alternativa B **DESCARTAR**: `VACUUM INTO` o copia de SQLite file directamente como archivo de recepciones — transportaría base completa, mezclaría backup y snapshot, no da idempotencia semántica de eventos y exigiría otro contrato de identidad.
Alternativa C **DESCARTAR**: reusar Household10 SQL port con selector `user_version=10|11` — abriría downgrade silencioso y riesgo de omitir eventos v11.
No incorporar bibliotecas nuevas, redes ni servicios pagados.

## B4 — Diseño mínimo y rutas exactas

Cuatro rutas autorizables, sin cambios a codecs/cifrado históricos:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold11ReceiptRowsPort.kt`: DTO canónico `RealityHousehold11ReceiptRows(revision,receipts)`, `ReceiptRow`, enum RESTORED/ALREADY_IDENTICAL, lectura SQL11 sellada y restore-or-verify transaccional exclusivo de tabla estado+eventos; exige schema11 exacto, STRICT, columnas esperadas, NO triggers ni inbound FKs, sin WAL sidecar ni links; validación 8192/4MiB, unicidad receipt_id/intent_key, unidad/fecha/cantidad/revisión; no toca Household10, otras tablas ni puntero.
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11ReceiptSql11Port.kt`: puente entre SQL11 rows y H11R ya certificado. Exige `FileStorageGenerationLayout.requireStaged`, target NO ACTIVE, pointer generation+sequence exacto, cinco archivos sellados y single-link, host exclusivo y quiescence; verifica puntero antes/después de read/restore y hace flush de SQLite/directorio tras commit. **No** sella FBS2 aquí ni aplica restauro global, no crea stock.
3. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11ReceiptSql11PortTest.kt`: pruebas dirigidas JVM sobre Reality11 STAGED auténtico con negativas de idempotencia, conflicto, schema, tamper, puntero, alias, límites y rollback.
4. `.github/workflows/faro-v5-s4-w05-a1b2b-sql11-capture-restore.yml`: preflight parent READY exact4, Gradle tests dirigidos, verificar XML real y preservar artefacto.

**Frontera rigurosa:** una fila SQL11 es un hecho de recepción; no derivar saldo inventado de necesidades ni extrapolar desde pedido/compra. El archivo FBS2v8 autenticado recién podrá integrarse a un **coordinador global que capture/restaure todos los owners** después de este contrato SQL11, con identidad de archivo/puntero y recuperación de generación (A1B3). No afirmar que este puente isolated constituye por sí mismo restauración completa de un backup FBS2v8.

## B5 — Auditoría adversarial previa (tests previstos, sin afirmar PASS)

1. Snapshot empty de revision0, H11R roundtrip sin inventar stock.
2. Recepciones parciales 7000/3000 misma compra con IDs/intents diferentes y revisions correctas; bytes canónicos y orden fijo.
3. Restore transaccional en stage vacío + segundo restore ALREADY_IDENTICAL.
4. Estado revisado sin filas se conserva (revision>0, snapshot explícito).
5. Stage previamente revisado o con otra recepción se rechaza, sin truncado ni reemplazo.
6. Payload inválido (cantidad, revisión, IDs duplicados, unidad/fecha, >8192/4MiB) rechazado antes de cambiar SQL.
7. Version SQL10/12, estructura/STRICT alterada, triggers inesperados y FK inbound rechazados.
8. Archivo con sidecars, symlink/hardlink o target igual al ACTIVE se rechaza sin abrir como mutable.
9. Writer/lease de host revocado, cambio de secuencia del puntero (incluye ABA) denegados.
10. Inyección antes de commit fuerza rollback; después reintento legítimo consigue exactamente el estado esperado; integridad y no corrupción de otros owners.
11. Nunca abrir ACTIVE ni activar stock: comprobar archivo de generación ACTIVE/puntero sin cambios.
12. Guard preauditoría, permiso READY individual, preflight 4 rutas, CI SHA+XML y auditoría B7 independiente; nunca heredar PASS de A1B2A.

STOP si faltan fondos del SQL11, si snapshot no es canónico, si restore reescribe un destino no vacío, si se abre ACTIVE o si se usa immutable en fuente cambiante. No V2 productiva, pagos, Auto/Noche, APIs pagas ni físico G15.
