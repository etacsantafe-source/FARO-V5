# FARO V5 — F2C1B1B1A · B7 RED_FIX1 — JVM build-logic memory and test source audit

Fecha: 2026-11-23. Unidad `S4_W04_F2C1B1B1A_OWNER_READ_SCOPE_DEV`. Commit funcional observado `2b6fc0d347d6c2fba22de5c4788430af1a96b16b`.
**B7 RED_FIX; NO PASS de lógica, NO XML de las seis pruebas.** No repetir B0 ni F2C0.

## B7 — Evidencia roja verificable
- Guard protocolo GitHub Actions `45187167965`: SUCCESS sobre el SHA de código. READY/preflight exact5 en CI dirigido PASS.
- CI dirigida `45187168132`: **COMPLETED FAILURE**; job `137735671966`, paso `Household10 selected DEV owner scope JUnit5` falló **antes de compilar fuentes de la unidad**. Log: `Execution failed for task ':build-logic:compileKotlin'` / `Not enough memory to run compilation`; Gradle reportó `max heap space 512 MiB` y `max metaspace 384 MiB`. `Evidence2` y XML no ejecutados, artifacts no encontrados. No clasificar tests como FAIL ni PASS.
- Auditoría estática adicional sobre los cuatro archivos recién incorporados: los tests usan `val nonce = assertNotNull(...)` importado de `org.junit.jupiter.api.Assertions`, método que retorna `void`. Causaría compilación incorrecta si se alcanzara Kotlin tests. Corregir solo a `checkNotNull` de Kotlin (resultado con tipo). Referencia de API: https://docs.junit.org/5.11.0/api/org.junit.jupiter.api/org/junit/jupiter/api/Assertions.html
- Auditoría adversarial B5 de fuente Evidence: `Grant` retiene `allRecords` y `selectedRecords`, una cache innecesaria de observaciones comerciales dentro de una capacidad efímera. El contrato exige *no caching comercial*; conservar únicamente watermark SHA-256 de fuente canonical, need/product y nonce; revalidar con snapshot fresco en cada consulta. El watermark es emitido por `EvidencePortabilityProjection.commercialStagedTyped` bajo la fuente owner, no desde renderer/modelo. Fuentes de test inyectables NO prueban autenticidad Android.

## RED_FIX acotado con 4 rutas del READY
1. `.github/workflows/faro-v5-s4-w04-f2c1b1b1a-owner-read-scope.yml`: aplicar `-Dorg.gradle.jvmargs=-Xmx3072m -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8` a los dos Gradle runs. No modificar Gradle global ni otros workflows.
2. `v5/owner/household/src/test/kotlin/ar/com/faro/v5/owner/household/spi/Household10DevReadScopeOwnerTest.kt`: cambiar asignaciones `assertNotNull` a `checkNotNull` manteniendo las tres pruebas.
3. `v5/evidence/src/test/kotlin/ar/com/faro/v5/evidence/CommercialEvidence2DevReadScopeOwnerTest.kt`: lo mismo en sus tres pruebas.
4. `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence2DevReadScopeOwner.kt`: eliminar registros del `Grant`, comparar watermark fresco del owner y exigir presencia tipada seleccionada, se rechaza revocación/cambio. No tocar otras rutas.
- B1–B5 subunidad original con blob `a0c1faf84008d4923092b3a730c435845f359d94` subsisten; este delta repara memoria y contrato de no-caché. Preflight exactas antes de commit.
- Aceptación B7: nueva CI con 6/6 JUnit5 XML, no failures/errors/skips, guard SUCCESS, ninguna otra regresión roja. Futuro F2C1B1B1B Android real continúa NO autorizado. V2 productiva OFF, no pagos/Apply/stock/red/Auto/Noche/G15.
