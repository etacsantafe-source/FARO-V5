# FARO V5 · W05-A2D1B · B7 RED_FIX de CI Android

## B7 · Evidencia y alcance

- B6 `51d9c57aa6782eae5a662020c04c906197e785c6`, guardia 46096130613 SUCCESS. CI dirigida 46096130474, job 140827765798 FAILURE antes de pruebas. D8 `:app:mergeExtDexV2CoreDev` dio `OutOfMemoryError: Java heap space`; `:control:compileKotlin` tampoco pudo transformar un JAR por heap. XML y APK no generados.
- Delta de misma unidad acotado a `.github/workflows/faro-v5-s4-w05-a2d1b-stock-android-dev.yml`. Aplicar el patrón de los workflows Android del repositorio: un worker, compilador Kotlin in-process y heap explícito. Ejecutar nueva CI del SHA y el mismo caso de API35, 200%, rotación y APK.
- Los jobs históricos de SQL11 se dispararon por rutas compartidas y rechazaron, en sus propios preflights, el permiso de A2D1B. No son ejecución funcional de SQL11 ni certifican A2D1B. La guardia de protocolo común de A2D1B pasó.
- Mantener READY de la misma unidad con B7 RED_FIX. A2D sigue 3/5 y A2D2 espera PASS de A2D1B.
