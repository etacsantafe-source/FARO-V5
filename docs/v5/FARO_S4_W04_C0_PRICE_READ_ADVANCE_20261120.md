# FARO V5 — W04 · Repaso posterior a B1 · Adelanto C0 y riesgos abiertos (DOC_ONLY)
Fecha 2026-11-20, baseline `5cc67fc5a3bc0bcfe1f3246c746b812dfc55a294`. No concede B6 de W04-C. Preparación adelantada sin interferir con READY B1A.

## Qué se puede adelantar ahora sin depender del código CRUD
1. Evidencia real: `v5/evidence/src/main/kotlin/ar/com/faro/v5/evidence/CommercialEvidence.kt` @blob `cf9d53089cafd93538e6b03f9873c98264d20b65` YA implementa `CommercialEvidenceStore.record`, `priceHistory`, fuente, comercio, currencyCode, amountMinor, observedAt opcional y acquiredAt. No crear segunda tabla ni nuevo backend Household para observaciones.
2. Sus pruebas `v5/evidence/src/test/kotlin/ar/com/faro/v5/evidence/CommercialEvidenceT2Test.kt` @blob `aa8a45b5b747fa4f7d1e5d00c0649a22727364f9`: persist/reopen, idempotencia, historial, pure read, sourceRef opaca, malformed, binding y stamps. Son tests históricos **existentes**, no claims de integración W04.
3. Según `v5/spec/generated/contract-baseline.tsv` @blob `5653a9758f682f683a852dcf286211bae71c41d0`, `commercial.price_history.read` es `commercial-evidence`, **no Household**; ownership no debe cambiar.
4. IMPORTANTES DATOS QUE FALTAN en ese tipo: moneda y comercio SÍ, pero localidad, vigencia de promo, stock disponible y descuento elegible NO están guardados en `CommercialPriceObservation`. Por tanto, la proyección debe decir **"precio observado"**, `SIN_FECHA` cuando observedAt null, `LOCALIDAD_DESCONOCIDA`, `VIGENCIA_NO_CONFIRMADA`; nunca anunciar "en oferta hoy" desde evidencia insuficiente.

## C0 B1—B5 preliminares
### B1 — Contrato de entrada
El futuro módulo C0 es una proyección local `CommercialEvidenceStore.priceHistory` por producto + lectura tipada del need Household, bajo grants propios; no registra compra/finanzas. Falta mapping confiable `HouseholdNeedId` ↔ `CommercialProductKey`. NO asumir igualdad de nombres, unidad o precios por paquete.
### B2 — Reuso
V4.4 L11/L15 SmartCompra conserva idea de comparar comercios. V5 permite Evidence≠Reality. Reusar metadata temporal de Evidence y nombres humanos de needs, sin DB legacy ni importación de servidores.
### B3 — Alternativas
A READ_MODEL NO_MUTANTE sobre Evidence existente, **ELEGIDA**; B precios duplicados en Household, RECHAZADA; C scraping / feeds de terceros / scraping sin consent, RECHAZADA (offline, falta proveedor, egress, vigencia).
### B4 — Propuesta de proyección
Datos por observación: producto canonizado **si se verifica correspondencia**, descripción humana, merchant, amountMinor, currencyCode, sourceKind, sourceRef no ejecutable, observedAt/acquiredAt, coverage flag. Visualización por moneda sin sumar ARS+USD, orden por observedAt con null explicit; evidencia obsoleta no puede generar una nueva propuesta de carrito sin revalidar review.
Congelar rutas solo después de B1B (tendrá ownerNeed snapshot y su grant). No tocar `CommercialEvidence.kt` salvo prueba de delta material; adapter READ_ONLY con tests JVM negativos; NO_READY C0.
### B5 — Matriz anticipada
C0-T01 null observedAt -> SIN_FECHA; T02 misma necesidad con dos claves ambiguas -> NO MATCH, preguntar; T03 ARS/USD separados; T04 ausencia de localidad -> NO PROMO VIGENTE; T05 sourceRef como texto opaco; T06 owner/grant revocado -> sin leak; T07 precio observado nunca crea entry Finance, stock ni recibo; T08 lecturas concurrentes y cierre/reopen no mutan Evidence; T09 cambio de revision de need invalida mapping de precio.
STATUS PREAUDIT_DOC_ONLY. Rutas, ACL de host e integración aún pendientes, no afirmar tests C0 PASS.

## Otros pendientes detectados para no colgarlos
- B0 global B7 existe sólo en **JVM/SQLite host DEV**. W04-E Android API35 y Moto G15 físico siguen NOT_RUN; no disfrazar B0 como experiencia humana lista.
- B1 necesita preparar el paso **ACTIVE10 no readonly**, inexistente en `RealityStore` actual. Evitar usar el puerto de archive STAGED como operación real.
- B0 FBS2v6 ya se apoya en cinco stores; al agregar CRUD B1 se debe demostrar backup/restore de datos **escritos en ACTIVE10**, con maintenance lease y stage sellado, y verificar ausencia de pérdida de receipt+historial al restaurar.
- **El modelo Household10 no tiene un historial/antes-después persistente separado**: Undo de update/remove podría no ser reversible si el origen no está en el Review COMMITTED durable. Debe resolverse con prueba y nuevo delta de B1D, NO anunciar Undo global por tener receipts genéricos.
- Cantidad V4.4 expresada en MICROS (10^6) mientras V5 usa MILÉSIMOS (10^3). Ninguna importación legacy puede copiar números sin conversión exacta. Si hace falta conversión de unidades, crear scope separado; no diseñarla como mutación de catálogo.
- Botón "Comprar" no mueve dinero en W04; W05 recepción/stock y W06 atomicidad multi-owner NO están incluidos en permiso W04-B1.
