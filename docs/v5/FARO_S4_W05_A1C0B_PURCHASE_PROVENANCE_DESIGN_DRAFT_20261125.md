# FARO V5 · W05-A1C0B · Compra manual confirmada

## B1 — Evidencia y alcance

El recorrido comercial W04 conserva un carrito efímero en `S4W04CommercialCartDraft` y estimaciones históricas en `S4W04ObservedCartBudget`. El esquema `RealityHousehold11ReceiptSchema` conserva cantidad recibida y referencia, pero carece de cantidad comprada y procedencia de compra. `RealityHousehold11ReceiptRowsPort` exige SQL11 y valida las filas de recepción. `FaroV5Household11Fbs2Codec` exige exactamente dos secciones, base y H11R, con digests. No hay un campo disponible para guardar procedencia de compra sin cambiar el contrato de datos.

## B2 — V4.4 y reutilización V5

P02 L11/L12 y P03 R11/R12 separan plan, pago y recepción. Reutilizar conocimientos de operación y los tipos de cantidad y puntero de Household de V5. Mantener el owner Household, la familia de cinco almacenes, la captura coordinada, los controles de SQLite y el cifrado autenticado FAROBAK2. Los puertos SQL11 de etapa se mantienen restringidos a STAGED.

## B3 — Alternativas

Usar un nuevo esquema Reality y una versión de carga FBS2 antes de registrar hechos. Preservar la lectura original de v8. La ruta de importación de v8 conserva todas las recepciones y empieza con procedencia de compra vacía, incluso para referencias que ya figuren en recibos. Evitar tabla silenciosa en SQL11, archivo lateral, campos de recibos usados como registro de compra o asignación de autoridad a Evidence. SQLite delega al proyecto la interpretación de user_version. La guía de Room respalda validar esquema y datos tras migrar y evitar fallback destructivo. Aplicar esos principios a los puertos SQLite existentes.

- https://system.data.sqlite.org/home/doc/0a3d6229a7425242/Doc/Extra/Core/pragma.html#pragma_user_version
- https://developer.android.com/training/data-storage/room/migrating-db-versions

## B4 — Diseño pendiente de rutas exactas

Propuesta Reality12 + FBS2v9. Incorporar una sección autenticada y canónica con referencias de compra, artículo, unidad, cantidad, revisión y procedencia manual explícita. Vincular principal, puntero completo y confirmación exacta en el writer DEV propietario antes de la transacción. Limitar la primera implementación a confirmaciones inmutables con revisión y reintentos idempotentes de la misma intención. Una colisión con datos distintos se rechaza. El adaptador confiable lee únicamente compras confirmadas de la generación autorizada y calcula el total recibido para la misma referencia, artículo y unidad. Capturar compras y recepciones bajo la misma vista coordinada. Restaurar en STAGED, validar y releer semánticamente antes de cualquier eventual cutover DEV. La capacidad de recepción conserva su gate independiente.

## B5 — Casos que condicionan READY

Demostrar conservación de filas y recibos al migrar, ausencia de inferencia de compras al abrir v8, round-trip autenticado de compras y recepciones, rechazo de payload adulterado y de puntero obsoleto, rollback sin filas parciales, rechazo de carrito, precio, grant de necesidades y confirmación revocada. Probar reapertura, duplicación de intento y colisión con cambio de artículo, unidad o total. La selección del conjunto exacto de rutas, la auditoría de integración y la guardia documental aún están pendientes. Este documento no emite READY ni habilita stock.
