# FARO W01 D1B1C2A — preparar una etapa Finance9 con los datos existentes

Fecha local 2026-11-17. Unidad propuesta `S4_W01_D1B1C2A_FINANCE9_DEV_PRESERVE_STAGE`. W1 OPEN, W2 STOP, V2 productiva OFF. Sin montaje Android en esta unidad.

## B1
El stage owner Planning8 existente crea un workspace vacío en DEV. Ese método no sirve para actualizar un workspace con datos. D1B1C0 admite una generación Finance9 sellada y un journal exacto; D1B1C1 activa y recupera ese target bajo mantenimiento. Falta preparar la etapa a partir de la generación real y distinguir contenido stale aunque el ID+sequence no cambien. `FileStorageGenerationLayout.stageWorkspaceV2` publica metadata nueva sin abrir SQLite; `FaroV5Finance9StagedMigration` valida las cinco versiones de la copia y convierte solo Reality8 de esa etapa.

## B2
Mantener la generación original apta para rollback y los recibos/Undo/semántica importados de V4.4. Reusar el lifecycle de journal, el gate material, el layout V2 y la migración de etapa. No modificar DB fuente, mover su puntero ni crear un segundo grafo. La semántica del ledger y los campos no confirmados provienen de sus filas actuales.

## B3
- ADOPTAR copia de las cinco bases cerradas y selladas dentro de un root privado W01 excluido del backup, bajo lease exclusivo de mantenimiento. Permite conservar columnas legacy, journals y recibos internos de cada SQLite sin recomponer sus filas.
- ALTERNATIVA FBS2v3. Ya captura y restaura cinco owners con semántica probada. Conservar para portabilidad, no duplicar serialización/contraseña para el traslado interno del mismo root.
- DESCARTAR copiar databases activos, copiar o descartar WAL/SHM para simular consistencia, inicializar un target vacío, migrar SQLite activo in-place o usar un segundo grafo. Referencia [SQLite WAL](https://www.sqlite.org/wal.html). El archivo WAL puede contener commits que no están en el archivo principal.

## B4
Rutas nuevas:
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9DevStagePreparation.kt`.
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9DevStagePreparationTest.kt`.
- `.github/workflows/faro-v5-s4-w01-d1b1c2a-finance9-preserve-stage.yml`.

Contrato interno: recibe root, gate compartido y aserción del lease exclusivo del host. Con la sesión previa ya cerrada, bajo quiescencia, verificar puntero completo, source V2 canónico, target nuevo y journals pendientes ausentes. Enumerar únicamente las cinco bases esperadas y exigir archivos regulares sin symlinks ni sidecars antes y después. Copiar por archivo al target canónico nuevo, forzar datos y directorio, verificar digest de copias y del origen, y migrar únicamente el Reality de la etapa mediante el owner existente. Encadenar migración, verification final, y recién después publicar journal de preparación que evoluciona por las fases legales hasta VALIDATED para el origen/target exacto. El resultado contiene el fingerprint de todas las bases fuente y del origen ID+sequence. La fase Android futura tiene que volver a comprobar ese fingerprint bajo su lease y persistir el intento autorizado antes de activar. No borrar automáticamente stage parcial, activo ni historial.

## B5
- Rechazar puntero stale/ABA, cambio de contenido sin cambio de puntero, stage ya existente o activo, descriptor/path foráneo, sidecar real/colgante, base faltante o esquema incorrecto, lock de root ausente, competidor o interrupción. Un fallo previo a VALIDATED deja el origen intacto y sin promover.
- Verificar muestra de datos existentes y recibos estructurales en la copia, bytes intactos en fuente, invariancia de pointer, cinco archivos completos y metadata durable. Fixtures separadas de las bases reales. La restauración de un proceso interrumpido debe limitarse al origin y a la operación registrada.
- La copia y recuperación en un host Android real necesitan su propio permiso, una única sesión y confirmación explícita de inicio; no introducir biometría obligatoria, envío de datos, APIs, W2 o shipping.
- CI JVM solo de este delta con reporte XML y auditoría del SHA referido.
