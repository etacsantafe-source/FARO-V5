# FARO V5 — S3.A5.7 X08/X12 — B7 INFRA STOP / RED_FIX

Fecha AR: 2026-11-09. Unidad `S3_A5_7_X08_X12_BACKGROUND_SEARCH`. Código exclusivo test `de7347c0266b837957e6a5e3b23fbe38f2616e62`. A5 OPEN, no certificar. Enlace CI dirigida: https://github.com/etacsantafe-source/FARO-V5/actions/runs/42673273294

## B7 — Auditar lo que efectivamente ocurrió

- Guard siete bloques [42673273578](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42673273578): **SUCCESS** exacto SHA. Sólo certifica permiso/evidencias/historial, NO Android.
- Test Android X08/X12 [42673273294](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42673273294): **COMPLETED FAILURE**, 0 jobs en `/actions/runs/42673273294/jobs`, sin job ID, sin compilación, sin APK, sin instrumentación, sin XML, sin `OK (1 test)`, sin `1/1`. La prueba real en este SHA **NO SE EJECUTÓ**. No atribuir esto al cambio `catch(AssertionError)` ni al emulador; causa de fallo pre-job aún desconocida.
- Acción de reintento dirigido `rerun_failed_workflow_run_jobs(42673273294)`: GitHub responde **403** `This workflow run cannot be retried` (cero jobs fallidos que reejecutar). No se relanza ni duplica trabajo antiguo.
- Prueba histórica `0d5bba65` sí corrió: FAILURE 1/1 de Compose en primera apertura; el diagnóstico no adjuntó el estado nativo. Cambio `de7347c0` sólo captura `AssertionError` para revelar status si test corre; no rebaja aserciones, no modifica producto.
- Las **47 pruebas PASS_SCOPED anteriores** permanecen por sus SHAs, sin repetición. X08/X12 esta subunidad no cuenta como PASS ni como caso 48.
- **Dictamen B7: RED_FIX / INFRA_STOP / NO_CERTIFIED.** Registrar permit BLOCKED y puntero. No admitir otra unidad funcional todavía según guard B7: la próxima continuación debe obtener un Android job ejecutable en el mismo SHA, inspeccionar `nativeStatusSnapshot`, y recién con `OK (1 test)` auditar PASSED. No tocar catálogo/producción/S4/Auto/Noche/G15.
