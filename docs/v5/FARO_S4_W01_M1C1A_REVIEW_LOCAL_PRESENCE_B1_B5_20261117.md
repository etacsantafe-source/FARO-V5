# FARO V5 — W01 M1C1A · Revisión canónica + presencia local: B1–B5
2026-11-17 AR. HEAD verificado `12c644f6693e5ec782796d2deb61f9ac0081c1c9`, writer global adquirido epoch1023. Unidad `S4_W01_M1C1A_CANONICAL_REVIEW_LOCAL_PRESENCE_BINDING`. **DOC_ONLY**; B6 NOT_RUN; W2 STOP.

## B1 — Evidencia actual, sin repetir lo certificado
- M1B final `be99fe41e7895eb0e5dac1e3b16197c1821eaf14`: AndroidKeyStore BiometricPrompt CryptoObject dormido; `S4W01FinanceLocalPrincipal.kt` emite `S4W01FinanceLocalPresenceEvidence` con operación, objeto, fingerprint, texto revisado, firma/challenge digest, UID app y pointer; JUnit 9/9 y APK DEV construida. **No** biometría física G15.
- M1C0 final `12c644f6693e5ec782796d2deb61f9ac0081c1c9` B7 scoped SQLite9: `RealityStore.finance9AclCurrentInsideApplyTransaction` comprueba policy epoch dentro de BEGIN IMMEDIATE con mismo scope; 8/8, 4 pares PID del SO, regresiones G4/receipt 2/2. **No** Apply real/productivo ni grant humano.
- `S4W01B2FinanceApprovedScopeReader` existente deriva operación/objeto/fingerprint sólo de `TrustedConversationApplyReviewPort` con Workspace durable, catálago oficial, revisión APPROVED y envelope canónico; doble lectura contra cambio de revisión/generación/catálogo. `ConversationPlanReviewSnapshot.reviewText` admite 4.000 chars, mientras subtítulo biométrico M1B admite máximo 120, por lo que la transición debe ser exacta/fail-closed hasta definir un resumen canónico explícito en otro bloque.
- `S4W01B2C0FinanceDevApplyHost` SIMULATED DEV y `S4W01B2FinanceObjectGrantOwner` in-process **NO** autentican humanos ni serializan revocaciones crossprocess. No reutilizarlos como autoridad humana.
- El permiso global está BLOCKED tras M1C0 B7. No existe owner/puente durable Review↔prueba biométrica↔ACL hasta COMMIT.

## B2 — Reutilización y V4.4
- Mantener `S4W01B2FinanceApprovedScopeReader`, Workspace APPROVED, envelope/fingerprint, pointer generation+sequence, `S4W01FinanceLocalPresenceEvidence`, `AndroidSandboxTechnicalEvidence`, M1A/M1C0: **no reinventar** preparación de plan, CAS ni SQLite.
- V4.4 UI/menú se recuperará en W01 B/F; no importar grants ni consentimientos de V4.4. No cambiar manifests/Activities ni shipping V2. No gastar APIs.

## B3 — Alternativas y criterio
- A **ADOPTAR**: binder interno, one-shot, previo a SQL; compara el scope APPROVED real con `evidence.scope` exacto (`operationId`, `objectScope`, fingerprint, `reviewedSummary == review.reviewText`), y además app UID/package, pointer+sequence, host epoch, expiración, hashes de firma y challenge. Dos lecturas del Workspace y del Reader; verificar que el envelope es idéntico byte a byte. No inventa user principal.
- B **RECHAZAR**: usar `approved=true` desde Compose/Intent o Workspace como grant; aceptar un resumen visual diferente o truncado, aceptar sólo UID, firma dummy como identidad, usar timestamp en lugar de policyEpoch SQL. La evidencia M1B se verifica sólo dentro de AndroidAuthHost tras CryptoObject, **el binder no prueba su procedencia criptográfica de forma autónoma**.
- C **DEFERIR** a M1C1B: derivación de principal estable desde alias Keystore y prueba de revalidación del key, grant SQL único ligado a intento + consumo con receipt en la **misma** transacción Apply y proceso Android real. Dado que M1C1A no puede certificar biometría física, no autorizar Apply por su resultado.

## B4 — Unidad técnica y pruebas esperadas
Permiso futuro exactamente TRES rutas **nuevas** (ninguna anterior tocada):
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01M1C1AReviewPresenceBinder.kt`
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01M1C1AReviewPresenceBinderTest.kt`
- `.github/workflows/faro-v5-s4-w01-m1c1a-review-presence-directed.yml`
Un `S4W01M1C1AReviewPresenceBinder` que solamente recibe evidencia M1B ya verificada por callback de host confiable y la contrasta contra un `TrustedConversationApplyReviewPort` y `S4W01B2FinanceApprovedScopeReadPort` reales; devuelve **binding informativo de vida corta**, sin principalHash, policy epoch, grant, receipt, DDL o Apply. El mismo binder no acepta dos llamadas, incluso si la primera falla. `close()` lo invalida.
Casos dirigidos 9/9 JUnit JVM:
T01 APPROVED+misma evidencia y misma generación PASS informativo,
T02 intento doble/replay del mismo binder DENY,
T03 scope de otra operación/objeto DENY,
T04 fingerprint cambiado DENY,
T05 texto de Review diferente/truncado/largo DENY,
T06 UID/app falsos DENY,
T07 puntero ABA y epoch host/lifecycle DENY,
T08 expiración, firma/challenge inválidos, cierre DENY,
T09 Workspace cambiado entre lecturas, Review pendiente/discarded DENY.
CI `:app:testDebugUnitTest --tests ...S4W01M1C1AReviewPresenceBinderTest`, XML exact9, compilación DEV `:app:compileV2CoreDevKotlin` (no APK redundante). B7 solo etapa JVM+compile, no físico Android.

## B5 — Auditoría adversarial, puertas
- Si una prueba JVM construye `S4W01FinanceLocalPresenceEvidence` sintética, demuestra sólo **binding estructural**, nunca origen Android Keystore ni intención humana, por lo tanto resultado no es usable como ticket productivo.
- Evitar retornar `authorityToken`/secretos o callback de autorización desde el DTO; fingerprint hash y scope tipado únicamente. Cambio de Workspace, pointer ABA, expiración, host suspendido/UID ajeno deniega.
- El subtítulo de 120 caracteres exactos limita W1; no truncar silenciosamente. M1C1B deberá exhibir texto aprobado completo y obtener consentimiento explícito en el dispositivo antes de grant.
- No tocar `main`, V4.4, schema7/8, S2/S3, fondo/Auto/Noche, IA/X03, API keys, teléfono ni W2. V2 productiva OFF. No crear dinero real ni transferencia bancaria.
- B1–B5 fuente autoexplicativa; prohibido ejecutar B6 hasta guard documental SUCCESS, independiente READY exact3 y preflight después de RELEASE writer. B7 PASS solo con XML real, guard code SHA y writer RELEASED.

## B6
NOT_RUN. Emitir READY distinto al aprobar guard de esta preauditoría.

## B7
PENDING. No extender PASS M1B/M1C0 a este binder ni a M1C1 global.
