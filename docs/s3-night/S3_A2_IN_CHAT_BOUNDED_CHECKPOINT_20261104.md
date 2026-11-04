# FARO V5 S3.A2 — búsqueda de Chat por mensaje, límites adaptativos · 04/11/2026

## Trabajo concreto publicado

- Punto de partida documentado: `c208f316`, S2 CLOSED 8/8, S3 OPEN, S4/V2/Auto/Noche OFF. Rama `work/s3-night-20261102`.
- Código funcional 1 `9bdbf7d44e21194e76f8aec1c35a01154d74d016`: búsqueda dentro de una conversación CANÓNICA existente, normalización de acentos/términos AND, coincidencia exacta por `ConversationMessageId`, anterior/siguiente y scroll/resaltado en `FaroV5MessageTimeline`. No índice secundario; no lee borradores o mensajes FARO sin estado COMPLETED; no toca Reality ni Apply.
- CI de código 1: run **#41836792753 SUCCESS** SHA exacto `9bdbf7d4`, LOG `S3_IN_CHAT_SEARCH_XML_PASS 5/5`, `S3_SURFACE_XML_PASS 55/55` y `BUILD SUCCESSFUL in 3m 6s`. La UI se compila; no hay prueba física.
- Código funcional 2 `09fa2b21d60011dc147971fa169829e3c8657b64`: análisis de recursos por consulta limitado a últimos **250 mensajes**, **64.000 caracteres canónicos** y **200 caracteres de consulta**; `FaroV5InChatSearchResult.complete` indica cobertura PARCIAL si se alcanza un límite. La UI no anuncia «sin coincidencias» definitivas cuando es parcial. Dos regresiones nuevas prueban límites de mensajes/caracteres; verificador XML espera 7/7.
- CI de código 2: run **#41837562790**, SHA `09fa2b21`, estado comprobado al checkpoint: **IN_PROGRESS**, conclusión **PENDIENTE**. NO declarar 7/7 ni 57/57 hasta observar salida XML en los logs del job.
- Writer GitHub: sesiones epoch 121 y 122, código publicado, writer RELEASED cada vez; sesión documental epoch 123 debe quedar RELEASED. CLOUD queue sigue STAGED, sin autorizar Auto/Noche.

## Comparación con V4.4 e integridad

- V4.4 `FaroV4GlobalSearch` recuperado solo READ_ONLY; se conservaron normalización/acento, conjunción de términos y procedimiento de relectura canónica. No se portó almacenamiento V4 ni se modificó el repo histórico.
- Esta función de Chat es local a un snapshot leído a través del `ConversationTimelineReadPort` del host; NO habilita búsqueda global. El port debe estar sujeto a generación y readiness reales. La búsqueda en Chat se muestra solo donde exista un timeline auténtico y la composición lo autorice.
- Bloqueo **G1/G2** persiste: shipping V1 no tiene quinto Workspace owner. El token `workspace_authority` es una protección de revisión/escritura, no una ACL de lectura revocable; readiness y pointer change tampoco equivalen a ACL independiente. `searchReadPort=null`, V2 OFF. **No crear grant ni epoch ficticio**.
- G3 parcial: el in-chat exact-message local ahora existe en código; búsqueda global sigue agrupada por conversación, sin salto global directo al `ConversationMessageId`. Falta probar invalidación UI de una pantalla de Chat ya montada tras revocación ACL; no afirmar protección completa en producción.
- G4: otras fuentes canónicas de Finanzas, Objetivos y Asuntos deben vincularse a propietarios reales. G5: X01–X12 y **una campaña física final**. Sin cambios en esquema/backup/FBS, main, V4.4, catálogo 172, credenciales o APIs pagas.

## Métricas y siguiente paso

- 2 commits funcionales nuevos y 1 CI confirmada SUCCESS exacta; segunda CI in_progress. Prueba física NOT_RUN; tiempos humanos NO_MEDIDOS; minutos de GitHub no son productividad humana.
- Prioridad híbrida única: terminar A2. Si segunda CI falla, diagnóstico de ese SHA y corrección mínima antes de extender. Si pasa, **G1 resolver autoridad ACL Workspace real con evento independiente revocación/regrant entre instancias y puntero**, luego G2 montaje seguro y G4 federación auténtica, sin dispersión.
- Continuación automática: frase `Continuar FARO V5`, leer puntero canónico y este checkpoint; comprobar escritor/cola/CI; no repetir PASS por SHA; mantener termómetro cualitativo.
