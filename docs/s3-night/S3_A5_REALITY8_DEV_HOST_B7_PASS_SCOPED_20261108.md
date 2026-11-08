# FARO V5 — S3.A5 Reality8 → Android V2 DEV host: B7 PASS_SCOPED

Fecha 2026-11-08 AR. Unidad exacta `S3_A5_REALITY8_DEV_HOST_MOUNT`, rama `work/s3-night-20261102`. **Quinta subunidad S3.A5 certificada con alcance DEV/JVM; S3.A5 global sigue OPEN.** V2 productiva OFF; S4/Auto/Noche OFF, G15 para la campaña física única final.

## B7 — Revisión independiente de evidencia por SHA
- B1–B5: `docs/s3-night/S3_A5_REALITY8_DEV_HOST_MOUNT_B1_B5_20261108.md`; auditoría adversarial de origen ID+sequence `docs/s3-night/S3_A5_REALITY8_DEV_HOST_B5_ORIGIN_BINDING_20261108.md`; permiso READY en padre exacto `3930158d24ae819b0477d6f9eed12dab001b7989`, después de un addendum de B5. Primer código funcional `60250bcede299c9ddb3028bb0b948ac1b700bcdd`, trailer `FARO-UNIT:S3_A5_REALITY8_DEV_HOST_MOUNT`.
- Diff `3930158d..60250bce`: **cinco rutas permitidas** exclusivamente: `FaroV5V2CoreDevAuthority.kt` (+62/-25), `FaroV5V2CoreDevActivity.kt` (+31/-2), nuevas `FaroV5V2Planning8DevHost.kt` (+267), `FaroV5V2Planning8DevHostTest.kt` (+179), workflow dirigido (+60). No cambia maintenance/generation previamente certificados, manifest shipping, Apply/Undo, S2/S4, ni main.
- Primera CI `#42534451918` FAIL **antes de compilar código** por tarea de AGP9 no activada; B7 RED_FIX explícito `docs/s3-night/S3_A5_REALITY8_DEV_HOST_B7_RED_FIX_20261108.md`. Parche `6c3c0947ef9d701e1e1ab801354b8c873b1afdc9` modifica **solo** `.github/workflows/faro-v5-s3-a5-dev-host-reality8-directed.yml` para pasar `-Pandroid.onlyEnableUnitTestForTheTestedBuildType=false`, reutilizando exactamente el patrón preexistente del workflow V2 core.
- CI final dirigida [#42534927828](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42534927828), job `128897007995`: **COMPLETED SUCCESS** en SHA exacto `6c3c0947`, `:app:compileV2CoreDevKotlin` y `:app:testV2CoreDevUnitTest` SUCCESS, `BUILD SUCCESSFUL in 3m 8s`. Verificador de JUnit XML: **`S3_A5_DEV_HOST_REALITY8_XML_PASS 8/8`** con `tests=8 failures=0 errors=0 skipped=0`. No se infiere el XML de un simple verde.
- Guard de siete bloques [#42534927840](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42534927840) **COMPLETED SUCCESS**, SHA `6c3c0947`. La CI V2 Core [#42534451741](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42534451741) **SUCCESS** en código SHA `60250bce`, pruebas existentes XML `V2_CORE_DEV_SEARCH_SESSION_XML_PASS 10/10`, `G5_REAL_HOST_FOUR_OWNER_XML_PASS 1/1`, aislamiento manifest 4/4; APK DEV [#42534452081](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42534452081) **SUCCESS** mismo código SHA. El parche final solo afectó workflow de prueba, no fuente APK.
- Las pruebas A5 históricas (6/6, 5/5, 5/5, 4/4) siguen certificadas y NO se relanzaron como gates nuevos de esta unidad.

## Cobertura B6 8/8 y límites
1. Abrir Chat habitual del entorno DEV nunca inicia Reality8 ni un journal8 nuevo.
2. Solo el upgrade explícito, con la sesión vieja cerrada, publica REALITY8 como generación nueva; journal COMPLETED, materialWritesAccepted y fence; old7 permanece byte a byte y Workspace nuevo vacío, sin migración de prueba.
3. Reinicio abre la misma generación8 y full pointer, sin segundo cutover.
4. Interrupción después del journal `VALIDATED`, antes del switch: reanudación de intención previamente admitida y cutover sin crear nueva operación.
5. Origen durable alterado: rechazo y pointer old7 intacto.
6. ABA del pointer con mismo ID old7 pero secuencia distinta: recuperación rechaza.
7. Acción no foreground o antigua autoridad ausente: no inicia promoción.
8. Journal PREPARING ajeno sin origen durable: no es adoptado automáticamente.

La ruta UI de Android `FaroV5V2CoreDevActivity` y permisos reales de lectura por dominio fueron compilados y su aislamiento de paquete fue verificado; la pantalla de Pendientes en el Moto G15 **NO se ha probado físicamente** y no es un PASS de T4. El cutover tras puntero publicado ya está probado por la subunidad canónica anterior 4/4, pero el recorrido completo E2E Android real + lifecycle necesita su campaña final. No se habilitan Apply/Undo, IA, OS deliveries ni V2 shipping.

## Dictamen y siguiente dependencia
- **PASS_SCOPED_S3_A5_REALITY8_DEV_HOST_MOUNT_ANDROID_BUILD_JVM_8_OF_8**. B1–B7 cerrados para esta subunidad; ninguna regresión observada en pruebas y manifiestos apuntados.
- **S3.A5 todavía OPEN**: preparación final de aceptación integrada S3, autorización/UX Android en la campaña física única X01–X12 reservada para el final, y auditoría de límites funcionales antes de cierre formal. No confundir un APK DEV compilado con instalación de este APK en G15.
- Siguiente paso permitido: revisar aceptación S3.A5 sin duplicar estas suites, registrar dependencias pendientes y avanzar únicamente en pruebas JVM/integración independientes; reservar G15 físico para campaña final, V2 productiva OFF, sin Auto/Noche ni APIs pagas.
