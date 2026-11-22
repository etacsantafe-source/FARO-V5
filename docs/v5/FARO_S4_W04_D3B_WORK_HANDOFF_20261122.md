# FARO V5 — W04-D3B · WORK TURBO: RELECTURA VIVA SOLO LECTURA

Fecha: 2026-11-22. Repositorio etacsantafe-source/FARO-V5, rama work/s3-night-20261102.
**MISIÓN ÚNICA: TERMINAR W04-D3B B6–B7.** No repetir D0/D1/D2A/D2B/D2C0/D2C1/D3A: todos B7 PASS acotado. B1–B5 de D3B ya documentados y su guardia documental 45023898670 SUCCESS.

## Recuperación
1. Leer AGENTS.md, docs/v5/FARO_CONTINUITY_POINTER.json y docs/v5/FARO_PROTOCOL_WORK_PERMIT.json.
2. Leer docs/v5/FARO_S4_W04_D3B_LIVE_READ_RECHECK_B1_B5_20261122.md (blob 19930365ef27f915d604c22d8605a4b95fb25200) y este handoff.
3. Confirmar HEAD real, writer RELEASED/target_head y READY del permiso, guardia de READY SUCCESS y preflight para cada ruta antes de adquirir el writer CAS.
4. No repetir B1–B5, investigar desde cero ni cambiar códigos certificados: pasar directamente a B6 solo cuando los gates estén confirmados.

## B6 — Exactamente tres rutas nuevas
1. v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialLiveReadRecheck.kt
2. v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04CommercialLiveReadRecheckTest.kt
3. .github/workflows/faro-v5-s4-w04-d3b-live-read-recheck.yml

Unidad: S4_W04_D3B_LIVE_READ_RECHECK_DEV.

Implementar una clase DEV pura y sin persistencia que coordina D1.project mediante su instancia inyectada por el host, reloj host y D0 draft / D1 baselineCoverage / D3A Preview. Validate identidad (fingerprint, watermark, asOf), ventana, filas del draft, timestamp, digest de formato pero NO tomarlo como firma de autoridad. Releer D1 en asOf del preview y cotejar la vista completa (records/grupos/status/truncado), proyectar ahora y rechazar observaciones seleccionadas que pierdan comparabilidad o estén truncadas; repetir el control histórico al final para detectar cambios entre lecturas y revalidar permisos antes/después mediante D1. Nunca confundir dos chequeos con transacción atómica. Clock host fail-closed ante null/retroceso/tiempo fuera de 60 segundos. Excepciones/denegación/source change => Reject tipado; nada de retorno parcial.

El ÚNICO resultado positivo es RecheckedForDisplay con advertencias READ_ONLY_OBSERVED_CHECK_NOT_APPLY / NO_CURRENT_PRICE_OR_BENEFIT_CERTIFICATION / RECHECK_BEFORE_FUTURE_ACTION; jamás APPROVED_TO_BUY, LIVE_QUOTE_VERIFIED, cashback pagado, capability, ApplyPlan, receipts ni mutaciones. NO SQL, Finance, stock, red, FX, V2 productiva, Android/G15, ni nuevas dependencias/owners. El chequeo depende de callbacks simuladas/injectadas: NO alegar host productivo integrado.

JUnit5: exactamente 8 casos adversariales T01–T08 especificados en el documento B5 (identidad, ACL/fences revocados, records cambiados con watermark igual, excepciones, reloj/ventanas, envejecimiento, parcial/solo lectura, cambios a mitad de recheck). CI Java21 y Android SDK37, comando Gradle ':app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04CommercialLiveReadRecheckTest', verificar XML tests=8 failures=0 errors=0 skipped=0 y 8 nombres distintos; subir artifact XML real.

Primer commit funcional modifica EXACTAMENTE tres rutas y mensaje incluye FARO-UNIT:S4_W04_D3B_LIVE_READ_RECHECK_DEV. Workflow incluye preflight para cada ruta desde HEAD^ READY. RED_FIX posterior puede cambiar subconjunto 1..3 sólo después de registrar evidencia RED_FIX según protocolo. Release writer antes de CI.

## B7 / Final
Acreditar guardia código SUCCESS, CI por SHA exacto, 8 XML individuales reales, artefacto descargado e inspeccionado. Si PASS, publicar B7 por commit documental, consumir READY→BLOCKED, actualizar checkpoint/puntero y writer global CAS RELEASED, verificar guardia de docs. Si falla, B7 RED_FIX con logs, no falsear PASS. No iniciar D3C/Apply, D4/E/F, ni pruebas físicas. No gastar presupuesto Work en repetir otras unidades.

**Recordatorio:** D3B sólo demuestra coherencia momentánea de lectura en DEV; nunca cotización actual, permiso de comprar ni seguridad futura frente a TOCTOU.
