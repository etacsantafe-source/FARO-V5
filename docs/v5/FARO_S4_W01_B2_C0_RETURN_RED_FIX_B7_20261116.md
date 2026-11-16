# FARO V5 — W01-B2-C0 B7 RED_FIX: Kotlin labelled return newline
Fecha AR: 2026-11-16; SAME UNIT S4_W01_B2_C0_DEV_FINANCE_REVIEWED_APPLY_TEST_HOST.
Código inicial 9d3a0abba7ba4190225e547e2c2327e0512fbd1c; guard 43881678616 SUCCESS.
CI 43881678355 FAILURE, job 133348879495:
error compilador Kotlin RETURN_TYPE_MISMATCH, ubicación
v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B2C0FinanceDevApplyHost.kt línea 91.
Se esperaba ConversationApplyHostOutcome y se obtuvo Unit.
No se ejecutó el JUnit dirigido ni se accedió a datos de usuario.

## B1
Diagnóstico directo del fuente: 5 ocurrencias de
return@withMaterialWrite seguidas de salto de línea y resultado
ConversationApplyHostOutcome.Denied(...). Kotlin termina la
sentencia de return antes de expresión; el cuerpo devuelve Unit.
La primera falla ocurre en línea 91.

## B2
Reusar el método, condiciones, código y tests originales.
La corrección es estrictamente de sintaxis: pasar
return@withMaterialWrite ConversationApplyHostOutcome.Denied(...)
a una sola expresión, con paréntesis si ocupa dos líneas.
No tocar lógica, cat, Reality/Workspace, permisos, grants,
tests ni workflow.

## B3
Opción A elegida: agrupar cada return etiquetado con su expresión.
B modificar tipo de retorno a Unit: RECHAZADA.
C cambiar expectations de XML: RECHAZADA.
D deshabilitar verificaciones por fallo de compilación: RECHAZADA.

## B4
Modificar SOLO un archivo funcional dentro de ruta READY:
v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B2C0FinanceDevApplyHost.kt,
5 sitios. Mantener todos siete tests intactos.
Luego repetir CI EXACTA 7/7, guard y B7 final.

## B5
Verificar que ningún cambio altere el orden de admission,
scope/lease, Workspace CAS, RealityTransactionScope y COMMIT.
El host no está montado en Android, policy de prueba no es
principal autenticado y policyEpoch cross-process sigue STOP.
No respaldar un PASS sin XML real.

## B7
RED_FIX del mismo permiso READY, todavía NO PASS.
Nuevo commit funcional debe citar FARO-UNIT y diff solo ruta
autorizada; guard mismo SHA, CI final 7/7, y B7 doc separado.
