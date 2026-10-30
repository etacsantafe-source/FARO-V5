# FARO V5 — CIERRE Noche · protocolo operativo V1.1
**Fecha:** 2026-10-30 · **Revisión:** V1.1, ventana operativa de 30 minutos · **Estado:** `DRAFT_DURABLE / INACTIVE / NO_AUTOMATION / NO_CUTOVER`

Este documento registra las reglas acordadas con el usuario y prepara la continuidad entre ejecuciones y chats. **Su existencia no autoriza iniciar una ejecución, crear una programación, modificar producto ni publicar repositorios.** La programación debe ser expresamente aprobada y probada antes de una noche real.

## 1. Objetivo y criterio de cierre

La IA investiga, FARO ejecuta por los contratos autorizados. La misión es cerrar **la etapa explícitamente autorizada**, no acumular tests, tickets, documentación ni nuevos requisitos. La lista de tareas es dinámica; los criterios de cierre no.

Para el bloque inicial S2.A6 se mantienen exactamente ocho criterios:
1. Identidad/admisión de los cinco owners — certificado histórico.
2. FBS2 y restore aislado — certificado histórico.
3. Journal, pointer, rollback, replay, WAL T2 — certificado histórico.
4. F1: Chat/Composer/Review/Apply/Delivery Android extremo a extremo — por certificar.
5. F2: activación y recuperación Android extremo a extremo, V1 protegido — por certificar.
6. F3: cinco stores, exportación/restauración/SAF y consentimiento — por certificar, con decisión humana pendiente S2/S5.
7. F4: regresiones integrales críticas — por certificar.
8. T4: físico — NOT_RUN, lo realizan usuario/equipo cuando estén disponibles.

**No reinterpretar 20/20 tests JVM como cierre F1 ni subir 3/8 sin evidencia de criterio completo.** Si solo resta T4, dejarlo preparado y detener trabajo adicional de cierre: no forzar 8/8, no abrir S3/S4.

## 2. Autorización, alcance y cortafuegos

- Repo exclusivo para futura ejecución: `etacsantafe-source/FARO-V5`, PRIVADO.
- Rama de escritura: únicamente la que autorice expresamente cada sesión, con writer/lease lógico exclusivo y CAS `expected_sha`. Nunca asumir que una rama de un piloto anterior siga autorizada.
- No alterar `main`, `etacsantafe-source/FARO` (histórico de referencia), FARO Auto, V4.4, V1, permisos globales, secretos, firma, privacidad, gastos, ni ejecutar merge o publicación unilateralmente.
- V2 OFF hasta autorización distinta; S3/S4 cerrados.
- Termux no debe ser requisito del modo nube. T4 físico no puede sustituirse por JVM, emulador o simulación.
- GitHub Actions con `contents: read` para compilar/probar; verificar SHA exacto, estado terminal y XML de cada gate. No atribuir test físico a CI.
- F3 conflicto de propiedad del consentimiento/Settings entre S2 y S5: dejar decisión humana pendiente y buscar tareas independientes. No decidir ni implementar S5 para eludirlo.
- Una noche nueva requiere **permiso vigente con fecha/hora de inicio, finalización, repo, rama y alcance**. Un permiso vencido es `NO_WRITE`. No extenderlo por inferencia ni porque una automatización figure enabled.

## 3. Ventanas temporales por ejecución

Objetivo operativo: programación **como máximo horaria**, con sesiones de **30 minutos como máximo objetivo**, medidas desde el inicio efectivo de cada invocación.

- Minuto 0: validar autorización, HEAD, writer, exclusividad, gates en vuelo y estado durable.
- Minutos 0–20: investigar, implementar, probar y encadenar trabajos funcionales útiles.
- Minuto 20: **no iniciar tareas nuevas, implementaciones nuevas ni nuevas Actions**. Continuar únicamente los trabajos que ya estén en curso y cerrar lo que sea seguro.
- Minutos 20–25: recoger los resultados de los trabajos iniciados antes del minuto 20; verificar las Actions terminadas y registrar evidencia.
- **Minuto 25, si una Actions sigue ejecutándose: guardar su identificador y retomarla en la siguiente sesión. No cancelarla automáticamente ni superponer escritores.** No esperar activamente esa Actions hasta el minuto 30: dejarla como `WAIT_CI`, con run ID, SHA y estado conocido, y seguir con cierre durable.
- Minutos 25–30: checkpoint, conciliación de estados conocidos, liberar writer y finalizar la ejecución. Ninguna escritura de producto ni Action nueva.
- Minuto 30: finalizar la sesión. Si la plataforma corta antes, prevalece el estado durable y se retoma bajo otra ejecución autorizada.

**Advertencia operativa:** ChatGPT puede finalizar una ejecución antes de 30 minutos, agotar cuota/contexto o fallar antes de su último checkpoint; la programación horaria no garantiza 30 minutos de cómputo continuo. Antes de operar toda una noche, medirlo en un piloto. No simular trabajo en segundo plano cuando ya terminó el turno.

Además, respetar el corte global de la noche: si vence durante una sesión, cesar nuevas escrituras/Actions, finalizar lo seguro y desactivar la automatización cuando el permiso y la herramienta lo permitan. Los límites globales son más estrictos que el reloj local.

## 4. Bucle de investigación y cierre por objetivos

Repetir **RECUPERAR → INVESTIGAR → DIAGNOSTICAR HUECOS → PLANIFICAR → ESCRIBIR → PROBAR → EVALUAR → CHECKPOINT → CONTINUAR**.

1. Recuperar: comprobar branch HEAD real y último checkpoint de GitHub, no la memoria del chat.
2. Investigar: ¿cuál es el siguiente hueco **indispensable** para un criterio incompleto? Buscar código existente, contratos, pruebas y evidencias; evitar inventar tareas.
3. Priorizar: F1 integrado, F2 real, F3 independiente del conflicto humano, F4 crítica, T4 preparado. Alterar el orden solo por dependencia fundada.
4. Planificar: cambio mínimo, hipótesis contrastable, riesgos, suite concreta y señal verificable. No duplicar microtests certificados.
5. Escribir: solo alcance permitido, revisión de HEAD y lease, commit atómico trazable.
6. Probar: CI dirigida, resultado terminal, SHA y XML exacto. `IN_PROGRESS`, `SKIPPED` o `NOT_RUN` no son `PASS`.
7. Evaluar: VERDE = completar y seguir **en la misma ejecución**; ROJO = investigar y cambiar de hipótesis; AMARILLO = pendiente justificadamente humano o externo, avanzar otro frente independiente.
8. Checkpoint: guardar resultado y siguiente tarea. Revalidar permiso, tiempo, writer y HEAD antes de otra escritura.
9. Si el objetivo sigue incompleto y hay tiempo, **encadenar otra tarea concreta**, no cerrar por un simple VERDE.

## 5. ROJO persistente: investigar sin bucles ciegos

- Primera falla: registrar causa, evidencia, una hipótesis y la corrección mínima segura; repetir.
- Si sigue rojo: investigar más profundamente documentación, integración, dependencias, versiones, arquitectura existente y alternativas; cambiar de hipótesis cuando la evidencia refute la anterior.
- Nuevas fallas **no son una orden automática de detenerse**, pero no repetir la misma corrección sin información nueva, no degradar seguridad, no forzar PASS ni gastar recursos sin provecho.
- Registrar `attempt_id`, cambio, hipótesis, resultado, SHA y run ID. Las pruebas pueden reintentarse cuando exista una causa nueva fundada; evitar duplicados accidentales mediante idempotencia.
- Si un problema no es resoluble aún con las herramientas presentes, documentar `BLOCKED_TECHNICAL` + pruebas/huecos y pasar a un frente independiente. Retomar en la siguiente ejecución **solo si hay autorización vigente**, con hipótesis nuevas.
- Si surge una decisión material, permiso humano, riesgo alto, bloqueo físico o conflicto de autoridad: detener **esa operación**, registrar `WAIT_HUMAN` o `WAIT_PHYSICAL`; continuar únicamente tareas independientes seguras.
- Si no queda ninguna tarea independiente que no requiera humano, finalizar sin inventar trabajo.

## 6. Continuidad ante pérdida de contexto, cuota o interrupción

El chat **NO** es la fuente durable. El estado autorizado, archivos y checkpoint deben residir en GitHub, con metadatos acotados y sin secretos.

- Al iniciar cualquier ejecución/chat, buscar el checkpoint más reciente de la **rama autorizada**, leer solo su índice y los documentos necesarios para el siguiente frente; nunca inyectar todo el historial/chat ni los 541 archivos de V5.
- Guardar checkpoint **al finalizar cada unidad segura**, antes de bifurcar una investigación difícil, después de una prueba terminal importante y al acercarse al minuto 20, y especialmente antes del minuto 25. Si es factible, registrar también avance intermedio aproximadamente cada 10–15 minutos, siempre con escrituras exclusivas/atómicas (no crear commits vacíos recurrentes).
- Los snapshots intermedios contienen: objetivo/criterio, estado de gates, HEAD fuente y de checkpoint, writer/lease, trabajo completado, hipótesis verificadas y refutadas, intentos con SHA/run, bloqueo, `next_action`, hora límite, permisos y evidencia exacta.
- No guardar credenciales, tokens, información personal, prompts sensibles, volcados completos de código/logs ni payloads de Reality. Referenciar enlaces/IDs de evidencia de GitHub redactada; resumen corto, preciso, suficiente para retomar.
- Ante contexto cercano al límite: **compactar el estado en checkpoint**, priorizar una única siguiente acción y finalizar limpio. No continuar explorando solo en memoria sin respaldo durable.
- Ante límite de uso o corte abrupto: no se puede garantizar ejecución ni escritura posterior al corte. La siguiente sesión primero contrasta HEAD, commits y estado Actions; identifica lo que sí quedó publicado, en vuelo o perdido, antes de reintentar.
- Si se publicó código y no se publicó checkpoint, derivar el estado desde Git commits/Action runs; nunca asumir que el último intento no ocurrió. Evitar `double-write` y duplicación de efectos.
- Si Actions queda `queued/in_progress`, registrar run ID (si se conoce) y `WAIT_CI`; la próxima ejecución lo verifica. Jamás declarar PASS anticipado.
- Si un checkpoint se contradice con HEAD o evidencia, prevalece GitHub verificable; entrar `READ_ONLY/CONFLICT` y diagnosticar.
- La programación no se repara ni reinicia sola después de vencer autorización. Un chat nuevo **requiere recuperar contexto, no renovar permisos**.

### Mínimo a leer al reanudar

1. `docs/cierre-noche/PROTOCOL_V1.md` (este contrato).
2. `docs/cierre-noche/LAST_KNOWN_STATE.json` (índice, **no autoridad para escribir**).
3. Último `docs/CLOUD_PILOT_CYCLE_*.md` o checkpoint de la futura rama autorizada.
4. HEAD/commits actuales, runs CI y únicamente archivos relevantes a `next_action`.
5. Autoridad histórica/contratos del criterio afectado si es imprescindible.

La recuperación funciona con un contexto corto, enlaces y hashes exactos, no con memoria narrativa.

## 7. Estado machine-readable y reglas de publicación

El archivo `LAST_KNOWN_STATE.json` mantiene el esquema mínimo de handoff. Es un **ejemplo de base real verificada al 30/10**, no un permiso nocturno ni un estado inmutable. Campos obligatorios: `schema_version`, `status`, `source_repo`, `source_branch`, `source_head`, `authorization`, `stage`, `criteria`, `evidence`, `in_flight`, `blockers`, `hypotheses`, `next_action`.

La siguiente ejecución autorizada **debe revalidarlo**, y escribir en su rama exclusiva su propio checkpoint atómico con HEAD esperado. Nunca copiar este estado ciegamente sobre un estado más nuevo.

Estados posibles: `CERTIFIED`, `IN_PROGRESS`, `PENDING`, `WAIT_HUMAN`, `WAIT_PHYSICAL`, `WAIT_CI`, `BLOCKED_TECHNICAL`, `NOT_RUN`. No declarar `CERTIFIED` a partir de solo compilación.

## 8. Política de cierre y comunicación

El ciclo informa hora, repo/rama/HEAD, trabajo funcional efectivo, CI SHA/XML, criterios X/8, ROJOS investigados (hipótesis nuevas), pendientes humanos/físicos, next_action y estado writer. Si pierde cuota, la falta de reporte NO significa FAIL ni PASS: inspeccionar evidencias reales.

Se detiene cuando: vence autorización o reloj de ejecución; S2.A6 realmente 8/8; solo falta T4 físico; solo restan decisiones humanas; hay riesgo/exclusividad no demostrable; o revocación explícita. Antes de cierre normal: checkpoint, liberar writer, no reiniciar tareas.

## 9. Estado de adopción

- Protocolo y esquema: documentados; **NO es un motor de ejecución certificado**.
- Garantía de 30 minutos continuos en ChatGPT: **NO DEMOSTRADA**.
- Regla v1.1: a los 25 minutos, toda Actions aún activa queda en `WAIT_CI` para la sesión siguiente, sin cancelación automática ni escritores superpuestos.
- Ejecutor periódico: **INACTIVO; cero tareas habilitadas al preparar este documento**.
- La autorización del piloto de la tarde finalizó; **no habilita ejecución nocturna futura**.
- Ningún código de producto ni `main` se modifica mediante este documento.
- S2.A6 en la fuente piloto al cierre del ciclo 02: 3/8; F1/F2/F3 abiertos; F4 pendiente; T4 NOT_RUN; V2 OFF.
