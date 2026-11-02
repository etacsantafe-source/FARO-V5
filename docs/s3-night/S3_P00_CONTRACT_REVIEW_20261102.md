# FARO V5 S3 — P00 · revisión contractual acotada (NOCHE_B 02/11/2026)

**Estado: FROZEN_SPEC_ONLY / IMPLEMENTATION NOT CERTIFIED / TESTS NOT_RUN.** Documento de diseño dentro de S3, no cambio del catálogo ni certificación de implementación. Los gates H01–H04/R0–R3 quedan congelados como contrato de diseño, no como implementación; X01–X12 y pruebas de integración permanecen NOT_RUN.

## Autoridad y evidencia concreta

- S2 CLOSED 8/8; app V2 OFF; S4 OFF. Rama S3 inicial `7bfb76fe7d48bbd499a6d71099588b4a837fcb0a`.
- Catálogo oficial D-229: **172 OperationContracts**. `docs/v5/V5_OPERATION_CONTRACT_INVENTORY.md` y `docs/v5/V5_CAPABILITY_TAXONOMY.md` leídos en rama S3. N-015 `history.query` y `history.receipt.read` READ_ONLY; N-018 `search.query` y 4 `search.saved_view.*` ya incluidos. SavedViewDefinition != MessageRef/bookmark.
- Benchmark `docs/v5/FARO_S3_BENCHMARK_APPS_20261102.md`: Obsidian/Notion/Keep/Linear/GitHub/Android; investigación documental, NO prueba de apps instaladas. X01–X12 pendientes.
- Comparación V4.4 READ_ONLY registrada por NOCHE_A en cola CLOUD: GlobalSearch dedup y owner-unavailable header; Workspace archivo reversible/limpia pin; Saved Views D-226. Reutilizar semántica, no copiar persistencia ni código.
- Spot-check independiente V5: `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5ShellNavigation.kt` blob `991be956`: CHAT es ROOT de drawer; BUSCAR/AVISOS son SECONDARY, ACTIVIDAD/GUARDADOS ausentes. `FaroV5PlanReview.kt` blob `3c528b23`: `COMMITTED` requiere autoridad Reality y recuperación UNKNOWN no debe re-Apply. `ConversationWorkspaceStore.kt` blob `4323fa9a`: receipts en transacción SQLite; Apply ObserveCommitted exige journal COMMITTED + receipt real.
- El gate N00 A/B se documenta en cola CLOUD; su PASS operacional no significa tests de UI/contratos.

## Contratos S3 que deben conservar autoridad

| Gate | Semántica mínima congelable | Prohibición / regresión obligatoria |
|---|---|---|
| R0 Actividad | Destino explícito de drawer único, vista READ_ONLY de N-015 history.query/history.receipt.read; filtro fecha/tipo/owner; receipt y efecto conocido; sin nuevo store | No inventar evento aplicado, no duplicar autoridad de Avisos; X01–X02 |
| R1 Búsqueda | Hit tipado `{owner,kind,stableRef,matchField,title,authorizedSnippet,asOf,coverage,rank}`; revalidar owner/ACL y revision al abrir; parcial != vacío; orden estable; versión compatible | Sin snippets revocados, sin false `sin resultados` cuando owner/FTS falla; X06–X08,X11 |
| R2 Guardados | `MessageRef(conversationId,messageId)` con identidad compuesta y generación; foco al mensaje exacto, si archivado/oculto/revocado mostrar unavailable seguro; bookmark no duplica mensaje | SavedView de N-018 no equivale a bookmark; no exposición de contenido borrado; X04–X05 |
| R3 Review | Chat genera Candidate; revisión muestra parámetros literales/antes-después; cada owner ejecuta bajo preflight/idempotencia y emite receipt propio; solo Reality puede declarar COMMITTED Reality | Auxiliary saved.add no puede producir Reality COMMITTED; UNKNOWN reconcilia, nunca re-Apply automático; X09–X10 |
| H01 Undo | Auxiliary reversible solo con receipt y regla del owner; Reality Undo sigue por N-016 con su authority/receipt | No reutilizar Undo Reality para borrar bookmark |
| H02 Referencia | ID de conversación + mensaje y revisión/generación, no índice visual ni fragmento textual | Colisiones entre chats, restore y revocación => unavailable; X04–X05 |
| H03 Lifecycle | Favorito/guardar/archivar/borrar/restaurar son estados diferentes; idempotency key estable y receipts auxiliares; eliminar no crea falsa papelera Reality | No resurrect por restore, ni duplicación por retry; X05,X10 |
| H04 Apply/receipt | Auxiliary, Workspace, Evidence, Reality mantienen dominios de autoridad distintos; COMMITTED Reality exige journal+receipt canonical | No falsificar receipt o inferir éxito por UI/Actions verde; X01,X09 |

## Bucle de investigación adversarial — hallazgos accionables

**Ronda A, comparación histórica:** V4.4 tiene búsqueda con aviso de owner no disponible que la UI puede perder; V5 debe modelar cobertura como dato, no texto parseado. V4.4 archivado reversible; V5 ya tiene proyección de archivados, no reimplementar.

**Ronda B, código actual:** Shell V5 aún muestra CHAT como entrada del drawer y carece de Actividad/Guardados; decisión humana S3 exige drawer único sin Chat duplicado. El PlanReview tiene estados Reality; una futura operación Guardados no debe pasar por esa transición.

**Ronda C, ataques:** (1) FTS o owner falla y devuelve vacío: mostrar `PARCIAL`; (2) MessageRef restaurado apunta a otro mensaje: vincular generación/revisión y revalidar; (3) búsqueda antigua revela snippet revocado: no cachear contenido autorizado tras revocación; (4) click en Aviso marca leído o altera Reality: lectura pura; (5) retry de guardado duplica receipts: idempotency key y transacción; (6) estado UNKNOWN genera doble Apply: reconciliación de receipt sin reejecución. Aceptación: tests X01–X12 y pruebas de integración del owner.

## Bounded review de Guardados: **NO APROBADO AÚN**

La propuesta de ocho operaciones de bookmark sobre N-018 (172→180) sigue CANDIDATE. No modificar inventario oficial ni OperationIds hasta revisar nombre, owner, clasificación READ_ONLY/AUXILIARY_MUTATION, persistencia, idempotencia, receipts, Undo, restore, privacidad y tests. Reusar Saved Views solo para consultas guardadas. No afirmar 180 ni PASS por este documento.

## Próximo trabajo sin repetir investigación

1. Validar contratos exactos contra tipos Kotlin reales y esquema persistente; decidir si R2 exige bounded reopen explícito y pruebas antes de escribir A3.
2. Agregar pruebas dirigidas para H01–H04/R0–R3 y X01–X12, empezando por autoridad y referencias; verificar XML por SHA.
3. Solo entonces marcar P00 PASS y habilitar A1; implementación incremental S3, única campaña física final. No ejecutar Actions duplicadas ni abrir S4.

## Verificación adicional de tipos reales (NOCHE_B)

- `ConversationId` es value class de `ConversationWorkspace.kt` blob `bd3834a2`; `ConversationMessageId` es value class de `ConversationTimeline.kt` blob `a3f51cdd`. El mensaje ya porta `conversationId`; la identidad compuesta propuesta NO debe confiar en índice de UI.
- `ConversationTimelineSnapshot` incluye `authorityToken` y `revision`: generación/restauración se valida contra owner actual al abrir, sin persistir contenido del mensaje en un bookmark.
- `ConversationPlanReview.kt` blob `f68bcc0d` exige `ExecutionReceipt(transactionId,planFingerprint)` para COMMITTED; `ExecutionReceipt.kt` blob `30141a4f` es Reality, no recibo auxiliar.
- `SearchProjectionHit.java` blob `f364508a` solo contiene key/sourceStamp/rankingSignal; `SearchProjectionQuery.java` blob `0dde83bf` es una búsqueda local por namespace; la federación N-018 y coverage deben vivir fuera del índice. `AndroidAppSearchProjection.java` blob `b95eab54` es proyección local descartable; NO reemplazarla por SQLite FTS sin pruebas de español/offline/restore.
- `FaroV5ShellNavigationContractTest.kt` blob `46c84810` verifica orden de raíces viejo (incluye CHAT) y Search/Avisos secundarios. A1 deberá actualizar esas pruebas, no silenciarlas. `FaroV5WorkspaceReadOnlyDrawerTest.kt` blob `7bd9f381` conserva 7 recientes, pinned y archivados; no duplicar su implementación.

## Decisión acotada P00

**FROZEN_SPEC_ONLY** para R0–R3 y H01–H04 según tabla anterior, con compatibilidad de los tipos actuales y separación de autoridad. Esto congela el *contrato de diseño*, NO certifica implementación ni tests. Los escenarios X01–X12 siguen NOT_RUN. No se agregan OperationIds; el catálogo permanece 172. El bounded reopen de 8 operaciones Guardados queda **BLOCKED_FOR_SEPARATE_REVIEW** antes de cualquier escritura A3. A1 (navegación/Inicio) puede avanzar sin depender de ese reopen; A2 debe preservar relectura del owner y cobertura; A4 no muta fuentes.
