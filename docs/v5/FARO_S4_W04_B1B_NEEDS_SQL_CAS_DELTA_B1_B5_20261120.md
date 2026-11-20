# FARO V5 · W04-B1B · Necesidades Household10 con CAS

Fecha local 2026-11-20. Unidad `S4_W04_B1B_NEEDS_SQL_CAS_DEV`. Delta sobre `e937ca0a657b722fd158584e03207adbfde9747a`. Base B1–B5 `FARO_S4_W04_B1B_B1D_NEEDS_OPERATIONS_B1_B5_20261120.md`. Sin permiso hasta guardia documental.

## B1 — Entrada y evidencia
B1A usa un lease único por root, el gate compartido, un fence de generación/puntero y una conexión Reality10 de solo lectura. B1B conserva esa sesión de lectura y habilita la persistencia sólo por el adaptador interno de Reality, con el scope de su transacción SQLite vigente. Empezar por el modelo de Household existente. El contrato necesita crear, reemplazar campos editables, completar y eliminar, con lectura tipada persistente. Cada mutación válida consume exactamente una revisión de la lista y la usa como revisión de la fila si todavía existe.

## B2 — Reutilización
Conservar las decisiones V4.4/V5 de la preauditoría. El patrón `ObjectivePersistencePort` demuestra la inyección de `RealityTransactionScope`, y el port de Household requiere además revisión de lista y revisión de fila. Usar los límites de `Household10RowsSql` antes de cambiar filas. No convertir cantidades de V4.4 al copiar el modelo ni publicar un nuevo host público.

## B3 — Alternativas ya evaluadas
Se mantienen las fuentes de SQLite y el contraste de la preauditoría. Adoptar BEGIN IMMEDIATE de Reality con una actualización SQL condicionada a la revisión de lista. Rechazar una conexión de escritura independiente y un upsert incondicional. Probar que una operación perdida por revisión no puede sobrescribir una fila ni dejar la revisión adelantada.

## B4 — Alcance y rutas
El adaptador de prueba persistente encadena sus llamadas desde la misma instancia Reality. Mantener la configuración de sólo lectura de B1A por defecto. El adaptador comparte conexión, gate y scope y revalida el fence antes del COMMIT. El host autorizado y los recibos se montan en B1C2 bajo su permiso. No se cambia el esquema certificado.

- `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/spi/HouseholdNeedPersistencePort.kt`
- `v5/store/reality/build.gradle.kts`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHouseholdNeedPersistence.kt`
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt`
- `v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHouseholdNeedPersistenceTest.kt`
- `.github/workflows/faro-v5-s4-w04-b1b-needs-sql-cas.yml`

## B5 — Negativas obligatorias
Probar dos operaciones con revisión vieja, fallo antes de commit, reopen, scope ajeno/reutilizado, root obsoleto, límite de snapshot H10S, cantidad y fecha tipadas, y preservación de Finanzas/Objetivos/Asuntos. Antes de SQL, validar el snapshot resultante con los límites ya respaldables. Si se elimina una fila, su preimagen podrá capturarse en la Review inmutable de B1C1/C2 antes de su Apply. No suponer aún un Undo durable ni escribir un historial aparte. La compatibilidad integral FBS2v6 después de datos activos se verifica en B1D. El respaldo actual exige fuente sellada no activa, lo que requiere preparar una copia autorizada bajo quiescencia conservando datos y WAL antes de capturar.
