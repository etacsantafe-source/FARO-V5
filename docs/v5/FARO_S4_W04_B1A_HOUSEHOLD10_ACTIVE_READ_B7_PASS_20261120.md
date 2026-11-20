# FARO V5 · W04-B1A · Lectura activa Household10 DEV

Fecha local 2026-11-20. Código `e937ca0a657b722fd158584e03207adbfde9747a`. Unidad `S4_W04_B1A_HOUSEHOLD10_ACTIVE_READ_DEV`.

## B7 — PASS de lectura DEV

- Las cinco rutas coinciden con el READY. Guardia 44746106811 y CI dirigida 44746107035 SUCCESS.
- Artefacto 14632839178 descargado e inspeccionado. ZIP SHA-256 `366e0c53a30860ec366f42e4e98f9796a94b1cc4fe77fcc007116c7bf95b4ff2`. Reality 4/4 con XML SHA-256 `0b5363a677cb87a9b9c789024ad706b5acab2e311c853a574ce39978b6428708`. Generation 5/5 con XML SHA-256 `07d67276b744939add430814baf454fc4dbca630c1d90ede38a703485c2e30ee`. Cero fallos, errores y omisiones.
- La sesión DEV usa el gate compartido y el lease existente, valida puntero y autorización al leer, y toma el snapshot desde la conexión Reality en modo solo lectura con WAL visible. Se verificaron revisión, bounds, UTF-8, reopen y rechazo de escritura.
- Las pruebas rechazaron pointer ABA, grant revocado, gate cerrado, lease ocupado, paths no canónicos, SQLite corrupta y versiones incompatibles. ARCHIVE10 continúa admitiendo lectura sellada. Finance9 mantiene su versión exacta. Las pruebas fueron JVM/SQLite. Android físico quedó sin ejecutar.
- Los workflows históricos 44746106798, 44746106776, 44746106884 y 44746106996 fallaron en el preflight de una unidad anterior. El run 44746106875 falló en `RealityFinance9ActiveDevGrantPortTest:175`. Se reprodujo la misma aserción sobre `98a114c79b3efc0838f36dc15b707a94ae286868` con Kotlin 2.4.21 y SQLite 2.7.1. El test espera que `finance.transfer.record` no emita un grant. B1A preserva ese comportamiento previo.
- Consumido READY de B1A. B1 integral pendiente. Sigue B1B bajo permiso individual.
