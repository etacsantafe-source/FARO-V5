# FARO V5 — S3.A5.7 X08/X12 — B7 RED_FIX de diagnóstico Compose (09/11/2026)

**Unidad:** `S3_A5_7_X08_X12_BACKGROUND_SEARCH`. **NO CERTIFICADO**, A5 permanece OPEN. SHA funcional diagnosticado `0d5bba653ceb3fed1d2eb6195b2a41d32721e63d`; no modificar historial de 47 PASS.

## B7 — Dictamen causal basado en CI exacta

- Instrumentación real Android API 35, run [42653795115](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42653795115), job `129286788765`: **COMPLETED FAILURE**. Compilación e instalación de los dos APK PASS; instrumentación `Tests run: 1, Failures: 1`, sin `OK (1 test)` ni marcador `S3_A5_7_SEARCH_BACKGROUND_REVOKE_REAL_PASS 1/1`. Guard seven-block run [42653795270](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42653795270) SUCCESS mismo SHA; no reemplaza test.
- Stack: `ComposeTimeoutException: Condition still not satisfied after 60000 ms`, helper `explicitlyOpenChat` línea 72, invocado desde **línea 105** (PRIMERA apertura nativa de Chat del test `x08ForegroundLossRevokesSearchAndRequiresNewUserClick`). La recreación todavía NO se había ejecutado. El documento inicial suponía segundo reopen desde ejecución previa, pero esta CI demuestra fallo más temprano; no confundir.
- La instrumentación del SHA anterior añadió `catch (failure: Exception)`, pero `ComposeTimeoutException` pasó sin adjuntar `nativeStatusSnapshot()`; el tipo de fallo de Compose no quedó incluido en la captura elegida. La causa de por qué no montó Chat continúa desconocida; este defecto impide el diagnóstico. No afirmar fallo de SQLite/ACL ni restauración.
- **B7 = RED_FIX**, no PASS, sin repetición de 47 previos; continuar SOLO la misma unidad. Revisar B1–B5 por hipótesis concreta, asegurar la captura de fallos de aserción sin relajar condiciones ni demorar test, ejecutar CI exacta 1/1, auditar.
