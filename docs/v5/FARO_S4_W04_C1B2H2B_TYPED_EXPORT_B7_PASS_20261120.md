# FARO V5 — W04-C1B2H2B · aprobación v2 y entrega documental controlada — B7 PASS

Fecha local 2026-11-20. Unidad `S4_W04_C1B2H2B_TYPED_EXPORT_APPROVAL_DEV`. Previas H1 B7, H2A B7 confirmadas; documentación B1–B5 individual `docs/v5/FARO_S4_W04_C1B2H2B_TYPED_EXPORT_APPROVAL_B1_B5_20261120.md`, blob `00e7b2cfb7d25a64227877534195061909ea9c47`. Permiso READY exact5 emitido en `71d43b64f104c9effe75f8bf176d1b1daaafc63b`; guard del permiso `44849096081` SUCCESS.

## B7

**PASS_SCOPED_STAGED_DEV_10_OF_10.** Código exacto `b6dff51670b74c1ddea51d48fb49ef17e567ed7c` y cinco rutas verificadas:
1. `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/CommercialTypedPortabilityV2.kt` — nuevo perfil V2, preview/plan SHA-256 con watermark, revisión y consentimiento humano explícito, copia en memoria con revalidación antes de escribir.
2. `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/PortabilityExternalWrite.kt` — se habilita sólo perfil V1 antiguo O perfil V2 exacto en las dos validaciones; CAS y estados de receipt existentes intactos.
3. `v5/maintenance/src/main/kotlin/ar/com/faro/v5/maintenance/PortabilityCore.kt` — recibo valida únicamente los dos perfiles exactos, preview V1 sigue estricto.
4. `v5/maintenance/src/test/kotlin/ar/com/faro/v5/maintenance/CommercialTypedPortabilityV2Test.kt` — diez pruebas JVM.
5. `.github/workflows/faro-v5-s4-w04-c1b2h2b-typed-export.yml` — CI dirigida con preflight exact5 y comprobación JUnit XML.

**Evidencia ejecutada y verificada:**
- Guardia del commit código `44849294438` **COMPLETED SUCCESS**.
- Workflow H2B `44849294556` **COMPLETED SUCCESS**, job `136676562729`, `:maintenance:test` **BUILD SUCCESSFUL in 3m 22s**. XML dirigido exacto `(tests=10,failures=0,errors=0,skipped=0)`; marca literal `W04_C1B2H2B_TYPED_EXPORT_JVM_XML_PASS 10/10`. Ejecutó también regresiones v1 de mantenimiento.
- Artefacto `14685000800`, `w04-c1b2h2b-typed-export-xml`, 23427 bytes, no expirado al verificar.
- Regresión independiente Guardados `44849294456` **COMPLETED SUCCESS** sobre mismo SHA de código, job `136676562978`.
- Auditoría de fallos: denegación sin consentimiento, fingerprint incorrecto, cambio de terms al preparar/copiar, cancelación sin escritura, mismo effect id reintentado, corte WRITE_STARTED→EFFECT_UNKNOWN sin replay y salida parcial con IOException sin SUCCESS. Fuente hostil literal; no se alteraron monedas ni inventaron vigencias.

**Alcance exacto del PASS:** servicio de preparación/documento comercial STAGED DEV y coordinador de intentos durable, demostrado con OutputStream simulado. No hay acción automática para seleccionar destino, ningún archivo personal escrito, ninguna URI ni egress real, no hay Android UI/SAF ni prueba física G15. No constituye backup restaurable, ni habilita V2 productiva, ACTIVE, Finance, pagos, scraping, API paga o cambio de main/V4.4. La autorización de compartir datos real exige confirmación humana y documento elegido en campaña posterior.

B7 H2B PASS; consumir READY→BLOCKED y liberar writer. Siguiente cierre documental global C1B2, no W04-F.
