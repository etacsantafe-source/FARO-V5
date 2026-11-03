# FARO V5 — Noche S3 SIMPLIFICADA (misión única encadenada)
Fecha: 2026-11-03. Estado histórico al 03/11: **PREPARADA / OFF**. El estado operativo vigente se determina exclusivamente leyendo la cola CLOUD y las automatizaciones en vivo.

## Decisión
Partir de la Noche S3 que produjo código y CI el 02/11, antes de la regresión de V2.2-R1. Los intervalos observados fueron aproximadamente 6+15+10+7 minutos de cuatro turnos (38/80 = 47,5% de ventana, NO productividad útil cronometrada). Los pilotos A/B de V2.2-R1 terminaron en segundos con 0 commits nuevos. No llamar V2.0 a la versión productiva sin verificar la etiqueta. No borrar ni sobrescribir los protocolos históricos.

## MISIÓN ÚNICA para ambos turnos A y B
**Continuar implementando S3 desde el último checkpoint real. Mientras quede tiempo efectivo de ejecución y haya trabajo autorizado seguro, encadenar otra unidad real.**

Ciclo operativo — repetir dentro de la MISMA ejecución cuando sea posible:
1. **Recuperar** checkpoint, puntero, rama/HEAD, cola CLOUD, permisos, writer/epoch, CI viva y tareas; no asumir estado a partir de los documentos históricos. Si no hay NUEVA autorización vigente por fecha, hora de inicio/corte y alcance y cola `ENABLED+activation_authorized=true`, STOP READ_ONLY. Una tarea desactivada o una cola STAGED nunca autoriza trabajo.
2. **Elegir** una unidad S3 concreta y no repetida, con archivos, resultado binario, dependencias satisfechas y alcance autorizado. A/B usan la MISMA cola y misión; B no replantea lo que ya hizo A. No confundir investigaciones RESEARCH_ONLY/BLOCKED con IMPLEMENT READY.
3. **Ejecutar** el protocolo completo sobre esa unidad: investigación dirigida, comparación puntual READ_ONLY con V4.4 y documentación/herramientas pertinentes, búsqueda adversarial de huecos; luego escribir código real, probar, corregir ROJOS y registrar evidencias. Investigar para resolver y programar, no para generar informes duplicados. No declarar PASS por prueba de otra unidad o SHA.
4. **Persistir** código y prueba por commit CAS en la rama S3 con writer PROPIO adquirido globalmente; verificar CI por run_id, HEAD exacto, conclusión y XML cuando corresponda, o WAIT_CI si sigue ejecutando. Guardar checkpoint útil, liberar inmediatamente writer propio mediante CAS y releer RELEASED; no retener durante CI.
5. **Continuar**: si queda ventana segura y ejecución activa, reevaluar backlog y volver al paso 2; no finalizar por un PASS temprano. Si la unidad está bloqueada, pasar a otra independiente READY o a investigación puntual NUEVA y falsable. Si no existe trabajo seguro, cerrar temprano dejando causa y siguiente experimento; no fabricar commits ni minutos.

## Guardas irrenunciables (no condensar ni omitir al ejecutar)
- Un writer GLOBAL entre manual/Auto/A/B; CAS con expected SHA, owner y epoch. Writer ajeno HELD o CAS incierto: solo READ_ONLY y revalidación; NUNCA takeover ni forzar ref. GitHub Actions en progreso NO equivale a writer ocupado. Effect UNKNOWN: reconciliar, sin replay ciego.
- Respeto al horario de cada slot **programado**, no inicio efectivo: 0–20 trabajo; 20–25 cierre sin tarea larga nueva; 25–27 último margen de checkpoint/release; 27–30 READ_ONLY. Si termina anticipadamente, release inmediato. Corte absoluto, no prórroga. Una automatización de ChatGPT NO garantiza 20 min de runtime ni se autorrelanza por este documento.
- Reutilizar CI PASS previa por SHA/XML; si CI espera, investigar otra unidad independiente autorizada sin duplicar tests. Tras dos ROJOS sin solución, nueva hipótesis o STOP funcional; sin ciclos circulares.
- Main, repositorio histórico y V4.4 intocables; S2 CLOSED 8/8 FROZEN; S3 solamente; S4 OFF y V2 APP OFF; no APIs pagas, credenciales, consentimiento real, firmas ni datos de usuario; catálogo oficial 172, +8 Guardados no aprobadas; una campaña física al finalizar S3.
- Métricas reales: slot programado, started/completed, última mutación, commit, CI/XML, checkpoint, writer release; ausencias = NO_REGISTRADO. `automation completed` NO equivale a `work completed`.

## Primer trabajo retomable, no repetido
S3-A1 PARTIAL. En piloto se produjo código/test `e7bf51be54f6d1f04de912f2a72658b4c148696d` con CI #41622566964 SUCCESS y 46/46 según watchdog. La prueba de Flow NO demuestra conexión al evento real de ACL/generación. Deuda: verificar origen real de invalidaciones en `FaroV5HomeNoticeBindingFactory.kt` y `FaroV5CompositionRoot.java`, test Avisos=3 -> revocación con readPort idéntico -> 'No disponible' sin reconstruir. No volver a investigar la misma hipótesis sin experimento NUEVO. N-031/N-015 permiten investigación puntual mientras A1 esté bloqueada. A2-A5 conservan dependencias; no promoverlas por conveniencia.

## A/B: entrada idéntica y preparación
- A/B únicamente difieren en identidad; mismo documento, cola y branch. Las dos tareas existentes se mantienen **DESACTIVADAS** y con horarios históricos vencidos.
- Prompts breves deben apuntar a este documento, NO duplicarlo ni añadir instrucciones en capas. Actualizar horarios/cola solo con aprobación NUEVA específica en próximo chat. No ejecutar GitHub Actions ni iniciar Noche con esta preparación.
- Antes de activar: READ_ONLY de rama, cola STAGED, writer RELEASED, A/B disabled, confirmar nuevo horario/corte, disponibilidad de una tarea READY y diseño de prueba piloto supervisada. El piloto puede fallar de nuevo: no prometer duración ni productividad.

## Handoff canónico
Puntero: `docs/v5/FARO_CONTINUITY_POINTER.json`; estado: `docs/s3-night/RUN_STATE.json`; cola en ref `faro-v5-night-queue:queue.json`; writer ref `faro-v5-writer-lock:writer.json`. La cola/ref viva prevalece sobre instantáneas documentales desactualizadas.
**Estado esperado después de este cambio: Noche OFF; cola STAGED; A/B OFF; writer RELEASED; ningún horario nuevo aprobado.**

## Corrección operativa — piloto inmediato de una hora (03/11)
- **Solo una orden breve en A/B:** recuperar esta misión, comprobar autorización/cola/writer/HEAD, continuar la siguiente unidad READY de S3 y entregar código+test+evidencia o bloqueo técnico comprobable; A y B difieren solo en identidad.
- No incrustar otra copia de este protocolo en cada prompt ni repetir auditorías. Seguir las etapas de esta misión como fuente única. No confundir una explicación narrativa con progreso.
- Al escribir, usar una operación mínima sobre archivos concretos con control de concurrencia. Un rechazo de seguridad no se soluciona deshabilitando controles, ampliando permisos o repitiendo a ciegas. Reconciliar el estado; seguir con unidad independiente permitida si existe o detener mutaciones.
- **Primera unidad:** conexión real de invalidación de autoridad desde el owner de FaroV5HomeNoticeBindingFactory.kt/FaroV5CompositionRoot.java; prueba de aviso 3 → revocación → «No disponible» en el mismo readPort; los 46/46 anteriores no certifican esa conexión.
- Piloto de 60 minutos únicamente con nueva autorización explícita; inicio/corte vigentes en cola CLOUD. Producción nueva excluye commits de coordinación o documentación. No garantizar duración de ejecución: medir inicio/fin reales y GitHub Actions por SHA/XML.
- Esta sección es una mejora del protocolo, **no demuestra que desapareció el bloqueo de herramientas** ni autoriza S4/V2, cambios en main, aplicaciones pagas, secretos, pruebas físicas o el catálogo +8.
