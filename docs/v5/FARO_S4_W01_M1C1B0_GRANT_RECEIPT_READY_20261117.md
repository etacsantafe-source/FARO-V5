# W01 M1C1B0 — READY exact 4 para grant staged con recibo real
Fuente B1-B5 docs/v5/FARO_S4_W01_M1C1B0_GRANT_REAL_RECEIPT_B1_B5_20261117.md blob f1e33a37385939a691727f8935d8a7d2ff7dcda6, guard 44011584108 SUCCESS. HEAD 9671d5802982368a4d4427d2456856dc29f92f75. Writer RELEASED epoch1027 verificado antes del READY. Permiso independiente tras M1C1A B7.
## B1
RealityStore.commit ya genera execution_receipt y journal COMMITTED; finance_apply_grant existe en SQLite9 pero no es consumida en COMMIT; M1C0 y M1C1A B7 PASS scoped.
## B2
Reutilizar schema9 y ACL de M1A sin reescribir ni V4.4 ni backup.
## B3
Nuevo issueGrant stage-only con transacción propia, y consumo contra receipt real después de commit pero ANTES de SQL COMMIT bajo active Reality scope. Deny obliga Rollback por caller, no construir un host productivo.
## B4
Rutas exactas:
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityFinance9Acl.kt
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityStore.kt
- v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityFinance9GrantReceiptStageTest.kt
- .github/workflows/faro-v5-s4-w01-m1c1b0-grant-receipt-directed.yml
R01–R08 exact JUnit XML, cero skips/fail/errors, SQLite9 staged only.
## B5
No inventar identidad humana; injection es test-only, no login/biometría/G15. No shipping/multiDB/Auto/Noche/V2 productiva; W2 STOP. Sin cambiar otro owner.
## B6
READY_TO_IMPLEMENT, CAS nuevo de writer antes de código.
## B7
PENDING scoped stage-only, no permission para M1C1B1.
