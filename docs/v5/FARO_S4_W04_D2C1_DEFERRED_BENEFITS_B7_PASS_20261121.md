# FARO V5 — W04-D2C1 · Condiciones y beneficios diferidos, B7 PASS

Fecha 2026-11-21. Unidad S4_W04_D2C1_CONDITIONAL_BENEFIT_NOTES_DEV. Rama work/s3-night-20261102. El antecedente D2C0 B7 PASS no se repitió. B1–B5: docs/v5/FARO_S4_W04_D2C1_DEFERRED_BENEFITS_B1_B5_20261121.md blob b925f6d31f8bfe06b1ce55357d307c800c826bc1, guard documental 44919975105 SUCCESS, permiso READY exact3 en checkpoint 6719b3decedbf9ad5f19dd99e1faf11ee5dbae6f con guard READY 44920117431 SUCCESS y preflight.

## B7 — PASS_SCOPED_DEV_JVM_READ_ONLY

Código funcional: 891b67b1645ee4de467b05a6a3d14c931baffa22, mensaje FARO-UNIT:S4_W04_D2C1_CONDITIONAL_BENEFIT_NOTES_DEV. Solo tres rutas permitidas:
- v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04DeferredCommercialBenefitNotes.kt, blob 861be2f416adf3cd1de05087ec1801979a3dba8c.
- v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04DeferredCommercialBenefitNotesTest.kt, blob f0942a2575009665e442377d25893c27067f9515.
- .github/workflows/faro-v5-s4-w04-d2c1-deferred-notes.yml, blob 9a78d65076bfe960adf95d7c1bf9954aa81bfee7.

Guardia código 44920363123 COMPLETED SUCCESS. CI dirigida real 44920363191 COMPLETED SUCCESS, job 136894858445; el workflow validó las tres rutas por preflight individual respecto de READY parent 6719b3decedbf9ad5f19dd99e1faf11ee5dbae6f; Java 21/Android SDK 37, :app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotesTest, BUILD SUCCESSFUL in 3m 16s, W04_D2C1_DEFERRED_BENEFITS_JVM_XML_PASS 8/8.

Prueba independiente: artefacto GitHub 14713731446 w04-d2c1-deferred-notes-xml descargado, inspección ZIP y XML testDebugUnitTest/TEST-ar.com.faro.v5.app.S4W04DeferredCommercialBenefitNotesTest.xml: 8 casos JUnit distintos, tests=8 failures=0 errors=0 skipped=0, sin nodos failure/error/skipped. SHA-256 ZIP 35b3e913dffafd3780da4d139d3f583b3a33ccfa0f205537ea0eba5bab7da78a; SHA-256 XML 1d64e6b0b790121f16615b475f6475abc864daaede3d351563314de2c86e019e.

T01–T08 incluyen identidad fingerprint/watermark/asOf, Summary forjado, grupo comercial ajeno, duplicados de clase, cadenas opacas y límites 1024, orden canónico, escenarios PARCIAL sin nota, inmutabilidad y no ejecución sourceRef. No se requirió RED_FIX.

**Alcance**: avisos explícitos de usuario para un grupo comercial y tipo CASHBACK_FUTURE / INSTALLMENT_CONDITION / BANK_ELIGIBILITY / OTHER_UNVERIFIED; todo REVIEW_REQUIRED_UNVERIFIED/UNCLASSIFIED_NO_CLAIM y USER_DECLARED_UNVERIFIED. CERO montos, fechas de cobro, ingresos financieros, cuotas bancarias reales, elegibilidad comprobada, checkout, FX, Apply/receipts, stock, DB, network, permisos o nueva UI. D2A/D2B/D2C0 y módulos anteriores intactos, V2 productiva OFF, Android G15 NOT_RUN.

Resolución B7: PASS_SCOPED_DEV_JVM_READ_ONLY. Consumir READY -> BLOCKED, documentar último commit funcional exacto como PASS, liberar writer CAS y esperar guardia del checkpoint documental final. Siguiente subunidad D3 requiere B1–B5 y READY independientes; NO heredar permisos de D2C1.
