# FARO V5 — M1C1B1B RED_FIX B7: import faltante en test Android DEV
Fecha AR 2026-11-17. Unidad S4_W01_M1C1B1B_FINANCE9_ANDROID_STAGE_GATE.
Código inicial exacto bcd29ccabdf1e4f6144aadaadee2bef578f49616. HEAD base de auditoría 760d38f31318e0b6ef742fb8b0be5b1baa48eef0.
Permiso inicial consumido BLOCKED. Writer adquirido CAS epoch1043 exclusivamente para RED_FIX.

## B7 — RED_FIX, no PASS
La guardia del código funcional 44059395073 terminó SUCCESS en SHA bcd29ccab... y el diff fue exactamente las 6 rutas autorizadas. El job nuevo de SQLite/Reality en CI dirigida 44059395512 completó SUCCESS la primera etapa, pero todavía no se da PASS a la unidad completa. El test de app nuevo S4W01M1C1B1BFinance9StageGateTest.kt importa kernel.contracts.*, maintenance.* y owner.finance.*, pero invoca PreparedPlan.of(...) sin importar ar.com.faro.v5.kernel.runtime.PreparedPlan. Los tests anteriores del app sí usan el import del package runtime. Esto impide :app:compileDebugUnitTestKotlin/:app:compileV2CoreDevUnitTestKotlin.
Cuatro regresiones automatizadas sobre la SHA inicial fallaron por el MISMO Unresolved reference 'PreparedPlan' al compilar tests Android: S3 Home Notice 44059395040, V2 Core M1/M2 44059395118, S3 A4 N031 44059395262 y N033C2B4 44059395321. No se les atribuyen fallas lógicas de comportamiento. No se recertifican ni se marcan PASS.
La etapa Android de CI dirigida 44059395512 todavía no estaba cerrada al confirmar esta auditoría. No inferir SUCCESS ni FAILURE final de ese run hasta consultar GitHub.

## Hipótesis y corrección delimitada
ÚNICO cambio funcional permitido del RED_FIX: agregar exactamente la línea
`import ar.com.faro.v5.kernel.runtime.PreparedPlan`
al encabezado de `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01M1C1B1BFinance9StageGateTest.kt`, sin cambiar ningún otro archivo, prueba, class name, grant, schema, host productivo ni motor Apply. No modificar workflows, manifest ni archivos legacy. No tocar el Kotlin de producción.
La segunda CI sobre nuevo SHA debe ejecutar el mismo workflow dirigido M1C1B1B, comprobar XML de 5 Reality + 8 legacy + 6 App, guard seven-block y las regresiones afectadas al app. Si un fallo distinto aparece, volver al bloque relevante; no hacer ensayo ciego.
La regresión del guard documental 44059421580 fue por dejar el permiso READY después del código inicial, y se corrigió consumiéndolo a BLOCKED; último guard del checkpoint 44059879599 COMPLETED SUCCESS. No confundir esa corrección administrativa con el error Kotlin.

## B5 — Restricciones adversariales mantenidas
No convertir objeto de presencia estructural en autenticación real, no montar el gate en Activity, shipping, main o Chat, no habilitar V2 productiva ni W2, no crear segundo SQL COMMIT. M1B físico/G15 queda NOT_RUN. Las certificaciones históricas M0, M1A, M1B, M1C0, M1C1A, M1C1B0 y M1C1B1A conservan su alcance.
Nuevo permiso READY debe declarar reviewed_head tras este documento, B1–B5 con evidencia exacta ya existente, B6 READY_TO_IMPLEMENT, B7 PENDING_FINAL_AUDIT, last_code_review=RED_FIX para bcd29ccab... con referencia a este blob. El siguiente commit funcional debe tocar SOLO el test y llevar FARO-UNIT igual a M1C1B1B.

## Checkpoint
B7 RED_FIX de SHA bcd29ccabdf1e4f6144aadaadee2bef578f49616, nunca PASS. Código nuevo pendiente de compilación y CI independiente. Writer debe liberarse tras permiso documental.
