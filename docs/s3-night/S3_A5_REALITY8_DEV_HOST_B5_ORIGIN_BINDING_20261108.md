# FARO V5 — S3.A5 B5 addendum: durable full-pointer origin for DEV Reality8

Fecha 2026-11-08 AR. Subproblema de `S3_A5_REALITY8_DEV_HOST_MOUNT` descubierto antes de B6; el B1–B5 original permanece histórico.

## B1 — Hallazgo comprobado
`BootstrapJournalEntry` conserva `previousGenerationId` pero NO `ActiveGenerationPointer.sequence`. `FaroV5Planning8DevCutoverOwner.recover(operationId, descriptor, expectedPrevious)` sí exige previous ID+sequence exactos. Después de SWITCH_PREPARED/SWITCHED no se puede reconstruir el valor de origen desde el puntero actual sin una suposición de ABA. Falta evidencia de recuperación segura entre procesos; el anterior permiso READY NO autoriza inferir secuencia.

## B2 — Código V5 y legado
`FileActiveGenerationPointer` almacena (generationId,sequence) con historial durable y CAS físico; el mismo ID puede reaparecer con otra secuencia. `FileBootstrapJournal` mantiene operación y fases; no proporciona pin exacto. `StorageGenerationDescriptor` es inmutable pero tampoco incluye el origen. No modificar estos tres módulos ya certificados ni trasplantar datos V4.4.

## B3 — Alternativas y verificación externa
- SQLite WAL https://www.sqlite.org/wal.html: durable por SQLite, **no** sustituye origen/puntero del coordinator de cinco stores. ADAPTAR registro de control fuera de SQLite.
- Java NIO FileChannel.force https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/channels/FileChannel.html#force(boolean) y Files.move/ATOMIC_MOVE https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/StandardCopyOption.html: escritura de bytes con force y renombre atómico en mismo directorio. ADAPTAR, validar la plataforma de Android en tests/CI.
- OWASP Authorization https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html: no aceptar una identidad de generación por sola coincidencia de ID ni confiar en un boolean de host para el principal de usuario. USAR validación separada de ACL.
- A **ELEGIDA**: un manifiesto de intención DEV pequeño e inmutable bajo raíz privada, escrito DURABLE ANTES de StageJournalOwner, que contiene `previousGenerationId`, `previousSequence`, `targetId`, `operationId`, `descriptor`. Recuperación valida exactamente ese registro contra journal y puntero. B DESCARTAR: inferir previousSequence = currentSequence - 1 (ABA). C DESCARTAR: extender journal global con un campo opcional sin versión (riesgo de alterar módulos ya certificados). D DESCARTAR: migrar ACTIVE7 in-place.
- No agregar dependencias, secretos, APIs o segundos writers. La comprobación de `force` y comportamiento de fallo se realizará con pruebas dirigidas; no se presume resistencia a cortes eléctricos sin test físico.

## B4 — Delta mínimo
1. Clase DEV-only de intención persistida en `v5/app/src/v2CoreDev/.../FaroV5V2Planning8DevHost.kt` con `CREATE_NEW`/archivo temporal, `FileChannel.force(true)`, renombre atómico, límite de bytes, rechazo de symlink, forma canónica y checksum para corrupción. No tratar SHA como prueba de autenticidad contra atacante con escritura al sandbox.
2. Antes de StageJournalOwner: exigir root/puntero Reality7, que no haya journal8, y cerrar Session anterior; persistir intención exacta; si crash después del intent y antes del journal, el siguiente open NO auto-crea operación: requiere nuevo acto foreground y admisión independiente. Nunca eliminar intent ni restos PREPARING por rutina.
3. Después de un journal8, reabrir intent exacto, validar ID/sequence de origen, descriptor canónico y fase, luego usar StageJournalOwner.recoverValidatedStage para STAGED o CutoverOwner.recover solo en sus fases. Rechazar PREPARING parcial y foreign pointer; completar ante journal post-switch solo con el origin pin persistido.
4. Cerrar/Rechazar sesiones previas por pointer ID+seq al cambiar, obtener nuevos grants locales explícitos por dominio para Pendientes; UI no abre upgrade por pulsar Chat. En B6 test negativo que manipule intent o fuerce pointer ABA.
5. Clase y pruebas solo en variante v2CoreDev. No modificar shipping, V4.4 ni historial S2.

## B5 — Adversarial STOP
- Intent ausente, incompleto, corrupto o symlink + journal8 existente: fail-closed, sin pointer CAS.
- Intent con originSeq falsificada, ID de origen no previsto, target/operation/descriptor extraños, otro journal pending, mismatch full pointer, puntero target seq distinta de origin+1, rollback distinto de origin+2: STOP; no adivinar ni reinicializar.
- Dos Activities o botón repetido: mismo guard process-wide, sin segunda operación; no borrar ni reemplazar intent existente.
- PREPARING parcial se conserva y bloquea; STAGED exacto se recupera sin reinicializar; crash tras pointer publish recupera con el mismo journal. Un resultado COMPLETED exige fence y journal materialWritesAccepted.
- B6 NO PASS hasta CI de nuevo host y tests negativos (al menos corrupción, crash/restart, old7 immutable, ABA y sin acto de usuario); B7 independiente debe revisar XML, ruta y diff. G15 físico al final.
