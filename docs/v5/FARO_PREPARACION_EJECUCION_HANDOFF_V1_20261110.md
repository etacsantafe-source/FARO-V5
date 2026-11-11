# FARO V5 — Preparar primero, ejecutar después (Work + manual) — V1
Fecha de adopción: 2026-11-10 Argentina. Aprobación humana conversacional: preparar investigación/auditoría y pasar desarrollo + CI a Work o manual. Estado de este documento: **PROTOCOLO OPERATIVO DOCUMENTAL**, no aprobación automática de código ni cambio del permiso vigente.

## 0. Precedencia y límites
Se suman reglas de coordinación a `AGENTS.md`, `docs/v5/FARO_V2_PROTOCOL_RESTITUTION_AND_RETRO_AUDIT_20261104.md`, el permiso `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` y el puntero de continuidad. Ninguna instrucción de este documento habilita saltar B1–B7, modificar `main`/V4.4, habilitar V2 productiva, S4, Auto o Noche, usar API paga o ejecutar tests físicos fuera de la campaña final. **Un solo writer CAS global** para toda escritura, también documentos; investigación READ_ONLY puede aprovechar la espera de CI si no se escribe. El `status: BLOCKED` actual del permiso permanece intacto.

## 1. Dos roles; una misma regla para Work y manual
- **PREPARADOR (chat/manual o Work dedicado a análisis):** recupera HEAD real, writer, permiso, último checkpoint y CI; identifica la brecha exacta. Completa B1 (evidencia), B2 (paridad/reutilización V4.4), B3 (alternativas externas reales, licencia/costo/mantenimiento), B4 (diseño y archivos), B5 (auditoría adversarial). Puede redactar documentación con writer CAS, pero no otorgar PASS inventados. Elige una unidad pequeña y deja un paquete verificable.
- **EJECUTOR (Work o chat manual):** no repite la investigación certificada de esa **misma unidad y SHA**. Comprueba frescura y permiso; si todo es válido, desarrolla B6, CI exacta, diagnóstico/RED_FIX acotado, B7 independiente, checkpoint y release. Si falta evidencia, no implementa: devuelve un `NEEDS_PREP` concreto, no investigación indefinida.
- La auditoría final B7 nunca se omite. Work no es una autoridad independiente de permisos, y ambos modos tienen exactamente los mismos límites. La preparación puede realizarse mientras otro actor ejecuta CI solo en **READ_ONLY**; nunca habrá dos writers.

## 2. Contrato del paquete de ejecución
Una unidad pasa de `DRAFT` a `PREAUDITED` y solo luego a `READY_FOR_IMPLEMENTATION` si se verifican **todos** estos campos:
1. `package_id`, `goal_id`, `unit_id`, `branch`, `base_head_sha`, `depends_on`, `status`, `version`; referencia al checkpoint/CI vigentes y alcance que NO se debe rehacer.
2. Evidencia B1–B5 con documento/ruta, **blob SHA y marcador** verificables, hipótesis, comparación V4.4 read-only, fuentes externas reales y decisión (USAR/ADAPTAR/REFERENCIA/DESCARTAR).
3. Interfaces/contratos aprobados, owners y generación involucrados, plan de 3–7 pasos, archivos EXACTOS permitidos, cambios prohibidos, riesgos y casos adversariales. No ampliar scope por conveniencia.
4. Criterios binarios de aceptación; tests positivos/negativos y workflows **existentes** a disparar por rama y SHA; comportamiento esperado, nunca resultados PASS antes de correr.
5. Requisitos de rollback, CAS, idempotencia/receipts, ACL, egress y aislamiento DEV cuando apliquen. Dependencias humanas explícitas (por ejemplo tests físicos finales).
6. **Permiso documental acotado y vigente** de la misma `unit_id`, `base_head_sha` y paths, emitido en commit solo documental *después* de B1–B5 y aceptado por el gate. `READY` no se infiere de que existe un Markdown ni sobreescribe `BLOCKED`.
7. `next_action` y criterio de cierre. Prohibido encadenar una unidad siguiente si la actual no tiene B7/cierre o RED_FIX autorizado.

**Entrega mínima**: documento de investigación B1–B5, paquete de ejecución con mapa de archivos y pruebas, permiso preciso, checkpoint/puntero. Preferir referencias a evidencia histórica con SHA sobre copiar textos o recrear trabajos aprobados.

## 3. Guardia de frescura / anti-repetición
Al iniciar B6 el ejecutor lee nuevamente HEAD real, lock, permiso y estado de CI.
- Si HEAD coincide con `base_head_sha` y el permiso/gate coinciden: puede reclamar writer y avanzar.
- Si HEAD cambió: comparar cambios desde base. **Nunca** aplicar código a ciegas. Si afecta contratos, owners, paths, pruebas o permisos de la unidad, marcar paquete `STALE_NEEDS_DELTA_REAUDIT`, revalidar únicamente bloques impactados B1–B5 y emitir nueva base/permiso documental. Si cambios son puramente ajenos, registrar comparación y revalidar preflight exacto antes de ejecutar; la aceptación de SHA anterior jamás se hereda sin evidencia.
- Si otra sesión ya certificó esa unidad por `unit_id` + alcance + SHA/B7, marcar `ALREADY_DONE` y pasar a siguiente dependencia; no recompilar ni reescribir por rutina.
- Si writer ajeno está HELD/UNKNOWN, investigar solo READ_ONLY; nunca tomarlo por fuerza.

## 4. Máquina de estados para el paquete
`DRAFT -> PREAUDITED -> READY_FOR_IMPLEMENTATION -> EXECUTING -> WAIT_CI -> B7_REVIEW -> DONE_VERIFIED`.
Ramas no exitosas: `NEEDS_PREP` (falta B1–B5), `STALE_NEEDS_DELTA_REAUDIT`, `RED_FIX` (misma unidad, hipótesis corregida) y `BLOCKED` (permiso/CI/writer/dependencia). `DONE_VERIFIED` requiere resultado real por SHA/XML, B7 documentado, checkpoint y writer RELEASED. Un paquete READY por sí solo cuenta **cero funcionalidad completada**.

## 5. Tablero de ejecución y comparación Work/manual
Registrar por **turno real**, no por tiempo prometido: `mode` (WORK/MANUAL), `package_id`, `start_head/end_head`, `writer_epoch`, `unit_scope`, `B1_B5_reused_or_delta`, `functional_commits`, `ci_runs`, `ci_pass_fail`, `tests_xml`, `B7_result`, `checkpoint`, `duration_observed_if_available`, `tokens_observed_if_available`, `blocker`.
Medir **unidades cerradas verificadas**, retrabajo (RED_FIX), fallos de CI, duplicaciones evitadas y minutos/tokens observables por unidad cerrada. Si consumo o duración no son observables, escribir `NOT_MEASURED`, nunca estimar límites de Work desde notificaciones. Separar preparación, ejecución y espera de CI para comparar equitativamente. `S3 ~40%` sigue siendo estimación histórica, no KPI calculado por esta tabla.

## 6. Aplicación inicial sin repetir certificados
Primera cola candidata **sin marcar READY todavía**: `S3.A3 Guardados` (mutación con autorización explícita/receipts; integración Chat/Review; navegación exacta a fuente), en unidades pequeñas según dependencias. Reutilizar `S3_A3_BOOKMARKS_READ_HOST_B7_PASS_20261111.md`, contratos tipados 8/8, CAS/receipts Auxiliary y lectura DEV nativa 19/19. Después, unidades autorizadas de A5 X01–X12 todavía abiertas, reservando pruebas Moto G15 para campaña física final. Ningún paquete de esta cola constituye por sí mismo nueva autorización.

## 7. Criterio para dar por consolidado este método
Probar **una unidad real** con paquete READY + Work o manual, observar B6/CI/B7/cierre con writer RELEASED, registrar métricas y sólo entonces evaluar mejoras automáticas adicionales. La adopción documental de este método no es certificación de productividad ni cierre de A3.
