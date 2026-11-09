# FARO V5 — S3.A5.7 X08/X12 Android background Search revoke — B6 WAIT_CI
Fecha AR 2026-11-09. Unidad `S3_A5_7_X08_X12_BACKGROUND_SEARCH`. **NO B7 PASS**; global A5 OPEN.

## B6 — Implementación y verificación pendiente
- B1–B5 `docs/s3-night/S3_A5_7_X08_X12_BACKGROUND_SEARCH_B1_B5_20261109.md` blob `1f3454767f0d183110e70ac111d8b04757535be8`, commit `1c0c00b9248df47f11e6d8e575005095719012dc`. Permiso READY separado commit `86a9f29575c385cbb01527dd50fb8122b752316b`; writer CAS epoch639 adquirido antes de escribir, dueño FARO.MANUAL.S3A5_7.X08_BACKGROUND_SEARCH.
- Código exacto [`dda9c3eceab6e30793432cfb829b75403783ce16`](https://github.com/etacsantafe-source/FARO-V5/commit/dda9c3eceab6e30793432cfb829b75403783ce16) cambia **solo cuatro archivos permitidos**: DEV Activity.onPause, DEV Authority.Session close grants, NUEVA clase Android UI y NUEVO workflow. No cambio de fuentes shipping, catálogos, manifest, main ni 47 pruebas históricas.
- Emulación Android API35 dirigida: https://github.com/etacsantafe-source/FARO-V5/actions/runs/42651371911 — **IN_PROGRESS** al guardar este checkpoint; requiere `OK (1 test)`, `INSTRUMENTATION_CODE: -1`, marcador `S3_A5_7_SEARCH_BACKGROUND_REVOKE_REAL_PASS 1/1`, SHA exacto y build correcto. No inferir PASS de una build.
- Guardia de siete bloques para SHA exacto: https://github.com/etacsantafe-source/FARO-V5/actions/runs/42651371907 **COMPLETED SUCCESS**. No significa que la prueba Android terminara.
- Otros workflows amplios se dispararon automáticamente por cambiar sources DEV; no son motivo para contar otra vez pruebas anteriores ni para certificar la unidad nueva.
- Aceptación: Search con hit real; pause STARTED y resume redacted; retry UNAVAILABLE sin falso cero; recreate+clic nativo nuevo restaura hit. Sin hardware físico.
- **Siguiente:** leer run 42651371911, comprobar instrumentación completa. En rojo: causa/root RED_FIX misma unidad, sin nueva funcional; verde: B7 auditoría y puntero, writer CAS. Mantener A5 global OPEN, A3 X04/X05 bloqueados, X03/X09/X10 pendientes, V2 productiva/S4/Auto/Noche OFF, G15 campaña final.
