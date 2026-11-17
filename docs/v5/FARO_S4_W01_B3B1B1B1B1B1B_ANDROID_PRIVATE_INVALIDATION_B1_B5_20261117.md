# FARO V5 — S4 W01 B3B1B1B1B1B1B Android private invalidation DEV: B1–B5
Fecha: 2026-11-17. Unidad: `S4_W01_B3B1B1B1B1B1B_ANDROID_PRIVATE_INVALIDATION_DEV`.
Documento SOLO de investigación/preauditoría. **NO READY, NO B6, NO B7, NO aprobación de montaje productivo.** Base exacta `36734d60e2dfc6e25943675ce8bf2e66977d3f06`, guard en base `44134102512` SUCCESS. Writer CAS leído en rama `faro-v5-writer-lock` y adquirido epoch 1123 sólo para documentar; no confundir con checkout local.

## B1 — Evidencias de código y riesgos presentes
- La unidad anterior B3B1B1B1B1B1A foreground reconciler ya terminó B7 PASS *scoped*: código `5ed88bdf6abb26fd97eca2216dac4ef71372db89`, 8/8 JVM, compile Android DEV, run `44132878065` SUCCESS, documento B7 `docs/v5/FARO_S4_W01_B3B1B1B1B1B1A_FOREGROUND_RECONCILIATION_B7_PASS_SCOPED_20261117.md` blob `7dd0f494f103cf08d4f99ce16fb99b2c92e811c8`. No repetirla.
- SQL ACL `RealityFinance9Acl.kt` blob `0b7effb6d5d75c9cc511f957c34cd6ecd8ecf467`: `revoke()` realiza `BEGIN IMMEDIATE` y `COMMIT`, pero devuelve mismo epoch también en no-op, y otros escritores `sqlite3` en distintos PID no publican eventos. `RealityStore.kt` blob `e08a76d444b8a5756aaa5dccf7572938cd4a722f` expone revocación READ DEV, sin ContentResolver ni callback post-COMMIT. Revocation port `fe1b295cf1711a5d7b2ffc2179f6e0b8930aeebd`.
- `S4W01FinanceReadForegroundReconciliation.kt` blob `0919ac9a1d1d2d17ad4927cc980a1d6f3cb9dac6` invalida por lifecycle y eventos futuros, requiere dos SELECT nuevos por lectura. NO escucha IPC real ni certifica UI. `S4W01FinanceReadForegroundLifecycleAdapter.kt` blob `63dcc87a905960579d3ae9208e5e6bf5cfbbc83e` DORMANT, no montado en shipping.
- `v5/app/src/v2CoreDev/AndroidManifest.xml` blob `97a23a12983f1a6e9ac973a4b4948142d477c439` elimina providers con `<provider tools:node="removeAll" />`. `v5/app/src/main/AndroidManifest.xml` blob `8596555bd6e11804af69da3d3cd1e3aa7454ef2c` no contiene provider Finance; sí InitializationProvider. NO modificar manifiesto principal, ni provider de S3.
- Writer y permisos no se heredan; el permiso actual `FARO_PROTOCOL_WORK_PERMIT.json` queda BLOCKED tras B7 anterior. S3 5/5 DEV cerrado H07 excepción; W2 STOP; V2 productiva OFF.

## B2 — Comparación, reutilización y límite V4.4
- Reutilizar `Reality9` WAL epoch, revocador scoped, `S4W01B3B1B1B0ObservedReadFence`, `S4W01FinanceReadForegroundReconciliation` y lifecycle en lugar de duplicar autorización, Apply, receipts, saldos o el modelo V4.4. V4.4 mantiene AtomicApply/receipts/Undo como núcleo duradero; **no transportar una notificación como permiso de ejecución**. Ninguna otra app, main o V4.4 es ruta escribible.
- Este DELTA NO cambia SQL, no agrega ACL, no se conecta a la UI shipping. Un aviso sin consulta SQL posterior es **insuficiente**; seguir con catch-up ON_RESUME ya certificado.

## B3 — Investigación externa, clasificación y riesgos reales
1. **ADAPTAR** Android `ContentProvider` + `ContentResolver.notifyChange` + `ContentObserver` para *señal vacía*: desde API 26 un notifyChange necesita URI respaldada por provider. Documentación primaria: https://developer.android.com/reference/android/content/ContentResolver y https://developer.android.com/reference/android/database/ContentObserver.
2. **ADAPTAR** provider `android:exported=false`, autoridades `${applicationId}.finance9read` y sin `query/insert/update/delete/openFile` exitosas; detalles: https://developer.android.com/guide/topics/manifest/provider-element . Registrar con handler del main; desregistrar al cerrar. No transmitir principal, saldo, source revision ni epoch. Verificar contra merger de variant.
3. **REFERENCIA** SQLite WAL snapshot isolation: https://www.sqlite.org/isolation.html ; la recepción de aviso no equivale a visibilidad de un COMMIT mientras una lectura previa tiene snapshot abierta. Reabrir siempre el fence SQL.
4. **DESCARTAR** un URI ficticio sin provider, FileObserver o notificación pre-COMMIT; `ContentObserver` no ve cambios SQL de Python/otro PID sin publisher cooperante. No utilizar Room InvalidationTracker como remedio mágico para escritores no Room.

## B4 — Partición acotada y aceptación
Separa infraestructura **DORMANT** de integración SQL post-COMMIT. Para esta primera pieza se permiten sólo componentes del paquete app DEV:
1. NUEVO `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadPrivateInvalidationProvider.kt`: provider vacío, sin datos, sin acceso externo.
2. NUEVO `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadPrivateInvalidation.kt`: contrato URI app-private, registro/desregistro ContentObserver, evento `onExternalSignal()` del reconciliador, fail-closed ante error, y API publisher sólo DEV para *futuro* post-commit cooperante. No hacer inferencias SQL; nunca publicar al construir el owner.
3. NUEVO `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01FinanceReadPrivateInvalidationTest.kt`: 8 tests sintéticos de estados, cierre e invalidación; NO los llamar dos-PIDs Android.
4. MODIFICAR sólo `v5/app/src/v2CoreDev/AndroidManifest.xml`: provider explícito `exported=false`, autoridad exacta derivada de applicationId sin Internet. Mantener `tools:node="removeAll"` para providers ajenos; probar Android merger sin suposiciones. En main/x03/S3 shipping no declararlo.
5. NUEVO `.github/workflows/faro-v5-s4-w01-b3b1b1b1b1b1b-private-invalidation.yml`: protocolo preflight parent exact5, JUnit XML 8/8, compile DEV, merged manifest con provider propio y sin terceros; probar no shipping mount.
**NO incorporar publicación REAL desde RealityStore en esta pieza** hasta inventario de todos los paths SQL de issue/revoke/cutover y mecanismo durable de change/no-op. Es la subpieza infra B, no cierre W1. El posterior enlace post-COMMIT necesita unidad + permiso B1–B5 independiente; G15 Android IPC sigue NOT_RUN.

## B5 — Auditoría adversarial antes de READY
- I01: URI derivada de applicationId único, sin permiso exportado ni datos; método desconocido/CRUD/read devuelve rechazo, nunca saldos.
- I02: no señal durante constructor/registro, pero registro exige *catch-up* clear y nuevo doble SQL antes de lectura; si falla clear, cerrar sin permitir DTO.
- I03: evento recibido invalida estado antes de futuras lecturas; callback jamás concede principal/lease ni ejecuta Apply.
- I04: close idempotente y unregister; callbacks tardíos no reabren estado.
- I05: fallo de registro/unregister/notify obliga fail-closed; no afirmar que una revocación SQL es visible/avisada si callback falla.
- I06: Android variant V2 CORE DEV conserva aislamiento y no produce provider en producto; `tools:node=removeAll` puede suprimir un elemento si merger se configura mal => prueba merged manifest obligatoria y RED_FIX si falla.
- I07: no-op SQL revoke y escritor crudo SQLite no garantizan notify; no atribuir sincronización distribuida completa a la infraestructura. Sin garantía de revocación instantánea tras último SELECT.
- I08: pending observer callback durante PAUSE/STOP o destroy no resucita DTO; reconciliación foreground existente permanece requerida.
**DICTAMEN:** B5 de ESTE documento prepara una revisión independiente de exact5, pero NO autoriza READY automáticamente. Necesario auditar la solución propuesta, comprobar guard, exact SHA de prueba, migración de manifiesto y después permiso sólo infra. Cualquier B6 que toque store/ACL/otra ruta está STOP.

## B6
NOT_RUN; prohibido antes de READY independiente + CAS + preflight.
## B7
PENDING; validar SHA CI/XML y manifest antes de PASS; nada productivo autorizado.
