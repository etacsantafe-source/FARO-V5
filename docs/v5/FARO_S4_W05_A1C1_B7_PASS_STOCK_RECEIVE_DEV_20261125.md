# FARO V5 · W05-A1C1 · Recepción parcial N-035 SQL12 DEV

## B7 — PASS acotado

- Código `0335c7657bc81f3d89a0709508c562f5e1099aac`, guardia [ 45605547137 ](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45605547137) SUCCESS. Verificados preflight local y remoto de las siete rutas.
- [CI 45605547164](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45605547164) SUCCESS, job 139175354107. Artefacto original [15018530682](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45605547164/artifacts/15018530682) descargado y verificado. XML de 8/8, cero fallos, errores u omitidas.
- ZIP SHA-256 `53e3353dc34e433e1505662dc365836255062c25db3563aad322c2f5bd5b6753`. XML SHA-256 `a6222c2dcddb8dabcd3dd3c5bef5156ca021e5146dd62e034b46886a46b61b90`.
- Compra N-036 confirmada de 10. Primer recibo de 7, pendiente 3. Segundo recibo de 3, pendiente 0. Reintento con el recibo durable y mismo físico. Reapertura y lectura persistente.
- Rechazos por exceso, unidad distinta, compra o artículo ausente, principal diferente, grant N-036, background, revisión del ledger obsoleta, epoch y puntero ABA. Rollback del evento y revisión, y conciliación tras COMMIT sin replay. Los hosts usan SQLite real con fixture del cutover COMPLETED. La operación de cutover se certificó aparte.
- Próxima unidad A1C2A, SQL13 STAGED para compensaciones, bajo permiso propio. W05-A1 DEV OPEN, Moto G15 físico NOT_RUN. V2 productiva, stock real, Apply productivo, pagos y Auto/Noche OFF.
