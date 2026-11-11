# FARO V5 — S3.A5.7 X04/X05 — cierre B7 PASS SCOPED Android API35

Fecha local: 2026-11-11 AR.
Unidad: `S3_A5_7_X04_X05_ANDROID_GUARDADOS`.
Rama exclusiva: `work/s3-night-20261102`.
Código final: `2d1007e8b0e557c716e95402dad5bd9fe638a433`.

## B7 — evidencia independiente y aceptación exacta

- **B1–B5**: ya certificados en `docs/s3-night/S3_A5_7_X04_X05_ANDROID_B1_B5_20261111.md`; anexo de precisión `S3_A5_7_X04_X05_ANDROID_EXECUTION_PRECISION_V1_20261111.md`. No repetidos.
- **B6**: implementación sólo de las 2 rutas autorizadas: `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57BookmarksUiTest.kt` y `.github/workflows/faro-v5-s3-a5-7-bookmarks-android-directed.yml`. Ningún owner, esquema, catálogo, dependencia ni producción cambiados.
- **CI exacta**: GitHub Actions run `43084379020`, job `130744830531`, `completed/success`, HEAD `2d1007e8b0e557c716e95402dad5bd9fe638a433`. Emulador `API35`, paquete DEV `ar.com.faro.alpha.v2coredev`.
- **Compilación**: `:app:assembleV2CoreDev` y `:app:assembleV2CoreDevAndroidTest` SUCCESS; instalados los dos APK reales en emulador, no en Moto G15. Marcador del build `S3_A5_7_X04_X05_BOOKMARKS_APKS_COMPILED`.
- **Instrumentación**: `x05RealRemoveExplicitRecoveryRestoreAndReopenCanonicalSource` `INSTRUMENTATION_STATUS_CODE: 0`; `x04RealSaveConfirmNativeReadAndOpenOriginalChat` `INSTRUMENTATION_STATUS_CODE: 0`. Runner imprimió **`OK (2 tests)`**, **`INSTRUMENTATION_CODE: -1`** y `S3_A5_7_X04_X05_BOOKMARKS_ANDROID_PASS 2/2`.
- **XML de ambos resultados**, derivado de eventos reales de instrumentación, verificado por el job: `S3_A5_7_X04_X05_BOOKMARKS_ANDROID_XML_VERIFIED 2/2`, `bookmarks-android-junit.xml` con `tests=2, failures=0, errors=0, skipped=0`.
- **ZIP GitHub Actions**: artifact ID `13885114408`, nombre `faro-v5-a5-7-x04-x05-api35-2d1007e8b0e557c716e95402dad5bd9fe638a433` (25,325,999 bytes, no expirado). El upload step `Preserve exact SHA APKs, real test log and verified XML` SUCCESS; contiene ruta de APK DEV, APK AndroidTest, `bookmarks-android-instrumentation.log` y XML verificado según workflow.
- **Guardia obligatoria mismo SHA**: `seven-block-evidence` run `43084378819` `completed/success`. Regresión `FARO V5 S3 Home notice directed` run `43084378829` `completed/success`. Main no modificado.
- **B7 RED_FIX previos**: documentados en versiones 1–5 antes de cada corrección funcional. La última auditoría fue `S3_A5_7_X04_X05_ANDROID_B7_RED_FIX_5_20261111.md`, y la corrección final hizo seleccionable el botón real de `Confirmar Guardados` mediante el árbol de accesibilidad, sin coordenadas mágicas ni bypass de review.

## Dictamen final y límites

`X04_X05_PASS_SCOPED_ANDROID_EMULATOR`: ambas rutas se ejecutaron en emulador real API35 con clics/gestos Android, relectura explícita, recreación de Activity y lectura de fuente canónica. X04 no atribuye un foco físico resaltado a partir de texto: M04 certificó foco tipado por separado. X05 no se confunde con la certificación de CAS/recibos y no infiere Apply implícito.

**S3.A5 global sigue OPEN**, sin campaña física G15, sin activar V2 productiva, S4, Auto o Noche; A3 anterior permanece certificado. No se cerraron X03/X08/X09/X10 ni otras unidades pendientes por analogía. No se ejercieron datos reales del usuario ni API pagas.

**Próximo permiso**: `BLOCKED` por cierre de esta unidad. Un nuevo objetivo requiere nuevo B1–B5/permisos específicos. Writer global CAS debe quedar `RELEASED` tras este checkpoint. No se declara PASS de sesión hasta comprobar publicación de checkpoint, guard del commit documental y release de writer.
