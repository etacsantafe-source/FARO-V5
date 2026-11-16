# FARO V5 — W01-B0 READY individual y acotado
Fecha 2026-11-15 AR, HEAD revisado c2b1d9dc55f5b561a1967b711c1d3cfe911625e9, unidad S4_W01_B0_FINANCE_CANONICAL_PREPARED_OPERATION.
Autorización humana explícita a finalizar W1 local con ampliación futura de catálogo/migración C/D/E;
B0 solo permite preparador inerte y tests. Guardia anterior 43785968428 SUCCESS,
writer epoch942 HELD exclusivo para documento.
## B1
Fuentes Finance y contrato oficial descritos en docs/v5/FARO_S4_W01_B0_CANONICAL_PROPOSAL_B1_B5_20261115.md, blob 8f99e0ee209d58e0df40e330432204bfdfe17f3f.
W01-A B7 PASS 10/10, guard 43782356841, permiso anterior BLOCKED.
## B2
Reusar payloads, stamps, PreparedOperation, lookup; no nuevos OperationIds ni DB.
## B3
Preparación pura preferida sobre escribir en UI/DAO o confiar en respuesta de modelo.
No hay grant o consentimiento por crear propuesta.
## B4
READY solo después de este documento para:
- v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceManualReviewedProposal.kt
- v5/owner/finance/src/test/kotlin/ar/com/faro/v5/owner/finance/FinanceManualReviewedProposalTest.kt
- .github/workflows/faro-v5-s4-w01-b0-proposal-directed.yml
12 tests nuevos exactos en CI JVM owner:finance. B6/B7 después de commit funcional y guard.
## B5
Rechazar metadata falsa, fuente cambiada, cuenta duplicada o ausente,
clave inválida, INTERNAL_ONLY y preview manipulado. Ningún Apply/receipt/autoridad/FX.
Sin modificar catálogo180, Reality, Android, S2/S3, main/V4.4, V2 productiva,
Auto/Noche ni ejecutar dinero externo.
## B6
READY de tres archivos nuevos tras checkpoint documental y preflight de 3 rutas.
## B7
PENDING_FINAL_AUDIT: CI XML 12/12 por SHA, guard SUCCESS, diff scoped, writer
RELEASED y B7 doc antes de la siguiente unidad funcional. No PASS anticipado.
