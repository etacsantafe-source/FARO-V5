# FARO V5 · W05-A1C2E · RED_FIX de lectura negativa SQL13 ACTIVE

## B7 — RED_FIX en la misma unidad

- Código SHA `71323880a3fc791523853f4ea1747bf3fda4a085` dejó resuelto el error de compilación del callback `beforeCommit`; guardia `45622637851` **SUCCESS**. CI `45622637907` terminó **FAILURE** en `:store:generation:test`, con `22` JUnit ejecutadas, `2` fallidas y `20` pasando. Artefacto XML de fallo: [15028150516](https://github.com/etacsantafe-source/FARO-V5/actions/runs/45622637907/artifacts/15028150516).
- Fallas exactas en `FaroV5Household13ActiveDevReadHostTest`: `selectedHistoricalSql12IsNotSilentlyAdmittedAsSql13` línea 139 y `evidence1ProfileIsRejectedBeforeExposingSql13Rows` línea 148. Tras verificar correctamente `assertThrows(IllegalStateException)` para la admisión ACTIVE inválida, ambos tests invocaban un lector **exclusivo de STAGED sellado** sobre un archivo que ya había sido seleccionado ACTIVE. El lector sellado debe fallar con WAL/SHM existentes; este comportamiento fail-closed no debe eliminarse ni flexibilizarse.
- Reparación mínima: solo en `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household13ActiveDevReadHostTest.kt`, cambiar las aserciones posteriores al rechazo por consultas SQLite directas de solo lectura con driver normal para `PRAGMA user_version` y conteo de filas, nunca `immutable=1`.
- Sin cambios en grafo, Reality, infraestructura, cutover, fuentes de stock o pagos. La compensación sigue append-only. Continuar con permiso exacto A1C2E y `last_code_review.result=RED_FIX`; correr CI, verificar `22/22` en XML y guardia por SHA. **No declarar PASS hasta completarlo.**
- G15 `NOT_RUN`; V2 productiva, Apply real, pagos, stock real y Auto/Noche `OFF`.
