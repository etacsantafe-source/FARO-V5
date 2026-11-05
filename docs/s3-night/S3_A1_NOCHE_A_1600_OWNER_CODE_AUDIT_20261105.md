# FARO V5 — S3.A1, auditoría de rutas canónicas N015/N031 (A 16:00, 05/11/2026)

## Recuperación y límites
HEAD inicial `c2fdfe607d9da1040d17ce6ae88f4733d3660046`; writer `RELEASED` epoch 279; cola `ENABLED` con corte 19:00 AR. Se reutilizan B1/B2 de `S3_A1_NOCHE_A_B1_B2_RECOVERY_20261105.md` y B3/B4/B5 documentados por B en `S3_A1_NOCHE_B_B3_B5_RESEARCH_20261105.md`. El permiso vigente `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` corresponde exclusivamente a `S3_A2_G5_ANDROID_EMULATOR_DIRECTED`; **no autoriza cambios funcionales A1**. No se ejecutaron pruebas ni Actions nuevas.

## Evidencia de código exacta, no hipótesis
1. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5HomeReadCoordinator.kt`: `FaroV5HomeOwnerBinding(expectedCanonicalSource,readPort,authorityStamp)`; el coordinador exige sello no vacío ANTES y DESPUÉS de leer, descarta cambios/revocación, falla cerrado ante excepción y verifica `canonicalSource`. Es un *seam*, no fuente de datos ni emisor ACL.
2. `v5/surface/src/test/kotlin/ar/com/faro/v5/surface/FaroV5HomeReadCoordinatorTest.kt`: pruebas existentes de cero vs UNKNOWN, provenance incorrecta, cancelación, revocación antes/después y cambio de generación. **No repetir** por rutina; no prueban un binding real de N015/N031.
3. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`, `receiptHistory` (líneas 105–109) y `queryCommittedReceipts` (aprox. 590–641): fuente canónica parcial `execution_receipt JOIN apply_attempt`, estado `COMMITTED`, cursor `transaction_id` lexicográfico, `LIMIT + 1`, bloqueo de lectura dentro de transacción no committed. El comentario del owner confirma que **no hay timestamp de commit** en esquema durable. No alimentar Actividad reciente ni inferir orden cronológico desde transactionId.
4. Mismo RealityStore `financeActivity` (líneas 111–113 y 643–679): feed financiero con `recordedAtEpochMillis`, orden descendente y cursor compuesto fecha+entryId, `financeRevision`. **No es historial global de operaciones**, y fecha de asiento no equivale necesariamente a tiempo de commit. No sumar como actividad global sin cobertura y ACL demostradas.
5. `v5/kernel/contracts/src/main/kotlin/ar/com/faro/v5/kernel/contracts/MatterAttentionReadProjection.kt`: `MatterAttentionSnapshot(generationId,revision,matters)` exige IDs únicos y generación; `urgentMatters` filtra `OPEN`, `reviewOn` futuro y HIGH/due ≤7 días civiles con zona explícita. Comentario vinculante: **NO bind a Home** hasta probar owner persistente, recordatorios, planificación y ACL. La proyección no crea propietario ni persistencia.
6. `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5HomeReadCoordinator.kt`: ausencia de binding en este archivo no prueba ausencia global. Falta localizar el composition root exacto y el emisor real de ACL/revision para N015/N031; no afirmar inexistencia.

## B5 adversarial contrastado con rutas
- A1-01 PREPARED/rollback: SQL de N015 filtra COMMITTED y rechaza lectura dentro de transacción; **mitigación parcial de owner**, no certificación de Home.
- A1-02 cronología: **BLOQUEO DEMOSTRADO** por falta de commit timestamp en Reality receipt. Mantener UNKNOWN para Actividad reciente; no resolver con UUID/transactionId.
- A1-03/04 ACL y generación: `FaroV5HomeReadCoordinator` revalida sello pre/post, pero falta demostrar quién lo emite y que es autoridad ACL de N015/N031. **NO PASS de integración**.
- A1-06/07 Pendientes: Matter-only es parcial; no hay evidencia en los archivos inspeccionados de owner combinado Matter/Reminder/MonthlyPlan ni de política total. No presentar `0` ni sumar snapshots parciales.
- A1-08/09 cierre, restore y multiproceso: pruebas genéricas del coordinador no prueban cambios externos de ACL ni invalidación multiinstancia. **PENDIENTE**.
- A1-10 Guardados: ocho operaciones candidatas no aprobadas, catálogo 172 intacto; mantener UNKNOWN.
- A1-11/12 egress y Android a11y: fuera de esta lectura; una sola campaña física S3.A5 pendiente.

## Decisión de implementación y siguiente unidad
**B5 PARTIAL_CODE_VERIFIED, NO PERMIT A1 READY.** No se justifica modificar código funcional hasta localizar `CompositionRoot` y dueños/ACL reales; evita adaptadores sintéticos. Próximo B: rastrear las rutas concretas de composición/lectura N015 y N031, confirmar qué campos de fecha y autoridad pueden garantizarse; si no, documentar owner missing y avanzar en S3.A3/A4 solo dentro del catálogo aprobado y con nuevo B1–B5 por unidad. No repetir G1–G5, no abrir S4, V2 productiva OFF, no tocar G15, X01–X12 + campaña única en A5.

**Estado:** S3 1/5 etapas cerradas en alcance automatizado; estimación histórica ~40% desarrollo, no recalculada. Código funcional nuevo: 0; CI nueva: ninguna; runtime/productividad: NO_REGISTRADO. Documento de auditoría no certifica un gate B6/B7.
