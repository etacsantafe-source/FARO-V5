# FARO V5 · W05-A1B3C1A · FBS2v8 SQL11 journaled PRE-CUTOVER authorization · B1–B5

Fecha 2026-11-24. Repo `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`. HEAD de entrada confirmado `61b9a3eac75fbd3e52cb82cec1c39aeab5cfd232`, writer RELEASED época 1870 y permiso anterior BLOCKED/B7 PASS. Guardia final B3C0 `45487897008` SUCCESS. Regresión V2 core `45487014742` SUCCESS, sin asumir habilitación productiva.

## B1 — Hechos y contratos certificados

A1B3C0 es solo lectura pre-cutover SQL11 Evidence1, archivo FBS2v8 autenticado y pin original, estado SQL11 H10/H11R + fingerprint exacto de cinco stores; CI `45487014676` XML 8/8, guard `45487014427` SUCCESS; informe `docs/v5/FARO_S4_W05_A1B3C0_SQL11_CUTOVER_PREFLIGHT_B7_PASS_20261124.md` blob `eddcda6505cf642b93fa8eff8ffc6ce561e2cb13`.

Reutilizar código ya auditado sin modificar:
- `FaroV5Household11DevCutoverPreflight.kt` blob `675870c927896697fe6e1940215b30f68b972bb4`, inspect `FaroV5Household11CutoverInspection` (SHA cifrado, pointer full, SHA256 cinco almacenes origen/destino, revisiones H10/H11R) y no journal/pointer write;
- `FaroV5Household10DevCutoverRuntime.kt` blob `803ad8b94e4dd30df6b0776b4c9f2a47e3563a4f`, patrón de root lock compartido `finance9-dev-root.lock`, ticket versionado, compuerta global `SingleProcessMaterialWriteGate`, crash/recover;
- `FaroV5Household10DevCutoverOwner.kt` blob `edd6835ad47a6950df946352d8d50c87776fafdf`, fingerprint cinco stores de solo lectura, journal CAS y reconciliación; incompatible SQL11 y no reutilizar su reopen SQL10;
- `FileBootstrapJournal` y `BootstrapTransitionPolicy`: transiciones legales PREPARING → STAGED → VALIDATED; `FileRestoreArchiveIdentity.requireExistingPinnedArchive`: NO crear pin.
- `FaroV5Household11V8FullStageReplay` ya certificó SQL9→SQL11 y receipts, CI10/10; no ejecutar nuevamente durante autorización.

Gap: hace falta un ticket durable distinto de Household10 que ligue operación/archivo cifrado original + target + pointer + fingerprints y journal preparado para futuro C1B, especialmente si hay caída entre ticket y journal. No existe permiso para switch ni reopen SQL11 aún.

## B2 — Comparación histórica y reuso

V4.4 no es autoridad para SQL11. V5 reutiliza material-write gate **global**, root file lock EXCLUSIVO compartido con Household10, `FileBootstrapJournal` inmutable, ticket versionado y SHA256, no autenticación falsa por fingerprint solamente; FBS2v8 autenticado + identidad durable original siguen obligatorios. La nueva unidad no crea backup, no restaura ni migra SQLite, no cambia activePointer, y NO instala `FileGenerationReplayFence`.

## B3 — Alternativas y decisiones

Primarias: SQLite WAL https://www.sqlite.org/wal.html; FileChannels Java https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html; journal/store actual del repositorio. Alternativas:
A ELEGIDA: unidad C1A prep-only: validar C0 bajo el mismo gate/lock, autorización humana en foreground antes y después, publicar ticket versionado *antes* del journal; crear PREPARING CAS → STAGED → VALIDATED; retornar siempre puntero ORIGEN. Crash después de ticket pero antes de journal exige reentrada con mismo FBS2v8 autenticado, nunca adopción automática sin archivo. `recoverReadOnly()` observa un ticket y estado del journal sin mover autoridad ni recrear ticket.
B descartada: reusar ticket Household10 o validador SQLite10; incompatible SQL11.
C descartada: publicar puntero desde C1A; no hay todavía owner de reapertura SQL11 ni rollback verificado.

## B4 — Rutas B6 exactas

1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11DevCutoverAuthorization.kt`: runtime DEV `acquire` usa lock raíz compatible, prueba host cerrado, gate compartido y inyección de fallos antes de journal, después de PREPARING, después de STAGED. Ticket `bootstrap/household11-dev-cutover-v1.bin` versionado, checksummed, atomic rename y fsync directorio, no contenido sensible (solo IDs/hashes). `authorize` prepara journal y nunca hace SWITCH_PREPARED; `recoverReadOnly` inspecciona bajo gate/lock y fail-close; reintento explícito requiere mismo archivo + passphrase. Verifica previos journals/fences, pointer ID+sequence, fingerprints, pin original, FBS2v8 Evidence1.
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11DevCutoverAuthorizationTest.kt`: 8 pruebas JUnit JVM stage SQL11 real, journal CAS idempotente, 3 puntos de crash, mismatch AES/nonces, journal conflict y host/pointer, sin switch.
3. `.github/workflows/faro-v5-s4-w05-a1b3c1a-authorization.yml`: preflight parent READY exact3, Gradle/JDK21 AndroidSDK37, original XML8/8 artifact.

## B5 — Auditoría adversarial y límites

T01: fuente Finance9 original intacta; SQL11 Evidence1 H10/H11R, autorización explícita dos veces, ticket/journal VALIDATED, puntero origen, NO fence ni aplicación de stock.
T02: mismo operación/target/ciphertext reintento → VALIDATED idempotente y mismo ticket.
T03: crash entre ticket publicado y journal, recuperación solo al reintroducir mismo FBS2v8; no auto-publish pointer.
T04: crash tras PREPARING → retry STAGED/VALIDATED, owner no restaura SQLite.
T05: crash tras STAGED → retry VALIDATED exacto, sin modificar realidad.
T06: contraseña falsa, tag alterado, nonce nuevo/otro archivo, v7 bloqueados sin mutar journal existente.
T07: journal de otra operación, replay fence, host revocado, target no canónico o pointer ABA → fail closed.
T08: `recoverReadOnly` sobre ticket corrupto, journal faltante o pointer cambiado → BLOCKED, no switch, no autoaceptación de escritura.

No prometer startup replay automático ni switch en C1A. Futura **C1B**: `ValidatedGenerationActivationCoordinator` específico Reality11 para switch, reopen/rollback y fault-injection; **C1C** integración Android y recovery startup si fuera necesario. Evidence2 separado. V2 productiva, Auto/Noche, stock real y pagos OFF, sin APIs pagas.

Secuencia: doc B1-B5 y guard SUCCESS; READY exact3 y guard SUCCESS; B6 código/CI/8 XML real; B7 scoped, permiso BLOCKED, puntero de continuidad y writer RELEASED; guard final SUCCESS.
