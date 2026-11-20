# FARO V5 — W04 B0C3B2 · Restauración real de Household10

Fecha local 2026-11-20. Unidad `S4_W04_B0C3B2_HOUSEHOLD10_ARCHIVE_REPLAY_DEV`. Commit funcional `b3f4fd0a0c082ef7dd5a6ac864094e8c087a1f17` con padre READY `98dc6ddc50460d1930a7eae0367ab66c3437cae0`.

## B7 — PASS de FBS2v6 con capture/replay real

- Se verificaron las cuatro rutas exactas y la guardia 44724997438 SUCCESS. CI JVM 44724997544 SUCCESS.
- Artefacto 14623896463 descargado e inspeccionado. XML JUnit 7 casos, 0 fallos, 0 errores, 0 omitidos. Digest ZIP `sha256:d4661e8790b5faf6f008d743885038d81ef80834acd77c2e11ce163334287687` y SHA-256 XML `561eed4350816e6af897749f7d16345ba5ad296951fca858b6c6ff047baf992c`.
- Roundtrip autenticado real con igualdad de cinco stores, Household, Finance9, recibo Auxiliary4 y revisión no cero de Workspace. Se verificó el reintento exacto y la revisión Household preservada con cero filas.
- Se rechazaron v5, contraseña incorrecta y archivo alterado antes de modificar el target. Se verificaron interrupciones tras restaurar stores, tras pasar a 10 y tras el commit Household, con rechazo de operación o cifrado ajeno y base Finance9 divergente.
- El target activo, hardlinks y archivo fuera de modo WAL se rechazaron. Durante esta unidad el puntero permaneció en origen salvo la prueba negativa que lo cambió expresamente para demostrar el rechazo.
- La suite histórica C3B1 se disparó por la ruta compartida en run 44724997426 y se detuvo en su preflight de otra unidad antes de ejecutar código.
- B0C3B1 y B0C3B2 completan la captura y restauración STAGED de B0C3B. Permiso consumido. B0 sigue abierto hasta B0D1/D2.
