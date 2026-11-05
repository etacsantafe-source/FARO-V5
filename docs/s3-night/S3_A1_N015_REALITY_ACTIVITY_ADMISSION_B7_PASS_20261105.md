# FARO V5 — S3.A1 N015 Reality activity admission · B7 independiente (05/11/2026)

## B7 AUDITORÍA FINAL INDEPENDIENTE

**Dictamen: PASS_SCOPED**, SOLO para `S3_A1_N015_REALITY_ACTIVITY_ADMISSION`. **NO** certifica `history.query` federado, la autorización del host real ni la conexión de Actividad reciente en Inicio. S3.A1 continúa OPEN. Revisión de código exacto `9d2491f6a03114753e68cdb4c37fb2f0a4c6b046` contra `079f876b48187ffe960d96aa26b174e891ebfa5b`.

### B1–B5 y alcance probado
Preauditoría: `docs/s3-night/S3_A1_N015_REALITY_ACTIVITY_ADMISSION_B1_B5_20261105.md` (blob `068a6e32c24ad694d7fa43ab9537580bcfb2303a`); permiso READY previo al cambio y separado del código. Cambios funcionales **únicamente** `FaroV5RealityTimedActivityReadSource.kt`, `FaroV5RealityTimedActivityReadSourceTest.kt` y workflow dirigido de app. El commit declara `FARO-UNIT:S3_A1_N015_REALITY_ACTIVITY_ADMISSION`.

El nuevo bridge recibe un `RealityTimedReceiptHistoryReadPort` real pero inyectado por host, hace revalidación anterior/posterior de admisión/generación/epoch, niega página tras revocación, conserva el cursor y el marcador `legacyUndated`. `coverage=REALITY_ONLY_NOT_FEDERATED` y `globallyComplete=false` son constantes; no cuenta ni monta indicadores Home, ni inventa horas de recibos legacy.

### Evidencia CI exacta
- GitHub Actions [42110935335](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42110935335), SHA `9d2491f6a03114753e68cdb4c37fb2f0a4c6b046`: **SUCCESS**. Log job `app-home-notice`: `S3_A1_REALITY_ACTIVITY_XML_PASS 6/6`, `S3_HOME_NOTICE_XML_PASS 11/11`, `S3_WORKSPACE_SEARCH_XML_PASS 7/7`, `S3_G4_FINANCE_SOURCE_XML_PASS 6/6_UNMOUNTED`, `S3_SEARCH_BRIDGE_XML_PASS 7/7`, `S3_AVISOS_XML_PASS 4/4`, `S3_CHAT_POINTER_ABA_XML_PASS 6/6`, `S3_ACTIVE_HOST_ABA_XML_PASS 1/1`, `S3_SEARCH_HOST_GRANT_XML_PASS 10/10`, `S3_G2_HOST_PORT_XML_PASS 10/10`, manifest single-process PASS. Las seis pruebas nuevas constan presentes y ejecutadas: 0 failures/errors/skipped.
- [42110935072](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42110935072) `seven-block-evidence` **SUCCESS** para mismo código SHA.
- Commit documental WAIT_CI `cacdd9a24dc2c2596e970454e3559aa5b1b924e0` generó [42111146380](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42111146380) **FAILURE** en preflight: `previous code change has no B7 disposition`. El historial de 40 unidades pasó; este B7 y disposición de permiso separada corrigen el fallo procedimental. No atribuir éxito a ese run.
- M5 APK y V2 core builds para SHA `9d2491f6` se observaron **IN_PROGRESS** al auditar; no contarlos como PASS ni certificar Android.

### Negativos / límites
PASS (JVM): ACL ausente no abre store; epoch/puntero revocados a mitad de consulta no revelan resultados; excepción no entrega contenido; cancelación se propaga; límites 1..100; legacy sin hora y cursor preservados. Esto no prueba inyección del host productivo ni revocación después del retorno, ACL independiente entre owners, multiproceso, Activity UI, resumen de Workspace/Auxiliary/Reality, ni campaña física.

**Restantes S3.A1**: (a) N015 owner real federado `history.query` y read grant del host; (b) N031 Pendientes combinado Matter/Reminder/MonthlyPlan, prioridades/snooze y ACL; (c) Guardados read-owner persistente sin aumentar catálogo 172 (+8 candidatos NO APROBADOS). Las 4 opciones de Inicio mantienen `No disponible` donde el owner real falta. S2 8/8 cerrado; S3.A2 5/5 cerrado dentro del scope automatizado; X01–X12 y Moto G15 al final S3.A5; S4, V2 productiva, Auto y Noche OFF.

**Acción de control**: marcar el último código `9d2491f6a03114753e68cdb4c37fb2f0a4c6b046` B7 PASS_SCOPED en `FARO_PROTOCOL_WORK_PERMIT.json` y volver a `status=BLOCKED`; antes de cada nuevo código, B1–B5 distintos, READY por ruta exacta y nuevo preflight. Writer liberado antes de CI, readquirido solo para B7 documental.
