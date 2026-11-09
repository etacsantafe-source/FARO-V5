# FARO V5 — S3.A5.7 X08/X12 RED_FIX apertura posterior a recreate — B1–B5
Fecha 2026-11-09 AR. Unidad misma `S3_A5_7_X08_X12_BACKGROUND_SEARCH`. Hipótesis nueva respecto de B1–B5 `docs/s3-night/S3_A5_7_X08_X12_BACKGROUND_SEARCH_B1_B5_20261109.md`; NO extiende permisos.

## B1 — Falla observada, aceptación
- Baseline de código `dda9c3eceab6e30793432cfb829b75403783ce16`, CI [42651371911](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42651371911), job 129278301671: `COMPLETED FAILURE` tras compilar/instalar APK Android API35. `Tests run: 1, Failures: 1`, `ComposeTimeoutException` 60000ms tras segunda `explicitlyOpenChat()` (test línea 132), después de ActivityScenario.recreate. Guard [42651371907](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42651371907) SUCCESS mismo SHA.
- B7 independiente: `docs/s3-night/S3_A5_7_X08_X12_BACKGROUND_SEARCH_B7_RED_FIX_20261109.md`. Los otros 47 casos PASS permanecen históricos y NO se repiten; no afirmar resultado positivo del test rojo.
- Criterio mínimo siguiente: capturar estado visible nativo de nueva Activity en caso de timeout de Compose y distinguir `No se abrió:` de `Verificando journal...` o ausencia de callback. Sin nuevo permiso de lectura, sin nueva mutación de app DEV.

## B2 — Reutilizar diagnóstico, no rediseñar
- La propia DEV Activity muestra `status: TextView` y texto `No se abrió: <tipo excepción>` si `FaroV5V2CoreDevAuthority.open()` falla; el test actual verifica únicamente semántica Compose tras click. Comparar con prueba Compose Chat de recreación `FaroV5S3A57ComposeChatUiTest` histórica 2/2 y X12 native lifecycle histórica 2/2; no duplicar sus pruebas.
- V4.4 es referencia de navegación/lifecycle (revisión previa `S3_A5_X01_X12_B1_B5_20261109.md`), pero este problema es exclusividad del DEV kernel Reality8/Workspace y no se corrige copiando V4.

## B3 — Investigación, alternativas, decisión
- AndroidX ActivityScenario https://developer.android.com/reference/androidx/test/core/app/ActivityScenario : `recreate()` recrea Activity y `onActivity` permite inspeccionar View state; USAR.
- Android testing https://developer.android.com/training/testing/espresso : diferenciar condición UI asíncrona, fallos visibles y error de instrumentación; USAR.
- Compose testing https://developer.android.com/develop/ui/compose/testing/common-patterns : esperar semántica real, no proclamar éxito por build; USAR.
- Alternativa A USAR: nuevo diagnóstico **solo de test** con captación del texto nativo de error cuando waitUntil falla y assertion contextual; reutiliza el mismo test y no modifica producción. Alternativa B DESCARTAR: subir timeout a 120s o reintentar ciegamente, ocultaría error y repetiría CI. Alternativa C DIFERIR: modificar política de sesiones/SQLite sin evidencia de causa, altera producto de modo especulativo. No librerías, red ni costo.

## B4 — Alcance mínimo y verificación
- Ruta única permitida: `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57SearchBackgroundRevokeUiTest.kt`; workflow existente corre al recibir cambio de ruta, sin edición. Añadir captura nativa de TextViews por `rule.runOnUiThread` y error que preserve excepción original en caso de timeout. No cambiar aserciones de seguridad ni convertir las fallidas en permisivas.
- B6 exact CI Android 1/1 con instrumentación; si sigue rojo, analizar status y corregir causa sin modificar otros owners a ciegas. B7 solo con evidencia, XML/log, SHA y guard.

## B5 — Adversarial / STOP
- No silenciar timeout con `runCatching` que devuelva true, ni aprobar un nuevo Chat falso, ni reutilizar Search grants tras onPause. Nunca modificar `FaroV5HostCertification.V2_ACTIVE_ENABLED=false`, manifest, red, A3/catalog 172, S4, G15, Auto/Noche.
- Exclusividad global writer CAS, una unidad RED_FIX a la vez; no cambiar paths no aprobados. Si diagnóstico encuentra otro origen, se vuelve a B1/B2/B5 con evidencia y permiso nuevo antes de fix productivo.
