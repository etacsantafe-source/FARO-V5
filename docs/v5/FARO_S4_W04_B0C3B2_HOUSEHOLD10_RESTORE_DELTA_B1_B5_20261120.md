# FARO V5 — W04 B0C3B2 · Restauración real del archivo Household10

Fecha local 2026-11-20. Unidad `S4_W04_B0C3B2_HOUSEHOLD10_ARCHIVE_REPLAY_DEV`. Complementa el B1–B5 original de B0C3B. Depende del B7 de captura B0C3B1. Esta unidad termina el roundtrip FBS2v6 en STAGED10.

## B1 — Evidencia y brecha

- B0C3A restaura Household10 bajo admisión externa y comprueba su transacción tras el cierre. B0C3B1 implementa la captura FBS2v6 y la comparación exacta de Reality10 más los cuatro stores que la acompañan.
- `FaroV5FiveStoreStagedRestore.restoreAuthenticatedAuxiliary4StageOnly` abre FBS2v5 con recibos. Comparte primitivas tipadas que restauran la base sobre Reality9. `RealityFinance9StagedRowsRestorePort` repone las ocho tablas del segmento Finance9 únicamente en STAGED9. `RealityHousehold10StagedFilePort` es el adaptador DB para convertir una Reality9 previamente admitida.
- `FaroV5Household10StagedMigration` valida una copia literal de ACTIVE9. En restauración se valida el STAGED9 reconstruido desde el archivo autenticado, por lo que solo se reutiliza el adaptador SQL y se verifica la base reconstruida contra el archivo original.

## B2 — Comparación y reutilización

- De la auditoría V4.4 L15 se conserva la necesidad de persistir Household y recuperarlo junto al resto de los datos. Se reutilizan el formato portable V5, su base con recibos, y el pin durable por operación y archivo. Se mantiene el parser histórico de v5.

## B3 — Alternativas

- Elegida una única autenticación FBS2v6 con bytes privados copiados. Un tipo de resultado de apertura no construible externamente permite pasar la base autenticada a la misma restauración de cinco stores. La fase Finance9 completa STAGED9 antes de instalar Household10.
- Descartados la recodificación a v5, los flags que permiten inyectar un payload sin autenticar y la reapertura de STAGED10 por un graph9. En el reintento de STAGED10 corroborar el pin ya existente, comparar primero la base íntegra con el archivo y solo entonces completar Household.

## B4 — Contrato y rutas

- Con lease de host DEV cerrado, admitir target canónico no activo y exacto, confirmar puntero ID+secuencia y prohibir hardlinks/symlinks/sidecars en comprobaciones del conjunto cerrado. Autenticar antes de escribir. Identidad de origen del archivo distinta del target.
- En STAGED9, pin durable de los bytes exactos de v6 para el mismo operation ID y generación. Reutilizar restore tipado de base y recibos, restaurar Finance9, cerrar y convertir a 10 dentro de la lease. Ante fallo devolver Blocked y mantener el puntero.
- Para STAGED10, rechazo si falta el pin durable. Releer los cinco stores y verificar la base del archivo antes de restaurar Household. Restaurar solo un Household fresco o idéntico. Confirmar ambos conjuntos exactos tras reapertura y repetir verificación de pin y lease.
- Rutas exactas candidatas:
  - `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10ArchiveCoordinator.kt`
  - `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreStagedRestore.kt`
  - `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household10ArchiveRestoreTest.kt`
  - `.github/workflows/faro-v5-s4-w04-b0c3b2-household10-restore.yml`

## B5 — Preauditoría adversarial

- Roundtrip desde STAGED10 con Household, Finance9 y recibo Auxiliary4. Comprobar reintento exacto y preservación del puntero. Probar Household vacío con revisión, v5 rechazado por v6, passphrase errónea y manipulación sin cambios.
- Inyectar interrupciones durante el replay de cinco stores, después de migrar a 10 y después de restaurar Household. Verificar que ningún paso devuelva StagedVerified antes de observar igualdad total tras reabrir. Reintento con segundo ciphertext o segunda operación sobre STAGED10 falla cerrado.
- Probar pérdida de lease/puntero, target activo, hardlinks y alteración de Finance9/Auxiliary4 después de una interrupción. Comprobar que ninguna ruta activa el puntero ni exporta grants.
- Exigir CI JVM, JUnit XML, guardia y evidencia B7 de la misma unidad antes de D1.
