# FARO V5 · W05-A1B3C1B · B7 PASS SCOPED · FBS2v8 Evidence1 SQL11 journaled DEV cutover / recovery

Fecha: 2026-11-24 Argentina. Repositorio `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`.

## B1–B5 — Cadena certificada

Entrada `589f3bacef552b3a215fcf1ae3af077762004b49`, W05 A1B3C1A B7 PASS: C1A ticket/checksum/fingerprint/ciphertext original y journal PREPARING→STAGED→VALIDATED, 8/8 XML y writer RELEASED epoch1878.
Preauditoría C1B `docs/v5/FARO_S4_W05_A1B3C1B_SQL11_JOURNALED_CUTOVER_B1_B5_20261124.md`, blob `2e39f4293f1466e65226387d5a60df0ebc360678`, commit `71050c378d58b0c289fc10e3fc42382708ed6cd2`, guard `45493942595` SUCCESS.
Permiso READY exact3 `fb28748bc1489918b74d61aa7bc48e453821cff7`, guard `45494090819` SUCCESS. No repetición de A1B3A/B/C1A ni W1–W4.

## B6 — Código, RED_FIX y CI real

Primer código `e836dd6e511e58eb99b6e98bcfb2a043bdcefb44`, exactamente tres rutas:
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11DevJournaledCutover.kt`
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11DevJournaledCutoverTest.kt`
- `.github/workflows/faro-v5-s4-w05-a1b3c1b-journaled-cutover.yml`

Primera guard `45494473484` SUCCESS pero primera CI `45494473649` job `138801167737` **FAILURE** por 2/8 JUnit rollback, XML original artefacto `14967777673` sha256 `db239ad5750dbc4ec3761007cf28c04dcb6315314a387fa2da0c8549fd744b82`, tests 8, failures 2, errors 0, skipped 0. Falló origen en callback DEV `projections.reconcile`, que solo permitía target, aun cuando el coordinator verifica obligatoriamente el origen durante rollback. No hubo corrupción de pointer/JUnit silenciosa: defectos documentados `docs/v5/FARO_S4_W05_A1B3C1B_RED_FIX_ROLLBACK_PROJECTION_45494473649_20261124.md`, blob `0fa9ff09c9270bc41cff7f989cbb576c918a502e`. Permiso RED_FIX emitido `c87dbde28ab85e447fb1e899c83c17ba097ae384`, guard `45495360277` SUCCESS.

Commit final de reparación **`432e6ece49eb50d0e7a607e581eab6c975c5cac4`** exactamente 2 rutas: callback acepta exclusivamente el target bajo pointer full + journal HEALTH_VERIFIED, o el origen bajo pointer full sequence+2 y journal ROLLED_BACK_POST_SWITCH/ROLLBACK_VERIFIED; cualquier tercero se rechaza. Proyecciones OS DEV siguen NOOP. Workflow admite exactamente dos rutas del RED_FIX o tres del original, preflight parent REAL.

**Guard final código `45495586735` COMPLETED SUCCESS**.
**CI dirigida `45495586732`**, job `138805068645`, código SHA exacto, **COMPLETED SUCCESS**. Preflight parent RED_FIX 2/2 PASS, Gradle `BUILD SUCCESSFUL in 3m 51s`; marcador `FARO_W05_A1B3C1B_SQL11_JOURNALED_DEV_CUTOVER_XML_PASS_8_8`.
Artefacto GitHub original `14966929882`, `w05-a1b3c1b-sql11-journaled-cutover-xml`, ZIP 872 bytes, digest `sha256:05749fc041478996b922b5432ea070ffb053352b1b31b619ec0dc821c593c9fe`. ZIP descargado y verificado hash con Python; XML original inspeccionado con `zipfile + xml.etree.ElementTree`: **tests=8, failures=0, errors=0, skipped=0**; ocho testcase originales sin fallos, duración reportada 16.113s.

## B7 — PASS acotado y matriz adversarial

**B7_PASS_SCOPED_EVIDENCE1_SQL11_JOURNALED_DEV_CUTOVER_RECOVERY_ROLLBACK_XML_8_8**.

C1B usa el ticket previamente autorizado C1A, sin crear permiso nuevo ni reescribir archivo autenticado. START exige nueva confirmación en foreground, FBS2v8 ciphertext original reautenticado, identidad durable existente, target y puntero completos, cinco SQLite sellados y H10/H11R SQL11 genuinos. START usa `ValidatedGenerationActivationCoordinator` (kernel compartido) en vez de CAS paralelo. En root DEV aislado:
1. Switch lleva pointer de origen sequence N a target SQL11 sequence N+1, journal COMPLETED con materialWritesAccepted y fence RESTORE **solo del entorno DEV**, comprobando readback H10+H11R.
2. Restart en COMPLETED devuelve ACTIVE_ELEVEN sin publicar nuevo pointer.
3. Crash simulado tras pointer target antes de avance del journal SWITCH_PREPARED recupera idempotentemente y completa activación.
4. Falla de salud SQL11 tras switch revierte al origen pointer N+2 con journal ROLLBACK_VERIFIED, **sin material writes en destino ni replay-fence target**.
5. Crash tras rollback pointer antes de journal se recupera ROLLBACK_PREPARED → ROLLBACK_VERIFIED, sin duplicación.
6. PREPARING/STAGED/VALIDATED de C1A no dispara switch automático al arrancar: requiere nueva autorización foreground.
7. Contraseña/nonce/archivo alterado, ticket corrupto y drift Reality/host/pointer fallan sin activar.
8. Verificador SQL11 de cinco SQLite exactos bajo root lock + gate global: schema11 Reality H10/H11R, ComercialEvidence1, Web2, Auxiliary4 y Workspace4. No reutiliza opener SQL10, ni abre graph Android productivo.

**NO CIERRE GLOBAL A1:** este PASS prueba DEV JVM Evidence1, no el Android host real V2/SQL11, no un UI/productive app graph, ni inicio de stock Apply. Resta **A1B3C1C** Android/startup/reopen host y **Evidence2 FBS2v8** independiente; después W05-A1C entrega la mutación Review/Apply/Undo de recepción stock, siempre explícita y certificada. No pruebas físicas Moto G15 este turno. Mantener V2 productiva, Auto/Noche, pagos y stock real OFF; no escribir main.

Para cerrar B7: consumir permiso READY a BLOCKED, continuidad a C1C, writer RELEASED, verificar guard final contra HEAD exacto. No afirmar A1 completo.
