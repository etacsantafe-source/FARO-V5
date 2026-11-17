# FARO V5 — W01 B3B1B1A · B7 PASS limitado: conversor puro Finance9 observado -> UI (DEV)
Fecha local AR: 2026-11-17. Código exacto `78761e59d19b21fac9ec77beca71c736635a6e27`. Rama `work/s3-night-20261102`.

## B1 — Evidencia y vigencia
- B1–B5 previos de `docs/v5/FARO_S4_W01_B3B1B1_TRUSTED_READ_ADAPTER_B1_B5_20261117.md` (blob `1ab8b0792325fb63d38bca3e32b3629896a80e67`), guard `44090592005` SUCCESS.
- B5 independiente, READY exact3 en `docs/v5/FARO_S4_W01_B3B1B1A_MAPPER_INDEPENDENT_B5_READY_20261117.md` (blob `96acb29645e76eaee5814688dd78e30e46b78720`), preflight `FARO_PROTOCOL_PREFLIGHT_PASS S4_W01_B3B1B1A_FINANCE9_INERT_UI_MAPPER_DEV 09b6c265acc4b101e0f5a449d69f2070892259b6`, guard `44091208614` SUCCESS.
- B3B1B0 lectura SQL de policy+opening en UNA transacción WAL y B3A Surface dormant ya B7 PASS scoped; NO repetir sus pruebas.

## B2 — Commit funcional y superficie de cambio
Mensaje de `78761e59d19b21fac9ec77beca71c736635a6e27` incluye `FARO-UNIT:S4_W01_B3B1B1A_FINANCE9_INERT_UI_MAPPER_DEV`.
Diff EXACTAMENTE 3 rutas READY:
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B3B1Finance9UiMapper.kt` — nuevo mapper `internal`, puramente valor, no port, SQL ni flujo de identidad.
2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B3B1Finance9UiMapperTest.kt` — ocho JUnit con fuentes sintéticas.
3. `.github/workflows/faro-v5-s4-w01-b3b1b1a-mapper-directed.yml` — CI XML y compilación DEV.
Nada en Activity, CompositionRoot, Surface, Navigation, RealityStore, migración, FBS2v4, Apply/receipts, S2/S3/main/V4.4/Permissions productivos.

## B3 — Semántica verificada
`mapObserved(null)` retorna UNAVAILABLE sin cuentas, generation/pointer ni saldos. Source observado vacío se distingue como EMPTY_CONFIRMED, nunca inferido desde null.
CONFIRMED_IN_SQL con ARS o USD conserva `ledgerBalanceMinor` real (negativo, positivo y cero confirmado) y revisión. LEGACY_UNVERIFIED/MISSING_CONFIRMATION conserva null y certeza OPENING_UNVERIFIED, sin saldo supuesto. Moneda sin escala permitida (ejemplo JPY) produce PARTIAL y amount null; no conversiones FX. La mezcla de filas produce PARTIAL, no READY global. DTO observado es falsificable desde código DEV: esto NO es autenticación Android, consentimiento humano, permiso productivo o garantía contra revocación entre procesos.

## B4 — CI por SHA real y XML nominal
- Workflow dirigido `44091802896`, job `134068543507`: **COMPLETED SUCCESS** sobre `78761e59d19b21fac9ec77beca71c736635a6e27`. `:app:testDebugUnitTest` SUCCESS, `:app:compileV2CoreDevKotlin` SUCCESS; verificador XML y no shipping wiring SUCCESS.
- Log exacto `W01_B3B1B1A_INERT_MAPPER_8_OF_8`: **8 pruebas, 0 failures, 0 errors, 0 skipped**, casos nominales m01–m08. Mensaje `NO_AUTHENTICATED_READ_OR_ANDROID_G15_ATTESTED`.
- Artefacto XML `faro-v5-w01-b3b1b1a-mapper-junit` ID `14331369236`, asociado a este SHA exacto.
- Guardia obligatoria `44091802858` COMPLETED SUCCESS.
- Regresión S3 Home `44091802772` COMPLETED SUCCESS; M5 APK DEV aislada `44091802667` COMPLETED SUCCESS; V2 core M1/M2 `44091802700` COMPLETED SUCCESS. Todos los cinco workflows disparados por este SHA: **5/5 SUCCESS**, cero fallos.
- Anteriores B3A/B3B0/B3B1A/B3B1B0 no se reejecutaron manualmente.

## B5 — Limitaciones aún abiertas
B3B1B1A sólo transforma datos; la B3B1B1B futura deberá autenticar principal técnico de AndroidKeyStore+UID, pointer durable exacto generation+sequence y hostEpoch, y controlar invalidación interproceso/revocation/lifecycle con fail-closed ANTES de presentar valores. `CONFIRMED_IN_SQL` no es prueba humana; un `observed` SQL no es grant real. `FileActiveGenerationPointer.addChangeListener` es sólo intraproceso; revocación remota posterior al snapshot puede dejar datos antiguos. No se probó Moto G15/biometría real para este port; no hay pantalla FINANZAS operativa ni cifras productivas. B3B1B1B y B3B2/G15 siguen OPEN, W01 B4/C/D/E/F OPEN, W2 STOP, V2 productiva OFF, Auto/Noche OFF, sin API paga ni operación bancaria.

## B6
IMPLEMENTED B6 exact code `78761e59d19b21fac9ec77beca71c736635a6e27`, writer epoch 1079 HELD -> RELEASED epoch1080 ANTES de CI. Ningún otro funcional.
## B7
**PASS_SCOPED_DEV_INERT_MAPPER_8_OF_8_JUNIT_AND_ALL_5_CI_SUCCESS** para B3B1B1A únicamente; no B7 de B3B1B1B ni de W1 global. Consumir permiso READY a BLOCKED en documento/continuidad, liberar writer para este checkpoint y verificar guard documental en HEAD nuevo. Nunca certificar cierre final sin verificación.
