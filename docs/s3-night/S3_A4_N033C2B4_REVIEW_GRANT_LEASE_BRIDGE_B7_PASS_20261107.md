# FARO V5 — S3.A4 N033C2B4 Review scope ↔ grant lease; B7 PASS_SCOPED
Fecha 2026-11-07 AR. Unidad S3_A4_N033C2B4_REVIEW_GRANT_LEASE_BRIDGE. SHA funcional exacto 3aba5ebac7a60358aaf049cac269a69c84ab0e7a.

## B7 — Auditoría final independiente: PASS_SCOPED_IN_PROCESS_BRIDGE_ONLY
- B1–B5 ANTES de código: docs/s3-night/S3_A4_N033C2B4_REVIEW_GRANT_LEASE_BRIDGE_B1_B5_20261107.md, blob fc4eb004e3d2433d2b2d938945b720bfedec8962, docs commit 91cf88c4e8e8971a5707294281c416fa8be38e1a. Permiso READY limitado a CUATRO paths en docs commit 339b00dc0fbc738e1eb7172ac392d3ceef7799b2.
- B6 code 3aba5ebac7a60358aaf049cac269a69c84ab0e7a FARO-UNIT:S3_A4_N033C2B4_REVIEW_GRANT_LEASE_BRIDGE, modifica SOLO: FaroV5Planning8ApprovedObjectGate.kt, nuevo FaroV5Planning8ReviewGrantLeaseBridge.kt, su suite nueva y workflow nuevo. Sin otros archivos Kotlin/código/manifest; no main/V4.4/S2/schema/FBS2/catálogo172.
- Writer CAS epoch491 en B1–B6, RELEASED epoch492 durante CI, reacquired epoch493 para B7, liberar epoch494 después de checkpoint. V2 productiva/Noche/Auto/S4 OFF, sin pruebas físicas Android G15.

### Evidencia exact SHA GitHub Actions, seis SUCCESS
- #42430753094 https://github.com/etacsantafe-source/FARO-V5/actions/runs/42430753094: N033 bridge directed SUCCESS, BUILD SUCCESSFUL, XML 51/51: FaroV5Planning8ReviewGrantLeaseBridgeT2Test 7/7, FaroV5Planning8ObjectGrantOwnerT2Test 18/18, FaroV5Planning8ApprovedObjectGateT2Test 14/14, FaroV5ActiveWorkspaceSearchGrantOwnerTest 10/10, FaroV5ChatToReviewedApplyFlowT2Test 2/2; cero failures/errors/skipped.
- #42430753095 https://github.com/etacsantafe-source/FARO-V5/actions/runs/42430753095: objeto+Review anterior SUCCESS XML 26/26.
- #42430753070 https://github.com/etacsantafe-source/FARO-V5/actions/runs/42430753070: V2 core SUCCESS, Chat10/10 G5 host1/1 M2Debug7/7 schema7 generation17/17 M2B4/4 M1 4/4.
- #42430753049 https://github.com/etacsantafe-source/FARO-V5/actions/runs/42430753049: isolated v2CoreDev APK package gate SUCCESS, BUILD SUCCESSFUL, package ar.com.faro.alpha.v2coredev, launcher único FaroV5V2CoreDevActivity, debug APK no sensibilidad/OS permissions, valid v2 signature; APK SHA256 03ec124789d0daa92da71c4f0a20f778afe402645901b882b57ea60bb1009b65. Esto NO es instalación/prueba física, no habilita V2.
- #42430753046 https://github.com/etacsantafe-source/FARO-V5/actions/runs/42430753046: Home notices regression SUCCESS.
- #42430753032 https://github.com/etacsantafe-source/FARO-V5/actions/runs/42430753032: mandatory seven-block protocol guard SUCCESS.
- Se comprobó status=completed/conclusion=success/head_sha=3aba5ebac7a60358aaf049cac269a69c84ab0e7a para las seis. No se cuentan pruebas históricas de otro SHA ni paquetes como pruebas físicas.

### Verificación funcional y límites
- Método interno currentApprovedObjectScopeOrNull(context) conserva EXACTAMENTE el algoritmo read-only probado por el Boolean histórico, agregando el retorno del scope operationId+objectId tras contrato, Review, payload, epoch y pointer. hasCurrentApprovedObjectReview sigue devolviendo Boolean de scope!=null.
- El bridge requiere scope exacto derivado de Review y nonce del mismo grant. Relee canon/pointer/hostReady antes y DENTRO del monitor existente FaroV5Planning8ObjectGrantOwner.withCurrentGrantLease. Action se invoca una sola vez solo cuando ambos chequeos pasan. No envuelve ni oculta excepciones del action.
- Pruebas 7/7: deny sin grant, admisión scope exacto, revisión de objeto A no válida para grants B/otra operación, review descartado, pointer ABA y host no listo, revoke concurrente espera callback, fallo libera monitor. Policy inyectada es FIxTURE explícita de test, jamás autoridad del host productivo.
- LOCK: solo protege revoke/revokeAll/close del MISMO owner/proceso durante callback síncrono. NO congela policy externa, cambios de pointer entre procesos, Workspace después de BeginApply ni mutación Reality. No hay transacción ni receipt de Planning en esta suite; éxito de callback NO implica COMMIT y no se promueve a host.
- Para ACL Planning real falta autoridad local legítima de solicitud/Review + principal técnico, epoch y decisión policy vinculados a objeto/operación, host real, orden de locks Workspace→grant→Reality, callback COMMIT+ROLLBACK y crash recovery/Undo N033C3. B1–B5 global TRANSACTIONAL_PLANNING_ACL previo sigue STOP. V2 shipping OFF, S3.A4 OPEN, A3+8 Guardados no aprobado, catálogo 172, S3 formal 0/5, S3 dev ~40% heurístico, pruebas G15 S3.A5 NOT_RUN.

### Continuidad
- Cerrar permiso BLOCKED y liberar writer epoch494. Próximo N033C2B4_LOCAL_REVIEW_TO_APPLY_TRANSACTION_POLICY: B1–B5 completamente nuevo para host real, no fake policy; comprobar fuente de evento de Apply explícito del usuario y serialización con owner+Workspace+Reality COMMIT. Si autoridad sigue ausente, B5 STOP documentado, no agregar LOGIN/cloud/permiso abierto.
- Después N033C3 Undo/retry y cierre de S3.A4, luego campaña A5 Android única. No reabrir pruebas 51/51 sin cambios.
