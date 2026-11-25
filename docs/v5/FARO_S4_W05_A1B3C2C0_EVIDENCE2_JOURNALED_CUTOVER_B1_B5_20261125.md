# FARO V5 · W05-A1B3C2C0 · Evidence2 v2 SQL11 journaled DEV cutover y recovery · B1–B5
Fecha de preparación: 2026-11-24/25 (Argentina). Repositorio `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`. Unidad exacta propuesta: `S4_W05_A1B3C2C0_EVIDENCE2_SQL11_JOURNALED_CUTOVER_DEV`.
Baseline verificado de entrada: `1f5d75663514b830a3fad4662d9d0202cbc76249`, writer remoto RELEASED epoch1926, permiso C2B1B BLOCKED consumido, W05 A1 global OPEN. Esta preparación es documental; B6 y B7 aún NO ejecutados. Mantener V2 productiva/pagos/Auto/Noche y stock real OFF.

## B1 — Evidencia y gap exacto
- C2B1B Evidence2 v2 autorización durable PRE-CUTOVER B7 PASS: código `e17aa76a41f2292ef32dbc1265d6a7882e7c42a1`; GitHub Actions CI `45522706065` SUCCESS, guard `45522706044` SUCCESS; XML original validado en CI 8 Evidence2 durable + 8 Evidence1 durable + 6 semánticos = 22/22. Auditoría `docs/v5/FARO_S4_W05_A1B3C2B1B_B7_PASS_EVIDENCE2_V2_DURABLE_AUTH_XML22_22_20261125.md` (blob `83b06b2d85c1a63971a2d952dc3954f9846286eb`). No reprogramar.
- C2A STAGED Evidence2 16/16, C2B0 preflight 14/14, C2B1A semántica 20/20, C1B Evidence1 journaled cutover 8/8, C1C Android Evidence1 read-only WAL 18/18, certificados en sus SHA anteriores. Evidencia histórica por alcance, NO equivale a Evidence2 ACTIVE.
- Falta consumir específicamente el ticket `bootstrap/household11-evidence2-dev-cutover-v2.bin` (`FARO_H11_SQL11_EVIDENCE2_PRE_CUTOVER_AUTHORIZATION_V2`) después del journal VALIDATED, con nuevo consentimiento foreground. El ticket v1 Evidence1 de C1B tiene otra identidad y debe rechazarse. C2B1B no cambia el puntero, no instala fence ni activa stock.
- Código real inspeccionado: `FaroV5Household11Evidence2DevCutoverAuthorization.kt` (ticket v2, hash, FBS2v8 schema2, semántica, 5-store, CAS, recuperación READ_ONLY); `FaroV5Household11DevJournaledCutover.kt` (algoritmo Evidence1 v1); `FaroV5Household11Evidence2SemanticPreflight.kt` y `FaroV5Household11ActiveDevReadHost.kt` (host Evidence1 v1). No usar ese host para Evidence2 sin contrato nuevo.

## B2 — V4.4 y reutilización exacta
- Comparación histórica P02/P03 (`docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md`; `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`): V4.4 separa compra, pago, recepción e inventario; su runtime y backup no prueban Reality11/Evidence2 ni son reutilizables como ejecutables.
- ADAPTAR (no duplicar kernel): `ValidatedGenerationActivationCoordinator`, `GenerationSwitchCoordinator`, `FileBootstrapJournal`, `FileActiveGenerationPointer`, `FileGenerationReplayFence`, `FileStorageGenerationLayout`, `FileRestoreArchiveIdentity` y el gate/lock raíz usados por C1B. Preservar invariantes de secuencia completa y comparación CAS.
- NO reutilizar ni modificar el ticket v1, el owner C1B certificado, el opener SQL10 ni `immutable=1` en ACTIVE. STAGED sellado puede usar immutable; ACTIVE WAL requiere lectura coherente de WAL, solo con host DEV posterior C2C1.

## B3 — Investigación de alternativas documentada
Referencias externas primarias con mecanismo:
- SQLite WAL: https://www.sqlite.org/wal.html — una conexión ACTIVE necesita considerar el WAL; sidecars válidos no deben confundirse con corrupción o datos STAGED.
- SQLite atomicidad: https://sqlite.org/atomiccommit.html y https://www.sqlite.org/howtocorrupt.html — journal/archivo es una unidad de recuperación; no copiar SQLite activo sin contrato.
- SQLite transacciones: https://www.sqlite.org/transactional.html — crash y reinicio se contrastan con estado durable y atomicidad.
- Android SQLiteDatabase: https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase — `OPEN_READONLY` no debe transformarse en `CREATE_IF_NECESSARY`; pruebas Android aparte de esta unidad.
Alternativa A ADAPTAR: clase DEV aislada de cutover Evidence2 v2 que toma ticket/journal existentes, reautentica FBS2v8 schema2 + semántica tipada, comparte coordinador CAS/fence y hace recovery/rollback idempotente; sin dependencia nueva ni coste, compatible offline. ELEGIDA.
Alternativa B DESCARTAR: ampliar C1B Evidence1 con ticket dual v1/v2 y branch implícito; aumenta superficie del código PASS e induce confusión entre perfiles. Alternativa C DESCARTAR: activar pointer al quedar ticket VALIDATED o hacerlo desde startup; elimina consentimiento foreground y viola fail-closed. Alternativa D POSPONER: host Android ACTIVE y operaciones de stock, requieren C2C1/A1C separados.
Hipótesis falsable: A produce exactamente una transición desde journal VALIDATED autorizado, puede recuperar crash sin crear segunda transición, revierte N+2 antes de aceptar material writes; toda inconsistencia rechaza sin seleccionar target.

## B4 — Diseño unificado, solo 3 rutas exactas nuevas
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11Evidence2DevJournaledCutover.kt`: acquire DEV bajo `finance9-dev-root.lock` y `SingleProcessMaterialWriteGate` con hostClosed; leer ticket v2 inmutable/no-symlink con SHA256 y versión exacta; rechazar ticket v1/foreign/pending; reautenticar ciphertext original + `FileRestoreArchiveIdentity` + semantic schema2 C2B1A y fingerprints+revisiones sellados; doble confirmación foreground; transicionar con coordinadores existentes y CAS pointer N→N+1; journal/fence RESTORE dev; recover solo si hubo SWITCH_PREPARED autorizado; rollback N+2 antes de writes; devolver estados tipados; sin graph Android/Apply.
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11Evidence2DevJournaledCutoverTest.kt`: 10 JUnit5 end-to-end, fixture FBS2v8 Evidence2 REAL y SQL11 H10/H11R, inyección de crash; ninguna fixture sintética bypass de semantic admission.
3. `.github/workflows/faro-v5-s4-w05-a1b3c2c0-evidence2-journaled-cutover.yml`: push rama aislada en rutas exactas, preflight `HEAD^` para las 3 rutas y el unit_id, JDK21, Android SDK37, `:store:generation:test` dirigido, comprobar XML ORIGINAL 10/10 nuevo + regresiones C1B 8/8 y C2B1B 8/8 = 26/26, 0 failures/errors/skips, upload XML. Si tests reales o dependencias obligan a ajustar cantidades, auditar RED_FIX y no falsear XML.

## B5 — Pruebas adversariales y STOP
T01 Evidence2 schema2 STAGED genuino, ticket v2 VALIDATED, dos confirmaciones foreground y archivo autenticado original → ACTIVE_ELEVEN DEV, pointer N+1, journal COMPLETED, fence RESTORE y H10/H11R exactos.
T02 Reentry mismo operationId con journal COMPLETED y mismos fingerprints → mismo pointer, no doble switch ni efecto.
T03 Cierre/crash luego de SWITCH_PREPARED antes de journal siguiente → recover idempotente con pointer target N+1, sin segundo Apply.
T04 Error de salud tras switch → rollback CAS origen N+2, journal ROLLBACK_VERIFIED, ningún write/fence target.
T05 Crash después de rollback pointer antes de journal → convergencia origen sin segundo switch.
T06 VALIDATED sin consentimiento foreground al reiniciar → AWAITING_FOREGROUND; no auto-cutover.
T07 Ticket v1 simultáneo o sustituto schema1, versión/CRC/hash corruptos, symlink/hardlink, archivo FBS2v7 → BLOCKED.
T08 Drift semántico Evidence2 tipado con fingerprint recalculado, wrong passphrase/ciphertext, archive pin o cinco stores/SQL11 inválidos → BLOCKED.
T09 ABA full-pointer, host abierto o revocado, lock concurrente, foreign journal/operation → BLOCKED; cero mutaciones.
T10 Confirmación negada, target diferente, llamada desde build productivo o intento stock Apply/pagos/Internet → no authority; fallo cerrado.
Regresión C1B Evidence1 8/8 y C2B1B autorización 8/8 solo para evitar deriva; no reimplementar PASS. La unidad NO certifica C2C1 Android, A1C recepción/Undo, G15 físico ni A1 global.

## Salida B5 y criterio de autorización
B1–B5 AUDITADOS documentalmente sobre los contratos vistos; alcance mínimo de 3 rutas elegido. Emitir permiso `READY` únicamente después del guard documental SUCCESS con `blob_sha` de este documento, `reviewed_head` ancestral exacto y `last_code_review` anterior C2B1B PASS. B6 y B7 NO EJECUTADOS. No modificar código sin READY/preflight ni mezclar permiso con código; escritor CAS; ver CI/XML y guard exactos, B7 independiente, consumir permiso y liberar writer.