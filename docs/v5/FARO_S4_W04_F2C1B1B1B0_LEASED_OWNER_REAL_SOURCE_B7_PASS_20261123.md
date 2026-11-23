# FARO V5 — S4 W04 F2C1B1B1B0 — B7 PASS (DEV REAL ACTIVE source)

Fecha 2026-11-23. Rama `work/s3-night-20261102`. Último código `9d000c710c7528288337f6f0c7caaa28fa0c0398`. Unidad `S4_W04_F2C1B1B1B0_LEASED_OWNER_REAL_SOURCE_DEV`.
**B7 PASS de esta unidad scoped DEV JVM, NO del Android F2C1B1B1B1 ni de F3 global.**

## B7 — Pruebas, guardias y archivos originales
- B1–B5: `docs/v5/FARO_S4_W04_F2C1B1B1B0_LEASED_OWNER_SOURCE_B1_B5_20261123.md`, blob `b95c3a20a64625f368c6eb83e7b30ef1f25a96f3`, guard documental `45191915994` SUCCESS. READY exact4 emitido en `ee7a740a4792b936ca295f5db72eefe526cbbba8`, guard `45192202241` SUCCESS.
- B6 código inicial `ce97438755eabcd0fb868d31673b5df68cfefb5b`, guard `45193029627` SUCCESS. CI `45193030044`: cuatro fallos por reentrada Household owner a la misma transacción. RED_FIX1 `f0220502b761d79e4d3de8dbe64a01f441542ace`, guard `45194704552` SUCCESS, eliminó la reentrada y revalidó grants antes/después; CI `45194704679` abortó ANTES de ejecutar por contador fijo de cuatro rutas en workflow, preflight PASS.
- RED_FIX2 `df9885a43bab8f6897211e068a7f66b229bbdbdc` permite 1–4 rutas **siempre tras preflight exacto**, guard `45195400619` SUCCESS. CI `45195400769`: 3/4 PASS; t10 revela falta de revocación terminal al observar epoch inválido que después vuelve a A. Artifact `14827851280`, ZIP SHA256 `d9748b1f9b13baf5f40d3e6d9b62c90a41b071d61545cee38ee926cab874d09b`.
- RED_FIX3 código final `9d000c710c7528288337f6f0c7caaa28fa0c0398`: read-only owner-backed path revoca **ambos** scopes irreversiblemente al observar un resultado nulo o una excepción de lectura, conservando intactas las semánticas previas del puerto F2C0 no vinculado a owners. Guardia final de código `45197012250` **SUCCESS**.
- CI FINAL `45197011644` job `137770048522`: **COMPLETED SUCCESS**. Paso Gradle `:store:generation:test` dirigido ÚNICAMENTE a t07,t08,t09,t10 PASS. Paso "Verify exact four new JUnit5 XML cases" SUCCESS. No se repitieron t01–t06 ni B0/B1 certificados.
- GitHub artifact original `14829471083`, `w04-f2c1b1b1b0-real-leased-owner-junit-xml`, ZIP SHA256 `aff667070af8c4860622c593f49a86b1f4503c0f460bbd15497a3702252645ee`, coincide con el digest remoto de GitHub. Descargado y validado separadamente: 1 XML `TEST-ar.com.faro.v5.generation.FaroV5Household10Evidence2DevSelectionReadPortTest.xml`, SHA256 `f6f6ac6482559fa655ffdcb7ba10bc8f0386144b4d8d8a94b7e1b0d4005ec8cf`. XML raíz: **4 tests, 0 failures, 0 errors, 0 skipped**.
- Exactos: t07 `RealOwnerSourcesShareOneActiveLeaseAndExactTypedSelection`; t08 `DeniedHumanDecisionNeverLeavesAnActiveRootLease`; t09 `LegacyTermsOrMissingSelectedProductAreNeverGranted`; t10 `HostEpochConsentAndPointerAbaCannotResurrectOwnerGrants`.

## Alcance técnico certificado
`FaroV5GenerationStoreGraph.Household10DevReadSession` provee el snapshot Evidence2 tipado desde **el mismo graph/lease ACTIVE** para una decisión de owner confiable; `FaroV5Household10Evidence2DevSelectionReadPort.openOwnerBackedDev` proyecta snapshot Household desde el mismo Reality Store, crea los dos scopes owner F2C1B1B1A, emite nonces delimitados por identidad/generación/época/necesidad/producto, revalida ambos antes y después de la lectura y revoca terminalmente ante cualquier rechazo observado.
La elección humana es un **callback confiable inyectado de prueba**, NO una prueba de autorización Android. No hay nuevos SQL stores, Apply, pagos, red ni precio actual. Sin mezcla ARS/USD.

## Pendiente sin sobredeclaración
F2C1B1B1B1 — integración positiva Android API35 con issuer real `FaroV5W04CommercialDevReadConsentIssuer`, actividad aislada y lifecycle, host `FaroV5W04CommercialDevTrustedSelectedHost`, selección humana explícita de necesidad/producto/observación y proyección F1→D4/E con nueva evidencia XML Android. Conectar owner-backed real API sin convertir callback JVM `{true}` en permiso de producción; verificar onPause/onStop, pointer ABA y cierre del OS lease. Requiere B1–B5 de la siguiente unidad, READY nuevo y B6/B7 independientes.
F3 aceptación integral y S4 global siguen OPEN; V2 productiva, pagos, stock, red, G15, Auto/Noche, main/V4.4 sin tocar.

## Checkpoint
B6 **DONE_VERIFIED**, B7 **PASS_SCOPED_REAL_ACTIVE_OWNER_SOURCE_JVM_4_4**. Permiso de esta unidad consumido/BLOCKED; writer CAS epoch1733 deberá figurar RELEASED con target_head de este checkpoint. Guardia final documental se verifica por run específico antes de dar cierre.
