# FARO V5 · W05-A1B2A · B7 PASS · FBS2v8 autenticado Reality11

2026-11-24 (Argentina). Unidad `S4_W05_A1B2A_AUTH_FBS2_V8_RECEIPT11_DEV`. Rama `work/s3-night-20261102`.

## B1–B5 — Autoridad de trabajo

Preauditoría `docs/v5/FARO_S4_W05_A1B2A_AUTH_FBS2_V8_RECEIPT11_B1_B5_20261124.md`, blob `6e2c49c2df37fcbabe8fc85d6f15e717cdb4f4a4`, commit `6f51ad5d4b42f02827534eca9e5bb871cfd285c0`, guardia `45427544745` SUCCESS.
Checkpoint posterior `68458c8c290c9ea6806ea7c198323d83ccc3c135`, guardia `45427786640` SUCCESS. Permiso READY exact4 `49d61df8d561cff1e608b94a6d0e82a219ff9974`, guardia `45428432023` SUCCESS. A0/A1A/A1B0/A1B1 conservan sus B7 previos, sin repetir.

## B6 — Implementación, RED_FIX y CI real

Código inicial `35f1b75b46766b8bfd93164131146c71b3ab08b4`, cuatro rutas exactas:
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11Fbs2Codec.kt`
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStorePortableBackupCrypto.kt`
3. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11Fbs2CodecTest.kt`
4. `.github/workflows/faro-v5-s4-w05-a1b2a-fbs2-v8-receipt11.yml`

Guardia del primer código `45428886798` SUCCESS; CI inicial `45428886998` FAILURE. Gradle compiló, pero XML real 10/10 fallos de fixture en `snapshot()` antes de ejercer el codec: faltaba migrar Auxiliary3→4 para la captura Finance9Auxiliary4. Se registró de forma independiente como **RED_FIX**, NO PASS, en `docs/v5/FARO_S4_W05_A1B2A_RED_FIX_CI_45428886998_20261124.md` (blob `63880c6b8dea4b3fecd8e75b0862da73db6a1fd4`), permiso de reparación `ec1c61e6c353fc67743250b569753661ce13d95b`, guardia `45429905994` SUCCESS. No hubo cambio del codec por este fallo.

Código de reparación y **SHA de aceptación final** `c3019e043af9c12b16c21f02c02d931828f1e20e`: exclusivamente test fixture y workflow A1B2A. Fixture reutiliza el flujo certificado `openQuarantinedAuxiliaryReceiptV4Stage` con Notice/receipt local; workflow admite preflight exact2 RED_FIX y exact4 original (sin relajar el gate global). Guard `45430216723` COMPLETED SUCCESS.

CI dirigida final `45430216765`, job `138573129660`, **COMPLETED SUCCESS** para SHA final `c3019e043af9c12b16c21f02c02d931828f1e20e`. Preflight exact2 ambas rutas: PASS. Gradle `BUILD SUCCESSFUL in 2m 42s`.
JUnit XML original `TEST-ar.com.faro.v5.generation.FaroV5Household11Fbs2CodecTest.xml` descargado de artefacto `14938502533`, nombre `w05-a1b2a-fbs2v8-receipt11-xml`, tamaño ZIP informado 847 bytes, digest GitHub `sha256:595cc50524a23c1d5d4bfbdaa41d60fb616409459fc81af3d1096a562134f38d`.
XML inspeccionado independientemente: **tests=10, failures=0, errors=0, skipped=0**, 10 `testcase` exactos. Log oficial marca `FARO_W05_A1B2A_AUTH_FBS2V8_RECEIPT11_XML_PASS_10_10`.

## B7 — Auditoría adversarial y alcance

**PASS_SCOPED_W05_A1B2A_AUTH_FBS2V8_RECEIPT11_JVM_XML_10_10**.

Certificado: nuevo contenedor puro FBS2 versión 8 con base Household10 obligatoria anidada (FBS2v6 Evidence1 o v7 Evidence2), sección H11R Reality11 tipada obligatoria, longitudes y digest por sección, límites de 16MiB total/4MiB sección, roundtrip determinista, recibos parciales preservados sin fabricar stock; lectores antiguos y nuevo lector rechazan cruzar versiones; tamper/headers/longitudes/truncamiento/duplicados fallan cerrados; dos endpoints internos nuevos `sealWithHouseholdReceipts11` y `openWithHouseholdReceipts11` reusan FAROBAK2 con AES-GCM y KDF existente, negativos de passphrase/tag/encabezado autenticados.

**No certificado y explícitamente OFF:** captura/restore SQL11, quiescencia real de los cinco stores, identidad de archivo independiente y generation fence, reapertura/cutover tras caída, Apply/compras/stock reales, pagos, Undo, Android físico/G15, V2 productiva, Auto/Noche, cualquier egress. El codec por sí solo no habilita stock real ni garantiza restauración. Próximas unidades separadas: **A1B2B** captura/restauración SQL11 y luego **A1B3** cutover/reopen, seguidas de aceptación de Apply A1C.

**Incidencia CI histórica no heredada:** workflow W04-B0C2 `45428886618` se disparó sobre la primera modificación del crypto compartido y falló su preflight porque la unidad autorizada era A1B2A, no W04; no ejecutó sus pruebas antiguas. Se conserva esa observación, por lo que NO se declara «todas las Actions verdes». La guardia específica obligatoria y la CI JUnit/XML de A1B2A sí están SUCCESS. V2 core dirigida `45428886713` del primer commit terminó SUCCESS, sin inferir pruebas físicas.

Consumir permiso A1B2A a BLOCKED y dejar writer RELEASED. El siguiente trabajo debe tener B1–B5/guard/READY propios; no heredar este permiso.
