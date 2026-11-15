# FARO V5 — S4 P08 · Auditoría final B7 documental (2026-11-15)
**Unidad:** `S4_PREP_P08_WORK_PACKAGES_DOCUMENTARY`. **ALCANCE:** planificación/documentación de W00–W11 exclusivamente. **NO** apertura S4, código, permiso READY, funcionalidad de dominio certificada ni pase de tests S4.

## B1 — Recuperación real
- HEAD de partida: `91bf381c1cc482b942387866eef1ad1f860f0f1b` (cierre S3 formal DEV 5/5 con excepción H07-HOME humana, NO PASS). S2 8/8. P00–P07 documentadas.
- Se consultaron `AGENTS.md`, `docs/v5/FARO_CONTINUITY_POINTER.json`, `docs/v5/FARO_PROTOCOL_WORK_PERMIT.json` (BLOCKED), protocolo B1–B7 y los P00–P07. S4 formal OFF; V2 productiva/Auto/Noche OFF.
- Writer previo RELEASED epoch 908 con checkpoint del HEAD base. CAS acquire `cd7cbffae5424beee48d9e86668a815cab5efdb5`, writer HELD epoch 909, owner `FARO.MANUAL.S4.P08.DOC.20261115`, DOC_ONLY. Publicación sin otro escritor.

## B2/B3 — Reuso e investigación
- Se reutiliza P02/P03 (comparativa V4.4 vs V5), P04 (benchmarks externos y restricciones), P05 (flujos manuales), P06 (UX decidida), P07 (ownership y opción A de pagos). No se repiten pruebas S3 ni se copia runtime V4.4 o software ajeno.
- Los 12 paquetes W00–W11 describen alternativas y riesgos por unidad; **falta** B3 específico con fuentes y versiones del código vivo, B5 auditado por unidad y permisos exactos **antes de cada escritura funcional futura**.

## B4 — Contratos documentales preservados
- Chat primero, drawer sin duplicados, Herramientas Guardados/Actividad/Avisos, Cocina dentro SmartCompra, Radar opción C (Inicio/Chat/Asuntos sin raíz), gráfico 12 meses M-6..M+5, eje 0 visible, solo negativos rojos, detalle M0..M+5 sin truncar cuotas futuras.
- Finance es único owner del dinero; Household es dueño conceptual de precios, ofertas según medio, presupuesto comercial y stock. Household aprobado **solo para diseño**. Opción A = una review humana, dos authorities, commit atómico demostrable; no fallback de dos commits parciales.
- W07 permanece BLOCKED hasta W06 PASS de atomicidad real entre owners. No suponer que descriptores `household` constituyen ejecutor; catálogo180 intacto.

## B5 — Auditoría adversarial documental
1. No convertir preauditoría W00–W11 en READY ni autorizar S4 formal por continuidad.
2. Los escenarios PF/P7T/PX/UX de P05–P07 no fueron ejecutados en P08; S3 DEV/fixture Profile no prueban pago de dos owners.
3. `UNKNOWN` requiere reconciliar receipt; no doble Apply. Revocación/restore/generation/ACL, FX y saldos desconocidos son gates FAIL_CLOSED.
4. Compra/pago/recepción/consumo separados. Reintegro futuro no ingreso REAL; cuotas previstas no débito REAL; fuente parcial no cero ni normalidad falsa.
5. Diff permitido solo documento P08 B1–B5, esta auditoría y pointer; no Kotlin, catálogo, CI Android nueva, APK/G15, claves, `main`, V4.4, V2 productiva o Auto/Noche.

## B6 — Publicación y evidencia observada
- Documento P08 W00–W11 publicado en commit `7137ed01001dace26ba6ae422dcb14c34e69ccb4`, blob remoto `b94b3b565e0050fe8a18ab692ada3400921e9b5b`. GET del contenido coincidió exactamente; 12 encabezados W00–W11.
- Este documento y `docs/v5/FARO_CONTINUITY_POINTER.json` completan B6 DOC_ONLY bajo writer epoch 909. El último commit, diff, guardia y release **se verifican después de su publicación**; no afirmar PASS antes de obtener evidencia.
- CI Android / XML / APK / G15 para S4: NOT_RUN. No repetir guardia histórica como si certificase nuevo HEAD.

## B7 — Gate final exigible, NO autodeclarado
P08 se puede cerrar exclusivamente en alcance `DONE_VERIFIED_DOC_SCOPE` si todas estas condiciones se observan después de publicar:
1. HEAD remoto contiene los dos documentos P08 y pointer con `s4_preparation.p08` coherente; W00–W11 y B1–B5 en archivo, sin contradicciones.
2. Diff HEAD base→final exclusivamente las tres rutas `docs/v5/FARO_S4_P08_W00_W11_B1_B5_20261115.md`, `docs/v5/FARO_S4_P08_B7_DOCUMENTARY_20261115.md`, `docs/v5/FARO_CONTINUITY_POINTER.json`.
3. Permiso funcional `BLOCKED`; `s4_open=false`, sin nuevas operaciones ni READY. S2/S3 previos intactos y S4 sin código.
4. Writer remoto RELEASED epoch 909, `last_checkpoint` = HEAD exacto; ningún HELD/UNKNOWN o cambio concurrente.
5. Guardia GitHub Actions `seven-block-evidence` del **HEAD final** completada SUCCESS (no basta run de 91bf).
6. Si falta guardia, writer o diff: reportar `P08_WAIT_CI` o `P08_BLOCKED`; no declarar cierre.

**Resultado documental previsto tras pasar B7:** P00–P08 completados SOLO en preparación; W00–W11 documentados como candidatos, NO implementados/READY. S4 formal OFF hasta nueva decisión humana sobre alcance/owner y permisos. **El cierre de P08 NO es el cierre de S4 funcional.**
