# FARO V5 — Piloto autónomo nube · ciclo 02 · 2026-10-30

## Autoridad y preflight
- Piloto autorizado desde 2026-10-30 10:21:34 America/Argentina/Buenos_Aires; corte absoluto 13:20 o S2.A6 CLOSED/PASS 8/8, lo primero.
- Repo de escritura exclusivo `etacsantafe-source/FARO-V5` PRIVATE; rama exclusiva `work/cloud-afternoon-pilot-20261030`. Main confirmado inalterado: `9bae214541f466a62e149853931b96abb8d74ed9`.
- Antes de editar: HEAD `109cf971eb625b1c091136649337cef8cb0e5391`; ninguna Actions activa, ningún PR abierto, permisos GitHub push presentes; único writer de este ciclo. Publicación atómica de los dos archivos mediante Git tree/commit y `update_ref` con `expected_sha` exacto, sin force.
- Repositorio original `etacsantafe-source/FARO` solo lectura; autoridad histórica C138. Sin Termux, V2 ON, S3/S4, merge, cambios de V1, firma, secretos, gasto, cambio de visibilidad o permiso de workflow.

## F1: un recorrido integrado real, todavía no entrega Android E2E
- Commit fuente: `56781736a4e315453b21c89f64e5984ba98f297d`, padre `109cf971eb625b1c091136649337cef8cb0e5391`.
- Nuevo test `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5ChatToReviewedApplyFlowT2Test.kt`, sin cambios de código de producción.
- Recorre **el mismo Workspace SQLite real** desde selección de conversación, Composer draft/send, timeline leído por host, creación de plan con envelope/fingerprint, aprobación humana por Review port, rechazo de Apply por UI mientras V2 OFF, ejecución solo por seam interno confiable, Reality SQLite receipt, cierre/reapertura de ambos stores y verificación de idempotencia y estado persistido.
- El test NO demuestra UI Android, generación/modelo, Delivery OS, activation/cutover ni T4 físico; tampoco habilita `executeAfterHumanRequest` para UI. No promover F1 a CERTIFIED.
- CI: se añadió esta clase a `.github/workflows/faro-v5-cloud-ci.yml` con permisos `contents: read`, XML exacto 1/0/0/0 y total dirigido 20/20. Un solo push disparó una sola Actions.

## Certificación de GitHub Actions
- Run: [41071061340](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41071061340), terminal **completed/success**, HEAD probado `56781736a4e315453b21c89f64e5984ba98f297d`, completado 2026-10-30T14:28:03Z.
- Structural `FARO_V5_T0_PASS`; composición `FARO_V5_APP_P5_A8_PASS`; spec `FARO_V5_SPEC_BASELINE_PASS`; compilación Kotlin/Java `BUILD SUCCESSFUL`.
- JUnit XML del runner (tests, failures, errors, skipped):
  - BackupExternalWriteT2Test: `(4,0,0,0)`.
  - FaroV5ReviewedApplyHostAdapterTest: `(3,0,0,0)`.
  - FaroV5ReviewedApplyHostIntegrationTest: `(2,0,0,0)`.
  - FaroV5PreSwitchHostAdmissionT2Test: `(7,0,0,0)`.
  - FaroV5QuarantinedRecoveryVsV1GateT2Test: `(3,0,0,0)`.
  - FaroV5ChatToReviewedApplyFlowT2Test: `(1,0,0,0)`.
- Marcador real de log: `FARO_V5_NEW_REPO_DIRECTED_XML_PASS 20/20`. Los XML se verificaron por el propio runner, no se subió artifact ZIP; evidencia en logs del run.
- No hubo correcciones ni repetición del gate, ni segundo workflow.

## Estado de los ocho criterios originales
1. Identidades/gate de cinco owners — CERTIFIED histórico.
2. FBS2/restore aislado — CERTIFIED histórico.
3. Journal/pointer/rollback/replay/WAL T2 — CERTIFIED histórico.
4. F1 Chat/Composer/Review/Apply/Delivery Android E2E — IN PROGRESS, nuevo recorrido cruzado JVM PASS pero sin UI/modelo/Delivery.
5. F2 activación/recuperación Android E2E — IN PROGRESS, regresiones 10/10 del ciclo 01 PASS.
6. F3 cinco stores export/restore/SAF/consentimiento — IN PROGRESS / decisión material UX BLOCKED.
7. F4 regresiones integrales críticas — PENDING.
8. T4 físico — NOT_RUN.

**S2.A6 OPEN · 3/8 CERTIFIED · V2_ACTIVE_ENABLED=false · V1 preservado · S3/S4 NOT OPEN.**

## Próximo paso seguro
- Volver a comprobar exclusividad/HEAD/Actions antes de otro ciclo.
- Priorizar cableado funcional faltante F1 hacia host Android real (UI/modelo/Delivery), o una brecha F2 de recuperación concreta, sin ampliar batería de microtests ni activar V2.
- F3 requiere decisión humana: consentimiento/exportación en S2 versus Settings backup/restore asignado a S5. No decidirla automáticamente.
- T4 físico permanece pendiente y no se sustituye por Actions. No hay autorización de cutover ni de cambio de autoridad.
- Este checkpoint es solo evidencia durable de la rama piloto, no certificación ni handoff a producción.
