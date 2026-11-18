# FARO W01 D1B1C2B — autorización duradera y sesión exclusiva para el cutover DEV

Unidad propuesta `S4_W01_D1B1C2B_FINANCE9_DEV_DURABLE_HOST`. W1 OPEN, W2 STOP, V2 productiva OFF.

## B1
D1B1C1 resuelve cambio y recuperación de puntero bajo un host exclusivo. D1B1C2A prepara cinco bases selladas y conserva los datos. Entre ambos falta una autorización persistente y un lease que bloquee al segundo propietario incluso si corre en otro proceso. El journal existente liga IDs y fases, pero no guarda la sequence del origen, el fingerprint de datos ni la decisión real de iniciar corte. `FaroV5V2Planning8DevHost` ya congela un origen para schema8; su formato es privado a Planning8 y su apertura no contempla una generación sucesora ni un retorno con +2.

## B2
Reusar el gate material del proceso, `FileBootstrapJournal`, el coordinador/replay de D1B1C1 y verificación de origen de D1B1C2A. Conservar las transacciones y recibos de V4.4. El futuro adaptador Android debe usar el mismo lease para abrir Planning8 o Finance9, y cerrarlo solo después de cerrar la sesión.

## B3
- ADOPTAR lease único por root canónico con `FileChannel.tryLock` y registro JVM. El [contrato FileLock](https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/nio/channels/FileLock.html) distingue hilos y procesos, y describe liberación por cierre o terminación. El archivo de lock permanece en la raíz y no se borra al liberar.
- ADOPTAR dos publicaciones duraderas dentro de la misma raíz: intención inicial PREPARE con identidad completa, y marca SWITCH_AUTHORIZED con fingerprint tras readmisión explícita del host. Reusar las transiciones del journal existente. Si solo existe PREPARE, startup conserva el origen y puede abortar la etapa exacta sin promover. Si existe SWITCH_AUTHORIZED, startup converge desde el journal previo con el mismo origen y destino.
- DESCARTAR un boolean de UI como autorización de recovery, inferir consentimiento a partir de VALIDATED, tiempo como lease, o un usuario con una segunda conexión. Android usará su raíz privada excluida de backup [Context.noBackupFilesDir](https://developer.android.com/reference/android/content/Context#getNoBackupFilesDir()) mediante wiring separado.

## B4
Rutas exactas a autorizar:
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9DevCutoverRuntime.kt`: lease de root, registro de procesos, publicar la evidencia duradera de decisión, preparar/promover/recuperar con el gate y devolver un estado de mantenimiento tipado. Escritura temporal, fsync y rename atómico; tamaño, versión y checksum verificados. Sin UI ni política de producto.
- `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9DevStagePreparation.kt`: permitir revalidar el mismo fingerprint durante recuperación SWITCH_PREPARED antes del CAS, bajo lease real, con opción acotada; la entrada de preparación sigue exigiendo VALIDATED.
- `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9DevCutoverRuntimeTest.kt`: ruta positiva de datos existentes, exclusión de segundo propietario, ausencia o revocación de confirmación, interrupción y recuperación posterior a publicación de puntero, corrupción/identidad ajena.
- `.github/workflows/faro-v5-s4-w01-d1b1c2b-finance9-durable-host.yml`: JDK21 JVM dirigida, XML exacto y guard.
El montaje Android posterior necesita una admisión explícita solo en W01. El resultado de mantenimiento no entrega un grafo SQLite alternativo a UI ni modifica FINANZAS/Chat.

## B5
- Probar que dos adquisiciones del mismo root en un proceso no crean otro canal capaz de soltar el lock existente. El lock de OS es cooperativo. Un segundo proceso debe recibir denegación hasta cierre o salida del dueño. Delimitar lo que cada prueba acredita.
- La confirmación se comprueba antes de preparar y antes de marcar SWITCH_AUTHORIZED. Con PREPARE aislado no iniciar promoción, incluso con journal VALIDATED. Abortar únicamente su journal exacto bajo full pointer y conservar cualquier stage parcial. No retomar un journal extraño.
- Releer source fingerprint cuando el origen siga activo, incluso SWITCH_PREPARED. Si cambió el puntero y el journal acredita la operación, usar recovery de D1B1C1 sin inventar un nuevo intento. Si ya se aceptaron escrituras, mantener protección contra rollback.
- Sin credenciales, ruído de logs con datos financieros, producto, W2, biometría ni intervención en el teléfono. Sigue obligatorio comprobar guard del permiso, writer y evidencia nueva antes de B6/B7.
