# FARO V5 · W05-A1B3C1A · B7 PASS SCOPED · FBS2v8 SQL11 pre-cutover durable authorization

Fecha 2026-11-24 Argentina. Repo `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`.
Unidad `S4_W05_A1B3C1A_FBS2V8_SQL11_DURABLE_AUTHORIZATION_DEV`.

## B1–B5 — Preauditoría y alcance probado

Entrada `61b9a3eac75fbd3e52cb82cec1c39aeab5cfd232`, A1B3C0 B7 PASS (SQL11 FBS2v8 Evidence1 pre-cutover READ_ONLY, CI `45487014676` JUnit XML8/8, guard `45487897008` SUCCESS). Writer RELEASED epoch1870, permiso anterior BLOCKED. V2 core anterior `45487014742` SUCCESS sin habilitar app productiva.

Preauditoría `docs/v5/FARO_S4_W05_A1B3C1A_SQL11_DURABLE_AUTHORIZATION_B1_B5_20261124.md`, blob `0bfcdbc68c660ede893859ffdd8113bca59ccf58`, commit `30510d3d3eb2b7040aa17659270f4988708f896e`, guard `45489475775` COMPLETED SUCCESS.
Permiso READY exact 3 rutas commit `8a846625a4da6959ce11983277360922a293ca0a`, guard `45489683356` COMPLETED SUCCESS.
No se repiten W1/W2/W3/W4 ni W05 A1B3A, A1B3B0, A1B3B1, A1B3C0.

## B6 — Código, guardia, CI y XML real

Commit **`df55b3c4f93281b34ae4dab854fdf43b9d4b0774`**, exactamente tres rutas publicadas:
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11DevCutoverAuthorization.kt`.
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11DevCutoverAuthorizationTest.kt`.
- `.github/workflows/faro-v5-s4-w05-a1b3c1a-authorization.yml`.

Guardia del código **`45490081297` COMPLETED SUCCESS**. CI dirigida `45490081521`, job `138785743922`, código SHA exacto, **COMPLETED SUCCESS**. Preflight READY parent exact3 PASS para las tres rutas; Gradle `BUILD SUCCESSFUL in 2m 49s`; marcador `FARO_W05_A1B3C1A_SQL11_DURABLE_AUTHORIZATION_XML_PASS_8_8`.
Artifact original GitHub `14966541486`, nombre `w05-a1b3c1a-sql11-durable-authorization-xml`, ZIP 832 bytes, digest GitHub `sha256:f25a7b598556c15e9bc93c5f81b92bc313e58d9af8e431fe90d0386b0a6509d8`.
ZIP original descargado en contenedor, SHA256 recalculado idéntico, `zipfile` y `xml.etree.ElementTree` independientes verificaron exactamente **1 XML**, suite `FaroV5Household11DevCutoverAuthorizationTest`, **tests=8, failures=0, errors=0, skipped=0** y ocho nombres de pruebas reales. Duración JUnit `10.964s`.

## B7 — Alcance estrictamente certificado

**B7_PASS_SCOPED_W05_A1B3C1A_FBS2V8_EVIDENCE1_SQL11_DURABLE_PRE_CUTOVER_JOURNAL_XML_8_8**.

En DEV JVM aislado, bajo `SingleProcessMaterialWriteGate` y el mismo root lock que Household10, se habilitó **solo autorización previa al switch**, con dos confirmaciones de foreground:
1. `FaroV5Household11DevCutoverPreflight` previamente certificado verifica fuente Finance9, destino SQL11 H10/H11R, Evidence1, ciphertext original pin durable, pointer ID+sequence y cinco stores. No se acepta FBS2v7 ni Evidence2 typed.
2. Ticket durable independiente `bootstrap/household11-dev-cutover-v1.bin` con versión, IDs, digest del archivo original y fingerprints de origen/target, sin passphrase ni contenido privado; escritura por temp+ATOMIC_MOVE+fsync, comprobación de readback y checksum. El ticket NO es autoridad para saltarse el journal.
3. Journal RESTORE PREPARING → STAGED → VALIDATED, transiciones legales `BootstrapTransitionPolicy` por CAS; el puntero original permanece intacto y `materialWritesAccepted=false`; no se crea `replay-fence`, no se abre runtime productivo, no se mueve generación ni se aplica inventario.
4. Retry exige **mismo operationId, target y ciphertext FBS2v8 autenticado**, verifica `FileRestoreArchiveIdentity.requireExistingPinnedArchive` sin crear otro pin; fingerprint exacto de cinco stores. Cambio de nonce, password errónea y archivo antiguo se rechazan incluso con ticket ya publicado.
5. Inyección de caída tras publicar ticket (antes de journal), tras PREPARING y tras STAGED: reinicio con root lock recuperado y archivo original retoma sin duplicar journal y converge a VALIDATED; `recoverReadOnly()` informa estado durable sin realizar cambios ni reactivar una generación.
6. Journal extranjero pendiente, host cerrado revocado y pointer ABA antes de publicar ticket fallan sin avanzar autoridad.

**Límites y trabajo pendiente:**
- No hay cambio de active pointer, SQL11 active graph, instalación de fence de escritura, generación visible, rollback ni recuperación después del SWITCH. No se realizó prueba física Moto G15.
- No se debe llamar a este ticket/caso `cutover terminado`; es **preparación durable para C1B**, no capacidad productiva.
- Próximo **W05-A1B3C1B**: conectar ticket validado a `ValidatedGenerationActivationCoordinator`, con lector/reopen de SQL11 y H10/H11R, CAS pointer + rollback/recovery y fault-injection; propia B1–B5/guard READY/CI/XML, sin usar el validador SQL10 incompatible.
- Luego **C1C** integración Android/startup recovery si necesita separación, Evidence2 propio, **A1C** Review/Apply del stock con autorización humana posterior.
- V2 productiva, Auto/Noche, pagos y stock real OFF. No publicar a main.

Al cerrar esta unidad consumir READY a BLOCKED, publicar puntero de continuidad, liberar writer y comprobar guardia FINAL SUCCESS contra checkpoint exacto. La regresión V2 core sobre el SHA de código `45490081365` estaba IN_PROGRESS en última comprobación; no adjudicarle PASS hasta lectura concluyente.
