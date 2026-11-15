# FARO V5 - S4 W00-A - Preauditoria individual B1-B5
Fecha: 2026-11-15 AR. Baseline HEAD: f2f9e0503126acd0242708cf40c06984db5e175a. Unidad: S4_W00_A_REALITY_TWO_EXISTING_OWNERS_ATOMICITY_TEST.
Autorizacion humana explicita: abrir S4 formalmente solo para W00-A; encadenar pasos seguros sin W01-W11. V2 productiva/Auto/Noche OFF.

## B1 - Evidencia exacta
- HEAD: f2f9e0503126acd0242708cf40c06984db5e175a; writer RELEASED epoch 910, luego HELD 911 para DOC_ONLY. Guard 43757166688 SUCCESS.
- RealityStore.kt blob be72978b28d79366452a3f43c7018db72b2ca9f7: run con BEGIN IMMEDIATE, un RealityTransactionScope, COMMIT/ROLLBACK y recuperacion por excepcion; financePersistence y objectivePersistence en la misma conexion.
- FinanceAccountCreate.kt blob 1de8eb82029d29b0dbe42d3f633119793f44e8bf; RealityStoreFinanceT2Test.kt blob 3d3593f4be58826c2a7c63e0342c1f0c1ac98004 aporta API y tests individuales.
- RealityDomainOwnersT2Test.kt ya cubre Objectives+Matters, no Finance+Objectives. RealityStoreFinanceApplyT2Test.kt ya cubre receipt individual, no doble owner.
- store/reality/build.gradle.kts usa JUnit Jupiter y kernel apply; nuevo test v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/S4W00TwoOwnerAtomicityFeasibilityTest.kt no aparece en listado del directorio; workflow .github/workflows/faro-v5-s4-w00-a-two-owner-atomicity-directed.yml nuevo.
- Codigo inspeccionado: ausencia de Household owner real y host conjunto; ni pruebas fisicas G15 ni catalogo en esta unidad.

## B2 - Reutilizacion y comparacion V4.4
P02/P03/P07 y W00 previos: no copiar V4.4 ni aceptar doble Apply compras/finanzas. Reusar executor y Reality single-store, garantia de scope y tests previos; no repetir suite G4 ni tests Finance individuales. Delta: dos owners reales diferentes dentro de una transaccion compartida.

## B3 - Investigacion externa
ADAPTAR single SQLite con unica conexion/transaccion: https://www.sqlite.org/lang_transaction.html (BEGIN IMMEDIATE, error y rollback). REFERENCIA API Android: https://developer.android.com/reference/androidx/sqlite/db/SupportSQLiteDatabase.
DESCARTAR ATTACH + WAL para atomicidad multiarchivo: https://www.sqlite.org/wal.html explica atomicidad por DB, no conjunto. DESCARTAR dos commits secuenciales o compensacion opaca para pago P07 opcion A. Sin API paga, red, library nueva ni cambio de licencia.

## B4 - Solucion minima y dos rutas exactas
- Kotlin NUEVA: v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/S4W00TwoOwnerAtomicityFeasibilityTest.kt.
- Workflow NUEVO: .github/workflows/faro-v5-s4-w00-a-two-owner-atomicity-directed.yml (ampliacion explicita frente al candidato documental W00 de un solo archivo, necesaria para CI dirigida; ambas rutas solo de test).
- Cinco tests: T01 commit Finance+Objectives durable tras reapertura; T02 excepcion tras Finance antes de Objectives rollback; T03 rollback explicito despues de escribir ambos restaura ambos; T04 rechazo stale del segundo owner revierte Finance y conserva objetivo preexistente; T05 nested transaction falla cerrado revirtiendo Finance.
- Cada test usa RealityStore real local, no fake DB, sin telefono ni datos de usuario. No anadir owner:household, schema, codigo productivo ni catalogo.
- CI: ./gradlew --no-daemon :store:reality:test --tests ar.com.faro.v5.store.reality.S4W00TwoOwnerAtomicityFeasibilityTest y XML JUnit exactamente 5/0/0/0; guard seven-block-evidence del mismo SHA.
- B7 scoped: factibilidad de persistencia multiowner en un SQLite, NO ApplyPlan de dos authorities con grant/receipt ni certificacion Household+Finance (W06) ni desbloqueo de W07.

## B5 - Auditoria adversarial previa
- Probar fallo despues del primer owner, fallo en segundo, rollback explicito, nested scope y persistencia durable; no inventar receipt o COMMITTED.
- El scope no concede authority host/ACL. Review, foreground, revoke, generation y race final-check-to-SQL-COMMIT siguen NOT_TESTED aqui.
- W00-T04/T05 de receipt/UNKNOWN, recovery del Apply multi-binding, power loss y W06 Household+Finance quedan en STOP separado; no transferir PASS.
- Sin secretos, datos G15, main/V4.4, egress, V2 productiva, Auto/Noche, pago real, nuevo catalogo, schema ni Kotlin productivo.
- STOP si nueva suite falla compilar/XML o guard, tratar RED_FIX de esta misma unidad con evidencia. Exigir READY DOC_ONLY anterior al codigo y preflight exacto de ambas rutas.
