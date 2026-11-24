# FARO V5 · W05-A0 · B7 PASS inerte de recepción física parcial

Fecha: 2026-11-24 (Argentina). Unidad `S4_W05_A0_RECEIPT_STOCK_CONTRACT_DEV`, rama `work/s3-night-20261102`.
Código B6 `b2ce0bd786871fc9d23f21034e64f2bdc98a60d6`; padre READY `b6d0cfa1f13a197a67574d393622ea46f2a01590`.
Alcance **exclusivamente un cálculo inerte de recepción parcial tipada** en `:owner:household`; no stock Reality durable, no SQL11, sin Apply, Review/receipt persistente, Cocina, Android físico, pago o V2 productiva.

## B1–B5 — Evidencia reutilizada

Preauditoría W05 propia `docs/v5/FARO_S4_W05_A0_RECEIPT_STOCK_CONTRACT_B1_B5_20261124.md`, blob `417077072f769feb635ff1b151f743ffe14387ff`; guardia documental `45386693352` SUCCESS. Permiso exact3 READY publicado en `b6d0cfa1f13a197a67574d393622ea46f2a01590` con guardia `45386856410` SUCCESS. No reutilizar permiso W04 anterior. Frente a V4.4 se conserva la distinción comprar/pagar/recibir, sin copiar el runtime. Grocy/KitchenOwl/SQLite en B3 son referencia conceptual, sin dependencias, APIs pagas ni egress.

## B6 — Código y CI reales

Commit `b2ce0bd786871fc9d23f21034e64f2bdc98a60d6`: exactamente tres rutas:
- `v5/owner/household/src/main/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockReceiptPreview.kt`: `calculate` puro, rechazo por referencia inválida, ledger inconsistente, sobre-recepción y unidades no equivalentes.
- `v5/owner/household/src/test/kotlin/ar/com/faro/v5/owner/household/api/HouseholdStockReceiptPreviewTest.kt`: 10 pruebas JUnit5.
- `.github/workflows/faro-v5-s4-w05-a0-receipt-preview.yml`: preflight exact3, JVM dirigida, chequeo de XML original y artefacto.

Guardia código `45387219544` **SUCCESS**, preflight parent exact3 PASS. GitHub Actions `45387220139`, job `138421190604`, `head_sha=b2ce0bd786871fc9d23f21034e64f2bdc98a60d6`, **COMPLETED SUCCESS**. Gradle `BUILD SUCCESSFUL in 2m 50s`. El paso `Verify W05-A0 real JUnit XML exact ten` fue **SUCCESS** tras leer un archivo `TEST-*HouseholdStockReceiptPreviewTest.xml` y comprobar `tests=10, failures=0, errors=0, skipped=0`; el log emitió `FARO_W05_A0_RECEIPT_PREVIEW_XML_PASS_10_10`. Artefacto original `14917565574`, `w05-a0-receipt-preview-xml`, 787 bytes; SHA256 ZIP reportado por runner `00e00ab38fbef42b140157227eb8b0c3ae13247df463726fed3e89fcab1349ed`. No afirmar rehash externo del ZIP.

## B7 — Auditoría final y límites

**PASS_SCOPED_W05_A0_INERT_PREVIEW_JVM_10_10.**

Pruebas cubren recepción 7 de 10 (pendiente 3), recepción 3 posterior, rechazo por exceso 4, unidades UNIT/PACKAGE y GRAM/KILOGRAM incompatibles, snapshot ausente, referencias inválidas, ledger negativo/desbordado, cantidades inválidas y máximo Int32 sin overflow. La repetición de un preview retorna un cálculo idéntico sin efectos; **NO** acredita idempotencia durable de recibir, receipts ni recovery.

En `RealityHousehold10Schema.kt` el esquema certificado tiene solamente `household_need_state` y `household_need`. No deducir que existe `stock.receive` persistente, lotes o tabla stock. W05-A1 debe hacer B1–B5 propios para generación staged de esquema, CAS/receipts, backup/restore, permisos/generación y negativas (doble intento, crash, restore, revocación). W05-A2 reservas/consumo y A3 Cocina separados. Sin autorización independiente no emitir código de estas unidades.

B7 consume permiso READY → BLOCKED; writer debe quedar RELEASED después de checkpoint. Se preservan W04 LOCAL DEV, S3 DEV 5/5, main/V4.4 y V2 productiva OFF. G15 físico, Chat/S3 integrado, W06 atomicidad Household↔Finance y W07 compra pagada no certificados.
