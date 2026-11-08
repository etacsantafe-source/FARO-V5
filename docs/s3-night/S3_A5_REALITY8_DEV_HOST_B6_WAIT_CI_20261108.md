# FARO V5 — S3.A5 Reality8 V2CoreDev host: B6 WAIT_CI

Fecha 2026-11-08 AR. Unidad `S3_A5_REALITY8_DEV_HOST_MOUNT`. Permiso B1–B5 del padre `3930158d24ae819b0477d6f9eed12dab001b7989`, con addendum del origen ID+sequence y B5 adversarial. V2 productiva OFF.

## Código exacto aún NO certificado
- Commit funcional `60250bcede299c9ddb3028bb0b948ac1b700bcdd`, trailer `FARO-UNIT:S3_A5_REALITY8_DEV_HOST_MOUNT`; cinco archivos: `FaroV5V2Planning8DevHost.kt`, `FaroV5V2CoreDevAuthority.kt`, `FaroV5V2CoreDevActivity.kt`, `FaroV5V2Planning8DevHostTest.kt` y workflow dirigido `faro-v5-s3-a5-dev-host-reality8-directed.yml`.
- Implementación propuesta: intent DEV durable con secuencia completa previa, StageOwner/CutoverOwner canónicos y barrier único compartido, verificación journal+fence, recovery preexistente, apertura exacta REALITY8 y revocación de sesión por pointer; Activity solo por botón explícito, Reality7 preservada.
- Gate nuevo esperado `#42534451918`: Android `:app:compileV2CoreDevKotlin`, JUnit XML exactos 8/8 de nueva suite `FaroV5V2Planning8DevHostTest`. Guard de protocolo `#42534452161`. Al crear el checkpoint, resultados `IN_PROGRESS`, no hay XML ni certificación. Otros workflows históricos se dispararon automáticamente por push: no relanzarlos ni contarlos como prueba nueva.
- B7 pendiente: revisar diff, run SHA, XML tests/failures/errors/skipped, Android variant, recuperación STAGED, intento corrupto, pointer ABA, ausencia de auto-promoción y despliegue DEV solo tras verificar. No G15, no S4, no V2 productiva.

## Siguiente acción y reglas de recuperación
1. Consultar terminal `#42534451918` y `#42534452161` para SHA exacto `60250bce`. Si verde verificar XML/logs y auditar B7 sin repetir suites ya certificadas.
2. Si rojo, leer error puntual, obtener nuevo writer CAS tras verificar HEAD/cola, documentar B7 RED_FIX por commit anterior y un único parche dirigido (sin reabrir B1–B5), ejecutar una sola CI corregida; dos fallos cambian hipótesis.
3. Si el nuevo código no fue ejecutado, estado `WAIT_CI`, no `DONE_VERIFIED`. Real V2 DEV G15 X01–X12 única campaña física al final.
