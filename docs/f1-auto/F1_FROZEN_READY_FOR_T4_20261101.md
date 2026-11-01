# FARO V5 — F1 congelado para T4 (2026-11-01)

**Estado:** `LISTO_PARA_T4 / FROZEN`, **NO CERTIFICADO**. Desarrollo automático de F1 cerrado; no se requieren pruebas físicas todavía. Mantener S2.A6 **OPEN 3/8**, `F1_ANDROID_CHAT_E2E=OPEN`, `T4_PHYSICAL=NOT_RUN`, `V2_ACTIVE_ENABLED=false`. No habilitar S3/S4.

## Evidencia congelada y reproducible

- Rama: `work/f1-auto-v15-20261101`.
- **SHA funcional del código congelado:** `2c79c5f2f18c7742cf2b8b512221a2a7a6430f62`.
- GitHub Actions: [#41266155404](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41266155404), en ese **mismo SHA**: `completed/success`, **3 jobs PASS**.
- `f1-groq-provider-and-consent`: Groq transporte **sintético**, políticas/consentimiento/model factory, XML exacto **20/20 PASS**; no hubo invocación Groq real.
- `f1-android-structured-review-delivery`: Android API35 emulado; consentimiento aislado, Composer, Workspace SQLite, UI recreation, Review humano simulado en instrumentación, Apply Reality **aislada**, receipt, idempotencia, Delivery permiso denegado y permitido, y recuperación tras reinicio; XML exacto **5/5 PASS**.
- `f1-android-keystore-and-apk`: Android Keystore/consentimiento emulado; XML **2/2 PASS**. El mismo run compiló y publicó APK debug históricamente, pero no se necesita descargar ni instalar para avanzar hacia F2.
- Total **27/27**, sin fallos ni omitidos. Pruebas Android emuladas son evidencia automatizada, **no física**.

## Decisión vigente y límites

El usuario aprobó explícitamente concentrar todas las pruebas físicas de F1–F4 en T4 al final de S2.A6. F1 queda congelado en la fase **automática** y se permite continuar **F2 en tandas manuales acompañadas en el chat**, sin pruebas físicas intermedias. No reabrir F1 ni gastar nuevas Actions salvo ROJO real, cambio funcional que afecte F1, o incompatibilidad detectada en integración.

**Pendientes diferidos a T4:** comprobar Groq real con cuenta Free/consentimiento explícito y clave local Android Keystore sin compartir secretos; recorrido aislado de Chat→IA real→Review→Apply→receipt→Delivery y recuperación en Moto G15; registrar semáforo y fallos. Las pruebas físicas no se sustituyen con mocks, emulador ni APK compilado. Nunca hacer cutover V2 para forzar resultado.

## Limpieza de automatización y artefacto

El workflow de F1 se conserva como evidencia/receta de pruebas dirigidas, pero se quita de esta rama manual el disparo automático por `push` y se eliminan los pasos **extra** `assembleDebug` y `upload-artifact` del APK descargable. **No** se quitan los builds internos que requiere `connectedDebugAndroidTest` para ejecutarse en emulador. No activar FARO Noche ni agendar ejecuciones futuras. El SHA del código producto `2c79c5f2f18c7742cf2b8b512221a2a7a6430f62` permanece idéntico (sólo cambio posterior de documentación y workflow).

## Siguiente tramo

Pasar a F2 (`F2_ANDROID_ACTIVATION_RECOVERY`) cuando el usuario indique continuar, sin deshacer la congelación F1 y sin modificar main o V1/V4.4. En una sesión futura recuperar este archivo y `docs/f1-auto/F1_AUTO_RUN_STATE.json`; no repetir auditorías ni tests F1 ya aprobados.
