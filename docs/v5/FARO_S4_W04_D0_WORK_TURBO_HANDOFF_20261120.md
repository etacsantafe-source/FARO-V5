# FARO V5 — W04-D0 · Work Turbo minimum-budget programming handoff

Fecha 2026-11-20. Objetivo mínimo: **cerrar UN solo bloque D0 por B6–B7**, sin explorar W4-D1/D2/D3 ni recertificar W1–W3/W4-A/B0/B1/C. Work usage ~11% restante según usuario, no hay garantía de duración/costo por tarea.

Referencia B1–B5: `docs/v5/FARO_S4_W04_D0_CART_DRAFT_B1_B5_20261120.md`, blob a verificar con SHA real de Git; permiso `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` unidad `S4_W04_D0_CART_DRAFT_DEV`, exact3, solo si READY y guardía SUCCESS en SHA actual. Al arrancar revalidar AGENTS.md, puntero, HEAD, writer remoto, permiso y guard. No asumir que el HEAD acá citado es el último si hubo nuevas escrituras.

**B6 único:**
1. Implementar un borrador efímero de carrito en Kotlin app puro usando C0A candidato ya seleccionado, `S4W04CommercialNeedProductSelection.revalidate`, `HouseholdNeedsSnapshot` y `CommercialNeedReadFence`. No nuevas interfaces ni cambios de clase anterior.
2. Tipo salida: `cartDraft` immutable, lista canonizada de líneas (NeedId, NeedRevision, NeedName, Need quantity thousandths/unit, productKey, packCount 1..10000), global revision/fence y fingerprint `CanonicalHashBuilder`. Nunca tratar packs como conversión de kg o costo, nunca declarar fuente/stock/moneda/precio; ningún cálculo D2.
3. Entrada 1..200 líneas; rechazo total por grant falso, need stale/completed/deleted, invalid pack, duplicate need, mismatch fence; revalidar draft con snapshot/fence/grants. Datos no se persisten; nada de SQL/Apply/Finance/Evidence writes/autoridad productiva.
4. Tests JUnit5 exactamente 12 delta: matriz B5 del documento; comprobar fingerprint determinístico/reorden, no mutation; `:app:testDebugUnitTest --tests ar.com.faro.v5.app.S4W04CommercialCartDraftTest`; workflow Java21/Android SDK37, XML exacto sin errores/skip, artefacto.
5. Workflow con **tres rutas exactas**; preflight de git diff por cada ruta contra parent READY, `test "$count" -eq 3`, marker `FARO-UNIT:S4_W04_D0_CART_DRAFT_DEV`. GitHub Actions guard final y CI directed; no reejecutar pruebas certificadas antiguas manualmente.
6. B7 independiente: verificar resultado real de CI/guard y XML, corregir sólo si RED_FIX con B7 previo. Publicar B7 + consumir permiso a BLOCKED y writer RELEASED en checkpoint. V2 prod OFF, G15 NOT_RUN.

**Prioridad de créditos:** detener tras D0 B7 y entregar checkpoint; NO empezar D1 o saltar a Android. Si presupuesto Work se agota, dejar el writer RELEASED y checkpoint honesto para terminar manualmente, jamás declarar B7 sin pruebas.
