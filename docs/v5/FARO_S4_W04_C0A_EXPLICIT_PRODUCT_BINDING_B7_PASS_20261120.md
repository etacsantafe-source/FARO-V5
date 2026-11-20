# FARO V5 — W04-C0A explicit need↔product selection — B7 PASS scoped DEV

Fecha: 2026-11-20 (Argentina). Rama: `work/s3-night-20261102`.
Unidad: `S4_W04_C0A_EXPLICIT_NEED_PRODUCT_MATCH_DEV`. Código exacto: `ebaaf5afbabda5a848fd266a5296a85ff597ef1f`.

## B7 — Auditoría final independiente del delta

### A. Alcance realmente implementado
Se implementó **únicamente** un binding candidato, efímero y no persistente, entre `HouseholdNeedId` y `CommercialProductKey`. El usuario debe elegir explícitamente entre claves presentadas; ninguna coincidencia fuzzy por nombre, marca, cantidad o unidad. La captura conserva una necesidad tipada completa, la revisión global, revision de need y fence de generación/puntero/epoch/principal. Revalidate deniega después de cambio de necesidad/completado/borrado/revision, generación/ABA/identidad o revocación de admisión. Las claves comerciales son opacas: no ejecutan URLs. Sin SQL nuevo, sin Apply, receipt, lectura/escritura Evidence, mutación Household, Finance, stock ni egress. C0B hará la admisión real de ambos owners y verificará provenance de producto.

### B. Control de permisos y diff
El commit `ebaaf5afbabda5a848fd266a5296a85ff597ef1f` incorpora exclusivamente tres rutas del permiso `READY` (preauditoría exacta @blob `c5f0b533fb66d0a55dca4ad25117ab85eb394849`):
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialNeedProductSelection.kt`
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04CommercialNeedProductSelectionTest.kt`
- `.github/workflows/faro-v5-s4-w04-c0a-match.yml`

Mensaje contiene `FARO-UNIT:S4_W04_C0A_EXPLICIT_NEED_PRODUCT_MATCH_DEV`. Workflow C0A ejecutó preflight exact3 contra `HEAD^` — PASS. Guardia obligatoria `44782895205` **SUCCESS** del SHA funcional. No hubo reescritura ni nueva certificación de W04-A/B0/B1.

### C. CI JVM y XML reales
- Workflow dirigido: `44782895246` — completed **SUCCESS** del SHA `ebaaf5afbabda5a848fd266a5296a85ff597ef1f`; job `w04-c0a-explicit-selection`.
- Gradle `:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04CommercialNeedProductSelectionTest` — `BUILD SUCCESSFUL`, ejecutó suite dirigida.
- Paso `Require exact C0A JUnit XML 12 of 12` — **SUCCESS**, `W04_C0A_EXPLICIT_SELECTION_XML_PASS 12/12`.
- Artefacto ZIP `14652917867`, nombre `w04-c0a-explicit-need-product-xml`, SHA-256 `a2b47b2d6733c4064f622dcde78bcdbce29c64320e30864752324fd39fd6a368` (log + descarga inspeccionada).
- Inspección independiente de `testDebugUnitTest/TEST-ar.com.faro.v5.app.S4W04CommercialNeedProductSelectionTest.xml`: **tests=12, failures=0, errors=0, skipped=0**; 12 casos individuales presentes.

### D. Auditoría adversarial
1. Selección explícita y misma denominación en dos needs con IDs diferentes: PASS.
2. Sin selección explícita / clave desconocida / opciones duplicadas, vacías o más de 200: PASS.
3. Need inexistente, completado o duplicado, eliminación y corrección posterior: PASS.
4. Revisión global/por need, nombre, cantidad y unidad cambiada: PASS.
5. Generation, pointer ABA por secuencia, host epoch o principal cambiado: PASS.
6. Owner no admitido, revocación al inicio o en la comprobación final: PASS.
7. Clave no canónica, fence sin identidad o secuencia válida: PASS.
8. Source URL como clave opaca sin ejecución, selección inmutable sin efectos de escritura: PASS en modelo puro.

**Limitación real:** son pruebas JVM/modelo efímero. NO prueban admisión efectiva de host, permiso `commercial-evidence`, lectura cruzada, base commercial Evidence, cotización manual, Android API35, interfaz, G15 físico, egress o B7 global W04. C0A no concede nada de eso. El compilador mostró aviso futuro `DATA_CLASS_COPY_VISIBILITY_WILL_BE_CHANGED_WARNING` para el constructor interno de `CommercialNeedProductCandidate`; la compilación Kotlin actual es PASS. Registrar revisión de compatibilidad al tocar C0B o actualizar versión Kotlin; no reclasificar aviso como fallo de C0A.

### E. Veredicto y próxima puerta
**B7 PASS SCOPED DEV, C0A cerrado.** Consumir el permiso individual READY→BLOCKED y mantener `last_code_review.sha=ebaaf5afbabda5a848fd266a5296a85ff597ef1f` con evidencia de este documento. No repetir C0A. Próximo trabajo **C0B**, revalidar B1–B5 delta y congelar propias rutas/READY tras guardia antes de B6; no heredar permiso C0A.

V2 productiva OFF. Auto/Noche OFF. Ninguna prueba física autorizada. Revalidar HEAD/CI/writer antes de nuevo trabajo.