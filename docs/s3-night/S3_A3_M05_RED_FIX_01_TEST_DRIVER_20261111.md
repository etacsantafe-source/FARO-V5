# FARO V5 · S3.A3 M05 · B7 RED_FIX 01 / test-only driver visibility · 2026-11-11

## B1 — Defecto observado en CI exacta
M05 está READY con tres rutas. Código inicial SHA `11c8906a9210a1c725a30ca6c256095b868468ad` run `43049922298`, job `130623889938` FAIL en `:app:compileDebugUnitTestKotlin`, referencia no resuelta `androidx.sqlite.driver.bundled.BundledSQLiteDriver` dentro de `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5BookmarkA3AcceptanceT2Test.kt:3`. No se llegó a ejecutar los tests de M05 ni a construir APK de esa CI. La guardia siete bloques `43049921519` SUCCESS en el SHA, no sustituye la CI dirigida roja.

`v5/app/build.gradle.kts` tiene `implementation(project(":store:auxiliary"))` y sustituye sqlite-bundled por sqlite-bundled-jvm en classpath runtime de tests, pero **NO** declara dependencia API o testImplementation directa de `androidx.sqlite:sqlite-bundled`. `v5/store/auxiliary/build.gradle.kts` usa `implementation(libs.androidx.sqlite.bundled)`; dependencia implementation no se expone en compile classpath de consumers. El fallo es **importación privada de dependencia** del nuevo test, no cambio en el código M01–M04.

## B2 — Reutilización de lo certificado
M01–M04 B7 PASS intactos; no tocar Gradle ni productivo ni reejecutar cierres anteriores salvo regresión motivada. El test M05 necesita ejecutar SQL de fixture para simular archive/deletion y fallos de receipt; se mantiene el propósito adversarial A3-07 y A3-10. El módulo Auxiliary ya aporta driver en runtime de test y substitución JVM explícita. Este RED_FIX mantiene rutas actuales y no agrega dependencias de compilación de producción.

## B3 — Alternativas y selección
A) modificar `v5/app/build.gradle.kts` con `testImplementation(libs.androidx.sqlite.bundled)`: técnicamente limpio, pero fuera de las tres rutas M05 autorizadas; requiere delta B1–B5 y nuevo permiso, por lo tanto NO hacerlo. B) eliminar pruebas SQL: reducción de cobertura adversarial, descartado. C) añadir dependencia solo mediante init script de CI: haría pruebas locales distintas, descartado. D) **elegido**: fixture test-only ejecuta SQL con reflexión sobre interfaces públicas `androidx.sqlite.SQLiteDriver`, `SQLiteConnection`, `SQLiteStatement` y la implementación `BundledSQLiteDriver` que ya es dependencia runtime de Auxiliary; no importa tipos no públicos en compile classpath de App. La presencia real del driver JNI será una aserción explícita de CI, nunca se afirmará antes de ejecutar.

La semántica sigue la API de SQLite bundled usada por Auxiliary; no se incorpora biblioteca, API paga ni servicio.

## B4 — Fix acotado y reversible
En `FaroV5BookmarkA3AcceptanceT2Test.kt` suprimir los dos imports que fallan; añadir helper test-only `execFixtureSql(file, sql)` que abre la implementación por `Class.forName("androidx.sqlite.driver.bundled.BundledSQLiteDriver")`, invoca métodos de interfaces públicas por reflexión, usa `prepare(sql).step()` y cierra statement+connection en finally. Reemplazar únicamente las llamadas de SQL de fixture (`sqlWorkspace`, CREATE/DROP TRIGGER). No tocar tests de lógica, grants, ids, scope, cobertura, workflow o APK salvo nuevos fallos verificados. Excepciones son fallos (no catch silencioso). Si `ClassNotFoundException` aparece en runtime, STOP con B1–B5 delta para habilitar dependencia test-only de forma declarativa; no ocultar ni omitir tests. El writer único CAS se libera al publicar commit, antes de esperar CI.

## B5 — Negativos y pruebas
Preflight exactamente ruta de test con permiso M05 READY, commit con `FARO-UNIT:S3_A3_BOOKMARKS_AUTOMATED_ACCEPTANCE`; debe comprobar compilación de app JVM tests, corridas efectivas 9 App + 2 Surface (conteos esperados de workflow), sin skipped/errors/failures; APK v2CoreDev aislada manifest/permisos y hash, guard SUCCESS sobre SHA. Verificar regresiones de fault injection y source revocation. Si runtime falla, no declarar B7; un segundo RED_FIX debe preceder al siguiente código. Ningún cambio de schema, owner, app gradle, permisos Android, V2 productiva o teléfono físico.

**Estado de este documento:** RED_FIX pre-código, evidencia CI roja y scope exacto, implementación futura NO ejecutada en esta publicación.
