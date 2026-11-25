# FARO V5 · W05-A1C0B3B · Rollback de un destino leído en WAL

## B7 — RED_FIX

- Código `706a0a0f0844468040b42a3f0224215bcd739ce2`, guardia 45597914115 SUCCESS. CI 45597914806 FAILURE. Los XML originales del ZIP 15014087542 (SHA-256 `13022cc95cdaa36a82aad73d514eecf7723960443ba675b429536f6bbb023a2b`) confirman SQL12 cutover 7/8 y replay STAGED 8/8.
- Falla `failedTargetReopenRollsBackWithVerifiedOriginalPointer`: la L276 obtiene BLOCKED en lugar de ROLLED_BACK. Las rutas de aprobación, cambio de puntero y recuperación de SWITCH_PREPARED pasaron.
- La revisión local detecta que tras devolver el puntero al origen, el destino anteriormente abierto read-only vuelve a ser evaluado como si nunca hubiera sido seleccionado. La SQLite nativa puede dejar un -wal vacío y -shm al cerrar una lectura, documentado en RED_FIX4 de W04-F2B2C. La comprobación de huellas y links debe mantenerse. Admitir exclusivamente WAL vacío o sin frames, y el -shm regular, si el puntero completo y el journal prueban que el destino ya fue seleccionado. Seguir rechazando sidecars del destino antes de seleccionar, WAL con frames, journal de escritura y alias.
- Corrección en el verificador del ticket. Repetir la suite dirigida y comprobar en el rollback que se retorna al origen con su puntero completo, sin fence de destino ni admisión de escrituras. B7 PASS pendiente de nuevo SHA, CI, XML y guardia.
