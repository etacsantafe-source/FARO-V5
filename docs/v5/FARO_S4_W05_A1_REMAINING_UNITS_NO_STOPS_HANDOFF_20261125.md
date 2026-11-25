# FARO V5 · W05-A1 RESTANTE · cadena continua de Work, sin rehacer certificaciones
Base auditada `6a18acc43ae5aa2f749735b0ca51ca7ef6b4aa64` (A1C0B0 B7 PASS scoped, 6/6). Fecha 2026-11-25. Este mapa NO confiere READY para unidades posteriores; la única unidad cuyo READY puede emitirse ahora es A1C0B1 codec v9. **Work debe preparar la unidad sucesora tan pronto cierre B7 de cada unidad, sin volver a detenerse simplemente porque el permiso anterior fue consumido.** El permiso BLOCKED posterior a B7 es EXPECTED, y la transición correcta es B1–B5 delta, nuevo READY y B6 dentro de la misma sesión si hay capacidad.

## Mapa de dependencias real

| Orden | Unidad | Reutilización y delta preciso | Puerta de B6 |
| --- | --- | --- | --- |
| Hecho | A1C0A | `stock.receive` grant contract DEV **inerte**, 8/8 B7 (sin fuente ACTIVE) | PASS no repetir |
| Hecho | A1C0B0 | Reality11→Reality12 STAGED schema para compras; XML 6/6, backup aún v8 | PASS no repetir |
| 1 | **A1C0B1** codec FBS2v9 | nuevo contenedor v9+GCM reutilizado, sin SQL | preauditoría `docs/v5/FARO_S4_W05_A1C0B1_FBS2V9_CODEC_B1_B5_20261125.md`, permiso exact4, B6/B7 |
| 2 | **A1C0B2** SQL12 *capture/restore* STAGED | capturar compras+recibos en snapshot consistente con 5 stores, integridad SQL12, restaurar STAGED vacío o idéntico; preservar v8/SQL11 sin fallback silencioso | Tras B1 B7, delta B1–B5, rutas exactas y READY propio |
| 3 | **A1C0B3** DEV *admission/cutover/reopen* | journal/restore+cutover autorizado 11→12, trusted source purchase, no LIVE write aún; FULL pointer/ABA, SQLite WAL, generación y Evidence2, conserva plan/finanzas/recibos | Tras B2 B7 y preaudit, READY nuevo |
| 4 | **A1C0B4** compra manual DEV | Review→Approve→Apply de hecho comprado con consent foreground + `MANUAL_EXPLICIT`, dedup/ref+revision y receipt; trusted read source para A1C0A. **No usar carrito/budget ni pago** | Tras B3 PASS; credenciales de grant efímeras y scoped |
| 5 | **A1C1** recepción | grant `stock.receive` independiente N-035, payload Review, SQLite revision CAS+generation pointer, comprobar unidad/total comprado, receipt+idempotency, parciales 10→7→3 | C0B4 B7, runtime purchase admitted, storage/recovery verificados |
| 6 | **A1C2** Undo/recovery | crash PREPARED/COMMITTED/UNKNOWN, buscar receipt antes de repetir, Undo compensatorio append-only, controles ABA/stale, FBS2v9 post-receive | C1 B7 |
| 7 | **A1C3** Android DEV | flujo UI chat-first simple de compra confirmada, recepción física, cantidad pendiente, historial y Undo; Android API35, dispositivo G15 físico NOT_RUN salvo autorización | C2 B7 |
| 8 | **A1C4** A1 DEV acceptance | regresiones B0→B4 / C1–C3 + C2C0 (26/26), C2C1 (9/9), restore/cutover v9/SQL12, coherencia de 5 stores, CI/XML por SHA y guard; cierre DEV solo con todas PASS | C3 B7 |

## Estudio adelantado de unidades futuras — observaciones + pruebas y rutas CANDIDATAS, NO listas READY

### A1C0B2: transacción de captura y restore, alcance candidato
- Reusar `FaroV5Household11V8FullStageReplay.kt`, `FaroV5Household11ReceiptSql11Port.kt`, `RealityHousehold11ReceiptRowsPort.kt` y `FaroV5FiveStoreBackupCapture.kt` **sin ampliar el lector v8 ni usar restore STAGED como ACTIVE writer**. Adaptador SQL12 propio que valida `PRAGMA user_version=12`, tabla `household_confirmed_purchase` exacta y H11R, integridad, límites, principal+provenance. Capturar ambas tablas de una misma vista/lock (NO dos snapshots independientes).
- Candidatos nuevos: `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household12PurchaseSql12Port.kt`, `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household12V9StagedReplay.kt`, tests homólogos y workflow dirigido. Antes de congelar, comparar dependencias *reales* de código B1 y verificar si se requiere port `:store:reality` o stage migration. **Si el owner del grafo necesita más rutas, verificar primero y emitir READY acotado; no ampliar el B1 permiso**.
- B5: v9 10→recibos 7+3 captura/restauración exacta, v8→migración compras vacías, duplicado/replay idéntico, mismatch del target, rollback atómico, fila alterada, XML SQLite real, otros stores byte-identical, no B7 de CI anterior reemplazado.

### A1C0B3: cutover seguro a Reality12
- Reutilizar solo patrones certificados `FaroV5Household11Evidence2DevJournaledCutover.kt` y `FaroV5Household11Evidence2ActiveDevReadHost.kt` en nuevos hosts puntuales SQL12. No auto-switch desde VALIDATED. Revalidar ticket/archivo FBS2v9 autenticado, cinco stores, WAL y full pointer, commit switch autorizado con journal/recovery; rollback cuando falla salud; host cerrado/foreground revocado STOP.
- B5: cutover duplicado, crash entre journal/prepared/committed, fuente v8/v9 incompatible, ticket legado, ABA, recuperación/rollback, todos con XML de pruebas reales. No habilitar productivo.

### A1C0B4: compra **manual confirmada** como fuente durable
- El cuadro `household_confirmed_purchase` SQL12 es **esquema solamente**, no existe ledger ACTIVE ni host confiable certificado. Diseñar autoridad propia `household purchase.confirm` solo si catálogo lo permite, sin inventar contrato: auditar catálogo180; si no existe operación aprobada, preparar solicitud independiente de alcance/autoridad antes de registrar en Apply. `stock.receive` N-035 **NO** autoriza confirmar compras por sí misma.
- Revisar si el esquema `purchase_ref` PK y `revision` por fila cubre dedup/replay bajo un recibo Apply, y si hace falta estado global CAS. **No añadir columnas/tablas a SQL12 ACTIVE a espaldas de backup/versionado; migrar a SQL13 si resulta imprescindible**. Fijar purchaseRef/item/quantity/unit/manualConfirmedBy/at en payload inmutable y comprobar principal/epoch/puntero exacto al Apply. Cambiar mismo purchaseRef con distinta cantidad -> STOP.
- B5: compra10 confirmada explícita persistida, reinicio+backup v9, replay mismo intent 1 efecto, stale principal/epoch/revision STOP, carrito/price/W04 no crean compras. Ninguna nueva API paga ni pagos.

### A1C1: recepción independiente
- Catalog `stock.receive` N-035 BINDABLE **no es executable sin binding y grant propios**. A1C0A solo prueba port inerte. El impl SQL12/SQL13 usa `household_stock_receipt_state` revisión y `intent_key UNIQUE`, lock único, source de compra confirmada, CAS por lectura de consumo; no escribir receipts si no hay compra confirmada. El `Undo` de necesidades W04 no autoriza Undo stock.
- B5: compra10/recibe7 = 3 pendientes, receive3 =0, overreceive denegada, no unit match denegada, duplicate intent un efecto, 2 recepciones concurrentes con CAS, crash antes/después de receipt, backup correcto, permisos revocados/ABA STOP. No pagos ni APPLY productivo.

### A1C2: recuperación / Undo
- No borrar ni editar evento de recepción tras revertir: movimiento compensatorio identificable con parent-receipt e intent propio, invariantes de saldo. Si SQLite12 no contiene soporte de evento compensatorio, plan de migración versionada y backup antes de escribir; nunca una fake Undo que borre historia.
- B5: compensación única y sin saldo negativo; doble Undo bloqueado; receipt UNKNOWN reconciliado sin segundo Apply; recovery PREPARED/COMMITTED; WAL+restart; tamper/ABA/invalid restored provenance fail-closed.

### A1C3 Android / A1C4 aceptación
- En CORE DEV aislada, chat-first, Review visible, selección de compra confirmada real, cantidad física, pendientes, history, Undo y fuente/fecha; UX V4.4 como referencia de simplicidad no permiso.
- API35 y JVM por SHA con XML originales, D8 heap/configurar una sola ejecución si hay OOM real, guard; physical G15 `NOT_RUN` sin campaña autorizada. A1C4 lista de invariantes: no doble recibo, 5 stores, Evidence separado, rollback, sin productiva/pagos/Auto/Noche. No declarar global cerrado por aprobar una sola suite.

## Bloqueadores previos a Work que podemos anticipar
1. **Versión**: FBS2v9 v8 exacto; Reality12 staged B0 y v9 codec B1 no autorizan abrir ACTIVE SQL12. Evitar writer prematuro.
2. **Semántica**: recibos legacy sin purchaseRef real pueden existir; **permitir como legacy read-only** y no inferir compras. Nuevos recibos requieren compra confirmada.
3. **Auth**: `MANUAL_EXPLICIT` restaurado es procedencia auditable, NO grant de usuario. La autenticación de archive no autoriza Apply.
4. **Esquema**: tabla compra no contiene `intent_key` ni singleton revision; evaluar dedup de Apply receipt y CAS antes de usarla. Si falta, nueva migración con respaldo, no corrupción de SQL12.
5. **Contratos**: confirmar contrato aprobable para `purchase.confirm` antes del alta manual; no inventar operaciones de catálogo.
6. **Writer y permisos**: después de cada B7 consumir READY -> BLOCKED es normal. Work puede preparar siguiente B1–B5 y READY documental, sin que el usuario tenga que intervenir, si las dependencias lo permiten. CAS y no escribir funcional sin permiso.
7. **CI**: C2C1 9/9, C2C0 26/26, C0B0 6/6 comprobados; no reejecutar regresiones generales en cada unidad sin motivo, solo al cierre integral. Work debe inspeccionar XML original, no guiarse por status verde aislado.
8. **Sistema**: no añadir librerías, licencias GPL, APIs pagas, Internet ni plugin; v2 productiva, stock real, pagos, Apply productivo, Auto/Noche OFF.
