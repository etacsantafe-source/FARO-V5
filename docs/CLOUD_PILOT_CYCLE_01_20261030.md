# FARO V5 — Piloto autónomo nube · ciclo 01 · 2026-10-30

## Autoridad, límites y exclusividad
- Autorización nueva y expresa del usuario: un piloto de hasta 3 horas desde 2026-10-30 10:21:34 America/Argentina/Buenos_Aires, con corte absoluto conservador 13:20 o S2.A6 CLOSED/PASS 8/8, lo primero.
- Repositorio exclusivo de escritura: `etacsantafe-source/FARO-V5`, PRIVATE. Rama exclusiva `work/cloud-afternoon-pilot-20261030`; `main` inalterada en `9bae214541f466a62e149853931b96abb8d74ed9`.
- Origen `etacsantafe-source/FARO` READ_ONLY, C138 CLOSED/epoch138/writer NONE, HEAD `44f1e6eb77102a71955b4fb113582d209d94ac69`; su checkpoint `docs/v5/checkpoints/V5_S2_A6_C138_SAF_HOST_BOUNDARY_20261030.md` mantiene autoridad histórica. Esta rama de prueba NO se convierte en autoridad del producto.
- Preflight GitHub: único piloto programado activo, ningún PR abierto, ninguna Actions en ejecución antes del commit, rama HEAD `3a514198c6559f8a21a81dee144f17ccd5f23e66`; cambio aplicado mediante `update_ref` con `expected_sha` exacto (CAS), sin force. No se abrió un writer permanente ni se compartió escritura entre tareas.
- Sin Termux, merge, gasto, secretos, publicación, firma, permisos ampliados, datos de usuario, V4.4/FARO Auto, ni modificaciones de código de producto.

## Entrega acotada F2 — regresión de admisión y recuperación V1/V2
- Commit fuente inmutable: `d43ce4d07bc8a75262cc0017f5307f341fa193de`, padre `3a514198c6559f8a21a81dee144f17ccd5f23e66`.
- Único archivo modificado: `.github/workflows/faro-v5-cloud-ci.yml`. Se añadieron al gate F1/F3 ya existente dos clases F2 ya presentes en el producto, sin reescribirlas: `FaroV5PreSwitchHostAdmissionT2Test` y `FaroV5QuarantinedRecoveryVsV1GateT2Test`. Se exige XML exacto y ausencia de fallos/errores/skips. Permisos del workflow: `contents: read`.
- Evidencia CI nueva: GitHub Actions [41062805515](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41062805515), `completed/success`, SHA exacto `d43ce4d07bc8a75262cc0017f5307f341fa193de`, finalizada 2026-10-30T13:31:04Z.
- T0 `FARO_V5_T0_PASS`, composición `FARO_V5_APP_P5_A8_PASS`, spec `FARO_V5_SPEC_BASELINE_PASS`, compilación Android Kotlin/Java BUILD SUCCESSFUL.
- XML del runner (tests, failures, errors, skipped):
  - `BackupExternalWriteT2Test`: `(4,0,0,0)`.
  - `FaroV5ReviewedApplyHostAdapterTest`: `(3,0,0,0)`.
  - `FaroV5ReviewedApplyHostIntegrationTest`: `(2,0,0,0)`.
  - `FaroV5PreSwitchHostAdmissionT2Test`: `(7,0,0,0)`.
  - `FaroV5QuarantinedRecoveryVsV1GateT2Test`: `(3,0,0,0)`.
- Marker de logs `FARO_V5_NEW_REPO_DIRECTED_XML_PASS 19/19`. Se añadieron **10** casos dirigidos F2 a la base de **9**; evidencia de regresión, NO certificación de ciclo Android E2E.
- El gate es CI de JVM/compilación; NO hubo T4 físico ni prueba de recuperación completa del host Android.

## Ocho criterios congelados (sin redefiniciones)
1. Identidades/gate de cinco owners — CERTIFIED histórico.
2. FBS2/restore aislado — CERTIFIED histórico.
3. Journal/pointer/rollback/replay/WAL T2 — CERTIFIED histórico.
4. F1 Chat/Composer/Review/Apply/Delivery Android E2E — IN PROGRESS.
5. F2 activación/recuperación Android E2E — IN PROGRESS (nuevo gate F2 de regresión PASS, NO cierre).
6. F3 cinco stores export/restore/SAF/consentimiento — IN PROGRESS, material UX conflict BLOCKED.
7. F4 regresiones integrales críticas — PENDING.
8. T4 físico — NOT_RUN.

**S2.A6 OPEN · 3/8 CERTIFIED · V2_ACTIVE_ENABLED=false · V1 preservado · S3/S4 NOT OPEN.** No declarar F1/F2/F3/F4 certificados por 19/19 JUnit.

## Bloqueo y siguiente paso seguro
- Conflicto material F3: `V5_S2_A6_FINISH_LINE.md` requiere consentimiento/flujo completo en S2; `V5_SURFACE_CUTOVER_DECOMMISSION_PLAN.md` asigna Settings backup/restore a S5. No se resuelve unilateralmente ni se implementa S5.
- En el próximo ciclo, volver a validar HEAD, ausencia de ejecuciones/escritores y permiso; priorizar un recorrido funcional real F1/F2 y regresión V1, no multiplicar microtests. Si falta exclusividad, pasar a READ_ONLY; si T4 no está disponible, mantener NOT_RUN.
- Este checkpoint es evidencia de la **rama piloto**, no handoff, cutover, release ni autorización para abrir S3/S4.
