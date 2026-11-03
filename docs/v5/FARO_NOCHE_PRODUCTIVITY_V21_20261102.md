# FARO Noche — Protocolo de productividad V2.1 (preparado, Noche OFF)
Fecha: 2026-11-02. Aplica al DESARROLLO S3, no activa V2 de la app.
Estado: PROPUESTA DURABLE PARA PRÓXIMA AUTORIZACIÓN / NO ACTIVO / sin ejecución CI nueva.

## Evidencia y diagnóstico de las sesiones 02/11
- A 19:00 ~6 min; A 20:00 ~15 min; B 20:30 ~10 min; A 21:00 ~7 min, de ventana 20 min por turno. Son **intervalos observados aproximados**, NO minutos de CPU ni esfuerzo medido; no inferir utilización real exacta.
- Gran parte de la ventana se pierde por cierre anticipado tras PASS o al encontrar un owner no integrado. El writer se libera correctamente; el error es terminar el turno sin consumir alternativas READ_ONLY válidas.
- S3-A1 1/4 fuentes de Inicio reales: Avisos N-032; Pendientes N-031, Actividad N-015 y Guardados bookmark son no disponibles, no cero. A2–A4 son funcionalmente dependientes; investigación independiente está permitida.
- S3-N00 PASS limitado: A/B coordinan mismos refs; rechazo CAS stale emite error genérico y no se probó carrera simultánea real entre dos agentes. No exagerar.
- Históricos: CI 45/45 para código concreto de A1 en #41549981598. No repetir XML por SHA si no cambia el ámbito.
- Cancelación: A, B e informe 22:10 desactivados; cola CLOUD pasa a STAGED, no se vacía backlog ni se concede nueva autorización.

## Principio operativo
Maximizar trabajo SEGURO y verificable, no minutos artificiales, pushes inútiles, investigaciones infinitas ni consumo de Actions. Nunca sacrificar exclusividad, autoridad, idempotencia, evidencia ni ventanas de cierre por productividad.

## Algoritmo de cada turno — bucle con salida
0. **Preflight**: comprobar fecha/hora local, nueva autorización vigente, cola CLOUD mode=ENABLED y activation_authorized=true, deadline futuro, writer, rama HEAD, checkpoints, automatizaciones, Actions en vuelo. Si falta cualquiera: READ_ONLY + STOP, explicar por qué.
1. **Recuperación barata**: reutilizar PASS por SHA y leer SOLO el checkpoint y archivos de la tarea. No auditar todo FARO ni reabrir S2.
2. **Elegir trabajo**: (a) tarea desbloqueada con aceptación concreta; (b) subtarea segura de la misma etapa; (c) investigación independiente READ_ONLY de su bloqueo (owner/tipos V4.4/benchmark/test plan); (d) verificación de resultados de CI existente; (e) cierre temprano JUSTIFICADO si no queda trabajo permitido.
3. **Ejecutar una unidad pequeña** con criterio binario de cierre y guardar evidencia. Comparar V4.4 READ_ONLY, fuentes oficiales de apps sólo cuando corresponda, revisión adversarial + mitigación. No repetir búsquedas sin una nueva hipótesis.
4. **Continuar**: si queda margen en la fase 0–20, comprobar nueva evidencia de GitHub, dependencias y siguiente subtarea. No finalizar por el primer PASS. En CI en vuelo, investigar READ_ONLY o planear integración siguiente, jamás disparar CI duplicada.
5. **ROJO**: una corrección mínima y una segunda hipótesis; tras dos intentos infructuosos STOP funcional, causa registrada, y solo investigación independiente permitida. Efecto UNKNOWN, disputa CAS, permisos o riesgo de egress: fail-closed inmediato.
6. **Cierre** con relojes desde hora PROGRAMADA, no desde ejecución real: 0–20 trabajo; 20–25 no iniciar nueva tarea ni Actions, terminar unidad y checkpoint; 25–27 liberar writer CAS; 27–30 READ_ONLY. Si agente termina antes, registrar la causa; no inventar trabajo.

## Planificador de alternativas permitido
- Si A1 exige un owner aún inexistente, escribir diagnóstico READ_ONLY sobre N-031 attention.read o N-015 history.query (types, fuentes, aislamiento, privacidad, pruebas). NO simular un conteo a partir de Workspace ni convertir lookup por ID en listado.
- Guardados N-018: revisar bounded +8 CANDIDATAS en documento, sin modificar inventario de 172 o ejecutar bookmarks antes de aprobación acotada humana.
- A2–A4: investigar dependencias y riesgos READ_ONLY, elaborar casos y gates, pero NO implementar fuera de orden.
- La investigación externa no necesita instalar apps ni copiar código; comparativa con V4.4 basada en funciones concretas y diferencias.
- Si el writer está HELD ajeno, hasta ~5 min READ_ONLY sobre tareas seguras; no renovar/forzar/takeover. Si sigue HELD STOP + checkpoint de bloqueo si puede hacerse SIN writer; si no, solo informe.
- Tareas independientes no autorizan segundo writer: SOLO un escritor global.

## Datos y métricas obligatorias por turno
Registrar scheduled_for, started_at, last_mutation_at, checkpoint_at, writer_release_at, completed_at y status, con fuente real; los faltantes = NO_REGISTRADO.
Contabilizar duración OBSERVADA por categorías {codigo, investigacion, revision, pruebas_locales, ci_espera, writer_espera, cierre, sin_actividad} con marcas reales, sin doble contar CI solapado. Si no hay telemetría, no presentar porcentajes como medidos.
Reportar units_completed, units_read_only, gate_xml_evidence, CI_run_ids_SHA, blocked_reason, stop_reason, siguiente tarea, estado writer y cola. Informes cortos por turno; un solo cierre final.

## Guardas al reactivar
- La cola tiene que estar STAGED, el writer RELEASED y las tres automatizaciones previas OFF antes de nueva autorización.
- Fijar un **nuevo** alcance y corte humanos, crear/actualizar schedules, validar A/B misma cola. Ninguna autorización anterior se reutiliza.
- Mantener validación de CAS stale (resultado genérico = evidencia LIMITADA) y un test de contienda aislado antes de reclamar certificación fuerte del scheduler.
- Necesita mecanismo de parada explícito: al cancelarse, desactivar tareas, cambiar cola a STAGED por CAS y activation_authorized=false, checkpoint; verificar relectura. La cancelación anterior dejó cola ENABLED: no repetir.
- No garantizar una duración de 20 min de ejecución: las automatizaciones pueden finalizar antes por límites propios. El control mejora qué hacer mientras ejecutan, no promete ejecución continua.
- Sin API paga, sin S4, app V2 OFF, S2 cerrada, main/V4.4 intocables; pruebas físicas juntas al final.

## Pruebas nuevas pendientes, NO PASS
N21-01 Cancelación desactiva A/B/informe y desautoriza la cola sin borrar tasks.
N21-02 Gate de tiempo y nueva autorización bloquea turnos tardíos aunque una schedule accidental siga habilitada.
N21-03 PASS temprano consume siguiente tarea segura, sin repetir CI verde.
N21-04 Owner bloqueado elige investigación READ_ONLY sin promover tareas dependientes.
N21-05 Writer HELD por tercero impide escritura aunque expiró lease.
N21-06 Reanudación desde checkpoint idempotente, con SHA esperado.
N21-07 Contienda aislada y error CAS stale documentan exactamente evidencia disponible.
N21-08 Métricas faltantes se informan NO_REGISTRADO; no inventar ocupación.

## Decisión humana realmente pendiente
Revisión acotada (bounded reopen) de las 8 operaciones de bookmark/Guardados candidatas. Abrir la revisión no significa aprobar el cambio a 180 contratos. Cualquier modificación final del catálogo requiere resolución independiente basada en evidencia. Activación futura de Noche requiere aprobación separada de horario y alcance. Activación V2/S4 no está solicitada ni es necesaria ahora.
