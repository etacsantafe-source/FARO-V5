# FARO V5 — S4 W00 · Preauditoría del código vivo y primera unidad programable
**Fecha:** 2026-11-15 Argentina. **Baseline:** `42e889d2f66fd61e074790b6e1dde5454fc3ae56`. **Unidad documental:** `S4_W00_PRECODE_FEASIBILITY_REVIEW`.
**Reglas:** B1–B5 revisados sobre código real, B6 solo docs y B7 independiente; NO funcional B6, NO permiso READY, NO apertura S4, NO activación V2 productiva/Auto/Noche. Writer DOC_ONLY epoch 910. S3 DEV 5/5 cerrado con excepción H07 HOME aceptada (no PASS); S2 8/8 congelado.

## B1 — Evidencia exacta: dónde y qué existe hoy
| Componente | Ruta exacta en rama | SHA blob confirmado | Veredicto observado |
|---|---|---|---|
| Grafo | `v5/kernel/contracts/src/main/kotlin/ar/com/faro/v5/kernel/contracts/ApplyPlan.kt` | `5808ef0021ff91fde19339bdbb06c2f7e255a2e1` | Varias operaciones ordenadas topológicamente, sin duplicado de nodo/ciclos |
| Nodos | `v5/kernel/contracts/src/main/kotlin/ar/com/faro/v5/kernel/contracts/ApplyPlanNode.kt` | `1bb68bc0a3257ea1c8721eee61b7f6c19785099e` | Nodo con operación y dependencias; no concede authority |
| Ejecutor | `v5/kernel/apply/src/main/kotlin/ar/com/faro/v5/kernel/apply/ApplyTransactionExecutor.kt` | `772decfde8aee398eda6c84149f9f0a4234d6d7f` | Un `transactions.run` para todos los bindings, comprobación de autorización antes/después de bindings, una invocación `commits.commit`; excepción => RecoveryRequired |
| Test kernel | `v5/kernel/apply/src/test/kotlin/ar/com/faro/v5/kernel/apply/ApplyTransactionExecutorTest.kt` | `6f43d3b057598b99f4149956f0c9ae2c34bc15d4` | Tests existentes de dos bindings con RecordingTransactionPort, rechazo y rollback simulado; NO son doble owner REAL en SQLite |
| Reality | `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt` | `be72978b28d79366452a3f43c7018db72b2ca9f7` | Líneas 435–471: `BEGIN IMMEDIATE`, `COMMIT`/`ROLLBACK`, scope activo único en una conexión; líneas 540–637: receipt/outbox/journal COMMITTED dentro del scope; 1010–1020: `synchronous=FULL`, WAL |
| Finance SPI | `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/spi/FinancePersistencePort.kt` | `4a5aa3af3d689b78cc67efbb8df513a47d3f29bc` | createAccount/recordEntry requieren `RealityTransactionScope`; RealityStore 147–162 los expone |
| Finance modelo | `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceModel.kt` | `22f9444f931aff356836bf61bffb10e9975142b7` | Saldos por moneda, entradas y cuentas; no owner de ofertas |
| Build | `v5/store/reality/build.gradle.kts` | `ba703235bf566c29996b2656ad88382b65aca25a` | Tests JVM ya tienen dependencia de `:kernel:apply` y JUnit; no introducir nueva librería |
| Owner graph | `v5/settings.gradle.kts` | `a2b6f7e4294537ff8add383ea8c620796d8d88ee` | `:owner:finance`, objectives, matters, etc. presentes; **NO `:owner:household`** |
| Contratos | `v5/spec/generated/contract-baseline.tsv` | `5653a9758f682f683a852dcf286211bae71c41d0` (P01, reconfirmar antes de código) | 180 descriptores; 32 household NO son puerto de escritura autorizado |

**Resultado B1 acotado:** sí hay infraestructura para varios bindings dentro de un scope REAL en un solo SQLite. No se observó ejecutor Household, schema ni host de pago conjunto en estas rutas. La prueba de pago Finance+Household y su recovery **NO EXISTE como PASS comprobado en W00**. Inspección de blobs ≠ ejecutar test/CI.

## B2 — Reutilización V4.4 y V5, sin repetir certificados
- Reusar P00–P08, especialmente P01 V01/V03/V04/V13/V14, P02 G0D3 y P03 R17, P05 PFxx y P07 H-P07-01 opción A. No reabrir S3 (incluida excepción H07).
- Conservar Reality single DB y single transaction gate, fingerprint/ApplyPlan, PREPARED/COMMITTED, receipts, journal y generación, con ACL de cada owner. No copiar V4.4, no segundo writer ni doble Apply.
- `ApplyTransactionExecutorTest.kt` ya cubre mock multi-binding y fallos de contrato: NO repetirlos solo para aumentar recuentos. Nuevo delta debe usar SQLite REAL y observar balances, filas, recibo y recovery.

## B3 — Alternativas reales, fuentes primarias y decisión
- **A / ADAPTAR (primera elección a comprobar):** DOS autoridades internas en UNA Reality SQLite, con bindings en `ApplyTransactionExecutor`, un `BEGIN IMMEDIATE`, un `COMMIT`, rollback completo y receipt único. Evidencia propia: `RealityStore.kt` líneas 435–471/540–637 y `ApplyTransactionExecutor.kt`. Compatible offline/Android minSdk29 y sin API paga. **Es hipótesis**, no certificación multiowner.
- **B / DESCARTAR:** conectar dos SQLite con `ATTACH` en modo WAL y suponer commit conjunto. La documentación oficial SQLite advierte que en WAL las transacciones con varias bases adjuntas son atómicas por archivo **NO como conjunto**: https://sqlite.org/wal.html (sección limitaciones). Un resultado verde en happy path NO elimina riesgo de power loss.
- **C / RECHAZAR para pago opción A:** dos `Finance.recordEntry` + `Household.confirmPurchase` secuenciales o saga con compensación silenciosa. Puede dejar compra confirmada sin dinero aplicado, o viceversa; violaría la decisión humana P07. En SQLite `BEGIN` no permite anidamiento independiente; alternativas `SAVEPOINT` siguen dentro de una misma transacción (https://www.sqlite.org/lang_transaction.html).
- **D / REFERENCIA:** `SupportSQLiteDatabase` de Android permite transacciones y exige distinguir scopes de persistencia y retorno (https://developer.android.com/reference/androidx/sqlite/db/SupportSQLiteDatabase). No añadir Room/ORM para resolver un problema que RealityStore ya aborda parcialmente. Revisión primaria consultada 2026-11-15; cuando se elija tecnología del owner Household, revalidar licencia/API/versiones.

**Decisión W00:** comenzar por una prueba REAL de atomicidad entre dos owners ya implementados (Finance+Objectives), exclusivamente como test de infraestructura, y después W06 probar **Household+Finance REAL**. Un PASS de Finance+Objectives **no transfiere** certificación a Household.

## B4 — Primera unidad futura minúscula, rutas exactas y criterio binario
**ID candidato:** `S4_W00_A_REALITY_TWO_EXISTING_OWNERS_ATOMICITY_TEST`.
**ÚNICA ruta funcional propuesta para la primera iteración:** `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/S4W00TwoOwnerAtomicityFeasibilityTest.kt` (**NUEVA, NO EXISTE AÚN; verificar disponibilidad y compatibilidad antes de READY**).
- Tests locales JUnit sobre RealityStore real, in-memory y on-disk aislada, sin teléfono ni datos del usuario. Utilizar puertos existentes `financePersistence` y `objectivePersistence` dentro de una misma `RealityTransactionScope` y commit/rollback; enlazar un solo ApplyPlan/attempt/receipt cuando sea factible, sin reimplementar executor.
- No crear ni habilitar `:owner:household`, modificar `RealityStore.kt`, catálogo, schema, permiso de producción ni host en esta subunidad. Si la prueba no es expresable por la API existente, documentar evidencia y volver a B3/B5 ANTES de ampliar rutas.
- Gradle esperado: `./gradlew :store:reality:test --no-daemon` desde `v5/`, con JUnit XML del test nuevo y regresión scoped; después guard `seven-block-evidence` del SHA correspondiente. Verificar tarea exacta en runner antes de ejecución.
- **Para emitir READY en otro paso:** apertura S4 y alcance W00-A autorizados explícitamente, B1–B5 con evidencias de blob actuales, ubicación del test comprobada, plan negativo aprobado, CAS writer, permiso individual en commit de **solo docs** anterior al commit de código con `FARO-UNIT:S4_W00_A_REALITY_TWO_EXISTING_OWNERS_ATOMICITY_TEST` y preflight por **esa ruta**. Cero permisos heredados de S3 o wildcard.

## B5 — Negativos obligatorios; NO ejecutados en esta auditoría
| Test futuro | Criterio indispensable |
|---|---|
| W00-T01 | Dos cambios existentes Finance+Objectives dentro de un scope: un COMMITTED/receipt, dos datos persistidos |
| W00-T02 | Falla deliberada tras Finance pero antes de Objectives: 0 efectos de ambos y 0 receipt |
| W00-T03 | Rechazo de segundo binding después de primero: rollback total; no COMMITTED parcial |
| W00-T04 | Fallo antes del COMMIT/receipt: RECOVERING_UNKNOWN y lookup para reconciliar, no reintento ciego |
| W00-T05 | Falla después del COMMIT antes de respuesta: lookup receipt original e idempotencia, 0 doble gasto |
| W00-T06 | Scope de otra conexión/transactionId, doble uso o transacción anidada: FAIL_CLOSED |
| W00-T07 | Revoke/foreground/epoch entre Review, binding y COMMIT: rechazar antes de efectos durables; host serialization aún NO demostrada |
| W00-T08 | Fallo de disco/ROLLBACK o journal PREPARED con reingreso: sin éxito inventado; comprobar recuperación real |
| W00-T09 | Caso explícito `ATTACH + WAL` no admisible como prueba multiarchivo atómica |
| W00-T10 | Atestiguar que PASS entre Finance+Objectives no habilita W07, que sigue STOP hasta W06 real Household+Finance |

**Adversarial pendiente antes de READY:** test API exacta con SQLite driver, inicialización/seed de Finance/Objective y journal existentes, review/ACL del host a partir de source actual, generación y posibles rutas de test que ya cubran estas situaciones. Conservar los PASS previos y ejecutar SOLO deltas.

## B6 — Publicación documental en este turno, NO código
Esta unidad publica el presente paquete, el B7 documental de W00 y un pointer. Permiso funcional S3 consumido continúa `BLOCKED`; no activar S4 ni Work, crear schema/owner ni ejecutar CI Android nueva. El diff remoto y writer RELEASED requieren GET posterior.

## B7 — Criterios para afirmar «listo para empezar a programar» (sin falsear guardia)
1. HEAD incluye paquete, B7 y pointer; diff SOLO tres archivos `docs/v5/`; `s4_open=false`, permiso BLOCKED, S3 y S2 intactos.
2. Writer RELEASED epoch 910 y `last_checkpoint=HEAD`, guard `seven-block-evidence` SUCCESS **del HEAD nuevo**. Si run no visible, WAIT_CI, no PASS.
3. La primera unidad está especificada (ID, ruta, prueba negativa, criterios, dependencias) y NO READY aún; solo queda apertura/permiso limitado después de la verificación de código/CI.
4. Nunca declarar S4 programada ni productiva habilitada; W06 es el gate técnico de W07.
