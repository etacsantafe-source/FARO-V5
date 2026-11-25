# FARO V5 · W05-A1 — auditorías anticipadas **CONDICIONALES** de unidades siguientes

Fecha 2026-11-25. Baseline comprobada `d28bfae7199cedce988ae2e7a0376ea6e09b7191`, último código funcional certificado A1C0B0 `d2f0a7a60be0ddf1cce9c02c80879ef0f79d5b27`. A1C0B1 es la **única unidad** con permiso READY hoy y B6 pendiente.

**IMPORTANTE:** Documento de preparación adelantada, no evidencia suficiente para permiso funcional de esta unidad. B1–B3 respaldados por código actual; B4 son rutas candidatas a congelar después del B7 predecesor, B5 matriz adversarial de futuros tests, NO resultados ejecutados. Tras B7 previo Work deberá (a) revalidar HEAD, fuentes y dependencias; (b) reconciliar el delta; (c) congelar rutas exactas reales; (d) completar auditoría B5 final y guard documental; (e) emitir READY propio y recién después B6. No usar este documento como permiso ni citarlo como B7.

## Unidad y precondición
**A1C0B3: Cutover y reapertura aislada SQL12 DEV.**
Dependencia: A1C0B2 captura/restore STAGED B7; C2C0/C2C1 Evidence2 SQL11 journal/ACTIVE B7 históricos.

## B1 — Evidencia disponible y alcance
- El cutover Evidence2 SQL11 certificado usa journal, ticket v2, fuente autenticada, validación de cinco stores, pointer y recuperación; no se transfiere automáticamente a SQL12.
- Reality12 STAGED existe solo como migración y payload; sin mecanismo de activación ni host ACTIVE SQL12 probado.
- Propósito acotado: poder validar y promover una copia SQL12 DEV únicamente con autorización nueva, cierre y recuperación; nunca activar stock real.

## B2 — Comparación V4.4 y reutilización real V5
- Reusar FaroV5Household11Evidence2DevJournaledCutover, FaroV5Household11Evidence2ActiveDevReadHost, FileActiveGenerationPointer, GenerationSwitchCoordinator, FileBootstrapJournal, FileGenerationReplayFence, ValidatedGenerationActivationCoordinator y Root/quiescence gate.
- Preservar rutas históricas SQL11/Evidence1, compatibilidad v8, startup aislado Android y revisión exacta full pointer.
Referencia histórica obligatoria: `docs/v5/FARO_S4_P02_V44_HISTORICAL_AUDIT_MATRIX_20261113.md` y `docs/v5/FARO_S4_P03_V44_TO_V5_REUSE_DELTA_MATRIX_20261113.md`; no copiar V4.4 ni inferir implementación desde documentos.

## B3 — Alternativas y decisión provisional
- A: nuevo admission/ticket SQL12 ligado a FBS2v9, stage schema12+Evidence2 y full generation, journaled cutover explícito y host read-only WAL exacto; no switch desde VALIDATED no autorizado.
- B DESCARTADA: adaptar magic/ticket v11 a SQL12 o aceptar wildcard de versión; rompe aislamiento.
- C DESCARTADA: abrir SQL12 ACTIVE mutable a través del host de lectura. La apertura de writer requiere permisos A1C0B4/A1C1 posteriores.
- D: si falta soporte de grafo para schema12, identificar modificación adicional y auditar rutas antes de READY; no extender por analogía un permiso consumido.
Revisar referencias técnicas ya citadas por los handoffs previos: SQLite transactions, WAL y versionado; Android testing cuando corresponda. No traer nuevas dependencias ni APIs pagas.

## B4 — Rutas CANDIDATAS: todavía NO exactas ni autorizadas
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household12DevCutoverAuthorization.kt (nuevo, candidato)
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household12Evidence2DevJournaledCutover.kt (nuevo, candidato)
- v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household12ActiveDevReadHost.kt (nuevo, candidato)
- v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household12Evidence2CutoverTest.kt (nuevo, candidato)
- v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5W05Household12DevStartup.kt (nuevo, candidato; opcional según prueba)
- .github/workflows/faro-v5-s4-w05-a1c0b3-sql12-dev-cutover.yml (nuevo, candidato)
**La lista B4 es un mapa de investigación, NO permiso funcional; puede cambiar según el código B7 previo.** Antes de B6, decidir el mínimo conjunto exacto sin incluir más rutas de las necesarias, fijar unit_id propio, evidencia SHA y parent-preflight.

## B5 — Matriz adversarial propuesta; pruebas todavía NO ejecutadas
- T01: VALIDATED no ejecuta auto-switch; ticket autenticado autoriza una sola promoción exacta
- T02: PREPARED/COMMITTED journal recuperable sin doble swap y sin fuga de escritura
- T03: pointer ABA (id igual/sequence distinta) -> rechazo; lease revocado -> rechazo
- T04: ticket legacy v8/v11 o Evidence1 bajo Evidence2 -> rechazo
- T05: SQL12 schema/version/purchase ledger/receipts divergentes -> rollback antes de exposición
- T06: crash entre commit y pointer -> recovery/rollback determinista
- T07: reabrir ACTIVE WAL dos veces en read-only y preservar otros cuatro stores
- T08: ningún grant stock o compra se habilita por cutover
Todas las pruebas finales deben ejecutarse con JUnit XML original, 0 failures/errors/skips y guard por SHA; si B7 falla, RED_FIX acotado con permiso actualizado.

## Condición de cierre de la preparación y siguiente paso
C2B2 B7 obligatorio. Revalidar contrato de grafo de almacenamiento y manifest/startup Android antes de fijar paths; permiso independiente para cada funcionalidad que realmente toque Active; G15 no ejecutado.

## Exclusiones globales
Un solo writer CAS; no main ni V4.4, no autorización de stock productivo, Apply productivo, V2 productiva, pagos o Auto/Noche; no inferir compra desde carrito/presupuesto/backup. Usuario no autorizó ensayo físico Moto G15. **Status: PREAUDITED_CONDITIONAL — NOT_READY / B6_NOT_AUTHORIZED / B7_NOT_RUN.**
