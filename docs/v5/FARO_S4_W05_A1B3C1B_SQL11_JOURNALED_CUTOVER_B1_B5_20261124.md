# FARO V5 · W05-A1B3C1B · FBS2v8 SQL11 journaled DEV cutover/recovery · B1–B5

Fecha 2026-11-24, Argentina. Branch `work/s3-night-20261102`. HEAD base `589f3bacef552b3a215fcf1ae3af077762004b49` confirmado; writer RELEASED epoch1878; permiso anterior BLOCKED, A1B3C1A B7 PASS; guard 45491085723 SUCCESS. V2 core regression 45490081365 SUCCESS.

## B1 — Hechos certificados

No repetir W05 A1B3A, A1B3B0, A1B3B1, A1B3C0, A1B3C1A ni W1–W4. A1B3C1A: ticket `bootstrap/household11-dev-cutover-v1.bin` versionado `FARO_H11_SQL11_PRE_CUTOVER_AUTHORIZATION_V1`, checksum SHA256, operationId, origin pointer completo, sourceId/targetId, digest FBS2v8 cifrado original, fingerprints SHA256 origen/target cinco SQLite, revisiones y cantidades H10/H11R. Journal RESTORE hasta VALIDATED; CI 45490081521 JUnit XML 8/8. Código `df55b3c4f93281b34ae4dab854fdf43b9d4b0774`. Documento B7 `docs/v5/FARO_S4_W05_A1B3C1A_SQL11_DURABLE_AUTHORIZATION_B7_PASS_20261124.md` blob `ad898f2c3e03b2f19243808c3adf484da38563a8`.

El kernel `ValidatedGenerationActivationCoordinator` y `GenerationSwitchCoordinator` proporcionan journaling CAS, SWITCH_PREPARED, pointer selection, rollback y replay fence. La activación DEV de Household10 NO es compatible con Reality SQL11. La reapertura de Graph productivo `FaroV5GenerationStoreGraph` solo está certificada para Reality10, por lo que jamás la invocar con Reality11 sin una ruta de schemas propia.

## B2 — Reuso y riesgos

Reusar `FileBootstrapJournal`, `BootstrapTransitionPolicy`, `FileGenerationReplayFence`, `ValidatedGenerationActivationCoordinator`, `GenerationSwitchCoordinator` y `FaroV5Household10DevCutoverOwner.fingerprint` (SHA de cinco stores). Reutilizar `FaroV5Household11DevCutoverAuthorization` como fuente de verdad de la estructura durable ticket, sin cambios al contrato ya certificado. Validación de SQL11 con `StagedSqlitePreflight.requireAll` strict cinco stores: Reality11/H10+H11R, Workspace4, EvidenceWeb2, EvidenceCommercial1, Auxiliary4. Lectura de H10/H11R SQL11 immutable sin sidecars. Solo DEV aislado. No crear nuevo motor de CAS ni activar Apply. El resultado DEV `ACTIVE_ELEVEN` jamás implica disponibilidad de hosts Android productivos.

C1B hace recuperación y rollback tras simulaciones de caída antes del fence; la instalación del fence ocurre solo en la generación DEV aislada y después de reapertura segura; no activa la app.

## B3 — Elección

A elegida: nuevo owner C1B con acceso a ticket **solo lectura** (checksum/versión/rutas estrictas), root-lock compartido y gate global, validaciones stage-11 bajo host cerrado y conexión immutable. Sin cambiar API de C1A. Start exige ticket journal VALIDATED, id/target/origin y digest del ciphertext cifrado original re-presentado; reautenticar FBS2v8 y `FileRestoreArchiveIdentity.requireExistingPinnedArchive`. Recovery de startup DEV puede usar ticket+journal+fingerprints sin passphrase (secreto nunca persistido); no autoactivar una generación sin ticket y journal válidos.

B descartada: forzar `Household10DevCutoverRuntime` con schema11, viola strict schema10. C descartada: marcar Productive V2 ON sin Android reopen, inseguro.

## B4 — Rutas exactas de implementación (3 nuevas)

1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11DevJournaledCutover.kt`: ticket verification SHA256, exact five-store immutable SQL11 health (sin cambiar grafo productivo), gateway lock DEV, foreground start + recovery, coordinator journaled, CAS pointer/replay fence, rollback prewrites, no-op controlled projection (DEV), fault seam y root close. Si ticket/estado extraño bloquea, sin crear ticket.
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11DevJournaledCutoverTest.kt`: mínimo ocho JUnit reales end-to-end: switch + reopen, reinicio idempotente, crash post-pointer pre-journal, after-health y rollback, fail-closed against ticket invalid, pointer ABA, host revoke, wrong ciphertext.
3. `.github/workflows/faro-v5-s4-w05-a1b3c1b-journaled-cutover.yml`: preflight READY parent exact3, Java21 AndroidSDK37, Gradle test, XML original 8/8, artifact.

## B5 — Certificación/bordes

T01 desde SQL11 restored Evidence1 y C1A VALIDATED: after confirmation ACTIVE_ELEVEN/COMPLETED, pointer sequence+1, journal writes accepted + fence RESTORE dev; H10/H11R exactos al reabrir, los cinco owners no se modifican.
T02 same ticket+op after restart: recover idempotente y no reemitir switch.
T03 interrupción `AFTER_POINTER_PUBLISH_BEFORE_JOURNAL_ADVANCE` → journal SWITCH_PREPARED con pointer target, recovery CAS converge sin segunda mutación.
T04 runtime health falla antes del fence → rollback al origen sequence+2 y journal ROLLBACK_VERIFIED sin permiso de target.
T05 caída post-rollback pointer pre-journal → recovery converge a origen, no aceptar material writes.
T06 ticket corrupto/ausente, foreign journal, wrong archive/passphrase, otro nonce o Evidence2 fail-close.
T07 host revocado/lock ocupado/ABA rompe sin nuevo switch.
T08 SQL11 reality o H10/H11R adulterados, schema10 o sidecars fail-close.

*Alcance limitado*: este owner sólo realiza DEV-only switch tras autorización C1A y con gate raíz aislado; no integra Android app startup. C1C integrar host Android, reapertura UI y pruebas físicas; Evidence2 separado. Sin V2 productiva, stock Apply, pagos o Auto/Noche. No afirmar A1 completo antes de C1C/Evidence2 y pruebas de cierre.

Protocolo: audit guard SUCCESS; READY exact3 + guard SUCCESS; código B6; CI real XML8/8; B7, permiso consumido, checkpoint HEAD, writer RELEASED, guard final SUCCESS. Si falla, RED_FIX documentado.
