# FARO V5 — S4 W03 · Asuntos, Recordatorios y Fechas — preauditoría B1–B5
Fecha 2026-11-19 · Rama work/s3-night-20261102 · HEAD inspeccionado bfc7b1ec967d0465fcdd95617516300e84f96ad4.
**Estado: DOCUMENTAL / CODE BLOCKED.** Es la preparación por unidades para Work; NO reemplaza preflight y guardia por SHA exacto, NO constituye READY ni acredita ninguna prueba W03 ejecutada. W01/W02 B7 GLOBAL PASS DEV, no repetir.

## B1 — Evidencias verificadas
- AGENTS.md, docs/v5/FARO_CONTINUITY_POINTER.json, docs/v5/FARO_PROTOCOL_WORK_PERMIT.json y FARO_V2_PROTOCOL_RESTITUTION_AND_RETRO_AUDIT_20261104.md leídos. Permiso funcional BLOCKED consumido W02.
- Fuente S4: P08 W03, P05 A01–A03/PF15–PF17, P07 G03/G04/G08/P7T17, P02 L08, P03 R08, P01 V06/V07/V17/V26, P06 UX/drawer. W00 factibilidad y W02 Android ya certificados en su alcance.
- MatterModel.kt `v5/owner/matters/src/main/kotlin/ar/com/faro/v5/owner/matters/api/MatterModel.kt` blob c3470f8a051c91193f33652f617f1fe051d85719: OPEN/IN_PROGRESS/WAITING/RESOLVED/ARCHIVED, title/detail, dueAtEpochMillis opcional, updatedAt, snapshot revision; **no prioridad ni fecha review, historial, editor o permiso**.
- MatterPersistencePort.kt `v5/owner/matters/src/main/kotlin/ar/com/faro/v5/owner/matters/spi/MatterPersistencePort.kt` blob 3edd39208b7e6b7328294526f77e3e336d39ade3: read/upsert transaccional sin sesión humana.
- RealityStore.kt `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt` blob c1d6e45ac3619f98e10582e4bb2b1cc38ef0ad2d: matter_state + matter_record y revisionado, transacción única; reminder_rule/occurrence en Planning8, Finance9 DEV coexistente; migración/backup nuevo aún no probados. NO crear segunda DB.
- ReminderModel.kt `v5/owner/reminders/src/main/kotlin/ar/com/faro/v5/owner/reminders/api/ReminderModel.kt` blob 4121862c038b8e3a4e333ba6880a2a50dc8c6436: Rule ONCE/DAILY/WEEKLY/MONTHLY, zoneId, fecha y hora civil, occurrence y ACK; NO scheduler global validado.
- ReminderCreateBinding.kt `v5/owner/reminders/src/main/kotlin/ar/com/faro/v5/owner/reminders/api/ReminderCreateBinding.kt` blob a4175db06d09e3b059ad8bd4157432b72bc60fd7: binding tipado y owner CAS, **rechaza linkedMatterId cuando no hay ACL Matter**, no está montado como flujo UI.
- ReminderPersistencePort.kt `v5/owner/reminders/src/main/kotlin/ar/com/faro/v5/owner/reminders/spi/ReminderPersistencePort.kt` blob 1de3578eb74510af98cf898052d551bea1e54bdd: createRule y cancelRule internos; no asumir update/attend en host.
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5RealityMatterDueReadSource.kt` blob 05404e44cc24cece2b37fb5537ca80c5653779f4: read de fechas Matter 7 días, ACL/pointer pre/post, cobertura solo MATTERS_DUE; no priorizar por prioridad inexistente.
- `v5/delivery/src/main/kotlin/ar/com/faro/v5/delivery/api/NotificationDeliveryScheduledWorkHandler.kt` blob 63df4c71bf98c55bb705fd6f9f4fd2e5c3a11a79: Delivery independiente PLANNED/UNKNOWN/terminal, no evidencia de permiso OS ni entrega al dispositivo.
- Catálogo oficial `v5/spec/generated/contract-baseline.tsv`: 180 descriptores, operaciones existentes matter.create/update/lifecycle.set/snooze, reminder.create/update/cancel/attend/read, notice.read/dismiss/seen.set, notification.delivery.dispatch. **No crear OperationIds nuevos por comodidad**.

## B2 — V4.4 contrastada
Fuente histórica `etacsantafe-source/FARO@bfa0ee71f5b9cd104baaadd71f1e77a61f203939`, NO MODIFICAR. V4.4 `app-v4/src/main/kotlin/ar/com/faro/v4/followup/FaroV4FollowUpSurface.kt` blob e14db5616c18130d0d6485e4835a6cf53d34d3f5: Asuntos, prioridad/fecha, filtros, botones y explicación "recordatorios siguen guardados" sin permiso POST_NOTIFICATIONS. `app-v4/src/test/kotlin/ar/com/faro/v4/followup/FaroV4FollowUpReversalContractTest.kt` blob 6023c09ddafab3657034c39f81bb540757e256f8: políticas de reversibilidad, revision Matter, occurrence fingerprint/zone, compensación de creación Reminder. **Adaptar semántica, no copiar runtime/DB/UI V4.4**.
Traducción UX inicial sin nuevo enum implícito: Esperar=WAITING, Resolver=RESOLVED, Cerrar=ARCHIVED, Reabrir=OPEN previa regla legítima. Editar prioridad/fecha y razón es acción revisada; leer/caducar/descartar Aviso no cambia estado Matter.

## B3 — Investigación externa y decisiones por unidad
1. Tasks.org, https://tasks.org/docs/ y https://github.com/tasks/tasks ; GPL-3.0. **REFERENCIA** para tareas, recurrencia, recordatorios Android y filtros; descartar copia, sync forzada y dependencia nube. Prueba negativa: ACK de Reminder no resuelve Matter.
2. Vikunja, https://vikunja.io/help/dates-and-reminders/ y https://github.com/go-vikunja/vikunja ; AGPL-3.0. **REFERENCIA** para fechas, recordatorios relativos y repetición; **DESCARTAR** backend y reapertura automática del asunto tras evento recurrente. Prueba: ocurrencia ACK no reabre ni archiva Matter.
3. Android offline-first, https://developer.android.com/topic/architecture/data-layer/offline-first ; **ADAPTAR** Reality como source of truth; **DESCARTAR** servidor, sync, APIs pagas, nuevo store.
4. Android runtime POST_NOTIFICATIONS, https://developer.android.com/develop/ui/compose/notifications/notification-permission ; **ADAPTAR** solicitud solo en momento apropiado, con estado denegado que conserva regla y Aviso local.
5. Android alarms, https://developer.android.com/develop/background-work/services/alarms y https://developer.android.com/about/versions/14/changes/schedule-exact-alarms ; **DESCARTAR** SCHEDULE_EXACT_ALARM por defecto, no prometer puntualidad exacta ni atribuir permiso OS.
6. Java ZonedDateTime, https://docs.oracle.com/en/java/javase/26/docs/api/java.base/java/time/ZonedDateTime.html ; **ADAPTAR** semántica explícita gap/overlap, end-of-month; sin doble occurrence en cambio horario.
Alternativas A: persistencia tipada por owners + review/Apply/receipt con Delivery desacoplado (**ELEGIR**); B: upsert directo desde UI o cron que muta Matter (**RECHAZAR**); C: copiar V4.4/Tasks/Vikunja o servidor (**RECHAZAR**). No importar código ni licencias; Work verificará URLs/licencias por unidad y SHA nuevo, si cambian.

## B4/B5 — Arquitectura, rutas candidatas y pruebas adversariales por unidad
**IMPORTANTE:** las rutas marcadas NUEVA son propuestas de archivo aún no existente, NO autorizaciones. Para READY Work debe verificar rutas y hashes de archivos existentes, cambios entre HEADs, B3 específico, auditoría B5, guardia documental y preflight POR CADA RUTA. La infraestructura tiene un único writer y una única Reality.

### A · S4_W03_A_MATTER_DETAILS_PERSISTENCE
B1: actual Matter carece de priority/reviewOn/history; SQL matter_record persiste solo los 6 campos conocidos. B2: V4.4 L08 prioridades/reversiones y W02 ObjectiveDetails como patrón de historial. B3: evento append-only local frente a sobrescribir la última versión (elegir primero). B4: modelo priority LOW/NORMAL/HIGH, fecha civil de revisión opcional y eventos con reason/versión/idempotencia, sin fabricar pasado para filas legacy. Revisar transiciones RESOLVED/ARCHIVED y FBS2v4/Finance9. Rutas candidatas: 
`v5/owner/matters/src/main/kotlin/ar/com/faro/v5/owner/matters/api/MatterModel.kt`;
`v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`;
`v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityMatterDetails.kt` (NUEVA);
`v5/owner/matters/src/test/kotlin/ar/com/faro/v5/owner/matters/MatterModelTest.kt`;
`v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/S4W03MatterDetailsTest.kt` (NUEVA).
B5 T-A01 viejo Matter sin historial ficticio; T-A02 migración idempotente; T-A03 stale revision/timestamp; T-A04 backup/restore + generation; T-A05 append-only; T-A06 segunda operación falla => rollback. Si se necesita modificar otro archivo de backup, STOP y ampliar permiso exacto antes de código. B6/B7: JVM/XML/guard y regresiones schema7/8/9, FBS2v4 y W01; NOT_RUN.

### B · S4_W03_B_MATTER_REVIEW_APPLY
B1: OperationIds Matter existentes, no host humano; B2: W02 sesión Review/Apply, V4.4 reversión; B3: ApplyPlan unificado (**ADAPTAR**) vs upsert UI (**DESCARTAR**). B4: intención de alta/edición/Esperar/Resolver/Cerrar/Reabrir/snooze => Review inmutable -> ACL Matters -> Apply + receipt y append-only. No modificar otro owner. Rutas propuestas:
`v5/owner/matters/src/main/kotlin/ar/com/faro/v5/owner/matters/api/MatterOperations.kt` (NUEVA);
`v5/owner/matters/src/main/kotlin/ar/com/faro/v5/owner/matters/api/MatterReviewBinding.kt` (NUEVA);
`v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W03MatterReviewHost.kt` (NUEVA);
`v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W03MatterReviewHostTest.kt` (NUEVA).
B5 T-B01 Discard=0 mutación; T-B02 double tap un receipt; T-B03 dos cierres concurrentes gana CAS; T-B04 ACL/generation/foreground revocado; T-B05 UNKNOWN reconciliar receipt antes del retry; T-B06 Undo por reversibilidad; T-B07 reabrir mantiene historial. B6/B7: pruebas JVM dirigidas, no certificar Android; NOT_RUN.

### C · S4_W03_C_REMINDER_CIVIL_DUE
B1: ReminderModel + tablas y canon codec ya existen; B2: V4.4 fingerprint con zone; B3: Java Time/Tasks.org recurrencia sin cron, con política DST documentada. B4: motor puro ONCE/DAILY/WEEKLY/MONTHLY; fecha/hora civil+zone, end-of-month y gap/overlap; occurrence ID estable, cálculo local read-only; regla no es materialización de efecto. Rutas:
`v5/owner/reminders/src/main/kotlin/ar/com/faro/v5/owner/reminders/api/ReminderModel.kt`;
`v5/owner/reminders/src/main/kotlin/ar/com/faro/v5/owner/reminders/api/ReminderCivilExpansion.kt` (NUEVA);
`v5/owner/reminders/src/test/kotlin/ar/com/faro/v5/owner/reminders/api/S4W03ReminderCivilExpansionTest.kt` (NUEVA).
B5 T-C01 Feb 28/29; T-C02 31→mes corto; T-C03 DST gap; T-C04 overlap sin doble evento; T-C05 cambio zona sin reescribir pasado; T-C06 solo consulta no inserta; T-C07 bound/paginación. B6/B7 JVM+guard, NOT_RUN.

### D · S4_W03_D_REMINDER_REVIEW_AND_LOCAL_NOTICE
B1: `ReminderCreateBinding` existente, link Matter explícitamente rechazado sin ACL; reminder.update/cancel/attend ya en catálogo; Avisos Auxiliary son otro owner. B2: V4.4 cancelar recordatorio creado es compensating, notification ≠ ACK. B3: revisión humana tipada vs automatizar estado (**ELEGIR** revisión). B4: create/update/cancel regla y ACK occurrence por Review+Apply+receipt bajo grants Reminder y, para vínculo, Matter independiente; Aviso local/dismiss no modifica regla o estado del Matter. Rutas candidatas:
`v5/owner/reminders/src/main/kotlin/ar/com/faro/v5/owner/reminders/api/ReminderCreateBinding.kt`;
`v5/owner/reminders/src/main/kotlin/ar/com/faro/v5/owner/reminders/api/S4W03ReminderMutationBinding.kt` (NUEVA);
`v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`;
`v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W03ReminderReviewHost.kt` (NUEVA);
`v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W03ReminderReviewHostTest.kt` (NUEVA).
Si Auxiliary necesita delta, comprobar ruta/owner y ampliar permiso previo. B5 T-D01 link sin grant no filtra; T-D02 dismiss Aviso sin mutar Matter; T-D03 cancel no borra historia; T-D04 ACK exacto; T-D05 restore ABA; T-D06 UNKNOWN dedupe; T-D07 OS permission OFF no borra origen. B6/B7 JVM, SQLite, avisos dirigidos, NOT_RUN.

### E · S4_W03_E_MATTERS_ANDROID_DEV
B1: shell V5 tiene destino ASUNTOS, W02 Android DEV es precedente; no editor W03 certificado. B2: panel V4.4, sin copiar runtime; B3: patrón offline-first y accesibilidad Android; B4: UI lista/detalle/filtros alta/editar/ciclo de vida/recordatorio + Chat, Review/receipt, Reactivity tras reabrir, fondo invalida grant; 200%, TalkBack, offline, sin saldo ficticio. Rutas candidatas:
`v5/app/build.gradle.kts`;
`v5/app/src/w03MattersDev/AndroidManifest.xml` (NUEVA);
`v5/app/src/w03MattersDev/kotlin/ar/com/faro/v5/app/S4W03MattersDevActivity.kt` (NUEVA);
`v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/S4W03MatterSessionActions.kt` (NUEVA);
`v5/app/src/w03AndroidTest/kotlin/ar/com/faro/v5/app/S4W03MattersAndroidTest.kt` (NUEVA);
`.github/workflows/faro-v5-s4-w03-matters-android-api35.yml` (NUEVA).
B5 T-E01 Chat create/Discard/Apply; T-E02 reopen persistent; T-E03 background cancel grant; T-E04 filtro read-only; T-E05 font200/TalkBack; T-E06 red OFF/POST_NOTIFICATIONS denied; T-E07 generation change; T-E08 lectura parcial no cero. B6/B7 API35 XML, APK DEV aislada, SHA, guard, G15 NOT_RUN.

### F · S4_W03_F_OPTIONAL_OS_DELIVERY (separada, no bloquea core local)
B1: Delivery model+scheduled handler existe, OS final no probado; B2: V4.4 explica permission denial; B3: Android runtime + exact alarm restrictions, WorkManager idempotente; B4: opt-in explícito OS; POSTED_TO_OS ≠ leído; sin permission OS, Reminder/Notice intactos; sin SCHEDULE_EXACT_ALARM por defecto. Las rutas F requieren INVENTARIO ADICIONAL de v5/platform/android y manifests antes de proponer READY; no inventar binding. B5 T-F01 denied; T-F02 revoked; T-F03 replay duplicate; T-F04 crash recovery; T-F05 user killed app; T-F06 zone changes; T-F07 sin exact alarm no prometer exactitud. F se puede diferir como OPTIONAL_NOT_RUN, nunca PASS supuesto.

### G · S4_W03_G_GLOBAL_B7_ANDROID_DEV
B1/B2: reunir B7 A–E y F si se ejecutó, W00/W01/W02 intactos. B3: verificar artifacts oficiales GitHub Actions y Android API35/manifest/sha. B4: matriz A01/A02/A03 → casos → commit → run → JUnit XML → resultado; emitir B7 global solo alcance DEV, no G15. B5: fallos o cobertura pendiente = BLOCKED; F opcional DEFERRED explícito. B6/B7 solo auditoría final documental del código ya certificado, NOT_RUN.

## B5 — invariantes y STOP transversales
- Matter, Reminder, Auxiliary Notice y Delivery OS tienen autoridades diferentes. Consultar fecha, silenciar o desechar aviso o recibir notificación NUNCA cambia Matter ni genera pago, crédito, deuda o asiento Finance.
- Source/grant faltante o revocado => PARTIAL/NO_AUTORIZADO, nunca 0 ni éxito falso; read pre/post ACL+generation. Asociar Matter a Reminder requiere grant independiente.
- Discard/preview cero cambio. No ejecutar segunda mutación con UNKNOWN sin consultar receipt; CAS, fingerprint, actor, SQL transaction, generation y recuperación obligatorios.
- No inventar history legacy; no romper backup/restore FBS2v4, Reality8/Finance9, catálogo 180, Android/Chat actual. Tests diseñados NO ejecutados ni equivalentes a B7.
- Cada unidad requiere B1–B5 vigente propio y permiso READY por rutas exactas en commit SOLO DOC posterior a guard. Luego preflight exacto, B6+CI, B7 y consumir permiso. Si estado writer HELD ajeno, solo READ_ONLY.
