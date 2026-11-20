# FARO V5 — Handoff Work Turbo para terminar W04-B0
Fecha local 2026-11-20 (Argentina). BASELINE 768c56824d04b051d323988aa2129461d8d16dd6. PUBLICACIÓN DOC_ONLY. Este documento no es un permiso funcional ni un test PASS. Prioridad: TERMINAR B0, SIN REHACER W1/W2/W3 o W04-A/B0A/B0B/B0C1.

## Recuperación obligatoria
Repositorio etacsantafe-source/FARO-V5 rama work/s3-night-20261102. Antes de escribir leer AGENTS.md, puntero, protocolo restitución B1–B7, docs/v5/FARO_PROTOCOL_WORK_PERMIT.json y:
- docs/v5/FARO_S4_W04_B0C2_HOUSEHOLD10_AUTHENTICATED_ARCHIVE_B1_B5_20261119.md
- docs/v5/FARO_S4_W04_B0C3A_HOUSEHOLD10_SQL_SNAPSHOT_B1_B5_20261120.md
- docs/v5/FARO_S4_W04_B0C3B_HOUSEHOLD10_COMPLETE_ARCHIVE_B1_B5_20261120.md
- docs/v5/FARO_S4_W04_B0D_HOUSEHOLD10_CUTOVER_RECOVERY_B1_B5_20261120.md
Revalidar HEAD, writer CAS remoto, guardias/CI, permisos y rutas. No confiar en estado histórico del pointer cuando el code SHA sea posterior.

## Secuencia estricta sin volver a ejecutar pruebas pasadas
0) B0C2: último code SHA 768c56824d04b051d323988aa2129461d8d16dd6, CI 44668249894 SUCCESS/XML 5/5, guard 44668249799 SUCCESS; B7 AÚN PENDIENTE. Hacer auditoría independiente de diff/artifact/XML/replay, publicar B7 y consumir READY→BLOCKED con writer global adquirido y liberado. No modificar código si los resultados ya pasan; no volver a correr por inercia.
1) B0C3A: utilizar documento propio B1–B5; después B0C2 B7, verificar SHAs/rutas y guardia documental exacta. Emitir READY SOLO por 4 rutas auditadas en commit DOC_ONLY; ejecutar SQL read/restore stage10 y pruebas JVM/XML, B7, liberar writer.
2) B0C3B: utilizar documento propio B1–B5, cubrir snapshot FBS2v6 de cinco stores y Household con archive identity pinned, y replay seguro; validar compatible con stage10 y el restore v5 histórico. Si seam requiere otras rutas, dividir en subunidades y completar DELTA B1–B5 antes de READY, no forzar un permiso. Ejecutar CI/XML/guard y B7.
3) B0D1: preflight schema10 DEV y opener estricto en un permiso exacto y B7.
4) B0D2: activation/journal/replay-fence/recovery bajo lease en permiso exacto, CI adversarial real y B7.
5) B7 global B0 únicamente tras demostrar backup v6→restauración cinco stores+Household→cutover/reopen/recover, cross-owner preservación, receipts, writer RELEASED, pointer durable y CI con SHA. B0 es INFRAESTRUCTURA DEV, no CRUD/Review Apply/stock/Finance payment.

## Condiciones
- Reutilizar W04-A 10/10, B0A 6/6, B0B 7/7, B0C1 8/8; evitar recertificaciones salvo delta real.
- Usar la misma rama y writer GLOBAL CAS: si otro owner HELD, sólo READ_ONLY. No tomar, liberar ni pisar escritor ajeno.
- No guardar secrets en repo, no pasar balances ni PII a servicios; no APIs pagas, producción, V2 productiva, S4 fuera de scope, main ni V4.4.
- No habilitar transacción multi-owner Household+Finance sin W06 B7 real; no mover dinero, hacer compras o modificar stock.
- La copia STAGED con Household debe conservar sin pérdidas Finance9, Auxiliary4 receipt ledger, Workspace, Evidence y WebEvidence; Active nunca se muta en sitio.
- Usuario quiere ejecutar sin interrupciones artificiales. Si Work se queda sin créditos, dejar checkpoint SHA/CI/XML/último permiso y writer RELEASED para continuar en Chat.

## Reporte mínimo
HEAD, unidad B0 exacta, B1–B7 estado, run IDs+XML, cambios/rutas, B0 cerrado o pendiente, writer RELEASED epoch, siguiente acción concreta. No inventar B7 PASS ni resultados físicos.

## ADDENDUM de B0C2 B7
B0C2 ya tuvo revisión independiente de código+CI y documento docs/v5/FARO_S4_W04_B0C2_HOUSEHOLD10_AUTHENTICATED_ARCHIVE_B7_PASS_20261120.md. Su permiso se consumió en el cierre. NO REPETIR el paso 0 si la guardia del checkpoint B7 es SUCCESS. Siguiente único READY futuro: W04-B0C3A, con permiso exact4 tras verificar guardia y B1–B5. Si hay rojo de guardia, parar hasta resolver. No reemitir B0C2 READY.

## ADDENDUM — permiso B0C3A emitido
Después del cierre B0C2, guard 44720117721 SUCCESS y su permiso BLOCKED, B0C3A ya tiene permiso READY **exclusivo para sus 4 rutas** en docs/v5/FARO_PROTOCOL_WORK_PERMIT.json. Work debe arrancar directamente con B6 de B0C3A; no reemitir autorización de B0C2 ni de C3A si el HEAD actual conserva el permiso. B0C3B y B0D siguen sin READY hasta sus turnos respectivos.
