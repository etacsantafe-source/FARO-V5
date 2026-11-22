# FARO V5 — S4 W04-F2A · Migración aislada STAGED Household10 + Evidence2 · B1–B5
Fecha: 2026-11-22. Unidad `S4_W04_F2A_HOUSEHOLD10_EVIDENCE2_STAGED_UPGRADE_DEV`. Esta publicación es DOCUMENTAL y **NO READY / NO B6** hasta guardia SUCCESS y permiso separado. Solo DEV, no productiva.

## B1 — Evidencia, objetivo y alcance
- Punto de partida exacto 228838383e33e7de57ef5fb7ab2860c8eee3d537; guardia 45055325319 SUCCESS; writer RELEASED epoch1578; F1 B7 PASS 8/8, E B7 PASS Android 4/4; permiso F1 consumido BLOCKED. Auditoría macro F2 `docs/v5/FARO_S4_W04_F2_ACTIVE_TYPED_SOURCE_FEASIBILITY_B1_B5_20261122.md`, blob 1b8939d9b737dc26b3d65adac20f0a7bcea57768.
- La fuente tipada Evidence2 está certificada **solo STAGED** por C1B2G/H1/C2/C3. La generación Household10 ACTIVE usa CommercialEvidence schema1 y `FaroV5GenerationStoreGraph.requireSealedHousehold10ArchiveStage` exige Reality10/Finance9/Aux4/Workspace4/WebEvidence2/Evidence1 exactos.
- `CommercialEvidenceStore(stagedTypedTermsV2=true)` ya tiene migración atómica schema1→2 con nueva tabla FK `commercial_evidence_quote_terms`; registros legacy permanecen físicamente SIN términos, por lo que no se deben convertir en precios comparables ni agregar términos ficticios.
- F2A prueba únicamente **evolución de una copia sellada STAGED** de Household10+Evidence1 a Household10+Evidence2 bajo lease de host cerrado + quiescence, puntero ACTIVE inmutable antes/después y conservación byte por byte de las otras cuatro bases. Su resultado NO se puede promover ni abrir por host ACTIVE; F2B debe diseñar y certificar cutover/recovery y session read tipada bajo un solo lease. F2C integración Android necesita F2B.

## B2 — Comparación y reutilización V4.4
- V4.4 SmartCompra: presupuesto histórico, selección humana, no promesa de stock/precio vigente. V5 no debe copiar runtime ni inferir promoción/moneda.
- Reusar `FaroV5Household10StagedMigration` (sellado, hashes SHA-256, puntero CAS, quiescence, alias/hardlink/symlink/sidecars); `CommercialEvidenceStore` transacción schema2 opt-in y `StagedSqlitePreflight.requireAll` con FK/integridad. NO modificar generador de ACTIVE, D0–D4, F1, E, Finance, owner Household, V2 productiva ni FBS2 codec. Probar copia íntegra antes de la única migración.
- Descartadas: sustituir DB dentro de ACTIVE, copiar STAGED por fuera del lease, forzar `commercialEvidence2StagedDev=true` en ACTIVE, clonar cinco stores manualmente con ATTACH como promesa atómica, convertir campos nulos a términos conocidos.

## B3 — Referencias, evaluación de alternativas y riesgos
- SQLite WAL https://www.sqlite.org/wal.html: STAGED debe estar sin -wal/-shm/-journal y con integridad comprobada; no abrir source ACTIVE con otro owner.
- SQLite isolation https://www.sqlite.org/isolation.html: snapshot y consistencia al leer las cinco bases requieren gate/lease y la publicación de puntero no se hace por la migración.
- SQLite foreign keys https://www.sqlite.org/foreignkeys.html: tabla terms no puede tener orphan IDs; preflight SQL schema2 y foreign_key_check.
- Android Rooms migration https://developer.android.com/training/data-storage/room/migrating-db-versions: solo referencia a estrategia de pruebas de migración, no introducir segundo ORM ni SQL auxiliar no auditado.
- Análisis: usar constructor de Evidence2 sólo sobre archivo target tras verificar clones y root pin; requerir fuente schema1 y target schema1 con preflight completo; después opt-in exactamente target; rechazar disparadores, tokens alterados, WAL, cambio ABA de puntero, archivos hardlink, fuente modificada o lease revocada. El target puede quedar QUARANTINED tras falla; nunca activar por error.

## B4 — Contrato EXACT3 F2A
1. v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5CommercialEvidence2StagedUpgrade.kt — clase interna `FaroV5CommercialEvidence2StagedUpgrade`, recibe root, MaterialWriteQuiescencePort, callback de lease host cerrado y probe adversarial opcional. `upgrade(operationId, targetStage, expectedActive)` es fail-closed:
   - bajo quiescence y lease, validar canonical source/target, puntero exacto y diferente, no sidecars/hardlinks/symlinks, cinco archivos byte idénticos antes, formato y cinco schemas Source/Target Household10+Evidence1 mediante `requireSealedHousehold10ArchiveStage`.
   - abrir SOLO Evidence target con `stagedTypedTermsV2=true` para activar migración transaccional actual, cerrar; forzar fsync del archivo/dir; verificar source hash intacto, otros cuatro hashes target intactos y puntero mismo, preflight schema2 + FK, snapshot typed backup H1/salida portable validada sin rellenar UNKNOWN para registro legacy. Si cualquier paso falla, no publicar pointer y no crear host ACTIVE.
2. v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5CommercialEvidence2StagedUpgradeTest.kt — 8 pruebas JUnit5 aisladas con cinco SQLite reales y directorios STAGED. T01 positivo preserva source, otros stores y términos legacy físicamente ausentes; T02 rechazo target ACTIVE/forjado; T03 target divergente; T04 hardlink; T05 WAL/sidecar/symlink; T06 pointer ABA tras SQL; T07 lease revocada sin escritura; T08 corrupción/trigger/schemas incompatibles fail closed. No simular un cutover real.
3. .github/workflows/faro-v5-s4-w04-f2a-staged-upgrade.yml — dirigido Java21/SDK37, `:store:generation:test --tests ...FaroV5CommercialEvidence2StagedUpgradeTest`, preflight EXACT3 en padre READY, guard obligatorio, JUnit XML 8 distintos 0 failure/error/skipped, artifact XML.
No tocar ninguna otra ruta en B6. Scope **STAGED upgrade proof only**. F2B necesita un nuevo contrato separado del mismo proyecto, y nunca hereda permisos F2A.

## B5 — Auditoría adversarial preparada y decisión
- Pruebas diseñadas T01–T08 todavía **NOT_RUN**; B7 exige código compilado, SQLite real, prueba de sello, delta EXACT3, guardia SUCCESS y XML descargado con 8/8; arreglos RED_FIX solo por fallos reales y con disposición formal previa.
- Amenaza explícita: schema2 puede existir en una copia STAGED, pero Active Household10 sigue abriendo schema1: **F2A PASS no equivale a F2 integral ni habilita mostrar cotizaciones o tocar el puntero**. No acceso fuera de lease, pagos, Apply, Finance, stock, V2 producción, APK G15, Internet/IA, main/V4.4.
- Permiso y writer: preaudit docs-only + guard; READY EXACT3 solo luego de B1–B5 y ACK explícito del alcance DEV proporcionado por usuario al pedir terminar F2 completo; código después de nueva guardia; CI y B7 documentado; writer RELEASED.
