# S3.A3 Guardados — Auxiliary CAS/receipts B7 RED_FIX
Fecha: 2026-11-09 AR. Unidad `S3_A3_BOOKMARKS_AUXILIARY_CAS_RECEIPTS`, código `32382b9c54f4b73795bd727c4e69c580a4ef2d5b`. Estado **RED_FIX**, NO PASS. Guardados continúa OFF.

## B7 — Resultado verificado y diagnóstico
- CI específica run [42728500403](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42728500403), job `129540218938`: completed/failure, `:store:auxiliary:compileKotlin` FAILED, Kotlin `SMARTCAST_IMPOSSIBLE` en `AuxiliaryBookmarkStore.kt:67`, porque `request.cursor` es propiedad de API de módulo externo. XML de las ocho pruebas NO ejecutado. NO atribuir éxito a compilación ni probar APK.
- Regresión esquema v3 run [42728499587](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42728499587) también falla por la MISMA compilación añadida, NO invalida por sí sola el PASS del commit anterior `1b9fa3e8`. Guardia de protocolo run [42728499613](https://github.com/etacsantafe-source/FARO-V5/actions/runs/42728499613) **SUCCESS** para autoría/permiso, distinta de JVM.
- Auditoría estática detectó problema adicional: `applyMutation` crea bookmark/folder con FK a `bookmark_scope(1)` ANTES de insertar la fila de scope, lo que invalidaría altas incluso después de arreglar Kotlin. Debe establecer scope dentro de la misma transacción ANTES de la primera mutación, y rollback también para rechazo.
- Negar montaje de operaciones; catálogo 172, V2 productiva/S4/Auto/Noche OFF. Solo RED_FIX acotado, no abrir otras unidades hasta CI verde y B7 posterior.

## Continuidad
Permiso documental nuevo para la MISMA unidad y scope Kotlin+tests, B1–B5 basados en esta evidencia verificable. No repetir contratos 8/8 ni esquema v3 5/5 ya certificados a su SHA previo.
