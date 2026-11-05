# FARO V5 — S3.A1 N015 schema7 DEV graph · B7 auditoría independiente (05/11/2026)

## B7 AUDITORÍA FINAL INDEPENDIENTE

**Resultado: PASS_SCOPED para el RESTABLECIMIENTO DE V2 DEV con Reality esquema 7.** No equivale a certificación de migración de todos los datos viejos ni al cierre S3.A1. Código SHA exacto `dc06c01da9597c6642d7e4055f0d71cb11fb217e` después del permiso `1eb0f5061e2fb04ecc86a9abef6f4808549a2a27`; unidad `S3_A1_N015_SCHEMA7_DEV_GRAPH_PREFLIGHT`; preauditoría `docs/s3-night/S3_A1_N015_SCHEMA7_DEV_GRAPH_PREFLIGHT_B1_B5_20261105.md` y evidencia inmutable correspondiente.

### Hallazgo y comprobación de regresión
Antes: [42110935035](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42110935035), V2 core M2 **FAILURE**, 11/11 tests fallaron en apertura de V2 después de migrar Reality a esquema 7 porque `FaroV5GenerationStoreGraph.requireStrictV2StoreSchemas` aún exigía esquema 6.

Después, exactamente SHA `dc06c01d`:
- [42113189767](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42113189767) **SUCCESS** y log: `V2_CORE_DEV_SEARCH_SESSION_XML_PASS 10/10`, `G5_REAL_HOST_FOUR_OWNER_XML_PASS 1/1`, `M2_DEBUG_T4_RELOCATED_XML_PASS 7/7`, `V2_CORE_M2B_DEV_APP_ISOLATED_PASS 4/4` y `V2_CORE_M1_ISOLATION_PASS 4/4`. Reproduce real bootstrap del grafo nuevo con SQLite 7.
- [42113190102](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42113190102) **SUCCESS** Reality 9/9 regresión.
- [42113189882](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42113189882) **SUCCESS** recibos cronológicos y FBS1/FBS2.
- [42113190220](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42113190220) **SUCCESS** guard siete bloques, SHA código exacto.
- Commit documental WAIT_CI `d8f978164b9479d5672c0db4c62ea6879d155db1` dio [42113345780](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42113345780) **FAILURE** solo preflight `previous code change has no B7 disposition`, esperado hasta este cierre y su permiso. No ocultarlo.

### Diff y garantías / excepciones
Modifica 3 archivos autorizados: `StagedSqlitePreflight.Schema` añade segunda versión legacy **explícita** y tablas asociadas; `FaroV5GenerationStoreGraph` acepta únicamente Reality 7 (12 tablas), 6 (12) y 5 (8), conserva controles de tablas, quick_check, FKs, symlinks y WAL; el escritor de migración sigue siendo solo `RealityStore`. Se agregaron dos pruebas nuevas en `FaroV5GenerationStoreGraphV2T2Test.kt` para matriz 5/6/7 y upgrade de fixture 6→7. No modifica Home ni V2 productiva, no cambia datos del teléfono.

**COVERAGE DEBIDA (NO DECLARAR PASS):** estas dos pruebas específicas de generación **NO_RUN** en los workflows existentes; M2 11/11 sí ejercitó apertura esquema7, pero no prueba por sí solo la migración de una base real esquema6 con datos. Hasta ejecutar la suite `FaroV5GenerationStoreGraphV2T2Test`, compatibilidad 5/6 histórica conserva calificación **NOT_CERTIFIED**. Los datos existentes nunca deben borrarse/reinicializarse ante falla. No se autoriza prueba física ahora ni ejecutar workflows manuales sin instrucción.

### Estado y próximo paso
B7 PASS_SCOPED *únicamente para restaurar apertura DEV nueva esquema7*, no para legacy5/6 ni S3.A1 global. Dejar permiso BLOCKED después de registrar esta disposición, prohibir afirmar compatibilidad vieja certificada. Para cerrarla de forma completa, agregar/correr suite de 17 pruebas exactas en un futuro gate autorizado, luego verificar Android de acuerdo con campaña A5; no usar la ausencia de una prueba como éxito.

S3.A1 sigue OPEN por N015 `history.query` federado y grants reales, N031 owners combinados Matter/Reminder/MonthlyPlan/priority/snooze, y Guardados con owner propio; +8 operaciones siguen NO APROBADAS, catálogo 172. S2 8/8, S3.A2 5/5 automatizada; S4, V2 productiva, Auto y Noche OFF. Writer único epoch311 exclusivamente documental para B7, liberarlo tras checkpoint.
