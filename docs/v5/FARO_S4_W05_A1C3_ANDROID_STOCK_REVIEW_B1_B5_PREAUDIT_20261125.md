# FARO V5 · W05-A1 — auditorías anticipadas **CONDICIONALES** de unidades siguientes

Fecha 2026-11-25. Baseline comprobada `d28bfae7199cedce988ae2e7a0376ea6e09b7191`, último código funcional certificado A1C0B0 `d2f0a7a60be0ddf1cce9c02c80879ef0f79d5b27`. A1C0B1 es la **única unidad** con permiso READY hoy y B6 pendiente.

**IMPORTANTE:** Documento de preparación adelantada, no evidencia suficiente para permiso funcional de esta unidad. B1–B3 respaldados por código actual; B4 son rutas candidatas a congelar después del B7 predecesor, B5 matriz adversarial de futuros tests, NO resultados ejecutados. Tras B7 previo Work deberá (a) revalidar HEAD, fuentes y dependencias; (b) reconciliar el delta; (c) congelar rutas exactas reales; (d) completar auditoría B5 final y guard documental; (e) emitir READY propio y recién después B6. No usar este documento como permiso ni citarlo como B7.

## Unidad y precondición
**A1C3: Interfaz Android aislada de compra y recepción.**
Dependencia: A1C0B4 compra, A1C1 recepción y A1C2 Undo/recovery B7.

## B1 — Evidencia disponible y alcance
- El diseño UX de V5 aprobado es chat-first, simple, accesible y con iconos; Android de C2C1 Evidence2 SQL11 B7 9/9 es scoped API35, no prueba física.
- Las variantes aisladas del app incluyen FaroV5V2CoreDevActivity y host DEV; ninguna instalación productiva de V2 o stock real está autorizada.
- El panel debe mostrar compras manuales confirmadas de un owner REAL, recibidas y pendientes, revisión exacta y Undo informado; no inventar montos desde Evidence.

## B2 — Comparación V4.4 y reutilización real V5
- Reusar las vistas y puentes actuales de revisión/aprobación, patrón S4W04HouseholdNeedApplyHost y Android source set v2CoreDev, no ampliar w04CommercialDev ni app productiva.
- Usar pruebas JVM y Android API35 y semántica TalkBack/font 200% cuando sea pertinente.
Referencia histórica obligatoria: `docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md` y `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`; no copiar V4.4 ni inferir implementación desde documentos.

## B3 — Alternativas y decisión provisional
- A: UI DEV mínima de selección de compra confirmada, cantidad recibida, pantalla de revisión, confirmación foreground, historial, pendientes y Undo; autoridad sigue en owner.
- B DESCARTADA: botón que llama directamente a DAO o salta Review/Approve.
- C DESCARTADA: mostrar un carrito/estimación como comprado, o app que autoacepta.
- D G5: ajustar heap D8 solo si OOM real dirigido, sin contaminar workflows no relacionados; verificar package y alcance físico separado.
Revisar referencias técnicas ya citadas por los handoffs previos: SQLite transactions, WAL y versionado; Android testing cuando corresponda. No traer nuevas dependencias ni APIs pagas.

## B4 — Rutas CANDIDATAS: todavía NO exactas ni autorizadas
- v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5W05StockReceiveDevPanel.kt (nuevo, candidato)
- v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevActivity.kt (existente, requiere scope)
- v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5W05StockReceiveDevPanelTest.kt (nuevo, candidato)
- v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5W05StockReceiveAndroidTest.kt (nuevo, candidato)
- .github/workflows/faro-v5-s4-w05-a1c3-stock-android-dev.yml (nuevo, candidato)
**La lista B4 es un mapa de investigación, NO permiso funcional; puede cambiar según el código B7 previo.** Antes de B6, decidir el mínimo conjunto exacto sin incluir más rutas de las necesarias, fijar unit_id propio, evidencia SHA y parent-preflight.

## B5 — Matriz adversarial propuesta; pruebas todavía NO ejecutadas
- T01: compra confirmada 10 visible sin inferencia de precio o pago
- T02: revisar 7, confirmar explícito, mostrar saldo +7 y pendiente3
- T03: Cancelar revisión o salir a background -> 0 mutaciones
- T04: selección stale, revision distinta o pointer ABA -> negar confirmación
- T05: doble toque/reintento -> un receipt
- T06: historial, Undo y refresh tras restart coherentes
- T07: semántica accesible, TalkBack/200% no pierde confirmación/estado
- T08: Android API35 XML real, distinguir emulator vs dispositivo Moto G15 NOT_RUN
Todas las pruebas finales deben ejecutarse con JUnit XML original, 0 failures/errors/skips y guard por SHA; si B7 falla, RED_FIX acotado con permiso actualizado.

## Condición de cierre de la preparación y siguiente paso
Primero integrar HOSTS de A1C0B4/C1/C2 probados. Congelar rutas tras comprobar actividad y variant source sets reales; no instalar en G15 sin campaña autorizada.

## Exclusiones globales
Un solo writer CAS; no main ni V4.4, no autorización de stock productivo, Apply productivo, V2 productiva, pagos o Auto/Noche; no inferir compra desde carrito/presupuesto/backup. Usuario no autorizó ensayo físico Moto G15. **Status: PREAUDITED_CONDITIONAL — NOT_READY / B6_NOT_AUTHORIZED / B7_NOT_RUN.**
