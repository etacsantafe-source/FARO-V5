# FARO V5 — W04 B0C3B1 · Captura autenticada STAGED10

Fecha local 2026-11-20. Unidad `S4_W04_B0C3B1_HOUSEHOLD10_ARCHIVE_CAPTURE_DEV`. Commit funcional `6fe4e70c011256536575ba189493e4654538dddb` con padre READY `1d3fa8046aa012b2fabd2c1d8ab026ad6a3adf01`.

## B7 — PASS acotado a captura de stage sellado generado por FARO

- Se verificó el diff de seis rutas, el preflight del workflow y la guardia 44722980442 SUCCESS.
- CI JVM 44722980567 SUCCESS. Artefacto 14623645542 descargado e inspeccionado. XML JUnit con 5 casos, 0 fallos, 0 errores y 0 omitidos. Digest ZIP `sha256:59304499b64b2c722b4caf32de39e82744abc0b105aa98e5973ef889f34c52d5`, SHA-256 XML `52bf877bb55b4231fe3224c9ba5d687e4268f185511d12331c653400a8c0ac52`.
- Los casos comprobaron captura real de cinco archivos preservados byte a byte, Household con filas y revisión vacía, Finance9 y recibo Auxiliary4, rechazo de schema9, pointer obsoleto, origen activo, links/sidecars, companion incompatible y pérdida de lease.
- El modo nuevo de Reality abre en SQLite immutable/read-only desde archivos sellados bajo lease. Los cuatro companions usan los openers existentes con las versiones verificadas. El scope probado es el conjunto WAL sellado de FARO. B0C3B2 añadirá comprobación explícita previa del encabezado de journal en los archivos admitidos al roundtrip.
- Workflows históricos se dispararon por las rutas compartidas. W03-A 44722980379 y W03-D1 44722980031 fallaron en el preflight de sus unidades. Guardados 44722979630 y Reality8 44722980070 fallaron al resolver Kotlin 2.4.10 antes de ejecutar pruebas. Esos runs quedan fuera de la certificación B0C3B1.
- Permiso consumido. B0C3B2 conserva la identidad del archivo v6 y verifica restauración real de cinco stores y Household. Cutover sigue pendiente.
