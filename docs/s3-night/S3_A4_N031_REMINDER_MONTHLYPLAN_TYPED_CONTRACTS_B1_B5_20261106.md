# FARO V5 — S3.A4 / N031 Reminder & MonthlyPlan typed owner contracts · B1–B5
Fecha: 2026-11-06 (AR), HEAD revisado `1a5bf50958cd6c13e44e436c31e55e98ea59e166`.
Unidad estricta `S3_A4_N031_TYPED_PLANNING_CONTRACTS` — **solo contratos puros JVM, SIN tablas, ni puerto de escritura, ni datos activos, ni UI, ni export/import, ni permisos de producto**.

## B1 — Evidencia y resultado binario
- Preauditorías previas `docs/s3-night/S3_A4_N031_REMINDER_MONTHLYPLAN_OWNERS_B1_B5_STOP_20261106.md` y `docs/s3-night/S3_A4_N031_OWNER_BACKUP_SCHEMA_DELTA_B1_B5_STOP_20261106.md` bloquean **implementar owners durables** hasta completar migración 7→8 y FBS2 v3. Ese STOP **permanece vigente** para código de persistencia o fuentes N031. No usar este permiso para rodearlo.
- Kotlin actual `:owner:matters` y `:owner:objectives` usa módulos aislados y `*Id`, `*Snapshot`, `*ReadApi`; `v5/settings.gradle.kts` registra módulos explícitos. No existen `:owner:reminders`, `:owner:monthlyplan` ni contratos equivalentes. S3.A4 y N031 OPEN; Matter due B7 6/6; G4 staged isolated B7 20/20.
- Aceptación de esta unidad: dos módulos JVM independientes con modelos inmutables de regla/ocurrencia y de item mensual/estado de pago, validaciones negativas de IDs/campos/fechas/referencia y seis tests JUnit por módulo; NO registrar instancias como owner real ni cambiar la situación de HOME Pendientes UNKNOWN. Exact XML 6/6 + 6/6, guard 7 bloques SUCCESS, B7 independiente.

## B2 — Comparación V4.4, V5 y reutilización
- V4.4 `FaroV4FollowUpGateway.readBlocking()` consulta reglas/ocurrencias y MonthlyPlan por separado, verificado históricamente y resumido en el preaudit anterior. V4.4 calendario/recurrencia NO se copia como scheduler, persistencia, ni callback; se adopta solo separación canónica rule vs occurrence vs plan-item.
- V5 `MatterModel.kt`/`ObjectiveModel.kt` aportan patrón inline ID, campos limitados, enum y Snapshot with revision; conservar otros 4 módulos existentes intactos. NO crear sexto store, DDL schema8, permisos nuevos, recibos, OperationIds ni N031 fake count. El contrato no será consumido por el host/producto hasta sus unidades persistencia/backup/ACL verificadas.
- Alternativas: (A) datos genéricos en Matter/Notice: DESCARTAR porque mezcla dueños y falsea conteo; (B) copiar código V4.4: DESCARTAR por acoplamiento al scheduler/Android; (C) modelos Kotlin puros con invariantes y módulos independientes: USAR para habilitar diseño/CI sin escribir datos. La prohibición previa B5 aplica a B6 durable, **no** a publicar especificaciones tipeadas no conectadas.

## B3 — Investigación externa y decisiones comprobables
- https://tasks.org/docs/recurrence/ y https://tasks.org/docs/notifications/ : recurrence rule, occurrence y delivery deben permanecer distintos; ADAPTAR separación, NO usar una notificación del sistema como fuente persistente. Casos: evento cancelado, referencia huérfana, fin de mes y zona inválida. Aún sin algoritmo de programación ni AlarmManager.
- https://developer.android.com/training/data-storage/room/migrating-db-versions : datos de usuario deben conservarse al versionar DB; USAR como advertencia de migración futura, no aplicar API Room a `RealityStore` que usa SQLite bundled. Prohibir DDL hasta backup V3/restore y schema7 regresiones.
- https://www.sqlite.org/lang_altertable.html : migraciones transaccionales y compatibilidad de esquemas; REFERENCIA para siguiente unidad (NO requerida para este JVM contract); no editar SQLite en B6.
- Comparar dos opciones: cálculos de calendario en constructores vs almacenar intención civil+zona e instante de ocurrencia real. ELEGIR intención civil/periodo explícita, postergar expansión de RRULE/DST al motor con pruebas; evita crear ocurrencias ficticias. Licencias/costo: solo consultar documentaciones, cero código ajeno, módulos propios Kotlin/JVM, API de pago cero.

## B4 — Diseño exactamente nueve rutas
1. `v5/settings.gradle.kts` incluye solo `:owner:reminders`, `:owner:monthlyplan`.
2. `v5/owner/reminders/build.gradle.kts` y `v5/owner/monthlyplan/build.gradle.kts`: mismo plugin `faro.jvm-library`, dependencia de kernel contracts y JUnit (sin Android/librerías nuevas).
3. `v5/owner/reminders/src/main/kotlin/ar/com/faro/v5/owner/reminders/api/ReminderModel.kt`: `ReminderRuleId`, `ReminderOccurrenceId`, `ReminderRule` (título, zoneId civil válido, anchor local date, local time, recurrence enum, enabled, opcional MatterId textual sin inferir), `ReminderOccurrence` (ID, rule reference, instante UTC, PENDING/ACKNOWLEDGED/CANCELLED, updated), `ReminderSnapshot` con revision y dedup y coherencia intra-owner; `ReminderSnapshotReadApi` interfaz NO montada. No callback de OS ni schedule.
4. `v5/owner/monthlyplan/src/main/kotlin/ar/com/faro/v5/owner/monthlyplan/api/MonthlyPlanModel.kt`: ID, periodo YearMonth, fecha civil dueDate, título, estado PLANNED/PAID_CONFIRMED/CANCELLED, pago confirmado solo si PAID_CONFIRMED, monto/currency opcionales coherentes, revision y read API no montada.
5. Tests: `v5/owner/reminders/src/test/kotlin/ar/com/faro/v5/owner/reminders/api/ReminderModelTest.kt`; `v5/owner/monthlyplan/src/test/kotlin/ar/com/faro/v5/owner/monthlyplan/api/MonthlyPlanModelTest.kt`: seis pruebas deterministas por tipo, duplicate/id invalid, timezone, no inferred payments, no silent recurrence, snapshot references.
6. `.github/workflows/faro-v5-s3-n031-owner-contracts-directed.yml`: Java21, Gradle exact modules, XML exact 6+6, sin acciones de app ni device.
- NUEVE rutas totales. No archivo `RealityStore`, `FaroV5GenerationStoreGraph`, `FBS2` o Android permitido en B6.

## B5 — Auditoría adversarial ANTES de B6
- Bounded change is **data-free**. Construir tipos/validaciones sin persistencia no puede causar pérdida por backup, because no owner is registered, no table/store or export path. Host/ReadApi is just interface and MUST NOT be used to claim N031 complete. No nuevas operaciones en catálogo (172).
- Reglas negativas obligatorias: zone ID inválido rechaza; fecha/schedule inválido rechaza; no confundir regla con ocurrencia, ni pago planificado con confirmado; no IDs duplicados ni occurrence sin regla en snapshot; overflow/negativos rechazados; documentar explícitamente DST y fin de mes como tarea de motor futura, no inferir fecha implícita.
- Se mantiene el STOP del documento de arquitectura para owner durable/FBS2 v3, host ACL e integración. No S4, no V2 productiva/Noche/Auto, no API paga, no main/V4.4, no G15 física.
- **B5 READY SOLO contratos tipados JVM; CODE SCOPED 9 rutas; B7 PASS_SCOPED_CONTRACTS_ONLY si pasa JUnit exacta.** Luego B1–B5 de SQLite8+FBS2v3 owners reales por separado antes de escribir datos. No recalificar A4 como cerrado.
