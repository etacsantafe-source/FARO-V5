# FARO V5 — Noche S3 por bloques dinámicos V1

**Fecha:** 2026-11-03 (Argentina).  
**Estado:** DISEÑADO / NO ACTIVADO / DOCUMENTO LOCAL.  
**IMPORTANTE:** Este documento NO fue incorporado a GitHub. La operación de adquisición del writer global fue bloqueada por la herramienta. No tratar este archivo como fuente remota certificada ni como autorización de ejecución.

## 1. Misión

Finalizar **todo el desarrollo automatizable de S3** y dejar exclusivamente las pruebas físicas imprescindibles de la campaña final y las decisiones humanas que no estén aprobadas. No declarar S3 CLOSED sin evidencia física. Se busca recuperar la continuidad de la Noche S3 original, sin complicar el prompt ni agregar coordinadores.

**Unidad de trabajo:** una subunidad concreta de S3, no toda S3 a la vez. Los siete bloques describen fases de esa subunidad. A y B usan la **misma misión**, cola y checkpoint; difieren solo en su identidad.

## 2. Bloques dinámicos

1. **EVIDENCIA:** releer HEAD/branch, checkpoint, especificaciones, código, tests, CI/artefactos, bloqueos y decisiones. Identificar lo demostrado y lo pendiente. Salida: inventario mínimo con referencias, SHAs y aceptación faltante.
2. **COMPARACIÓN:** comparar V5 vs. V4.4 y soluciones externas/documentación pertinente, **READ_ONLY**, solo donde la comparación puede cambiar la implementación. Salida: diferencias y activos reutilizables o justificación de no aplicabilidad.
3. **INVESTIGACIÓN:** formular y comprobar incógnitas técnicas con hipótesis falsables, archivos concretos y criterios de decisión. Salida: solución fundamentada o bloqueo nuevo verificable.
4. **UNIFICACIÓN:** reconciliar evidencia, comparación e investigación en un enfoque de implementación sin duplicados ni contradicciones. Salida: plan de archivos y pruebas y dependencias satisfechas.
5. **AUDITORÍA PREVIA:** buscar huecos adversariales sobre autorización, identidad, ACL, generación, recuperación, privacidad, regresiones, recursos y accesibilidad según la unidad. Salida: hallazgos específicos resueltos/planteados con prueba; no auditoría global rutinaria.
6. **IMPLEMENTAR Y PROBAR:** producir código y tests reales, commit en rama S3 con writer propio, CI por run_id y SHA, corregir ROJOS. CI pendiente => WAIT_CI, no PASS. Salida: commit(s), resultados y XML o bloqueo demostrado.
7. **AUDITORÍA FINAL:** contrastar implementación contra aceptación, tests, CI/XML, casos adversariales y restricciones. Si falla, volver **solo al bloque necesario**. Si todo lo automatizable de la unidad está certificado, marcar DONE_VERIFIED o READY_FOR_SINGLE_PHYSICAL_CAMPAIGN según corresponda; no declarar cierre físico.

### Saltos y reingresos

- **Saltar** bloques que ya tienen evidencia válida **para la misma unidad, alcance y SHA**. No dar por verificado un bloque por narrativa o CI de otro commit.
- Cambios de SHA que afectan contratos/alcance pueden **invalidar selectivamente** evidencias previas, pero no obligan a repetir toda la investigación.
- Si se detecta problema en auditoría final, reingresar al bloque que lo resuelve y luego revalidar solo lo afectado.
- Si se aprueba temprano un bloque y hay runtime autorizado, **continuar** con el siguiente bloque o la siguiente unidad READY; no esperar al siguiente turno por regla.
- Si CI está ejecutándose, liberar writer y realizar trabajo independiente real o investigación dirigida READ_ONLY; jamás duplicar la CI ni tomar un PASS futuro por anticipado.
- Si no hay trabajo seguro, registrar impedimento verificable, liberar escritor propio y terminar, sin inventar progreso o tiempo ocupado.

## 3. Ventanas A/B y límite temporal

- Se requieren **dos automatizaciones separadas**, A y B, cada una como máximo **una vez por hora**, desfasadas 30 minutos.
- Para cada slot, medir **desde el horario programado**, no desde el inicio efectivo del agente:
  - `0–20`: desarrollo y encadenamiento dinámico.
  - `20–27`: finalizar operación en curso, checkpoint, evidencia, liberación de writer; **no iniciar trabajos ni CI nuevos**.
  - `27–30`: solo READ_ONLY y margen.
- Terminar antes del minuto 27 si la ejecución concluye antes. Un turno terminado prematuramente **no asegura trabajo hasta 20 minutos**; registrar su runtime observado.
- Al minuto 30 comienza el slot opuesto **solo si fue programado y autorizado**. Se verifica writer+epoch+cola; un turno no presume que el anterior terminó por el reloj.
- Corte absoluto de piloto/noche independiente de cada slot; tareas desactivadas y cola STAGED antes del cierre si se puede adquirir writer propio de forma segura. Tras el corte, **no escribir GitHub**; informe final READ_ONLY.
- Horarios nuevos solo por **aprobación explícita del usuario**. Este documento **no autoriza horarios** ni activación.

## 4. Writer y checkpoint

- Un **writer GLOBAL** incluye trabajo manual, Auto, A y B. Antes de mutar, revalidar cola CLOUD `faro-v5-night-queue:queue.json`, HEAD, propietario, epoch y writer `faro-v5-writer-lock:writer.json`.
- Adquisición/liberación solo con CAS/expected SHA, propietario/epoch propios y relectura del resultado. **Writer ajeno HELD/UNKNOWN: nunca takeover, nunca force sin validación, solo READ_ONLY**; revalidar hasta 5 minutos si hay trabajo útil.
- No mantener writer durante CI, lectura o espera. GitHub Actions `IN_PROGRESS` **no significa** writer HELD.
- Persistir checkpoint por **unidad y bloque**, con información suficiente para continuar sin repetir. No crear un esquema paralelo incompatible con RUN_STATE/cola vivos.

### Checkpoint lógico mínimo

```json
{
  "goal_id": "G-S3-AUTO",
  "unit_id": "S3-A1",
  "block_id": 6,
  "block_status": "WAIT_CI",
  "expected_head": "SHA_EXACTO",
  "acceptance": "criterio binario de la unidad",
  "evidence": ["archivo", "commit", "run_id", "xml"],
  "next_step": "Verificar CI, retomar bloque 7",
  "blocker": null,
  "writer_release_epoch": 0,
  "last_observed_at": "timestamp_real"
}
```

Estados admitidos por subunidad/bloque: `READY`, `IN_PROGRESS`, `WAIT_CI`, `WAIT_WRITER`, `WAIT_HUMAN`, `BLOCKED`, `DONE_VERIFIED`, `READY_FOR_SINGLE_PHYSICAL_CAMPAIGN`. No promover un WAIT_CI a DONE_VERIFIED.

## 5. Protocolo de desarrollo y restricciones que permanecen

Aplicar el protocolo de desarrollo completo **en cada unidad según necesidad**: investigar, comparar V4.4, detectar/cerrar huecos, implementar, probar, corregir, verificar, documentar evidencia. No repetir auditorías ni benchmarks por rutina. Dos intentos dirigidos fallidos sobre una misma hipótesis como máximo sin evidencia nueva; buscar nueva causa o detener la unidad.

- **S2:** CLOSED 8/8 FROZEN, no reabrir.
- **S3:** único alcance funcional autorizado para futuro piloto, una unidad a la vez.
- **S4:** OFF. **App V2:** OFF.
- **Ramas:** código solo `work/s3-night-20261102`. `main`, FARO histórico y V4.4 **solo lectura**.
- **Catálogo oficial:** 172 operaciones. Las 8 candidatas de Guardados **NO APROBADAS**; no activar, añadir o inferir aprobación humana.
- Sin APIs pagas, credenciales, firmas, consentimiento implícito, exposición de datos reales ni cambios en el teléfono físico.
- Una **única campaña física** al final; la automatización no declara haber hecho pruebas físicas.
- Conservar resultados PASS por SHA y XML exactos; no reutilizarlos como certificación para código nuevo.

## 6. Métricas

Registrar por sesión: `scheduled_for`, `started_at`, `completed_at`, últimas operaciones de código/test/checkpoint, `writer_release_at`, CI run_id, SHA, conclusión y XML; clasificar observaciones de trabajo real **solo si existen timestamps comparables**. Faltantes => `NO_REGISTRADO`.

Separar claramente:
1. Duración ejecutándose el agente: `completed_at - started_at`.
2. Tiempo efectivamente productivo observado: intervalos no superpuestos de investigación útil, código, pruebas, correcciones y verificación respaldados por evidencia.
3. Producción: unidades/bloques verificados, commits de código/test, CI válido, duplicaciones evitadas.

No contar esperas de Actions como escritura ni confundir ventana autorizada de 20 minutos con runtime garantizado.

**Referencias de comparación operativa:** Noche S3 anterior ~38/80 minutos observados = 47,5% de duración, no trabajo efectivo cronometrado. Objetivos V1: 7m12s/60 = ~12% de duración, con un turno A detenido por writer ocupado y B con corrección + CI. Estas cifras son baselines, no garantías.

## 7. Fuente de verdad y documentos

- Protocolo de desarrollo: `docs/v5/FARO_DEVELOPMENT_UNIFIED_PROTOCOL_V2_20261102.md`
- Estado/checkpoint: `docs/s3-night/RUN_STATE.json`
- Backlog: `docs/s3-night/S3_BACKLOG_20261102.md`
- Puntero de continuidad: `docs/v5/FARO_CONTINUITY_POINTER.json`
- Protocolo anterior: `docs/s3-night/S3_NIGHT_OBJECTIVES_V1_20261103.md` en `docs/s3-night-goals-v1-20261103` (histórico, no prevalece sobre esta propuesta futura).
- Cola viva: rama `faro-v5-night-queue`, archivo `queue.json`.
- Writer vivo: rama `faro-v5-writer-lock`, archivo `writer.json`.

En todo conflicto entre instantánea documental y GitHub real: prevalecen HEAD/queue/writer/CI vivos.

## 8. Handoff desde 2026-11-03 12:53 AR (verificar en vivo antes de activar)

**Último estado VERIFICADO, no promesa de vigencia futura:**

- Rama funcional `work/s3-night-20261102`, HEAD `9a7d0537138756987bbb4760068b93b7f338160e`.
- CI `#41656343364`, SUCCESS en ese SHA; pruebas dirigidas 5/5 según informe previo. No implica S3-A1 terminada.
- S3-N00 PASS (con salvedad de concurrencia), S3-P00 PASS_SPEC_ONLY, S3-A1 IN_PROGRESS. S3-A2/A3/A4/A5 pendientes. Próximo punto: ACL/generación independientes desde owner real, además de fuentes canónicas restantes.
- Cola CLOUD `STAGED`, `activation_authorized=false`; cutoff anterior expirado.
- Writer `RELEASED`, epoch `53`.
- Automatizaciones A, B, watchdog e informe todas **OFF**. Se preparó un esquema histórico simple 12:46/13:16, pero **nunca se activó**; esos horarios ya no constituyen autorización.
- Intento de guardar este protocolo en GitHub: el control de seguridad **bloqueó la operación CAS de adquisición del writer**. Ningún archivo de GitHub fue escrito como parte de este diseño. Es intencional conservar todo OFF.

## 9. Prompt ejecutivo corto sugerido para A/B

> Continuá FARO V5 S3 desde el checkpoint vivo. Objetivo: terminar todo lo automatizable de S3 y dejar solo pruebas físicas/decisiones humanas. Seguí este protocolo de bloques dinámicos: omití bloques ya verificados y encadená los pendientes mientras haya runtime seguro. En el turno programado, 0–20 desarrollo, 20–27 checkpoint/cierre y 27–30 solo lectura. Respetá la autorización y el corte, writer único CAS, rama S3 y restricciones vigentes. Guardá pruebas/CI/SHAs y liberá tu writer al terminar. No repitas trabajo ni amplíes permisos.

La misión real **no debería duplicar los siete bloques** dentro de cada prompt: usar referencia al archivo canónico y fuente remota verificada tras poder publicarlo de manera segura.

## 10. Activación en otro chat (NO ejecutar ahora)

1. Recuperar este archivo del adjunto/link de conversación o publicarlo en el repositorio **solo cuando se pueda adquirir el writer de manera autorizada y segura**.
2. Releer estado en vivo: HEAD, último CI, cola, writer, A/B, watchdog, informe. No asumir el snapshot de arriba.
3. Acordar nueva ventana de prueba, hora de inicio y corte absoluto; una hora si así se autoriza.
4. Activar únicamente A y B a 30 minutos de separación; cada una no más frecuente que una vez por hora. Control de cierre antes del cutoff e informe final READ_ONLY después.
5. No afirmar éxito ni tiempo de desarrollo hasta observar ejecución real y CI vinculada.

**Estado final deseado de esta conversación:** diseño guardado localmente, FARO Noche OFF, sin nueva autorización, sin GitHub writes.
