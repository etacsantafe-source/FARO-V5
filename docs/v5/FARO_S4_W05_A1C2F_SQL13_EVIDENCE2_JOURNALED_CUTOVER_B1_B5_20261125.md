# FARO V5 · W05-A1C2F · SQL13 Evidence2 journaled cutover DEV

Predecesor: A1C2E B7 PASS scoped, código `6e800b3a96feeb52a16a35b238953bc12dd7171a`, checkpoint `96813106a86980aa92f380ecc33f53ad771668d2`, 22/22 JUnit XML, CI `45624005370` intento 2 SUCCESS, guardia `45625281214` SUCCESS. El predecesor solo admite **lectura** SQL13 ACTIVE DEV; NO activa SQL13. Esta auditoría NO equivale a cutover ejecutado, permiso READY o B7.

## B1 — Evidencia y dependencia exacta
- `FaroV5Household13V10StagedReplay.verifyForEvidence2Cutover` certificó autenticidad del archivo v10 original y equivalencia de cinco almacenes SQL13 STAGED. El v9 histórico permanece un perfil separado.
- `FaroV5Household13ActiveDevReadHost` y `FaroV5GenerationStoreGraph.openActiveHousehold13ForHeldDevHost` existen, WAL-aware, read-only y sin autorización de Apply.
- `FaroV5Household12Evidence2DevJournaledCutover` y su test (8 casos) aportan la secuencia PREPARING/STAGED/VALIDATED/SWITCH_PREPARED/SWITCHED/COMPLETED y ROLLBACK, ticket autenticado, root lease, pointer full-sequence, fingerprint de cinco archivos, replay fence y recuperación de interrupción.
- **Nuevo origen SQL12**: para permitir el siguiente cutover, exigir selección previa `FaroV5Household12Evidence2DevJournaledCutover.hasCompletedSelection(root, origin)`, no inferir permiso por existir una base SQL12 ni inventar estado ACTIVE.

## B2 — Reutilización V4.4 y V5
- V4.4 P02/P03 matrices R17: conservar invariantes AtomicApply, receipts, Undo y recovery ya traducidos a V5; no copiar código V4.4 ni recrear escrituras fuera del único coordinator.
- Reusar `ValidatedGenerationActivationCoordinator`, `GenerationSwitchCoordinator`, `FileBootstrapJournal`, `FileActiveGenerationPointer`, `FileGenerationReplayFence`, el patrón de SQL12 cutover y `FaroV5Household13V10StagedReplay`.
- Integración aislada y de lectura: `FaroV5Household13ActiveDevReadHost.reopen` solo bajo ticket COMPLETED de la unidad nueva. No agregar N-016 Undo ni mutaciones de stock aquí.

## B3 — Diseño decidido y alternativas rechazadas
- A: nuevo cutover **explícito** Evidence2 SQL12 ACTIVE → SQL13 ACTIVE para DEV aislado, con ticket de versión exclusiva `FARO_SQL13_EVIDENCE2_CUTOVER_V1`, archivo separado `bootstrap/sql13-evidence2-cutover.ticket`, lock de raíz único, fingerprint del ciphertext v10 y cinco archivos; requerir autorización foreground y verificación dos veces antes de publicar ticket.
- B RECHAZADA: instalar SQL13 sobre la base SQL12 ACTIVE o cambiar puntero por simple validación STAGED.
- C RECHAZADA: reutilizar el ticket SQL12 sin versión ni diferenciar el origen, abrir ACTIVE con `immutable=1`, confiar solo en ID sin secuencia (ABA), modificar historia o deducir consentimiento de la contraseña de backup.
- D: journal autoriza solo transición; recovery retoma estado seguro, reconoce el mismo COMMITTED sin segundo switch; falla en verificación de salud → ROLLBACK al SQL12 original con pointer incrementado y sin stock Apply.
- El stage SQL13 debe ser completo y sellado antes del ticket. El origen SQL12 debe tener ticket anterior COMPLETED y schema12+Evidence2 coherente, no meramente tabla SQL12.
- Nada de launcher Android, pagos, Apply productivo, stock real, Auto/Noche o una nueva API.

## B4 — Tres rutas exactas candidatas a congelar
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household13Evidence2DevJournaledCutover.kt` — nuevo cutover autónomo con autoridad y recovery
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household13Evidence2DevJournaledCutoverTest.kt` — tests JVM con SQLite real y origen SQL12 auténticamente COMPLETED
3. `.github/workflows/faro-v5-s4-w05-a1c2f-sql13-evidence2-cutover.yml` — CI/preflight exact3, regressions v10 y v12, JUnit XML/artefacto

Si durante B6 aparece una dependencia genuina en archivo adicional, **STOP**, auditar y emitir nuevo permiso exacto. No ampliar el scope sin autoridad.

## B5 — Matriz adversarial y aceptación pendiente
- T01: validar FBS2v10 de cinco owners, compra10, recepciones7+3+7, compensación7, estado histórico intacto y neto10; origen SQL12 ACTIVE con ticket COMPLETED legítimo.
- T02: VALIDATED solo mantiene puntero origen; foreground revocado deniega sin cambiarlo.
- T03: start autorizado conmuta full pointer exactamente una vez; COMPLETED y fence RESTORE verificados; cierre y reapertura del lector SQL13 muestran compensación persistente.
- T04: crash en SWITCH_PREPARED previo a publicar puntero se recupera sin duplicar transición.
- T05: crash después de pointer publicado, antes de avanzar journal, se recupera al mismo SQL13.
- T06: health fallido de SQL13 después del switch conduce a ROLLBACK verificado a SQL12 ACTIVE; ningún Apply autorizado.
- T07: ABA de puntero, lease revocado o ticket corrupto → BLOCKED; rehuso de archivo v9 o ciphertext diferente → BLOCKED.
- T08: divergencia de cualquiera de cinco almacenes / WAL o sidecar no verificable deniega start; no abre un writer productivo.
- T09: origen SQL12 sin ticket anterior COMPLETED o con otro selected → denegar autorización, incluso si existe schema12.
- CI: JUnit exactos, cero failures/errors/skipped, XML original por SHA, guardia siete bloques. Una CI roja obliga RED_FIX de esta unidad, no B7 ficticio.

**Estado**: B1–B5 diseño/preauditoría, sin resultados T01–T09. READY requiere guardia documental SUCCESS y permiso exact3 distinto de A1C2E. Física G15 NOT_RUN. V2 productiva, pagos, stock real, Apply productivo y Auto/Noche OFF.
