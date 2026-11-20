# FARO V5 · W04-B0B · B7 de la copia sellada Household10

Unidad `S4_W04_B0B_HOUSEHOLD10_STAGED_GENERATION_DEV`. SHA B6 `7ed40534e20bcca21e2888282a333848591f71e0`, padre READY `5fbaeff0d8bb78aa92dc5ef558da6a4aeafa9b35`.

## B6 — Implementación y evidencia

- Guardia documental `44665165140` SUCCESS. Preflight de cuatro rutas PASS y guardia de código `44665566350` SUCCESS.
- CI dirigida `44665566368`, job `136053865248`, SUCCESS. Artefacto descargado `14597970504`, ZIP SHA-256 `e8b323727efdc69aa9560fe2c80d8c1b4c739a37f412824cc429a1e66edd9357`. XML inspeccionado de manera independiente con **7/7**, 0 failures, errors y skipped.
- La copia válida pasa de Reality9 a Reality10, conserva los otros cuatro stores y el origen por bytes, y mantiene el puntero fijado. La suite valida versión Auxiliary4, rechazo de target activo o ruta forjada, copia divergente, hard links, symlinks y sidecars, formato no admitido, revocación de lease y ABA antes de emitir resultado verificado. El target con SQL aplicado durante la prueba ABA quedó sin activación o journal.
- Regresiones automáticas del SHA exacto: historial Reality 44665566297, V2 core M1/M2 44665566288 y G4 Reality 44665566405, las tres SUCCESS.
- Android y G15 NOT_RUN en este alcance. La API sigue limitada a una copia STAGED admitida bajo el lease exclusivo del host cerrado. Falta el backup portable autenticado que preserve Household y el cutover/reopen.

## B7 — PASS_SCOPED_W04_B0B_SEALED_STAGE

Consumir el permiso B0B. La unidad siguiente es B0C1, sección portable tipada, con guardia y permiso propios antes de su B6. CRUD, grants, Review/Apply, Undo y recuperación de ejecución se mantienen pendientes.
