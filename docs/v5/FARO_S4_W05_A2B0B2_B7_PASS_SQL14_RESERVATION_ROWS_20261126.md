# FARO V5 · W05-A2B0B2 · Captura y restauración SQL14 STAGED

## B7 — PASS acotado a DEV

- Código `8ae675ff9d94e0172bd5f618212cacbc5359f666`, cinco rutas exactas. [Guardia 45816178736](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45816178736) y [CI 45816179042](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45816179042) SUCCESS.
- [Artefacto original 15114873472](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45816179042/artifacts/15114873472). ZIP SHA-256 `3e0959c84353fa315ba212951d0e607da195d5df451441f6a0c06aa655250a69`. XML originales verificados, 13/13, cero fallos, errores u omitidas.
- ReservationRowsPort 6/6; XML SHA-256 `c600014042e1785c3497d029e564a605a32bd6fa222960ce53c71cedddcea239`.
- ReservationSchema 7/7; XML SHA-256 `32de5487e0743b029fe08025ad2948ba5c98ff0bb4458cb0ae0f734eed383ebc`.
- Comparación exacta de estado tras COMMIT y reapertura. Se verificaron histórico SQL13, reserva 2/liberación 1, rechazo sobre físico neto 3, reintentos idempotentes, revocación antes de COMMIT, UTF-8, DDL/FK, versión y archivo sellado.
- Las CI históricas 45816178907, 45816178654 y 45816178629 rechazaron el preflight de otra unidad disparado por rutas compartidas, antes de ejecutar tests. La prueba de esquema14 está incluida en la dirigida.
- Próximo A2B0B3 con su delta y permiso propio. SQL14 STAGED. V2 productiva, stock real, pagos, Auto/Noche OFF.
