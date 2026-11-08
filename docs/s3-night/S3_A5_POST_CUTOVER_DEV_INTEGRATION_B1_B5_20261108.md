# FARO V5 — S3.A5 integración después del cutover Reality8 — B1–B5
Fecha: 2026-11-08 (AR). Unidad nueva: `S3_A5_POST_CUTOVER_DEV_INTEGRATION`. Baseline `feb7a6969c1307187ec3d80e795addd76f96f202`. Writer global epoch598. S2 CLOSED 8/8, S4 OFF, V2 productiva OFF. G15 solo en campaña física X01–X12 al final.

## B1 — Evidencia y brecha exacta
- Cinco subunidades S3.A5 ya tienen B7 scoped: Active8 host 6/6, fresh staged Reality8 5/5, journal PREPARED/VALIDATED 5/5, cutover/recovery 4/4, Android V2CoreDev host 8/8 (CI `42534927828` SHA `6c3c0947`, guard `42534927840`, APK DEV `42534452081`). No repetirlas.
- `FaroV5V2Planning8DevHost` promueve Reality7→8, con intent durable de origin ID+sequence, journal, fence; `FaroV5V2CoreDevAuthority.open()` abre el grafo8 por pointer exacto. Se probó el cutover, pero no hay una prueba **nueva y conjunta** de Chat + Buscar federado de los 4 owners **después** del cutover y reinicio con datos REALES escritos en Reality8. Tampoco la prueba de no filtración de snippets antiguos7 frente a una nueva8, rechazo de SQLite8 corrupto, upgrade duplicado y ABA del host ya en8.
- Aceptación mínima scoped: 5 nuevos JVM tests sobre cinco SQLite reales en `@TempDir`, exactamente `tests=5,failures=0,errors=0,skipped=0`; paquete DEV compila; guard B1–B7 en el mismo SHA. **No** significan X01–X12 aprobadas, ni UI/device real, ni Contactar IA/Apply.
- El contrato de diseño `S3_P00_CONTRACT_REVIEW_20261102.md` mantiene X01–X12 pendientes, con A3 Guardados +8 NO APROBADO. No abrir A3 ni reinterpretar la evidencia como final de S3.A5.

## B2 — Composición actual y V4.4
- V4.4 solo referencia de navegación/semántica read-only; no hay cutover de cinco bases + pointer8 equivalente que justifique copiar datos o código legacy.
- Reutilizar `FaroV5V2CoreDevAuthority`, `RealityStore.run` transacciones tipadas, `ConversationWorkspace`, `FaroV5FederatedSearchSurfaceAdapter`, `FaroV5G5ActiveFourOwnerSearchTest` (patrón de fixture en Reality7), y `FaroV5V2Planning8DevHostTest` (8/8, no duplicar esos oráculos).
- Probar diferencia material: **los mismos cuatro owners con datos nacidos DESPUÉS del cutover8**, sin volver a usar sesión/grafo7, con búsqueda federada y persistencia Chat a través del reinicio. Mensajes de la generación anterior solo disponibles en almacenamiento old7, no en runtime8. `pointer.sequence` es obligatorio, no basta ID.

## B3 — Investigación externa y opciones
- Guía oficial Android Data Layer https://developer.android.com/topic/architecture/data-layer : cada repositorio usa fuente única canónica, testear el grafo real en vez de una composición simulada (USAR).
- Arquitectura Android Offline First https://developer.android.com/topic/architecture/data-layer/offline-first : base local es fuente de verdad, lectura offline segura y sin requerir nube (USAR). No integrar red, WorkManager ni permisos nuevos.
- SQLite WAL https://www.sqlite.org/wal.html : concurrencia y atomicidad se aplican por DB; un test de una sola tabla no prueba la selección de cinco stores por generation/pointer (ADAPTAR prueba real multidominio).
- Opción A ELEGIDA: clase de 5 pruebas end-to-end de JVM solo en `src/testV2CoreDev` con fixture real antes/después de cutover, XML dirigido y guard. Opción B DESCARTAR: duplicar las suites 6/6+5/5+5/5+4/4+8/8 o construir mocks. Opción C DESCARTAR: activar V2 productiva o G15 para acelerar evidencia; primero se cierra todo lo automatizable. Sin nuevas librerías ni API paga.

## B4 — Delta único, pruebas y gates
1. `v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5S3A5PostCutoverIntegrationTest.kt`: (i) Chat+Finance+Objectives+Matters Search real Reality8, 4 namespaces, re-open, sin cifras/IDs sensibles; (ii) hit old7 revocado/aislado de new8; (iii) corrupción ACTIVE8 rechazada sin reinit ni retorno a7; (iv) segundo upgrade explícito rechazado por puntero8 ya activo, sin doble switch/fence; (v) ABA del puntero8 invalida puertos Chat/Search incluso al regresar al mismo ID. Datos únicamente `@TempDir`.
2. `.github/workflows/faro-v5-s3-a5-post-cutover-dev-integration-directed.yml`: habilitar propiedad AGP9 que ya usan otros workflows; compilar V2 Core DEV + SOLO los cinco tests nuevos, verificar XML exacto 5/5; CI guard obligatorio existente.
3. No modificar Activity, runtime de generación, stores, main, S2, S4, V4.4 ni schemas. Si alguna prueba roja descubre defecto real, volver a B5 con red/fix dirigido; no falsificar pasando una simulación.

## B5 — Adversarial, exclusión y criterios STOP
- Fuente vieja: ausencia de datos old7 en búsqueda new8 y no apertura de hit/token antiguo después de switch. Source labels y canonicals deben ser reales; caso de resultados parciales distinguir UNAVAILABLE de lista vacía.
- Integridad: corrupción ACTIVE8 post COMMITTED no autoriza fallback a old7, reset de base ni migración auto; pointer/journal/fence permanecen durables. Segundo upgrade no reutiliza autoridad ni crea nuevo journal.
- Revocación: tras ABA mismo generationId con sequence distinta, tanto Chat como Search deniegan la lectura y la apertura de hit previamente emitido; sesión debe cerrar.
- Test fixture debe cerrar conexiones antes de escribir y reabrir, no usar aplicación G15 ni secretos. Ningún permiso humano/Apply derivado de Search o technical principal.
- STOP si build falla o XML no se produce; guard/CI RUNNING ≠ PASS. B7 solo scoped 5/5 con commit SHA y XML verificables; A5 OPEN hasta aceptación X01–X12 + campaña física final. No usar porcentaje ficticio de S3.
