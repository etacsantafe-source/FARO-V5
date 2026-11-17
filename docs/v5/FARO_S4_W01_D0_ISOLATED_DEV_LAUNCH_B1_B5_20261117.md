# FARO V5 — S4 W01 D0: launcher DEV aislado, preauditoría B1–B5
Fecha: 2026-11-17 (Argentina). Unidad candidata: `S4_W01_D0_ISOLATED_DEV_LAUNCH_PACKAGE_ALLOWLIST`.
Estado: **PREAUDITADO DOC_ONLY; NO READY, NO B6 NI B7.** Checkpoint base `14c63d8b54c1c6ea374dd5381c8ed254f77084a8`.

## B1 — Evidencia / reproducción estática
- `v5/app/build.gradle.kts` blob `06efa77d0b8bb09911b0a74fb200e8d883fe1464`: buildType `w01FinanceDev`, `applicationIdSuffix=".w01financedev"`, applicationId final `ar.com.faro.alpha.w01financedev`, código Kotlin `src/v2CoreDev/kotlin`.
- `v5/app/src/w01FinanceDev/AndroidManifest.xml` blob `ef4b2e16d82cd9535fb4c1645fa9599341f739f1`: launcher `FaroV5V2CoreDevActivity`, provider privado W01 exclusivamente, no Internet; v2CoreDev tiene cero providers certificado en B7 anterior.
- `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevActivity.kt` blob `25ff91deb1d79013f7709c869eb7cd66d234fc0f`: `FaroV5V2CoreDevPackagePolicy.accepts` contiene sólo `ar.com.faro.alpha.v2coredev`, `ar.com.faro.g15isolated.v2coredev` y `ar.com.faro.alpha.s3integrateddev`; el `onCreate` ejecuta `check(accepts(packageName))`. Por lectura lógica, un launcher W01 con el ID configurado falla ANTES de mostrar la pantalla. **No afirmar crash físico observado ni APK lanzada**.
- El B7 anterior de `B3B1B1B1B1B1C` prueba 16 casos JVM y compilación Android DEV, pero NO ejecuta un launcher en dispositivo: `docs/v5/FARO_S4_W01_B3B1B1B1B1B1C_POST_COMMIT_NOTICE_B7_PASS_SCOPED_20261117.md`. NO repetir.
- Criterio binario: el ID EXACTO W01 de desarrollo debe pasar `accepts`; cualquier ID productivo, desconocido o prefijo parecido debe permanecer rechazado; manifest/paquete y compilación no deben cambiar.

## B2 — Comparación / reutilización
- V4.4 no posee este launcher aislado STAGED9; las invariantes AtomicApply/receipts/Undo y el control V2 productiva OFF permanecen intactos. No incorporar componentes V4.4 ni tocar main.
- Reutilizar la allowlist explícita ya existente. Las otras tres identidades DEV siguen certificadas y no se modifican. Conservar el `check(debuggable)` y `check(!V2_ACTIVE_ENABLED)` en `onCreate`.
- No introducir una Activity nueva, provider nuevo, permiso de red, egress, cambios Gradle, acceso a saldos, sesión humana ni otro store.

## B3 — Investigación externa real y decisión
- Android Gradle Plugin, variantes de compilación y `applicationIdSuffix`: https://developer.android.com/build/build-variants — **USAR como fuente primaria**: cada variante adquiere un applicationId concreto; no debe inferirse identidad con `startsWith`.
- Android, configuración de applicationId y manifest: https://developer.android.com/build/configure-app-module — **REFERENCIA**, validar ID final efectivo al compilar.
- Android, `<activity>` y Activity launcher: https://developer.android.com/guide/topics/manifest/activity-element — **REFERENCIA**, el launcher declarado no garantiza éxito de `onCreate`.
- Alternativa A: añadir sólo el ID W01 **exacto** a la allowlist existente y testear positivos/negativos — **ADAPTAR/ESCOGIDA**, mínimo diff y continuidad DEV.
- Alternativa B: crear launcher Activity nuevo específico con política paralela — **DESCARTAR** para D0, duplica lifecycle/owners y amplía superficie.
- Alternativa C: aceptar paquetes por sufijo/prefijo o quitar `check` — **DESCARTAR** por expansión no auditada del conjunto permitido.
- Riesgo conocido: compilación no equivale a ejecución; exigir pruebas negativas de política y prueba real Android posterior separada. No confundir documentación externa con evidencia de ejecución local.

## B4 — Unidad mínima exacta, sin mezclar dos PID
Cambios funcionales propuestos, sólo DESPUÉS de READY de esta unidad:
1. `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevActivity.kt`: constante `W01_FINANCE_DEV`, añadir igualdad exacta a `accepts`, sin tocar otros gates ni el lifecycle.
2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01DevLaunchPackagePolicyTest.kt` (nuevo): casos JUnit explícitos de las cuatro identidades DEV, denegación del productivo `ar.com.faro.alpha`, variantes parecidas, vacío y mayúsculas, sin Android UI simulada.
3. `.github/workflows/faro-v5-s4-w01-d0-isolated-launch-allowlist.yml` (nuevo): preflight del SHA padre exact3, ejecutar JUnit, compilar `W01FinanceDev` y `V2CoreDev`, inspeccionar manifest de W01 y cero providers V2 Core; no equiparar CI con prueba física.

Fuera de scope: conectar `S4W01FinanceReadForegroundReconciliation`, `ContentObserver`, `S4W01FinanceReadPostCommitPublisher`, dos PID, alterar Reality9, agregar finanzas a UI o hacer cualquier grant. Esos son D1 posterior con preauditoría y permiso propios. W1 abierto; W2 STOP.

## B5 — Adversarial previo a READY
- R01 la igualdad exacta W01 debe pasar; variantes inesperadas `.w01financedev.fake`, mayúsculas, vacío, productivo, x03 y s3/shipping deben fallar.
- R02 las tres identidades DEV existentes conservan su comportamiento; seguridad `debuggable` y V2 productiva OFF no se alteran.
- R03 `w01FinanceDev` mantiene URI provider privado (`exported=false`), sin Internet, y `v2CoreDev` cero providers; no modificar manifests.
- R04 no generar/leer grants, saldos, datos reales, claves, tokens ni tocar terminal, G15, main, S3 o W2.
- R05 la suite debe comprobar JVM, compilaciones y manifest **por SHA real**; no reclamar dos PID ni arranque real en G15.
- R06 si falla carga de Activity/JUnit, cambiar únicamente el test mediante revisión B5, no levantar el gate para pasar.
- R07 ausencia de READY implica NO código; permiso consumido de C sigue BLOCKED; independiente B5 y guard de docs requeridos antes de READY D0.

## B6/B7
**NOT_RUN/PENDING**. Próximo: revisión adversarial independiente de este documento y guardia en GitHub. Sólo después permiso READY individual exact3, writer CAS y código. Ningún componente productivo habilitado.
