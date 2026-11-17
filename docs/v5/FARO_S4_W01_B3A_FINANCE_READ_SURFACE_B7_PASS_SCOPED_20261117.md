# FARO V5 — W01 B3A · Auditoría final B7 scoped UI FINANZAS, solo DEV
Fecha AR: 2026-11-17; unidad `S4_W01_B3A_FINANCE_READ_SURFACE_DEV`. Commit funcional exacto `f9c524cfc3b0ddda93d3def16371efbbcda9f933`.

## B1
B1-B5 previos `docs/v5/FARO_S4_W01_B3A_FINANCE_READ_SURFACE_B1_B5_20261117.md`, guard `44076134595` SUCCESS. B5 independiente y READY exact3 `docs/v5/FARO_S4_W01_B3A_FINANCE_READ_SURFACE_READY_20261117.md`, guard preflight `44076409849` SUCCESS. Writer global epoch1057 HELD→RELEASED inmediatamente tras B6 código.
## B2
Tres rutas funcionales exactas en el commit: `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5FinanceReadSurface.kt`, `v5/surface/src/test/kotlin/ar/com/faro/v5/surface/FaroV5FinanceReadSurfaceContractTest.kt`, `.github/workflows/faro-v5-s4-w01-b3a-finance-read-surface-directed.yml`. Mensaje incluye FARO-UNIT correcto. Sin cambios en app, manifest, Reality, Finance owner, CompositionRoot, ShellNavigation, AGENTS, main o V4.4. No copiar certificaciones previas de M1C1.
## B3
Presentación con `FaroV5FinanceCoverage` READY/EMPTY_CONFIRMED/PARTIAL/DENIED/UNAVAILABLE/STALE y `FaroV5FinanceAccountCertainty`; objeto rechaza importes en cuentas sin verificación y rechaza cuentas/sourceRevision ante no autorización. READY exige fuente revisionada y cuentas verificadas, EMPTY_CONFIRMED revision con lista vacía. Un saldo 0.00 sólo se muestra si realmente VERIFIED. ARS/USD separados sin FX, signo negativo en color de error. Monedas sin exponente autorizado no exponen importe. Proyección deshabilitada con mensaje explícito W08. `FaroV5FinanceReadScreen` monta una lectura suspendible de port futuro con `Flow<Unit>` invalidaciones y borra valor al recibir cada invalidación; nunca ejecuta Apply ni un write.
## B4 — Evidencia CI real SHA exacto
- Directed `44076899701` COMPLETED/SUCCESS en `f9c524...`, Gradle `:surface:compileDebugKotlin` SUCCESS, `:surface:testDebugUnitTest` SUCCESS. Parser XML nominal **8/8 nuevos**, 0 failures, 0 errors, 0 skipped. Log: `W01_B3A_FINANCE_SURFACE_POLICY_8_OF_8`.
- Regresiones nominales de `FaroV5HomeIndicatorsContractTest`, `FaroV5AvisosSurfaceContractTest`, `FaroV5ShellNavigationContractTest`: **19 casos PASS**, no skipped; log: `W01_B3A_RELEVANT_NAVIGATION_HOME_AVISOS_REGRESSIONS_SUCCESS 19`. Artefacto `faro-v5-w01-b3a-finance-read-surface-junit` ID `14325363846`.
- Verificación estática de código publicado confirmó que ninguna de `FaroV5ShellNavigation.kt`, `FaroV5AdaptiveShell.kt`, `FaroV5CompositionRoot.java` llama `FaroV5FinanceReadScreen(`; no hay wiring productivo.
- Regresión general Surface `44076899540` COMPLETED/SUCCESS SHA exacta. Guardia código `44076899531` COMPLETED/SUCCESS SHA exacta.
- V2 Core `44076899664`: compilación variante aislada paso SUCCESS mientras la ejecución dirigida M2 JVM todavía estaba IN_PROGRESS al redactar. **No reportar V2 Core completo PASS hasta conclusión verificada**. No es prueba necesaria del nuevo port inerte sin montaje, y sus tests M2 no pertenecen a este delta.
## B5 — Limitaciones y riesgos
Sólo se certifica **contrato y render Compose DEV/dormant**; no hay port app conectado a Reality9, no se obtuvieron saldos reales ni prueba Android física G15 o TalkBack 200%. `FinanceSnapshot` solo no demuestra opening confirmado; Finance9 lleva `finance_opening_confirmation` con `LEGACY_UNVERIFIED` para saldos heredados. El futuro B3B DEBE comprobar esa procedencia contra un owner autorizado, generación+pointer/ACL y revisión antes de dar `VERIFIED`. No convertir `null` en 0, no fabricar W08 horizon.
El read model utiliza lista Kotlin read-only como entrada pero la procedencia de seguridad la aporta el futuro port app, no un DTO desde UI/Chat; no afirmar protección contra código malicioso del mismo proceso ni autenticación humano productivo.
W01 B3B/B4/C/D/E/F, G15 y trusted production host OPEN. V2 productiva OFF. W2 STOP. Preservar S2/S3/main/V4.4, sin red/API pagas, Auto/Noche OFF.
## B6
IMPLEMENTED code `f9c524cfc3b0ddda93d3def16371efbbcda9f933`.
## B7
**PASS_SCOPED_DEV_DORMANT_FINANCE_READ_SURFACE_8_OF_8_XML**. El permiso queda consumido/BLOCKED; writer epoch1058 se libera antes de última guardia documental. El próximo paso seguro es W01-B3B app read adapter B1-B5, NO montar la pantalla productiva sin root trust.
