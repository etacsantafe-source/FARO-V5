# FARO V5 — W05-A1B3B0 — Base FBS2v8 → cinco stores STAGED9 — B1–B5

Fecha 2026-11-24. Rama `work/s3-night-20261102`. Entrada verificada HEAD `d33d2580bd0628ee8de5c6e65f2773b9c84110c5`; writer RELEASED epoch 1838; permiso A1B3A BLOCKED consumido. Unidad independiente `S4_W05_A1B3B0_FBS2V8_FIVE_STORE_BASE_REPLAY_DEV`. Todos los cambios DEV STAGED, no ACTIVATION, V2 productiva / Auto/Noche / pagos / stock real OFF.

## B1 — Hechos y estado previo

W05 A0/A1A/A1B0/A1B1/A1B2A/A1B2B/A1B3A B7 PASS. A1B3A usa `FaroV5Household11V8RestoreAdmission.inspect` read-only para stage Finance9/Auxiliary4, Evidence1/2, FBS2v8 autenticado, identidad y host/pointer, 8/8 XML `45437871429`, guard `45437871066` y cierre `45438804361` SUCCESS. NO repetir.

Código auditado:
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreStagedRestore.kt` blob `919caa9fbb26c9433d806f1dab7360bacb1664db`: restaura los 5 owners (Reality legacy/G4/Planning, Commercial, Web, Auxiliary4 recibos, Workspace) dentro de STAGED9. Solo acepta v6/v7 Household10 verificado o FBS2v5; el pin del ciphertext se crea antes del primer write.
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10ArchiveCoordinator.kt` blob `b80251088566fe58a4f0027bb489acbfb3073409`: patrón de restore bajo quiescence, `FileRestoreArchiveIdentity`, captura de verificación de cinco owners, Finance9 transaccional. NO modificar esta unidad certificada.
- `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/FileRestoreArchiveIdentity.kt` blob `f3309d1dddcbd1eca6a191590dce1bd90256a2a3`: pin durable de SHA256 de ciphertext contra (operationId, targetId), inmutable al reintentar. Reutilizar sin modificaciones.
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8RestoreAdmission.kt` blob `cd88eb994f0bd288037a1502e16f4b11584b814c`: preflight y autenticación v8, no autoridad de restore.
- `RealityFinance9StagedRowsRestorePort` blob `442fdd6b80cc95980f6358d228b1c61abc06534a`: restaura ocho tablas Finance9 en SQLite9 con rollback/readback. Mantener original.
- Gap: el pin/restore v6 ya verificado no debe aceptar ciphertext distinto y la versión v8 no se puede reinterpretar como v6 ni sellar de nuevo con IV distinto por reintento. Falta ligar el **cifrado v8 original** a replay de base sin descartar las secciones H10/H11R posteriores.

## B2 — Comparación y reutilización

V4.4 SmartCompra contaba compra, necesidad y confirmación pero no brindaba identidad FAROBAK2/SQL11. V5 reutiliza: `FaroV5FiveStoreStagedRestore` exacto, `FaroV5FiveStoreBackupCapture.captureReceiptProjection`, `RealityFinance9SealedReadPort`, `RealityFinance9StagedRowsRestorePort`, `FaroV5GenerationStoreGraph.openValidatedV2Finance9Auxiliary4[Evidence2]Stage`, `FileRestoreArchiveIdentity`, `MaterialWriteQuiescencePort`, `FileActiveGenerationPointer` (ID+sequence). No usar código productivo ni downgrade.

## B3 — Investigación y alternativas

Documentación primaria: SQLite https://www.sqlite.org/wal.html (checkpoint y cierre WAL; un .db sin -wal solo es estable después de cierre seguro), https://sqlite.org/pragma.html (`integrity_check` no incluye errores FK; comprobar también `foreign_key_check`), y NIST https://csrc.nist.gov/pubs/sp/800/38/d/final (GCM autentica ciphertext y AAD). Son referencias, no sustituyen evidencia CI.
A: **Elegida**: sobrecarga estrictamente interna que entrega el **snapshot v8 autenticado y los mismos bytes cifrados** al restaurador de base preexistente, sin cambiar rutas públicas ni decodificar como v6. Pin durable siempre del ciphertext v8 original; después Finance9 SQL9, comparación canónica de cinco owners y Finance9, y verificación de identidad. Limitado a STAGED9; reintento de operación/archivo/target idéntico.
B: **Descartada**: crear un FBS2v6 nuevo de la sección base y pasar al restore existente. Reencriptaría con salt/nonce aleatorio cada vez, rompiendo identidad del archivo v8 y permitiendo falsear procedencia.
C: **Descartada**: permitir base restore genérico de FBS2v8 por fallback. Riesgo de ignorar receipts11 sin fail-close.

## B4 — Diseño y cuatro rutas exactas

1. **Modificar únicamente API interna** `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreStagedRestore.kt`: agregar `restoreVerifiedHousehold11BaseStageOnly(operationId, archive: AuthenticatedHousehold11RestoreArchive, origin)` que deriva base FBS2v8 ya autenticada, usa ciphertext original para el pin y reusa `restoreAuthenticatedImpl` histórico. Sin cambiar comportamiento de v5/v6/v7.
2. **Nuevo** `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8BaseReplay.kt`: holder `AuthenticatedHousehold11RestoreArchive`, coordinador DEV isolated solo STAGED9; bajo quiescence llamar A1B3A admission, autenticar v8, pin original, graph stage9 Evidence1 o Evidence2, restaurar cinco owners, cerrar, restaurar Finance9 SQL9, volver a leer cinco owners/Finance9 y cotejar bit semántico con archivo v8 (normalizar solo manifest fuente), comprobar pointer original, exigir pin existente. No escribir Household10/H11R, no migración 9→10/11, no journal switch.
3. **Nuevo** `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8BaseReplayTest.kt`: ocho pruebas de regresión dirigida JVM.
4. **Nuevo** `.github/workflows/faro-v5-s4-w05-a1b3b0-fivestore-base-replay.yml`: preflight parent READY exact4 + 8 tests XML, artefacto original. Modificación 1 llama también a workflows legacy por archivo compartido; registrar si alguno hace fail PRELIGHT por permiso de unidad distinta, no llamar «todo CI verde» sin evidencia.

## B5 — Auditoría adversarial planificada

T01: archivo FBS2v8 autenticado desde STAGED9 origen con datos Finance9/Workspace/G4/Auxiliary4, replay base a target STAGED9 y comparación semántica; no cambiar active pointer.
T02: misma operación y mismo ciphertext de v8: retry idempotente, SHA pinned; ninguna fila duplicada.
T03: misma operación con ciphertext distinto pero mismo plaintext de v8 (nuevo nonce) debe fallar ARCHIVE_IDENTITY_CONFLICT y no modificar DB.
T04: corte después de `AUXILIARY` de los cinco owners; retry tras recrear coordinador debe completar exactamente.
T05: corte tras FINANCE9 restaurado; retry idempotente y verificación semántica exacta.
T06: password incorrecta, tag/cipher tampered, v6/v7 en vez de v8: rechazo ANTES de pin y cambios.
T07: source==target, target ACTIVE, WAL sidecar/hardlink: rechazo antes de SQLite write; pin inexistente en errores previos a autenticación.
T08: host DEV revocado o cambio ABA del puntero tras admisión: no debe instalar pin ni modificar los stores.

El B7 de esta unidad certificará **solo cinco owners + Finance9 en STAGED9** desde ciphertext v8. **NO** Household10, H11R SQL11, backup completo terminado, journal switch, reopen/recovery, Apply ni stock real. Continuidad explícita: **A1B3B1** migrar STAGED9→10→11, restaurar H10 + H11R, reanudar cortes y readback exacto; **A1B3C** cutover/reopen/recovery; **A1C** stock Apply humano y Android después. No iniciar B6 sin guard documental SUCCESS y permiso READY exact4.
