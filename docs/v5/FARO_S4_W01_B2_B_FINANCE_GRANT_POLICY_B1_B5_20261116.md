# FARO V5 — S4 W01-B2-B · Owner Finance grant/policyEpoch DEV · B1–B5
Fecha local 2026-11-16. DOC_ONLY, NO READY, sin código.
Depende de W01-B2-A B7 y su guard + writer RELEASED.
B2 general: docs/v5/FARO_S4_W01_B2_FINANCE_APPLY_PREAUDIT_B1_B5_20261116.md.

## B1 — Estado real y brecha
W01-B2-A valida alcance de Finance en Workspace APPROVED y
catálogo oficial, sin grant ni Apply. W01-B1 crea PENDING;
la aprobación humana durable se almacena por Workspace.
Planning8ObjectGrantOwner.kt tiene nonce por objeto, epoch
de política, revocación y withCurrentGrantLease alrededor
de una acción síncrona, PERO su documentación admite:
el monitor in-memory no detiene cambios externos de policyEpoch,
intercambio de generación, otro proceso ni revocación externa
después de la última comprobación y antes de COMMIT.
No hay un owner Finance independiente de política/ACL de Apply
certificado. Reusar grants de MonthlyPlan/Reminders implicaría
transgredir owner authority. FaroV5AndroidSandboxPrincipal
solo demuestra principal técnico de instalación, NO persona.

## B2 — Reutilización sin heredar autoridad
Separar la función de solicitud del usuario, la política local de
Finance, el grant owner, y una lease que cubra ENTERA la
transacción Reality; utilizar puntero completo con sequence,
hostLifecycleEpoch y MaterialWriteGate existentes. No almacenar
grants en Workspace ni en la respuesta de la IA.
Mantener B1/A/B0/B2A certificados sin repetir.
Policies Household/MonthlyPlan no se transfieren a Finance.

## B3 — Alternativas y decisión
A: nuevo owner Finance por scope operationId+objectId+accountId,
policyEpoch independiente, tokens nonces revocables, default-deny;
dev-only primero. Futuro host debe asegurar revocación cross-process
hasta COMMIT con barrera durable autorizada. ELEGIDA CON CONDICION.
B: usar Planning8Owner sin adaptar scope: RECHAZADA.
C: dar Apply por estado APPROVED/foreground boolean: RECHAZADA.
D: considerar monitor Kotlin como barrera cross-process: RECHAZADA.
E: persistir grants en backup/restaurar nonce: RECHAZADA.
Fuentes SQLite transactions https://www.sqlite.org/lang_transaction.html,
Android offline-first https://developer.android.com/topic/architecture/data-layer/offline-first.

## B4 — Descomposición y rutas candidatas sin READY
**B2-B0**: policy owner DEV acotado, libre de código UI/Reality,
que solo permite emitir/revocar scope Finance bajo principal
técnico independiente, policyEpoch verificado y puntero pinneado.
No es Apply. Rutas nuevas propuestas:
- v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B2FinanceObjectGrantOwner.kt
- v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B2FinanceObjectGrantOwnerTest.kt
- .github/workflows/faro-v5-s4-w01-b2b-grant-directed.yml
Después de B2-B0, B2-C deberá unir MaterialWriteGate,
Workspace BeginApply, owner policy lease con SQL COMMIT,
recibo y recovery UNKNOWN. Cross-process policyEpoch exige
una autoridad durable o STOP. No fingir credenciales humanas.
UI Android solo tras composición validada por instancia.
Sin nuevos OperationIds ni tablas por B2-B0; cualquier nueva
tabla futura precisa auditoría SQL versionada y permiso propio.

## B5 — Ocho pruebas adversariales diseñadas, NOT_RUN
T01 permiso independiente exacto con scope token.
T02 default deny sin policy/owner.
T03 cross-object y cross-operation scope deniega.
T04 pointer ABA y host epoch deniegan.
T05 policyEpoch cambió, revoke/grant no reutiliza nonce.
T06 revoke durante lease in-process se serializa.
T07 backup/restart no restaura grant.
T08 cerrar owner o falta de principal deniega.
T4 productiva requiere además pruebas cross-process revocation
durante Reality SQL, antes/después COMMIT, aunque estos 8
tests pasen. No habilitar V2 productiva, Auto/Noche, G15,
main/V4.4/S2/S3, pagos externos, Reality7/8 o catalog180.
B2-B0 requiere READY individual después de guard documental.
