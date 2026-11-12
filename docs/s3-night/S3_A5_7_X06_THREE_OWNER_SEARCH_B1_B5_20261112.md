# FARO V5 X06 — prueba Android real de tres owners Reality8, B1–B5
Fecha 2026-11-12, base b045237cb6a82451ad7c2f0eb861b6603b2ad51c, unidad S3_A5_7_X06_THREE_REALITY_OWNER_SEARCH_ANDROID. PREAUDITORIA PARA COLA, NO READY SI X02 SIGUE ACTIVO.

## B1 — Fuente y gap
X06 Android Workspace UI 2/2 PASS scoped docs/s3-night/S3_A5_7_X06_SEARCH_ANDROID_B7_PASS_SCOPED_20261109.md blob 3b72deaa7887d4832aff3ee1a1192f28175c72f9 (run 42627471693), no tres owners Reality.
FaroV5G5ActiveFourOwnerSearchTest.kt blob 7750e41c915a313c88bf04fc51f642e6ca0e51ff es fixture canónica verificada: FaroV5V2CoreDevAuthority(root).open().use { }, cerrar stores, FileStorageGenerationLayout.requireStaged("v2-core-dev-workspace-1"), RealityStore real, financePersistence.createAccount/write entry, objectivePersistence.upsert, matterPersistence.upsert, reabrir y consultar 3 namespaces. No reejecutar ese test JVM sin delta.
FaroV5V2CoreDevAuthority.kt blob 333d7b727784752fb970597e25a66bf60106c963 ya crea grant independientes Workspace Finance Objectives Matters; federador y Screen existen. Falta test Android UI desde fuentes genuinas tipadas.

## B2 — V4.4 y reutilizacion
V4.4 solo UX Buscar. V5 usa sources tipados y owner stamps/revision/grants, no fake Search. Reusar semilla de G5 testV2CoreDev y helpers Compose de FaroV5S3A57SearchComposeUiTest.kt, NO modificar producción ni repetir 2/2 Workspace ya certificados. No migrar datos G15.

## B3 — Fuentes y decision
Android offline-first https://developer.android.com/topic/architecture/data-layer/offline-first : Reality owner es canonico. Compose testing https://developer.android.com/develop/ui/compose/testing : drawer+UI real. SQLite WAL https://www.sqlite.org/wal.html : cierre/commit de base antes de abrir nueva Session; no copiar SHM/WAL.
ELEGIDA: dos tests nuevos en emulador V2 DEV sobre owners reales. DESCARTADAS: fake hits, grant Workspace reusado por Finance, editar fuentes productivas por test, nueva DB aparte no vinculada al puntero.

## B4 — DOS rutas solo DESPUES de X02 B7
1. v5/app/src/g5AndroidTest/kotlin/ar/com/faro/v5/app/FaroV5S3A57X06ThreeOwnerSearchUiTest.kt
2. .github/workflows/faro-v5-s3-a5-7-x06-three-owner-search-android-directed.yml
Test x06ThreeCanonicalRealityOwnersSearchFromAndroidWithoutPrivateFields: hacer fixture real como G5 testV2, en raíz TEMP de APK test (no usuario), semilla legal de tres owners con transaccion canonica, abrir Activity, click Chat DEV, drawer Buscar, consultar tokens y distinguir Finance/Objective/Matter sin revelar amount/IDs. Si tipo namespace no observable por UI, comprobar puerto real y acciones UI separadamente; no falsear que UI muestra datos.
Test x06RevokedRealityOwnerHitDoesNotOpenAfterPause: hit tipado REAL, pausar Activity y cambio epoch/owner, rechazar opener con autoridad antigua y exigir click nuevo; ninguna fuga de snippets. Workflow API35 dirigido solo nueva clase, gradle -PfaroG5AndroidTestV2CoreDev=true, XML 2/0/0/0, APKs SHA/manifests no INTERNET, guard en SHA exacto.

## B5 — Auditoria y gating
Owner DATA solo test, seeds por puertos oficiales y stores cerrados antes de reabrir. No falsificar hit/crear grants por UI ni reescribir federador. Revalidar stamp, revision, permission y puntero; stale -> UNAVAILABLE, nunca COMPLETE falso. No pagos, keys, G15 ni codigo de X01/X02 previo. Si fixture necesita prod hook -> NEEDS_PREP con ruta, no expandir scope.
Cierre B7 scoped X06 three-owner solo tras Android 2/2 y guard. X06 GLOBAL permanece abierta mientras falte matriz de búsqueda integral.
DICTAMEN B5 PREAUDITED_QUEUED, PERMISO INDIVIDUAL solo despues de X02 B7 y nueva guardia/cotejo HEAD.
