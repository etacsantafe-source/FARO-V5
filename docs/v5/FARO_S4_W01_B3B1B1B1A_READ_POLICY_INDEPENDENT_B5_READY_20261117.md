# FARO V5 W01 B3B1B1B1A — B5 adversarial independiente + READY exact cuatro
Fecha 2026-11-17 AR. Unidad `S4_W01_B3B1B1B1A_FINANCE9_READ_ONLY_POLICY_ISSUER_DEV`. Baseline SHA `5096516b4af77f6386517acacc493e71cbf5baf4`; guard de documento previo `44109546378` SUCCESS; preauditoría `docs/v5/FARO_S4_W01_B3B1B1B1A_SEPARATE_READ_POLICY_ISSUER_B1_B5_20261117.md` blob `22ba3a1875d7baf8ae75f66c7cfb3193a652bcb8`. Este B5 no autoriza host Android ni UI.

## B1
Verificación secundaria: `RealityFinance9Acl.issueAfterTrustedStageAdmission` agrega fila policy sin grant; `RealityFinance9Acl.revoke` aumenta epoch; `RealityFinance9ObservedSourceReadPort` consulta operation/scope FIJOS `finance.snapshot.read/finance:all` en una transacción WAL. `RealityStore.finance9TrustedStageGrantIssuerForDevOnly` genera `finance_apply_grant` y está prohibido para leer. B3B1B1B0 B7 cerrado `2a2d4c100b44adf732d2a529b7ca5806d36d01d1`, 8/8 +5/5; no repetir certificación.

## B2
Reutilizar el SQL ACL probado, owner SQLite9 y `withMaterialAdmission`; no tocar implementación de `RealityFinance9Acl`, ningún DDL, ninguna migración, manual Finance Apply/receipts, B3B1B0/B3B1B1A/B3B1B1B0, V4.4/main/S2/S3/FBS2v4 ni CompositionRoot. Es estrictamente **emisión de policy local para pruebas**, no datos financieros ni operaciones reales.

## B3
Comparación adversarial A vs B: A implementa emisor de read-policy con identidad y scope FIJOS; TTL explícito máximo 30s, epoch SQL monotónico, separación total de Apply. B reutiliza método de Apply-grant y debe rechazarse por generar permisos incorrectos y confundir lectura/escritura. Android https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.CryptoObject exige operación auth-per-use real para certificar callback; no hay aquí. SQLite https://www.sqlite.org/isolation.html confirma que revocación posterior a lectura exige nueva observación. No emitir eventos de UI ficticios ni reclamar read grant productivo.

## B4
Permiso **EXACT4** de código:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedReadPolicyIssuerPort.kt`: NUEVO DTO `Finance9TrustedReadPolicyIssueRequest` con principal SHA256 hex64, generation ID validada, pointerSequence>0, issuedAtMs>=0, expires>issued <=30_000ms; *sin campos operationId ni objectScope*, se fijan en el port; record `Finance9ReadPolicyIssuedRecord` que sólo informa epoch, expires y identidad, y `RealityFinance9TrustedReadPolicyIssuerPort` funcional `issueAfterExternallyVerifiedReadAdmission`. Ningún sello humano, credencial de UI ni poder de Apply.
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`: factory `finance9TrustedReadPolicyIssuerForDevOnly()` de RealityStore SOLO STAGED9. `withMaterialAdmission` + lock owner, `activeTransactionId==null`; `RealityFinance9Acl.issueAfterTrustedStageAdmission` con `Finance9AclScope(principal,generation,pointer,OPERATION_ID,OBJECT_SCOPE)` y luego devuelve record sólo con epoch>0; sin `issueGrantAfterTrustedStageAdmission`, sin `finance_apply_grant`, sin receipt.
3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedReadPolicyIssuerTest.kt`: 8 JUnit SQLite9 reales con nombres R01–R08: schema7/8 deny, policy read sin Apply grant, lectura atómica B3B1B0 positiva, wrong principal/generation/pointer/epoch/operation deny, TTL/expiry boundary, renewal/ABA old denied, revocation externa/renewal, closed/malformed/receipts 0.
4. `.github/workflows/faro-v5-s4-w01-b3b1b1b1a-read-issuer-directed.yml`: workflow exact :store:reality:test con test nuevo + B3B1A/B3B1B0 afectados, XML esperado 8/8 nuevo + 8/8 +8/8, y control de cero product wiring; artefacto XML, guard.
No tocar Manifest, Activities, Shell, Compose, Home, Graph, schema, V4.4/S2/S3/main. Code commit FARO-UNIT exacto; guard antes de B6.

## B5
Auditoría hostil separada:
- A1 **BRECHA NO RESUELTA**: cualquier código DEV con acceso al issuer puede fabricar principalHash y timestamp, activar policy. No usar fuera del staging. Tests SQLite sólo prueban SQL y aislamiento de scope, jamás AndroidKeystore ni consentimiento informado.
- A2 `operationId` y `objectScope` no son parámetros de request, deben ser constantes. Un policy READ nunca crea `finance_apply_grant`, receipts, Apply ni pagos reales.
- A3 limitado a TTL 30s (límite superior, no licencia para renovar sin biometría); denegar 0, negativo, overflow y demasiado largo, tanto en DTO como store. Epoch monotónico de `finance_acl_state` no se recicla tras revoke o reissue.
- A4 `withMaterialAdmission` es necesario para SQL. Rechazar schema7/8, cerrado y active Apply; si una transacción falla, no devolver un record que pueda observarse como vigente.
- A5 después de devolver record, otra app/proceso puede revocar. `RealityFinance9ObservedSourceReadPort` y fence B3B1B1B0 deben revalidar. **Esto NO soluciona invalidación interproceso ni la limpieza de Compose**: B3B1B1B1B debe diseñarla aparte, con pruebas G15 posteriores.
- A6 no instalar este issuer en shipping product, ni extender M1C1B1C Apply. Separación observable en cero grants, scope READ sólo, sin UI.
- A7 tests nuevos real SQLite y regresiones dirigidas NO certificar hardware Android o firma real; Android host B3B1B1B1B STOP.
- A8 todo alcance limitado a dev stage, W2 STOP, V2 productiva OFF, no trabajo completado repetido.
**DICTAMEN:** PASS limitado al emisor SQL STAGED9 **EXACT4** y su test dirigido. No PASS para permiso de Finance UI ni Android trusted host. Con HEAD y guard documental PASS, READY exact4 apto para preflight. Revocación real/Android queda pendiente.

## B6
READY_TO_IMPLEMENT exclusivamente tras preflight de cada ruta en guard automatizada; cero código en este commit de documentación.

## B7
PENDING_FINAL_AUDIT de código SHA + 8 XML nuevos, 16 afectados, guard, diff 4/4, writer RELEASED; cierre no se extrapola a B3B1B1B completo.
