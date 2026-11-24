# FARO V5 · W05-A1B3B1 · FBS2v8 → Household10 + Reality11 SQL11 receipts · B1–B5

Fecha: 2026-11-24 Argentina. HEAD entrada `67e0222c1a28d6fbea625e2e34805ea1fd1ace9f` rama `work/s3-night-20261102`, writer inicial RELEASED epoch1850, guardia 45442640827 SUCCESS. Unidad `S4_W05_A1B3B1_FBS2V8_H10_H11R_STAGED_REPLAY_DEV`.

## B1 — Evidencia y contrato

B7 anteriores A0/A1A/A1B0/A1B1/A1B2A/A1B2B/A1B3A/A1B3B0, todos PASS acotados; no repetir. A1B3B0 último commit de código `e99015370474b97b560ed4b2852825cfc39c6dd6`, CI dirigida 45441529195 JUnit XML 8/8 PASS, pin durable del ciphertext FBS2v8 ORIGINAL a op+target, cinco owners y Finance9 restaurados en STAGED9 Evidence1; checkpoint `67e0222c1a28d6fbea625e2e34805ea1fd1ace9f`. V2 core regresión 45441529106 comprobada SUCCESS. Productivo/Auto/Noche/pagos/stock real OFF.

Módulos de contrato existentes (no modificar):
- `FaroV5Household11V8BaseReplay.kt` blob `191a2472ec0e70b8c34ec829d32b42c00ccfd386` replays cinco owners + Finance9 a STAGED9 bajo lease y pin del archivo; **solo perfil Evidence1 probado**.
- `RealityHousehold10StagedFilePort.kt` blob `71b34ab0426a51b7fb3425f980667618de40c86f`: migración atómica Reality9→10 STAGED sin cutover.
- `RealityHousehold10StagedRowsRestorePort.kt` blob `bc6b2172f91d0013f06346207aa0e0c195f159df`: Household10 idempotente, destino fresco o identical, sqlite BEGIN IMMEDIATE/rollback/readback.
- `RealityHousehold11StagedFilePort.kt` blob `dbb32284960620296f94bad8fc7756658b9c2623`: migración 10→11 con nueva tabla de receipts STRICT, transaccional y WAL checkpoint.
- `FaroV5Household11ReceiptSql11Port.kt` blob `4f85895fc7b02534529027d525188f4046a53c5c`: recibos SQL11 8192/4MiB idempotentes, relectura, pin de puntero y stage, bajo quiescence. No invocar sobre DB activa.
- `FileRestoreArchiveIdentity` blob `f3309d1dddcbd1eca6a191590dce1bd90256a2a3`: `requireExistingPinnedArchive(op,target,originalCiphertext)`, falla si pin no existe o archivo distinto.

Gap preciso: falta puente autorizado que, **después** del restore base B0, haga 9→10, replay H10, 10→11, replay H11R, reanude desde schema10/11 tras crash y exija el mismo archivo autenticado/pin/pointer con readback final y sin cutover. Base B0 jamás puede ejecutarse sobre un schema10/11 porque reescribiría owners.

## B2 — Comparación y reuso

V4.4 SmartCompra no certificó identidad FBS2v8 ni SQL11; V5 reusa puertos atómicos por owner y puntero ID+sequence, no importa runtime V4.4. `RealityHousehold10SealedRowsPort` lee exclusivamente schema10 y valida datos; para comprobar Household10 al final en schema11 se requiere un puerto de lectura **separado de solo lectura** que valide schema11 y forma H10 sin downgrade ni alterar PRAGMA. B1 no modifica puertos de migración ni endpoints productivos.

## B3 — Alternativas y decisión

Docs primarias: https://www.sqlite.org/lang_transaction.html (BEGIN IMMEDIATE, atomicidad); https://sqlite.org/pragma.html (integrity_check + foreign_key_check y version); https://sqlite.org/wal.html (checkpoint tras cierre y rechazo de sidecars para fuente sellada). El crypto AES-GCM de FAROBAK2 ya está certificado, nunca reencriptar para replay.

A **elegida**: coordinador DEV interno con `quiescence` host-wide, autenticación FBS2v8 antes de tocar stage, `requireExistingPinnedArchive` en reanudaciones schema10/11, 4 fases observables 9→10, H10 restored, 10→11, H11R restored; el stage9 hace previamente B0 mismo op+bytes. Revisar schema estrictamente 9/10/11, perfil Evidence1; Evidence2 se deniega hasta test específico. Comparar H10/H11R con snapshot autenticado; conservar cinco otros owners inmutables durante fase.
B **descartada**: reiniciar B0 en schema10/11, riesgo de corrupción de datos al haber migrated.
C **descartada**: crear nuevo ciphertext FBS2v6/v7 para replay y omitir H11R, rompe identidad e inventario.

## B4 — Alcance exacto, cuatro rutas

1. Nuevo `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold11NeedsRowsPort.kt`: puerto de lectura SQL11 inmutable, sin mutación; PRAGMA exact SQL11, integridad+FK, forma STRICT de H10/11, rechazar triggers e inbound FKs Household10, leer/validar filas canónicas con lógica `Household10RowsSql` ya certificada (sin downgrade).
2. Nuevo `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8FullStageReplay.kt`: solo inicial y retry de STAGED9/10/11 Evidence1; quiescence host, stage y archivo original pin, revalidar puntero y cinco archivos, fase migración 9→10 → H10 → 10→11 → H11R, callbacks de fault test, `force` y readback H10/H11R; nunca pointer write ni SQLite outside stage.
3. Nuevo `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11V8FullStageReplayTest.kt`: 10 pruebas dirigidas JVM con snapshot H10 no vacío y H11R parcial; cierre desde stage9, reintento stage11, revisiones sin filas, crash después de migración10, H10 restore, migración11 y H11R commit, rechazo de archivo distinto, ciphertext/tag/versión inválida, ACTIVE/ABA/host, nonfresh H10 o receipt.
4. Nuevo `.github/workflows/faro-v5-s4-w05-a1b3b1-h10-h11r-stage.yml`: preflight parent READY exact4, JDK21 Android Gradle, 10 JUnit reales, verificador XML y artifact original.

**Corte de certificación:** DEV Evidence1, STAGED único e inactivo. A1B3B1 NO habilita Evidence2, no certifica autenticidad de export SQL11 desde una fuente activa, journal switch/reopen/recovery en A1B3C, ni A1C stock Apply. No modifica main ni permisos de unidades históricas.

## B5 — Pruebas adversariales previas

T01: full replay base → H10 + H11R SQL11; readback 2 needs H10, 2 receipts H11R parciales, Finance9 y cinco owners base (B0) y puntero activo intacto.
T02: operación/target/ciphertext mismo tras SQL11: idempotente sin duplicados.
T03: etapa tras SQL9→10 y crash, recuperación con pin original.
T04: etapa tras H10 commit y crash, H10 idempotente.
T05: etapa tras 10→11 y crash, H10 se conserva, H11R termina.
T06: etapa tras receipt SQL11 commit y crash, reanudación exacta.
T07: mismo plaintext distinto nonce tras primer pin falla; un pin ajeno nunca acepta sustitución.
T08: archive v6/v7, bad password, tampered ciphertext fallan antes de cambios.
T09: host revocado, pointer ABA, active target, sidecar/hardlink fallan.
T10: etapa SQL11 con H10 o H11R extraños debe bloquear sin overwrite ni activar.
Gates: guard B1–B5 SUCCESS → READY exact4 guard SUCCESS → B6 -> CI JUnit XML 10/10 -> B7 auditoría de alcance → READY consumido, puntero durable y writer RELEASED. Si no hay XML o fails, RED_FIX real; nunca afirmar PASS.

V2 productiva OFF, pagos OFF, Auto/Noche OFF, stock real OFF; no Android físico ni APIs pagas.
