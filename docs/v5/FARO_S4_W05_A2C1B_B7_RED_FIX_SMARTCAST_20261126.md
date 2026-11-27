# FARO V5 — W05-A2C1B B7 RED_FIX (compilación Kotlin)

**Unidad:** `S4_W05_A2C1B_CONSUME_ATOMIC_SQL15_APPLY`.
**Commit B6 auditado:** `a2b33b4f32838b383a398d8e2ad85a83761de9bf`.
**Guardia del código:** [45928195293](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45928195293) — completed/success, mismo SHA, 14 casos sintéticos y preflight exact4 PASS.
**CI funcional:** [45928195593](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45928195593) — completed/failure, mismo SHA. Job `stock-consumption-atomic` 140274433559. Logs reales inspeccionados.

## B7 — RED_FIX, nunca PASS

El commit funcional altera exactamente las cuatro rutas del permiso READY de A2C1B; el preflight contra el padre fue PASS. Gradle `:store:reality:test` falló en `:store:reality:compileKotlin`, con **SMARTCAST_IMPOSSIBLE** en `RealityHousehold15ConsumptionApplyPort.kt:123`: la propiedad pública nullable `current.source`, declarada en otro módulo, no admite smart cast Kotlin tras `current.source == null`. **JUnit NOT_RUN, XML inexistente, artifact no publicado.** La falla secundaria `upload-artifact` deriva de la ausencia legítima de XML y no es una prueba funcional.

**Hipótesis de reparación acotada y falsable:** tomar una copia local de la fuente validada, p. ej. `val verifiedSource = current.source ?: return false` antes del `if`, y usar `verifiedSource.remainingThousandths`. No cambiar el contrato de consumo ni autorizar stock real. Segunda corrección en workflow, misma unidad: su paso `Verify exact four routes` debe aceptar **un subconjunto no vacío** de las cuatro rutas permitidas en un `RED_FIX`, sin permitir rutas extras ni relajar `faro-protocol-gate.py --mode preflight`; ejecutar el gate sobre cada archivo realmente modificado. Los cambios se limitan a esas dos rutas ya preauditadas.

**Disposición de B7:** `RED_FIX` para este SHA, **no** B7 PASS. Permiso READY continúa estrictamente para la **misma unidad** con B1–B5 preservados, guardia propia y writer CAS nuevo antes de B6 correctivo. Con CI verde comprobar las ocho pruebas y XML original, plus alcance y negativos B5 (incluidos casos no cubiertos por las ocho pruebas dirigidas) antes de declarar cierre acotado. C–F siguen NOT_READY; V2 productiva, stock real, pagos y FARO Auto/Noche OFF; G15 NOT_RUN.
