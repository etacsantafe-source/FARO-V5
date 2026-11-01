# FARO Noche V1.6 — Especificación de medición objetiva

## Eventos observados

Cada tramo es `[start_utc,end_utc)`, en ISO 8601 UTC, con `start_utc < end_utc`, `category` del catálogo y `description` de una acción observable. La sesión incluye `slot_start_local`, `slot_end_local`, `first_observed_utc`, `last_observed_utc`, `owner`, `head_before` y `head_after`. Al finalizar se produce un checkpoint *con eventos de esa sesión*, no estimaciones.

- `IMPLEMENTATION`: trabajo efectivo sobre código/pruebas/documentación funcional.
- `INVESTIGATION`: investigación concreta, incluso si no se puede escribir.
- `VALIDATION_REVIEW`: examen de XML, logs, SHA, validaciones activas.
- `COORDINATION`: CAS, controles, organización, reporte/checkpoint.
- `CI_WAIT`: espera sin otra actividad útil por CI.
- `WRITER_WAIT`: espera sin otra actividad útil por exclusión de escritor.
- `IDLE_OBSERVED`: tramo realmente observado sin trabajo.
- `UNOBSERVED`: intervalo de actividad no conocido.

Un único intervalo a la vez en el reloj de la sesión; dos acciones concurrentes de distinto tipo se asignan al trabajo primario observado y la segunda se anota como nota. El tiempo de Github Actions es telemetría paralela y **nunca** suma segundos de actividad del agente.

## Cálculo

1. Recortar todos los tramos al `slot_start_local/slot_end_local` programado y al cutoff final. Ordenar. Rechazar solapamientos sin reconciliación.
2. Sumar por categoría; gaps entre `first_observed_utc` y `last_observed_utc` se registran `UNOBSERVED` salvo evidencia explícita de estado.
3. `useful = IMPLEMENTATION + INVESTIGATION + VALIDATION_REVIEW`.
4. `classified = useful + COORDINATION + CI_WAIT + WRITER_WAIT + IDLE_OBSERVED`.
5. `useful_ratio_classified = useful / classified` sólo si classified > 0; acompañar siempre de `unobserved_seconds` y `coverage_classified`. Si `unobserved_seconds > 0`, marcar `PARTIAL_NOT_COMPARABLE`; no extrapolar al tiempo sin observación.
6. `useful_ratio_scheduled = useful / scheduled_enabled_seconds` es un **mínimo observado**, no productividad neta del total, salvo cobertura completa.
7. `coverage = (classified + unobserved_seconds) / scheduled_enabled_seconds`. Señala tiempo con ventana observada, NO productividad. `CI_runtime` y `CI_queue` separados del denominador de actividad humana.
8. No calcular porcentaje comparativo V1.4 vs V1.5 al no haber medición exclusiva compatible. En V1.6 fijar comparación futura solo entre sesiones con igual definición y cobertura suficiente.

Ejemplo: durante 10 minutos de CI, 6 de investigación y 4 de espera ociosa, registrar 6 `INVESTIGATION` + 4 `CI_WAIT`; reportar CI 10 min aparte. No 16 ni 20 minutos.

## Salida obligatoria por turno

Slot original y ejecución real; writer owner/epoch/release; HEAD antes y después; log de intervalos; segundos por categoría; UNOBSERVED/NO_REGISTRADO; CI IDs, SHA, XML y tiempos cola/ejecución; ROJO y resolución; objetivo siguiente único; estado READY_FOR_T4/WAIT_HUMAN sólo con evidencia; resumen de métricas sin porcentajes engañosos. Si no hubo trabajo, reportar causa precisa.

## Regla anticarga administrativa

Una entrada por tramo significativo al cambiar de actividad, no un documento por evento ni un commit por minuto. Agregar a checkpoint de sesión; mantener costo de medición dentro de COORDINATION. Se acepta registrar `NO_REGISTRADO` antes que una estimación inventada.
