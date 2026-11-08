# FARO V5 — S3.A5 Reality8 DEV host B6 retry / WAIT_CI

Fecha 2026-11-08 AR. Unidad `S3_A5_REALITY8_DEV_HOST_MOUNT`.
- Código y 8 nuevas pruebas en `60250bcede299c9ddb3028bb0b948ac1b700bcdd`. Primera CI `#42534451918` FAILED ANTES de compilar: AGP9 test task no creada porque faltó `-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false`, log job `128895549206`; red dirigido documentado `S3_A5_REALITY8_DEV_HOST_B7_RED_FIX_20261108.md`.
- El workflow histórico `faro-v5-v2-core-directed.yml` ya contenía la misma propiedad, B7 RED_FIX documentado. Parche **solo workflow** commit `6c3c0947ef9d701e1e1ab801354b8c873b1afdc9` con trailer de la misma unidad. No cambia código de Android, Authority, StageOwner, CutoverOwner ni pruebas.
- Nueva CI dirigida `#42534927828` RUNNING al checkpoint, necesita compiler y XML exactos 8/8 (0 errors/failures/skips). Guard nuevo `#42534927840` pendiente. El workflow original del núcleo V2 `#42534451741` SUCCESS para SHA `60250bce`, pero no prueba las 8 nuevas; no contar como PASS de esta unidad.
- Mantener B6=`WAIT_CI`, B7=`PENDING` hasta inspección terminal y XML. Si segunda CI rojo por otra causa, no repetir este ajuste ni suites anteriores; capturar error y abrir solo el delta.
- S3.A5 OPEN, V2 productiva OFF, S4, Auto y Noche OFF. G15/X01–X12 al final.
