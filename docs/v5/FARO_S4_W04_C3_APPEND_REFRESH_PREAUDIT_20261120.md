# FARO V5 — W04-C3 · Refresh comercial manual tipado append-only sobre Evidence2 STAGED — B1–B5 final

Fecha local 2026-11-20. Baseline C2 código `d208213f01c4e5b290cee0e23afe1472dfa3cdf1`, guard `44850685830` SUCCESS, CI `44850685841` SUCCESS 14/14, B7 C2 publicado junto a esta revisión. Unidad siguiente `S4_W04_C3_STAGED_TYPED_APPEND_REFRESH_DEV`. **B1–B5 DOC_ONLY, NO READY, NO B6 antes de guard y nuevo permiso**.

## B1
- `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence.kt` blob `df75f0d53a792131bc638536e738aafae567b78a`: `recordWithTypedTerms(observation,terms,idempotencyKey,payloadFingerprint)` exige `stagedTypedTermsV2=true` y `readSchemaVersion()==2`, verifica idempotencyKey previo, matches payloadFP+terms para replay; inserta **fila comercial y fila terms** en una sola transacción `BEGIN IMMEDIATE` con `COMMIT/ROLLBACK`. El método `record()` legacy prohíbe writes a schema2. `stagedTypedPortability.snapshot()` de H1 lee de ese MISMO store con integridad/FK/terms fingerprint y watermark por todos los records.
- C2 nuevo `CommercialTypedPriceComparison.project` comprueba H1 hash y devuelve historial observado con grupo seguro; no escribe. C1a host legado `S4W04ManualPriceObservationHost` admite `commercial.evidence.record` OBSERVATIONAL_WRITE con grants Household+Commercial y fence, pero su ejecución está limitada a schema1: rechaza los términos tipados. C3 no debe montarse en host ACTIVE ni abrir un segundo root/generation.
- Catálogo oficial N-034 `CommercialEvidenceOperations.RECORD`, `CommercialEvidenceOperations.CAPABILITY_ID`, `EXECUTOR_ID`, profile `ExecutionProfile.OBSERVATIONAL_WRITE` y exposure `BINDABLE`. `:evidence:test` tiene `kernel:runtime` para fixtures de contrato. Ningún nuevo OperationId.
- El refresco mutante con Review/Apply y consentimiento en Evidence2 STAGED **no existe**. No afirmarlo por tener el método SQL.

## B2
- V4.4 SmartCompra permitía actualizar cotizaciones; en V5 un precio nuevo se **anexa** con fuente y fecha, y el histórico viejo queda intacto con su provenance. No UPDATE/DELETE ni supersession durable inventada. Mantener Evidence ≠ Reality (necesidades) ≠ Finance (dinero). Una promoción no autoriza descuento, pago, stock ni compra. No copiar host legacy directo a schema2, ni llamar `commercial.quote` READ_ONLY para escribir.

## B3
- **ADAPTAR/USAR:** Servicio tipado STAGED sobre **la misma instancia** `CommercialEvidenceStore` y su `stagedTypedPortability`, con `CommercialTypedPriceComparison` para verificar snapshot antes de Review y antes de commit; `recordWithTypedTerms` ya implementa atomicidad SQL e idempotencia de registros.
- **ADAPTAR:** ACL de C1a con doble grant, comprobación de descriptor canónico, fence de generation/epoch/sequence y confirmación humana; deben ser callbacks de fuente host confiable (no tokens autocreados), revalidarse antes/después. Tras commit con revocación tardía, `RecoveryRequired(key)` para no simular rollback.
- **REFERENCIA:** modelo V4.4 L11/L15 de refresh y patrones SQLite WAL/atomicidad ya investigados en P02/W04-C; usar kernel `CanonicalHashBuilder` actual para idempotency+payloadFP. Sin bibliotecas nuevas ni API externa.
- **DESCARTAR:** editar precio anterior, registros batch sin confirmación, refresh automático por scraping, acceso autónomo a ACTIVE, segundo store/gate, idempotencia aleatoria en retry, escritura de sourceRef como URI ejecutable y conciliación financiera.

## B4
**Unidad C3 en Evidence2 STAGED local con contrato exacto:**
1. `CommercialTypedRefreshFence(generationId,pointerSequence,hostEpoch,principal)` obtenido desde un caller confiable; validez exige generationId no vacío, sequence positivo, epoch/principal explícitos. Cabeceras de usuario no se convierten en permisos. Catálogo `commercial.evidence.record` OBSERVATIONAL_WRITE exacto; callbacks independientes Household/Commercial y snapshot saludable.
2. `prepare(observation MANUAL + CommercialEvidenceTypedTerms + localRequestId)`: requiere usuario/producto explícito con historial existente verificado por C2, before/after grants y fence, requestId bounded; congela fingerprint del watermark H1, observation, terms, principal, epoch/sequence, idempotencyKey estable por principal+fence+requestId, payloadFingerprint canónico por todos los campos comerciales y terms.fingerprint. Sólo draft/Review, sin SQL ni archivo.
3. `confirmAndAppend(review, trustedHumanConfirmed)`: chequea descriptor y grants, fence y confirmación humana, relee snapshot para rechazar cambio de precio/terms/generation antes del commit. Una sola llamada a `store.recordWithTypedTerms` (transacción atómica ya certificada); retorna `APPENDED_NEW_OBSERVATION_NOT_SUPERSESSION` si commit confirmado. Reevalúa permisos/fence después; si outcome no resoluble o revocación tardía, `RecoveryRequired(idempotencyKey)`, nunca afirmar rollback tras posible COMMITTED. Reintentar draft obsoleto rechaza stale, jamás duplica.
4. Sin owner ACTIVE ni Android UI ni acceso a Money/Stock ni egress; sin trigger automático. El host real/Android/sistema de permisos se montará sólo con permiso propio posterior.

**Tres rutas exactas C3 candidatas, NO AUTORIZADAS antes de guard/READY:**
- `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialTypedPriceRefresh.kt` (NUEVA)
- `v5/evidence/src/test/kotlin/ar/com/faro/v5/evidence/CommercialTypedPriceRefreshTest.kt` (NUEVA)
- `.github/workflows/faro-v5-s4-w04-c3-append-refresh.yml` (NUEVA).
Ningún cambio de SQL/legacy, app, graph, finance, Android o main.

## B5
T01 refresh produce segunda fila nueva con terms intactos; primera fila e IDs originales intactos.
T02 prepare y confirmación cancelada NO mutan; grants Household y Commercial independientes y contrato equivocado STOP.
T03 cambio epoch/pointer/foreground durante Review STOP, sin commit.
T04 otra observación entre Review y confirmación invalida watermark; stale sin escritura ni rebase automático.
T05 misma intención localRequestId/epoch no duplica tras primer append; replay y conflict los resuelve store con idempotency+terms, draft stale no se re-ejecuta.
T06 sourceRef hostil literal sin HTTP/file, moneda ARS/USD sin FX, observedAt nullable no se sustituye por acquiredAt, promo UNKNOWN no es vigente.
T07 crash/transacción SQLite/fallo de persistencia → RecoveryRequired, si COMMITTED antiguo permanece.
T08 revocación después del commit da RecoveryRequired, nunca "rechazado/no escrito"; producto fuera de registro o tipo TOOL no se refresca como MANUAL.
T09 schema1 o puerto no autorizado FAIL-CLOSED; no downgrade ni segundo root.
T10 read-back tras reopen muestra ambos records/terms persistidos; no UPDATE, sin recibos de Reality/Finance/stock.
T11 JUnit XML dirigido con casos nuevos, suite `:evidence:test`, guard exact3; release writer. G15/API35/portabilidad real NOT_RUN.

## Gate
Estado `B1_B5_PREAUDIT_NO_READY`. Esperar guard documental del SHA que incluye el B7 de C2, emitir permiso C3 READY exact3 sólo tras ese SUCCESS, validar protocolo y preflight sobre HEAD anterior al commit funcional. B7 scoped de C3 y W04-C global sólo con CI real. C2 C1B2 y anteriores permanecen certificados y no se repiten.
