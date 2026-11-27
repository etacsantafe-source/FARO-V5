# FARO V5 — W05-A2C1 · B7 PASS integral DEV SQL15 (56/56 + APK aislada)

**Unidad:** `S4_W05_A2C1_GLOBAL_DEV_ACCEPTANCE`. **B7:** `PASS_SCOPED_DEV_XML56_OF_56_APK`, fecha 27/11/2026 Argentina, rama `work/s3-night-20261102`. **No es homologación de stock productivo ni del Moto G15**.
**HEAD funcional exacto:** `340e08983f0b69a6a5d37fa7369074f4715bbe5e`. Las seis unidades A–F ya estaban certificadas; en esta unidad no se modificó ni recompiló contenido nuevo en sus fuentes, sino que se ejecutó una matriz integrada fresca desde ese HEAD.

## B7 — auditoría independiente de la aceptación integral

1. **B1–B5 precedentes:** `docs/v5/FARO_S4_W05_A2C1_GLOBAL_DEV_ACCEPTANCE_B1_B5_20261127.md`, blob `9fbec0b786276edb6a326a5d1a2f4a0829fef828`, commit `e37378e07e1a67a47b1d4ff7542f2465e97b4252`, guardia documental `45982027334` completed/SUCCESS.
2. **Permiso acotado:** `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` en parent `3a49d5e75bdfde73ee63017498da96d3a182ae0e` READY para `S4_W05_A2C1_GLOBAL_DEV_ACCEPTANCE`, única ruta `.github/workflows/faro-v5-s4-w05-a2c1-global-dev-acceptance.yml`, guardia permiso `45982222016` SUCCESS. No hubo acceso a fuentes funcionales certificadas, `main`, V4.4, catálogo ni Realidad productiva.
3. **B6 único archivo:** commit `340e08983f0b69a6a5d37fa7369074f4715bbe5e`, workflow blob `8efbc2d9af47e10c95304e5932ebe64126531487`. Incluye `FARO-UNIT:S4_W05_A2C1_GLOBAL_DEV_ACCEPTANCE` en el commit y verifica parent permit READY exact1 + siete bloques. GitHub Actions CI guardia `45982428504`, completed/SUCCESS, SHA exacto.
4. **CI global independiente única:** [run 45982428740](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45982428740), `head_sha=340e08983f0b69a6a5d37fa7369074f4715bbe5e`, completed/SUCCESS, job `140449455501` completed/SUCCESS. Java21, Android SDK37 aislado, catálogo `N-035 stock.consume household` y `N-016 undo.execute undo-coordinator` verificados, los tres Gradle pasos en secuencia completed/SUCCESS: `:owner:household:test`, `:store:reality:test` y `:app:testV2CoreDevUnitTest` + `:app:assembleV2CoreDev`.
5. **XML original inspeccionado EN CI (mismo run y SHA), sin confiar en previas CIs:** verificador `Independently verify seven original JUnit XML suites and APK bytes` completed/SUCCESS. Sus siete suites y tests esperados son:
   - A `HouseholdStockConsumptionDevAdmissionTest` — **8/8**.
   - D `HouseholdStockConsumptionUndoDevAdmissionTest` — **9/9**.
   - Regresión Undo antiguo N-016 `HouseholdStockUndoDevAdmissionTest` — **10/10**.
   - B `RealityHousehold15ConsumptionApplyPortTest` — **8/8**.
   - E `RealityHousehold15ConsumptionUndoApplyPortTest` — **8/8**.
   - C `S4W05StockConsumptionDevApplyHostTest` — **5/5**.
   - F `S4W05StockConsumptionUndoDevApplyHostTest` — **8/8**.
   Total **56 tests, 0 failures, 0 errors, 0 skips**; un XML por clase, nombres de testcase únicos, ninguna clase ausente o vacía. Los 56 son CI en el **mismo HEAD**, no la suma de certificados históricos.
6. **APK aislada:** `:app:assembleV2CoreDev` completed/SUCCESS; verificador comprobó `v5/app/build/outputs/apk/v2CoreDev/*.apk` presente con bytes no nulos e imprimió su SHA256 en el log. No se afirma validación física ni instalación, firma de release o despliegue.
7. **Artefactos de pruebas:** `actions/upload-artifact@v4` completó SUCCESS en el mismo job. ID GitHub Actions **15190402388**, `w05-a2c1-global-dev-xml56-apk`, **25.296.572 bytes**, digest del ZIP de artefacto `sha256:eb6b49b3ddb2fec1e87416c67ec337bba0b81de19716ac7e1320d1bf0abba994`, expiración=false al verificar. Se preservan siete XML originales y APK. El verificador de CI examinó los XML; NO se afirma segunda extracción externa independiente del ZIP.
8. **Fronteras y adversarial:** la matriz fresca vuelve a ejercitar casos previamente desarrollados de origen Workspace+Reality COMMITTED, revisión humana separada N-016, idempotencia/repetición de Undo, diferencia físico/reservado, pointer ABA/epoch/root lease, rechazo de source forjado y recuperación crash sin doble Apply. **No** prueba generación de inventario real, módulo de pagos/finanzas, integridad de una base del usuario, G15 físico, penetración, Android release, migración productiva, pruebas bajo carga ni las futuras A2C2/A2C global.
9. **Disposición:** **B7 PASS_SCOPED_DEV_XML56_OF_56_APK** para aceptación integral A2C1 exclusivamente DEV. Al cerrar: `FARO_PROTOCOL_WORK_PERMIT.json` CONSUMIDO/BLOCKED, `last_code_review` a `340e08983f0b69a6a5d37fa7369074f4715bbe5e` con evidencia B7 blob exacto, checkpoint apuntando a auditoría A2C2 o secuencia A2C siguiente y writer global RELEASED. Ningún permiso nuevo para B6 de otra unidad; no repetir A–F.

**Límites operativos mantenidos:** V2 productiva OFF, stock real OFF, pagos OFF, FARO Auto/Noche OFF, `main` intacta. El cierre A2C1 DEV no constituye cierre de W05-A2C global.
