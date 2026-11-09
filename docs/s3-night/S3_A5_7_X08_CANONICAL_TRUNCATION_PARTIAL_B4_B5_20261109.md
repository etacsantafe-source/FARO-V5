# FARO V5 — S3.A5.7 X08 cobertura parcial real por límite canónico — B4/B5
Fecha AR 2026-11-09. SOLO B4/B5 de viabilidad documental, no código. Complementa `docs/s3-night/S3_A5_7_X06_X08_FEDERATED_SEARCH_ANDROID_B1_B5_20261109.md` sin reabrir lo certificado. X06 Android 2 tests código e857e57a CI #42625768734 en curso al documentar; NO nuevo permiso funcional hasta su B7.

## B1 — Evidencia de fuentes reales
- `ConversationDraftPolicy.MAX_TEXT_CHARS = 32_000` (v5/kernel/contracts/src/main/kotlin/ar/com/faro/v5/kernel/contracts/ConversationDraft.kt). Compositor Compose acepta mensajes hasta ese máximo.
- `FaroV5WorkspaceSearchReadSource.readAuthorized` invoca `ConversationWorkspaceStore.readSearchableConversationText(conversation.id, minOf(4_096, remainingTextChars))` por conversación. Si `body.complete == false`, `allContentCovered=false`, snapshot `complete=false`.
- `ConversationWorkspaceStore.readSearchableConversationText` retorna fragmento hasta `maxChars`, marca `complete=false` cuando un único USER ACCEPTED supera dicho límite, sin inventar ausencia.
- `FaroV5FederatedSearchSurfaceAdapter.search` exige `coverageBySource` COMPLETE para cuatro owners Y Workspace page COMPLETE; de lo contrario devuelve `FaroV5SearchCoverage.PARTIAL` cuando existe al menos una fuente legible. `FaroV5SearchSurfacePolicy.message` con PARTIAL sin hits emite `Cobertura parcial: pueden existir más resultados.`, nunca `No se encontraron resultados en las fuentes consultadas.`.

## B2 — Comparación y cambio de hipótesis
- B1–B5 Search anterior sostenía que no había fixture real para PARTIAL sin caída de owner. Ahora se verificó una **segunda causa contractual de cobertura parcial**: truncación canónica por límite 4096. No simula un fallo ni altera producción, usa owner real. Esto habilita una prueba determinista para un aspecto de X08.
- No se repiten 44 suites históricas; solo se probará la rama que estaba NOT_RUN, tras B7 del último código X06.

## B3 — Elección técnica e investigación
- API pública Compose Testing https://developer.android.com/develop/ui/compose/testing permite `performTextInput` de texto sintético a campo existente. Fuente de límites/semántica prioritaria es el Kotlin real del repo, no una inferencia.
- Elegida: enviar un USER real de ~5000 caracteres por compositor y buscar un token único ubicado DESPUÉS del carácter 4096. Se espera `PARTIAL` sin hits y advertencia de cobertura, no "sin resultados".
- Rechazadas: mockear SearchReadPort para forzar PARTIAL (no prueba grafo real), denegar ACL por reflection, activar A3/V2 productiva, usar Moto G15 ahora, llenar 17 conversaciones para agotar presupuesto global (redundante y más lento).

## B4 — Aceptación acotada futura
- Nueva clase AndroidTest distinta de X06, un solo test de X08 PARTIAL_TRUNCATION_REAL: (1) botón nativo abre Chat DEV; (2) ingresar texto USER sintético >4096, con token secreto solo al final; (3) esperar Workspace USER guardado real; (4) abrir drawer Buscar y consultar token final; (5) exigir literalmente `Cobertura parcial: pueden existir más resultados.`; (6) exigir ausencia de `No se encontraron resultados en las fuentes consultadas.` y cero hits del token no leído; no inferir éxito ni datos globales.
- Cualquier falta de ese mensaje es RED y requiere B7 análisis, sin relajar assertions. Workflow Android API35 dirigido solo ese test, exige `OK (1 test)` y `INSTRUMENTATION_CODE: -1`, guard B1–B7 del SHA exacto.
- La UI no debe exponer los ~5000 caracteres en Search, no reenviar a IA, no mutar Reality. El permiso futuro solo permitirá nuevo test y workflow. No escribir aún.

## B5 — Límites y STOP
- Esta prueba demuestra X08_PARTIAL_CANONICAL_TEXT_BUDGET, NO revocación de ACL en la mitad del read ni PARTIAL por 1 de 4 owners offline, NO X08 global.
- El token no debe estar en los primeros 4096 caracteres; usar prefijo alfabético de 4900 o 5000 chars y token solo al final, longitud total menor de 32000; UUID irrepetible. Un texto de prueba no es dato de usuario.
- Compose puede tener un máximo visual de 5 líneas pero acepta hasta MAX_TEXT_CHARS; `performTextInput` no debe reemplazar el texto de Buscar accidentalmente. Enviar una vez, no rerun de suite anterior.
- A3 no autorizado, catálogo172 intacto, G15 una campaña física final, S4/V2 productiva/Auto/Noche OFF. No abrir código X08 antes de terminar B7 del code SHA e857e57a.
