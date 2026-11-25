# FARO V5 · W05-A1C2G1A · B7 PASS acotado

Fecha: 2026-11-25. Repo `etacsantafe-source/FARO-V5`, rama `work/s3-night-20261102`.
**B7 PASS SCOPED; NO es cierre G1B ni W05-A1 global.**
SHA funcional exacto `b911e9bce5bd233073ae64dd271f1869a748a6d9`; parent READY `ce53c437863d6522884d4f3e398e97fbb9c51e3f`.
Unidad `S4_W05_A1C2G1A_STOCK_UNDO_ORIGIN_ADMISSION`, 3 rutas exactas y trailer FARO-UNIT. No reutiliza G0.

## B7 — Evidencia verificada

- Guardia del código SHA `b911e9bce5bd233073ae64dd271f1869a748a6d9`: [run 45642672642](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45642672642) **COMPLETED SUCCESS**. El preflight del workflow constató las tres rutas del parent READY.
- [CI 45642672627](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45642672627) **COMPLETED SUCCESS** en SHA exacto `b911e9bce5bd233073ae64dd271f1869a748a6d9`. Job `139305225788` SUCCESS. `:owner:household:test` **BUILD SUCCESSFUL en 1m54s** (log original inspeccionado).
- El job `Validate original strict JUnit XML` **SUCCESS**: `HouseholdStockUndoDevAdmissionTest` **10/10** y regresión `HouseholdStockReceiveDevAdmissionTest` **8/8**. XML originales con **18/18**, 0 failures, 0 errors, 0 skipped; suite lengths comprobadas en script CI.
- Artefacto publicado `w05-a1c2g1a-stock-undo-origin-xml`, ID `15037095155`, ZIP 1640 bytes, no expirado en consulta; paso Upload SUCCESS.
- Código: `HouseholdStockUndoDevAdmission` valida catálogo N-016 `undo-coordinator`, tipo `stock.receive`, fuente tipada COMMITTED **solo provista por host trusted**, bytes de payload original, distinción receipt físico `stock.receive.intentKey` versus receipt de Apply, principal/compra/item/cantidad/unidad, revisión, neto, tiempo y revalidación con segundo read, permiso fresco, foreground/epoch/pointer. Permite origen SQL12 con puntero distinto del SQL13 después del cutover (G1B debe validar su lineage real).
- Las 10 pruebas nuevas cubren origen correcto, catálogo incorrecto, fuente ausente o desparejada, payload falsificado, confusión de receipts, errores de stock, estado/compensación/revisión, revocación/ABA/foreground y cambios durante read, preview sin grant y redacción de datos sensibles.

## Alcance no certificado (debe continuar)

G1A **NO** demuestra por sí mismo autenticación COMMITTED de Workspace/Reality, ni journal de selección SQL13, ni contiene host Android, binding `undo.execute`, escritura de compensación, método `Apply`, consentimiento humano de UI o pruebas físicas. Sus puertos de origen y posición son inyecciones *trusted* que **G1B** debe implementar y revalidar.

Próxima unidad **W05-A1C2G1B**: host trusted para source COMMITTED original de N-035, sellado Workspace+Reality y lineage de cutover, Review→Confirm→Apply con nuevo receipt, idempotencia, recuperación de UNKNOWN, revalidación de N-016 y transacción única SQL13. Requiere B1–B5 y permiso independiente. Luego A1C3 Android y A1C4 aceptación integral.

V2 productiva, Apply productivo, stock real, pagos, Auto/Noche OFF; G15 NOT_RUN, main/V4.4 intactas. Writer liberar tras B7, permiso G1A consumido/BLOCKED.
