# FARO V5 — T4 física, Moto G15 · 2026-11-01

## Origen y aislamiento
- Fuente funcional congelada: `0750ee530703141016c6cd59a5312758d915ad1c` (`work/faro-noche-v15-20261101`).
- Build GitHub Actions: #41307450405 SUCCESS; artefacto con ambas APK; release de distribución `faro-v5-t4-20261101`.
- Aplicación debug aislada: `ar.com.faro.alpha.t4.v5dev`, versionCode 66 / `5.0.0-dev-debug`.
- App APK SHA-256: `ef0968c4b211abc841b93adc4ee3c8458d5c82209cdc453ca65f3336bb8de22f`.
- AndroidTest APK SHA-256: `3c5f10079241913bebf2557c828be23cc435fec52244f161092176c2ffa69fb0`.
- Firma de build: `0ba56c4d8a3c69891f632857da956c7ac7e34a92d989170e94ec37af46bd8343`.
- No reemplazar `ar.com.faro.alpha.v5dev`; mantener V2 productiva OFF y Reality real intacta.

## Evidencia aportada por el usuario en este chat
- ADB Moto G15 conectado y autorizado.
- Usuario reportó `Success` al instalar la aplicación T4; reportó «Abrió FARO» al lanzar `ar.com.faro.alpha.t4.v5dev`. T4-0: **apertura PASS observada por el usuario**, revisión exhaustiva de navegación/punteros **NO_REALIZADA**.
- AndroidTest APK: hash `OK`, `adb install`: `Success`.
- Test físico ejecutado explícitamente con `adb shell am instrument -w -r -e class ar.com.faro.v5.app.FaroV5F1QuarantinedComposeInstrumentationTest ar.com.faro.alpha.t4.v5dev.test/androidx.test.runner.AndroidJUnitRunner`; salida mostrada íntegra en chat: **OK (6 tests)**, `INSTRUMENTATION_CODE: -1`; seis casos con código 0.
- En la misma app/runner, usuario ejecutó el lote `FaroV5F2IsolatedActivationRecoveryInstrumentationTest` (5), `FaroV5F3SafConsentInstrumentationTest` (6), `FaroV5F3QuarantinedSafRoundTripInstrumentationTest` (8) y `FaroV5TrustedAssistantAndroidIntegrationTest` (2); confirmó `OK (21 tests)`, `INSTRUMENTATION_CODE: -1`. El comando dejó el stdout íntegro en `~/storage/downloads/FARO-T4-restante.txt` del teléfono; **todavía no se ha adjuntado ni auditado ese log íntegro**.
- Total **27/27 pruebas instrumentadas aisladas reportadas PASS**; NO confundir con campaña T4 manual completa ni con S2.A6 CERTIFIED.
- Todos los ensayos mencionados usan datos de test aislados y no constituyen prueba de Groq real, consentimiento humano SAF ni operación de producción.

## Pendientes explícitos de T4
1. Interacción humana **real** con Groq desde Ajustes > Configurar IA (clave sólo en el dispositivo, nunca chat/log); probar botón de comprobación debug con texto fijo, revocar y verificar cierre.
2. SAF real: exportar respaldo cifrado eligiendo destino, verificación y restauración **sólo a cuarentena** con consentimiento del usuario.
3. Revisar criterios no cubiertos por tests aislados: interacción real Chat/Review/Apply y observación de entrega y recuperación; no habilitar V2 ni forzar certificación con código de prueba.
4. Capturar resultados reales de cada paso y sólo entonces revisar si se puede certificar cada uno de ocho criterios S2.A6. Hasta entonces **S2.A6 3/8 CERTIFIED**, S3/S4 cerrados a nuevas aperturas, T4 en curso.

## Próxima acción puntual
En la APK aislada, abrir menú lateral > Ajustes > Configurar IA. Si el usuario acepta realizar la prueba de red, guardar la credencial Groq en Keystore local y ejecutar «Probar conexión Groq (solo debug)» con texto fijo. Nunca pedir que publique su API key, ni activar IA remota sin autorización.

## Actualización de interacción humana (10:10–10:??, hora argentina; horario exacto no registrado)
- El usuario confirmó que abrió la aplicación T4 desde `ar.com.faro.alpha.t4.v5dev` y entró a Ajustes/Configurar IA.
- Después de la instrucción de cargar la clave Groq en el teléfono, consentir y pulsar «Probar conexión Groq (solo debug)», respondió: **«Todo ok»**. Registrar como **Groq debug real: PASS_REPORTADO_POR_USUARIO**, pendiente de guardar literalmente el mensaje/status de prueba si se exige evidencia más fina. No hay captura de status ni logs de red; no asumir que se probó Chat/Apply real.
- Próxima prueba: pulsar «Desactivar IA remota y borrar clave», y luego «Probar conexión Groq (solo debug)» para confirmar bloqueo tras revocación. Estado: NO_RUN.
- Seguidamente, SAF manual (destino físico + exportación/restauración aislada) y cierre de criterios pendientes.
