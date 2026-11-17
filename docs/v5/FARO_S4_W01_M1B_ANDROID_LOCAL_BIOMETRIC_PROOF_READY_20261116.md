# W01 M1B — READY exact 5 paths, Android Keystore/BiometricPrompt dormant
Unit `S4_W01_M1B_ANDROID_LOCAL_BIOMETRIC_PROOF`; B1–B5 source `docs/v5/FARO_S4_W01_M1B_ANDROID_LOCAL_BIOMETRIC_PROOF_B1_B5_20261116.md`, proof blob `97ff925257a8d9fa822ff66a8bdcbbceece24d04`; reviewed HEAD `4a0780ede90eef3e24477f02e2fb16f6378e7148`, guard 44004088951 SUCCESS. Writer previously RELEASED 1011. Code NOT_RUN.
## B1
Reuse Finance9 M0C2B2B and M1A B7; AndroidSandboxPrincipal UID+pointer session (technical only), no actual human auth nor cross-OS process COMMIT.
## B2
V4.4 UI only; do not import grants or redesign existing certified Finance review/receipt.
## B3
AndroidX BiometricPrompt CryptoObject and AndroidKeyStore per-use signing; fail closed without STRONG sensor, no synthetic auth; https://developer.android.com/reference/androidx/biometric/BiometricPrompt
## B4
Bounded exact paths:
- `v5/app/build.gradle.kts`
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceLocalPrincipal.kt`
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceAuthHost.kt`
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01FinanceLocalPrincipalTest.kt`
- `.github/workflows/faro-v5-s4-w01-m1b-local-biometric-directed.yml`
P01–P09 challenge/pointer/sandbox/nonce/replay/expiry/cancel/keystore-dev-compile; exact XML + isolated DEV build; hardware/G15 NOT_RUN.
## B5
No public key injected from untrusted caller, no arbitrary approval via fake; never token/ACL grant or Apply from mere biometric callback; invalidated keys DENY. M1C still needed to revalidate ACL under same Reality SQL COMMIT across OS processes.
## B6
READY_TO_IMPLEMENT only after preflight + new writer CAS and separate code commit.
## B7
PENDING independent SHA CI XML and manifest/isolated build, writer RELEASED, W2 STOP. Physical proof not possible via JVM.
