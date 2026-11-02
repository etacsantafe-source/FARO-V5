# FARO V5 S2 — ÚNICA PRUEBA FÍSICA T4 · APK AISLADA
**Estado técnico:** READY_FOR_SINGLE_MINIMAL_PHYSICAL_T4. **Certificación:** S2.A6 OPEN 6/8; F1 (#4) y T4 (#8) pendientes de evidencia física. V2 OFF. No activar FARO habitual.

## APK única y procedencia comprobada (02/11/2026)
- Rama: `work/s2-chat-finish-night-20261102`; SHA funcional: `def90be3cafc7fec25885448315a57b5215eda0b`.
- Actions [#41421309967](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41421309967): SUCCESS, JVM F1 4/4, JVM A5 9/9 y Android emulador 5/5, todos verificados con oráculos XML. E2E de emulador sintético; no Groq real.
- [Descargar artifact 13129913168](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41421309967/artifacts/13129913168): ZIP con **un** `app-debug.apk`. ZIP SHA256 `ab57643b082c761149010165a1c1539762cac5da4bf2b1e65bc9fbc5c5c0b638`.
- **SHA256 del APK** (no del ZIP): `0efe51ea514eb49407e166a08865ee9499b47bb9c48c5b6a9f579113f7efc238`.
- Package `ar.com.faro.alpha.s2isolated.v5dev`, versionCode 66, versionName `5.0.0-dev-debug`; firma **Android Debug**, certificado SHA256 `cc14bcf1b2890e8fabcfcab0e2d8f18f23a98c9e7beb6a7af796dfd589b106db`. No usar como APK productiva ni instalar sobre el paquete FARO habitual.
- La firma debug puede diferir de otra APK debug previamente instalada: si Android rechaza por firma incompatible, **no desinstalar ninguna aplicación habitual**; detenerse y revisar exclusivamente el paquete aislado.

## Único recorrido necesario en Moto G15
1. Instalar esta APK aislada. Abrir **FARO S2 CHAT AISLADO**. En **Configurar o revocar Groq**, ingresar la clave solo localmente y dar consentimiento Android explícito. No compartir credenciales por chat, GitHub o CI.
2. Pulsar **Abrir Chat aislado**. Enviar «Hola, respondé brevemente» y confirmar una respuesta real (no sintética). Luego enviar «Cambiá mi nombre declarado a Prueba T4». Esperar una propuesta de revisión; si no aparece, anotar ROJO sin improvisar un Apply.
3. Leer la propuesta, **Confirmar** y después **Aplicar** de forma explícita. Verificar recibo y aviso local. Cerrar/reabrir la app y comprobar que el cambio, recibo y aviso persisten **una sola vez**, sin duplicados. Solo afecta Reality aislada.
4. Registrar PASS/FAIL de estos tres pasos y versión Android. Revocar consentimiento Groq al terminar si no se desea conservarlo. Si falla, detenerse; no repetir SAF ni los 32/32 previos.

**A5:** selector, límites, lectura ContentResolver y consentimiento de texto ya tienen 9 JVM + 3 Android dentro de los 5 instrumentados. No se exige prueba física adicional de adjuntos para el T4 mínimo. Imágenes y PDF son vista previa local; texto de hasta 8 KiB requiere confirmación de incorporación al borrador y otra acción Enviar para egreso.

**Criterio de cierre:** no declarar S2 8/8 hasta evidencia real del recorrido F1/T4. Si todo es verde, congelar S2 y no activar V2 productiva automáticamente.
