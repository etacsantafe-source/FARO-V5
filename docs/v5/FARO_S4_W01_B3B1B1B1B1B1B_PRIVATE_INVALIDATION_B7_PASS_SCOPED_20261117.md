# FARO V5 W01 B3B1B1B1B1B1B — B7 PASS acotado, provider aislado w01FinanceDev
Fecha Argentina: 2026-11-17. Unidad `S4_W01_B3B1B1B1B1B1B_ANDROID_PRIVATE_INVALIDATION_DEV`. **PASS_SCOPED** a infraestructura Android DEV de invalidaciones, **NO** publisher SQL cooperante en producción, NO test Android real de IPC ni cierre W1.

## B1 — Antecedentes y permiso final
- Primera infraestructura código `db714a0e25426c6f6b66936cef632685d4f62291`, dirigido `44151067699` 8/8 JVM+Android compile PASS; **B7 RED_FIX** por romper invariante V2 CORE cero providers: run `44151067496` fallo `V2_CORE_M2B_UNEXPECTED_PROVIDERS`. B7 RED_FIX documentado `docs/v5/FARO_S4_W01_B3B1B1B1B1B1B_PRIVATE_INVALIDATION_B7_RED_FIX_20261117.md` blob `e51cba14d8e241f042e1e3ed66f4bc9f78b273a0`. No maquillar ese fallo histórico.
- B1–B5 del arreglo en `docs/v5/FARO_S4_W01_B3B1B1B1B1B1B_ISOLATED_VARIANT_RED_FIX_B1_B5_20261117.md`, blob `c38c0ea179ce720c77287103205b14d94ab2f740`, guard `44153192083` SUCCESS.
- B5 independiente del arreglo `docs/v5/FARO_S4_W01_B3B1B1B1B1B1B_ISOLATED_VARIANT_INDEPENDENT_B5_20261117.md`, blob `507740592201b7d5a6c29af32ae00f4559336ea2`, guard `44153482281` SUCCESS.
- Permiso RED_FIX READY exact4, mismo unit_id, en parent documental `18896f3478b6590a283fd1953faea6612c19c3a3`, guard `44153705198` SUCCESS, `last_code_review=RED_FIX` anclado a db714 y evidencia exacta. Preflight parent verificado en run dirigido `44154198775`.
- Writer se adquirió por CAS sólo en bloques de escritura, **RELEASED epoch1131** antes de CI. Jamás mantener writer durante compilación.

## B2 — Diff exacto auditado
Commit corrección `6eece332b81436af3dbff955b2800557440045ff`, parent exacto READY `18896f3478b6590a283fd1953faea6612c19c3a3`, mensaje `FARO-UNIT:S4_W01_B3B1B1B1B1B1B_ANDROID_PRIVATE_INVALIDATION_DEV`. GitHub compare: exactamente cuatro archivos:
1. `v5/app/build.gradle.kts` +10 líneas: nuevo build type `w01FinanceDev`, UID/applicationId aislado `ar.com.faro.alpha.w01financedev`, fuentes Kotlin DEV previamente existentes.
2. `v5/app/src/v2CoreDev/AndroidManifest.xml`: revertidas las 7 líneas de provider; restaurado exactamente blob histórico `97a23a12983f1a6e9ac973a4b4948142d477c439`, que mantiene **CERO** providers.
3. `v5/app/src/w01FinanceDev/AndroidManifest.xml` nuevo, hardening offline y único ContentProvider `exported=false`, `grantUriPermissions=false`, `${applicationId}.finance9read`.
4. `.github/workflows/faro-v5-s4-w01-b3b1b1b1b1b1b-private-invalidation.yml` amplía CI a test exact8, compilación/merged y packaged manifests de dos variantes y segregación 0 vs 1 provider.
No cambia SQL, RealityStore, RealityFinance9Acl, Apply, receipts, S3/S2, FBS2v4, V4.4, `main`, product CompositionRoot ni W2. El provider y bridge Java/Kotlin anterior se conservan DORMANT sin reescribir código que ya hizo 8/8 JVM PASS.

## B3 — CI/JUnit XML, control de aislamiento y APK exacto
- Guardia del commit de código `44154198669` COMPLETED SUCCESS, incluyendo 14 autotests del protocolo, B1–B5/RED_FIX parent y preflight exact4.
- Workflow dirigido `44154198775`, job `134290381168`, SHA exacto `6eece332b81436af3dbff955b2800557440045ff`, COMPLETED SUCCESS:
  - Test JUnit `S4W01FinanceReadPrivateInvalidationTest` **8/8, 0 fallos, 0 errors, 0 skip**, verificado por XML del propio job y los 8 nombres I01–I08. Marcador `W01_RED_FIX_JVM_8_OF_8_PASS`. Artefacto `faro-v5-w01-b3b1b1b1b1b1b-redfix-junit`, ID `14360552430`; el ZIP no fue inspeccionado manualmente por fuera de CI.
  - Android compile de `:app:compileV2CoreDevKotlin` y `:app:compileW01FinanceDevKotlin`, y procesamiento merged+packaged manifest de ambos: tres invocaciones Gradle **BUILD SUCCESSFUL** (3m54s, 34s y 33s, evidencias de logs del job). Marcadores `V2_CORE_ZERO_PROVIDERS_PRESERVED`, `W01_FINANCE_DEV_ONE_PRIVATE_PROVIDER_OFFLINE`. Confirmó V2 CORE=0 providers y W01 DEV=1 privado, autoridad específica appId, sin INTERNET.
  - Preflight log `FARO_PROTOCOL_PREFLIGHT_PASS S4_W01_B3B1B1B1B1B1B_ANDROID_PRIVATE_INVALIDATION_DEV 18896f3478b6590a283fd1953faea6612c19c3a3`.
- **Regresión crítica antes roja recuperada:** V2 CORE M1/M2 full-directed `44154198671`, job `134290381249`, COMPLETED SUCCESS, log `V2_CORE_M1_ISOLATION_PASS 4/4`. No se debilitó su checker de providers.
- También SUCCESS sobre mismo SHA: M5 DEV APK `44154198682`, G4 four-owner `44154198677`, S3 Home `44154198765`.
- **No afirmar CI global verde:** M1B biometría `44154198674` falla por descarga Gradle Maven HTTP **429 Too Many Requests** de dependencias Kotlin antes de ejecutar tests; no es prueba de regresión funcional, pero no es PASS. S3 integrated `44154198694` y S3 distinct IA `44154198956` estaban en progreso en esta observación; confirmar después si se necesitan. El test histórico S3 `nativeSourcesShareCanonicalWorkspaceRealityAndGeneration` ya estaba registrado antes como `s3_related_regression.run_id=44004832751` no resuelto: no asignar falsamente corrección a W01.

## B4 — Mitigación real y límites
- El provider de W01 sirve sólo como URI Android privada sin CRUD/saldos; eventos del ContentObserver vacíos y no conceden permiso. No hay enlace de `publishAfterVerifiedCommitForDevOnly` desde ningún owner de Reality9; no hay garantía de aviso tras COMMIT ni distinción issue/revoke vs no-op.
- Un raw SQLite writer desde otro PID puede revocar sin mandar notificación; el reconciliador foreground de B3B1B1B1B1B1A sigue revalidando SQL/ACL doble en ON_RESUME. Un callback no es un lease de render ni prueba de SQLite. Dos procesos Android, registro en Activity real, BiometricPrompt G15 y limpieza de saldo en UI productiva **NOT_RUN**.
- `w01FinanceDev` paquete aislado, V2 core productivo OFF, W2 STOP. Esta unidad de infraestructura es preparatoria y no mueve dinero ni ejecuta Apply.

## B5 — Riesgos residuales, decisión adversarial
- Un COMMIT remoto después del último SELECT y antes de render permanece posible; no afirmar revocación inmediata. Provider export=false no elimina la necesidad de probar IPC Android en una campaña posterior.
- M1B falla ambiental 429; no remapear a PASS. S3 integrated histórico no es parte del diff y no se arregla en esta unidad.
- El permiso anterior se consume a BLOCKED ahora. La siguiente unidad distinta (posterior al B7 actual) requiere B1–B5 nueva, B5 independiente y READY exacto para **publicación post-COMMIT cooperante** desde RealityStore y discriminación de no-op; no aprovechar el permiso gastado.
- Mantener no APIs pagas, no V2 productiva, W2 STOP, sin modificar el teléfono.

## B6
**IMPLEMENTED** `6eece332b81436af3dbff955b2800557440045ff`, writer RELEASED epoch1131 antes de CI, 8/8 JVM y compilación de dos variantes con manifiestos correctos. V2 core regresión dirigida SUCCESS.

## B7
**PASS_SCOPED_DEV_PRIVATE_INVALIDATION_ISOLATED_W01_VARIANT_8_JVM_V2_CORE_RESTORED**. Evidencia actual basta para esta unidad, **no** cierre global W1 o IPC Android físico. Registrar checkpoint, consumir permiso a BLOCKED, writer RELEASED al terminar documentación.