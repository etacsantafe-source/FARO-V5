# FARO V5 · W02-A · Contrato ampliado y persistencia

Rama `work/s3-night-20261102`, base auditada `9c08023fb23aa7c0db9c59ffaf438ee547754bdd`. Unidad `S4_W02_A_OBJECTIVE_PERSISTENCE`. Preauditoría documental. Los casos de aceptación de W02 todavía están pendientes de ejecución.

## B1
- La guardia [44435066080](https://github.com/etacsantafe-source/FARO-V5/actions/runs/44435066080) terminó SUCCESS para esta base; el writer remoto observado estaba RELEASED, epoch 1258. El permiso funcional seguía BLOCKED. W01 conserva su cierre global.
- El contrato ObjectiveRecord tiene cinco campos y ObjectiveSnapshot conserva la revisión del owner. RealityStore upsert exige el scope activo exacto, evita repetir el mismo registro y rechaza actualizaciones con timestamp no creciente. El código de búsqueda ya toma ObjectiveSnapshot y consume únicamente los campos base. El upsert existente tiene callers de prueba y ningún caller funcional fuera del owner.
- RealityStore usa esquema base 7, planificación 8 y Finance9 exacto con admisiones distintas. El snapshot G4 alimenta FBS2 y el coordinador de archivo autenticado Finance9. FaroV5G4DomainSemanticCodec fija la versión interna 1 y solo transmite los cinco campos. Una ampliación exclusiva de SQLite perdería los campos al exportar y restaurar.
- Aceptación W02-A. Leer filas y archivos anteriores con valores legados explícitos. Persistir métrica opcional, fecha civil, prioridad e historial ordenado en una sola transacción Reality. Mantener esos datos y la revisión tras reapertura y exportación/restauración. Probar cada invariante con SQLite real y XML en CI por SHA. El punto de entrada de aplicación, la semántica de corrección y el recibo corresponden a W02-B/C.

## B2
- Leído el repositorio V4.4 en el snapshot `bfa0ee71f5b9cd104baaadd71f1e77a61f203939`. `app-v4/src/main/kotlin/ar/com/faro/v4/objectives/FaroV4ObjectivesActions.kt` admite prioridad entera, meta opcional en centavos y fecha LocalDate; UPDATE diferencia preservar y limpiar. `FaroV4ObjectivesSurface.kt` muestra el progreso real separado de capacidad proyectada y protege la visualización de montos. Conservar la fecha civil y la separación meta/avance. V5 suma cantidades no monetarias sin atar la métrica a una cuenta.
- Reutilizar el owner, la conexión, la revisión y el scope de Reality, la restauración G4 y los límites del codec FBS2. Evitar copiar la DB o llevar los datos de capacidad de Finanzas a esta unidad.

## B3
- [SQLite ALTER TABLE](https://www.sqlite.org/lang_altertable.html). DESCARTAR ALTER sobre objective_record para esta unidad. Añadir varias columnas con restricciones obliga a representar y validar grupos coherentes, amplía las rutas de admisión 7/8/9 y no resuelve el historial ni el wire. [SQLite CREATE TABLE](https://www.sqlite.org/lang_createtable.html). USAR extensión aditiva con tabla de detalles y tabla hija de eventos en la conexión existente; SQLite es dominio público, sin dependencia adicional ni red. SQLite considera un CHECK con resultado NULL satisfecho. Las comprobaciones de grupos opcionales deben ser explícitas; probar una métrica parcial inválida.
- [Migraciones Room](https://developer.android.com/training/data-storage/room/migrating-db-versions). ADAPTAR pruebas de apertura de datos anteriores y comparación antes/después. DESCARTAR reconstrucción destructiva y cambiar de motor/abrir otra base. La documentación vigente usa una utilidad que requiere esquema exportado; Reality ya utiliza el driver SQLite y puede probar la base física anterior directamente.
- [Tasks.org](https://github.com/tasks/tasks) y [Loop](https://github.com/iSoron/uhabits), GPL-3.0. REFERENCIA para separar vencimiento/prioridad de mediciones registradas e historial local. Descartar introducir sync, calendario de rachas, SDK o código GPL. Para W02-A alcance y costos quedan locales, sin red, licencias nuevas ni API paga.

## B4
- Extender ObjectiveRecord con campos opcionales, prioridad NORMAL por defecto y una secuencia de eventos vacía en registros anteriores. Métrica con unidad explícita y objetivo entero positivo, fecha de calendario sin instante ni zona; cuantía monetaria opcional en unidades menores y con moneda explícita mediante el contrato de unidad, sin redondeo ni float. Evento de medición tipado en la misma unidad y con secuencia estable. Escribir y restaurar sin pérdida del prefijo histórico. Los metadatos de la meta no implican una medición real.
- Tablas de extensión creadas de forma idempotente en el primer upsert ampliado o restore ampliado, dentro de la transacción existente. Una base anterior sin ninguna tabla representa detalles por defecto. Ausencia parcial de tablas en una base con extensión se rechaza. Las claves foráneas apuntan al registro canónico de Objetivos. Conservar esquema base, adopción de Finance9, WAL y las revisiones ya existentes. El cambio diferencia base de SQLite de versión del formato G4, sin mover ni recodificar datos de W01.
- Mantener el wire G4 v1 idéntico para snapshots con datos base. Emitir G4 v2 para detalle/historial y permitir que el lector nuevo restaure v1/v2. Mantener límites de bytes, orden canónico y no ignorar datos desconocidos. Un lector anterior debe rechazar G4 v2 por versión. W02 debe usar su APK DEV aislada antes de crear datos ampliados desde UI.
- Rutas funcionales exactas:
- `v5/owner/objectives/src/main/kotlin/ar/com/faro/v5/owner/objectives/api/ObjectiveModel.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityObjectiveDetails.kt`
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityObjectiveDetailsTest.kt`
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5G4DomainSemanticCodec.kt`
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5ObjectiveDetailsBackupTest.kt`
- `.github/workflows/faro-v5-s4-w02-a-objective-persistence.yml`

## B5
- Scope ajeno y datos inválidos fallan antes de mutar, replay idéntico no incrementa revisión y actualización stale falla. La transacción de registro/revisión/tablas/eventos debe revertir por completo. Probar rollback del primer write ampliado y reapertura.
- Probar lectura intacta de un objetivo legado, upgrade repetido, round trip extendido con fechas y eventos, restore idempotente, secuencia o payload inválidos y rechazo de reescritura del historial. Backups v1 deben seguir decodificando con exactamente los valores legados. Restore que detecta un destino poblado debe rechazar antes de escribir.
- Evitar cambios en Apply, publicación, generation pointer, policy/grants, catálogo, pantalla o selección Android. Los grants y la comprobación de generación permanecen para las unidades con host concreto. No introducir datos de Evidence, red, Auto/Noche, APIs pagas, G15 o dinero real. W03 continúa detenido.
- B6/B7 pendientes. Publicar con guardia documental verde, después READY en checkpoint separado y preflight para cada ruta. Ejecutar pruebas JVM de owner, SQLite y backup autenticado por SHA; revisar XML y alcance antes de avanzar a W02-B.
