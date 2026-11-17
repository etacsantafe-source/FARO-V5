# FARO V5 W01 M1C1B0 — Finance9 grant STAGED y recibo REAL, B1–B5
Fecha 2026-11-17 AR. HEAD observado 05f62c564e1ab0e76c774719fcee18ef713ffe92. Writer epoch1027. DOC_ONLY; B6 NOT_RUN. W2 STOP.

## B1 — Evidencia del HEAD
M1A RealityFinance9Acl.kt está B7 PASS staged: issue/revoke policy con epoch monotónico bajo BEGIN IMMEDIATE, sin emisión durable de grant. M1C0 está B7 PASS (12c644f6693e5ec782796d2deb61f9ac0081c1c9): RealityStore.finance9AclCurrentInsideApplyTransaction verifica policy dentro de BEGIN IMMEDIATE con dos PID reales 8/8 y cuatro pares de PID. M1C1A está B7 PASS scoped (05f62c564e1ab0e76c774719fcee18ef713ffe92): revisión APPROVED y evidencia M1B estructural 9/9, sin grant ni prueba física. No repetir.
Schema9 ya contiene finance_apply_grant con grant_id PK, intent_key UNIQUE, FK policy scope, FK execution_receipt, policy_epoch, fingerprint, issued/expiry, used_at y receipt_id ambos NULL o ambos no-NULL.
RealityStore.commit(scope,identity,[]) inserta recibo real y cambia PREPARED a COMMITTED dentro de RealityStore.run BEGIN IMMEDIATE -> COMMIT. No existe aún consumo del grant dentro del mismo COMMIT. El host real de Android y su post-receipt hook son posteriores.

## B2 — Reutilización y frontera
Reutilizar DDL9, RealityFinance9Acl, RealityStore.commit, el scope M1C0, FK/UNIQUE, tests y preflight. No duplicar arquitectura M0, M1A, M1C0, ni tocar V4.4. La emisión de grant es exclusivamente stage-test con Finance9AclScope ya inyectado; NO genera principal real ni autentica humano. Sólo staging9, no variante activa.

## B3 — Alternativas
ADOPTAR issueGrantAfterTrustedStageAdmission bajo BEGIN IMMEDIATE + validación policyEpoch, fingerprint hex64, intent_key y TTL. ADOPTAR finance9ConsumeGrantAgainstReceiptInsideApplyTransaction después de RealityStore.commit y antes de Reality.run COMMIT; consultar el recibo real del MISMO transactionId y fingerprint en la misma conexión; UPDATE grant SET used_at_ms,receipt_id WHERE scope exacto, unused, policy/current epoch y vencimiento; comprobar SELECT changes() == 1. Si no coincide, devolver false, caller obligatorio Rollback. SQLite serializa otro proceso.
RECHAZAR callback previo al BEGIN como autoridad, receipts de UI, bandera APPROVED, dos SQLite/ATTACH WAL, consumir fuera de transacción o usar host in-memory. DEFERIR M1C1B1: integración real de ApplyTransactionExecutor posterior al receipt con rechazo obligatorio y AndroidKeystore principal estable/presencia física G15.

## B4 — Cuatro rutas exactas
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9Acl.kt
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt
- v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9GrantReceiptStageTest.kt
- .github/workflows/faro-v5-s4-w01-m1c1b0-grant-receipt-directed.yml
Criterios R01–R08 y XML exact:
R01 grant staged se emite sólo con política y reabierto persiste unused.
R02 Apply REAL RealityStore.commit + efecto Finance y consumo grant con recibo real en SAME SQL TX produce COMMITTED único.
R03 revocar policy antes Apply produce DENY y rollback del efecto y receipt.
R04 fingerprint, intent/grant o scope/generación/pointer ajeno produce DENY y rollback.
R05 grant vencido o policy vencida no autoriza.
R06 reuso de grant usado rechaza, no duplica receipt ni efecto.
R07 falla inyectada después de consumo y antes de SQL COMMIT revierte efecto, receipt y used_at.
R08 schema7/8, admisión de stage falsa y colisión de unique/fk rechazan.
CI :store:reality:test --tests RealityFinance9GrantReceiptStageTest, ocho JUnit exactos y regresiones estrictamente pertinentes. Sin APK ni hardware.

## B5 — Auditoría adversarial
Comprobar owner de scope (activeApplyScope, transactionId, financeStage9/planningStage8, schema9), receipt real por ejecución y journal COMMITTED. Grant scope incluye principal_hash, generation_id, pointer_sequence, operation_id, object_scope, policy_epoch, plan fingerprint; ninguna herencia entre objetos o monedas. Deny implica rollback obligatorio desde caller; proveer sólo seam stage y no conectarla a producto. Reloj de test no equivale reloj inviolable. Missing/hardware/Keystore/informado NO CERTIFICADOS. No borrar/reescribir backups M0, V2 productiva OFF, main/V4.4/S2/S3/catalog180/W2 STOP y no pagos reales.

## B6
NOT_RUN hasta guard B1–B5 SUCCESS, writer RELEASED, permiso READY exact4 independiente y preflight.

## B7
PENDING exact 8/8 XML, guard SHA, regresiones relevantes y writer RELEASED. PASS sólo staged SQLite9; M1C1B1 Android productivo y G15 pendientes.
