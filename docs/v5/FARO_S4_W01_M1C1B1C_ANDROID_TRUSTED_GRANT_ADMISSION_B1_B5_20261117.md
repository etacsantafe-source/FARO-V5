# FARO V5 — W01 M1C1B1C · Emisión de grant vinculada al host Android confiable · B1–B5
Fecha: 2026-11-17 Argentina. HEAD auditado `2bf2ea30b1324e00b88794e73d76388711c1d04c`; writer CAS epoch1050. Unidad candidata `S4_W01_M1C1B1C_TRUSTED_LOCAL_GRANT_ADMISSION`.
ESTADO **DOC_ONLY, NO READY**, sin código ni pruebas nuevas. V2 productiva OFF, W2 STOP.

## B1 — Evidencia vigente y brecha falsable
- M1B `S4W01FinanceAuthHost.kt`: AndroidKeyStore ECDSA, autenticación STRONG por uso, BiometricPrompt.CryptoObject(Signature); `S4W01FinanceLocalPrincipal.completeFromAuthenticatedCrypto` verifica firma del desafío antes de devolver `S4W01FinanceLocalPresenceEvidence`. B7 PASS acotado 9/9 JUnit y APK V2 DEV; hardware Moto G15 NO probado.
- M1C1A `S4W01M1C1AReviewPresenceBinder.kt`: binder one-shot asocia Review Workspace APPROVED canónico a la evidencia anterior, con UID, pointer+sequence y host epoch; 9/9 JUnit PASS acotado. El binder recibe una data class y los tests pueden FABRICARLA; no puede acreditar el origen biométrico independientemente.
- M1A/M1C0: `RealityFinance9Acl.kt` policy_epoch global monotónico, revocación SQL interproceso y `RealityStore.finance9AclCurrentInsideApplyTransaction` bajo BEGIN IMMEDIATE; pruebas anteriores certificadas, no repetir.
- M1C1B0: `RealityFinance9Acl.issueAfterTrustedStageAdmission` y `issueGrantAfterTrustedStageAdmission` crean policy/grant durable únicamente tras admisión EXTERNA hipotética; consumo vinculado a receipt real probado con 8/8 JVM; no autentican un humano.
- M1C1B1B: B7 PASS scoped checkpoint `2bf2ea30b1324e00b88794e73d76388711c1d04c`; final code `a52dbb3cea2210f84b66893f30a5450672bef235`, CI dirigida `44061977799` SUCCESS 19/19 XML, regresión S3 `44061977693` SUCCESS, guard final `44063372055` SUCCESS, writer RELEASED1049. `RealityFinance9TrustedStageGrantPort` sólo CONSUME grant ya existente; **no lo emite ni comprueba identidad humana**. NO repetir B6/CI.
- **Brecha exacta:** no hay flujo sellado del callback CryptoObject firmado -> revisión APPROVED exacta -> principal técnico estable de instalación -> emisión de `finance_acl_policy` y `finance_apply_grant` ligada a generación/fingerprint/intento, entregado al gate post-receipt. Tampoco prueba física G15 de la clave. Se conserva DENY productivo hasta que ambos existan.

## B2 — Comparación V4.4 y V5 existente
- V4.4 AtomicApply, receipts, PREPARED/COMMITTED, startup recovery y Undo son canónicos; no copiar ni migrar consentimientos/permisos. V5 ya tiene los owners de Review, sandbox principal, firma Keystore, SQL ACL mono-epoch, idempotencia y finalCommitGate. Reusar todo sin duplicar motor ni tabla ni crear executor paralelo.
- El `S4W01B2C0FinanceDevApplyHost` usa policy/grant simulados y continúa inhabilitado para shipping. `S4W01FinanceLocalPresenceEvidence` es evidencia técnica, no grant ni principal portable. La mera etiqueta APPROVED de Workspace no otorga capacidad de Apply.
- Alias Keystore pertenece al UID Android local, no identifica a una persona singular; una nueva instalación, invalidación biométrica o restore requiere nuevo consentimiento/grant. No exportar claves ni grants en FBS2.

## B3 — Investigación y alternativas con fuentes primarias
- ANDROID: `BiometricPrompt.CryptoObject` desbloquea claves auth-per-use, atadas a un criptograma concreto y no a un callback booleano: https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.CryptoObject. ADOPTAR firma ECDSA per-use, Strong, verifica key pinned y challenge ligado a scope antes de emitir capacidad.
- ANDROID: https://developer.android.com/identity/sign-in/biometric-auth describe la autenticación por uso, y https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt exige Strong para operaciones Keystore CryptoObject. ADOPTAR callback de host foreground/RESUMED, cancelación y latch one-shot; denegar fallback weak/credencial automática.
- SQLITE: https://www.sqlite.org/lang_transaction.html y https://www.sqlite.org/isolation.html; ADOPTAR serialización de policy/grant en SQLite `BEGIN IMMEDIATE` y consumo posterior con receipt en la MISMA transacción Apply. Denegar revocación ganadora del lock; usar epoch que no se recicla. No confiar en Kotlin mutex entre PID.
- RECHAZAR: `S4W01FinanceLocalPresenceEvidence` construible por un caller como proof de emisión; exponer un `Finance9TrustedStageGrant` desde Activity Intent, transferir permiso desde Chat, confiar en `onAuthenticationSucceeded` sin firma, usar public key suministrada por cliente, reabrir clave invalidada silenciosamente, publicar grant en backup o crear transacción de efectos separada.
- SIN DEPENDENCIA NUEVA: Kotlin, AndroidX Biometric existente y SQLite ya están en el proyecto; no API paga, red, servidor ni librería externa de autenticación.

## B4 — Unidad mínima unificada; no permiso todavía
Diseñar **host de desarrollo STAGED9**, no shipping. El callback de `S4W01FinanceAuthHost`, SOLO después de firma CryptoObject verificada, debe exponer a un consumidor interno único una prueba opaca, de una sola instancia/uso, no serializable/no exportable, vinculada a Review exacta, sandbox UID, key pública instalada, pointer+sequence, host epoch y vencimiento. El bridge Android no aceptará desde Surface un DTO `S4W01FinanceLocalPresenceEvidence` como autoridad por sí solo. El principalHash será hash de identidad técnica estable de clave local verificada más contexto instalación; no afirmar identidad de persona. Si no se puede demostrar origen de callback en B5 => DENY y NO READY.
Reality9 ofrecerá emisión STAGED-only estrecha, sin dar handle SQLite ni exponer la clase internal `Finance9AclScope`, reusando issue policy/grant y epoch monotónico. Policy/grant se cotejan con fingerprint, opId, objectScope, generación, secuencia y nonce/intento; solo emisión exact once; ante error no hay grant utilizable. El issuer no ejecuta Apply, su consumo posterior lo hace el gate M1C1B1B ya certificado.

Rutas candidatas (NO autorizadas todavía; revisar mapa de dependencias B5 antes del READY):
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceAuthHost.kt` (trusted callback con verificación Keystore interna; sin Activity wiring).
2. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01M1C1B1CTrustedGrantAdmission.kt` (NEW, lease opaco single-use/no export).
3. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedStageGrantIssuerPort.kt` (NEW, API estrecha test/staged).
4. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt` (factoría STAGED9, no schema7/8).
5. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01M1C1B1CTrustedGrantAdmissionTest.kt` (NEW, JVM scoped).
6. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedStageGrantIssuerPortTest.kt` (NEW, JUnit SQLite).
7. `.github/workflows/faro-v5-s4-w01-m1c1b1c-grant-admission-directed.yml` (NEW, Android compile DEV, JUnit exact).
NO tocar `S4W01FinanceLocalPrincipal.kt` salvo evidencia de necesidad; requeriría preauditoría nueva de ruta. No tocar product Graph/Activities, manifest, catalog180, UI, main, V4.4, S3, W2.

Casos de aceptación futuros `I01–I10` (TODOS NOT_RUN):
I01 callback trusted validado produce admisión única; I02 DTO sintético sin origen del host DENY; I03 reused/cancelled/closed/expired DENY; I04 challenge/firma/fingerprint/Review inconsistente DENY; I05 UID/alias/host epoch/pointer ABA DENY; I06 policy/grant SQL persistentes con época monotónica, sin clave ni firma persistida; I07 revocación/Regrant no revive grant viejo; I08 schema7/8, restore o foreign generation DENY; I09 conflicto intent/grant UNIQUE no produce segunda autorización; I10 ningún binding productivo/Manifest ni acceso Internet/Auto/Noche. La compilación DEV y XML no acreditan hardware G15.

## B5 — Auditoría adversarial y decisión de salida
- **Riesgo principal**: la evidencia M1B actual es data class. Un objeto Kotlin con hashes de firma no puede constituir proof criptográfico del callback para emitir ACL. Antes de READY demostrar que el host retiene el control del token y que se verifica firma en AndroidKeyStore pinned alias real; nunca aceptar proveedor de firma arbitrario en shipping. Los tests usan fake sólo para política; no certificar CryptoObject físico.
- Evitar un mismo tipo público que transporte permiso al Chat o al backup; ningún campo `secret`, firma cruda, credencial biométrica o llave privada en tablas. Si el único camino es entregar data class libre al issuer, B5 STOP y rediseñar seam antes de B6.
- La secuencia policy→grant debe estar protegida de revoke entre transacciones por epoch recheck; si se requiere atomicidad más fuerte, definir otra unidad que no duplique motor. Consumo final siempre valida epoch nuevamente dentro del BEGIN IMMEDIATE de Apply.
- Un proceso sin host READY, AndroidKeystore invalidado, pantalla no RESUMED, sensor no STRONG o Review alterado no emite grant. No autenticar un principal humano sin evidencia física; solo `local technical principal` en DEV.
- Pruebas G15 Android real siguen requeridas en aceptación final, no ejecutar ahora ni modificar teléfono. W1 B/C/D/E/F OPEN; W2 STOP, V2 productiva OFF, Auto/Noche OFF.
- **NO READY en este checkpoint**: revisar concretamente el API de callback opaco, empaquetado de pruebas, y dependencia entre `S4W01FinanceAuthHost` y el módulo Reality para asegurar que no se crea un bypass. Permiso individual sólo luego de guard documental, revisión independiente del B5 y rutas exactas.

## B6
NOT_RUN. Ningún commit funcional habilitado. Writer RELEASED tras documento/continuity.

## B7
PENDING. B7 de esta unidad exigirá diff exact7, CI SHA/XML, regresiones afectadas y writer RELEASED; no certificar biometría física sin G15.
