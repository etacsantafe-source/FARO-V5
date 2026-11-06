# FARO V5 S3.A4 — FBS2 v3 Planning semantic envelope B7 PASS_SCOPED
Fecha: 2026-11-06 AR. Unidad `S3_A4_N031_FBS2V3_SEMANTIC_ENVELOPE`.
SHA funcional `8fb44d2343b58182c97c4e2c32fa8c1940db9172`.

## B7 — Auditoría final independiente / veredicto
**PASS_SCOPED_SEMANTIC_V3_FENCED** — FBS2 v3 versioned sections authenticated *as encoded bytes* under existing FAROBAK2 AEAD, all restore/export paths **deliberately blocked** until real schema8 owner storage/recovery is certified. NOT READY for user export or user restore; NOT A4 CLOSED.

### B1–B5 y permiso anteriores al código
- Preauditoría: `docs/s3-night/S3_A4_N031_FBS2V3_SEMANTIC_ENVELOPE_B1_B5_20261106.md` blob `733b89cfef854279e10056a5b3516f52f4a82cf7`, docs-only commit `489b5dabb2517c29be7356335bfd3248577ca18e`. READY docs-only `e517162282cc38e14c1cacbb9506c7b9846dadb3`.
- B6 exact diff de cinco paths admitidos (FBS2 codec, stage restore fence, export fence, test y workflow), commit con `FARO-UNIT:S3_A4_N031_FBS2V3_SEMANTIC_ENVELOPE`. No cambios de RealityStore, schema7, active pointer, five-store snapshot capture, app, Activity, owner, S2, main o V4.4.
- Writer CAS adquirido epoch 373, liberado epoch 374 durante CI, readmisión B7 epoch 375. Noche/Auto OFF y V2 productiva OFF.

### Siete workflows sobre SHA exacto, todos SUCCESS
- Protocolo obligatorio guardia `seven-block-evidence` [#42260545295](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42260545295): SUCCESS.
- Dirigido v3 [#42260545388](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42260545388): SUCCESS, JUnit XML `FaroV5Fbs2V3PlanningEnvelopeTest` **6/6**; G4 codec **5/5**; legacy FBS2 v1 **7/7**; G4 staged restore **7/7**; export safety **4/4** = **29/29**, 0 errors/failures/skipped. `BUILD SUCCESSFUL in 2m45s`.
- G4 legacy restore fail closed [#42260545293](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42260545293): SUCCESS, XML **1/1 + 7/7 + 5/5**.
- G4 typed codec directed [#42260545207](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42260545207): SUCCESS, XML **5/5 + 7/7**.
- G4 stage-only restore [#42260545212](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42260545212): SUCCESS, XML **7/7 + 1/1 + 7/7 + 5/5**.
- G4 export fence [#42260545163](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42260545163): SUCCESS, XML **4/4 + 5/5 + 7/7**.
- V2 core [#42260545224](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42260545224): SUCCESS, `V2_CORE_DEV_SEARCH_SESSION_XML_PASS 10/10`, `G5_REAL_HOST_FOUR_OWNER_XML_PASS 1/1`, `M2_DEBUG_T4_RELOCATED_XML_PASS 7/7`, `S3_A1_SCHEMA7_GENERATION_XML_PASS 17/17`, `V2_CORE_M2B_DEV_APP_ISOLATED_PASS 4/4`, `V2_CORE_M1_ISOLATION_PASS 4/4`. No prueba física.

### Negativos / alcance exacto
- `FaroV5FiveStoreSemanticSet` incorpora `planningDomains` opcional pero **FBS2 v3 exige Planning + G4 explícitos**; null no significa owner vacío certificado. v1/v2 byte-compatible en tests. v3 cuatro secciones delimitadas+digests, límite global16MiB, sublimit4MiB por G4/Planning. SHA section != authenticity; FAROBAK2 AEAD separada, plaintext no se persiste.
- `FaroV5FiveStoreStagedRestore.restoreAuthenticated()` y `restoreAuthenticatedG4StageOnly()` rechazan v3 con `UNSUPPORTED_PLANNING_ARCHIVE` ANTES del pin y de todo cambio SQLite. JUnit confirma sin alterar Reality/Workspace ni pointer y permite recuperar archivo legacy v1 con mismo operationId tras v3 bloqueado (prueba de no-pin). ExportPreparation niega explícitamente Planning; los G4 v2 productivos continúan bloqueados como antes.
- **NO implementado**: schema8, Reminder/MonthlyPlan Reality owner persistence, 5/6/7→8 migrations, 5-owner quiescent real v3 capture, staged idempotent 4-owner replay, user export/restore approval, host ACL/N031+N015 o campaña Moto G15. El test semántico B7 NO certifica persistencia de datos reales.
- S2 CLOSED 8/8; S3 A1/A2 2/5 alcances automatizados; A3 +8 candidates no aprobadas / catálogo172; S3 A4 OPEN; A5 X01–X12/G15 NOT_RUN; V2 prod/Noche/Auto/S4 OFF.

### Cierre y siguiente unidad
Poner permiso `BLOCKED`, checkpoint durable, writer RELEASED epoch376 y NEXT obligatorio: **migración real a Reality schema8, propietarios Reminder/MonthlyPlan, captura y restauración v3 con cobertura íntegra**, pruebas de migración desde schemas 5,6,7 y fresh8; para cada nuevo cambio ejecutar B1–B5 adversarial documentados antes de READY, **sin escribir datos persistentes hasta garantizar backup+restore**. No repetir G4 ni estos siete workflows por rutina.
