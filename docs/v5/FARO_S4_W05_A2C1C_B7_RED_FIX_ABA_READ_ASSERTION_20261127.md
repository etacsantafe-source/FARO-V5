# FARO V5 · W05-A2C1C — B7 RED_FIX de fixture ABA

**Unidad única:** `S4_W05_A2C1C_STOCK_CONSUMPTION_REVIEW_APPLY_HOST`.
**B6 original:** `b50a7f8aca168c48fbc15a6a290e5484cd277908`. **B6 RED_FIX previo:** `b742e87f01783e4e8f5f33192a1f3be096ebf294`.
**Guardia de este código:** [45939174920](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45939174920) — completed/success, SHA exacto.
**CI dirigida:** [45939174991](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45939174991) — completed/failure, SHA exacto, job `140309277446`.

## B7 — RED_FIX real (NO PASS)

Gradle compiló la app, el grafo y el test. Se ejecutaron cinco pruebas dirigidas: **4 PASS y 1 FAILURE**, `rootLeaseEpochAndPointerAbaDenySecondApplyHost()` en `S4W05StockConsumptionDevApplyHostTest.kt:428`; `java.lang.IllegalStateException`. XML original de CI negativa existe en artefacto `15171404204`, ZIP sha256 `e578bce9fd040aa5b107f4b8f73839c383d6f18cd213df58b88522371161d3b6`; no confundir con XML 5/5 verde.

### Diagnóstico comprobado

La prueba ejecutó el cambio ABA de `FileActiveGenerationPointer`: `compareAndSet(selected,other)` y `compareAndSet(other,selected)`. La secuencia monotonía del pointer aumentó, por lo que `FaroV5Household15Evidence2DevJournaledCutover.hasCompletedSelection(root,selected)` ya no certifica la selección vieja. El `S4W05StockConsumptionDevApplyHost` y el `RealityStore` conservan fence/lease y rechazan posiciones de ese host invalidado. En línea 427 la prueba correctamente exige que el Apply no quede COMMITTED; **en línea 428 erróneamente lee el stock del host inválido como si todavía tuviese admisión**, causando excepción esperable. Las cuatro pruebas restantes pasaron. Es un fallo de aserción/fixture, NO justificación para debilitar el fencing SQL15.

### Corrección acotada propuesta

Modificar solo `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W05StockConsumptionDevApplyHostTest.kt`: sustituir la línea 428 por `assertThrows(IllegalStateException::class.java) { rig.position() }`, manteniendo la denegación de Apply y el ABA. Modificar además solo el workflow A2C1C, `.github/workflows/faro-v5-s4-w05-a2c1c-consume-apply-host.yml`, para que el RED_FIX de 2 rutas compruebe el diff exacto de ambas, que todas pertenecen a las cuatro preauditadas, y ejecute el preflight obligatorio sobre las rutas realmente modificadas. Nunca relajar la protección de producción, root lease, recibos ni persistencia SQL15.

**Alcance:** dos rutas (subconjunto del permiso original exact4). Permiso `READY` conserva B1–B5 evidenciados y stages `B6=READY_TO_IMPLEMENT`, `B7=PENDING_FINAL_AUDIT` para que `faro-protocol-gate.py` valide el nuevo intento. El campo `last_code_review` registra `RED_FIX` para este código y su evidencia, y deberá existir en el padre del commit correctivo. No transferir a A2C1D–F.

**Aceptación exigida:** guardia documental SUCCESS, escritor CAS nuevo, preflight exact2, guardia código SUCCESS, CI dirigida SUCCESS del SHA exacto, archivo JUnit XML original 5 tests / 0 fails / 0 errors / 0 skipped, y auditoría B7 posterior. G15, stock real, pagos, V2 productiva y Auto/Noche permanecen OFF / NOT_RUN.
