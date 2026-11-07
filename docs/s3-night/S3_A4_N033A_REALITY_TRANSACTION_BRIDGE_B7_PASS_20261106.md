# FARO V5 — S3.A4 N033A Reality8 same-transaction Planning bridge — B7 PASS_SCOPED
2026-11-06 AR. Auditada independientemente la unidad `S3_A4_N033A_REALITY_TRANSACTION_BRIDGE` en **SHA funcional exacto `14af40329004a9bc063267c9e20fd64ecc3e78ba`**. Rama `work/s3-night-20261102`.

## B7 — Veredicto final y alcance
**PASS_SCOPED_N033A_REALITY8_SAME_TRANSACTION_BRIDGE**; no equivale a N033 completo, S3.A4 CLOSED, Review/ACL/Undo de usuario, V2 productiva ni prueba física G15. El permiso de cuatro rutas estaba READY en el padre documental `65bbd51af2ecabd1e5921c3a8921a0e0f0b0beea`. Código B6 exacto `14af4032`, `FARO-UNIT:S3_A4_N033A_REALITY_TRANSACTION_BRIDGE`, solo cuatro rutas:
1. `RealityStore.kt`
2. `RealityPlanningStageMutationPort.kt`
3. `RealityPlanningApplyBridgeT2Test.kt`
4. `.github/workflows/faro-v5-s3-a4-n033a-bridge-directed.yml`

## Auditoría estática y regresión de autoridad
- `insertPlanningStage8` de N032 sigue siendo *staged-only*, tiene `BEGIN IMMEDIATE` propio, COMMIT/ROLLBACK propio, y DENIEGA llamada anidada en `AtomicApply`; no se promocionó a operación de Chat.
- Nuevo port `planningApplyInsert` **internal** en el mismo RealityStore, con `RealityTransactionScope` autenticado por `requireActiveScope`, `activeApplyScope === scope` para negar ABA, sin abrir ni cerrar SQL transacciones. No agrega sexto store ni segundo escritor.
- Ambos caminos comparten la misma validación privada SQL: owner CAS/revision, duplicado, FK, lectura completa del snapshot, PLN1 4MiB/20k y preservación del otro owner **antes del COMMIT externo**. `RealityStore.commit` escribe receipt y journal COMMITTED bajo el mismo `run`.
- Revisión negativa: excepción después de owner insert y luego de receipt revierte ambos; tras reopen, PREPARED reintenta igual identidad. Scope stale/foreign, schema7, huérfanos, owner stale/duplicate y presupuesto PLN1 fallan sin efecto persistido. Los tests no prueban autenticidad de Review humana/ACL ni Undo; eso corresponde a N033B/C y se mantiene STOP en producción.

## Evidencia CI exacta y XML (nueve workflows SUCCESS sobre el mismo SHA)
| Scope | Run | Resultado |
|---|---:|---|
| **N033A nuevo puente + N032 staged** | [42314685064](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42314685064) | **12/12 XML**, 6/6 nueva clase + 6/6 N032, cero failures/errors/skips |
| **N032 legacy + generación** | [42314684906](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42314684906) | **47/47 XML**, seis clases, cero failures/errors/skips |
| **N031 FBS2v3 Reality8 backup/recovery** | [42314684922](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42314684922) | **48/48 XML**, seis clases, cero failures/errors/skips |
| **V2 core aislamiento de desarrollo** | [42314684904](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42314684904) | SUCCESS: session 10/10, G5 host 1/1, T4 debug 7/7, schema7 generation 17/17, M1/M2B 4/4 cada uno |
| **G4 Reality domains** | [42314684943](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42314684943) | SUCCESS |
| **G4 domain restore** | [42314684900](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42314684900) | SUCCESS |
| **FBS2 G4 capture** | [42314684957](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42314684957) | SUCCESS |
| **Reality receipt history** | [42314685052](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42314685052) | SUCCESS |
| **Guardia obligatoria de siete bloques** | [42314684924](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42314684924) | SUCCESS; 61 commits funcionales validados incluido N033A |

Nueve de nueve `head_sha=14af40329004a9bc063267c9e20fd64ecc3e78ba`, `status=completed`, `conclusion=success`, confirmados en GitHub. Pasajes `N033A_SAME_TRANSACTION_RECEIPT_STAGE_PASS 12/12`, `N032_PLANNING_STAGE_BOUNDED_INSERT_PASS 47/47`, `PLANNING8_STAGE_ONLY_FBS2V3_CAPTURE_RESTORE_PASS 48/48` observados en logs, no inferidos de workflow YAML.

## Próximo escalón y protecciones
**NEXT N033B**: fresh B1–B5 para binding exacto de IDs oficiales `reminder.create` y `monthly_plan.item.add`, input canónico, fingerprints, stamps, ACL y review; nunca permitir que staged insert reciba órdenes directas de Chat. Luego N033C host generación/ABA/receipt retry/Undo compensatorio y B7 propio. N033A no materializa ocurrencias ni ejecuta recordatorios, no ejecuta pagos Finance, no modifica catálogo 172.
S2 8/8 CLOSED (congelado). S3.A4 OPEN; A3 Guardados +8 NOT_APPROVED, A5 física X01–X12 NOT_RUN, S4 OFF, V2 productiva OFF, Auto/Noche OFF, CLOUD STAGED. Writer CAS liberado antes de CI; registrar release posterior del checkpoint B7.

**Resultado:** PASS_SCOPED únicamente para el puente interno Reality8, no para el comportamiento del producto.
