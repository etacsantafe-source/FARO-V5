# FARO V5 — S3.A4 N033A same-Reality-transaction Planning bridge, B1–B5 PREAUDIT

2026-11-06 AR. Unidad: `S3_A4_N033A_REALITY_TRANSACTION_BRIDGE`.
Source: `827cb89a01f5dfc1e364e075860477d1ac04507b`.
Scope propuesto: `RealityStore.kt`, `RealityPlanningStageMutationPort.kt`, prueba nueva `RealityPlanningApplyBridgeT2Test.kt`, CI Reality dirigida **existente** (sin workflows nuevos). N032 y N031 congelados. Permiso actual BLOCKED hasta documento separado. Ninguna operación de usuario habilitada por esta preauditoría.

## B1 — Evidencia verificable y criterios
- N032 funcional `8863adf3`: 47/47 y ocho workflows SUCCESS exact SHA; port `internal` staged-only; ownership CAS, checks FK y PLN1 4MiB/20k antes de COMMIT.
- HEAD `827cb89a`: N033 B1–B5 demuestra que N032 abre `BEGIN IMMEDIATE` y que `RealityStore.run(tx,scope)` ya abrió la transacción SQLite del Apply. El intento de BEGIN anidado está explícitamente negado en el test N032.
- `RealityStore.commit(scope, identity, outbox)` coloca receipt y journal COMMITTED **dentro** de `run`, sin COMMIT propio. `requireActiveScope` comprueba clase privada, owner idéntico y transactionId activo. `resolveAttemptIdentity` persiste PREPARED antes de transacción.
- Aceptación N033A: mismo handle/conexión para Planning+receipt+COMMITTED, port interno con scope obligatorio; rollback antes/después de receipt elimina owner/revision/receipt COMMITTED, mantiene attempt PREPARED; CAS, FK, budget, cross-store scope negados; N032 staged igual; default schema7 no habilitado. Ninguna identidad de Review/ACL inventada: N033B/C posteriores.

## B2 — V4.4, delta reutilizable
- V4.4 ya fue contrastada en `S3_A4_N031_PLANNING_STAGE8_BACKUP_RESTORE_B1_B5_20261106.md`: distinguir regla/ocurrencia/periodo mensual y no copiar scheduler/CRUD legacy. Reutilizar exactamente la validación SQL y PLN1 de N032 en vez de bifurcar dos validadores.
- Diseño original V5: `AtomicApplyTransactionPort.run` es único límite de COMMIT; `requireActiveScope` protege propiedad del handle. No sexto store, no transacción SQLite paralela, ni modificación de schema7/productivo.
- Alteración prevista: extraer el cuerpo de inserción/validación de N032 a una primitiva privada que *exige transacción ya abierta por su llamador*. N032 sigue abriendo/cerrando su transacción STAGED; N033A la invoca solo desde un nuevo port `internal` que exige `RealityTransactionScope` auténtico. Ningún wrapper concede permisos de usuario.

## B3 — Investigación independiente y riesgos
1. SQLite transacciones: https://www.sqlite.org/lang_transaction.html — `BEGIN` no admite nesting, escritor serial, `BEGIN IMMEDIATE` bloquea competidores. **USAR** una conexión/una única transacción externa.
2. SQLite SAVEPOINT: https://www.sqlite.org/lang_savepoint.html — `RELEASE` interno no es COMMIT durable; crash/rollback externo lo deshace. **DESCARTAR** savepoint como falsa independencia, **REFERENCIA** de semántica.
3. SQLite ATTACH+WAL: https://www.sqlite.org/lang_attach.html — no asumir atomicidad de crash entre distintos archivos WAL. **DESCARTAR** un store de recibos externo.
4. Android Room transacciones: https://developer.android.com/reference/androidx/room/RoomDatabase#withTransaction(kotlin.coroutines.SuspendFunction1) — unidad de trabajo que incluye todas las operaciones, **REFERENCIA**; no migrar V5 a segunda DB.
5. Tasks.org (https://github.com/tasks/tasks; GPL-3.0): **REFERENCIA** sobre recordatorios, no reutilizar GPL código ni crear scheduler; Now in Android (https://github.com/android/nowinandroid; Apache-2.0): **REFERENCIA** seams de almacenamiento/test y offline; ninguna dependencia nueva.
Alternativas evaluadas: A) puente interno de mismo scope **ADAPTAR**; B) insertar staged y después receipt en transacciones separadas **RECHAZAR** por crash gap; C) SAVEPOINT interno sin scope autenticado **RECHAZAR** por falsos límites/ownership. Riesgos: ACL/generación no se resuelven aquí; exponer port fuera del módulo queda prohibido.

## B4 — Diseño mínimo y pruebas binarias
- Nuevo `RealityPlanningApplyInsertPort.insert(command,transactionScope)` `internal`; pasa por `withMaterialAdmission`, lock, `requireActiveScope` y schema8. Reutiliza núcleo N032; N032 mantiene fail-fast en nested Apply y semántica propia de commit/rollback.
- Crear pruebas JVM: rollback con insert sin receipt; rollback tras `commit` forzado; éxito owner+receipt y reabrir; stale CAS/no doble efecto; schema7, scope extranjero, orfandad y budget. La prueba nunca debe llamar al API de usuario/chat.
- Ejecutar workflow dirigido existente Reality más regresiones N031/N032, verificar SHA exacto, XML/log y guardia. Auditor B7 solo después de CI verde. Si roja: STOP, RED_FIX misma unidad; no cambiar otra cosa.

## B5 — Auditoría adversarial previa y veredicto
**READY_FOR_SCOPED_PERMIT_ONLY** tras comprobar HEAD exacto y B1–B4, **NO B6 realizado**.
Negativos: (a) excepción entre insert y receipt no persiste owner; (b) excepción tras receipt antes de COMMIT no deja receipt ni owner; (c) intento schema7 aborta; (d) scope inventado/ajeno aborta; (e) owner version ABA o duplicado aborta; (f) FK huérfana revierte; (g) overflow 4MiB revierte; (h) N032 no puede insertarse dentro de `run`; (i) fallo CI retiene bloqueo. El puente no acredita Undo, UI, ACL, recovery Workspace ni completitud A4: esos son N033B/C.
No V2 productiva, S4, Noche ni Auto; catálogo 172 sin cambios; S2 8/8 frozen; física G15 pospuesta a A5. Solo autorizar archivos exactos por permiso documental nuevo.


### Addendum de B4/B5 — CI aislada para N033A
La prueba nueva `RealityPlanningApplyBridgeT2Test` no figura en los filtros `--tests` del workflow N032. Ejecutar solo ese workflow daría falsos verdes: confirma N032, NO puente N033A. En consecuencia se autoriza **una** cuarta ruta funcional exacta `.github/workflows/faro-v5-s3-a4-n033a-bridge-directed.yml`, workflow separado con test nuevo + N032 y verificaciones XML de casos exactos. No modificar los tests, XML ni workflows previamente certificados de N032. Permiso B6 específico debe incluir esta cuarta ruta. B7 rechazará PASS si la prueba nueva no corrió bajo SHA de código.
