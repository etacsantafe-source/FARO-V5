# FARO V5 — S3.A5.7 X06 Search Workspace Android, precisión B4/B5
Fecha 2026-11-09 AR. Complementa el B1–B5 preaudit `docs/s3-night/S3_A5_7_X06_X08_FEDERATED_SEARCH_ANDROID_B1_B5_20261109.md`. Commit funcional anterior `675241634ce62e9ab4324ac178e0a9ab93318910` con B7 PASS_SCOPED 3/3 y guard verde, docs/s3-night/S3_A5_7_X01_X02_ANDROID_B7_PASS_SCOPED_20261109.md. Writer HELD epoch629.

## B4 — Delimitación binaria de la primera aceptación
- Unit `S3_A5_7_X06_SEARCH_ANDROID_SCOPED`: exactamente DOS nuevos tests Android API35, solo nuevo source file g5AndroidTest y nuevo workflow dirigido, ninguna edición productiva.
- Caso 1: click nativo "Abrir Chat local de desarrollo" en RESUMED, mensaje USER único creado con Composer UI real y guardado en Workspace, navegación drawer "Buscar", campo de UI etiquetado "Buscar en FARO", botón "Buscar". Esperar hit de `workspace.conversations`, abrirlo mediante TextButton de hit y exigir navegación confirmada a Chat con mensaje canónico real. No leer datos históricos. Sin modelo ni Apply.
- Caso 2: consultar texto UUID inexistente en Search Compose con cuatro sources montados en DEV, esperar una de tres coberturas explícitas (COMPLETE, PARTIAL, UNAVAILABLE) y comprobar que nunca se presenta a la vez la declaración COMPLETE sin hits con la de PARTIAL. La prueba de texto UX NO fuerza partial; X08 global continúa OPEN.
- La UI del campo y botón de Buscar se seleccionará mediante matcher semántico REAL `hasSetTextAction()` y `hasText("Buscar") and hasClickAction()` o equivalentes documentados; no asumir índices de TopAppBar como botones, no introducir imports inventados. Verificar antes uso de estos matchers en Android Compose API.
- Registrar solo X06_WORKSPACE_UI_PASS_SCOPED si dos tests y guard PASS mismo SHA; no contar X06 cuatro owners con datos ni X08 parcial revocada como cerrados.

## B5 — Ataques y puertas
- El hit debe estar respaldado por `FaroV5WorkspaceSearchReadSource`, que busca títulos y mensajes canónicos, y `FaroV5FederatedSearchSurfaceAdapter.openAuthorized` revalida authorityStamp; el click por sí mismo jamás muta Reality.
- Si la navegación falla por texto no indexado o el hit no se genera en Workspace, B7 RED_FIX con dato real, NO modificar producción sin un permiso propio.
- Si no hay resultado en CI, no aceptar "No se encontraron..." como éxito del test 1. Si cobertura del test 2 es PARTIAL/UNAVAILABLE, conservarla como tal, sin inventar dato.
- No repetir A5 44 tests previos: seis subunidades 33/33, owner rev4/4, native2/2, Chat Compose2/2, Activity/Avisos3/3. A3 +8 Guardados NO AUTORIZADO, catálogo172 congelado, G15 físico una campaña final, V2 productiva/S4/Auto/Noche OFF.
- B1–B3 originales siguen vigentes; no reabrir auditorías cumplidas. B7 solo después de logs Android `OK (2 tests)`, `INSTRUMENTATION_CODE: -1` y guard mismo SHA.
