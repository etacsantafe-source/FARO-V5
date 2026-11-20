# FARO V5 · W04-B1D0 · Respaldo de necesidades activas y preimagen durable

Fecha local 2026-11-20. Unidad `S4_W04_B1D0_ACTIVE_HOUSEHOLD10_FBS2V6_DEV`. Código C2 `08eb8b92021233d4cbe5fb126597b21543e70b1a`. Complementa la preauditoría maestra B1B–D. C2 debe cerrar B7 antes de B6.

## B1 — Evidencia y brecha
C1 congela estado anterior y posterior en `CanonicalPayload`; C2 persiste el envelope en Workspace y relaciona el fingerprint del plan con el recibo SQLite COMMITTED. `WorkspaceBackupPlanReview` persiste envelope y digest y FBS2v6 transporta esa sección; la preimagen cabe en el esquema actual. El path Household10 de captura/restauración abre Workspace con verificador por defecto deny-all y por ahora no admite una generación activa como origen. Un archivo válido con necesidades y envelopes COMMITTED exige resolver ambas brechas antes de certificar Undo durable.

## B2 — Reuso
Reutilizar `FaroV5Household10ArchiveCoordinator`, codecs FBS2v6/H10S, `FaroV5FiveStoreStagedRestore` y `CanonicalPreparedPlanEnvelopeVerifier`. El restore base ya admite un grafo que inyecta verificador; habilitarlo sólo en la ruta autorizada de Household10. Mantener V4.4 de solo lectura y no recrear su modelo o backups como otra autoridad.

## B3 — Alternativas
Mantener fuentes oficiales SQLite sobre WAL e isolation de la preauditoría. Un archivo activo con WAL puede tener commits fuera del principal, por lo cual copiarlo sin cierre y lease es inadmisible. Para esta unidad DEV se admite una copia archivística de las cinco bases después de confirmar host cerrado, lease exclusivo, pointer exacto, perfiles sellados y ausencia de sidecars. Copiar sólo bajo quiescencia a un stage nuevo con marcador y comprobación posterior de integridad. Rechazar copiar una generación en vivo vía filesystem, relajar verificaciones canónicas, o agregar historial SQL cuando la preimagen ya está en el plan.

## B4 — Rutas y límites
Inyectar verificador opcional en los openers archivísticos y preservar deny-all por defecto. Agregar captura autenticada de copia sellada de la generación Household10 DEV seleccionada y reusar el restore autenticado hacia stage. Conservar puntero, base activa y journal. Esta unidad certifica preservación del historial, recibos y preimagen tras captura, restore y reopen de la stage. El Apply de Undo, el historial humano y la admisión final del host quedan para el delta posterior B1D1.

- `v5/store/generation/build.gradle.kts`
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10ArchiveCoordinator.kt`
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household10CommittedPlansArchiveTest.kt`
- `.github/workflows/faro-v5-s4-w04-b1d0-household10-live-archive.yml`

## B5 — Aceptación y negativas
Probar datos Household y un envelope COMMITTED real con preimagen, recibo y journal tras restaurar FBS2v6 a SQLite. Rechazar verifier ausente o falso al encontrar planes, puntas ABA, archivo manipulado, root sin lease o sidecar y respaldo incompleto. Comprobar consistencia y ausencia de cambios a la fuente activa, preservando Finanzas, Objetivos, Asuntos y las stores compañeras. Para B1D1, derivar origen desde `loadCommitted` y confirmar recibo/journal; vincular la identidad en Workspace con el principal que puede deshacer. No fabricar un origen histórico que carezca del vínculo verificable.
