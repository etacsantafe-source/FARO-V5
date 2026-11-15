# FARO V5 — S4 W00-D2 · Preauditoría de lectura canónica de Review compuesta (B1–B5)
Fecha: 2026-11-15 AR. Baseline HEAD `b4b95fcf1e096c1ae9700c3febcfbc8242e5d260`. Unidad `S4_W00_D2_CANONICAL_COMPOSITE_REVIEW_READ_PREAUDIT`.
Alcance DOC_ONLY, writer global CAS epoch928; permiso funcional BLOCKED, sin cambios Kotlin/SQL/CI. La autorización humana a W00-D NO extiende automáticamente permisos W00-D1, Household ni Finance+Household.

## B1 — Evidencia de contrato, SHA y brecha
- AGENTS.md y protocolo B1–B7 verificados; W00-D1 cerrado B7 scoped JVM11/11, SHA `caf4d2c0bb187e93f667b5a7a7f4eabd575ecc79`, CI `43771193795`, guard código `43771193752`, checkpoint `b4b95fcf1e096c1ae9700c3febcfbc8242e5d260`, guard post-release `43771806206` SUCCESS. Writer RELEASED epoch927, permiso W00-D1 consumido/BLOCKED.
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W00D1AdmissionShape.kt` blob `912ea3d7bfc77fe554484b9ff7f85840d7111887`: valida coincidencia de **claims suministrados**, pero NO descubre la autoridad canónica ni comprueba Review/Workspace. Resultado true no es grant.
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5Planning8ApprovedObjectGate.kt` blob `2bd779e96cfafc897bf96379bc8d55b5f33169d4`: guard de Review humano APPROVED y contrato oficial SOLO de un nodo; no tocar su `nodes.size==1`.
- `v5/kernel/contracts/src/main/kotlin/ar/com/faro/v5/kernel/contracts/ConversationReviewPort.kt` blob `732a14b7d1c56ebedffc46e7e92c460e1fec99d7`: `TrustedConversationApplyReviewPort.load(context)` devuelve `TrustedApprovedConversationPlan` con bytes copiados, SIN autoridad de Apply.
- `v5/kernel/contracts/src/main/kotlin/ar/com/faro/v5/kernel/contracts/ConversationPlanReview.kt` blob `f68bcc0dee9610ff73dd49079319becf199c229d`: `ConversationPlanReviewContext` incluye conversationId, reviewId, authorityToken, expectedRevision y fingerprint; `APPROVED` es precondición para BeginApply posterior, no acto comercial.
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W00CCompositeHostDenialTest.kt` blob `a22fdf6803ddc52fde02c3bad7803efe6b157026`: fixture REAL Workspace4 + Reality8, aprobación humana sintética y host que DENIEGA plan Reminder+MonthlyPlan de dos owners. Reusar su fixture sin volver a ejecutar W00-C.
- Futuras rutas de diseño comprobadas AUSENTES en HEAD: `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W00D2CompositeApprovedReviewScope.kt`, `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W00D2CompositeApprovedReviewScopeTest.kt`, `.github/workflows/faro-v5-s4-w00-d2-composite-review-read-directed.yml`. Son CANDIDATAS, no permiso READY.
- `FaroV5Planning8ReviewedApplyHost.kt` mantiene host único y dormant; nunca dar acceso productivo dual con este paquete.

## B2 — Comparación V4.4, reutilización y fronteras
V4.4 G0D3 reveló peligro de dos commits para un pago; V5 mantiene immutable ApplyPlan/planFingerprint, Workspace revisión sellada, generational fencing, Reality single writer. Reutilizar `PreparedPlanEnvelopeCodec.decode` y `TrustedConversationApplyReviewPort` sin importar runtime antiguo ni compartir grants. `Reminder` y `MonthlyPlan` solo son prueba de dos owners **existentes**, no Household/Finance.
La secuencia segura de un futuro host separa: (i) recuperar Review canónico y extraer scopes sin modificar datos; (ii) comprobar permisos reales de cada owner bajo lease; (iii) BeginApply por CAS; (iv) transacción atómica y recibo. Esta unidad únicamente propone (i).

## B3 — Investigación de alternativas y decisión
A / **CANDIDATO:** servicio interno READ_ONLY que verifica Review exactamente `APPROVED`, contexto/fingerprint/envelope canónicos, generación, hostReady y contrato `OperationContractLookupPort` para CADA nodo, descifra dos payloads tipo `Reminder.CREATE` y `MonthlyPlan.ITEM_ADD` con límites, retorna las dos `S4W00D1ScopeRequirement` tipadas. Valida revisión y contrato de NUEVO tras lectura para evitar preview stale.
B / RECHAZADA: reutilizar `currentApprovedScopeForTrustedRequestOrNull` alterando `nodes.size==1` en guard legado; rompe certificaciones single-owner.
C / RECHAZADA: entregar `ExecutionReceipt`, grant/ticket o BEGIN_APPLY durante lectura; sería elevación.
D / REFERENCIA: leer contratos del `Kernel`, no inventar owner `Household` ni usar token de Search. Documentación oficial SQLite https://www.sqlite.org/lang_transaction.html, https://www.sqlite.org/isolation.html y AndroidX https://developer.android.com/reference/androidx/sqlite/db/SupportSQLiteDatabase apoyan separación de lectura y mutación; no nuevas dependencias ni código externo.
B3 futuro deberá revalidar blobs del SHA anterior a permiso y justificar cualquier desvío.

## B4 — Contrato propuesto, dependencias y test negativos
**Futura unidad funcional candidata:** `S4_W00_D2_CANONICAL_COMPOSITE_REVIEW_SCOPE_READ` con tres rutas NUEVAS exactas arriba; requiere su propio permiso READY previo y SHA/guard. Output un par de requisitos estructurales de `S4W00D1ScopeRequirement` sin grant ni acción. La función no debe exponer `ConversationApplyRequestPort`.
Admitir solo dos nodos con operations EXACTAMENTE `ReminderOperations.CREATE` (capability/executor oficiales, linkedMatterId null) y `MonthlyPlanOperations.ITEM_ADD` (PLANNED, paymentConfirmedAt null), un owner cada uno. Los dos sentidos topológicos deberán producir scopes equivalentes pero ordenados según DAG. No permitir 3 nodos, owner duplicado, unknown op, fila de catálogo fake, payload no decodificable, SOURCE stale, plan fingerprint distinto ni generación cambiada.
Propuestas de nuevos tests dirigidos (aún **NOT_RUN**):
- D2-T01: APPROVED canonical Workspace dos owners→2 scopes de operaciones e IDs correctos, SIN Apply.
- D2-T02: PENDING o DISCARDED→NULL; sin autoridad por pantalla.
- D2-T03: catálogo cambia/revoca un descriptor durante lectura→NULL.
- D2-T04: pointer/host epoch cambian durante lectura→NULL.
- D2-T05: plan 1 o 3 nodos / payload o owner inválido→NULL.
- D2-T06: review o envelope mutado entre primera y segunda lectura→NULL.
- D2-T07: invertir orden topológico de owners→correspondencia por nodeId sin relabel.
Se exigiría CI JVM :app:testDebugUnitTest con XML exacto y workflow nuevo de SHA, no APK/G15 hasta campaña integral.

## B5 — Auditoría adversarial y límites
- Aun con T01 verde, un retorno de scopes no autentica quién aprobó ni puede emitir policyEpoch/leaseNonce/receipt. El caller puede usar un `OperationContractLookupPort` incorrecto; futuro trusted host debe aportar catálogo oficial de verdad.
- Lectura de Workspace prohibida bajo mutex `Reality.run` por orden de locks. No crear PREPARED ni ejecutar bindings/Finance; denegación existente W00-C sigue PASS y no se reabre.
- Revocación externa entre lectura y COMMIT, ABA/generación, rollback y recuperación real quedan sin verificar: W00-D2 no reemplaza W00-D1 ni W06 Household+Finance. Household sigue diseño-only.
- Writer único, permiso `BLOCKED` de D1 consumido, no permitir compilar ni codificar con este documento. No tocar V2 productiva, S2/S3, main, Auto/Noche, API paga, credenciales, pruebas Moto G15.
- Si el futuro diseño requiere cambiar host single-node o grant owner existente, pedir alcance independiente y auditar regresiones antes de READY.

## B6 — Publicación documental
Se publicarán este B1–B5, auditoría B7 DOC_ONLY y pointer exclusivamente, sin modificar permiso funcional ni código. Writer CAS epoch928 y guard de checkpoint al finalizar.
## B7 — Puerta de cierre
`PASS_DOCUMENTARY_ONLY` solo cuando verificados tres cambios DOC, permiso sigue BLOCKED, writer RELEASED epoch928 last_checkpoint=HEAD y guard siete-bloques del nuevo SHA SUCCESS. Ninguna prueba D2 ejecutada, ningún owner nuevo, W00 global OPEN.
