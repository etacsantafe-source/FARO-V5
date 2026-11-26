# FARO V5 · A1C4 · Mantenimiento autenticado desde SQL12 cerrado

Unidad propuesta `S4_W05_A1C4_CLOSED_SQL12_MAINTENANCE_DEV`. La corrección del verificador de SQL13 es un prerrequisito de esta unidad. La aceptación A1C4 conserva sus tres rutas exactas y depende de esta composición.

## B1 — Evidencia de la brecha y condición de salida

Los hosts DEV de compra N-036, recepción N-035 y Undo N-016 abren sesiones bajo `finance9-dev-root.lock`. El replay v9/v10 y los dos coordinadores de cutover son internos a `:store:generation`. La app no puede usar esas clases sin una frontera explícita. El replay solo captura desde etapas inactivas y comprueba que los cinco archivos canónicos estén sellados, que la secuencia del puntero coincida y que el stage difiera de ACTIVE.

Se requiere una entrada explícita de mantenimiento DEV que lleve datos de un SQL12 cerrado a un SQL13 seleccionado mediante FBS2v9/FBS2v10 y el journal existente. Un stage con copia de bases seguido por un puntero sintético no cumple este criterio. El flujo debe admitir las pruebas de app sin agregar una acción de cutover a la UI.

## B2 — Interfaces reutilizables

- Patrón de copia sellada y comprobación de huellas de `FaroV5Household10ArchiveCoordinator.captureClosedActiveAuthenticated` para conservar identidad de los cinco archivos bajo lease.
- `FaroV5Household12Evidence2DevJournaledCutover.hasCompletedSelection` para admitir el origen con puntero completo y fence.
- `FaroV5Household12V9StagedReplay.captureAuthenticated`, `restoreAuthenticatedV9AsThirteen`, `captureAuthenticated` de V10, `restoreAuthenticatedV10` y `FaroV5Household13Evidence2DevJournaledCutover` con el mismo verificador canónico.
- `FaroV5WorkspaceV2StageOwner`, `FaroV5Finance9StagedMigration` y las aperturas de stage ya admitidas para preparar destinos SQL9 con Evidence2 y Auxiliary4.

## B3 — Decisión

Mantener una sola entrada DEV, con admisión de dueño, confirmación foreground y contexto de mantenimiento cerrado, que coordine puertos internos. Adquirir el lock de mantenimiento compartido antes de leer o copiar. Mantener el mismo lease y quiescencia hasta el journal de selección. El host de app debe liberar su sesión y recuperar un Apply en vuelo antes de solicitar mantenimiento. Usar códigos de estado tipados. El origen permanece seleccionado hasta `start`.

Aislar tres destinos por IDs únicos (copia SQL12, puente SQL13 y destino SQL13). Validar huellas del origen y copia, puntero y sidecars antes y después. El restore v9 al puente conserva la procedencia sin compensaciones. El backup v10 del puente sirve de fuente autenticada del destino final. La atestación de cada restauración debe conservar la vinculación del ciphertext con el operation ID y descriptor correspondientes. Separar preparación/autorización de la confirmación que habilita la selección. El journal opera sobre el destino final y resuelve recovery después del punto de preparación del switch.

Rechazar antes de seleccionar si queda un Apply abierto o un intento PREPARED. La reconciliación pertenece al host original: el respaldo semántico de Reality conserva COMMITTED vía ExecutionReceipts y no transporta intentos PREPARED sueltos. Comprobar con el puerto de inspección que el total de intentos coincida con el de ExecutionReceipts y que Workspace no tenga Reviews APPLYING o RECOVERING_UNKNOWN, todo bajo la misma quiescencia. Los estados PREPARED y UNKNOWN se prueban en el origen. A1C4 conserva los casos de recuperación de los hosts.

## B4 — Alcance candidato

- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household13Evidence2DevJournaledCutover.kt` para integrarse al lease y al journal existentes.
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5ClosedHousehold12DevMaintenance.kt` para la entrada específica de mantenimiento y composición.
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5ClosedHousehold12DevMaintenanceTest.kt` para la prueba del límite de módulo, persistencia y negativas.
- `.github/workflows/faro-v5-s4-w05-a1c4-closed-sql12-maintenance.yml` para CI dirigida y XML original.

Confirmar rutas exactas y base certificada antes de emitir READY. Toda ampliación pasa por el control de siete bloques. Sin cambios en hosts de aplicación, panel, permisos productivos, build types ni catálogos.

## B5 — Prueba adversarial

1. Abrir un SQL12 confirmado con un Review canónico y su ExecutionReceipt persistidos, cerrar lease, preparar ambas restauraciones autenticadas y verificar igualdad de las cinco secciones y del ledger de Reality. El test de generación verifica el transporte desde ACTIVE con un fixture. A1C4 verifica el origen mediante los hosts reales de Apply de app.
2. Rechazar con lease de app activo, dueño inválido, puntero con ABA, confirmación revocada, sobre inválido, archivo corrupto o revisión PREPARED pendiente. Comprobar cero avances del puntero y cero writes a ACTIVE.
3. Interrumpir al preparar el switch y recuperar bajo lease, con verificador y puntero resultante único. Confirmar journal COMPLETED y liberación efectiva del lock al terminar, incluso después de una excepción.
4. Ejecutar A1C4 con sus permisos propios y comprobar N-036, 7+3 de N-035, compensación de 7 una sola vez y conservación de las evidencias. Android API35 con fuente 200 %, paquete DEV y prueba por accesibilidad. Sin repetir suites certificadas fuera de la cobertura necesaria para el flujo integrado.

V2 productiva, stock productivo, pagos y Auto/Noche OFF. Moto G15 físico NOT_RUN.
