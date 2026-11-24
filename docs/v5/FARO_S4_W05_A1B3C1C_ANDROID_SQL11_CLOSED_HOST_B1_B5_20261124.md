# FARO V5 · W05-A1B3C1C · Android DEV SQL11 startup / strict reopen · B1–B5

Fecha: 2026-11-24 (Argentina). Trabajo exclusivo en `work/s3-night-20261102`. NO cierre de A1. Esta preauditoría no implementa ni certifica C1C.

## B1 — Evidencia, invariantes y alcance (revisado)

Entrada: HEAD observado `56ab6d8ec70362d8fa582d1fff67e7f2a901e3fc`, writer remoto `faro-v5-writer-lock:writer.json` RELEASED epoch1890, permiso anterior C1B BLOCKED. El código SQL11 journaled C1B es `432e6ece49eb50d0e7a607e581eab6c975c5cac4`; CI `45495586732` COMPLETED SUCCESS XML 8/8 y guardia `45495586735` SUCCESS. B7 previo: `docs/v5/FARO_S4_W05_A1B3C1B_SQL11_JOURNALED_CUTOVER_B7_PASS_20261124.md` (blob `75dfc5be981c915e068d99fc3459d153f4e133a5`). No repetir C1A/C1B ni W1–W4.

Hallazgo: `FaroV5V2CoreDevAuthority.openSession` solo selecciona graph legacy/Planning8/Finance9; `FaroV5ActiveGenerationGraphOwner.open` y los openers de `FaroV5GenerationStoreGraph` no están certificados para active Reality11. Una selección SQL11 no equivale a un host Android abierto. `FaroV5Household11DevJournaledCutover.recover` devuelve `NO_TICKET`, `AWAITING_FOREGROUND`, `ACTIVE_ELEVEN`, `ROLLED_BACK` o `BLOCKED`; nunca debe forzarse auto-START ante VALIDATED. El host actual no debe tratar `ACTIVE_ELEVEN` como un graph SQL10.

`ActiveSqlitePreflight.requireAll` ya proporciona inspección de SQLite ACTIVE con `SQLITE_OPEN_READONLY | SQLITE_OPEN_NOFOLLOW` y WAL visible; `StagedSqlitePreflight` y los `RealityHousehold11*RowsPort.readTrustedSealedStageEleven` emplean `immutable=1` solamente en STAGED sellado. **No usar immutable=1 para ACTIVE**, pues puede omitir commits en WAL. La prueba Android debe diferenciar datos staged cerrados de datos activos con WAL y usar version exacta, H10 + H11R.

Objetivo binario C1C: startup/recovery DEV de journal C1B sin nuevo switch, reopen read-only de generación ACTIVADA SQL11 (cinco SQLite exactos), lectura limitada H10/H11R, puntero completo fijado antes/después, ACL de sesión revocable, sin autoridad de escritura/Apply, sin activar UI productiva. No se exige ni se acredita instalación G15 en este bloque.

## B2 — Comparación V4.4 / reuso V5 sin repetir

Auditoría histórica READ_ONLY `docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md` L12/L17/L20 y delta `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md` R12/R17/R20: V4.4 separa recepción/stock físico de compra o pago y posee mecanismos de backup/recovery, **sin probar importación compatible a Reality11**. No reutilizar el runtime V4.4, ni trasladar pruebas físicas históricas a V5.

Reuso vigente: `FaroV5Household11DevJournaledCutover` (C1B) para resultado de recuperación DEV y validación de journal; `FileActiveGenerationPointer` con full pointer/sequence; `FileStorageGenerationLayout`; `FileBootstrapJournal`; `FileGenerationReplayFence`; `ActiveSqlitePreflight`; `RealityHousehold11NeedsRowsPort` y `RealityHousehold11ReceiptRowsPort` solo como contratos de columnas/validación STAGED; `SingleProcessMaterialWriteGate`; `FaroV5V2CoreDevActivity` solo en build type aislado. **No reutilizar** opener SQL10 ni sus grants de Apply. El resultado activo habilita como máximo lectura DEV, no mutación.

## B3 — Investigación externa y decisión de alternativas

Fuentes primarias y mecanismos:
- SQLite WAL: https://sqlite.org/wal.html — los lectores deben observar commits en WAL; tratar un snapshot SQLite como simple archivo sin journal puede producir lecturas antiguas.
- SQLite daño y backup concurrente: https://www.sqlite.org/howtocorrupt.html y https://sqlite.org/backup.html — no copiar archivos activos por fuera del contrato de checkpoint/cierre.
- Android `SQLiteDatabase` / flags de read-only: https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase — nunca `CREATE_IF_NECESSARY` al reabrir una base existente.
- AndroidX Room MigrationTestHelper: https://developer.android.com/reference/androidx/room/testing/MigrationTestHelper y guía https://developer.android.com/training/data-storage/room/migrating-db-versions — validar esquemas/versiones y pruebas de migración; aquí NO se ejecuta migración en startup.

Alternativa A **ADAPTAR**: host DEV especializado con selección journaled C1B, full-pointer, root lock/gate, `ActiveSqlitePreflight`, lectores SQL11 ACTIVE que no usen `immutable=1`, y DTOs read-only limitados. Menor riesgo; conserva owners/cutover existentes.
Alternativa B **DESCARTAR**: abrir SQL11 con el graph SQL10 o configurar Room fallback destructivo para simplificar; puede recrear/migrar o leer schema incompatible, incumple fail-closed.
Alternativa C **REFERENCIA, NO ADOPTAR AHORA**: escribir un opener productivo Room schema11 completo; grande, cambia bindings/UI y requiere revisión de migraciones independiente. Evitarlo hasta permiso de activación productiva.
Hipótesis falsable: el host A admite SQL11 exacto, observa WAL válido y revoca read grants ante pointer ABA; B falla por incompatibilidad/posible mutación; ninguna alternativa publica Apply.

## B4 — Unidad mínima; rutas exactas congeladas para revisión

ID `S4_W05_A1B3C1C_ANDROID_SQL11_CLOSED_HOST_DEV`. Propuesta de **seis rutas**, todas sujetas a permiso nuevo READY y preflight en parent:
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11ActiveDevReadHost.kt` — opener de generation11 ACTIVE sin graph SQL10; root lock, journaling C1B, full pointer, version/health de cinco DB, lecturas H10/H11R seguras con WAL y cierre/revocación.
2. `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5W05Household11DevStartup.kt` — adaptador Android variante aislada, no URL/OS/imports de V2 productiva; devuelve estado tipado y lectura readonly.
3. `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevActivity.kt` — solo affordance de reapertura explícita DEV, sin habilitar Apply ni sustituir el Chat ya certificado; no activar al cold start sin ticket completo.
4. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11ActiveDevReadHostTest.kt` — JVM de selección, WAL, schema, ABA, rollback, cierre/host.
5. `v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5W05Household11DevStartupTest.kt` — test de política Android DEV, lifecycle, no shipping, grants revocados.
6. `.github/workflows/faro-v5-s4-w05-a1b3c1c-android-sql11-reopen.yml` — Java21 + Android SDK, preflight parent EXACT6, Gradle testV2CoreDevUnitTest y generación, artefactos XML y assert de no failures.

No cambiar `main`, V4.4, manifest productivo, build types históricos, activación V2, Evidencia2 ni stock Apply. Si B6 muestra dependencia imposible sin séptima ruta, STOP/RED_FIX de permiso antes de ampliar, nunca escribir afuera.

## B5 — Auditoría adversarial previa y plan de pruebas

T01 VALIDATED C1A sin START C1B → AWAITING_FOREGROUND; startup NO promueve puntero.
T02 COMPLETED C1B + full pointer exacto + fence RESTORE → ACTIVE_ELEVEN y reopen SQL11 READ_ONLY, cinco SQLite intactos.
T03 caída después de SWITCH_PREPARED y pointer target → recovery C1B idempotente; NO segundo switch.
T04 rollback a origen N+2 → nunca abrir target ni dar grant ni instalar fence target.
T05 pointer ABA, diferente sequence con mismo generationId → revocar sesión y fallar.
T06 ticket/operation/journal inconsistente, foreign pending, sin sello o archive identity errónea → BLOQUEADO antes de DB.
T07 Reality versión10/12, H10/H11R columnas/triggers alterados, Evidence/Workspace/Auxiliary con versión equivocada → fallar antes de exponer lecturas.
T08 WAL ACTIVE con cambios comprometidos/sidecars normales → reflejar datos actuales; `immutable=1` prohibido. Symlink en root, DB o sidecar → bloquear.
T09 host pasa a background/destroy o cierre simultáneo → revocar port, close idempotente, cero SQLite handles vivos.
T10 intento de Apply, pago, transferencia, mutación stock o versión productiva → no hay port ni flag; ningún write.
T11 dos lectores/root lock ocupado, writer material concurrente → no aceptar authority duplicada; quiescence.
T12 reinicio sin ticket/archivo alterado → no inventar estado inicial ni sobrescribir/restaurar base.

B5 decisión: **GO solo para B6 DEV readonly acotado bajo READY nuevo**. Si no se pueden probar WAL activo, full-pointer ABA y ausencia de mutaciones, NO B7. Las pruebas Android en CI NO sustituyen G15 físico. Aplicación V2 productiva, Auto/Noche, pagos y stock real continúan OFF. Evidencia2 restauración/cutover es unidad separada posterior; W05-A1 global permanece abierto.

## Checkpoint de preauditoría

B1–B5 documentados; B6 NO EJECUTADO; B7 NO CERTIFICADO. Esta pieza documental por sí sola NO autoriza código: exige guardia documental SUCCESS, blob SHA fijado en permiso de seis rutas, preflight parent, writer CAS y CI/XML reales.
