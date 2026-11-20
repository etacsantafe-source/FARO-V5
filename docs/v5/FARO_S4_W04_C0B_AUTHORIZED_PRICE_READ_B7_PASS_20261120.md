# FARO V5 — W04-C0B · B7 PASS scoped DEV · lectura autorizada Evidence

Fecha Argentina: 2026-11-20. Rama `work/s3-night-20261102`. Unidad `S4_W04_C0B_AUTHORIZED_PRICE_HISTORY_READ_DEV`.
HEAD funcional **final** `4f9808cc26956e6e49b3836487dee68f5b0b9d99`. Baseline previo C0A B7 PASS `5df8c94099bad13c396313cb8f184194d1a4b054`.
No repetir W04-A/B0/B1 ni C0A.

## B7 — Evidencia y veredicto

### I. Contratos reutilizados, alcance real
La primera entrega de C0B `b7cd33869fdb8309bf694357a7539a05fff7f620` modificó exactamente 5 rutas permitidas: grafo Generation, host Household, nuevo host comercial read-only, prueba JVM y workflow. El ROOT único de Household10 `Household10DevApplySession` ahora expone sólo `authorizedCommercialPriceHistory` con `CommercialPriceHistoryReadExecutor`, sin exportar `CommercialEvidenceStore` mutable ni abrir segundo lease. Las verificaciones de grant Household y Commercial se hacen antes/después de consulta y se revalida la selección C0A con snapshot/revisión/fence (generation, pointer epoch/sequence y principal). La política de lectura N-033 `shopping.snapshot.read` es distinta de la N-034 `commercial.price_history.read`; el descriptor comercial es READ_ONLY/BINDABLE/ejecutor canónico. Un producto nunca se obtiene por búsqueda difusa. Fuente opaca no ejecutable.

La vista `CommercialPriceHistoryView` preserva registros reales Evidence con comercio, moneda, amountMinor, `observedAtEpochMillis` nullable y adquisición por separado; informa `localityKnown=false` y `quoteValidityKnown=false` porque schema Evidence v1 no posee esos datos. Historial sin entradas retorna Observed(0) sólo con autorizaciones válidas. No hay FX, geolocalización inventada, cotización completa, mutaciones Finance/Household/Evidence ni nueva fuente exterior. Sin UI Android ni prueba G15.

### II. RED_FIX real sin contaminar código funcional
B6 inicial `b7cd338...` pasó guardia `44787942287` y preflight exact5 pero workflow `44787942512` **FAILURE**: test app importó `BundledSQLiteDriver` sin dependencia directa de app; no XML. Disposición B7 **RED_FIX** publicada y verificada en `docs/v5/FARO_S4_W04_C0B_B7_RED_FIX_20261120.md` @blob `5d68e2df1221057d170ec4de7fee74df5719cc55`.
Corrección `4f9808cc26956e6e49b3836487dee68f5b0b9d99`: únicamente test y workflow, dentro del mismo permiso exact5. Fixture crea necesidad por Review/Apply Household existente (sin SQLite app extra) y después prueba lecturas sin nueva mutación. Permiso de historial vacío cubre SKU sin observaciones. Workflow valida *cada ruta* cambiada con preflight exacto y acepta subconjuntos 1..5; mensaje sigue `FARO-UNIT:S4_W04_C0B_AUTHORIZED_PRICE_HISTORY_READ_DEV`. No se alteró `:evidence`, esquema, gradle, legacy, C0A ni lógica productiva en RED_FIX.

### III. CI exacta + XML independiente
- Commit probado: `4f9808cc26956e6e49b3836487dee68f5b0b9d99`.
- Preflight de rutas cambiadas en run C0B `44789820660` **SUCCESS**.
- `:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04CommercialPriceReadHostTest` **SUCCESS**.
- Paso `Require exact C0B JUnit XML 9 of 9` **SUCCESS**, 9/9.
- Guardia `seven-block-evidence` run `44789820679` **SUCCESS** del SHA funcional.
- ZIP artefacto `14656895187`, `w04-c0b-authorized-price-read-xml`, sha256 ZIP descargado `9b49f35475bc6c516b145ac4fe64ba8eb953e0d8e5ed81daeca803d43f36ce42`.
- XML leído y parseado independientemente `testDebugUnitTest/TEST-ar.com.faro.v5.app.S4W04CommercialPriceReadHostTest.xml`: `tests=9 failures=0 errors=0 skipped=0`, nueve casos individuales presentes.

### IV. Matriz adversarial cubierta
1. Lectura CommercialEvidence1 persistida de la misma generación y Need creado por Apply real, preservando merchant, currency, amountMinor, fuente opaca, acquiredAt y observedAt null.
2. Historial vacío autorizado no fabrica precio vigente, localidad ni expiración.
3. Revocación Household independiente y rechazo de selección.
4. Revocación Commercial independiente sin heredar grant Household.
5. Selección cancelada/ambigüedad/need inexistente no consulta Evidence.
6. Límite inválido 0 y 201 denegado sin leer.
7. Background o epoch rotado invalida lectura.
8. Pointer ABA y apertura concurrente de segundo lease rechazadas.
9. Revocación Commercial tardía no expone datos ni cambia revisión Reality.

### V. Scope, límites y next
- **B7 PASS SCOPED DEV — C0B cerrado**, no B7 global W04-C. Solo JVM integrada con SQLite staging y host DEV; Android físico/UI/W04-C1/W04-D siguen NOT_RUN.
- El workflow antiguo `W04-B1A` run `44787942245` falló al auto-dispararse por el cambio anterior del archivo Generation compartido y exigir su preflight B1A. Es **fallo de trigger ajeno** a la prueba C0B y no prueba regresión de B1A. Registrado para reparación separada bajo permisos propios, sin rehacer B1A ni declararlo corregido.
- Consumir permiso individual C0B READY → BLOCKED; `last_code_review.sha` igual al SHA final y resultado PASS, documento @blob; `B6=PUBLISHED_VERIFIED`, `B7=PASS_CONSUMED`.
- Próxima unidad: `S4_W04_C1_MANUAL_QUOTE_EVIDENCE_DEV`, **sólo B1–B5 preliminares**, inspeccionar metadata/localidad/vigencia, backup/migración y permisos antes de congelar rutas. No usar permiso C0B para escribir C1.
- Writer global debe quedar RELEASED. V2 productiva OFF, Auto/Noche OFF; main/V4.4 intocados; no APIs pagas.