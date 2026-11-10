# Guardados backup — fixture de versión futura
Unidad `S3_A3_BOOKMARKS_BACKUP_RESTORE`; fuente e75d3227ac87d0fec5eca5e4dd421803418f8aa2. 2026-11-10 AR.
## B7 — RED_FIX documental por inspección
42919193690 sigue en ejecución, sin declarar resultado. La revisión de la clase histórica añadida al CI detecta `semanticBackupV1RejectsUnknownNewVersionWithoutReinterpretingPayload`: crea solo 8 bytes con versión 2 y espera IllegalStateException por versión desconocida. V2 ya estaba soportada ANTES de esta misión (ver codec dd365d9d); por tanto no prueba la hipótesis declarada, produce EOF al leer manifiesto. Corrección concreta: usar versión 99 realmente no soportada; conservar assert de manifiesto v1 y excepción fail-closed. Sin cambiar decoder para satisfacer un oracle obsoleto.
## B1 — Evidencia
LegacyGenerationAndBackupV1T1Test.kt:50–59 y SemanticBackupSetCodec LEGACY_VERSION/TIMED_VERSION/BOOKMARK_VERSION. Misma unidad, sin funcionalidad nueva ni pérdida de prueba histórica. Añadir ruta exacta del test al permiso. Añadir rutas de regresión afectadas al filtro push del workflow para que cambios futuros del test disparen CI dirigida.
## B2 — Reutilización
Conservar golden bytes de descriptor y política histórica; no regenerar ni reescribir esos oráculos. Reutilizar corrección Unicode/política y negativas del B1–B5 anterior.
## B3 — Alternativas
Reutilizar documentación primaria de formatos/versiones y límites en preauditorías anteriores. (A) rebajar assert a Exception: DESCARTAR, ocultaría EOF; (B) bloquear V2 soportada en decoder: DESCARTAR, rompe compatibilidad; (C) fixture versión futura 99: USAR, comprueba rechazo por versión antes del payload. Sin biblioteca, coste ni código externo.
## B4 — Delta
Un literal y comentario de fixture, más filtros push de los tres tests de regresión ahora involucrados. Mantener todos los tests seleccionados; no omitir el test defectuoso ni cancelarle su CI anterior.
## B5 — Condiciones
READY misma unidad para test/workflow solamente, después de permiso documental actualizado. CI final por SHA/XML debe probar los 11 tests nuevos y regresiones; B7 PASS sigue prohibido hasta ver resultados. Writer CAS; catálogo 172 y flags OFF intactos.
