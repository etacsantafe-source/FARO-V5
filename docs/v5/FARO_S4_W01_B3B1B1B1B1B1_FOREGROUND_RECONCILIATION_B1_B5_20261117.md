# FARO V5 · S4 W01 B3B1B1B1B1B1 — Post-commit Android publisher y pérdida de avisos
Fecha AR 2026-11-17. BASE `15ecb56ddf6f7fff2d1c522ceb9fddf8c03fd65a`. **DOC_ONLY B1–B5: no habilita publisher productivo, no modifica Android manifest y no re-certifica trabajos cerrados.** Siguiente candidato seguro `B3B1B1B1B1B1A`: reconciliación SQL al volver a primer plano, sin eventos externos supuestos.

## B1 — Auditoría concreta del flujo existente
- Revocador SQLite9 READ `RealityFinance9TrustedReadPolicyRevocationPort.kt` blob `fe1b295cf1711a5d7b2ffc2179f6e0b8930aeebd`, `RealityStore.kt` blob `e08a76d444b8a5756aaa5dccf7572938cd4a722f`; `RealityFinance9Acl.kt` blob `0b7effb6d5d75c9cc511f957c34cd6ecd8ecf467` ejecuta BEGIN IMMEDIATE y COMMIT, devuelve epoch revocado actual también para un no-op, NO anuncia cambio a Android. Dos PIDs de SQLite están cubiertos por B7 previo sin publisher: `B3B1B1B1B1A` code `80e46887d8b46f7030fbbe4fc9c5bc93a93cb6c9`, CI `44125813960` 24/24 PASS, no repetir.
- Consent READ DEV `S4W01FinanceReadAuthHost.kt` blob `26a12243f7d42f9722ded60782dab542b1eef6ec`: one-shot, cierre ON_STOP/ON_DESTROY B7 PASS code `fce49a3cee5b1836f7088f5cd484e12e702b0254`, CI `44129165643` 8/8 + Android compile. No enlazado a Activity/CompositionRoot de producción.
- Doble observación fresca de policy+opening `S4W01B3B1B1B0ObservedReadFence.kt` blob `c738c963bdc0eacc31f557e2597b868f3453b029`, es NUEVA POR INVOCACIÓN e invalida si cambia policyRevision, opening source, puntero, principal, host o disponibilidad. Un resultado no autoriza render.
- `FaroV5FinanceReadSurface.kt` blob `f166cde78466c6935a25423877b484d72dfd277d`: invalida Compose mediante `Flow<Unit>`, pero carece de lifecycle, TTL y productor de revocaciones de otro PID. Permanece DORMANT.
- `AndroidManifest.xml` blob `8596555bd6e11804af69da3d3cd1e3aa7454ef2c`: no app-private Finance ContentProvider. Ningún notifyChange puede cubrir SQL externo sin dueño que publique tras COMMIT.

## B2 — Reutilización sin contaminación
Reusar la doble observación `S4W01B3B1B1B0ObservedReadFence` y su `observeForDevOnly()` que hace SELECTs nuevos cada vez, más `FileActiveGenerationPointer` y validación epoch/host. No abrir transacciones, grants, saldos, receipts, Apply, manifest, ContentProvider, Base UI, V4.4/main/S2/S3, FBS2v4, W2 o V2 productiva. SIN API de pago. La capa de reconciliación no toma titularidad de SQLite ni identidad humana.

## B3 — Fuentes y alternativas
- Android `ContentResolver` https://developer.android.com/reference/android/content/ContentResolver : desde Android O notificaciones deben estar respaldadas por ContentProvider; NO basta usar URI ficticia.
- `ContentObserver` https://developer.android.com/reference/android/database/ContentObserver : callback es aviso en cola, no certificado de SQL, no garantiza entrega durante proceso muerto ni revocación por Python/sqlite3.
- Android `LifecycleResumeEffect` https://developer.android.com/topic/libraries/architecture/lifecycle/ : limpiar al perder foreground y releer al `ON_RESUME`; **SELECCIONADO** para preparación por DEV owner LifecycleEventObserver sin shipping UI.
- SQLite https://www.sqlite.org/isolation.html : una lectura WAL anterior al COMMIT no representa current policy después. Un owner que reciba una notificación debe limpiar antes de leer desde WAL fresco.
- **RECHAZAR**: provider productivo sin inventario completo de write paths, usar notifyChange dentro de BEGIN IMMEDIATE, polling mientras muestra saldos viejos, o notificar idempotencia SQL no-op como transición de epoch.

## B4 — Partición de unidades y alcance de código
`B3B1B1B1B1B1A` — **NEXT exact4 después de B5 independiente**: owner DEV de reconciliación de foreground, sin registrar ContentObserver todavía. Al ON_PAUSE/ON_STOP/ON_DESTROY invalidar cualquier DTO retenido; al ON_RESUME, eliminar lo anterior ANTES de permitir una NUEVA doble observación de Reality9; callback externo futuro también invalida. No almacenar balances, sin UI shipping y sin garantizar no-race después del último SELECT. EXACT4:
1. NUEVO `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadForegroundReconciliation.kt` (estado lógico + fábrica de doble SQL-fence, resultados solo DEV).
2. NUEVO `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadForegroundLifecycleAdapter.kt` (LifecycleEventObserver inerte hasta mount DEV real, no Compose).
3. NUEVO `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01FinanceReadForegroundReconciliationTest.kt` (ocho tests NEW focused lost notice/races/ABA).
4. NUEVO `.github/workflows/faro-v5-s4-w01-b3b1b1b1b1b1a-foreground-reconcile.yml` (preflight de parent, JVM 8 XML, Android DEV compile, no shipping).
`B3B1B1B1B1B1B` — Publisher app-private Android, luego del A: auditar productores de `issue/revoke`, registrar provider `exported=false` en flavor **DEV aislado**, publicar únicamente después del COMMIT realizado por owner exacto, descartar no-op; subscriber con `onStart` + `onResume` catch-up SQL (después) y prueba instrumental dos PIDs Android. No declarar cobertura sobre escrituras directas externa, y no activar saldos UI. Esta pieza NO obtiene READY ahora.
`B3B2` — integración de UI DEV + pruebas físicas Android G15 y cierre de ventanas de render, scope separado, NO_READY.

## B5 — Auditoría adversarial del NEXT y del publisher futuro
- Durante el STOP/PAUSE cualquier instantánea previa se borra de memoria del reconciliador. RESUME no muestra cache: exige fuente doble-fresh, y se verifica `activePointer`, `hostEpoch`, principal SHA, policyEpoch, globalPolicyRevision y sourceRevision al usar el `S4W01B3B1B1B0ObservedReadFence` existente. No existe lease UI.
- Una revocación por otro PID DURANTE STOP no avisa; RESUME limpia y al nuevo SELECT el ACL revocado devuelve null, incluso sin notifyChange. **R01–R08**: never read before RESUME; STOP cancels; resume revalidates NEW source; lost external notice denied; pointer ABA denied; revision drift denied; concurrent invalidation during double SELECT discarded; callback exception/close poison.
- Eventual notice de publisher no debe incluir principal/epoch/saldo en payload. La novedad del epoch NO se infiere del valor retornado por `revoke` idempotente: hace falta comparar revisión COMMITTED y separar commits externos. No escribir notifyChange antes del SQL COMMIT ni fingir 100% cobertura de autores.
- Ningún test JVM de `ON_RESUME` sintético certifica callbacks reales de FragmentActivity, IPC ContentResolver ni Android G15. La compilación DEV prueba tipo/wiring, no comportamiento físico.
- Una revocación después del último SELECT y antes de dibujar sigue siendo posible; un reconciliador que solo devuelve DTO no certifica UI. No declarar PRODUCTIVO ni cerrar W1.

**DICTAMEN B5 DOCUMENTAL**: GO para B5 independiente del `B3B1B1B1B1B1A` exact4 y luego preflight+CI del DEV owner; STOP para publisher/manifiesto, pantalla, controles humanos y W2.

## B6
NOT_RUN, DOC_ONLY.
## B7
PENDING de `B3B1B1B1B1B1A`; su permiso individual se crea solo después del B5 independiente, comprobaciones de HEAD, writer, guard y blobs exactos.
