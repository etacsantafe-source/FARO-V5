# FARO V5 — Corrección integral T4 SAF (2026-11-01)

## Identidad y estado seguro
- Repositorio: `etacsantafe-source/FARO-V5`.
- Base comprobada: `work/faro-t4-build-20261101` @ `0e1fcb62ce0344680547b18c38bdf35e44ca1f7e`.
- Rama de corrección exclusiva: `work/t4-saf-integral-20261101`.
- Ningún cambio a `main`, producción, almacenes activos, V2 ni S3/S4. `V2_ACTIVE_ENABLED=false`.
- T4 SAF manual: **ROJO** hasta evidencia positiva física. S2.A6: **3/8 certificados**.
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

## Prueba física pendiente en Moto G15
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
- **NO** es todavía PASS físico ni validación de restauración de producción. Se conserva T4 SAF ROJO y S2.A6 3/8 hasta que el usuario pruebe el selector, confirmación, exportación y restore físico en cuarentena.

