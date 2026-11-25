# FARO V5 · W05 C2C1 · RED_FIX D8

## B7 — RED_FIX
- Run 45529803487, SHA `3a171952d498b5831f3adc0266dd18e59c479eef`: el JVM ACTIVE terminó y exportó la fixture. `:app:mergeExtDexV2CoreDev` falló con `D8: java.lang.OutOfMemoryError: Java heap space`. Instrumentación y XML total pendientes.
- Solo el workflow queda READY para usar max-workers=1, Kotlin in-process y heap 3072 MiB/metaspace 768 MiB del workflow G5 Android del mismo APK. Preservar implementación y casos. Repetir CI dirigida por SHA.
- Guardia 45529803344 SUCCESS. C1C Evidence1 (45529803361) se disparó por la Activity compartida y su preflight rechazó el ID C2C1 antes de las pruebas. G15 pendiente.
