# FARO V5 — W03-D2B2B3A · B7 del preflight ACTIVE Auxiliary4 DEV

Unidad S4_W03_D2B2B3A_ACTIVE_AUX4_PREFLIGHT_DEV. Código 0f86b186e99f4effdb2bb733f57d3b034078fff7. Scope: abrir grafo exacto en DEV. W03 abierto.

## B1–B5
docs/v5/FARO_S4_W03_D2B2B3A_ACTIVE_AUX4_PREFLIGHT_B1_B5_20261119.md y docs/v5/FARO_S4_W03_D2B2B3A_PREFLIGHT_CI_ADDENDUM_20261119.md, guardias 44606027445 y 44606428390 SUCCESS. READY final e7f41890cae5bf0080dcebe6afbb88e0737d9aa7, guardia 44606685296 y preflight cinco rutas SUCCESS.

## B6
Un único grafo Finance9 + Auxiliary4, pin de secuencia+generación, preflight WAL-aware, handle preexistente sin CREATE y schema/PK/FK exactos antes de configuración. ACTIVE4 no ejecuta migración ni el backup aislado. Receipt CAS usa material gate y fence. La ruta ACTIVE3 previa conserva su admisión exacta. Workflow de backup anterior restringido a su unidad y quedó SKIPPED 44607262996; guardia global sigue activa.

Run 44607263020, job 135854163540 SUCCESS, guardia de SHA 44607263280 SUCCESS. Artefacto 14571310744, ZIP sha256:fda15ee870287667f85052d6320242484f5c93a9eb68831e63bc0de05db9d98e verificado; XML 5/5, 0 fallas, errores u omisiones. Negativas con Aux3, ledger ausente, versión futura, ABA; exacto ACTIVE4 reabre y replay de recibo conserva una única revisión. Los casos operan sobre directorios SQLite de test JVM.

## B7
PASS acotado de admisión y recibos ACTIVE4 DEV. READY consumido. Guardia del documento y writer RELEASED requeridos para checkpoint.
Siguiente unidad B3B: docs/v5/FARO_S4_W03_D2B2B3B_AUX4_DURABLE_CUTOVER_B1_B5_20261119.md, switch exclusivo y recuperación/rollback antes del host. Luego conciliación y recibos en Avisos, B7 D, E Android y G. V2 productiva OFF y F opcional diferida, G15 NOT_RUN.
