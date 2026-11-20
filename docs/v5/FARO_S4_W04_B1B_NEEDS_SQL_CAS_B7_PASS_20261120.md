# FARO V5 · W04-B1B · Persistencia SQLite de necesidades

Fecha local 2026-11-20. Unidad `S4_W04_B1B_NEEDS_SQL_CAS_DEV`. Código `51124c0e31915419075e8307d62962b61be90c23`.

## B7 — PASS de persistencia DEV

- CI 44748638104 y guardia 44748637785 SUCCESS para el SHA exacto. Artefacto 14634203701 descargado y verificado. SHA-256 del ZIP `83f5e664c6465e2c0c896086b2755dfb7c7f41839f44885b05cce20cc7a5422c` y del XML `26cc3f2d7f4c45c2a0c0761c6a5693e2cd7e84778cb871869486e6389d4846a5`. 6/6 pruebas, cero fallos, errores u omisiones. Localmente 6/6 de persistencia y 9/9 de regresión B1A, JVM/SQLite. Android físico no ejecutado.
- Las cuatro operaciones mantienen una revisión CAS de lista y fila, preservan valores después de reapertura e impiden reutilizar un scope SQL cerrado aunque el ID de transacción coincida. Rollback en interrupción y pérdida de fence antes de COMMIT. No se alteró DDL. Límites de cantidad, fecha y filas preservan la compatibilidad de la sección H10S.
- Las pruebas probaron dos operaciones simultáneas con la misma revisión y preservación del estado de los otros owners. La activación del host de Review/Apply y los recibos corresponden a C2. Undo e integración FBS2v6 de datos activos corresponden a D.
- El checkpoint de espera `a8ac5d5a` mantuvo READY después de B6. Guardia 44748975372 aplicó correctamente STOP hasta B7. Se corrigió el estado documental en `e6e0a138bb47212993aed1494a654f8a20784617`, guardia 44749463298 SUCCESS. El código funcional conservó el permiso original de su padre git.
- Runs históricos 44748637544, 44748637674, 44748637892 y 44748637562 pararon en sus preflights de otra unidad. 44748637777 repitió la aserción de Finanzas preexistente documentada en B1A. El alcance de este B7 es B1B.
- B1B consumido. La misma guardia documental 44749463298 verificó el delta B1–B5 de B1C1. B1 integral continúa abierto.
