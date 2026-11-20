# FARO V5 — W04-C · Deltas por unidad C0B, C1, C2/C3 (B1–B5)
Fecha local 2026-11-20; baseline `a60c184d116bd0c8a9ddc6941a6eb3478b5455a6`. Este archivo documenta preparación íntegra de diseño y riesgos, **no** autoriza rutas ni B6. Evidencia transversal completa en `FARO_S4_W04_C_COMPLETE_PREAUDIT_B1_B5_20261120.md`.

## C0B — AUTHORIZED_PRICE_HISTORY_READ_DEV
### B1
`CommercialPriceHistoryReadExecutor` verifica descriptor READ_ONLY N-034/commercial-evidence/BINDABLE y delega al `priceHistory(productKey, limit 1..200)`. `FaroV5GenerationStoreGraph` ofrece CommercialEvidenceStore y Reality en el mismo grafo, pero `Household10DevApplySession` solo expone Reality/Workspace. Requiere salida de lectura segura, no segundo root lock.
### B2
Legado L11/L15 permite historia comercial/cotizaciones, pero las observaciones V5 son Evidence independiente de necesidad/Finance. Mantener null observedAt, acquiredAt y monedas sin inferir localidad.
### B3
ELEGIDA: read-port temporal en sesión EXISTENTE con callbacks/grants independientes y fences alrededor de consulta. RECHAZADO: abrir grafo segundo, lectura directa sin ACL, pantalla que llama store sin host, copiar Evidence a Household.
### B4
Entrada única de C0A validada en snapshot/fence, executor catalogado; resultado observado con store provenance y coverage; host verificará foreground, principal, grants Household+Commercial, generation pointer, needRevision y validez antes/después. Dev-only. Rutas candidatas: `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt`, `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialPriceReadHost.kt`, test homónimo en app/src/test, workflow dirigido. Revisar firmas tras C0A; no congelar READY ahora.
### B5
Revocar cualquiera de los dos grants, ABA generation, limit>200, null fecha, fuente opaca, cierre concurrente, zero results, store aún sano, no mutation+receipt, misma selección inválida tras cambio de unidad. Pruebas NOT_RUN.

## C1 — MANUAL_QUOTE_EVIDENCE_DEV
### B1
`commercial.evidence.record` OBSERVATIONAL_WRITE con `CommercialEvidenceRecordExecutor`, `CommercialPriceObservation` y Evidence store v1 existente. `commercial.quote` READ_ONLY **no** es licencia para grabar precio o hacer pago. Faltan ciudad, vigencia, promoción y forma de pago en observación actual.
### B2
V4.4 L11/L15 permite cargas/manuales contextualizadas. No copiar un precio "vigente" sin fecha y ciudad ni guardar oferta financiera en Reality.
### B3
ELEGIDA CONDICIONAL: reutilizar `CommercialEvidenceRecordExecutor` para registrar la observación manual con los campos soportados. Para cotización completa, diseño de metadata tipada versionada en la MISMA Evidence, compatibilidad con v1, backup/restore/import/export; rechazar campos mágicos codificados en sourceRef. Si la migración no está certificable, devolver cobertura incompleta. Carga manual offline, no API externa.
### B4
Ingreso por humano: producto explícito, comercio, monto positivo, código moneda, sourceKind MANUAL, fuente literal, observedAt opcional; localidad, issuedAt/validUntil, unidad/pack y elegibilidad conocidos o UNKNOWN. Prohibido registrar como completa una quote que no pueda persistir esa metadata; preparar cambios versionados con backup y lectura histórica, decidir rutas exactas después de inspeccionar codec/migración Evidence. Rutas candidatas `CommercialEvidence.kt`, `CommercialEvidenceBackupPort.kt`, tests Evidence y host/app, sin nuevo owner/base ni catálogo.
### B5
Precisión menor por moneda, monto inválido, fecha nula, local no verificado, expiration, doble envío/idempotencia, crash, migration v1→v2, restore antiguo/nuevo, revocación, sourceRef texto opaco y no-mutación Finance/stock. NOT_RUN.

## C2/C3 — HISTORY_COMPARE_REFRESH_DEV
### B1
History ya existe en Evidence, con orden SQL por COALESCE y adquirida separada de observada; comparación/contexto, re-cotización y vínculo de renovación NO existen como host W04. Comparador debe leer sin alterar.
### B2
L11/L15 ofrece comparación por comercio y presupuesto; V5 no equipara SKU/package ni moneda automáticamente, ni conserva localidad/vigencia en v1.
### B3
ELEGIDA: proyección determinista de solo lectura que expone coverage, serie por moneda, filtros de comercio/localidad y flag stale; refresh manual = registro nuevo vía evidencia autorizada, sin UPDATE destructivo. RECHAZADA: convertir moneda automáticamente, completar fechas implícitas, sobreescribir observación vieja o registrar compra/pago.
### B4
Comparar precio observado solo entre unidades verificadamente equivalentes con fórmula/transporte auditables y misma moneda. Cada observación conserva merchant/sourceRef/observedAt/acquiredAt. Historial no elimina precios viejos al refresh; local desconocida ⇒ no ahorrar por local; expiración/actualización invalida cotización/resumen para W04-D. Si link de sustitución no se guarda con id verificado, no mostrarlo como probado. Rutas App read-model/tests más workflow después de C1; cambios Evidence solo con delta material y permiso propio.
### B5
ARS/USD, pack frente a kilo, unidad inexistente, mercados de ciudad distinta, oferta caducada, hora adquirida distinta de observada, cambio de precio justo antes de Review, doble refresh, fallos de owner/foregound, read puramente no mutante, respaldo y constancia de evidencias antiguas. NOT_RUN.

## Puertas y conflictos
C0A primero → C0B → C1 → C2/C3. C1b nueva metadata sólo si preflight independiente y migración/backup validada. W04-D = plan/cart/promo/presupuesto comercial, W04-E = UI Android DEV/API35, W04-F = B7 integral; W05 stock, W06 atomicidad Finance+Household. NO realizar pagos ni stock desde cotización. Estas rutas son candidatas, no scopes READY.
