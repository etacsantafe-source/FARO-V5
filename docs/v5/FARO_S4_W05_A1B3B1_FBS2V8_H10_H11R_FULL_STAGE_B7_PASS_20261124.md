# FARO V5 · W05-A1B3B1 · B7 PASS SCOPED · FBS2v8 H10 + H11R SQL11 STAGED

Fecha 2026-11-24 (Argentina). Unidad `S4_W05_A1B3B1_FBS2V8_H10_H11R_STAGED_REPLAY_DEV`. Rama `work/s3-night-20261102`.

## B1–B5 — Preauditoría y permiso

Preauditoría `docs/v5/FARO_S4_W05_A1B3B1_FBS2V8_H10_H11R_FULL_STAGE_B1_B5_20261124.md`, blob `1b2f8fd5d299b81665b5c8744ab76ec112728493`, commit `d7fae63575485b56970728d27831ea1e5cc3a6c6`, guard `45443788587` COMPLETED SUCCESS.
Permiso READY exact 4 rutas `06426d138845b89565cea1be8293f755b28cf287`, guard `45443988085` COMPLETED SUCCESS.
Último checkpoint anterior `67e0222c1a28d6fbea625e2e34805ea1fd1ace9f` (A1B3B0 B7 PASS 8/8 Evidence1 base+Finance9), writer RELEASED 1850. B0, A1B3A, A1B2A/B, W1/W2/W3/W4 ya certificados, NO repetir.

## B6 — Código, RED_FIX, compilación, pruebas y XML original

Código original `0c4e5e93ebb2f130eee2c43e62afde19e3927f44`, exactamente cuatro rutas:
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold11NeedsRowsPort.kt`
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8FullStageReplay.kt`
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8FullStageReplayTest.kt`
- `.github/workflows/faro-v5-s4-w05-a1b3b1-h10-h11r-stage.yml`

Guard original `45444505243` SUCCESS, pero CI original `45444505515` job `138624435441` **FAILURE**: `UNRESOLVED_IMPORT androidx` en generation production. No JUnit ni XML del primer intento, NO se consideró B7. Registrado `docs/v5/FARO_S4_W05_A1B3B1_RED_FIX_GENERATION_SQLITE_45444505515_20261124.md`, blob `8070b4d413d85a0b5b5ffd5ccc9910f02283a163`, contiene además revisión de baseline de hashes antes/tras replay B0. Permiso `RED_FIX` conservó unidad READY, commit `5761116b9ee7d07555445fbd5287b68f80af9a6a`, guard `45445510532` COMPLETED SUCCESS.

Commit final de código **`c706dc8d27c34760fa68d054a98e4a71304b4605`**, exactamente tres rutas RED_FIX: lectura inmutable `PRAGMA user_version` en store:reality, eliminar imports AndroidX de generación y delegar a Reality, mover baseline de los cuatro stores no-Reality a **después** del B0 restaurado, permitir preflight RED_FIX exact3 en workflow. Sin cambio a test ni dependencias Gradle.

Guard final de código `45445804261` **COMPLETED SUCCESS**. CI dirigida `45445804222`, job `138629118513`, SHA del código final exacto, **COMPLETED SUCCESS**. Preflight READY/RED_FIX parent exact3 PASS para las tres rutas, Gradle `BUILD SUCCESSFUL in 3m 11s`; workflow marcador `FARO_W05_A1B3B1_FBS2V8_H10_H11R_STAGED_XML_PASS_10_10`.
Artefacto original de GitHub `14945658509`, `w05-a1b3b1-fbs2v8-h10-h11r-stage-xml`, digest SHA256 `51aa4eaacbc6e895c748d07b7da4d128b49aec1e50196280bc29a7c4070023bb`, tamaño ZIP 902 bytes. ZIP original descargado, hash recalculado idéntico y XML inspeccionado independientemente con `zipfile` y `xml.etree.ElementTree`:
`TEST-ar.com.faro.v5.generation.FaroV5Household11V8FullStageReplayTest.xml`, **tests=10,failures=0,errors=0,skipped=0**, tiempo 11.884 s, diez nombres reales y 0 nodos failure/error.

Regresiones mismo SHA: G4 Reality owners `45445804333` COMPLETED SUCCESS y S3 Reality receipt history `45445804697` COMPLETED SUCCESS. V2 core `45445804317` aún IN_PROGRESS al confirmar B7; **no se afirma PASS** hasta comprobar su estado.

## B7 — PASS acotado con frontera adversarial

**PASS_SCOPED_W05_A1B3B1_FBS2V8_EVIDENCE1_SQL9_TO_SQL11_STAGE_XML_10_10**.

En DEV JVM, un archivo FBS2v8 cifrado auténtico y pin durable **del mismo ciphertext original** (sin reencriptado, ligado a operationId + target) restaura: B0 cinco almacenes base + Finance9 en STAGED9, migración SQL9→10, Household10 (necesidades y revisión), migración SQL10→11 y H11R (dos recepciones parciales 7000 y 3000) con comparación exacta de estado/fila. El código nunca cambia el puntero activo ni adopta una generación.

- Recuperaciones idempotentes probadas tras cortes de migración 9→10, después de H10 commit, después de DDL 10→11 y después de H11R commit. Reentrada detecta schema9/10/11 y JAMÁS ejecuta restore B0 sobre target schema10/11.
- Revisiones positivas con listas vacías H10/H11 se conservan, sin inventar stock.
- FBS2v6 antiguo, password falsa, tag alterada y mismo payload con nuevo nonce fallan, sin sustitución durable; stage activado, hardlink, host revocado y H10 extranjero no pueden importar datos.
- `RealityHousehold11NeedsRowsPort` inspecciona estrictamente H10 dentro del SQL11 SIN cambio de PRAGMA ni downgrade. Lecturas immutable sobre stage sellado; `integrity_check` y FKs heredados del contrato comprobado. H11 SQL11 replica solo tabla receipts atómicamente, sin reescribir otras secciones. Hash de cuatro stores no-Reality antes/después de migración se conserva.
- Scoped Evidence1 porque la admisión inicial de B0 no ha sido certificada para Evidence2. No usar en Evidence2.

**NO certificado aún**: restauración de respaldos Evidence2, export/capture v8 completo desde generación **Reality11 activa** bajo host Android, firma y publicación de journal de cutover, reopen/recovery tras caída de la app, rollback/origen tras switch, ni cualquier operación Apply/Undo del stock. El resultado `StagedVerified` indica exclusivamente stage aislado, **no permiso de activación**. No hubo prueba física Moto G15. V2 productiva, pagos, Auto/Noche y stock real OFF.

Próxima unidad **W05-A1B3C**: escenario de cinco stores en Reality11, journaled cutover, selección de generación, reopen + startup recovery después de fallos con SQL11 (B1–B5 propios, permiso READY y CI real). El perfil Evidence2 se corrige/prueba en unidad propia previa a declararlo cubierto. Después **W05-A1C** stock Preview→Review→Apply humano autorizado; no antes.

Se consume READY a BLOCKED y writer se libera a RELEASED después del checkpoint y guardia final real. No repetir B7 anterior.
