# FARO V5 S4 W01 B3B1B1B1B1 — Revocación interproceso y sesiones de lectura Finance9
Fecha AR 2026-11-17. HEAD base 76b4ed0411cb0f67e08cd7e2f712c119c4c3786e. MODO DOC_ONLY, sin nuevo READY ni código.
Padre B3B1B1B1B0 ya B7 PASS scoped DEV: code bb20a48266f69ded178f5e9c806c8127149fdfe9, CI 8/8 JVM + compile Android DEV y 5/5 workflows SUCCESS, checkpoint 76b4ed0411cb0f67e08cd7e2f712c119c4c3786e, guard 44119423391 SUCCESS, writer RELEASED epoch1108. No repetir.

## B1 — Evidencia exacta de las dos autoridades
- RealityFinance9Acl.kt (blob 0b7effb6d5d75c9cc511f957c34cd6ecd8ecf467) define key principalHash/generationId/pointerSequence/operationId/objectScope, issueAfterTrustedStageAdmission y revoke con BEGIN IMMEDIATE, revocación persistente, epoch y global revision monotónicos. Es un owner SQL STAGED9, NO un broadcaster ni Android trust.
- RealityFinance9AclCrossProcessTest.kt (blob 4bb4c362bf0681962d4c3f91470921e34007b3e1) y RealityFinance9AclProcessWorker.kt (blob 9daf54909c9ef6a601c6101a8d35032760349bcd) prueban dos PIDs reales Kotlin+Python sqlite3, incluidas revoke antes/después del commit, ABA de epoch y bloqueo BEGIN IMMEDIATE. El worker Python puede modificar finance_acl_policy y finance_acl_state directamente, sin avisar a una Activity/ContentObserver. NO repetir esta certificación; un test nuevo debe cubrir exclusivamente el lector READ finance.snapshot.read.
- RealityStore.finance9TrustedReadPolicyIssuerForDevOnly emite policy READ scope fijo sin grant Apply; el host DEV S4W01FinanceReadAuthHost devuelve solo Finance9ReadPolicyIssueRecord en memoria tras CryptoObject. El record no concede un lease de UI.
- RealityStore.finance9ObservedSourceReadForDevOnly obtiene policy + cuentas de la misma snapshot WAL; S4W01B3B1B1B0ObservedReadFence hace segundo snapshot y verifica pointer/host. Un remote revoke tras ese instante puede dejar el DTO viejo expuesto.
- FileActiveGenerationPointer.kt addChangeListener es solo intra-process, sin aviso de cambio de generación remota. FaroV5HomeReadinessInvalidations.kt solo readiness/aux y puntero local.
- FaroV5FinanceReadSurface.kt tiene invalidations Flow<Unit> y limpia el estado en Compose al recibir eventos, pero no observa ACL remota, STOP/RESUME ni pérdida de eventos confiablemente. Shell shipping NO monta Finanzas.
- AndroidManifest.xml tiene InitializationProvider androidx.startup y carece de proveedor app-private Finance. Existing FaroV5A5TestContentProvider.kt es fixture DEV de lectura de archivo y no es una fuente ACL ni se reutiliza como tal.

## B2 — Reutilización y ausencia de contaminación
Reusar contratos Finance9 policy epoch/version, cierre de owner, ACL WAL con rollback, pruebas dos PIDs, full pointer+sequence, B3B1B1B0 WAL observed fuente y B3A UI vacío al invalidar. Preservar un solo writer real y receipts idempotentes del núcleo V4.4, sin re-Apply. No modificar V4.4/main/S2/S3, FBS2v4, DDL schema7/8/9, Finance ledger, permisos de Apply, W2, ni V2 productiva.

## B3 — Alternativas, investigación y decisión
A SELECCIONAR: dueño revocador READ exclusivo dentro de RealityStore STAGED9. Su entrada typed solo acepta principal SHA256 hex64, generation y pointerSequence válidos, sin operationId ni objectScope arbitrarios. Invoca existing RealityFinance9Acl.revoke con finance.snapshot.read/finance:all y retorna resultado durable after COMMIT, distingue missing/no-op de un revoke nuevo mediante epoch+revision si es necesario. No emite grants ni toca balances.
B CANDIDATO CONDICIONAL: cuando todos los escritores reales pasen por owner, registrar un ContentProvider interno android:exported=false con URI app-private y usar ContentResolver.notifyChange DESPUÉS del commit de revoke/issue, owner restore y pointer cutover. El observador expone solo señal sin cuentas/principal/epoch como payload. Desde Android O una notificación registrada debe referir URI respaldada por provider. El observer debe recomprobar SQL/active pointer y no conceder permisos.
C OBLIGATORIO: lifecycle del host readPort: limpiar saldos en STOP/background, cancelar lecturas in-flight, invalidar onStart/onResume y revalidar generation+sequence, hostEpoch, principalHash, policy_epoch, globalRevision y opening source antes de cada render. La expiración<=30s debe generar limpieza incluso sin evento remoto.
D RECHAZAR: FileObserver o callbackFlow sin publisher, confiar en notificaciones sin reread, tomar la igualdad de dos snapshots como lease permanente, o un polling mientras saldos revocados permanezcan visibles.
Refs Android https://developer.android.com/reference/android/content/ContentResolver y https://developer.android.com/reference/android/database/ContentObserver y https://developer.android.com/topic/libraries/architecture/views/coroutines-views . SQLite https://www.sqlite.org/isolation.html .

## B4 — Partición de ejecución, NO READY
B3B1B1B1B1A (siguiente candidato acotado): REVOCADOR READ SQLite DEV. Rutas propuestas NUEVAS RealityFinance9TrustedReadPolicyRevocationPort.kt y RealityFinance9TrustedReadPolicyRevocationTest.kt, RealityStore.kt y workflow dirigido. Debe exigir scope READ fijo, ningún grant Apply, usar mismo BEGIN IMMEDIATE de ACL, contar monotonicidad epoch, comprobar absent/foreign/expiry/pointer, rollback fault y dos PIDs contra REAL WAL. No modificar schema ni issuer. Requiere un B5 independiente, permiso exact4, guard/prefight, B6/CI y B7.
B3B1B1B1B1B (después): publisher Android real + listener de invalidaciones/lifecycle, con manifest/provider si la revisión de owners confirma que cubre todos los commit paths. Debe asegurar post-commit signaling sin duplicación y reconciliar missed events al RESUME. Tests de dos procesos Android G15 aún NOT_RUN. El publisher no debe ejecutarse si una ruta de revocación no puede notificar.
B3B2: montar una lectura DEV aislada solo tras B7 acotados, auditar BiometricPrompt hardware, invalidaciones Android, source Finance9 y políticas de limpieza de pantalla. V2 shipping sigue OFF.
Ninguno de los candidatos tiene READY. Estado actual: B7 cerrado de B3B1B1B1B0, protocolo permit BLOCKED.

## B5 — Auditoría adversarial de la ventana de revocación
- El worker remoto de pruebas escribe directamente en SQL. Un ContentObserver basado en notifyChange del host no puede saber de esa escritura si no existe un publisher en el proceso que hace COMMIT; para la app productiva se debe cerrar el conjunto permitido de writers, no fingir 100% cobertura.
- El aviso puede perderse por cierre, transición foreground/background o cola del handler. No es una credencial; al RESUME, back stack, rotación y nueva lectura se exige revalidación durable y limpiar cuentas antes de mostrar.
- Una revocación DESPUÉS del último SELECT y ANTES de render sigue siendo posible: no prometer cero-latencia ni afirmar que un listener resuelve atomicidad SQL+Compose. Si se exige garantía fuerte de no visualizar saldo revocado, definir propietario/lease que no permita esa ventana o mantener UI inactiva.
- Revocaciones de un principal o pointerSeq no pueden invalidar otro por coincidencia accidental de generationId, y un nuevo policyEpoch tras ABA jamás resucita el permiso anterior. Un no-op revoke no debe anunciarse como nuevo epoch.
- No reconocer un issuer SQL de prueba como humano; solo un verdadero callback AndroidKeyStore auth-per-use y UID app pueden iniciar una sesión DEV. Política de read separada de Apply.
- Tests futuros: dos PIDs SQL read-revoke-verify y rollback, epoch y política externa; notificaciones Android físicas requieren autorización y G15, no son tests JVM.
- Nunca escribir saldo en log o evento, no API paga, no conectar UI ni autorizar V2 productiva, no repetir certificaciones previas.
DICTAMEN B5: PASS SOLO DOCUMENTAL para investigación/ B5 independiente de REVOCADOR READ STAGED9. NO READY para publisher/host o shipping, sin pruebas nuevas ejecutadas en este checkpoint.

## B6
NOT_RUN — DOC_ONLY, no cambios funcionales.
## B7
PENDING. La preauditoría debe verificarse con guard, checkpoint y writer RELEASED antes del próximo código.
