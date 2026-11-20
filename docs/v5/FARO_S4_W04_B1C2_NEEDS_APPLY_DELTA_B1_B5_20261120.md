# FARO V5 · W04-B1C2 · Apply y recibos de necesidades

Fecha local 2026-11-20. Unidad `S4_W04_B1C2_NEEDS_REVIEW_APPLY_RECEIPT_DEV`. Delta sobre el código C1 `7ee850874f377fa004f627f87c226ca21c69b516`. La preauditoría principal es `FARO_S4_W04_B1B_B1D_NEEDS_OPERATIONS_B1_B5_20261120.md`.

## B1 — Entrada verificable
C1 provee cuatro operaciones, payload inmutable con estado anterior/posterior, fingerprint y pin de generación. B1B provee el SPI de mutación dentro de la transacción real de Reality10, exactitud del scope e incremento CAS. El catálogo ya registra los cuatro descriptores del owner Household. Workspace debe persistir el envelope canónico y aprobarlo por su contrato humano antes de Apply.

## B2 — Reutilización
Adaptar la integración de `S4W03MatterReviewHost` y `ReviewedConversationApplyCoordinator` para Workspace, PREPARED/COMMITTED y observación del recibo. Reutilizar `PreparedPlanEnvelopeCodec`, `ApplyTransactionExecutor` y `CanonicalPreparedPlanEnvelopeVerifier`. El lease de DEV ya existe, y Reality10 sigue usando una conexión y el gate de su grafo. Mantener patrones de exclusividad de V5 sin importar tool runtime ni persistencia de V4.4.

## B3 — Alternativas
SQLite y V4.4 ya se compararon en la preauditoría. Preferir el mismo commit SQLite para necesidad, journal y receipt más la reconciliación existente del Review por recibo. El grant Household se limita a un intento que confirma en primer plano, vive en el host DEV y se consume justo antes de COMMIT. Su caducidad tras reinicio es deliberada: los recibos y el journal siguen durables y la recuperación sólo los observa. Rechazar una tabla de grants por fuera del esquema autorizado y la reutilización del grant de Finanzas. Rechazar reintento ciego después de COMMIT o tomar la previsualización como autorización.

## B4 — Diseño y rutas
Exponer sesión de servicios al host DEV con la misma admisión de B1A y modo de mutación propio. Inyectar verificador canónico de Workspace. El host ofrece submit, Apply autorizado y reconciliación por recibo. Usa un grant Household por Review explícitamente aprobado, con principal, epoch, generación y secuencia, operación, objetivo, fingerprint y contexto de intento. El binding revalida catálogo, payload y preimagen en la transacción antes de mutar. El gate final invalida el grant antes de commit. Tests locales JVM abren archivos SQLite reales y simulan confirmación humana controlada.

- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04HouseholdNeedApplyHost.kt`
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04HouseholdNeedApplyHostTest.kt`
- `.github/workflows/faro-v5-s4-w04-b1c2-needs-review-apply.yml`

## B5 — Negativas
Probar cuatro operaciones y recibos reales, PENDING sin efectos, denial por foreground y política, stale revision y pointer ABA, identidad de intento y idempotencia, rollback cuando se pierde el grant y reconciliación después de fallar la publicación del recibo en Workspace. Verificar fijación de scope de transacción y consistencia tras reabrir. Reusar los cuatro descriptores, sin cambios en producción, dinero, compras, stock o archivos de otros owners. Persiste la preimagen en el envelope de Workspace para el posterior Undo e historial. La unit B1D deberá verificar el backup/restore de estado activo con envelopes durables y obtener permiso propio para cualquier delta adicional.
