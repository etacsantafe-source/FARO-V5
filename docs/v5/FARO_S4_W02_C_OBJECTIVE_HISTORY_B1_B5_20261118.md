# FARO V5 · W02-C · Avances, correcciones y estados

Unidad `S4_W02_C_OBJECTIVE_HISTORY`. Preparación sobre el código de Review/Apply `b504a6e809f064e892ff2a8ce6320ed1212a4700`. Requiere B7 de B y guardia verde para emitir READY.

## B1
- P05 O02 y PF13 exigen avance declarado por persona, fecha civil, explicación e historial que sobreviva a corrección. W02-A prueba medidas numéricas persistentes. Falta representar avances cualitativos, correcciones que conservan el original y transiciones de lifecycle con su motivo.
- W02-B introduce intención canónica, binding y comprobación de revisión en SQL. N-030 contiene `objective.update` y `objective.lifecycle.set`. Ninguna propuesta de IA, estimación financiera ni entrada de Evidence puede contar como avance confirmado.
- Aceptación: solo Review/Apply autorizado agrega un evento. Reapertura y restore conservan el orden por secuencia, fecha, razón, evidencia textual/medición y estado. Ediciones de metadatos no borran historial. Una corrección apunta al logro original. Proyección cuantitativa únicamente si hay objetivo positivo y una lectura confirmada en la misma unidad.

## B2
- V4.4 snapshot `bfa0ee71f5b9cd104baaadd71f1e77a61f203939`, FaroV4ObjectivesGateway y FaroV4ObjectivesSurface. Progreso aplicado/reservado y estado de lifecycle son conceptos separados. Reusar esa separación y transición explícita, sin llevar asignaciones financieras al avance manual de W02.
- Reusar el port de Objetivos, SQLite en la misma conexión, Review y grants de B, y backup G4 de A. El registro de cambios complementa el registro actual y su revisión.

## B3
- [Azure Event Sourcing](https://learn.microsoft.com/en-us/azure/architecture/patterns/event-sourcing) describe corrección mediante evento compensatorio y comparación de versión. ADAPTAR anexión y referencia al original. DESCARTAR reconstruir todos los registros existentes desde eventos, colas y servicios Azure. Probar editar un logro dos veces sin perder el original.
- [SQLite Foreign Keys](https://www.sqlite.org/foreignkeys.html). USAR clave compuesta objetivo/secuencia para orden determinista y referencia al padre. La FK no valida por sí sola el tipo de evento referenciado. Verificar en modelo y transacción que una corrección refiera a un avance anterior del mismo objetivo. Sin páginas cloud ni motor adicional. [Loop](https://github.com/iSoron/uhabits) REFERENCIA para diferenciar entrada histórica de un agregado y preservar compatibilidad al cambiar una regla. GPL-3.0, sin incorporar código.
- Alternativa de sobrescribir el progreso actual y la nota anterior, DESCARTADA por PF13. Proyección como lectura derivada de datos confirmados, sin avanzar al cargar pantalla.

## B4
- Crear un historial con avance, corrección y cambio de estado. Avance lleva fecha del hecho, tiempo de registro, explicación y lectura opcional con unidad. La fecha civil puede diferir del tiempo de registro. Corrección apunta al avance original y conserva las lecturas anteriores. Una meta cualitativa puede registrar avances sin que aparezca porcentaje. Para métrica cuantitativa el valor confirmado es lectura acumulada declarada y no suma de instantáneas. Excluir lecturas con otra unidad, permitir sobrecumplimiento y evitar overflow al calcular proporción.
- Los cambios de estado se solicitan de forma explícita y su transición conserva estado anterior y nuevo. Ni la fecha ni el porcentaje cambian estado automáticamente. Esquemas de datos previos siguen representando historial vacío sin agregar eventos sintéticos. Misma transacción para fila, revisión, registro de historial y receipt. Verificar continuidad y no reescritura del prefijo antes de mutar base.
- Extender G4 con versión interna 3 condicional para historial. Conservar v1/v2 para documentos que no requieren representar eventos nuevos. La extensión de SQLite se crea únicamente dentro del primer write/restore que la requiere. Backup/restore mantienen prefijo. El binder de edición de B copia el historial actual sin tomarlo del formulario.
- Reutilizar el mismo Review Host para `objective.update` con payload específico de avance/corrección y `objective.lifecycle.set` para lifecycle. Identificar el formato canónico y su operación antes de emitir grant. La operación con formato de edición existente sigue funcionando.
- Rutas candidatas a confirmar contra el cierre B:
  - `v5/owner/objectives/src/main/kotlin/ar/com/faro/v5/owner/objectives/api/ObjectiveModel.kt`
  - `v5/owner/objectives/src/main/kotlin/ar/com/faro/v5/owner/objectives/api/ObjectiveHistory.kt`
  - `v5/owner/objectives/src/main/kotlin/ar/com/faro/v5/owner/objectives/api/ObjectiveHistoryOperations.kt`
  - `v5/owner/objectives/src/main/kotlin/ar/com/faro/v5/owner/objectives/api/ObjectiveReviewedEditBinding.kt`
  - `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
  - `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityObjectiveDetails.kt`
  - `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5G4DomainSemanticCodec.kt`
  - `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W02ObjectiveReviewHost.kt`
  - `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W02ObjectiveHistoryTest.kt`
  - `.github/workflows/faro-v5-s4-w02-c-objective-history.yml`

## B5
- Probar avance cualitativo y cuantitativo, corrección referenciada con razón, dos correcciones, cambio de unidad, metas ausentes, stale, reintento, fallo por grant o falta de foreground, transición inválida, fecha civil, reapertura y rollback. Distinguir falta de lectura de cero registrado.
- Probar v1/v2 y v3, restore sin pérdida y rechazo de referencias a eventos inexistentes. Android queda para E. Probar regresión de alta/edición en el mismo CI del cambio. No tocar otras operaciones catalogadas ni los owners Finance/Matter.
