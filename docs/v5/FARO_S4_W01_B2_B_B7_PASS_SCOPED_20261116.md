# FARO V5 — S4 W01-B2-B B7 PASS scoped DEV Finance grant owner
Fecha 2026-11-16 AR; unidad S4_W01_B2_B_DEV_SCOPED_FINANCE_GRANT_OWNER.
B1–B5: docs/v5/FARO_S4_W01_B2_B_FINANCE_GRANT_POLICY_B1_B5_20261116.md.
READY individual: docs/v5/FARO_S4_W01_B2_B_READY_20261116.md.

## B1
W01-B2-A B7 PASS 8/8, guard 43870813220 SUCCESS y permiso consumido.
W01-B2-B añadió dueño FINANCE técnico DEV aislado; no reutiliza
Planning8 como autoridad ni monta V2 productiva.
## B2
Código exacto SHA 8b9257aebc626c135abb2eee44396d84e1b54f7e, tres rutas:
v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B2FinanceObjectGrantOwner.kt,
v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B2FinanceObjectGrantOwnerTest.kt,
.github/workflows/faro-v5-s4-w01-b2b-grant-directed.yml.
Scopes tipados por operación+objeto+cuenta+fingerprint, policyEpoch de
host independiente, nonce único y revocación propia del proceso.
Sin SQL, Reality, Workspace modificación, Apply o receipt.
## B3
Sin policy o sin scope canónico, DENY. No heredar privilegios
de Household/Planning8, no restaurar tokens luego de backup/restart.
Scope PENDING/APPROVED en UI no constituye permiso de Apply.
## B4
GitHub Actions 43871641948 COMPLETED SUCCESS exact SHA 8b9257aebc626c135abb2eee44396d84e1b54f7e,
job 133314077362, Gradle BUILD SUCCESSFUL in 3m09s,
S4_W01_B2B_SCOPED_FINANCE_GRANT_XML_PASS 8/8; 0 fail/error/skip,
8 métodos XML exactos. Guard 43871641769 COMPLETED SUCCESS.
Diff exacto tres archivos, writer RELEASED epoch959 last_checkpoint 8b9257aebc626c135abb2eee44396d84e1b54f7e.
## B5
Se probaron nonce scope, default-deny, aislamiento de operationId
y cuenta, ABA/hostEpoch, cambio policyEpoch, revoke/regrant,
revoke concurrente serializado con lease síncrona, nuevo owner
no recupera grant anterior, close/principal inválido DENY.
**NO SE CERTIFICA** revocación entre procesos, ACL externa durante
COMMIT, persona humana autenticada, APP REAL, receipt, Undo o
persistencia productiva. En particular el monitor in-memory
no impide carrera con cambios hechos por otro proceso.
W01-B2-C y W01 global siguen OPEN. Catálogo180/Reality7/8
intactos, V2 productiva/Auto/Noche OFF, sin pagos externos.
## B6
Implementación DEV 8/8 scoped; consumir permiso READY en BLOCKED.
## B7
PASS_SCOPED_DEV_FINANCE_GRANT_INPROCESS_8_OF_8,
condicionado a guard posterior documental, writer RELEASED epoch960
last_checkpoint=HEAD, diff solo docs y permiso BLOCKED.
