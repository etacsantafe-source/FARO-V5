# FARO V5 · W05-A1B3C1C · RED_FIX strict ACTIVE WAL reopen · B1–B5

Fecha 2026-11-24. Alcance: misma unidad `S4_W05_A1B3C1C_ANDROID_SQL11_CLOSED_HOST_DEV`. No cierre global A1 ni habilitación de stock.

## B1 — Evidencia y falla de cobertura B7
Código initial `39d458c84fcfddf12034d0265966a707c6c9b66e`; RED_FIX de task Gradle `ce12c6f2bbb5c13faafdd8d4930c4cd19335e801`. Guardia `45507479250` SUCCESS, CI dirigida `45507479289` SUCCESS, job `138846311976`, Gradle BUILD SUCCESSFUL, XML original **16 tests, failures0, errors0, skipped0**, artifact `14973821956` digest `sha256:702a7ecdddbc55c089e20195c172077a60f703ff6e6261c65565c2f6f652eb02`.

**B7 NO APROBABLE TODAVÍA**: las 16 pruebas no comprueban un segundo inicio después de abrir en modo SQLite ACTIVE READONLY/WAL. `FaroV5Household11DevJournaledCutover.recover()` llama `requireSealed()`, que rechaza cualquier `-wal/-shm/-journal`. El host C1C actual llama siempre `C1B.recover()` ANTES de su propio `ActiveSqlitePreflight`. En SQLite, una lectura read-only sobre base WAL puede crear archivos auxiliares que sobreviven al cierre. Reproducción exploratoria Python sqlite3 WAL mode ro: aparecen `-wal` de 0 bytes y `-shm` de 32768 bytes tras cerrar el último lector. Eso es indicio de riesgo real, NO prueba física Android; exige fixture JVM nativo y verificación exacta. No borrar sidecars a mano ni usar immutable=1 para ACTIVE.

## B2 — Reuso sin CAS/owners duplicados
C1B conserva exclusividad para PREPARING/STAGED/VALIDATED/SWITCH_PREPARED y todo journal de recuperación pendiente o rollback. Cuando journal RESTORE ya está **COMPLETED + materialWritesAccepted** y pointer exacto target N+1 con fence RESTORE, el host C1C puede reabrir directamente (SIN reejecutar C1B.recover sobre un activo con WAL), bajo el MISMO lock raíz y gate material, comprobando ticket autenticado por SHA256, operación, origen, destino, seis campos de identidad, no pending ajenos, cinco SQLite ACTIVE con WAL, esquema exacto, integridad y puntero full antes/después. Es lectura únicamente, no un nuevo switch ni CAS. No usar opener SQL10 o lógica V4.4.

## B3 — Alternativas / fuentes
Referencia base B3 C1C: `docs/v5/FARO_S4_W05_A1B3C1C_ANDROID_SQL11_CLOSED_HOST_B1_B5_20261124.md` y fuentes primarias https://sqlite.org/wal.html y https://www.sqlite.org/howtocorrupt.html. A **ADAPTAR**: separar recuperación pendiente (C1B sellado) de reapertura COMPLETED (C1C ACTIVE WAL), con full-pointer+ticket+journal+fence sin inmutabilidad falsa. B **DESCARTAR**: borrar manualmente WAL/SHM para engañar C1B; puede destruir commits. C **DESCARTAR**: ignorar WAL por `immutable=1`; puede devolver lecturas viejas. D **DESCARTAR**: cambiar C1B ya certificado sin permiso. A aplica solo al host DEV y no a producción.

## B4 — Rutas exactas (4, mismo unit_id)
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11ActiveDevReadHost.kt` — distinguir journal COMPLETED de pending, reconstruir ticket durable completo, full pointer, fence y revisiones originales; exposición de DTO esperado read-only bajo lock.
2. `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5W05Household11DevStartup.kt` — comparar contadores/revisiones ACTIVE leídas con ticket para negar una mutación no autorizada; sin mutar datos.
3. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11ActiveDevReadHostTest.kt` — test de segundo re-open ACTIVE con lectura SQLite en WAL y sidecars reales, pointer ABA, ticket/journal/fence adulterado, sólo C1B para pending.
4. `v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5W05Household11DevStartupTest.kt` — prueba política de comparación de revisiones/conteos y deny.
Workflow C1C existente debe correr tests nuevos por clase, XML asserts >=8; NO modificar workflow en este RED_FIX. No ampliar a main/V4.4/C1B ni manifest productivo.

## B5 — Adversarial/criterios binarios
T13 abrir ACTIVE SQL11 read-only de cinco SQLite; cerrar, constatar archivos WAL si se crean, reabrir **segunda vez** exitosamente con puntero full N+1 sin otro switch ni mutación.
T14 durante `VALIDATED`, startup sigue `AWAITING_FOREGROUND` y nunca promueve; después de SWITCH_PREPARED, solo C1B puede recuperar.
T15 ticket corrupto, operación ajena, journal distinto, pointer ABA, migración fence o root ocupado → BLOCKED.
T16 si estado activo H10/H11R difiere de revisiones y conteos autorizados en C1A, UI DEV bloquea, no inventa cero ni ofrece stock Apply.
T17 WAL ACTIVE sin usar immutable=1; no borrar WAL/SHM manualmente, integridad y FK OK, cero credenciales/cache externas.
T18 origen rollback → ROLLED_BACK y nunca lectura SQL11 del target.
T19 Android empaquetado/compilación DEV y JVM generational/app XML real, sin pruebas físicas G15 asumidas.

**Stop condition**: si la ruta directa COMPLETED no puede validarse con journal+ticket exacto bajo el mismo root lock y owner, mantener B7 BLOCKED. Nunca habilitar stock real.

## B7 — Disposición del código C1C previo
El SHA `ce12c6f2bbb5c13faafdd8d4930c4cd19335e801` tiene **CI 16/16 PASS scoped**, pero **B7 RED_FIX** por T13/T16 aún no verificados. Esta evidencia no revoca pruebas anteriores: exige delta acotado dentro de la MISMA unidad, con permiso nuevo y guardia, CI/XML real y B7 independiente. V2 productiva, pagos, Auto/Noche y stock real OFF.
