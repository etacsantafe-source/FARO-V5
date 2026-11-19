# FARO V5 — S4 W03-D2B1 · Notice Attention CAS (preauditoría B1–B5)
Fecha 2026-11-19. Unidad S4_W03_D2B1_NOTICE_ATTENTION_CAS_DEV, HEAD 4f08bd014b7a310109541859ab1241bb7ef9af92. DOC_ONLY hasta READY específico. W03-D2A B7 PASS 9/9 code ecc21ba2d40672660420515fed56728edaa5a2b2, CI44576678840, guard final44578295954, writer1315 RELEASED. No reabrir A/B/C/D1/D2A, W01/W02.

## B1 — Evidencia vigente
AuxiliaryDeliveryStore.kt blob96a5a832128578fd0e91f914331a1f36bdee6f26 ya mantiene delivery_notice.attention = UNSEEN/SEEN/DISMISSED, notice.revision INTEGER>0, relevance OPEN/RESOLVED, SQLite WAL FULL + BEGIN IMMEDIATE y MaterialWriteGate. S4W03 D2A new route local Notice SUPPRESSED_POLICY, no OS. HomeNoticeBindingFactory blob22393a2d4c9e7db1bf6d1a9e549f561637a712e0 filtra OPEN y attention != DISMISSED (SEEN permanece visible). DeliveryModel.kt blob67a51b1e0dd2dd37bb14a45d2ab1859344dcb308 define los estados pero no mutate. No se encontró owner de NoticeAttention ni política por click; el catálogo180 incluye notice.seen.set y notice.dismiss BINDABLE, no IDs nuevos. Exige CAS por revision+estado, mutación única Auxiliary. El sistema tiene schema3 y preflight de active generation exige EXACTAMENTE schema3: FaroV5GenerationStoreGraph.kt blob34bcd78cc417c63a55acaec1065e4dcf566f9e90 lines 389–483 y tests FaroV5AuxiliaryV3PreflightTest.kt blob15e904c035a6e4feb2aa13086528d6d8e54fd904. Por tanto NO se puede agregar tabla de recibos a schema3 silenciosamente.

## B2 — V4.4 y reutilización
V4.4 UX separaba estado del aviso y del recordatorio. D2A mantiene local Notice durable independiente de Reminder/Matter. Reusar Auxiliary store exclusivo y Home count, Planning8TrustedObjectPolicyPort + FaroV5Planning8ObjectGrantOwner para grant de notice.read/id + notice.seen.set/id o notice.dismiss/id, pointer completo/epoch/foreground. El botón Visto sólo actualiza attention UNSEEN->SEEN, permanece OPEN visible; Descartar actualiza attention UNSEEN/SEEN->DISMISSED y Home la omite; NO ACK Reminder, NO cambio Matter, NO OS Delivery. Uso explícito de click DEV, no UI de producción en este alcance.

## B3 — Alternativas y decisión
USAR SQLite transacción, CAS y user_version oficiales https://sqlite.org/lang_transaction.html y https://sqlite.org/pragma.html ; ADAPTAR política offline-first local https://developer.android.com/topic/architecture/data-layer/offline-first . REFERENCIA guías de migraciones Android https://developer.android.com/training/data-storage/room/migrating-db-versions.html (FARO Auxiliary no usa Room para esta DB; no aplicar migración Room automáticamente). DESCARTAR registro nuevo de recibo en bookmark_receipt (dueño equivocado), CREATE TABLE IF NOT EXISTS sobre active schema3 (violación de preflight), nueva BD sidecar, cambios automáticos de esquema3 a4 durante apertura activa, "recibos" basados sólo en memoria/ephemeral, POST_NOTIFICATIONS o acciones source Matter/Reminder.

División segura: D2B1 implementa SOLO CAS duradero state+revision con resultado Saved/Stale/Denied/Terminal; D2B2 posterior implementará ledger persistente idempotente por request-key, versionado schema4 y migración en GENERACIÓN STAGED con backup/restore, preflight active schema4 y regresiones v3/4. B7 D2B1 jamás significa D2B completo, no afirma recibos independientes ni replay por request key.

## B4 — Arquitectura y cinco rutas exactas D2B1
1. v5/store/auxiliary/src/main/kotlin/ar/com/faro/v5/store/auxiliary/AuxiliaryDeliveryStore.kt: nueva S4W03NoticeAttentionCasResult y mutateReminderNoticeAttention(noticeId,expectedRevision,target,admitted). En la misma conexión/lock/MaterialWriteGate, BEGIN IMMEDIATE, check local reminder sourceRef + destination LOCAL, relevance OPEN, action monotónica, revision CAS, check changes()==1, revalidar admitted antes de COMMIT. Saved devuelve NoticeSnapshot durable; Stale/Terminal/Denied y NotFound no mutan. Rechaza OBSERVER writing no grant, Notice de otros dominios y stale revision. Sin schema changes ni receipt-key inventado.
2. v5/store/auxiliary/src/test/kotlin/ar/com/faro/v5/store/auxiliary/S4W03AuxiliaryNoticeAttentionCasTest.kt (NUEVO): 5 pruebas SQLite para visto, descartado, stale/doble tap, revocación rollback, restart + OS suppression y legacy guard.
3. v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W03NoticeAttentionCasHost.kt (NUEVO): host trusted DEV, no mount productivo, ejercicio de click humano foreground; scope owner notice.read/id y operación notice.seen.set/id o notice.dismiss/id por policy independiente; pointer/epoch y shared gate, no exposición de raw store fuera host.
4. v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W03NoticeAttentionCasHostTest.kt (NUEVO): 3 tests con active Finance9 graph/auxiliar y puntero, confirmación/admisión independiente, stale pointer/revocation, aislamiento Matter/Reminder/OS.
5. .github/workflows/faro-v5-s4-w03-d2b1-attention-cas.yml (NUEVO): exact 5-route preflight against READY parent, :store:auxiliary:test dirigido 5 y :app:testDebugUnitTest dirigido 3, JUnit XML 8/8 + artifact, guard protocolo.

Sin cambio v3 de la DB, sin migrar active schema, no adición a catálogo, no internet ni APIs pagas. No modificar W03-D2A ni app Activity. Si la operación se reintenta con revision antigua devuelve Stale, NO un recibo falso de replay.

## B5 — Auditoría previa y criterios de B7
C1 UNSEEN->SEEN aumenta revision sólo una, permanece visible en Home.
C2 UNSEEN/SEEN->DISMISSED aumenta revision sólo una, Home oculta aviso pero source intacto.
C3 intento SEEN desde DISMISSED o RESOLVED => Terminal y no revive; UPDATE idempotente sin nueva revision rechazada.
C4 dos taps stale revision => Stale exacto, no sobrescribe ni reporta Saved sin COMMIT.
C5 grant notice.read sin grant de notice.dismiss o seen => Denied y DB intacta.
C6 foreground/pointer/host epoch caducado => Denied y rollback, sin fuente leída/escrita.
C7 NOTICE de radar/external y notices OS-PLANNED no pueden ser mutados por API restringida a reminder LOCAL; D2A local SUPPRESSED_POLICY sigue sin ofrecer dispatch.
C8 reopen SQLite preserva attention/revision; ninguna tabla nueva ni cambios en user_version=3, FK healthy.
C9 mutación Auxiliary no modifica Reality Reminder/Matter, recibos Apply D2A conservados.
B6 exige guard de preaudit SUCCESS + READY exacto anterior; B7 exigir SHA, guard, 8/8 XML, artifact y writer RELEASED. D2B2 durable idempotency-key receipt permanece NOT_RUN hasta separar corte de esquema y política backup/restore, con permiso propio y pruebas de staged migration; D2B global NO cerrar con este PASS. OS F, UI E, G15 físico NOT_RUN.
