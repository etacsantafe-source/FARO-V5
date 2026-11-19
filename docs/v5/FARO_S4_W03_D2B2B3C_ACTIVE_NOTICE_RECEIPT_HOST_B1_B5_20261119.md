# FARO V5 — W03-D2B2B3C · Host real de Avisos con recibos y reproyección local

Unidad S4_W03_D2B2B3C_ACTIVE_NOTICE_RECEIPT_HOST_DEV. Base funcional 032bbea4e8e9819a158dd142f6b8a84828cd0f54, cutover 44609511296, Android cortes DEV. V2 productiva OFF.

## B1 — Evidencia
El cutover DEV de Auxiliary4 solo migra la copia aislada y reabre el grafo exacto. El backup/restore FBS2v5 lleva notices/recibos y omite intenciones de entrega Clase D. AuxiliaryDeliveryStore.routeReminderLocalOnly, resolveReminderLocalOnly y mutateReminderNoticeAttentionWithReceipt buscan JOIN preexistente con delivery_intent. Tras restore ese JOIN puede faltar; es necesario rehacer únicamente la proyección SUPPRESSED_POLICY del Notice determinístico, preservando atención/revisión. S4W03NoticeAttentionCasHost y el router de Avisos del Session todavía no exponen el ledger de recibos.

## B2 — Reutilización
V4.4 FaroV4FollowUpSurface.kt @ bfa0ee71f5b9cd104baaadd71f1e77a61f203939 separa estado durable de permisos Android. Mantener distinción Reminder/Aviso/Matter. Usar el mismo Auxiliary owner/connection, sus IDs determinísticos, CAS y publishNoticeCommit. Reusar grants separados notice.read y objetivo exacto, AndroidSandboxPrincipal, owner lifecycle y vista de Avisos. No duplicar storage ni UI; conservar la entrada previa de prueba Aux3 sin habilitarla en la ruta AUX4.

## B3 — Investigación
ADAPTAR sandbox UID/paquete (https://source.android.com/docs/security/app-sandbox) y lifecycle RESUMED (https://developer.android.com/reference/androidx/lifecycle/Lifecycle.State) usando los mecanismos existentes; APIs de plataforma/AndroidX y documentación primaria, sin dependencia. Reusar un SQLite BEGIN IMMEDIATE como cerco de reparación/atención y SQLite FK (https://www.sqlite.org/lang_transaction.html y https://www.sqlite.org/pragma.html, dominio público). DESCARTAR scheduler/alarma, acción confiada desde Chat/modelo, restaurar efectos OS o copiar una proyección auxiliar por fuera del owner.

## B4 — Unificación y rutas
Añadir restauración local de proyección únicamente cuando el Notice retenido tiene clave/ref/ID determinísticos y destination exacta. Si está RESOLVED, reconstruir CANCEL suprimido; mantener estado/revisión cuando solo se repone el vínculo. Usar la misma transacción de route, source resolution o click, respetando grants. Nuevo click tipado recibe request key estable, expected revision, Notice ID y objetivo foreground; mantén key/fingerprint historia exacta. Session aislado DEV puede abrir una transición Aux4 solo con aceptación foreground y Finance9 vigente, maneja reopen/rollback en el mismo runtime y ofrece mutación detrás de la autoridad local existente de Avisos. W3-E dibuja UI en unidad posterior.
- v5/store/auxiliary/src/main/kotlin/ar/com/faro/v5/store/auxiliary/AuxiliaryDeliveryStore.kt
- v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W03NoticeAttentionCasHost.kt
- v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthority.kt
- v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W03NoticeAttentionReceiptHostTest.kt
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Auxiliary4RestoredNoticeRouteTest.kt
- .github/workflows/faro-v5-s4-w03-d2b2b3-notice-receipt-host.yml

## B5 — Auditoría previa
Probar recomposición exacta tras restore sin DeliveryIntent y sin duplicación/OS, rechazo de Notice ajeno o ID incorrecto, read/mutation scope y lifecycle revocados, replay mismo key, conflicto de payload, stale ID y ABA. Ningún cambio en Reminder/Matter por click. Validar host Session contra pointer+epoch de lectura foreground y permiso exclusivo por click. Reconciliación suprimida jamás arma servicio/alarma ni cambia permisos Android. Aux4 upgrade solo en root correcto y explícito. CI generacional SQLite, app JVM y compilación del variant DEV, no pruebas físicas. W3-D requiere luego revisar matriz total con evidencias de due, reconciliación, permisos, estados y backup antes de E, Android G.
