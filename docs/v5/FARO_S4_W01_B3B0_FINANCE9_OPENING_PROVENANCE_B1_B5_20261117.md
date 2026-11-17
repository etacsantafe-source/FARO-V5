# FARO V5 — W01-B3B0 · Procedencia de apertura REAL vs desconocida en lectura Finance9
Fecha AR 2026-11-17. Unidad candidata `S4_W01_B3B0_FINANCE9_OPENING_PROVENANCE_READ_DEV`. Baseline exacto `3a5fe5b0aef5406cef350173217eee3185bfa980`. Writer epoch1059 DOC_ONLY; B6 NOT_RUN, sin READY.
Objetivo único: habilitar en una etapa DEV posterior un **read model de procedencia** para la pantalla W01-B3A ya certificada, sin falsear el saldo de apertura. **No** abrir FINANZAS productiva ni trasladar datos de prueba.

## B1 — Evidencia: brecha real del código y del esquema
- W01-B3A UI code `f9c524cfc3b0ddda93d3def16371efbbcda9f933`, B7 PASS scoped checkpoint `3a5fe5b0aef5406cef350173217eee3185bfa980`, 8/8 JUnit, 19 regresiones dirigidas, guard `44077972825` SUCCESS, writer RELEASED epoch1058, permiso BLOCKED. NO repetir.
- `RealityStore.kt` en `readFinance()` lee apertura mediante `LEFT JOIN finance_account_opening` y **`COALESCE(o.amount_minor, 0)`**. Por tanto, `FinanceSnapshot.openingBalanceMinor==0` NO prueba que el usuario confirmó una apertura de cero ni que exista `finance_account_opening`. **Prohibido usar directamente `FinanceSnapshot.ledgerMinor()` para `FaroV5FinanceAccountLine.VERIFIED` en una UI nueva.**
- `RealityStore.createFinanceAccount` inserta `finance_account_opening` solo si `openingBalanceMinor > 0`, y no inserta automáticamente `finance_opening_confirmation` en el fragmento inspeccionado. Incluso cero explícito no puede inferirse del resultado del JOIN actual.
- `RealityFinance9Schema.kt` agrega `finance_opening_confirmation(account_id PK,amount_minor NULLABLE,confirmation_state in LEGACY_UNVERIFIED/CONFIRMED, confirmed_at_ms, provenance)` con CHECK: `LEGACY_UNVERIFIED => amount/confirmed_at NULL`, `CONFIRMED => amount y confirmed_at no NULL`. Migración 8→9 inserta `LEGACY_UNVERIFIED` por cuenta migrada, **sin** forzar monto 0; esto debe conservarse.
- `FaroV5FinanceSearchReadSource.kt` es lector DEV no montado para búsqueda, usa generation/aclEpoch/owner revision antes y después; NO expone balances ni es autoridad UI. `FaroV5FinanceReadSurface.kt` nuevo exige `READY` sourceRevision, VERIFIED account, ARS/USD y lectura temporal invalidable. No existe adapter `FaroV5FinanceReadPort` autenticado para esos datos.
- La admisión SQL ACL de M1C1B1C es DEV/STAGED9, no prueba humanos ni lectura shipping; M1B G15 hardware pendiente.

## B2 — Reuso y comparación
Conservar snapshot y ledger Finance del owner como datos locales, lectura mediante RealityStore propietario, no sumar/convertir ARS/USD. Reusar `RealityFinance9Schema`, DDL de confirmación, `FinanceSnapshot` revision, `Finance9Acl` y pointer generation fencing; reutilizar `FaroV5FinanceUiSnapshot` W01-B3A sin modificarlo ahora. UX V4.4 P02/P03 es referencia de cuentas pero no credencial/proveniencia. No crear tabla ni versión nueva ni tocar FBS2v4. El source antiguo schema7/8 debe seguir funcionando.

## B3 — Investigación de alternativas y elección
A **ADOPTAR** DEV: port tipado de lectura del propietario Reality9 con dataset de `FinanceSnapshot` y **confirmación** de cada cuenta consultados bajo un snapshot SQLite coherente; solo `CONFIRMED` con igualdad de monto respecto de `finance_account_opening` (o cero explícito avalado por fuente) puede ofrecer `confirmedOpeningMinor`. Si no hay confirmación, `null` + OPENING_UNVERIFIED. Un adapter app FUTURO comprobará ACL/generation/pointer/host epoch antes/después de todo read, y solo él convertirá apertura+entries en saldo VERIFIED. DENY en discrepancia.
B **DESCARTAR**: `FinanceSnapshot.ledgerMinor()` + `COALESCE` como saldo confirmado, asumir `openingBalanceMinor=0` como confirmación, insertar ficticiamente `CONFIRMED`, usar otro store o leer SQL directamente desde Compose, sumar monedas, conceder permiso a partir de Workspace APPROVED o un DTO de UI.
C **DEFERIR** hasta B3B1: proveedor de invalidaciones `Flow<Unit>` desde app con revocación, cutover/ABA, policy epoch y lectura permitida, tests de 2 procesos; B3B0 no compone Activities ni shipping.
Fuentes ya auditadas: SQLite aislamiento https://www.sqlite.org/isolation.html; transacciones https://www.sqlite.org/lang_transaction.html; Android offline-first https://developer.android.com/topic/architecture/data-layer/offline-first.

## B4 — Partición de ejecución con permisos específicos, NO READY todavía
`W01-B3B0` una lectura DEV de procedencia Reality9:
1. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9OpeningProvenanceReadPort.kt` NEW DTO tipado, epoch/revision y fuente; NULL nunca cero.
2. `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt` factory STAGED9 explícita, un snapshot de cuentas/aperturas/entradas bajo lock+lectura transaccional coherente; la factoría rechaza schema7/8/closed/Apply en curso, no escribe.
3. `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9OpeningProvenanceReadTest.kt` NEW JUnit SQLite real.
4. `.github/workflows/faro-v5-s4-w01-b3b0-opening-provenance-directed.yml` NEW CI con XML exact y regresión dirigida de Reality9 legacy/opening.
Ningún cambio a app/Surface/manifest ni Compose. `W01-B3B1` futuro adapter app separado con ACL/pointer/epoch, `FaroV5FinanceReadPort` e invalidaciones owner. `W01-B3B2` eventual host DEV read-only con Compose y prueba instrumental; product shipping sigue STOP.
Casos del futuro B3B0 `O01–O08` **todos NOT_RUN**:
O01 schema9 migrado opening LEGACY_UNVERIFIED -> null no 0;
O02 sin fila confirmation -> UNKNOWN (no fabricate);
O03 CONFIRMED amount=0 sólo desde fuente persistida/verified, revision estable;
O04 CONFIRMED monto positivo discrepante de opening ledger -> DENY;
O05 account sin opening física y confirmación incompatible -> DENY;
O06 entries revisadas con saldo +/- sólo si opening confirmado y sin overflow;
O07 lectura concurrente revision changed / rollback / close -> DENY o STALE, nunca PASS con snapshot mixto;
O08 schema7/8 y store cerrado -> DENY, sin writes ni migraciones.
B3B0 **NO** emite policy/grant ni permite Apply ni asigna autoridad humana.

## B5 — Auditoría adversarial antes de código
- El estado `CONFIRMED` no es automáticamente auténtico: la tabla puede existir por datos heredados o pruebas; evaluar procedencia permitida, escritura/receipt y coherencia con `finance_account_opening` antes de verificación. Confirmed no debe reescribirse durante una lectura.
- Snapshot coherente: cambios interproceso pueden ocurrir entre SELECT de accounts, opening y entries si no se fijan dentro de una transacción/read snapshot; evaluar lock/active apply scope y no tomar un BEGIN que interrumpa el writer del motor. Si no puede garantizarse, B5 STOP/partir unidad.
- La `finance_opening_confirmation` no está rellenada por `createFinanceAccount` actual, así que **B3B0 puede entregar estado no verificado incluso para cuenta nueva; eso es correcto**. No diseñar inserción de confirmaciones dentro de B3B0, ni inventar 0.
- Sólo el futuro adaptador app B3B1 puede acreditar ACL, UID técnico, pointer+sequence y revision; no pasar de aquí a `FaroV5FinanceUiSnapshot.READY` automático. La UI W01-B3A permanece DORMANT.
- PREPARED/COMMITTED/Undo/recovery/FBS2v4, schema7/8, S2/S3/main/V4.4, W2 STOP, V2 productiva/Auto/Noche OFF. Sin red/API pagas ni datos personales exportados. No hardware G15 ni cambios al teléfono.
- **NO READY aún:** auditar contrato de coherencia del snapshot SQLite y confirmar campos de la tabla `finance_account_opening` vs `finance_opening_confirmation` para evitar que `CONFIRMED` sea inconsistente, hacer B5 independiente y luego READY exact4/guard+preflight.

## B6
NOT_RUN. Trabajo DOC_ONLY; no escribir código hasta guard y READY independiente.
## B7
PENDING. Requerirá CI real + XML exact O01–O08, guard del commit y writer RELEASED; nunca certificar UI productiva ni fuente ACL.
