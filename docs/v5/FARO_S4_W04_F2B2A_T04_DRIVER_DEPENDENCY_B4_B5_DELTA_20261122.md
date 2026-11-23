# FARO V5 — F2B2A T04 · delta B4/B5 de SQLite sin romper pureza de módulos
Fecha 2026-11-22. SAME UNIT `S4_W04_F2B2A_HOUSEHOLD10_TYPED_SEALED_STAGE_ADMISSION_DEV`.
B1–B5 originales `docs/v5/FARO_S4_W04_F2B2A_TYPED_SEALED_ADMISSION_B1_B5_20261122.md` (blob `5573038f92cc42d5732295a62247d5e21f3507a1`); RED_FIX real `docs/v5/FARO_S4_W04_F2B2A_B7_RED_FIX_TRIGGER_20261122.md` (blob `85949a2c48a91e8ab8d255cfb90caf8fdacaab98`). No repetir certificados.

## B4 — Rutas exactas actualizadas por invariante estructural
La indicación inicial RED_FIX de instanciar `BundledSQLiteDriver` directamente desde Graph es incompatible con el check estructural ya existente en `v5/build.gradle.kts` (líneas 497–514): `:store:generation` no puede importar `androidx.`, ni `SQLiteDriver`, ni drivers SQL. También `v5/store/generation/build.gradle.kts` solo declara driver como `testImplementation`. Rechazar agregar dependencia main o romper esa frontera.

Solución mínima de DOS rutas funcionales y workflow:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/StagedSqlitePreflight.kt`: sumar a `Schema` la opción opt-in `rejectTriggers: Boolean = false`; usando su conexión ya abierta `BundledSQLiteDriver` en modo `SQLITE_OPEN_READONLY | SQLITE_OPEN_NOFOLLOW | SQLITE_OPEN_URI` URI immutable de STAGED sellado, exigir cero filas en `sqlite_master WHERE type='trigger'` antes de aceptación.
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`: al construir exactamente el schema Commercial Evidence2 de STAGED typed, `rejectTriggers = commercialEvidence2StagedDev`. Legacy schema1 y ACTIVE mantienen false.
3. `.github/workflows/faro-v5-s4-w04-f2b2a-sealed-typed-stage.yml`: incluir ruta `StagedSqlitePreflight.kt` en disparo; mantener seis tests, preflight READY y XML.
4. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household10Evidence2SealedStageAdmissionTest.kt`: permitido como ruta auditada, **no modificar**, preservar test adversarial T04 de trigger y orphan.
Se corrige el anterior borrador de dependencia directa que nunca fue implementado. Permit EXACT4 reemplaza el alcance previo. Ninguna modificación Gradle ni nueva dependencia.

## B5 — Verificación adversarial de arquitectura y seguridad
El opt-in predeterminado `false` evita cambiar validaciones Legacy, Finance9 o ACTIVE. El método `StagedSqlitePreflight.requireAll` ya comprueba inexistencia de `-wal/-shm/-journal`, archivo físico sin symlink, `user_version`, tablas, `quick_check`, `foreign_key_check` y usa `BundledSQLiteDriver` READONLY inmutable; añadir el rechazo de triggers dentro de esa misma apertura es más seguro, sin second open, sin mutación, sin esquema nuevo, sin race entre verificaciones. Rechazar antes de finalizar aceptación. La rama typed Evidence2 debe mantener 6/6, T04 también comprueba orphan FK. Se conserva pureza de `:store:generation`, sin driver SQL directo y sin tocar code genérico más allá del boolean opt-in. No cambios a Apply, puntero ACTIVE, cutover, archiving, pagos, red, main, G15, V2 productiva. Si no supera compilación o tests, RED_FIX de la misma unidad.
