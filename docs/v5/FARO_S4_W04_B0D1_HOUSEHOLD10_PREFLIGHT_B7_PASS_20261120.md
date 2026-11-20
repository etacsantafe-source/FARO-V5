# FARO V5 — W04 B0D1 · Admisión y reapertura DEV Household10

Fecha local 2026-11-20. Unidad `S4_W04_B0D1_HOUSEHOLD10_PREFLIGHT_ACTIVE_GRAPH_DEV`. Código `106532c3222e5d268b9e6b014c7eb762dd5ee685`, fixture corregido `a4d8f4ebd4ff300605bbdec3e9bc321b5bf7edf4`.

## B7 — PASS de preflight y reapertura de verificación

- El primer run 44726197350 falló por fase de journal incorrecta en el fixture. RED_FIX documental con guardia 44726960603 SUCCESS. Se usaron las dos rutas autorizadas para corregir el fixture y el conteo del workflow.
- CI JVM 44727157959 SUCCESS y guardia 44727157893 SUCCESS. ZIP 14623704350 descargado e inspeccionado, JUnit XML 6 casos, 0 fallos, 0 errores, 0 omitidos. Digest ZIP `sha256:128e0d4afb4ebd9c1426bc7106a810008f230ae222deda3ff5b8b20d69417264`; SHA-256 XML `4140f10b655506841832441a4dc188e142fc5cd7d180f4091a6126ffbfc02582`.
- Admisión exacta y sellada de cinco stores, Household10 y Finance9, con rechazo de versión anterior, companion incorrecto, pointer ABA, journal ajeno, aliases, sidecars, fence y lease ausente. La inspección positiva conservó archivos y puntero.
- Reapertura del DEV ya seleccionado bajo puntero ID+secuencia con comprobación de salud. El opener histórico Finance9 rechaza Reality10. Esta prueba usa un puntero publicado por el fixture; D2 prueba la publicación real del puntero y su recuperación.
- Permiso consumido. Siguiente D2 con rutas propias.
