# FARO V5 — ABCD Tarea B adelantada (05/11/2026)

## Tarea B: código y pruebas — alcance ya ejecutado
- Rama work/s3-night-20261102, HEAD 2c00fc06100c0856812b1bd5a8cec357e39713f2.
- Reutilizado permiso READY S3_A1_HOME_BATCH_REVALIDATION y B1–B5 de docs/s3-night/S3_A1_ABCD_HOME_BATCH_B1_B5_20261105.md.
- Código B6 ya publicado en 2c00fc06: revalidación por owner al cerrar el lote Home y 2 nuevas pruebas de revocación/generación. No duplicar ese cambio.
- CI exacta 42094317827 SUCCESS: S3_SURFACE_XML_PASS 61/61.
- CI exacta 42094317877 SUCCESS: V2_CORE_DEV_SEARCH_SESSION_XML_PASS 10/10, G5_REAL_HOST_FOUR_OWNER_XML_PASS 1/1 y M2_DEBUG_T4_RELOCATED_XML_PASS 7/7.
- Guardia exacta 42094317812 SUCCESS: FARO_PROTOCOL_GATE_PASS y FARO_PROTOCOL_PREFLIGHT_PASS.

## STOP acotado para otro commit funcional
- last_code_review.sha del permiso todavía es bcb38e9ca36da7ae9df387d80bb2e911cbb71533 (G5), no el último commit funcional 2c00fc06.
- El protocolo impide un segundo commit de código hasta B7 independiente de ese último commit. Tarea B no suplanta la auditoría C. Estado: WAIT_C_B7; NO CI roja.
- N015: falta semántica de tiempo de commit y federación history.query; el índice lexicográfico por ID no sirve como cronología.
- N031: no existe owner agregado autorizado Matter+Reminder+MonthlyPlan para Pendientes; Matter solo no es cobertura suficiente.
- Guardados +8 operaciones no aprobadas; no se muestran contadores inventados ni se expande catálogo 172.
- No existe permiso nuevo y acotado para implementar N015/N031; no inventar ACL o fuentes. No se creó código adicional en este turno ni se lanzaron Actions manuales.

## Handoff
- C: auditar diff de 2c00fc06, las dos pruebas dirigidas y CI por SHA; emitir dictamen B7 independiente, con riesgo residual de revocación después del retorno.
- D: corregir SOLO fallos demostrados, con permiso válido y pruebas exactas; no repetir G1–G5.
- Writer global CAS adquirido y liberado tras este checkpoint, sin retenerlo durante CI.
- S3 1/5 cerrada en alcance automatizado, desarrollo histórico ~40% no reestimado; S3.A1 OPEN. S2 8/8; V2 productiva OFF, S4 OFF, X01–X12 y Moto G15 diferidos S3.A5.
- Tiempo productivo real NO_REGISTRADO; termómetro amarillo por dependencia B7, sin suite roja.
