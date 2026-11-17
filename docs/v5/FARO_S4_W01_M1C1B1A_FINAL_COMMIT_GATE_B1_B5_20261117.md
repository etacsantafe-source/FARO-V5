# FARO V5 — W01 M1C1B1A · Post-receipt final COMMIT gate — B1–B5
Fecha AR 2026-11-17, HEAD base verificado 3c4d051af82ffbbf86452c8bbfbff393693c112a, writer CAS epoch1031. Unidad S4_W01_M1C1B1A_POST_RECEIPT_FINAL_COMMIT_GATE.
**DOC_ONLY** — código B6 NOT_RUN, permiso previo M1C1B0 BLOCKED/consumido. W2 STOP.

## B1 — Evidencia de implementación vigente
- M1C1B0 B7 PASS, code 0684cc59039c1dc031a6cd8e328cd685ab0c8ab8, final 3c4d051af82ffbbf86452c8bbfbff393693c112a, guard 44012762174 SUCCESS, writer RELEASED1030. RealityStore.finance9ConsumeGrantAgainstReceiptInsideApplyTransaction exige STAGED9, activeApplyScope, receipt real, policy epoch SQL, UPDATE grant used/receipt dentro del mismo SQLite BEGIN IMMEDIATE; 8/8 JVM y regresiones. **No reejecutar sus pruebas para probar una hipótesis antigua**.
- M1C1A B7 PASS scoped 9/9 JVM y compilación DEV; M1B Android Keystore CryptoObject implementado **dormido** y prueba de G15 NO_RUN; no hay composición entre callback humano y grant ni prueba biométrica del hardware.
- ApplyTransactionExecutor.kt actual ejecuta binding y comprobación previa ApplyTransactionAuthorizationPort; luego ApplyCommitPort.commit escribe receipt, comprueba identity y directamente AtomicApplyTransactionDecision.Commit. **Falta puerta explícita obligatoria para un host que deba consumir grant SQL después del receipt y antes del COMMIT**; la comprobación previa al receipt es insuficiente para ligar receipt real + grant. Firma de constructor existente: (transactions, commits, authorization? = null). Test actual ApplyTransactionExecutorTest.kt contiene harness RecordingTransactionPort y RecordingCommitPort reutilizables y casos de rechazo/rollback/autorización previos; no hay final-commit hook.
- Permiso global BLOCKED para unidad anterior y writer RELEASED al iniciar. No habilitar nueva autorización productiva con este bloque.

## B2 — Comparación V4.4/V5 y reutilización
- V4.4 AtomicApply y receipts son base durable; no modificar V4.4, migrar consentimientos ni restaurar grants desde backup.
- V5 ya tiene contratos AtomicApplyTransactionPort, ApplyCommitPort, ExecutionReceipt, ApplyAttemptIdentity, AppliedPlan, ApplyTransactionAuthorizationPort y rechazo transaccional, sin necesidad de otro motor o tabla. Aprovechar executor actual y harness de sus tests, no crear nueva capa Apply paralela.
- El nuevo post-receipt hook **no reemplaza** chequeo previo por nodo ni M1C1B0 SQL. Agregar cuarto parámetro opcional default null preserva todas las invocaciones actuales y evita alterar comportamientos legacy.

## B3 — Alternativas / investigación
- USAR: puerto interno puro `ApplyTransactionFinalCommitGate` suministrado SOLO por el futuro trusted Finance host. Llamado una vez después de que receipt real coincide con identity/plan, antes de `AtomicApplyTransactionDecision.Commit`, con plan, identity, receipt, nodos validados y active RealityTransactionScope. Retorna boolean; false => Rollback typed AUTHORIZATION_REJECTED, sin receipt durable; excepción => RecoveryRequired y rollback transaction.
- DESCARTAR: una lectura extra de ACL antes de receipt (TOCTOU), Kotlin monitor entre procesos, comprobación desde Compose/Chat, marcar COMMITTED en Workspace antes del receipt, un segundo SQLite transaction o callback después de salir de `run`.
- SIN CANDIDATO MATERIAL EXTERNO: no incorporar librería ni APIs externas. El kernel existente permite el control con menos superficie y sin dependencia/egress/costo. El host Finance SQL es una unidad posterior, por lo que pruebas JVM solo certifican orden y rollback del hook.

## B4 — Plan unido con aceptación binaria
Exactamente tres rutas:
- `v5/kernel/apply/src/main/kotlin/ar/com/faro/v5/kernel/apply/ApplyTransactionExecutor.kt`
- `v5/kernel/apply/src/test/kotlin/ar/com/faro/v5/kernel/apply/ApplyTransactionExecutorTest.kt`
- `.github/workflows/faro-v5-s4-w01-m1c1b1a-final-commit-gate-directed.yml`
Mantener constructor fuente compatible y `authorization` previo idéntico. Agregar interfaz `ApplyTransactionFinalCommitGate` en mismo archivo del executor; cuarto parámetro opcional `finalCommitGate: ApplyTransactionFinalCommitGate? = null`. API `allowCommit(preparedPlan,identity,receipt,checkedNodes,scope):Boolean`; `checkedNodes.toList()` congelada; false -> Rollback(Rejected(AUTHORIZATION_REJECTED)); exceptions -> RecoveryRequired.
Casos D01–D06 nuevos en `ApplyTransactionExecutorTest` existente, más regresión **completa del mismo archivo**:
D01 hook positivo ve receipt y orden después de binding y antes del COMMIT SQL;
D02 false después del receipt produce Rollback, sin COMMITTED publicado;
D03 revocación simulada luego de binding y antes de receipt => false, rollback;
D04 excepción en hook causa RecoveryRequired sin éxito ni commit SQL;
D05 receipt mismatch impide invocar hook y provoca Rollback;
D06 binding rechazado impide invocar hook/crear receipt;
CI `:kernel:apply:test --tests ar.com.faro.v5.kernel.apply.ApplyTransactionExecutorTest`, validar XML exactamente seis nuevos métodos y cero fallas/errores/skips en **toda la clase**; conservar tests legacy. Prohibido código de UI, grant issue, Android o product Apply.

## B5 — Auditoría adversarial previa
- Hook ejecuta DENTRO del bloque de `AtomicApplyTransactionPort.run`; `ApplyCommitPort.commit` es inserción provisional en SQL. Rollback remueve efecto/outbox/journal/receipt en RealityStore; el test `RecordingTransactionPort` prueba contrato/orden, no garantiza SQLite real (ya verificado aislado M1C1B0).
- Hacer coincidir receipt.transactionId y receipt.planFingerprint **antes** de invocar hook. Nunca permitir hook ante nodos fallidos, dependencias o fingerprint incorrecto. No compartir `checkedNodes` mutable. Retorno false debe bloquear el COMMIT; excepción es incertidumbre, no éxito.
- `finalCommitGate = null` deja intactos owners legacy, **pero NO proporciona seguridad para Finanzas productiva**. M1C1B1B deberá pasar un gate concreto que verifique M1C1B0 SQL, y prohibir cualquier camino DEV host sin ese gate.
- Receipt de test RecordingCommitPort no equivale receipt SQLite. El gate NO autentica humanos ni certifica BiometricPrompt, AndroidKeystore, G15 o dos procesos Android. La interfaz no será expuesta a Surface/Chat/API pública de producto.
- No tocar main, V4.4, Reality7/8/9 schema, S2/S3, M0/M1A/M1B/M1C0/M1C1A/M1C1B0 B7, G15, secrets, pagos reales, V2 productiva, Auto/Noche o W2. No iniciar B6 hasta guard documental SUCCESS y READY exact3 en commit documental distinto, con writer liberado.

## B6
NOT_RUN. READY independiente pendiente.

## B7
PENDING, requiere diff exact3, gate SHA, CI JVM XML real y writer RELEASED. PASS scoped kernel hook NO es certificado de host Finanzas real.
