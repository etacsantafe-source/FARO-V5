# FARO V5 — W04 F2C1B1B1A · owner DEV READ scopes · B7 PASS (SCOPED)

Fecha: 2026-11-23. Rama `work/s3-night-20261102`. Unidad `S4_W04_F2C1B1B1A_OWNER_READ_SCOPE_DEV`.
Último código `8a58d9f044a0c8b5d1e10abeba170d9ab5c99b22`. **B7 PASS solo para los DOS contratos DEV efímeros de lectura owner, NO para lectura de stores reales Android, D4/E, F3 o V2 productiva.**

## B7 — Evidencia exacta e independiente
- B1–B5: `docs/v5/FARO_S4_W04_F2C1B1B1A_OWNER_READ_SCOPE_B1_B5_20261123.md`, blob `a0c1faf84008d4923092b3a730c435845f359d94`. Guardia documental `45186230152` SUCCESS; permiso READY exact5 `1f5b2c8b6894315a41dc4fa77bbd80873892f711`, guardia `45186493222` SUCCESS. No se reejecutó B0.
- Primer código `2b6fc0d347d6c2fba22de5c4788430af1a96b16b`, guardia `45187167965` SUCCESS; CI `45187168132` FAILURE **antes de tests** por JVM 512 MiB en `:build-logic:compileKotlin`; evidencia RED_FIX `docs/v5/FARO_S4_W04_F2C1B1B1A_B7_RED_FIX_CI_HEAP_20261123.md` blob `3ce222bb45edadff20bec0b1acab3aae1b9c07a1`; no falsificar XML ni tests allí.
- RED_FIX1 código final `8a58d9f044a0c8b5d1e10abeba170d9ab5c99b22` modificó solamente CUATRO rutas del permiso original: workflow memoria 3 GiB, tests Kotlin `checkNotNull`, Evidence lease sin copia retenida de registros comerciales (solamente watermark); `FARO-UNIT:S4_W04_F2C1B1B1A_OWNER_READ_SCOPE_DEV` válido. Guardia exacta `45188613176` SUCCESS.
- CI dirigida FINAL `45188613035`: **COMPLETED SUCCESS** job `137740696300`; `Household10` Gradle BUILD SUCCESSFUL y tres tests, `Evidence2` Gradle BUILD SUCCESSFUL y tres tests, preflight READY cuatro rutas exactas. Marcador en job log `FARO_W04_F2C1B1B1A_OWNER_SCOPE_JUNIT_XML_PASS_6_6`.
- Artifact GitHub Actions `14824603707`, `w04-f2c1b1b1a-owner-scope-junit-xml`, ZIP descargado y validado independientemente; SHA256 ZIP **`fdb233f924e7f15f5d570aa06b84578571064dd6e328f298c2dedfcdec2a7036`** coincide con digest remoto. Dos XML reales:
  - `owner/household/build/test-results/test/TEST-ar.com.faro.v5.owner.household.spi.Household10DevReadScopeOwnerTest.xml`, SHA256 `e825ca670372672ecf0848574df5dbb2adfa1e788743010d056fcc806f2a0688`, **3/3 PASS**: open selección, negación need/consent, ABA/revoke.
  - `evidence/build/test-results/test/TEST-ar.com.faro.v5.evidence.CommercialEvidence2DevReadScopeOwnerTest.xml`, SHA256 `1cb134e0b62a5763e65ca60ee9b01a69e308ed61ffda065c439f3585ba81eaab`, **3/3 PASS**: términos tipados, ausencia/legado, watermark/epoch/revoke.
  - Se verificaron por parser independiente exactamente **6 métodos distintos; failures=0, errors=0, skipped=0**.
- Delta final revisado contra commit B7 RED_FIX: cuatro rutas exactas, 2 líneas workflow, cambio del Evidence grant sin caché y 8 asignaciones de JUnit corregidas; no cambios a F2C0/B0, D4/E, Android, `main`, V4.4 ni otros stores.

## Alcance y brecha aún abierta
Los dos objetos `Household10DevReadScopeOwner` y `CommercialEvidence2DevReadScopeOwner` poseen nonce, scope de principal/generación/secuencia/epoch, selección exacta y revocación irreversible si el owner source, objeto, watermark o permiso cambia. Son contratos DEV JVM: **fuentes inyectadas en JUnit son sintéticas**. Ninguna prueba comprueba que el host Android haya conectado la fuente SQLite ACTIVA Household10/Evidence2, ni que haya autenticado grants reales de los owners a través de la misma raíz/lease. La clase Kotlin pública tampoco es una frontera de seguridad contra código host malicioso. No declarar F2C1B1B1 integral PASS.

**SIGUIENTE F2C1B1B1B:** conectar estas dos fuentes owner a `FaroV5GenerationStoreGraph.Household10DevReadSession` real bajo un único lease; revocar ambas con el issuer y lifecycle; conservar ambos taps, N-033/N-034 y selección explícita; emitir B1–B5/READY propios y probar lectura POSITIVA REAL de Household10/Evidence2 ACTIVE tipada en Android API35, F1/D4/E sin simulaciones, con XML original. Posteriormente F3 integral. Sin pagos/Apply/red/G15/Auto/Noche; V2 productiva OFF.

## Cierre
B6 **DONE_VERIFIED**, B7 **PASS_SCOPED_DUAL_OWNER_DEV_JVM_6_OF_6**. READY consumido/BLOCKED; writer release requerido y verificación `RELEASED` epoch 1723 tras este checkpoint. Solo este alcance fue certificado.
