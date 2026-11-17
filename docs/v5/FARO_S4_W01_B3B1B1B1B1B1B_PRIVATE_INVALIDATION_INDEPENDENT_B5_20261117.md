# FARO V5 — W01 B3B1B1B1B1B1B B5 adversarial independiente de invalidación Android DEV
Fecha: 2026-11-17. MODO DOC_ONLY. Base B1–B5: `docs/v5/FARO_S4_W01_B3B1B1B1B1B1B_ANDROID_PRIVATE_INVALIDATION_B1_B5_20261117.md`, blob exacto `4a67804e336d89583be89689a9fabce40f980c49`; guard `44149642229` SUCCESS sobre `84506f3f40fa326aa8459dff216b811be9e79110`. Unidad propuesta `S4_W01_B3B1B1B1B1B1B_ANDROID_PRIVATE_INVALIDATION_DEV`.
## B1 — Scope exacto, precedencia
B7 anterior `5ed88bdf6abb26fd97eca2216dac4ef71372db89` y su documento `7dd0f494f103cf08d4f99ce16fb99b2c92e811c8` NO se repiten. El contrato SQL no emite eventos y devuelve epoch tanto por revocación como por no-op; ninguna nueva señal aquí se reclama post-commit de forma garantizada. Releer writer, HEAD y permiso antes de B6.
## B2 — Descartes explícitos
NO modificar `RealityFinance9Acl`, `RealityStore`, el revocador, Compose, `FaroV5CompositionRoot`, main manifest, V4.4, S2/S3, FBS2v4, schemas, Apply/receipts, W2, ni V2 productiva. NO montar esta infraestructura en Activity. V4.4 y contratos persistentes siguen siendo autoridad; las notificaciones no.
## B3 — Contraste independiente con documentación primaria
Android desde O exige URI respaldada por provider: https://developer.android.com/reference/android/content/ContentResolver . Provider `exported=false`, autoridad dinámica y sin CRUD: https://developer.android.com/guide/topics/manifest/provider-element . `ContentObserver` con Handler entrega callbacks en cola y no certifica DB: https://developer.android.com/reference/android/database/ContentObserver . SQLite WAL mantiene snapshot entre procesos: https://www.sqlite.org/isolation.html . Elegir infra provider privado + callback sin payload; descartar URI falsa, polling, FileObserver o confiar en notificación sin SQL fresco.
## B4 — EXACT5 y CI
1. NUEVO `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadPrivateInvalidationProvider.kt`
2. NUEVO `v5/app/src/main/kotlin/ar/com/faro/v5/app/S4W01FinanceReadPrivateInvalidation.kt`
3. NUEVO `v5/app/src/test/kotlin/ar/com/faro/v5/app/S4W01FinanceReadPrivateInvalidationTest.kt`
4. SOLO `v5/app/src/v2CoreDev/AndroidManifest.xml` (mantener `removeAll` histórico y registrar explícitamente sólo proveedor DEV).
5. NUEVO `.github/workflows/faro-v5-s4-w01-b3b1b1b1b1b1b-private-invalidation.yml`
Pruebas dirigidas: 8 JVM exactas con XML + `:app:compileV2CoreDevKotlin`, inspección del merged manifest **real**, provider sin export ni paths de datos, revisión de no mount/product. Preflight CI en HEAD padre del código. Si merge elimina provider o reintroduce InitializationProvider, RED_FIX; jamás aprobar por mera presencia en source.
## B5 — Ataques y criterios de STOP
I01 SIN SQL/provider accesible externamente y sin lectura de saldos. I02 registro obliga limpiar estado previo y nunca devuelve permiso. I03 callback invalida reconciliador ya certificado, no retorna DTO. I04 unregister y close idempotentes, callbacks tardíos ignorados. I05 excepción CLEAR o register hace fail closed; unregister error no reclama garantía de listeners; lo ya publicado no resucita. I06 XML merged variant v2CoreDev contiene sólo provider DEV exacto `${applicationId}.finance9read`, `exported=false`, sin permiso de Internet, *sin declarar provider en main*. I07 no-op revoke y raw sqlite3 no emiten signal; no cerrar la cobertura SQL de otro PID ni atribuir JVM a instrumentación Android. I08 si notifyChange falla no asumir IPC; ON_RESUME catch-up sigue siendo guardia indispensable.
**B5 DICTAMEN:** PASS_SCOPED_PRECODE_INFRASTRUCTURE_ONLY, condicionado a READY individual exact5 y tests XML/merged-manifest; no permiso para enlazar notify a SQL actual, no garantía Android IPC, no permiso para product UI ni W2. En la unidad posterior se audita el publisher cooperante posterior a COMMIT real y la distinción no-op; nunca mezclar.
## B6
PENDING hasta permit READY y writer CAS.
## B7
PENDING, revisar diff exact5, CI XML, manifest/seguridad y rechazo de claims de dos PIDs.