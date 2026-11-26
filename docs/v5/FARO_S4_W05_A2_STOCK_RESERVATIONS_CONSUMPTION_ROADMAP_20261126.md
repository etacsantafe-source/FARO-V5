# FARO V5 · W05-A2 — Plan acotado de inventario, reservas y consumo

Baseline `2315c09b2cc6aad42490900f79dad77453afbf18`. **A1 B7 PASS DEV** y no repetir sus unidades. W05 todavía abierto fuera de A1. El orden se apoya en P05 S04/S05, P08 W05 y P07 Household/Finance. Este plan **no autoriza B6** de los sucesores ni activa producción.

## Secuencia de unidades independientes

- **A2A — Físico neto SQL13 READ_ONLY**: contrato de vista física recibida y compensada, sin falso "disponible". Primera unidad a preparar B1–B5, permiso exact3 y posterior B6/B7.
- **A2B0 — Modelo y persistencia de reservas**: elegir generación nueva SQL14 STAGED (no alterar SQL13 ACTIVE), libro tipado append-only de reservas/releases con cantidades, caducidad explícita si aplica y asociación SKU+unidad. Especificar invariantes `reserved>=0`, `reserved<=physicalNet-consumed` y reservas revocables; FBS2v11 y cinco stores si hay nuevas tablas. Debe tener su propia matriz B1–B5, permisos, backup, restore y B7. No usar `stock.reserve` hasta grant/owner real probado.
- **A2B1 — Review/Apply stock.reserve / stock.release DEV**: una revisión humana y recibo por acto, WRITE COORDINATOR único, CAS de generation/revision, recovery COMMITTED/UNKNOWN, Undo compensatorio según contrato. Rechazar oversubscription y doble tap, sin modificar dinero.
- **A2C0 — Consumo físico SQL15 STAGED + backup**: journaling de uso explícito, exact source, cantidad, unidad, fecha y estado de reservas; `consumed` sólo desde evento Apply humano; FBS2v12 y SQL15 requieren aprobación separada (numeración provisional, auditada antes de adoptar).
- **A2C1 — Review/Apply stock.consume DEV**: no consumir por receta preview, carrito, pago o fecha programada; bajo stock disponible y autorización; actualizar físicamente sólo tras recibo COMMITTED. Reversión por compensación separada, sin borrar evidencia.
- **A2D — Lectura integral y UX stock**: ahora sí `physical = receipts - receiveUndo - consumes + validConsumeUndo`; `reserved` incluye compromisos activos; `available=physical-reserved` con unidades homogéneas, no negativos. Pruebas Android API35 fuente200, lectura SQL15, historial/recuperación, restauración, TalkBack si procede. Bloquear presentación numérica de disponible antes del libro certificado.
- **A2E — B7 global inventario DEV**: XML original de suites nuevas, APK DEV, recuperación/crash/ABA, 10 comprado→7 recibido→3 faltante→reserva→consumo con receipts y sin pagar; instalación G15 físico NOT_RUN hasta campaña específica.

## Fronteras

A2 no es Cocina: A3 de W05 futuro para recetas, planificación, confirmación de preparación y sobrantes con A2B/C como dependencia. No usar nueva sección raíz de Cocina. W06 atomicidad Finance+Household y W07 pagos son paquetes S4 posteriores y permanecen fuera de W05. V2 productiva, stock real, pagos, Auto/Noche OFF; sin APIs pagas. Descripción de SQL14/15 y FBS2v11/v12 es **candidata**, no esquema aprobado ni código autorizado.

## Control de cierre

Cada A2 subunidad comienza con permiso propio exacto; B7 previo es condición. Si una unidad no puede demostrar atomicidad o backup/versionado seguro, STOP antes de Apply. A2 global no CLOSED hasta A2E B7, y Moto G15 no será PASS sin prueba física.
