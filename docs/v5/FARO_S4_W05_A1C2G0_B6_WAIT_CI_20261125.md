# FARO V5 · W05-A1C2G0 · B6 COMMITTED, CI pendiente

Fecha 2026-11-25. Unidad `S4_W05_A1C2G0_SQL13_UNDO_DEV_WRITE_ADMISSION`.
Código funcional `8056a65959647a4ae58b0616d5c1f198ec1c84bc`. Padre con permiso READY exact5 `064da0314a9f6b202330168e4bc3c4ce9f3e3680`.
**Estado documental:** B6_COMMITTED / CI_WAIT / B7_NOT_VERIFIED. Nunca confundir QUEUED con PASS.

## B6 — alcance realmente publicado
- Cinco rutas exactas autorizadas: `RealityHousehold13CompensationApplyPort.kt`, `RealityStore.kt`, `FaroV5GenerationStoreGraph.kt`, `RealityHousehold13CompensationApplyPortTest.kt`, workflow `faro-v5-s4-w05-a1c2g0-sql13-undo-write-admission.yml`.
- Nuevo puerto SQL13 DEV que revalida receipt físico original, compra confirmada manual, principal, unidad/cantidad, ledger_revision, neto no negativo, `intent_key`/receipt duplicado, conservación append-only. UPDATE CAS + INSERT dentro de una única transacción `RealityStore.run`; sin COMMIT propio.
- `RealityStore` rechaza escritura SQL13 salvo flag aislado y fence de generación, comprueba nuevamente antes de COMMIT; `synchronous=FULL` y WAL. Antes del flag todos los clientes SQL13 siguen read-only.
- El grafo expone nueva sesión DEV únicamente bajo lock raíz exclusivo y `FaroV5Household13Evidence2DevJournaledCutover.hasCompletedSelection` exacto, ya existente. No conecta UI, Apply binding ni grant `undo.execute`; G1 posterior debe demostrar receipt COMMITTED y nueva aprobación.
- Las seis pruebas nuevas cubren éxito/reapertura, principal/revisión obsoletos, doble Undo/intent, rollback/fallo antes del COMMIT, modo readonly y scope ajeno, revocación y gate suspendido.

## CI/guardia: SOLO observación de arranque
- Guardia siete bloques: https://github.com/etacsantafe-source/FARO-V5/actions/runs/45637952691 — QUEUED al observar, no hay PASS.
- CI G0: https://github.com/etacsantafe-source/FARO-V5/actions/runs/45637952977 — QUEUED al observar, no hay XML certificado.
- Suites planificadas: `RealityHousehold13CompensationApplyPortTest` 6, regresión `RealityHousehold13CompensationRowsPortTest` 6, regresión lectura `FaroV5Household13ActiveDevReadHostTest` 6, total 18 si CI confirma. Por ahora **0 resultados verificados de esta ejecución**.
- El diff del código confirmado contiene exactamente las cinco rutas del permiso y el trailer `FARO-UNIT`. No se tocó main/V4.4 ni ningún producto.

## B7 siguiente obligatorio
1. Esperar observación CI + guardia reales en SHA `8056a65959647a4ae58b0616d5c1f198ec1c84bc`; inspeccionar job, BUILD, test XML originales y artefactos.
2. Si falla, elaborar diagnóstico/RED_FIX limitado a la unidad y su propio permiso; nunca inventar PASS. La guardia puede revelar un fallo independiente.
3. Si todos pasan, registrar B7 scoped documentando qué no se probó físicamente. Reconciliar `FARO_CONTINUITY_POINTER.json`, consumir permiso, liberar writer y recién después preparar G1 N-016 Review/Apply con autorización propia.
4. G1 debe probar end-to-end COMMITTED original y permisos, Android A1C3 y global A1C4 quedan abiertos.

Moto G15 **NOT_RUN**. Stock real, V2 productiva, Apply productivo, pagos y Auto/Noche OFF. Writer al cierre documental se libera en época 2017.
