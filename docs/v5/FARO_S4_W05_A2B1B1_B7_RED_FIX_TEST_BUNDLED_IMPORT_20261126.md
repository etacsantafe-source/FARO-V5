# FARO V5 — W05-A2B1B1 — B7 RED_FIX de fixture Android/JVM

Unidad S4_W05_A2B1B1_STOCK_RESERVATION_REVIEW_APPLY_HOST.
Código inicial e597449b561edc10b50e3756e6a371ef834dbc4a, cuatro rutas exactas.

## B7 — RED_FIX (NO PASS)

- Guardia de protocolo 45862745576 SUCCESS, preflight exact4 SUCCESS.
- CI 45862745835 FAILURE en :app:compileV2CoreDevUnitTestKotlin.
- Error real del log: UNRESOLVED_IMPORT de androidx.sqlite.driver.bundled.BundledSQLiteDriver
  en S4W05StockReservationDevApplyHostTest.kt línea 11.
- La compilación de producción avanzó; la CI no ejecutó las cinco pruebas nuevas.
  El paso JUnit XML quedó SKIPPED; no hay archivo original para certificar.
- Causa localizada: acceso directo a SQLite que usa el fixture de generación,
  pero no existe en el classpath de compilación de tests del módulo Android app.
- RED_FIX mínimo: retirar el import y los dos bloques de SQL directos del fixture
  de test (semillas de finance_budget_limit y household_need). Conservar la
  reconstrucción autenticada FBS2v11 real, 7+3 recibidos, Undo7, reserva2,
  release1, corte SQL14 COMPLETED y comprobación de receipts.
- Permiso original exact4 permanece acotado. Reintento con solo dos rutas
  SUBSET: el archivo de test y el workflow dirigido. Preflight del permiso
  completo contra HEAD padre y diff exacto del subconjunto.
- No modificar código del host, RealityStore, G15 ni producción. V2,
  stock real, pagos, Auto/Noche permanecen OFF.
- Continuar con B6 RED_FIX, CI original JUnit 5/5 y B7 independiente.
  A2B1C no está autorizado mientras A2B1B1 no tenga B7 PASS.

Esta auditoría registra error real, no certifica nuevas pruebas.
