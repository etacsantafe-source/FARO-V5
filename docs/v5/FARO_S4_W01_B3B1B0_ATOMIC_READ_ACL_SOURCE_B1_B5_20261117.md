# FARO V5 — W01-B3B1B0 · Atomic Finance9 policy+provenance READ, B1–B5
Fecha Argentina: 2026-11-17. Head auditado `56feaaee5e20f0618089598593939835dd5d514a`, B3B1A B7 scoped guard `44085666062` SUCCESS, writer epoch1069 RELEASED. Unit candidata `S4_W01_B3B1B0_FINANCE9_SINGLE_SNAPSHOT_POLICY_AND_OPENING_DEV`; **DOC_ONLY, NO READY, B6 NOT_RUN**, W2 STOP.

## B1 — Confrontación contra código real
- B3B0 `RealityStore.readFinance9OpeningProvenanceForDevOnly()` ejecuta su propio `BEGIN DEFERRED TRANSACTION` → `readFinance()` + `finance_account_opening/finance_opening_confirmation` → `ROLLBACK`. B7 final `docs/v5/FARO_S4_W01_B3B0_OPENING_PROVENANCE_B7_PASS_SCOPED_20261117.md`, 8/8 JUnit y 20/20 CI. Este port NO verifica ACL.
- B3B1A `RealityStore.observeFinance9ReadPolicyForDevOnly()` ejecuta **otra** transacción `BEGIN DEFERRED` → `RealityFinance9Acl.observedCurrentEpochOrNull` + revision del ACL → `ROLLBACK`. B7 `docs/v5/FARO_S4_W01_B3B1A_READ_POLICY_OBSERVER_B7_PASS_SCOPED_20261117.md`, 8/8 JUnit, 21/21 workflows. Este port NO entrega saldos ni autoriza UI.
- **Carrera probada por inspección:** ejecutar los dos ports consecutivamente no garantiza una vista atómica: otro proceso puede revocar/renovar `finance_acl_policy`, alterar Finance o restaurar generación entre ambos `ROLLBACK`; no emitir `READY` desde un ensamblaje de dos resultados.
- Schema `finance_acl_policy` clava principalHash+generationId+pointerSequence+operationId+objectScope+policyEpoch/revoked/expires; `finance_acl_state.policy_revision` es monotónico. `FaroV5FinanceReadSurface` está DORMANT, no montado productivo. La apertura `LEGACY_UNVERIFIED` debe seguir con saldo NULL. `FileActiveGenerationPointer.addChangeListener` es **sólo intraproceso**, documentación expresa revalidación entre procesos requerida.

## B2 — Reuso sin duplicación
Una refactorización **interna** de `RealityStore.kt` extrae el cuerpo SQL de B3B0 como helper `readFinance9OpeningProvenanceInsideReadTransaction()` que NO inicia ni cierra transacción, ejecutado sólo bajo el mutex y snapshot previamente abierto; el port B3B0 certificado conserva su misma API y transacción independiente. El nuevo port compuesto usa la misma `Finance9Acl` de B3B1A y helper de B3B0 dentro **del mismo BEGIN DEFERRED / ROLLBACK**; no copiar consultas manuales nuevas, no duplicar la semántica de opening, no meter `BEGIN IMMEDIATE` ni hacer Apply/grants. Preserve source schema7/8, backup FBS2v4, previous tests, V4.4 UI references from P02/P03 (no code import).

## B3 — Alternativas
A **ADOPTAR sólo DEV/STAGED9**: observar `finance.snapshot.read` exact scope/epoch + recuperar `RealityFinance9OpeningProvenanceSnapshot` desde un único SQLite WAL snapshot y devolver un DTO combinado `RealityFinance9ObservedSourceRead` que sigue claramente marcado como **observed-only**, no admission de host ni autorización productiva. Debe fallar cerrado ante ACL ausente/expirada/revocada antes de snapshot, source inconsistente, contradicción o overflow, store closed/active Apply, schema7/8, rollback fallido. `Finance9Acl.observedCounterForStageTest` aporta política revisada del mismo snapshot.
B **DESCARTAR**: componer `B3B1A.observe()` con `B3B0.read()` fuera de transacción, usar `admitted()=true`, dar `READY` a Surface, tratar `CONFIRMED_IN_SQL` como firma humana o liberar datos sin ACL externo.
C **DIFERIR**: comprobación final de UID/Keystore/certificado local, hostEpoch, pointer real y un origen autorizado de invalidaciones interproceso. En B3B1B1 necesitará nuevo owner de host Android/ACTIVITY y prueba real; no se simulará con callbacks siempre-true.
Fuentes SQL/Android ya auditadas: https://www.sqlite.org/isolation.html y https://developer.android.com/topic/architecture/data-layer/offline-first. Sin API pagas.

## B4 — Plan técnico por unidad, no autorización
B3B1B0 se limita a cuatro rutas **candidatas** sin permiso:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9ObservedSourceReadPort.kt` — DTO combinado observed policy epoch/revision, sourceRevision y accounts, no grants.
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt` — helper extraído de B3B0 (sin nuevas consultas SQL), factory STAGED9 y lectura compuesta una sola transacción; preservar semántica del port original.
3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9ObservedSourceReadTest.kt` — 8 casos nuevos de SQL9 con dos SQLite connections, revocation/ABA, opening unknown, zero confirmed, snapshot consistent, denial.
4. `.github/workflows/faro-v5-s4-w01-b3b1b0-atomic-read-directed.yml` — CI exact XML + regresión dirigida de B3B0/B3B1A **por cambio real de helper**, no repetir ceremonias antiguas.
B3B1B1 futuro app adapter sólo si B3B1B0 obtiene B7; W01-B3B2 Android host físico después. Ninguna ruta autorizada hoy.

## B5 — Auditoría adversarial / STOP
- **Atomicidad**: el punto de linealización del lector es la primera lectura SQL en un `BEGIN DEFERRED`. Revocación de otro proceso posterior a esa lectura puede convivir con el snapshot antiguo; el resultado **no** es un lease durable válido al volver a la UI. Debe presentarse como observado en snapshot, no conceder lectura productiva o cachear balances. Revocar **antes** del snapshot y siguiente lectura debe denegar. Comprobar rollback y ausencia de escrituras.
- Si no puede extraerse el cuerpo de B3B0 sin cambiar sus invariantes, STOP/REDESIGN antes de READY; no hacer nested transaction. Tests originales B3B0 8/8 y B3B1A 8/8 deben permanecer verdes tras modificar `RealityStore`.
- El caller puede forjar principalHash, generationId, pointerSequence, expectedEpoch y fecha, lo que invalida cualquier afirmación de acceso real. La autoridad externa del HOST debe aprobar principal técnico atado a Keystore y UID y comparar el puntero activo, además de invalidar Surface en revocación/restore. **Solo información DEV, no producción**.
- Un `CONFIRMED` SQL con amount=0 es un dato confirmado **sintético SQL**, no consentimiento humano; `LEGACY_UNVERIFIED` nunca se traduce a saldo $0.
- Criterios T01 ausencia de ACL DENY; T02 scope exact válido + fuente consistente observado; T03 revoke/ABA DENY en siguiente lectura; T04 policy sólo Apply DENY; T05 expiry DENY; T06 unknown/legacy opening null preservado; T07 snapshot WAL entre procesos coherente; T08 legacy7/8/closed/overflow DENY y 0 mutations. **NOT_RUN**.
- G15 físico NOT_RUN, W1 B3B1B1/B3B2/B4/C/D/E/F OPEN, W2 STOP, V2 productiva OFF, Auto/Noche OFF, main/V4.4/S2/S3 y catálogo180 intactos.

B5 verdict: **PASS_PREAUDIT_FOR_INDEPENDENT_REFACTOR_REVIEW_ONLY**, NO READY. El nuevo B6 requiere auditoría B5 independiente de extracción/cuerpo SQL exacto, permiso B1–B5 READY exact4, guard preflight y writer CAS. No iniciar código si no se puede probar la unicidad transaccional.

## B6
NOT_RUN, no permiso funcional.
## B7
PENDING, requiere XML nueva + regresiones y guard final. Documento de preauditoría no cierra B3B1B0.
