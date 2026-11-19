# FARO V5 — S4 W03-D2A Source-to-Auxiliary Local Notice · B7 PASS scoped DEV
2026-11-19. Unit S4_W03_D2A_SOURCE_TO_AUX_LOCAL_NOTICE. **PASS_SCOPED_W03_D2A_DEV_JVM_9_OF_9**. NO W03-D total B7, NO W03 global B7, NO Android shipping/G15, NO OS delivery.

## B1
Leídos AGENTS, pointer, permit, protocolos, handoff. W03-D1 B7 PASS 9/9 code 0e4333a25d74c1bb59863f1835683f4b0b687256, guard44567198187 SUCCESS y no reimplementado. Preauditoría específica D2A docs/v5/FARO_S4_W03_D2A_SOURCE_TO_AUX_LOCAL_NOTICE_B1_B5_20261119.md blob f14cbd2e0f9c6ec8cfbee75a7017f8d53bbd2c83, commit21e4b77d53719fdbd20a99b2856fc0889598ffe7, guard44574650567 SUCCESS. Permiso exact 5 rutas READY commit80858a357024ca2e517f686e9d248bb1202577d0, guard44574932752 SUCCESS. Writer epoch1313 RELEASED antes de B6.

## B2
Mantiene Reality Reminder como Truth (Review/Apply+receipt), Auxiliary DeliveryStore v3 como dueño exclusivo del Notice y DeliveryIntent, y W03-D1 S4W03ReminderLocalNoticeProjection como fuente. Usa ACTIVE Finance9 graph real y shared MaterialWriteGate. Deduplica por noticeKey+occurrenceRef e IDs SHA256 semánticos. No se toca historial Matter/Reminder al producir o resolver Notice. View Home reutiliza Auxiliary OPEN/no-dismissed existente. En V4.4 se reconoció Aviso como señal distinta del estado de Asunto. Ninguna nueva base, migración o operación en catalog180.

## B3
Patrón offline-first Android https://developer.android.com/topic/architecture/data-layer/offline-first y SQLite BEGIN IMMEDIATE https://www.sqlite.org/lang_transaction.html adaptados; NOTIFICATION_PERMISSION Android https://developer.android.com/develop/ui/views/notifications/notification-permission respetada: ninguna notificación del sistema en D2A. No licencia GPL/AGPL importada, Internet ni API pagas. Se descartó route() normal por state PLANNED; alternativa acotada es routeReminderLocalOnly con SUPPRESSED_POLICY probado.

## B4
Commit funcional exacto **ecc21ba2d40672660420515fed56728edaa5a2b2**, mensaje FARO-UNIT:S4_W03_D2A_SOURCE_TO_AUX_LOCAL_NOTICE, diff solo 5 rutas autorizadas: AuxiliaryDeliveryStore.kt; S4W03AuxiliaryReminderLocalNoticeTest.kt; S4W03ReminderNoticeReconcileHost.kt; S4W03ReminderNoticeReconcileHostTest.kt; workflow faro-v5-s4-w03-d2a-reminder-notice.yml. Local route hace transaction serializada, admite callback de policy/epoch antes y justo antes COMMIT, conserva notice identity y estado SUPPRESSED_POLICY, rechaza adoptar rutas anteriores PLANNED; local resolve mantiene state suppressed y desired_action CANCEL, preserva atención/history. Host DEV explícito aplica permisos separados reminder.read, notice.route, matter.read por vinculados, reread source antes/después de cada commit, reporta Denied/Partial si cambia grant/fuente. No está montado en shipping Activity/Chat/OS y no reclama ACID entre Reality/Auxiliary.

## B5
CI dirigido run **44576678840 SUCCESS**, job **135745916113 SUCCESS**, SHA ecc21ba2d40672660420515fed56728edaa5a2b2. Logs: FARO_PROTOCOL_PREFLIGHT_PASS unit W03-D2A HEAD parent80858a357024ca2e517f686e9d248bb1202577d0; :store:auxiliary:test y :app:testDebugUnitTest dirigidos BUILD SUCCESSFUL; verificador JUnit XML confirma:
- S4W03AuxiliaryReminderLocalNoticeTest: **5/5 PASS**: file reopen/OS NotEligible, replay/stable IDs, denied+fault rollback, source resolve sin OS, old PLANNED not hijacked.
- S4W03ReminderNoticeReconcileHostTest: **4/4 PASS**: real Reminder source→Aux local + replay, revocations foreground/policy/epoch fail-closed, linked Matter independent grant partial→allow, ACK resuelve sin mutar Reminder.
- TOTAL **9/9**, failures=0, errors=0, skipped=0, marker W03_D2A_AUX_LOCAL_NOTICE_XML_PASS 9/9.
XML artifact w03-d2a-aux-local-notice-xml ID **14556702721**, sha256:13d284ad25fb87faafa73f143ac695778093f6e3ddacca7d14ef26fc964b5a20, not expired. Protocolo guard 44576678898 **SUCCESS** same SHA. Sin errores, no RED_FIX.

## B6
B6 funcional de cinco rutas completado/certificado en ejecución arriba. Resulta Notice local durable desacoplado. No se introduce ruta app productiva de aviso automático ni notificación OS; ruta local explícita trusted DEV sólo.

## B7
**PASS_SCOPED_W03_D2A_DEV_JVM_9_OF_9**. Consumir permiso READY a BLOCKED para esta unidad. Próximo W03-D2B: NoticeAttention SEEN/DISMISSED, policy-grant independiente Auxiliary, revision CAS, recibos/restart/UNKNOWN, mutación sólo Notice (no Reminder/Matter). Requiere B1–B5 propio, guard documental y permiso READY exacto antes B6. W03-E UI Android/G final, OS F opcional y G15 física siguen NOT_RUN. No reclamar protección cross-process ni 2PC. Writer final debe estar RELEASED epoch 1315 y guard del commit documental SUCCESS, ambos verificados externamente antes de cierre.
