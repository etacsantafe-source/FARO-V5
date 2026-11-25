# FARO V5 · W05-A1C0B→A1C4 · mapa de ejecución y dependencias
24/25-11-2026. Documento de planificación; NO READY por sí mismo. Reutilizar C2C0 y C2C1 B7. Sin pagos, stock real productivo, V2 productiva ni Auto/Noche.

## A1C0A — contrato tipado DEV (primero)
Preauditoría independiente `docs/v5/FARO_S4_W05_A1C0A_STOCK_RECEIVE_DEV_ADMISSION_B1_B5_20261125.md`. B6 tres rutas exactas y 8 JUnit; B7 acotado. Su salida no activa stock ni crea compras. No confundir con un grant runtime.

## A1C0B — origen durable de compra confirmada
**Brecha real observada:** los carritos W04 son DRAFT y los presupuestos son observaciones estimadas, no compras. El SQL11 de recepción contiene `purchase_ref` y `quantity`, pero no una tabla/autoridad que conserve `purchased_total` verificable entre sesiones. Un callback arbitrario del chat no es fuente confiable.
Decisión arquitectónica propuesta: usar un hecho **manual y explícitamente confirmado** por el usuario en Household DEV, con referencia opaca de compra, artículo, unidad, cantidad comprada y revisión/origen. No exigir factura o pago externo si la compra se declara manualmente; pero **no afirmar verificación externa de realidad física**. Persistir e identificar una sola verdad local revalidable, con recibo/intención durable y posterior restore. No convertir carrito ni finanzas en ese hecho.
Antes de B6: auditar si existe un owner activo de compra real (hoy no identificado); determinar cómo persistir purchased-total sin romper schema11/FBS2v8 backup y restore: nueva versión de Reality o contrato auditado equivalente, más plan de migración. **No improvisar tabla bajo SQL11 ACTIVE sin versión/backup**. Preparar B1–B5, rutas exactas y permiso nuevo. Sin fuente, A1C1 permanece BLOQUEADO.

## A1C1 — Apply real DEV de recepción física
Tras C0A y C0B B7: único owner Household, generación ACTIVE SQL11/versión validada, principal+epoch+full pointer+grant `stock.receive`, `Review→Approve→Apply` human-in-the-loop, SQLite transaction + CAS de revisión/recibo, intent_key UNIQUE. Cantidad física parcial 10→+7 pendiente3, luego +3. Nunca escribir si presupuesto es solo estimación, si la compra no está confirmada o si la unidad no coincide. `UNKNOWN` reconciliar receipt, no reintento ciego.

## A1C2 — recovery y Undo verificables
Crash PREPARED/COMMITTED, repetición de intent, reinicio/restauración FBS2 y ABA; Undo compensatorio mediante nuevo evento/recibo vinculado; jamás DELETE de historial físico ni doble devolución. Backup/restauración de purchased-total junto con ledger de recepciones.

## A1C3 — Android DEV y UX
Revisión y confirmación explícitas desde variante aislada, acceso simple a compra confirmada, cantidad recibida/restante, historial y Undo. Foreground revoked/background bloqueado, lectura de WAL y revalidación completa; test Android CI no equivale a G15 físico.

## A1C4 — cierre A1 DEV
Regresiones dirigidas C2C0/C2C1, receipts, source bought+received, recovery, Undo y backup. CI por SHA + XML originales, guard, B7; registrar exactamente lo que prueba el emulador. Si G15 no se ejecuta, `PHYSICAL_NOT_RUN`. No declarar cierre global hasta que A1C0B–A1C4 B7 real pasen y el permiso esté consumido/BLOCKED con writer RELEASED.

## Principios de decisión
- B1–B5 por unidad y permiso propio READY; no extender READY de C0A a C0B.
- `stock.receive` N-035 ya catalogado; no cambiar catálogo180.
- Snapshot de presupuesto, carrito, precio e instrumento de pago NO autoriza `stock.receive`.
- No introducír APIs pagas, Internet, pagos ni acceso a finanzas.
- Si hace falta modificación de esquema, incluir compatibilidad de backup/restore en el diseño **antes de programar**; fail-closed si no está demostrado.