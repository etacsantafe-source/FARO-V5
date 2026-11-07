# FARO V5 — S3.A4 N033C2B4 · autorización dentro de transacción (infraestructura mínima) B1–B5
Fecha: 2026-11-07 AR. Base exacta: 9830c95711acc997d2355e51d76949a17076593c. Unidad: S3_A4_N033C2B4_TXN_AUTH_HOOK. No es la ACL productiva, ni activa Planning8/V2.

## B1 — Evidencia real y aceptación limitada
- HEAD confirmado 9830c957, writer RELEASED epoch 472 antes de adquirir lease; queue CLOUD STAGED / activation_authorized=false; guard histórico SUCCESS #42406012688. Work permit previo BLOCKED. Último código faba6fed, B7 Android sandbox 56/56 #42405240480; no repetir.
- ApplyTransactionExecutor.execute prepara identity y bindings antes de entrar en AtomicApplyTransactionPort.run; dentro itera bindings, crea outbox y luego llama commits.commit. No existe puerto opcional de autorización dentro de la transacción. El test ApplyTransactionExecutorTest usa RecordingTransactionPort y certifica rollback y cero commit para fallas de binding/scope.
- RealityStore.run entra en BEGIN IMMEDIATE y garantiza rollback de Reality8, no permite concluir exclusión sobre mutaciones de Review guardadas en Workspace ni policy en otro proceso. FaroV5Planning8ApprovedObjectGate es READ_ONLY. FaroV5Planning8ObjectGrantOwner es session-only, depende de Planning8TrustedObjectPolicyPort todavía sin implementación productiva.
- Criterio B6 de ESTA unidad: permitir inyectar un verificador real de autorización al ejecutar cada nodo dentro del scope transaccional y volverlo a validar antes de escribir receipt; FALSE causa rollback tipado sin binding posterior/receipt; exceptions continúan RecoveryRequired; cuando no se inyecta se conserva comportamiento histórico Profile/Finance. A4 completa NO puede cerrarse con eso.

## B2 — Comparación/reutilización V4.4 y V5
- V4.4 separa Reminder/MonthlyPlan por owners; no aporta una ACL que resuelva revocación concurrente de V5. No migrar ni copiar código/datos V4.4 (V5 instalación limpia).
- Reusar AtomicApplyTransactionPort.run, OperationBindingPort, ApplyCommitPort, ApplyTransactionExecution.Rejected, y el executor existente. No duplicar persistencia de owner, esquema de Reality, policy tables, ni controller Android. Mantener ordinales y semántica antigua: extender el enum al final y parametrizar constructor opcional, sin cambiar las invocaciones existentes.
- Los bindings reales ReminderCreateBinding y MonthlyPlanItemAddBinding ya chequean target ID, owner revision, tipo, FK, duplicate y operación. El hook no repite esas validaciones ni crea grants.

## B3 — Investigación externa y decisión verificable
- OWASP Authorization Cheat Sheet https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html : default deny, validar cada pedido. ADAPTAR para verificación dentro de Apply y negar ante callback false.
- OWASP Authorization Patterns https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Patterns_Cheat_Sheet.html : PEP próximo al recurso protegido, con PDP confiable. ADAPTAR puerto inyectable cercano al commit; DESCARTAR que un gate UI boolean previo sea autorización.
- SQLite Transaction https://www.sqlite.org/lang_transaction.html : BEGIN IMMEDIATE serializa escritores de ESA DB, no acopla estados de Workspace/Review de otra DB. ADAPTAR rollback del scope; DESCARTAR inferir bloqueo cross-store/cross-process. Sin dependencia nueva ni servicio cloud.
- Android Application Fundamentals https://developer.android.com/guide/components/fundamentals.html : UID es sandbox de app, no identidad de persona. REFERENCIA para no inventar autenticación ni equiparar test principal con autorización productiva.
- Alternativas evaluadas: (A) callback opt-in sin cambio al flujo legacy, USAR para infraestructuras aisladas; (B) lectura de Review fuera del run, DESCARTAR TOCTOU; (C) nueva tabla ACL/esquema9/singleton global, DESCARTAR por scope y falta de source auténtico; (D) ejecutar hoy Planning8 en shipping, DESCARTAR por ACL incompleta y S4/V2 OFF.

## B4 — Plan mínimo delta-only
1. Añadir tipo ApplyTransactionAuthorizationPort en ApplyTransactionExecutor.kt: chequeo por PreparedPlanView, ApplyAttemptIdentity, ApplyPlanNode y RealityTransactionScope, sin resolver actores por sí mismo.
2. Constructor opcional por defecto null: **solo infraestructura**. El host actual no registra bindings Planning8 y no se modifica. Si se inyecta un verificador, rechazar al inicio de cada nodo, y volver a comprobar todos los nodos justo antes de commits.commit. Respuesta false: Rollback(Rejected(AUTHORIZATION_REJECTED,nodeId)) sin emitir receipt; excepciones quedan como RecoveryRequired.
3. Agregar pruebas dirigidas en ApplyTransactionExecutorTest: denial antes de binding; cambio de permiso tras ejecutarse un binding implica rollback; autorización válida chequeada dos veces e igual receipt; legado sin hook permanece intacto. Test real de revocación con Workspace/Reality queda para futura unidad de ACL completa.
4. Crear workflow directed de CI para :kernel:apply:test sobre push de esos dos archivos/workflow, con XML esperado EXACTO; mantener el guard siete bloques independiente. Sin actualizar FaroV5HostCertification, no firmar APK ni tocar G15.

## B5 — Auditoría adversarial y límite de certificación
- Verificar callback false dentro de AtomicApplyTransactionPort.run => aborta todos los cambios, outbox y receipt. Evitar retorno de Completed falso, ejecutar fail-closed si el hook lanza una excepción (RecoveryRequired, no Commit).
- No es un lock sobre cambios de política. Revocación DESPUÉS del último check y ANTES de SQLite COMMIT aún exige misma exclusión/consenso que Review de Workspace y política del owner, no resuelta. El hook no debe recibir un snapshot UI obsoleto ni inventar un actor.
- Restart/cross-process: no persistir grants en este cambio; el futuro host debe comprobar identidad, pointer ID+sequence, Review canónico, epochs host/policy, y retry PREPARED bajo fence antes de habilitar Planning. Sin eso la ACL PRODUCTIVA permanece STOP.
- No activar V2, Noche, Auto, S4, ampliar catálogo172, alterar main/V4.4, realizar pruebas físicas ni limpieza S5. Mantener B7 exact-SHA y auditoría adversarial de alcance.
- B5 DICTAMEN: READY **solo para TXN_AUTH_HOOK como infraestructura kernel testeable**, nunca READY para política real, permisos de Planning8, creación ni A4 final. Próxima unidad tras B7: N033C2B4_TRANSACTIONAL_PLANNING_ACL con policy auténtica y locks/Review serializables; después N033C3 Undo/retry.
