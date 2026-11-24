# FARO V5 · W05-A1B3C1C · B7 PASS scoped · Android DEV SQL11 closed-host startup/reopen

Fecha: 24/11/2026 (Argentina). Rama `work/s3-night-20261102`. Solo desarrollo aislado, sin habilitar stock real ni V2 productiva.

## B1–B5 y decisiones previas
Preauditoría original `docs/v5/FARO_S4_W05_A1B3C1C_ANDROID_SQL11_CLOSED_HOST_B1_B5_20261124.md`, blob `25f8a09be80b3c23641ad78dec9b37f8eac6b6a7`, guardia `45503157662` SUCCESS. Permiso exact6 previo y guard `45503337713` SUCCESS. C1B ya B7 PASS con SHA `432e6ece49eb50d0e7a607e581eab6c975c5cac4`, CI `45495586732` 8/8 y guardia `45495586735`. No repetir trabajo certificado.

La primera implementación C1C en `39d458c84fcfddf12034d0265966a707c6c9b66e` hizo seis rutas (host DEV, startup Android y Activity DEV, dos pruebas, workflow). Guardia `45506514385` SUCCESS. Primera CI `45506514281` FAILURE de **task selection** Gradle antes de test; RED_FIX auditado `docs/v5/FARO_S4_W05_A1B3C1C_RED_FIX_GRADLE_UNIT_TASK_45506514281_20261124.md`. Permiso RED_FIX exact1 y ajuste `ce12c6f2bbb5c13faafdd8d4930c4cd19335e801`; guardia `45507479250` SUCCESS; CI `45507479289` XML16/16 PASS scoped.

La auditoría adversarial de C1C halló segundo inicio ACTIVE SQLite WAL aún no probado: C1B exige STAGED sellado y no admite sidecars WAL/SHM de lectura activa. RED_FIX B1–B5 `docs/v5/FARO_S4_W05_A1B3C1C_RED_FIX_ACTIVE_WAL_REOPEN_B1_B5_20261124.md`, blob `da29e861006875b8f8e3fc2bc6432e5e41c1d4a4`, permiso exact4 guard `45508511376` SUCCESS. Código `41dab6671ae2e998bd80f17b2f16034cbadf6db5`: ruta `COMPLETED` se abre ACTIVE y valida ticket/journal/fence/pointer completo bajo mismo root lock y gate; para journal pendiente C1B mantiene recuperación exclusiva. `ACTIVE` usa readonly WAL sin `immutable=1`; Android rechaza revisiones/conteos distintos a ticket original. CI inicial de este código `45508825833` FAILURE **antes de Gradle** por contador workflow 1/6 que omitía 4 rutas. RED_FIX B1–B5 + B7 de ese SHA en `docs/v5/FARO_S4_W05_A1B3C1C_RED_FIX_WAL_CI_ROUTE_COUNT_45508825833_20261124.md`, blob `7390b4471bc91b3721d755203ac7901e18719ac5`. Permiso exact1 emitido y guard `45509184967` SUCCESS.

## B6 — Implementación final y pruebas reales
Último código `8ffc0fa5af6df44aa295d2dc96d862aedbc6462a`: RED_FIX de una ruta workflow, acepta únicamente contadores exactos 1/4/6 y hace preflight de **cada ruta** contra permiso del HEAD padre; no modifica Java/Kotlin ni certificados previos. Diff parent: un archivo workflow con una condición booleana modificada. Guardia de protocolo para SHA final `45509361802`: COMPLETED SUCCESS.

CI dirigida **`45509361871`**, job **`138852770320`**, SHA exacto `8ffc0fa5af6df44aa295d2dc96d862aedbc6462a`: COMPLETED SUCCESS. Parent READY exact1 PASS, Java21 y Android SDK37, Gradle `BUILD SUCCESSFUL in 4m 17s`, tareas `:store:generation:test` y `:app:testV2CoreDevUnitTest` compilaron y finalizaron. Comprobador original JUnit XML emitió `FARO_W05_A1B3C1C_ANDROID_SQL11_DEV_REOPEN_XML_PASS [18, 0, 0, 0]`.

Artefacto real GitHub **`14974808496`**, `w05-a1b3c1c-android-sql11-original-xml`, 1715 bytes, `sha256:2d1c33cb71b1232f9c5c3964a6c262d4af6f92314533cd3d521d40004ac0cc61`. Archivo ZIP original descargado, SHA256 corroborado con Python `hashlib`, dos XML originales leídos con `zipfile` y `xml.etree.ElementTree` y testcase individuales contados:
- `FaroV5Household11ActiveDevReadHostTest` **12 tests; failures0, errors0, skipped0**.
- `FaroV5W05Household11DevStartupTest` **6 tests; failures0, errors0, skipped0**.
- **Total real 18/18 PASS, 0/0/0**. ZIP original y XML inspeccionados, no se infiere PASS por los nombres de workflows.

## B7 — Auditoría final y matriz adversarial
**B7_PASS_SCOPED_W05_A1B3C1C_SQL11_DEV_ANDROID_STARTUP_ACTIVE_WAL_READONLY_REOPEN_XML18_18**.

Evidencia de prueba original: `completedSecondReopenAfterReadOnlyWalInspectionPreservesFullPointer()` verifica doble apertura ACTIVE con readonly SQLite/WAL y mismo pointer; `pointerAbaRevokesLeaseEvenIfGenerationIdReturns()` bloquea reutilización ABA; `pendingValidatedTicketDoesNotAutoSwitch()` exige autorización foreground; `rootLockCollisionFailsClosedWithoutLease()`, `tamperedTicketIsNeverAdopted()`, `symbolicRootIsRejected()`, `postClosePortCannotReadOrReactivate()`, `revokedForegroundDeniesFurtherReads()`, `uncommittedWalIsNeverReadAsACommittedRevision()`, `noTicketIsUnavailableWithoutNewGeneration()` y `absentDirectoryFailsClosed()` cubren negativas. Android `exactArchivedCountsAndRevisionsAreRequiredBeforeUiDisclosure()` y política allowlist dos variantes DEV, no-debug/productive/otras variantes DENY.

**Límite obligatorio de certificación**: es CI JVM/Android `testV2CoreDevUnitTest`, no una prueba de dispositivo físico ni instrumental `connectedAndroidTest`. No se ha activado un grafo Reality11 productivo, no se ha probado una UI real con usuario G15, no se ha autorizado mutación de stock/Apply/Undo ni se ha cerrado recuperación FBS2v8 Evidence2. Este pase solo certifica implementación DEV/read-only en la ruta exacta de C1C. V2 productiva, Auto/Noche, pagos y stock real permanecen OFF; main/V4.4 intactos.

B7 aceptación: C1C **PASS ACOTADO** por sus 18 tests y guardia; Evidence2 FBS2v8 restore/cutover y pruebas integrales globales A1 siguen pendientes. A1C posterior Review/Apply/Undo de stock requiere autorización y certificación independiente. No repetir W1/W2/W3/W4 ni C1B. Tras este documento: consumir permiso READY a BLOCKED, registrar `last_code_review` B7 PASS con blob exacto, actualizar puntero durable y liberar writer; verificar HEAD remoto y guardia final. No declarar global A1 cerrado.
