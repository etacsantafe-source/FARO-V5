# FARO V5 W01 B3B1B1B0 — Contraste adversarial separado B1–B5 + READY EXACT3
Fecha AR: 2026-11-17. Unidad `S4_W01_B3B1B1B0_FINANCE9_STAGED_READ_SESSION_FENCE`. Revisado HEAD `7106d2abec18646c28dc9b68ae19f050a9063f6a`.
Preauditoría B1–B5 `docs/v5/FARO_S4_W01_B3B1B1B0_CROSS_PROCESS_READ_FENCE_B1_B5_20261117.md`, blob `7e2364b16c1aaa7c83d852d26218671961440f24`; guard `44094780996` SUCCESS. SOLO DEV/STAGED9. Nunca root trust/ACL shipping.

## B1
El port `RealityFinance9ObservedSourceReadPort.observe(request)` (B3B1B0 B7) expone un snapshot de política y opening en una transacción; `RealityFinance9ReadPolicyObservationRequest` acepta identidad y epoch suministrados por código caller, no credenciales. `ActiveGenerationPointerPort.read()` incluye generationId y sequence. `FaroV5FinanceReadPort` aún no está montado. B3B1B1A mapper code `78761e59d19b21fac9ec77beca71c736635a6e27` B7 8/8 PASS y 5/5 Actions; no repetir.

## B2
Preservar lector SQL, ACL, DTOs, SQLite9, Compose, RealityStore, M1B Keystore, catálogo180, V4.4, FBS2v4, Apply/receipts y S2/S3. En particular, no modificar un solo método de B3B1B0. El nuevo componente sólo tiene el propósito de comprobar entre lecturas y no presenta números en UI.

## B3
SQLite WAL https://www.sqlite.org/isolation.html: una snapshot no ve revocaciones posteriores; dos snapshots completos sirven para detectar revoke ENTRE snapshots si la segunda es nueva. Android https://developer.android.com/reference/android/os/FileObserver: eventos pueden avisar cambios pero no equivalen a una transacción ni autoridad. Android lifecycle https://developer.android.com/guide/components/activities/activity-lifecycle: reválidar al RESUME y limpiar al STOP en host posterior. Se rechazan autorización por callback, `FileObserver` como único gate, permiso Apply para READ, y falsos vales de UI. Sin dependencias nuevas.

## B4
Autorizar exactamente TRES nuevas rutas de DEV:
1. `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01B3B1B1B0ObservedReadFence.kt`: `internal class S4W01B3B1B1B0ObservedReadFence`, constructor con full pinned pointer, `ActiveGenerationPointerPort`, expectedHostEpoch, `hostEpochOrNull`, `hostReady`, `principalHashOrNull`, expectedPolicyEpoch, `RealityFinance9ObservedSourceReadPort`, `nowMs`. Método `observedForDevOnly()` devuelve `RealityFinance9ObservedSourceRead?`, NUNCA un `FaroV5FinanceUiSnapshot` ni `FaroV5FinanceReadPort`. Comprobar pointer exacto/host antes/entre/después, hash formato 64hex estable, now fresco >=0. Dos `source.observe` con requests de mismo principal, generation, sequence y epoch; validar policy response exacta en ambas; comprobar igualdad estructural de resultados (epoch, global revision, opening sourceRevision y cuentas). Null, incongruencia, excepción o cierre => null. No usa `BEGIN IMMEDIATE`, no escribe SQL ni persiste caché.
2. `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01B3B1B1B0ObservedReadFenceTest.kt`: ocho casos JVM por nombre F01–F08: permiso/hashes inválidos; lectura doble exacta; revocación entre lecturas; pointer ABA; host epoch/ready cambia; policy revision/source cambia; excepción y expiración; close/no cache tras cambio. Tests usan DTOs y principal *sintéticos*, no certifican Android.
3. `.github/workflows/faro-v5-s4-w01-b3b1b1b0-fence-directed.yml`: workflow `:app:testDebugUnitTest --tests ...ObservedReadFenceTest`, `:app:compileV2CoreDevKotlin`, parseo XML exact8 y check de cero mención del nuevo tipo en CompositionRoot/Navigation/Activity.
NO incorporar `FaroV5FinanceReadPort`, Surface, Android lifecycle, Activity, Manifest, SQL, FBS2, Finance Apply, real grants o red. El host REAL B3B1B1B1 es futura unidad con permiso distinto.

## B5
Control adversarial distinto del diseño B1–B5:
- Doble lectura NO garantiza que un tercero no revoque justo DESPUÉS de la segunda; por diseño el nuevo componente no puede proporcionar ni acreditar permiso para una pantalla. `ObservedSourceRead` sigue siendo una observación.
- `principalHashOrNull` y `expectedPolicyEpoch` son inyectables, incluso forjables: por eso este componente es mecánico DEV, y el host futuro B3B1B1B1 deberá reemplazar la fuente por una raíz AndroidKeyStore+UID validada y read issuer separado del write grant. Prohibido adaptarlo directamente al UI.
- Comprobar valor exacto pointer sequence para ABA; `hostReady` no reemplaza lifecycle real. Eventos de revocación remota no garantizan cero-latencia, ni siquiera con FileObserver, mientras Compose esté visible; host futuro debe instrumentar writer/revocation y limpiar tras invalidación.
- Dos `observe` sobre una conexión Store con transacciones propias son lecturas completamente separadas, no nested BEGIN; se compara su valor, nunca se pretende un snapshot distribuido.
- Fail closed para null/expired/foreign/wrong epoch/new policy/global revision/changed opening/closed/exception; cero side effects.
**Dictamen B5 acotado:** PASS para el componente inerte de doble comprobación con los límites anteriores y EXACT3. NO PASS para B3B1B1B1 (host real), read issuer, notificaciones interproceso, biometría G15 ni UI productiva.
## B6
READY_TO_IMPLEMENT sólo tras guard documental + preflight exact3. NO CODE en este commit.
## B7
PENDING_FINAL_AUDIT. Exigir código exact3, XML 8/8, compilación DEV, guard, checkpoint y writer RELEASED. W2 STOP y V2 productiva OFF.
