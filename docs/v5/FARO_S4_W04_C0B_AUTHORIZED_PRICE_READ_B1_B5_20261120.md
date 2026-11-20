# FARO V5 — W04-C0B · Lectura AUTORIZADA precios comerciales — B1–B5
Fecha: 2026-11-20 Argentina. Unidad exacta `S4_W04_C0B_AUTHORIZED_PRICE_HISTORY_READ_DEV`.
Baseline HEAD `5df8c94099bad13c396313cb8f184194d1a4b054`; C0A B7 PASS scoped DEV `ebaaf5afbabda5a848fd266a5296a85ff597ef1f`, 12/12 XML run 44782895246; no repetir C0A ni W04-A/B0/B1.

## B1
Verificado en HEAD: `FaroV5GenerationStoreGraph.kt` blob `06d04a22d23aec6d67294002adc01c8aaf20a785` aloja simultáneamente Reality, CommercialEvidence y Workspace bajo el lease `finance9-dev-root.lock`; `Household10DevReadSession` tiene gate compartido y `Household10DevApplySession` expone Reality/Workspace, NO lectura comercial. `S4W04HouseholdNeedApplyHost.kt` revisa `ownerCurrent() && trustedForeground()`, epoch y puntero durable; su política existente sólo acepta operación/need target, no un grant commercial.
`CommercialEvidence.kt` @blob `cf9d53089cafd93538e6b03f9873c98264d20b65`: `CommercialPriceHistoryReadExecutor` constructor comprueba descriptor canonical `N-034/commercial-evidence/READ_ONLY/BINDABLE`; `execute(key,limit)` usa `CommercialEvidenceStore.priceHistory(1..200)`. Observations guardan merchant, currency, amountMinor positivo, sourceKind, sourceRef opaca, observedAt nullable; acquisition timestamp separado. Contrato `shopping.snapshot.read` N-033 household READ_ONLY; contrato `commercial.price_history.read` N-034 Evidence READ_ONLY, ambos en catálogo generado, sin nuevas operaciones.
`HouseholdNeedPersistencePort.kt` @blob `f48e6528ffcdf4986274dc5111ca0b8a10255c00`: snapshot global revision y need revision/OPEN. `S4W04CommercialNeedProductSelection.kt` @blob `b2f5c63ea25d01ef092606a588c1df74fca6b047` selecciona explícitamente con fence generation/pointer/epoch/principal y `revalidate`. Gaps reales: no host actual con DOBLE autoridad/resultado Coverage; Android/UI no autorizado.
Resultado B1: puede reutilizarse la misma sesión y executor; ninguna migración ni tabla necesaria.

## B2
Contraste documentado contra V4.4 SmartCompra P02 L11/L15 y P04: V4.4 enlaza catálogo de precio/comercio a necesidades humanas y presupuesto; V5 separa Evidence (observación, no verdad financiera) de Reality (necesidad). Reusar nombres/UX como referencia, NO identidad textual ni un segundo CompositionRoot ni lógica de "precio de hoy". C0A ya resolvió needId→productKey explícito, NO volver a programarlo.

## B3
USAR: `CommercialPriceHistoryReadExecutor` original, grafo abierto con un lease, `BuiltInContractCatalog`; sin APIs nuevas. ADAPTAR: puerta en la sesión DEV existente (`Household10DevReadSession`→ApplySession) que toma callback `householdAdmission` + `commercialAdmission`, revisa ambos antes/después dentro del mismo `MaterialWriteGate` reentrante; port no expone store mutable. ADAPTAR: coordinador App `S4W04CommercialPriceReadHost` con `select` C0A y revalidate antes/después del read. REFERENCIA: P04 y Legacy para UX futura. DESCARTAR: abrir dos roots/locks, store directo desde UI, grants comerciales implícitos por tener grant Household, saltarse executor catalogado, incluir cambio de schema C1, scrapear, cloud.
Hipótesis: sobre raíz materializada con una observación comercial preexistente y Need OPEN, con ambos grants válidos la lectura devuelve evidencia exacta sin write; retirar cualquiera de los dos, variar revisión/fence o impedir foreground devuelve rechazo sin datos.

## B4
Contrato DEV:
- `S4W04HouseholdNeedApplyHost.selectCommercialProduct(needId, productKey, presented, explicitlyChosen)`: requiere foreground, snapshot lectura admitida `shopping.snapshot.read` (N-033 household, READ_ONLY), need OPEN; fence deriva **sólo del host confiable** desde pinned generationId, pointer.sequence, hostEpoch y principal, NO from Chat.
- `S4W04HouseholdNeedApplyHost.commercialPriceHistoryForSelection(candidate,limit,commercialAdmission)`: callback comercial independiente y explícito; valida `commercial.price_history.read` frozen canonical N-034. C0A revalidate contra snapshot/fence antes y después; comprueba admisión Household+Evidence con principal target correcto, foreground y pointer cada fase.
- `FaroV5GenerationStoreGraph.Household10DevApplySession.readCommercialPriceHistory(...)`: un solo root ya abierto; no expone `CommercialEvidenceStore`, gate/lease antes+después; `CommercialPriceHistoryReadExecutor` valida el catálogo y lee datos v1 existentes. Sin mutación.
- View sólo read-only `CommercialPriceHistoryView` con registros reales y cobertura explícita `localityKnown=false`, `quoteValidityKnown=false`; preserved `observedAtEpochMillis` nullable, `acquiredAtEpochMillis` separada. No tasa de cambio ni precios actualizados inventados; sourceRef sólo texto, nunca URL ejecutable.
- Limit 1..200; empty history es Observed con 0 registros (no inventa). Fail closed para cambio ABA/need/completion/revocación/closed/stale contract.
- No llamadas externas ni permitir Store.record/Apply/Finance/Compras/stock. No crear nueva sesión/lease.
**Rutas exactas propuestas para el permiso individual C0B (todas y solamente cinco):**
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`
2. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04HouseholdNeedApplyHost.kt`
3. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialPriceReadHost.kt` (NUEVA)
4. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04CommercialPriceReadHostTest.kt` (NUEVA)
5. `.github/workflows/faro-v5-s4-w04-c0b-price-read.yml` (NUEVA)
No modificar `CommercialEvidence.kt`, schema, Kotlin C0A, catálogo, Gradle, otros owners o app productiva.

## B5
Matriz adversarial PRECODE (TESTS NOT_RUN hasta B6):
T01 lectura real de observaciones SQLite sin mutar Reality/Evidence/receipts; T02 snapshot needId y selección explícita, nombres duplicados; T03 falta household read grant; T04 falta commercial read grant; T05 revocación durante/final; T06 pointer ABA, generación o epoch stale; T07 need revisión/completed/removal; T08 background o closed; T09 limit 0/201, contrato distinto o que ya no es READ_ONLY; T10 sourceRef hostil como texto opaco; T11 fecha observedAt null ≠ acquiredAt; T12 cero observaciones y moneda/localidad desconocida (no precio actual inventado); T13 nueva conexión/lease rehusada mientras host está abierto; T14 cierre/reapertura sin escritura. Las pruebas dirigidas deben cubrir este perímetro sin pruebas físicas.
Antes de B6: crear permiso exact5 con B1-B5 VERIFIED @blob de este documento y preflight de cinco rutas en workflow contra HEAD^. Guard documental SUCCESS. B6 deberá incluir tests reales y XML; B7 sólo tras SHA/CI/xml verificados.
## Estado
Este documento B1–B5 es preauditoría. Por sí mismo NO concede código ni pruebas PASS. El permiso actual para C0A está consumido/BLOCKED. C0B READY solo tras gate documental + CAS + permiso exact5.