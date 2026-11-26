# FARO V5 — W05-A2B1B0 · Writer de reservas SQL14 DEV

## B7 — PASS acotado a DEV

- Código inicial b2705e1c7733d74e0339bcb50ef7b023f0bfb603, cuatro rutas exactas.
  Primer intento CI 45835861255 FAILURE de compilación Kotlin SMARTCAST_IMPOSSIBLE;
  documentado como B7 RED_FIX en FARO_S4_W05_A2B1B0_B7_RED_FIX_KOTLIN_SMARTCAST_20261126.md.
  No se contabilizaron pruebas en ese intento.
- Código final RED_FIX 6885c5427e5be674870369496b0a7ed013f9a33f,
  dos rutas dentro del permiso original: puerto y workflow. Guardia
  45837426645 SUCCESS y CI 45837426749 SUCCESS por SHA exacto.
- Preflight de permiso READY del padre, compilación Gradle, cinco tests SQL14
  y verificación del JUnit XML original: todos SUCCESS.
- Artefacto original https://github.com/etacsantafe-source/FARO-V5/actions/runs/45837426749/artifacts/15126362068
  descargado y auditado independientemente. ZIP SHA-256:
  d4d8db9e581cf2715b3132dbfd9bdcfceb5b4bdfbfd291ff22d8782fcdc21fd9.
  XML TEST-ar.com.faro.v5.store.reality.RealityHousehold14ReservationApplyPortTest.xml
  SHA-256 e1f308199b8b0f1488ceec22071a15e5d61987c7af2035dc2d28b9b67097010d.
  JUnit: 5 tests, failures=0, errors=0, skipped=0; cinco nombres distintos.
- Casos certificados: reserva 2 de físico 3 y release 1, reapertura íntegra,
  sobresuscripción y revisión obsoleta denegadas, intent duplicado,
  principal ajeno, release excesiva, rollback/crash antes de COMMIT,
  scope externo denegado, revocación y read-only.
- Undo N-016 de SQL13 sigue DESHABILITADO sobre SQL14. No se
  autoriza disminución física bajo reserva vigente ni un nuevo recibo
  Undo sin protección cruzada certificada.
- NO significa host Review→Confirm→Apply completo: A2B1B1 abierto.
  NO significa aceptación global: A2B1C abierto.
  Available UNKNOWN hasta A2C. G15 físico NOT_RUN.
  V2 productiva, stock real, pagos, Auto/Noche OFF.

B7 formalmente PASS solo para S4_W05_A2B1B0_SQL14_RESERVATION_ATOMIC_APPLY_PORT,
con evidencia original. A2B1B1 necesita delta audit, guardia y permiso READY propio.
