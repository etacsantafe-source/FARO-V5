# FARO V5 · W05-A2C1B B7 PASS acotado (SQL15 atomic consume DEV)

**Unidad certificada:** `S4_W05_A2C1B_CONSUME_ATOMIC_SQL15_APPLY`; únicamente desarrollo aislado SQL15, sin stock ni pagos reales ni integración en Android productivo.
**Código final:** `09a7f72445b3a8a9c691d1a79096e6d468afb929` (`fix RED_FIX` sobre `a2b33b4f32838b383a398d8e2ad85a83761de9bf`). El intento inicial falló compilación por Kotlin SMARTCAST_IMPOSSIBLE; su auditoría `RED_FIX` está en `docs/v5/FARO_S4_W05_A2C1B_B7_RED_FIX_SMARTCAST_20261126.md`, evidencia sellada en el padre del código correctivo.

## B7 — pruebas y auditoría observadas

- Guardia de protocolo del código final **45928809081 SUCCESS** para el SHA exacto. Anterior permiso RED_FIX guardia **45928653403 SUCCESS**; guardia primer B6 **45928195293 SUCCESS**.
- CI específica **45928809060 completed/success** para el SHA exacto; job `stock-consumption-atomic` **140276386627 SUCCESS**.
- GitHub Actions verificó preflight contra el padre de código con solo dos rutas modificadas, todas dentro de las cuatro del permiso; log `FARO_PROTOCOL_PREFLIGHT_PASS S4_W05_A2C1B_CONSUME_ATOMIC_SQL15_APPLY 5988b828c1c1e547d99ce0bfe7164a2749bfd993`.
- Gradle `:store:reality:test --tests ar.com.faro.v5.store.reality.RealityHousehold15ConsumptionApplyPortTest` **BUILD SUCCESSFUL**, 3m08s; paso CI `Verify original JUnit XML` SUCCESS. Validación exacta del XML original: **8 tests, 0 failures, 0 errors, 0 skipped; 8 casos únicos**. Se verificó desde el workflow; no se afirma una segunda extracción independiente del ZIP.
- Artefacto XML: GitHub Actions **15167840813**, nombre `w05-a2c1b-consumption-atomic-xml`, ZIP SHA256 `dd5c7fe1497618004064aa383448a466eb71846b9deff3ec7c2060f026b95bd3`, 830 bytes, existente/no vencido.
- Archivo funcional nuevo `RealityHousehold15ConsumptionApplyPort.kt`: stock.consume DEV solo con Review canónica, principal, generación, posición SQL15 de origen verificable; protege saldo físico no reservado, saldo de origen, revisiones stale, idempotencia, historial y transacción única existente. `RealityStore` restringe el writer por flag DEV OFF de fábrica y gate vivo. Sin capacidad de Apply para host normal ni ejecución real de stock.
- Las ocho pruebas dirigidas cubren consumo, reservas e historia, sobreconsumo, revisión stale y origen ajeno, identidad duplicada entre ledgers, rollback/falla/scope extraño, modo por defecto lectura, revocación/gate cerrado, reservationRef y tiempo negativo, segundo consumo sobre disponible y fecha retroactiva.

**Límite de la certificación:** `B7 PASS` es **acotado al contrato probado y a 8/8 casos dirigidos**, no prueba física de G15 ni habilitación general ni aceptación de todo SmartCompra. Este run no ejecutó explícitamente cada subtipo adversarial previsto en B5 (p. ej. inyección SQLITE_BUSY o saturación 8192); conservarlos como cobertura negativa pendiente para auditoría integradora, sin afirmar haberlos probado. No confundir con el fallo del primer B6 ni con workflows históricos no relacionados.

**Disposición:** A2C1B B7 PASS DEV acotado. Consumir/bloquear su permiso, mantener A2C1C–F **NOT_READY** hasta B1–B5, permiso y guardia propios. No repetir A2C1A/B certificados. Writer debe terminar RELEASED, V2 productiva OFF, stock real OFF, pagos OFF, Auto/Noche OFF. G15 NOT_RUN.
