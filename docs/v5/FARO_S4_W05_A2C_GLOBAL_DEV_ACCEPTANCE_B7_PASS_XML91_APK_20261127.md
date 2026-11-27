# FARO V5 — W05-A2C global · B7 PASS scoped DEV (91/91 + APK aislada)

**Fecha:** 27/11/2026 Argentina. **Unidad:** `S4_W05_A2C_GLOBAL_DEV_ACCEPTANCE`. **Rama:** `work/s3-night-20261102`.
**Resultado independiente:** `PASS_SCOPED_DEV_XML91_OF_91_APK`. **A2C0 y A2C1 CLOSED DEV**; W05-A2 sigue ABIERTO por A2D (lecturas/UX stock) y A2E (aceptación global inventario). No se afirma físico Moto G15 ni stock productivo.

## B7 — auditoría final independiente sobre código y evidencia viva

1. **B1–B5 propios**: `docs/v5/FARO_S4_W05_A2C_GLOBAL_DEV_ACCEPTANCE_B1_B5_20261127.md`, blob `352fc7d47ec13ab0d13e81f07c597a34701e894d`, checkpoint `6fcc081d11a63001ce15e9a33687650e48431618`, guardia documental `46044991837` completed/SUCCESS.
2. **Permiso acotado**: commit `77695e0a9799b7ade98fcba91dd7aad97471ac5a`, READY exact1 solo `.github/workflows/faro-v5-s4-w05-a2c-global-dev-acceptance.yml`; guardia de permiso `46045130167` completed/SUCCESS; B6 antes de READY no ejecutado.
3. **B6 únicamente workflow**: SHA exacto `b064960b4443f56776781744bc30279648da01cb`, un archivo cambiado (el workflow nuevo) y mensaje `FARO-UNIT:S4_W05_A2C_GLOBAL_DEV_ACCEPTANCE`. No hay modificaciones a fuentes o tests certificados de A2C0, A2C1, Android, Reality, owners, `main`, V4.4, permisos productivos ni catálogos.
4. **Guardia de código**: `46045293544` completed/SUCCESS, SHA `b064960b4443f56776781744bc30279648da01cb`, preflight parent READY exact1 y siete bloques verificables.
5. **CI global fresca**: [run 46045293622](https://github.com/etacsantafe-source/FARO-V5/actions/runs/46045293622), SHA `b064960b4443f56776781744bc30279648da01cb`, completed/SUCCESS, job `140662920321` completed/SUCCESS. Java21, SDK37 Android aislado. Todas las etapas Gradle, verificación de 11 XML y APK, y subida del artefacto en SUCCESS.
6. **XML originales validados dentro de la misma CI**, no suma de previas:
   - Generation SQL15 cutover/replay/read: **9 + 6 + 15 + 5 = 35/35**.
   - Household consume, Undo y regresión N-016: **8 + 9 + 10 = 27/27**.
   - Reality SQL15 atomic consume/Undo: **8 + 8 = 16/16**.
   - Android DEV host Review→Confirm→Apply y Undo: **5 + 8 = 13/13**.
   - **TOTAL 91/91** en once XML (11 suites distintas, 91 casos únicos), cero `failures`, cero `errors`, cero `skipped`, comprobación de archivo no vacío/unicidad. La APK `v2CoreDev` existe, tiene bytes positivos y el pipeline imprimió su SHA256. Es DEV aislada, no release.
7. **Artifact** `w05-a2c-global-dev-xml91-apk`, ID `15222466759`, tamaño `25300264` bytes, digest de paquete `sha256:0fa49032bdf95b701137ea9adbd4b1ecbd36f45106e605fd8ce3f57bfcd72cf6`, expired=false al consultar. Contiene los 11 XML originales declarados y APK DEV. La verificación de XML se hizo dentro de CI; **no** se proclama descarga/extracción externa independiente de este ZIP.
8. **Límites adversariales**: pruebas existentes cubren ticket SQL14 autenticado antes de SQL15, replay FBS2v12, journaling, ACTIVE/WAL, pointer/epoch ABA, lease, crash/rollback/idempotencia, Review/Confirm/Apply y Undo compensatorio autorizado; el alcance de los tests no prueba datos reales, load/performance, campaña G15 ni migración productiva. `stock.consume` N-035 y `undo.execute` N-016 comprobados como contratos/owners independientes. `stock.consume` nunca se deriva de receta, preview, compra o pago.
9. **Cierre administrativo**: consumir permiso de esta unidad a BLOCKED, registrar B7 con blob exacto y checkpoint DEV A2C global; writer global RELEASED tras CAS y guardia documental final SUCCESS **deben verificarse después de publicar este documento**. Si no, el turno sigue bloqueado.

**Siguiente etapa admitida solo con su propio B1–B5 y permiso:** W05-A2D lectura integral/UX stock. Después A2E B7 global de W05-A2. **No inventar A2C2**; no repetir A2C0 ni A2C1. No tocar G15 sin autorización física; V2 productiva, stock real, pagos, Auto/Noche OFF. Sin APIs pagas.
