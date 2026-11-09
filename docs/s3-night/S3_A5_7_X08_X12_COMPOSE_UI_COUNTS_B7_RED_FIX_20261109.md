# FARO V5 — S3.A5.7 X08/X12 — B7 RED_FIX first Chat UI mount (09/11/2026)

**Unidad:** `S3_A5_7_X08_X12_BACKGROUND_SEARCH`. CI real [42682116828](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42682116828), job `129379132266`, SHA `2b61c14b7295b1ce8498bb02e4009d9998d7625c`: **COMPLETED FAILURE**, 1 test ejecutado y 1 FAIL. A5 permanece OPEN; 47 PASS_SCOPED anteriores no se repiten.

## B7 — Evidencia exacta, sin falsas inferencias

- Android API35 y 2 APK DEV instalados, compilación SUCCESS. Guard siete bloques [42682116846](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42682116846) SUCCESS, Home notice [42682116837](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42682116837) SUCCESS.
- Instrumentación X08/X12: `AssertionError: DEV Chat mount failed after explicit native click:` con estado nativo vacío, `ComposeTimeoutException` 60000ms. Stack `explicitlyOpenChat:91` invocado desde **línea 119 (primera apertura, ANTES de Search o recreate)**. No `OK (1 test)` ni marcador PASS. A diferencia de [42679986787](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42679986787) que falló en segunda apertura, esta CI demuestra que **la restauración de Buscar no explica todas las fallas**. Mantener corrección de destino restaurado, pero no afirmar causa productiva.
- `nativeStatusSnapshot()` colecciona solo TextViews nativos, puede estar vacío tras `FaroV5SurfaceHost.install` que llama `setContent` y sustituye vistas. La ausencia de texto nativo NO demuestra ausencia de Compose; tampoco valida ACL/SQLite ni garantiza que un Compose root exista.
- Hipótesis de investigación nueva: falta distinguir estado de jerarquía Android nativa (`ComposeView` visible o no) y número de nodos Compose reconocibles (Chat/Buscar/Enviar/editores). El diagnóstico debe exponer únicamente clases y conteos, **no texto de mensajes ni snippets**.
- **B7 = RED_FIX**, same unit only. Siguiente paso test-only bounded Compose/native structural counters. No relajar 60s, cambiar código productivo, grant/restore policy, main, catálogo 172, G15, S4 ni Auto/Noche.
