# FARO V5 W01 D1B1B — Session ACTIVE única → pantalla Finanzas DEV — preauditoría B1–B5
Fecha: 2026-11-17. Unidad: `S4_W01_D1B1B_SINGLE_ACTIVE_SESSION_FINANCE_UI_DEV`. Estado inicial: DOC_ONLY, no READY, no B6 ni B7. Base exacta: `0886082dd3e1b10bf0b10a43fd702b26cd15d6aa`. W1 OPEN; W2 STOP; V2 productiva OFF; seguridad biométrica opcional, OFF por defecto.

## B1 — Evidencia real y aceptación
- Rama `work/s3-night-20261102`, HEAD `0886082dd3e1b10bf0b10a43fd702b26cd15d6aa`; permisos push confirmados; writer externo RELEASED epoch 1157 antes de adquirir esta unidad. Runs D1B1A `44191824683` SUCCESS (10/10 JVM y W01 Kotlin compile) y guard `44191824581` SUCCESS; **no repetir** D0, D1A, D1B0, D1B1A.
- `FaroV5W01FinanceDevActivities.kt` blob `20fda6da5fc4a5aa32e85ecd176f3bd3cca0364c`: launcher abre una Activity no exportada que **deniega toda lectura**; el texto antiguo invoca biometría inexistente. `w01FinanceDev/AndroidManifest.xml` blob `4b49f2072135fbd965a41193aa57b166683aa0ad`: W01 aislada, quita permisos biométricos y red.
- `FaroV5V2CoreDevActivity.kt` blob `724e8f3c6bdc4697914851d9c449697f2f78ae36`: `session` única privada, `openLocalChat()` abre graph ACTIVE, `onPause` revoca grants de búsqueda sin cerrar graph, `onDestroy` cierra Session.
- `FaroV5V2CoreDevAuthority.kt` blob `78a394142ad68760d9449bca2dc5dc0de8f00cd6`: owner `graph.reality.financePersistence.read()`, `openingPointer` y `ready` reales. Reusar este owner; prohibido abrir otro RealityStore/SQLite.
- `S4W01D1B1AActiveFinanceSnapshotRead.kt` blob `dda8b3cb753c81ab2442baacf50135dbe9023d4c`: doble lectura, UID, paquete W01, pointer completo/epoch, ACL de sesión, foreground, overflow y cierre fail-closed; DTO etiquetado `ACTIVE_FINANCE_SNAPSHOT_NOT_FINANCE9`, no UI lease.
- `FinanceSnapshot` blob `22f9444f931aff356836bf61bffb10e9975142b7`: Finance ACTIVE de versión anterior; `Finance9` sigue STAGED-only. No fabricar saldos ni declarar `Finance9 ACTIVE`.
- Criterio: un solo graph, misma Session y pointer; apertura por toque explícito Finanzas, vista W01 propia en foreground sin PIN/huella; si owner no disponible, mostrar UNAVAILABLE en vez de 0; nunca renderizar montos Finance8 como Finance9 verificados; volver a Chat no duplica conexión.

## B2 — Reutilización V4.4/V5
V4.4 mantenía AtomicApply, receipts y recuperación como invariantes; no modificar esos componentes ni migraciones. Reusar exactamente: Session del launcher, puerto FinanceSnapshotReadApi ACTIVE de Authority, lector D1B1A, UI `FaroV5FinanceReadScreen` y `FaroV5FinanceUiSnapshot`, lifecycle D1B0 y manifiesto W01 sin exportación. Diferencia nueva acotada: traspaso de la **referencia de Session** dentro del mismo proceso para lectura sólo informativa, nunca mediante Intent/Bundle, sin segundo graph, sin `BiometricPrompt`.

## B3 — Investigación y alternativas externas
Documentación Android (2026-11-17):
- https://developer.android.com/guide/components/activities/activity-lifecycle : `onPause/onStop/onDestroy` son transiciones distintas; la Activity anterior puede seguir viva al abrir la siguiente. **REFERENCIA**; cerrar préstamo al salir de foreground y fallar cerrado si muere owner.
- https://developer.android.com/topic/architecture/data-layer : repositorio con fuente de verdad única y dueño bien delimitado. **ADAPTAR**; una Session y DAO existente.
- https://developer.android.com/topic/architecture/recommendations : UI observando estado/flujo; preferir estado indisponible explícito al saldo supuesto. **REFERENCIA**.
- https://developer.android.com/reference/androidx/lifecycle/ProcessLifecycleOwner.html : callback de proceso puede ser retardado y no aísla Activity por sí solo. **DESCARTAR** como única valla.
Alternativas: (A) abrir segundo graph en Finance Activity, DESCARTAR, owner duplicado; (B) Finance9 STAGED abierto como ACTIVE, DESCARTAR, contrato impide; (C) biometría por defecto, DESCARTAR, decisión humana; (D) reemplazar launcher/Chat por una Activity nueva, DESCARTAR por regresión; (E) **ELEGIR** préstamo one-shot de Session del launcher con expiración y foreground de pantalla, misma instancia FinanceSnapshotReadApi, `D1B1A` por lectura, invalidación en pause y UI sin montos aún no verificados. No librerías externas ni APIs pagas.

## B4 — Delta funcional exacto, pruebas y unidades
Rutas permitidas **exactas** y sólo ellas:
1. `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevActivity.kt` — hook por defecto inert para Session abierta, sin cambiar comportamiento de otras variantes.
2. `v5/app/src/v2CoreDev/kotlin/ar/com/faro/v5/app/FaroV5V2CoreDevAuthority.kt` — préstamo interno Finance ACTIVE usando `graph.reality.financePersistence.read()` y D1B1A, host UID/pointer reales; revoke no destruye Session.
3. `v5/app/src/w01FinanceDev/kotlin/ar/com/faro/v5/app/FaroV5W01FinanceDevActivities.kt` — click único, handoff same-process sin Session por Intent, pantalla Compose y lifecycle revoke.
4. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01D1B1BActiveFinanceUiMapper.kt` — presentador **PARTIAL/unverified** por cuenta o confirmado vacío sólo de owner ACTIVE; sin balances Finance9.
5. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01D1B1BActiveFinanceUiMapperTest.kt` — 6 tests JVM nuevos (no repetir D1B1A).
6. `.github/workflows/faro-v5-s4-w01-d1b1b-single-session-ui.yml` — preflight de las seis rutas en parent READY, 6 JVM y compilación W01.
Aceptación: `FARO-UNIT`, gate, CI SHA y XML exactos; ningún cambio a main, S2/S3, W2, Finance9, manifest, banco, red o configuración productiva. La pantalla es DEV; prueba física/G15 y Finance9 ACTIVE quedarán unidades propias.

## B5 — Adversarial preliminar
STOP si la Session no existe antes del toque, `application.packageName` no W01, launcher/Activity destruidos, borrower reused, UID diferente, pointer ABA, owner closed, host epoch/ACL cambiado, Activity no RESUMED, fuente cambia entre lecturas, overflow o proceso reiniciado. Nunca transportar el objeto de sesión por Intent; broker privado estático de un solo uso con TTL breve y cancelación en destroy. El préstamo se cierra en onPause y **no** se restaura automáticamente al volver de background. Evitar mostrar montos Finance8 como Finance9 y evitar presentar `UNAVAILABLE` como cero. Auditar independientemente antes de READY.

## B6
NO_RUN_PRE_READY.

## B7
PENDING_FINAL_AUDIT. No inventar PASS.
