# FARO V5 — W04-F2B2B01 B7 RED_FIX Kotlin SMARTCAST
Fecha local 2026-11-22/23. SAME UNIT `S4_W04_F2B2B01_TYPED_FBS2V7_CODEC_DEV`.
Código `ead893930b114a709ae7eac7a32f9798aad54be3`, READY parent `f5ab8de8afd0f2e6683d91dd97d9ff35553a2257`. B1–B5 original `docs/v5/FARO_S4_W04_F2B2B01_TYPED_FBS2V7_CODEC_B1_B5_20261122.md`, blob `a1e3632e1465bad040ba028b3ee721d89b1071c3`. Guardia de READY `45098381479` SUCCESS. Guardia de código `45098567664` SUCCESS.

## B7 — RED_FIX (NO PASS)
Workflow dirigido `45098567829`, job `137443852600`, SHA exacto del código: COMPLETED FAILURE. Preflight B1–B5/permiso y SDK37 PASARON. Falló `:store:generation:compileKotlin` ANTES de ejecutar pruebas JUnit: `SMARTCAST_IMPOSSIBLE` en `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10Fbs2Codec.kt:108`. Mensaje del compilador: `Smart cast to 'CommercialEvidenceStagedTypedBackupSnapshot' is impossible, because 'stagedTyped' is a public API property declared in different module.` No hubo XML JUnit ni artifact. **No declarar 6/6 PASS**. Es un error real de tipo entre módulos; no implica que T01–T06 hayan sido ejecutados.

## B5 — Corrección única y prueba falsable
En `FaroV5Household10Fbs2Codec.kt`, rama de `requireEvidenceProfile`, sustituir el acceso inseguro `evidence.stagedTyped.rows.map { it.record }` por la cadena segura `evidence.stagedTyped?.rows?.map { it.record }`. La comprobación sigue exigiendo `schemaVersion == 2`, `stagedTyped?.schemaVersion == 2` y correspondencia exacta con `records`. Null continúa rechazado. NO modificar tests, parser v6, egress, crypto, SQL o cualquier otro archivo funcional.

Consumir el permiso original y registrar `last_code_review=RED_FIX`, guard documental SUCCESS; reemitir READY SAME UNIT con B1–B5 ya verificados y scope EXACT4 (aunque solo cambiará 1 ruta), entonces commit `FARO-UNIT:S4_W04_F2B2B01_TYPED_FBS2V7_CODEC_DEV`, guard de protocolo + CI 6 casos nuevos y XML descargado. Cualquier nuevo fallo exige otro RED_FIX exacto, no cambiar scope ni habilitar cutover. Writer debe quedar RELEASED durante CI.

F2B2B02 captura/restauración tipada, F2B2C journal/recovery y F2C Android permanecen pendientes. V2 productiva OFF; main/V4.4/pagos/Apply/G15 intactos.
