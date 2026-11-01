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

## Revocación Groq y bloqueo SAF (actualización del chat)
- Después de pulsar «Desactivar IA remota y borrar clave» y verificar de nuevo el control de red, el usuario respondió «Todo ok!!». **REVOCACIÓN: PASS_REPORTADO_POR_USUARIO**, falta capturar texto literal de la UI si se exige una prueba de evidencia más estricta. No guardar credenciales.
- Descubierto bloqueo arquitectónico antes de la campaña SAF real: `FaroV5MaintenanceRuntime.prepareConsentedFiveStoreExport()` contiene `check(FaroV5HostCertification.V2_ACTIVE_ENABLED)` y éste sigue en `false`. Exportar respaldo de cinco stores desde la actividad de mantenimiento NO está autorizado con esta APK y no se debe activar V2 solo para forzar el test.
- `FaroV5FiveStoreSafConsentActivity` es `exported=false` en manifest y no hay ruta de menú pública desde la superficie normal. Su entrada requiere un harness de instrumentación de confianza o una variante específica debug, manteniendo la cuarentena y el acceso explícito. No presentarla como disponible en Ajustes.
- Vía segura todavía posible: verificar **cancelación/preview** con actividad del paquete de debug operada por su UID y archivo sintético; una prueba real de exportación/restauración debe usar fixture aislado y no reactivar V2. No atribuirle PASS sin interacción real con selector Android.
- Estado S2.A6 continúa **3/8 CERTIFIED** hasta evidencias finales; resto T4 manual abierto.

## T4 SAF manual — corrección del acceso (01/11/2026)
- Usuario ejecutó `adb shell run-as ar.com.faro.alpha.t4.v5dev /system/bin/am start -n ar.com.faro.alpha.t4.v5dev/ar.com.faro.v5.app.FaroV5FiveStoreSafConsentActivity`.
- Resultado: `SecurityException: Permission Denial: package=com.android.shell does not belong to uid=...`. Este comando sugerido por el asistente era incorrecto: `run-as` no hace que `am` deje de ser shell ni confiere permiso para abrir una Activity `exported=false`. No hubo escritura ni pérdida de datos. **No repetir ni cambiar el atributo exported de la Activity original**.
- Bloqueo real: `FaroV5MaintenanceRuntime.prepareConsentedFiveStoreExport` exige `V2_ACTIVE_ENABLED`, actualmente `false`; no intentar flujo de backup de producción.
- Para obtener evidencia de **picker SAF real**, se agregó sólo en `v5/app/src/debug` un harness `FaroV5T4SafSandboxActivity`, que prepara cinco stores sintéticos y un sentinel V1 temporales, exige consentimiento de exportación/importación, exporta por `ACTION_CREATE_DOCUMENT`, lee mediante `ACTION_OPEN_DOCUMENT`, y usa journal y restore validados en cuarentena; V2 permanece OFF.
- El debug test harness se compila con applicationId separado `ar.com.faro.alpha.t4saf.v5dev`, sin tocar `ar.com.faro.alpha.t4.v5dev` (Groq F1) ni `ar.com.faro.alpha.v5dev` anterior. No constituye certificación de la vía de exportación de producción.
- Workflow `.github/workflows/faro-v5-t4-saf-debug.yml`, Actions #41311989732, estado pendiente de resultado final al registrar. No informar PASS hasta ejecutar realmente con Android DocumentsUI y observar receipt+restore.

## Harness SAF sintético compilado y publicado
- Actions #41311989732: **SUCCESS** (Gradle BUILD SUCCESSFUL). Commit de build `0de4e5d1ddc5ef79a528e277e33c836891dcd149`; fuente funcional base `0750ee53`, modificaciones solo debug/workflows/docs.
- GitHub release `faro-v5-t4-saf-picker-20261101`, archivo `FARO-V5-T4-SAF-PRUEBA.apk`.
- SHA-256 esperado `ad214d30e51f31113d0eb88df2d0c9490310def76bba024f99f63a6b7b099223`.
- Package `ar.com.faro.alpha.t4saf.v5dev`; firma debug `aeac671eab68e93437c69cc5a7bef7df1333fa42045f73632f24d8cd9b032e9a`.
- NO prueba ejecutada en teléfono aún. Próximo paso descargar/validar/instalar APK aislada y abrir `FaroV5T4SafSandboxActivity` para permitir selección real de destino SAF con consentimiento y datos sintéticos, después importar/restaurar en cuarentena. Solo tras evidencia de Android se podrá marcar manual SAF PASS limitado al harness, no a producción.

## SAF manual — ROJO reportado por usuario (01/11/2026)
- El usuario mostró la pantalla `FARO T4 - SAF REAL (datos sintéticos)` abierta en dispositivo: APK auxiliar instalada y Activity visible. Primera captura con casillas desmarcadas y estado `Pendiente de consentimiento. V2 OFF.`.
- Tras indicación de marcar consentimiento y pulsar exportación, el usuario respondió `Rojo`. **No hay aún mensaje literal posterior a la acción, ni screenshot del error**. Estado `MANUAL_SAF_EXPORT_FAIL_REPORTED_DIAGNOSIS_PENDING`, NO PASS, selector/exportación no comprobada. No inferir fallo de generación, permisos, ni identidad sin evidencia.
- Se pausa restore, no modificar V2 ni FARO existente. Diagnóstico siguiente: recuperar el mensaje visible en la etiqueta de estado después de pulsar Exportar (o captura de esa pantalla); clasificar si fallo antes del picker, en selección, o en escritura verificada. Corregir sólo caso afectado.

## SAF ROJO diagnosticado mediante captura (11:07 hora argentina)
- La captura entregada contiene literalmente: `ROJO: selector o consentimiento inválido. Sin efectos.` y casillas desmarcadas tras volver del selector.
- La ruta de código original prueba que este texto sólo aparece tras recibir un URI no nulo desde DocumentsUI, cuando `exportConsent.isChecked` leído al volver es falso **o** el URI no cumple `content://`; la captura sola NO distingue ambas causas. La casilla desmarcada después es comportamiento explícito del callback y no permite deducir cuál falló.
- Corrección confinada al harness `v5/app/src/debug/kotlin/ar/com/faro/v5/app/FaroV5T4SafSandboxActivity.kt`: ticket de autorización efímero/one-shot capturado al pulsar el botón y abrir el picker; no depender del estado UI restaurado; errores separados `falta autorización original`, `URI distinto de content://` y `picker no abre`. No modificar controles de producción ni activar V2. Commit `835c7450`.
- Workflow modificado para crear APK de prueba **separada** en `ar.com.faro.alpha.t4saf2.v5dev`, sin colisión de firma con la auxiliar anterior. Commit `31e96450`; Actions `#41314736385` compilando al registrar.
- **NO VALIDADO EN TELÉFONO** todavía: habrá que registrar el nuevo resultado antes de decir PASS.

## SAF picker v2 publicado para reintento dirigido
- Actions #41314736385 **SUCCESS**; Gradle BUILD SUCCESSFUL. Fuente funcional `0750ee530703141016c6cd59a5312758d915ad1c`; workflow/debug harness HEAD compilado `31e96450e91e00d6348edfdd903bb889e485ba0a`.
- Release directa: `https://github.com/etacsantafe-source/FARO-V5/releases/tag/faro-v5-t4-saf-picker-v2-20261101`.
- APK `FARO-V5-T4-SAF-PRUEBA-V2.apk`, package separado `ar.com.faro.alpha.t4saf2.v5dev`, SHA-256 `098dfe06afa2a0f6dac92462028c0b9b62950f22ceac6f7df019e6cd6d63726a`, firma debug SHA-256 `5d323d5071e8ae7761ee9224d3704b4082da9386de804349088040a9b1beb3ec`.
- Este resultado sólo valida compilación + firma. **SAF manual permanece ROJO** hasta que el usuario ejecute el picker real y se observe resultado en el dispositivo.

## T4 SAF manual — fallo del picker V2 (captura 17:27 Argentina)
- Captura textual: `ROJO: falta autorización original para este selector. Sin efectos.` al volver de seleccionar destino con Android.
- El código V2 sólo emite ese diagnóstico con `uri != null` y `pending == null` (o `consentAtLaunch=false`, imposible al asignarse el ticket). Por tanto, **se perdió la referencia a la sesión pendiente** mientras Android estaba mostrando DocumentsUI; la recreación de Activity es una causa plausible, no quedó demostrada una muerte de proceso.
- Las capturas anteriores muestran FARO en pantalla dividida con ChatGPT, condición que puede provocar recreación por cambios de configuración. No asumir que el error es un permiso Android negado ni que se escribió un archivo; el callback rechazó la operación antes de `writeSelectedExportDocument`.
- Corrección limitada a `src/debug`: `RetainedPickerState : ViewModel` conserva ticket one-shot, prepared source y root temporal a través de recreación de Activity; **no** persiste autorización ni secreto en Bundle/SQLite, y un cierre de proceso hace fail-closed con nuevo consentimiento. Los botones se deshabilitan si hay picker pendiente. Commits `94309163` y `84391676`.
- Workflow v3 con APK independiente `ar.com.faro.alpha.t4saf3.v5dev` y release/tag nuevos, commit `7e04363e`. GitHub Actions #41347186599 ejecutándose al registrar; **no declarar SAF PASS hasta evidencia física**. La APK ordinaria de F1-F4 y V2 OFF siguen sin modificación.

## Compilación SAF manual v3 finalizada (01/11/2026)
- GitHub Actions `#41347186599`: **SUCCESS**, Gradle `BUILD SUCCESSFUL`, commit de código debug y workflow `7e04363e3223a6889543219a9c922f4a7630d688`, fuente funcional `0750ee530703141016c6cd59a5312758d915ad1c`.
- Artefacto público: `https://github.com/etacsantafe-source/FARO-V5/releases/tag/faro-v5-t4-saf-picker-v3-20261101`.
- APK: `FARO-V5-T4-SAF-PRUEBA-V3.apk`; paquete aislado `ar.com.faro.alpha.t4saf3.v5dev`; tamaño 61.854.647 bytes; SHA-256 `0527e88f904995446a68b35a6303e7c83f5c62d1881ae299c4c4d3963b712b52`; certificado debug SHA-256 `7bbe536fe65f52c1d90718277d94be9b47ead57aa2ca194fbce4ea69d7ac417c`.
- Corrección incluye retención de ticket y fuente sintética sólo ante recreación de Activity, ninguna autorización persistida tras pérdida de proceso. `V2_ACTIVE_ENABLED=false` comprobado por build.
- Próxima acción: descargar y verificar SHA desde Termux, instalar vía `termux-open --view` sin ADB (usuario con datos móviles y sin Wi-Fi), abrir la app auxiliar aislada **a pantalla completa**, repetir solo exportación SAF. **Aún no hay PASS físico de esta nueva versión.**

## SAF manual V3 — ROJO físico 01/11/2026, 17:43 AR (captura de usuario)
- La APK aislada V3 se abrió y el usuario repitió la selección de archivo desde Android.
- Estado visible exacto: `ROJO: se perdió la sesión autorizada del selector (posible cierre de proceso). No se escribió nada. Volvé a autorizar antes de repetir.`
- Es el diagnóstico diseñado para una respuesta `uri != null` con ticket ausente al volver. Este texto **no prueba** una muerte de proceso: pérdida de Activity/ViewModel puede producirse por recreación no-config, "No conservar actividades", baja memoria o reinicio de proceso. El selector sí entregó `content://` o al menos un URI no nulo, pero URI no fue aún validado porque la comprobación de ticket precede a la comprobación de esquema.
- En captura FARO T4 ocupa la mitad superior y ChatGPT la inferior en modo pantalla dividida; **no atribuir causalidad** pero repetir UNA vez a pantalla completa, sin multitarea/split-screen, antes de fabricar más APK.
- No hay autorización retenida tras la pérdida de sesión y **no se escribe ningún respaldo**. Mantener T4-SAF manual **ROJO**; no repetir 27 tests ni Groq. V2 activa sigue OFF.
- Si vuelve a fallar a pantalla completa, rediseñar prueba manual para separar selección de URI de consentimiento post-picker y exigir confirmación humana NUEVA luego de volver, permitiendo recuperación segura de nueva fuente sintética, sin confiar en ticket o secreto previo y sin introducir persistencia de autorización. Gate de exportación normal en producción sigue inaccesible con V2 OFF.
