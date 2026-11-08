# FARO V5 S3.A4 — Review ↔ scope exacto ↔ grant lease; B1–B5
Fecha 2026-11-07 AR. Base exacta 8431a5231bcbe482033f5919a2fc62bcc382714c. Unidad S3_A4_N033C2B4_REVIEW_GRANT_LEASE_BRIDGE.
Resultado de este diseño: READY_SCOPED_IN_MEMORY_BRIDGE_ONLY. N033C2B4_TRANSACTIONAL_PLANNING_ACL productivo continúa STOP. V2 prod/S4/Auto/Noche OFF, catálogo172, G15 S3.A5 NOT_RUN.

## B1 — Evidencia y brecha binaria
- HEAD y writer RELEASED epoch490 verificadas; se adquirió CAS epoch491. Permiso global BLOCKED. Último B7 GrantCommitLease: docs/s3-night/S3_A4_N033C2B4_GRANT_COMMIT_LEASE_B7_PASS_20261107.md, owner 18/18, code 74c4a5ae legacy workflow XML 44/44, guard SUCCESS. No repetir.
- FaroV5Planning8ApprovedObjectGate actualmente retorna Boolean: verifica Workspace Review APPROVED, plan canónico 1 nodo, descriptor completo, payload exacto, ACL scoped y nonce, pointer durable, Review re-leído. Pero un host con grants de A y B podría usar autorización de Review A junto con lease B si no fija el scope derivado del plan.
- FaroV5Planning8ObjectGrantOwner.withCurrentGrantLease mantiene monitor de grants durante callback, bloqueando revoke/revokeAll/close de otro hilo en el mismo owner/proceso. NO autentica principal ni policy real, y no une el scope del callback al Review.
- Aceptación: extracción del scope canónico desde EL MISMO algoritmo del guard; bridge niega antes y dentro del lease si scope!=review scope, nonce incorrecto, grant ausente, Review Discard, pointer ABA o host no listo. Callback se ejecuta exactamente una vez solamente tras segunda verificación. Prueba de revoke concurrente esperando al callback, excepción libera monitor. Siete tests nuevos + 18 owner + 14 gate +10 Search +2 Chat = 51/51 JUnit XML sin skip, guard y V2 regresión.

## B2 — Comparación V4.4, reutilización
- V4.4 separa Rule y MonthlyPlan por dominio pero NO suministra Review+grant V5 ni política de cuentas portable. No copiar gateway, datos de tests ni UID de V4.
- Reusar Workspace TrustedConversationApplyReviewPort, PreparedPlanEnvelopeCodec, descriptor/payload allowlist del guard, pointer durable, existing GrantOwner monitor y su lease. NO nuevo executor/tabla/scheduler/catálogo, no cambios a Reality ni HostCertification.
- DESCARTAR un preflight Boolean más un grant de cualquier scope por confusión de objetos; DESCARTAR duplicar el validador de Review en el puente (drift); DESCARTAR MaterialWriteGate.readLock como exclusión de revocación. ELEGIR extraer scope dentro del guard y usar lease existente.

## B3 — Investigación independiente y riesgo
- OWASP Authorization https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html: deny-by-default, revisar cada operación y objeto, ADAPTAR scope exacto y rechecks.
- OWASP Authorization Patterns https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Patterns_Cheat_Sheet.html: PEP próximo a mutación y policy independiente, REFERENCIA para futuro COMMIT real; este puente no es PEP productivo.
- Java synchronized JLS17 https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html: monitor reentrante y exclusión de otros hilos, REUSAR. SQLite https://www.sqlite.org/lang_transaction.html: BEGIN IMMEDIATE de Reality no serializa ACL/Workspace de otros procesos.
- Hipótesis: grants A/B simultáneos NO autorizan acción B si Review A, Discard/ABA/revoke antes lease niega, revoke concurrente espera al callback, excepción no deja monitor bloqueado. Ninguna API paga ni código externo copiado.

## B4 — Delta cuatro paths exactos
1. v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5Planning8ApprovedObjectGate.kt: convertir Boolean existente en currentApprovedObjectScopeOrNull(context), sin duplicar validación, y conservar hasCurrentApprovedObjectReview = (scope != null). Mismos controles de Review, descriptor, ACL y pointer.
2. v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5Planning8ReviewGrantLeaseBridge.kt: nuevo internal bridge withReviewedGrantLease(context,expectedScope,expectedNonce,action), primer preflight de scope+pointer+host, y segundo chequeo DENTRO de grantOwner.withCurrentGrantLease, ejecutar action una vez. Denied tipado y excepciones propagadas. No asumir action COMMIT si caller no lo hace.
3. v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5Planning8ReviewGrantLeaseBridgeT2Test.kt: siete casos con Workspace durable, Review approved/discard, pointer real, grant owner y policy solo fixture CLARAMENTE no productiva; permisos y carreras; NO escritura Reality ni receipt fabricado.
4. .github/workflows/faro-v5-s3-a4-n033c2b4-review-grant-lease-directed.yml: JVM :app:testDebugUnitTest clases exactas 7+18+14+10+2 = 51/51 XML. Guard de protocolo y regresión de V2 por cambio a app.

## B5 — Auditoría adversarial y autoridad
- PASS máximo: SCOPED internal single-process bridge, NO productivo. Ningún permiso verdadero se emite sin fuente de política real (actualmente fixture). Principal UID técnico no es usuario, Review aprobado no es ACL en sí, Search grants no dan Reality writes.
- Lock order: grant monitor -> consulta canónica de Workspace y ACL reentrante -> callback futuro sin suspensión; no tomar Reality mutex antes que grant monitor en otro flujo. Revocación desde otro hilo por este owner espera; policy externa/pointer cross-process NO queda bloqueada por este monitor. Probar race local, no fingir transacción cross-store.
- NO re-chequear y devolver Denied DESPUÉS de callback porque podría haber COMMIT; la verificación final para el COMMIT real pertenece a nuevo B1–B5 con ApplyTransactionExecutor y durable receipt. Discard antes de BeginApply compite con Workspace CAS, no deja aplicar Review descartado.
- B5 READY exclusivamente cuatro paths listados, sin habilitar S3_A4_N033C2B4_TRANSACTIONAL_PLANNING_ACL ni Shipping V2, sin main/V4.4/S2/S4, sin egress ni pruebas G15. Si falla CI: B7 RED_FIX antes del siguiente commit funcional.
