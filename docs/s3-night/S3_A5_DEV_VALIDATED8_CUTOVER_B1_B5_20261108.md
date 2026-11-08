# FARO V5 — S3.A5 — DEV VALIDATED Reality8 → canonical cutover/recovery — B1–B5
Fecha 2026-11-08 AR. Unidad `S3_A5_DEV_VALIDATED8_CUTOVER`. Base `f050832965629db73d66f24101bef2e0a300ca16`. Writer epoch593. V2 productiva OFF, G15 al final.

## B1 — Brecha / evidencia vigente
- A5 tres subunidades certificadas: active8 host 6/6, fresh STAGED8 5/5, DEV stage-journal `8e1cdac4` 5/5 + B7, todas PASS_SCOPED. La tercera produce **de verdad** `BootstrapJournalEntry RESTORE/VALIDATED` tras stage fresco Reality8 con cinco SQLite, bajo quiescence inyectada y puntero anterior ID+sequence exacto. No promueve ACTIVE ni instala fence.
- N033C2A 17/17 ya certificó el `ValidatedGenerationActivationCoordinator` y `GenerationSwitchCoordinator` con Reality8 en fixture + journal, CAS, crash y rollback. **No reproducir switcher ni crear segundo journal**. Falta el puente DEV real que consuma el nuevo producer VALIDATED, use la MISMA barrera y abra únicamente Reality8 activa exacta tras switch. V2 DEV Android sigue abriendo Reality7; futuro montaje es otra subunidad.
- `FaroV5V2CoreDevAuthority.open()` verifica al inicio operación schema7 histórica. No modificar esa ruta hasta existir adaptador cutover con pruebas exactas. No abrir S4 ni shipping.

## B2 — Patrón existente / frontera con V4.4
- V4.4 solo referencia de semántica funcional; no copiar datos ni actualizar repos legacy. V5 durable pointer, journal y replay fence son autoridades exclusivas.
- Componer `FileBootstrapJournal`, `FileActiveGenerationPointer`, `FileStorageGenerationLayout`, `GenerationSwitchCoordinator`, `ValidatedGenerationActivationCoordinator`, `FileGenerationReplayFence` y `FaroV5GenerationStoreGraph.openActivePlanning8ForCertifiedHost` sin modificar sus implementaciones certificadas. La generación previa DEV es V2 Reality7 bajo mismo `MaterialWriteGate`.
- Orden: VALIDATED real → admisión previa STAGED8 exacto bajo lease → SWITCH_PREPARED → CAS pointer y recuperación crash → health active8 → replay fence → reconcilación sin OS effects → COMMITTED/COMPLETED, jamás antes. En fallo health y antes de material writes, rollback a Reality7, sin borrar target. La posterior Activity necesitará revocar sesiones previas por secuencia, no basta ID.

## B3 — Ingeniería externa y alternativas
- SQLite WAL https://www.sqlite.org/wal.html y atomic commit https://www.sqlite.org/atomiccommit.html : independencia de cada DB; un `COMMIT` SQLite no es `COMMIT` del puntero+cinco stores, por lo que se adopta coordinator durable a nivel generación (**ADAPTAR**). Riesgo: crash entre pointer y journal.
- SQLite isolation https://www.sqlite.org/isolation.html : escritor único por archivo no garantiza atomicidad conjunta de los cinco owners (**USAR gate global**).
- Android offline-first https://developer.android.com/topic/architecture/data-layer/offline-first y ejemplo real Now in Android https://github.com/android/nowinandroid/blob/main/docs/ArchitectureLearningJourney.md : fuente local canónica por repositorio, host/UI no debe crear autoridad alterna; proyecto oficial Apache-2.0, solo patrón (**REFERENCIA**).
- OWASP Authorization https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html : puntero y host certified no son un grant de objeto ni de persona; Activity/ACL se auditan después (**ADAPTAR**).
- Alternativa A **ADOPTAR**: adaptador pequeño, generación interna, mismo coordinator, sin OS reconciliation. B **DESCARTAR**: switcher paralelo por schema8. C **DESCARTAR**: promoción automática al arrancar Chat/IA, por sorpresa y sesiones stale. D **DESCARTAR**: migrar activo7 in-place o fallback destructivo. No añadir paquetes externos ni APIs pagas.

## B4 — B6 mínimo, tres rutas exactas
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Planning8DevCutoverOwner.kt`: constructor por host con `MaterialWriteGate`, `MaterialWriteQuiescencePort` DEL MISMO HOST, plan verifier y certificado explícito; `activateValidated` y `recover` que delegan al coordinator canónico, verifican journal/descriptor/puntero ID+sequence y replay fence. `projections` es no-op explícito.
2. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Planning8DevCutoverOwnerT2Test.kt`: **4 nuevos tests integrados**; producer A5 journal real + promoción Reality7→8 + replay; crash después de pointer con `recover`; health 8 fallida con rollback a 7; stale pointer ABA/uncertified denied antes de switch.
3. `.github/workflows/faro-v5-s3-a5-dev-validated8-cutover-directed.yml`: suite JVM única, XML exacto 4/4, guard B1–B7. Sin reejecutar 17/17 C2A ni 5/5 preparación.
- Cero cambios previos en Activity/manifest/surface y cero pruebas físicas. No fusionar con otras unidades.

## B5 — Casos hostiles y STOP
- `hostCertified=false`, op distinto de RESTORE, target distinto, estado anterior a VALIDATED, manifest/descriptor no canónico, pointer previo diferente aunque mismo ID, journal ajeno/pending concurrente o ACTIVE8 de secuencia no esperada → fail-closed, sin transición de journal/pointer.
- Si `coordinator.activate` falla después de publicar pointer, el siguiente `recover` debe aceptar solo `target.sequence = previous.sequence + 1` con journal coincidente; nunca restaurar token/grant anterior ni ejecutar side effect repetido. Un rollback correcto recupera previous con `sequence = previous.sequence + 2`; no admite escritura target.
- `COMPLETED` exige journal materialWritesAccepted, replay fence existente y pointer correcto; el método del cutover no concede permiso de lectura humana ni expone mutable graph al model/chat. El caller futuro debe cerrar Session Reality7 y usar un único gate.
- Este B7 solo puede cerrar `PASS_SCOPED_VALIDATED8_REAL_JOURNAL_TO_CUTOVER` si el build, JUnit XML, guard, SHA, rutas y revisión independendiente son verdes. A5 continúa OPEN hasta DEV host/UI + campaña física final. 
