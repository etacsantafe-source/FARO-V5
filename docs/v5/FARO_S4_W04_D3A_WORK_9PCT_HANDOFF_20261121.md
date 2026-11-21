# FARO V5 — W04-D3A · WORK TURBO · presupuesto semanal 9%

## MISIÓN ÚNICA: B6–B7 D3A
Repo etacsantafe-source/FARO-V5, rama work/s3-night-20261102. Leer AGENTS.md, docs/v5/FARO_CONTINUITY_POINTER.json, docs/v5/FARO_PROTOCOL_WORK_PERMIT.json, docs/v5/FARO_S4_W04_D3A_PRE_REVIEW_B1_B5_20261121.md (blob 93626f7556f699d2b95a6d3924ce225edd1ca023), este handoff. **B1–B5 ya documentados y guardados**: no repetir trabajo B7 de D0/D1/D2A/D2B/D2C0/D2C1 ni B1–B5 de D3A. Esperar guardia READY y preflight real antes de B6. Presupuesto usuario: 9% semanal restante, 92% diario, no abrir unidades adicionales.

## B6 — EXACTAMENTE tres rutas NUEVAS
1. v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialPreReviewEnvelope.kt
2. v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04CommercialPreReviewEnvelopeTest.kt
3. .github/workflows/faro-v5-s4-w04-d3a-pre-review-envelope.yml

Implementar pure Kotlin preview(D2C0 Summary,D2C1 Annotated)->Decision de PRE-REVISIÓN ONLY. Validar cart fingerprint/watermark/asOf, claves de grupos, montos observados e hipotéticos por grupo, cobertura y needIds, notas USER_DECLARED_UNVERIFIED con status coherente, sin confiar en datos fabricados. Generar digest SHA-256 de campos tipados en orden canónico vía CanonicalHashBuilder domain w04.d3a.pre-review.v1. Salida inmutable READ_ONLY_PREVIEW_NOT_APPLY + LIVE_REVALIDATION_REQUIRED, no ApplyPlan, recibo, permiso, cotización vigente, fondos reales ni compra. D3B posterior será owner/host LIVE ACL+revision+generation+Evidence revalidation, no implementarlo acá. Mantener D0/D1/D2A/D2B/D2C0/D2C1 intactos. No tocar Finanzas, Evidence, stock, Android UI, DB, V2 productiva, APIs, Finance/Apply.

OCHO JUnit5 T01–T08 ya definidos en B5: fence, Summary forjada, grupos alterados, warnings/provenance falsa, orden canonical, digest cambia por un minor unit o texto, cobertura parcial/notas UNKNOWN, inmutabilidad/hostile sourceRef. Workflow Java21 Android SDK37 :app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04CommercialPreReviewEnvelopeTest; exigir XML (tests=8, failures=0, errors=0, skipped=0) + 8 cases distintos y artefacto, guardia SUCCESS. Commit funcional FARO-UNIT:S4_W04_D3A_PRE_REVIEW_ENVELOPE_DEV; permiso de 3 rutas exactas ya publicado en docs-only commit anterior. Preflight para CADA ruta contra HEAD^ READY; inicial exact3, RED_FIX 1..3 tras evidencia B7 RED_FIX.

## B7 y STOP
Revalidar HEAD, writer CAS, permiso y guardias antes de escribir. Writer global CAS desde faro-v5-writer-lock, release durante CI. Inspeccionar XML real y certificar B7 solo con 8/8 y guard SUCCESS; consumir READY a BLOCKED y writer RELEASED con checkpoint. Si falla CI, registrar RED_FIX y corregir únicamente D3A; si Work semanal insuficiente, dejar SAFE checkpoint, writer RELEASED y B7 NOT_PASS. No continuar D3B/D4/E/F, V2 productiva ni pruebas G15.
