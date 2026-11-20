# FARO V5 — W04 B0D2 · Activación y recuperación Household10 DEV

Fecha local 2026-11-20. Unidad `S4_W04_B0D2_HOUSEHOLD10_ACTIVATE_RECOVERY_DEV`. Código `7381c8767a062a740b4729d1fa353499eb654611` con padre READY `231a61ef8f02b67eb5d35f9c2d8525f4de1dbb9c`.

## B7 — PASS integrado de cutover DEV

- Diff verificado contra sus cuatro rutas. Preflight y guardia 44728570497 SUCCESS. CI JVM 44728570347 SUCCESS.
- Artefacto 14625736200 descargado e inspeccionado. XML JUnit 9 casos, 0 fallos, 0 errores, 0 omitidos. Digest ZIP `sha256:ff6838d5a72e517536f48a1ac878f9b62ac302ddc936e2b4929a7e84e3ccb3f2`, SHA-256 XML `e53df2593fbd12f1225394248b8cd316210dc196c6249709a5e26fcdd3fcf1d3`.
- Activación con copia real FBS2v6 verificada, Household y Finance9, recibo Auxiliary4 y origen intacto. Recovery idempotente comprueba puntero ID+secuencia, journal COMPLETED y replay-fence.
- Pruebas adversariales verificadas: interrupción antes de validación, tras CAS, tras fence y durante rollback, fallo de salud, datos cambiados, lease concurrente o perdida, autorización revocada, sustitución de archivo y ABA.
- El runtime usa el bloqueo DEV de root existente, requiere host cerrado y confirmación de primer plano, fija una intención durable con huellas de origen, destino y cifrado, y delega el switch al coordinador journaled. El caller de mantenimiento recibe el estado de la selección y el puntero.
- Permiso de esta unidad consumido. El cierre integral B0 enlaza la evidencia de las nueve unidades de infraestructura.
