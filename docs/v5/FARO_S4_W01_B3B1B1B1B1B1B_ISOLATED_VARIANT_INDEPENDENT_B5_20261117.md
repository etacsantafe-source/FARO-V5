# FARO V5 — W01 B3B1B1B1B1B1B RED_FIX: B5 independiente para build type dedicado
Fecha Argentina 2026-11-17. Solo DOC. Unidad SAME `S4_W01_B3B1B1B1B1B1B_ANDROID_PRIVATE_INVALIDATION_DEV`.
Base B1–B5 de corrección `docs/v5/FARO_S4_W01_B3B1B1B1B1B1B_ISOLATED_VARIANT_RED_FIX_B1_B5_20261117.md`, blob `c38c0ea179ce720c77287103205b14d94ab2f740`, commit `c8207f7b799d5f344b3bd73d88c514ee70b84b23`, guard `44153192083` SUCCESS. Previo B7 RED_FIX blob `e51cba14d8e241f042e1e3ed66f4bc9f78b273a0` del mismo código `db714a0e25426c6f6b66936cef632685d4f62291`.

## B1 — Hecho adversarial real
V2 CORE `44151067496` falló `V2_CORE_M2B_UNEXPECTED_PROVIDERS` porque nuestro cambio añadió provider a un paquete que por contrato tenía CERO; no editar esa guardia. El workflow dirigido W01 `44151067699` 8/8 JVM SUCCESS fue insuficiente, por eso esta revisión exige **ambos** verificadores. S3 integrated `44151067469` error separado ya registrado `44004832751` en `nativeSourcesShareCanonicalWorkspaceRealityAndGeneration`.

## B2 — Riesgos de reutilización
La variante debe compilar realmente `FaroV5V2CoreDevActivity.kt` en `src/v2CoreDev/kotlin`, sin duplicar las mismas clases en `src/main` y otra sourceSet compartida dentro de una misma variante. Build type nuevo no altera `v2CoreDev`, `x03AiDev`, `s3IntegratedDev` ni packageId shipping. Mantener el contenido histórico de `v2CoreDev/AndroidManifest.xml` exactamente igual al blob `97a23a12983f1a6e9ac973a4b4948142d477c439`. V4.4 AtomicApply y Realm9 SQL invariantes no tocados.

## B3 — Solución versus alternativas
`buildTypes.create("w01FinanceDev") { initWith(buildTypes.getByName("v2CoreDev")); applicationIdSuffix=".w01financedev"; matchingFallbacks += listOf("v2CoreDev","debug") }` más sourceSet Kotlin DEV existente es supported por Android Gradle Plugin: https://developer.android.com/build/build-variants y https://developer.android.com/reference/tools/gradle-api/8.12/com/android/build/api/dsl/BuildType .
Un manifest específico de build type tiene prioridad y permite provider exclusivamente W01: https://developer.android.com/build/manage-manifests . El provider no exportado es válido y ofrece URI app-private: https://developer.android.com/guide/topics/manifest/provider-element . Rechazar añadir excepción a V2 CORE, poner provider en main, reutilizar S3 integrado o confiar en `ContentResolver.notifyChange` sin COMMIT (https://developer.android.com/reference/android/content/ContentResolver).

## B4 — Alcance, pruebas y límites
**EXACT4:**
1. `v5/app/build.gradle.kts` — nuevo buildType `w01FinanceDev`, appIdSuffix propio, sourceSets compartido, sin main.
2. `v5/app/src/v2CoreDev/AndroidManifest.xml` — restaurar cero provider (revertir 7 líneas).
3. `v5/app/src/w01FinanceDev/AndroidManifest.xml` — manifest endurecido desde V2 DEV más único provider privado y autoridad `${applicationId}.finance9read`.
4. `.github/workflows/faro-v5-s4-w01-b3b1b1b1b1b1b-private-invalidation.yml` — HEAD^ preflight EXACT4, JVM8, dos compiles/manifest paths; verificadores con 0 vs 1 proveedores. Ejecutar workflow V2 CORE histórico sin modificarlo.
NUNCA tocar `RealityStore`, `RealityFinance9Acl`, Kotlin provider/observer/test ya certificado 8/8, SQL, datos, G15, S3 ni W2.

## B5 — Pruebas negativas y decisión adversarial
1. **Z01** paquete `v2CoreDev` fuente/herramienta final (packaged, merged) tiene 0 providers y su guard original pasa; si no, RED_FIX.
2. **Z02** paquete `w01FinanceDev` final es `ar.com.faro.alpha.w01financedev`, sin Internet, distinto UID y appId de V2 y S3, y contiene sólo provider `S4W01FinanceReadPrivateInvalidationProvider`, `exported=false`, `grantUriPermissions=false`, autoridad con sufijo correcto.
3. **Z03** no Android sourceSet duplicada causa conflicto o Activity inexistente; probar compile de ambas variantes, no contar sólo parseo de manifest.
4. **Z04** `FaroV5CompositionRoot` y main manifest NO modificados ni montan Finanzas; no activar pagos ni V2 productiva.
5. **Z05** ningún publisher SQL real o revocación no-op se anuncia; este RED_FIX restaura aislamiento únicamente.
6. **Z06** no editar workflow V2 histórico para hacerlo verde; si el nuevo paquete rompe otra suite, no declarar PASS.
7. **Z07** pérdida de notificación Android, cambio de política desde Python/raw sqlite3 y carrera post-SELECT siguen fuera de garantía; conservar reconciliación al RESUME.
8. **Z08** verificar diff EXACT4, SHA CI y XML 8/8; dos PID Android y G15 NOT_RUN, W2 STOP.
**DICTAMEN B5**: GO scoped SAME-UNIT RED_FIX exact4, dependiente de guard y nuevo READY individual DOC-only con reviewed_head correcto y `last_code_review.result=RED_FIX` para código anterior. Si fallan compilación/manifest/V2 core, no se cierra B7.

## B6
PENDING tras READY exact4; no código en este commit.
## B7
PENDING del fix. El B7 RED_FIX previo sigue abierto hasta que se certifique variante aislada.