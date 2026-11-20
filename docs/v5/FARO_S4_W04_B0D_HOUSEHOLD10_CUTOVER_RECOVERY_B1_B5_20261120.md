# FARO V5 — W04 B0D · cutover, reopen y recovery DEV Household10 — B1–B5
Fecha local 2026-11-20 (Argentina). Baseline 768c56824d04b051d323988aa2129461d8d16dd6. Unidad lógica W04-B0D, subdividida D1 y D2 para no permitir un permiso monolítico. Ambos PREAUDIT_DOC_ONLY, NO_READY, B6/B7 NOT_RUN. Dependencia W04-B0C3A+B0C3B B7 PASS.

## B1 — Código existente y brecha
- B0B B7 PASS de stage9→10 bajo lease, no activación; el cambio de pointer no está en ese alcance.
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9DevCutoverPreflight.kt blob dd8c7336deb0f7f7fa7f9d22159010a4116ed60c: controla pointer, journal, fence, descriptor, marker, sidecars y cinco stores, pero exact Reality9, NO Household10.
- FaroV5Finance9DevCutoverOwner.kt blob a3febb682991cb98e67359a7d08d5f4118476219: GenerationSwitchCoordinator, rollback, journal y replay fence; checkActive reabre schema8/9, NO schema10.
- FaroV5Finance9DevCutoverRuntime.kt: posee lease de host cerrado, intent y recuperación DEV; modos ACTIVE_NINE/AUX4, no admite H10.
- FaroV5GenerationStoreGraph.kt blob 109a993de231f503380b810f4ee54d7d9701c476 y RealityStore.kt: active Finance9 exige schema9; solo se admite active10 si se agrega un modo NUEVO, controlado, sin mutación automática.
- B0C3B producirá STAGED10 restaurado y verificado. No hay B7 de cutover Household10, ni B7 de graph10 activo o recomposición luego de crash.
- W00 factibilidad (docs/v5/FARO_S4_W00_FINAL_B7_ARCHITECTURE_20261115.md) GO infraestructura, NO_GO ejecución compuesta multi-owner. Household10 active DEV no equivale a transacción Household+Finance, pago real ni autorización de Chat.

## B2 — Reuso y diferencias con V4.4
- V4.4 SmartCompra guardaba datos mediante store real, pero su G0D3 reconoció rutas Apply no atómicas. NO copiar DB/runtime ni sus recovery paths; preservar V5 writer único, token/lease de mantenimiento, pointer monotónico, bootstrap journal/fence, snapshot Reality/Evidence separados y validación ante rollback/replay.
- Reusar clases Finance9 de *proceso* y coordinator genérico solo con variante Household10 aislada. No modificar semánticas de Finance9 ni habilitar producción. V5 stage10 es un formato nuevo, no permiso de escritura UI.

## B3 — Alternativas
A: preflight strict10 independiente + owner dev Household10 que usa GenerationSwitchCoordinator, journal/fence y acceso a graph10 explícito; elección para dos unidades chicas.
B: añadir boolean de schema10 al opener Finance9 y activar sin corroborar backup C3; RECHAZADA por aceptación falsa.
C: cambiar PRAGMA user_version de 10 a 9, copiar archivos al ACTIVE o mover pointer sin journal: RECHAZADA.
D: host compuesto Household+Finance tras habilitar graph10: RECHAZADA; necesita W06 pruebas de atomicidad y grants separados.
Referencias auditadas: Finance9DevCutoverOwner/Runtime/Preflight, StagedSqlitePreflight, ActiveSqlitePreflight, documentos de integridad W00 y operaciones SQLite transaccionales.

## B4 — Unidad D1: preflight + apertura DEV10 sin activación (rutas candidatas)
Unidad S4_W04_B0D1_HOUSEHOLD10_PREFLIGHT_ACTIVE_GRAPH_DEV:
1. v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10DevCutoverPreflight.kt (nuevo).
2. v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt (delta de strict Reality10 + demás stores + opener DEV10).
3. v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt (delta de modo admitted schema10, NO cambio a opener normal).
4. v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household10DevCutoverPreflightTest.kt (nuevo).
5. .github/workflows/faro-v5-s4-w04-b0d1-household10-preflight.yml (nuevo).
Preflight requiere target stage10 canónico no activo, cinco stores válidos con auxiliary4 receipts, esquema10 estricto y Household revision/filas, source generation/pointer expected ID+sequence, journal único en fase VALIDATED, ningún replay fence indebido, ausencia de symlink/hardlink/sidecars, FK/integridad/ACL. No abrir writable ni cambiar pointer. Reapertura DEV10 para verificación sólo tras grant de mantenimiento, nunca host normal ni capa de pagos. Si Graph10 de B0C3B ya implementó un opener stage-only, ADAPTAR sólo el delta active, no duplicar.
D1 es GO/NO_GO técnico, no activación.

## B4 — Unidad D2: cutover, recover y rollback DEV10 (rutas candidatas)
Unidad S4_W04_B0D2_HOUSEHOLD10_ACTIVATE_RECOVERY_DEV:
1. v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10DevCutoverOwner.kt (nuevo, coordinator).
2. v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10DevCutoverRuntime.kt (nuevo, lease durable y reingreso).
3. v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household10DevCutoverRuntimeTest.kt (nuevo).
4. .github/workflows/faro-v5-s4-w04-b0d2-household10-cutover.yml (nuevo).
Solo después D1 B7 y C3B B7: foreground humano en host DEV aislado (o prueba inyectada del consentimiento), root exclusive lease, host cerrado, backup autenticado completo verificado, pointer pinned, journal PREPARED/VALIDATED, archive identity, CAS con switch, health check de los cinco stores+H10, fence RESTORE, recuperación idempotente. Resultado ACTIVE_TEN o ROLLED_BACK o BLOCKED, sin comportamiento opaco. No usar permisos de W1/Finance; no activar writer paralelo.
Si D2 necesita delta en clase existente (p.ej. preflight/graph/host) AGREGAR rutas SOLO después de B1–B5 delta y nuevo READY exacto; no aprovechar permiso D1 ni bajar guardia.

## B5 — Adversarial y pruebas diseñadas, sin PASS anticipado
D1-T01 stage10 y Auxiliary4 auténticos -> preflight admite; D1-T02 schema9, schema7, versiones de companions erróneas, tablas ausentes -> DENIED; D1-T03 puntero ABA/journal ajeno/fence presente -> DENIED; D1-T04 ruta no canónica/symlink/hardlink/sidecars -> DENIED; D1-T05 opener Finance9/productivo rechaza10 mientras dev10 validado sin creación/migración.
D2-T01 activa stage10 con NEED + FINANCE/RECIBOS preservados, pointer secuencia+1 y fence durable; D2-T02 crash antes CAS -> origin retained; D2-T03 crash después switch, antes health -> recupera/rollback correcto; D2-T04 crash después fence -> no segundo Apply ni rollback peligroso; D2-T05 reintento mismo operationId idempotente; D2-T06 dos sesiones/lease competidor bloquea; D2-T07 grant revocado/host abierto/foreground DENIED; D2-T08 corrupción de un store o Household después de switch -> rollback verificado sin pérdida de origin; D2-T09 restore desde archivo diferente bajo mismo intent DENIED; D2-T10 Finance9 activa o V2 productiva queda intacta.
No filtrar datos Household ante ACL denegada. W05 stock/Cocina, W06 transacción financiera compuesta y W07 compra pagada permanecen STOP. Ninguna unidad D habilita UI ni host productivo.
**Criterio B5:** ambos planes documentados y rutas propuestas; D1 NO_READY hasta C3B B7; D2 NO_READY hasta D1 B7. Cada permiso individual via DOC_ONLY/guard/preflight, luego B6 JVM real/XML, auditoría B7 y writer RELEASED. Android/G15 NOT_RUN hasta fase específica autorizada.
