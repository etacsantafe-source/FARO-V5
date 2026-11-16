# FARO V5 W01-B0 · B7 RED_FIX de confirmación independiente
Fecha local 2026-11-15. Unidad S4_W01_B0_FINANCE_CANONICAL_PREPARED_OPERATION.
Código ecfc7cf1b93e93e440de359d39b36f113ff33a32, CI 43786516543 COMPLETED/FAILURE; guard del mismo SHA 43786516504 SUCCESS.

## B1
Log GitHub Actions job 133035291078: 12 tests ejecutados, 11 PASS/1 FAIL.
FinanceManualReviewedProposalTest.forgedAccountPreviewAndDuplicateAccountDenied
en línea 120: al modificar solo openingBalanceMinor de preview 0 a 77, el preparador
lo reconstruye desde el mismo preview y acepta el cambio como válido. Es un
problema real de congruencia entre intención original y propuesta, no fixture.
La preview W01-A es inerte, no contiene firma de autoría.

## B2
Conservar validación W01-A, CanonicalPayload, contrato Finance actual,
stamps y tests positivos/negativos. No modificar FinanceManualDraftPreview ni
catálogo 180, Reality, host, UI o esquema. W01-A 10/10 no se repite.

## B3
Rechazado: cambiar aserción para aprobar el preview falsificado; rechazada
una clave/suma hash aportada por caller como prueba de consentimiento.
Solución: introducir parámetro de entrada humana INDEPENDIENTE del preview,
entregado más adelante por host de confianza desde intención confirmada.
Aquí comparar por igualdad tipada estricta ANTES de crear PreparedOperation.
Android offline-first https://developer.android.com/topic/architecture/data-layer/offline-first;
no es una garantía de identidad ni sustituye Review/Apply.

## B4
Modificar SOLO:
- v5/owner/finance/src/main/kotlin/ar/com/faro/v5/owner/finance/api/FinanceManualReviewedProposal.kt
- v5/owner/finance/src/test/kotlin/ar/com/faro/v5/owner/finance/FinanceManualReviewedProposalTest.kt
Ambas funciones requieren el payload humano confirmado independiente:
FinanceAccountCreate y FinanceEntryRecord, respectivamente.
Comparar preview.create / preview.record con dicho payload y revalidar snapshot,
catálogo y moneda. El test forged conserva la creación alterada con
confirmedAccount ORIGINAL: debe DENY. No otorgar permiso por la coincidencia.

## B5
Riesgo residual: este preparador no autentica quién proporcionó el payload
confirmado; únicamente el futuro host W01-B1/B2 podrá hacerlo.
No eliminar pruebas T01–T12; repetir suite XML completa 12/12.
No cambiar .github workflow, tests Foundation, V2 productiva, S2/S3,
main/V4.4, Auto/Noche, catálogo/migraciones ni teléfono.

## B7
RED_FIX scoped del SHA ecfc7cf1b93e93e440de359d39b36f113ff33a32, NO PASS. Autoriza corrección SAME UNIT
tras este checkpoint documental, seguida de nuevo CI exacto/guard/B7.
Si CI falla, registrar nueva causa; no avanzar W01-B1 ni CDE funcional.
