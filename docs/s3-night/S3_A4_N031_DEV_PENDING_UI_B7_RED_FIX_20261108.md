# FARO V5 · S3.A4 N031 DEV Pendientes UI · B7 RED_FIX
Fecha AR 2026-11-08. Código `77d5bfa19bf9bc191d44b364a45955e03672f5e2`, unidad `S3_A4_N031_DEV_PENDING_UI_MOUNT`.

## B7
- **No PASS de esta unidad**. CI nueva [#42521187552](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42521187552), job #128854823779: `Cannot locate tasks that match ':app:testV2CoreDevUnitTest'`; el task NO EXISTE en variante custom actual. Es fallo de configuración de suite de test que exige B5 6/6; no certificarla por compilación APK.
- Guardia seven-block [#42521187437](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42521187437) SUCCESS mismo SHA; compilar APK M5 [#42521187481](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42521187481) aún PENDING al documentar. No afirmar su resultado anticipadamente.
- Revisión local del diff: 5 rutas exactas de permiso B1–B5; UI botón separado, grants per-source, excepción schema7=>No disponible, SQL worker y onPause revoke; falta verificación JVM real y APK final. No hay fuentes/capacidades productivas nuevas.
- **RED_FIX limitado**: reubicar el renderizador Kotlin puro a `src/main` (no montaje shipping) y la suite a `src/test`, fuentes con tasks existentes `:app:testDebugUnitTest` y XML 6/6; borrar archivos homónimos de source set v2CoreDev y testV2CoreDev; actualizar workflow. No cambiar la lógica autorizada del host, owner, Activities, schemas ni permisos. No superar 6 casos con test estático.
- Mantener **A4 OPEN** hasta B7 posterior. El código actual NO está certificado. Este documento es la disposición del último code SHA para permitir nueva implementación de la MISMA unidad, con permiso nuevo y preauditoría B1–B5 amendida.
