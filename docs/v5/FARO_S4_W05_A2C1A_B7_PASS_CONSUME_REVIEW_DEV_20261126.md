# FARO V5 — W05-A2C1A B7 PASS acotado DEV

**Unidad:** `S4_W05_A2C1A_CONSUME_REVIEW_ADMISSION`  
**Código auditado:** `3885084a6447febde9586da2c5a02414e3c4aa40`  
**Guardia:** [45925067509](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45925067509) — completed/success, `head_sha` exacto.  
**CI funcional:** [45925067636](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45925067636) — completed/success, `head_sha` exacto.  
**Job:** `stock-consumption-review` (`140264364104`), todos los pasos SUCCESS.  
**XML original conservado:** artifact `15165624289`, `w05-a2c1a-consumption-review-xml`, digest de archivo ZIP `sha256:1002e9113a26caa7943c06ad285c06a15cc486d3ae0ea332df575965d451a38a`.

## B6 — evidencia

El commit funcional modifica exactamente cuatro rutas admitidas en el permiso READY heredado, según la comprobación de scope/preflight del workflow. Gradle `:owner:household:test --tests ar.com.faro.v5.owner.household.api.HouseholdStockConsumptionDevAdmissionTest` completó `BUILD SUCCESSFUL` en 2m 3s. El paso de CI `Verify original JUnit XML` parseó el archivo original `TEST-*HouseholdStockConsumptionDevAdmissionTest.xml` y exigió `(tests,failures,errors,skipped)=(8,0,0,0)`, con ocho nombres únicos; el paso concluyó SUCCESS. El artifact original fue publicado en el mismo run y SHA.

## B7 — auditoría final acotada

**PASS scoped DEV, 8/8 pruebas según XML original verificado por CI.** Las ocho pruebas cubren Review sin Apply, disponibilidad física no reservada, origen autenticado y unidad, descriptor N-035, consentimiento/contexto, ABA y revalidación, payload canónico y entradas inválidas/reservationRef. La evidencia se apoya en el parser del runner y artifact original: este turno verificó logs, estado de jobs, workflow y metadatos de artifact; **no realizó una segunda extracción independiente del ZIP**.

**Límites:** solo Review/admisión aislada, sin mutación efectiva de stock; sin stock real, consumo de reservas, pagos, V2 productiva, FARO Auto histórico ni Moto G15 físico. A2C1 global OPEN. A2C1B–F NO READY: cada sucesor requiere B1–B5, guardia y permiso independiente antes de código. No repetir A2C1A B6.
