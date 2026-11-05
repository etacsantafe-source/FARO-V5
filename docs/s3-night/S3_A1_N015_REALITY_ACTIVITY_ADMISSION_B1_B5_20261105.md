# FARO V5 — S3.A1 N015 Reality activity read admission · 05/11/2026

Unidad **S3_A1_N015_REALITY_ACTIVITY_ADMISSION**. Base exacta `b726351106970bc8ccae5d6b5d153f8bd8947c19`. B1–B5 corresponde SOLO a adaptador app de lectura de Reality; no es `history.query` federado, ni habilita Home ni cierra A1. Permiso funcional debe publicarse separado después de este documento y B6 debe pasar CI exacta.

## B1 — Evidencia exacta
- `v5/store/reality/src/main/kotlin/ar/com/faro/v5/store/reality/RealityReceiptHistoryPort.kt`: `RealityTimedReceiptHistoryReadPort.query` retorna `RealityTimedReceiptPage(receipts,nextCursor,legacyUndated)`; `globallyFederated=false` y `canonicalSource=reality.execution_receipt`. Nunca convertirlo en historial de todos los dominios.
- `RealityStore.kt` esquema 7 y FBS1/FBS2 acreditados por commit `55ef7edd9373a020309df427de16e17ddae9eb0c`, runs `42107761539` y `42107761716` SUCCESS. `b7263511` cerró B7 scoped, preflight siguiente BLOCKED; no repetir ni modificar storage.
- `FaroV5CompositionRoot.java` posee `DeliveryGraph.stores.getReality()` y puntero durable completo; `readActiveHomePort()` enlaza SOLO Avisos N032. No hay grant ACL de history ni vinculación Home de N015.
- `FaroV5MattersSearchReadSource.kt` y `FaroV5ObjectivesSearchReadSource.kt` ya usan admisión externa de generación + epoch ACL, revalidación tras lectura. Reutilizar patrón, no crear owner paralelo.
- `FaroV5HomeReadCoordinator.kt` niega count si fuente o sello faltan, prueba 61/61 en `42094317827`. Esta unidad no toca Surface ni muestra conteos.

## B2 — Comparación V4.4 / V5 y reutilización
V4.4 `FaroV4GlobalHistoryGateway` resuelve filtro y paginación, pero no prueba orden temporal durable o permiso global. No copiar su servicio mutable. V5 Reality ya suministra COMMITTED con marca temporal durable, cursor estable para sus propios recibos. Reutilizar `RealityTimedReceiptHistoryReadPort`, sin mezclar Workspace, Auxiliary, Matter, Finanzas o Evidence.

## B3 — Investigación externa y alternativas reales
- Android Developers, *Offline-first data layer*: https://developer.android.com/topic/architecture/data-layer/offline-first : fuente local canónica, reads observables y no derivar autoridad en UI. **ADAPTAR** capa de lectura del owner persistente; cero APIs de pago.
- Android Developers, *Single source of truth*: https://developer.android.com/topic/architecture/data-layer : el repositorio es autoridad; **USAR** port existente y no nuevo store de historial.
- OWASP MASVS Privacy: https://mas.owasp.org/MASVS/controls/MASVS-PRIVACY-1/ : minimización/fail closed ante revocación; **ADAPTAR** revisión de ACL/generación previa y posterior.
- SQLite keyset: https://www.sqlite.org/lang_select.html : orden `timestamp DESC, transactionId ASC` con cursor del owner, no ordenar ID opaco como fecha. **REFERENCIA**; Reality ya implementa la consulta, no modificar SQL.
Alternativa A: bridge de lectura tipada, con `aclEpoch`/generación/pinned-pointer otorgados por host, y cobertura `REALITY_ONLY`: **USAR**. Alternativa B: usar `count()` de ID/finanzas como actividad global: **DESCARTAR**; cobertura incompleta y reloj errado. Alternativa C: nuevo event-store o polling de UI: **DESCARTAR** por duplicación, cruces de transacción y revocación. No importar licencias ni bibliotecas.

## B4 — Unidad mínima DESIGN ONCE / DELTA ONLY
Crear `FaroV5RealityTimedActivityReadSource.kt` en `v5/app/src/main/kotlin/ar/com/faro/v5/app/`, consumiendo exclusivamente `RealityTimedReceiptHistoryReadPort` inyectado, `expectedGeneration`, `currentGeneration`, `aclEpoch` y `admitted` de host. Exponer `readAuthorized(before?,limit)` que devuelve `null` ante denegación; si válido, snapshot tipado `REALITY_ONLY_NOT_FEDERATED` con recibos, `nextCursor` y `legacyUndated` originales. Ningún `FaroV5HomeCount`, ni binding en `readActiveHomePort`; no convertir muestra de hasta 100 en contador total.
Archivo de prueba `FaroV5RealityTimedActivityReadSourceTest.kt`; añadir prueba al workflow existente `.github/workflows/faro-v5-s3-home-notice-directed.yml` y exigir XML exacto. Ningún cambio Reality, main, V2 productiva, catálogo ni permisos Android.

## B5 — Auditoría adversarial previa
1. **ACL ausente**: `aclEpoch=null` o `admitted=false` impide consultar siquiera al propietario.
2. **Revocación intermedia**: cambio de epoch o generación durante `query` descarta toda la página; no se muestra parcial ni se reintenta con permiso nuevo.
3. **ABA de puntero**: `admitted` debe venir de host y confirmar secuencia durable, no solo ID de generación; probar `admitted` pasando true→false al volver de source.
4. **Cobertura**: con `legacyUndated=true`, `nextCursor` presente o fuente incompleta, nunca declarar cronología global/total. El bridge siempre declara Reality-only.
5. **Errores/cancelación**: errores de store se traducen a unavailable; `CancellationException` se propaga. `limit` 1..100.
6. **Confidencialidad**: no registrar receipt ni enviarlo a red, no cachear página entre grants; retorno incluye objetos ya autorizados solamente.
7. **Pruebas de B6**: al menos cinco JUnit (sin grant, éxito tipado, revocación, generation ABA, excepción) y XML exacto en workflow junto con suites históricas, sin modificar sus cuentas esperadas.

**Dictamen**: B1–B5 VERIFIED para SOLO esta subunidad, pendiente B6/CI/B7. El grant real de history y federation se implementarán en unidad posterior; esta no hace visible Actividad. S3.A1 permanece OPEN; Guardados +8 NO autorizados, 172 intacto.