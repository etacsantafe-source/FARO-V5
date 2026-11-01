# FARO V5 — Faro Noche V1.6 (preparado, NO activado)

**Estado:** `PREPARADO_SIN_AUTOMATIZACIONES` · 2026-11-01, Argentina.
**Objeto exclusivo de este cambio:** mejorar el protocolo de productividad y de medición de futuras noches. NO iniciar sesión ni CI, NO editar código FARO, NO modificar main. Las tareas V1.5 quedaron desactivadas.

## Base inmutable y continuidad

- Fuente funcional: `work/faro-noche-v15-20261101`, SHA `0750ee530703141016c6cd59a5312758d915ad1c`. Esta rama de preparación contiene los mismos archivos de código, con documentación V1.6 añadida.
- Situación: F1, F2, F3, F4 `READY_FOR_T4` (AUTOMATED), **S2.A6 3/8 CERTIFIED**, T4 físico **NOT_RUN**, V2 OFF. No inferir certificación de pruebas automáticas.
- El paso siguiente es la campaña **T4 manual con el usuario**, no otra noche. Leer `docs/faro-noche-v16/NEXT_CHAT_T4.md` y `docs/faro-noche-v15/T4_SINGLE_CAMPAIGN_PLAN.md`.
- Este protocolo no crea automatizaciones, no habilita escritores, no ejecuta pruebas, no activa Groq ni modifica producción. Requiere una NUEVA autorización expresa si alguna vez se planifica otra noche.

## Mejoras operativas preservadas de V1.5

1. **Continuidad delta-first**: empezar por último HEAD, checkpoint, Actions pendientes y next_action concreta. Evitar auditorías generales y relectura de gates verificados para el SHA correcto.
2. **Trabajo encadenado por objetivo**: no terminar una sesión tras revisar sólo resultados. Si una tarea acabó, continuar con la siguiente del mismo frente, respetando dependencias.
3. **Preparación útil durante esperas**: si writer ocupado, operar estrictamente en READ_ONLY, analizar logs, escribir hipótesis en memoria, localizar fixture y definir próximo lote sin competir. Si Actions pendientes, preparar trabajo independiente de la misma F. No atribuir esta actividad a escritura.
4. **CI con criterio**: agrupar cambios, usar XML real y SHA exacto; no relanzar CI por costumbre ni consumir minutos por repetir PASS. Un ROJO reparable se investiga y corrige, no habilita saltarse la F actual.
5. **Cierre seguro y temprano**: si sólo queda T4/humano, detener; liberar writer únicamente por CAS comprobado. No ocupar tiempo artificialmente ni extender ventanas.
6. **Handoff operacional único**: al cerrar, registrar resumen breve de SHA, owner/writer, evidencia y siguiente tarea lista para ejecutar; no añadir certificaciones o documentos superfluos.

## NUEVO en V1.6 — Cronometría exclusiva verificable

Usar `METRICS_SPEC.md` y `SESSION_METRICS_TEMPLATE.json`. Cada intervalo observado requiere **inicio y fin UTC reales**, categoría y descripción de la acción realizada. Registrar eventos al cambiar actividad, no reconstruir a ojo al cierre.

Categorías EXCLUSIVAS:
- `IMPLEMENTATION`: editar/corregir archivos y preparar commit funcional.
- `INVESTIGATION`: investigar causa/documentación/código y preparar diffs o fixture con resultado concreto, incluso READ_ONLY.
- `VALIDATION_REVIEW`: revisión activa de XML, logs, invariantes o pruebas (sin contar CI ejecutándose por sí sola).
- `COORDINATION`: preflight, locks CAS, checkpoint, reporte y gestión de herramientas/sesión.
- `WRITER_WAIT`: no trabajo útil por escritor ajeno ocupado.
- `CI_WAIT`: no trabajo útil por Actions ocupada o en cola.
- `IDLE_OBSERVED`: inactividad comprobada durante tramo observado.
- `UNOBSERVED`: hueco sin actividad medible; NO etiquetar automáticamente como idle.

Si una Actions corre al mismo tiempo que investigación, el tiempo del agente se clasifica `INVESTIGATION`, NO `CI_WAIT`; Actions se anota en registro secundario con queue/start/end/status y jamás se suma al tiempo de la sesión. Intervalos de actividades mutuamente excluyentes, recortados a los límites programados; si hay superposición errónea, invalidar medición hasta corregir. Los intervalos sin evidencia van a `UNOBSERVED`, sin rellenarlos con supuestos.

### Indicadores que NO se deben confundir

- `uso_util_observado = (IMPLEMENTATION + INVESTIGATION + VALIDATION_REVIEW) / tiempo_observado_clasificado`; sólo publicar porcentaje con cobertura y tiempos cronometrados completos.
- `cobertura_temporal = segundos_observados / segundos_programados_efectivamente_habilitados`. No significa tiempo productivo.
- `uso_util_ventana = segundos_utiles / segundos_programados_efectivamente_habilitados`: se puede informar junto a los segundos UNOBSERVED; no adjudicar útil a esos huecos.
- `tiempo_total_CI` se reporta aparte; cola y ejecución NO duplican trabajo del agente.
- `tasa_de_exitos`: suites/sha/test XML exactos, no mezclar casos distintos con ejecuciones repetidas.
- Objetivo orientativo >65% **medido** de trabajo útil; no promesa. La cifra ~50% de V1.4 era referencia estimada, NO una línea base metrológica compatible. V1.5 tuvo ~43m54s de presencia observada, pero desglose exclusivo `NO_REGISTRADO`; tampoco equivale a 73,2% productivo.

### Regla de calidad de métricas

Para calcular tasa productiva exigir intervalos completos sin superposiciones ni gaps inexplicados del tramo observado. Si la cobertura falta, publicar duraciones conocidas, `UNOBSERVED`, y marcar `RATIO_NO_CONFIABLE`. No crear cifras retroactivas V1.4/V1.5. Mostrar mejora por resultados verificables (objetivos terminados, ROJOS corregidos, CI repetidas evitadas) separada de mejora porcentual.

## Salvaguardas para ejecución futura (sólo tras nueva autorización)

- Un writer, exclusividad confirmada incluso con lease vencido, CAS HEAD exacto, checkpoints durables; si hay conflicto READ_ONLY.
- Código únicamente en rama de noche autorizada, jamás main, V1/V4.4, repositorio histórico, Reality producción ni puntero activo. `V2_ACTIVE_ENABLED=false` sin excepción.
- Tareas A/B horarias desfasadas 30 minutos como máximo si en el futuro se aprueba otra ejecución. Respetar ventana 0–20 trabajo, 20–25 cierre, 25–27 checkpoint y liberación, 27–30 READ_ONLY, cutoff estricto.
- No pagos, firma privada, credenciales en GitHub ni pruebas físicas automáticas. No abrir S3/S4 por derivación de esta preparación.
- No utilizar V1.6 para reejecutar F1–F4 ya verificados: la próxima acción aprobada hoy es T4 manual.

## Estado de implementación

Se aplicaron las mejoras **al protocolo durable, definición de métricas y plantilla de registro**. No se desplegó un agente instrumentado, no se crearon turnos y no existe medición V1.6 de una sesión real hasta futura activación explícita.
