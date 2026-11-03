# FARO V5 S3 — NOCHE_A 21:00 · A1 handoff durable (02/11/2026)

## Autoridad y checkpoint
S2 CLOSED 8/8, S3 exclusiva `work/s3-night-20261102`, V2 app OFF, S4 OFF; main y V4.4 intocables. Autorización termina 22:00 AR. S3-N00 PASS con caveat CAS stale genérico, P00 FROZEN_SPEC_ONLY; catálogo 172 intacto, +8 Guardados CANDIDATE sin bounded approval. Writer NOCHE_A epoch 23 adquirido con CAS; la liberación se certifica por separado en cola CLOUD y writer ref. Ninguna prueba física.

## Trabajo funcional A1
Commit [`89ce6d9bb21722d708daafb20f3f19b638cad46b`](https://github.com/etacsantafe-source/FARO-V5/commit/89ce6d9bb21722d708daafb20f3f19b638cad46b): mejora semántica de Inicio: encabezado de accesibilidad, destinos con rol botón, altura táctil mínima 48dp ya existente y anuncio explícito `Pendientes/Avisos/Actividad/Guardados: No disponible` frente a cero real. Agregada prueba JUnit para los cuatro indicadores. No se alteraron fuentes de datos, permisos, catálogo, Apply, ni V2.

GitHub Actions [#41549981598](https://github.com/etacsantafe-source/FARO-V5/actions/runs/41549981598), SHA exacto `89ce6d9bb21722d708daafb20f3f19b638cad46b`, conclusión SUCCESS, log `S3_SURFACE_XML_PASS 45/45` y JUnit XML 45/45 verificados por workflow. No equivale a prueba Android/lector de pantalla; X12 sigue NOT_RUN integral. Advertencia Kotlin menor `UNUSED_EXPRESSION` en test (línea 46), no fallo. No repetir 45/45 sobre el mismo SHA.

## Bucle adversarial / comparación obligatoria READ_ONLY
- V4.4 `FaroV4HomeSurface.kt` blob `d3d3e8446882686f22c9d46654682f790cf58a78` ejecuta `radarService.refresh()` al cargar; V5 Inicio preserva `home.read` sin efectos, no portar ese refresh.
- V4.4 `FaroV4FollowUpGateway.kt` blob `bef0fd295ffc818c51fc4790de282e77d63d3e49` proyecta matters, recordatorios y atención desde owners; no usar `ConversationWorkspaceEntry.hasPendingReview` como sustituto del N-031.
- V4.4 `FaroV4GlobalHistoryGateway.kt` blob `1a30d55fd1d358e9495c44633cc83e3c4c3753d2` ofrece consultas filtradas/paginadas de receipts. V5 N-015 requiere propietario de consulta real; `RealityStore.executionReceiptLookup` sólo lookup por ID, no un historial paginado.
- Benchmark documental oficial `docs/v5/FARO_S3_BENCHMARK_APPS_20261102.md` conservado; no se instalaron apps externas ni copió código. Revisión adversarial: falta owner N-031, falta history.query N-015, Guardados bookmark +8 sin bounded approval; no fabricar conteos ni adelantar A2/A3/A4.

## Estado y siguiente paso
A1 **PARTIAL_CI_VERIFIED** con **1/4** indicadores reales: Avisos N-032; Pendientes, Actividad reciente y Guardados permanecen UNKNOWN. Accesibilidad tiene JVM dirigida verde, no certificación UI/Android. X01–X12 y campaña física única NOT_RUN. NOCHE_B 21:30 debe recuperar el checkpoint real y avanzar sólo si encuentra owner canónico seguro; de lo contrario investigar READ_ONLY. Sin repetir tests verdes por SHA ni extender autorización.

Tiempos observados aproximados 21:01–21:07 AR, con CI solapada; desglose de escritura/investigación/pruebas/espera **NO_REGISTRADO**.
