# FARO V5 — M2A_FIRST_START_RECOVERY: B1–B5 preauditoría acotada (04/11/2026)

**Unidad:** `M2A_FIRST_START_RECOVERY`. **Rama:** `work/s3-night-20261102`. **HEAD base revisado:** `1c37928c94f97c32a478d2afd1a19436edd39636`. **Estado:** B1–B5 documentados para delta mínimo DEV; B6 y B7 NO ejecutados en este checkpoint. La autorización humana es solo V2 DEV aislada; no activar V2 shipping/IA/Apply/OS, Noche, Auto, S4 ni modificar main/V4.4/S2.

## B1 EVIDENCIA
- `FaroV5V2CoreDevAuthority.kt` blob `1cc07c49ace4d5f7955dfb6cf178a55b9a9a4caa` líneas 73–137: con puntero `initialId`, `journal.pending().isEmpty()` bloquea el siguiente arranque si hubo interrupción PREPARING/STAGED/VALIDATED; el ramal `targetId` solo recupera estados desde VALIDATED. Nuevo inicio intenta `stageWorkspaceV2` y crea cinco SQLite antes de avanzar PREPARING→STAGED→VALIDATED.
- `FaroV5V2CoreDevAuthorityTest.kt` blob `84f38cd16efe4a2cb9d43d6f22a08b3ce4c3674b`: cuatro tests actuales cubren activación, persistencia Chat, rechazo de Workspace corrompido; **ninguno** inyecta cortes en las tres fases solicitadas.
- `BootstrapLifecycle.kt`: PREPARING→STAGED→VALIDATED→SWITCH_PREPARED; ABORTED_PRE_SWITCH es terminal. `ValidatedGenerationActivation.kt` 198–222 y `GenerationSwitchCoordinator.kt`: recuperación desde VALIDATED solo con puntero anterior, stage íntegro, host admission, CAS y quiescence. No hace falta nuevo switch.
- `FileStorageGenerationLayout.kt` 68–147: `stageWorkspaceV2` exige dir nuevo; `resumeWorkspaceV2` solo admite archivos de marcador/descriptor, nunca SQLite parcial/symlinks; marcador inmutable antes de descriptor.
- CI conservada: `41884102154` SUCCESS (M2/M3 JVM 4/4; T4 debug 7/7; M1/M2B isolation 4/4 cada uno), guard `41885642662` SUCCESS. No equivalen a prueba de recuperación M2A. Puntero V1 y app habitual fuera del root DEV.

## B2 COMPARACIÓN
- **V4.4 solo lectura:** `etacsantafe-source/FARO@44f1e6eb`, `app-v4/.../history/FaroV4ApplyStartupRecovery.kt` blob `b6599c4fce8f97139b8084f923a9277837122f6d`: PREPARED obliga a restaurar antes de reconciliar, COMMITTED verifica identidad de receipt antes de reflejar en Chat; no inventa estado. Es un precedente de recuperación según fase **pero es Apply, no la autoridad de generaciones V2**.
- `app-v4/.../conversation/FaroV4ConversationLifecycleRecovery.kt` blob `bc27aa1160af6f4dc899070b545fdef7278c83ef`: solo recupera selección si existe la conversación preferida. V5 ya implementa persistencia del Chat DEV, no copiar controller.
- **V5 reutilizable:** journal durable con CAS, pointer CAS, replay fence, `resumeWorkspaceV2`, `GenerationStoreGraph.openValidatedV2WithSharedGate`, `ValidatedGenerationActivationCoordinator.recover`. No duplicar recovery genérico, owner o base SQLite.

## B3 INVESTIGACIÓN
- **A — borrar stage/journal y volver a empezar:** DESCARTAR. Puede perder WAL o adoptar bases parciales, vulnera trazabilidad y no es idempotente.
- **B — abrir todo stage existente automáticamente:** DESCARTAR. `resumeWorkspaceV2` rechaza SQLite y material extraño deliberadamente; un stage parcial no implica consistencia.
- **C — reconstruir solo pre-SQLite inequívoco y revalidar stage completo:** **ADAPTAR / ELEGIDO**, reutilizando marcador/descriptor durable, 5-owner validation y coordinador existentes. Para PREPARING sin stage, iniciar stage una vez; para PREPARING con solo marcador/descriptor, `resumeWorkspaceV2`; para STAGED, NO crear stores, solo validar cinco existentes; para VALIDATED, delegar en `coordinator.recover` con puntero V1 exacto. Desconocidos/corrupción/operación ajena => STOP.
- **D — nueva librería, Room migrator, WorkManager o nuevo recovery actor:** SIN CANDIDATO MATERIAL para DEV local offline. Sumaría autoridad sin resolver el journal actual.
- Referencias externas contrastadas: https://www.sqlite.org/wal.html y https://www.sqlite.org/howtocorrupt.html (WAL/hot journal son parte de estado persistente, jamás separar/eliminar por limpiar crash); https://developer.android.com/reference/android/content/Context.html#getNoBackupFilesDir() (root aislado del host DEV no respaldado). Sin API paga ni dependencia añadida.
- **Hipótesis falsable:** preestado no ambiguo reanuda sin escribir V1; un stage con SQLite parcial, journal extraño, puntero objetivo inesperado o symlink jamás se adopta, borra ni promueve.

## B4 UNIFICACIÓN
- **Único código funcional permitido:** `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthority.kt`.
- **Único test nuevo/ajuste permitido:** `v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthorityTest.kt`.
- Modificar exclusivamente entrada `initialId` y factorizar la validación/creación **sin tocar** coordinadores de mantenimiento, marker, gráficos ni source-sets shipping. Reusar `operationId`, descriptor inmutable, los cinco stores y `SingleProcessMaterialWriteGate` actual; no inventar ACL ni abrir Search.
- Elegir no recuperación silenciosa si existen SQLite parcialmente abiertos antes de STAGED. Un PREPARING indeterminado puede terminar BLOCKED hasta futura recuperación explícita; no es cierre completo de todos los crashes.
- Pruebas dirigidas de aceptación: (P0) PREPARING sin stage recupera; (P1) PREPARING con marcador/descriptor sin SQLite recupera; (P2) PREPARING con SQLite parcial bloquea y preserva bytes; (S0) STAGED con cinco stores válidos recupera; (S1) STAGED incompleto bloquea sin crear bases; (V0) VALIDATED + puntero V1 se activa y fence completo; (V1) VALIDATED + puntero distinto bloquea; (F0) journal ajeno y symlink se rechazan. Todos verifican V1 intacta y no acceso a OS/IA/Apply.
- Solo el CI del commit funcional exacto puede certificar; tests nuevos deben reportar XML concreto. No exigir repetición de CI S3/S2 sin cambio de esos contratos.

## B5 AUDITORÍA PREVIA ADVERSARIAL
1. **Crash y reinicio:** el journal se escribe ANTES de crear stage y SQLite. Recovery no debe marcar STAGED/VALIDATED sin evidencia de la fase. Rechazo conservador ante directorio sin marcador, temp, WAL suelto, fichero inesperado o symlink.
2. **Autoridad:** validar un `pending` único, misma `operationId` / RESTORE / `initialId` / `targetId` y `materialWritesAccepted=false`; otros pending/identidad distinta denegados; puntero debe seguir en V1 durante PREPARING/STAGED/VALIDATED.
3. **No sobreescritura:** si SQLite existe en PREPARING, no `stageWorkspaceV2`, no abrir store, no borrar archivo; STAGED requiere validación en vez de inicialización. Ningún pointer publish hasta VALIDATED y host canOpen true.
4. **Rollback/quiescence:** conservar `ValidatedGenerationActivationCoordinator` y `GenerationSwitchCoordinator` CAS; nunca auto-rollback tras `materialWritesAccepted`. Fallar cerrado si health/replay/projection/pointer no convergen.
5. **Aislamiento y egress:** solo código `v2CoreDev`, root exclusivo sin backup; ni IA real, HTTP, Apply, Delivery, OS, Search ACL, ni permisos globales. S2 8/8 congelada, S3 0/5 cerrada, catálogo 172.
6. **Alcance residual:** no probar crash kill real Android ni interproceso solo con JVM; requerirá M4/M5 y campaña física consolidada. El test V1 intacta no equivale a certificación de dispositivo.
7. **Control de cambios:** documento B1–B5 en commit documental; después permiso independiente con blob SHA exacto y READY. Preflight local y guard `seven-block-evidence` PASS antes de código; writer CAS global; commit funcional `FARO-UNIT:M2A_FIRST_START_RECOVERY`; B7 tras CI. Sin permiso => STOP.

## B6 IMPLEMENTACIÓN + CI
**PENDING.** No inferir PASS por diseñar casos.

## B7 AUDITORÍA FINAL
**PENDING.** No declarar `M2A_DONE_VERIFIED` ni `V2_DEV_CORE_READY` hasta CI exacta, revisión independiente, CHECKPOINT y writer RELEASED.
