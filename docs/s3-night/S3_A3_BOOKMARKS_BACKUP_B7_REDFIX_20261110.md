# Guardados backup — revisión adversarial RED_FIX
Unidad `S3_A3_BOOKMARKS_BACKUP_RESTORE`, SHA dd40cea9e68b4a63f8854670b789551d649343bd, 2026-11-10 AR.

## B7 — RED_FIX, no certificación funcional
Guardia 42918308223 SUCCESS; CAS auxiliar automático 42918308314 SUCCESS. CI backup 42918308433 aún en ejecución al detectar estos hallazgos; no cancelarla ni declararla PASS.
1. `ORDER BY` SQLite BINARY compara UTF-8 y `sortedBy` Kotlin compara UTF-16. Reproducción READ_ONLY: U+E000 precede U+10000 en SQLite, orden inverso en Kotlin. Los IDs válidos admiten ambos; el snapshot valida orden Kotlin y fallaría con filas válidas. Corregir orden final de carpetas/items/receipts y probar roundtrip SQLite real con los dos IDs.
2. La nueva sección conserva Guardados, pero `BackupClassPolicy.decisions` solo declara AUXILIARY_NOTICES. Debe existir una decisión explícita de Guardados/metadatos/recibos retenidos en wire v3, preservando exactamente la política y bytes v1/v2. No fingir que los nuevos datos pertenecen a Avisos.

## B1 — Delta observado
Mismos ocho archivos anteriores, más SemanticBackupRestoreT2Test.kt para que la regresión de captura actual verifique la política nueva. El test histórico LegacyGenerationAndBackupV1T1Test sigue intacto y debe probar la política antigua. No modificar contratos CAS ni catálogo.
## B2 — Reutilización
Mantener snapshot/restore/codec de dd40cea9 y comparar B2 previo; delta exclusivo de orden y declaración de clases. No repetir investigación V4.4 certificada.
## B3 — Evidencia adicional
SQLite https://www.sqlite.org/datatype3.html §7 describe BINARY y su comparación de bytes. JVM String https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html#compareTo(java.lang.String) usa valores Unicode de sus unidades char; los pares surrogate no tienen el mismo orden binario UTF-8. Aplicar orden canónico final Kotlin ya elegido. Reutilizar alternativas semántica vs física del B3 anterior y AEAD existente; ninguna nueva librería/licencia/coste.
## B4 — Selección
Ordenar resultados acotados antes de construir snapshot. Añadir clase explícita AUXILIARY_BOOKMARKS con política v3 separada, manteniendo `decisions` histórica; codec reconstruye política según versión. Manifest builder selecciona política según presencia explícita de snapshot Guardados. Nueva prueba adversarial Unicode + assertion de política en codec; añadir regresión histórica al workflow. v1/v2 no cambian y no reinterpretan ausencia como vacío.
## B5 — Condiciones
READY para RED_FIX de la misma unidad. Debe pasar nueva prueba Unicode, pruebas nuevas anteriores, regresiones backup y política histórica, guardia exacta. No emitir B7 PASS antes. Mismos límites V2/S4/Auto/Noche OFF, writer CAS y física final. CI previa se conserva como evidencia de su SHA sin confundirla con la corrección.
