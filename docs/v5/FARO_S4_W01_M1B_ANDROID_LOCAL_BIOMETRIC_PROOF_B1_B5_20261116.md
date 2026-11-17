# FARO V5 — W01 M1B · presencia biométrica Android local y prueba criptográfica B1–B5
**Fecha AR:** 2026-11-16. **HEAD base:** `bda454ccb30206f51b69e32d415d79c762fd3efb`. **Unidad:** `S4_W01_M1B_ANDROID_LOCAL_BIOMETRIC_PROOF`. **Estado:** DOC_ONLY / B6 STOP hasta permiso READY independiente. W02 STOP.

## B1 — Evidencia real del HEAD
- M0C2B2B Finance9 FBS2v4 B7 PASS scoped `ebb8ae17414c7ce248730e53f5d43aa71891fdad`, 10/10 JUnit + regresiones; M1A SQLite9 ACL durable B7 PASS scoped `bda454ccb30206f51b69e32d415d79c762fd3efb`, 7/7 XML, 3 regresiones, guard 43998788724 SUCCESS. **No repetir**.
- `FaroV5AndroidSandboxPrincipal.kt` ya verifica UID de Application confiable, package, proceso no aislado ni SDK sandbox, pointer ID+sequence y epoch host con invalidación latch; 16 tests históricos. **Sólo evidencia técnica de sandbox, NO autorización humana ni Apply.**
- `S4W01B2FinanceObjectGrantOwner.kt` guarda nonces efímeros in-process, no asegura revocación desde otro proceso ni acredita identidad humana; `S4W01B2C0FinanceDevApplyHost.kt` es test-only.
- `v5/app/build.gradle.kts`: `androidTestImplementation(libs.androidx.biometric)`, pero falta `implementation(libs.androidx.biometric)` para código principal; existe versión central 1.1.0 en `v5/gradle/libs.versions.toml`. Android minSdk29/targetSdk36/compileSdk37. No hay host real de CryptoObject/Keystore Finance compuesto ni pruebas físicas G15 de esta unidad.
- M1C control SQL COMMIT entre **dos procesos reales**, M1B aceptación biométrica física y W01 B/C/D/E/F siguen OPEN; V2 productiva, Auto/Noche, W2 OFF.

## B2 — Reuso / V4.4 sin contaminación
- Reutilizar `FaroV5AndroidSandboxPrincipal` como prueba de UID sandbox, `ActiveGenerationPointerPort` para pointer+sequence, host epoch y contratos Finance APPROVED existentes; sin copiar permisos de V4.4 o sustituir el Review humano persistente.
- Mantener M1A como única autoridad SQL durable y M1C como único lugar futuro que revalide ACL hasta COMMIT. M1B **no expide** `finance_apply_grant`, no emite receipt, no escribe ledger, no instala Activity ni configura app habitual.
- El controlador de desafío debe ser puro/testable JVM y requiere un adaptador Android con AndroidKeyStore/biometría real; el fake sólo ensaya política, nunca prueba hardware o biometría.

## B3 — Investigación independiente y decisión
- Android Keystore `KeyGenParameterSpec.Builder.setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)` (API30+, API29: setUserAuthenticationValidityDurationSeconds(-1)), `setInvalidatedByBiometricEnrollment(true)` y `BiometricPrompt.CryptoObject(Signature)`: **ADOPTAR** firma ECDSA SHA256 auth-per-use, no prompt sólo decorativo. Ver https://developer.android.com/reference/android/security/keystore/KeyGenParameterSpec.Builder y https://developer.android.com/reference/androidx/biometric/BiometricPrompt
- Android `BiometricManager.canAuthenticate(BIOMETRIC_STRONG)`: **ADOPTAR** fail-closed si sensor fuerte no está disponible/configurado. CryptoObject no compatible con biometría weak en este flujo; no ofrecer credential fallback silencioso. https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.CryptoObject
- Alternativa flag `onAuthenticationSucceeded` sin firma de desafío: **DESCARTAR** (replay/confusión de alcance). Nonce externo/fecha sin firma o sesión en RAM como grant: **DESCARTAR**. `deviceCredential` para API29 con CryptoObject: **DESCARTAR** en esta unidad; posible diseño posterior separado. APIs pagas, servidores y export de llaves: **DESCARTAR**.
- Security caveat: BiometricPrompt demuestra autenticación local con factor inscrito, **no identifica una persona física inequívoca ni prueba consentimiento financiero informado**. M1C deberá vincular el Review concreto, identidad/principal local y ACL de generación bajo transacción; el prompt con resumen contextual aquí es prueba de presencia aislada.

## B4 — Unidad única, rutas exactas y criterios
Permiso futuro **CINCO** rutas, no extendible:
1. `v5/app/build.gradle.kts` (añadir dependencia AndroidX biometric a **main** sin modificar variantes/manifest).
2. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceLocalPrincipal.kt` (nuevo controlador in-memory one-shot con generación/puntero/sandbox/contexto + challenge criptográfico, no grants).
3. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceAuthHost.kt` (nuevo adaptador AndroidKeyStore + BiometricPrompt CryptoObject, **dormant**, sin composición de Activity).
4. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01FinanceLocalPrincipalTest.kt` (nuevo JVM test con fakes, **NO** certificación hardware).
5. `.github/workflows/faro-v5-s4-w01-m1b-local-biometric-directed.yml` (nuevo Android SDK37 JVM exact + `:app:compileV2CoreDevKotlin` o `:app:assembleV2CoreDev`, XML).
Criterios P01–P09: (1) desafío fresh con pointer/UID/review fingerprint; (2) sin sandbox/pointer/epoch rechaza antes de abrir prompt; (3) único desafío por instancia, nonce fuerte; (4) firma validada con clave **pinned AndroidKeyStore** tras CryptoObject, evidencia técnica sin grant; (5) fake firma y challenge ajenos/orden cruzado DENY + latch; (6) pointer ABA, cambio epoch, revocación sandbox, expiración DENY; (7) cancel/error/close falla cerrado; (8) API29/30+ limita STRONG y no fallback; (9) Gradle Android aislado compila, JUnit 0 fallos y manifest shipping no compuesto. XML de test JVM no prueba biometría física.
**M1B final requiere** campaña Android real (API35/device G15) con usuario presente y autorización separada; no llamarlo cerrado sólo por Kotlin/JVM.

## B5 — Auditoría adversarial y STOP
- La llamada a signer no puede ser un callback inyectable en producción que se acepte sin verificar firma contra PUBLIC KEY de alias AndroidKeystore, ni permitir cualquier clave pública enviada por usuario. Firma ocurrirá únicamente tras BiometricPrompt, con challenge que incluye `operationId`, `objectScope`, `planFingerprint`, `pointerSequence`, `sandboxSessionNonce`, `reviewSummary`, `nonce` y expiración.
- No completar por `onAuthenticationSucceeded` sin `Signature.sign()`; verificar ciphertext bytes y descartar evidencia si falla firma/revisión/pointer.
- No crear/rotar clave automáticamente si fue invalidada por cambio de biometría: bloquear y requerir rearme humano independiente.
- La prueba JVM fake podrá afirmar **solo challenge y política**, nunca biometría, Keystore hardware, respuesta de G15 ni identidad humana.
- `M1C` deberá revocar en el mismo ámbito SQL `BEGIN IMMEDIATE→COMMIT` que Apply/receipt y verificar interleaving entre **dos procesos OS reales**; no convertir M1B en una puerta productiva.
- No modificar `main`, V4.4, catálogo180, S2/S3, permisos de backup, datos del G15, X03 o conexión Groq; W02 STOP.

## B6
NOT_RUN. Publicar y verificar guard documental B1–B5, emitir READY separado con cinco rutas exactas y guard independiente, CAS writer para código y release ANTES de CI.

## B7
PENDING. Exigir compilación de `v2CoreDev`, JUnit XML casos P01–P09, guard SHA funcional y auditoría exact diff. Cerrar solo alcance de código biométrico **dormant**; M1B hardware/G15 y M1C productiva permanecen STOP.
