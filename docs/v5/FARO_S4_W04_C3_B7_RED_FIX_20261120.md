# FARO V5 — W04-C3 · B7 RED_FIX del B6 inicial (fixture ACL vs historia sin datos)

Fecha local 2026-11-20. Unidad `S4_W04_C3_STAGED_TYPED_APPEND_REFRESH_DEV`, código `698a093c29240806e7e27fc5a9f08cc98190b568`, parent permiso READY `54e75ebd2b84fec4287a792cfaeabee6d0ef978a`. Guardia de código `44851508611` COMPLETED SUCCESS, preflight EXACT3 PASS. Preauditoría B1–B5 `docs/v5/FARO_S4_W04_C3_APPEND_REFRESH_PREAUDIT_20261120.md`, blob `0af24f8d6f1afa88922ca0f5b1f3330a97ecbdf3`, guard de permiso `44851309307` SUCCESS.

## B7

**RED_FIX, NO PASS.** CI `44851508657` COMPLETED FAILURE sobre SHA exacto. `:evidence:test` ejecutó 99 pruebas: **1 fallo**, `CommercialTypedPriceRefreshTest > toolSourceAndMissingProductAreNotManualRefresh()` con `AssertionFailedError` en `CommercialTypedPriceRefreshTest.kt:173`. El pipeline falló al ejecutar pruebas, no en preflight ni en compilación. Workflow subió artefacto `14684989552` `w04-c3-staged-typed-refresh-xml` (10432 bytes). No se puede declarar C3 B7 PASS.

**Causa y semántica correcta:** El test pasa `productKey=other-product`, pero el fixture ACL admite solamente `rice-500`. El servicio deniega con `NOT_AUTHORIZED` ANTES de consultar historial: es la respuesta correcta; el test esperaba `NO_EXISTING_HISTORY`, estado que sólo corresponde a un producto previamente autorizado pero sin observaciones. No flexibilizar el código de producción ni invertir las puertas de ACL.

## B5 — Delta adversarial de reparación acotada

1. En `CommercialTypedPriceRefreshTest.kt` añadir al fixture una autorización EXPLÍCITA para `other-product` controlada por variable de test (por defecto false) y exigida en ambos callbacks Household y Commercial, sin interferir con pruebas de revocación de `rice-500`.
2. Primero verificar que `other-product` no autorizado produce `NOT_AUTHORIZED`; luego habilitar únicamente ese producto en ambos grants y verificar `NO_EXISTING_HISTORY` sin escritura. Mantener prueba `TOOL` que devuelve `INVALID_INPUT`.
3. Mantener los otros 11 casos, atomía de Evidence2, control de revisión, snapshot watermark e idempotencia. No modificar `CommercialTypedPriceRefresh.kt`, SQL, C2, C1B2, Finance ni ACTIVE.
4. El workflow inicial exige `count == 3` para primer B6, pero el RED_FIX sólo cambia test + workflow (subconjunto 2/3). Cambiar a `1 <= count <= 3`, manteniendo `faro-protocol-gate.py --mode preflight --head HEAD^ --unit ... --path ...` POR CADA RUTA REAL y el marker `FARO-UNIT:S4_W04_C3_STAGED_TYPED_APPEND_REFRESH_DEV`. No abrir rutas nuevas.
5. Emitir permiso SAME UNIT READY exact3 en documento previo, con `last_code_review.result=RED_FIX`, blob SHA real de este B7 y guard verde del commit documental. La siguiente funcional será del mismo unit C3; no pasar a W4-D.
6. Ejecutar `:evidence:test` JVM y comprobar XML exacto 12/12, sin fallos, errores u omisiones, y guardia por SHA. Si falla de nuevo, RED_FIX real; si pasa, B7 PASS independiente y release writer.

**No se realizaron pruebas Android/G15, egress, pagos ni activación productiva.** Estado C3 `RED_FIX_PENDING`, C2 B7 PASS previo no se reabre.
