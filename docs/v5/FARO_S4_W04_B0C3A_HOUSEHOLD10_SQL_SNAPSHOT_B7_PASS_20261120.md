# FARO V5 — W04 B0C3A · Lectura sellada y restauración SQL de Household10

Fecha local 2026-11-20. Unidad `S4_W04_B0C3A_HOUSEHOLD10_SEALED_SQL_RESTORE_DEV`. Commit funcional `e46917aa8ef8c70588226af9e4ac1576dca72d5a` con padre READY `73c561dfaa922177a2bdc6a275d51cb7106ae135`.

## B7 — PASS acotado a los puertos SQL STAGED10

- Diff verificado contra las cuatro rutas exactas del permiso. El preflight del workflow y la guardia 44721593129 resultaron SUCCESS.
- CI JVM 44721593248 SUCCESS. Artefacto 14621847809 descargado e inspeccionado. XML JUnit con 8 casos, 0 fallos, 0 errores, 0 omitidos.
- SHA-256 ZIP `fc534a5ce644e8806949143c9a68db6dd3ddb697f6da240341115d2114d00b9a`. SHA-256 XML `9a40d51f295ecb0149201a5866e62745021d1c3900047e737616713f0c48d1cc`.
- El XML verifica lectura canónica y fechas, revisión sin filas, replay idéntico, conflicto sin reemplazo, rollback antes de commit, versiones y archivos inadmisibles, límites de datos, integridad y preservación SQL de las tablas ajenas a Household.
- La autoridad de generación y su lease siguen a cargo del caller. Los puertos solo definen lectura sellada y restauración de las dos tablas Household. Capture/restore de cinco stores, recibos Auxiliary4, puntero, cutover y reapertura son pruebas de unidades posteriores.
- Permiso B0C3A consumido. Siguiente unidad `S4_W04_B0C3B1_HOUSEHOLD10_ARCHIVE_CAPTURE_DEV`, con preauditoría delta y permiso nuevo tras la guardia de este checkpoint.
