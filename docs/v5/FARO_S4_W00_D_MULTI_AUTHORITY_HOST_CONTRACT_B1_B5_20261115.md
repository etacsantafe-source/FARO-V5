# FARO V5 — S4 W00-D: contrato de admisión para múltiples autoridades
**Fecha:** 2026-11-15 AR. **Unidad DOC_ONLY:** `S4_W00_D_MULTI_AUTHORITY_HOST_CONTRACT_PREAUDIT`. **Baseline:** `1068254c1b7ca7b553ae6569cd1addab81294492`.
**Alcance:** arquitectura, riesgos y criterios; ningún Kotlin, SQL, workflow, owner ni permiso READY. S4 formal OPEN solo en alcance W00, productiva V2/Auto/Noche OFF. Writer único epoch 924 DOC_ONLY.

## B1 — Evidencia primaria del HEAD vivo
- W00-A 5/5 SQLite real; W00-B 5/5 Apply+receipt/recovery con binding Objective TEST_ONLY; W00-C 2/2 denegación compuesta de Reminder y MonthlyPlan en host real. W00-C B7 PASS scoped: código `b5c2906654c1a6b88aadfa4997c3324d3395f20b`, CI `43768537598`, guard `43768537572`, checkpoint `1068254c1b7ca7b553ae6569cd1addab81294492`, guard checkpoint `43769148919` SUCCESS, writer RELEASED epoch 923.
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5Planning8ApprovedObjectGate.kt` (blob `2bd779e96cfafc897bf96379bc8d55b5f33169d4`) exige `nodes.size==1` y un `Planning8ObjectScope` individual; no grants multi-node.
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5Planning8ReviewedApplyHost.kt` (blob `b7655b4cf63266050100dcb08a69ee450271d948`) usa `singleOrNull`/one scope y está DORMANT, no montado en shipping. Su `authorization` revalida en-scope pero comenta que no protege por sí sola la ventana final check-to-SQL-COMMIT cross-process.
- `FaroV5Planning8ObjectGrantOwner.kt` (blob `5301094c14dabdca7a024dc5bd6b505fe146f746`): un lease por objeto y monitor en memoria, no política/identidad de usuario productiva, ni revocación cross-process.
- `FaroV5Planning8ReviewGrantLeaseBridge.kt` (blob `5fa2f461455bbf312a7240d0d1346be95530b340`): lock de un objeto cubre el callback síncrono y SQL COMMIT, sin soporte de unión de scopes.
- `FaroV5Planning8LocalUserRequestPolicy.kt` (blob `a86e67370782cfeb0f6308a5a350a7237d8cae65`): ticket temporal vinculando UNA Review con UNA identidad; el propio código advierte que una callback no prueba gesto humano.
- `v5/kernel/apply/src/main/kotlin/ar/com/faro/v5/kernel/apply/ApplyTransactionExecutor.kt` (blob `772decfde8aee398eda6c84149f9f0a4234d6d7f`) revalida cada nodo antes de binding y nuevamente antes del recibo, un `transactions.run`, un commit. `RealityStore.kt` (blob `be72978b28d79366452a3f43c7018db72b2ca9f7`) usa un scope SQLite y receipt/journal dentro del COMMIT.
- `v5/settings.gradle.kts` sin módulo `:owner:household`; sus 32 descriptores en catálogo180 NO son operaciones ejecutables ni grants aprobados. El usuario aprobó autoridad Household PARA DISEÑO, no su implementación. En P07 aprobó OPCIÓN A de producto: una revisión humana, DOS authorities independientes, una transacción atómica demostrable o STOP. W06 REAL Household+Finance sigue bloqueando W07.

## B2 — Reuso sin reapertura de S3
Conservar ApplyPlan inmutable/fingerprint, Review humano, single writer, SQLite Reality y receipts/Undo/recovery, ACL por owner, checks de generación/epoch, separación Workspace/Auxiliary/Evidence/Reality y operaciones manuales offline. W00-A/B/C ya prueban deltas independientes (5+5+2), no repetirlos salvo motivo nuevo. No copiar runtime V4.4 ni hacer que Finance absorba precios/stock. El host Planning8 actual debe seguir DENEGANDO multi-node hasta que exista NUEVA vía segura expresamente autorizada.

## B3 — Alternativas y fuentes verificables
- **A / CANDIDATA:** crear en el FUTURO un contrato de host de Review compuesta con matriz estable `nodeId→authority→scope→generation→grantEpoch`, uno por owner, con adquisición/orden de locks documentado y un SQL COMMIT. Requiere nueva autorización de diseño exacto + permisos de código; no declararla implementada.
- **B / PELIGROSA SIN REDISEÑO:** quitar `nodes.size==1` o expandir `singleOrNull` directamente en host Planning8, o compartir el grant de Reminder con Finance; abriría privilegios cruzados. Rechazada.
- **C / RECHAZADA:** dos commits secuenciales/Saga silenciosa o dos bases WAL atómicas solo por archivo, porque opción A comercial-financiera exige un resultado indivisible para compra pagada.
- **D / STOP SEGURO:** mantener la denegación actual si no es viable resolver revocación, aislamiento y recibo compuesto con prueba real.
Fuentes primarias: SQLite https://www.sqlite.org/lang_transaction.html (BEGIN IMMEDIATE, errores antes/durante COMMIT), https://www.sqlite.org/isolation.html (serialización), https://www.sqlite.org/wal.html (multiarchivo no atómico como conjunto), AndroidX https://developer.android.com/reference/androidx/sqlite/db/SupportSQLiteDatabase (alcance de transacción). Código externo NO importado, sin APIs pagas, sin licencias nuevas.

## B4 — Contrato candidato y puertas técnicas
1. **Review único humano** incluye panel comercial Household y panel financiero Finance, con dos efectos separados y trazables. `Approved Review` no crea permisos. Cualquier dato ambiguo/cambio de precio/promoción/cuenta/medio o fingerprint exige nueva Review, no extrapolar autorización.
2. **Admisión pre-Apply por autoridad:** obtener grants y policyEpoch con principal, objeto, permiso, owner revision, sourceRevision, generación y host epoch independientes. Un grant ausente/revocado => FAIL_CLOSED. No usar token Workspace ni Search como permiso mutante.
3. **Secuencia segura:** fijar snapshot y conjunto de autoridades antes de BeginApply; adquirir leases en orden canónico sin ciclos; validar en cada transición; Workspace BeginApply y promoción de intent deben estar FUERA de Reality SQL mutex; dentro de Reality.run solo comprobaciones sin llamadas a Workspace.
4. **Aislamiento al COMMIT:** mantener permisos efectivos/leases o un fencing transaccional equivalente hasta COMMIT/ROLLBACK para CADA owner; un mero callback de validación antes de COMMIT no bloquea revocación externa entre chequeo y escritura. Necesita solución comprobada para cross-process y restauración de generación.
5. **Ejecución:** dos bindings productivos bajo UN `RealityTransactionScope`, comprobación topológica/revalidación, receipt/journal único reconciliable, rollback total ante rechazo de cualquier owner. No doble Apply, no dinero externo y no recibir stock por registrar pago.
6. **Recuperación:** `UNKNOWN` => buscar receipt/journal por identidad e idempotencia antes de cualquier reintento; si no hay prueba de estado, detener y pedir revisión, no fingir éxito ni duplicar cargo.
7. **Frontera funcional:** para tener `Household+Finance` real se necesita aprobación de implementación de Household, owner, SQLite, bindings, grants y permisos individuales. W00-D no autoriza esos cambios; W06 hará la prueba real antes de W07.

## B5 — Pruebas adversariales futuras (diseñadas, NO ejecutadas)
| ID | Caso que deberá probarse | Resultado requerido |
| --- | --- | --- |
| W00D-T01 | Review compuesta con solo grant Finance | DENIED sin compra/gasto/receipt |
| W00D-T02 | Grants A y B, uno cambia epoch entre Review y Apply | DENIED, sin Apply parcial |
| W00D-T03 | Revocación de B después de binding A, antes de COMMIT | rollback A+B, cero COMMITTED |
| W00D-T04 | Revocación cross-process luego de último check | demostrar fencing real o STOP |
| W00D-T05 | Dos objetos, locks solicitados en orden opuesto | orden canónico, sin deadlock ni bypass |
| W00D-T06 | Restauración/ABA generación o cambio de fuente | stale DENIED, sin usar grant antiguo |
| W00D-T07 | Crash antes/después COMMITTED con recibo perdido en UI | receipt-first, cero doble intento |
| W00D-T08 | Precio/promo/cuenta/moneda cambian tras Review | fingerprint stale/STOP, no gasto histórico |
| W00D-T09 | Solo contrato Household sin owner activo | STOP, ninguna promoción a productiva |
| W00D-T10 | Dos monedas distintas sin FX autorizado | STOP sin suma nominal |

**Resultado de la preauditoría:** diseño candidato y lista de riesgos, NO solución técnica aprobada, NO preflight/READY, NO prueba ejecutada, W00 global sigue OPEN. La siguiente unidad funcional necesita elegir ownership y rutas exactas tras aprobación humana específica si crea/expande permisos, y B1–B5 propios. Mientras tanto puede seguirse haciendo investigación READ_ONLY.
