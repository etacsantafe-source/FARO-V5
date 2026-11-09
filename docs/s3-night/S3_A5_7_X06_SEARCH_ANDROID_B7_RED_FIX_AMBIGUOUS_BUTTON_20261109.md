# FARO V5 — S3.A5.7 X06 Android Search B7 RED_FIX selector semántico
Fecha 2026-11-09 AR. Unit S3_A5_7_X06_SEARCH_ANDROID_SCOPED. **B7 RED_FIX, NO PASS** de X06; A5 GLOBAL OPEN.

## B1 — Evidencia de red observada
- B1–B5 original docs/s3-night/S3_A5_7_X06_X08_FEDERATED_SEARCH_ANDROID_B1_B5_20261109.md, blob 1e40a270e110cd3fee4f50ee60bdfa317bae2c7a, más B4/B5 docs/s3-night/S3_A5_7_X06_SEARCH_REAL_ANDROID_B4_B5_SCOPE_20261109.md, blob 75ee31e8cfb19ace741724d6b5623786440cddff, permiso READY anterior al código en HEAD a388a590dc9dd36b4310532e3ba613d0b15e4616.
- Código exacto e857e57af3fb89c1f50edbf3605bd1c25cc31d19, solo nuevo test Android y workflow. CI https://github.com/etacsantafe-source/FARO-V5/actions/runs/42625768734 job 129189024761 **COMPLETED FAILURE**. BUILD/APK instrumentación exitosas (S3_A5_7_SEARCH_COMPOSE_APKS_COMPILED), emulador Android35 ejecutó 2 tests, `Tests run: 2, Failures: 2`; guard https://github.com/etacsantafe-source/FARO-V5/actions/runs/42625768539 **COMPLETED SUCCESS** mismo código SHA.
- Ambos tests fallaron ANTES de solicitar búsqueda, en helper `searchFor` línea101 al ejecutar `rule.onNode(hasText("Buscar") and hasClickAction()).performClick()`: `AssertionError: Failed to inject touch input. Reason: Expected exactly '1' node but found '2'`.
- Árbol real de semántica del emulador: MATCH 1 = Node #132/#268 Role=Button, bounds positivos (l=44, t=697, r=298, b=807), texto Buscar y OnClick. MATCH 2 = Node #33/#168 Role=Tab, seleccionado pero fuera de pantalla bounds negativos (l=-968, r=-22), texto Buscar y OnClick. No es fallo de SQL, Search host ni ACL; ninguna aserción de búsqueda/navegación se ejecutó.

## B2 — Comparación con patrón V5 certificado
- Compose X12 test existente con B7 PASS usa `SemanticsNodeInteractionCollection[index]` (member get(index), no import externo) para elegir editor, evitando onLast/no match.
- El test de X06 ya usa ese patrón para editor. El matcher para botón se basó erróneamente en texto+click, que también coincide con un Tab oculto en el drawer. No hay evidencia de que la app productiva tenga 2 botones Buscar; son roles diferentes.

## B3 — Investigación de API y alternativas
- SemanticsNodeInteractionCollection API oficial AndroidX https://developer.android.com/reference/kotlin/androidx/compose/ui/test/SemanticsNodeInteractionCollection respalda member operator get(index) para seleccionar un nodo de colección.
- Compose Testing https://developer.android.com/develop/ui/compose/testing exige seleccionar por semántica verificable, no un click ambiguo.
- A ELEGIDA: conservar `hasText("Buscar") and hasClickAction()` y seleccionar **índice 0**, probado para AMBOS métodos del job como Role=Button visible; no introducir import/matcher nuevo ni código de producto. B DESCARTADA: deshabilitar drawer, modificar app o agregar testTag productivo. C POSIBLE pero menos mínima: agregar un matcher de Role.Button; no necesario con evidencia de orden real.

## B4 — Corrección exacta
- SOLO `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57SearchComposeUiTest.kt`.
- Reemplazar `rule.onNode(hasText("Buscar") and hasClickAction()).performClick()` por `rule.onAllNodes(hasText("Buscar") and hasClickAction())[0].performClick()` y actualizar comentario del selector con el motivo rol Button vs Tab offscreen. Conservar los DOS tests, sus afirmaciones, workflow, fuentes productivas, permisos Android, S4, catálogo172 y G15 sin tocar.
- El test de negocio X06 sigue sin veredicto. CI siguiente debe entregar stdout `OK (2 tests)`, `INSTRUMENTATION_CODE: -1`, workflow+guard SUCCESS mismo nuevo SHA.

## B5 — Riesgos residuales y STOP
- Index0 está respaldado por DOS trazas reales de test Android API35, no por suposición. Si cambia orden y la selección no es Button visible en futura UI, el test fallará de forma segura, no falsificará éxito. No aceptar el primer hit si no proviene de canonical Workspace.
- En caso de nuevo failure con causa distinta, nuevo B7 RED_FIX antes de ajuste; NO relajar assertions. X08 cobertura parcial por texto >4096 documentada aparte, no ejecutada ni computada.
- Mantener intactos 44 tests previos: 33 + 4 + 2 + 2 + 3. A3 Guardados X04/X05 no autorizado; V2 productiva/S4/Auto/Noche OFF; G15 único bloque físico final.

## B7 — Veredicto
**RED_FIX (NOT PASS)** para SHA e857e57a: dos errores de selección de UI idénticos; ambos tests Android intentaron ejecutar pero no llegaron a búsqueda. Solo permitir reparación B4 con B7 previo/permiso READY en padre exacto. Global A5 OPEN.
