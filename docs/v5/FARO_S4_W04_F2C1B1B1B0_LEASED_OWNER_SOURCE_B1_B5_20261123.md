# FARO V5 — W04 F2C1B1B1B0: los dos owner scopes leen stores ACTIVE reales bajo un único lease (DEV)

Fecha 2026-11-23, branch `work/s3-night-20261102`, baseline `9330d47bda675da5bb4a9e40a92129d33377924c`, writer CAS epoch1724.
Unidad funcional acotada: `S4_W04_F2C1B1B1B0_LEASED_OWNER_REAL_SOURCE_DEV`. **No certifica Android D4/E ni autorización productiva, F2C1B1B1B global sigue OPEN.**

## B1 — Evidencia y brecha
- F2C0 `FaroV5Household10Evidence2DevSelectionReadPort` mantiene **un** `Household10DevReadSession` activo Household10/Evidence2, apunta al mismo `ActiveGenerationPointer`, mantiene `finance9-dev-root.lock` y valida el esquema; F2C0 B7 original 6/6 no repetir.
- F2C1B1B0 host Android negativo B7 PASS 5/5 CI 45181057697; no tiene owner ACL real. F2C1B1B1A `Household10DevReadScopeOwner` y `CommercialEvidence2DevReadScopeOwner` B7 6/6 XML CI `45188613035`, commit `8a58d9f044a0c8b5d1e10abeba170d9ab5c99b22`, B7 checkpoint `9330d47bda675da5bb4a9e40a92129d33377924c`; son **fuentes sintéticas JUnit**, aún no link real.
- Lo que falta: un host de generación que obtenga snapshots de los **owners reales** sin crear otra conexión o segundo lease, los inyecte a sus dos scope owners, emita nonces **solo tras decisión humana confiable inyectada** y use las dos verificaciones en cada lectura F2C0. El dueño de la decisión humana será el futuro host Android F2C1B1B1B1, no la fuente SQLite.
- Scope de este delta: test JVM **de una generación ACTIVE Household10/Evidence2 real** de la fixture sellada F2C0, con 4 pruebas nuevas (t07–t10) sin volver a ejecutar t01–t06. ReadOnly; nunca modifica base activa durante lectura.

## B2 — Reutilización / V4.4
No importar base, UI o ACL de V4.4. Reusar F2B2C cutover, F2C0 lease y port, modelo `HouseholdNeed`, snapshot `HouseholdNeedsSnapshot`, snapshot tipado comercial y los dos scope owners de F2C1B1B1A. V4.4 UX histórico solo orienta a seleccionar antes de mostrar; el presente delta no toca UI. No duplicar grafo, ni gestor de locks, ni propietarios ni transacciones.

## B3 — Alternativas
Documentación primaria: `https://sqlite.org/isolation.html` (snapshot aislado) y `https://sqlite.org/wal.html` (WAL), `https://developer.android.com/guide/components/activities/activity-lifecycle` (la próxima unidad requiere cerrar al pausar), `https://mas.owasp.org/MASVS/07-MASVS-AUTH/` (consent no equivale a authorization).
1. Reabrir SQLite para cada owner: DESCARTAR, replica graph y causa lecturas de distintas generaciones.
2. Usar una pareja de callbacks `{true}` o el issuer de consent como ACL: DESCARTAR, origen no confiable.
3. Adaptar el `Household10DevReadSession` existente con **una fuente interna de Evidence tipada que revalida lease + pointer + host**, y proyectar `HouseholdNeedsSnapshot` a partir de `readNeeds` dentro de la misma sesión; inyectar fuentes reales en dos owners distintos, conceder scoped tokens efímeros tras `trustedHumanDecision` DEV: ELEGIR.
4. Extender esquema SQL Finance9 ACL a N-033/N-034: DESCARTAR para esta unidad; migración nueva, posible confusión de authority y mayor riesgo. Autorización de Android real se certificará aparte.

## B4 — Diseño y CUATRO rutas exactas
1. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5GenerationStoreGraph.kt` (existente): añadir a `Household10DevReadSession` método **internal, solo DEV** para leer Evidence2 tipada durante decisión de owner, bajo mismo gate/lease/pointer; exige callback host + revalidación antes/después. No exponer store mutable ni abrir DB.
2. `v5/store/generation/src/main/kotlin/ar/com/faro/v5/generation/FaroV5Household10Evidence2DevSelectionReadPort.kt` (existente): añadir factory `openOwnerBackedDev` con session única, con root/staged/pointer verificados como F2C0, technicalPrincipal y epoch, NEED/PRODUCT exactos, source Household desde `session.readNeeds`, Evidence desde nuevo método, ambas políticas independientes `issueAfterTrustedOwnerDecision` y `admitted` con nonces. Factories deben cerrar ambas políticas y session al fallar; close de port revoca primero ambas, después root lease. Restricción `trustedHumanDecision` es callback de **composición DEV confiable**, no bandera de Intent; Android real debe probarla aparte. No cache de precios/autoridades.
3. `v5/store/generation/src/test/kotlin/ar/com/faro/v5/generation/FaroV5Household10Evidence2DevSelectionReadPortTest.kt` (existente): agregar **solo t07–t10** bajo fixture real ya certificada: positivo exacto una necesidad+producto schema2, denegar decisión no confirmada y liberar lease, revocar por epoch/foreground sin ABA, rechazar producto legado/missing sin root leak. No ejecutar t01–t06 certificados.
4. `.github/workflows/faro-v5-s4-w04-f2c1b1b1b0-owner-backed-source.yml` (nuevo): protocolo, preflight exact4, Java21 Gradle 3 GiB, `:store:generation:test` con `--tests` exacto para t07..t10, verificar 4/4 XML real, publicar original. No emulador, no APK, no cuentas, pagos, red ni servicios pagados.

## B5 — Auditoría adversarial / alcance
PASS de diseño para **este** seam DEV si: (a) mismo graph/lock/SHA+secuencia, (b) cada owner toma fuente real de su módulo y no de otra `Activity`, (c) ambos necesitan host+decisión comprobables, y la decisión no es una ACL productiva, (d) permisos ligados a principal/generation/epoch/need/product, se invalidan permanentemente al cambiar pointer, scope, dataset o foreground, (e) ningún permiso aislado deja leer con el otro faltante, (f) close libera mismo OS lease aun ante fallo de uno, (g) menos datos comerciales expuestos y NO cached record, (h) XML de cuatro tests reales, (i) no confundir una prueba JVM inyectada con autenticación humana en Android.
**STOP global** F2C1B1B1B hasta que Android real presente un gesto de selección de producto/observación, emita las decisiones por host autenticado, proyecte F1/D4/E y cierre en lifecycle con CI API35. No activar V2 productiva ni Apply, pagos, stock, Noche/Auto, G15, main o V4.4. No afirmar B7 sin CI original.
