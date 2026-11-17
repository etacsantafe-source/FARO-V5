# FARO V5 W01 — Same-unit RED_FIX B1–B5, dedicated Android Finance DEV variant
Fecha Argentina 2026-11-17. Unidad SAME: `S4_W01_B3B1B1B1B1B1B_ANDROID_PRIVATE_INVALIDATION_DEV`.
MODO DOC_ONLY. Base `f06999feaaffeb9d49462d19e5c784756bcc6d86`, permit BLOCKED y last_code_review=RED_FIX sobre `db714a0e25426c6f6b66936cef632685d4f62291`; B7 RED_FIX `docs/v5/FARO_S4_W01_B3B1B1B1B1B1B_PRIVATE_INVALIDATION_B7_RED_FIX_20261117.md` blob `e51cba14d8e241f042e1e3ed66f4bc9f78b273a0`. Requiere independiente B5, guard y READY exact4 antes de B6.

## B1 — Evidencia de regresión, alcance, criterio binario
- La suite W01 dirigida `44151067699` PASS 8/8 JVM, Android V2CoreDev compile + proveedor privado en merged manifest. La regresión existente V2 CORE `44151067496`, job `134279224447`, muestra explícitamente `V2_CORE_M2B_UNEXPECTED_PROVIDERS ['ar.com.faro.v5.app.S4W01FinanceReadPrivateInvalidationProvider']`. Históricamente `v5/app/src/v2CoreDev/AndroidManifest.xml` blob `97a23a12983f1a6e9ac973a4b4948142d477c439` usa `<provider tools:node="removeAll" />` y verifica **cero** providers; el delta lo rompió. NO debilitar ni editar su workflow de seguridad.
- Código Kotlin Provider + Observer + 8 tests fue añadido bajo `v5/app/src/main/kotlin` pero **no** se monta shipping. Sólo se registró en el manifiesto V2 Core, blob actual `be9e2891067bb73056bcaf66c103e1cb67e04da3`. V2 CORE debe restaurar exactamente su manifiesto anterior (eliminar las siete líneas nuevas). `v5/app/build.gradle.kts` blob `52d93e83c52d7a3a43a3ea7d917cb146e56a0ce6` ofrece variantes por build type; `src/v2CoreDev/kotlin` contiene Activity/Authority/host DEV real, reutilizable.
- S3 integrated `44151067469` falla en `nativeSourcesShareCanonicalWorkspaceRealityAndGeneration`, error registrado ya en `s3_related_regression.run_id=44004832751` como pendiente separado. No trasladar arreglo de S3 ni afirmar que este RED_FIX lo resuelve. W2 STOP, V2 productiva OFF.

## B2 — Comparación V4.4/V5, reutilización
Preservar núcleo V4.4 AtomicApply/receipts/Undo, no introducir otra autoridad de datos ni provider en producto. Mantener infraestructura actual (JVM8 PASS) sin reescribir Kotlin ni reabrir sus tests. Reutilizar `FaroV5V2CoreDevActivity` de `src/v2CoreDev/kotlin`, con paquete DEV separado por UID/applicationId, para la variante W01. Ningún cambio a `RealityStore`, Finance9Acl, schemas, main, S3, S2, Apply o Gradle de otros módulos.

## B3 — Alternativas externas y decisión
1. **ADAPTAR:** Android Gradle Plugin admite build types y sourceSets específicos; crear `w01FinanceDev` en `app/build.gradle.kts`, `initWith(v2CoreDev)`, appIdSuffix propio `.w01financedev`, fallback a v2CoreDev/debug y `sourceSets.getByName("w01FinanceDev").kotlin.srcDir("src/v2CoreDev/kotlin")`. Fuente: https://developer.android.com/build/build-variants .
2. **ADAPTAR:** manifest de mayor prioridad por build variant con provider propio `exported=false` sin tocar `main` ni `v2CoreDev`: https://developer.android.com/build/manage-manifests y https://developer.android.com/guide/topics/manifest/provider-element . El `removeAll` de V2 Core se mantiene; en variante aislada se conserva removiendo proveedores de libraries y se declara un único propio.
3. **DESCARTAR:** agregar excepción a guard V2 CORE de cero providers: debilitaría M2 aislamiento por conveniencia. También descartar provider en main/S3/debug o URI ficticia sin provider. Android O requiere URI respaldada por ContentProvider (https://developer.android.com/reference/android/content/ContentResolver).
4. **REFERENCIA:** SQLite WAL https://www.sqlite.org/isolation.html impide asumir señal=COMMIT; incluso nueva variante no certifica comunicación real con SQL ni dos PID sin pruebas de instrumentación posteriores.
Decisión: build type **W01 DEV aislado**. No reutilizar `x03AiDev`/S3 ni pagar API.

## B4 — Plan delta exacto, testing
EXACT4 rutas funcionales autorizables tras B5 independiente:
1. MODIFICAR `v5/app/build.gradle.kts`: nuevo build type `w01FinanceDev`, distinto appId `ar.com.faro.alpha.w01financedev`, Kotlin sourceSet DEV core compartido; no activar V2 productiva.
2. RESTAURAR `v5/app/src/v2CoreDev/AndroidManifest.xml` a blob `97a23a12983f1a6e9ac973a4b4948142d477c439`, cero providers de nuevo.
3. CREAR `v5/app/src/w01FinanceDev/AndroidManifest.xml`: mismo hardening offline y DEV application/activity, provider único Finance `${applicationId}.finance9read`, `exported=false`, ningún permiso Internet/export.
4. MODIFICAR `.github/workflows/faro-v5-s4-w01-b3b1b1b1b1b1b-private-invalidation.yml`: preflight HEAD^ EXACT4, JVM8 XML exacto, compile ambos `V2CoreDev` y `W01FinanceDev`, verificar merged **y packaged** manifests con V2CoreDev cero proveedores y W01 exactamente uno privado (autoridad appId de variante), sin Internet, sin shipping mount. Mantener guard existente independiente.
B6 sólo luego de READY/document guard y writer CAS; CI rojo => nueva B7 RED_FIX de la misma unidad, sin salto a W2.

## B5 — Adversarial/fail-closed
- R01: V2CoreDev packaged manifest de cero providers, actividades exclusivas existentes, y `V2_CORE_M1_ISOLATION_PASS` tras cambio. Si falla, STOP; no tocar guard.
- R02: W01FinanceDev provider único `exported=false`, `grantUriPermissions=false`, autoridad exacta y appId `ar.com.faro.alpha.w01financedev`, sin Internet.
- R03: nueva variante no hereda otro launcher/certification provider de main/Android libraries; debe revisar las rutas finales merged y packaged y no asumir merge por XML fuente.
- R04: Activity/Application clases DEV se compilan realmente por sourceSet; no instalar ni probar físicamente G15 durante CI.
- R05: `S4W01FinanceReadPrivateInvalidation` sigue DORMANT; no publicar notificaciones sin COMMIT ni confundir callback con autorización/SQL.
- R06: ningún cambio a S3 UI/app product o SQL ledger; suite S3 integrada con fallo anterior no se declara PASS ni se arregla por inercia.
- R07: nombre buildType no colisiona con x03AiDev, s3IntegratedDev, v2CoreDev; applicationId y UID nuevos.
- R08: ningún arreglo de estatus por texto: XML 8/8 exactos, Gradle de ambas variantes y checker M2 efectivamente PASS; si alguno falla, RED_FIX.
**Dictamen B5 del plan:** GO a auditoría independiente exact4. Sin ella no READY.

## B6
NOT_RUN; código inicial sigue RED_FIX.
## B7
PENDING nueva implementación; no reescribir B7 histórico ni afirmar W1 terminado.