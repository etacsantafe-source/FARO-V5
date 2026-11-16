# FARO V5 — S4 W01-B2-A · B7 PASS SCOPED canonical Finance APPROVED reader
Fecha local: 2026-11-16. Unidad S4_W01_B2_A_CANONICAL_FINANCE_APPROVED_SCOPE.
PREAUDIT: docs/v5/FARO_S4_W01_B2_FINANCE_APPLY_PREAUDIT_B1_B5_20261116.md.
Individual READY previo: docs/v5/FARO_S4_W01_B2_A_READY_20261116.md.

## B1
Código inicial a1ff9c4598cc80fe282b9fa2edb40ebd936e9f02,
guard 43868651130 SUCCESS, CI 43868651234 FAILURE 7/8.
La prueba generationPointerAbaAndHostEpochRevocationDeny detectó
que el lector aceptaba un puntero de la MISMA generación con
secuencia cambiada (A→B→A), porque adoptaba pointer.read() al inicio.
RED_FIX documentado en
docs/v5/FARO_S4_W01_B2_A_ABA_RED_FIX_B7_20261116.md
SHA 94b0828e0ade36fd1329fb090dc4a897704ca85d,
guard 43869650325 SUCCESS.

## B2
Corrección funcional exclusivamente en
v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B2FinanceApprovedScope.kt
y test
v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B2FinanceApprovedScopeTest.kt;
SHA fa0e26376186961f05a24ebcc3b6bc3a76b3f388. El constructor recibe el ActiveGenerationPointer COMPLETO
capturado por host, verifica su generationId esperada y coteja
pointer.read() == pinned en entrada, antes/después Workspace
y catálogo. Los demás tests se conservan intactos.
No hay cambios en Activity, Reality, Finance owner, migrations ni catálogo.

## B3
No se relajan los casos de ABA: se exige secuencia + generación.
No se considera la aprobación en Workspace un grant de owner,
una identidad humana autenticada, un permiso de Apply ni recibo.

## B4
GitHub Actions 43869877546 COMPLETED SUCCESS exact SHA fa0e26376186961f05a24ebcc3b6bc3a76b3f388,
job 133307910354. BUILD SUCCESSFUL in 3m 21s.
Verificador XML de 8 métodos exactos:
S4_W01_B2A_FINANCE_APPROVED_SCOPE_XML_PASS 8/8;
0 failures/errors/skipped. Guard del mismo SHA
43869877438 COMPLETED SUCCESS.
Writer RELEASED epoch956 checkpoint fa0e26376186961f05a24ebcc3b6bc3a76b3f388, diff solo dos rutas de RED_FIX.

## B5
T01 Approved Account obtiene scope oficial; T02 Approved Entry
no inventa moneda; T03 Pending/Discarded deniega;
T04 metadata/catalogo spoof deniega; T05 multinodo/target externo;
T06 payload falso/format incorrecto; T07 pointer ABA, epoch/host;
T08 doble lectura Workspace/catalogo cambiado. Todas 8 PASS.
Este lector es READ_ONLY DEV/DORMANT, no está montado en Android,
ni autentica consentimiento humano, ACL propietario, lease Finance,
revalidación de dependencias ni Reality SQL/receipt. W01-B2B/C/D OPEN.
No pagos externos, no V2 productiva/Auto/Noche,
main/V4.4/S2/S3, catálogo180 ni schema7/8 alterados.

## B6
Se certifica SOLO código inerte y 8/8 JVM. Consumir READY
de B2-A en BLOCKED antes de siguiente unidad funcional.

## B7
PASS_SCOPED_FINANCE_CANONICAL_APPROVED_SCOPE_8_OF_8, no W01 final.
Debe revalidar guard documental del HEAD, writer RELEASED epoch957
last_checkpoint=HEAD, diff solo documentos y permiso BLOCKED.
Si falla cualquiera, B7 documental pendiente.
