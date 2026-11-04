# FARO V5 — RESTITUCIÓN OBLIGATORIA DEL PROTOCOLO Y AUDITORÍA RETROSPECTIVA DE V2
Fecha: 04/11/2026 (Argentina).
Estado: **PROTOCOLO MANUAL RESTITUIDO; AUDITORÍA RETROSPECTIVA PENDIENTE; SIN NUEVO CAMBIO FUNCIONAL EN ESTE CHECKPOINT.**
Objetivo único vigente: V2 mínima **de desarrollo aislado** → Chat/Workspace real → cerrar S3 → cerrar S4 → eventual decisión humana separada para V2 productiva.

## 0. Mandato humano y precedencia
El usuario reclamó explícitamente recuperar el protocolo de desarrollo completo y revisar lo ya implementado sin respetarlo. Este archivo es el mandato vigente para el **trabajo manual** y prima, en este alcance, sobre el estado histórico «propuesta/no activado» de:
- `docs/v5/FARO_DEVELOPMENT_UNIFIED_PROTOCOL_V2_20261102.md`;
- `docs/s3-night/FARO_S3_NOCHE_BLOQUES_DINAMICOS_V1_20261103.md`;
- `docs/v5/FARO_V2_MINIMUM_CORE_UNBLOCK_PLAN_20261104.md`.

**Activar el protocolo manual NO activa FARO Noche, Auto, tareas programadas, la V2 de producción ni un nuevo permiso.** La cadena A/B temporal y sus horarios no se aplican al desarrollo manual; se reutiliza solo la lógica de los siete bloques dinámicos. Permisos humanos anteriores se conservan, jamás se amplían. `Continuar FARO V5` retoma el checkpoint vivo, el objetivo y **primero la auditoría retrospectiva**, sin exigir repetirle las instrucciones al usuario.

## 1. Siete bloques por unidad: condición verificable de entrada y salida
1. **EVIDENCIA:** leer HEAD/CI/XML/contratos/código de la unidad, checkpoint y restricciones. Listar prueba exacta, brecha y criterio binario. No usar informes históricos como si fueran hechos vigentes.
2. **COMPARACIÓN:** comparar contra el **comportamiento V4.4** (solo lectura), V5 ya implementado y soluciones externas pertinentes. Anotar activos reutilizables y diferencias; si V4.4 no aplica, justificar con evidencia, no omitir en silencio.
3. **INVESTIGACIÓN:** antes de escribir, revisar alternativas razonables (D-021: código/librerías/patrones/docs/open source), y clasificar USAR / ADAPTAR / REFERENCIA / DESCARTAR / SIN CANDIDATO MATERIAL según seguridad, madurez, mantenimiento, licencia, simplicidad, dependencias, costo y compatibilidad offline. Formular hipótesis falsables y decidir con pruebas.
4. **UNIFICACIÓN:** DESIGN ONCE / DELTA ONLY / NO REPEAT. Elegir una solución sin duplicar owners ni reabrir trabajo cerrado; relacionar archivos, riesgos, pruebas y dependencias.
5. **AUDITORÍA PREVIA ADVERSARIAL:** antes de nuevos cambios de código revisar integridad, generation/pointer/journal, rollback, crash/restart, writer, ACL/identidad, egress, Android components, secretos, datos locales, permisos y regresiones relevantes. Corregir los huecos concretos en el plan y definir negativas. Sin auditoría global por inercia.
6. **IMPLEMENTAR + PROBAR:** únicamente la unidad aprobada y mínima, writer global CAS con HEAD/epoch y liberación temprana; CI exacta con SHA, run y XML. RUNNING/WAIT_CI jamás es PASS. Tras dos fallos de la misma hipótesis, revisar la causa en B2/B3/B5 en lugar de ensayo y error.
7. **AUDITORÍA FINAL INDEPENDIENTE:** contrastar aceptación, archivos/diff, CI/XML, riesgos y prohibiciones; cerrar como DONE_VERIFIED solo lo que realmente pasó, o dejar WAIT_CI/BLOCKED/READY_FOR_SINGLE_PHYSICAL_CAMPAIGN. Si hay rojo, regresar únicamente al bloque indispensable y revalidar lo afectado.

**Saltos:** solo cuando existe evidencia **de la misma unidad, alcance y SHA**; aprovechar partes PASS y no repetir ensayos por rutina. La evidencia de SHA histórico certifica ese alcance histórico, no el HEAD posterior. Si un gate no está documentado => NOT_VERIFIED. Auditar el legado sin tocarlo primero.

## 2. Estado vivo comprobado al restaurar (releer siempre al reanudar)
- Repo `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`, HEAD `ae5079194d11bf658e85ff9acb7e305cbbb24173`.
- Writer `faro-v5-writer-lock:writer.json` **RELEASED epoch 141**. CLOUD queue `faro-v5-night-queue:queue.json` **STAGED** / `activation_authorized=false`. Revalidar ambos antes de escrituras.
- S2 CLOSED 8/8 (100%), **congelada**. S3 estimación histórica ≈40% desarrollado (35–45%, no certificación), cierre **0/5 (0%)**. S3.A2 gates 0/5; S4 OFF. Catálogo 172.
- V2 productiva OFF; Auto OFF; Noche OFF; sin APIs pagas, ISO/IRAM, main/V4.4/legacy escritos ni campaña física.
- CI M1 `d2a41acb7dc63429a0075adc222807b02e8ee502` Actions `#41864185753` SUCCESS `V2_CORE_M1_ISOLATION_PASS 4/4`; S3 regresión sobre `ae507919`, `#41872895353` SUCCESS.
- CI de integración **actual** `ae507919`, Actions `#41872895263`: **FAILURE GLOBAL**. El log sí demuestra `V2_CORE_M2_M3_LOCAL_CHAT_JVM_PASS 4/4` y `M2_DEBUG_T4_RELOCATED_XML_PASS 7/7`. Paso de manifest FAIL: `V2_CORE_M1_MANIFEST_AMBIGUOUS debug 2`; **no** afirmar `V2_CORE_M1_ISOLATION_PASS` ni `V2_CORE_M2B_DEV_APP_ISOLATED_PASS` para el HEAD actual.
- No existe evidencia de instalación/prueba física de esta V2 aislada ni de V2_DEV_CORE_READY.
- El «≈70% de V2» comunicado previamente fue **juicio orientativo no respaldado por denominador/gates**, no métrica certificada ni razón para saltar un bloque.

## 3. Matriz de auditoría retroactiva, SOLO lectura antes de correcciones
| Unidad histórica | Reutilizable probado | Incumplimiento/gap pendiente del protocolo | Entrada próxima |
| --- | --- | --- | --- |
| **M0** inventario/dependencias | Documento `FARO_V2_MIN_CORE_M0_M1_20261104.md`; identificación 4→5 owners, preflight y flags | Comparación concreta de flujo V4.4 vs V2 y evaluación documentada de alternativas no probadas en los siete bloques; confirmar que S4 no es dependencia bloqueante con evidencia de rutas | B1 → B2 → B3 → B4/B5. No rediseñar todavía |
| **M1** paquete/aislamiento | Commit `d2a41acb` y CI `#41864185753` 4/4 de aislamiento histórico | Reauditar integración de `AndroidManifest.xml` actual, fusión de providers/launchers, Internet y backup; run actual falla por selección ambigua de dos manifests `debug`; no debilitar prueba eligiendo arbitrariamente | B1/B5, preservar M1 PASS histórico |
| **M2.A** 5 owners activos | Código `FaroV5V2CoreDevAuthority.kt`; 4/4 JVM conjuntos actuales con M3 | Riesgo a investigar: tras journal creado `PREPARING`/stage parcial, `journal.pending().isEmpty()` bloquea el primer arranque y la ruta de recuperación actual solo contempla estados posteriores; no asumir recuperación de todos los crashes. Auditar quiescence, marker, fence, rollback, exclusividad y ruta física | B1 → B2/B3 → B5; pruebas negativas nuevas solo si faltan |
| **M2.B** launcher DEV | Activity/Application y manifest dev `ae507919`; compilación en CI actual; app normal V2 global no fue activada | Manifest final de SHA actual **NO CERTIFICADO**, log `AMBIGUOUS debug 2`; auditar componentes exportados, proveedor auto-start, UID, separación del root, backup/egress, lifecycle y persistencia de callback | B1 → B5 → B6 mínimo → B7 |
| **M3** Chat local | Envío USER/ACCEPTED persistente sin respuesta FARO artificial incluido en 4/4 JVM; puertos read/selection/composer | No confundir Chat local con IA real. Faltan adversariales de puntero stale, reingreso, cierre/reinicio, ACL de Buscar (mantener Search UNAVAILABLE), no Apply/model/OS, interfaz Android física y auditoría final | B1 → B2/B3/B5; mínimos B6/B7 |
| **M4/M5** seguridad/certificación global | No consta cierre | Pruebas negativas integrales, errores de recuperación, CI exacta del manifest completo y una sola campaña física. `V2_DEV_CORE_READY` únicamente si gates M1–M5 resultan PASS | READY después de retro-auditar M0–M3 |

**Criterio del recorrido:** clasificar para cada unidad sus siete bloques como EVIDENCED / PARTIAL / MISSING con pruebas y SHA. Lo que esté EVIDENCED se conserva; completar primero B1–B5 de lo incompleto **sin escribir código**. Sólo luego permitir B6 selectivo. B7 independiente al cerrar cada unidad. **No convertir esta revisión en auditoría general interminable**: un inventario acotado con salidas binarias y un único plan de corrección.

## 4. Secuencia operativa al abrir chat nuevo
1. Recuperar este documento, `FARO_CONTINUITY_POINTER.json` y `FARO_V2_PROTOCOL_RETRO_AUDIT_20261104.json`; reconsultar HEAD, writer, cola, Actions y permisos **en vivo**.
2. Empezar por **RETRO-01 M0/M1 — B1 evidencia y B2 comparación V4.4**, aprovechando pruebas PASS del SHA histórico y registrando explícitamente las brechas. Después B3/B4/B5 y M2/M3. Encadenar varios bloques seguros dentro del turno, pero detener escritura funcional hasta que exista auditoría previa completa para su unidad.
3. Corregir el selector de manifest solo tras verificar con evidencia las rutas duplicadas y diseñar una selección robusta; no rehacer M2/M3 4/4 ni T4 7/7 a ciegas. Si el workflow cambia y amerita CI nueva, dirigir únicamente lo afectado, con SHA/XML.
4. Mantener writer CAS único; si ajeno HELD/UNKNOWN, READ_ONLY; sin forzar ni absorber writer ajeno; liberar tras commit y no durante CI.
5. Al cerrar cada turno, dejar por unidad `goal_id,unit_id,block_id,block_status,expected_head,acceptance,evidence,next_step,blocker,writer_release_epoch,last_observed_at`. Actualizar documento y **puntero final**, verificar su HEAD remoto. Estado incompleto si falla.
6. Reportar **S2 100% (8/8), S3 ≈40% desarrollo estimado / 0% cerrado (0/5)** con los cambios realmente observados, progreso de V2 por gates (no el «70%» inexacto) y termómetro cualitativo.

## 5. Restricciones que no se renegocian
- Una sola rama funcional y writer CAS, **cero** escrituras en main, V4.4, Auto histórico, repos antiguos o datos de app habitual.
- S2 8/8 congelada; S4 no se abre; catálogo 172, Guardados +8 solo revisión; no APIs pagas, secretos, credenciales ni firmware/persistencia física sin interacción humana.
- No activar V2 productiva, FARO Noche, FARO Auto ni automatizaciones por heredar documentos.
- No repetir trabajo terminado, no inventar PASS, porcentajes de CI ni productividad, no prometer revisión de fondo que aún no fue hecha.
- Objetivo principal vigente: **V2_DEV_CORE_READY con seguridad probada y luego cerrar S3 y S4**.

**Checkpoint de este documento:** protocolo recuperado, hallazgos iniciales registrados, revisión completa de B1–B7 del legado **NOT_DONE**. La siguiente conversación debe efectuar ese recorrido, no afirmar que ya terminó.


## 6. Regla explícita de B3 externo verificable — 04/11/2026
Por instrucción humana, la investigación de **soluciones ajenas y errores ya resueltos** es obligatoria y central; inspeccionar solo FARO/V4.4 no cumple B3.
Para **cada unidad antes de emitir permiso READY**: 1) consultar documentación primaria actual y proyectos de aplicaciones reales, bugs/fixes, o experiencias open source pertinentes; 2) registrar URLs concretas, mecanismo, límites, versiones/fechas si importan, seguridad, licencias, mantenimiento, recursos y coste; 3) comparar al menos dos alternativas razonables con el diseño FARO y con V4.4 (o demostrar por qué solo hay una); 4) clasificar USAR / ADAPTAR / REFERENCIA / DESCARTAR / SIN CANDIDATO MATERIAL; 5) convertir errores observados en pruebas negativas; 6) B5 rechaza READY si falta evidencia material. La ausencia de ejemplos comparables requiere búsqueda documentada, **nunca inventar resultados**.
La referencia S3.A2 actual es `docs/s3-night/S3_A2_G1_B3_EXTERNAL_REAL_WORLD_RESEARCH_20261104.md`. El mero cumplimiento documental no autoriza B6: continúan el owner real, el B5 adversarial y el permiso por rutas pendientes.
