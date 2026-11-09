# FARO V5 — S3.A5.7 Compose real Android Chat X12 — B6 WAIT_CI

Fecha 2026-11-09 AR. Unidad S3_A5_7_COMPOSE_CHAT_REAL_ANDROID. Preaudit B1–B5 en docs/s3-night/S3_A5_7_COMPOSE_CHAT_REAL_ANDROID_B1_B5_20261109.md (blob 320d7895b4998f0e988266005bab9a647a169374). Permit READY previo al código en HEAD 4ec342c27d5472e9bf29c1968e079a44f4621a09.

## B6 — evidencia y límites
- Commit código d0c61074cd9631fb3f82fa0cff25fcfd1498565d, exactamente dos archivos nuevos: v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57ComposeChatUiTest.kt y .github/workflows/faro-v5-s3-a5-7-compose-chat-android-directed.yml. Sin archivos productivos ni permisos nuevos.
- Actions CI https://github.com/etacsantafe-source/FARO-V5/actions/runs/42592348610 IN_PROGRESS al observarse; resultado Android y stdout `OK (2 tests)` NO VERIFICADO. Compilación y tests pueden fallar; no inferir éxito.
- Guard de protocolo https://github.com/etacsantafe-source/FARO-V5/actions/runs/42592348491 completado SUCCESS para SHA d0c61074. No equivale a test Compose.
- Casos dirigidos NUEVOS: (1) clic nativo real monta UI Compose y "Enviar" está deshabilitado en vacío, editor semántico; (2) input USER, envío, recreación Activity y reapertura por clic muestra exactamente una vez el mensaje persistido sin IA/Apply.
- 33/33 seis subunidades anteriores, X07/X11 4/4 y X12 Native API35 2/2 permanecen PASS en su alcance histórico; cero reruns deliberados de esas suites.
- V2 productiva OFF, S4/Auto/Noche OFF, CLOUD queue STAGED/activation_authorized false, 172 contratos sin cambios, A3 Guardados X04/X05 sin aprobación, Moto G15 NOT_RUN para única campaña final.

## Próximo paso independiente
Verificar CI dirigida exacto SHA, job, compilación y salida de instrumentación `OK (2 tests)`, `INSTRUMENTATION_CODE: -1`; si FAILURE, documentar B7 RED_FIX causal antes de corrección, permiso nuevo sólo mismas rutas. Si SUCCESS: B7 PASS_SCOPED, mantener A5 GLOBAL OPEN hasta X01–X12 y dependencias autorizadas. Nunca llamar PASS a RUNNING.
