# FARO V5 — W04-D2 · Presupuesto y promociones — diseño de avance sin permiso funcional

2026-11-20. Depende de W04-D0 B7 `32934177735ac3557195a67815452dc6a48284bd`, W04-D1 Coverage scoped STAGED futuro. **PRE-DESIGN ONLY — NO READY/B6.** No repetir W4-C ni D0.

## B1
El carrito D0 tiene packs por necesidad y producto, sin precios. C2 contiene precios observados con currency/locality/presentation/pack/eligibility/validity; no garantiza precio actual ni stock. C1B1 terms promociones tipadas: `DISCOUNT_PERCENT` en basis points (1..10000), `FIXED_AMOUNT` en minor units de la moneda original, condiciones fuente y método; `UNKNOWN` implica que no se puede suponer beneficio. No hay esquema de compra pagada ni captura de boleta.

## B2
V4.4 SmartCompra mostraba presupuestos y promociones; V5 separa estimación de dinero REAL de Finanzas. Promoción de reintegro posterior ≠ descuento inmediato. Cuotas, impuestos, topes, vigencias, elegibilidad y bancos no se interpretan por intuición. Cualquier cálculo sin ellos será parcial, no certificado.

## B3
ELEGIDA para estudio: motor puro de presupuesto por moneda y por comercio/condición, aritmética entera checked (Math.multiplyExact/addExact), divisisión redondeada definida para bps; nunca Double para dinero, sin FX. ADAPTAR: promociones tipadas C1B1, Revisión D3. RECHAZAR sumatoria de cotizaciones STAGED como compra confirmada, cuota igual monto pago, cashback como saldo, scraping.

## B4
Scope tentativo D2A: subtotal observado por línea sólo bajo coverage D1 (con advertencia), packs × price minor checked y presentación compatible explícita; separar ARS/USD. D2B: promociones verificadas con ventana/condiciones/contexto; porcentual bps y fixed amount con tope y piso 0, sin apilar descuentos automáticamente. D2C: escenarios y evaluación por comercio, reintegro futuro como campo informativo separado. D3 revalida asOf, fuente, permits, fingerprint antes de Review. Antes de B1–B5 específicos D2 consultar código real D1 B7 y congelar unidades/rutas; D2 no necesita pagos/stock.

## B5
12 adversarios futuros: mezcla moneda; exceso Long y 200 líneas×10000 packs; precio observado stale/UNKNOWN; promoción porcentaje 10% exacto y bps 1234 redondeo explícito; fixed discount > subtotal; promo restricciones, método o localidad; cashback después sin sumar a ahorro inmediato; cuotas/período; fecha de expiración/futuro; cambios de sourceWatermark; need quantity ≠ pack conversion; ningún Finance Apply. Todos NOT_RUN.

## Gate
DESIGN_ONLY; no permiso, no código, no CI. Esperar D1 B7 para emitir preauditoría/READY separado. W04-E UI y W04-F global no se activan.
