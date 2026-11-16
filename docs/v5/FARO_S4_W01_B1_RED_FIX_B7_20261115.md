# FARO V5 — S4 W01-B1 · B7 RED_FIX de fixture de chat vacío
Fecha AR 2026-11-15. Unidad S4_W01_B1_DEV_CANONICAL_FINANCE_REVIEW_CREATION.
Código fad7545710800cc177fbcbb592bb6f764cb6223f; guard protocolo 43789125545 SUCCESS exact SHA.
CI dirigida 43789125645 COMPLETED/FAILURE, job 133043313241, compilación correcta,
7 pruebas ejecutadas, 6 PASS y 1 FAIL en
S4W01B1FinanceReviewHostTest.workspaceRevisionAndChatSelectionAreRechecked
línea 148. XML final OMITIDO por fallar tests.

## B1
El test llamó Workspace.createAndSelect(other) con el chat original recién
creado, sin mensajes ni borrador. ConversationWorkspaceStore.create() conserva
la regla histórica de reutilización de chat activo vacío; devuelve Saved sin
crear/cambiar selección. El test esperaba erróneamente un chat distinto.
El host W01-B1 preservó una revisión legítima en el chat aún activo.
El mismo defecto del fixture afecta la segunda sección de carrera (callback
que intenta crear chat mientras el original continúa vacío).
## B2
El comportamiento está documentado en la política de reutilización de V4.4
ya certificada; no cambiar Workspace ni el host. Se reutiliza el método
ConversationTimelinePort.writeTimeline para sembrar SOLO un mensaje USER
ACCEPTED real en el fixture de ambos escenarios, luego crear/seleccionar
el segundo chat. Pruebas de B1 de los otros seis escenarios no se repiten
por separado; reejecutar la suite dirigida para comprobar 7/7.
## B3
A: sembrar un mensaje de usuario persistido antes de cambiar selección, 
VALIDA la condición real y conserva la política Workspace; elegida.
B: cambiar Workspace para forzar un nuevo chat vacío: RECHAZADA.
C: debilitar el test para no revisar cambio de chat: RECHAZADA.
D: montar Apply/Finance para causar actividad falsa: RECHAZADA.
Fuente del contrato: ConversationWorkspaceStore.create() branch empty-chat
reuse; ConversationTimelineWriteRequest/ConversationTimelineIntent.AcceptUser.
## B4
Corrección SOLO en
v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B1FinanceReviewHostTest.kt:
helper seedAcceptedUserMessage() con autoridad/revisión de Workspace real;
ambos escenarios T07 llaman seed antes de createAndSelect.
No cambios al host, Kotlin productivo, workflow, catálogo ni schema.
## B5
Con chat vacío: Workspace DEBE conservar el original; no probar esto como
revocación. Con mensaje aceptado, selección debe cambiar y host negar Review
de chat anterior, incluida carrera tras segunda callback. No borrar los
otros 6 tests ni invertir expectaciones. V2 productiva/Auto/Noche, main/V4.4,
S2/S3, datos/móvil y pagos externos permanecen intactos.
## B7
RED_FIX de MISMA unidad, no PASS. Permiso READY solo para corrección de
fixture y CI dirigida 7/7. Requiere nuevo commit funcional con FARO-UNIT,
guard exacto y B7 final cuando XML confirme.
