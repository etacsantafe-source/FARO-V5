# FARO V5 W01 B3B1B1B1B0 — B5 independiente y READY exact4, host lectura Android DEV
Fecha AR 2026-11-17, base HEAD 14747d00d3e56bd9c335b5f44775c39eaa08c42b. Unidad S4_W01_B3B1B1B1B0_ANDROID_READ_CONSENT_STAGE_DEV. Exclusivamente DEV/STAGED9, no shipping.
Preauditoría madre docs/v5/FARO_S4_W01_B3B1B1B1B_TRUSTED_ANDROID_READ_HOST_B1_B5_20261117.md blob 72e5cdaf66dc823993c386cfb44ebc72d613a98f, guard 44115102817 SUCCESS.
Subunidad previa B3B1B1B1A B7 PASS scoped: código 8a4d79ca3a27e5d312345df0a1c50503749487e0, 8+16 JUnit PASS, 23/23 Actions SUCCESS, B7 doc docs/v5/FARO_S4_W01_B3B1B1B1A_READ_POLICY_ISSUER_B7_PASS_SCOPED_20261117.md blob 34a330f5c91f5f5addcc3b8dccefd3a2abdb9af8, guard 44115934095 SUCCESS. No repetir.

## B1
Evidence exact HEAD: S4W01FinanceAuthHost.kt M1B uses a real AndroidKeyStore EC auth-per-use via AndroidX BiometricPrompt.CryptoObject on a FragmentActivity RESUMED. Existing S4W01FinanceLocalPrincipal.kt signs a canonical Apply review fingerprint; cannot reuse as read consent. FaroV5AndroidSandboxPrincipal.kt exposes local Application-UID technical evidence, full ActiveGenerationPointer and host epoch, not human identity. Finance9TrustedReadPolicyIssueRequest from B3B1B1B1A accepts principalHash, generation and clock, scope fixed finance.snapshot.read/finance:all; pure SQL issuer does NOT authenticate caller. Shipping AndroidManifest has no Finance provider, UI port is DORMANT.

## B2
Reuse existing dependencies AndroidX biometric, FragmentActivity/Keystore, sandbox evidence, pointer and stage9 SQL issuer. Build a NEW domain-separated READ challenge and a different Keystore alias so a signature captured for Apply cannot be accepted for reading. Do not add manifest provider, interface FinanceReadPort, Compose/Activity/Navigation wiring or modify M1B reviewed Apply path. Preserve V4.4 writer, receipts, idempotence, FBS2v4, main/S2/S3, W2 STOP, V2 productiva OFF.

## B3
A accepted: isolate a new READ-only internal gate using challenge SHA256 over domain/version, 32-byte SecureRandom nonce, appId/UID, sandbox sessionNonce, full pointer generationId+sequence, host epoch, issued/expires and fixed human-readable consent summary; a specific AndroidKeyStore alias unlocked by BIOMETRIC_STRONG prompt signs the digest. Verify with fixed alias public certificate, NEVER caller-provided key or boolean. After real host callback only, gate issues typed SQL READ policy with <=30-second expiry; response is an informational epoch, no Apply grant and no UI data.
B rejected: use existing M1C1B1C stage grant, fake ApplyPlan fingerprint, voice/AI consent, plain Boolean or unconditional read enrollment.
Android docs: https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.CryptoObject ; https://developer.android.com/reference/android/content/ContentResolver ; https://developer.android.com/topic/libraries/architecture/views/coroutines-views . No new deps or paid API.

## B4
READY exact FOUR paths:
1. v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadConsentScope.kt NEW: internal S4W01FinanceReadChallenge and S4W01FinanceReadConsentGate in the app. Does not depend on Android UI. One-shot beginAfterExplicitReadRequest / completeFromAuthenticatedCrypto. Inject sandboxEvidence, verifyTrustedSignature, principalHashOrNull for DEV JVM tests (not real auth); default SecureRandom. Validate full pointer and app UID+session evidence+epoch repeatedly, challenge matches exactly, signature valid, time 120s max, stage READ policy only TTL <=30s, response identity/epoch and no retry. Never expose Surface port.
2. v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadAuthHost.kt NEW: internal S4W01FinanceReadAuthHost, FragmentActivity RESUMED+main thread, BiometricManager.BIOMETRIC_STRONG, distinct fixed Keystore alias, Signature challenge via CryptoObject, reject absent/wrong callback signature, key invalidation, changed lifecycle or cancel; calls the gate only inside callback, closes/cancels on error. NO Activity shipping wiring; no Apply.
3. v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01FinanceReadAuthPolicyTest.kt NEW: 8 JUnit JVM tests r01–r08 with LOCAL fake Java-EC signer and fake read-policy issuer; success one-shot, replay, wrong digest/signature, pointer ABA, lifecycle epoch/UID/session drift, expiration/rewind, foreign/exception issuer response, close/cancel. No real Android biometrics in JVM.
4. .github/workflows/faro-v5-s4-w01-b3b1b1b1b0-read-auth-directed.yml NEW workflow directed :app:testDebugUnitTest --tests S4W01FinanceReadAuthPolicyTest + :app:compileV2CoreDevKotlin, verify eight named XML cases, 0 fail/skip, zero new usage in CompositionRoot/Shell/Activity. Guard + artifact JUnit.
FARO-UNIT exact on code commit and preflight four paths after this doc guard.

## B5
Independent adversarial verdict:
- Test-supplied sandbox evidence, principal hash, verifyTrustedSignature lambda and issuer are for policy tests only. A JVM PASS cannot attest AndroidKeyStore, actual biometric prompt, unique human or product reader.
- Host must be only real invoker: it must obtain CryptoObject.Signature from AuthenticationResult and prove object identity to the original prepared operation; no boolean fallback, weak biometric, password fallback or use of static principal as user consent.
- New READ alias/domain cannot validate Apply signatures or grants; fixed operation/scope and summary, nonce, timestamp, generation+sequence, Application.uid and hostEpoch in challenge; replay and ABA deny.
- Failure before/after issuing SQL may leave a READ policy with short TTL; without cross-process notifications it cannot be treated as a shippable right to SHOW balances. B3B1B1B1B1 publisher/revoke and B3B2 physical G15 remain STOP.
- If a key is invalidated, never silently regenerate to pass existing operation. No evidence persisted, avoid storing raw signature/identity as user secret; response is merely epoch record.
- Error/expiry/closed host/revoked lifecycle => fail-closed, no retry in same session and no Finance Account balances leaked.
- Only existing internal Android app DEV code; no edits to schema, issuer SQL, Apply, FBS2v4, product Nav/Activity; W2 STOP.
- Kotlin tests must mark FAKE_SIGNER_NOT_G15 and CI must assert no product wiring.
**PASS_ACOTADO_PARA_DEV_READ_CHALLENGE_EXACT4**. Permission applies to this isolated READ auth bridge only; no auth physically certified, no cross-process invalidation, no shippable FinanceReadPort. B3B1B1B1B1 and B3B2 need separate B1-B7.

## B6
READY_TO_IMPLEMENT only after guard and exact4 preflight; no code in this doc.
## B7
PENDING_FINAL_AUDIT; require exact4 diff, 8/8 XML, DEV compile, guard, writer RELEASED, verify no product wiring.
