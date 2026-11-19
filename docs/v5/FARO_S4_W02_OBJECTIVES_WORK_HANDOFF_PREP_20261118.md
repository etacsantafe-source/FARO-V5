# FARO V5 — W02 Objetivos: preparación documental y handoff a Work

Fecha 2026-11-18 | Rama work/s3-night-20261102 | HEAD auditado 122c0b6c86ca25f6bce73fde4e41b02eca85e896
Autorización humana: «Autorizo. Preparemos el camino y delegamos a work».
TIPO: DOC_ONLY / PREPARED. W01 B7 GLOBAL PASS DEV, SHA de código 6b4bf55ad002e50287fcad5616fcc5c6d6b72f0f, NO REPETIR. W03 STOP, V2 productiva OFF, main y V4.4 intocables.
Este documento NO acredita B1–B5 finales por unidad, NO emite READY y NO permite todavía B6. El permiso funcional de W01 sigue BLOCKED; Work debe completar B1–B5 específicos y emitir READY de rutas exactas en commit solamente documental luego de guard verde.

## B1 — Estado real y brechas
- v5/owner/objectives/src/main/kotlin/ar/com/faro/v5/owner/objectives/api/ObjectiveModel.kt (blob 23534407f37d4c6d27523bfd4f3c859b9728757e): ObjectiveId, ObjectiveTitle, ObjectiveDetail, ObjectiveStatus (PLANNED/ACTIVE/PAUSED/COMPLETED/ARCHIVED), ObjectiveRecord(id,title,detail,status,updatedAtEpochMillis), ObjectiveSnapshot(revision,objetivos). No incluye métrica, fecha, prioridad, eventos, próximos pasos ni historial.
- v5/owner/objectives/src/main/kotlin/ar/com/faro/v5/owner/objectives/spi/ObjectivePersistencePort.kt (blob 8393d043cf7f551c8132f5ce48152be44528c201): read/upsert bajo RealityTransactionScope, NO es host de permiso ni Review/Apply.
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt: tablas objective_state y objective_record, revisionado, upsert transaccional con updatedAt monotónico, backup de dominio. Las pruebas v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityDomainOwnersT2Test.kt ya verifican persistencia tras reopen, upsert repetido/stale, rollback, scope ajeno, migración y backup en su alcance original.
- ObjectiveModelTest.kt prueba invariantes tipados; FaroV5ObjectivesSearchReadSource.kt usa generation, epoch ACL y revision para búsquedas autorizadas. NO confundir ninguno con CRUD manual/Android o permiso para aplicar.
- P05: docs/v5/FARO_S4_P05_FUNCTIONAL_PRODUCT_SPEC_20261113.md O01 alta (nombre, finalidad, métrica opcional, fecha, prioridad), O02 progreso real e historial, O03 pasos y referencias con grant; PF12–PF14. Plan P08: docs/v5/FARO_S4_P08_W00_W11_B1_B5_20261115.md § W02. Todavía NO hay editor ordinario Objetivos B7 ni integración Android W02 confirmada.

## B2 — Recuperar lo útil sin duplicar
- V4.4 L07, documentado en docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md: cartera, progreso REAL, plan, dependencias, filtros, historial y acciones tipadas. Archivos legado objectives/FaroV4ObjectivesSurface.kt @9642cc98; objectives/FaroV4ObjectivesActions.kt @cac4e773. El contraste de flujo real debe completarse en preauditoría de cada unidad sin modificar V4.4.
- P03 R07 (docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md): REIMPLEMENTAR operación manual offline completa, adaptar experiencia legacy sin copiar DB ni declarar garantía inexistente.
- Reutilizar kernel ApplyPlan/review/receipt, RealityTransactionScope y restauración existente, Workspace, session/scope DEV y UI accesible de W01, sin acoplar Objective a la cuenta Finance ni abrir otro SQLite.

## B3 — Alternativas externas contrastadas (referencias, no copiar código)
- Tasks.org https://github.com/tasks/tasks y https://tasks.org/docs/sync/ : tareas, subtareas y trabajo offline; REFERENCIA útil para pasos. GPL-3.0, no copiar fuentes ni adoptar sincronización/nube.
- Loop Habit Tracker https://github.com/iSoron/uhabits : historial y progreso local; REFERENCIA para trazabilidad, DESCARTAR rachas obligatorias en objetivos no recurrentes. GPL-3.0, no copiar fuentes.
- Android Developers https://developer.android.com/topic/architecture/data-layer/offline-first : base local fuente de verdad; ADAPTAR criterio, DESCARTAR infraestructura de sincronización/WorkManager sin necesidad.
- ALTERNATIVA ELEGIDA: contrato tipado con progreso manual, revisión humana y Apply/receipt local; DESCARTAR IA que autocierre, edición SQLite directa, backend de terceros y sync innecesaria.
- Work revalidará mantenimiento/licencias, riesgos, links y alternativas por unidad en su B3; documentación presente no equivale a permiso B6.

## B4 — Ejecución encadenada (límites funcionales)
W02-A: ampliar contrato persistente de Objetivos con métrica opcional, fecha, prioridad y registro de eventos. Diseñar migración compatible y backup/restore sin borrar registros legados. B1–B5+permiso propio antes de B6.
W02-B: alta y edición manual con preview -> Review -> ApplyPlan -> Reality, grant revocable, receipt, idempotencia, cancelación inocua, una sesión.
W02-C: avances, correcciones y estados con historial durable/versionado. NUNCA convertir texto sugerido por IA en progreso REAL; porcentaje sólo con numerador/denominador válido.
W02-D: próximos pasos, dependencias y referencias Finance/Matter expresas, con grants independientes, degradación parcial/UNKNOWN al negar o revocar.
W02-E: UI Android DEV aislada desde Chat/drawer, listado/detalle/alta/progreso/edición/historial, fuente 200%, etiquetas accesibles; aplicar sólo en primer plano con autorización válida.
W02-F: CI por SHA + JUnit XML real + guard seven-block-evidence + regresión dirigida + B7 GLOBAL. Cierre sólo DEV; Moto G15 NOT_RUN hasta autorización física separada.
Encadenar sin esperas artificiales, sin reabrir W01; si guard roja => STOP y RED_FIX acotado. A cada cambio funcional exige FARO-UNIT:<unidad>, preflight exacto, writer CAS, checkpoint y writer RELEASED.

## B5 — Casos adversariales candidatos (diseñados, NOT_RUN)
PF12: nombre vacío; meta cualitativa no recibe porcentaje ni monto 0; fecha/importe inválidos no hacen upsert.
PF13: preview y descarte no mutan; doble confirmación = una sola mutación/receipt; stale revision/generation/ACL = STOP; corrección no borra evento anterior.
PF14: vínculo a Finance/Matter revocado no expone dato ni inventa capacidad de ahorro; objetivo monetario jamás es cuenta, pago o autorización de Finance.
Migración: viejo ObjectiveRecord intacto; schema upgrade repetido idempotente; backup/restore conserva revision y eventos; fallo segundo efecto => rollback.
Android: Home/background/kill/reapertura no reutiliza grant; recibo persistido y recuperación sin segundo Apply; fuente 200% y TalkBack accesibles.
Protecciones: Evidence ≠ Reality, sin Internet/API paga, no dinero real, Auto/Noche OFF, catálogo 180 y W01 congelados.

## Contrato de entrega para Work
1. Leer AGENTS.md, docs/v5/FARO_CONTINUITY_POINTER.json y docs/v5/FARO_PROTOCOL_WORK_PERMIT.json; revalidar HEAD, CI y writer GitHub real. Si writer ajeno HELD, sólo READ_ONLY y NO TAKEOVER.
2. Auditar primero W02-A B1–B5 de modo concreto y adversarial, documentar permisos de rutas exactas, publicar permiso READY en commit DOC_ONLY y verificar la guardia. NO heredar el permiso W01.
3. Programar B6 con writer exclusivo, CI exacta (JVM/Android cuando aplique) y evidencia XML; B7 independiente. Liberar writer y continuar con W02-B/C/D/E/F sin repetir B7 de lo cerrado.
4. Registrar por unidad SHA, rutas, permiso, test/run, resultados XML, estado de writer, checkpoint y siguiente. Cerrar W02 sólo con B7 GLOBAL verificado; si no hay créditos, checkpoint durable y RELEASED.
5. Prohibido modificar main, V4.4, W01, W03+, V2 productiva, Auto/Noche, secretos y G15 físico.

ESTADO DEL DOCUMENTO: W02-PREPARED_DOC_ONLY. CI W02 NOT_RUN, funcionalidad B6 NOT_RUN, permiso de código BLOCKED.
