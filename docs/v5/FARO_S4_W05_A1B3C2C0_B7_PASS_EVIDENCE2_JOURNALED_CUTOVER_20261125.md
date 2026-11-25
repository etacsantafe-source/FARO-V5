# FARO V5 · W05-A1B3C2C0 · Evidence2 SQL11 cutover DEV

## B7 — PASS acotado
- Código: `f96bbb154ca09450d4123f3eac69b24fe12185c4`. Diff de tres rutas exactas, con READY en el padre `d1a8e4472ea6267bf15a167710f0616a418b691b`.
- [CI funcional 45528363926](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45528363926) y [guardia 45528363998](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45528363998) SUCCESS sobre el mismo SHA.
- [Artefacto original 14983154037](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45528363926/artifacts/14983154037), ZIP SHA-256 `4bdb63523b1fd7a5d8c44b2f590bfb0e6e6ad6563b0713578073b0439216d4d0`. Auditados los tres JUnit XML: Evidence2 cutover 10/10, Evidence1 cutover 8/8, Evidence2 autorización 8/8. Total 26/26, cero fallos, errores u omitidas.
- La suite observa journal, puntero completo, fence RESTORE y lectura de H10/H11R en SQLite real. Cubre reingreso, dos puntos de interrupción, rollback, revocación, ticket corrupto/enlazado, rechazo ante journal competidor y divergencia del archivo autenticado.
- Alcance JVM DEV de generación cerrada. C2C1 Android ACTIVE, A1C recepción, W05-A1 global y G15 físico siguen pendientes. Permiso C2C0 consumido. V2 productiva, stock real, pagos y Auto/Noche OFF.
