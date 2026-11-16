# FARO V5 — W01-B2-B READY scoped Finance grant owner DEV
Fecha AR 2026-11-16. Reviewed HEAD 04872696d5518562c3c8537a696b3f7f92d8ced8, guard 43870813220 SUCCESS.
Unidad S4_W01_B2_B_DEV_SCOPED_FINANCE_GRANT_OWNER. Este permiso NO autoriza W01-B2-C Apply.

## B1
Doc audit docs/v5/FARO_S4_W01_B2_B_FINANCE_GRANT_POLICY_B1_B5_20261116.md, blob 978c6cd5ed65d3e9b160dba5fd6b5d08110d4fa5; B2-A ya B7 PASS
read-only 8/8, permiso consumido; B1/B0/A también cerrados.
El estado Approved de Workspace NO concede permiso Finance.
## B2
Reusar S4W01B2FinanceApprovedScope tipado, puntero exacto,
hostLifecycleEpoch, policyEpoch y MaterialWriteGate sin crear base nueva.
El owner de Planning8 solo sirve como patrón, sin heredar scopes.
## B3
Elegida: implementación owner DEV de nonce por scope/policyEpoch,
revocable y memoria local, default-deny; rechazar su uso en producción,
porque no certifica revocación cross-process ni identidad humana.
## B4
READY para EXACTAMENTE tres rutas nuevas:
- v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B2FinanceObjectGrantOwner.kt
- v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B2FinanceObjectGrantOwnerTest.kt
- .github/workflows/faro-v5-s4-w01-b2b-grant-directed.yml
Requiere 8 tests JVM app:testDebugUnitTest y XML 8/8.
No introducir Surface API, Activity, Reality, DB, cobros ni pagos.
## B5
Pruebas de aislamiento entre scope/operation/account, ABA completo,
hostEpoch, grant sin policy, revoke/regrant no reuso nonce, lease
síncrona, close/restart, sin credenciales. No sustituir comprobaciones
de policy por etiqueta APPROVED ni callback de UI.
## B6
READY_TO_IMPLEMENT solo para DEV owner INERTE; CI exacta propia.
## B7
PENDING_FINAL_AUDIT hasta XML 8/8, guard por SHA, diff exacto,
B7 documental/permiso BLOCKED y writer RELEASED.
