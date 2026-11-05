# FARO V5 — S3.A4 AVISOS, corrección acotada de puntero ABA · B1–B5 (05/11/2026)

**Unidad:** `S3_A4_AVISOS_POINTER_ABA`. **Estado:** B1–B5 PREAUDIT_VERIFIED (diseño/amenazas, NO código), B6 PENDING, B7 PENDING. Ningún contrato nuevo, ningún cambio de catálogo, V2 productiva OFF, S2 8/8 congelado, S4 OFF, Auto/Noche OFF. Esta preauditoría permite únicamente emitir un permiso de tres rutas en commit separado y probar la unidad dirigida; no certifica A4 completo.

## B1 — Evidencia y diferencia reproducible

HEAD revisado `ed20b456b9260ff4d50c6e70c2cbf6c0df18162e`, escritor compartido CAS; `docs/v5/FARO_CONTINUITY_POINTER.json`; `docs/s3-night/S3_A1_FORMAL_SCOPE_RECONCILIATION_20261105.md`. S3.A1 y A2 cerradas solo alcance automatizado; A3 bloqueada por 8 contratos Guardados NO aprobados; A4 puede avanzar de manera independiente.

Código exacto inspeccionado:
- `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ActiveAvisosBindingFactory.kt` blob `fea95d1da739233c6b56fb5ea25530578e4c7acf`: `admitted()` comprueba solo `startupReady` y `activeGeneration == expectedGeneration`. Después de A→B→A con mismo ID, un lector de vieja instancia puede pasar.
- `v5/app/src/main/java/ar/com/faro/v5/app/FaroV5CompositionRoot.java` blob `730a8d869530d9178cd8444aa06ba0e04f81195d`: `readActiveAvisosPort()` llama `create` sin comparar `graph.pinnedPointer` con `graph.searchPointer.read()`, aunque `readActiveHomePort()` inmediatamente debajo **sí usa** `FaroV5HomeNoticeBindingFactory.createPinned` con esa comparación. `DeliveryGraph.matchesActivePointer` ya expresa el mismo fence.
- `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5ActiveAvisosBindingFactoryTest.kt` blob `f7d335ecdf2225025ac74e6a28d806dd8b79a4b3`: cuatro tests existentes de orden, privacy, indisponibilidad y límite 100, **sin ABA de puntero**.
- `v5/surface/src/main/kotlin/ar/com/faro/v5/surface/FaroV5AvisosSurface.kt` blob `819f30c000dd0d759170b5ddcf155b43fd949d53`: UI sólo lectura, invalida antes de releer; sin mutación implicitamente.
- CI existente `.github/workflows/faro-v5-s3-home-notice-directed.yml` incluye suite de Avisos 4/4, compile app e identidad de host. Mantener exactamente esas cuatro pruebas, aumentando las negativas en las pruebas existentes; NO cambiar CI en esta unidad. CI nuevo se exigirá en SHA de código nuevo, no reutilizar CI histórica como su resultado.
- **Aceptación binaria**: un port de Avisos viejo falla cerrado cuando la secuencia durable del puntero cambió aunque el ID sea igual, antes de leer y si cambia durante la lectura. No perder lectura legítima ni mutar notice, No disponible ≠ cero.

## B2 — Comparación V4.4 y V5, reaprovechamiento

La comparación V4.4 READ_ONLY disponible en `docs/s3-night/S3_P00_CONTRACT_REVIEW_20261102.md` conserva separación de owner, aviso no-disponible y semántica de navegación; no se presume una implementación V4.4 de pin-sequence comparable al nuevo runtime V5. Para resolver este defecto del runtime V5 la comparación de código **directa** es `FaroV5HomeNoticeBindingFactory.createPinned` y `DeliveryGraph.matchesActivePointer`, ambos ya implementados; un contador de Inicio sí hace fence completo mientras Avisos no. **REUSAR** proveedor de secuencia existente; no crear nuevas fuentes de autorización, no copiar código V4.4 ni reabrir A1.

## B3 — Investigación externa y alternativas con juicio técnico

**Fuentes oficiales verificadas en este turno:**
1. Android Room InvalidationTracker: https://developer.android.com/reference/androidx/room/InvalidationTracker — `Flow` y observadores notifican invalidación de tablas, **no aportan por sí mismos** autoridad, identidad de generación ni fence ABA. **REFERENCIA** para notificaciones; **DESCARTAR** como solución exclusiva.
2. Android Room 3 MultiInstanceInvalidationService: https://developer.android.google.cn/reference/androidx/room3/MultiInstanceInvalidationService — servicio remoto para invalidaciones entre instancias, no sustituye lectura durable del puntero ni se requiere agregar otro proceso en FARO. **DESCARTAR PARA ESTA UNIDAD** por complejidad extra y falta de necesidad.
3. Kotlin Flow `collectLatest`: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/ — cancela lectura anterior cuando llega invalidación nueva; ya se usa en superficie. **REUTILIZAR**, pero un evento perdido o lectura tardía exige fence antes/después.

**Alternativas comparadas:** A) ID de generación únicamente: inseguro ante A→B→A, DESCARTAR; B) notificación Flow únicamente: ventana de carrera, DESCARTAR; C) nueva tabla/owner de sesión: agrega authority duplicada y migración, DESCARTAR; D) BooleanSupplier del puntero durable actual comparado contra el `pinnedPointer` del graph, comprobado pre/post y con el Flow existente: **ADAPTAR/USAR** (mínimo delta, fail-closed, offline, no pago).

## B4 — Unificación DELTA ONLY

Rutas funcionales autorizables:
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/FaroV5ActiveAvisosBindingFactory.kt`: método `createPinned` con `BooleanSupplier pinnedPointerCurrent`; `admitted` exige `startupReady && generationId match && pinnedPointerCurrent`. Preservar `create` para compatibilidad de consumidores existentes; `createPinned` es el camino productivo explícito.
2. `v5/app/src/main/java/ar/com/faro/v5/app/FaroV5CompositionRoot.java`: únicamente `readActiveAvisosPort()` pasa lambda basada en `graph.pinnedPointer` / `graph.searchPointer.read()`, idéntica a la que ya usa Inicio. No alterar Chat, Search, app V2, storage ni contratos.
3. `v5/app/src/test/kotlin/ar/com/faro/v5/app/FaroV5ActiveAvisosBindingFactoryTest.kt`: extender pruebas existentes sin incrementar total de métodos (4/4 XML actual): puntero stale pre-lectura no toca store; revocación durante `readCanonical` devuelve `null`; lectura con pin válido funciona; después de A→B→A mismo ID se deniega lector viejo. Mantener tests de privacidad, prioridad y límite.

CI: `faro-v5-s3-home-notice-directed.yml` existing push path `v5/app/**`, test Avisos 4/4, además compila `app` y ejecuta resto de pruebas existentes. Guard de siete bloques y preflight READY obligatorio antes de código. No nueva Action.

## B5 — Auditoría previa adversarial y límites de aceptación

- **ABA**: secuencia pasa gen A→B→A, ID igual; pin viejo distinto → `readNotices()==null` sin leak.
- **TOCTOU**: revalidar después de `readCanonical.get()`; si cambia el pin durante lectura, descartar página completa (no cache).
- **Fail closed**: puntero `null`, cerrado o lector `searchPointer.read()` lanza excepción → no read/zero artificial. En cambio un pin igual con ready+generation válidos devuelve datos reales.
- **Privacidad**: `SENSITIVE` sigue redactado; `SEEN` no auto marca; `DISMISSED/RESOLVED` no aparecen; sin efectos Android NotificationManager, Reality, Workspace o receipts.
- **Compatibilidad/negativa**: el adapter `create` sin pin es puente de compatibilidad local y no debe usarse en ruta productiva; chequear invocación única de `readActiveAvisosPort()` y compilar Java→Kotlin.
- **Datos/recuperación**: no migraciones, no cambio de cinco stores, ni escritura en teléfono; tests simulan punteros, **no** sustituyen campaña física X01–X12 en S3.A5. No afirmar seguridad cross-process o ACL que no demuestra esta prueba.
- **Gate final**: B6 solo tras permiso `READY` por esas tres rutas, mismo SHA observado y CI de protocolo preflight PASS, commit `FARO-UNIT:S3_A4_AVISOS_POINTER_ABA`; B7 separado con resultados reales de pruebas y XML por SHA, luego `BLOCKED`.

**B5 veredicto pre-implementación:** APROBADO PARA EMITIR PERMISO ACOTADO, no PASS de implementación. No modifica las 8 operaciones Guardados ni autoriza otras funciones A4.
