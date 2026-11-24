# FARO V5 · W05-A1B3B0 · B7 PASS scoped · FBS2v8 cinco stores + Finance9 STAGED9

Fecha 2026-11-24 (Argentina). Unidad `S4_W05_A1B3B0_FBS2V8_FIVE_STORE_BASE_REPLAY_DEV`. Rama `work/s3-night-20261102`.

## B1–B5 — Evidencia anterior y permiso exacto

Preauditoría `docs/v5/FARO_S4_W05_A1B3B0_FBS2V8_FIVESTORE_BASE_REPLAY_B1_B5_20261124.md`, blob `67813806574748653c6512454db4b224314875f2`, commit `4e18ed06bff5d91d117d2271b7d893c92c472c4a`, guardia `45439620967` COMPLETED SUCCESS. Permiso READY de cuatro rutas en `a9788f4a44f4bb80a90ae87b03065b7fe38738c6`, guardia `45439827785` COMPLETED SUCCESS. Punto de entrada A1B3A B7 PASS 8/8, checkpoint `d33d2580bd0628ee8de5c6e65f2773b9c84110c5`, writer RELEASED 1838.

## B6 — Código original, RED_FIX, CI y XML auténtico

Commit inicial `5f9df8977bc0b61d91f1a67996c177b3fd3fc7d6`, cuatro rutas: `FaroV5FiveStoreStagedRestore.kt` (solo sobrecarga interna), nuevo `FaroV5Household11V8BaseReplay.kt`, test `FaroV5Household11V8BaseReplayTest.kt` y workflow `faro-v5-s4-w05-a1b3b0-fivestore-base-replay.yml`. Guardia inicial `45440247086` SUCCESS pero CI inicial `45440247314` FAILURE, `compileKotlin` NO_RETURN_IN_FUNCTION_WITH_BLOCK_BODY, línea209, **sin JUnit/XML**. Esta falla fue registrada explícitamente como **RED_FIX, nunca PASS**, en `docs/v5/FARO_S4_W05_A1B3B0_RED_FIX_COMPILER_45440247314_20261124.md`, blob `b5d912a1d829f7c3d677780323e5bccc0e9a640d`; permiso RED_FIX commit `6ee058247e4e746c2d0456c9c524af93025e5d7c`, guardia `45441228927` SUCCESS.

Commit final de código **`e99015370474b97b560ed4b2852825cfc39c6dd6`**: exactamente dos rutas de reparación, añadir `return` al `try/finally` que devuelve el resultado de restauración y preflight RED_FIX exact2 del workflow. El `finally` conserva el borrado de ciphertext. No se modificaron los codecs ni otros owners certificados. Guardia final código `45441529015`: COMPLETED SUCCESS.

CI dirigida final `45441529195`, job `138613666455`, exact SHA final: COMPLETED SUCCESS. Parent READY/RED_FIX preflight para dos rutas PASS, Gradle `BUILD SUCCESSFUL in 3m 42s`, verificador `FARO_W05_A1B3B0_FIVESTORE_BASE_REPLAY_XML_PASS_8_8`.
Artifact original GitHub Actions `14945340315`, nombre `w05-a1b3b0-fbs2v8-fivestore-base-replay-xml`, tamaño ZIP declarado 839 bytes, digest GitHub `sha256:62f80553c17a8dac37f6c43ebb8a7d0c9c15757215471c7c20c1a363810c93c1`. ZIP descargado y analizado con Python `zipfile` y `xml.etree.ElementTree`: `TEST-ar.com.faro.v5.generation.FaroV5Household11V8BaseReplayTest.xml`, **tests=8, failures=0, errors=0, skipped=0**, ocho testcases y ninguna failure/error (time 11.125s). Se verificó de forma independiente del marcador de workflow.

## B7 — Alcance realmente certificado y límites

**PASS_SCOPED_W05_A1B3B0_FBS2V8_BASE_REPLAY_EVIDENCE1_STAGED9_XML_8_8**.

Certificado en JVM DEV aislada con **Evidence1**:
- Un FBS2v8 autenticado se abre sin convertirlo en un nuevo FBS2v6; el ciphertext ORIGINAL v8 queda fijado por `FileRestoreArchiveIdentity` a operationId/target antes del primer write. Misma operación y mismos bytes reanudan sin duplicar; otro ciphertext, aunque el plaintext sea idéntico, rechaza.
- Cinco almacenes base Reality legado/G4/Planning, Commercial, Web, Auxiliary4 (incluidos recibos de aviso) y Workspace, más las ocho tablas Finance9, se restauran solamente en STAGED9; tras cierre hay verificación semántica exacta de los cinco y Finance9, normalizando exclusivamente manifest source/target.
- Tests de interrupción tras Auxiliary y tras commit Finance9 demuestran reintentos con archivo idéntico sin pérdida; password falsa, tag alterada y versiones anteriores se rechazan antes de pin. Source==target, hardlinks, sidecars, host cerrado revocado y pointer ABA son rechazados.
- No se ejecutó migración 9→10/11; los recibos H11R permanecen autenticados **en el archivo** pero NO aplicados a SQL11; no hay mentira de inventario ni activación del puntero.
- No hay Android físico, Apply ni producción.

**NO certificado**: Evidence2 typed (el preflight genérico inicial de B0 sigue verificando perfil Evidence1 antes de conocer el perfil archivado; exigir reparación/prueba separada), Household10 y H11R/SQL11 replay, captura/export de una generación Reality11 completa, journal de cutover, reopen tras caída, rollback de generación, red/egress, humano, stock real. No invocar B0 para Evidence2 hasta certificación propia. La historia de W04/S3 no se declara toda verde: se dispararon workflows de rutas compartidas con permisos de otras unidades y el commit inicial tuvo una falla Kotlin, ambos registrados. V2 core dirigida del SHA final `45441529106` estaba IN_PROGRESS al registrar este informe; no atribuirle PASS sin comprobar su cierre.

Próxima unidad propia **W05-A1B3B1**: readmission Evidence2 o selección explícita fail-close; migrar STAGED9→10→11, restaurar Household10 + H11R con pin original y reanudación idempotente ante fallos, comparación total del backup FBS2v8 al terminar, con guard/READY/CI propios. Después W05-A1B3C cutover/reopen/recovery. Stock Apply en A1C posterior y siempre V2 productiva/Auto/Noche/pagos OFF hasta autorización y certificaciones.

Consumir permiso de B0 a BLOCKED, publicar checkpoint y liberar writer; no rehacer las unidades B7 anteriores.
