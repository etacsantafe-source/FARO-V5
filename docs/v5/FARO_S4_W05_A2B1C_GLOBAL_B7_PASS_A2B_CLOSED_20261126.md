# FARO V5 — W05-A2B1C y cierre global A2B en desarrollo aislado

## B7 — PASS GLOBAL DEV · A2B 9/9

- Repositorio etacsantafe-source/FARO-V5, rama work/s3-night-20261102.
  Unidad S4_W05_A2B1C_GLOBAL_RESERVATION_DEV_ACCEPTANCE.
- B1–B5 diferenciales certificados en
  FARO_S4_W05_A2B1C_B1_B5_WORK_PREAUDIT_20261126.md,
  blob f5cdc7884b92fbdc9a386c9b72f36633edbf40fd;
  permiso independiente READY exact2 sobre
  1978f8175f8a08f20b37caac42374b2d184674dd,
  guardia documental 45865558327 SUCCESS.
- Código final B6 a805f847089fb3cb647f8b578dd73f6fc6656eea,
  solo las dos rutas previstas: test global FBS2v11 real y workflow dirigido.
  Guardia siete bloques 45865955289 SUCCESS, CI 45865955416 SUCCESS
  en el mismo SHA, con preflight exact2 SUCCESS y build Gradle Android V2 DEV.
- Evidencia original CI:
  https://github.com/etacsantafe-source/FARO-V5/actions/runs/45865955416
  artefacto 15140458117:
  https://github.com/etacsantafe-source/FARO-V5/actions/runs/45865955416/artifacts/15140458117
  ZIP original auditado SHA-256
  e3100ab931bb6b6c6d72f1147776143c40713dfe2ac44e144f3a83f43a72e38d.
  XML test-results/testV2CoreDevUnitTest/TEST-ar.com.faro.v5.app.S4W05StockReservationGlobalDevAcceptanceTest.xml,
  SHA-256 e300c5748f015407e1b066ada2d16db76ec10fb8cd99fb37aa6112d222cb2472.
  JUnit XML original tests=2, failures=0, errors=0, skipped=0.
  Casos únicos:
    authenticFiveStoreRestoreThenSeparateReceiptsPreserveNetPhysicalOnRestart;
    globalNegativeAcceptanceDenyWithoutApprovalOrGrantAndAfterAba.
  APK aislado outputs/apk/v2CoreDev/app-v2CoreDev.apk,
  66,522,319 bytes, SHA-256
  e80f944e9d6bbd36d49e2c12b1c3e051feb0ac562be9a940b1ab9a5506cac94a.
- Aceptación integral: FBS2v11 auténtico con cinco stores y cutover SQL14
  COMPLETED, purchase10 + receipts7+3 + Undo7, neto físico3, reserva
  inicial1; release inicial1, reserve nuevo2, release nuevo1 con tres
  receipts COMMITTED independientes, reintento idempotente y readback
  después de reiniciar. Final físico3, reservado1, revisión8, cinco
  eventos append-only; compras, recibos y compensaciones intactos.
  Negativas: sobre-reserva, Review sin APPROVED, ACL revocada, pérdida
  de foreground, stale hostEpoch, root lease ocupado y pointer ABA.
- Predecesores A2B0A/B0B/B0C y A2B1A/B1B0/B1B1 conservan B7
  existentes y NO se repiten en esta unidad. A2B1B0 CI 45837426749
  XML 5/5; A2B1B1 CI 45864041512 XML 5/5; los demás seis B7 previos.
- CIERRE: W05-A2B 9/9 unidades B7 PASS, limitado a DEV aislado,
  con restauración y reservas sin stock real. A2C, A2D, A2E
  siguen OPEN/NOT_RUN bajo permisos independientes.
  No se habilita Available, consumo real, pagos, V2 productiva,
  Auto/Noche, modificación de main ni G15 físico: todo permanece OFF
  o NOT_RUN según corresponda.

Esta certificación NO significa una liberación productiva ni prueba G15.
Se requiere nueva auditoría B1–B5 y permiso separado para A2C.
