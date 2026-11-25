# FARO V5 · W05-A1 — auditorías anticipadas **CONDICIONALES** de unidades siguientes

Fecha 2026-11-25. Baseline comprobada `d28bfae7199cedce988ae2e7a0376ea6e09b7191`, último código funcional certificado A1C0B0 `d2f0a7a60be0ddf1cce9c02c80879ef0f79d5b27`. A1C0B1 es la **única unidad** con permiso READY hoy y B6 pendiente.

**IMPORTANTE:** Documento de preparación adelantada, no evidencia suficiente para permiso funcional de esta unidad. B1–B3 respaldados por código actual; B4 son rutas candidatas a congelar después del B7 predecesor, B5 matriz adversarial de futuros tests, NO resultados ejecutados. Tras B7 previo Work deberá (a) revalidar HEAD, fuentes y dependencias; (b) reconciliar el delta; (c) congelar rutas exactas reales; (d) completar auditoría B5 final y guard documental; (e) emitir READY propio y recién después B6. No usar este documento como permiso ni citarlo como B7.

## Unidad y precondición
**A1C0B2: Captura y restauración consistente de compras/recibos Reality12.**
Dependencia: A1C0B1 FBS2v9 codec + FAROBAK2 B7 PASS; A1C0B0 SQL12 STAGED B7 PASS.

## B1 — Evidencia disponible y alcance
- RealityHousehold12PurchaseSchema.kt instala únicamente la tabla STRICT household_confirmed_purchase en copia STAGED, sin writer ACTIVE; A1C0B0 B7 SQL6/6, código d2f0a7a60be0ddf1cce9c02c80879ef0f79d5b27.
- El lector RealityHousehold11ReceiptRowsPort y el codec H11R son v11 exactos: NO reinterpretar SQL12 como SQL11. El nuevo codec FBS2v9 debe aprobar B7 antes de usarlo en captura o restore.
- El módulo :store:generation depende de :store:reality, pero SQLite AndroidX driver está implementado en Reality y no como dependencia de producción de generation. Por tanto, lectura/carga SQL12 vive en Reality; generation orquesta lease, pin, snapshot y replay.

## B2 — Comparación V4.4 y reutilización real V5
- Reusar patrón de FaroV5Household11ReceiptSql11Port, RealityHousehold11ReceiptRowsPort, FaroV5FiveStoreBackupCapture y FaroV5Household11V8FullStageReplay (NO editar v8).
- Cinco stores y Evidence se preservan byte/semánticamente; compras y recibos se capturan del mismo archivo SQLite12 en una sola vista transaccional. Las filas legacy sin compra confirmada NO crean una compra nueva.
Referencia histórica obligatoria: `docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md` y `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`; no copiar V4.4 ni inferir implementación desde documentos.

## B3 — Alternativas y decisión provisional
- A: puerto Reality12 de snapshot comprado+recibido, validación exacta de esquema, SQL snapshot read-only o transacción consistente y restauración exclusivamente de STAGED vacío/idéntico; generación solo coordina identidad, lease y pin.
- B DESCARTADA: hacer dos lecturas separadas de compras y recibos sin snapshot coherente, exponiendo overreceipt invisible o entradas fantasma.
- C DESCARTADA: cambiar RealityHousehold11ReceiptRowsPort para aceptar SQL12; rompería un contrato v8 certificado.
- D LIMITACIÓN: no puede promover una etapa, abrir ACTIVE ni emitir grant. Si se requiere separar SQL capture de restore por alcance, subdividir esta unidad y emitir permisos propios en orden.
Revisar referencias técnicas ya citadas por los handoffs previos: SQLite transactions, WAL y versionado; Android testing cuando corresponda. No traer nuevas dependencias ni APIs pagas.

## B4 — Rutas CANDIDATAS: todavía NO exactas ni autorizadas
- v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityHousehold12PurchaseRowsPort.kt (nuevo, candidato)
- v5/store/reality/src/test/kotlin/ar/com/faro/v5/store/reality/RealityHousehold12PurchaseRowsPortTest.kt (nuevo, candidato)
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household12PurchaseSql12Port.kt (nuevo, candidato)
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household12V9StagedReplay.kt (nuevo, candidato)
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household12V9StagedReplayTest.kt (nuevo, candidato)
- .github/workflows/faro-v5-s4-w05-a1c0b2-sql12-capture-restore.yml (nuevo, candidato)
**La lista B4 es un mapa de investigación, NO permiso funcional; puede cambiar según el código B7 previo.** Antes de B6, decidir el mínimo conjunto exacto sin incluir más rutas de las necesarias, fijar unit_id propio, evidencia SHA y parent-preflight.

## B5 — Matriz adversarial propuesta; pruebas todavía NO ejecutadas
- T01: compra 10 y receipt 7+3 capturados en única vista; roundtrip FBS2v9 y relectura SQL12 exacta
- T02: stage v8 con recibos históricos y compras vacío se migra a SQL12 sin inferir compras
- T03: restauración hacia etapa no vacía/divergente -> fail-closed sin mutación
- T04: error antes de COMMIT -> rollback completo de compras, recibos y revisión
- T05: fecha, unidad, artículo, referencia, cantidad, checksum o schema12 falsos -> rechazo
- T06: lease revocado, puntero ABA, etapa con enlaces/sidecars o no canónica -> rechazo
- T07: cinco stores y Evidence antes/después equivalentes; no writer ACTIVE
- T08: captura/replay repetido idéntico sin dobles filas ni pérdida de receipts
Todas las pruebas finales deben ejecutarse con JUnit XML original, 0 failures/errors/skips y guard por SHA; si B7 falla, RED_FIX acotado con permiso actualizado.

## Condición de cierre de la preparación y siguiente paso
Verificar B7 efectivo de FBS2v9; cotejar tipos y firma reales, ordenar scope exacto Reality vs generation, confirmar reuso FBS2v9, decidir si subdividir B2 por cantidad de rutas. Preparar nuevo READY propio y guard. NO READY a día de hoy.

## Exclusiones globales
Un solo writer CAS; no main ni V4.4, no autorización de stock productivo, Apply productivo, V2 productiva, pagos o Auto/Noche; no inferir compra desde carrito/presupuesto/backup. Usuario no autorizó ensayo físico Moto G15. **Status: PREAUDITED_CONDITIONAL — NOT_READY / B6_NOT_AUTHORIZED / B7_NOT_RUN.**
