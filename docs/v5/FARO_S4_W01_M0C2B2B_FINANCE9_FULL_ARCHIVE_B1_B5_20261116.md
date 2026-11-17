# FARO V5 — S4 W01 M0C2B2B · FBS2v4 Finance9 cinco stores, B1–B5
Fecha local 2026-11-16. Unidad `S4_W01_M0C2B2B_FINANCE9_FULL_FBS2V4_CAPTURE_RESTORE`. Baseline HEAD `0325edd97d8ca5ac484146e751da6767e426bd46`. **PREAUDIT DOC_ONLY; B6 NO AUTORIZADO.** W02 STOP. Ninguna certificación anterior se repite.

## B1 — Evidencia comprobada en HEAD exacto
- M0C1 codec FBS2v4 + fence legacy B7 PASS scoped (6/6, run 43962889585); M0C2A sealed read Finance9 B7 PASS (7/7, run 43965425488); M0C2B1 authenticated FINANCE9-only restore B7 PASS (7/7, run 43979784062); M0C2B2A STAGED9 five-store graph B7 PASS (6/6, run 43982348068), 19/19 regresiones SUCCESS, cierre guard 43983409824 SUCCESS HEAD `0325edd97d8ca5ac484146e751da6767e426bd46`. Reutilizar, no retestear sin delta.
- Captura previa `FaroV5FiveStoreBackupCapture.kt` snapshot de 5 owners con una `SemanticBackupCaptureCoordinator` y G4+Planning pareados, pero retorna `finance9=null`; el puerto Finance9 sellado `FaroV5Finance9SealedSnapshotCapture.kt` lee ocho tablas Finance9 y rechaza WAL/sidecars, sin capturar otros owners.
- `FaroV5Finance9AuthenticatedSectionRestore.kt` autentica FAROBAK2/FBS2v4, restaura **sólo** las ocho tablas Finance9 en STAGED9 después de que sus FK de legacy estén satisfechas; no restaura Workspace/G4/Planning/otros owners ni fija `archiveIdentity`.
- `FaroV5FiveStoreStagedRestore.kt` pin durable de `RestoreArchiveIdentityPort` ANTES de escrituras, replay de ledger→G4→Planning→Evidence→Auxiliary→Workspace; RESTORE público y método stage8 rechazan FINANCE9 v4 ANTES del pin.
- `FaroV5GenerationStoreGraph.openValidatedV2Finance9Stage` abre cinco owners STAGED9, conserva pointer ID+sequence; no active ni product host. `FileRestoreArchiveIdentity.kt` fija SHA-256 del cifrado+op+target con registro durable, pero explícitamente no sustituye journal/replay fence.
- Permiso actual BLOCKED consumido por M0C2B2A; writer RELEASED epoch1002 antes de esta preauditoría. M1 interprocess authority y W01 UI siguen pendientes. No afirmar archive FBS2v4 íntegro por tener sección Finance9 sola.

## B2 — Reuso, comparación con V4.4 y compatibilidad
- V4.4 no posee Finance9, cinco-store FBS2v4 ni archivo FAROBAK2; no copiar utilidades legacy que pierdan G4/Planning/Finance9. Preservar semántica de backup/recuperación sin cambios visuales V4.4. V5 legacy FBS2 v1/v2/v3 debe quedar byte-compatible y denegar v4 por rutas anteriores.
- Adaptar captura con **una sola lease global** sobre cinco owners y Finance9; las herramientas selladas deben leerse con la generación cerrada/sealed ANTES de abrir el grafo SQLite, para evitar sidecars generados por conexiones abiertas. Revalidar pointer exacto y descriptor antes/después de ambas fases; una lease de DEV aún no prueba cross-process.
- Adaptar el replay stage-only existente (no copiar algoritmos de cinco owners): autenticar v4 completo; fijar una identidad durable de archivo; ejecutar ledger→G4→Planning y cuatro owners restantes en graph9, cerrar grafo; luego importar Finance9 con su restaurador autenticado sobre Reality9 cerrado/sealed. Revalidar exactamente ocho tablas y los otros cinco snapshots, pointer y archive pin tras crash/retry.
- **No** activar ni cambiar pointer, ni trasladar permisos ACL, ni crear un segundo writer. No ampliar admisión schema9 a apertura legacy o activa.

## B3 — Investigación externa y decisión
- SQLite Online Backup API https://sqlite.org/backup.html: opción REFERENCIA para snapshot coherente de **un** archivo; se descarta copiar archivos binarios como formato FAROBAK2 por falta de semántica portable y whitelist ACL.
- SQLite WAL https://www.sqlite.org/wal.html y ATTACH https://www.sqlite.org/lang_attach.html: opción DESCARTAR para prometer atomicidad entre varios DB en WAL; SQLite sólo garantiza atomicidad por archivo, no de los cinco como conjunto. Necesarios pin inmutable + replay idempotente + stage sin cutover ante crash.
- SQLite transaction/FK https://sqlite.org/transactional.html y https://www.sqlite.org/foreignkeys.html: ADAPTAR semántica de transacciones separadas por owner y orden de FK de Reality para restauración segura.
- FAROBAK2 AES-GCM/PBKDF2 existente: REUTILIZAR (ya autenticado/certificado), no introducir librería, API, export en nube ni clave persistida. Alternativa raw SQLite/ATTACH o serializar SQL/ACL: RECHAZAR.

## B4 — Contrato y alcance exacto B6 sujeto a permiso independiente
Paths previstos (cerrar lista tras revisión de dependencias; ningún path extra por conveniencia):
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreBackupCapture.kt` (extensión captureFinance9 stage-only, sin cambiar captura v1-v3).
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5FiveStoreStagedRestore.kt` (helper interno v4 para reusar replay existente; mantener RESTORE público denegado a v4).
3. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Finance9FullArchiveCoordinator.kt` (nuevo, captura y restauración completa, pinned pointer, una lease, archiveIdentity, stage-only; NO activate).
4. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Finance9FullArchiveCoordinatorTest.kt` (nuevo, negativos y crash replay).
5. `.github/workflows/faro-v5-s4-w01-m0c2b2b-finance9-full-archive-directed.yml` (nuevo, JVM exact + JUnit XML + artifact).

Criterios binarios de aceptación: (T01) captura snapshot **real** Finance9/legacy/G4/Planning y 4 stores auxiliares bajo una lease; (T02) archive FAROBAK2+FBS2v4 descifra cinco secciones y no contiene ACL/grants; (T03) restaurar datos interdependientes en STAGED9 fresco, sin alterar ACTIVE; (T04) reintento idéntico no duplica receipts ni filas; (T05) inyección de crash después de Reality/G4/Planning y tras Finance9 permite replay de **mismo** op/archivo/target; (T06) sustitución de archivo/op/target bloqueada por pin; (T07) contraseña/tamper/legacy v1–v3 bloqueados sin mutación de target; (T08) target nofresh, foreign data/ACL o FK rota DENY; (T09) descriptor/pointer stale/ABA/schema8/sidecars DENY; (T10) no App/host/activación/export/pago y regresión legacy conservada. Si T01..T10 no son verdaderos, B7 = BLOCKED sin relajar expectativas.

## B5 — Auditoría adversarial previa
- Orden de FK crítico: no insertar apertura Finance9 mientras cuenta/ledger referidos no se restauraron. Los stores tienen commits independientes; crash puede dejar parcial STAGED, jamás promocionar esa generación antes de replay verificado y journal/fences de M1. No inventar transacción global.
- Archive pin SHA del cifrado, operación y target debe preceder primer write. Pin existente con archivo distinto = STOP; si error tras varios stores, reentrar **misma** identidad y restaurar/verify, jamás auto-limpiar/auto-activar.
- La captura Finance9 sellada y la apertura del graph pueden producir conflictos de sidecar; leer Finance9 sealed bajo lease antes del graph y verificar pointer repetidamente. Prohibir lectura de source ACTIVE/foreign y la sustitución durante ventana entre reads.
- Rechazar ACL/grants/policy desde backup; al restaurar, destino debe conservar ACL inicial deny-by-default sin recuperar tokens o permisos. No declarar exclusión interproceso por quiescence inprocess (M1 pendiente).
- El respaldo no es automáticamente aplicable ni disponible a producción. Requerir XML por caso, guard exact SHA y B7 independiente; si el plan no puede compilar en rutas acotadas, volver a B5 y emitir nuevo permiso DOC_ONLY, nunca improvisar expansión.

## B6 y B7
**B6 = NOT_RUN.** Antes de código: publicar este documento, verificar guard en SHA documental, emitir `READY` de unidad exacta en commit DOC_ONLY separado, verificar cinco rutas contra gate preflight y adquirir writer nuevo con HEAD actual.
**B7 = PENDING.** Exigir CI exacta con logs y JUnit XML T01–T10, guard del código, diff y ausencia de cambios en main/V4.4/S2/S3, pin y replay, luego checkpoint durable y writer RELEASED. M1 y W01 UI sólo después. W02 STOP.
