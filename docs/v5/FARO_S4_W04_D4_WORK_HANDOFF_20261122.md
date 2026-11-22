# FARO V5 — WORK TURBO · W04-D4 · Presentación de revisión comercial histórica

## Recuperación
Repo etacsantafe-source/FARO-V5, branch work/s3-night-20261102. Después de este handoff recuperar HEAD/puntero/writer y CI actual; no confiar en SHAs de mensajes viejos si HEAD cambió. Guardia documental B1–B5 de D4: 45032414215 SUCCESS contra b58b2353f9f6addb0a8f5c792afab57908200f46. Documento de B1–B5: docs/v5/FARO_S4_W04_D4_REVIEW_PRESENTATION_B1_B5_20261122.md, blob 7a8dfec051296d48d1726343c4e8a807f66da3f6. READY sólo es válido si el guard de este commit de permiso también finaliza SUCCESS.

## Misión única posterior, B6 y B7
Implementar S4_W04_D4_HISTORICAL_REVIEW_PRESENTATION_DEV en JVM **puro, read-only, no Android UI**. D4 genera read-model agrupado de salida verificada D3A+D3C y avisos D2C1; la pantalla Android será W04-E con autorización propia. No repetir W04-D0/D1/D2A/D2B/D2C0/D2C1/D3A/D3B/D3C ni W1–W3. **No Apply, pago, Finance, stock, compra, refresh, host, grants, SQL, Internet, V2 productiva, G15 ni pruebas físicas**.

Leer AGENTS.md, FARO_CONTINUITY_POINTER.json, FARO_PROTOCOL_WORK_PERMIT.json, protocolo B1–B7 y preauditoría exacta. Revalidar HEAD/writer RELEASED/permiso READY (unit/paths/B1–B5 blob exacto), guard de READY SUCCESS y preflight contra las tres rutas. Adquirir writer CAS con target HEAD real. Primera ejecución B6 SOLO tres rutas NUEVAS:
1. v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialReviewPresentation.kt
2. v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04CommercialReviewPresentationTest.kt
3. .github/workflows/faro-v5-s4-w04-d4-review-presentation.yml

Implementar proyección inmutable de agrupaciones y escenarios sin mezclar monedas, mostrando explícitamente histórico/WHAT_IF/beneficio condicional y faltantes; llamar D3C.trace con las seis entradas en vez de confiar en un traceDigest aislado. El resultado nunca es autorización de compra ni de mutación. Crear 8 pruebas JUnit5 distintas T01–T08 del documento; workflow Java21 SDK37, :app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04CommercialReviewPresentationTest, artifact XML. Commit funcional con línea EXACTA FARO-UNIT:S4_W04_D4_HISTORICAL_REVIEW_PRESENTATION_DEV, sin controles docs/permiso en el mismo commit. Liberar writer mientras CI. Comprobar guard y CI reales por SHA y XML descargado con tests=8, failures=errors=skipped=0 y 8 nombres únicos. Si falla, registrar RED_FIX y corregir sólo falla comprobada, sin reescribir código certificado.

B7 PASS únicamente con evidencia guard/CI/XML/archivo/code SHA. Consumir READY a BLOCKED en documento final, nuevo checkpoint, writer RELEASED, guard documental final. No saltar directamente a W04-E/F; requieren su unidad y permiso.

## Presupuesto Work
Priorizar programación y CI, no rehacer B1–B5. Trabajo preparatorio ya realizado por Chat con writer exclusivo. Si la guardia READY no está en verde, STOP, no B6.
