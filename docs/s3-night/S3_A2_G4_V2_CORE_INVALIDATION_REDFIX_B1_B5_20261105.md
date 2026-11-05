# FARO V5 — G4 V2 Core invalidation test RED_FIX, B1–B5 — 05/11/2026

**Unidad estricta:** `S3_A2_G4_V2_CORE_CI_COVERAGE`. HEAD revisado `3490855e3fb04f03a88da17bd54fd42e727c347f`. Rama `work/s3-night-20261102`. La prueba física es G5 y no se realiza aquí. La corrección sólo puede modificar `v5/app/src/testV2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthorityTest.kt`; no se modifica lógica real, grants ni shipping.

## B1 EVIDENCIA
- CI exacta del HEAD de código `a49b5cea329b34d15d56891a12606cd33ab27c04`: V2 Core run #42018187304 FAILURE; 10 tests, 1 falla `durablePointerSwitchRevokesDevSearchAndEmitsLiveInvalidation()` a nivel de método 366; los pasos posteriores T4 7/7 y verificación manifest no se ejecutaron. Run protocolo #42018187421 SUCCESS. HEAD de handoff documental `3490855e3fb04f03a88da17bd54fd42e727c347f` no toca código.
- El test crea el collector con `invalidations.take(2)`, supone `observed.size == 1` antes de cambiar el puntero y `==2` tras el cambio. En el host G3 anterior (`0fab63a`) la fuente de invalidaciones era sólo Workspace. G4 (`05de1c6`) ahora combina `merge(workspaceSearch.invalidations, financeGrant.invalidations, objectiveGrant.invalidations, matterGrant.invalidations)` (cuatro streams).
- `FaroV5V2CoreDevSearchReadAuthority` expone `MutableStateFlow(version).map { Unit }`: cada stream emite una vez al suscribirse, y cada uno incrementa versión en la invalidación del puntero. Por lo tanto el test de dos eventos puede concluir ANTES de que ocurra el switch; no prueba revocación viva. La CI reporta `AssertionFailedError` con el número de línea del método, no identifica el aserto exacto; la causa se fundamenta en código y se somete a prueba, no se certifica como hecho del XML.
- Aceptación: 4 emisiones iniciales verificadas, ninguna quinta antes del switch, al menos una nueva emisión disparada por el switch, Search UNAVAILABLE y hit antiguo UNAVAILABLE; 10/10 V2 Core, T4 7/7 y manifest aislado en nueva CI de SHA exacto. G4 27/27 y Reality 21/21 ya PASS, reutilizables por ausencia de cambios funcionales.

## B2 COMPARACIÓN
- V4.4 no tenía esta composición DEV de cuatro grants `StateFlow`; la comparación pertinente es G3 un-owner en el commit `0fab63a` versus G4 four-owner en `a49b5cea`. Con un owner, `take(2)` era suficiente; con cuatro, no.
- V5 G4 ya dispone de revalidación del puntero duradero, ACL y revisión por cada lectura y apertura. Esa seguridad NO depende de observar un número específico de notificaciones UI.
- Evitar reabrir el federador G4, Reality v6, SQLite, el host, la V2 productiva o G1-G3. Un test estable debe esperar estado inicial declarado y un evento posterior genuino, sin inferir revocación por conteo total.

## B3 INVESTIGACIÓN DE ALTERNATIVAS
- Fuente oficial Kotlin coroutines `merge`: https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.flow/merge.html — recolecta en paralelo y reemite de todos los orígenes, no es coalescencia de eventos. ADAPTAR la prueba a los cuatro streams.
- Implementación open source Kotlin StateFlow: https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/common/src/flow/StateFlow.kt — cada nuevo suscriptor recibe el valor inicial; las actualizaciones confluyen. REFERENCIA para test y los límites de conteo.
- Documentación oficial Kotlin `combine` y `merge`: https://github.com/Kotlin/kotlinx.coroutines/blob/master/docs/topics/coroutines-flow-operators.md — `combine` podría colapsar emisiones iniciales, pero cambia temporización del runtime y puede coalescer revocaciones; DESCARTAR por falta de bug productivo probado.
- Alternativa A ELEGIDA: sólo ajustar test para cuatro emisiones iniciales y una quinta inducida, con `withTimeout` para no colgar CI; mantener asertos de denegación posteriores.
- Alternativa B DESCARTADA: cambiar `merge` por `combine` sólo para forzar aserto antiguo. Alternativa C DESCARTADA: borrar la verificación de invalidaciones o aceptar cualquier conteo (false green). No dependencias, APIs, permisos ni catálogo nuevos.

## B4 UNIFICACIÓN
- Una ruta de test exacta, conservar las otras nueve pruebas y la API de producción.
- Importar `kotlinx.coroutines.yield` y `kotlinx.coroutines.withTimeout`. Cambiar `take(2)` por `take(5)`; esperar hasta registrar cuatro iniciales con timeout finito, comprobar exactamente cuatro ANTES de mover el puntero; después del `compareAndSet`, esperar el quinto evento con timeout y conservar `search(...).coverage == UNAVAILABLE` y `openAuthorized(hit) == UNAVAILABLE`.
- Esto detecta tanto falso evento anterior al switch como ausencia de revocación tras el switch. Si falla CI, mantener G4 abierto y no adivinar.
- Trigger V2 Core CI existente ya cubre `v5/app/src/testV2CoreDev/**` y ejecutará la suite, T4 y manifests; reutilizar gates verdes G4/Reality que el test no modifica. Requiere B7 final.

## B5 AUDITORÍA ADVERSARIAL
- Un flujo de invalidaciones `merge` puede emitir por cuatro `StateFlow` antes de cualquier mutación. `take(2)` termina prematuramente y una prueba roja por conteo de eventos no demuestra falla de ACL. Se comprueba explícitamente antes del switch para evitar falso PASS.
- Tras cambio de puntero, la sesión debe negar búsqueda y navegación incluso si el UI no consume eventos; la prueba independiente `pointerAbaCannotReviveOldSearchHitWithoutAnInvalidationSubscriber()` se mantiene intacta, al igual que journal/replay fence y reinicios.
- Timeout acotado evita espera infinita; no sustituye un PASS verdadero. El nuevo test no debe alterar la clase de producción, versión de SQLite, stores, filtros CI, rama main, V4.4, credenciales, egress, Android manifest ni empaquetado.
- **Dictamen:** READY_BOUNDED_TEST_ONLY_RED_FIX con el protocolo y writer CAS vigente. **G4 sigue OPEN** hasta CI exacta V2 Core 10/10, T4 7/7, manifest PASS y B7 final. Backup FBS1/FBS2 portable con datos G4 no está implementado y permanece fail-closed.
