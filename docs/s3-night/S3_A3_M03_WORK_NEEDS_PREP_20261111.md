# S3.A3 M03 — NEEDS_PREP: lookup autorizado REMOVED · 2026-11-11

## Evidencia y STOP
M02 DONE_VERIFIED código c04a19971f8d610f221c3739961f4e8b3a0c2b22, CI43029315832 XML12/12+APK y guard43029315790. M01 intacta. Esta revisión cumple la comprobación mecánica de dependencia/delta; no reabre investigación ya aprobada.

La preparación M03 S3_A3_M03_FOLDERS_LIFECYCLE_B1_B5_20261111.md (blob729e56f1a56c42c2801c5dcd3bbaba86e154754d), sección B5, ordena STOP si no existe lookup autorizado por ID de bookmark REMOVED. La cola mantiene restore_admission_gap y solo6 rutas propuestas de app/workflow.

En el SHA actual AuxiliaryBookmarkStore.read filtra lifecycle ACTIVE; reviewSnapshot M02 también devuelve solo target ACTIVE. persistedTarget(id,token) sigue privado y exclusivo de admisión mutante. FaroV5BookmarkReadHostBinding solo lee páginas ACTIVE. Ni Review M02 ni backup/portabilidad ofrecen el lookup autorizado de REMOVED necesario para Restaurar después de reabrir app. No simularlo con Stale, no sondear mediante mutación, ni ampliar rutas silenciosamente.

## Preparación delta requerida, acotada
Diseñar y auditar lectura host-only por ID exacto con revisión scope canónica y estado mínimo REMOVED, sobre la misma SQLite/ownerLock. Exigir grant de metadata+mensaje, Workspace source y generation/pointer/host fences antes/después, scope incompatible distinto de ausente, sin texto ni listado abierto de tombstones. Confirmar restauración mediante M01 con request exacto; CAS Stale requiere nuevo Review y consentimiento. No cambio de schema/contrato público ni catálogo.

Cotejar reutilización de snapshot M02 y M01 con B1-B5 M03. Enumerar rutas adicionales Auxiliary/host/tests exactas, pruebas de reopen→restore, source ausente/archivado, wrong ID/token/gen/target, revocación, ABA y stale. Emitir permiso documental separado y guardia/preflight antes de código. Esto describe la brecha y salida requerida; NO es auditoría B1-B5 completada ni permiso READY.

## Estado
M03 NEEDS_PREP; B6/B7 NOT_RUN;0 commits funcionales M03. M04 y M05 DEPENDENCY_BLOCKED. No se omiten restauración ni campaña G15 para declarar A3 CLOSED. El ejecutor se detiene aquí conforme al paquete maestro; preparar solo esta delta antes de reanudar programación.
