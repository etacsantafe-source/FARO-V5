# FARO V5 — G4 real federation RED_FIX · B1–B5 + B7 rejection

Fecha 05/11/2026. Unidad original `S3_A2_G4_REAL_FEDERATION_TYPED_NAV` (mismo unit_id, NO otro proyecto). SHA funcional evaluado `05de1c6d22deed15ab5094ce4af5c3e091ffee4d`, parent READY `f85fb2e3372daf38e0e2ed51fbb4402984fa8425`.

## B1 EVIDENCIA NUEVA
- Guardia obligatorio [#42015087329](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42015087329) SUCCESS. Canonical Search [#42015087370](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42015087370) SUCCESS, Surface [#42015087287](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42015087287) SUCCESS, Home [#42015087197](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42015087197) SUCCESS, APK aislado [#42015087367](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42015087367) SUCCESS; no se relanzan esos PASS.
- G4 dirigido [#42015087253](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42015087253) FAILURE: **27 JVM, 26 PASS, 1 FAIL**. Sólo `FaroV5FederatedSearchSurfaceAdapterTest.pointerSwitchRevokesAllSourcesWithoutFalseEmptyComplete` lanza `IllegalArgumentException` desde `.hits.single()` (ahora existen cuatro hits legítimos). No hay fallo de Kotlin compile en ese job.
- V2 core dirigido [#42015087300](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42015087300) FAILURE: múltiples aperturas `FaroV5V2CoreDevAuthorityTest.open()` rechazan con `IllegalStateException`. Causa comprobada por lectura de código: `RealityStore.SCHEMA_VERSION=6`, pero `FaroV5GenerationStoreGraph.requireStrictV2StoreSchemas` todavía exige `StagedSqlitePreflight.Schema(REALITY,5,...)`. No equivale a corrupción ni habilita saltar el guard.
- `StagedSqlitePreflight.requireAll` y `ActiveSqlitePreflight.requireAll` sólo aceptan `version == schema.version`. Nuevo esquema6 tiene cuatro tablas `objective_state/objective_record/matter_state/matter_record`; el esquema5 histórico carece de ellas y debe poder reconocerse de forma limitada antes de migración segura.
- Writer RELEASED epoch255 y permisos del mismo unit_id listos para auditar RED_FIX. V2 shipping sigue OFF.

## B2 COMPARACIÓN
- G1/G2/G3 y viejo esquema5 son contratos probados S2; NO convertir preflight en `version >=5`, ni permitir downgrade, tablas faltantes o compatibilidad implícita para otros stores.
- El preflight debería aceptar explícitamente v5 con SOLO tablas históricas o v6 con tablas históricas+cuatro G4; luego `RealityStore` abre y ejecuta migración transaccional5→6 bajo gate existente. El certificado de stage no nace de un `catch Exception`.
- El test de G4 debe admitir cardinalidad 4 cuando comprueba revocación global. La prueba sigue verificando que el hit elegido no pueda abrirse tras el switch.

## B3 INVESTIGACIÓN Y ALTERNATIVAS
- Android Database Migrations https://developer.android.com/training/data-storage/room/migrating-db-versions : conservar ruta explícita de migración y pruebas de todas las versiones; rechazar fallback destructivo. ADAPTAR compatibilidad 5/6 acotada a Reality.
- SQLite `PRAGMA user_version` y cambios de esquema https://www.sqlite.org/pragma.html#pragma_user_version y https://www.sqlite.org/lang_altertable.html : versión debe corroborarse con tablas reales; NO reasignar user_version falso.
- Opción A ELEGIDA: campos opcionales `legacyVersion/legacyTables` en `StagedSqlitePreflight.Schema`, checks exactos en los dos preflights y G4 Reality(expected6,legacy5), otras bases permanecen EXACTAS. Migración la realiza SOLO `RealityStore`.
- Opción B DESCARTADA: reducir RealityStore a 5 o no usar user_version; ocultaría el cambio G4. Opción C DESCARTADA: desactivar preflight o permitir 5..max; expondría archivos corruptos/host antiguos.
- Sin dependencias nuevas, APIs pagas, activación V2 shipping ni cambio de datos físicos.

## B4 UNIFICACIÓN DE CORRECCIÓN / RUTAS EXACTAS
1. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5FederatedSearchSurfaceAdapterTest.kt`: `.hits.single()` → `.hits.first()` en ÚNICO test de pointer switch, conservar el resto sin modificación.
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/StagedSqlitePreflight.kt`: `Schema` soporta SOLO legacyVersion opcional menor a versión actual y `legacyTables` explícitas, con chequeos; intactas versiones de las otras 4 stores.
3. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/ActiveSqlitePreflight.kt`: misma regla de decisión exacta de v5/v6; revisar requiredTables según versión observada, no aceptar versiones arbitrarias.
4. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`: establecer Reality actual6 y tablas G4, legacy5 con 8 tablas previas, ninguna otra schema admite legacy.
- Cuatro rutas, mismo `FARO-UNIT:S3_A2_G4_REAL_FEDERATION_TYPED_NAV`. Pruebas CI: G4 27/27, V2 Core runtime y APK, guard; si el RED persiste, no declarar PASS.

## B5 NEGATIVAS Y LÍMITES
- Versiones 4,7, sin user_version o tablas incompletas deben STOP; por defecto `legacyVersion=null` no permite downgrade en Commercial, Web, Auxiliary o Workspace. Active WAL usa READONLY normal, staged sigue `immutable=1`; no mutar preflight.
- Un esquema5 previo válido puede migrar sólo mediante RealityStore con MaterialWriteGate; ningún preflight de solo lectura migra. El test debe verificar que el source realmente construye cuatro hits ANTES de seleccionar uno para revocación.
- La corrección del fixture no reduce cobertura: sigue prohibiendo resultado antiguo tras switch y resto 26 casos permanecen.
- Si CI sigue fallando, diagnosticar evidencia nueva, no publicar B7 PASS ni activar V2.

## B7 RED_FIX AUDITORÍA DEL COMMIT PREVIO
- SHA `05de1c6d22deed15ab5094ce4af5c3e091ffee4d` NO recibe PASS global: 26/27 G4 y V2 Core rojo, guard y otras dirigidas verdes. Dictamen **RED_FIX** del MISMO unit_id, razones de corrección concretas. Ningún G1–G3 se reabre por rutina.
- No actualizar A2 a 4/5 hasta obtener nuevo SHA con G4 27/27, V2 Core SUCCESS y APK manifest CI verde. S2 8/8 histórico conserva su certificación; S3 formal 0/5, G4 OPEN.
