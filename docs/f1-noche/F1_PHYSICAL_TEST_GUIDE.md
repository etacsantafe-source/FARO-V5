# FARO F1 — prueba física con APK debug (sin activar V2)

Estado: READY_FOR_HUMAN **solo si** el workflow `FARO V5 F1 night directed` del SHA exacto termina SUCCESS y publica el artefacto `faro-v5-f1-debug-apk`. Hasta entonces APK **NO VERIFICADA**. F1 continúa OPEN.

## Preparación segura

1. En GitHub Actions del repositorio público `etacsantafe-source/FARO-V5`, abrir la última ejecución SUCCESS de `FARO V5 F1 night directed` correspondiente a la rama `work/f1-noche-v14-20261031-1820`. Confirmar SHA exacto y los jobs `f1-groq-provider-and-consent` y `f1-android-keystore-and-apk` en SUCCESS; revisar XML 19/19 JVM y 2/2 Android si se ejecutaron.
2. Descargar el artefacto `faro-v5-f1-debug-apk`, extraer `app-debug.apk` y comparar SHA-256 con `FARO_F1_debug_SHA256.txt`. El artefacto expira a los 3 días; si no aparece, no afirmar APK lista.
3. **Usar exclusivamente una cuenta Groq Free sin facturación habilitada**. Groq publica precios para planes pagos; una solicitud real puede generar cargos en cuentas facturadas. El botón consume cuota del proveedor y no garantiza costo cero en una cuenta paga.
4. Instalar **solo** el APK debug en Android. Identificador esperado `ar.com.faro.alpha.v5dev`; no reemplaza V4.4 ni activa V2. Si hay un debug previo firmado con otra clave, desinstalar **solo** ese paquete debug, nunca V4.4.
5. Abrir la pantalla local `IA de FARO · configuración local` desde el acceso de Ajustes del shell. Ingresar la clave Groq **en el teléfono**, marcar consentimiento y guardar. La clave se almacena con Android Keystore; no copiarla al chat, GitHub, capturas ni registros.
6. En APK debug, tocar `Probar conexión Groq (solo debug)`. El mensaje enviado es fijo (`Respondé solo FARO_OK`), sin datos personales, y no modifica Workspace ni Reality. Registrar únicamente estado éxito/fallo, modelo, hora y si hubo error 401/404/429 u otro, **nunca** la clave.
7. Revocar consentimiento desde la misma pantalla al terminar; comprobar que queda desactivado. La prueba manual de Composer→SQLite→Review→Atomic Apply→Delivery en el dispositivo es **otra prueba pendiente**, no cubierta por el botón de transporte.

## Límites de evidencia

- La prueba Groq real requiere clave personal, conexión y acción humana: `WAIT_HUMAN`.
- Android Keystore/emulador, si pasa, no sustituye el dispositivo físico T4.
- La ruta de Review está conectada en CompositionRoot **solo bajo** `V2_ACTIVE_ENABLED`, que sigue `false`. No hacer cutover ni usar esta APK como FARO de producción.
- No declarar F1 COMPLETE por un APK compilado ni por `READY_FOR_HUMAN`.

**Evidencia A2:** artefacto #13060368194 corresponde a SHA `dffe785a`, no al último SHA `ea1886fc`, cuyo CI falló por interop Java/Kotlin. La pantalla muestra categorías de error, no necesariamente códigos HTTP exactos. No atribuir PASS integral.
