# FARO V5 — Noche V1.5 · Preparación para activación (01/11/2026)

**Estado actual:** CONFIGURADO, **NO ACTIVADO**. Autoriza preparar este protocolo, no iniciar las tareas hasta orden expresa en chat nuevo.

## Alcance y plazo
- Repositorio: `etacsantafe-source/FARO-V5`.
- Rama de código y checkpoints para Noche: `work/faro-noche-v15-20261101`. Partir del HEAD actual confirmado, conservar la rama F2 manual y F1 congelada sin cambios.
- Fecha de corte de **toda escritura y desarrollo**: **domingo 01/11/2026 a las 06:00:00**, `America/Argentina/Cordoba` (UTC-03). Sin prórroga por retrasos, CI ni lease.
- Objetivo exclusivo: **S2.A6** (cinco stores, FBS2, replay/WAL ya certificados; F1 congelado listo T4; automatizar lo que falta de **F2 → F3 → F4**). No S3/S4, ni una etapa "S6" diferente; la referencia al alcance es S2.A6.
- Orden de precedencia: terminar TODO lo automatizable de F2 y congelar `F2_READY_FOR_T4_OR_HUMAN` con evidencias antes de F3; lo mismo para F3 antes de F4. Un ROJO reparable no habilita salto; resolver primero. Sólo una necesidad física/humana genuina, documentada y aislada, permite avanzar a trabajo independiente.
- Si termina trabajo automatizable F4 antes de las 06:00, detener A/B y emitir informe. No instalar APK, no pedir teléfono, no probar en Moto G15, no hacer cutover. Mantener `V2_ACTIVE_ENABLED=false`, Reality de producción y puntero V1 intactos. No declarar "CERTIFIED" lo que depende de T4 o consentimiento real.

## Contexto al prepararse
- Rama origen manual F2: `work/f2-manual-20261101`; base HEAD `3f61f5c483d70d05d41048f704c45c4337c21fda`.
- F1 prueba automática 27/27 aprobada en #41266155404; F1 `READY_FOR_T4_FROZEN`, código SHA `2c79c5f2f18c7742cf2b8b512221a2a7a6430f62`. No repetir F1 salvo regresión real.
- F2 S01: emulador Android + oráculo XML **3/3 PASS** en Actions #41273805209, SHA exacto `736ebd63ecbfb12187a43de8601d1223a4045a20`. F2 todavía OPEN hasta completar integración/casos pendientes. No repetir esa corrida sin cambios/razón.
- La decisión humana de F3 **opción A ya está APROBADA**: `docs/FARO_V5_F3_S2_S5_DECISION_20261030.md`. En S2 implementar exclusivamente UI mínima para exportar/restaurar cinco owners cifrados y consentidos mediante SAF; Ajustes/Seguridad definitivos permanecen S5. No volver a pedir la misma decisión.
- S2.A6 **3/8 certificados**. T4 físico aplazado al final para F1/F2/F3/F4 como un paquete, conforme `docs/S2_A6_BATCHED_PHYSICAL_T4_DECISION_20261031.md`.

## Exclusividad y gobierno
- **Un escritor** entre Noche A y B y cualquier otro agente. No confiar en sólo reloj/lease: leer HEAD y estado durable, comprobar que el dueño anterior está TERMINAL y que no sigue escribiendo. Si hay incertidumbre, READ_ONLY, nunca force.
- Adquirir escritor en un commit atómico CAS del HEAD esperado con owner, epoch y lease; no reusar uno de otra sesión. Antes de cada commit revalidar permiso, fecha, propietario, lease y HEAD. Publicar con `create_blob/create_tree/create_commit/update_ref(expected_sha)`; no actualizar archivos a ciegas cuando pueda haber competencia.
- Rechazo CAS => no escribir: leer HEAD reciente, reconciliar y reiniciar sólo si se demuestra exclusividad. `NONE` sólo tras liberación confirmada, nunca por expiración sola. No superponer writers ni sobrescribir trabajo ajeno.
- No tocar `main`, repositorio histórico, FARO V1/V4.4, FARO Auto, gestión de secretos/firmas, pagos, permisos, privacidad, configuración de producción o apps ajenas. No abrir S3/S4.
- Mantener rama exclusiva. Todo cambio se debe probar y registrar por SHA; no reescribir código que ya está cubierto ni inventar test PASS.

## Ventanas y automatizaciones (se crean SÓLO en el chat siguiente)
- Dos tareas A y B, cada una **una vez por hora**, desfasadas 30 minutos entre sí. El sistema permite como máximo una frecuencia **horaria por tarea**; no intentar una sola tarea cada 30 minutos.
- La ventana original dura 30 minutos desde el disparo programado, no desde cuando empieza a responder:
  - 0–20: trabajo funcional encadenado y pruebas dirigidas; no parar tras una auditoría.
  - 20–25: finalizar trabajo iniciado, sin iniciar una tarea nueva ni Actions nuevos.
  - minuto 25: registrar Actions pendientes con run ID, SHA, estado; no cancelar por esperar.
  - 25–27: checkpoint durable CAS, liberar writer verificado.
  - 27–30: investigación/seguimiento READ_ONLY.
- Inicios tardíos consumen su propio margen; no desplazan fronteras. Última escritura/release **antes de las 06:00**, preferentemente antes de las 05:57 para permitir checkpoint. Si no alcanza para cierre seguro, sólo READ_ONLY.
- Si writer ocupado: durante ~5 min revisar GitHub, estudiar ROJOS y preparar el próximo diff, fixtures, scripts y tests en memoria (**sin** escribir branch, lanzar Actions, adquirir writer, alterar locks o crear PR). Revalidar luego HEAD/owner/lease/corte; si sigue ocupado, cerrar turno READ_ONLY con conclusión útil.
- Si CI ocupado: READ_ONLY sobre **el siguiente paso de la misma F actual** y, sólo si no tiene tareas útiles, investigación de F siguiente **sin** editar código ni adelantar F3/F4. Agrupar cambios de un lote antes del CI; no hacer pushes repetidos que provocan cola y repeticiones. CI asíncrono conocido permanece por run ID; evitar lanzar otro para el mismo SHA.
- No cancelar CI por minuto 25 o por el corte. Si sigue corriendo, documentar run ID y SHA para siguiente turno/informe. Una Actions exitosa no certifica por sí sola F completa: revisar XML exacto y contratos.
- Cuando una tarea automatizable queda bloqueada por dependencias o usuario: demostrar que las alternativas propias de esa F están agotadas; congelar estado `READY_FOR_T4` o `WAIT_HUMAN` documentando lo concreto. Continuar sólo con partes independientes y seguras de F siguiente.
- Si F2, F3 y F4 quedan `READY_FOR_T4` antes de 06:00, desactivar inmediatamente A y B, finalizar y emitir informe READ_ONLY. No seguir por llenar horas.

## Mejoras de productividad V1.5
1. **Cola de trabajo preparada en READ_ONLY** mientras writer/Actions ocupados, según `BACKLOG_READ_ONLY.md`. No confundir preparar con ejecutar.
2. **Arranque incremental**: leer el último checkpoint + SHA + Actions en curso; no reiniciar auditorías generales ni repetir F1.
3. **Agrupar gates útiles** por lote y reutilizar PASS exacto; diagnosticar ROJO en lugar de relanzar ciegamente.
4. **Cierre anticipado inteligente**: si quedan sólo pasos humanos/físicos, avanzar F siguiente sin perder ciclos; si todo S2.A6 automatizable está resuelto, terminar.
5. **Métricas no ficticias**: timestamps observables por sesión para investigación READ_ONLY, escritura/implementación, CI revisión, espera de writer, espera de CI, coordinación y tiempo sin actividad registrada. CI solapado con trabajo no cuenta dos veces como tiempo productivo. Objetivo orientativo >65% tiempo útil **medido**, no promesa.
6. **Tareas siguientes ya delimitadas** antes del cierre de la sesión; evitar gastar el inicio del siguiente turno en localizar la brecha.

## ROJOS, evidencia y salida
- ROJO: inspeccionar error real (compilación/runtime/XML), corregir y probar mínimo; tras segundo intento fallido, cambiar hipótesis con investigación y no repetir lo mismo. Si aparece riesgo de corrupción/autoridad/egreso, fail-closed, `BLOCKED` y no avanzar indebidamente.
- Reporte de cada turno: F, resultado funcional, SHA de cambios, Actions IDs con SHA/resultado, XML (tests/fail/errors/skipped), issue o bloqueo humano, next task, writer lease confirmado y métricas observadas. Ausencias = `NO_REGISTRADO`.
- Al corte 06:00 o cierre funcional anticipado: no más cambios; desactivar A/B y verificar desactivación; informe final READ_ONLY resumido y durable con progreso real de F2/F3/F4, qué resta para T4, pruebas, commits, Actions, tiempos productivos/espera/inactividad y problemas. No afirmar más de lo observado.

**Autoridad temporal:** esta preparación no crea ni habilita automatizaciones. Sólo cuando el usuario escriba `Activar FARO Noche V1.5` en chat nuevo se programa. No iniciar antes.