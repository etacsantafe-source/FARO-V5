# FARO V5 · Segunda auditoría PRE-WORK W05-A1 · 2026-11-25

Estado al inspeccionar: rama work/s3-night-20261102, código A1C0B0 B7 d2f0a7a60be0ddf1cce9c02c80879ef0f79d5b27, 6/6 CI 45538967777 y guard 45538967902; checkpoint 6a18acc43ae5aa2f749735b0ca51ca7ef6b4aa64, guard 45540204924 SUCCESS. B1–B5 de A1C0B1 nuevos publicados al SHA ef325eadc58cf41bc656ed4055a6593368e81694, guard documental 45541113347 SUCCESS. **No nuevo B7 ni SQL/Android físico.**

## Revisión independiente — resultados y controles
1. PASA: formato previo FBS2v8 implementado por `FaroV5Household11Fbs2Codec`; FAROBAK2 por `FaroV5FiveStorePortableBackupCrypto` permite métodos nuevos sin alterar versión ni KDF. Mantener v8 byte-exact y FBS2v9 como codec separado; solo exact4 rutas B6.
2. PASA: Reality12 `household_confirmed_purchase` migra en STAGED, `purchase_ref` PRIMARY KEY y `provenance='MANUAL_EXPLICIT'`. **NO existe alta ACTIVE comprobada**. A1C0B2 SQL capture/restore + A1C0B3 cutover + A1C0B4 grant/alta son dependencias reales, no deben omitirse por pasar codec.
3. RESUELTO DOCUMENTALMENTE: catálogo oficial **sí tiene** `shopping.purchase.record` (N-036, household/REALITY_MUTATION/BINDABLE/MERGE), `shopping.purchase.read` (N-036 READ_ONLY/BINDABLE), `shopping.receipt.record` (N-036 REALITY_MUTATION/BINDABLE) y `stock.receive` (N-035 REALITY_MUTATION/BINDABLE), evidencia `v5/spec/generated/contract-baseline.tsv` blob `5653a9758f682f683a852dcf286211bae71c41d0`. No inventar `purchase.confirm` ni ampliar 180 operaciones. **Pero BINDABLE NO significa binding/admisión activo**, grant nuevo N-036 requerido.
4. ABIERTO: SQL12 no guarda `intent_key` de compra ni singleton revision: `purchase_ref` único permite detectar colisión, pero la idempotencia de Apply/receipt y CAS deberán comprobarse antes de A1C0B4. Si hace falta evolución schema, exigir versión y backup nuevo; no añadir tabla de manera silenciosa.
5. ABIERTO: H11R legacy puede tener recibos `purchase_ref` sin compra confirmada. El codec v9 acepta esos recibos históricos, pero no crea compras inferidas; una nueva recepción solo admite referencia confirmada en ledger local confiable.
6. ABIERTO: Undo de stock no equivale al Undo `shopping_need.*` de W04. Revisar evento compensatorio bajo versión con backup antes de A1C2 y proteger contra double undo.
7. ABIERTO: C2C1 9/9 fue Android API35 CI, no Moto G15 físico. G15 NOT_RUN, no bloquear cierre DEV acotado por ausencia de campaña no autorizada.
8. PASA: no es necesario introducir APIs pagas, redes, abrir finanzas ni activar pagos para registrar una compra manual DEV.

## Resultado operacional
No hay otro bloqueo documental para A1C0B1 codec una vez que su guard y READY exact4 estén verificados. El orden A1C0B2→A1C0B3→A1C0B4→A1C1→A1C2→A1C3→A1C4 es **plan, no permiso universal**. El agente Work prepara cada B1–B5/READY después de B7 y sigue programando sin detenerse por el cambio normal READY→BLOCKED. Si faltan pruebas o autorización, STOP únicamente ese alcance; documentar fail-closed, no degradar contrato.
