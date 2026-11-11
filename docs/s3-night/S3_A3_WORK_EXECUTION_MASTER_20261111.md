# FARO V5 — Paquete maestro de ejecución Work · S3.A3 · 2026-11-11

## ÚLTIMO CHECKPOINT · M03 REVISION MANUAL · 2026-11-11 · PREVALECE

**M01 B7 PASS** (`649d3f08ec7d6a14dea4869ad84603408dbee953`, 64/64) y **M02 B7 PASS** (`c04a19971f8d610f221c3739961f4e8b3a0c2b22`, 12/12 XML+APK). NO repetir estas unidades ni las pruebas antiguas sin delta pertinente.

**M03**: STOP anterior `AUTHORIZED_REMOVED_REFERENCE_LOOKUP_ABSENT` resuelto en B1–B5 DELTA `docs/s3-night/S3_A3_M03_REMOVED_LOOKUP_DELTA_B1_B5_20261111.md` blob `0e49d9b128945d232960c8b46a83facc20d16d2b`. Recuperar `REMOVED` exige (a) lookup autorizado por ID y (b) vista opcional paginada de quitados tras reabrir app, sin filtrar textos ni introducir tombstones en lectura ACTIVE. Permiso M03: **13 rutas**, emítase en commit DOCUMENTAL POSTERIOR a esta preauditoría, solo READY tras validar guard y preflight. Work luego programa B6, CI/XML+APK y B7. M04–M05 dependientes, sin permiso.

**CONTROL DE HISTORIAL**: las menciones posteriores en este documento a que M01/M02 faltan son registros de su fecha original; nunca deben prevalecer sobre puntero, cola y permiso ACTUAL. Writer exclusivo CAS. V2 productiva/S4/Auto/Noche OFF; G15 físico al final.

---

## CORRECCIÓN OPERATIVA MÁS RECIENTE · 2026-11-11 · PRIORIDAD SOBRE ESTADO HISTÓRICO

**M01 TERMINADA, B7 PASS** en código `649d3f08ec7d6a14dea4869ad84603408dbee953`, CI `43016377617` 64/64 XML y APK DEV, guard `43016377548`. NO volver a programar M01.

**M02 bloqueo de revisión global resuelto en diseño B1–B5**: leer antes del Review el `bookmark_scope.revision` canónico Auxiliary con gate de lectura técnica y source Workspace; no consultar backup, extrapolar de item.revision, ni usar `Stale` de una mutación como preflight. Evidencia delta `docs/s3-night/S3_A3_M02_REVISION_PREFLIGHT_DELTA_B1_B5_20261111.md` blob `839aacce48140cce75e49d35804c62bf83d84985`. M02 requiere scope TOTAL 16 rutas y permiso individual separado del commit de esta auditoría. La cola JSON y el permiso vigente son la autoridad actual. Una vez emitido READY por commit documental+guardia, Work debe **desarrollar B6/CI/B7 de M02**. M03–M05 aún no autorizadas.

**ALCANCE DELTA:** proteger `scope=null` como revisión0 solo cuando ACL/generación válidos, distinguir incompatible/unavailable, lectura sin escribir, preparar preview tras click usuario, nueva mutación exacta tras confirmar, Stale sin reintento. Tests de auxiliar y app. Sin alterar catálogo180 ni contratos B7.

---

Propósito: Work debe dedicar su sesión al desarrollo B6, CI y B7; toda investigación previa B1–B5, contratos, alternativas, auditoría y casos negativos quedan aquí vinculados. Este archivo es una cola DOCUMENTAL, no una licencia para programar todos los paquetes de una vez.

## 1. Estado verificado al preparar

Repositorio etacsantafe-source/FARO-V5; branch work/s3-night-20261102; HEAD base 00bd1f5dd1833bcea136cc4a50219ede08000fd0; writer RELEASED epoch694 ANTES de publicar esta preparación; al ejecutar, consultar de nuevo. M01 tiene permiso READY, run de preflight 42940764338 SUCCESS en 00bd1f5dd1833bcea136cc4a50219ede08000fd0. Únicamente M01 puede comenzar B6 hoy con nuevo writer CAS y protocolo.

Certificados que NO repetir sin delta: contratos 8/8 run 42720699353, Aux CAS/receipts run 42729311671, mutación gate run42923566608, catálogo180 run42927956001, lector nativo DEV19/19+APK run42935422794, protocolo run42935422815. S3.A3 continúa OPEN.

## 2. Secuencia de unidades en orden obligatorio

M01 S3_A3_BOOKMARKS_TRUSTED_MUTATION_HOST_DEV — paquete existente READY, implementar host trusted DEV sin montar UI; probar SQL+ACL+grant+APK, luego B7.

M02 S3_A3_BOOKMARKS_CHAT_REVIEW_DEV — preparar y cablear gesto explícito por mensaje de Chat + confirmación + Auxiliary receipt, SIN Reality Apply. Investigación S3_A3_M02_CHAT_REVIEW_B1_B5_20261111.md blob 955155399d7c32c5e7caeade33d2e4c0ae7840da. Estado PREAUDITED_DEPENDENCY, no permiso.

M03 S3_A3_BOOKMARKS_FOLDERS_LIFECYCLE_DEV — UI DEV carpetas/ciclo de vida/restore y CAS contra owner; investigar lookup REMOVED antes de activar restore. Investigación S3_A3_M03_FOLDERS_LIFECYCLE_B1_B5_20261111.md blob 729e56f1a56c42c2801c5dcd3bbaba86e154754d. Estado PREAUDITED_DEPENDENCY.

M04 S3_A3_BOOKMARKS_EXACT_SOURCE_NAV_DEV — Guardados a conversationId+messageId exactos, selección Workspace fresca, ACL+generation+epoch y foco visible, sin deep-link público ni copia de texto. Investigación S3_A3_M04_EXACT_SOURCE_NAV_B1_B5_20261111.md blob 97be894856ea585d46869881c4c46557eb2631a6. Estado PREAUDITED_DEPENDENCY.

M05 S3_A3_BOOKMARKS_AUTOMATED_ACCEPTANCE — matriz A3-01…A3-12 (12 conjuntos de escenarios, no número de tests), CI real+APK y B7, solo READY_FOR_FINAL_PHYSICAL_CAMPAIGN. Investigación S3_A3_M05_INTEGRATED_ACCEPTANCE_B1_B5_20261111.md blob f293a902fc03b1070313021b1a82a33571b4831c. Estado PREAUDITED_DEPENDENCY.

## 3. Contrato executor-only para Work

Al comenzar: leer AGENTS.md, docs/v5/FARO_PREPARACION_EJECUCION_HANDOFF_V1_20261110.md, docs/v5/FARO_PROTOCOL_WORK_PERMIT.json, docs/v5/FARO_CONTINUITY_POINTER.json, este maestro y JSON S3_A3_WORK_EXECUTION_QUEUE_20261111.json; verificar HEAD exacto/lock real y permiso. Ejecutar primero M01, sin rehacer B1–B5 salvo delta concreto. Reclamar writer CAS, hacer preflight de TODOS los paths exactos permitidos; escribir exclusivamente esos paths con FARO-UNIT:UNIT_ID, CI de XML y APK real al SHA; auditoría B7 independiente; publicar checkpoint y liberar writer. Si no hay B7 PASS, STOP. Para M02–M05 solo promover permiso READY tras B7 predecesor y cotejo del delta B1–B5: acto DOCUMENTAL mínimo, separado del commit funcional, bajo CAS; no hacer investigación exhaustiva de cero. Cualquier cambio de owner/contrato fuera de documento => NEEDS_PREP, NO editar por impulso. Preflight exacto es vinculante incluso si el paquete fue preparado.

Este mecanismo impide un permiso global unsafe: solamente una unidad READY por vez. Work puede hacer el trámite de permiso como parte de su flujo de ejecución o devolver NEEDS_PREP para que lo haga el preparador; no debe inventar auditoría ni permisos.

## 4. Inmutables de seguridad

Un writer único para código y documentación; main y V4.4 intocables; V2 productiva, S4, Auto y Noche OFF; sin APIs pagas; catálogo 180 no cambia; Reality Apply/COMMITTED/Undo aislados; Auxiliary owns saved item/receipt, Workspace owns source, Evidence!=Reality; grants de lectura y mutación separados; App DEV aislada. Teléfono G15 solo campaña final.

## 5. Riesgos adversariales prevalentes

R1 MUTATION grant action-scoped: si gate M01 acepta cualquier request por token genérico, NO habilitar mutación. R2 restore de REMOVED ocultos: no inventar listado ni nuevo port sin delta B5. R3 abrir Guardado no equivale a focus: verificar conversación y mensaje exactos en nuevo snapshot, no chat cacheado. R4 callbacks tras revoke/ABA crash no publican éxito; idempotencia+receipt real; N05 CI verde no sustituye físico.

## 6. Estado y métricas

Registrar por unidad: start/end SHA, epoch, paths, validación B1-B5 reutilizada o delta, código, B7, CI run+XML+APK, causas RED_FIX, tiempo/tokens solo si observables. Checkpoint final por unidad, writer RELEASED obligatorio. Después M05 y pruebas automatizadas no declarar A3 CLOSED ni S3 cierre sin campaña física pactada.

## 7. Investigación B3 externa ya verificada

Android https://developer.android.com/topic/architecture/recommendations; Slack Saved https://slack.com/help/articles/360042650274-Save-messages-and-files-for-later; SQLite https://www.sqlite.org/lang_transaction.html. Se eligió flujo UI intent→host→owner y fuente de verdad Workspace/Auxiliary, no framework nuevo ni código V4.4. Reusar resultados históricos y SHA listados en las cuatro fichas. Investigación no significa ejecución verificada.

## 8. Corrección adversarial final 2026-11-11 — prevalece sobre brechas previas
Ver docs/s3-night/S3_A3_FINAL_AUDIT_M01_M05_B1_B5_DELTA_20261111.md, prueba SHA completa. **M01** debe incluir AuxiliaryBookmarkStore.kt y AuxiliaryBookmarkMutationAuthorityTest.kt en el permiso: isMutationActive(generation,token) no conoce target ni operación y, en el código actual, operaciones existentes no consultan isTargetAvailable. Requiere admission request-bound bajo transacción y ACL fuente donde aplique, y tests de revocación/otros IDs. Mantener compatibilidad con tests anteriores; nunca ampliar grants por ser una simple boolean. **M03** requiere lookup autorizado de REMOVED por ID, pues lector ACTIVE los excluye: STOP/DELTA si se necesita nueva ruta fuera de permiso. **M04** debe unir la vista nativa Guardados (Activity setContentView) con SurfaceHost Compose usando Session canónica, no asumir mismo back stack, ni reutilizar searchConfirmedTarget. **CI** nueva en rama requiere on:push con branch y paths completos para que el workflow dirigido se ejecute; workflow_dispatch por sí solo requiere workflow en default branch, y main no puede tocarse. **AGENTS** alineado a catálogo180/8 aprobados. No retirar B5 ni preflight.
