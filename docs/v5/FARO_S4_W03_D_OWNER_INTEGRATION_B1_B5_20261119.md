# FARO V5 — W03-D · Integración de los owners reales en una sesión local

Unidad S4_W03_D_INTEGRATED_MATTER_REMINDER_NOTICE_DEV. Base funcional 7ed59b8d770b279fbcecd7c007910e9f37d7bef0. Aval host Avisos 44611463388. W03-E usa esta sesión para su UI.

## B1 — Evidencia
A/B/C/D1 y D2A/D2B1/D2B2A B7 acotados; no repetir. Las extensiones D2B2B verificaron archivo/restore, switch, replay y host de Avisos. S4W03MatterReviewHost, S4W03ReminderReviewHost y S4W03ReminderNoticeReconcileHost tienen contratos ejecutables bajo un graph real pero falta coordinarlos como sesión W3. Reusar Workspace, Reality, Auxiliary, MaterialWriteGate y full pointer ya abiertos. D cierra integración JVM real: Matter/Reminder Review+Apply/receipts, vínculos autorizados, expansión recurrente y Avisos locales, recuperación de correlación y negativa por pausa/puntero.

## B2 — Comparación y reutilización
V4.4 FARO@bfa0ee71f5b9cd104baaadd71f1e77a61f203939 FaroV4FollowUpSurface.kt jerarquiza Asuntos/Recordatorios y conserva data con permisos Android denegados. Adaptar la semántica, sin copiar persistencia. V5 S4W02ObjectiveSessionActions es patrón de sesión prestada, port review/apply, separar acción foreground y revoke. Reusar los tres hosts W03 y no construir segunda DB, scheduler, catálogo ni permisos sintéticos.

## B3 — Investigación
ADAPTAR el Android sandbox UID/paquete y Lifecycle RESUMED ya integrados (https://source.android.com/docs/security/app-sandbox y https://developer.android.com/reference/androidx/lifecycle/Lifecycle.State); documentación de platform/AndroidX, sin incorporación de dependencia. Reusar puertos y coroutine worker de W02 para operaciones SQL fuera de UI. DESCARTAR mutaciones directas de pantalla, lectura autoconcedida por clic de otro owner, polling y trabajo en background.

## B4 — Unificación y rutas
Montar contrato local de sesión prestada solamente en package DEV W03, con probe Android, puntero persistido y la selección Auxiliary4. Lecturas Matter/Reminder se desbloquean desde la vista específica y se revocan al perder foreground. Los enlaces a Matter tienen concesión distinta por objeto. Conversación vigente y prefijo W03 filtran reviews/decisiones. Aprobar/descartar usa Workspace; Apply usa host del owner y reconcilia por recibo cuando el estado durable es APPLYING/UNKNOWN. Reconciliación de Avisos es foreground, explícita, bajo permisos Reminder, Matter enlazado y Auxiliary, sin estado de OS. Integración JVM usa SQLite y contratos reales; la única entrada simulada de fixture es autenticación/lifecycle. Pruebas solo de la integración nueva.
- v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/S4W03OwnerSessionActions.kt
- v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthority.kt
- v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/S4W03OwnerSessionIntegrationTest.kt
- .github/workflows/faro-v5-s4-w03-d-owner-integration.yml

## B5 — Auditoría previa
Probar Matter→Reminder enlazado en Review/Apply con ACL independiente, recurrencia civil, emisión de un Aviso por ocurrencia, idempotencia y revocación/denegación sin mutación durable. Cierre de sesión o ABA invalida todo acceso. No confiar en identificadores de otro chat o prefijo. Nada fuera del owner MaterialWriteGate; guardia y CI XML exacto. V2 productiva OFF, entrega OS OFF, G15 NOT_RUN. E incorpora la UI nativa y las pruebas Android luego de B7 D.
