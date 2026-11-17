# FARO V5 — W01 M1C1B1B · Finance9 Android STAGED post-receipt gate · B1–B5
Fecha local: 2026-11-17. Unidad candidata: S4_W01_M1C1B1B_FINANCE9_ANDROID_STAGE_GATE.
Base auditada: HEAD 3d1c9944200e4c3c15a1a2575e9df96b3118693d, rama work/s3-night-20261102.
Ejecución de esta unidad: DOC_ONLY; B6 NOT_RUN; B7 NOT_RUN; permiso funcional BLOCKED.
Objetivo: preparar el puente explícito Reality9 ↔ ApplyTransactionFinalCommitGate ↔ host Android de pruebas aisladas, SIN habilitar Apply productivo ni inferir autenticación física. W2 STOP.

## B1 — Evidencia del HEAD, permisos y brecha exacta
- AGENTS.md, FARO_CONTINUITY_POINTER.json, FARO_PROTOCOL_WORK_PERMIT.json, protocolo de siete bloques y retro auditoría leídos. El permiso anterior S4_W01_M1C1B1A está BLOCKED/consumido. Writer global fue confirmado RELEASED epoch1037 sobre HEAD 3d1c994... antes de tomar writer DOC_ONLY epoch1038.
- M1C1B1A: B7 PASS scoped código 68db898115683e7acf3c36d156447908e0de6341; CI 44035460133 SUCCESS 13 legacy + 6 nuevos =19/19 XML, regresión S3 A4 44035460249 SUCCESS, guard 44035460120 SUCCESS. Hook opt-in ApplyTransactionFinalCommitGate se ejecuta después del receipt coincidente y antes de COMMIT en un solo Reality transaction scope; null conserva legacy.
- M1C1B0: B7 PASS scoped código 0684cc59039c1dc031a6cd8e328cd685ab0c8ab8, CI 44012135325 SUCCESS 8/8. RealityStore.finance9ConsumeGrantAgainstReceiptInsideApplyTransaction exige scope REAL activo, schema9 STAGED, receipt de la misma SQL transaction, policy epoch, generation, pointer sequence, fingerprint, grant UNUSED, y cambia used_at/receipt antes del COMMIT. El método y Finance9AclScope son internal al módulo store: app NO puede llamarlos directamente sin un puente explícito.
- M1A policy epoch durable SQL, M1C0 2 procesos OS, M1B AndroidKeystore/CryptoObject dormido 9/9 + APK DEV, M1C1A Review/presencia estructural 9/9: TODOS B7 PASS scoped, NO repetir.
- S4W01FinanceAuthHost sólo emite evidencia técnica después de BiometricPrompt.CryptoObject; S4W01M1C1AReviewPresenceBinder sólo valida consistencia de Review/presencia, NO concede policy ni grant. S4W01B2C0FinanceDevApplyHost usa grant simulado/in-process y declara explícitamente NO shipping, NO auth humana, NO cross-process fence; no promoverlo.
- Falta una composición obligatoria de host STAGED9 con el post-receipt gate que consuma grant real o rechace la transacción. Principal estable ligado al Keystore, origen no falsificable del callback, emisión durable de grant desde consentimiento humano, Android G15 y prueba física NO están certificados. Una prueba con objeto JVM falsificado no basta.
- Regresión S3 integrada 44004832751 FAILURE de otra unidad sigue separada sin certificación global; no ocultarla ni reejecutarla por inercia. Main, V4.4, S2 y S3 frozen.

## B2 — Comparación V4.4/V5 y reutilización
- V4.4 define AtomicApply, PREPARED/COMMITTED, receipts, Undo, startup recovery e idempotencia. Reutilizar ese núcleo y el nuevo hook V5; no introducir un segundo motor ni un segundo COMMIT.
- V5 Reality9 M0 ya tiene FBS2v4, StageGraph, SQL ACL monotónica y revocación interproceso. Los grants nunca viajan por backup. M1C1B0 ya certificó vinculación grant+receipt real; no reimplementar SQL ni migraciones.
- V5 M1B/M1C1A poseen challenge firmado y binding Review, pero su uso fiable exige el trusted host; una referencia de UI/Chat a un objeto data class no es prueba criptográfica. El puente propuesto acepta datos de un gate ya aprobado por host, jamás una capability enviada por Intent o Chat.

## B3 — Investigación de alternativas primarias
- Android BiometricPrompt CryptoObject (https://developer.android.com/reference/android/hardware/biometrics/BiometricPrompt.CryptoObject): ADAPTAR autenticación por uso de Signature y comprobación del objeto CryptoObject. No confundir autenticación local con autorización transaccional ni aceptar datos sueltos desde otro proceso.
- Guía Android biometric (https://developer.android.com/identity/sign-in/biometric-auth): USAR patrón CryptoObject tras review visible y en foreground, sin fallback silencioso a un boolean, credencial débil o aprobación anterior. El hardware G15 exige ensayo específico posterior.
- SQLite BEGIN IMMEDIATE/COMMIT (https://www.sqlite.org/lang_transaction.html): USAR un único write transaction SQL. Otro proceso obtiene lock después del COMMIT previo; revisar policy epoch y consumir el grant dentro de esa transacción evita TOCTOU.
- DESCARTAR: finalCommitGate=null en Finance9; post-commit ACL; Kotlin synchronized interproceso; transferir presencia en Parcelable/Intent; cuenta o fingerprint a partir de texto Chat; inyección de grant desde tests en shipping; un nuevo store para grants; red/API paga.
- SIN DEPENDENCIA EXTERNA NUEVA para B6 STAGED: Kotlin, módulos internos, SQLite y AndroidX Biometric ya presentes. Licencias/deps nuevas: no requeridas. No implementar Key Attestation remoto: excede LOCAL_ONLY y no sustituye review/grant local.

## B4 — Diseño DELTA ONLY y aceptación binaria
Se propone **primero un seam STAGED9 SOLO PARA DEV**, no product Apply. Una API pública *estrecha* entre módulos representará un único grant previamente emitido por un issuer confiable (NO emitir en este gate), con datos inmutables: principalHash derivado/verificado por host, generationId+pointerSequence, operationId, objectScope, expectedPolicyEpoch, grantId, planFingerprint, attempt identity y deadline. No exponer conexión SQLite ni Finance9AclScope internal al app.
- Puente Reality: factory/port sólo instanciable desde RealityStore STAGED9 y ligado a la misma instancia de conexión/lease. Traduce a Finance9AclScope internamente y delega únicamente a finance9ConsumeGrantAgainstReceiptInsideApplyTransaction; deny/throw ante schema7/8, scope ajeno o store cerrado.
- Gate Android DEV: dependencia obligatoria (no nullable) de ApplyTransactionFinalCommitGate, un único nodo Finance F02/F03, Review APPROVED canónico estable revalidado, fingerprint/operation/objectScope igual al grant, pointer+host epoch aún vigentes, evidencia local que no pueda obtenerse desde Surface. Validación antes de ejecutar + consumo EXACT ONCE post receipt dentro del mismo transaction scope; false => Rollback, excepción => RecoveryRequired.
- M1C1B1B no implementará emisor biométrico real, onboarding de principal ni habilitación shipping: si falta principal derivado y verified callback desde AndroidKeyStore, el host de producción permanece NO MOUNTED. Tests JVM usarán issuer faked marcado DEV y sólo certificarán wiring/SQL, jamás identidad.
- Rutas candidatas exactas (requieren READY individual futuro; lista NO ES PERMISO):
  1. v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt
  2. v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedStageGrantPort.kt (NEW)
  3. v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01M1C1B1BFinance9StageGate.kt (NEW)
  4. v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9TrustedStageGrantPortTest.kt (NEW)
  5. v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01M1C1B1BFinance9StageGateTest.kt (NEW)
  6. .github/workflows/faro-v5-s4-w01-m1c1b1b-stage-gate-directed.yml (NEW)
- Pruebas diseñadas, TODAS NOT_RUN: G01 success un receipt real+grant usedAt en mismo COMMIT; G02 revocación proceso B antes del lock => rollback sin receipt durable; G03 revocación intentando durante transacción => serialización; G04 fingerprint/operation/scope mismatch denegado; G05 pointer ABA, host epoch/stale Review, caducidad y replay denegados; G06 receipt mismatch/no hook/rollback; G07 excepción post receipt => RecoveryRequired sin efectos persistidos; G08 schema7/8 y otra generación denegados; G09 legacy Apply sin gate conserva S3 13 tests (no rehacer salvo necesidad causada por diff); G10 DEV host no registrado ni Activity/exported shipping.
- CI futura en SHA exacta: :store:reality:test y :app:testDebugUnitTest con clases G01–G10 y XML nominal, guard seven-block-evidence, comprobación de diff exact6 y ausencia de cambio de manifest/flags. APK DEV aislada si el compilador Kotlin/Android requiere validación. G15 físico = NOT_RUN.

## B5 — Auditoría adversarial previa y decisión
1. Riesgo cross-module: Finance9AclScope y el método de consumo son internal; no cambiar visibilidad arbitrariamente ni permitir acceso a SQL. La factory STAGED del owner Reality es el único puente admisible; si no se puede demostrar su cierre, STOP.
2. Riesgo callback falsificado: el resultado S4W01FinanceLocalPresenceEvidence es una data class que un test puede fabricar. El código de M1C1B1B no puede afirmar verificación humana sólo por sus campos. No exponer puerta producto, y más adelante certificar firma del challenge con clave fija Keystore bajo contexto autenticado, UID, generation y Review enlazado.
3. Riesgo ACL/ABA: el estado durable policy_epoch no puede reinicializarse; recapturar epochs bajo BEGIN IMMEDIATE y mismo scope. Si revocation gana el lock => deny; si el COMMIT gana primero => recibo durable con autorización vigente al momento serializado. Nunca validar policy sólo antes del receipt.
4. Riesgo semántica del recibo: provisional receipt en SQLite NO significa resultado publicado. Gate false exige Rollback y grant queda UNUSED; excepción exige RecoveryRequired, nunca COMMITTED, y recuperación reconciliada por recibo real. Doble tap/replay no produce segunda ejecución.
5. Riesgo de admisión: Stage9 opt-in; schema7/8, generación equivocada, token caducado, review mutado, UID/host epoch inconsistente, key invalidated o proceso no foreground => DENY. No aceptar parámetros de Internet, Chat, Intent, cloud, backup, activity result ajeno.
6. Riesgo scope: ninguna ruta de main, V4.4, S2/S3, Android shipping, build flags productivos, Auto/Noche, pagos reales o W2 puede alterarse. Mantener el catálogo oficial de 180 y LOCAL_ONLY.
7. Límite binario: esta preauditoría justifica **solamente** una futura unidad DEV STAGED9 de wiring. No certifica hardware G15, principal humano productivo, issue de policy/grant desde consentimiento, habilitación productiva, UX financiera ni cierre global W01. Si el permiso READY o preflight de cualquiera de seis rutas falla, B6 STOP.

## B6
NOT_RUN. No hay permiso READY de M1C1B1B; primero guard documental SUCCESS en el SHA de este documento, luego permiso exact6 independiente en commit DOC_ONLY y preflight de cada ruta.

## B7
PENDING. B7 de esta unidad exige código exact6, CI/XML real, guard independiente, post-receipt SQL rollback y writer RELEASED; cualquier prueba física será un gate separado.

## Checkpoint documental
Sin código funcional; sin test nuevo; solo deja preparados B1–B5 de M1C1B1B. M1C1B1A, M1C1B0 y anteriores se conservan PASS; W01 B/C/D/E/F OPEN, W02 STOP.
