# FARO V5 — W01 M1C1B1C — B5 revisión adversarial independiente
Fecha 2026-11-17 Argentina; base verificada `1594962c2d16a41a2c0980a31bb6c3f1b6fefeb3`, guard preauditoría `44063976837` SUCCESS. Unidad `S4_W01_M1C1B1C_TRUSTED_LOCAL_GRANT_ADMISSION`. Este documento no emite permiso B6.
Referencias B1–B4: `docs/v5/FARO_S4_W01_M1C1B1C_ANDROID_TRUSTED_GRANT_ADMISSION_B1_B5_20261117.md` y código real.

## B1
- `S4W01FinanceAuthHost.requestAfterForegroundReview` SOLO produce evidencia tras `BiometricPrompt.CryptoObject`, objeto Signature idéntico a prepared, firma del digest de desafío, `S4W01FinanceLocalPrincipal.completeFromAuthenticatedCrypto` verifica contra certificate de alias local pinned; `finish` cierra estado y devuelve `S4W01FinanceLocalPresenceEvidence?`.
- Esa evidencia es **data class pública para el módulo app**; por sí sola **se puede fabricar**. `S4W01M1C1AReviewPresenceBinder.bindAfterTrustedPresence(context,presence)` verifica Workspace APPROVED canónico + scope exacto pero también admite fake en JVM. `Finance9TrustedStageGrant` no es emisor; sólo se consume por M1C1B1B B7 19/19.
- `RealityFinance9Acl.issueAfterTrustedStageAdmission` y `issueGrantAfterTrustedStageAdmission` son stage-only y no acreditan autenticación del caller; sus factories NO deben ingresar a shipping composition.
- Existencia real de `RealityStore.finance9TrustedStageGrantPortForDevOnly` demuestra deny schema7/8 y la revocación en COMMIT. Regresión S3 Home en último SHA `44061977693` SUCCESS.

## B2
Reuso exacto sin otro motor: `AndroidKeyStore` + per-use ECDSA M1B, `Workspace APPROVED` + binder M1C1A, `RealityFinance9Acl` epoch y single-use grant M1A/B0, COMMIT hook M1C1B1B. V4.4 AtomicApply/Undo/recovery se conservan. Ningún servidor o dependencia paga.

## B3 — Opciones frente al spoofing
A) **DESCARTAR:** exponer `issue(evidence: S4W01FinanceLocalPresenceEvidence)` o `issue(match)` fuera del host; ambos DTO son fabricables, aunque tengan hash de firma. 
B) **ADOPTAR DEV/STAGED9 acotado:** NUEVA operación únicamente en `S4W01FinanceAuthHost` que orquesta el mismo callback real de CryptoObject, obtiene bind APPROVED verdadero, deriva principal técnico de fingerprint del PUBLIC KEY del alias fijo + package/UID, valida pointer/host epoch/RESUMED y llama al port issuer dentro del callback. La API del caller **no acepta un evidence ni un match provisto por UI**. Generación de intent/grant a partir del digest de desafío y Review, y one-shot; store devuelve solo `Finance9TrustedStageGrant` tipado. Integración **NO se monta en ninguna Activity productiva**.
C) **ADOPTAR** para tests aislados: validador puro one-shot en `S4W01M1C1B1CTrustedGrantAdmission.kt` llamado por el host; en JUnit se pueden sintetizar entradas para probar reglas *sin afirmar presencia criptográfica real*. El límite de autoridad es el host Android, NO el DTO ni el validador inyectable.
D) **DEFERIR** autenticación física Moto G15 y root of trust de cualquier producto: la factory Reality9 issuer es accesible al código propietario de la app de test, así que DEV stage no demuestra resistencia a código malicioso dentro del mismo UID. Para producto hace falta trusted issuer admission **no exponible por API genérica** y verificar en hardware. 
Android primario: https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder y https://developer.android.com/reference/androidx/biometric/BiometricPrompt; SQLite https://www.sqlite.org/lang_transaction.html .

## B4 — Alcance B6 revisado y específico
Las siete rutas candidatas existentes son suficientes, **NO más**:
1 `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceAuthHost.kt`: añadir método DEV exclusivo `request...TrustedStageGrant`, flujo de callback real, key hash local. Preservar función M1B existente, fail-close y lifecycle.
2 `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01M1C1B1CTrustedGrantAdmission.kt`: NUEVO validador one-shot staged-only; no derecho de Apply.
3 `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedStageGrantIssuerPort.kt`: NUEVO DTO request/port *explicitly externally verified stage admission*, no DB handle ni auth independiente.
4 `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`: factoría de issuer SOLO Finance9/planning8/schema9; usa `RealityFinance9Acl` existente; `issuePolicy`+ `issueGrant`, revalidación de policy epoch; colisión/revocación → DENY, no grant y sin receipt.
5 `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01M1C1B1CTrustedGrantAdmissionTest.kt`: NUEVO 6 casos puros sin AndroidKeystore real.
6 `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedStageGrantIssuerPortTest.kt`: NUEVO 4 tests SQLite reales para stage9, persistencia, colisiones y schema7/8.
7 `.github/workflows/faro-v5-s4-w01-m1c1b1c-grant-admission-directed.yml`: NUEVO workflow CI JVM Android compile, XML 6 + 4, regresión M1C1B1B pertinente 19/19 sólo si cambia su API (no es necesario repetir íntegramente porque no cambia su implementación; compilar app ya la enlaza), S3 Home compile manifest si la modificación de host afecta a app.

## B5 — Riesgos y negativas antes de READY
- **PASS para desarrollar un puente DEV-STAGED9 SIN root of trust productivo**, NO PASS para poner un emisor confiable en producción. Esta distinción es obligatoria en nombres, comentarios, test y B7.
- Única ruta desde la UI hacia emisión DEV debe ser `S4W01FinanceAuthHost.request...TrustedStageGrant`, que NO acepta evidencia ya hecha; rechaza sin CryptoObject firmado, lifecycle RESUMED, UID/app, pointer + secuencia, epoch, Review APPROVED literal completo, vencimiento, alias/key inválidos. `S4W01M1C1AReviewPresenceBinder` sigue one-shot. No crear puente genérico Surface/Chat ni Intent/Service/IPC expuesto.
- PrincipalHash no debe ser hash del texto APPROVED, UID solo ni del nonce. Derivar del public key local pinned de Keystore + namespace + package/UID, y combinar scope/generación en la fila SQL. Public key derivada de alias fijo, nunca suministrada por caller. **No representa identidad humana**.
- Issue policy y grant son dos transacciones de ACL existente: si grant falla queda policy más reciente pero **ningún grant** nuevo; old grants quedan revocados por epoch. No afirmar atomicidad total de emisión. COMMIT de efectos permanece transacción única ya certificada.
- Duraciones <=30s grant, <=120s presencia, overflow DENY; evitar reiniciar grants al cambiar de Activity, restores, replays; secrets/no raw ECDSA private key/no tokens en backup o logs.
- Si DI del issuer de tests es falsificable, marcarlo TEST_POLICY_ONLY, no certificación seguridad del hardware. Sin Android biométrico G15 no certificar M1B físico ni shipping.
- B6 sólo después de guard documental, READY exact7 independiente, preflight; luego CI exact SHA, XML, B7 independiente. Writer global CAS y RELEASED antes de CI. W2 STOP.

## B6
NOT_RUN (revisión solo documental).
## B7
NOT_RUN. La guardia B1–B5 no autoriza Apply ni shipping.
