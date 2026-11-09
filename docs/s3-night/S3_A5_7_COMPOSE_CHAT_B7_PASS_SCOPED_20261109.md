# FARO V5 — S3.A5.7 X12 Compose real Android — B7 PASS_SCOPED (2/2)
Fecha 2026-11-09 AR. Unidad `S3_A5_7_COMPOSE_CHAT_REAL_ANDROID`. **B7 PASS_SCOPED; S3.A5 GLOBAL OPEN.**

## B7 — Auditoría independiente de CI Android REAL emulado
- B1–B5 aprobados ANTES del primer código en `docs/s3-night/S3_A5_7_COMPOSE_CHAT_REAL_ANDROID_B1_B5_20261109.md`, blob 320d7895b4998f0e988266005bab9a647a169374; permiso previo en 4ec342c27d5472e9bf29c1968e079a44f4621a09.
- Historial RED (conservado, NO contabilizado): primer SHA d0c61074, CI #42592348610 falla por import Kotlin `assertExists`; segundo SHA 4186e0b2, CI #42593488175 falla por import `onAllNodes`; tercer SHA 9ddd585, CI #42594626211 falla por selector `onLast`. Fueron errores EXCLUSIVOS del test; no se ejecutaron pruebas Android en esos runs. Auditorías causales y permisos asociados `S3_A5_7_COMPOSE_CHAT_B7_RED_FIX_IMPORT_20261109.md`, `S3_A5_7_COMPOSE_CHAT_B7_REDFIX2_IMPORTS_AUDIT_20261109.md`, `S3_A5_7_COMPOSE_CHAT_B7_REDFIX3_SELECTOR_API_20261109.md`.
- SHA único correcto `82e0858f6f18879aacb3d6d7d63fb9180e6eceeb`, diff UNA expresión en `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57ComposeChatUiTest.kt`; `SemanticsNodeInteractionCollection.get(index)` sustituye `onLast()`. Ningún archivo productivo/manifest/Gradle/contrato/catálogo cambió.
- CI dirigida https://github.com/etacsantafe-source/FARO-V5/actions/runs/42595625941 job 129087429174 **COMPLETED SUCCESS mismo SHA**. `BUILD SUCCESSFUL in 5m 57s` y marcador `S3_A5_7_COMPOSE_CHAT_APKS_COMPILED`. La instrumentación REAL Android API35 ejecutó ambos nombres `x12RealDevChatMountsAccessibleComposeEditorWithoutFalseApply` y `x12RealComposeUserTurnIsDurableAcrossActivityRecreationWithoutFakeReply` en paquete aislado `ar.com.faro.alpha.v2coredev`.
- Prueba literal de salida (no solo compilación): `OK (2 tests)`, `INSTRUMENTATION_CODE: -1`, `S3_A5_7_COMPOSE_CHAT_LIFECYCLE_A11Y_PASS 2/2` en logs del job 129087429174.
- Guard de protocolo B1–B7 https://github.com/etacsantafe-source/FARO-V5/actions/runs/42595625984 **COMPLETED SUCCESS mismo SHA**. Los 33/33 de seis subunidades A5 anteriores, X07/X11 4/4 y X12 Activity native 2/2 NO fueron re-ejecutados.

## Alcance real
1. X12 Chat Compose real en emulador Android API35: entrada con botón nativo, campo semántico accesible, `Enviar` deshabilitado sin texto y ausencia de control falso Apply/IA en host aislado.
2. X12 Workspace Chat: mensaje USER real tras Enviar, persistencia en Workspace canónico, recreation de Activity y reentrada explícita sin doble mensaje ni respuesta FARO fingida.
3. **No** demuestra G15 físico, TalkBack completo, orientaciones múltiples, recursos del G15, conexión IA real, Guardados/A3, Review/Apply ni X01–X12 global.

## Dependencias y siguiente
- X01/X02 preaudit independiente B1–B5 ya documentada en `docs/s3-night/S3_A5_7_X01_X02_REAL_ACTIVITY_AVISOS_UI_B1_B5_20261109.md`; siguiente unidad permitida: comenzar tras un nuevo permiso READY/reviewed_head del HEAD presente, solo test Android emulado, sin reruns antiguos.
- X04/X05 = **BLOCKED_PERMISSION**: no aprobar A3 ni ocho operaciones candidatas; catálogo oficial 172 intacto.
- X03 y X09/X10 = flujos de IA/Review/Apply sin integración E2E autorizada en DEV; no contarlos PASS.
- V2 productiva/S4/Auto/Noche OFF, escritor único CAS, G15 **NOT_RUN** hasta una sola campaña final.
**Dictamen B7: PASS_SCOPED 2/2; S3.A5 permanece OPEN.**
