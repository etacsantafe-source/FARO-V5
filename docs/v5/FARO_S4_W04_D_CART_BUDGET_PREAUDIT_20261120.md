# FARO V5 — W04-D · Carrito, presupuesto y promociones — preauditoría inicial B1–B5 de diseño

Fecha 2026-11-20. **DESIGN_ONLY, NO READY, NO B6, NO PRODUCTO ACTIVO**. No repetir W04-A/B0/B1/C ni W01–W03.

## B1
Household Reality10 tiene necesidades y Review/Apply/Undo B7. C0A enlaza producto explícito; C0B lee precios legacy con grants, C1A registra schema1, C1B2 schema2 tipado STAGED, C2 compara observaciones históricas con cobertura, C3 refresca manual STAGED. No hay cotización vigente activa ni carrito/presupuesto certificado. `purchase_plan.preview` y `commercial.quote` READ_ONLY no autorizan pagos, Finance o stock.

## B2
V4.4 SmartCompra L11/L14/L15 guía UX de listado, carrito por tienda y presupuesto; V5 separa precio Evidence, compromiso comercial Household y dinero Finance. Mantener necesidades por cantidad/unidad sin asumir equivalencia de SKU/pack. Opción A P07: Household precios/promos, Finance dinero; nada de doble Apply financiero ni stock al elegir carrito.

## B3
Elegida: modelo local determinista de carrito con líneas explícitas, moneda y pack/comercio/localidad, Coverage de Evidence y suma en minor units sin FX. Referencias UX Grocy/KitchenOwl/P08 sin importar sus runtimes/licencias; descartar scraping obligatorio, precio "actual" inventado, descuentos no verificables, pagos automáticos y sumar monedas distintas.

## B4
División CANDIDATA no autorizada: D0 draft inmutable de carrito con cantidades/unidad/producto seleccionado; D1 cuota de certeza (fuente/fecha/localidad/vigencia/pack/moneda/eligibilidad); D2 presupuesto y promo condicional/por instrumento con descuento inmediato distinto de reintegro futuro; D3 review/fingerprint y revalidación de fuente, permisos y generación; D4 integración Android/UI en W04-E con pruebas propias. Empezar D0 sólo tras inspección de contratos y rutas exactas propias, publicación de B1–B5 final, guard documental y READY independiente. C2/C3 STAGED no habilitan automáticamente host ACTIVE.

## B5
T01 need ausente/completado no crea línea; T02 gramos vs pack desconocido no se convierten; T03 ARS+USD sin FX => totales separados; T04 localidad/vigencia UNKNOWN => presupuesto PARCIAL; T05 descuento 20% en 100000 minor => 80000 sólo si condiciones verificadas; T06 reintegro esperado no es REAL; T07 promo vencida/cambio de medio o epoch invalida Review; T08 cuotas no duplican deuda Finance; T09 historial viejo y stock desconocido no prometen disponibilidad; T10 lectura/carrito no mutan Finance, Evidence, receipts ni stock; T11 offline cobertura parcial; T12 UI 200%/TalkBack/G15 NOT_RUN.

## Gate
Estado **PREAUDIT_DESIGN_ONLY_NO_READY**. D0 exige B1–B5 específicos, rutas exactas, guard y READY separados. No usar el permiso C3 ni activar V2 productiva/egress/pagos.
