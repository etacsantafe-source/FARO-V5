# FARO V5 — S3.A4/N031 G4 internal domain restore · B7 final audit
Fecha: 2026-11-06. Código exacto `9458b4889564458529430c546afb8a311cce9342`, unidad `S3_A4_N031_G4_DOMAIN_RESTORE`.
**B7: PASS_SCOPED — internal owner-domain restore primitive only. NO FBS2 G4 integration, NO N031/A4 closure.**

## B7 — Evidencia independiente y alcance verificado
- B1–B5 preaudit aprobada, archivo `docs/s3-night/S3_A4_N031_G4_DOMAIN_RESTORE_B1_B5_20261106.md` blob `244488da43069f9914d479212f49c46bc076843b`, commit docs-only `30baba298215c5caf50cd820a827294f6388063f`. Permiso READY en docs-only commit `7c19d6cf73066af99743918b46623018d3dc3930`, guard protocolo SUCCESS `42192159149`.
- Commit B6 `9458b4889564458529430c546afb8a311cce9342`, 4 archivos y FARO-UNIT correcto: `RealityDomainBackupPort.kt`, `RealityStore.kt`, `RealityG4DomainRestoreT2Test.kt`, workflow dirigido. No modifica FBS1/FBS2, scheme7, `main`, V4.4, S2, catálogos ni Android host.
- Protocolo exact SHA [run 42192793305](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42192793305) SUCCESS.
- Dirigido [run 42192793326](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42192793326) SUCCESS: `BUILD SUCCESSFUL`, XML exacto `RealityG4DomainRestoreT2Test 5/5` y regresiones previas `RealityDomainOwnersT2Test 9/9`, cero failures/errors/skipped.
- Otro gate de propietarios G4 [run 42192793307](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42192793307) SUCCESS, exact SHA.
- Recibos/FBS1/FBS2 [run 42192794719](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42192794719) SUCCESS, logs XML `S3_REALITY_RECEIPT 3/3`, `S3_FINANCE_ACTIVITY 3/3`, `S3_FBS1_RESTORE 4/4`, `S3_FBS2_SEMANTIC 7/7`. Son REGRESIONES del formato existente, **no** prueba de FBS2 con G4 no vacío.
- V2 core [run 42192793230](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42192793230) SUCCESS, JVM Search 10/10, real host four-owner 1/1, T4 debug 7/7, generation schema7 17/17, M1 isolation 4/4, sin Android física.
- Writer global liberado epoch344 antes de CI; B7 adquirido exclusivamente epoch345 para docs; liberar epoch346 al cerrar, no sostener writer en CI.

## Corrección funcional auditada
- `RealityDomainBackupSnapshot` sigue siendo tipo interno, no archivo, no fuente duplicada ni UI. Un puerto de restauración separado entra por `withMaterialAdmission`/mutex Reality.
- Validación previa: esquema exacto7, máximo100.000 filas por tipo, revisiones posibles, orden binario UTF8 de claves, `PRAGMA integrity_check`/FK. Se prohíbe transacción anidada. Origen válido se importa en **una transacción BEGIN IMMEDIATE/COMMIT**, las tablas y revisiones juntas; excepciones hacen `ROLLBACK`. Repetición idéntica devuelve misma snapshot, distinto destino no se sobrescribe.
- Pruebas dirigidas verifican persistencia a reinicio, idempotencia, bloqueo de destino ocupado, versión/orden/revisión inválidos, rechazo de restore en transacción activa. El camino legacy `backup.snapshot()` sigue fallando cerrado ante G4 ocupado. La simulación de corte de energía y el host quiescence/ACL FBS2 no se han certificado.

## Bloqueos y siguiente decisión técnica
- **FBS2 NONEMPTY G4 aún BLOQUEADO**: `FaroV5FiveStoreBackupCapture` llama legacy `reality.backup.snapshot()`, que rechaza G4; codec FBS2 v1 encapsula FBS1 (sin G4) + Workspace; `FaroV5FiveStoreStagedRestore` restaura legacy/Workspace, NO invoca este puerto nuevo. El puerto es una pieza de infraestructura, no exportación ni recuperación productiva. No convertir esta prueba a aprobación de FBS2 real.
- **Siguiente nueva unidad** `S3_A4_N031_FBS2_G4_INTEGRATION`: B1–B5 revisar captura atómica Reality legacy+G4 bajo quiescencia 5 stores, evolución aditiva del contenedor autenticado (sin alterar FBS1), source/target generation, replay fence y pin archive; restore por secciones con pruebas bytes y crash/ABA. Recién luego dueños Reminder y MonthlyPlan, ACL host y UI N031/N015, A4 integrada.
- V2 productiva OFF; Noche/Auto OFF; S4 OFF; S2 CLOSED 8/8; catálogo 172 Guardados +8 NO APROBADOS; A5 X01–X12/G15 una campaña final NOT_RUN. S3 A1/A2 cerradas en alcance automatizado (2/5), A3/A4/A5 integrados OPEN. A4 OPEN.
- **B7 PASS_SCOPED** permite RESET permiso `BLOCKED` con last_code_review exacto; no más código sin nuevo B1–B5/READY.
