# FARO V5 — S3.A5.7 X04/X05 B7 RED_FIX5 — botón nativo Android

Unidad: `S3_A5_7_X04_X05_ANDROID_GUARDADOS`. SHA funcional: `fc9f51f7b4be5ee059315eb583810774ec7b7ba7`.
Android API35: [run 43082501290](https://github.com/etacsantafe-source/FARO-V5/actions/runs/43082501290), job `130738735349`, FAILURE; guard en mismo SHA run `43082501349`, SUCCESS.

## B7 — RED_FIX, evidencia real por test

- APK DEV y APK AndroidTest compilados e instalados en emulador API35; instrumentación ejecutada 2 tests reales.
- **X04 PASS**: `INSTRUMENTATION_STATUS: test=x04RealSaveConfirmNativeReadAndOpenOriginalChat` seguido de `INSTRUMENTATION_STATUS_CODE: 0`. Guardar, confirmar, reabrir Guardados y abrir fuente sin Apply. Este PASS individual no basta para cierre total.
- **X05 FAIL**: `AssertionError: Exactly one native positive review button expected:<1> but was:<0>`, en `confirmRealNativeReview` después de verificar título `Confirmar Guardados` y mensaje `Quitar referencia de Guardados`. El test filtraba `className == "android.widget.Button"` y `text == "Confirmar"` en la ventana de accesibilidad.
- Diagnóstico: el review nativo sí apareció; la selección exacta por clase y capitalización de etiqueta fue demasiado restrictiva para el árbol del emulador. No hay evidencia de fallo productivo; tampoco hay todavía evidencia de quitar/restaurar con persistencia.
- Corrección autorizada SOLO en test nuevo: buscar etiqueta de confirmación por texto exacto **ignorando mayúsculas**, comprobar ventana activa con título y mensaje exactos, seleccionar el propio nodo o un ancestro clickable/enabled del botón, limitar ascenso y exigir candidato positivo único. Ejecutar `AccessibilityNodeInfo.ACTION_CLICK`, sin coordenadas, sin bypass, sin falsificar resultado.
- Conserva 2 métodos Android y todo X04; si el árbol no permite selección inequívoca o la mutación falla, `NEEDS_PREP`/nuevo B7 RED_FIX, no ampliar código productivo. Sin tocar A3/main/V2 productiva/S4/Auto/Noche/G15.
- Antes de otro commit funcional, registrar este documento y actualizar `last_code_review` RED_FIX; exigir guard exacta, nuevo run Android 2/2 con `OK (2 tests)`, `INSTRUMENTATION_CODE: -1`, APK+XML, B7 independiente y writer CAS RELEASED.
