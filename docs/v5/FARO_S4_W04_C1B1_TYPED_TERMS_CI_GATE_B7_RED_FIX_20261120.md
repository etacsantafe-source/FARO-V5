# FARO V5 — W04-C1b1 · B7 RED_FIX del workflow DEV de correcciones parciales
Fecha 2026-11-20. Unidad `S4_W04_C1B1_STAGED_TYPED_TERMS_DEV`. SHA de corrección Kotlin `2a9bfb6038d545bfde736f86be1819d040cc1386`.

## B7
**RED_FIX, no PASS:** CI `44813118438` FAILURE, job `136554466420`, paso 3 `C1b1 exact-four-route preflight against parent`. Log: `FARO_PROTOCOL_PREFLIGHT_PASS S4_W04_C1B1_STAGED_TYPED_TERMS_DEV 9b0ee24d51b0eed1020f9dd498a7ca4bc77200e0`, luego exit code 1 porque el workflow exige `test "$count" -eq 4`. La corrección RED_FIX anterior cambió legítimamente UNA ruta de las cuatro autorizadas. El control B1–B5 real acepta rutas exactas; exigir exactamente cuatro cambios es un error del workflow y bloqueó compilación/tests (no hay XML de esta segunda CI). Guardia general `44813118366` SUCCESS.

Corregir **solo** `.github/workflows/faro-v5-s4-w04-c1b1-terms-staged.yml`: mantener `while` de preflight cada ruta exacta contra el commit padre, pero reemplazar `test "$count" -eq 4` por `test "$count" -ge 1` y `test "$count" -le 4`. Los cambios fuera de las cuatro rutas quedan DENY por gate de protocolo; 0 cambios sigue DENY. NO cambiar pruebas de éxito 9/9, no ampliar rutas, no tocar C0A/C0B/C1a/Finance ni activar Evidence2 en graph activo. Luego compilar JVM, analizar XML real y emitir B7 independiente.
