# FARO V5 · W05-A2B0C0 · Lectura SQL14 ACTIVE DEV

## B7 — PASS acotado a DEV

- Código `f5ebdf3633c60ba16ca198021b7ebe0fdab60c4e`, seis rutas exactas. [Guardia 45822974573](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45822974573) y [CI 45822974925](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45822974925) SUCCESS.
- [Artefacto original 15118922441](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45822974925/artifacts/15118922441), ZIP SHA-256 `4cb6e7aaba3d692712db01729844d32c452d40f985926422bd3631788f169777`. XML original 11/11, 5 lectura y 6 cutover, cero fallos, errores u omitidas.
- FaroV5Household14ActiveDevReadHostTest.xml 5/5; XML SHA-256 `a8768ea2150159684d91f81b8a108f4b13e04481f27a74c3f226d3928ddc8024`.
- FaroV5Household14Evidence2DevJournaledCutoverTest.xml 6/6; XML SHA-256 `6432bfec5b7f7d39c6e3379f9b78c4541ae9fa5c03f1d557520b037354977fb1`.
- Snapshot SQLite DEFERRED de físico y reservado por principal. Se probó lectura real de un RELEASE en WAL, cinco stores intactos, SQL13 y SQL14 sin ticket bloqueados, revoke, lease ocupada y ABA. C1 real completa el fixture.
- Próximo A2B1A con permiso propio. Disponibilidad pendiente A2C. V2 productiva, stock real, pagos y Auto/Noche OFF.
