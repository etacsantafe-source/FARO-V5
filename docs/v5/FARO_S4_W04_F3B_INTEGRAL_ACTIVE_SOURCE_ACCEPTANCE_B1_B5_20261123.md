# FARO V5 — W04 F3B · Integración de fuente real y aceptación adversarial Android · B1–B5

**Fecha local:** 2026-11-23. **Unidad:** `S4_W04_F3B_ACTIVE_SOURCE_INTEGRAL_ACCEPTANCE_DEV`. **Baseline:** `317e538d4e9af0bb7bf1bd1113c93dde36ec7884`. Estado `B1_B5_PREAUDIT_DOC_ONLY`; no código sin guard y permiso separado READY. F3B evalúa una **parte** de F3 integral, no equivale a certificar integración en el host principal ni G15.

## B1 — Evidencia de código, huecos nuevos, no duplicar pruebas
W04-F1 B7 8/8 JVM, F2B2C corte tipado PASS, F2C1B1B1B1A Android fuente ACTIVE real 3/3, F2C1B1B1B1B decisión histórica D4/E Android 3/3, F3A botón nativo Android 3/3 y APK offline. Último código de F3A `9d2f63e046696f0df731c3c5f29acc25fe6d20df`; CI `45308808271` SUCCESS t07–t09 y XML/manifest, guard funcional `45308808174` SUCCESS, B7 docs `FARO_S4_W04_F3A_TRUSTED_NATIVE_HISTORY_ENTRY_B7_PASS_20261123.md`, checkpoint `317e538d4e9af0bb7bf1bd1113c93dde36ec7884`, guard final `45310581801` SUCCESS, writer RELEASED epoch1760. T01–T09 y módulos F2 anteriores certificados no se repiten.
**Brechas distintas no probadas en combinación Android real:** (a) D4/E no modifica Household/Commerce ni cambia generación o su revisión tras revisión; (b) revocación de scope Commercial entre entrada y selección invalida inmediatamente la vista y no recupera selección; (c) puntero activo A→B→A con **secuencia nueva** no revive sesión antigua a pesar de tener igual generationId final; (d) recuperación del proceso a foreground no reaprovecha consents/host, pero una sesión posterior con **dos consentimientos NUEVOS** puede abrir la misma generación con lease fresco. La Activity DEV es inerte al inicio. Todo usando almacenes SQLite/Evidence2 reales privados existentes y única fuente ACTIVE, no mocks de precios.

## B2 — Reutilización / comparación UX
Reutilizar clase test `S4W04CommercialOwnerBackedPositiveAndroidTest` y `realFixture`, `consent`, `taps`, `mount` ya certificados. No cambiar producción ni reescribir su fixture. Estado del UI nativo debe seguir simple y explícito como UX V4.4, sin reutilizar runtime. El método `offerNativeHistoricalEntryForTrustedDevHost` F3A y el chooser D4/E F2C son autoridad existente y serán ejercitados end-to-end. El root canonizado privado Android, 5-store graph, F1 read fence, two-owner consent y validación D1–D4 no se modifican.

## B3 — Alternativas y razones
- SQLite aislamiento/WAL: https://www.sqlite.org/isolation.html y https://www.sqlite.org/wal.html: lectura no debe modificar registros de negocio, nuevo snapshot solo tras nueva sesión válida.
- Android Activity lifecycle: https://developer.android.com/guide/components/activities/activity-lifecycle : pause/stop revocan; nuevo resume NO restaura estado.
- OWASP MASVS AUTH https://mas.owasp.org/MASVS/07-MASVS-AUTH/ : no promover Boolean/Intent a owner grant; pruebas instrumentadas no son autenticación humana física.
- GitHub Actions Android instrumented tests https://developer.android.com/training/testing/instrumented-tests : validar XML de las **nuevas** pruebas y SHA de APK; no rerun de suites históricas.
A) Repetir t01–t09: descartado por costo y trabajo ya certificado. B) Mockear snapshot en pantalla: descartado por falso positivo. C) Generar test con nueva Activity o segundo graph: descartado por doble autoridad. D) Ejecutar cuatro **delta tests** en la clase fixture existente bajo runner filtrado y CI propia: elegido.

## B4 — Congelar 2 rutas exactas, 4 tests nuevos
1. `v5/app/src/w04AndroidTest/kotlin/ar/com/faro/v5/app/S4W04CommercialOwnerBackedPositiveAndroidTest.kt`: agregar únicamente:
   - `t10IntegratedHistoricalReviewLeavesActiveSourcesUnchanged` — registrar hash/fence, necesidad/revisión, evidencia y pointer antes, dos consentimientos reales, entrada+selección, D4/E histórico visible y aclaración no actual; relectura fuente y puntero iguales (sin Apply/pagos/stock).
   - `t11RevokedCommercialConsentDuringChooserNeverDisplaysHistory` — abrir selector, revocar solo Commercial scope, intentar elección y probar no importe/pantalla; Household consent independiente.
   - `t12PointerGenerationAbaInvalidatesHistoricalChooser` — después de selector, CAS ACTIVE A→B→A, comprobar secuencia distinta y observación elegida obsoleta, revocación fail-closed.
   - `t13FreshForegroundSessionMustReconsentBeforeNewHistoricalReview` — primer host e issuer revocados en Activity.onPause; onResume prueba sin mounts automáticos ni datos; emitir NUEVO issuer y tocar dos NUEVOS botones, abrir NUEVO owner-backed lease y revisión D4/E, no revivir host antiguo.
   Usar `Button.performClick` (instrumentado, no identidad humana). Nunca inventar precio actual.
2. `.github/workflows/faro-v5-s4-w04-f3b-integral-active-android.yml`: runner Android API35, Java21 SDK37, **solo t10–t13**, 4/4 XML parseados y classnames exactos, cero failures/errors/skips, APK aislada offline, original XML y APK como artifact, digests SHA. Preflight y guard con READY exact2 + HEAD parent y commit FARO-UNIT de esta unidad.

## B5 — Controles adversariales y criterio de cierre
- Null/falta de permiso de evidencia, pointer change, generational ABA, observer revocado o pausa => NUNCA mostrar valor. No permiten nuevo montaje sin consents frescos y lease único. Sin mostrar ni interpretar `HISTORICAL_PARTIAL` como precio vigente ni cotización actual.
- La lectura puede producir archivos técnicos WAL/lock temporales, pero no modificar datos semánticos Household10/Evidence2 ni estados Finance9/stock/real payment. Comparar `household.revision`, selected observation record y source watermark; pointer `sequence` es parte de identidad.
- Caso positivo sin Internet, fuentes test-only en directorio privado, no datos personales, nunca abrir segunda autoridad, no seleccionar precio sin click. Metadatos src literales no son contenido confiable de contrato comercial.
- F3B sólo `B7_PASS_SCOPED_ANDROID_DEV` después de guard SUCCESS, CI XML 4/4 original y APK verificada; si RED, documentar falla real y solo corregir delta. La aceptación global W04-F3 y montaje sobre la navegación ordinaria de FARO siguen **OPEN** hasta revisión de todas las superficies, G15 físico independiente. V2 productiva, Finance Apply, pagos, compras, cloud, Auto/Noche OFF. Rama main intacta.

## Release
Primero guard documental SUCCESS, publicar READY **exactamente dos rutas** con evidencia B1–B5 por blob real. Adquirir/release writer CAS entre commit/CI. B6/CI sin repetir t01–t09; B7 independiente consume READY y reescribe puntero sólo tras datos reales. Si falta XML o CI pasa sin tests contados, no marcar PASS.
