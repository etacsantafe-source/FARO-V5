# FARO V5 — W04-C2 · Comparación tipada de observaciones comerciales STAGED Evidence2 — B1–B5

Fecha local 2026-11-20. Punto de partida y código verificado: C1B2 B7 global DEV `4bffc59b59458c185932bad8c8c127d8b84bbb00`, guard `44849835100` SUCCESS; writer RELEASED epoch 1476. Unidad NUEVA `S4_W04_C2_STAGED_TYPED_PRICE_COMPARISON_DEV`. Documento B1–B5; **DOC_ONLY, NO READY/NO B6** hasta guard + permiso exacto. No repetir C0A/C0B/C1a/C1b1/C1b2E/F/G/H1/H2A/H2B.

## B1
- Contrato vivo `CommercialEvidenceStagedTypedPortabilitySnapshot(schemaVersion=2,sourceWatermark,records)` en `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/EvidencePortabilityPort.kt`, blob `16cf4709ee533fb5cfedd08c4e44942b3b41f7ef`, es lectura STAGED sin idempotencyKey/evidenceId/payloadFingerprint. Projection H1 `faro-portability-commercial-v2`, hash + sort canónico; 100000 registros máximo, null físico != terms.UNKNOWN.
- `CommercialEvidenceTypedTerms.kt` blob `f5078d1ebb9a6541fa673a9b4dc3d4903fdef2c1`: localidad KNOWN/UNKNOWN, fechas issued/validFrom/validUntil, cantidad milésimos + unidad + pack, payment, elegibilidad, promociones y condiciones tipadas. Desconocido ≠ válido.
- `CommercialEvidenceStore.priceHistory` v1 ORDER BY COALESCE observedAt/acquiredAt, límite1..200; C0B host lee con dos autoridades y revalida before/after, pero su `CommercialPriceHistoryView` prohíbe localidad/vigencia=true y no monta schema2. C1b Evidence2 STAGED no está ACTIVE. Comparador puro NO constituye un nuevo host/grant ni salida a Android.
- Contratos oficiales N-034 `commercial.price_history.read` y `commercial.quote` READ_ONLY. Refresco mutante C3 aparte: actualmente `recordWithTypedTerms` sí anexa observaciones tipadas STAGED con idempotencia y transaction; C2 no lo invoca. Datos de compra, stock y Finance prohibidos.
- No existe comparador C2 tipado certificado. Las suites de C1B2 no lo probaron.

## B2
- V4.4 L11/L15 ofrecía historial y cotejos entre comercios; V5 debe conservar fecha real observada separada de adquisición, distinguir moneda/localidad/pack/instrumento, no inferir oferta actual, stock ni ahorro real. El titular único de Evidence conserva todas las observaciones; Reality necesidad, Finance dinero. Reutilizar H1 DTO limpio en vez de tabla espejo/segundo store/grant.
- Mostrar cobertura parcial frente a falta de fuente/local/fecha/validity. SourceRef texto opaco nunca ejecutable. Ni JSON export ni egress, ningún UPDATE de evidencia histórica. La comparación sólo reporta valores observados, no precio garantizado.

## B3
- **USAR** read-model determinista Kotlin puro sobre snapshot H1, con verificación del watermark canónico al entrar y corte `asOf` explícito.
- **ADAPTAR** agrupación estricta por producto, moneda, código de localidad conocida, pack+unidad+cantidad, mismo medio de pago (conocido) y elegibilidad GENERAL; series históricas de todos los registros sin suprimir originales. Sólo candidatos con observedAt real, asOf dentro de validez declarada, vigencia razonable, sin promoción condicional, para ranking de *precios observados*.
- **REFERENCIA** P02/P03 SmartCompra V4.4 + Grocy/KitchenOwl del análisis aprobado W04-C, sin copiar código ni licencia/runtimes. SQLite histórico ya lo resuelve Evidence; no añadir SQL ni red.
- **DESCARTAR** buscar ofertas por scraping, ordenamiento por FX nominal, convertir paquetes a gramos implícitamente, tratar acquiredAt como observedAt, computar descuento incierto, marcar actual la oferta por defecto, o actualizar precios viejos.

## B4
Una única unidad C2 **sólo lectura**, separada de C3. API pura `CommercialTypedPriceComparison.project(snapshot,productKey,asOfEpochMillis,maxAgeMillis,limit)`; sin I/O, sin SQLite directa, sin puerto de permisos ni Android, FAIL-CLOSED ante hash H1 incorrecto, orden malformado, tamaño/límites inválidos. Devuelve:
1. `history` completo del producto en el límite solicitado ordenado por observedAt (null al final), fecha adquirida separada, productKey original, merchant, fuente literal, moneda/terms originales y razón de cobertura de cada entrada.
2. Grupos exactos por localidad CONOCIDA, moneda, pack/qty/unit, paymentMethod conocido, elegibilidad GENERAL; ningún FX o normalización fuzzy. Candidatos a mínimo sólo si observado en asOf (no futuro), within freshness configurable `maxAgeMillis`, validez desde/hasta explícita que incluye asOf, promoción NONE certificada y ausencia de incertidumbre material. Cambios sourceRef/terms invalidan watermark. Min observado se informa sólo con al menos DOS comercios diferentes y sin truncamiento; anotación `OBSERVED_NOT_CURRENT_QUOTE`, nunca ahorro/stock garantizado.
3. `truncated` y cobertura parcial; 1..200; preferir null al candidato no verificable. No dependencia de user locale ni de la fecha actual del dispositivo.
4. C3 futura usa refresh anexando `recordWithTypedTerms` con permiso propio y CAS/idempotencia; no meter mutación dentro de C2.

**Rutas exactas C2, TRES (NO AUTORIZADAS hasta READY):**
- `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialTypedPriceComparison.kt` (NUEVA)
- `v5/evidence/src/test/kotlin/ar/com/faro/v5/evidence/CommercialTypedPriceComparisonTest.kt` (NUEVA)
- `.github/workflows/faro-v5-s4-w04-c2-typed-price-comparison.yml` (NUEVA)
No tocar H1, SQL, Finance, ACTIVE, app ni legacy.

## B5
T01 producto filtrado, 0/1/200 y limit201 rechazado; no datos => NO_DATA.
T02 null observedAt no sustituido por acquiredAt; no ranking ni "hoy".
T03 ARS/USD, localidades distintas y pack qty/unit distintos jamás comparables.
T04 validez UNKNOWN/expired/future y observedAt future/obsoleto => NO_COMPARABLE; asOf explícito.
T05 promociones UNKNOWN/PERCENT y elegibilidad RESTRICTED/UNKNOWN => sin ranking ni descuento inferido.
T06 moneda, localidad, presentación, payment, sourceRef idénticos pero comercios diferentes y periodo admitido => grupo observado comparable, mínimo por amountMinor y sin proclamar precio actual.
T07 doble registro mismo comercio no infla grupo; historia conserva ambos y último observado por comercio se elige sin borrar histórico.
T08 watermark corrupto, terms cambiado, orden no canónico, límites inválidos FAIL-CLOSED.
T09 sourceRef hostil `file://` y caracteres Unicode permanecen literales, no red ni archivos, sin execution IDs/receipts.
T10 truncamiento => ninguna afirmación de mínimo global; snapshot reconsultado no modifica Evidence.
T11 CI JVM `:evidence:test` dirigido con XML exacto, guard protocolo 3 rutas, regresión Evidence, release writer. Android/G15 NOT_RUN.

## Gate
`B1_B5_PREAUDIT_DOC_ONLY`. Publicar en commit documental, esperar guard SUCCESS, emitir READY individual EXACT3 en otro commit DOC_ONLY, esperar preflight verde, luego B6+CI+XML y B7 independiente. C3 no queda autorizado por C2. W04-C global sigue OPEN.
