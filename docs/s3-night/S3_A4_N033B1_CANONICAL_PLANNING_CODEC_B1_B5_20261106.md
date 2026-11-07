# FARO V5 — S3.A4 N033B1 canonical planning payload + typed D-229 IDs — B1–B5

2026-11-06 AR. Unidad acotada `S3_A4_N033B1_CANONICAL_PLANNING_CODEC`. Fuente revisada: `2222b3812262497f3ad0ecf28d4c3ba0ee26a34c`, rama exclusiva `work/s3-night-20261102`. Este documento es la preauditoría ANTERIOR al permiso y a cualquier código; B6/B7 inicialmente NOT_RUN. La auditoría de N033B completo sigue STOP.

## B1 — Evidencia y aceptación verificable
- `docs/s3-night/S3_A4_N033B_OFFICIAL_PLANNING_BINDINGS_B1_B5_STOP_20261106.md` separa N033B1 de N033B2/B3/N033C; faltan codecs puros y OperationIds tipados, pero el puente N033A y los contratos existentes permanecen probados y no se reabren.
- `v5/owner/reminders/.../ReminderModel.kt`: `ReminderRule(id,title,zoneId,anchorLocalDate,anchorLocalTime,recurrence,enabled,linkedMatterId,updatedAtEpochMillis)`, sin ejecución de ocurrencias; `ReminderRuleId.of` recorta y valida 1..128. Tests existentes `ReminderModelTest` 6/6.
- `v5/owner/monthlyplan/.../MonthlyPlanModel.kt`: `MonthlyPlanItem(id,title,period,dueDate,status,expectedAmountMinor,currencyCode,paymentConfirmedAtEpochMillis,updatedAtEpochMillis)`; monto esperado no equivale a pago; tests existentes 6/6.
- Baseline `v5/spec/generated/contract-baseline.tsv` D-229 **172** incluye `reminder.create` (N-032) y `monthly_plan.item.add` (N-028), ambas executor `planning` y REALITY_MUTATION. Reusar `CapabilityId`, `ExecutorId`, `OperationId` y `CanonicalPayload`, sin generar un nuevo ID.
- Aceptar únicamente un codec de intención de creación por owner, versionado por `formatId` distinto y campo de versión interno; límites de bytes, lectura exacta, UTF-8 estricto, reencode byte-identical, timestamps/periodo civiles intactos. N033B1 no crea un `OperationBindingPort`, no promete autorización, recibo, Apply, Undo, permisos ni almacenamiento.

## B2 — Comparación con V4.4 y reutilización
- Comparación histórica documentada, revisada y reutilizable: `S3_A4_N031_REMINDER_MONTHLYPLAN_TYPED_CONTRACTS_B1_B5_20261106.md`, `S3_A4_N033_TRUSTED_PLANNING_APPLY_B1_B5_STOP_20261106.md`, y `S3_A4_N033B_OFFICIAL_PLANNING_BINDINGS_B1_B5_STOP_20261106.md`; **regla ≠ ocurrencia/disparo**, plan esperado ≠ asiento/pago Finance, no copiar scheduler ni SQLite legacy.
- Reusar patrón determinista y `CanonicalPayload.fromEncoded` de `FinanceEntryRecordPayload` sin copiar su debilidad: su `String(bytes, UTF_8)` reemplaza bytes malformados; N033B1 usará `CharsetDecoder` con `CodingErrorAction.REPORT` y re-encoding exacto.
- Mantener operaciones solo como constantes tipadas en cada owner, sin registro en host. No cambiar contratos generados ni S2, V4.4, Android, esquema, manifest, N033A ni el catálogo.

## B3 — Investigación externa real y alternativas
- RFC 8785, https://www.rfc-editor.org/rfc/rfc8785.html: invariancia de representación, rechazo de Unicode inválido, sin alterar datos Unicode. **ADAPTAR principio**; aquí el contrato existente `CanonicalPayload` usa bytes binarios inmutables, NO declarar que el codec binario sea JSON RFC8785.
- Java `CharsetDecoder`, https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/CharsetDecoder.html: `onMalformedInput(REPORT)` y `onUnmappableCharacter(REPORT)` fallan ante bytes malformados. **USAR** para decode y validación de strings de entrada.
- Android offline-first, https://developer.android.com/topic/architecture/data-layer/offline-first: el almacén local permanece fuente de verdad, pero no implica construir scheduler/cloud en este paso. **REFERENCIA** al preservar `RealityStore` y su único writer.
- Alternativa A: codec binario versionado acotado, revalidación estricta, sin librería nueva, sin red **USAR**. Alternativa B: JSON ad hoc sin JCS aplicado a enteros, surrogate y campos opcionales **DESCARTAR** por riesgo de equivalencias ambiguas. Alternativa C: usar estructura SQL o UI como payload **DESCARTAR** porque acopla preview a persistencia/revisión. Dependencias/coste externo agregados: cero.
- Pruebas negativas derivadas: UTF-8 truncado/sustitución, longitud negativa/excesiva, trailing bytes, byte de boolean inválido, enum desconocido, formato/version incorrecta, fechas inválidas, cantidad monetaria negativa y objeto reencode no idéntico. No afirmar resultados de pruebas todavía.

## B4 — Unificación y scope exacto
- Nuevas clases: `ReminderCanonicalOperation.kt` y `MonthlyPlanCanonicalOperation.kt`, bajo sus módulos owner existentes. IDs explícitos exactamente `reminder.create`, `monthly_plan.item.add`, executor `planning`, capabilities N-032/N-028. Cada codec `FORMAT_ID` v1 y magic/version internos fijos; `MAX_PAYLOAD_BYTES=8192`, `MAX_FIELD_BYTES=4096`; byte counts antes de asignación, exact EOF y `encoded.contentEquals(input)` después de decodificar. Formato binario *específico* no intercambiable entre owners.
- Propiedades civiles `LocalDate`, `LocalTime`, `YearMonth` via representaciones ISO y `ZoneId` del modelo, nunca expansión DST ni fecha derivada. ID lo aporta caller, no se sintetiza desde reloj/AI. No añadir autorización o permisos ficticios.
- Únicos paths funcionales preautorizables: 
  1. `v5/owner/reminders/src/main/kotlin/ar/com/faro/v5/owner/reminders/api/ReminderCanonicalOperation.kt`
  2. `v5/owner/reminders/src/test/kotlin/ar/com/faro/v5/owner/reminders/api/ReminderCanonicalOperationTest.kt`
  3. `v5/owner/monthlyplan/src/main/kotlin/ar/com/faro/v5/owner/monthlyplan/api/MonthlyPlanCanonicalOperation.kt`
  4. `v5/owner/monthlyplan/src/test/kotlin/ar/com/faro/v5/owner/monthlyplan/api/MonthlyPlanCanonicalOperationTest.kt`
  5. `.github/workflows/faro-v5-s3-a4-n033b1-codecs-directed.yml`.
- CI: Gradle `:owner:reminders:test :owner:monthlyplan:test` aislado JVM, XML exacto `ReminderModelTest=6/6`, `MonthlyPlanModelTest=6/6`, y `ReminderCanonicalOperationTest=6/6`, `MonthlyPlanCanonicalOperationTest=6/6`; total 24/24 (solo objetivo, nunca certificar antes de CI). Guardia de siete bloques adicional.

## B5 — Auditoría adversarial y decisión ANTES de código
- Riesgo de normalización silenciosa: exigir reencode exacto a bytes de ingreso, UTF-8 estricto, validación de ids y enums, ningún padding/byte extra. No usar `readUTF` modificado. Rechazar entradas enormes ANTES de parsear, límites estrictos al escribir/leer.
- Riesgo de transacción/ACL: N033B1 **NO** crea puente de escritura, ni Review, ni dependencia stamps, ni permission grant. N033B2/B3/N033C quedan BLOCKED por N033B B1–B5 existente y requieren preauditoría separada.
- Riesgo temporal: ninguna regla futura o día final de mes dispara una notificación; se serializa intención civil exacta. UTC offset de DST no se inventa. Sin pagar/registrar Finance aunque exista monto esperado.
- Riesgo de privacidad: no introducir secretos ni datos de usuario en tests; fixtures sintéticas, sin Android/G15 ni tocar main.
- **Veredicto: READY_SCOPE_N033B1_ONLY** sujeto al permiso docs-only separado, CAS writer y preflight del guardia antes de B6. **N033B completo = STOP**, A4 abierta, V2 productiva/Noche/Auto/S4 OFF. B7 independiente después del CI y recién entonces permiso BLOCKED/checkpoint.

Próximo paso: emitir `FARO_PROTOCOL_WORK_PERMIT.json` limitado, revisar preflight, hacer un commit funcional con `FARO-UNIT:S3_A4_N033B1_CANONICAL_PLANNING_CODEC`, liberar writer, verificar Actions/XML, registrar B7.
