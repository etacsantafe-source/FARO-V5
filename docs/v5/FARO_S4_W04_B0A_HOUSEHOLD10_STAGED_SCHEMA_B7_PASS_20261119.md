# FARO V5 · W04-B0A · B7 del esquema Household10 aislado

Fecha local 2026-11-19. Unidad `S4_W04_B0A_HOUSEHOLD10_STAGED_SCHEMA_DEV`. SHA B6 `8dfd37aad5a3bab8306fe901e7989fec962cc091`, padre READY `fc9a35e2495a2f50354450dea9fd12e4dc1e5884`.

## B6 — Implementación y evidencia

- Guardia del permiso `44663835794` SUCCESS, preflight de tres rutas y guardia del SHA B6 `44664204914` SUCCESS.
- CI dirigida `44664205036`, job `136049428945`, **SUCCESS**. Artefacto descargado y verificado `14597696043`, ZIP SHA-256 `3817a52620caed07db8f8a9a2991ef77f71f6044389b78afbb18259f3676bb41`. XML inspeccionado, **6/6**, 0 failures, errors y skipped.
- Copia separada 9→10, archivo origen idéntico, apertura Finance y procedencia preservadas. Rollback de DDL más versión y reintento. Rechazo de layout anterior/incompleto/ya migrado, FK desactivadas o inconsistentes, filas fuera de rango y apertura del esquema 10 por el modo Finance9 activo.
- Regresiones automáticas en el mismo SHA: G4 Reality domain owners 44664204902, historial Reality 44664204887 y V2 core M1/M2 44664204923, las tres SUCCESS.
- Android y G15 NOT_RUN. El instalador es interno y sigue sin un puerto de generación ni montaje. No hay respaldo Household ni CRUD.

## B7 — PASS_SCOPED_W04_B0A_STAGED_SCHEMA

Cerrada la migración SQL de una copia aislada; consumir el permiso. El próximo paso es validar identidad, sellado y puntero de cinco stores mediante W04-B0B, con guardia documental y READY propio. Backup/restauración, cutover, ACL, Apply y Undo mantienen unidades aparte.
