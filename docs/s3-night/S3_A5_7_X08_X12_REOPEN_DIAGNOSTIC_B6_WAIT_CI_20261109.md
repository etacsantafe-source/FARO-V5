# FARO V5 — S3.A5.7 X08/X12 reopening diagnostics — B6 WAIT_CI
Fecha 2026-11-09 AR. A5 global OPEN; **NO PASS**. Unidad `S3_A5_7_X08_X12_BACKGROUND_SEARCH`.

## B6 — Evidencia observada y trabajo nuevo
- CI anterior exacta del código `dda9c3eceab6e30793432cfb829b75403783ce16` [run 42651371911](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42651371911) FAILED 1/1 tras reapertura después de ActivityScenario.recreate; guard [42651371907](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42651371907) SUCCESS. Auditoría `docs/s3-night/S3_A5_7_X08_X12_BACKGROUND_SEARCH_B7_RED_FIX_20261109.md` B7 RED_FIX.
- B1–B5 nuevos `docs/s3-night/S3_A5_7_X08_X12_REOPEN_DIAGNOSTIC_B1_B5_20261109.md` blob `2ceabf68b8583c74f16b879f6b9f5cf317f43c00`; permiso READY separado anterior `f9f1e9378a5f0af1cac6c1e7f74bd4a8c71491bd`.
- Test-only delta SHA `0d5bba653ceb3fed1d2eb6195b2a41d32721e63d` en una sola ruta `FaroV5S3A57SearchBackgroundRevokeUiTest.kt`: captura native TextViews cuando Compose waitUntil falla y conserva AssertionError+causa. NO cambia comportamientos productivos, ni rebaja aserciones, ni añade casos.
- CI nueva dirigida [run 42653795115](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42653795115), exact SHA `0d5bba65` **IN_PROGRESS** al emitir checkpoint. Guard [run 42653795270](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42653795270) IN_PROGRESS al checkpoint. Exigir XML/INSTRUMENTATION `OK (1 test)` y marcador 1/1 para PASS; si falla leer native status para localizar causa.
- Otros 47 casos anteriores PASS_SCOPED históricos intactos; red actual 1/1 NO cuenta como 48.
- Writer global CAS epoch640, liberar durante CI. V2 productiva/S4/Auto/Noche OFF, A3/X04/X05 bloqueados, catalog 172 sin cambio, Moto G15 NO_RUN (única campaña final).
- Próximo: CI exacta y auditoría independiente B7; si rojo, B1/B2/B5 causal y corrección mínima MISMA unidad. No otra unidad funcional mientras falte B7.
