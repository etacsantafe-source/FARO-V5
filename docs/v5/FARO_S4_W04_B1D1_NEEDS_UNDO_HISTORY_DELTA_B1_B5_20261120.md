# FARO V5 · W04-B1D1 · Undo, historial y aceptación integral

Fecha local 2026-11-20. Unidad `S4_W04_B1D1_NEEDS_UNDO_HISTORY_DEV`, posterior a W04-B1D0 B7. Delta sobre `5b087ef3cc34c2d8da0d3440dff8d9fc10c3be85`.

## B1 — Origen durable
Workspace ofrece `TrustedCommittedConversationPlanReadPort.loadCommitted` con contexto canónico y verifica el envelope. El plan Household conserva preimagen y posimagen, y `RealityStore` conserva journal COMMITTED y recibo del mismo fingerprint. D0 restaura esos datos FBS2v6 con la autoridad Workspace regenerada en la stage. Usar el contexto leyéndose de la Workspace restaurada. La preimagen no requiere una tabla o migración nueva.

## B2 — Reutilización
Adaptar el patrón S3 Undo de nueva Review y `undo.execute` del catálogo. El payload tipado Household debe admitir originales de Edit/Complete/Remove, además de alta. Reusar `S4W04HouseholdNeedApplyHost`, su grant, port de Reality y coordinator. El historial se deriva de Reviews COMMITTED verificadas contra sus recibos, de forma análoga a la prueba de origen canónico S3. V4.4 se usa para referencia de usabilidad; no compartir stock, compras o dinero.

## B3 — Alternativas
Mantener las fuentes SQLite sobre transacciones y WAL de la preauditoría maestra. Usar la preimagen ya autenticada por el plan y el recibo con CAS de revisión; rechazar materializar un estado anterior desde texto o una fila inexistente. El workspace restaurado cambia el contexto de acceso conservando los bytes verificados. Para Undo, enlazar el autor con un identificador de Review derivado del principal. Los reviews anteriores sin ese vínculo conservan historial bajo su realm local; no atribuir una preimagen histórica a un principal sin prueba. Rechazar agregar una segunda DB o ledger.

## B4 — Rutas exactas
Undo crea otro Review mediante `undo.execute`, con receipt/fingerprint original, cambio inverso canónico y revisión actual fijada. Rechazar fuente que no sea una mutación Household COMMITTED verificable y autorizada por el principal. SQL debe restaurar la preimagen con una revisión nueva y sin reinterpretar cantidad o estado. La historia enlaza ambos recibos. El caso de fila ausente necesita también fence de revisión original para descartar ABA después de una eliminación. Para una fila existente, comparar la posimagen exacta y CAS actual. Las cuatro mutaciones y Undo se ejecutan por el mismo gate, sin tocar B0 ni W04-C.

- `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdNeedUndo.kt`
- `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/spi/HouseholdNeedPersistencePort.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHouseholdNeedPersistence.kt`
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04HouseholdNeedApplyHost.kt`
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04HouseholdNeedUndoHostTest.kt`
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household10CommittedPlansArchiveTest.kt`
- `.github/workflows/faro-v5-s4-w04-b1d1-household10-undo-history.yml`

## B5 — Aceptación y negativas
Probar los cuatro inversos, alta que vuelve a ausencia, edición y completar que restauran campos/estado, eliminar que recupera la fila, stale o ABA, petición sin Review aprobado, principal ajeno, receipt incompatible, reintento y reapertura con historial y recibos. Verificar integración FBS2v6 de un Undo confirmado y original COMMITTED, contraste C2 si se cambia el host común y archivística D0 si cambia su fixture. Verificar CI dirigida, XML y conservación de Finanzas/Objetivos/Asuntos antes del B7 global. La cobertura Android física queda explícitamente sin ejecutar.
