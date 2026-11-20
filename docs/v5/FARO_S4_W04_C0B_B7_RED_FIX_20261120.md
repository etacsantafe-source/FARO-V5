# FARO V5 — W04-C0B · B7 RED_FIX de B6 inicial — 2026-11-20

## B7
Unidad `S4_W04_C0B_AUTHORIZED_PRICE_HISTORY_READ_DEV`, código `b7cd33869fdb8309bf694357a7539a05fff7f620`.
Guardia del código `44787942287` **SUCCESS**, preflight exact5 **PASS**.
Workflow C0B `44787942512` **FAILURE REAL** en `:app:compileDebugUnitTestKotlin`:
`S4W04CommercialPriceReadHostTest.kt:3` — Kotlin `UNRESOLVED_IMPORT` para `androidx.sqlite.driver.bundled.BundledSQLiteDriver`.
No hay XML ni prueba JVM PASS; el artefacto no pudo subirse. Los módulos fuente `:store:generation:compileKotlin` y `:app:compileDebugKotlin` sí compilaron, pero eso NO equivale a suite verde.

Inspección estática del mismo archivo de prueba: el caso de historial vacío usa `unpriced`, mientras el mock comercial en el fixture aprueba sólo `priced`. Ese caso necesita un grant legítimo para `unpriced` y no debe reinterpretar rechazo ACL como historial vacío. Corregir ambas fallas en un único delta de TEST, sin cambios de código productivo.

**Resultado B7 del SHA inicial: RED_FIX (NO PASS).** Permite únicamente reparación de la misma unidad antes de volver a evaluar B7.

## B5 delta adversarial previa a la reparación
1. Quitar import `BundledSQLiteDriver` del test app: no agregar dependencia Gradle fuera del scope.
2. Inicializar Need y su revision a través del **host Household10 existente**: seleccionar conversación DEV, Review Add, aprobar, Apply y recuperar NeedId real desde snapshot. No fabricar ID/filas SQLite, no sumar un nuevo root lease.
3. Los permisos del fixture habilitan temporalmente Add sólo durante su setup; tras setup, únicamente `shopping.snapshot.read` y `commercial.price_history.read` con ACL independientes.
4. Fijar expectativa `revision=1` del Need creado por Apply y comprobar que la lectura no cambia la revisión ni agrega registros comerciales. Receipt del setup no es receipt de lectura.
5. Habilitar grants reales para `priced` y `unpriced` del test: historial vacío debe devolver Observed(0) y NO un rechazo por ACL.
6. Verificar por CI `:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04CommercialPriceReadHostTest` 9/9, XML 0 errores y artefacto. Guard del SHA funcional. Mantener las pruebas de revocación tardía, foreground, epoch/ABA, sourceRef opaca y única lease.
7. No tocar `FaroV5GenerationStoreGraph.kt`, `S4W04HouseholdNeedApplyHost.kt`, `S4W04CommercialPriceReadHost.kt`, workflow ni la app productiva para este RED_FIX. El permiso mantiene las cinco rutas exactas; commit siguiente usa sólo la ruta de test autorizada.
8. El workflow **antiguo** W04-B1A `44787942245` falló por dispararse cuando cambia el grafo compartido y exige preflight de otra unidad: es una incompatibilidad de trigger histórica independiente, no evidencia de fallo de C0B. NO modificar B1A sin permiso específico.

Esta redacción es evidencia de B7 RED_FIX, no certificación funcional. C0B todavía OPEN.