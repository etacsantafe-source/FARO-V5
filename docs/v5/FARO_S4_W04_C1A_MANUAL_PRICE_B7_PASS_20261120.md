# FARO V5 — W04-C1a · B7 PASS scoped DEV · Observación manual Evidence v1

Fecha local: 2026-11-20. Rama `work/s3-night-20261102`.
Unidad `S4_W04_C1A_MANUAL_PRICE_OBSERVATION_DEV`.
HEAD funcional final `42c7a6366b7aeced94250ffe84ed9fd7f6694ef1`. Prev C0B B7 `55cd674af79b0ee0f6d287311ded0fe5da9fe12f`.

## B7 — Auditoría final independiente

### Alcance real verificado
C1a agrega captura y confirmación de observaciones manuales de precio **sólo** en Evidence schema1 existente:
- `prepareManualPriceObservation` NO escribe; exige needId/productKey seleccionado explícitamente por C0A, necesidad OPEN, snapshot+revision, fence de generación/pointer/epoch/principal, foreground y dos permisos independientes.
- `confirmManualPriceObservation` exige confirmación humana por callback confiable y permiso `commercial.evidence.record` OBSERVATIONAL_WRITE N-034; jamás usa `commercial.quote` READ_ONLY para escribir.
- `S4W04ManualPriceObservationHost` arma un `PreparedOperation` con `CommercialEvidencePayload.payload`, `sourceKind=MANUAL`, `DependencyStampSet.empty()`, y key estable por principal+generación+secuencia+epoch+needId+requestId, sin idempotencia basada en texto de nombre.
- Una única sesión `Household10DevApplySession` usa `CommercialEvidenceRecordExecutor` y `CommercialEvidenceStore.record` con la raíz y shared gate ya existentes; nunca abre otro root ni exporta store mutable. Revalidación de permisos antes de INSERT; si se revoca después de commit, retorna `RecoveryRequired` para reintentar con igual key sin duplicar, no afirma rollback.
- Sólo persiste producto, comercio, monto en unidades menores, moneda, fuente de texto, observedAt opcional, acquiredAt del store. Cobertura localidad/validez/presentación declarada UNKNOWN (false). Si el usuario proporciona localidad, vigencia, promoción, condiciones, presentación o elegibilidad tipadas, devuelve `REQUIRES_VERSIONED_TERMS` sin degradar a sourceRef. Sin FX, pagos, stock o mutación Finance/Reality.

### Rutas y contratos
Código funcional `42c7a6366b7aeced94250ffe84ed9fd7f6694ef1`: EXACTAMENTE las cinco rutas del permiso previo READY `S4_W04_C1A_MANUAL_PRICE_OBSERVATION_DEV`:
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
2. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04HouseholdNeedApplyHost.kt`
3. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04ManualPriceObservationHost.kt`
4. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04ManualPriceObservationHostTest.kt`
5. `.github/workflows/faro-v5-s4-w04-c1a-manual-observation.yml`
B1–B5 documento `FARO_S4_W04_C1A_MANUAL_PRICE_B1_B5_20261120.md` @blob `91a0acaeb2a6ca9c5b474c4e6a4939241fe4b317`. Guardia del READY `44803332132` SUCCESS. Preflight de cinco rutas `FARO_PROTOCOL_PREFLIGHT_PASS` documentado en CI. Guardia code `44803997203` SUCCESS SHA exacto.
No cambios en `:evidence`, esquema, backup, portability, Kotlin C0A/C0B, catálogo, Gradle, main/V4.4 o app productiva.

### CI, XML y pruebas reales
- GitHub Actions `44803997142`: **SUCCESS**, sobre `42c7a6366b7aeced94250ffe84ed9fd7f6694ef1`.
- `:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04ManualPriceObservationHostTest`: **BUILD SUCCESSFUL**, test JVM dirigido.
- JUnit XML `testDebugUnitTest/TEST-ar.com.faro.v5.app.S4W04ManualPriceObservationHostTest.xml` extraído del artefacto y parseado independientemente: `tests=9, failures=0, errors=0, skipped=0`.
- Artifact GitHub `14663835058` — `w04-c1a-manual-schema1-xml`, ZIP SHA-256 `6ebdd3f54172e13e89729fc2e7efc85e06b796bd4f47ef4b4e22b49688b56e4e`.
- Matriz: T01 prepare sin write, commit y persistencia/reopen/backup v1: PASS. T02 cancelación: PASS. T03 términos no representables: PASS. T04 grants N-033/N-034 independientes: PASS. T05 replay/conflicto idem: PASS. T06 validaciones amount/source/date/request + constructores: PASS. T07 Need completada/stale: PASS. T08 foreground/epoch/pointer ABA: PASS. T09 revocación post-commit, RecoveryRequired y replay sin duplicar: PASS.
**Limitación:** pruebas JVM de integración DEV con SQLite, no UI Android ni G15; no migración Evidence schema2 ni cotización contextual completa.

### Incidencias ajenas a esta unidad
Los workflows históricos W04-B0 `44803997012` y `44803997072` se dispararon por modificaciones de archivos compartidos y fallaron; el run C1a y la guardia canónica pasaron. No se afirma que esos triggers quedaron corregidos ni se reabre B0 certificado en este scope.

### Veredicto, estado y handoff
**B7 PASS SCOPED DEV para C1a**. Consumir permiso individual C1a READY→BLOCKED, `last_code_review.sha=42c7a6366b7aeced94250ffe84ed9fd7f6694ef1` result PASS con evidencia de este documento; `B6=PUBLISHED_VERIFIED`, `B7=PASS_CONSUMED`.
**C1 global todavía OPEN** porque falta C1b términos tipados/vigencia/localidad versionados. Siguiente `S4_W04_C1B_TYPED_COMMERCIAL_TERMS_MIGRATION_DEV`: revisar la preauditoría C1b, congelar rutas exactas, guard/READY propio antes de B6. C2/C3 aún no READY. No repetir W04-A/B0/B1/C0A/C0B/C1a.
No ISO/IRAM, APIs pagas, modificación de main, activación V2 productiva/Auto/Noche, ni campaña física. Revalidar HEAD y writer RELEASED al reanudar.
