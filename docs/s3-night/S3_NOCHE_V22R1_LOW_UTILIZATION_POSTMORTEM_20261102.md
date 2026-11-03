# FARO Noche V2.2-R1 — incidente de baja productividad y cancelación (02/11/2026)

Estado: **NOCHE CANCELADA POR USUARIO. NO REACTIVAR SIN NUEVA APROBACIÓN.**
Alcance: desarrollo S3 solamente; S2 CLOSED 8/8, S4 OFF, app V2 OFF, catálogo 172. No se reabre etapa ni se cambia funcionalidad.

## Evidencia observable del scheduler (no estimaciones)

| Slot AR | Inicio ejecución AR | Fin ejecución AR | Runtime observado | Nuevo código / test / checkpoint |
| --- | --- | --- | ---: | --- |
| NOCHE_A 23:00 | 23:00:16.487 | 23:00:42.157 | 25.67 s | 0 / 0 / 0 |
| NOCHE_B 23:30 | 23:28:56.679 | 23:30:12.857 | 76.18 s | 0 / 0 / 0 |

Duración combinada: **101.85 segundos = 2.83% de una hora**. Es tiempo de ejecución del agente, NO necesariamente tiempo de trabajo útil; minutos útiles efectivos por categoría NO_REGISTRADO. B comenzó antes de 23:30, sin que eso amplíe el horario autorizado. Ambas ejecuciones dejaron el código S3 en `f4f23d379403e2d6c85a7d8e075505409f0edde8`. Ambas informaron la misma hipótesis de stale count en `FaroV5HomeIndicators.kt` / `produceState(key1=readPort)`, **ya registrada como OPP-A1-ADV** en `docs/s3-night/S3_V22R1_OPPORTUNITY_BANK_20261102.json`. No hubo prueba de reproducción ni implementación. No promocionar esta repetición como dos hallazgos.

## Causa observable y límite de plataforma

- La automatización ejecuta un trabajo autocontenido y **puede finalizar tras una respuesta breve**; una ventana autorizada de 20 minutos no obliga a disponer de 20 minutos de runtime. A/B cerraron después de inspección/repetición sin hacer la unidad de prueba/código disponible.
- El protocolo largo y el banco de oportunidades no establecieron una obligación verificable de producir una unidad: el agente eligió investigar un punto ya inventariado y redactar informe. Esto describe el fallo del comportamiento observado, no demuestra una causa única interna.
- No adjudicar minutos imaginarios a CI, writer o bloqueos. Writer estaba RELEASED; ninguna evidencia de disputa o CI que forzase la detención.
- El resultado previo cercano a 50% de ocupación no demuestra que la configuración nueva mejore; la versión V2.2-R1 representa una regresión operacional severa.

## Cancelación real

Automatizaciones A `6ae9072890248191b886a790cf456be6`, B `6ae9072a7b6c8191b9e107fe6d6c6940` y final `6ae9073eccd08191b88aec776771ca9e`: verificadas **disabled**. Cola CLOUD ref `faro-v5-night-queue`: **STAGED**, `activation_authorized=false`, 7 tareas intactas, sin inflight, nueva autorización obligatoria. Writer global: **RELEASED** (verificar de nuevo antes de escribir). La autorización original hasta 07:00 fue revocada anticipadamente por el usuario. Ninguna automatización ni CI nueva debe ejecutarse.

## Corrección propuesta — NO IMPLEMENTADA / NO CERTIFICADA

1. **Separar coordinador y ejecutor**: el coordinador selecciona UNA unidad READY con fichero concreto, aceptación binaria y test verificable; el ejecutor primero intenta la unidad real. Sin investigación genérica como entrega por defecto.
2. **Deduplicar por hipótesis + scope + source_head ANTES de investigar**. OPP-A1-ADV ya existe: siguiente avance es escribir prueba adversarial (ideal Compose aislado) o declarar por qué no se puede probar; no describir otra vez la hipótesis.
3. **Prompt ejecutivo corto**: preflight CAS, unidad asignada, pasos mínimos para código/test, salida con commit+CI o bloqueo nuevo verificable. Documentos extensos se leen como fuentes, no se copian enteros al prompt.
4. **Trabajo verificable antes que duración artificial**: al terminar una unidad, encadenar siguiente READY de baja complejidad y repetir chequeo de reloj; nunca inventar minutos. Si un ejecutor termina a los 26 segundos, reportar exactamente eso.
5. **Prueba piloto antes de activar toda la noche**: realizar dos sesiones aisladas con supervisión y medir duración real, acciones GitHub, código/pruebas y duplicaciones; si no hay progreso, no programar ocho horas. El piloto requiere autorización nueva.
6. **Límite estructural**: si se desea garantizar trabajo continuo durante una ventana larga, estas automatizaciones por sí solas NO lo garantizan. Evaluar agente persistente/Work o runner de trabajos deterministas (GitHub Actions ejecuta tests/scripts, NO escribe código novedoso autónomamente sin agente); requiere arquitectura y permiso nuevos, no prometerlo ahora.

## Criterios del siguiente piloto, aún NO_RUN

- Se respeta la cola única CLOUD y el writer CAS; cero contienda, un solo branch S3, relectura RELEASED.
- Al menos una unidad de código/test con aceptación demostrada, o un bloqueo NUEVO y verificable registrado durablemente; no duplicación OPP-A1-ADV.
- Registrar duración real y producción por sesión sin confundir ventana de 20 minutos y runtime entregado.
- Si ambos pilotos vuelven a ser ejecuciones de segundos sin producción, DETENER; no continuar automáticamente ocho horas.
- No extender el permiso revocado ni empezar A2/A3/A4 dependientes o +8 Guardados antes de decisión contractual independiente. Sin pruebas físicas hasta una campaña final.

Próximo paso: **discutir con el usuario el rediseño; Noche OFF**. No afirmar V2.3 implementada ni pruebas PASS por redactar este incidente.
