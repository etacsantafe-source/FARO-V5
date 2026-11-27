# FARO V5 — W05-A2C1C · B7 RED_FIX fixture Android/JVM

**Unidad:** `S4_W05_A2C1C_STOCK_CONSUMPTION_REVIEW_APPLY_HOST`. **Código B6 inicial:** `b50a7f8aca168c48fbc15a6a290e5484cd277908`; cuatro rutas exactas sobre permiso READY, sin activación productiva. **Guardia:** [45938195109](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45938195109) completed/success del mismo SHA; preflight inicial y permiso exact4 PASS.

## B7 — RED_FIX (NO PASS)

- **CI dirigida** [45938195386](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45938195386) completed/failure, job 140306170046.
- Gradle ejecutó generación y compilación app V2CoreDev; el código de producción `store:generation:compileKotlin` y `app:compileV2CoreDevKotlin` avanzó. La caída ocurrió **solo** en `:app:compileV2CoreDevUnitTestKotlin`: `UNRESOLVED_IMPORT` de `androidx.sqlite.driver.bundled.BundledSQLiteDriver` en `S4W05StockConsumptionDevApplyHostTest.kt:10`.
- Es la misma incompatibilidad de classpath que el RED_FIX de A2B1B1 (documento `FARO_S4_W05_A2B1B1_B7_RED_FIX_TEST_BUNDLED_IMPORT_20261126.md`). La fixture importó el driver SQLite de los tests del módulo generation, que no está disponible en el módulo app. **Pruebas nuevas NOT_RUN, no hay XML original; upload-artifact secundario FAILURE por ausencia del archivo.**
- **Hipótesis correctiva mínima falsable:** en la nueva fixture app eliminar solo el import `BundledSQLiteDriver` y los dos bloques `BundledSQLiteDriver().open(...).use` que insertaban filas auxiliares de `finance_budget_limit` y `household_need`. Esas semillas no son requisito para la evidencia de consumo; conservar construcción auténtica FBS2v12 con compra y receipts, reserva/release, consume/undo sintéticos, cutover SQL15 COMPLETED y revalidación por host.
- Ajustar solo el nuevo workflow A2C1C para aceptar en RED_FIX un **subconjunto no vacío** de las cuatro rutas originales, comprobado con `git diff`, `permit.paths` y el preflight obligatorio contra el padre del commit. En este intento exact2: **solo test + workflow**; no tocar `FaroV5GenerationStoreGraph`, host, `RealityStore` ni módulos productivos.
- Mantener B1–B5 y el permiso READY **solo para la misma unidad** hasta que CI y guardia originales sean verificadas. No transferir autorización a A2C1D–F. Sin PASS artificial; con CI verde exigir XML original test>0, 0 fallos/errores/omitidos y B7 independiente. Reportar casos B5 no ejecutados.
- V2 productiva, stock real, pagos, Auto/Noche y Moto G15 físico: OFF / NOT_RUN.

**Disposición:** B7 **RED_FIX** para `b50a7f8aca168c48fbc15a6a290e5484cd277908`, no cierre. Writer CAS RELEASED después de checkpoint y guardia documental antes de retomar B6.
