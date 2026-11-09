# FARO V5 — S3.A5.7 X08/X12 — B1–B5 de restauración Search vs Chat

Fecha: 2026-11-09 AR. Unidad `S3_A5_7_X08_X12_BACKGROUND_SEARCH`. Baseline funcional `78ce6c4924b6d44af4cd5960c17815e2521c97ce`. Los 47 casos certificados permanecen congelados.

## B1 — Problema, aceptación y criterio estricto

CI exacta [42679986787](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42679986787) **FAILED 1/1** sólo en segundo `explicitlyOpenChat` tras `scenario.recreate`, con montaje de Compose Chat no encontrado y estado nativo vacío. Shell tiene navegación `rememberSaveable`: tras usar Buscar, debe poder restaurarse Buscar sin imponer Chat. Aceptación: tras click nativo real, reconocer pantalla Buscar restaurada, ejecutar nueva búsqueda real, obtener exactamente un hit canónico Workspace, sin grant stale ni falsos resultados.

## B2 — Reutilizar semántica existente

El saver en `FaroV5ShellNavigation.kt` guarda destinos secundarios con raíz Chat/otras, y `FaroV5ShellNavigationContractTest.savedPresentationStateRestoresValidContextAndFailsClosedWhenMalformed` mantiene esta política. `FaroV5SurfaceHost.install` usa `setContent` y reemplaza vistas nativas. Reutilizar prueba X08 de fondo real y el método de búsqueda; no copiar V4, no repetir los 47 casos previos ni ampliar pruebas de UI.

## B3 — Hipótesis y alternativas

A USAR: permitir al helper de **test** comprobar explícitamente el destino restaurado `Buscar en FARO` con un editor de búsqueda antes de intentar buscar con grant nuevo. B DESCARTADA: forzar navegación a Chat después del recreate; ocultaría la restauración real. C DESCARTADA: hacer match `Chat OR Buscar` sin distinguir destino, permisivo y podría ocultar regresión. D DESCARTADA: aumentar timeout, reintentar automáticamente, relajar aserciones de revocación o cambiar owners de producción. Android state saving de Compose: https://developer.android.com/develop/ui/compose/state-saving. Hipótesis respaldada por el código; no afirmar éxito Android previo.

## B4 — Ruta y pruebas

ÚNICA ruta funcional: `v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57SearchBackgroundRevokeUiTest.kt`.
- Agregar parámetro bounded `expectRestoredSearch: Boolean = false` al helper de apertura nativa: primera apertura exige Chat real; segunda, tras `recreate()`, exige `Buscar en FARO` y exactamente un editor de búsqueda.
- Permitir al helper `search(body)` reutilizar Buscar **ya abierto** sin navegación redundante de drawer; no omitir nueva consulta real. Final sigue exigiendo un único hit `Fuente: workspace.conversations`, cero `Aplicar cambios`, no grant reactivado sin click nativo, y que la búsqueda previa tras pausa haya sido `UNAVAILABLE`.
- CI Android exacta 1/1 + `OK (1 test)` + `INSTRUMENTATION_CODE: -1` + guard 14/14. Si roja, nueva evidencia B7 y corregir causa, sin fingir PASS.

## B5 — Adversarial y exclusividad

No sustituir cobertura revocación por test de navegación, ni permitir búsqueda antes del click fresco. No autoseleccionar conversación ni inventar contenido; tras recreate Search conserva UI, pero la autoridad de Search **debe ser nueva**. No crear más tests redundantes ni mutar código productivo; writer CAS antes de cada commit, permiso READY documental en commit padre y B7 previo RED_FIX de la misma unidad. No modificar `main`, catálogo 172, datos G15, S4, Auto/Noche ni V2 productiva.
