# S3.A3 M05 — Aceptación integrada automatizada y físico final diferido — B1–B5

Unidad S3_A3_BOOKMARKS_AUTOMATED_ACCEPTANCE. Depende M01–M04 B7 PASS.

Fecha 2026-11-11 AR. Base auditada 00bd1f5dd1833bcea136cc4a50219ede08000fd0.

Estado PREAUDITED_DEPENDENCY; NO READY ni permiso emitido. Solo M01 cuenta con permiso READY. Esta preparación no ejecuta B6 ni B7.

Fuentes externas verificadas: Android Architecture https://developer.android.com/topic/architecture/recommendations (ADAPTAR single-source-of-truth y UDF), Slack Saved https://slack.com/help/articles/360042650274-Save-messages-and-files-for-later (REFERENCIA experiencia, no cloud), SQLite BEGIN IMMEDIATE https://www.sqlite.org/lang_transaction.html (USAR transacción existente). Sin dependencias pagas.

Política transversal: un writer CAS, HEAD y permit frescos, staged ref solo rama work/s3-night-20261102; V2 shipping/S4/Auto/Noche OFF; main/V4.4 no tocar; tests físicos G15 reservados a campaña única final.

## B1 Resultados certificados que no se repiten

Contratos 8/8 run 42720699353; catálogo oficial 180 con 8 agregadas run 42927956001; CAS/receipts Auxiliary run 42729311671; mutation authority 5/5 run 42923566608; lector host nativo 19/19+DEV APK run 42935422794, guard run 42935422815. Estas pruebas son históricas por SHA y no prueban integración nueva. A3 global sigue OPEN.

## B2 Reutilización

R2/H02–H04 de P00 y experiencia V4.4 comparada. No reimplementar tabla, migración, backup ni catálogo salvo falla real provocada por delta de integración. Read/admission, mutation, UI y navegación permanecen owners separados.

## B3 Investigación

Android recomendaciones de arquitectura/UDF (https://developer.android.com/topic/architecture/recommendations) ADAPTAR tests de capas. SQLite transacciones (https://www.sqlite.org/lang_transaction.html) USAR commit real y receipt en mismo DB. Slack Saved (https://slack.com/help/articles/360042650274-Save-messages-and-files-for-later) REFERENCIA salto a fuente. Sin servicios ni librerías nuevas. Descartar confiar solo en mock o pantalla optimista.

## B4 Matriz binaria integrada (sin afirmar que ya pasó)

A3-01 default deny de mutation sin grant, read no concede write; A3-02 Guardar mensaje exacto→Auxiliary receipt; A3-03 idempotencia y CAS para misma key y conflicto; A3-04 remove/restore/favorite sin borrar fuente; A3-05 folder create/rename/assign/root/delete-empty, negar cascade; A3-06 cuotas32/1024, pagina50, unavailable distinto de vacío; A3-07 revoke/archived/deleted/pointer ABA no filtración; A3-08 Chat→review humano→confirmar→receipt, sin Reality COMMITTED ni doble tap; A3-09 Guardados→chat original/mensaje exacto y foco, sin fallback; A3-10 crash antes/después commit y recuperación sin retry; A3-11 restore/migration no revive fuentes ajenas (reutilizar B7 anterior si integración no cambia storage); A3-12 shipping/V2 productiva OFF, APK DEV y manifiesto aislado. Test dedicado app + surface y workflow A3 acceptance; suite dirigida, XML real sin skips + artifact apk, guard de protocolo mismo SHA.

## B5 Dictamen de cierre

No marcar S3.A3 CLOSED por APK y unit test solamente. Resultado permitido tras B7 M05: A3_AUTOMATED_READY_FOR_FINAL_PHYSICAL_CAMPAIGN; campaña real Moto G15 única al final junto a A5 X01–X12. Fallo en código dueño de M01–M04 exige RED_FIX B1–B5/permit de la unidad propietaria, no ampliación de este paquete de solo tests. Porcentaje S3≈40% histórico no KPI de estos tests.
