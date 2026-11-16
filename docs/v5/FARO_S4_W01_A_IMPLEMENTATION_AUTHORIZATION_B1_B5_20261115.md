# FARO V5 — S4 W01-A · autorización documental acotada antes de B6
Fecha local: 2026-11-15. Unidad `S4_W01_A_FINANCE_MANUAL_DRAFT_PREVIEW`, reviewed HEAD `761ec4b5049df55eea19df20dcf38cfda8214941`.
La orden humana vigente es «Continuar FARO V5. Termina w1. Solo detenete si me necesitas o tenés dudas».
La autorización se interpreta exclusivamente como avance seguro por unidad conforme a AGENTS.md; NO habilita
modificación del catálogo180, transferencia, saldo REAL ni API bancaria.
B1–B5 revisados, contenidos y disponibles en `docs/v5/FARO_S4_W01_FINANCE_PREAUDIT_B1_B5_20261115.md` blob `71bd619d283295fd366087a7654ceb832d0365f8`.
Guard documental SUCCESS run 43780158841 exact SHA, writer epoch934 HELD DOC_ONLY para publicar READY.

## B1
Fuentes y API reales de FinanceAccountCreate, FinanceEntryRecord, FinanceSnapshot, FinanceOperations,
FinancePersistencePort; 180 contratos congelados. W00 B7 GO infraestructura, no dual owner.
## B2
Sin duplicar FinanceOwnerFoundationTest ni FinanceEntryFoundationTest; reuso de modelos como datos puros.
## B3
Alternativa elegida: captura humana inerte previa a Review/Apply, fuente oficial Android offline-first;
rechazadas escrituras DAO directas y permisos inferidos.
## B4
READY después de publicación solo para:
- `v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceManualDraftPreview.kt`
- `v5/owner/finance/src/test/kotlin/ar/com/faro/v5/owner/finance/FinanceManualDraftPreviewTest.kt`
- `.github/workflows/faro-v5-s4-w01-a-manual-preview-directed.yml`
API de preview sin mutación; 10 casos XML exactos; CI owner finance `:owner:finance:test`.
## B5
Ausencia de apertura DENY; saldo inicial cero solo si explícito, cuenta inexistente DENY,
ID duplicado DENY, ledger overflow DENY; never treat preview as permission/receipt.
No cambiar V2 productiva, Auto/Noche, main, V4.4, S2/S3, 180, DB, grants, app ni G15.

## B6/B7
B6 después del READY y preflight exacto de tres rutas; B7 solo después de compilar,
verificar 10 casos XML, guard por SHA, diff y writer RELEASED. No certificado aún.
