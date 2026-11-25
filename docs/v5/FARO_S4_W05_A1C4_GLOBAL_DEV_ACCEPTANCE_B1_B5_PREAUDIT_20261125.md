# FARO V5 · W05-A1 — auditorías anticipadas **CONDICIONALES** de unidades siguientes

Fecha 2026-11-25. Baseline comprobada `d28bfae7199cedce988ae2e7a0376ea6e09b7191`, último código funcional certificado A1C0B0 `d2f0a7a60be0ddf1cce9c02c80879ef0f79d5b27`. A1C0B1 es la **única unidad** con permiso READY hoy y B6 pendiente.

**IMPORTANTE:** Documento de preparación adelantada, no evidencia suficiente para permiso funcional de esta unidad. B1–B3 respaldados por código actual; B4 son rutas candidatas a congelar después del B7 predecesor, B5 matriz adversarial de futuros tests, NO resultados ejecutados. Tras B7 previo Work deberá (a) revalidar HEAD, fuentes y dependencias; (b) reconciliar el delta; (c) congelar rutas exactas reales; (d) completar auditoría B5 final y guard documental; (e) emitir READY propio y recién después B6. No usar este documento como permiso ni citarlo como B7.

## Unidad y precondición
**A1C4: Certificación integral W05-A1 en DEV aislada.**
Dependencia: A1C0B1–B4, A1C1, A1C2 y A1C3 B7; A1C0A/A1C0B0/C2C0/C2C1 ya certificados.

## B1 — Evidencia disponible y alcance
- El cierre global A1 está OPEN: pasar B7 de subunidades no equivale al cierre DEV de experiencia completa.
- Regresiones históricas observadas: C2C0 XML26/26, C2C1 XML9/9, C0A XML8/8, C0B0 XML6/6; conservar sus artefactos y SHA en el ledger de aceptación.
- Necesitamos pruebas de integración real de compras confirmadas, recepción, saldo, restore/cutover, Undo/recovery y UI Android, sin activar producción.

## B2 — Comparación V4.4 y reutilización real V5
- Reusar suites dirigidas existentes por SHA y crear matriz de cobertura que cite cada XML/artefacto y ledger de permisos sin falsear presencia física.
- Aplicar política UI simple + Evidence≠Reality, writer único, AtomicApply, stock y pago OFF productivo.
Referencia histórica obligatoria: `docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md` y `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`; no copiar V4.4 ni inferir implementación desde documentos.

## B3 — Alternativas y decisión provisional
- A: aceptación multicapa JVM/SQLite/API35 (cuando corresponda) sobre DEV activa con datos controlados, verificar todas las transiciones y roles; B7 independiente de unidad y checkpoint global.
- B DESCARTADA: declarar A1 DONE por green de codec, CI de plugin o por emulador sin pruebas del host integrado.
- C: G15 real es campaña separada y puede constar como pendiente si no es condición explícita de cierre DEV; no inferir aprobación productiva.
- D: si se descubre una regresión de una subunidad, abrir RED_FIX limitado y recertificar antes de A1 global.
Revisar referencias técnicas ya citadas por los handoffs previos: SQLite transactions, WAL y versionado; Android testing cuando corresponda. No traer nuevas dependencias ni APIs pagas.

## B4 — Rutas CANDIDATAS: todavía NO exactas ni autorizadas
- docs/v5/FARO_S4_W05_A1_GLOBAL_DEV_B7_ACCEPTANCE_20261125.md (nuevo, candidato solo al final)
- v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5W05A1DevAcceptanceTest.kt (nuevo, candidato)
- v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5W05A1DevAcceptanceAndroidTest.kt (nuevo, candidato)
- .github/workflows/faro-v5-s4-w05-a1c4-global-dev-acceptance.yml (nuevo, candidato)
**La lista B4 es un mapa de investigación, NO permiso funcional; puede cambiar según el código B7 previo.** Antes de B6, decidir el mínimo conjunto exacto sin incluir más rutas de las necesarias, fijar unit_id propio, evidencia SHA y parent-preflight.

## B5 — Matriz adversarial propuesta; pruebas todavía NO ejecutadas
- T01: compra10 confirmada idempotente y recibo durables
- T02: recepción7 luego3, stock y pendientes exactos
- T03: Undo compensatorio autorizado, historial coherente
- T04: restart y recuperación PREPARED/COMMITTED/UNKNOWN sin duplicación
- T05: FBS2v9+Reality12 (o versión aprobada) backup/restore coherente de cinco stores
- T06: legacy v8+H11R no crea compras; Evidence2 authenticado y permisos revocados
- T07: Android API35 UI sin Apply productivo ni pagos; accesibilidad
- T08: callbacks de fallos rollback, ABA, grants y scopes negativos
- T09: todos los B7 y guardias por SHA/artefactos originales + writer RELEASED
- T10: G15 físico NOT_RUN salvo campaña explícita, V2 productiva/stock real/pagos/Auto/Noche OFF
Todas las pruebas finales deben ejecutarse con JUnit XML original, 0 failures/errors/skips y guard por SHA; si B7 falla, RED_FIX acotado con permiso actualizado.

## Condición de cierre de la preparación y siguiente paso
Solo después de B7 de todas las subunidades. Capturar evidencia real final; jamás inventar PASS o autorizar producción por un gate global.

## Exclusiones globales
Un solo writer CAS; no main ni V4.4, no autorización de stock productivo, Apply productivo, V2 productiva, pagos o Auto/Noche; no inferir compra desde carrito/presupuesto/backup. Usuario no autorizó ensayo físico Moto G15. **Status: PREAUDITED_CONDITIONAL — NOT_READY / B6_NOT_AUTHORIZED / B7_NOT_RUN.**
