# FARO V5 — S4 W03-G · B7 FINAL PASS (LOCAL DEV)

Fecha 2026-11-19 Argentina / CI 2026-11-20 UTC. Unidad: `S4_W03_G_GLOBAL_B7_ANDROID_DEV`. SHA funcional **`0706f4ba89ce48921adfe885157b135f8dd4296c`**. Final **PASS_SCOPED_W03_LOCAL_DEV_ANDROID_API35**, no shipping ni notificaciones del sistema.

## B1 — Evidencia y base

W03-A Matter persistencia B7 PASS JVM/SQLite 33/33; W03-B Matter Review/Apply B7 PASS 8/8; W03-C Reminder civil 10/10; W03-D integración owners 3/3; W03-D2A 9/9 Auxiliary local y D2B1 8/8 Notice CAS, D2B2 durable receipts/cutover ya B7 PASS. No repetir. W03-E Android DEV build/aislamiento B7 PASS scoped, run `44645128424`. Contrato G en `docs/v5/FARO_S4_W03_G_GLOBAL_ANDROID_B1_B5_20261119.md` (B1–B5 verificados por guardia `44646678896`, permiso G READY exacto bajo guardia `44647020272`). B7 RED_FIX de G para cobertura y regresiones quedó documentado antes de cada commit funcional posterior.

## B2 — Comparación y reutilización

Se reutilizó la sesión real W03-D, UI Chat/menú/owner de W02, no se copió ni reabrió V4.4 ni se activó V2 productiva. Experiencia: Chat → Asuntos → Recordatorios → Avisos. Selección de Matter por **nombre**, bajo gesto explícito, con `grantMatterReadAfterExplicitRequest()` y `matterSnapshot()` del owner; al confirmar, `authorizeMatterReference` y Review/Approve/Apply comprueban ID y política. No segundo grafo ni fake receipt.

## B3 — Alternativas revisadas

Se adaptaron los enfoques oficiales AndroidX Test/ActivityScenario, JUnit XML y Espresso/Compose del proyecto, con SDK 35 en emulador: https://developer.android.com/training/testing/instrumented-tests . JVM propietarios/SQLite de W03-D aportan adversariales CAS/ACL y receipts, **no** se sustituyen pruebas Android por mock/screenshot ni se depende de API paga. Sin permisos OS ni Internet.

## B4 — Programación y pruebas reales

Commit final `0706f4ba89ce48921adfe885157b135f8dd4296c`: exactamente dos rutas aprobadas del permiso G:
- `v5/app/src/w03MattersDev/kotlin/ar/com/faro/v5/app/FaroV5W03MattersPanel.kt`, selector explícito de Matter con lectura autorizada de owner.
- `v5/app/src/w03AndroidTest/kotlin/ar/com/faro/v5/app/S4W03MattersAndroidTest.kt`, aceptación nativa a fuente 200% ampliada.

**Guarda obligatoria** run `44656219767` **SUCCESS** SHA exacto. **Compilación E de regresión** run `44656219755` **SUCCESS** SHA exacto. Navegación/Avisos S3 dirigido `44656219714` SUCCESS. **Android W03-G** run `44656219736`, job `136022975147`, **SUCCESS** con emulador google_apis x86_64 API35, fuente 200%, JUnit XML **1/1 PASS, 0 failures, 0 errors, 0 skipped**; el testcase es un recorrido integrado largo, NO reclamar que sean múltiples tests.

El recorrido ejercitó Chat inicial, activación explícita DEV de Reality8→Finance9→Auxiliary4, lista/alta/edición de Matter, descarte de borrador sin guardado, Review→Approve→Apply con recibo real, estado/Undo compensatorio/historial, fecha inválida rechazada y vencimiento válido, crear/editar/desactivar Reminder, conciliación local de Avisos, atención Seen y Dismiss con lectura canónica de versión actual, revocación de lectura tras onPause, recreación de Activity y persistencia. Finalmente creó Recordatorio vinculado a un Matter real **elegido por nombre**, confirmó Review/Apply y reabrió la relación durable.

El verificador CI comprobó `W03_G_ANDROID_API35_REAL_OWNER_1_1_PASS` y `W03_G_ANDROID_OFFLINE_MANIFEST_PASS`: package `ar.com.faro.alpha.w03mattersdev`, solo un launcher, sin INTERNET/POST_NOTIFICATIONS/SCHEDULE_EXACT_ALARM/RECEIVE_BOOT_COMPLETED. APK `app-w03MattersDev.apk` SHA256 `1c3e30fb0623ada02dc70bda13f55969beb7d646e753c69175193de1e515b87d`. Artefacto GitHub `w03-g-android35-xml-apk`, ID `14593547645`, digest ZIP `sha256:a53cf737e6bb59ee853427fb90ba5dfec7f44342e4b6da6d1c3a35c975e78cc1`, presente, no expirado. Hashes de log/API; no afirmar re-hash independiente del ZIP.

## B5 — Auditoría adversarial y límites honestos

PASS local DEV por evidencia combinada: Android prueba descarte sin escritura, fecha inválida antes de Review, Apply con recibo, Undo preservando historial, revisión actualizada para Seen→Dismiss y fuente Matter–Reminder preservada; onPause revoca permiso y no muestra datos hasta reautorización; recrear lee persistencia. W03-B y W03-D2B1/D2B2 ya certificaron con tests JVM independientes staleness, policy/foreground, CAS de revisión, idempotencia/replay/receipts, backup/cutover y rechazo de rutas OS no elegibles. No fingir que todos esos escenarios JVM se reprodujeron también en Android.

**Fuera del scope certificado**: W03-F emisión de notificaciones al OS (DEFERRED por decisión aprobada), TalkBack con servicio real en G15, campaña física Moto G15, OS dispatch, producción V2, cross-process crash entre COMMIT y receipt; quedan **NOT_RUN / OFF** y requerirían autorización/campaña separada. Fuente 200% en emulador sí PASS. No datos reales del usuario, no red, main/V4.4 intactos.

## B6 — Implementación

A–D + E y G de W03 LOCAL_ONLY DEV quedaron ejecutados/certificados. Esta unidad no reescribe código certificado, solo consume su permiso al concluir la aceptación API35. Writer único CAS se libera antes y después de CI.

## B7 — PASS_SCOPED_W03_LOCAL_DEV_ANDROID_API35

**Cerrada la aceptación integral local DEV de W03-A/B/C/D/E/G**, con W03-F opcional OS *deferida*. Consumir permiso G READY→BLOCKED, preservar estos SHA/log/XML en puntero, cerrar solo W03 local DEV, no shipping. Siguiente trabajo de S4 requerirá B1–B5/guardia/permiso de la nueva unidad. Verificar guardia final del checkpoint documental y writer RELEASED antes de reportar cierre del turno.
