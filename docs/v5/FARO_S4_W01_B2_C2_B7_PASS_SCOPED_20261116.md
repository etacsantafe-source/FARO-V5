# FARO V5 — W01-B2-C2 · B7 PASS scoped Finance receipt-only recovery JVM
Fecha local 2026-11-16. Unidad S4_W01_B2_C2_FINANCE_RECEIPT_ONLY_RECOVERY_JVM.

## B1 — Fuente y fallo inicial
Auditoría B1–B5 docs/v5/FARO_S4_W01_B2_C2_RECEIPT_RECOVERY_B1_B5_20261116.md,
READY docs/v5/FARO_S4_W01_B2_C2_READY_20261116.md.
Código inicial 883018665bcf823bf3703e3ec49a382848634917;
guard 43885090377 SUCCESS, CI 43885090652 FAILURE
app:compileDebugUnitTestKotlin INAPPLICABLE_LATEINIT_MODIFIER
línea 140, cinco tests NOT_RUN.
RED_FIX previo docs/v5/FARO_S4_W01_B2_C2_INLINE_LATEINIT_RED_FIX_B7_20261116.md,
checkpoint cd5eb05f1e1e8d4f815a3755c872c0073c97e4a4,
guard 43886063178 SUCCESS.

## B2 — Corrección acotada
Solo S4W01B2C2FinanceReceiptRecoveryTest.kt cambió
dos locals Kotlin inline ConversationId/FinanceAccountId desde lateinit
a valores tipados determinísticos con asserts de identidad.
Código SHA d49cc17d99b460651042a9f99c4a389cbcd28e01; no source runtime, no schema ni catálogo ni Activity,
workflow dirigido intacto. Los cinco tests se conservaron.

## B3 — Reutilización
Se reutiliza ReviewedConversationApplyCoordinator.reconcileCommittedOnly,
no una implementación nueva de replay ni un receipt inventado.
Acceso REALITY local/JUnit exclusivamente a bases temporales.
Se rechaza rerun de un Apply incierto, texto APPROVED como grant
y alterar la política de Workspace.

## B4 — Pruebas y recibos
GitHub Actions 43886319792 COMPLETED SUCCESS del SHA d49cc17d99b460651042a9f99c4a389cbcd28e01,
job 133365329925, Gradle BUILD SUCCESSFUL in 3m12s.
S4_W01_B2C2_FINANCE_RECEIPT_ONLY_RECOVERY_XML_PASS 5/5;
0 failures, 0 errors y 0 skipped en los cinco métodos exactos.
Guard mismo SHA 43886319690 SUCCESS.
Cubre: COMMITTED después de caída/reapertura, solo lookup/receipt,
APPROVED sin intento DENY, APPLYING sin receipt RECOVERY_REQUIRED,
contexto falso DENY, segunda observación no duplica ledger/receipt.

## B5 — Alcance NO certificado
Solo Finance Account CREATE SQLite temporal, NO se probó
ENTRY recovery, identidad/consentimiento humano, política Finance
durable multi-proceso a través COMMIT, OS/G15, producto activo
ni migración 7/8. No reusar F1 Profile ni C0 7/7 como
C1 cross-process. W01 y C1 OPEN; no pagos externos.

## B6
Test y workflow nuevos, corrección solo test en RED_FIX,
sin cambios runtime. Consumir READY de C2.

## B7
PASS_SCOPED_FINANCE_RECEIPT_ONLY_RECOVERY_5_OF_5,
no cierre W01 global. Cierre documental sujeto a guard nuevo HEAD
SUCCESS, diff solo docs, writer RELEASED epoch971 con
last_checkpoint==HEAD, y permiso BLOCKED.
