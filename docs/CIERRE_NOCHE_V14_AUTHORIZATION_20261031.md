# FARO V5 — CIERRE Noche V1.4 · autorización 31/10/2026

Autorización expresa del usuario para una ejecución autónoma de **seis horas máximo**, o hasta cerrar S2 antes, en repositorio público `etacsantafe-source/FARO-V5`, **única rama escritora** `work/cierre-noche-v14-20261031`.

## Horarios y caducidad — Argentina UTC−03

- Inicio autorizado **2026-10-31T12:10:00-03:00**.
- Corte de escritura absoluto **2026-10-31T18:10:00-03:00**, sin extensión ni reprogramación por retrasos.
- Noche A: 12:10, 13:10, 14:10, 15:10, 16:10, 17:10.
- Noche B: 12:40, 13:40, 14:40, 15:40, 16:40, 17:40.
- Informe READ_ONLY: 18:10 (o de inmediato al cierre real anticipado de S2, evitando informe duplicado).
- La ventana 0–20, 20–25, minuto 25, 25–27, 27–30 se mide **desde su disparo programado**, nunca desde la ejecución efectiva.
- Si la tarea empieza con retraso, queda menos tiempo operativo. Si writer ocupado, **esperar exactamente cinco minutos y verificar HEAD, writer, lease, automatizaciones y fecha**; sólo trabajar si queda tiempo seguro hasta límites originales. Si no, checkpoint READ_ONLY y terminar.
- Al 20, ninguna tarea de desarrollo o Action nueva. 20–25 sólo finalizar en curso. Al 25 registrar CI pendientes por ID y SHA, sin cancelar. 25–27 checkpoint y liberar writer CAS. 27–30 sin escritura.

## Origen verificado

- V1.3 rama `work/cierre-noche-v13-20261030`, HEAD documental `b9219d5a501a49e6834f540afc2ad99a46aa0616`.
- Último código funcional `2bf2e65ce61827a62a2e79f34db3c2e4177f5624`.
- V1.3 writer NONE epoch 33 (checkpoint durable; volver a validar estado y HEAD).
- S2.A6 abierto **3/8**: certificados cinco owners, restore FBS2, journal/replay/WAL; pendientes F1, F2, F3, F4, T4 físico.
- GitHub Actions en repo público #41212633698 SUCCESS; prueba pequeña de infraestructura, **no** certificación S2.
- Estado previo: `docs/CIERRE_NOCHE_V13_RUN_STATE.json`; último checkpoint `docs/CIERRE_NOCHE_V13_B8_CHECKPOINT_20261031_0614.md`. Protocolo base V1.2 `docs/cierre-noche/PROTOCOL_V1.md` en `docs/cierre-noche-v1-20261030`; esta autorización V1.4 prevalece ante conflictos.

## Exclusividad dura

**Un solo writer.** No adquirir si hay sesión viva, lease vigente o incertidumbre. Adquirir, actualizar y liberar mediante confirmación CAS del **HEAD exacto** del branch; usar `create_blob/create_tree/create_commit/update_ref` con `expected_sha` y verificación posterior. No usar `create_file/update_file` para saltarse CAS en ejecución concurrente. No usar `force` sin expected_sha. Un lease vencido no da permiso si anterior writer sigue escribiendo. Registrar owner/epoch/lease de hasta 30 min; ante duda, READ_ONLY / CONFLICT. Registrar siempre estado final y writer NONE sólo si se verifica.

No modificar `main`, `etacsantafe-source/FARO` histórico, V1, V4.4, FARO Auto, permisos/visibilidad, secretos, facturación, ni hacer merges. No publicar otros datos. V2 OFF, S3 y S4 **prohibidos** para esta prueba. Sin cutover ni activación de funcionalidades incompletas. Sin gastos.

## Prioridades y evidencia

Objetivo exclusivo: cerrar S2.A6 y S2. F1 Chat, IA real con consentimiento, Review, Apply, Delivery Android; F2 activación y recuperación; F3 cinco stores respaldados/restaurados cifrados, SAF y consentimiento, opción A (UI mínima S2; Ajustes completo S5); F4 regresiones integrales; T4 físico preparado para el usuario, jamás inventar PASS. Reutilizar aprobaciones válidas y comprobar SHA exacto. No nuevas auditorías/certificaciones de terceros. Pruebas dirigidas por lote funcional, gate integral al cierre.

Ante ROJO: primer intento diagnosticar/arreglar/probar; si segundo fracasa, investigar documentación, arquitectura, antecedentes y formular nueva hipótesis fundamentada; continuar sin intentos idénticos ciegos. Si no queda trabajo seguro, dejar bloqueo durable y detener.

## Métricas obligatorias por sesión

Guardar hora programada, hora real inicio y final, tiempo observado de investigación/diagnóstico, escritura/código, pruebas/revisión/corrección, espera CI, espera writer, checkpoint/coordinación, y huecos sin actividad comprobable. Cronometrar de forma exclusiva (no doble contabilidad de CI en paralelo), distinguiendo **wall time**, tiempo productivo, esperas y CI en paralelo. Actions: run ID, SHA, cola, ejecución, fin, resultado, XML y tests. Si no hay medición, `NO_REGISTRADO`, nunca inventar minutos. Checkpoint durable tras cada turno; journal de métricas en `docs/CIERRE_NOCHE_V14_RUN_STATE.json`.

## Informe y finalización

A las 18:10 (o antes si S2 cerrado), tarea final **sin cambios de producto**: leer GitHub, desactivar A/B, verificar ambos disabled y writer NONE si es cierto; si writer aún activo, informar CONFLICT y no afirmar liberación. Entregar informe PDF descargable con estado 8 criterios, funcionalidad y commits, Actions y XML, desglose medido por actividad/sesión, CI cola/exec, gráfico distribución, tiempo productivo/espera/muerto, ROJOS, próximos pasos. Si PDF no es posible, informe íntegro en archivo durable y limitación explícita. No escribir más código al corte.
