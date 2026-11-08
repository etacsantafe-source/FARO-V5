# FARO V5 — S3.A4 N015 real Activity host · RED_FIX B1–B5
Fecha Argentina 2026-11-08. Unidad `S3_A4_N015_REAL_SQLITE_ACTIVITY_HOST_BRIDGE`, initial code `d167d550841c223fb9b4045539c976190b2f0a8a`, B7 RED_FIX documento separado.

## B1 — Problema delimitado y criterio de aceptación
- La CI real #42514838413, #42514838184 y #42514838205 detectó compilación fallida por `ConversationWorkspaceStore.create` `internal` llamada desde `:app`. Además `timelineSnapshot` y `saveTimeline` pertenecen al módulo Workspace y la API pública expone `createAndSelect`, `readTimeline`, `writeTimeline`.
- El host de N015 revalida **no nulidad**, no igualdad de epochs después del último procesamiento. Necesita retener epoch de ambas fuentes antes del primer owner read y comparar ambos después del mapping.
- Aceptación: seis tests reales SQLite compile/pass, source6/Surface5/Nav11 intocados, V2 core compile, guard PASS exact SHA, jamás elevar V2 ni instalar ACL fixture.

## B2 — Reuso de V5 y comparación V4.4
- V5 tiene `ConversationWorkspaceStore.createAndSelect` público, `readTimeline` y `writeTimeline` suspend; usar `runBlocking` en JUnit, NO ampliar visibilidad de `create`, `timelineSnapshot`, `saveTimeline`. Evita duplicar mutaciones y preserva Workspace4.
- `FaroV5FederatedHistoryReadSource` tiene B7 6/6 verificando epochs dentro de read; para el segmento DESPUÉS de source hasta la respuesta del host, añadir check propio de igualdad. No duplicar cursor ni schema.
- V4.4 FollowUp es irrelevante para este error; no tocar V4.4, main, N031, N033C3 Undo ni Surface anterior B7.

## B3 — Investigación de referencia
- Kotlin official visibility https://kotlinlang.org/docs/visibility-modifiers.html : `internal` restringe módulo; los tests `:app` NO son friends de `:workspace`. USAR contrato público.
- Kotlin coroutines https://kotlinlang.org/docs/coroutines-guide.html : `runBlocking` en tests JVM para llamar métodos suspend públicos, no en UI/Android main thread. ADAPTAR test-only.
- OWASP Authorization https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html : revocación y permiso por source se revalidan, negar cambio de epoch. USAR igualdad capturada.
- SQLite isolation https://www.sqlite.org/isolation.html : las dos lecturas no tienen snapshot global; ningún cambio en semántica de `completeWithinSources`.
- Rechazadas: hacer `public` methods internal, eliminar assertions, reintentar con fake grants permanentes, mover todo N015 al módulo Workspace, relajar permisos / duplicar broker. Sin dependencias ni APIs pagas.

## B4 — Delta mínimo de código y CI
1. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5RealHistoryActivityHostBindingT2Test.kt`: reemplazar un `create(...)` por `runBlocking { createAndSelect(...) }`, `timelineSnapshot(chat)` por `runBlocking { readTimeline(chat) }`, `saveTimeline(request)` por `runBlocking { writeTimeline(request) }`; assertion de Saved acorde al puerto. Mantener seis tests.
2. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5RealHistoryActivityHostBinding.kt`: `readActivity()` empieza con snapshot no nulo de reality/workspace policy epoch y al final exige EXACTA igualdad de ambos con el inicial, además de hostCurrent; exceptions/cancellation fail closed.
- **SOLO DOS paths funcionales autorizados**; sin workflow nuevo. Mantener trailer `FARO-UNIT:S3_A4_N015_REAL_SQLITE_ACTIVITY_HOST_BRIDGE`. Re-ejecutar CI para SHA final con XML exactos 6 host + 6 federated + 5 Activity Surface + 11 Nav = 28/28, más guard y V2 core/Home que dependan del test source.

## B5 — Auditoría negativa
- En el mismo turno técnico no convertir null/grant nueva en acceso anterior: `epoch==captured` de Reality Y Workspace; cambio del puntero y host epoch niega, no confiar en `principalId` técnico como identidad humana real. readPolicy=null permanece DENY.
- No ampliar permisos de Surface/Android ni Reactivar V2; owner READ real SQL, receptor Page neutral. El test seed vía puertos públicos mantiene receipt+Workspace real, sin cuerpo visible en DTO. Corrida de CI y XML exigida, no inferir sin ejecución.
- S3.A4 OPEN; el RED_FIX no cierra A4, ni crea fuente de ACL productiva. Un nuevo B7 independiente decidirá PASS solo luego de guard/CI exactos y regresiones.
