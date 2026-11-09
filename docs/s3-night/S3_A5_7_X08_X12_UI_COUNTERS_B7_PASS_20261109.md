# FARO V5 — S3.A5.7 X08/X12 — B7 PASS real Android API 35

**Fecha:** 2026-11-09 AR. **Unidad exacta:** `S3_A5_7_X08_X12_BACKGROUND_SEARCH`. Código probado `e09643c4310441d0b8afc7a810470928994d6867` (rama `work/s3-night-20261102`). **Dictamen de esta subunidad: PASS SCOPED 1/1**. El cierre integrado global A5/A5.7 NO se deduce de un solo test.

## B7 — Evidencia verificable y decisión

- GitHub Actions [run 42717372040](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42717372040), job `129502284645`: `workflow_dispatch` NO; workflow `push` automático en el HEAD exacto, **COMPLETED/SUCCESS**. Java 21, Android SDK 37, dos APKs V2 DEV aislados, emulador API35, instalación e instrumentación real.
- Log de instrumentación: **`OK (1 test)`**, **`INSTRUMENTATION_CODE: -1`** y **`S3_A5_7_SEARCH_BACKGROUND_REVOKE_REAL_PASS 1/1`**. El workflow exige ambos marcadores y emite el tercero exclusivamente después de las comprobaciones; no hay `FAILURES!!!` en este run.
- Guardia siete bloques [run 42717371972](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42717371972): **SUCCESS** sobre mismo SHA. Preflight anterior [run 42716964826](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42716964826) **SUCCESS** sobre commit documental padre `1d846318`.
- Test único `FaroV5S3A57SearchBackgroundRevokeUiTest.x08ForegroundLossRevokesSearchAndRequiresNewUserClick`: Chat DEV aislado, mensaje genuino Workspace, hit de búsqueda, pausa Android real, revocación visible y nueva consulta no autorizada `UNAVAILABLE`, sin falsos ceros; sólo click nativo nuevo tras recreación restablece permiso y hit canónico real. Asserts de cero `Aplicar cambios` preservadas.
- El delta `e09643c4` es **solamente diagnóstico de test** para fallos futuros: estructura de `View` por clases/visibilidad y conteos Compose de textos FIJOS, sin mensajes ni snippets; no modificó aplicación, permisos, aserciones, tiempos, catálogo, Reality, ni `main`.
- Anteriores ejecuciones rojas `42653795115`, `42675954072`, `42678117217`, `42679986787`, `42682116828` se conservan como evidencia histórica de variabilidad. El PASS se limita al SHA/run observado: **no demuestra ausencia de intermitencia en múltiples corridas**. No repetir 47 pruebas previas certificadas.
- **B7 = PASS_SCOPED; desbloquea revisión/documentación de A3** con nuevo permiso y writer CAS. **A5.7 integrado sigue OPEN** para otros cierres no certificados por esta prueba, y la campaña física G15 permanece reservada para el final. V2 productiva, Auto, Noche, S4 OFF; catálogo 172.
