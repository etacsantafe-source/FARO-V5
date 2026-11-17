# FARO V5 — S4 W01 B3B1B1B1B1B1A, B5 independiente para reconciliación READ
Fecha 2026-11-17. Evidencia B1–B5 base `docs/v5/FARO_S4_W01_B3B1B1B1B1B1_FOREGROUND_RECONCILIATION_B1_B5_20261117.md` blob `a5817860bdfb4c43f17bb5169320d3a6c29f29e1`, guard `44131877296` SUCCESS. MODO DOC_ONLY, sin codigo ni permiso en este mismo commit. Delimitación: `S4_W01_B3B1B1B1B1B1A_FOREGROUND_SQL_RECONCILIATION_DEV`.

## B1 — Contratos previos e identidad
`S4W01B3B1B1B0ObservedReadFence.kt` (blob `c738c963bdc0eacc31f557e2597b868f3453b029`) reconsulta DOS snapshots SQL/ACL, compara `generationId`, `pointerSequence`, `globalPolicyRevision`, `openingSource.sourceRevision`, puntero completo, identidad principal, epoch host y tiempo. No hay permiso UI ni token biométrico. `RealityFinance9ObservedSourceReadPort` retorna DTO DEV. `S4W01FinanceReadAuthHost.kt` (blob `26a12243f7d42f9722ded60782dab542b1eef6ec`) cancela challenge ON_STOP/DESTROY; no hay readPort productivo.
Base `15ecb56ddf6f7fff2d1c522ceb9fddf8c03fd65a`, guard `44130462658` SUCCESS, writer RELEASED epoch1119 al iniciar. No duplicar B7 previo SQL ni B0 lifecycle.

## B2 — Scope y no duplicación
No modificar `RealityStore`, `RealityFinance9Acl`, `FaroV5FinanceReadSurface`, `AndroidManifest`, `S4W01FinanceReadAuthHost`, Compose shipping, V4.4/main/S2/S3, FBS2v4, Apply/receipts, W2 o V2 productiva.
Reutilizar el double-SQL fence existente **creando una instancia nueva por cada lectura** y cerrándola. Debe seguir siendo un DTO informativo, no caché de saldos ni credencial de humano.

## B3 — Opciones evaluadas
A: clase DEV de reconciliación + LifecycleEventObserver Android DORMANT; en `ON_PAUSE`/ `ON_STOP` borrar estado antes de permitir futuras lecturas, al `ON_RESUME` borrar estado y exigir nuevo fence doble antes de devolver DTO. Se elige A para mitigar señales perdidas sin provider.
B: provider/notifyChange productivo AHORA, rechazado: owner SQL sin callback y posibilidad de writes directos desde otro PID; Android ContentResolver exige URI respaldada por provider (https://developer.android.com/reference/android/content/ContentResolver). C: confiar en callbacks de ContentObserver (https://developer.android.com/reference/android/database/ContentObserver) como prueba de SQL, rechazado. D: polling con saldo visible hasta próxima tick, rechazado. Lifecycle docs https://developer.android.com/topic/libraries/architecture/lifecycle/ .

## B4 — Permiso EXACT4 nuevo, segregado
`S4_W01_B3B1B1B1B1B1A_FOREGROUND_SQL_RECONCILIATION_DEV`
1. NUEVO `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadForegroundReconciliation.kt`
2. NUEVO `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadForegroundLifecycleAdapter.kt`
3. NUEVO `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01FinanceReadForegroundReconciliationTest.kt`
4. NUEVO `.github/workflows/faro-v5-s4-w01-b3b1b1b1b1b1a-foreground-reconcile.yml`
El workflow exige gate preflight `HEAD^` sobre permiso `READY` y 4 rutas, `:app:testDebugUnitTest` 8/8 JUnit nuevos y `:app:compileV2CoreDevKotlin` verde, XML exacto/guard, sin montar envío productivo, sin añadir manifest/provider ni adoptar contenido saldos. No certifica ContentObserver IPC ni hardware G15.

## B5 — Ataques independientes y R01–R08
- R01 Antes de ON_RESUME nunca se llama al doble SELECT.
- R02 Tras RESUME se notifica CLEAR antes del primer nuevo SELECT y no se conserva DTO; cada nueva lectura construye nuevo fence. El estado no es lease.
- R03 ON_PAUSE/STOP invalida inmediatamente permiso temporal de lectura del reconciliador y obliga otra reconciliación al regreso.
- R04 Un writer SQLite remoto durante background que omitió notifyChange no puede reaparecer por caché: al RESUME un nuevo SELECT devuelve null por policy revocada. El test puede usar fuente sintética NO prueba dos PIDs Android.
- R05 ABA de puntero aun regresando a mismo generationId debe fallar por sequence.
- R06 Drift de globalPolicyRevision o sourceRevision entre las dos lecturas retorna null.
- R07 Invalidación concurrente durante doble SELECT no puede publicar resultado de epoch de evento anterior.
- R08 Excepción de callback CLEAR deja owner permanentemente cerrado/no emite DTO; cierre y RESUME posterior no resucitan.

En caso de callback CLEAR fallido, NO devolver DTO ni afirmar que la pantalla real borró saldo. Para `ON_RESUME` limpiar primero y solo luego habilitar lectura, cada callback cambia serial monotónico. Observer Android se registra/quita en hilo principal, lifecycle event dispatch puede fallar y entonces se debe cerrar; `onExternalSignal` solo marca invalidación local, NO supone entrega interproceso. No hay lectura SQL en constructor ni UI montada. Exigir `FaroV5FinanceReadPort` trusted y chequeo antes de cada render solo en B3B2.

**DICTAMEN B5**: PASS_SCOPED_PRECODE_DEV_FOREGROUND_RECONCILIATION para las 4 rutas. NO PASS para publisher/private ContentProvider y UI lifecycle shipping. Solo crear READY después del guard y blob check.

## B6
PENDING; implementar tras READY exact4, HEAD CAS, writer, preflight oficial, JVM+Android compile.
## B7
PENDING exact scoped. La prueba de lost-notice sintética NO es prueba real de ContentResolver ni Moto G15.
