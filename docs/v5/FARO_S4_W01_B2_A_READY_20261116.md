# FARO V5 W01-B2-A · READY individual de alcance Finance APPROVED puramente lector
2026-11-16 AR. Reviewed head 609d43305eed0c4718a7a1574d14b558b8db3022, guard B1 final 43867450955 SUCCESS.
Unidad S4_W01_B2_A_CANONICAL_FINANCE_APPROVED_SCOPE.

## B1
Auditoría fuente docs/v5/FARO_S4_W01_B2_FINANCE_APPLY_PREAUDIT_B1_B5_20261116.md, blob 358333aee5f9b9dd3a910a4a3e0a226bbb00f381. Finance B1
B7 PASS scoped 7/7; B0 12/12. Host anterior metadata legacy no coincide
con catálogo runtime actual; lector debe consultar descriptor oficial.
## B2
Reusar canonical Workspace TrustedConversationApplyReviewPort,
PreparedPlanEnvelopeCodec, Catalog, generación/puntero y payloads Finance.
No crear owner nuevo, no modificar flujo Apply.
## B3
Elegido: lectura APPROVED con doble comprobación de Workspace/pointer/hostEpoch,
rechazando un permiso falso basado en texto/estado UI. No ejecutar
Apply ni conceder lease de owner.
## B4
SOLO estas tres rutas nuevas:
- v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B2FinanceApprovedScope.kt
- v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B2FinanceApprovedScopeTest.kt
- .github/workflows/faro-v5-s4-w01-b2a-scope-directed.yml
Clase DEV inerte, no montada en Activity. CI dirigida JVM app:testDebugUnitTest,
8 casos nominales y adversariales exactos; XML verifica 8/8.
## B5
PENDING/DISCARDED/COMMITTED, descriptor falseado, cambio del catálogo,
una operación distinta, payload corrupto, scope ajeno, revisión/stale,
puntero ABA y revocación de host deben devolver null. Sin writes.
## B6
READY_TO_IMPLEMENT para las tres rutas exactas tras checkpoint documental;
scope NO autoriza B2-B/C, catálogo180, Reality, migraciones ni UI.
## B7
PENDING_FINAL_AUDIT hasta CI XML 8/8, guard SHA SUCCESS, diff acotado,
writer RELEASED y B7 documental. No acreditación humana/owner grant/Apply.
