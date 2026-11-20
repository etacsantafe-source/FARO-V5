# FARO V5 — W04-C0A · Binding explícito necesidad ↔ producto comercial · B1–B5
Fecha local: 2026-11-20. Baseline `a60c184d116bd0c8a9ddc6941a6eb3478b5455a6`. Unidad `S4_W04_C0A_EXPLICIT_NEED_PRODUCT_MATCH_DEV`. Solo modelo puro DEV, NO lectura ni persistencia Evidence/Reality.
## B1
`HouseholdNeedId`, `HouseholdNeed`/revision y `HouseholdNeedsSnapshot` existen en `HouseholdNeedModel.kt` (blob 224b4aac04238b2241adb046161761c931389bdc) / `HouseholdNeedPersistencePort.kt` (blob f48e6528ffcdf4986274dc5111ca0b8a10255c00); `CommercialProductKey` existe en `CommercialEvidence.kt` (blob cf9d53089cafd93538e6b03f9873c98264d20b65). No hay binding verificado en el grafo W04-C. App depende de :evidence y :owner:household vía módulo y grafo actual: verificar dependencias Gradle antes de implementar. B1 de W04-B1 B7 PASS 17/17 se conserva; no rehacer.
## B2
V4.4 L11/L14 admitía interacción por nombre humano, pero producto/cantidad y necesidad no tenían la misma identidad semántica. Elegir producto explicitamente (por humano) sin recuperar id interno como input externo. V5 debe preservar revisiones, generación, grants y separación Evidence≠Reality.
## B3
ELEGIDA: objeto de selección efímero tipado, apto para pruebas unitarias, y API fail-closed. RECHAZADAS: unir por String de nombre, key heurística, escribir catálogo propio, esquema SQLite o migración en C0A. REFERENCIAS: P02/P04 ya estudiadas, reutilizadas sin copiar código/servicios.
## B4
Entrada: snapshot Household admitido; needId existente OPEN y su revision; productKey explícita elegida por usuario en una lista cuya provenance será validada por C0B; identidad de sesión/generation/epoch solo del contexto confiable, nunca inventada de texto Chat. Salida: selección *candidata* con needId, needRevision, globalSnapshotRevision, productKey, generation/epoch y condición de validez para revalidación posterior. No hay escritura/Apply, tarifa/stock, sourceRef, ni receipt. Cambiar Need/revision, borrar/completar, cambiar generación o revocar lectura impide reutilizar candidato. Ausencia/ambigüedad/selección cancelada produce estado explícito y mensaje humano, no coincidencia automática. La selección NO promete persistencia/reapertura hasta otro permiso.
Rutas exactas admitibles para C0A (solo tras guardia y READY):
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W04CommercialNeedProductSelection.kt`
2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W04CommercialNeedProductSelectionTest.kt`
3. `.github/workflows/faro-v5-s4-w04-c0a-match.yml`
Todas son rutas NUEVAS exactas. No tocar :evidence, schema, app build.gradle ni generacion en esta unidad. Dependencias de compilación verificadas: app implementa :evidence, :store:generation, y prueba accesos a tipos Household usados por S4W04HouseholdNeedApplyHost, sin cambiar dependencias.
B6 futuro: model+tests JVM y workflow dirigido con `FARO-UNIT:S4_W04_C0A_EXPLICIT_NEED_PRODUCT_MATCH_DEV`; XML real y guard por SHA. B7 independiente; C0B solo después.
## B5
T01 nulo/need no existente; T02 nombre ambiguo; T03 revision/generation stale; T04 productKey inválida; T05 identidad/capability no admitidas; T06 cambio COMPLETED/REMOVE; T07 snapshot concurrente; T08 comprobación que no cambió Reality/Evidence ni generó recibos; T09 selección explícita sin inventar precios; T10 no presentar un link de origen como ejecutable. Todos TESTS FUTUROS, NO PASS aún.
## Permiso
B1–B5 preauditado con scope exact3; no READY todavía. Requiere primero guardian `seven-block-evidence` del commit documental SUCCESS, permiso individual con blob SHA de este archivo y preflight exact3.
