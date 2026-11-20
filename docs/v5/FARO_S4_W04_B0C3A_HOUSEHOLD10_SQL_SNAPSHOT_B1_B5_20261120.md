# FARO V5 — W04 B0C3A · Household10 SQL snapshot y restore — B1–B5
Fecha local 2026-11-20 (Argentina). Baseline de fuente: 768c56824d04b051d323988aa2129461d8d16dd6. Unidad propuesta S4_W04_B0C3A_HOUSEHOLD10_SEALED_SQL_RESTORE_DEV. Estado: PREAUDIT_DOC_ONLY, NO_READY, B6/B7 NOT_RUN. Autoridad humana W4 aprobada; permisos por unidad obligatorios. El permiso READY de B0C2 permanece intacto hasta B7.

## B1 — Evidencia y criterio
- W04-A, B0A, B0B, B0C1 cuentan con documentos B7 PASS scoped; no repetir sus pruebas. B0C2 código 768c56824d04b051d323988aa2129461d8d16dd6, guard 44668249799 SUCCESS, CI 44668249894 SUCCESS y XML 5/5; B7 independiente TODAVÍA PENDIENTE.
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold10Schema.kt blob 0fca56b7c0910a37085d18b4ce1bd59a1d118f33: instala en un STAGED9 tablas STRICT household_need_state (revision) y household_need (id, name, quantity_thousandths, unit, priority, status, revision, due_epoch_day); user_version 10, rollback íntegro. No crea lectura/restore de filas.
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold10StagedFilePort.kt blob 71b34ab0426a51b7fb3425f980667618de40c86f: migración de archivo bajo admisión EXTERNA, no administración de generation ni archivo ACTIVE.
- v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdNeedModel.kt: necesidad tipada y cantidad en milésimos, revisión Long, estado OPEN/COMPLETED, fechas civiles; no CRUD persistente ni permisos host.
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10BackupSectionCodec.kt blob 5feff64fd2fb7f69479d10fc33f287d22c050376: H10S v1 inmutable y canónico; 8192 filas, 4 MiB, orden estricto de IDs, revision agregada y fecha opcional. NO captura SQLite.
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9SealedReadPort.kt y RealityFinance9StagedRowsRestorePort.kt son precedentes reales de extracción/restauración de Finance9 pero deben respetarse versión exacta y separación owner. Ningún PASS de esos puertos implica Household10.
- Resultado exigido B0C3A: leer desde una Reality10 sellada y restaurar SOLO las dos tablas Household en otra Reality10 STAGED, con verificación exacta. No incluir otras stores ni activar puntero.

## B2 — V4.4 y activos reutilizados
- Auditoría V4.4 P02 L11/L14/L15: listas humanas, planes, datos comerciales durables en composition root; no trasladar base legacy ni runtime. La app histórica tuvo límites, no equivale a certificación actual.
- Conservar V5: schema STRICT Household10, tipos W04-A, codec H10S, puertos Finance9, snapshot SQL sellado, transacción BEGIN IMMEDIATE única, foreign_keys, integrity_check, writer/mantenimiento exclusivo y generación pinned. Copiar semántica de revisión/orden, no duplicar código Finance9 sin adaptar validaciones.
- No abrir RealityStore actual Finance9 como si reconociera schema10: su validador exacto rechaza 10.

## B3 — Alternativas, evaluación
- A: dos puertos internos Reality10 (lectura sellada y restore en STAGED bajo proof de generation) siguiendo los invariantes existentes de Finance9. ELEGIDA para B0C3A.
- B: agregar métodos al RealityStore normal/ACTIVE que auto-migren o capturen Household. RECHAZADA por apertura productiva, versión y writer.
- C: exportar SQL crudo, restaurar con INSERT OR REPLACE sin validación, inventar DB Household externa. RECHAZADA por integridad, idempotencia y datos de usuario.
- Referencias técnicas heredadas, sin depender de APIs pagas: documentación de transacciones SQLite, StagedSqlitePreflight actual, Finance9SealedReadPort/RowsRestorePort. Comparar HEAD real antes de escoger implementación.

## B4 — Contrato, rutas propuestas exactas y aceptación
Unidad aislada propuesta, todas rutas sujetas a revalidación post-B0C2-B7, NUNCA permiso amplio:
1. v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold10SealedRowsPort.kt (nuevo, solo lectura).
2. v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold10StagedRowsRestorePort.kt (nuevo, solo target10 STAGED autenticado).
3. v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold10SealedRowsRestoreTest.kt (nuevo).
4. .github/workflows/faro-v5-s4-w04-b0c3a-household10-sql-rows.yml (nuevo; JVM, JUnit XML).
La interfaz Reality no depende de types de generation para evitar dependencia circular: snapshot SQL acotado (revision + filas), convertido a Household10BackupSection exclusivamente en generation. Exigir que el caller certifique canonical path, marker, stage, expected pointer ID+sequence, lease y ausencia sidecars ANTES del puerto; el puerto revalida archivo regular, versiones/tables/FK. Lectura de archivo cerrado/sellado no genera sidecars ni cambia DB; restitución de target exclusivo sin mutar otras tablas.
En restore: decodificar/verificar snapshot completo antes de BEGIN; rechazar revisiones negativas, filas incompatibles, ID duplicado, mezcla de unidades, exceso 8192 filas o 4 MiB; orden canónico; una transacción atómica, solo nuevo STAGED vacío o EXACTAMENTE igual para reintento; rechazar datos distintos sin sobrescribir; incrementar revision solo por snapshot existente, no inventar grants ni receipts Apply. Después commit validar igualdad de lecturas e integridad.
B0C3A DONE requiere CI/JUnit XML de SHA exacto, guard, auditoría B7 y writer RELEASED; la operación sigue sin montar host ni restaurar cinco stores.

## B5 — Auditoría adversarial y matriz de pruebas diseñadas
T01 stage10 con dos necesidades y revision > filas: lectura ordenada; roundtrip tipado exacto, fecha opcional.
T02 stage10 vacío con revision no cero: preservar revision; nada se supone cero por ausencia.
T03 doble restore mismo snapshot: idempotencia, sin segunda mutación; snapshot distinto con mismos IDs: STOP, sin reemplazar.
T04 fallo inyectado a mitad de INSERT o antes de COMMIT: rollback completo, tablas Finance9 y recibos intactos.
T05 ruta symlink/hardlink/sidecar/ACTIVE/incorrect schema9, FK desactivadas, corrupción: denegar antes de tocar.
T06 snapshot con fila inválida, fecha fuera de rango, sobrelímites, conflicto de nombre/ID/revision: rechazo y cero efectos.
T07 opener antiguo Finance9 sobre 10 falla cerrado; no degradar schemaVersion ni alterarlo.
T08 restore correcto de Household no muta finance_entry, apply_attempt, receipt, auxiliary, evidence ni pointer.
Riesgos: un puerto SQL sin prueba externa de generación no debe hacerse público a host; el in-process gate no prueba exclusividad interproceso por sí solo. El fichero ACTIVE permanece inmutable.
Pruebas T01–T08 NOT_RUN. B5 DOCUMENTADO, NO_READY hasta B0C2 B7 y guard documental de esta unidad; luego permiso READY 4 rutas en commit DOC_ONLY, preflight y B6/B7 independientes.
