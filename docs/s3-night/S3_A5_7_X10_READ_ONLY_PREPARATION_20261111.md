# FARO V5 — X10 Apply/receipts · Preparación de dependencia READ_ONLY
Fecha: 2026-11-11. Base `c5e5e5bad2c50c2c6c0797442a1f09c89444391f`. **NO B1–B5 completos, NO permiso READY y NO código**. Debe comenzar solo tras cerrar X09 B7 y publicar nuevo checkpoint; no consumir permiso X09.

## Qué se reutiliza sin repetir
- `FaroV5ActiveReviewedApplySession.kt`: ejecución tras solicitud humana y chequeo de host/generación/Reality/receipts.
- `FaroV5QuarantinedReviewedApplyDeliveryPort.kt`: efecto auxiliar y reconciliación bajo authority; no exponer notificación Android ni efectos externos por defecto.
- `ReviewedConversationApplyCoordinator`: idempotencia/plan/receipt; Reality y Workspace no se sustituyen.
- `FaroV5ActiveChatScreen.kt`: ya acepta `applyRequestPort` opcional y distingue Committed/Denied/RecoveryRequired; X09 lo deja explícitamente null hasta X10.
- `FaroV5PlanReview.kt`: UI diferida para PENDING→APPROVED→APPLYING/UNKNOWN→COMMITTED, nunca automático en X09.
- Tests `FaroV5ChatToReviewedApplyFlowT2Test`, `WorkspacePlanReviewT2Test`, `FaroV5F1QuarantinedComposeInstrumentationTest`: PASS históricos por alcance, no equivalen al host X10 nuevo.

## Pendientes B1–B5 para un paquete nuevo
1. Leer HEAD post-X09, contratos y rutas reales de Apply, Reality8 y recovery. Elegir si X10 necesita otro host staged o puede admitir Apply del U02 debug **tras X09**, sin violar que V2 productiva está OFF. Nuevos permisos antes de modificar Activity U02.
2. Comparar V4.4 solo como UX/atomic-receipt referencia; conservar `AtomicApply`, PREPARED/COMMITTED, hashes, WAL FULL, receipts y recovery.
3. B3: consultar fuentes Android lifecycle y SQLite WAL; usar inyección de fallos/reinicio sin efectos duplicados, sin paid APIs.
4. B4: listar rutas EXACTAS (test+workflow incluidos), pruebas positivas y adversariales de Apply una vez y UNKNOWN no repetir. Nunca sustituir recibo real por estado UI; nunca fabricar snapshot de Reality.
5. B5: adversarial de doble toque, stale fingerprint/revision/generation, controller offline/UNKNOWN, crash entre PREPARED y COMMITTED, resultado unknown y recuperación. Debe negar Apply si no hay consentimiento específico sobre el candidato aprobado.
6. Publicar documento B1–B5 y paquete; guard; permiso individual READY en commit documental separado, writer CAS; ejecutar B6/CI/APK/XML/B7; después actualizar checkpoint y pasar X01 si su prep está lista.

**Bloqueo intencional:** No autoriza traer `FaroV5ActiveReviewedApplySession` a U02 ni probar efectos físicos. La preparación profunda de X10 se puede avanzar READ_ONLY mientras corre CI X09.
