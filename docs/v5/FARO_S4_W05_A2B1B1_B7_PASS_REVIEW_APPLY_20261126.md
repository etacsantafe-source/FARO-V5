# FARO V5 · W05-A2B1B1 — Reserva y liberación Review→Apply DEV

## B7 — PASS acotado a DEV

- Predecesor A2B1B0 B7 PASS (5/5) no repetido. B1–B5 de esta unidad
  evidenciados en FARO_S4_W05_A2B1B1_B1_B5_WORK_PREAUDIT_20261126.md,
  guardia de permiso 45838883899 SUCCESS, cuatro rutas exactas.
- Código B6 inicial e597449b561edc10b50e3756e6a371ef834dbc4a:
  guardia 45862745576 SUCCESS, preflight SUCCESS, CI 45862745835
  FAILURE por UNRESOLVED_IMPORT en fixture Kotlin Android.
- RED_FIX documentado en FARO_S4_W05_A2B1B1_B7_RED_FIX_TEST_BUNDLED_IMPORT_20261126.md,
  guardia 45863799571 SUCCESS.
- Código final 4f26fd97446bcdec113a8f724fbb8aed7b387384, cambio acotado al
  test y al workflow dentro de las rutas del permiso; guardia
  45864041608 SUCCESS y CI 45864041512 SUCCESS del mismo SHA.
- Compile Android DEV :app:testV2CoreDevUnitTest, preflight READY original,
  cinco tests reales y validación JUnit XML original: SUCCESS.
- Artefacto original GitHub 15139926525:
  https://github.com/etacsantafe-source/FARO-V5/actions/runs/45864041512/artifacts/15139926525
  Descargado y verificado independientemente.
  ZIP SHA-256 161dd4e5f030650ace2cb9bf3c7cb54741c918a8ef4843e86c0a29b66a84af23.
  XML original TEST-ar.com.faro.v5.app.S4W05StockReservationDevApplyHostTest.xml
  SHA-256 46df3219971728a1d6745c986e667c1699407bb06a4ad39249e37a68284b688b.
  JUnit: tests=5, failures=0, errors=0, skipped=0, cinco nombres únicos.
- Casos certificados: dos consentimientos distintos y aprobación foreground;
  reserva2 + release1 con receipts independientes y reopen real; stale Review
  y sobresuscripción fail-closed; recuperación desde receipt COMMITTED sin
  duplicar reserva; revocación, host epoch, pointer ABA, root lease exclusivo.
- Fixture restauró FBS2v11 autenticado y cutover SQL14 COMPLETED antes de abrir
  host. No se concede permiso de stock real, segundo COMMIT, background o modelo.
- A2B1C global acceptance NO_READY hasta delta B1–B5 y su guardia/permiso.
  A2C unavailable/available UNKNOWN; G15 físico NOT_RUN; V2 productiva,
  stock real, pagos, Auto/Noche OFF.

Certificación B7 PASS solo para
S4_W05_A2B1B1_STOCK_RESERVATION_REVIEW_APPLY_HOST.
No declara A2B global cerrado: 8/9 unidades, falta A2B1C.
