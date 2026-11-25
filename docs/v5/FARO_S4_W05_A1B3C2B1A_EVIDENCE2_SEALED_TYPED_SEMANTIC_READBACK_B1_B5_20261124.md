# FARO V5 — W05-A1B3C2B1A · Evidence2 sealed typed semantic READ_ONLY · B1–B5
Fecha de planificación 2026-11-24 (Argentina). Repositorio `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`.
Unidad `S4_W05_A1B3C2B1A_EVIDENCE2_SEALED_TYPED_SEMANTIC_READBACK_DEV`.

## B1 — Base, pruebas observadas y necesidad
Checkpoint entrante `8369c0da448f08035d07c83e0552bbda230dbdd6`, C2B0 B7 PASS scoped; guardia final `45518604102` SUCCESS y writer RELEASED epoch 1915. C2B0 código `957a67f9462c2d7a6126d0ca6f0bb8d4b00278ad`, CI `45517890891`, XML14/14 (C2B0 6/6, C0 Evidence1 8/8). C2A STAGED FBS2v8 Evidence2 SQL11 CI `45514567419`, XML16/16. No repetir nada.
Gap exacto: C2B0 valida el SQLite comercial schema2, tablas, integridad, ausencia de triggers y fingerprints de los archivos cerrados, PERO NO compara la semántica comercial de todas las filas del destino contra la fuente FBS2v8 autenticada; un adversario podría actualizar una oferta, recalcular el fingerprint tipado, mantener esquema íntegro y engañar al siguiente cutover. El owner histórico `CommercialEvidenceStore` abre SQLite writable y configura WAL; NO reutilizarlo para esta observación sellada.
Se exige igualdad completa de `CommercialEvidenceStagedTypedBackupSnapshot` autenticado con el destino comercial sellado: IDs, idempotency, payload fingerprints, observación, tiempos, ausencia física de términos (ABSENT), UNKNOWN explícito, KNOWN y todos los campos de condiciones/localidad/vigencia/presentación/promoción. El comparador no debe modificar bases ni permitir operación material.

## B2 — Reutilización y contraste con V4.4
Reusar las clases de `EvidenceBackupPort.kt` (CommercialEvidenceStagedTypedBackupRow/Snapshot), `CommercialEvidenceTypedTerms.kt` (fingerprint canónico), `FaroV5Household11DevCutoverPreflight` C2B0 (full pointer, pin, host, H10/H11R, cinco store), `FaroV5Household11V8Evidence2FullStageReplayTest` (fixture REAL) y `StagedSqlitePreflight` (read-only immutable SQLite). Lógica SQL de lectura existente en `CommercialEvidence.kt`: `readAllRecords` y `readTypedTerms`; evitar segunda implementación de modelo o downgrade de esquema.
V4.4 P02/P03 solo es referencia histórica; NO aporta validación Evidence2 ni habilita recuperación productiva. W1–W4, C1A–C1C, C2A, C2B0 intocados.

## B3 — Alternativas evaluadas
A ADOPTAR: puerto nuevo en `:evidence` para reconstrucción estricta de snapshot typed desde archivo SQLite STAGED sellado en lectura URI `mode=ro&immutable=1`, `SQLITE_OPEN_READONLY | SQLITE_OPEN_NOFOLLOW | SQLITE_OPEN_URI`, solo tras check de directorio físico canónico, archivo regular de un enlace, cero sidecars, versión exacta y DB sin triggers/integridad/foreign keys sanas. Leer LEFT JOIN comercial/terms con valor null si NO existe fila y struct UNKNOWN si existe, validar fingerprint de cada fila y crear snapshot canónico ordenado. Al cerrar repetir checks de sello. No abrir SQLite en módulo generation.
B ADOPTAR complementaria: preflight de generación separado invoca C2B0 schema2 bajo **único** quiescence, autentica FBS2v8 original, compara snapshot completo del archivo cerrado con el typed snapshot autenticado, ejecuta seam adversarial, vuelve a llamar C2B0 y repite comparación (pointer/host/pin/sello se verifican de nuevo).
C RECHAZADA: abrir `CommercialEvidenceStore` en modo normal, ya que puede generar WAL/SHM o migrar; es incompatible con READ_ONLY.
D RECHAZADA: fiarse del schema y hashes de registros sin contraste semántico; no prueba ausencia física ni promociones.
E POSPUESTA: ticket/journal durable, cutover, activation, recovery y Android (C2B1B/C2C), bajo protocolos independientes.
Referencias técnicas internas: `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/StagedSqlitePreflight.kt`, SQLite `https://sqlite.org/wal.html` y `https://sqlite.org/pragma.html` solo para motivación; el código y pruebas reales son fuente de autoridad.

## B4 — Alcance congelado de cuatro rutas
1. `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/SealedCommercialEvidence2SnapshotReader.kt`: lector estricto y sin escritura de la fila typed completa, schema2, fingerprint, FK e integridad.
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household11Evidence2SemanticPreflight.kt`: invocar C2B0 opt-in (sin cambiarlo), abrir FBS2v8 original con clave explícita, cotejar snapshot exacto del respaldo, revalidar después de seam.
3. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household11Evidence2SemanticPreflightTest.kt`: fixture C2B0 real, positivos ABSENT/UNKNOWN/KNOWN y adversariales de cambios comerciales con fingerprint regenerado, eliminación de fila terms, alteración de fingerprint, password/nonce, pointer ABA y host revocado.
4. `.github/workflows/faro-v5-s4-w05-a1b3c2b1a-semantic-readback.yml`: parent READY preflight exact4, Java21+SDK37, nueva suite (6 casos) más regresión C2B0 (6) y C0 (8), XML original total 20 y artefacto.
No alterar otros Kotlin, Graph, código C2A/C2B0/C1A/C1B/C1C, manifests, `main`, V4.4 ni rutas productivas.

## B5 — Matriz adversarial / GO
T01: archived Evidence2 FBS2v8 autenticado, SQL11 STAGED, ABSENT vs UNKNOWN vs KNOWN equivalentes en todas las columnas: PASS sin alteración de 5 SQLite/pointer/journal/ticket.
T02: modificar `conditions`/promoción y recalcular `terms_fingerprint` para que SQLite siga válida: comparación exacta con snapshot autenticado debe RECHAZAR.
T03: borrar términos UNKNOWN para convertirlos en ABSENT: RECHAZAR aun cuando FK e integridad pasen.
T04: manipular fingerprint tipado sin modificar datos: RECHAZAR; sidecars y triggers ya bloqueados C2B0.
T05: clave falsa, nonce diferente, archive no pin, host revocado, pointer ABA y mutación entre lectura y seam: RECHAZAR, sin publicar pointer.
T06: regresiones Evidence2 C2B0 6/6 y Evidence1 C0 8/8, más seis pruebas propias (total 20); cero failures/errors/skips, XML JUnit real, guard SUCCESS.
T07: ausencia de ticket, cutover, ACTIVE, stock real, Apply/pagos/V2 productiva/Auto/Noche. No habilitar C2B1B antes de B7 de este módulo.

**B1–B5 PREAUDITED; B6 NO EJECUTADO; B7 PENDING.** Gate de siete bloques, guard documental SUCCESS y permiso nuevo READY exact4 ANTES de código.
