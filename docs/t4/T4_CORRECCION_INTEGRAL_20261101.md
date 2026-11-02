# FARO V5 — Corrección integral T4 SAF (2026-11-01)

## Identidad y estado seguro
- Repositorio: `etacsantafe-source/FARO-V5`.
- Base comprobada: `work/faro-t4-build-20261101` @ `0e1fcb62ce0344680547b18c38bdf35e44ca1f7e`.
- Rama de corrección exclusiva: `work/t4-saf-integral-20261101`.
- Ningún cambio a `main`, producción, almacenes activos, V2 ni S3/S4. `V2_ACTIVE_ENABLED=false`.
- T4 SAF aislado V4: **exportación VERDE con captura, restauración VERDE reportada por usuario**; campaña negativa/integración productiva pendiente. S2.A6: **3/8 certificados**.
- F1 Groq debug y revocación: PASS reportado por usuario; no son prueba de Chat/Apply producción.
- 27/27 pruebas Android instrumentadas T4 reportadas PASS; no son la campaña SAF real ni cierran F2–F4.

## Fallos y riesgos revisados
1. `adb shell run-as ... /system/bin/am start`: comando previo incorrecto para una Activity `exported=false`. SecurityException esperable, sin lectura o escritura de datos; **no** abrir el componente de producción a apps externas.
2. Sin Wi-Fi: ADB inalámbrico sin dispositivo disponible. No confundirlo con APK rota; distribuir instalable independiente mediante release, descarga desde navegador y instalador del teléfono.
3. Nombres V1/V2/V3 distintos: evitar indicar APKs inexistentes y verificar archivo, paquete y SHA exactos del release.
4. V1-V3 SAF: perdían el ticket de consentimiento al volver de DocumentsUI. ViewModel V3 no resuelve muerte de proceso ni recreación completa. Split-screen es un factor posible, no causa probada.
5. Harness V3: `ready` habilitaba restauración incluso con exportación fallida; `testRoot` reutilizado generaba colisiones de rutas temporales en reintentos; `onDestroy` cancelaba trabajadores sin asegurar actualización visible. Fallos/diagnóstico insuficientemente distinguibles.
6. Exportación externa interrumpida: el archivo seleccionado puede haber sido creado o quedar incompleto. No declarar `Sin efectos` después de iniciar una escritura; mostrar rojo y pedir descartar ese archivo.
7. Rutas de restore: exigir `content://`, almacenar bytes bajo staging privado acotado, autenticar FAROBAK2, validar los cinco stores con journal sin cambiar puntero V1. No proceder tras resultado de exportación desconocido.
8. `FaroV5FiveStoreSafConsentActivity` (producción): guarda `pending` en la Activity y revoca al destruirse. Es fail-closed, pero no ofrece continuidad funcional post-proceso; además la preparación de exportación real exige V2 y permanece bloqueada. Requiere trabajo independiente antes de certificación productiva, NO corregir mediante `exported=true` ni activar V2.

## Remediación aislada V4
- Nueva Activity **solo debug** `FaroV5T4SafIntegralActivity`; la antigua V3 queda fuera del manifiesto launcher.
- Dos ActivityResult launchers registrados en orden fijo: exportación y restauración. El tipo de operación procede del callback, no de un ticket guardado en memoria.
- Seleccionar URI no autoriza escritura: al regresar se exige checkbox **nuevo** y pulsar explícitamente Confirmar. Gate one-shot `FaroV5T4PostPickerGate`. Sin persistir URI, consentimiento ni contraseña. Si se pierde el proceso luego de la selección, el usuario debe seleccionar otra vez; si vuelve el resultado por Android, igualmente debe consentir de nuevo.
- Preparación de cinco almacenes y cifrado **después** de confirmación, cada ejecución en un directorio temporal nuevo bajo `noBackupFilesDir`. Intento durable y escritura external comienzan solo después del consentimiento.
- Restauración en un nuevo entorno sintético separado; entrada verificada, journal VALIDATED, V1 sentinel intacto y V2 OFF. No depende de `ready` ni de exportación retenida en otra Activity.
- Cancelación por señal, cierre de Activity cancela la operación y limpieza temporal best-effort. Nunca declarar SUCCESS ante resultado del proveedor incierto.
- Clase testable `FaroV5T4PostPickerGate` con 4 pruebas dirigidas (cancelación, reinicio, operación errónea, autorización one-shot).
- CI exclusiva de esta rama con verificación de alcance de cambios, test JVM, compilación APK, aplicación ID nueva, hash y firma; publica prerelease solo si todos los gates pasan.
- Diferencia importante: SAF manual de prueba aislada NO constituye autorización para exportación de almacenes reales o para reactivar V2.

## Plan original de prueba física (histórico; recorrido positivo realizado en V4)
1. Descargar desde el release CI **solo si aparece SUCCESS**, comprobar nombre y SHA; instalar con Android/Termux sin depender de ADB ni Wi-Fi. La app usa package aislado `ar.com.faro.alpha.t4saf4.v5dev`.
2. Abrir la app a pantalla completa; elegir destino con selector Android. Al volver, la casilla debe estar DESMARCADA y no haber escritura FARO.
3. Marcar consentimiento nuevo y pulsar Confirmar. Solo `VERDE: respaldo sintético cifrado escrito y verificado por Android` cuenta como exportación física PASS (debug).
4. Elegir ESE archivo con selector de restauración; nueva confirmación. Solo `VERDE: ... journal VALIDATED, V1 intacta y V2 OFF` cuenta como restauración física PASS (debug).
5. Para cobertura integral: cancelar picker, forzar recreación/split-screen, probar proceso terminado entre selección y confirmación, URI inaccesible/archivo inválido, retry limpio y cancelación durante operación. Documentar efectos inciertos, sin borrar archivos reales automáticamente.
6. F2/F4 UI real y autorización productiva F3 siguen pendientes por separado. Ninguna prueba T4 sintética habilita S3/S4 ni certifica S2.A6.

## CI comprobado y cierre de build (2026-11-01)
- Ejecución #41356258845: **FAILURE**, únicamente import `org.junit.Assert` no resuelto en test nuevo (la Activity principal compiló). Ninguna APK publicada.
- Ejecución #41356581883: **FAILURE**, orden incompatible en `assertFalse` de JUnit Jupiter; corregido antes de tercer build. Ninguna APK publicada.
- Ejecución final #41356724771: **SUCCESS** al HEAD `339c394e2e118a3b47f5e6977ae725d90e54581b` (prueba 4/4 PASS, `:app:assembleDebug` BUILD SUCCESSFUL, chequeos package/firma/hash PASS, release publicado).
- APK exacta `FARO-V5-T4-SAF-INTEGRAL-V4.apk`; package `ar.com.faro.alpha.t4saf4.v5dev`; tamaño **61.887.411 bytes**; SHA-256 **`6958cf99d21b97b8932c87f142ae57a62f9421a35c1dc046f7ff52c03dcf15af`**; firma debug SHA-256 **`d26a4f3b6a7f0404ad45423c2d14335658317b4ce8b274e6c4ab07a97d8c325b`**.
- Release oficial: https://github.com/etacsantafe-source/FARO-V5/releases/tag/faro-v5-t4-saf-integral-v4-20261101
- APK: https://github.com/etacsantafe-source/FARO-V5/releases/download/faro-v5-t4-saf-integral-v4-20261101/FARO-V5-T4-SAF-INTEGRAL-V4.apk
- Manifest de integridad: https://github.com/etacsantafe-source/FARO-V5/releases/download/faro-v5-t4-saf-integral-v4-20261101/T4-SAF-INTEGRAL-V4-MANIFEST.txt
- **No ejecutar de nuevo los 27 tests físicos previos**; hacer únicamente la prueba manual SAF positiva/negativa delimitada.
- **Estado al cierre de CI, antes de la prueba V4 (histórico):** todavía no había PASS físico. Fue actualizado en la sección de evidencia positiva posterior. La restauración de producción sigue NO validada, S2.A6 3/8.


## Evidencia física V4 positiva — Moto G15, 01/11/2026 aprox. 23:00–23:10 AR

**Identidad probada**
- Archivo instalado: `FARO-V5-T4-SAF-INTEGRAL-V4.apk`, package aislado `ar.com.faro.alpha.t4saf4.v5dev`, SHA-256 `6958cf99d21b97b8932c87f142ae57a62f9421a35c1dc046f7ff52c03dcf15af`.
- El usuario ejecutó en Termux el bloque de descarga, `sha256sum -c -` y `adb install -r`, con salida literal `FARO-V5-T4-SAF-INTEGRAL-V4.apk: OK`, `Performing Streamed Install`, `Success`.
- Capturas aportadas en la conversación, **no incorporadas como binarios al repositorio**; la restitución no tiene captura del texto completo de verificación.

**Interacción comprobada**
1. Se abrió `FARO T4 - SAF integral aislado` en el teléfono. Tras seleccionar destino de Android, captura: checkbox de consentimiento desmarcada y estado `Falta consentimiento NUEVO después del selector.`. Esto concuerda con la necesidad de autorización posterior al picker. No identifica por sí solo URI ni contenido.
2. El usuario marcó consentimiento y confirmó. La siguiente **captura** muestra literalmente: `VERDE: respaldo sintético cifrado escrito y verificado por Android. Ahora elegí ESE MISMO archivo para restauración aislada.` → **EXPORTACION_SAF_DEBUG_FISICA = PASS_OBSERVADO_EN_CAPTURA**.
3. Se indicó elegir el mismo archivo en `3. ELEGIR RESPALDO SINTÉTICO` y conceder consentimiento nuevo. El usuario comunicó inicialmente `"falta consentimiento....."` y después de marcar y confirmar respondió `"Verde"`. → **RESTAURACION_SAF_DEBUG_FISICA = PASS_REPORTADO_POR_USUARIO**. No adjudicar texto exacto de `journal VALIDATED` ni `V1 intacta` sin captura/log; el código V4 comprueba esos elementos antes de mostrar su mensaje verde, pero la evidencia disponible es el reporte del usuario.
4. No repetir 27/27 pruebas instrumentadas previas ni Groq debug por estas dos operaciones; son campañas distintas.

**Alcance y faltantes para cerrar T4**
- **Verde parcial dirigido**: se superó el bloqueo físico del picker V1–V3 y el recorrido feliz de exportación y restauración con 5 almacenes sintéticos, usando documentos externos SAF y consentimiento nuevo. El mensaje de consentimiento requerido es **comportamiento esperado**, no fallo.
- **No ejecutadas/documentadas aún** las pruebas negativas de SAF: cancelación del selector, recreación/split-screen o muerte de proceso, URI inaccesible/archivo inválido, reintentos y cancelación en operación. Una respuesta verde no demuestra automáticamente esos casos.
- **F2/F4 en UI e integración real Chat/Review/Apply + recuperación**, aún requieren evidencias físicas específicas; **F3 productiva** requiere diseño/autorización separado porque `V2_ACTIVE_ENABLED=false` bloquea exportación real. No habilitar V2 para “forzar” T4.
- **T4 global: ABIERTO**, no cierre ficticio ni transferencia de los PASS debug a producción. **S2.A6: 3/8 CERTIFIED**, sin alterar números de criterios sin mapeo y evidencia por criterio.
- `main` intacta, paquete FARO habitual intacto, no S3/S4; esta actualización no adquiere writer y no activa FARO Noche. Es exclusivamente documental.

**Próxima acción prioritaria:** no construir otra APK ni repetir pruebas positivas. Elegir un pequeño lote dirigido de casos negativos SAF y registrar sus resultados, y en paralelo mapear los criterios de F2/F4/F3 realmente pendientes para terminar T4 sin confundir certificación del harness con la del producto.


## Preverificación preventiva previa a nuevos ensayos (01/11/2026, solo lectura de código)

### Confirmado sin ejecutar un nuevo build
- El diff de la rama V4 contra `work/faro-t4-build-20261101` afecta solo el launcher debug, su gate y pruebas, workflow y docs. Ningún archivo de `src/main` cambió por V4. `main` observado en `9bae214541f466a62e149853931b96abb8d74ed9` al momento de verificar.
- Actions #41356724771 `completed/success`, HEAD compilado `339c394e2e118a3b47f5e6977ae725d90e54581b`, **4/4 tests del gate**; no es una suite de ciclo de vida Activity ni de contenido persistido.
- Dos launchers `ActivityResult` fijos distinguen EXPORT/RESTORE. `handlePicked` reinicia el gate y desmarca checkbox, y la confirmación posterior es one-shot. La evidencia física positiva V4 indica que el flujo principal dejó de perder el ticket al regresar del picker.
- `AndroidBackupSafAdapter` usa `wt` para truncar, verifica tamaño/SHA leyendo destino y ante fallos posteriores a escritura registra `EFFECT_UNKNOWN`; la restauración ingresa primero por staging privado y autentica el contenedor antes de validar generación aislada.

### Riesgos remanentes detectados por inspección (NO equivalen a defectos reproducidos)
1. **R1, integración productiva / bloqueo conocido:** `FaroV5FiveStoreSafConsentActivity` sigue siendo una ruta distinta, con `pending` efímero; la preparación de exportación productiva exige `V2_ACTIVE_ENABLED=true`, que continúa prohibido. El PASS V4 no resuelve F3 del producto. Requiere un flujo S2 mínimo autorizado y gates separados, **no** activar V2 solo para probar.
2. **R2, cobertura de datos:** `prepareSyntheticExport` crea/abre las bases de los cinco owners, pero **no siembra registros de prueba conocidos** ni demuestra recuperación de filas. El roundtrip físico V4 valida sobre todo estructura, transporte cifrado, integridad y journal; aún falta un fixture con contenido no trivial + comparación por owner sin datos reales.
3. **R3, destrucción de Activity durante escritura:** `onDestroy` cancela `CancellationSignal` y hace `worker.shutdownNow()`. El worker puede quedar a medio escribir un documento externo; proveedor SAF puede haber creado archivo vacío o parcial. No reutilizar el respaldo VERDE ni afirmar que una cancelación deshace el documento externo.
4. **R4, limpieza tras muerte de proceso:** el directorio de intento está en `noBackupFilesDir` y se elimina en un `finally` best-effort. La muerte abrupta evita ese `finally`; **no se encontró barrido al reiniciar** en el Activity V4. Es material únicamente sintético por diseño, pero varios reintentos interrumpidos podrían dejar basura privada.
5. **R5, pruebas de ciclo de vida insuficientes:** los 4 tests JVM comprueban el gate puro, **no** reconstrucción de Activity/ActivityResult, reinicio de proceso, permisos SAF, cancelación real o comportamiento de proveedor de documentos. El camino verde físico tampoco cubre esos negativos.
6. **R6, diagnósticos:** la Activity V4 captura `Exception` y muestra principalmente el nombre de clase. Ante un archivo corrupto, permiso revocado u otro fallo, la UI puede dar `ROJO` sin razón suficiente para diferenciarlo; preservar mensaje/captura y contrastar con logs locales limpios de secretos.
7. **R7, archivos al elegir destino:** `ACTION_CREATE_DOCUMENT` puede crear un documento exterior antes del clic `CONFIRMAR` de FARO. El consent gate impide escribir contenido FARO prematuramente, pero no puede impedir que el proveedor cree el archivo vacío. No interpretar un nombre de archivo existente como éxito ni prometer ausencia total de documento externo.
8. **R8, exclusividad del harness:** el `worker`, `isWorking` y `SingleProcessMaterialWriteGate` del intento son por instancia/fixture, no una exclusividad global probada entre varias instancias de Activity con el mismo URI externo. Evitar dos ventanas escribiendo sobre el mismo archivo; producción necesita revisión independiente de writer.

### Plan mínimo para adelantarse a fallos, sin duplicar ensayos aprobados
- **Primero sin APK nueva:** (a) cancelar `ACTION_OPEN_DOCUMENT` y `ACTION_CREATE_DOCUMENT` sin confirmar; (b) elegir archivo de prueba inválido para restauración y comprobar ROJO sin cambio V1; (c) elegir archivo y girar/recrear pantalla **antes** de confirmar, verificar consentimiento desmarcado y confirmar nuevamente. Usar siempre archivos sintéticos de prueba, nunca sobrescribir el respaldo VERDE.
- **Luego, solo con control de efectos:** interrumpir durante la operación en archivo sintético descartable, observar resultado incierto y revisar/eliminar manualmente el destino incompleto; evitar automatizar muerte de proceso mientras se escribe hasta tener un procedimiento de recuperación.
- **Para cerrar cobertura de contenido:** incorporar un fixture no vacío de los cinco owners y oráculos de comparación antes/después, más tests de lifecycle instrumentados, en una futura variante aislada si se autoriza cambiar código. No exigir al usuario instalar inmediatamente una nueva APK solo por este hallazgo.
- **Para F2/F4 y F3 productiva:** mapear gates/criterios congelados por separado. `T4` sigue abierto y `S2.A6=3/8`; preservar V2 OFF, `main` sin cambios, no S3/S4 y no activar FARO Noche.

_Esta sección registra verificación estática y recomendaciones; no se ejecutaron pruebas físicas nuevas, no se modificó código, no se creó APK._
