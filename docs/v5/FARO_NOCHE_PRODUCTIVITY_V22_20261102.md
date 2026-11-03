# FARO Noche V2.2 — Banco de oportunidades y espera útil
**Fecha:** 2026-11-02. **Estado:** PREPARED_NOT_ACTIVE / PROTOCOL_ONLY / TESTS_NOT_RUN. Este documento NO activa automatizaciones ni autoriza cambios funcionales. Conserva Noche V2.1 como referencia histórica.

## Objetivo verificable
Si una unidad obtiene PASS a los 7 minutos, no cerrar el turno por ese solo motivo. Durante la fase productiva programada, continuar con otra unidad de trabajo realmente útil y segura que reduzca tiempo de desarrollo, escritura, diagnósticos o regresiones en el siguiente turno. Una tarea programada de ChatGPT puede finalizar antes de los 20 minutos: el protocolo NO garantiza duración ni dispone un mecanismo de autorrelanzamiento fuera de schedules autorizadas.

## Autoridad inmutable
- Repo etacsantafe-source/FARO-V5, rama work/s3-night-20261102; **main y V4.4 sin escritura**.
- S2 CLOSED 8/8 y congelada; S3 abierta; **S4 OFF**, app **V2 OFF**.
- Cola única CLOUD `faro-v5-night-queue:queue.json` y writer global `faro-v5-writer-lock:writer.json`; uno solo, CAS con expected SHA + ownership. No asumir que la cola LOCAL coordina hosts.
- Activación solo con autorización NUEVA de alcance, horario y deadline, cola `mode=ENABLED, activation_authorized=true` y schedules habilitadas/verificadas. Al preparar: STAGED y OFF; jamás reutilizar el corte anterior 22:00 del 02/11.
- Guardados +8: **REVIEWED_PROVISIONAL_NOT_APPROVED**. Catálogo oficial **172**, ninguna modificación de A3/registry ni aprobación tácita.
- Sin APIs pagas, egreso/credenciales o consentimientos implícitos. Sin pruebas físicas hasta campaña única final. No reabrir ni recertificar S2 por rutina.

## Planificador de oportunidades (obligatorio dentro de cada turno)
Mantener **3 a 5 unidades** preseleccionadas con campos `opportunity_id,kind,scope,depends_on,approval_needed,writer_needed,expected_head,acceptance,estimated_size,status,evidence,artifact_path`. `kind`: IMPLEMENT, RESEARCH, ADVERSARIAL, VERIFY_EXISTING_CI. Estados: READY, BLOCKED, IN_PROGRESS, DONE_VERIFIED, RESEARCH_ONLY, WAIT_HUMAN, WAIT_WRITER, WAIT_CI. Una investigación no convierte una dependencia en PASS.
Orden de prioridad:
1. Trabajo primario desbloqueado/autorizado, con criterio binario y owner real.
2. Continuación inmediata tras PASS: siguiente subtarea del mismo objetivo, luego otra READY de S3 que no dependa de gates sin cerrar; requiere writer + CAS.
3. Trabajo aislado de código S3 con alcance explícito aprobado y aceptación verificable, aun si otra etapa tiene dependencia bloqueada. No adelantar A2-A4 dependientes ni ejecutar A3 sin decisión sobre catálogo.
4. Investigación READ_ONLY de bloqueo concreto: hipótesis verificable, código/owner exacto, comparación puntual V4.4 o documentación oficial, problema identificado y siguiente experimento seguro.
5. Auditoría adversarial **dirigida** a un cambio o flujo reciente: nueva hipótesis de recuperación/ACL/privacidad/accesibilidad/identidad/tests, con hallazgo reproducible. Prohibido reauditar globalmente sin descubrimiento nuevo, hacer commits decorativos o disparar Actions por ocio.
Reevaluar la cola **tras cada unidad** mientras haya tiempo productivo. No terminar porque una prueba salió verde, salvo que no haya más trabajo seguro, exista riesgo o el runtime termine.

## Oportunidades S3 iniciales sin aprobación funcional nueva
- OPP-N031: localizar owner canónico `attention.read`, origen de asuntos, ACL, dedupe y validación; prohibido aproximar con `hasPendingReview`.
- OPP-N015: rastrear `history.query` paginada/receipt canónico; lookup por ID NO es historial.
- OPP-GUARDADOS: hipótesis y pruebas MessageRef/restauración/revocación/idempotencia para las 8 CANDIDATAS; **solo investigación**.
- OPP-A2: pruebas diseñadas de SQLite FTS español/acento/offline/cobertura por owner y ACL; **no implementar A2 antes de gates**.
- OPP-A1: test adversarial nuevo de Inicio y permisos/generación, sin repetir el XML 45/45 ya verde por SHA.
Las oportunidades no se añaden automáticamente como tareas ejecutables ni modifican el backlog de siete tareas hasta autorización/validación de dependencias.

## Recuperación del acuerdo 31/10: GitHub ocupado ≠ cancelar
Distinguir **writer HELD** de **GitHub Actions QUEUED/IN_PROGRESS**:
- **Writer de otro owner HELD o CAS incierto**: no escribir, no reclamar tarea de escritura, no crear Actions ni hacer takeover por lease vencido. Investigar READ_ONLY hasta **5 minutos como máximo para revalidar ocupación**, respetando siempre el reloj programado. Volver a consultar writer ref+owner+epoch, queue, HEAD, CI. Si sigue HELD, detener mutaciones, conservar tarea OPEN/WAIT_WRITER y seguir con investigación útil READ_ONLY hasta el minuto 20 si hay hipótesis; nunca liberar writer ajeno ni forzar ref.
- **CI en ejecución**: registrar run_id/SHA/workflow, consultar progreso sin lanzar un duplicado, preparar análisis o pruebas READ_ONLY; **CI en vuelo no implica writer ocupado**. Si el writer está efectivamente libre, se puede desarrollar otra unidad autorizada y **genuinamente independiente**, con CAS y HEAD propios, sin mutar el resultado de la CI. Si no, solo READ_ONLY. Al concluir CI validar conclusión y XML exacto, no solo build success.
- Si tras 5 minutos persiste la espera, **no cancelar la tarea por estar ocupada**: checkpoint con WAIT_CI/WAIT_WRITER y reanudar siguiente sesión. No consumir tiempo con bucles de polling sin investigación. Effect UNKNOWN => reconciliar antes de reintentar; prohibido replay ciego.
- La espera se corta antes si llega el final de 0–20 o deadline. Nunca desplazar horarios por inicios tardíos.
- A minuto 25 registrar Actions vivas con run IDs/SHA sin cancelarlas. Writer propio: release solo CAS, sin solapar sesiones.

## Distribución temporal inalterada
Minutos desde el horario **PROGRAMADO**, no desde inicio real:
- `0–20`: trabajo primario + banco + READ_ONLY útil ante impedimentos. No garantizar 20 minutos reales de runtime.
- `20–25`: terminar unidad, recopilar evidencia/CI, NO iniciar tarea ni Action nueva.
- `25–27`: checkpoint durable y liberación de writer propio por CAS; verificar relectura.
- `27–30`: solo READ_ONLY. Corte autorizado absoluto; detención y deshabilitación de A/B/informe + cola STAGED sin borrar backlog.
En caso de error de permisos, conflicto CAS, owner UNKNOWN, egreso no autorizado o consentimiento: fail-closed, sin maquillaje de productividad.

## Métricas honestas
Registrar marcas reales `scheduled_for,started_at,last_mutation_at,checkpoint_at,writer_release_at,completed_at` y fuentes; valores faltantes => `NO_REGISTRADO`. Clasificar minutos no solapados entre código, investigación con evidencia, adversarial útil, tests, CI en espera, writer en espera, cierre y sin actividad.
- **Utilización de ventana productiva** = minutos útiles observados dentro del intervalo 0–20 / 20. Estándares aspiracionales: 60–75% aceptable, 75–90% ideal, 90–100% excepcional.
- **Uso del runtime disponible** = minutos útiles observados / minutos reales de ejecución hasta cierre, separado de la primera medida.
- **Resultado real** = unidades verificadas y problemas futuros evitados con evidencia; una auditoría sin hipótesis ni hallazgo no cuenta. No inferir 90% por tener el writer tomado.
Reportar causas de cierre anticipado y límites de plataforma, sin atribuir al agente tiempo que no tuvo.

## Pruebas V2.2 pendientes: NO_PASS, NO_RUN
V22-01 PASS temprano encadena siguiente READY; V22-02 bloqueo A1 conduce a N031/N015 READ_ONLY sin adelantar A2-A4; V22-03 descarta auditoría repetitiva; V22-04 implementación independiente exige autorización + CAS; V22-05 HELD/UNKNOWN bloquea writes; V22-06 fase 20 y liberación 25-27; V22-07 reutiliza PASS por SHA/XML; V22-08 runtime temprano registra limitación y no inventa tiempo; V22-09 Guardados no aprobado => solo investigación; V22-10 cancelación deshabilita A/B/informe y cola STAGED; V22-11 writer ajeno espera útil 5 min/revalida, no force; V22-12 CI activa no se duplica/cancela; V22-13 writer aún ocupado permite continuar investigación READ_ONLY útil hasta minuto 20; V22-14 CI activa+writer libre permite unidad independiente autorizada, no dependiente; V22-15 al corte conservar Actions en vuelo y WAIT_CI/WAIT_WRITER sin replay.

## Antes de activar en próximo chat
1. Releer checkpoint HEAD, puntero de continuidad, cola CLOUD, writer/ref y automatizaciones; verificar que V2.2 está presente y **sigue NO ACTIVA**.
2. Validar sin tocar funcionalidad las pruebas V22-01..15 o declarar cuales permanecen NOT_RUN. La revisión documental no certifica el scheduler.
3. Pedir autorización explícita **nueva** de horario de inicio, alcance y corte. Habilitar cola por CAS y A/B/informe solo con ese consentimiento; nunca reciclar old deadline.
4. Verificar writer RELEASED al cierre y que main/V4.4, S2, catálogo y app V2 permanezcan intactos.
