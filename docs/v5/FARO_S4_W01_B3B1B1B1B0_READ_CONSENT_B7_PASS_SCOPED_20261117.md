# FARO V5 — W01 B3B1B1B1B0: B7 PASS scoped stage DEV read consent
Fecha AR 2026-11-17. Unidad S4_W01_B3B1B1B1B0_ANDROID_READ_CONSENT_STAGE_DEV. Código bb20a48266f69ded178f5e9c806c8127149fdfe9. No es una autorización de Finanzas productiva ni una prueba física de G15.

## B1 — Preauditoría y READY exactos
Preauditoría parent B1-B5 docs/v5/FARO_S4_W01_B3B1B1B1B_TRUSTED_ANDROID_READ_HOST_B1_B5_20261117.md blob 72e5cdaf66dc823993c386cfb44ebc72d613a98f, guard 44115102817 SUCCESS.
B5 separado + READY exact4 docs/v5/FARO_S4_W01_B3B1B1B1B0_READ_CONSENT_INDEPENDENT_B5_READY_20261117.md blob fe9466b35f2584b2cdf999e03497ab9b9ded4de3, guard 44116707550 SUCCESS, preflight FARO_PROTOCOL_PREFLIGHT_PASS S4_W01_B3B1B1B1B0_ANDROID_READ_CONSENT_STAGE_DEV 33f034177daa6ec47b3345cdb1527861fcf75032. El issuer SQL B3B1B1B1A B7 PASS scoped, code 8a4d79ca3a27e5d312345df0a1c50503749487e0, 23/23 CI y checkpoint 14747d00d3e56bd9c335b5f44775c39eaa08c42b; no repetir.

## B2 — Diff B6 contra READY
Código commit bb20a48266f69ded178f5e9c806c8127149fdfe9, FARO-UNIT:S4_W01_B3B1B1B1B0_ANDROID_READ_CONSENT_STAGE_DEV presente y archivos cambiados EXACTAMENTE cuatro:
1. v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadConsentScope.kt
2. v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadAuthHost.kt
3. v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01FinanceReadAuthPolicyTest.kt
4. .github/workflows/faro-v5-s4-w01-b3b1b1b1b0-read-auth-directed.yml
No edits a RealityStore/SQL/ACL, schema, FBS2v4, Apply, receipts, main/V4.4/S2/S3, Manifest, Navigation, CompositionRoot, Chat, Surface ni V2 productiva. Writer RELEASED epoch1106 ANTES de CI.

## B3 — Implementación DEV aislada
S4W01FinanceReadConsentGate crea challenge one-shot domain-separated READ finance.snapshot.read/finance:all y texto explícito “Consultar mis cuentas y saldos” con nonce32, sandbox sessionNonce, Application ID/UID, full pointer generationId+sequence, hostEpoch, issuedAt y expiry 120s. Verifica challenge, firma, sandbox/host/pointer y principal derivado de llave con forma hex64 antes y después del issuer. Sólo solicita el record typed de política SQL READ con TTL máximo 30s; no produce saldo, token de render, Apply grant ni receipts.
S4W01FinanceReadAuthHost permanece internal, DORMANT y no montado. Su código Android exige FragmentActivity RESUMED, main thread, BiometricManager BIOMETRIC_STRONG y BiometricPrompt.CryptoObject con EXACTAMENTE la misma Signature preparada, autenticada por AndroidKeyStore EC ECDSA con alias READ separado del alias Apply. El callback verifica Signature con la clave pública del alias fijo y obtiene principal técnico UID+certificado. Cancel/close/error fallan cerrados sin retry del mismo challenge. Un UID no representa una identidad humana.
Cualquier caller Kotlin DEV que tenga acceso a los callbacks del pure gate puede falsificar una firma/test: la autorización no es todavía productiva.

## B4 — CI exacta
CI dirigida 44117580437, job 134159773245, COMPLETED SUCCESS en SHA bb20a48266f69ded178f5e9c806c8127149fdfe9. :app:testDebugUnitTest 8 casos nominados r01-r08, 8/8 PASS, 0 failures/errors/skips; :app:compileV2CoreDevKotlin BUILD SUCCESSFUL; verificador de XML exact 8 + NO shipping wiring SUCCESS. Log W01_B3B1B1B1B0_JVM_READ_CONSENT_8_OF_8_PASS. Artefacto faro-v5-w01-b3b1b1b1b0-read-auth-junit ID 14342669514.
Las **5/5 GitHub Actions** sobre SHA bb20a48266f69ded178f5e9c806c8127149fdfe9 COMPLETED SUCCESS:
- Protocolo guard 44117580111
- Host read consent 44117580437
- S3 Home 44117580131
- M5 APK DEV aislada 44117580061
- V2 core M1/M2 44117580110.
Cero workflow rojo o pendiente, sin reruns de certificaciones anteriores. No se ejecutaron biometría de hardware ni instalación G15 por esta unidad.

## B5 — Auditoría residual
PASS sólo en DEV: un challenge READ no equivale a Apply, alias distinto, one-shot, nonce, scope fijo, pointer/UID/epoch/reloj, falta de evidencia, tampering/replay/issuer mismatch o exception fail-closed. Firma y SQL issuer se ejercitan en tests JVM sintéticos y código Android compila.
NO CERTIFICADO: presencia biométrica física, validez de llaves reales G15, consentimiento humano informado, issuer seguro contra código arbitrario dentro del mismo UID, protección del saldo luego de revocación remota, eventos cross-process, lifecycle Compose, ni presentación productiva. Firma dentro de callback y emisión SQL en host DEV no es una lease de render. La integración entre procesos B3B1B1B1B1 y la campaña B3B2 G15 continúan STOP/NOT_RUN. W1 OPEN, W2 STOP, V2 productiva OFF, Auto/Noche OFF, sin pagos ni APIs pagas.

## B6
IMPLEMENTED bb20a48266f69ded178f5e9c806c8127149fdfe9, 8 nuevos JUnit PASS, Android DEV compila, 5/5 workflows SUCCESS.

## B7
**PASS_SCOPED_DEV_READ_BIOMETRIC_CODE_8_JVM_5_CI_NO_G15**. Consumir permiso READY a BLOCKED, registrar last_code_review y checkpoint; confirmar writer RELEASED y guard del commit documental antes de cerrar turno.
