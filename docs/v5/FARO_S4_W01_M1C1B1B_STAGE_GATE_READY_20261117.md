# FARO V5 — W01 M1C1B1B · Revisión independiente B5 y autorización limitada de código STAGED
Fecha 2026-11-17 Argentina. HEAD revisado 5280bc2e1e164447f712b680ec31ee175d26c9bb.
Unidad S4_W01_M1C1B1B_FINANCE9_ANDROID_STAGE_GATE. Documento de permiso — sin código funcional.

## B5 — Verificación adversarial independiente
Revisé B1–B5 en docs/v5/FARO_S4_W01_M1C1B1B_FINANCE9_ANDROID_STAGE_GATE_B1_B5_20261117.md, blob e408bbbff39387babe41ac16b4f6eb52a300c583, con guard documental 44058021176 COMPLETED/SUCCESS en el SHA 5280bc2e1e164447f712b680ec31ee175d26c9bb.
- Se conserva M1C1B1A 19/19, M1C1B0 8/8 y M1B/M1C0/M1C1A scoped B7 sin recertificarlos.
- Dato adversarial real: RealityFinance9AclScope y el método de consumo SQL son internal al módulo Reality; el host Android NO puede importarlos. Se admite sólo port intermodular acotado que mantiene la verificación y ejecución internamente en Reality.
- El port no emite grants ni valida por sí mismo personas. La evidencia M1B/M1C1A es objeto estructural falsificable en JVM: PROHIBIDO instalar un listener de producto, derivar principal de Chat/Intent, elevar DEV a Shipping o indicar biometric G15 PASS.
- La prueba de G01–G10 exige UN SOLO receipt SQL real, grant single-use dentro del mismo BEGIN IMMEDIATE→COMMIT, revoked/expired/ABA fail closed y rollback total ante denegación. Gate nulo en Finance9 está prohibido; otros owners legacy no cambian.
- El constructor/factory deberá denegar schema7/8, store cerrado y scope ajeno, y no dar raw SQLite al módulo app. El código Android será nuevo, no registrado en una Activity/CompositionRoot. No modificar product flags ni AndroidManifest.
- No se requirió otra librería; las fuentes oficiales Android CryptoObject y SQLite BEGIN IMMEDIATE están en el B3. Sin API pagas, sin red, sin nuevo OperationId.
- Lo anterior habilita **sólo implementar y probar el seam DEV STAGED9**. NO habilita emisión real de principal/grant humano, biometría física, integración productiva, G15 o cierre W01.

## READY — Alcance exacto único
1. v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt
2. v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedStageGrantPort.kt
3. v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01M1C1B1BFinance9StageGate.kt
4. v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedStageGrantPortTest.kt
5. v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01M1C1B1BFinance9StageGateTest.kt
6. .github/workflows/faro-v5-s4-w01-m1c1b1b-stage-gate-directed.yml

Cualquier path adicional => STOP y nueva revisión. Ejecutar preflight por las seis rutas **antes** del primer commit funcional. La nueva autorización debe estar en el PADRE del código, nunca junto a código. En código: FARO-UNIT:S4_W01_M1C1B1B_FINANCE9_ANDROID_STAGE_GATE.
CI en SHA exacto y XML G01–G10 + regresiones afectadas, guard SUCCESS, B7 independiente o RED_FIX de la misma unidad. Mantener writer CAS exclusivo, liberado antes de esperar CI. W1 B/C/D/E/F OPEN; W2 STOP.
