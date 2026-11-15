# FARO V5 — S4 W00 · B7 independiente de pre-código (solo documentación)
**Fecha:** 2026-11-15 (AR). **Unidad:** `S4_W00_PRECODE_FEASIBILITY_REVIEW`. **No** es autorización para escribir código. **S4 formal OFF**.

## B1 — Recuperación comprobada
Baseline `42e889d2f66fd61e074790b6e1dde5454fc3ae56`, writer RELEASED 909, permiso BLOCKED. Tras adquisición CAS, writer HELD epoch 910 `FARO.MANUAL.S4.W00.PRECODE.20261115`. Se leyó `AGENTS.md`, pointer, permiso y P01/P03/P08, más blobs reales de ApplyPlan, nodos, ApplyTransactionExecutor, RealityStore, Finance SPI/model, Gradle y test kernel. P08 previo B7 de guardia **NO certificado**, no elevarlo.

## B2 — Reutilización
S2 8/8 y S3 5/5 DEV con H07-HOME excepción humana (NO PASS). P00–P08 documentado; no se modifican ni repiten. Los resultados del test `RecordingTransactionPort` no se atribuyen a dos owners reales.

## B3 — Evidencia externa y riesgos
`https://sqlite.org/wal.html` documenta la excepción de atomicidad **multiarchivo ATTACH+WAL**; `https://www.sqlite.org/lang_transaction.html` delimita BEGIN y savepoints. Se comparan transacción REAL de un archivo, multiarchivo WAL y dos commits secuenciales, descartando los últimos para P07 opción A. No introducir librerías/APIs pagas. Investigar source del host/ACL por ruta antes de READY.

## B4 — Resultado alcanzado
Documento W00 B1–B5 `docs/v5/FARO_S4_W00_PRECODE_B1_B5_AND_FIRST_UNIT_20261115.md` publicado en commit `568dc6be3a0153dba90761895b6ee5682e69d116`, blob `67ebc91ec3ffee54e331376311323575bdb4cf37` (GET igualdad exacta). Hallazgo: `RealityStore.run` hace BEGIN IMMEDIATE y receipt dentro de COMMIT de un archivo; falta owner/executor Household real y prueba Finance+Household. Primera unidad `S4_W00_A_REALITY_TWO_EXISTING_OWNERS_ATOMICITY_TEST` queda con **única ruta nueva propuesta** y W00-T01..T10 definidos. Esto NO es W06 PASS.

## B5 — Limitaciones no negociables
- No aprobar S4/owner Household, schema ni permiso READY a partir de este documento. Household solo diseño previamente aprobado.
- La fuente primaria de atomicidad no garantiza que un host serialice revocaciones/generation/ACL al final del COMMIT. Prueba real host es futura.
- W00-A prueba solo Finance+Objectives; no certificar W07. W06 Household+Finance real requerido antes de pago.
- No escribir operación en catálogo180, Kotlin/Gradle, workflow, main ni V4.4; S4 productiva, V2 productiva, Auto y Noche OFF.

## B6 — Diff documental permitido
Publicación del W00 B1–B5, este B7 y pointer, bajo writer epoch 910. Esta auditoría **no convierte en PASS el B7 previo de P08**. No se corrió CI/Android/G15 de W00, no se toma por PASS una guardia de otro SHA.

## B7 — Checklist posterior al último commit (estrictamente NO PASS anticipado)
1. HEAD remoto incluye W00 B1–B5, este B7, pointer y los mismos contratos preexistentes; diff base→HEAD solo estos tres archivos docs/v5/.
2. Pointer: S4 OFF, W00 `PRECODE_DOC_PUBLISHED_FIRST_CODE_UNIT_CANDIDATE_NOT_READY`; permiso funcional `BLOCKED`. Riesgo/primera ruta y W00-T01..T10 trazables.
3. Writer `RELEASED` epoch 910, last_checkpoint=HEAD; no escritor ajeno.
4. Guardia `seven-block-evidence` del HEAD NUEVO `SUCCESS`, verificada por run/job; si API GitHub de consultas por SHA solo devuelve pull_request y hay push, NO interpretar resultado vacío como éxito ni fracaso: `WAIT_CI_UNVERIFIED`.
5. Documentos B1–B5 son auditoría de fuente y test design, no Kotlin implementado. Para W00-A READY faltarán revisión de test source existente, aprobación formal de alcance S4, preflight por **una ruta exacta**, permiso con evidencias B1–B5 y B7 pendiente, emitido en commit docs separado. Nunca autoactivar.

**Conclusión permitida hoy:** paquete PRE-CÓDIGO W00 establecido, no `S4_READY_TO_PROGRAM` hasta satisfacer guard+apertura+permiso; todas las nuevas pruebas `NOT_RUN`.
