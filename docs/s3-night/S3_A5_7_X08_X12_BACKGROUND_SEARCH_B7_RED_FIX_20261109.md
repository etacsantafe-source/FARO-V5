# FARO V5 — S3.A5.7 X08/X12 Search onPause — B7 RED_FIX
Fecha AR 2026-11-09. Unidad inmutable `S3_A5_7_X08_X12_BACKGROUND_SEARCH`. A5 GLOBAL OPEN.

## B7 — Auditoría de falla exacta, NO certificación
- B1–B5 originales `docs/s3-night/S3_A5_7_X08_X12_BACKGROUND_SEARCH_B1_B5_20261109.md` blob `1f3454767f0d183110e70ac111d8b04757535be8`; permiso READY anterior en commit `86a9f29575c385cbb01527dd50fb8122b752316b`.
- Código `dda9c3eceab6e30793432cfb829b75403783ce16` cambió solo cuatro rutas permitidas, sin productiva/main/A3/172/Auto/Noche/G15. Guard seven-block-evidence run [42651371907](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42651371907) **SUCCESS** mismo SHA; no sustituye prueba Android.
- Android API35 exact SHA: [run 42651371911](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42651371911), job 129278301671, **COMPLETED FAILURE**. Build de APKs success y Android emulator booteó; app/AndroidTest APKs instaladas Success.
- Instrumentación: `Tests run: 1, Failures: 1`; `ComposeTimeoutException: Condition still not satisfied after 60000 ms` en `FaroV5S3A57SearchBackgroundRevokeUiTest.explicitlyOpenChat(...:53)`, invocado desde test línea 132. La llamada de línea 132 ocurre **después** de verificar hit real, pause STARTED→RESUMED, redacción del hit, nueva búsqueda no disponible y ActivityScenario.recreate. No afirmar todos los pasos por un marcador independiente: localización del error solo implica que las aserciones precedentes no arrojaron excepción.
- `INSTRUMENTATION_CODE: -1` aparece también en salida FALLIDA, por lo que solo cuenta junto a `OK (1 test)`, ausente. No agregar caso 48 ni fingir PASS.
- Causa inmediata probada: después de recrear Activity, no se montó Compose Chat en 60s. Todavía **no sabemos** si la apertura nueva fue rechazada por owner/SQLite, falló el callback, o solo no se montó el árbol; no atribuir falla a revocación o al emulador sin diagnóstico.
- Riesgo real: reabrir una sesión después de background/recreate puede ser una ruta de recuperación incompleta. Ningún dato del Moto G15 tocado. Los 47 casos previos siguen certificados en sus SHAs y alcances.
- **Dictamen: B7 RED_FIX de misma unidad, NO PASS; bloquear siguiente funcional hasta nuevo B1–B5 y permiso por rutas, diagnóstico de status nativo y CI nueva.**
